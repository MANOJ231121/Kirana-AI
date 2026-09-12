package com.kirana.assistant.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "inventory_items")
public class InventoryItem {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private boolean available;

    private double price;

    private String category;

    private String alternatives;

    private String imageUrl;

    public InventoryItem() {
    }

    public InventoryItem(String name, boolean available, double price, String category, String alternatives) {
        this(name, available, price, category, alternatives, null);
    }

    public InventoryItem(String name, boolean available, double price, String category, String alternatives, String imageUrl) {
        this.name = name;
        this.available = available;
        this.price = price;
        this.category = category;
        this.alternatives = alternatives;
        this.imageUrl = imageUrl;
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

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
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

    public String getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(String alternatives) {
        this.alternatives = alternatives;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
