package com.kirana.assistant.service;

import com.kirana.assistant.dto.CreateOrderRequest;
import com.kirana.assistant.dto.OrderItemRequest;
import com.kirana.assistant.exception.InvalidOrderException;
import com.kirana.assistant.exception.InvalidStatusTransitionException;
import com.kirana.assistant.exception.OrderNotFoundException;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.model.OrderStatus;
import com.kirana.assistant.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All order business logic lives here — controllers stay thin.
 * Also enforces the valid status-transition graph.
 */
@Service
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED,
                OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED,
                OrderStatus.REJECTED));
        ALLOWED.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.READY, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final DashboardNotifierService dashboardNotifierService;

    public OrderService(OrderRepository orderRepository,
                        DashboardNotifierService dashboardNotifierService) {
        this.orderRepository = orderRepository;
        this.dashboardNotifierService = dashboardNotifierService;
    }

    public Order create(CreateOrderRequest req) {
        validateCreate(req);
        Order order = new Order();
        order.setCustomerName(req.getCustomerName().trim());
        String phone = req.getCustomerPhone() != null ? req.getCustomerPhone().trim() : null;
        order.setCustomerPhone(phone != null && phone.isEmpty() ? null : phone);
        order.setCustomerPhoneNumber(order.getCustomerPhone());
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest i : req.getItems()) {
            String unit = i.getUnit() == null || i.getUnit().isBlank() ? "pc" : i.getUnit().trim();
            items.add(new OrderItem(i.getName().trim(), i.getQuantity(), unit));
        }
        order.setItems(items);
        order.setPickupTime(req.getPickupTime());
        order.setAddress(req.getAddress());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        dashboardNotifierService.notifyOrderUpdate(saved, "CREATED");
        return saved;
    }

    public List<Order> listAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Order> listByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Order getById(String id) {
        validateId(id);
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order updateStatus(String id, String rawStatus) {
        Order order = getById(id);
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new InvalidOrderException("Status must not be blank");
        }
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Invalid status: " + rawStatus);
        }
        OrderStatus current = order.getStatus();
        if (current == target) {
            return order;
        }
        Set<OrderStatus> allowed = ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStatusTransitionException(current.name(), target.name());
        }
        order.setStatus(target);
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        dashboardNotifierService.notifyOrderUpdate(saved, "STATUS_" + target.name());
        return saved;
    }

    public void delete(String id) {
        Order order = getById(id);
        orderRepository.delete(order);
        dashboardNotifierService.notifyOrderUpdate(order, "DELETED");
    }

    private void validateCreate(CreateOrderRequest req) {
        if (req == null) {
            throw new InvalidOrderException("Order request must not be null");
        }
        if (req.getCustomerName() == null || req.getCustomerName().isBlank()) {
            throw new InvalidOrderException("Customer name must not be blank");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }
        for (OrderItemRequest i : req.getItems()) {
            if (i.getName() == null || i.getName().isBlank()) {
                throw new InvalidOrderException("Item name must not be blank");
            }
            if (i.getQuantity() <= 0) {
                throw new InvalidOrderException(
                        "Invalid quantity for '" + i.getName() + "': must be > 0");
            }
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidOrderException("Order id must not be blank");
        }
    }
}
