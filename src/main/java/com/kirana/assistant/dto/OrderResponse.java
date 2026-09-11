package com.kirana.assistant.dto;

import com.kirana.assistant.model.InventoryItem;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.repository.InventoryItemRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Canonical order response for the React frontends. */
public class OrderResponse {

    private String id;
    private String customerName;
    private String customerPhone;
    private List<OrderItem> items;
    private String status;
    private String pickupTime;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private double totalPrice;

    public OrderResponse() {
    }

    public static OrderResponse fromOrder(Order order) {
        return fromOrder(order, null);
    }

    public static OrderResponse fromOrder(Order order, InventoryItemRepository inventoryRepo) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setCustomerName(order.getCustomerName());
        // Backwards compat: older callers expect customerId / phoneNumber fields,
        // but the spec-facing fields are customerName + customerPhone.
        String phone = order.getCustomerPhone() != null
                ? order.getCustomerPhone()
                : order.getCustomerPhoneNumber();
        response.setCustomerPhone(phone);
        response.setItems(order.getItems());
        response.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
        response.setPickupTime(order.getPickupTime());
        response.setAddress(order.getAddress());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        if (inventoryRepo != null && order.getItems() != null) {
            double total = 0.0;
            for (OrderItem item : order.getItems()) {
                InventoryItem inv = inventoryRepo.findByNameIgnoreCase(item.getName());
                if (inv != null) {
                    total += inv.getPrice() * item.getQuantity();
                }
            }
            response.setTotalPrice(Math.round(total * 100.0) / 100.0);
        }

        return response;
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

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}
