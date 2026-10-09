import { config } from '../config';
import type {
  AlertView,
  CrashEvent,
  CrashStatus,
  Device,
  Stats,
  User,
  Vehicle,
} from '../types';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${config.apiUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = typeof body.detail === 'string' ? body.detail : JSON.stringify(body.detail);
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, detail);
  }
  return res.json() as Promise<T>;
}

export interface CrashFilters {
  status?: CrashStatus;
  deviceId?: string;
  vehicleId?: string;
  active?: boolean;
  limit?: number;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

const crashPath = (eventId: string) => `/api/crashes/${encodeURIComponent(eventId)}`;

export const api = {
  health: () => request<{ status: string; version: string }>('/health'),
  stats: () => request<Stats>('/api/stats'),

  listCrashes: (filters: CrashFilters = {}) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([k, v]) => v !== undefined && params.set(k, String(v)));
    const qs = params.toString();
    return request<CrashEvent[]>(`/api/crashes${qs ? `?${qs}` : ''}`);
  },
  getCrash: (eventId: string) => request<CrashEvent>(crashPath(eventId)),
  cancel: (eventId: string, by: string, note?: string) =>
    post<CrashEvent>(`${crashPath(eventId)}/cancel`, { operator: by, note }),

  getAlert: (token: string) => request<AlertView>(`/api/alerts/${encodeURIComponent(token)}`),

  listDevices: () => request<Device[]>('/api/devices'),
  listVehicles: () => request<Vehicle[]>('/api/vehicles'),
  getUser: (userId: string) => request<User>(`/api/users/${encodeURIComponent(userId)}`),

  demoReset: () => post<{ removedIncidents: number }>('/api/demo/reset'),
};
