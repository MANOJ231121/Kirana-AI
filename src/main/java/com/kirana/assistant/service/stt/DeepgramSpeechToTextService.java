package com.kirana.assistant.service.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Deepgram implementation of {@link SpeechToTextService}.
 * Uses the pre-recorded endpoint (nova-3, Hinglish-tuned) so the
 * browser only uploads short clips — no keys in React.
 * Falls back to empty transcript on failure; controller maps that
 * to a 502 with a friendly message.
 */
@Service
public class DeepgramSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSpeechToTextService.class);

    @Value("${DEEPGRAM_API_KEY:}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper mapper;

    public DeepgramSpeechToTextService(ObjectMapper mapper) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.deepgram.com")
                .build();
        this.mapper = mapper;
    }

    @Override
    public String transcribe(byte[] audio, String contentType) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("DEEPGRAM_API_KEY is not configured");
        }
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Empty audio");
        }
        String ct = contentType != null && !contentType.isBlank() ? contentType : "audio/webm";
        String body;
        try {
            body = webClient.post()
                    .uri(uri -> uri.path("/v1/listen")
                            .queryParam("model", "nova-3")
                            .queryParam("language", "hi")
                            .queryParam("smart_format", "true")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Token " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, ct)
                    .bodyValue(audio)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("Deepgram request failed", e);
            throw new RuntimeException("STT provider unavailable", e);
        }
        JsonNode root = mapper.readTree(body);
        String transcript = root.path("results").path("channels").path(0)
                .path("alternatives").path(0).path("transcript").asText("").trim();
        log.info("Deepgram transcript: {}", transcript);
        return transcript;
    }

    @Override
    public String providerName() {
        return "deepgram";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
