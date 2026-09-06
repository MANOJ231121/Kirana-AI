import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws-dashboard';

/**
 * Subscribes to /topic/orders + /topic/calls.
 * Returns live events + connection state for toasts/badges.
 */
export function useOrdersSocket({ onOrderEvent, onCallEvent } = {}) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const handlers = useRef({ onOrderEvent, onCallEvent });
  handlers.current = { onOrderEvent, onCallEvent };

  useEffect(() => {
    let client;
    let cancelled = false;
    try {
      const sock = new SockJS(WS_URL);
      client = Stomp.over(sock);
      client.debug = () => {};
      client.connect(
        {},
        () => {
          if (cancelled) return;
          setConnected(true);
          client.subscribe('/topic/orders', (msg) => {
            let payload = null;
            try {
              payload = JSON.parse(msg.body);
            } catch {
              payload = { raw: msg.body };
            }
            setLastEvent({ kind: 'order', at: Date.now(), payload });
            handlers.current.onOrderEvent?.(payload);
          });
          client.subscribe('/topic/calls', (msg) => {
            let payload = null;
            try {
              payload = JSON.parse(msg.body);
            } catch {
              payload = { raw: msg.body };
            }
            setLastEvent({ kind: 'call', at: Date.now(), payload });
            handlers.current.onCallEvent?.(payload);
          });
        },
        () => {
          if (!cancelled) setConnected(false);
        },
      );
    } catch {
      setConnected(false);
    }
    return () => {
      cancelled = true;
      try {
        client?.disconnect();
      } catch { /* noop */ }
    };
  }, []);

  return { connected, lastEvent };
}
