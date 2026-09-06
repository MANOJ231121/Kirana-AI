import { Link } from 'react-router-dom';

export default function Home() {
  return (
    <div className="page">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">🛒</span>
          <div>
            <h1>Kirana AI</h1>
            <small>Voice-Activated Smart Grocery Store</small>
          </div>
        </div>
        <nav className="navlinks">
          <Link to="/customer" className="nav-link primary">
            Customer Store →
          </Link>
          <Link to="/shopkeeper" className="nav-link">
            Shopkeeper Login
          </Link>
        </nav>
      </header>

      <section className="home-hero">
        <h1>Namaste! 🙏 Kirana Shopping, Just Say It.</h1>
        <p>
          Browse our smart digital store menu or speak to your AI assistant — say “Kirana…” or click the mic to fill your basket and place instant live orders.
        </p>
        <div className="portal-cards">
          <Link to="/customer" className="portal-card">
            <div className="portal-icon customer">🎤</div>
            <h3>Customer Store Portal</h3>
            <p>Interactive item catalog, wake-word voice assistant, instant cart, and live order status.</p>
          </Link>
          <Link to="/shopkeeper" className="portal-card">
            <div className="portal-icon shopkeeper">🏪</div>
            <h3>Shopkeeper Dashboard</h3>
            <p>Secure login, real-time incoming order notifications, and status lifecycle management.</p>
          </Link>
        </div>
      </section>
    </div>
  );
}
