package com.kirana.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Validates the Groq API key once at startup by issuing a minimal probe
 * request. Any auth failure marks the key invalid for this process run so
 * the STT/LLM services fail fast to browser/mock fallbacks instead of
 * burning a round-trip on every single request.
 */
@Component
public class GroqKeyHealth {

    private static final Logger log = LoggerFactory.getLogger(GroqKeyHealth.class);
    private static final String GROQ_PROBE_URL =
            "https://api.groq.com/openai/v1/chat/completions";
    private static final String PROBE_BODY =
            "{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\","
            + "\"content\":\"hi\"}],\"max_tokens\":1,\"temperature\":0}";

    @Value("${GROQ_API_KEY:}")
    private String apiKey;

    private volatile boolean validated;

    @PostConstruct
    public void probeSoon() {
        if (apiKey == null || apiKey.isBlank()) {
            validated = true; // no key configured; services report unavailable via their own check
            return;
        }
        Thread t = new Thread(this::probe, "groq-key-probe");
        t.setDaemon(true);
        t.start();
    }

    private void probe() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_PROBE_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(PROBE_BODY))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                log.warn("Groq API key probe failed with HTTP {} - Groq STT/LLM will be "
                        + "skipped and browser/mock fallbacks used instead.", res.statusCode());
                invalid = true;
            } else {
                log.info("Groq API key probe succeeded (HTTP {})", res.statusCode());
            }
        } catch (Exception e) {
            log.debug("Groq API key probe could not complete (will probe lazily): {}", e.getMessage());
        } finally {
            validated = true;
        }
    }

    private volatile boolean invalid;

    /** True once the startup probe ran, so services can trust {@link #isAvailable()}. */
    public boolean isValidated() {
        return validated;
    }

    /** True when the Groq key may be used (not blank AND probe didn't reject it). */
    public boolean isAvailable() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return !invalid;
    }

    /** Called by services on a live 401 so we lock off repeat attempts fast. */
    public void markInvalid() {
        invalid = true;
    }
}