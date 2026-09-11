package com.kirana.assistant.controller;

import com.kirana.assistant.model.CallSession;
import com.kirana.assistant.model.Customer;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.repository.CallSessionRepository;
import com.kirana.assistant.repository.OrderRepository;
import com.kirana.assistant.service.AiOrderAgentService;
import com.kirana.assistant.service.DashboardNotifierService;
import com.kirana.assistant.service.RimeTtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/web-call")
public class WebCallController {

    private static final Logger log = LoggerFactory.getLogger(WebCallController.class);

    @Autowired
    private AiOrderAgentService aiOrderAgentService;

    @Autowired
    private RimeTtsService rimeTtsService;

    @Autowired
    private CallSessionRepository callSessionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DashboardNotifierService dashboardNotifierService;

    /**
     * Start a new WebPhone call session.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCall(@RequestBody Map<String, String> body) {
        String phoneNumber = body.getOrDefault("phoneNumber", "+919999999999");
        String callSid = "web_" + System.currentTimeMillis();

        log.info("Starting WebCall session {} for customer {}", callSid, phoneNumber);

        // A new call ALWAYS starts fresh: close out any previous active
        // orders so stale items never leak into the next conversation.
        aiOrderAgentService.startFreshCall(phoneNumber);
        Customer customer = aiOrderAgentService.getOrCreateCustomer(phoneNumber);
        String greeting = aiOrderAgentService.greetingFor(customer);

        CallSession session = new CallSession(callSid, phoneNumber);
        session.addTranscriptEntry("AI", greeting);
        // Single save - a new session MUST NOT be persisted twice or the
        // `callSid` lookup later fails with "returned non unique result".
        CallSession saved = callSessionRepository.save(session);
        session.setId(saved.getId());

        dashboardNotifierService.notifyCallUpdate("Web Call started from: " + phoneNumber);

        String base64Audio = "";
        try {
            // Drive the audio from the TTS-normalized text so quantities and
            // units are pronounced smoothly ("do kilo aata", not "2 kay-gee").
            base64Audio = rimeTtsService.synthesizeSpeechBase64Mp3(
                    aiOrderAgentService.ttsTextFor(greeting));
        } catch (Exception e) {
            log.warn("Failed to generate Rime TTS greeting: {}", e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("callSid", callSid);
        response.put("phoneNumber", phoneNumber);
        response.put("greeting", greeting);
        response.put("audioBase64", base64Audio);

        return ResponseEntity.ok(response);
    }

    /**
     * Process user speech in a WebPhone call session.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processSpeech(@RequestBody Map<String, String> body) {
        String callSid = body.get("callSid");
        String transcript = body.get("transcript");
        String phoneNumber = body.getOrDefault("phoneNumber", "+919999999999");

        log.info("WebCall speech from {} (call {}): {}", phoneNumber, callSid, transcript);

        if (transcript == null || transcript.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        CallSession session = callSessionRepository.findTopByCallSidOrderByStartTimeDesc(callSid)
                .orElseGet(() -> {
                    CallSession cs = new CallSession(callSid, phoneNumber);
                    return callSessionRepository.save(cs);
                });

        String aiResponse = aiOrderAgentService.processUserSpeech(transcript, phoneNumber, session);
        callSessionRepository.save(session);

        String base64Audio = "";
        try {
            base64Audio = rimeTtsService.synthesizeSpeechBase64Mp3(
                    aiOrderAgentService.ttsTextFor(aiResponse));
        } catch (Exception e) {
            log.warn("Failed to generate Rime TTS audio response: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("aiResponse", aiResponse);
        result.put("audioBase64", base64Audio);

        // Live grocery list snapshot so the call UI can verify quantities
        // (including real-time corrections like "No no, 2 kg Atta instead").
        try {
            var customer = aiOrderAgentService.getOrCreateCustomer(phoneNumber);
            var activeOrder = aiOrderAgentService.getOrCreateActiveOrder(customer);
            result.put("orderId", activeOrder.getId());
            result.put("orderStatus", activeOrder.getStatus() != null
                    ? activeOrder.getStatus().name() : null);
            result.put("items", activeOrder.getItems());
            result.put("pickupTime", activeOrder.getPickupTime());
            result.put("customerName", customer.getName());
            result.put("nameKnown", aiOrderAgentService.isNameKnown(customer));
            result.put("address", activeOrder.getAddress());
            result.put("addressKnown", aiOrderAgentService.addressKnown(activeOrder));
        } catch (Exception e) {
            log.warn("Failed to snapshot call order: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * End a WebPhone call session.
     */
    @PostMapping("/end")
    public ResponseEntity<Map<String, String>> endCall(@RequestBody Map<String, String> body) {
        String callSid = body.get("callSid");
        String phoneNumber = body.getOrDefault("phoneNumber", "+919999999999");

        log.info("Ending WebCall session {}", callSid);

        if (callSid != null) {
            callSessionRepository.findTopByCallSidOrderByStartTimeDesc(callSid).ifPresent(session -> {
                session.setEndTime(java.time.LocalDateTime.now());
                session.setStatus("COMPLETED");
                callSessionRepository.save(session);
            });
        }

        dashboardNotifierService.notifyCallUpdate("Web Call ended: " + phoneNumber);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    /**
     * Modify a single item of the active call order (REMOVE or SET_QTY).
     * Called from the +/−/× controls of the live grocery list.
     */
    @PostMapping("/order-update")
    public ResponseEntity<Map<String, Object>> updateOrderItem(@RequestBody Map<String, String> body) {
        String phoneNumber = body.getOrDefault("phoneNumber", "+919999999999");
        String itemName = body.get("itemName");
        String action = body.getOrDefault("action", "SET_QTY");
        Double quantity = null;
        if (body.get("quantity") != null && !body.get("quantity").isBlank()) {
            try {
                quantity = Double.parseDouble(body.get("quantity"));
            } catch (NumberFormatException e) {
                quantity = null;
            }
        }
        if (itemName == null || itemName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var customer = aiOrderAgentService.getOrCreateCustomer(phoneNumber);
            var activeOrder = aiOrderAgentService.getOrCreateActiveOrder(customer);
            boolean changed = false;
            if ("REMOVE".equalsIgnoreCase(action)) {
                changed = activeOrder.getItems().removeIf(i -> i.getName().equalsIgnoreCase(itemName));
            } else {
                for (OrderItem item : activeOrder.getItems()) {
                    if (item.getName().equalsIgnoreCase(itemName)) {
                        if (quantity != null && quantity > 0) {
                            item.setQuantity(quantity);
                            changed = true;
                        }
                        break;
                    }
                }
            }
            if (changed) {
                activeOrder.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(activeOrder);
                dashboardNotifierService.notifyOrderUpdate(activeOrder, "SPOKEN_UPDATE");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("items", activeOrder.getItems());
            result.put("orderId", activeOrder.getId());
            result.put("orderStatus", activeOrder.getStatus() != null ? activeOrder.getStatus().name() : null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to update call order item: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
