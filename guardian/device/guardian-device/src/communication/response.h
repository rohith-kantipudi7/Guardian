#pragma once
// Backend response -> delivery decision. Shared rule with the simulator's event_store.py:
// POST /api/crashes answers 201 (new) or 200 {"duplicate": true} (already stored) - both mean
// the backend has the event, so the device stops retrying.

#include "guardian_types.h"

namespace guardian {

// httpStatus <= 0 means a transport failure (no HTTP response).
inline SendResult classifyResponse(int httpStatus) {
    if (httpStatus == 200 || httpStatus == 201) return SendResult::ACKED;
    if (httpStatus <= 0 || httpStatus >= 500 || httpStatus == 429 || httpStatus == 408) return SendResult::RETRY;
    return SendResult::REJECTED;
}

}  // namespace guardian
