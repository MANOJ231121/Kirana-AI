import { useCallback, useEffect, useRef, useState } from 'react';
import { transcribeAudio } from '../services/api.js';

/**
 * Microphone hook: prefers MediaRecorder upload to backend STT,
 * falls back to the browser Web Speech API when the backend has no key.
 */
export function useSpeech({ onTranscript } = {}) {
  const [listening, setListening] = useState(false);
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
    rec.start();
    setListening(true);
    return true;
  }, [onTranscript]);

  const start = useCallback(async () => {
    if (listening) {
      stop();
      return;
    }
    // Try backend STT via microphone capture first.
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
        if (!blob.size) return;
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
  }, [listening, onTranscript, startBrowserSpeech, stop]);

  useEffect(() => () => stop(), [stop]);

  return { listening, supported, start, stop };
}
