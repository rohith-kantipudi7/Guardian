"""Guardian crash detection engine — Python reference implementation.

Mirrors device/guardian-device/src/crash (C++). Keep the two in sync:
same config defaults, same features, same scoring, same state machine.

Units: acceleration in m/s^2 (input) / g (features), gyro in rad/s, speed in km/h, time in ms.

This is a deterministic hackathon prototype, NOT an automotive-certified algorithm.
"""

from __future__ import annotations

import json
import math
from dataclasses import asdict, dataclass, field, fields
from enum import Enum
from pathlib import Path

G = 9.80665


@dataclass
class CrashConfig:
    gravity_alpha: float = 0.01
    impact_threshold_g: float = 2.5
    severe_impact_g: float = 8.0
    rotation_threshold_rad_s: float = 3.0
    severe_rotation_rad_s: float = 6.0
    speed_drop_threshold_kmh: float = 10.0
    severe_speed_drop_kmh: float = 60.0
    # A peak must be sustained for this many samples; rejects single-sample spikes.
    min_impact_samples: int = 3
    repeated_impact_gap_ms: int = 150
    analysis_window_ms: int = 1000
    verification_window_ms: int = 10000
    critical_skips_verification: bool = True
    post_event_cooldown_ms: int = 30000
    weight_impact: float = 0.45
    weight_rotation: float = 0.20
    weight_speed: float = 0.25
    weight_repeated: float = 0.10
    minor_confidence: float = 0.25
    moderate_confidence: float = 0.50
    critical_confidence: float = 0.85

    @classmethod
    def load(cls, path: str | Path | None) -> "CrashConfig":
        if not path or not Path(path).exists():
            return cls()
        data = json.loads(Path(path).read_text())
        known = {f.name for f in fields(cls)}
        return cls(**{k: v for k, v in data.items() if k in known})


@dataclass
class ImuSample:
    t_ms: int
    ax: float
    ay: float
    az: float
    gx: float
    gy: float
    gz: float


class Severity(str, Enum):
    NORMAL = "NORMAL"
    MINOR = "MINOR"
    MODERATE = "MODERATE"
    CRITICAL = "CRITICAL"


class CrashState(str, Enum):
    NORMAL = "NORMAL"
    SUSPECTED_IMPACT = "SUSPECTED_IMPACT"
    ANALYZING = "ANALYZING"
    FALSE_POSITIVE = "FALSE_POSITIVE"
    PROBABLE_CRASH = "PROBABLE_CRASH"
    VERIFYING = "VERIFYING"
    CANCELLED = "CANCELLED"
    EMERGENCY_ACTIVE = "EMERGENCY_ACTIVE"
    TRANSMIT_EVENT = "TRANSMIT_EVENT"
    WAIT_FOR_ACK = "WAIT_FOR_ACK"
    COOLDOWN = "COOLDOWN"


@dataclass
class CrashResult:
    trigger_ms: int
    acceleration_peak_g: float
    rotation_peak_rad_s: float
    robust_impact_g: float
    robust_rotation_rad_s: float
    impact_count: int
    speed_before_kmh: float | None
    speed_after_kmh: float | None
    confidence: float
    severity: Severity
    reasons: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["severity"] = self.severity.value
        return d


def classify(confidence: float, cfg: CrashConfig) -> Severity:
    if confidence >= cfg.critical_confidence:
        return Severity.CRITICAL
    if confidence >= cfg.moderate_confidence:
        return Severity.MODERATE
    if confidence >= cfg.minor_confidence:
        return Severity.MINOR
    return Severity.NORMAL


def _kth_largest(values: list[float], k: int) -> float:
    if not values:
        return 0.0
    ordered = sorted(values, reverse=True)
    return ordered[min(k, len(ordered)) - 1]


def _clamp01(v: float) -> float:
    return max(0.0, min(1.0, v))


def analyze_window(
    times: list[int],
    dyn_g: list[float],
    rot: list[float],
    speed_before: float | None,
    speed_after: float | None,
    trigger_ms: int,
    cfg: CrashConfig,
) -> CrashResult:
    robust_g = _kth_largest(dyn_g, cfg.min_impact_samples)
    robust_rot = _kth_largest(rot, cfg.min_impact_samples)

    impacts = 0
    last_above: int | None = None
    for t, g in zip(times, dyn_g):
        if g >= cfg.impact_threshold_g:
            if last_above is None or t - last_above >= cfg.repeated_impact_gap_ms:
                impacts += 1
            last_above = t

    reasons: list[str] = []
    sustained = robust_g >= cfg.impact_threshold_g or robust_rot >= cfg.rotation_threshold_rad_s
    if not sustained:
        confidence = 0.0
        reasons.append("single_sample_spike")
    else:
        weighted = cfg.weight_impact * _clamp01(robust_g / cfg.severe_impact_g)
        weighted += cfg.weight_rotation * _clamp01(robust_rot / cfg.severe_rotation_rad_s)
        weighted += cfg.weight_repeated * _clamp01((impacts - 1) / 2)
        total = cfg.weight_impact + cfg.weight_rotation + cfg.weight_repeated
        if speed_before is not None and speed_after is not None:
            drop = max(0.0, speed_before - speed_after)
            weighted += cfg.weight_speed * _clamp01(drop / cfg.severe_speed_drop_kmh)
            total += cfg.weight_speed
            if drop >= cfg.speed_drop_threshold_kmh:
                reasons.append("large_speed_drop")
            if speed_before >= 30:
                reasons.append("vehicle_speed_before_event")
        else:
            reasons.append("vehicle_speed_unavailable")
        confidence = round(weighted / total, 4)
        if robust_g >= cfg.impact_threshold_g:
            reasons.insert(0, "high_deceleration" if robust_g >= cfg.severe_impact_g / 2 else "impact")
        if robust_rot >= cfg.rotation_threshold_rad_s:
            reasons.append("rapid_rotation")
        if impacts >= 2:
            reasons.append("repeated_impacts")

    return CrashResult(
        trigger_ms=trigger_ms,
        acceleration_peak_g=round(max(dyn_g, default=0.0), 3),
        rotation_peak_rad_s=round(max(rot, default=0.0), 3),
        robust_impact_g=round(robust_g, 3),
        robust_rotation_rad_s=round(robust_rot, 3),
        impact_count=impacts,
        speed_before_kmh=speed_before,
        speed_after_kmh=speed_after,
        confidence=confidence,
        severity=classify(confidence, cfg),
        reasons=reasons,
    )


class CrashDetector:
    """Streaming crash detector + state machine (see docs/crash-detection.md)."""

    def __init__(self, cfg: CrashConfig | None = None):
        self.cfg = cfg or CrashConfig()
        self.state = CrashState.NORMAL
        self.transitions: list[tuple[int, CrashState]] = []
        self.last_result: CrashResult | None = None
        self.minor_events: list[CrashResult] = []
        self._gravity: tuple[float, float, float] | None = None
        self._speed: float | None = None
        self._trigger_ms = 0
        self._speed_before: float | None = None
        self._times: list[int] = []
        self._dyn: list[float] = []
        self._rot: list[float] = []
        self._verify_deadline = 0
        self._cooldown_until = 0
        self._now = 0

    # -- helpers ---------------------------------------------------------
    def _go(self, state: CrashState) -> None:
        self.state = state
        self.transitions.append((self._now, state))

    def _features(self, s: ImuSample) -> tuple[float, float]:
        if self._gravity is None:
            self._gravity = (s.ax, s.ay, s.az)
        gx, gy, gz = self._gravity
        dyn = math.sqrt((s.ax - gx) ** 2 + (s.ay - gy) ** 2 + (s.az - gz) ** 2) / G
        rot = math.sqrt(s.gx**2 + s.gy**2 + s.gz**2)
        return dyn, rot

    def _update_gravity(self, s: ImuSample) -> None:
        a = self.cfg.gravity_alpha
        gx, gy, gz = self._gravity  # type: ignore[misc]
        self._gravity = (gx + a * (s.ax - gx), gy + a * (s.ay - gy), gz + a * (s.az - gz))

    def _raise_emergency(self) -> CrashResult:
        self._go(CrashState.EMERGENCY_ACTIVE)
        self._cooldown_until = self._now + self.cfg.post_event_cooldown_ms
        self._go(CrashState.TRANSMIT_EVENT)
        return self.last_result  # type: ignore[return-value]

    # -- public API ------------------------------------------------------
    def process(self, s: ImuSample, speed_kmh: float | None = None) -> CrashResult | None:
        """Feed one sample. Returns a CrashResult exactly once when an emergency must be transmitted."""
        self._now = s.t_ms
        dyn, rot = self._features(s)

        if self.state in (CrashState.TRANSMIT_EVENT, CrashState.WAIT_FOR_ACK, CrashState.COOLDOWN):
            # Same incident: suppress re-triggers (secondary impacts, vehicle settling).
            if self._now >= self._cooldown_until:
                self._go(CrashState.NORMAL)
            else:
                self._speed = speed_kmh
                return None

        if self.state == CrashState.VERIFYING:
            self._speed = speed_kmh
            return self.tick(self._now)

        if self.state == CrashState.NORMAL:
            if dyn >= self.cfg.impact_threshold_g or rot >= self.cfg.rotation_threshold_rad_s:
                self._trigger_ms = s.t_ms
                self._speed_before = self._speed if self._speed is not None else speed_kmh
                self._times, self._dyn, self._rot = [], [], []
                self._go(CrashState.SUSPECTED_IMPACT)
            else:
                self._update_gravity(s)
                self._speed = speed_kmh
                return None

        # SUSPECTED_IMPACT: collect the analysis window.
        self._times.append(s.t_ms)
        self._dyn.append(dyn)
        self._rot.append(rot)
        self._speed = speed_kmh
        if s.t_ms - self._trigger_ms < self.cfg.analysis_window_ms:
            return None
        return self._analyze()

    def _analyze(self) -> CrashResult | None:
        self._go(CrashState.ANALYZING)
        result = analyze_window(
            self._times, self._dyn, self._rot, self._speed_before, self._speed, self._trigger_ms, self.cfg
        )
        self.last_result = result
        if result.severity in (Severity.NORMAL, Severity.MINOR):
            if result.severity == Severity.MINOR:
                self.minor_events.append(result)
            self._go(CrashState.FALSE_POSITIVE)
            self._go(CrashState.NORMAL)
            return None
        self._go(CrashState.PROBABLE_CRASH)
        if result.severity == Severity.CRITICAL and self.cfg.critical_skips_verification:
            return self._raise_emergency()
        self._verify_deadline = self._now + self.cfg.verification_window_ms
        self._go(CrashState.VERIFYING)
        return None

    def tick(self, now_ms: int) -> CrashResult | None:
        """Advance time without a sample (verification timeout / cooldown)."""
        self._now = max(self._now, now_ms)
        if self.state == CrashState.VERIFYING and self._now >= self._verify_deadline:
            return self._raise_emergency()
        if self.state == CrashState.SUSPECTED_IMPACT and self._now - self._trigger_ms >= self.cfg.analysis_window_ms:
            return self._analyze()
        return None

    def cancel(self) -> bool:
        """Occupant pressed I'M SAFE during verification."""
        if self.state != CrashState.VERIFYING:
            return False
        self._go(CrashState.CANCELLED)
        self._go(CrashState.NORMAL)
        return True

    def on_transmit_started(self) -> None:
        if self.state == CrashState.TRANSMIT_EVENT:
            self._go(CrashState.WAIT_FOR_ACK)

    def on_ack(self) -> None:
        if self.state == CrashState.WAIT_FOR_ACK:
            self._go(CrashState.COOLDOWN)

    @property
    def verify_deadline_ms(self) -> int:
        return self._verify_deadline
