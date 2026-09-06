package com.kirana.assistant.service.ai;

import com.kirana.assistant.model.OrderItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured result the AI layer returns for one user utterance.
 * The backend validates this before touching any order state —
 * never trust LLM data blindly.
 */
public class AiOrderParseResult {

    private Intent intent = Intent.UNKNOWN;
    private List<OrderItem> items = new ArrayList<>();
    private String pickupTime;
    private boolean needsClarification;
    private String clarificationQuestion;
    private boolean confirmed;
    private String replyText = "";

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items != null ? items : new ArrayList<>();
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

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }
}
