package com.kirana.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class DeepgramService {

    private static final Logger log = LoggerFactory.getLogger(DeepgramService.class);

    private static final String DEEPGRAM_WS_URL = "wss://api.deepgram.com/v1/listen";

    @Value("${DEEPGRAM_API_KEY}")
    private String deepgramApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WsSession> sessions = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    public DeepgramService() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    private static class WsSession {
        WebSocket webSocket;
        Consumer<String> onFinal;
        StringBuilder interim = new StringBuilder();
    }

    /**
     * Streams an audio chunk (base64 mulaw 8kHz) to Deepgram for real-time transcription.
     * When an "utterance_end" event is received, the final transcript is passed to the callback.
     */
    public void streamAudioChunk(String callSid, String base64Chunk, Consumer<String> onFinal) {
        WsSession session = sessions.computeIfAbsent(callSid, k -> createSession(k, onFinal));
        session.onFinal = onFinal;

        if (session.webSocket == null) {
            log.warn("WebSocket not ready for call {}, dropping chunk", callSid);
            return;
        }

        byte[] audioBytes = Base64.getDecoder().decode(base64Chunk);
        session.webSocket.sendBinary(ByteBuffer.wrap(audioBytes), true);
    }

    private WsSession createSession(String callSid, Consumer<String> onFinal) {
        WsSession session = new WsSession();
        session.onFinal = onFinal;

        String url = DEEPGRAM_WS_URL
                + "?model=nova-3"
                + "&language=hi"
                + "&smart_format=true"
                + "&encoding=mulaw"
                + "&sample_rate=8000"
                + "&endpointing=400"
                + "&utterance_end_ms=800";

        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder messageBuffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                log.info("Deepgram WebSocket opened for call {}", callSid);
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageBuffer.append(data);
                if (last) {
                    String json = messageBuffer.toString();
                    messageBuffer.setLength(0);
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        handleDeepgramEvent(callSid, root, session);
                    } catch (Exception e) {
                        log.error("Error parsing Deepgram response", e);
                    }
                }
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("Deepgram WebSocket closed for call {}: {} {}", callSid, statusCode, reason);
                sessions.remove(callSid);
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("Deepgram WebSocket error for call {}: {}", callSid, error.getMessage());
                sessions.remove(callSid);
            }
        };

        httpClient.newWebSocketBuilder()
                .header("Authorization", "Token " + deepgramApiKey)
                .buildAsync(URI.create(url), listener)
                .thenAccept(ws -> session.webSocket = ws)
                .exceptionally(e -> {
                    log.error("Failed to connect to Deepgram for call {}", callSid, e);
                    return null;
                });

        return session;
    }

    private void handleDeepgramEvent(String callSid, JsonNode root, WsSession session) {
        String type = root.path("type").asText();
        if ("Results".equals(type)) {
            boolean isFinal = root.path("is_final").asBoolean();
            String transcript = root.path("channel").path("alternatives").path(0).path("transcript").asText();

            if (isFinal && !transcript.isBlank()) {
                log.info("Final transcript for call {}: {}", callSid, transcript);
                Consumer<String> cb = session.onFinal;
                if (cb != null) {
                    cb.accept(transcript);
                }
            }
        }
    }

    /**
     * Closes the Deepgram stream for a call.
     */
    public void closeStream(String callSid) {
        WsSession session = sessions.remove(callSid);
        if (session != null && session.webSocket != null) {
            session.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Call ended").join();
            log.info("Closed Deepgram stream for call {}", callSid);
        }
    }

    @PreDestroy
    public void cleanup() {
        sessions.forEach((k, v) -> {
            if (v.webSocket != null) {
                v.webSocket.abort();
            }
        });
        sessions.clear();
    }
}
