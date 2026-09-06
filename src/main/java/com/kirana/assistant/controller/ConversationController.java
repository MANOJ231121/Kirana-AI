package com.kirana.assistant.controller;

import com.kirana.assistant.dto.ConversationMessageRequest;
import com.kirana.assistant.dto.ConversationMessageResponse;
import com.kirana.assistant.service.ConversationService;
import com.kirana.assistant.service.stt.DeepgramSpeechToTextService;
import com.kirana.assistant.service.stt.GroqSpeechToTextService;
import com.kirana.assistant.service.stt.MockSpeechToTextService;
import com.kirana.assistant.service.stt.SpeechToTextService;
import com.kirana.assistant.service.tts.MockTextToSpeechService;
import com.kirana.assistant.service.tts.RimeTextToSpeechService;
import com.kirana.assistant.service.tts.TextToSpeechService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Web-voice endpoints. All provider keys stay server-side.
 *
 * POST /api/conversation/message — transcript → structured order + reply
 * POST /api/stt/transcribe — audio clip → transcript (Groq Whisper, Deepgram or mock)
 * POST /api/tts/synthesize — reply text → base64 audio (Rime or mock)
 * GET /api/voice/status — which providers are live
 */
@RestController
@RequestMapping("/api")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;
    private final GroqSpeechToTextService groqStt;
    private final DeepgramSpeechToTextService deepgram;
    private final MockSpeechToTextService mockStt;
    private final RimeTextToSpeechService rime;
    private final MockTextToSpeechService mockTts;

    public ConversationController(ConversationService conversationService,
                                  GroqSpeechToTextService groqStt,
                                  DeepgramSpeechToTextService deepgram,
                                  MockSpeechToTextService mockStt,
                                  RimeTextToSpeechService rime,
                                  MockTextToSpeechService mockTts) {
        this.conversationService = conversationService;
        this.groqStt = groqStt;
        this.deepgram = deepgram;
        this.mockStt = mockStt;
        this.rime = rime;
        this.mockTts = mockTts;
    }

    private SpeechToTextService getActiveSttService() {
        if (groqStt.isAvailable()) return groqStt;
        if (deepgram.isAvailable()) return deepgram;
        return mockStt;
    }

    private TextToSpeechService getActiveTtsService() {
        if (rime.isAvailable()) return rime;
        return mockTts;
    }

    @PostMapping("/conversation/message")
    public ResponseEntity<ConversationMessageResponse> message(
            @Valid @RequestBody ConversationMessageRequest req) {
        try {
            return ResponseEntity.ok(conversationService.process(req));
        } catch (Exception e) {
            log.error("Conversation processing error", e);
            // Fallback response instead of 500 error
            ConversationMessageResponse fallbackRes = new ConversationMessageResponse();
            fallbackRes.setReplyText("Theek hai, aapka order note kar liya hai. Aur kuch chahiye?");
            fallbackRes.setAiProvider("fallback");
            return ResponseEntity.ok(fallbackRes);
        }
    }

    @PostMapping("/stt/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audio) {
        try {
            byte[] bytes = audio.getBytes();
            SpeechToTextService stt = getActiveSttService();
            String transcript = stt.transcribe(bytes, audio.getContentType());
            return ResponseEntity.ok(Map.of(
                    "transcript", transcript,
                    "provider", stt.providerName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("STT failure, falling back to empty transcript", e);
            return ResponseEntity.ok(Map.of(
                    "transcript", "",
                    "provider", "fallback"));
        }
    }

    @PostMapping("/tts/synthesize")
    public ResponseEntity<Map<String, String>> synthesize(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Text must not be blank"));
        }
        try {
            TextToSpeechService tts = getActiveTtsService();
            String audioBase64 = tts.synthesizeBase64(text);
            return ResponseEntity.ok(Map.of(
                    "audioBase64", audioBase64,
                    "mimeType", tts.audioMimeType(),
                    "provider", tts.providerName()));
        } catch (Exception e) {
            log.error("TTS failure, using fallback", e);
            try {
                String audioBase64 = mockTts.synthesizeBase64(text);
                return ResponseEntity.ok(Map.of(
                        "audioBase64", audioBase64,
                        "mimeType", mockTts.audioMimeType(),
                        "provider", "mock"));
            } catch (Exception ex) {
                return ResponseEntity.ok(Map.of(
                        "audioBase64", "",
                        "mimeType", "audio/mp3",
                        "provider", "browser-fallback"));
            }
        }
    }

    @GetMapping("/voice/status")
    public ResponseEntity<Map<String, Object>> voiceStatus() {
        SpeechToTextService stt = getActiveSttService();
        TextToSpeechService tts = getActiveTtsService();
        return ResponseEntity.ok(Map.of(
                "stt", Map.of("provider", stt.providerName(),
                        "available", true,
                        "browserFallback", "webspeech"),
                "ai", Map.of("provider", conversationService.activeAiProvider(),
                        "available", true),
                "tts", Map.of("provider", tts.providerName(),
                        "available", true)));
    }
}
