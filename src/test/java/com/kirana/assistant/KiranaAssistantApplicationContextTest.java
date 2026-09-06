package com.kirana.assistant;

import com.kirana.assistant.service.AiOrderAgentService;
import com.kirana.assistant.service.GroqLlmService;
import com.kirana.assistant.service.GroqWhisperService;
import com.kirana.assistant.service.OrderService;
import com.kirana.assistant.service.RimeTtsService;
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
    private GroqWhisperService groqWhisperService;

    @Autowired
    private AiOrderAgentService aiOrderAgentService;

    @Autowired
    private OrderService orderService;

    @Test
    void contextLoadsAndBeansPresent() {
        assertNotNull(groqLlmService);
        assertNotNull(rimeTtsService);
        assertNotNull(groqWhisperService);
        assertNotNull(aiOrderAgentService);
        assertNotNull(orderService);
    }
}
