package com.kirana.assistant.dto;

import com.kirana.assistant.model.OrderItem;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

/** POST /api/conversation/message request. */
public class ConversationMessageRequest {

    @NotBlank(message = "Transcript must not be blank")
    private String transcript;

    private String customerName;
    private String customerPhone;
    private String pickupTime;
    private List<OrderItem> currentItems = new ArrayList<>();

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public List<OrderItem> getCurrentItems() {
        return currentItems;
    }

    public void setCurrentItems(List<OrderItem> currentItems) {
        this.currentItems = currentItems != null ? currentItems : new ArrayList<>();
    }
}
