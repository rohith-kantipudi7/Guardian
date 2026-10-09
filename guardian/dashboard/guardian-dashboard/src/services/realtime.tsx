import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { config } from '../config';
import type { CrashEvent, WsMessage } from '../types';

interface RealtimeState {
  connected: boolean;
  alert: CrashEvent | null;
  dismissAlert: () => void;
}

const RealtimeContext = createContext<RealtimeState>({ connected: false, alert: null, dismissAlert: () => {} });

export const useRealtime = () => useContext(RealtimeContext);

function playAlertTone() {
  try {
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'square';
    osc.frequency.value = 880;
    gain.gain.value = 0.05;
    osc.connect(gain).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.6);
  } catch {
    /* audio blocked until user interacts with the page */
  }
}

/** Keeps a WebSocket to the backend open, reconnects with backoff, and refreshes React Query caches. */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  const [alert, setAlert] = useState<CrashEvent | null>(null);
  const retry = useRef(0);

  useEffect(() => {
    let ws: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    let pingTimer: number | undefined;
    let closed = false;

    const connect = () => {
      ws = new WebSocket(config.wsUrl);
      ws.onopen = () => {
        retry.current = 0;
        setConnected(true);
        // Catch up on anything missed while disconnected.
        queryClient.invalidateQueries();
        pingTimer = window.setInterval(() => ws?.readyState === WebSocket.OPEN && ws.send('ping'), 20000);
      };
      ws.onmessage = (e) => {
        let msg: WsMessage;
        try {
          msg = JSON.parse(e.data);
        } catch {
          return;
        }
        switch (msg.type) {
          case 'CRASH_DETECTED':
            queryClient.invalidateQueries({ queryKey: ['crashes'] });
            queryClient.invalidateQueries({ queryKey: ['devices'] });
            queryClient.invalidateQueries({ queryKey: ['stats'] });
            if (msg.event.status === 'EMERGENCY_ACTIVE') {
              setAlert(msg.event);
              playAlertTone();
            }
            break;
          case 'CRASH_UPDATED':
            queryClient.invalidateQueries({ queryKey: ['crashes'] });
            queryClient.invalidateQueries({ queryKey: ['stats'] });
            queryClient.setQueryData(['crash', msg.event.eventId], msg.event);
            break;
          case 'DEVICE_HEARTBEAT':
          case 'DEVICE_REGISTERED':
            queryClient.invalidateQueries({ queryKey: ['devices'] });
            break;
          case 'DEMO_RESET':
            setAlert(null);
            queryClient.invalidateQueries();
            break;
        }
      };
      ws.onclose = () => {
        setConnected(false);
        window.clearInterval(pingTimer);
        if (closed) return;
        const delay = Math.min(10000, 500 * 2 ** retry.current++);
        reconnectTimer = window.setTimeout(connect, delay);
      };
      ws.onerror = () => ws?.close();
    };

    connect();
    return () => {
      closed = true;
      window.clearTimeout(reconnectTimer);
      window.clearInterval(pingTimer);
      ws?.close();
    };
  }, [queryClient]);

  return (
    <RealtimeContext.Provider value={{ connected, alert, dismissAlert: () => setAlert(null) }}>
      {children}
    </RealtimeContext.Provider>
  );
}
