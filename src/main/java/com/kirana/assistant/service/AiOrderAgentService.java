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

        try {
            String response = groqLlmService.chatCompletion(messages, 0.8);

            // After LLM responds, check if inventory items are available
            response = checkInventoryAndPrompt(response, currentOrder);

            return response;
        } catch (Exception e) {
            log.warn("Groq LLM unreachable, using offline reply: {}", e.getMessage());
            return offlineReply(currentOrder);
        }
    }

    /**
     * Offline reply when the LLM is unreachable: confirms the spoken list
     * out loud so the call keeps working without any API key.
     */
    private String offlineReply(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "Boliye, kya chahiye? Jaise 1 kg Atta and 2 kg Chini.";
        }
        StringBuilder sb = new StringBuilder("Theek hai, list mein hai: ");
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem it = order.getItems().get(i);
            sb.append((int) it.getQuantity()).append(" ").append(it.getUnit())
                    .append(" ").append(it.getName());
            if (i < order.getItems().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(". Aur kuch chahiye, ya confirm kar doon?");
        return sb.toString();
    }

    /**
     * Uses a structured Groq call to detect order actions from the spoken transcript
     * (add/remove/quantity/pickup/confirm) and persists them to the Mongo order.
     * Only runs when the user likely mentioned items or order changes.
     */
    private void applySpokenActions(String transcript, Order order, CallSession session) {
        String lower = transcript.toLowerCase();

        boolean likelyItemChange = lower.matches(".*\\b(?:add|add karo|lagao|daalo|chahiye|chahi|do|dedo|kharid|mangwan|order|remove|hatao|hatana|utao|quantity|kitna|aur|bhi|ban|maaf|cancel|theek|confirm|pickup|time|shaam|subah|kal|aaj)\\b.*")
                || lower.matches(".*(?:आटा|दूध|दाल|चीनी|सामान|लिस्ट|जोड़ो|हटाओ|भेजो|चाहिए|किलो|ग्राम|लीटर|पैकेट|व्हाट्सएप).*")
                || orderParsingService.parseList(transcript).size() > 0;

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
            - {"op":"CONFIRM"}
            - {"op":"CLEAR"}

            Use the quantity/unit verbatim from the speech when present (default quantity 1, unit "pc").
            Match item names to the current order when the customer says "remove that" or "the first one".
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

        // Offline confirmation: "haan, confirm kar do" locks the spoken list in.
        if (!order.getItems().isEmpty()
                && lower.matches(".*\\b(haan|haa|confirm|kar do|pakka|theek hai|order confirm)\\b.*")
                && lower.length() < 60) {
            order.setStatus(OrderStatus.ACCEPTED);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            dashboardNotifierService.notifyOrderUpdate(order, "ORDER_CONFIRMED");
            addOrderChange(session, "ORDER_CONFIRMED");
            return;
        }

        List<OrderItem> parsed = orderParsingService.parseList(transcript);
        if (parsed.isEmpty()) {
            return;
        }
        boolean correction = lower.matches(".*\\b(instead|actually|change|sorry|rather|correction)\\b.*")
                || lower.matches(".*\\b(no+\\s+no+|nahi|nahin|badal|usko|isko)\\b.*");

        boolean changed = false;
        for (OrderItem p : parsed) {
            if (p.getName() == null || p.getName().isBlank() || p.getQuantity() <= 0) {
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
                        if (name.isEmpty()) break;
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
        StringBuilder sb = new StringBuilder();
        sb.append("Customer phone: ").append(order.getCustomerPhoneNumber()).append("\n");
        sb.append("Order status: ").append(order.getStatus()).append("\n");
        sb.append("Pickup time: ").append(order.getPickupTime() != null ? order.getPickupTime() : "not set").append("\n");
        sb.append("Items:\n");
        for (OrderItem item : order.getItems()) {
            sb.append("- ").append(item.getName()).append(": ")
              .append(item.getQuantity()).append(" ").append(item.getUnit()).append("\n");
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
                    return orderRepository.save(order);
                });
    }
}
