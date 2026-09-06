import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchOrders, getToken, login, logout, me, updateOrderStatus } from '../services/api.js';
import { useOrdersSocket } from '../websocket/useOrdersSocket.js';

const TABS = ['All', 'PENDING', 'ACCEPTED', 'PREPARING', 'READY', 'COMPLETED', 'REJECTED', 'CANCELLED', 'History'];

const NEXT_ACTIONS = {
  PENDING: ['ACCEPTED', 'REJECTED'],
  ACCEPTED: ['PREPARING', 'REJECTED', 'CANCELLED'],
  PREPARING: ['READY', 'CANCELLED'],
  READY: ['COMPLETED', 'CANCELLED'],
};

export default function ShopkeeperPage() {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [loggingIn, setLoggingIn] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (!getToken()) {
      setChecking(false);
      return;
    }
    me()
      .then((d) => setUser(d.username))
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, []);

  const doLogin = async (e) => {
    e.preventDefault();
    setLoggingIn(true);
    setLoginError('');
    try {
      const d = await login(username.trim(), password);
      setUser(d.username);
    } catch (err) {
      setLoginError(err.message);
    } finally {
      setLoggingIn(false);
    }
  };

  const doLogout = async () => {
    await logout();
    setUser(null);
    navigate('/');
  };

  if (checking) {
    return (
      <div className="page" style={{ textAlign: 'center', padding: '60px 0' }}>
        <p style={{ color: 'var(--text-muted)' }}>Checking shopkeeper session…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="page">
        <header className="topbar">
          <Link to="/" className="brand">
            <span className="brand-mark">🏪</span>
            <div>
              <h1>Shopkeeper Portal</h1>
              <small>Authorized Access Only</small>
            </div>
          </Link>
          <nav className="navlinks">
            <Link to="/" className="nav-link">← Home</Link>
            <Link to="/customer" className="nav-link primary">Customer Store →</Link>
          </nav>
        </header>

        <div style={{ maxWidth: '440px', margin: '40px auto 0' }}>
          <section className="card">
            <h2 className="card-title" style={{ marginBottom: 6 }}>🔐 Shopkeeper Login</h2>
            <p className="card-subtitle">Sign in to manage store inventory and fulfill real-time customer orders.</p>

            {loginError && <div className="error-banner">{loginError}</div>}

            <form onSubmit={doLogin}>
              <div className="form-group">
                <label>Username</label>
                <input
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  placeholder="shopkeeper"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 20 }}>
                <label>Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  placeholder="••••••••"
                />
              </div>

              <button className="btn-primary" type="submit" disabled={loggingIn}>
                {loggingIn ? 'Authenticating…' : 'Sign In →'}
              </button>
            </form>

            <div style={{ marginTop: 20, paddingTop: 14, borderTop: '1px solid var(--card-border)', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Default credentials: <code style={{ background: '#f1f5f9', padding: '2px 6px', borderRadius: 4 }}>shopkeeper / changeme</code>
            </div>
          </section>
        </div>
      </div>
    );
  }

  return <Dashboard user={user} onLogout={doLogout} />;
}

function Dashboard({ user, onLogout }) {
  const [tab, setTab] = useState('All');
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [historySearch, setHistorySearch] = useState('');

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      // 'History' is a client-side view — fetch everything, filter locally.
      setOrders(await fetchOrders(tab === 'History' ? 'All' : tab));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  useOrdersSocket({
    onOrderEvent: (payload) => {
      if (payload?.action === 'CREATED') {
        setToast('🔔 New Customer Order Received!');
        setTimeout(() => setToast(''), 4500);
      }
      loadOrders();
    },
  });

  const handleStatusChange = async (id, status) => {
    try {
      await updateOrderStatus(id, status);
      await loadOrders();
    } catch (e) {
      setError(e.message);
    }
  };

  const pendingCount = orders.filter((o) => o.status === 'PENDING').length;

  const filteredOrders = useMemo(() => {
    if (tab === 'History') {
      const pastOrders = orders.filter(
        (o) => o.status === 'COMPLETED' || o.status === 'READY' || o.status === 'REJECTED' || o.status === 'CANCELLED',
      );
      if (!historySearch.trim()) return pastOrders;
      const query = historySearch.toLowerCase();
      return pastOrders.filter(
        (o) =>
          (o.id && o.id.toLowerCase().includes(query)) ||
          (o.customerName && o.customerName.toLowerCase().includes(query)) ||
          (o.customerPhone && o.customerPhone.toLowerCase().includes(query)),
      );
    }
    if (tab === 'All') return orders;
    return orders.filter((o) => o.status === tab);
  }, [orders, tab, historySearch]);

  const historyMetrics = useMemo(() => {
    const past = orders.filter(
      (o) => o.status === 'COMPLETED' || o.status === 'READY' || o.status === 'REJECTED' || o.status === 'CANCELLED',
    );
    const revenue = past
      .filter((o) => o.status === 'COMPLETED' || o.status === 'READY')
      .reduce((sum, o) => sum + (o.totalPrice || 0), 0);
    const completed = past.filter((o) => o.status === 'COMPLETED').length;
    return { count: past.length, revenue, completed };
  }, [orders]);

  return (
    <div className="page">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark">🏪</span>
          <div>
            <h1>Shopkeeper Dashboard</h1>
            <small>Logged in as <b>{user}</b> {pendingCount > 0 ? `• ${pendingCount} pending order(s)` : ''}</small>
          </div>
        </Link>
        <nav className="navlinks">
          <ConnectionBadge />
          <button className="nav-link" onClick={onLogout}>Logout ⏻</button>
        </nav>
      </header>

      {toast && <div className="toast-banner">{toast}</div>}
      {error && <div className="error-banner">{error}</div>}

      {/* Tabs */}
      <div className="tabs-container">
        {TABS.map((t) => {
          let count = 0;
          if (t === 'All') count = orders.length;
          else if (t === 'History') count = historyMetrics.count;
          else count = orders.filter((o) => o.status === t).length;

          return (
            <button
              key={t}
              className={`tab-btn ${t === tab ? 'active' : ''}`}
              onClick={() => setTab(t)}
            >
              {t === 'History' ? '📜 Order History' : t} {count > 0 ? `(${count})` : ''}
            </button>
          );
        })}
      </div>

      {tab === 'History' && (
        <section className="card" style={{ marginBottom: 20 }}>
          <div className="card-title">
            <span>📜 Order History & Revenue Analytics</span>
            <small style={{ color: 'var(--text-muted)', fontWeight: 500 }}>Completed & Archived Orders</small>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14, margin: '16px 0' }}>
            <div style={{ background: '#f8fafc', border: '1px solid var(--card-border)', padding: 14, borderRadius: 'var(--radius-sm)' }}>
              <small style={{ color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>Total Revenue</small>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--primary)' }}>₹{historyMetrics.revenue.toFixed(0)}</div>
            </div>

            <div style={{ background: '#f8fafc', border: '1px solid var(--card-border)', padding: 14, borderRadius: 'var(--radius-sm)' }}>
              <small style={{ color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>Completed Orders</small>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--accent-emerald)' }}>{historyMetrics.completed}</div>
            </div>

            <div style={{ background: '#f8fafc', border: '1px solid var(--card-border)', padding: 14, borderRadius: 'var(--radius-sm)' }}>
              <small style={{ color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>Total Records</small>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-main)' }}>{historyMetrics.count}</div>
            </div>
          </div>

          <div className="search-box">
            <span className="search-icon">🔍</span>
            <input
              type="text"
              placeholder="Search history by Customer Name, Phone, or Order ID..."
              value={historySearch}
              onChange={(e) => setHistorySearch(e.target.value)}
            />
          </div>
        </section>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted)' }}>
          Loading orders…
        </div>
      ) : filteredOrders.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          <p style={{ fontSize: '1.1rem', fontWeight: 600 }}>No {tab} records found.</p>
          <small>Order history and status records pop up here automatically! 📜</small>
        </div>
      ) : (
        <div className="orders-grid">
          {filteredOrders.map((o) => (
            <article key={o.id} className="order-card">
              <div className="order-header">
                <span className="order-id">ORDER #{o.id?.slice(-6)}</span>
                <span className={`status-badge ${o.status}`}>{o.status}</span>
              </div>

              <div className="order-customer">
                Customer: <b>{o.customerName || 'Walk-in Customer'}</b>{' '}
                {o.customerPhone && <span style={{ fontSize: '0.8rem' }}>({o.customerPhone})</span>}
              </div>

              <ul className="order-items-list">
                {(o.items || []).map((item, idx) => (
                  <li key={idx} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>• {item.name}</span>
                    <b>× {item.quantity} {item.unit}</b>
                  </li>
                ))}
              </ul>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.88rem', color: 'var(--text-muted)' }}>
                <span>Pickup Time: <b>{o.pickupTime || 'Immediate'}</b></span>
                {o.totalPrice ? <span style={{ fontWeight: 800, color: 'var(--text-main)' }}>₹{o.totalPrice}</span> : null}
              </div>
              {tab === 'History' && o.createdAt && (
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  🕒 {new Date(o.createdAt).toLocaleString('en-IN')}
                </div>
              )}

              {tab !== 'History' && (
                <div className="order-actions">
                  {(NEXT_ACTIONS[o.status] || []).map((nextStatus) => {
                    const isNegative = nextStatus === 'REJECTED' || nextStatus === 'CANCELLED';
                    return (
                      <button
                        key={nextStatus}
                        className={`action-btn ${isNegative ? 'reject' : 'accept'}`}
                        onClick={() => handleStatusChange(o.id, nextStatus)}
                      >
                        {nextStatus === 'ACCEPTED' ? '✅ Accept Order' : nextStatus === 'REJECTED' ? '✕ Reject' : nextStatus}
                      </button>
                    );
                  })}
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}

function ConnectionBadge() {
  const { connected } = useOrdersSocket({});
  return (
    <span
      className={`status-badge ${connected ? 'READY' : 'PENDING'}`}
      style={{ padding: '6px 14px' }}
    >
      {connected ? '● WebSocket Live' : '○ Reconnecting…'}
    </span>
  );
}
