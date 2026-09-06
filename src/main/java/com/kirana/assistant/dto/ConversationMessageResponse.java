package com.kirana.assistant.dto;

import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.service.ai.AiOrderParseResult;

import java.util.List;

/** POST /api/conversation/message response. */
public class ConversationMessageResponse {

    private String replyText;
    private String intent;
    private List<OrderItem> items;
    private String pickupTime;
    private boolean needsClarification;
    private String clarificationQuestion;
    private boolean confirmed;
    private String aiProvider;

    public static ConversationMessageResponse from(AiOrderParseResult r, String provider) {
        ConversationMessageResponse out = new ConversationMessageResponse();
        out.setReplyText(r.getReplyText());
        out.setIntent(r.getIntent() != null ? r.getIntent().name() : "UNKNOWN");
        out.setItems(r.getItems());
        out.setPickupTime(r.getPickupTime());
        out.setNeedsClarification(r.isNeedsClarification());
        out.setClarificationQuestion(r.getClarificationQuestion());
        out.setConfirmed(r.isConfirmed());
        out.setAiProvider(provider);
        return out;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public boolean isNeedsClarification() {
        return needsClarification;
    }

    public void setNeedsClarification(boolean needsClarification) {
        this.needsClarification = needsClarification;
    }

    public String getClarificationQuestion() {
        return clarificationQuestion;
    }

    public void setClarificationQuestion(String clarificationQuestion) {
        this.clarificationQuestion = clarificationQuestion;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }
}
