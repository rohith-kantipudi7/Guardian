import { useQuery } from '@tanstack/react-query';
import { api, type CrashFilters } from './api';
import { simulator } from './simulator';

// Device "online" is computed server-side from heartbeat age, so poll as a safety net.
const DEVICE_REFRESH_MS = 10000;

export const useCrashes = (filters: CrashFilters = {}) =>
  useQuery({ queryKey: ['crashes', filters], queryFn: () => api.listCrashes(filters) });

export const useCrash = (eventId: string | undefined) =>
  useQuery({ queryKey: ['crash', eventId], queryFn: () => api.getCrash(eventId!), enabled: !!eventId });

export const useDevices = () =>
  useQuery({ queryKey: ['devices'], queryFn: api.listDevices, refetchInterval: DEVICE_REFRESH_MS });

export const useVehicles = () => useQuery({ queryKey: ['vehicles'], queryFn: api.listVehicles });

export const useUser = (userId: string | null | undefined) =>
  useQuery({ queryKey: ['user', userId], queryFn: () => api.getUser(userId!), enabled: !!userId, staleTime: 60_000 });

export const useStats = () => useQuery({ queryKey: ['stats'], queryFn: api.stats, refetchInterval: DEVICE_REFRESH_MS });

export const useSimState = () =>
  useQuery({ queryKey: ['sim'], queryFn: simulator.state, refetchInterval: 2000, retry: false });
