package com.kirana.assistant.service.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirana.assistant.service.GroqKeyHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Groq Whisper Speech-to-Text service implementation.
 * Transcribes recorded microphone audio using Groq's whisper-large-v3-turbo model.
 */
@Service
public class GroqSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(GroqSpeechToTextService.class);
    private static final String GROQ_AUDIO_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    @Value("${GROQ_API_KEY:}")
    private String apiKey;

    @Value("${GROQ_WHISPER_MODEL:whisper-large-v3-turbo}")
    private String whisperModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GroqKeyHealth keyHealth;

    /** Set to true once a request fails with an auth error, to fail fast on repeat calls. */
    private volatile boolean authFailed;

    public GroqSpeechToTextService(ObjectMapper objectMapper, GroqKeyHealth keyHealth) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = objectMapper;
        this.keyHealth = keyHealth;
    }

    @Override
    public String transcribe(byte[] audio, String contentType) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Audio payload is empty");
        }

        String extension = "webm";
        if (contentType != null) {
            if (contentType.contains("wav")) extension = "wav";
            else if (contentType.contains("mp3")) extension = "mp3";
            else if (contentType.contains("ogg")) extension = "ogg";
            else if (contentType.contains("m4a")) extension = "m4a";
        }
        final String fileName = "speech." + extension;
        final String mediaType = (contentType != null && !contentType.isBlank()) ? contentType : "audio/webm";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        ByteArrayResource audioResource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        builder.part("file", audioResource, MediaType.parseMediaType(mediaType));
        builder.part("model", whisperModel);
        builder.part("language", "hi");
        builder.part("response_format", "json");

        log.info("Sending {} bytes to Groq Whisper STT (model: {})", audio.length, whisperModel);

        try {
            String response = webClient.post()
                    .uri(GROQ_AUDIO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                String transcript = root.path("text").asText("").trim();
                log.info("Groq Whisper transcript: {}", transcript);
                return transcript;
            }
            return "";
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api key")
                    || msg.contains("authentication") || msg.contains("api key")) {
                authFailed = true;
                keyHealth.markInvalid();
                log.error("Groq Whisper auth failed, disabling Groq STT for this run: {}", e.getMessage());
                throw new IllegalStateException("GROQ_API_KEY is invalid");
            }
            throw e;
        }
    }

    @Override
    public String providerName() {
        return "groq-whisper";
    }

    @Override
    public boolean isAvailable() {
        if (authFailed) {
            return false;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return keyHealth.isValidated() ? keyHealth.isAvailable() : true;
    }
}
