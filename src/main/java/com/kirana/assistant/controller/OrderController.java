package com.kirana.assistant.controller;

import com.kirana.assistant.dto.OrderResponse;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.repository.InventoryItemRepository;
import com.kirana.assistant.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    /**
     * Get all orders (for the shopkeeper dashboard).
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> orders = orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(o -> OrderResponse.fromOrder(o, inventoryItemRepository))
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    /**
     * Get a single order by ID.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(o -> OrderResponse.fromOrder(o, inventoryItemRepository))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get orders by status.
     */
    @GetMapping("/orders/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable String status) {
        List<OrderResponse> orders = orderRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(o -> OrderResponse.fromOrder(o, inventoryItemRepository))
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    /**
     * Update an order's status (e.g., shopkeeper marks order as COMPLETED).
     */
    @PostMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable String orderId,
                                                           @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null) {
            return ResponseEntity.badRequest().build();
        }

        return orderRepository.findById(orderId)
                .map(order -> {
                    order.setStatus(newStatus);
                    order.setUpdatedAt(LocalDateTime.now());
                    orderRepository.save(order);
                    return ResponseEntity.ok(OrderResponse.fromOrder(order, inventoryItemRepository));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
