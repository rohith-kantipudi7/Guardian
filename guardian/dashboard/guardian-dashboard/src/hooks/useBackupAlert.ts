import { useCallback, useEffect, useRef, useState } from 'react';
import type { DeviceData, PhoneData } from '../services/device';

export type BackupReason = 'device-flag' | 'phone-offline-collision' | null;

export interface BackupAlertState {
  active: boolean;
  reason: BackupReason;
  atMs: number | null;
}

/**
 * Raises a backup alert when the IoT device detects a collision while the phone is offline — either
 * because the device said so (`backupAlert` flag) or, if the sketch doesn't set it yet, inferred
 * from the collision count rising while the phone link is down. Stays up until acknowledged.
 */
export function useBackupAlert(d?: DeviceData, p?: PhoneData): [BackupAlertState, () => void] {
  const lastCount = useRef<number | null>(null);
  const [triggeredAt, setTriggeredAt] = useState<number | null>(null);
  const [reason, setReason] = useState<BackupReason>(null);
  const [ackedAt, setAckedAt] = useState(0);

  useEffect(() => {
    if (!d) return;
    const prev = lastCount.current;
    const phoneOffline = p ? !p.phoneOnline : false;
    if (d.backupAlert) {
      setTriggeredAt(Date.now());
      setReason('device-flag');
    } else if (prev != null && d.count > prev && phoneOffline) {
      setTriggeredAt(Date.now());
      setReason('phone-offline-collision');
    }
    lastCount.current = d.count;
  }, [d, p]);

  const active = triggeredAt != null && triggeredAt > ackedAt;
  const ack = useCallback(() => setAckedAt(Date.now()), []);
  return [{ active, reason: active ? reason : null, atMs: active ? triggeredAt : null }, ack];
}
