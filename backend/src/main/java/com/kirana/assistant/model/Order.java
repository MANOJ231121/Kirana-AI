package com.kirana.assistant.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB order document.
 *
 * Spec fields: id, customerName, customerPhone (optional), items,
 * pickupTime, status, createdAt, updatedAt.
 *
 * Legacy fields customerId / customerPhoneNumber are kept for the
 * pre-existing phone-call agent code paths and are kept in sync
 * with customerPhone where possible.
 */
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    private String customerName;

    private String customerPhone;

    /** Customer delivery/pickup address captured via voice or the storefront. */
    private String address;

    /** Legacy: linked Customer document id (phone-call flows). */
    private String customerId;

    /** Legacy alias of {@link #customerPhone} (phone-call flows). */
    private String customerPhoneNumber;

    private List<OrderItem> items = new ArrayList<>();

    private OrderStatus status = OrderStatus.PENDING;

    private String pickupTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Order() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerPhoneNumber() {
        // Prefer the canonical field, fall back to legacy.
        if (customerPhoneNumber != null) {
            return customerPhoneNumber;
        }
        return customerPhone;
    }

    public void setCustomerPhoneNumber(String customerPhoneNumber) {
        this.customerPhoneNumber = customerPhoneNumber;
        if (this.customerPhone == null) {
            this.customerPhone = customerPhoneNumber;
        }
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /** String-based setter for legacy callers (e.g. dashboard sends "ACCEPTED"). */
    public void setStatus(String status) {
        if (status == null) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        // Backwards compatibility with the old phone-call lifecycle.
        if ("CREATED".equals(normalized)) {
            normalized = "PENDING";
        } else if ("CONFIRMED".equals(normalized)) {
            normalized = "ACCEPTED";
        }
        this.status = OrderStatus.valueOf(normalized);
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Effective phone for notifications: canonical first, then legacy. */
    public String effectivePhone() {
        if (customerPhone != null && !customerPhone.isBlank()) {
            return customerPhone;
        }
        return customerPhoneNumber;
    }
}
