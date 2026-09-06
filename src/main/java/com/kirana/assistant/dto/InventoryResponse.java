package com.kirana.assistant.dto;

import com.kirana.assistant.model.InventoryItem;

/** Public menu item for the customer storefront. */
public class InventoryResponse {

    private String id;
    private String name;
    private double price;
    private String category;
    private boolean available;
    private String unit;
    private String imageUrl;

    public static InventoryResponse from(InventoryItem item) {
        InventoryResponse r = new InventoryResponse();
        r.setId(item.getId());
        r.setName(item.getName());
        r.setPrice(item.getPrice());
        r.setCategory(item.getCategory());
        r.setAvailable(item.isAvailable());
        r.setUnit(unitFor(item.getCategory()));
        r.setImageUrl(item.getImageUrl());
        return r;
    }

    private static String unitFor(String category) {
        if (category == null) {
            return "pc";
        }
        switch (category.toLowerCase()) {
            case "grains":
            case "staples":
            case "vegetables":
                return "kg";
            case "dairy":
                return "litre";
            default:
                return "packet";
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
