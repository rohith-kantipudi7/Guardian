import pytest

from crash_engine import CrashConfig, CrashDetector, CrashState, ImuSample, Severity, analyze_window
from scenarios import SCENARIOS


def run(name: str, cfg: CrashConfig | None = None, with_speed: bool = True):
    det = CrashDetector(cfg)
    emergencies = []
    for f in SCENARIOS[name](with_speed=with_speed):
        r = det.process(f.sample, f.speed_kmh)
        if r:
            emergencies.append(r)
            det.on_transmit_started()
            det.on_ack()
    return det, emergencies


@pytest.mark.parametrize("name", ["normal", "hard_brake"])
def test_no_trigger_in_normal_driving(name):
    det, emergencies = run(name)
    assert emergencies == []
    assert det.last_result is None
    assert det.state == CrashState.NORMAL


def test_pothole_single_spike_is_false_positive():
    det, emergencies = run("pothole")
    assert emergencies == []
    assert det.last_result is not None
    assert det.last_result.severity == Severity.NORMAL
    assert "single_sample_spike" in det.last_result.reasons
    states = [s for _, s in det.transitions]
    assert CrashState.FALSE_POSITIVE in states


def test_minor_impact_is_minor_without_emergency():
    det, emergencies = run("minor_impact")
    assert emergencies == []
    assert det.last_result.severity == Severity.MINOR
    assert len(det.minor_events) == 1


def test_severe_crash_is_critical_and_raised_once():
    det, emergencies = run("severe_crash")
    assert len(emergencies) == 1, "secondary impacts must not create duplicate events"
    r = emergencies[0]
    assert r.severity == Severity.CRITICAL
    assert r.confidence >= 0.85
    assert r.impact_count >= 2
    assert {"high_deceleration", "rapid_rotation", "repeated_impacts", "large_speed_drop"} <= set(r.reasons)
    states = [s.value for _, s in det.transitions]
    assert states[:5] == ["SUSPECTED_IMPACT", "ANALYZING", "PROBABLE_CRASH", "EMERGENCY_ACTIVE", "TRANSMIT_EVENT"]


def test_severe_crash_without_vehicle_data_still_critical():
    _, emergencies = run("severe_crash", with_speed=False)
    assert len(emergencies) == 1
    assert emergencies[0].severity == Severity.CRITICAL
    assert "vehicle_speed_unavailable" in emergencies[0].reasons


def test_rollover_rotation_goes_through_verification():
    det = CrashDetector()
    for f in SCENARIOS["rollover"]():
        assert det.process(f.sample, f.speed_kmh) is None
    assert det.state == CrashState.VERIFYING
    assert det.last_result.severity == Severity.MODERATE
    assert "rapid_rotation" in det.last_result.reasons
    result = det.tick(det.verify_deadline_ms)
    assert result is not None and det.state == CrashState.TRANSMIT_EVENT


def test_verification_can_be_cancelled():
    det = CrashDetector()
    for f in SCENARIOS["rollover"]():
        det.process(f.sample, f.speed_kmh)
    assert det.cancel() is True
    assert det.state == CrashState.NORMAL
    assert det.tick(det.verify_deadline_ms + 1) is None


def test_critical_can_require_verification_when_configured():
    det, emergencies = run("severe_crash", CrashConfig(critical_skips_verification=False))
    assert emergencies == []
    assert det.state == CrashState.VERIFYING


def test_cooldown_suppresses_retrigger_then_rearms():
    cfg = CrashConfig(post_event_cooldown_ms=2000)
    det = CrashDetector(cfg)
    frames = SCENARIOS["severe_crash"]()
    hits = [r for f in frames if (r := det.process(f.sample, f.speed_kmh))]
    assert len(hits) == 1
    # A second, separate crash after the cooldown must be detected again.
    t0 = frames[-1].sample.t_ms + 3000
    second = [
        r
        for f in SCENARIOS["severe_crash"](seed=9)
        if (r := det.process(ImuSample(**{**f.sample.__dict__, "t_ms": f.sample.t_ms + t0}), f.speed_kmh))
    ]
    assert len(second) == 1


def test_repeated_impacts_counted():
    times = list(range(0, 1000, 10))
    dyn = [0.1] * 100
    for start in (0, 30, 60):  # three bursts, each 4 samples, separated by >150 ms
        for i in range(start, start + 4):
            dyn[i] = 4.0
    r = analyze_window(times, dyn, [0.0] * 100, 50, 10, 0, CrashConfig())
    assert r.impact_count == 3
    assert "repeated_impacts" in r.reasons


def test_thresholds_are_configurable():
    strict = CrashConfig(minor_confidence=0.9, moderate_confidence=0.97, critical_confidence=0.99)
    det, emergencies = run("severe_crash", strict)
    assert emergencies == []
    assert det.last_result.severity in (Severity.NORMAL, Severity.MINOR)
