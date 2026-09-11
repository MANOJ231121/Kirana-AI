package com.kirana.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Rime AI TTS. Uses the JDK HttpClient with a fresh connection per request
 * (Connection: close, like curl) instead of a pooled keep-alive client:
 * Rime's AWS ELB drops pooled connections while the MP3 body is streaming,
 * which surfaces in pooled clients as a 200-with-error and silent replies.
 */
@Service
public class RimeTtsService {

    private static final Logger log = LoggerFactory.getLogger(RimeTtsService.class);

    private static final String RIME_BASE_URL = "https://users.rime.ai";
    private static final String RIME_PATH = "/v1/rime-tts";

    @Value("${RIME_API_KEY:}")
    private String rimeApiKey;

    @Value("${RIME_SPEAKER:nadi}")
    private String rimeSpeaker;

    private final HttpClient httpClient;

    public RimeTtsService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isAvailable() {
        return rimeApiKey != null && !rimeApiKey.isBlank();
    }

    /**
     * Generate speech audio from text using Rime TTS in MP3 format for web/browser playback.
     */
    public byte[] synthesizeSpeechMp3(String text) {
        log.info("Synthesizing speech with Rime TTS. Speaker: {}, Text: {}", rimeSpeaker, text);

        if (!isAvailable()) {
            log.warn("RIME_API_KEY is not configured! Cannot synthesize speech with Rime.");
            return new byte[0];
        }

        Map<String, Object> payload = Map.of(
                "text", text,
                "speaker", rimeSpeaker != null && !rimeSpeaker.isBlank() ? rimeSpeaker : "nadi",
                "modelId", "coda",
                "audioFormat", "mp3"
        );

        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                byte[] audioData = postAudio(payload);
                if (audioData != null && audioData.length > 0) {
                    log.info("Rime generated {} bytes of MP3 audio for speaker {}", audioData.length, rimeSpeaker);
                    return audioData;
                }
                lastError = new IllegalStateException("Rime returned empty MP3 audio");
                log.warn("Rime TTS attempt {} returned empty audio (will retry)", attempt);
            } catch (Exception e) {
                lastError = e;
                if (attempt == 1) {
                    log.warn("Rime TTS attempt 1 failed (will retry): [{}] {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }
            try {
                Thread.sleep(700L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.error("Rime TTS MP3 synthesis failed after 3 attempts: {}",
                lastError == null ? "empty response" : lastError.getMessage());
        return new byte[0];
    }

    public byte[] synthesizeSpeech(String text) {
        log.info("Synthesizing telephony speech with Rime. Speaker: {}", rimeSpeaker);

        if (!isAvailable()) {
            log.warn("RIME_API_KEY is not set.");
            return new byte[0];
        }

        Map<String, Object> payload = Map.of(
                "text", text,
                "speaker", rimeSpeaker != null && !rimeSpeaker.isBlank() ? rimeSpeaker : "nadi",
                "modelId", "coda",
                "audioFormat", "pcm"
        );

        try {
            byte[] audioData = postAudio(payload);
            return audioData == null ? new byte[0] : audioData;
        } catch (Exception e) {
            log.error("Rime TTS request failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private byte[] postAudio(Map<String, Object> payload) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RIME_BASE_URL + RIME_PATH))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + rimeApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mp3")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Rime returned HTTP " + response.statusCode()
                    + ": " + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return response.body();
    }

    public String synthesizeSpeechBase64Mp3(String text) {
        byte[] bytes = synthesizeSpeechMp3(text);
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String synthesizeSpeechBase64(String text) {
        byte[] bytes = synthesizeSpeech(text);
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}