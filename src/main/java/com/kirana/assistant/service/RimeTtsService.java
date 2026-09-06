package com.kirana.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Map;

@Service
public class RimeTtsService {

    private static final Logger log = LoggerFactory.getLogger(RimeTtsService.class);

    private static final String RIME_BASE_URL = "https://users.rime.ai";
    private static final String RIME_PATH = "/v1/rime-tts";

    @Value("${RIME_API_KEY:}")
    private String rimeApiKey;

    @Value("${RIME_SPEAKER:nadi}")
    private String rimeSpeaker;

    private final WebClient webClient;

    public RimeTtsService() {
        this.webClient = WebClient.builder()
                .baseUrl(RIME_BASE_URL)
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

        try {
            byte[] audioData = webClient.post()
                    .uri(RIME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rimeApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "audio/mp3")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioData == null || audioData.length == 0) {
                log.error("Rime returned empty MP3 audio");
                return new byte[0];
            }

            log.info("Rime generated {} bytes of MP3 audio for speaker {}", audioData.length, rimeSpeaker);
            return audioData;
        } catch (Exception e) {
            log.error("Rime TTS MP3 synthesis failed: {}", e.getMessage());
            return new byte[0];
        }
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
            byte[] audioData = webClient.post()
                    .uri(RIME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rimeApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioData == null || audioData.length == 0) {
                return new byte[0];
            }

            return audioData;
        } catch (Exception e) {
            log.error("Rime TTS request failed: {}", e.getMessage());
            return new byte[0];
        }
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
