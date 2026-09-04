package com.kirana.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

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

    /**
     * Generate speech audio from text using Rime TTS.
     * Returns raw audio bytes in G.711 mu-law format (8kHz) for telephony compatibility.
     */
    public byte[] synthesizeSpeech(String text) {
        log.info("Synthesizing speech with Rime. Speaker: {}, Text length: {}", rimeSpeaker, text.length());

        Map<String, Object> payload = Map.of(
                "text", text,
                "modelId", "coda",
                "speaker", rimeSpeaker,
                "lang", "hi",
                "samplingRate", 8000
        );

        try {
            byte[] audioData = webClient.post()
                    .uri(RIME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rimeApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "audio/PCMU")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioData == null || audioData.length == 0) {
                log.error("Rime returned empty audio");
                throw new RuntimeException("Rime returned empty audio");
            }

            log.info("Rime generated {} bytes of audio", audioData.length);
            return audioData;
        } catch (Exception e) {
            log.error("Rime TTS request failed", e);
            throw new RuntimeException("Rime TTS failed", e);
        }
    }

    /**
     * Convert to base64 for sending over WebSocket to Twilio.
     */
    public String synthesizeSpeechBase64(String text) {
        return Base64.getEncoder().encodeToString(synthesizeSpeech(text));
    }
}
