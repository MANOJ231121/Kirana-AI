package com.kirana.assistant.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "whatsapp_messages")
public class WhatsAppMessage {

    @Id
    private String id;

    private String customerId;

    private String customerPhoneNumber;

    private String message;

    private String parsedOrder;

    private LocalDateTime timestamp;

    public WhatsAppMessage() {
    }

    public WhatsAppMessage(String customerId, String customerPhoneNumber, String message) {
        this.customerId = customerId;
        this.customerPhoneNumber = customerPhoneNumber;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerPhoneNumber() {
        return customerPhoneNumber;
    }

    public void setCustomerPhoneNumber(String customerPhoneNumber) {
        this.customerPhoneNumber = customerPhoneNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getParsedOrder() {
        return parsedOrder;
    }

    public void setParsedOrder(String parsedOrder) {
        this.parsedOrder = parsedOrder;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
