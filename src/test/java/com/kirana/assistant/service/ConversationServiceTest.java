package com.kirana.assistant.service;

import com.kirana.assistant.dto.ConversationMessageRequest;
import com.kirana.assistant.dto.ConversationMessageResponse;
import com.kirana.assistant.model.OrderItem;
import com.kirana.assistant.service.ai.MockAiService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the spec conversation examples through the mock AI path. */
class ConversationServiceTest {

    private ConversationService service() {
        MockAiService mock = new MockAiService(new OrderParsingService());
        return new ConversationService(nullSafeGroq(), mock);
    }

    private com.kirana.assistant.service.ai.GroqAiService nullSafeGroq() {
        // Groq without a key → isAvailable() false → mock path. Build without Spring.
        return new com.kirana.assistant.service.ai.GroqAiService(null, null,
                new MockAiService(new OrderParsingService())) {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String providerName() {
                return "groq";
            }
        };
    }

    private ConversationMessageRequest req(String transcript) {
        ConversationMessageRequest r = new ConversationMessageRequest();
        r.setTranscript(transcript);
        r.setCurrentItems(List.of());
        return r;
    }

    @Test
    void addItemsExample() {
        ConversationMessageResponse res =
                service().process(req("Bhaiya 2 packet Amul milk aur 3 Maggi chahiye"));
        assertEquals("ADD_ITEM", res.getIntent());
        assertFalse(res.getItems().isEmpty());
        assertTrue(res.getReplyText().contains("Aur kuch chahiye?"));
    }

    @Test
    void ambiguousMilkAsksClarification() {
        ConversationMessageResponse res = service().process(req("2 milk dena"));
        assertTrue(res.isNeedsClarification());
        assertNotNull(res.getClarificationQuestion());
    }

    @Test
    void shortConfirmationConfirms() {
        ConversationMessageResponse res = service().process(req("Haan"));
        assertEquals("CONFIRM_ORDER", res.getIntent());
        assertTrue(res.isConfirmed());
    }

    @Test
    void invalidAiItemsAreDropped() {
        MockAiService mock = new MockAiService(new OrderParsingService());
        ConversationService s = new ConversationService(nullSafeGroq(), mock);
        ConversationMessageRequest r = req("hello???");
        ConversationMessageResponse res = s.process(r);
        assertNotNull(res.getReplyText());
        for (OrderItem i : res.getItems()) {
            assertFalse(i.getName().isBlank());
            assertTrue(i.getQuantity() > 0);
        }
    }
}
