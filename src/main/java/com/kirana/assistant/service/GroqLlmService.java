package com.kirana.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GroqLlmService {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmService.class);

    private static final String GROQ_BASE_URL = "https://api.groq.com";
    private static final String GROQ_PATH = "/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    @Value("${GROQ_LLM_MODEL:llama-3.3-70b-versatile}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GroqLlmService(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(GROQ_BASE_URL)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Send a chat completion request to Groq.
     * Returns the assistant's text response.
     */
    public String chatCompletion(List<Map<String, String>> messages, double temperature) {
        log.info("Calling Groq LLM with {} messages", messages.size());

        ArrayNode messagesJson = objectMapper.createArrayNode();
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
            messagesJson.add(msgNode);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("messages", messagesJson);
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("max_tokens", 512);

        try {
            String response = webClient.post()
                    .uri(GROQ_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            log.debug("Groq response: {}", content);
            return content;
        } catch (Exception e) {
            log.error("Groq API request failed", e);
            throw new RuntimeException("Groq LLM call failed", e);
        }
    }

    /**
     * Convenience method with system prompt and single user message.
     */
    public String chatCompletion(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatCompletion(messages, 0.7);
    }

    /**
     * Send a low-temperature request that is expected to return raw JSON.
     * Sets response_format to json_object and temperature low for structured output.
     */
    public String chatCompletionJson(String systemPrompt, String userMessage) {
        log.info("Calling Groq JSON mode");
        try {
            ArrayNode messagesJson = objectMapper.createArrayNode();
            ObjectNode sys = objectMapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messagesJson.add(sys);
            ObjectNode usr = objectMapper.createObjectNode();
            usr.put("role", "user");
            usr.put("content", userMessage);
            messagesJson.add(usr);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("messages", messagesJson);
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 512);
            ObjectNode fmt = objectMapper.createObjectNode();
            fmt.put("type", "json_object");
            payload.set("response_format", fmt);

            String response = webClient.post()
                    .uri(GROQ_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            log.debug("Groq JSON response: {}", content);
            return content;
        } catch (Exception e) {
            log.error("Groq JSON request failed", e);
            throw new RuntimeException("Groq LLM JSON call failed", e);
        }
    }
}
