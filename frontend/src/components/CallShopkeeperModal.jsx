import { useEffect, useRef, useState } from 'react';
import {
  createOrder,
  endCall,
  processCallSpeech,
  startCall,
  synthesizeSpeech,
  transcribeAudio,
  updateWebCallOrder,
} from '../services/api.js';
import { playBase64Audio, speakBrowser, stopAllAudio } from '../services/audio.js';
import { CheckIcon, ListIcon, MicIcon, MinusIcon, PlusIcon, SpeakerIcon, StoreIcon, XIcon } from './Icons.jsx';

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
 *
 * Audio safety: single-flight playback (stopAllAudio before every voice),
 * StrictMode-guarded single session start, and all audio killed on hang-up.
 */
export default function CallShopkeeperModal({ customerName, address, phone, pickupTime, onClose, onOrderPlaced }) {
  const [phase, setPhase] = useState('starting'); // starting | live | verifying | done
  const [callSid, setCallSid] = useState('');
  const [turns, setTurns] = useState([]);
  const [items, setItems] = useState([]);
  const [listening, setListening] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [verifyText, setVerifyText] = useState('');
  const [details, setDetails] = useState({ customerName: customerName || '', address: address || '' });
  const recRef = useRef(null);
  const streamRef = useRef(null);
  const liveRef = useRef(true);
  const sidRef = useRef('');

  useEffect(() => {
    // StrictMode-safe: each mount gets its own cancellation flag, so a
    // remount always starts a fresh session instead of hanging on
    // "Connecting…". The abandoned first session is ended best-effort.
    let cancelled = false;
    liveRef.current = true;
    (async () => {
      try {
        const s = await startCall(phone);
        if (cancelled || !liveRef.current) {
          try {
            if (s?.callSid) await endCall({ callSid: s.callSid, phoneNumber: phone });
          } catch { /* orphan cleanup */ }
          return;
        }
        sidRef.current = s.callSid;
        setCallSid(s.callSid);
        setTurns([{ from: 'ai', text: s.greeting }]);
        playAiAudio(s.audioBase64, 'audio/mpeg', s.greeting);
        setPhase('live');
      } catch (e) {
        if (!cancelled && liveRef.current) {
          setError(e.message || 'Could not start the call. Is the backend running on port 8080?');
          setPhase('live');
        }
      }
    })();
    return () => {
      cancelled = true;
      liveRef.current = false;
      stopAllAudio();
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
      // Sync voice-captured name/address into the customer-details section.
      setDetails((d) => ({
        customerName: r.nameKnown && r.customerName ? r.customerName : d.customerName,
        address: r.address ? r.address : d.address,
      }));
      playAiAudio(r.audioBase64, 'audio/mpeg', r.aiResponse);
    } catch (e) {
      if (liveRef.current) setError(e.message);
    } finally {
      if (liveRef.current) setBusy(false);
    }
  };

  const listenOnce = async () => {
    if (listening) return;
    stopAllAudio();

    // 1) Web Speech API streams live and auto-ends on silence — best UX
    //    where Chrome/Edge is available (which is where the whole app runs).
    if (listenBrowser()) return;

    // 2) Otherwise record a short clip and send it to the backend STT.
    try {
      setError('');
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
          else setError('No speech detected. Tap the mic and speak again.');
        } catch (e) {
          setError(e.message || 'Speech recognition unavailable on this browser.');
        }
      };
      recRef.current = rec;
      rec.start();
      setListening(true);
      setTimeout(() => {
        if (rec.state !== 'inactive') rec.stop();
      }, 8000);
    } catch {
      setError('Microphone unavailable — type your reply below.');
    }
  };

  const listenBrowser = () => {
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Ctor) {
      return false;
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
      return true;
    } catch {
      setListening(false);
      return false;
    }
  };

  const fmtQty = (q) => (Number.isInteger(q) ? String(q) : String(Number(q)));

  const updateItem = async (item, action, quantity) => {
    if (busy || !liveRef.current) return;
    setBusy(true);
    setError('');
    try {
      const r = await updateWebCallOrder({
        phoneNumber: phone,
        itemName: item.name,
        action,
        quantity,
      });
      if (!liveRef.current) return;
      if (Array.isArray(r.items)) setItems(r.items);
    } catch (e) {
      if (liveRef.current) setError(e.message || 'Could not update the item.');
    } finally {
      if (liveRef.current) setBusy(false);
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
    const name = details.customerName?.trim() || customerName?.trim() || '';
    if (!name) {
      setError('Add your name on the storefront first.');
      return;
    }
    const address = details.address?.trim() || '';
    if (!address) {
      setError('Tell me your delivery address on the call, or type it below.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const order = await createOrder({
        customerName: name,
        customerPhone: phone?.trim() || undefined,
        items: items.map((i) => ({ name: i.name, quantity: i.quantity, unit: i.unit || 'pc' })),
        pickupTime,
        address,
      });
      try {
        await endCall({ callSid, phoneNumber: phone });
      } catch { /* noop */ }
      stopAllAudio();
      speakBrowser('Order confirm ho gaya. Dhanyavaad!');
      setPhase('done');
      onOrderPlaced?.(order);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const hangUp = async () => {
    stopAllAudio();
    try {
      if (callSid) await endCall({ callSid, phoneNumber: phone });
    } catch { /* noop */ }
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={hangUp}>
      <div className="call-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Call shopkeeper">
        <div className="call-header">
          <span className="call-avatar">
            <StoreIcon size={22} />
          </span>
          <div className="call-id">
            <b>Sharma Kirana Store</b>
            <span className={`call-status ${phase === 'live' ? 'live' : ''}`}>
              {phase === 'starting' ? 'Connecting…' : phase === 'done' ? 'Order sent' : '● Live call'}
            </span>
          </div>
          <button className="call-hangup" onClick={hangUp} aria-label="Hang up">
            <XIcon size={16} />
          </button>
        </div>

        {phase === 'starting' && <p className="empty">Connecting your call…</p>}
        {error && <div className="error-banner">{error}</div>}

        <div className="chat-box call-chat">
          {turns.map((t, i) => (
            <p key={i} className={`chat-bubble ${t.from}`}>
              <b>{t.from === 'you' ? 'You: ' : 'AI: '}</b>{t.text}
            </p>
          ))}
        </div>

        <section className="call-list call-details">
          <h3>Customer details</h3>
          <div className="detail-grid">
            <label className="detail-field">
              <span>Name</span>
              <input
                value={details.customerName}
                placeholder="Your name"
                autoComplete="off"
                onChange={(e) => setDetails((d) => ({ ...d, customerName: e.target.value }))}
              />
            </label>
            <label className="detail-field">
              <span>Delivery address</span>
              <input
                value={details.address}
                placeholder="e.g. Shastri Nagar, Gali no. 2"
                autoComplete="off"
                onChange={(e) => setDetails((d) => ({ ...d, address: e.target.value }))}
              />
            </label>
          </div>
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Just speak it — “mera naam Rahul hai” and “pata Shastri Nagar” are captured automatically.
          </p>
        </section>

        <section className="call-list">
          <h3><ListIcon size={16} /> Live grocery list</h3>
          {items.length === 0 ? (
            <p className="empty">Nothing yet — say e.g. “1 kg Atta and 2 kg Chini”.</p>
          ) : (
            <ul>
              {items.map((i, idx) => (
                <li key={idx} className="call-item">
                  <span className="call-item-name">{i.name}</span>
                  <div className="call-item-qty">
                    <button
                      className="qty-btn call-qty-btn"
                      onClick={() => updateItem(i, 'SET_QTY', (i.quantity || 1) - 1)}
                      disabled={busy || (i.quantity || 1) <= 1}
                      aria-label={`Decrease ${i.name}`}
                    >
                      <MinusIcon size={13} />
                    </button>
                    <b className="call-qty-text">{fmtQty(i.quantity)} {i.unit}</b>
                    <button
                      className="qty-btn call-qty-btn"
                      onClick={() => updateItem(i, 'SET_QTY', (i.quantity || 1) + 1)}
                      disabled={busy}
                      aria-label={`Increase ${i.name}`}
                    >
                      <PlusIcon size={13} />
                    </button>
                  </div>
                  <button
                    className="call-item-remove"
                    onClick={() => updateItem(i, 'REMOVE')}
                    disabled={busy}
                    aria-label={`Remove ${i.name}`}
                  >
                    <XIcon size={14} />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Use + / − to fix a quantity, tap × to remove an item — or just say “No no, 2 kg Atta instead”.
          </p>
        </section>

        {phase === 'verifying' && (
          <div className="toast-banner">{verifyText}</div>
        )}

        <form
          className="typed-form"
          onSubmit={(e) => {
            e.preventDefault();
            const v = e.target.elements.typed.value;
            e.target.reset();
            sendTranscript(v);
          }}
        >
          <input name="typed" placeholder="Type your reply…" autoComplete="off" />
          <button className="btn-primary" style={{ width: 'auto' }} type="submit" disabled={busy}>Send</button>
        </form>

        <div className="call-actions">
          <button className={`mic-btn ${listening ? 'live' : ''}`} onClick={listenOnce} disabled={busy} aria-label="Speak">
            <MicIcon size={24} />
          </button>
          {phase !== 'done' ? (
            <>
              <button className="action-btn verify" onClick={verifyList} disabled={busy || !items.length}>
                <SpeakerIcon size={15} /> Verify my list
              </button>
              <button
                className="btn-primary"
                style={{ flex: 1 }}
                onClick={confirmOrder}
                disabled={busy || !items.length || phase !== 'verifying'}
              >
                <CheckIcon size={16} /> Confirm & send
              </button>
            </>
          ) : (
            <div className="toast-banner" style={{ flex: 1 }}>Order sent — track it in your basket!</div>
          )}
        </div>
      </div>
    </div>
  );
}
