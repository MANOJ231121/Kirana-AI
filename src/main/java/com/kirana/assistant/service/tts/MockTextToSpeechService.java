package com.kirana.assistant.service.tts;

import org.springframework.stereotype.Service;

/**
 * No-credential fallback: returns empty audio so the customer UI
 * shows the AI reply as text (and can use browser speechSynthesis).
 */
@Service
public class MockTextToSpeechService implements TextToSpeechService {

    @Override
    public byte[] synthesize(String text) {
        return new byte[0];
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String audioMimeType() {
        return "audio/mpeg";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
