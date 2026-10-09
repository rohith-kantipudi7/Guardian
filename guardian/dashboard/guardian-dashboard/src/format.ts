import type { CrashStatus, RecipientType, Severity } from './types';

export const formatTime = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : '—');

export const formatClock = (iso: string | null) =>
  iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';

export const formatPercent = (v: number) => `${Math.round(v * 100)}%`;

export const formatCoords = (lat: number | null, lon: number | null) =>
  lat === null || lon === null ? 'Unavailable' : `${lat.toFixed(5)}, ${lon.toFixed(5)}`;

/** "2m 14s" style elapsed time. */
export function formatDuration(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ${s % 60}s`;
  const h = Math.floor(m / 60);
  return `${h}h ${m % 60}m`;
}

export const severityColor: Record<Severity, 'default' | 'info' | 'warning' | 'error'> = {
  NORMAL: 'default',
  MINOR: 'info',
  MODERATE: 'warning',
  CRITICAL: 'error',
};

export const severityHex: Record<Severity, string> = {
  NORMAL: '#94a3b8',
  MINOR: '#38bdf8',
  MODERATE: '#f59e0b',
  CRITICAL: '#ef4444',
};

export const statusLabel: Record<CrashStatus, string> = {
  DETECTED: 'Detected',
  VERIFYING: 'Verifying',
  EMERGENCY_ACTIVE: 'Responders alerted',
  ACKNOWLEDGED: 'Seen by responders',
  RESOLVED: 'Resolved',
  CANCELLED: 'Cancelled',
};

export const statusHex: Record<CrashStatus, string> = {
  DETECTED: '#38bdf8',
  VERIFYING: '#f59e0b',
  EMERGENCY_ACTIVE: '#ef4444',
  ACKNOWLEDGED: '#f59e0b',
  RESOLVED: '#22c55e',
  CANCELLED: '#94a3b8',
};

const severityRank: Record<Severity, number> = { CRITICAL: 0, MODERATE: 1, MINOR: 2, NORMAL: 3 };

/** Queue order: nobody responding yet first, then severity, then newest. */
export function triageOrder<T extends { status: CrashStatus; severity: Severity; timestamp: string }>(a: T, b: T) {
  const waiting = (x: T) => (x.status === 'EMERGENCY_ACTIVE' ? 0 : 1);
  return (
    waiting(a) - waiting(b) ||
    severityRank[a.severity] - severityRank[b.severity] ||
    Date.parse(b.timestamp) - Date.parse(a.timestamp)
  );
}

export const recipientLabel: Record<RecipientType, string> = {
  POLICE: 'Police',
  HOSPITAL: 'Hospital / ambulance',
  EMERGENCY_CONTACT: 'Family',
};
