const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const TOKEN_KEY = 'kirana_shopkeeper_token';

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* noop */ }
}

function authHeaders() {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
}

async function json(res) {
  const text = await res.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return { message: text };
  }
}

function throwIfAuth(data, res) {
  if (res.status === 401) {
    setToken('');
    throw new Error('Shopkeeper login required — please log in again.');
  }
}

// ---------- public: menu + conversation ----------

export async function fetchMenu() {
  const res = await fetch(`${API}/api/inventory`);
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Menu failed to load');
  return data;
}

export async function sendConversation({ transcript, currentItems }) {
  const res = await fetch(`${API}/api/conversation/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transcript, currentItems }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Conversation failed');
  return data;
}

export async function createOrder({ customerName, customerPhone, items, pickupTime, address }) {
  const res = await fetch(`${API}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ customerName, customerPhone, items, pickupTime, address }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Order failed');
  return data;
}

export async function fetchOrder(id) {
  const res = await fetch(`${API}/api/orders/${encodeURIComponent(id)}`);
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Fetch failed');
  return data;
}

// ---------- shopkeeper: protected ----------

export async function login(username, password) {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Login failed');
  setToken(data.token);
  return data;
}

export async function logout() {
  try {
    await fetch(`${API}/api/auth/logout`, {
      method: 'POST',
      headers: { ...authHeaders() },
    });
  } catch { /* noop */ }
  setToken('');
}

export async function me() {
  const res = await fetch(`${API}/api/auth/me`, { headers: { ...authHeaders() } });
  const data = await json(res);
  if (!res.ok) {
    throwIfAuth(data, res);
    throw new Error(data?.message || 'Not logged in');
  }
  return data;
}

export async function fetchOrders(status) {
  const url = status && status !== 'All' && status !== 'History'
    ? `${API}/api/orders?status=${encodeURIComponent(status)}`
    : `${API}/api/orders`;
  const res = await fetch(url, { headers: { ...authHeaders() } });
  const data = await json(res);
  if (!res.ok) {
    throwIfAuth(data, res);
    throw new Error(data?.message || 'Fetch failed');
  }
  return data;
}

export async function updateOrderStatus(id, status) {
  const res = await fetch(`${API}/api/orders/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ status }),
  });
  const data = await json(res);
  if (!res.ok) {
    throwIfAuth(data, res);
    throw new Error(data?.message || 'Status update failed');
  }
  return data;
}

// ---------- Web Call API ----------

export async function startWebCall(phoneNumber = '+919999999999') {
  const res = await fetch(`${API}/api/web-call/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Web call start failed');
  return data;
}

export async function processWebCallSpeech({ callSid, transcript, phoneNumber }) {
  const res = await fetch(`${API}/api/web-call/process`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callSid, transcript, phoneNumber }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Speech processing failed');
  return data;
}

export async function endWebCall({ callSid, phoneNumber }) {
  const res = await fetch(`${API}/api/web-call/end`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callSid, phoneNumber }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Call end failed');
  return data;
}

export async function updateWebCallOrder({ phoneNumber, itemName, action, quantity }) {
  const res = await fetch(`${API}/api/web-call/order-update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber, itemName, action, quantity }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Order item update failed');
  return data;
}

// ---------- AI phone agent ("Call Shopkeeper") ----------

export async function startCall(phoneNumber) {
  const res = await fetch(`${API}/api/web-call/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber: phoneNumber || '+919999999999' }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Call failed to start');
  return data;
}

export async function processCallSpeech({ callSid, transcript, phoneNumber }) {
  const res = await fetch(`${API}/api/web-call/process`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callSid, transcript, phoneNumber }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Call processing failed');
  return data;
}

export async function endCall({ callSid, phoneNumber }) {
  const res = await fetch(`${API}/api/web-call/end`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callSid, phoneNumber }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'Call failed to end');
  return data;
}

// ---------- voice helpers ----------

export async function synthesizeSpeech(text) {
  const res = await fetch(`${API}/api/tts/synthesize`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'TTS failed');
  return data;
}

export async function transcribeAudio(blob) {
  const form = new FormData();
  form.append('audio', blob, 'clip.webm');
  const res = await fetch(`${API}/api/stt/transcribe`, { method: 'POST', body: form });
  const data = await json(res);
  if (!res.ok) throw new Error(data?.message || 'STT failed');
  return data;
}

// ---------- voice provider status ----------

export async function fetchVoiceStatus() {
  try {
    const res = await fetch(`${API}/api/voice/status`);
    const data = await json(res);
    if (!res.ok) throw new Error(data?.message || 'Status failed');
    return data;
  } catch {
    return null;
  }
}
