// Mirrors the backend contract in docs/api.md. Do not change silently.

export type Severity = 'NORMAL' | 'MINOR' | 'MODERATE' | 'CRITICAL';

export type CrashStatus =
  | 'DETECTED'
  | 'VERIFYING'
  | 'CANCELLED'
  | 'EMERGENCY_ACTIVE'
  | 'ACKNOWLEDGED'
  | 'RESOLVED';

export const ACTIVE_STATUSES: CrashStatus[] = ['DETECTED', 'VERIFYING', 'EMERGENCY_ACTIVE', 'ACKNOWLEDGED'];

export type AlertSource = 'PHONE' | 'DEVICE';

export type EvidenceKind = 'AUDIO' | 'IMAGE' | 'SENSOR_LOG' | 'TRANSCRIPT' | 'AI_ASSESSMENT';

export interface BlackBoxEvidence {
  kind: EvidenceKind;
  source: AlertSource;
  label: string;
  contentText: string | null;
  mediaUrl: string | null;
  mediaMime: string | null;
  capturedAt: string;
  storedBy: string | null;
  createdAt: string;
}

export interface EmergencyAction {
  channel: 'POLICE' | 'HOSPITAL' | 'EMERGENCY_CONTACT' | 'DRIVER' | 'TOW' | 'RESPONSE_CENTER' | string;
  status: string;
  message: string;
  operator: string | null;
  createdAt: string;
}

export interface CrashEvent {
  eventId: string;
  deviceId: string;
  vehicleId: string | null;
  timestamp: string;
  latitude: number | null;
  longitude: number | null;
  speed: number | null;
  accelerationPeak: number | null;
  rotationPeak: number | null;
  confidence: number;
  severity: Severity;
  phoneConnected: boolean;
  cellularConnected: boolean;
  status: CrashStatus;
  alertSource: AlertSource | null;
  aiAssessment: string | null;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  closedBy: string | null;
  closedAt: string | null;
  resolution: string | null;
  createdAt: string;
  updatedAt: string;
  actions: EmergencyAction[];
  notifications: Notification[];
  evidence: BlackBoxEvidence[];
}

export type RecipientType = 'POLICE' | 'HOSPITAL' | 'EMERGENCY_CONTACT';

export interface Notification {
  recipientType: RecipientType;
  recipientName: string;
  recipientPhone: string | null;
  token: string;
  message: string;
  sentAt: string;
  openedAt: string | null;
}

export interface Contact {
  name: string;
  phone: string;
  relationship?: string | null;
}

export interface AlertView {
  recipientType: RecipientType;
  recipientName: string;
  eventId: string;
  status: CrashStatus;
  severity: Severity;
  confidence: number;
  timestamp: string;
  latitude: number | null;
  longitude: number | null;
  mapsUrl: string | null;
  speed: number | null;
  accelerationPeak: number | null;
  rotationPeak: number | null;
  phoneConnected: boolean;
  cellularConnected: boolean;
  vehicleRegistration: string | null;
  vehicleDescription: string | null;
  driver: Contact | null;
  emergencyContacts: Contact[];
  responders: Pick<Notification, 'recipientType' | 'recipientName' | 'openedAt'>[];
  closedBy: string | null;
  resolution: string | null;
}

export interface Stats {
  activeIncidents: number;
  criticalActive: number;
  totalIncidents: number;
  phoneIndependentIncidents: number;
  devicesTotal: number;
  devicesOnline: number;
  avgAcknowledgeSeconds: number | null;
}

export interface Device {
  id: string;
  serialNumber: string | null;
  firmwareVersion: string | null;
  vehicleId: string | null;
  batteryLevel: number | null;
  gpsStatus: string | null;
  cellularStatus: string | null;
  bleStatus: string | null;
  phoneConnected: boolean;
  vehicleConnected: boolean;
  lastLatitude: number | null;
  lastLongitude: number | null;
  lastHeartbeat: string | null;
  createdAt: string;
  online: boolean;
}

export interface Vehicle {
  id: string;
  userId: string | null;
  registrationNumber: string;
  make: string | null;
  model: string | null;
  year: number | null;
  deviceId: string | null;
  createdAt: string;
}

export interface User {
  id: string;
  name: string;
  phone: string;
  emergencyContacts: { id: number; name: string; phone: string; relationship: string | null }[];
}

export type WsMessage =
  | { type: 'CONNECTED' }
  | { type: 'PONG' }
  | { type: 'CRASH_DETECTED'; event: CrashEvent }
  | { type: 'CRASH_UPDATED'; event: CrashEvent }
  | { type: 'DEVICE_HEARTBEAT'; device: Device }
  | { type: 'DEVICE_REGISTERED'; device: Device }
  | { type: 'DEMO_RESET' };
