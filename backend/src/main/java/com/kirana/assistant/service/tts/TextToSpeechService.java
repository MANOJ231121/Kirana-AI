package com.kirana.assistant.service.tts;

/**
 * Text-to-speech abstraction. The LLM text goes through this to
 * produce browser-playable audio. Mock keeps the app testable
 * without TTS credentials.
 */
public interface TextToSpeechService {

    byte[] synthesize(String text) throws Exception;

    default String synthesizeBase64(String text) throws Exception {
        byte[] bytes = synthesize(text);
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    String providerName();

    /** Audio mime this provider returns (e.g. "audio/mpeg"). */
    String audioMimeType();

    boolean isAvailable();
}
