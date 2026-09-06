import { useCallback, useEffect, useRef, useState } from 'react';

export const DEFAULT_WAKE_WORDS = ['kirana', 'siri'];

/**
 * Always-on wake-word listener ("Kirana…", "Siri…").
 * Passive mode scans everything; on wake it captures the command that
 * follows (same sentence or the next one) and hands it to onCommand.
 *
 * Chrome-only (Web Speech API). The push-to-talk mic + typed box in the
 * UI remain as fallbacks everywhere else.
 */
export function useWakeWord({ enabled, onCommand, wakeWords = DEFAULT_WAKE_WORDS } = {}) {
  const [supported] = useState(
    () =>
      typeof window !== 'undefined' &&
      (window.SpeechRecognition !== undefined || window.webkitSpeechRecognition !== undefined),
  );
  const [awakened, setAwakened] = useState(false);
  const [heard, setHeard] = useState('');
  const recRef = useRef(null);
  const stateRef = useRef({ enabled, onCommand, wakeWords, awaitingCommand: false, timer: null });
  stateRef.current = {
    enabled,
    onCommand,
    wakeWords,
    awaitingCommand: stateRef.current.awaitingCommand,
    timer: stateRef.current.timer,
  };

  const stopPassive = useCallback(() => {
    try {
      recRef.current?.abort?.();
    } catch { /* noop */ }
    recRef.current = null;
    clearTimeout(stateRef.current.timer);
    stateRef.current.awaitingCommand = false;
    setAwakened(false);
  }, []);

  useEffect(() => {
    if (!enabled || !supported) {
      stopPassive();
      return undefined;
    }
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
    const rec = new Ctor();
    rec.lang = 'hi-IN';
    rec.continuous = true;
    rec.interimResults = true;
    rec.maxAlternatives = 1;

    const words = () => stateRef.current.wakeWords.map((w) => w.toLowerCase());

    rec.onresult = (e) => {
      let finalText = '';
      for (let i = e.resultIndex; i < e.results.length; i += 1) {
        const r = e.results[i];
        if (r.isFinal) finalText += `${r[0].transcript} `;
      }
      if (!finalText.trim()) return;
      const lower = finalText.toLowerCase();
      setHeard(finalText.trim());

      if (stateRef.current.awaitingCommand) {
        // The sentence after the wake word is the command.
        stateRef.current.awaitingCommand = false;
        clearTimeout(stateRef.current.timer);
        setAwakened(false);
        stateRef.current.onCommand?.(finalText.trim(), 'wakeword');
        return;
      }

      const hit = words().find((w) => lower.includes(w));
      if (hit) {
        const after = lower.split(hit)[1]?.replace(/^[,\s:—-]+/, '').trim();
        setAwakened(true);
        if (after) {
          setAwakened(false);
          stateRef.current.onCommand?.(after, 'wakeword');
        } else {
          // Wake word alone — grab the next sentence as the command.
          stateRef.current.awaitingCommand = true;
          clearTimeout(stateRef.current.timer);
          stateRef.current.timer = setTimeout(() => {
            stateRef.current.awaitingCommand = false;
            setAwakened(false);
          }, 9000);
        }
      }
    };

    rec.onend = () => {
      // Keep passive listening alive until toggled off.
      if (stateRef.current.enabled && recRef.current === rec) {
        try {
          rec.start();
        } catch { /* noop */ }
      }
    };
    rec.onerror = (e) => {
      if (e?.error === 'not-allowed') stateRef.current.enabled = false;
    };

    recRef.current = rec;
    try {
      rec.start();
    } catch { /* noop */ }

    return () => {
      if (recRef.current === rec) {
        try {
          rec.abort();
        } catch { /* noop */ }
        recRef.current = null;
      }
      clearTimeout(stateRef.current.timer);
    };
  }, [enabled, supported, stopPassive]);

  return { supported, awakened, heard, stopPassive };
}
