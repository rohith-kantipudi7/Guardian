import { config } from '../config';

/** Client for the virtual Guardian device control panel (`python simulator.py serve`). */

export type TracePoint = [tMs: number, accelG: number, rotationRadS: number, speedKmh: number | null];

export interface SimOutcome {
  scenario: string;
  emergency: boolean;
  delivered: boolean;
  eventId: string | null;
  severity: 'NORMAL' | 'MINOR' | 'MODERATE' | 'CRITICAL' | null;
  confidence: number | null;
  reasons: string[];
  accelerationPeakG: number | null;
  rotationPeakRadS: number | null;
  transitions: string[];
  trace: TracePoint[];
  phoneConnected: boolean;
  network: string;
}

export interface SimState {
  deviceId: string;
  vehicleId: string;
  phoneConnected: boolean;
  network: 'ONLINE' | 'OFFLINE' | 'DELAYED';
  battery: number;
  pendingEvents: number;
  lastHeartbeatOk: boolean | null;
  lastOutcome: SimOutcome | null;
  scenarios: string[];
  log: string[];
}

async function call(path: string, body?: unknown): Promise<SimState> {
  const res = await fetch(`${config.simulatorUrl}${path}`, {
    method: body === undefined && path === '/state' ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Device simulator: ${res.status}`);
  return res.json();
}

export const simulator = {
  state: () => call('/state'),
  seed: () => call('/seed', {}),
  reset: () => call('/reset', {}),
  scenario: (name: string, opts: { cancel?: boolean; noSpeed?: boolean } = {}) => call(`/scenario/${name}`, opts),
  phone: (connected: boolean) => call(`/phone/${connected ? 'connect' : 'disconnect'}`, {}),
  network: (mode: 'online' | 'offline' | 'delayed') => call(`/network/${mode}`, {}),
};
