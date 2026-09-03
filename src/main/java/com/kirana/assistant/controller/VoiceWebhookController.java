package com.kirana.assistant.controller;

import com.kirana.assistant.model.CallSession;
import com.kirana.assistant.repository.CallSessionRepository;
import com.kirana.assistant.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/voice")
public class VoiceWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebhookController.class);

    @Autowired
    private TwilioService twilioService;

    @Autowired
    private CallSessionRepository callSessionRepository;

    @Value("${PUBLIC_BASE_URL:}")
    private String publicBaseUrl;

    @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleIncomingCall(@RequestParam Map<String, String> callParams,
                                                     @RequestBody(required = false) String rawBody) {
        log.info("Incoming call received. Params: {}", callParams);

        String fromNumber = callParams.getOrDefault("From", "unknown");
        String callSid = callParams.getOrDefault("CallSid", "unknown");
        String toNumber = callParams.getOrDefault("To", "unknown");

        log.info("Call SID: {}, From: {}, To: {}", callSid, fromNumber, toNumber);

        try {
            // Create a call session record
            CallSession session = new CallSession(callSid, fromNumber);
            callSessionRepository.save(session);

            String twimlResponse = twilioService.generateStreamingResponse(fromNumber, callSid, publicBaseUrl);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(twimlResponse);
        } catch (Exception e) {
            log.error("Error handling incoming call", e);
            String errorTwiml = twilioService.generateErrorResponse();
            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(errorTwiml);
        }
    }
}
