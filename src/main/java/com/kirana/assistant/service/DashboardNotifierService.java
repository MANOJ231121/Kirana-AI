package com.kirana.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirana.assistant.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardNotifierService {

    private static final Logger log = LoggerFactory.getLogger(DashboardNotifierService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sends an order update to the shopkeeper dashboard in real-time.
     */
    public void notifyOrderUpdate(Order order, String action) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "ORDER_UPDATE");
            payload.put("action", action);
            payload.put("orderId", order.getId());
            payload.put("customerPhone", order.getCustomerPhoneNumber());
            payload.put("status", order.getStatus());
            payload.put("pickupTime", order.getPickupTime());
            payload.put("items", order.getItems());
            payload.put("timestamp", java.time.LocalDateTime.now().toString());

            String message = objectMapper.writeValueAsString(payload);
            messagingTemplate.convertAndSend("/topic/orders", message);
            log.info("Sent order update to dashboard: {}", message);
        } catch (Exception e) {
            log.error("Failed to send dashboard notification", e);
        }
    }

    /**
     * Sends a call session update to the dashboard.
     */
    public void notifyCallUpdate(String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "CALL_UPDATE");
            payload.put("data", message);
            payload.put("timestamp", java.time.LocalDateTime.now().toString());

            String msg = objectMapper.writeValueAsString(payload);
            messagingTemplate.convertAndSend("/topic/calls", msg);
        } catch (Exception e) {
            log.error("Failed to send call update", e);
        }
    }
}
