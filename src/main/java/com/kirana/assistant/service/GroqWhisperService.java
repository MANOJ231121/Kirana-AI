package com.kirana.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class GroqWhisperService {

    private static final Logger log = LoggerFactory.getLogger(GroqWhisperService.class);

    private static final String GROQ_AUDIO_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final double SILENCE_RMS_THRESHOLD = 600.0;
    private static final int SILENCE_CHUNKS_TRIGGER = 25; // ~500ms of silence after speech
    private static final int MAX_BUFFER_BYTES = 48000;    // ~6 seconds max speech buffer

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    @Value("${GROQ_WHISPER_MODEL:whisper-large-v3-turbo}")
    private String whisperModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, AudioStreamSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public GroqWhisperService(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = objectMapper;
    }

    private static class AudioStreamSession {
        final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        boolean isSpeaking = false;
        int silentChunkCount = 0;
        Consumer<String> onFinal;
    }

    /**
     * Process an incoming audio chunk (base64 mu-law 8kHz) from Twilio Media Streams.
     */
    public void processAudioChunk(String callSid, String base64Chunk, Consumer<String> onFinal) {
        AudioStreamSession session = sessions.computeIfAbsent(callSid, k -> {
            AudioStreamSession s = new AudioStreamSession();
            s.onFinal = onFinal;
            return s;
        });
        session.onFinal = onFinal;

        byte[] muLawBytes = Base64.getDecoder().decode(base64Chunk);
        double rms = calculateRms(muLawBytes);

        synchronized (session) {
            if (rms > SILENCE_RMS_THRESHOLD) {
                session.audioBuffer.write(muLawBytes, 0, muLawBytes.length);
                session.isSpeaking = true;
                session.silentChunkCount = 0;
            } else if (session.isSpeaking) {
                session.audioBuffer.write(muLawBytes, 0, muLawBytes.length);
                session.silentChunkCount++;

                if (session.silentChunkCount >= SILENCE_CHUNKS_TRIGGER
                        || session.audioBuffer.size() >= MAX_BUFFER_BYTES) {
                    byte[] accumulatedMuLaw = session.audioBuffer.toByteArray();
                    session.audioBuffer.reset();
                    session.isSpeaking = false;
                    session.silentChunkCount = 0;

                    if (accumulatedMuLaw.length >= 4000) { // At least 0.5s of speech
                        executor.submit(() -> transcribeSpeech(callSid, accumulatedMuLaw, session.onFinal));
                    }
                }
            }
        }
    }

    /**
     * Transcribes accumulated speech using Groq Whisper API.
     */
    public void transcribeSpeech(String callSid, byte[] muLawBytes, Consumer<String> onFinal) {
        try {
            byte[] wavBytes = createWavFile(muLawBytes);
            log.info("Sending {} bytes of speech audio to Groq Whisper for call {}", wavBytes.length, callSid);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            ByteArrayResource audioResource = new ByteArrayResource(wavBytes) {
                @Override
                public String getFilename() {
                    return "speech.wav";
                }
            };

            builder.part("file", audioResource, MediaType.parseMediaType("audio/wav"));
            builder.part("model", whisperModel);
            builder.part("language", "hi");
            builder.part("response_format", "json");

            String jsonResponse = webClient.post()
                    .uri(GROQ_AUDIO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (jsonResponse != null) {
                JsonNode root = objectMapper.readTree(jsonResponse);
                String transcript = root.path("text").asText().trim();
                log.info("Groq Whisper transcript for call {}: {}", callSid, transcript);

                if (!transcript.isBlank() && onFinal != null) {
                    onFinal.accept(transcript);
                }
            }
        } catch (Exception e) {
            log.error("Groq Whisper transcription failed for call {}", callSid, e);
        }
    }

    /**
     * Cleans up stream session resources.
     */
    public void closeSession(String callSid) {
        AudioStreamSession session = sessions.remove(callSid);
        if (session != null) {
            synchronized (session) {
                session.audioBuffer.reset();
            }
        }
        log.info("Closed Groq Whisper audio session for call {}", callSid);
    }

    /**
     * Calculate RMS (volume level) of G.711 mu-law audio bytes.
     */
    private double calculateRms(byte[] muLawBytes) {
        double sum = 0;
        for (byte b : muLawBytes) {
            short pcm = mulawToPcm(b);
            sum += pcm * pcm;
        }
        return Math.sqrt(sum / muLawBytes.length);
    }

    /**
     * Decodes G.711 mu-law byte to 16-bit linear PCM sample.
     */
    private static short mulawToPcm(byte mulawByte) {
        int ulaw = (~mulawByte) & 0xFF;
        int sign = (ulaw & 0x80);
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int sample = ((mantissa << 3) + 0x84) << exponent;
        sample -= 0x84;
        return (short) (sign != 0 ? -sample : sample);
    }

    /**
     * Wraps G.711 mu-law audio bytes into 16-bit 8kHz PCM WAV file byte array.
     */
    private static byte[] createWavFile(byte[] mulawBytes) {
        int sampleCount = mulawBytes.length;
        byte[] pcmData = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            short pcm = mulawToPcm(mulawBytes[i]);
            pcmData[i * 2] = (byte) (pcm & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
        }

        int totalDataLen = pcmData.length;
        int totalAudioLen = totalDataLen + 36;
        int sampleRate = 8000;
        int channels = 1;
        int byteRate = sampleRate * channels * 2;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalAudioLen & 0xff);
        header[5] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[6] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[7] = (byte) ((totalAudioLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 2); header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalDataLen & 0xff);
        header[41] = (byte) ((totalDataLen >> 8) & 0xff);
        header[42] = (byte) ((totalDataLen >> 16) & 0xff);
        header[43] = (byte) ((totalDataLen >> 24) & 0xff);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header, 0, 44);
        out.write(pcmData, 0, pcmData.length);
        return out.toByteArray();
    }
}
