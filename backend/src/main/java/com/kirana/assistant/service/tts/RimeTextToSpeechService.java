package com.kirana.assistant.service.tts;

import com.kirana.assistant.service.RimeTtsService;
import org.springframework.stereotype.Service;

/**
 * Rime-backed {@link TextToSpeechService} (MP3 for browser playback).
 */
@Service
public class RimeTextToSpeechService implements TextToSpeechService {

    private final RimeTtsService rime;

    public RimeTextToSpeechService(RimeTtsService rime) {
        this.rime = rime;
    }

    @Override
    public byte[] synthesize(String text) {
        if (!isAvailable()) {
            return new byte[0];
        }
        return rime.synthesizeSpeechMp3(text);
    }

    @Override
    public String providerName() {
        return "rime";
    }

    @Override
    public String audioMimeType() {
        return "audio/mpeg";
    }

    @Override
    public boolean isAvailable() {
        return rime.isAvailable();
    }
}
