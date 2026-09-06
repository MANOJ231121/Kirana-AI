package com.kirana.assistant.service.ai;

import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.service.OrderParsingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic fallback used when no LLM key is configured (and in tests).
 * Handles the spec conversation examples + ambiguous requests:
 * "2 milk dena" → asks Amul ya koi aur; "Haan" → confirm, etc.
 */
@Service
public class MockAiService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(MockAiService.class);

    private final OrderParsingService parsingService;

    public MockAiService(OrderParsingService parsingService) {
        this.parsingService = parsingService;
    }

    @Override
    public AiOrderParseResult parseUtterance(String transcript, List<OrderItem> currentItems) {
        AiOrderParseResult r = new AiOrderParseResult();
        String t = transcript == null ? "" : transcript.trim();
        String lower = t.toLowerCase(Locale.ROOT);

        if (t.isBlank()) {
            r.setIntent(Intent.UNKNOWN);
            r.setReplyText("Sorry, sunai nahi diya. Dobara boliye?");
            return r;
        }

        // Confirmations: "haan", "ha", "confirm", "order confirm", "kar do"
        if (lower.matches(".*\\b(haan|haa|ha|confirm|order confirm|kar do|kar dena|theek hai|ok)\\b.*")
                && lower.length() < 40) {
            r.setIntent(Intent.CONFIRM_ORDER);
            r.setConfirmed(true);
            r.setReplyText("Order confirm ho gaya. Dhanyavaad!");
            return r;
        }

        // Cancellations
        if (lower.contains("cancel") || lower.contains("rehne do") || lower.contains("mat do")) {
            r.setIntent(Intent.CANCEL_ORDER);
            r.setReplyText("Theek hai, order cancel kar diya.");
            return r;
        }

        // Pickup time hints: "7 baje", "shaam", "kal", "19:30"
        String pickup = extractPickup(lower);
        if (pickup != null && parsingService.parseList(t).isEmpty()) {
            r.setIntent(Intent.CREATE_ORDER);
            r.setPickupTime(pickup);
            r.setReplyText("Pickup time " + pickup + " note kar liya. Aur kuch chahiye?");
            return r;
        }

        // Remove hints
        if (lower.contains("hatao") || lower.contains("remove") || lower.contains("nikal")) {
            List<OrderItem> items = parsingService.parseList(t);
            r.setIntent(Intent.REMOVE_ITEM);
            r.setItems(items);
            r.setReplyText(items.isEmpty()
                    ? "Kaunsa item hatana hai, naam boliye?"
                    : items.get(0).getName() + " hata diya. Aur kuch?");
            return r;
        }

        List<OrderItem> items = parsingService.parseList(t);

        if (items.isEmpty()) {
            r.setIntent(Intent.ASK_QUESTION);
            r.setReplyText("Samajh gaya. Item ka naam aur quantity boliye, "
                    + "jaise '2 packet Amul milk'.");
            return r;
        }

        // Ambiguity guard: bare "milk/dudh" without brand → clarify.
        for (OrderItem item : items) {
            String n = item.getName().toLowerCase(Locale.ROOT);
            boolean mentionsMilk = n.contains("milk") || n.contains("dudh") || n.contains("doodh");
            boolean hasBrand = n.contains("amul") || n.contains("mother") || n.contains("verka")
                    || n.contains("nestle") || n.contains("sudha");
            if (mentionsMilk && !hasBrand) {
                r.setIntent(Intent.ADD_ITEM);
                r.setNeedsClarification(true);
                r.setClarificationQuestion("Kaunsa milk chahiye — Amul ya koi aur?");
                r.setReplyText("Kaunsa milk chahiye — Amul ya koi aur?");
                log.debug("Ambiguous milk request, asking clarification");
                return r;
            }
            if (n.equals("maggi") && (item.getUnit() == null || item.getUnit().equals("pc"))) {
                item.setUnit("packet"); // sensible default, no need to ask
            }
        }

        r.setIntent(Intent.ADD_ITEM);
        r.setItems(items);
        StringBuilder sb = new StringBuilder("Bilkul. ");
        for (int i = 0; i < items.size(); i++) {
            OrderItem it = items.get(i);
            sb.append((int) it.getQuantity()).append(" ").append(it.getUnit())
                    .append(" ").append(it.getName());
            if (i < items.size() - 1) {
                sb.append(" aur ");
            }
        }
        if (pickup != null) {
            r.setPickupTime(pickup);
            sb.append(" (pickup ").append(pickup).append(")");
        }
        sb.append(". Aur kuch chahiye?");
        r.setReplyText(sb.toString());
        return r;
    }

    private String extractPickup(String lower) {
        if (lower.matches(".*\\b\\d{1,2}[:.]\\d{2}\\b.*")) {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("\\b(\\d{1,2}[:.]\\d{2})\\b").matcher(lower);
            if (m.find()) {
                return m.group(1).replace('.', ':');
            }
        }
        if (lower.contains("7:30") || lower.contains("7.30") || lower.contains("saat baje")
                || lower.contains("7 baje") || lower.contains("shaam")) {
            return "19:30";
        }
        if (lower.contains("subah") || lower.contains("morning")) {
            return "09:00";
        }
        return null;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
