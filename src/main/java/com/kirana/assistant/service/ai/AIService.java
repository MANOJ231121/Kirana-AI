package com.kirana.assistant.service.ai;

import com.kirana.assistant.model.OrderItem;

import java.util.List;

/**
 * LLM abstraction. Swap Groq for any other provider by
 * implementing this interface — the rest of the app never
 * touches provider SDKs directly.
 */
public interface AIService {

    /** Parse one user utterance into a structured order action. */
    AiOrderParseResult parseUtterance(String transcript, List<OrderItem> currentItems);

    /** Short conversational Hinglish reply for the customer UI. */
    default String reply(String transcript, List<OrderItem> currentItems) {
        return parseUtterance(transcript, currentItems).getReplyText();
    }

    /** Provider name for diagnostics (e.g. "groq", "mock"). */
    String providerName();

    /** True when real credentials are configured. */
    boolean isAvailable();
}
