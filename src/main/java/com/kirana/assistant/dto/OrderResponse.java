package com.kirana.assistant.dto;

import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

import com.kirana.assistant.model.InventoryItem;
import com.kirana.assistant.repository.InventoryItemRepository;

public class OrderResponse {

    private String id;
    private String customerId;
    private String customerPhoneNumber;
    private List<OrderItem> items;
    private String status;
    private String pickupTime;
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
        response.setCustomerId(order.getCustomerId());
        response.setCustomerPhoneNumber(order.getCustomerPhoneNumber());
        response.setItems(order.getItems());
        response.setStatus(order.getStatus());
        response.setPickupTime(order.getPickupTime());
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
