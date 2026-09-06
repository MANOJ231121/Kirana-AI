package com.kirana.assistant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Single item inside a create-order request. */
public class OrderItemRequest {

    @NotBlank(message = "Item name must not be blank")
    private String name;

    @Min(value = 1, message = "Quantity must be at least 1")
    private double quantity = 1;

    private String unit = "pc";

    public OrderItemRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
