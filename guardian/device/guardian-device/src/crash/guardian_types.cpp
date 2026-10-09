#include <cstdio>
#include <cstring>

#include "guardian_types.h"

namespace guardian {

const char* toString(Severity s) {
    switch (s) {
        case Severity::NORMAL: return "NORMAL";
        case Severity::MINOR: return "MINOR";
        case Severity::MODERATE: return "MODERATE";
        case Severity::CRITICAL: return "CRITICAL";
    }
    return "?";
}

const char* toString(CrashState s) {
    switch (s) {
        case CrashState::NORMAL: return "NORMAL";
        case CrashState::SUSPECTED_IMPACT: return "SUSPECTED_IMPACT";
        case CrashState::ANALYZING: return "ANALYZING";
        case CrashState::FALSE_POSITIVE: return "FALSE_POSITIVE";
        case CrashState::PROBABLE_CRASH: return "PROBABLE_CRASH";
        case CrashState::VERIFYING: return "VERIFYING";
        case CrashState::CANCELLED: return "CANCELLED";
        case CrashState::EMERGENCY_ACTIVE: return "EMERGENCY_ACTIVE";
        case CrashState::TRANSMIT_EVENT: return "TRANSMIT_EVENT";
        case CrashState::WAIT_FOR_ACK: return "WAIT_FOR_ACK";
        case CrashState::COOLDOWN: return "COOLDOWN";
    }
    return "?";
}

const char* toString(GpsStatus s) {
    switch (s) {
        case GpsStatus::AVAILABLE: return "AVAILABLE";
        case GpsStatus::NO_FIX: return "NO_FIX";
        case GpsStatus::UNAVAILABLE: return "UNAVAILABLE";
    }
    return "?";
}

const char* toString(CellularStatus s) {
    switch (s) {
        case CellularStatus::CONNECTED: return "CONNECTED";
        case CellularStatus::CONNECTING: return "CONNECTING";
        case CellularStatus::DISCONNECTED: return "DISCONNECTED";
        case CellularStatus::UNAVAILABLE: return "UNAVAILABLE";
    }
    return "?";
}

void reasonsToString(uint16_t reasons, char* buf, size_t len) {
    static const struct {
        uint16_t flag;
        const char* name;
    } names[] = {
        {REASON_HIGH_DECELERATION, "high_deceleration"}, {REASON_IMPACT, "impact"},
        {REASON_LARGE_SPEED_DROP, "large_speed_drop"},   {REASON_SPEED_BEFORE_EVENT, "vehicle_speed_before_event"},
        {REASON_RAPID_ROTATION, "rapid_rotation"},       {REASON_REPEATED_IMPACTS, "repeated_impacts"},
        {REASON_SPEED_UNAVAILABLE, "vehicle_speed_unavailable"}, {REASON_SINGLE_SPIKE, "single_sample_spike"},
    };
    if (len == 0) return;
    buf[0] = '\0';
    size_t used = 0;
    for (const auto& n : names) {
        if (!(reasons & n.flag)) continue;
        int w = std::snprintf(buf + used, len - used, "%s%s", used ? "," : "", n.name);
        if (w < 0 || static_cast<size_t>(w) >= len - used) break;
        used += static_cast<size_t>(w);
    }
}

}  // namespace guardian
