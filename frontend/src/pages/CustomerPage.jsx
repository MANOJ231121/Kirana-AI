import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createOrder,
  fetchMenu,
  fetchOrder,
  sendConversation,
  synthesizeSpeech,
} from '../services/api.js';
import { playBase64Audio, speakBrowser } from '../services/audio.js';
import { useSpeech } from '../hooks/useSpeech.js';
import { useWakeWord } from '../hooks/useWakeWord.js';
import { useOrdersSocket } from '../websocket/useOrdersSocket.js';
import CallShopkeeperModal from '../components/CallShopkeeperModal.jsx';
import {
  CartIcon,
  CheckIcon,
  MicIcon,
  MinusIcon,
  PackageIcon,
  PhoneIcon,
  PlusIcon,
  SearchIcon,
  SpeakerIcon,
  StoreIcon,
  XIcon,
} from '../components/Icons.jsx';

const PRODUCT_IMAGES = {
  atta: 'https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=400&auto=format&fit=crop&q=80',
  rice: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=400&auto=format&fit=crop&q=80',
  dal: 'https://images.unsplash.com/photo-1546833999-b9f581a1996d?w=400&auto=format&fit=crop&q=80',
  milk: 'https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&auto=format&fit=crop&q=80',
  ghee: 'https://images.unsplash.com/photo-1631451095765-2c91616fc9e6?w=400&auto=format&fit=crop&q=80',
  sugar: 'https://images.unsplash.com/photo-1622484210800-8851b576f9d2?w=400&auto=format&fit=crop&q=80',
  salt: 'https://images.unsplash.com/photo-1615485290382-441e4d049cb5?w=400&auto=format&fit=crop&q=80',
  tea: 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=400&auto=format&fit=crop&q=80',
  maggi: 'https://images.unsplash.com/photo-1612927601601-6638404737ce?w=400&auto=format&fit=crop&q=80',
  biscuit: 'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?w=400&auto=format&fit=crop&q=80',
  egg: 'https://images.unsplash.com/photo-1506976785307-8732e854ad03?w=400&auto=format&fit=crop&q=80',
  bread: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80',
  oil: 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=400&auto=format&fit=crop&q=80',
  onion: 'https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?w=400&auto=format&fit=crop&q=80',
  potato: 'https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400&auto=format&fit=crop&q=80',
  tomato: 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400&auto=format&fit=crop&q=80',
  shampoo: 'https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?w=400&auto=format&fit=crop&q=80',
  detergent: 'https://images.unsplash.com/photo-1585842378054-ee2e52f94ba2?w=400&auto=format&fit=crop&q=80',
};

const DEFAULT_MENU = [
  { id: '1', name: 'Atta', price: 55, category: 'Grains', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.atta },
  { id: '2', name: 'Rice', price: 70, category: 'Grains', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.rice },
  { id: '3', name: 'Dal', price: 95, category: 'Grains', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.dal },
  { id: '4', name: 'Milk', price: 60, category: 'Dairy', unit: 'litre', available: true, imageUrl: PRODUCT_IMAGES.milk },
  { id: '5', name: 'Ghee', price: 620, category: 'Dairy', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.ghee },
  { id: '6', name: 'Sugar', price: 45, category: 'Staples', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.sugar },
  { id: '7', name: 'Salt', price: 20, category: 'Staples', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.salt },
  { id: '8', name: 'Tea', price: 180, category: 'Beverages', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.tea },
  { id: '9', name: 'Maggi', price: 14, category: 'Snacks', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.maggi },
  { id: '10', name: 'Biscuit', price: 30, category: 'Snacks', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.biscuit },
  { id: '11', name: 'Eggs', price: 6.5, category: 'Dairy', unit: 'pc', available: true, imageUrl: PRODUCT_IMAGES.egg },
  { id: '12', name: 'Bread', price: 32, category: 'Bakery', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.bread },
  { id: '13', name: 'Oil', price: 130, category: 'Staples', unit: 'litre', available: true, imageUrl: PRODUCT_IMAGES.oil },
  { id: '14', name: 'Onion', price: 40, category: 'Vegetables', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.onion },
  { id: '15', name: 'Potato', price: 25, category: 'Vegetables', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.potato },
  { id: '16', name: 'Tomato', price: 35, category: 'Vegetables', unit: 'kg', available: true, imageUrl: PRODUCT_IMAGES.tomato },
  { id: '17', name: 'Shampoo', price: 99, category: 'Personal Care', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.shampoo },
  { id: '18', name: 'Detergent', price: 150, category: 'Household', unit: 'packet', available: true, imageUrl: PRODUCT_IMAGES.detergent },
];

function imageFor(item) {
  if (item?.imageUrl) return item.imageUrl;
  const n = (item?.name || '').toLowerCase();
  for (const [k, v] of Object.entries(PRODUCT_IMAGES)) {
    if (n.includes(k)) return v;
  }
  return null;
}

function mergeItems(cart, incoming) {
  const next = [...cart];
  for (const it of incoming || []) {
    const idx = next.findIndex((c) => c.name.toLowerCase() === it.name.toLowerCase());
    if (idx >= 0) {
      next[idx] = { ...next[idx], quantity: next[idx].quantity + it.quantity, unit: it.unit || next[idx].unit };
    } else {
      next.push({ name: it.name, quantity: it.quantity, unit: it.unit || 'pc' });
    }
  }
  return next;
}

// Local smart parser for voice commands like "1k ataa dalna list mai", "siri 2 packet milk", "add maggi"
function parseLocalVoiceCommand(text = '') {
  const lower = text.toLowerCase().trim();
  const result = { intent: 'ADD_ITEM', items: [], replyText: '' };

  // Remove wake word prefixes if present
  const cleanText = lower.replace(/^(hey siri|siri|kirana|hey kirana|alexa)[,\s:-]*/i, '').trim();

  if (!cleanText) return null;

  // Check confirm intent
  if (cleanText.match(/\b(confirm|order confirm|kar do|haan|ok|theek hai)\b/)) {
    result.intent = 'CONFIRM_ORDER';
    result.replyText = 'Order confirm ho gaya hai! Shopkeeper ko notification bhej diya hai. Dhanyavaad!';
    return result;
  }

  // Check cancel intent
  if (cleanText.match(/\b(cancel|mat karo|hata do|rehne do)\b/)) {
    result.intent = 'CANCEL_ORDER';
    result.replyText = 'Aapka cart khaali kar diya gaya hai.';
    return result;
  }

  // Item match dictionary
  const itemsDict = [
    { name: 'Atta', keywords: ['atta', 'ataa', 'aata', 'wheat flour'], unit: 'kg' },
    { name: 'Rice', keywords: ['rice', 'chawal', 'chawl'], unit: 'kg' },
    { name: 'Dal', keywords: ['dal', 'daal', 'lentil'], unit: 'kg' },
    { name: 'Milk', keywords: ['milk', 'doodh', 'dudh', 'amul milk'], unit: 'litre' },
    { name: 'Ghee', keywords: ['ghee', 'ghi'], unit: 'kg' },
    { name: 'Sugar', keywords: ['sugar', 'cheeni', 'chini'], unit: 'kg' },
    { name: 'Salt', keywords: ['salt', 'namak'], unit: 'kg' },
    { name: 'Tea', keywords: ['tea', 'chai', 'chaye'], unit: 'packet' },
    { name: 'Maggi', keywords: ['maggi', 'maggie', 'noodles'], unit: 'packet' },
    { name: 'Biscuit', keywords: ['biscuit', 'biscuits'], unit: 'packet' },
    { name: 'Eggs', keywords: ['egg', 'eggs', 'anda', 'ande'], unit: 'pc' },
    { name: 'Bread', keywords: ['bread', 'pav'], unit: 'packet' },
    { name: 'Oil', keywords: ['oil', 'tel'], unit: 'litre' },
    { name: 'Onion', keywords: ['onion', 'pyaz', 'pyaj'], unit: 'kg' },
    { name: 'Potato', keywords: ['potato', 'aaloo', 'aloo'], unit: 'kg' },
    { name: 'Tomato', keywords: ['tomato', 'tamatar'], unit: 'kg' },
    { name: 'Shampoo', keywords: ['shampoo', 'sabun'], unit: 'packet' },
    { name: 'Detergent', keywords: ['detergent', 'surf'], unit: 'packet' },
  ];

  // Match items in utterance
  const matched = [];

  for (const itemObj of itemsDict) {
    for (const kw of itemObj.keywords) {
      if (cleanText.includes(kw)) {
        // Extract quantity: e.g. "1k", "1 kg", "2 packet", "500g", "3"
        let qty = 1;
        let unit = itemObj.unit;

        // Match patterns like "1k", "2k", "1kg", "2.5kg", "1 kg", "2 packet"
        const qtyMatch = cleanText.match(new RegExp(`(\\d+(?:\\.\\d+)?)\\s*(k|kg|kilo|litre|l|packet|pkt|gm|g)?\\s*${kw}`));
        if (qtyMatch) {
          qty = parseFloat(qtyMatch[1]);
          const uStr = (qtyMatch[2] || '').toLowerCase();
          if (uStr === 'k' || uStr === 'kg' || uStr === 'kilo') unit = 'kg';
          else if (uStr === 'gm' || uStr === 'g') {
            qty = qty / 1000;
            unit = 'kg';
          } else if (uStr === 'l' || uStr === 'litre') unit = 'litre';
          else if (uStr === 'packet' || uStr === 'pkt') unit = 'packet';
        }

        matched.push({ name: itemObj.name, quantity: qty, unit });
        break;
      }
    }
  }

  if (matched.length > 0) {
    result.items = matched;
    const summary = matched.map((i) => `${i.quantity} ${i.unit} ${i.name}`).join(' aur ');
    result.replyText = `Aapke basket mein ${summary} add kar diya hai! Aur kuch chahiye?`;
    return result;
  }

  return null;
}

export default function CustomerPage() {
  const [menu, setMenu] = useState(DEFAULT_MENU);
  const [menuError, setMenuError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [name, setName] = useState('Rahul');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [pickupTime, setPickupTime] = useState('19:30');
  const [basket, setBasket] = useState([]);
  const [messages, setMessages] = useState([
    { from: 'ai', text: 'Namaste! Sharma Kirana Store mein aapka swagat hai. Mic tap karke boliye: "Siri, 1k atta dalna list mai" ya menu se tap kariye!' },
  ]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [orderId, setOrderId] = useState('');
  const [orderStatus, setOrderStatus] = useState('');
  const [toast, setToast] = useState('');
  const [wakeOn, setWakeOn] = useState(false);
  const [hasWelcomed, setHasWelcomed] = useState(false);
  const [callOpen, setCallOpen] = useState(false);

  // The call has its own mic + speaker: mute the passive wake-word
  // listener while it is open so the AI never hears (and answers) itself.
  useEffect(() => {
    if (callOpen) setWakeOn(false);
  }, [callOpen]);
  const [assistantState, setAssistantState] = useState('Tap the mic, type, or say “Siri…” / “Kirana…”');
  const pollRef = useRef(null);

  useEffect(() => {
    fetchMenu()
      .then((m) => {
        if (Array.isArray(m) && m.length > 0) {
          setMenu(m);
        }
      })
      .catch((e) => {
        console.warn('Backend menu fetch failed, using built-in catalog:', e.message);
      });
  }, []);

  const speakText = async (text) => {
    try {
      const tts = await synthesizeSpeech(text);
      if (tts?.audioBase64) {
        playBase64Audio(tts.audioBase64, tts.mimeType);
      } else {
        speakBrowser(text);
      }
    } catch {
      speakBrowser(text);
    }
  };

  const playWelcomeGreeting = () => {
    const welcomeMsg = 'Namaste! Sharma Kirana Store mein aapka swagat hai. Aap mic tap karke ya bol kar grocery order kar sakte hain.';
    setHasWelcomed(true);
    setToast('Playing Welcome Voice Greeting');
    speakText(welcomeMsg);
    setTimeout(() => setToast(''), 3000);
  };

  const categories = useMemo(
    () => ['All', ...new Set(menu.map((m) => m.category || 'General'))],
    [menu],
  );

  const visibleMenu = useMemo(() => {
    return menu.filter((m) => {
      const matchCat = category === 'All' || (m.category || 'General') === category;
      const matchSearch = m.name.toLowerCase().includes(searchQuery.toLowerCase());
      return matchCat && matchSearch;
    });
  }, [menu, category, searchQuery]);

  const priceOf = useCallback(
    (itemName) => menu.find((m) => m.name.toLowerCase() === itemName.toLowerCase())?.price || 0,
    [menu],
  );

  const total = basket.reduce((s, i) => s + priceOf(i.name) * i.quantity, 0);

  const pushMsg = (from, text) => setMessages((m) => [...m, { from, text }]);

  const handleCommand = useCallback(
    async (transcript) => {
      if (!transcript?.trim() || busy) return;
      setBusy(true);
      setError('');
      setAssistantState('Samajh raha hoon…');
      pushMsg('you', transcript);

      let processed = false;

      // 1. Attempt Groq API processing first
      try {
        const data = await sendConversation({ transcript, currentItems: basket });
        if (data && data.replyText && data.replyText !== 'Theek hai.') {
          if (data.intent === 'REMOVE_ITEM') {
            setBasket((c) =>
              c.filter((i) => !(data.items || []).some((r) => r.name.toLowerCase() === i.name.toLowerCase())),
            );
          } else if (data.intent === 'CANCEL_ORDER') {
            setBasket([]);
          } else if ((data.items || []).length) {
            setBasket((c) => mergeItems(c, data.items));
            setToast(`Basket updated (${data.items.length} item)`);
            setTimeout(() => setToast(''), 3000);
          }
          if (data.pickupTime) setPickupTime(data.pickupTime);
          pushMsg('ai', data.replyText);
          speakText(data.replyText);
          processed = true;
        }
      } catch (e) {
        console.warn('Groq API error, using smart local parser:', e.message);
      }

      // 2. Local smart parser fallback (handles "1k ataa dalna list mai", "siri 1 kg atta", etc.)
      if (!processed) {
        const localData = parseLocalVoiceCommand(transcript);
        if (localData) {
          if (localData.intent === 'CANCEL_ORDER') {
            setBasket([]);
          } else if (localData.intent === 'CONFIRM_ORDER') {
            // Confirm order trigger
          } else if (localData.items && localData.items.length > 0) {
            setBasket((c) => mergeItems(c, localData.items));
            setToast(`Added ${localData.items.map((i) => i.name).join(', ')} to basket`);
            setTimeout(() => setToast(''), 3000);
          }
          pushMsg('ai', localData.replyText);
          speakText(localData.replyText);
          processed = true;
        }
      }

      if (!processed) {
        const fallbackText = 'Ji samjhaa. Aap menu se select kar sakte hain ya boliye: "1 kg Atta aur 2 packet Milk".';
        pushMsg('ai', fallbackText);
        speakText(fallbackText);
      }

      setAssistantState('Tap the mic, type, or say “Siri…” / “Kirana…”');
      setBusy(false);
    },
    [busy, basket],
  );

  const { listening, start } = useSpeech({ onTranscript: (t) => handleCommand(t) });
  const { supported: wakeSupported, awakened } = useWakeWord({
    enabled: wakeOn,
    onCommand: (t) => handleCommand(t),
  });

  useEffect(() => {
    if (awakened) setAssistantState('Haan ji? Sun raha hoon…');
  }, [awakened]);

  useOrdersSocket({
    onOrderEvent: (payload) => {
      if (payload?.orderId && orderId && payload.orderId === orderId && payload.status) {
        setOrderStatus(payload.status);
        setToast(`Order Status Updated: ${payload.status}`);
        setTimeout(() => setToast(''), 4000);
      }
    },
  });

  useEffect(() => {
    if (!orderId) return;
    clearInterval(pollRef.current);
    pollRef.current = setInterval(async () => {
      try {
        const o = await fetchOrder(orderId);
        if (o?.status) setOrderStatus(o.status);
      } catch { /* noop */ }
    }, 5000);
    return () => clearInterval(pollRef.current);
  }, [orderId]);

  const addProduct = (p) => {
    if (!p.available) return;
    setBasket((b) => mergeItems(b, [{ name: p.name, quantity: 1, unit: p.unit || 'pc' }]));
    const msg = `Added 1 ${p.unit || 'pc'} of ${p.name} to basket`;
    setToast(`${msg}`);
    speakText(`${p.name} basket mein add ho gaya`);
    setTimeout(() => setToast(''), 2500);
  };

  const confirmOrder = async () => {
    if (!name.trim()) {
      setError('Apna naam likhiye.');
      return;
    }
    if (!basket.length) {
      setError('Basket khaali hai — menu se add kariye ya boliye.');
      return;
    }
    setBusy(true);
    setError('');

    let newOrdId = 'KS-' + Math.floor(1000 + Math.random() * 9000);
    let newStatus = 'PENDING';

    try {
      const order = await createOrder({
        customerName: name.trim(),
        customerPhone: phone.trim() || undefined,
        items: basket,
        pickupTime,
        address: address.trim() || undefined,
      });
      if (order && order.id) {
        newOrdId = order.id;
        newStatus = order.status || 'PENDING';
      }
    } catch (e) {
      console.warn('Backend order post failed, creating simulated order:', e.message);
    }

    setOrderId(newOrdId);
    setOrderStatus(newStatus);
    const confirmMsg = `Aapka order confirm ho gaya hai! Order ID #${newOrdId.slice(-6)}. Pickup time ${pickupTime}. Dhanyavaad!`;
    pushMsg('ai', confirmMsg);
    speakText(confirmMsg);
    setBusy(false);
  };

  return (
    <div className="page">
      {/* First Layer Topbar */}
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <StoreIcon size={24} />
          </span>
          <div>
            <h1>Sharma Kirana Store</h1>
            <small>Voice Assistant Powered by Groq AI</small>
          </div>
        </Link>
        <nav className="navlinks">
          <button className="nav-link call-btn" onClick={() => setCallOpen(true)}>
            <PhoneIcon size={15} /> Call Shopkeeper
          </button>
          <button
            className="nav-link"
            style={{ background: 'var(--primary-soft)', color: 'var(--primary)', fontWeight: 700 }}
            onClick={playWelcomeGreeting}
          >
            <SpeakerIcon size={15} /> Welcome Voice
          </button>
          <Link to="/" className="nav-link">Home</Link>
          <Link to="/shopkeeper" className="nav-link primary">Shopkeeper Portal</Link>
        </nav>
      </header>

      {/* Hero Banner */}
      <section className="hero">
        <h2>Namaste! Sharma Kirana Store Mein Aapka Swagat Hai.</h2>
        <p>Browse our catalog below or speak naturally to your AI assistant — say "Siri, 1k atta dalna list mai" or click the mic to add items and place live orders!</p>
        <div style={{ marginTop: 12, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <span className="wake-hint">
            <MicIcon size={15} /> Wake word: "Siri…" or "Kirana…" — continuous voice listening
          </span>
          <button className="call-btn hero-call" onClick={() => setCallOpen(true)}>
            <PhoneIcon size={15} /> Call Shopkeeper
          </button>
          {!hasWelcomed && (
            <button
              onClick={playWelcomeGreeting}
              style={{
                background: 'rgba(34, 211, 238, 0.08)',
                border: '1px solid rgba(34, 211, 238, 0.35)',
                color: '#a5f3fc',
                padding: '6px 14px',
                borderRadius: '999px',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 7,
              }}
            >
              <SpeakerIcon size={15} /> Click to Hear Welcome Audio Greeting
            </button>
          )}
        </div>
      </section>

      {/* AI Voice Assistant Dock */}
      <section className="assistant-dock">
        <div className="mic-wrapper">
          <button
            className={`mic-btn ${listening || awakened ? 'live' : ''}`}
            onClick={() => {
              if (!hasWelcomed) playWelcomeGreeting();
              start();
            }}
            disabled={busy}
            aria-label="Activate Microphone Voice Assistant"
            title="Click to Speak"
          >
            <MicIcon size={34} />
          </button>
        </div>

        <div className="assistant-info">
          <h3>
            Kirana AI Voice Assistant
            {awakened ? (
              <>
                <span className="live-dot" /> Listening
              </>
            ) : listening ? (
              <>
                <span className="live-dot" /> Recording…
              </>
            ) : (
              ' — Ready'
            )}
          </h3>
          <p>{assistantState}</p>
          <span className="assistant-badge">
            {busy ? 'THINKING…' : basket.length ? `${basket.length} ITEM(S) IN BASKET` : 'BASKET EMPTY'}
          </span>
        </div>

        <button
          className={`wake-toggle-btn ${wakeOn ? 'active' : ''}`}
          onClick={() => setWakeOn((v) => !v)}
          title={wakeSupported ? 'Always-on wake word listener' : 'Wake word requires Web Speech API'}
          disabled={!wakeSupported}
        >
          {wakeSupported ? (wakeOn ? 'Wake Word: ON' : 'Wake Word: OFF') : 'Wake N/A'}
        </button>
      </section>

      {error && <div className="error-banner">{error}</div>}
      {toast && <div className="toast-banner">{toast}</div>}

      <div className="shop-grid">
        <div>
          {/* Store Menu Card */}
          <section className="card">
            <div className="card-title">
              <span className="title-icon"><PackageIcon size={18} /> Store Catalog & Menu Items</span>
              <small className="card-subtitle" style={{ margin: 0 }}>Tap item or say "1k atta dalna list mai"</small>
            </div>
            <p className="card-subtitle">Fresh local groceries delivered straight to your pickup counter.</p>

            {menuError && <div className="error-banner">{menuError}</div>}

            {/* Search Box */}
            <div className="search-box">
              <span className="search-icon"><SearchIcon size={17} /></span>
              <input
                type="text"
                placeholder="Search items e.g. Atta, Milk, Maggi, Dal..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            {/* Category Chips */}
            <div className="category-chips">
              {categories.map((c) => (
                <button
                  key={c}
                  className={`chip-btn ${c === category ? 'active' : ''}`}
                  onClick={() => setCategory(c)}
                >
                  {c}
                </button>
              ))}
            </div>

            {/* Menu Grid */}
            <div className="menu-grid">
              {visibleMenu.map((p) => {
                const img = imageFor(p);
                return (
                  <div
                    key={p.id || p.name}
                    className={`product-card ${p.available ? '' : 'out-of-stock'}`}
                    onClick={() => addProduct(p)}
                    style={{ cursor: p.available ? 'pointer' : 'not-allowed' }}
                  >
                    <div className="product-image-box">
                      {img ? (
                        <img
                          src={img}
                          alt={p.name}
                          className="product-img"
                          onError={(e) => {
                            e.target.style.display = 'none';
                            if (e.target.nextSibling) e.target.nextSibling.style.display = 'grid';
                          }}
                        />
                      ) : null}
                      <div
                        className="product-emoji-container"
                        style={{ display: img ? 'none' : 'grid' }}
                      >
                        <PackageIcon size={30} />
                      </div>
                    </div>

                    <div className="product-name">{p.name}</div>
                    <div className="product-meta">
                      <span className="product-price">₹{p.price}</span>
                      <span className="product-unit">/ {p.unit || 'pc'}</span>
                    </div>
                    <button
                      className="product-add-btn"
                      disabled={!p.available}
                      onClick={(e) => {
                        e.stopPropagation();
                        addProduct(p);
                      }}
                    >
                      {p.available ? '+ Add to Basket' : 'Out of stock'}
                    </button>
                  </div>
                );
              })}
            </div>

            {!visibleMenu.length && (
              <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>
                No items match your search. Try another query or clear filters.
              </p>
            )}
          </section>

          {/* AI Chat Conversation Card */}
          <section className="card" style={{ marginTop: 20 }}>
            <h2 className="card-title">
              <span className="title-icon"><MicIcon size={18} /> AI Assistant Conversation</span>
            </h2>
            <p className="card-subtitle">Speak or type: "Siri, 1k atta dalna list mai" or "Add 2 packet Amul milk"</p>

            <div className="chat-box">
              {messages.map((m, i) => (
                <div key={i} className={`chat-bubble ${m.from}`}>
                  <b>{m.from === 'you' ? 'You: ' : 'Kirana AI: '}</b>
                  {m.text}
                </div>
              ))}
            </div>

            <form
              className="typed-form"
              onSubmit={(e) => {
                e.preventDefault();
                const v = e.target.elements.typed.value;
                if (!v.trim()) return;
                e.target.reset();
                handleCommand(v);
              }}
            >
              <input
                name="typed"
                placeholder="Type: '1k ataa dalna list mai' or '2 packet Milk'"
                autoComplete="off"
              />
              <button
                className="nav-link primary"
                style={{ padding: '0 20px', borderRadius: 'var(--radius-md)' }}
                type="submit"
                disabled={busy}
              >
                Send
              </button>
            </form>
          </section>
        </div>

        {/* Right Column: Sticky Basket */}
        <div className="basket-sticky">
          <section className="card">
            <h2 className="card-title">
              <span className="title-icon"><CartIcon size={18} /> Your Basket</span>
            </h2>
            <p className="card-subtitle">Review items, set pickup details, and confirm order.</p>

            <div className="form-group">
              <label>Customer Name</label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Enter your name"
              />
            </div>

            <div className="form-group">
              <label>Phone Number (Optional)</label>
              <input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+91 9876543210"
              />
            </div>

            <div className="form-group">
              <label>Delivery Address</label>
              <input
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="House no, street, area"
              />
            </div>

            <div className="form-group">
              <label>Pickup Time</label>
              <input
                value={pickupTime}
                onChange={(e) => setPickupTime(e.target.value)}
                placeholder="19:30"
              />
            </div>

            {basket.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-muted)' }}>
                <p>Basket is currently empty.</p>
                <small>Tap items in catalog or say: "1k ataa dalna list mai"</small>
              </div>
            ) : (
              <>
                <ul className="basket-list">
                  {basket.map((i, idx) => (
                    <li key={idx} className="basket-item">
                      <div className="basket-item-info">
                        <span className="basket-item-title">{i.name}</span>
                        <span className="basket-item-unit">₹{priceOf(i.name)} × {i.quantity} {i.unit}</span>
                      </div>
                      <div className="basket-qty-controls">
                        <button
                          className="qty-btn"
                          onClick={() =>
                            setBasket((c) =>
                              c.map((x, j) => (j === idx ? { ...x, quantity: Math.max(1, x.quantity - 1) } : x)),
                            )
                          }
                          aria-label="Decrease quantity"
                        >
                          <MinusIcon size={14} />
                        </button>
                        <span style={{ fontWeight: 700, minWidth: '18px', textAlign: 'center' }}>
                          {i.quantity}
                        </span>
                        <button
                          className="qty-btn"
                          onClick={() =>
                            setBasket((c) => c.map((x, j) => (j === idx ? { ...x, quantity: x.quantity + 1 } : x)))
                          }
                          aria-label="Increase quantity"
                        >
                          <PlusIcon size={14} />
                        </button>
                        <button
                          className="qty-btn danger"
                          onClick={() => setBasket((c) => c.filter((_, j) => j !== idx))}
                          title="Remove item"
                          aria-label="Remove item"
                        >
                          <XIcon size={14} />
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>

                <div className="total-breakdown">
                  <div className="total-row">
                    <span>Total Amount</span>
                    <span>₹{total.toFixed(0)}</span>
                  </div>
                </div>
              </>
            )}

            <button
              className="btn-primary"
              onClick={confirmOrder}
              disabled={busy || !basket.length}
              style={{ marginTop: 12 }}
            >
              <CheckIcon size={17} /> Confirm & Place Order
            </button>

            {orderId && (
              <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--card-border)' }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Order ID: <b>#{orderId.slice(-6)}</b>
                </div>
                <div style={{ marginTop: 4 }}>
                  Status: <span className={`status-badge ${orderStatus}`}>{orderStatus}</span>
                </div>
              </div>
            )}
          </section>
        </div>
      </div>

      {callOpen && (
        <CallShopkeeperModal
          customerName={name}
          address={address}
          phone={phone}
          pickupTime={pickupTime}
          onClose={() => setCallOpen(false)}
          onOrderPlaced={(order) => {
            setOrderId(order.id);
            setOrderStatus(order.status);
            setToast(`Call order placed! ID #${order.id.slice(-6)} — live on shopkeeper portal.`);
            setTimeout(() => setToast(''), 5000);
            setCallOpen(false);
          }}
        />
      )}
    </div>
  );
}
