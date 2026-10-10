import { useEffect, useRef, useState } from 'react';
import type { BlackBox, DeviceData } from '../services/device';

/** A real crash record (not the phone's live "MONITORING" snapshot). */
export function isCrashRecord(bb?: BlackBox): boolean {
  return !!bb?.eventId && bb.eventId !== 'LIVE' && (bb.severity ?? '').toUpperCase() !== 'MONITORING';
}

/**
 * True only when the device reports a NEW collision: its impact counter rises above what this page
 * first saw (a reboot resets the baseline), or a crash record arrives. Nothing is hardcoded.
 */
export function useCollision(d?: DeviceData, bb?: BlackBox, backupAlert = false): boolean {
  const baseline = useRef<number | null>(null);
  const [deviceHit, setDeviceHit] = useState(false);
  const count = d?.count;

  useEffect(() => {
    if (count == null) return;
    if (baseline.current == null || count < baseline.current) {
      baseline.current = count; // first reading or device rebooted
      setDeviceHit(false);
    } else if (count > baseline.current) {
      setDeviceHit(true);
    }
  }, [count]);

  return deviceHit || isCrashRecord(bb) || backupAlert;
}
