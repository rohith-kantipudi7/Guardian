"""Synthetic but internally consistent vehicle sensor scenarios (100 Hz).

Body frame: +x forward, +y left, +z up. Gravity reads +9.81 on z when level.
Frontal deceleration appears as negative ax. Speed decreases consistently with deceleration.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass

from crash_engine import G, ImuSample

RATE_HZ = 100
DT_MS = 1000 // RATE_HZ
BASE_LAT, BASE_LON = 12.9716, 77.5946


@dataclass
class Frame:
    sample: ImuSample
    speed_kmh: float | None
    lat: float
    lon: float


class _Builder:
    def __init__(self, seed: int, speed_kmh: float, with_speed: bool = True):
        self.rng = random.Random(seed)
        self.t = 0
        self.speed = speed_kmh
        self.with_speed = with_speed
        self.lat, self.lon = BASE_LAT, BASE_LON
        self.frames: list[Frame] = []

    def step(self, ax: float = 0.0, ay: float = 0.0, az: float = 0.0, gx=0.0, gy=0.0, gz=0.0, noise=0.3):
        n = lambda s: self.rng.gauss(0, s)  # noqa: E731
        # Integrate longitudinal acceleration into speed (never negative).
        self.speed = max(0.0, self.speed + ax * (DT_MS / 1000) * 3.6)
        # Move north-east-ish proportional to speed (~111 km per degree).
        d_km = self.speed * DT_MS / 3_600_000
        self.lat += d_km / 111.0 * 0.7
        self.lon += d_km / 111.0 * 0.7
        sample = ImuSample(
            t_ms=self.t,
            ax=ax + n(noise),
            ay=ay + n(noise),
            az=G + az + n(noise),
            gx=gx + n(0.03),
            gy=gy + n(0.03),
            gz=gz + n(0.03),
        )
        self.frames.append(Frame(sample, round(self.speed, 2) if self.with_speed else None, self.lat, self.lon))
        self.t += DT_MS

    def cruise(self, ms: int, yaw: float = 0.0):
        for _ in range(ms // DT_MS):
            self.step(ax=self.rng.gauss(0, 0.2), gz=yaw)

    def pulse(self, ms: int, peak_g: float, axis: str = "x", gyro: tuple[float, float, float] = (0, 0, 0)):
        """Half-sine acceleration pulse (typical crash pulse shape)."""
        n = ms // DT_MS
        for i in range(n):
            a = -peak_g * G * math.sin(math.pi * (i + 0.5) / n)
            kw = {"ax": 0.0, "ay": 0.0, "az": 0.0}
            kw["a" + axis] = a
            self.step(**kw, gx=gyro[0], gy=gyro[1], gz=gyro[2], noise=0.5)


def normal(seed: int = 1, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 50, with_speed)
    b.cruise(4000)
    b.cruise(1500, yaw=0.3)  # lane change
    b.cruise(4500)
    return b.frames


def hard_brake(seed: int = 2, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 60, with_speed)
    b.cruise(2000)
    while b.speed > 0:
        b.step(ax=-0.9 * G, gy=0.05)  # 0.9 g emergency stop, slight pitch
    b.cruise(1500)
    return b.frames


def pothole(seed: int = 3, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 40, with_speed)
    b.cruise(2000)
    b.step(az=3.5 * G, noise=0.5)  # single-sample vertical spike
    b.step(az=1.5 * G, noise=0.5)
    b.cruise(2500)
    return b.frames


def minor_impact(seed: int = 4, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 20, with_speed)
    b.cruise(2000)
    b.pulse(80, 5.0, gyro=(0, 0, 0.5))
    while b.speed > 8:
        b.step(ax=-0.4 * G)
    b.cruise(1500)
    return b.frames


def severe_crash(seed: int = 5, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 72, with_speed)
    b.cruise(2000)
    b.pulse(120, 10.0, gyro=(0.5, 0.5, 2.0))  # primary frontal impact
    for _ in range(30):  # 300 ms spin
        b.step(ax=-2.0 * G, gz=5.0, noise=0.8)
    b.pulse(60, 4.0, axis="y", gyro=(0, 0, 3.0))  # secondary side impact
    while b.speed > 0:
        b.step(ax=-1.5 * G)
    b.cruise(2000)
    return b.frames


def rollover(seed: int = 6, with_speed: bool = True) -> list[Frame]:
    b = _Builder(seed, 50, with_speed)
    b.cruise(2000)
    for k in range(3):  # three ground contacts while rolling at ~7 rad/s
        b.pulse(50, 3.5, axis="z", gyro=(7.0, 0, 0))
        for _ in range(15):
            b.step(ax=-1.2 * G, gx=7.0, noise=0.6)
    while b.speed > 0:
        b.step(ax=-1.0 * G)
    b.cruise(2000)
    return b.frames


SCENARIOS = {
    "normal": normal,
    "hard_brake": hard_brake,
    "pothole": pothole,
    "minor_impact": minor_impact,
    "severe_crash": severe_crash,
    "rollover": rollover,
}
