package com.kirana.assistant.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirana.assistant.model.CallSession;
import com.kirana.assistant.repository.CallSessionRepository;
import com.kirana.assistant.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MediaStreamsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaStreamsHandler.class);

    private static final Map<String, SessionContext> activeStreams = new ConcurrentHashMap<>();

    @Autowired
    private DeepgramService deepgramService;

    @Autowired
    private AiOrderAgentService aiOrderAgentService;

    @Autowired
    private RimeTtsService rimeTtsService;

    @Autowired
    private CallSessionRepository callSessionRepository;

    @Autowired
    private DashboardNotifierService dashboardNotifierService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class SessionContext {
        String streamSid;
        String callSid;
        String customerPhone;
        boolean greetingSent;
        StringBuilder audioBuffer;
        WebSocketSession twilioSession;

        SessionContext(String streamSid, String callSid) {
            this.streamSid = streamSid;
            this.callSid = callSid;
            this.greetingSent = false;
            this.audioBuffer = new StringBuilder();
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String event = payload.path("event").asText();

        switch (event) {
            case "connected" -> handleConnected(session);
            case "start" -> handleStart(session, payload);
            case "media" -> handleMedia(session, payload);
            case "stop" -> handleStop(session, payload);
            case "mark" -> handleMark(session, payload);
        }
    }

    private void handleConnected(WebSocketSession session) {
        log.info("Twilio Media Stream connected: {}", session.getId());
    }

    private void handleStart(WebSocketSession session, JsonNode payload) throws Exception {
        JsonNode start = payload.path("start");
        String streamSid = start.path("streamSid").asText();
        String callSid = start.path("callSid").asText();
        String customerPhone = start.path("customParameters").path("from").asText();
        String toNumber = start.path("customParameters").path("to").asText();

        log.info("Media Stream started. Stream: {}, Call: {}, From: {}", streamSid, callSid, customerPhone);

        SessionContext context = new SessionContext(streamSid, callSid);
        context.customerPhone = customerPhone;
        context.twilioSession = session;
        activeStreams.put(streamSid, context);

        // Create call session in DB
        CallSession callSession = new CallSession(callSid, customerPhone);
        callSessionRepository.save(callSession);

        dashboardNotifierService.notifyCallUpdate("Call started: " + customerPhone);

        // Send greeting using Rime TTS
        if (!context.greetingSent) {
            context.greetingSent = true;
            sendSpeechToTwilio(session, streamSid, "Namaste! Main aapki kirana assistant hoon. Kaise help kar sakti hoon?");
            callSession.addTranscriptEntry("AI", "Namaste! Main aapki kirana assistant hoon. Kaise help kar sakti hoon?");
            callSessionRepository.save(callSession);
        }
    }

    private void handleMedia(WebSocketSession session, JsonNode payload) throws Exception {
        JsonNode media = payload.path("media");
        String streamSid = media.path("streamSid").asText();
        String chunk = media.path("payload").asText();

        SessionContext context = activeStreams.get(streamSid);
        if (context == null || context.customerPhone == null) {
            return;
        }

        // Send audio chunk to Deepgram for streaming STT
        deepgramService.streamAudioChunk(context.callSid, chunk, finalTranscript ->
                handleUserTranscript(context, finalTranscript));
    }

    private void handleStop(WebSocketSession session, JsonNode payload) {
        JsonNode stop = payload.path("stop");
        String streamSid = stop.path("streamSid").asText();

        log.info("Media Stream stopped: {}", streamSid);
        SessionContext context = activeStreams.remove(streamSid);
        if (context != null) {
            deepgramService.closeStream(context.callSid);
            callSessionRepository.findByCallSid(context.callSid)
                    .ifPresent(cs -> {
                        cs.setEndTime(java.time.LocalDateTime.now());
                        cs.setStatus("COMPLETED");
                        callSessionRepository.save(cs);
                    });
            dashboardNotifierService.notifyCallUpdate("Call ended: " + context.customerPhone);
        }
    }

    private void handleMark(WebSocketSession session, JsonNode payload) {
        JsonNode mark = payload.path("mark");
        String streamSid = mark.path("streamSid").asText();
        log.debug("Mark received for stream: {}", streamSid);
    }

    private void handleUserTranscript(SessionContext context, String transcript) {
        log.info("User transcript: {}", transcript);

        if (transcript == null || transcript.isBlank()) {
            return;
        }

        // Get agent response via Groq LLM + order management
        String response = aiOrderAgentService.processUserSpeech(transcript, context.customerPhone,
                callSessionRepository.findByCallSid(context.callSid).orElse(null));

        // Convert response to speech via Rime and send to Twilio
        sendSpeechToTwilio(context.twilioSession, context.streamSid, response);
    }

    private void sendSpeechToTwilio(WebSocketSession session, String streamSid, String text) {
        try {
            byte[] audio = rimeTtsService.synthesizeSpeech(text);
            String base64Audio = Base64.getEncoder().encodeToString(audio);

            Map<String, Object> mediaMsg = Map.of(
                    "event", "media",
                    "streamSid", streamSid,
                    "media", Map.of("payload", base64Audio)
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(mediaMsg)));
            log.info("Sent Rime speech to Twilio for stream {}", streamSid);
        } catch (Exception e) {
            log.error("Failed to send speech to Twilio", e);
            // Handle Rime error gracefully - don't silently switch TTS providers
            try {
                Map<String, Object> fallbackMsg = Map.of(
                        "event", "media",
                        "streamSid", streamSid,
                        "media", Map.of("payload", "") // empty payload
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(fallbackMsg)));
            } catch (Exception ex) {
                log.error("Even fallback failed", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket closed: {} status: {}", session.getId(), status);
        activeStreams.values().removeIf(ctx -> ctx.twilioSession == session);
    }
}
