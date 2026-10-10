import { useEffect, useState } from 'react';

/** Records the wall-clock time each flag was first observed true; clears all when `resetKey` changes. */
export function useFirstSeen(flags: Record<string, boolean>, resetKey: string): Record<string, Date | undefined> {
  const [seen, setSeen] = useState<Record<string, Date | undefined>>({});
  const [key, setKey] = useState(resetKey);
  const signature = Object.entries(flags).map(([k, v]) => `${k}:${v}`).join('|');

  useEffect(() => {
    if (key !== resetKey) {
      setKey(resetKey);
      setSeen({});
      return;
    }
    setSeen((prev) => {
      let next = prev;
      for (const [k, v] of Object.entries(flags)) {
        if (v && !prev[k]) next = { ...next, [k]: new Date() };
      }
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, resetKey]);

  return seen;
}
