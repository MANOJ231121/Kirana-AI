package com.kirana.assistant.service.stt;

/**
 * Speech-to-text abstraction. The browser captures microphone audio;
 * the backend transcribes it so API keys never reach React.
 */
public interface SpeechToTextService {

    /** Transcribe raw audio bytes. Returns plain transcript text. */
    String transcribe(byte[] audio, String contentType) throws Exception;

    String providerName();

    boolean isAvailable();
}
