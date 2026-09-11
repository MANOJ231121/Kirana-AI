package com.kirana.assistant.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.service.GroqKeyHealth;
import com.kirana.assistant.service.GroqLlmService;
import com.kirana.assistant.service.OrderParsingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Groq-backed {@link AIService}. Returns structured JSON via the
 * json_object response mode, then validates it before use.
 * Falls back to {@link MockAiService} behaviour on any failure so the
 * app stays 100% testable and resilient without crashing.
 */
@Service
public class GroqAiService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(GroqAiService.class);

    private static final String SYSTEM =
            "You are a helpful Kirana store AI assistant extracting grocery orders. "
                    + "Always return ONLY a JSON object with fields: "
                    + "intent (one of ADD_ITEM, REMOVE_ITEM, UPDATE_QUANTITY, CREATE_ORDER, "
                    + "CONFIRM_ORDER, CANCEL_ORDER, ASK_QUESTION, UNKNOWN), "
                    + "items (array of {name, quantity, unit}), pickupTime (string or null), "
                    + "needsClarification (boolean), clarificationQuestion (string or null), "
                    + "confirmed (boolean), replyText (short polite Hinglish reply, 1-2 sentences). "
                    + "If the item brand is ambiguous (e.g. just 'milk'), set needsClarification "
                    + "true and ask 'Kaunsa milk chahiye — Amul ya koi aur?'. "
                    + "Default quantity 1, default unit 'packet' for packed goods.";

    private final GroqLlmService groq;
    private final ObjectMapper mapper;
    private final MockAiService fallback;
    private final GroqKeyHealth keyHealth;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    @Value("${LLM_API_KEY:}")
    private String llmApiKey;

    /** Locked off after an auth failure so the app fails fast to the rule-based fallback. */
    private volatile boolean authFailed;

    public GroqAiService(GroqLlmService groq, ObjectMapper mapper, MockAiService fallback,
                         GroqKeyHealth keyHealth) {
        this.groq = groq;
        this.mapper = mapper;
        this.fallback = fallback;
        this.keyHealth = keyHealth;
    }

    private String getApiKey() {
        if (groqApiKey != null && !groqApiKey.isBlank()) return groqApiKey.trim();
        if (llmApiKey != null && !llmApiKey.isBlank()) return llmApiKey.trim();
        return "";
    }

    @Override
    public AiOrderParseResult parseUtterance(String transcript, List<OrderItem> currentItems) {
        if (!isAvailable()) {
            return fallback.parseUtterance(transcript, currentItems);
        }
        StringBuilder ctx = new StringBuilder("Current cart: ");
        if (currentItems == null || currentItems.isEmpty()) {
            ctx.append("empty");
        } else {
            for (OrderItem i : currentItems) {
                if (i == null) continue;
                ctx.append(i.getName()).append(" x").append(i.getQuantity())
                        .append(" ").append(i.getUnit()).append("; ");
            }
        }
        try {
            String json = groq.chatCompletionJson(SYSTEM,
                    "Customer said: \"" + (transcript != null ? transcript : "") + "\"\n" + ctx);
            JsonNode root = mapper.readTree(json);
            return validate(root, transcript);
        } catch (Exception e) {
            if (isAuthFailure(e)) {
                authFailed = true;
                keyHealth.markInvalid();
                log.error("Groq AI auth failed, locking off Groq LLM for this run: {}", e.getMessage());
            } else {
                log.warn("Groq AI parse failed, using mock fallback: {}", e.getMessage());
            }
            return fallback.parseUtterance(transcript, currentItems);
        }
    }

    private boolean isAuthFailure(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("401") || m.contains("403") || m.contains("unauthorized")
                        || m.contains("invalid api key") || m.contains("authentication")
                        || m.contains("api key")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private AiOrderParseResult validate(JsonNode root, String transcript) {
        AiOrderParseResult r = new AiOrderParseResult();
        if (root == null) {
            return fallback.parseUtterance(transcript, new ArrayList<>());
        }
        Intent intent;
        try {
            intent = Intent.valueOf(root.path("intent").asText("UNKNOWN").toUpperCase());
        } catch (Exception e) {
            intent = Intent.UNKNOWN;
        }
        r.setIntent(intent);

        List<OrderItem> items = new ArrayList<>();
        JsonNode arr = root.path("items");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String name = n.path("name").asText("").trim();
                double qty = n.path("quantity").asDouble(1.0);
                String unit = n.path("unit").asText("pc").trim();
                if (name.isEmpty() || qty <= 0 || qty > 1000) {
                    continue;
                }
                if (unit.isEmpty()) {
                    unit = "pc";
                }
                items.add(new OrderItem(name, qty, unit));
            }
        }
        r.setItems(items);

        String pickup = root.path("pickupTime").asText(null);
        if (root.path("pickupTime").isNull()) {
            pickup = null;
        }
        r.setPickupTime(pickup);
        r.setNeedsClarification(root.path("needsClarification").asBoolean(false));
        
        String q = root.path("clarificationQuestion").asText(null);
        r.setClarificationQuestion(root.path("clarificationQuestion").isNull() ? null : q);
        r.setConfirmed(root.path("confirmed").asBoolean(intent == Intent.CONFIRM_ORDER));
        
        String reply = root.path("replyText").asText("").trim();
        if (reply.isEmpty()) {
            reply = fallback.parseUtterance(transcript, items).getReplyText();
        }
        r.setReplyText(reply);

        return r;
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public boolean isAvailable() {
        if (authFailed) {
            return false;
        }
        String key = getApiKey();
        if (key.isBlank()) {
            return false;
        }
        return keyHealth.isValidated() ? keyHealth.isAvailable() : true;
    }
}
