package com.kirana.assistant;

import com.kirana.assistant.service.AiOrderAgentService;
import com.kirana.assistant.service.DeepgramService;
import com.kirana.assistant.service.GroqLlmService;
import com.kirana.assistant.service.RimeTtsService;
import com.kirana.assistant.websocket.MediaStreamsHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class KiranaAssistantApplicationContextTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("GROQ_API_KEY", () -> "dummy-groq");
        registry.add("RIME_API_KEY", () -> "dummy-rime");
        registry.add("RIME_SPEAKER", () -> "nadi");
        registry.add("DEEPGRAM_API_KEY", () -> "dummy-deepgram");
        registry.add("PUBLIC_BASE_URL", () -> "https://example.ngrok.io");
        registry.add("SEED_DATA", () -> "false");
    }

    @Autowired
    private GroqLlmService groqLlmService;

    @Autowired
    private RimeTtsService rimeTtsService;

    @Autowired
    private DeepgramService deepgramService;

    @Autowired
    private AiOrderAgentService aiOrderAgentService;

    @Autowired
    private MediaStreamsHandler mediaStreamsHandler;

    @Test
    void contextLoadsAndBeansPresent() {
        assertNotNull(groqLlmService);
        assertNotNull(rimeTtsService);
        assertNotNull(deepgramService);
        assertNotNull(aiOrderAgentService);
        assertNotNull(mediaStreamsHandler);
    }
}
