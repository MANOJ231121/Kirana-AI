import { useEffect, useRef, useState } from 'react';
import {
  createOrder,
  endCall,
  processCallSpeech,
  startCall,
  synthesizeSpeech,
  transcribeAudio,
} from '../services/api.js';
import { playBase64Audio, speakBrowser } from '../services/audio.js';

function playAiAudio(base64, mime, fallbackText) {
  if (base64) {
    try {
      if (playBase64Audio(base64, mime || 'audio/mpeg')) return;
    } catch { /* fall through */ }
  }
  speakBrowser(fallbackText || '');
}

/**
 * "Call Shopkeeper" AI phone-agent modal.
 * Live call session: greeting → voice turns (with real-time quantity
 * corrections) → spoken list verification → confirm pushes the order
 * live to the shopkeeper portal.
 */
export default function CallShopkeeperModal({ customerName, phone, pickupTime, onClose, onOrderPlaced }) {
  const [phase, setPhase] = useState('starting'); // starting | live | verifying | done
  const [callSid, setCallSid] = useState('');
  const [turns, setTurns] = useState([]);
  const [items, setItems] = useState([]);
  const [listening, setListening] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [verifyText, setVerifyText] = useState('');
  const recRef = useRef(null);
  const streamRef = useRef(null);
  const liveRef = useRef(true);

  useEffect(() => {
    liveRef.current = true;
    (async () => {
      try {
        const s = await startCall(phone);
        if (!liveRef.current) return;
        setCallSid(s.callSid);
        setTurns([{ from: 'ai', text: s.greeting }]);
        playAiAudio(s.audioBase64, 'audio/mpeg', s.greeting);
        setPhase('live');
      } catch (e) {
        if (liveRef.current) {
          setError(e.message);
          setPhase('live');
        }
      }
    })();
    return () => {
      liveRef.current = false;
      try {
        recRef.current?.abort?.();
      } catch { /* noop */ }
      streamRef.current?.getTracks?.().forEach((t) => t.stop());
    };
  }, [phone]);

  const sendTranscript = async (transcript) => {
    if (!transcript?.trim() || busy || !liveRef.current) return;
    setBusy(true);
    setError('');
    setTurns((t) => [...t, { from: 'you', text: transcript }]);
    try {
      const r = await processCallSpeech({ callSid, transcript, phoneNumber: phone });
      if (!liveRef.current) return;
      setTurns((t) => [...t, { from: 'ai', text: r.aiResponse }]);
      if (Array.isArray(r.items)) setItems(r.items);
      playAiAudio(r.audioBase64, 'audio/mpeg', r.aiResponse);
    } catch (e) {
      if (liveRef.current) setError(e.message);
    } finally {
      if (liveRef.current) setBusy(false);
    }
  };

  const listenOnce = async () => {
    if (listening) return;
    // Prefer backend STT via a short mic clip, fall back to Web Speech.
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const rec = new MediaRecorder(stream);
      const chunks = [];
      rec.ondataavailable = (e) => {
        if (e.data?.size) chunks.push(e.data);
      };
      rec.onstop = async () => {
        setListening(false);
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: rec.mimeType || 'audio/webm' });
        if (!blob.size) return;
        try {
          const d = await transcribeAudio(blob);
          if (d?.transcript) sendTranscript(d.transcript);
          else listenBrowser();
        } catch {
          listenBrowser();
        }
      };
      recRef.current = rec;
      rec.start();
      setListening(true);
      setTimeout(() => {
        if (rec.state !== 'inactive') rec.stop();
      }, 8000);
    } catch {
      listenBrowser();
    }
  };

  const listenBrowser = () => {
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Ctor) {
      setError('Microphone unavailable — type your reply below.');
      return;
    }
    const rec = new Ctor();
    rec.lang = 'hi-IN';
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    rec.onresult = (e) => {
      const text = e.results?.[0]?.[0]?.transcript || '';
      if (text) sendTranscript(text);
    };
    rec.onend = () => setListening(false);
    rec.onerror = () => setListening(false);
    recRef.current = rec;
    setListening(true);
    try {
      rec.start();
    } catch {
      setListening(false);
    }
  };

  const verifyList = async () => {
    if (!items.length) {
      setError('List is empty — speak your items first.');
      return;
    }
    const spoken = items.map((i) => `${i.quantity} ${i.unit || ''} ${i.name}`.trim()).join(', ');
    const text = `Your list: ${spoken}. Shall I confirm this order?`;
    setVerifyText(text);
    setPhase('verifying');
    try {
      const tts = await synthesizeSpeech(text);
      if (tts?.audioBase64) playBase64Audio(tts.audioBase64, tts.mimeType);
      else speakBrowser(text);
    } catch {
      speakBrowser(text);
    }
  };

  const confirmOrder = async () => {
    if (!customerName?.trim()) {
      setError('Add your name on the storefront first.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const order = await createOrder({
        customerName: customerName.trim(),
        customerPhone: phone?.trim() || undefined,
        items: items.map((i) => ({ name: i.name, quantity: i.quantity, unit: i.unit || 'pc' })),
        pickupTime,
      });
      try {
        await endCall({ callSid, phoneNumber: phone });
      } catch { /* noop */ }
      setPhase('done');
      onOrderPlaced?.(order);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const hangUp = async () => {
    try {
      if (callSid) await endCall({ callSid, phoneNumber: phone });
    } catch { /* noop */ }
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={hangUp}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Call shopkeeper">
        <header className="modal-head">
          <b>📞 Calling Sharma Kirana Store…</b>
          <span className={`call-dot ${phase === 'live' ? 'on' : ''}`} />
          <button className="pill" onClick={hangUp}>Hang up ✕</button>
        </header>

        {phase === 'starting' && <p className="empty">Connecting your call…</p>}
        {error && <p className="error">{error}</p>}

        <div className="chat call-chat">
          {turns.map((t, i) => (
            <p key={i} className={`bubble ${t.from}`}>
              <b>{t.from === 'you' ? 'You: ' : 'AI: '}</b>{t.text}
            </p>
          ))}
        </div>

        <section className="verify-box">
          <h3>🧾 Live grocery list</h3>
          {items.length === 0 ? (
            <p className="empty">Nothing yet — say e.g. “1 kg Atta and 2 kg Chini”.</p>
          ) : (
            <ul>
              {items.map((i, idx) => (
                <li key={idx}>{i.name} <em>× {i.quantity} {i.unit}</em></li>
              ))}
            </ul>
          )}
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Corrections work live — say “No no, 2 kg Atta instead”.
          </p>
        </section>

        {phase === 'verifying' && (
          <p className="toast">🔊 {verifyText}</p>
        )}

        <form
          className="typed"
          onSubmit={(e) => {
            e.preventDefault();
            const v = e.target.elements.typed.value;
            e.target.reset();
            sendTranscript(v);
          }}
        >
          <input name="typed" placeholder="Type your reply…" autoComplete="off" />
          <button className="btn primary" style={{ width: 'auto' }} type="submit" disabled={busy}>Send</button>
        </form>

        <div className="actions" style={{ marginTop: 12 }}>
          <button className={`mic small ${listening ? 'live' : ''}`} onClick={listenOnce} disabled={busy} aria-label="Speak">
            🎤
          </button>
          {phase !== 'done' ? (
            <>
              <button className="btn ghost" onClick={verifyList} disabled={busy || !items.length}>
                🔊 Verify my list
              </button>
              <button className="btn primary" onClick={confirmOrder} disabled={busy || !items.length || phase !== 'verifying'}>
                ✅ Confirm & send to shopkeeper
              </button>
            </>
          ) : (
            <p className="toast">🎉 Order sent to the shopkeeper — track it in your basket!</p>
          )}
        </div>
      </div>
    </div>
  );
}
