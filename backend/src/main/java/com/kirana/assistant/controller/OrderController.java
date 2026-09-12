package com.kirana.assistant.controller;

import com.kirana.assistant.dto.CreateOrderRequest;
import com.kirana.assistant.dto.OrderResponse;
import com.kirana.assistant.dto.UpdateOrderStatusRequest;
import com.kirana.assistant.exception.InvalidOrderException;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderStatus;
import com.kirana.assistant.repository.InventoryItemRepository;
import com.kirana.assistant.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spec order API:
 * POST /api/orders, GET /api/orders, GET /api/orders/{id},
 * PATCH /api/orders/{id}/status, DELETE /api/orders/{id}.
 *
 * Thin controller — all logic lives in {@link OrderService}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final InventoryItemRepository inventoryItemRepository;

    public OrderController(OrderService orderService,
                           InventoryItemRepository inventoryItemRepository) {
        this.orderService = orderService;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        Order saved = orderService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.fromOrder(saved, inventoryItemRepository));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(
            @RequestParam(value = "status", required = false) String status) {
        List<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus s;
            try {
                s = OrderStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidOrderException("Invalid status: " + status);
            }
            orders = orderService.listByStatus(s);
        } else {
            orders = orderService.listAll();
        }
        return ResponseEntity.ok(orders.stream()
                .map(o -> OrderResponse.fromOrder(o, inventoryItemRepository))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOne(@PathVariable String id) {
        return ResponseEntity.ok(
                OrderResponse.fromOrder(orderService.getById(id), inventoryItemRepository));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable String id,
                                                      @Valid @RequestBody UpdateOrderStatusRequest req) {
        Order updated = orderService.updateStatus(id, req.getStatus());
        return ResponseEntity.ok(OrderResponse.fromOrder(updated, inventoryItemRepository));
    }

    /** Backwards-compatible alias for older dashboard builds using POST. */
    @PostMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatusPost(@PathVariable String id,
                                                          @RequestBody Map<String, String> body) {
        Order updated = orderService.updateStatus(id, body.get("status"));
        return ResponseEntity.ok(OrderResponse.fromOrder(updated, inventoryItemRepository));
    }

    /** Backwards-compatible alias: GET /api/orders/status/{status}. */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderResponse>> listByStatusPath(@PathVariable String status) {
        return list(status);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
