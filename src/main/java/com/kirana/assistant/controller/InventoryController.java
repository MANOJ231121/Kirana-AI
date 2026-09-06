package com.kirana.assistant.controller;

import com.kirana.assistant.dto.InventoryResponse;
import com.kirana.assistant.repository.InventoryItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public menu for the customer storefront. No auth — the menu is the
 * shop window. Only available items are shown by default.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryItemRepository inventory;

    public InventoryController(InventoryItemRepository inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> menu() {
        List<InventoryResponse> items = inventory.findAll().stream()
                .map(InventoryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }
}
