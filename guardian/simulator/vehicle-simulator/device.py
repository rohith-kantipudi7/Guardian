"""Virtual Guardian device: the software twin of the ESP32 firmware.

Emergency path:  sensors -> crash engine -> event store -> cellular (HTTP) -> backend
Companion path:  BLE notify -> phone (only if connected; never required)
"""

from __future__ import annotations

import math
import secrets
import time
import urllib.parse
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Callable

from crash_engine import G, CrashConfig, CrashDetector, CrashResult, CrashState, Severity
from event_store import EventStore, QueuedEvent
from network import NetworkError, SimulatedNetwork
from scenarios import SCENARIOS, Frame

TRACE_STEP = 4  # 100 Hz -> 25 Hz for charts

# Phone's on-device AI model used to understand the crash (runs offline, no network).
ON_DEVICE_AI_MODEL = "Gemma-3n (on-device)"
EMERGENCY_SEVERITIES = (Severity.MODERATE, Severity.CRITICAL)


@dataclass
class DeviceConfig:
    device_id: str = "GD-001"
    device_secret: str = "dev-secret-change-me"
    vehicle_id: str = "VH-001"
    firmware_version: str = "sim-0.1.0"


@dataclass
class DeviceState:
    phone_connected: bool = True
    battery: int = 92
    vehicle_connected: bool = True
    latitude: float = 12.9716
    longitude: float = 77.5946


@dataclass
class ScenarioOutcome:
    scenario: str
    result: CrashResult | None
    emergency: bool
    event: dict | None
    delivered: bool
    queued: QueuedEvent | None
    transitions: list[str]
    # (t_ms, dynamic accel g, rotation rad/s, speed km/h) downsampled for charts
    trace: list[tuple[int, float, float, float | None]] = field(default_factory=list)


def now_iso() -> str:
    return datetime.now(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def new_event_id(device_id: str) -> str:
    return f"EVT-{device_id}-{int(time.time() * 1000)}-{secrets.token_hex(2)}"


def cabin_photo_data_uri(event_id: str) -> str:
    """Self-contained SVG 'cabin photo' placeholder so the dashboard renders offline."""
    svg = (
        "<svg xmlns='http://www.w3.org/2000/svg' width='320' height='200'>"
        "<rect width='320' height='200' fill='#111827'/>"
        "<rect x='6' y='6' width='308' height='188' fill='none' stroke='#ef4444' stroke-width='2'/>"
        "<text x='16' y='30' fill='#f87171' font-family='monospace' font-size='13'>GUARDIAN BLACK BOX</text>"
        "<text x='16' y='106' fill='#e5e7eb' font-family='monospace' font-size='12'>CABIN CAM \u2014 POST IMPACT</text>"
        f"<text x='16' y='182' fill='#9ca3af' font-family='monospace' font-size='9'>{event_id}</text>"
        "<circle cx='292' cy='26' r='6' fill='#ef4444'/></svg>"
    )
    return "data:image/svg+xml;utf8," + urllib.parse.quote(svg)


class VirtualDevice:
    def __init__(
        self,
        cfg: DeviceConfig,
        state: DeviceState,
        network: SimulatedNetwork,
        store: EventStore,
        crash_cfg: CrashConfig | None = None,
        log: Callable[[str], None] = print,
    ):
        self.cfg = cfg
        self.state = state
        self.network = network
        self.store = store
        self.crash_cfg = crash_cfg or CrashConfig()
        self.log = log

    @property
    def headers(self) -> dict[str, str]:
        return {"X-Device-Secret": self.cfg.device_secret}

    # -- backend -----------------------------------------------------------
    def register(self) -> bool:
        body = {
            "deviceId": self.cfg.device_id,
            "deviceSecret": self.cfg.device_secret,
            "firmwareVersion": self.cfg.firmware_version,
            "vehicleId": self.cfg.vehicle_id,
            "serialNumber": f"SIM-{self.cfg.device_id}",
        }
        try:
            code, resp = self.network.request("POST", "/api/devices/register", body, {})
        except NetworkError as e:
            self.log(f"  [cellular] register failed: {e}")
            return False
        if code not in (200, 201):
            self.log(f"  [cellular] register rejected {code}: {resp.get('detail')}")
            return False
        return True

    def heartbeat(self) -> bool:
        body = {
            "deviceId": self.cfg.device_id,
            "timestamp": now_iso(),
            "batteryLevel": self.state.battery,
            "gpsStatus": "AVAILABLE",
            "cellularStatus": "CONNECTED",
            "bleStatus": "CONNECTED" if self.state.phone_connected else "ADVERTISING",
            "phoneConnected": self.state.phone_connected,
            "vehicleConnected": self.state.vehicle_connected,
            "latitude": self.state.latitude,
            "longitude": self.state.longitude,
            "firmwareVersion": self.cfg.firmware_version,
        }
        try:
            code, resp = self.network.request(
                "POST", f"/api/devices/{self.cfg.device_id}/heartbeat", body, self.headers
            )
        except NetworkError as e:
            self.log(f"  [cellular] heartbeat not sent: {e}")
            return False
        if code != 200:
            self.log(f"  [cellular] heartbeat rejected {code}: {resp.get('detail')}")
        return code == 200

    def flush(self, force: bool = False) -> list[QueuedEvent]:
        delivered = self.store.flush(self.network, self.headers, force=force)
        for item in delivered:
            self.log(f"  [cellular] ACK {item.event_id} -> {item.server_status}")
        return delivered

    # -- companion (BLE) ---------------------------------------------------
    def ble_notify(self, what: str) -> None:
        if self.state.phone_connected:
            self.log(f"  [BLE] -> phone: {what}")
        else:
            self.log(f"  [BLE] phone DISCONNECTED - skipped '{what}' (emergency path unaffected)")

    # -- crash pipeline ----------------------------------------------------
    def build_event(
        self,
        result: CrashResult,
        frame: Frame,
        *,
        event_id: str | None = None,
        alert_source: str = "DEVICE",
        phone_connected: bool | None = None,
        ai_assessment: str | None = None,
    ) -> dict:
        event = {
            "eventId": event_id or new_event_id(self.cfg.device_id),
            "deviceId": self.cfg.device_id,
            "vehicleId": self.cfg.vehicle_id,
            "timestamp": now_iso(),
            "latitude": round(frame.lat, 6),
            "longitude": round(frame.lon, 6),
            "speed": result.speed_before_kmh,
            "accelerationPeak": result.acceleration_peak_g,
            "rotationPeak": result.rotation_peak_rad_s,
            "confidence": round(result.confidence, 2),
            "severity": result.severity.value,
            "phoneConnected": self.state.phone_connected if phone_connected is None else phone_connected,
            "cellularConnected": self.network.connected,
            "alertSource": alert_source,
        }
        if ai_assessment is not None:
            event["aiAssessment"] = ai_assessment
        return event

    def _emergency(self, detector: CrashDetector, result: CrashResult, frame: Frame) -> tuple[dict, QueuedEvent, bool]:
        event = self.build_event(result, frame)
        self.state.latitude, self.state.longitude = event["latitude"], event["longitude"]
        self.log(
            f"  [crash] EMERGENCY {result.severity.value} confidence={result.confidence:.2f} "
            f"reasons={','.join(result.reasons)}"
        )
        queued = self.store.enqueue(event)
        self.log(f"  [store] persisted {event['eventId']}")
        self.ble_notify(f"CRASH_STATUS {result.severity.value}")
        detector.on_transmit_started()
        delivered = bool(self.flush(force=True))
        if delivered:
            detector.on_ack()
        else:
            self.log(f"  [cellular] not delivered yet ({queued.last_error}); queued for retry")
        return event, queued, delivered

    # -- phone-primary + device black-box fallback -------------------------
    def detect(self, name: str, with_speed: bool = True) -> tuple[CrashResult | None, Frame, bool]:
        """Run a scenario through the crash engine and return (result, impact_frame, is_emergency)."""
        frames = SCENARIOS[name](with_speed=with_speed)
        detector = CrashDetector(self.crash_cfg)
        result: CrashResult | None = None
        impact_frame = frames[-1]
        for frame in frames:
            emergency = detector.process(frame.sample, frame.speed_kmh)
            if emergency:
                result, impact_frame = emergency, frame
        if result is None and detector.state == CrashState.VERIFYING:
            result = detector.tick(detector.verify_deadline_ms)
        is_emergency = result is not None and result.severity in EMERGENCY_SEVERITIES
        return (result or detector.last_result), impact_frame, is_emergency

    def ai_assessment(self, result: CrashResult) -> str:
        """Mock on-device LLM (Gemma/LLaMA) situation summary built from the crash signals."""
        parts = [f"{result.severity.value.capitalize()} collision detected ({round(result.confidence * 100)}% confidence)."]
        if result.speed_before_kmh:
            parts.append(f"Impact at ~{result.speed_before_kmh:.0f} km/h with {result.acceleration_peak_g:.1f} g deceleration.")
        else:
            parts.append(f"Impact force {result.acceleration_peak_g:.1f} g.")
        if result.rotation_peak_rad_s and result.rotation_peak_rad_s > 4:
            parts.append("High rotation suggests a rollover.")
        parts.append("In-cabin audio: driver audible but distressed and asking for help.")
        parts.append("Recommendation: dispatch ambulance and police immediately.")
        return " ".join(parts)

    def capture_evidence(
        self, event_id: str, result: CrashResult, source: str, stored_by: str | None = None
    ) -> list[dict]:
        """Phone-captured (or device-held) black-box records for a crash."""
        ts = now_iso()
        speed = result.speed_before_kmh or 0.0
        sensor_log = (
            "t_ms,accel_g,rot_rad_s,speed_kmh\n"
            f"-200,1.0,0.1,{speed:.0f}\n"
            f"0,{result.acceleration_peak_g:.1f},{result.rotation_peak_rad_s:.1f},{speed * 0.4:.0f}\n"
            "+200,1.3,0.4,0"
        )
        transcript = (
            '"...oh god, are you okay?... I can\'t move my leg..." '
            '[impact] "...somebody help... please call someone..."'
        )
        base = {"source": source, "capturedAt": ts, "storedBy": stored_by}
        return [
            {**base, "kind": "AUDIO", "label": "In-cabin audio (12s)",
             "mediaUrl": f"blackbox://{self.cfg.device_id}/{event_id}/audio.aac", "mediaMime": "audio/aac"},
            {**base, "kind": "TRANSCRIPT", "label": "Audio transcript (on-device)", "contentText": transcript},
            {**base, "kind": "IMAGE", "label": "Cabin photo",
             "mediaUrl": cabin_photo_data_uri(event_id), "mediaMime": "image/svg+xml"},
            {**base, "kind": "SENSOR_LOG", "label": "Impact sensor log", "contentText": sensor_log},
        ]

    def send_crash_as_phone(self, event: dict) -> bool:
        try:
            code, resp = self.network.request("POST", "/api/crashes", event, self.headers)
        except NetworkError as e:
            self.log(f"  [phone] alert send FAILED: {e}")
            return False
        ok = code in (200, 201)
        self.log(f"  [phone] alert {'DELIVERED' if ok else f'rejected {code}: ' + str(resp.get('detail'))} "
                 f"({event['eventId']})")
        return ok

    def upload_evidence_as_phone(self, event_id: str, evidence: list[dict]) -> int:
        sent = 0
        for item in evidence:
            try:
                code, _ = self.network.request("POST", f"/api/crashes/{event_id}/evidence", item, {})
                sent += code in (200, 201)
            except NetworkError as e:
                self.log(f"  [phone] evidence upload failed: {e}")
        self.log(f"  [phone] uploaded {sent}/{len(evidence)} black-box record(s) to backend")
        return sent

    def upload_blackbox_as_device(self, event_id: str, evidence: list[dict]) -> bool:
        body = {"eventId": event_id, "evidence": evidence}
        try:
            code, resp = self.network.request(
                "POST", f"/api/devices/{self.cfg.device_id}/blackbox", body, self.headers
            )
        except NetworkError as e:
            self.log(f"  [blackbox] upload failed: {e}")
            return False
        ok = code in (200, 201)
        self.log(f"  [blackbox] {'stored & uploaded' if ok else f'rejected {code}'} {len(evidence)} record(s)")
        return ok

    def crash_with_phone(self, scenario: str = "severe_crash", phone_can_send: bool = True) -> dict:
        """Phone is primary (sensors + on-device AI); the device black box is the fallback.

        The phone detects the crash, understands it with on-device AI, captures evidence and
        streams a copy to the device over BLE. The phone sends the alert if it can; otherwise
        the device black box sends it. Backend dedup by eventId keeps it to one incident.
        """
        result, frame, is_emergency = self.detect(scenario)
        if not is_emergency or result is None:
            sev = result.severity.value if result else "NORMAL"
            self.log(f"  [crash] {scenario}: {sev} - below emergency threshold, nothing sent")
            return {}

        event_id = new_event_id(self.cfg.device_id)
        self.state.latitude, self.state.longitude = round(frame.lat, 6), round(frame.lon, 6)
        self.log(f"  [crash] {result.severity.value} impact detected on the PHONE's sensors "
                 f"(confidence={result.confidence:.2f})")
        assessment = self.ai_assessment(result)
        self.log(f"  [phone AI] {ON_DEVICE_AI_MODEL}: {assessment}")

        phone_evidence = self.capture_evidence(event_id, result, source="PHONE")
        self.log(f"  [BLE] phone -> device: streaming {len(phone_evidence)} black-box record(s)")
        device_evidence = self.capture_evidence(event_id, result, source="DEVICE", stored_by=self.cfg.device_id)
        self.log(f"  [store] device black box now holds {len(device_evidence)} record(s) (survives phone loss)")

        event = self.build_event(
            result, frame, event_id=event_id, alert_source="PHONE", phone_connected=True, ai_assessment=assessment
        )

        if phone_can_send:
            if self.send_crash_as_phone(event):
                self.upload_evidence_as_phone(event_id, phone_evidence)
                self.log("  [result] PHONE delivered the alert; device black box stayed on standby")
                return event
            self.log("  [phone] alert could not be delivered - device black box taking over")
        else:
            self.log("  [phone] DESTROYED / OFFLINE - no alert from phone; device black box taking over")

        # Device fallback: send the alert over its own cellular link + upload the stored black box.
        event = self.build_event(
            result, frame, event_id=event_id, alert_source="DEVICE", phone_connected=False, ai_assessment=assessment
        )
        queued = self.store.enqueue(event)
        self.log(f"  [store] persisted {event_id} on device")
        delivered = bool(self.flush(force=True))
        if delivered:
            self.upload_blackbox_as_device(event_id, device_evidence)
            self.log("  [result] DEVICE black box delivered the alert + stored evidence (phone was gone)")
        else:
            self.log(f"  [result] device queued the alert for retry ({queued.last_error})")
        return event

    def run_scenario(self, name: str, *, cancel_verification: bool = False, with_speed: bool = True) -> ScenarioOutcome:
        frames = SCENARIOS[name](with_speed=with_speed)
        detector = CrashDetector(self.crash_cfg)
        event = queued = None
        delivered = False
        result: CrashResult | None = None

        for frame in frames:
            emergency = detector.process(frame.sample, frame.speed_kmh)
            if emergency:
                result = emergency
                event, queued, delivered = self._emergency(detector, emergency, frame)

        g0 = frames[0].sample
        trace = [
            (
                f.sample.t_ms,
                round(
                    math.sqrt((f.sample.ax - g0.ax) ** 2 + (f.sample.ay - g0.ay) ** 2 + (f.sample.az - g0.az) ** 2) / G,
                    2,
                ),
                round(math.sqrt(f.sample.gx**2 + f.sample.gy**2 + f.sample.gz**2), 2),
                f.speed_kmh,
            )
            for f in frames[::TRACE_STEP]
        ]

        if detector.state == CrashState.VERIFYING:
            result = detector.last_result
            self.log(
                f"  [crash] PROBABLE {result.severity.value} confidence={result.confidence:.2f} "  # type: ignore[union-attr]
                f"- verifying for {self.crash_cfg.verification_window_ms / 1000:.0f}s"
            )
            self.ble_notify("VERIFY prompt (I'M SAFE?)")
            if cancel_verification:
                detector.cancel()
                self.log("  [crash] occupant pressed I'M SAFE - cancelled, nothing transmitted")
            else:
                emergency = detector.tick(detector.verify_deadline_ms)
                if emergency:
                    self.log("  [crash] no response within verification window")
                    event, queued, delivered = self._emergency(detector, emergency, frames[-1])

        result = result or detector.last_result
        if result and not event:
            self.log(
                f"  [crash] {result.severity.value} confidence={result.confidence:.2f} "
                f"reasons={','.join(result.reasons) or '-'} -> no emergency"
            )
        elif not result:
            self.log("  [crash] no impact detected")

        return ScenarioOutcome(
            scenario=name,
            result=result,
            emergency=event is not None,
            event=event,
            delivered=delivered,
            queued=queued,
            transitions=[s.value for _, s in detector.transitions],
            trace=trace,
        )
