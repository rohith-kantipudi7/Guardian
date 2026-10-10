import { Box, Stack, Typography } from '@mui/material';
import type { TracePoint } from '../services/simulator';

const W = 640;
const H = 200;
const PAD = { l: 34, r: 34, t: 10, b: 22 };
const IMPACT_THRESHOLD_G = 2.5;

/** Synthetic sensor trace of the last scenario: dynamic acceleration (g) and rotation (rad/s). */
export function ImpactChart({ trace }: { trace: TracePoint[] }) {
  if (trace.length < 2) {
    return (
      <Box sx={{ height: H, display: 'grid', placeItems: 'center', color: 'text.secondary' }}>
        Run a scenario to see the sensor signal
      </Box>
    );
  }
  const t0 = trace[0][0];
  const tMax = trace[trace.length - 1][0] - t0 || 1;
  const gMax = Math.max(4, ...trace.map((p) => p[1])) * 1.1;
  const rMax = Math.max(4, ...trace.map((p) => p[2])) * 1.1;
  const x = (t: number) => PAD.l + ((t - t0) / tMax) * (W - PAD.l - PAD.r);
  const yG = (g: number) => H - PAD.b - (g / gMax) * (H - PAD.t - PAD.b);
  const yR = (r: number) => H - PAD.b - (r / rMax) * (H - PAD.t - PAD.b);
  const path = (sel: (p: TracePoint) => number, y: (v: number) => number) =>
    trace.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(sel(p)).toFixed(1)}`).join(' ');

  return (
    <Box>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Impact signal">
        {[0, 0.25, 0.5, 0.75, 1].map((f) => (
          <line key={f} x1={PAD.l} x2={W - PAD.r} y1={yG(gMax * f)} y2={yG(gMax * f)} stroke="#CBD3DF" />
        ))}
        <line
          x1={PAD.l}
          x2={W - PAD.r}
          y1={yG(IMPACT_THRESHOLD_G)}
          y2={yG(IMPACT_THRESHOLD_G)}
          stroke="#B7791F"
          strokeDasharray="6 4"
        />
        <text x={W - PAD.r - 4} y={yG(IMPACT_THRESHOLD_G) - 4} fill="#B7791F" fontSize="11" textAnchor="end">
          impact trigger {IMPACT_THRESHOLD_G} g
        </text>
        <path d={path((p) => p[2], yR)} fill="none" stroke="#52617A" strokeWidth="1.5" opacity={0.85} />
        <path d={path((p) => p[1], yG)} fill="none" stroke="#D92D4A" strokeWidth="2" />
        <text x={4} y={PAD.t + 10} fill="#D92D4A" fontSize="11">
          {gMax.toFixed(0)} g
        </text>
        <text x={W - 4} y={PAD.t + 10} fill="#52617A" fontSize="11" textAnchor="end">
          {rMax.toFixed(0)} rad/s
        </text>
        <text x={PAD.l} y={H - 4} fill="#4B5870" fontSize="11">
          0 s
        </text>
        <text x={W - PAD.r} y={H - 4} fill="#4B5870" fontSize="11" textAnchor="end">
          {(tMax / 1000).toFixed(1)} s
        </text>
      </svg>
      <Stack direction="row" spacing={2} sx={{ justifyContent: 'center' }}>
        <Typography variant="caption" sx={{ color: '#D92D4A' }}>
          ━ acceleration (g)
        </Typography>
        <Typography variant="caption" sx={{ color: '#52617A' }}>
          ━ rotation (rad/s)
        </Typography>
      </Stack>
    </Box>
  );
}
