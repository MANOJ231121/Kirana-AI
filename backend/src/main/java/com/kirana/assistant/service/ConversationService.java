package com.kirana.assistant.service;

import com.kirana.assistant.dto.ConversationMessageRequest;
import com.kirana.assistant.dto.ConversationMessageResponse;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.service.ai.AIService;
import com.kirana.assistant.service.ai.AiOrderParseResult;
import com.kirana.assistant.service.ai.GroqAiService;
import com.kirana.assistant.service.ai.MockAiService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser-voice orchestration: transcript → structured order action.
 * Prefers Groq when configured, otherwise the deterministic mock —
 * the React app always gets the same JSON shape either way.
 */
@Service
public class ConversationService {

    private final GroqAiService groq;
    private final MockAiService mock;

    public ConversationService(GroqAiService groq, MockAiService mock) {
        this.groq = groq;
        this.mock = mock;
    }

    public ConversationMessageResponse process(ConversationMessageRequest req) {
        AIService ai = groq.isAvailable() ? groq : mock;
        List<OrderItem> cart = req.getCurrentItems() != null ? req.getCurrentItems() : new ArrayList<>();
        AiOrderParseResult parsed = ai.parseUtterance(req.getTranscript(), cart);

        // Backend validation of AI data: drop blanks / non-positive quantities.
        List<OrderItem> clean = new ArrayList<>();
        if (parsed.getItems() != null) {
            for (OrderItem i : parsed.getItems()) {
                if (i == null || i.getName() == null || i.getName().isBlank()) {
                    continue;
                }
                if (i.getQuantity() <= 0 || i.getQuantity() > 1000) {
                    continue;
                }
                if (i.getUnit() == null || i.getUnit().isBlank()) {
                    i.setUnit("pc");
                }
                i.setName(i.getName().trim());
                clean.add(i);
            }
        }
        parsed.setItems(clean);
        return ConversationMessageResponse.from(parsed, ai.providerName());
    }

    public String activeAiProvider() {
        return groq.isAvailable() ? groq.providerName() : mock.providerName();
    }
}
