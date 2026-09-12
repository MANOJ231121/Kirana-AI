package com.kirana.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirana.assistant.model.*;
import com.kirana.assistant.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiOrderAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiOrderAgentService.class);

    private static final String SYSTEM_PROMPT = """
        You are "Kirana Bhai", a friendly, helpful AI assistant for a local Indian kirana store.
        You answer phone calls in a short, natural, conversational tone, using simple Hinglish
        (Hindi + English mix) because the caller speaks Hindi, English, or Hinglish.

        IMPORTANT RULES:
        1. ALWAYS respond in Hinglish (Hindi-English mix) in the Devanagari or Roman script.
        2. Keep responses SHORT and conversational. 1-2 sentences max.
        3. Sound like a friendly local shop assistant, not a robot.
        4. Let the caller speak naturally. Never force language choice.
        5. When the caller references their WhatsApp list, mention you are checking it.
        6. Ask for the caller's name if you do not know it yet ("Aapka naam kya hai?").
           When you hear a name, use it and ask what they want to order.
        7. After the caller has items in the order, ask for the delivery address
           ("Delivery ka pata bataiye?") if you do not have it yet.

        The caller can do these things:
        - Add items (ADD_ITEM)
        - Remove items (REMOVE_ITEM)
        - Change quantity of items (UPDATE_QUANTITY)
        - Check the current order (CHECK_ORDER)
        - Confirm the order (CONFIRM_ORDER)
        - Check total price (GET_TOTAL)
        - Set pickup time (SET_PICKUP_TIME)
        - Reference WhatsApp list (REFERENCE_WHATSAPP_LIST)
        - Ask about inventory (CHECK_INVENTORY)
        - Ask general questions (GENERAL_QUESTION)

        Respond naturally in Hinglish as short conversational text ONLY. No JSON, no labels.
        """;

    @Autowired
    private GroqLlmService groqLlmService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WhatsAppMessageRepository whatsAppMessageRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderParsingService orderParsingService;

    @Autowired
    private DashboardNotifierService dashboardNotifierService;

    /**
     * Process a user's speech transcript and generate the assistant's spoken response.
     * Returns the text response which is then converted to speech via Rime.
     */
    public String processUserSpeech(String transcript, String customerPhoneNumber, CallSession session) {
        log.info("Processing user speech from {}: {}", customerPhoneNumber, transcript);

        if (session != null) {
            session.addTranscriptEntry("CUSTOMER", transcript);
        }

        Customer customer = getOrCreateCustomer(customerPhoneNumber);
        Order currentOrder = getOrCreateActiveOrder(customer);

        String response = generateAgentResponse(transcript, customer, currentOrder, session);

        if (session != null) {
            session.addTranscriptEntry("AI", response);
        }

        log.info("Agent response: {}", response);
        return response;
    }

    /**
     * Generates the AI response using Groq LLM with context of the current order state.
     */
    private String generateAgentResponse(String transcript, Customer customer, Order currentOrder,
                                         CallSession session) {
        // Build message history with system prompt, order state, and user message
        List<Map<String, String>> messages = new ArrayList<>();

        String orderContext = buildOrderContext(currentOrder);

        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT + "\n\nCurrent order state:\n" + orderContext));

        // Include recent transcript context
        if (session != null && !session.getTranscript().isEmpty()) {
            int start = Math.max(0, session.getTranscript().size() - 6);
            for (int i = start; i < session.getTranscript().size(); i++) {
                CallSession.TranscriptEntry entry = session.getTranscript().get(i);
                messages.add(Map.of("role", "user".equals(entry.getSpeaker()) ? "user" : "assistant",
                        "content", entry.getText()));
            }
        } else {
            messages.add(Map.of("role", "user", "content", transcript));
        }

        // Determine if this is a WhatsApp reference request
        if (isWhatsAppReference(transcript)) {
            handleWhatsAppReference(customer, currentOrder, session);
        }

        // Persist any item changes the caller made by speaking, and push to
        // dashboard. Runs BEFORE the reply so the spoken list is saved even
        // when the LLM itself is unreachable (rule-based fallback inside).
        applySpokenActions(transcript, currentOrder, session);

        // Capture the caller's name / delivery address when they say it aloud.
        applySpeechDetails(transcript, customer, currentOrder, session);

        try {
            String response = groqLlmService.chatCompletion(messages, 0.8);

            // After LLM responds, check if inventory items are available
            response = checkInventoryAndPrompt(response, currentOrder);

            return response;
        } catch (Exception e) {
            log.warn("Groq LLM unreachable, using offline reply: {}", e.getMessage());
            return offlineReplyFlow(customer, currentOrder);
        }
    }

    /**
     * Offline reply when the LLM is unreachable: echoes the live list and keeps
     * the conversation moving (with name/address capture and confirm handling).
     */
    private String offlineReplyFlow(Customer customer, Order order) {
        boolean nameKnown = isNameKnown(customer);
        String name = nameKnown ? customer.getName() : "";
        List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
        boolean addressKnown = addressKnown(order);

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            return "Theek hai, order cancel kar diya hai. Ab bataiye, kya chahiye?";
        }
        if (OrderStatus.ACCEPTED.equals(order.getStatus())) {
            String head = nameKnown ? "Theek hai " + name + "! " : "Theek hai! ";
            if (!addressKnown) {
                return head + "Aapki list hai: " + itemListText(items) + ". Aur delivery ka address bataiye.";
            }
            return head + "Order confirm ho gaya. " + itemListText(items)
                    + ". Address: " + order.getAddress() + ". Dhanyavaad! Aur kuch chahiye?";
        }
        if (items.isEmpty()) {
            return "Boliye, kya chahiye? Jaise ek kilo Atta, do kilo Chini.";
        }
        if (!addressKnown) {
            return "Theek hai, list mein hai: " + itemListText(items) + ". Aur delivery ka address bataiye?";
        }
        return "Theek hai " + name + ", list mein hai: " + itemListText(items)
                + ". Aur kuch chahiye, ya confirm kar doon?";
    }

    private String itemListText(List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            OrderItem it = items.get(i);
            double q = it.getQuantity();
            String qty = q == Math.floor(q) ? String.valueOf((int) q) : String.valueOf(q);
            sb.append(qty).append(" ").append(it.getUnit()).append(" ").append(it.getName());
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.length() == 0 ? "(khaali hai)" : sb.toString();
    }

    /**
     * Captures the caller's name and delivery address from the spoken transcript
     * and persists them to the customer/order. Requires a saved order to attach
     * the address to, so it is called after the active order exists.
     */
    private void applySpeechDetails(String transcript, Customer customer, Order order, CallSession session) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }

        if (!isNameKnown(customer)) {
            String name = extractName(transcript);
            if (name != null) {
                customer.setName(name);
                customer.setUpdatedAt(java.time.LocalDateTime.now());
                customerRepository.save(customer);
                order.setCustomerName(name);
                order.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(order);
                addOrderChange(session, "SET_NAME " + name);
            }
        }

        if (!addressKnown(order)) {
            String address = extractAddress(transcript, order);
            if (address != null) {
                order.setAddress(address);
                order.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(order);
                addOrderChange(session, "SET_ADDRESS " + address);
            }
        }
    }

    private String extractName(String transcript) {
        String t = transcript.trim();

        // Devanagari: "मेरा नाम राहुल है", "मैं राहुल बोल रहा हूँ"
        String captured = null;
        Matcher m = Pattern.compile("मेरा\\s+नाम\\s+([\\p{L}\\s]{2,60})").matcher(t);
        if (m.find()) {
            captured = m.group(1);
        }
        if (captured == null) {
            Matcher m2 = Pattern.compile("मैं\\s+([\\p{L}\\s]{2,60})").matcher(t);
            if (m2.find()) {
                captured = m2.group(1);
            }
        }
        if (captured != null) {
            return cleanName(captured, true);
        }

        String[] patterns = {
                "(?i)\\b(?:my name is|mera naam|mera name|mery naam)\\s*(?:hai\\s*)?([A-Za-z][A-Za-z .'-]{1,40})",
                "(?i)\\b(?:i am|i'm|main|mai)\\s+([A-Za-z][A-Za-z .'-]{1,40})",
                "(?i)\\bnaam\\s*(?:hai\\s*)?([A-Za-z][A-Za-z .'-]{1,40})"
        };
        for (String pat : patterns) {
            Matcher m3 = Pattern.compile(pat).matcher(t);
            if (m3.find()) {
                captured = m3.group(1);
                break;
            }
        }
        return cleanName(captured, false);
    }

    private String cleanName(String raw, boolean devanagari) {
        if (raw == null) {
            return null;
        }
        String c = raw.replaceAll("\\s+", " ").trim();
        if (devanagari) {
            c = c.split("\\s+(है|हैं|था|थी|हूँ|हूं|बोल|बोलता|बोलती|रहा|रही)")[0];
        } else {
            c = c.split("(?i)\\s+(hai|hain|tha|thi|hu|hoon|hun|bol raha|bol rahi|bolta|bolti|aur|and|please|kya|chahiye|mujhe|mera|meri|sir|madam|ji)\\b")[0];
        }
        if (!devanagari) {
            c = c.replaceAll("(?i)\\b(main|mai|i am|i'm|sir|madam|bhai|bhaiya|ji)\\b", "").replaceAll("\\s+", " ").trim();
        }
        if (c.isEmpty() || c.length() > 40) {
            return null;
        }
        long words = c.chars().filter(ch -> ch == ' ').count() + 1;
        if (words > 4) {
            return null;
        }
        if (c.toLowerCase().matches("(?i)(bhai|bhaiya|sir|madam|doston|thik|theek|theek hai|ok|okay|hmm|aap|unknown|unknown user)")) {
            return null;
        }
        return c;
    }

    private String extractAddress(String transcript, Order order) {
        String t = transcript.trim();
        String captured = null;

        String[] explicit = {
                "(?i)(?:my address is|mera address|my pata|address is|pata|paata|ghar ka pata)\\s*(?:hai\\s*)?(?:ye\\s*)?([\\p{L}0-9][\\p{L}0-9 .,/-]{4,120})",
                "(?i)(?:mujhe|meri)\\s+(?:delivery\\s+)?(?:address|pata)\\s+(?:is\\s+|hai\\s+)?([\\p{L}0-9][\\p{L}0-9 .,/-]{4,120})"
        };
        for (String pat : explicit) {
            Matcher m = Pattern.compile(pat).matcher(t);
            if (m.find()) {
                captured = m.group(1);
                break;
            }
        }

        if (captured == null) {
            String[] devanagari = {
                    "(?:मेरा पता|पता|एड्रेस|घर का पता)\\s*(?:है\\s*)?([\\p{L}0-9\\s.,/-]{4,120})"
            };
            for (String pat : devanagari) {
                Matcher m = Pattern.compile(pat).matcher(t);
                if (m.find()) {
                    captured = m.group(1);
                    break;
                }
            }
        }

        if (captured != null) {
            String cleaned = cleanAddress(captured);
            if (cleaned != null) {
                return cleaned;
            }
        }

        // Bare-answer fallback: caller just says their address after being asked
        // ("Shastri Nagar, gali no 2"). Only when the conversation has items and
        // the utterance contains nothing item-ish or command-like.
        if (!order.getItems().isEmpty()) {
            String lower = t.toLowerCase();
            boolean hasItemLike = lower.matches(".*\\b(chahiye|chahi|kilo|kg|gm|gram|litre|packet|atta|rice|dal|chini|doodh|milk|sugar|oil|salt|add|hatao|remove|dedo|confirm|cancel)\\b.*")
                    || t.matches(".*(?:आटा|दाल|चावल|दूध|चीनी|किलो|चाहिए|हटाओ|भेजो).*");
            if (!hasItemLike && t.length() >= 4 && t.length() <= 80) {
                return cleanAddress(t);
            }
        }
        return null;
    }

    private String cleanAddress(String raw) {
        String a = raw.replaceAll("\\s+", " ").trim();
        a = a.replaceAll("(?i)\\s+(?:hai|hain)\\.?$", "");
        a = a.replaceAll("\\s+(है|हैं)$", "");
        a = a.replaceAll("[\\s,./-]+$", "");
        if (a.length() < 4 || a.length() > 80) {
            return null;
        }
        return a;
    }

    /**
     * Converts assistant text into a form the Hindi TTS reads smoothly:
     * "2 kg Atta" -> "do kilo आटा", "1.5" -> "dedh".
     */
    public String ttsTextFor(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String out = text;

        Matcher m = QTY_PATTERN.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double qty;
            try {
                qty = Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            String unit = m.group(2) == null ? "" : m.group(2).toLowerCase();
            String spokenUnit = switch (unit) {
                case "kilo", "kg" -> " kilo";
                case "gm", "gram" -> " gram";
                case "litre", "liter" -> " litre";
                case "packet", "pack" -> " packet";
                case "pc", "pcs", "piece" -> " piece";
                case "bottle" -> " bottle";
                default -> " " + unit;
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(numberToHindiWords(qty) + spokenUnit));
        }
        m.appendTail(sb);
        out = sb.toString();

        Matcher digits = DIGITS_PATTERN.matcher(out);
        sb = new StringBuffer();
        while (digits.find()) {
            double d;
            try {
                d = Double.parseDouble(digits.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            digits.appendReplacement(sb, Matcher.quoteReplacement(numberToHindiWords(d)));
        }
        digits.appendTail(sb);
        out = sb.toString();

        out = out.replaceAll("(?i)\\bkg\\b", "kilo")
                .replaceAll("(?i)\\bgm\\b", "gram")
                .replaceAll("(?i)\\bpcs?\\b", "piece")
                .replaceAll("(?i)\\blitre\\b", "litre");

        for (Map.Entry<String, String> e : TTS_ITEM_WORDS.entrySet()) {
            out = out.replaceAll("(?i)\\b" + Pattern.quote(e.getKey()) + "\\b", e.getValue());
        }

        out = out.replaceAll("[*_`~#|^><\\[\\]{}()\\\\]", "").replaceAll("\\s+", " ").trim();
        return out;
    }

    private static final Pattern QTY_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|kilo|gm|gram|litre|liter|packet|pack|pcs?|piece|bottle)\\b");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\b");

    private static final java.util.Map<String, String> TTS_ITEM_WORDS = java.util.Map.ofEntries(
            java.util.Map.entry("Atta", "आटा"), java.util.Map.entry("Rice", "चावल"),
            java.util.Map.entry("Dal", "दाल"), java.util.Map.entry("Milk", "दूध"),
            java.util.Map.entry("Ghee", "घी"), java.util.Map.entry("Sugar", "चीनी"),
            java.util.Map.entry("Salt", "नमक"), java.util.Map.entry("Tea", "चाय"),
            java.util.Map.entry("Maggi", "मैगी"), java.util.Map.entry("Biscuit", "बिस्कुट"),
            java.util.Map.entry("Eggs", "अंडे"), java.util.Map.entry("Bread", "ब्रेड"),
            java.util.Map.entry("Oil", "तेल"), java.util.Map.entry("Onion", "प्याज़"),
            java.util.Map.entry("Potato", "आलू"), java.util.Map.entry("Tomato", "टमाटर"),
            java.util.Map.entry("Butter", "मक्खन"), java.util.Map.entry("Shampoo", "शैम्पू"),
            java.util.Map.entry("Detergent", "डिटर्जेंट"));

    private static final String[] HINDI_ONES = {
            "", "ek", "do", "teen", "char", "paanch", "chhah", "saat", "aath", "nau",
            "das", "gyarah", "baarah", "terah", "chaudah", "pandrah", "solah", "satrah",
            "atharah", "unnis", "bis", "ikkees", "baais", "teis", "chaubis", "pachis",
            "chhabbis", "sattais", "atthais", "untis", "tees"};
    private static final String[] HINDI_TENS = {"", "", "bis", "tees", "chaalees", "pachaas",
            "saath", "sattar", "asi", "nabbe"};

    private String numberToHindiWords(double qty) {
        if (qty <= 0.0001) {
            return "shunya";
        }
        int whole = (int) Math.floor(qty);
        double frac = qty - whole;
        if (frac > 0.49 && frac < 0.51) {
            return switch (whole) {
                case 0 -> "aadha";
                case 1 -> "dedh";
                case 2 -> "dhai";
                case 3 -> "saade teen";
                default -> "saade " + hindiWhole(whole);
            };
        }
        if (frac > 0.001) {
            String f = String.format("%.2f", frac).substring(2).replaceAll("0$", "");
            StringBuilder fs = new StringBuilder();
            for (char c : f.toCharArray()) {
                fs.append(hindiWhole(Character.digit(c, 10))).append(" ");
            }
            return hindiWhole(whole) + " point " + fs.toString().trim();
        }
        return hindiWhole(whole);
    }

    private String hindiWhole(int n) {
        if (n <= 30) {
            return HINDI_ONES[n];
        }
        int tens = n / 10;
        int ones = n % 10;
        String t = HINDI_TENS[tens];
        return ones == 0 ? t : t + " " + HINDI_ONES[ones];
    }

    /**
     * Uses a structured Groq call to detect order actions from the spoken transcript
     * (add/remove/quantity/pickup/confirm) and persists them to the Mongo order.
     * Only runs when the user likely mentioned items or order changes.
     */
    private void applySpokenActions(String transcript, Order order, CallSession session) {
        String lower = transcript.toLowerCase();

        // Name/address statements ("mera naam X", "pata Y", "मेरा पता Y") must
        // never be turned into grocery items unless a real item is also present.
        if (isPersonalInfoOnly(lower, transcript)) {
            return;
        }

        boolean likelyItemChange = lower.matches(".*\\b(?:add|add karo|lagao|daalo|chahiye|chahi|do|dedo|kharid|mangwan|order|remove|hatao|hatana|utao|quantity|kitna|aur|bhi|ban|maaf|cancel|theek|confirm|pickup|time|shaam|subah|kal|aaj)\\b.*")
                || lower.matches(".*(?:आटा|दूध|दाल|चीनी|सामान|लिस्ट|जोड़ो|हटाओ|भेजो|चाहिए|किलो|ग्राम|लीटर|पैकेट|व्हाट्सएप).*")
                || orderParsingService.parseKnownList(transcript).size() > 0;

        if (!likelyItemChange) {
            return;
        }

        String actionPrompt = """
            You extract order actions from a customer's spoken grocery request.
            A kirana store customer has said: "%s"

            Current order:
            %s

            Return ONLY JSON with an array field "actions". Each action is an object:
            - {"op":"ADD","name":"item name","quantity":2,"unit":"kg"}
            - {"op":"REMOVE","name":"item name"}
            - {"op":"SET_QTY","name":"item name","quantity":1}
            - {"op":"SET_PICKUP","time":"tomorrow evening"}
            - {"op":"SET_NAME","name":"customer's name"}
            - {"op":"SET_ADDRESS","address":"delivery address"}
            - {"op":"CONFIRM"}
            - {"op":"CLEAR"}

            Use the quantity/unit verbatim from the speech when present (default quantity 1, unit "pc").
            Match item names to the current order when the customer says "remove that" or "the first one".
            If the caller states their name or delivery address, emit SET_NAME / SET_ADDRESS.
            If nothing clearly maps to an action, return {"actions":[]}.
            """.formatted(transcript, buildOrderContext(order));

        try {
            String json = groqLlmService.chatCompletionJson(
                    "You are a strict JSON extractor. Only output the JSON object, no extra text.", actionPrompt);
            applyActionsFromJson(json, order, session);
        } catch (Exception e) {
            log.warn("LLM action extraction failed, using rule-based fallback: {}", e.getMessage());
            applyRuleBasedActions(transcript, order, session);
        }
    }

    /**
     * Deterministic fallback when the LLM is unreachable (no key / offline).
     * Merges newly spoken items and handles live quantity corrections such as
     * "No no, I want 2 kg Atta instead" by overwriting the matched item's quantity.
     */
    private void applyRuleBasedActions(String transcript, Order order, CallSession session) {
        String lower = transcript.toLowerCase();

        // Offline cancel: "cancel kar do", "bas ho gaya", "khaali kar do".
        // MUST run before the confirm check because "cancel kar do" also
        // contains the confirm cue "kar do".
        if (lower.matches(".*\\b(cancel|mat karo|rehne do|khaali kar do|clear list|bas ho gaya)\\b.*")
                || lower.contains("कैंसल") || lower.contains("रद्द")) {
            if (!order.getItems().isEmpty()) {
                order.getItems().clear();
                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(order);
                dashboardNotifierService.notifyOrderUpdate(order, "ORDER_CANCELLED");
                addOrderChange(session, "ORDER_CANCELLED");
            }
            return;
        }

        // Offline confirmation: "haan, confirm kar do" locks the spoken list in.
        if (!order.getItems().isEmpty()
                && lower.matches(".*\\b(haan|haa|confirm|kar do|pakka|theek hai|order confirm)\\b.*")
                && lower.length() < 60
                && !lower.contains("cancel") && !lower.contains("रद्द")) {
            order.setStatus(OrderStatus.ACCEPTED);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            dashboardNotifierService.notifyOrderUpdate(order, "ORDER_CONFIRMED");
            addOrderChange(session, "ORDER_CONFIRMED");
            return;
        }

        List<OrderItem> parsed = orderParsingService.parseKnownList(transcript);
        if (parsed.isEmpty()) {
            return;
        }
        boolean correction = lower.matches(".*\\b(instead|actually|change|sorry|rather|correction)\\b.*")
                || lower.matches(".*\\b(no+\\s+no+|nahi|nahin|badal|usko|isko)\\b.*");

        boolean changed = false;
        for (OrderItem p : parsed) {
            if (p.getName() == null || p.getName().isBlank() || p.getQuantity() <= 0
                    || isJunkSpokenItem(p.getName())) {
                continue;
            }
            OrderItem existing = order.getItems().stream()
                    .filter(i -> matchesItem(i.getName(), p.getName()))
                    .findFirst().orElse(null);
            if (existing != null && correction) {
                existing.setQuantity(p.getQuantity());
                if (p.getUnit() != null && !p.getUnit().isBlank()) {
                    existing.setUnit(p.getUnit());
                }
                changed = true;
                addOrderChange(session, "SET_QTY " + existing.getName() + " -> " + p.getQuantity());
            } else if (existing != null) {
                existing.setQuantity(existing.getQuantity() + p.getQuantity());
                changed = true;
                addOrderChange(session, "ADD " + p.getName() + " x" + p.getQuantity());
            } else {
                order.getItems().add(new OrderItem(capitalize(p.getName()), p.getQuantity(),
                        p.getUnit() == null || p.getUnit().isBlank() ? "pc" : p.getUnit()));
                changed = true;
                addOrderChange(session, "ADD " + p.getName() + " x" + p.getQuantity());
            }
        }

        if (changed) {
            order.setUpdatedAt(java.time.LocalDateTime.now());
            if (OrderStatus.PENDING.equals(order.getStatus()) && !order.getItems().isEmpty()) {
                order.setStatus(OrderStatus.PREPARING);
            }
            orderRepository.save(order);
            dashboardNotifierService.notifyOrderUpdate(order, "SPOKEN_UPDATE");
        }
    }

    private boolean matchesItem(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim().toLowerCase();
        String y = b.trim().toLowerCase();
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    /** Spoken name/address sentences must not become grocery items. */
    private boolean isJunkSpokenItem(String name) {
        if (name == null) {
            return true;
        }
        String n = name.toLowerCase();
        if (n.startsWith("naam") || n.startsWith("pata ") || n.equals("pata")
                || n.startsWith("address") || n.equals("address")
                || n.startsWith("mera") || n.startsWith("meri") || n.startsWith("mere")
                || n.startsWith("main ") || n.startsWith("mai ")) {
            return true;
        }
        return n.contains("raha hoon") || n.contains("rahi hoon") || n.contains("bol raha")
                || n.contains("bol rahi");
    }

    /** True when the utterance is purely a name/address statement (no grocery item). */
    private boolean isPersonalInfoOnly(String lower, String raw) {
        boolean personal = lower.contains("name") || lower.contains("naam")
                || lower.contains("address") || lower.contains("pata") || lower.contains("paata")
                || containsCharSequence(raw, "मेरा नाम") || containsCharSequence(raw, "मेरा पता")
                || containsCharSequence(raw, "पता") || containsCharSequence(raw, "एड्रेस")
                || containsCharSequence(raw, "पता है") || containsCharSequence(raw, "घर का पता")
                // Pure introduction with no explicit marker: "mujhe mera naam batao..." handled above;
                // "main Rahul bol raha hoon", "मैं राहुल बोल रहा हूँ".
                || lower.matches(".*\\b(main|mai|i am|i'm)\\s+[a-z]+\\b.*")
                || raw.matches(".*(मैं|मै|मेरा|मेरी)\\s+[\\p{L}]+.*");
        if (!personal) {
            return false;
        }
        boolean hasItem = LATIN_ITEM_PATTERN.matcher(lower).find()
                || DEVA_ITEM_PATTERN.matcher(raw).find();
        return !hasItem;
    }

    private static boolean containsCharSequence(String s, String sub) {
        return s != null && s.contains(sub);
    }

    private static final Pattern LATIN_ITEM_PATTERN = Pattern.compile(
            "(?i)\\b(atta|aata|rice|chawal|dal|daal|milk|doodh|ghee|sugar|chini|cheeni|salt|namak|tea|chai|maggi|maggie|biscuit|biskit|eggs?|ande?|bread|pav|oil|tel|onion|pyaaz|potato|aloo|tomato|tamatar|shampoo|shampu|detergent|soap|butter|noodles)\\b");

    private static final Pattern DEVA_ITEM_PATTERN = Pattern.compile(
            "(?<![\\p{IsDevanagari}])(आटा|अटा|चावल|चवल|दाल|दल|दूध|दुध|घी|घ्य|चीनी|शक्कर|नमक|चाय|मैगी|मग्गी|नूडल्स|बिस्कुट|बिस्किट|अंडा|अंडे|अन्डा|ब्रेड|तेल|तैल|प्याज|प्याज़|आलू|अलू|टमाटर|शैम्पू|शैंपू|डिटर्जेंट|साबुन)(?![\\p{IsDevanagari}])");

    private void applyActionsFromJson(String json, Order order, CallSession session) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode actions = root.path("actions");
            if (!actions.isArray() || actions.isEmpty()) {
                return;
            }

            boolean changed = false;
            for (JsonNode action : actions) {
                String op = action.path("op").asText().toUpperCase();
                switch (op) {
case "ADD" -> {
                        String name = action.path("name").asText().trim();
                        if (name.isEmpty() || !orderParsingService.isKnownName(capitalize(name))) {
                            break;
                        }
                        double qty = action.path("quantity").asDouble(1.0);
                        String unit = action.path("unit").asText("pc");
                        if (unit.isBlank()) unit = "pc";

                        OrderItem existing = order.getItems().stream()
                                .filter(i -> i.getName().equalsIgnoreCase(name))
                                .findFirst().orElse(null);
                        if (existing != null) {
                            existing.setQuantity(existing.getQuantity() + qty);
                        } else {
                            order.getItems().add(new OrderItem(capitalize(name), qty, unit));
                        }
                        changed = true;
                        addOrderChange(session, "ADD " + name + " x" + qty + unit);
                    }
                    case "REMOVE" -> {
                        String name = action.path("name").asText().trim();
                        boolean removed = order.getItems().removeIf(i -> i.getName().equalsIgnoreCase(name));
                        if (removed) {
                            changed = true;
                            addOrderChange(session, "REMOVE " + name);
                        }
                    }
                    case "SET_QTY" -> {
                        String name = action.path("name").asText().trim();
                        double qty = action.path("quantity").asDouble(1.0);
                        for (OrderItem i : order.getItems()) {
                            if (i.getName().equalsIgnoreCase(name)) {
                                i.setQuantity(qty);
                                changed = true;
                                addOrderChange(session, "SET_QTY " + name + " -> " + qty);
                                break;
                            }
                        }
                    }
                    case "SET_ADDRESS" -> {
                        String address = action.path("address").asText().trim();
                        if (!address.isEmpty()) {
                            order.setAddress(address);
                            changed = true;
                            addOrderChange(session, "SET_ADDRESS " + address);
                        }
                    }
                    case "SET_NAME" -> {
                        String name = action.path("name").asText().trim();
                        if (!name.isEmpty()) {
                            order.setCustomerName(name);
                            changed = true;
                            addOrderChange(session, "SET_NAME " + name);
                        }
                    }
                    case "SET_PICKUP" -> {
                        String time = action.path("time").asText().trim();
                        if (!time.isEmpty()) {
                            order.setPickupTime(time);
                            changed = true;
                            addOrderChange(session, "PICKUP " + time);
                        }
                    }
                    case "CONFIRM" -> {
                        order.setStatus(OrderStatus.ACCEPTED);
                        changed = true;
                        addOrderChange(session, "ORDER_CONFIRMED");
                    }
                    case "CLEAR" -> {
                        order.getItems().clear();
                        changed = true;
                        addOrderChange(session, "ORDER_CLEARED");
                    }
                    default -> { }
                }
            }

            if (changed) {
                order.setUpdatedAt(java.time.LocalDateTime.now());
                if (OrderStatus.PENDING.equals(order.getStatus())
                        && !order.getItems().isEmpty()) {
                    order.setStatus(OrderStatus.PREPARING);
                }
                orderRepository.save(order);
                dashboardNotifierService.notifyOrderUpdate(order, "SPOKEN_UPDATE");
            }
        } catch (Exception e) {
            log.warn("Error applying parsed speech actions: {}", e.getMessage());
        }
    }

    private void addOrderChange(CallSession session, String change) {
        if (session != null) {
            session.getOrderChanges().add(change);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }

    /**
     * Checks if the user is referencing their WhatsApp list.
     */
    private boolean isWhatsAppReference(String transcript) {
        String lower = transcript.toLowerCase();
        return lower.contains("whatsapp") || lower.contains("whats app")
                || lower.contains("list") || lower.contains("was ap") || lower.contains("wasapp")
                || lower.contains("व्हाट्सएप") || lower.contains("व्हाट्सऐप") || lower.contains("लिस्ट");
    }

    /**
     * Loads the latest WhatsApp list into the current order and updates it.
     */
    private void handleWhatsAppReference(Customer customer, Order currentOrder, CallSession session) {
        Optional<WhatsAppMessage> latestMsg = whatsAppMessageRepository
                .findFirstByCustomerPhoneNumberOrderByTimestampDesc(customer.getPhoneNumber());

        if (latestMsg.isPresent()) {
            WhatsAppMessage msg = latestMsg.get();
            log.info("Found WhatsApp message from {}: {}", customer.getPhoneNumber(), msg.getMessage());

            List<OrderItem> parsedItems = orderParsingService.parseList(msg.getMessage());
            if (!parsedItems.isEmpty()) {
                currentOrder.getItems().clear();
                currentOrder.getItems().addAll(parsedItems);
                currentOrder.setStatus(OrderStatus.PREPARING);
                currentOrder.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(currentOrder);

                dashboardNotifierService.notifyOrderUpdate(currentOrder, "WHATSAPP_LIST_LOADED");

                if (session != null) {
                    session.getOrderChanges().add("WhatsApp list loaded: " + parsedItems.size() + " items");
                }
            }
        } else {
            log.warn("No WhatsApp message found for {}", customer.getPhoneNumber());
        }
    }

    /**
     * Checks inventory for unavailable items in the current order and prompts alternatives.
     */
    private String checkInventoryAndPrompt(String response, Order currentOrder) {
        List<String> unavailable = new ArrayList<>();
        for (OrderItem item : currentOrder.getItems()) {
            InventoryItem inv = inventoryItemRepository.findByNameIgnoreCase(item.getName());
            if (inv != null && !inv.isAvailable()) {
                unavailable.add(item.getName() + " (try " + inv.getAlternatives() + ")");
            }
        }

        if (!unavailable.isEmpty()) {
            response += " Note: " + String.join(", ", unavailable) + " abhi available nahi hai.";
        }
        return response;
    }

    /**
     * Builds a string representation of the current order for the LLM context.
     */
    private String buildOrderContext(Order order) {
        return buildOrderContext(order, null);
    }

    private String buildOrderContext(Order order, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer name: ").append(customer != null ? customer.getName() : "unknown").append("\n");
        if (order.getAddress() != null && !order.getAddress().isBlank()) {
            sb.append("Customer address: ").append(order.getAddress()).append("\n");
        }
        sb.append("Customer phone: ").append(order.getCustomerPhoneNumber()).append("\n");
        sb.append("Order status: ").append(order.getStatus()).append("\n");
        sb.append("Pickup time: ").append(order.getPickupTime() != null ? order.getPickupTime() : "not set").append("\n");
        sb.append("Items:\n");
        for (OrderItem item : order.getItems()) {
            double q = item.getQuantity();
            String qty = q == Math.floor(q) ? String.valueOf((int) q) : String.valueOf(q);
            sb.append("- ").append(item.getName()).append(": ").append(qty).append(" ").append(item.getUnit()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Gets or creates a customer by phone number.
     */
    public Customer getOrCreateCustomer(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> {
                    Customer c = new Customer(phoneNumber, "Customer " + phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
                    return customerRepository.save(c);
                });
    }

    /**
     * Gets the active order for a customer, or creates a new one.
     */
    public Order getOrCreateActiveOrder(Customer customer) {
        return orderRepository.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .filter(o -> !OrderStatus.COMPLETED.equals(o.getStatus())
                        && !OrderStatus.CANCELLED.equals(o.getStatus()))
                .orElseGet(() -> {
                    Order order = new Order();
                    order.setCustomerId(customer.getId());
                    order.setCustomerPhoneNumber(customer.getPhoneNumber());
                    order.setCustomerName(customer.getName());
                    return orderRepository.save(order);
                });
    }

    /**
     * Closes out any previous active orders for this customer so a brand-new
     * call always starts from a clean, empty order.
     */
    public void startFreshCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        Customer customer = getOrCreateCustomer(phoneNumber);
        List<Order> previous = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        for (Order o : previous) {
            if (!OrderStatus.COMPLETED.equals(o.getStatus())
                    && !OrderStatus.CANCELLED.equals(o.getStatus())) {
                o.setStatus(OrderStatus.CANCELLED);
                o.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(o);
            }
        }
    }

    /**
     * Opening greeting for a new call: friendly, and asks for the name when
     * the customer is new or still anonymous.
     */
    public String greetingFor(Customer customer) {
        if (isNameKnown(customer)) {
            return "Hello " + customer.getName() + "! Welcome to Sharma Kirana Store. Aapko kya chahiye? Boliye.";
        }
        return "Hello! Welcome to Sharma Kirana Store. Aapka naam kya hai?";
    }

    public boolean isNameKnown(Customer customer) {
        return customer != null && customer.getName() != null && !customer.getName().isBlank()
                && !customer.getName().toLowerCase().startsWith("customer ");
    }

    public boolean addressKnown(Order order) {
        return order != null && order.getAddress() != null && !order.getAddress().isBlank();
    }
}
