import { keepPreviousData, useQuery } from '@tanstack/react-query';

export interface DeviceEvent {
  n: number;
  accel: number;
  jerk: number;
  gyro: number;
  t: number;
}

export interface DeviceData {
  accel: number;
  gyro: number;
  count: number;
  uptime: number;
  events: DeviceEvent[];
  // Set by the device when it escalates on its own (collision while the phone was offline).
  backupAlert?: boolean;
}

export interface PhoneData {
  accelG: number;
  rotationRadS: number;
  detection: boolean;
  state: string;
  phoneOnline: boolean;
}

export interface BlackBox {
  eventId?: string;
  severity?: string;
  confidence?: number;
  sensorLog?: string;
  audioSec?: number;
  preAudioSec?: number;
  images?: number;
  frames?: number;
  media?: string; // phone-hosted media server base URL
  checkpointAt?: number; // epoch ms of the last 10-minute audio+photo checkpoint
  thumbCabin?: string; // base64 JPEG thumbnails the device keeps if the phone is lost
  thumbRoad?: string;
  assessment?: string;
  location?: string; // "lat,lon"
  smsSent?: number;
  transcript?: string;
}

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return (await res.json()) as T;
}

/** Live collision + motion data straight from the ESP32 device (no backend, no simulator). */
export function useDeviceData(baseUrl: string) {
  return useQuery({
    queryKey: ['device-data', baseUrl],
    queryFn: ({ signal }) => getJson<DeviceData>(`${baseUrl}/data`, signal),
    refetchInterval: 2000,
    retry: false,
    // The single-threaded board sometimes starves a request; keep the last good value so the UI
    // doesn't flicker to "offline" on a transient timeout.
    placeholderData: keepPreviousData,
  });
}

/** Live phone telemetry the device received from the app over Wi-Fi. */
export function usePhoneData(baseUrl: string) {
  return useQuery({
    queryKey: ['device-phone', baseUrl],
    queryFn: ({ signal }) => getJson<PhoneData>(`${baseUrl}/phone`, signal),
    refetchInterval: 2000,
    retry: false,
    placeholderData: keepPreviousData,
  });
}

/** The last black box the phone streamed to the device. */
export function useBlackBox(baseUrl: string) {
  return useQuery({
    queryKey: ['device-blackbox', baseUrl],
    queryFn: ({ signal }) => getJson<BlackBox>(`${baseUrl}/blackbox`, signal),
    refetchInterval: 3000,
    retry: false,
    placeholderData: keepPreviousData,
  });
}
