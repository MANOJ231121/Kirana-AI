package com.kirana.assistant.service.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fallback STT used when no provider key is configured.
 * Returns an empty transcript so the UI can fall back to the
 * browser Web Speech API or typed input — the app stays testable.
 */
@Service
public class MockSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(MockSpeechToTextService.class);

    @Override
    public String transcribe(byte[] audio, String contentType) {
        log.debug("Mock STT called with {} bytes ({})", audio == null ? 0 : audio.length, contentType);
        return "";
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
