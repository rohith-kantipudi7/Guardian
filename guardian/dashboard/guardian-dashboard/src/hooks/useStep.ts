import { useEffect, useState } from 'react';

/** Counts 0..max one step per `ms` once `active`; resets to 0 when inactive (drives the post-crash simulation). */
export function useStep(active: boolean, max: number, ms: number): number {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (!active) {
      setStep(0);
      return;
    }
    const t = setInterval(() => setStep((s) => (s >= max ? s : s + 1)), ms);
    return () => clearInterval(t);
  }, [active, max, ms]);
  return step;
}
