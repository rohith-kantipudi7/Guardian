import { useQuery } from '@tanstack/react-query';

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
  assessment?: string;
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
    refetchInterval: 1000,
    retry: false,
  });
}

/** Live phone telemetry the device received from the app over Wi-Fi. */
export function usePhoneData(baseUrl: string) {
  return useQuery({
    queryKey: ['device-phone', baseUrl],
    queryFn: ({ signal }) => getJson<PhoneData>(`${baseUrl}/phone`, signal),
    refetchInterval: 1000,
    retry: false,
  });
}

/** The last black box the phone streamed to the device. */
export function useBlackBox(baseUrl: string) {
  return useQuery({
    queryKey: ['device-blackbox', baseUrl],
    queryFn: ({ signal }) => getJson<BlackBox>(`${baseUrl}/blackbox`, signal),
    refetchInterval: 2000,
    retry: false,
  });
}
