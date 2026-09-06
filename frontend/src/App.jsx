import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import CustomerPage from './pages/CustomerPage.jsx';
import ShopkeeperPage from './pages/ShopkeeperPage.jsx';
import Home from './pages/Home.jsx';
import './styles.css';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/customer" element={<CustomerPage />} />
        <Route path="/shopkeeper" element={<ShopkeeperPage />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  );
}

function NotFound() {
  return (
    <div className="page home">
      <h1>404</h1>
      <p><Link to="/">← Back home</Link></p>
    </div>
  );
}
