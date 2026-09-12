import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchVoiceStatus, transcribeAudio } from '../services/api.js';

/**
 * Microphone hook: prefers backend STT when a real provider key is
 * configured (Groq Whisper / Deepgram), otherwise uses the browser
 * Web Speech API directly for an instant start/stop experience.
 */
export function useSpeech({ onTranscript } = {}) {
  const [listening, setListening] = useState(false);
  const [backendSttReady, setBackendSttReady] = useState(false);
  const [supported] = useState(
    () =>
      typeof window !== 'undefined' &&
      (window.MediaRecorder !== undefined ||
        window.webkitSpeechRecognition !== undefined ||
        window.SpeechRecognition !== undefined),
  );
  const recRef = useRef(null);
  const mediaRef = useRef(null);
  const chunksRef = useRef([]);

  // Ask the backend which STT provider is live. When Groq/Deepgram are
  // unavailable we skip the fixed 8s clip upload and use Web Speech.
  useEffect(() => {
    let mounted = true;
    fetchVoiceStatus().then((s) => {
      if (!mounted) return;
      const p = s?.stt?.provider;
      const avail = s?.stt?.available;
      const real = p && p === 'groq-whisper';
      setBackendSttReady(Boolean(real && avail !== false));
    });
    return () => {
      mounted = false;
    };
  }, []);

  const stop = useCallback(() => {
    setListening(false);
    try {
      recRef.current?.stop();
    } catch { /* noop */ }
    try {
      recRef.current?.abort?.();
    } catch { /* noop */ }
    mediaRef.current?.getTracks?.().forEach((t) => t.stop());
    recRef.current = null;
  }, []);

  const warn = useCallback((msg) => {
    if (typeof console !== 'undefined') console.warn(msg);
  }, []);

  const startBrowserSpeech = useCallback(() => {
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Ctor) return false;
    const rec = new Ctor();
    rec.lang = 'hi-IN';
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    rec.onresult = (e) => {
      const text = e.results?.[0]?.[0]?.transcript || '';
      if (text) onTranscript?.(text, 'webspeech');
    };
    rec.onend = () => setListening(false);
    rec.onerror = () => setListening(false);
    recRef.current = rec;
    setListening(true);
    try {
      rec.start();
      return true;
    } catch {
      setListening(false);
      return false;
    }
  }, [onTranscript]);

  const start = useCallback(async () => {
    if (listening) {
      stop();
      return;
    }

    // Browser Web Speech is instant and ends when the user stops talking —
    // prefer it when there is no real backend provider key configured.
    if (!backendSttReady) {
      if (startBrowserSpeech()) return;
      warn('Speech recognition is not available in this browser.');
      return;
    }

    // Real provider key present → capture a short clip and upload it.
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaRef.current = stream;
      const rec = new MediaRecorder(stream);
      chunksRef.current = [];
      rec.ondataavailable = (e) => {
        if (e.data?.size) chunksRef.current.push(e.data);
      };
      rec.onstop = async () => {
        setListening(false);
        const blob = new Blob(chunksRef.current, { type: rec.mimeType || 'audio/webm' });
        stream.getTracks().forEach((t) => t.stop());
        if (!blob.size) {
          startBrowserSpeech();
          return;
        }
        try {
          const data = await transcribeAudio(blob);
          if (data?.transcript) onTranscript?.(data.transcript, data.provider || 'backend');
          else startBrowserSpeech();
        } catch {
          startBrowserSpeech();
        }
      };
      recRef.current = rec;
      rec.start();
      setListening(true);
      // Auto-stop after 8s so clips stay small.
      setTimeout(() => {
        if (rec.state !== 'inactive') rec.stop();
      }, 8000);
      return;
    } catch {
      startBrowserSpeech();
    }
  }, [listening, backendSttReady, onTranscript, startBrowserSpeech, stop, warn]);

  useEffect(() => () => stop(), [stop]);

  return { listening, supported, start, stop };
}
