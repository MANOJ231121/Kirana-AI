package com.kirana.assistant.service;

import com.twilio.twiml.voice.Parameter;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Stream;
import com.twilio.twiml.voice.Connect;
import com.twilio.twiml.VoiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    /**
     * Generates TwiML that starts a bidirectional media stream to our WebSocket server.
     * This is the real-time voice pipeline where Rime speaks and Deepgram listens.
     */
    public String generateStreamingResponse(String fromNumber, String callSid, String publicBaseUrl) {
        log.info("Generating streaming response for caller: {}, base URL: {}", fromNumber, publicBaseUrl);

        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            log.warn("PUBLIC_BASE_URL not set, returning simple greeting");
            return generateErrorResponse();
        }

        String wsUrl = publicBaseUrl.replaceFirst("^http", "ws") + "/media-stream";

        Stream stream = new Stream.Builder()
                .url(wsUrl)
                .track(Stream.Track.BOTH_TRACKS)
                .parameter(new Parameter.Builder()
                        .name("from")
                        .value(fromNumber)
                        .build())
                .parameter(new Parameter.Builder()
                        .name("callSid")
                        .value(callSid)
                        .build())
                .build();

        Connect connect = new Connect.Builder()
                .stream(stream)
                .build();

        VoiceResponse response = new VoiceResponse.Builder()
                .connect(connect)
                .build();

        String twiml = response.toXml();
        log.debug("Generated streaming TwiML: {}", twiml);
        return twiml;
    }

    public String generateErrorResponse() {
        Say say = new Say.Builder("Sorry, kuch problem hua. Thoda der baad dobara try karein.")
                .voice(Say.Voice.POLLY_ADITI)
                .language(Say.Language.HI_IN)
                .build();

        VoiceResponse response = new VoiceResponse.Builder()
                .say(say)
                .build();

        return response.toXml();
    }
}
