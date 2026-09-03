package com.kirana.assistant.repository;

import com.kirana.assistant.model.InventoryItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InventoryItemRepository extends MongoRepository<InventoryItem, String> {

    InventoryItem findByNameIgnoreCase(String name);

    List<InventoryItem> findByAvailable(boolean available);
}
