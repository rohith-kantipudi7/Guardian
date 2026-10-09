#pragma once
// Persistent crash-event queue with retry/backoff and ACK semantics (pure C++).
// Mirrors simulator/vehicle-simulator/event_store.py.

#include <array>
#include <cstdint>

#include "guardian_types.h"

namespace guardian {

enum class QueueStatus : uint8_t { EMPTY = 0, PENDING, SENT, FAILED };

struct QueueItem {
    QueueStatus status = QueueStatus::EMPTY;
    uint8_t attempts = 0;
    uint32_t nextAttemptMs = 0;
    uint32_t seq = 0;  // insertion order; oldest SENT items are evicted first
    CrashEvent event;
};

constexpr size_t EVENT_QUEUE_CAPACITY = 16;
constexpr uint32_t MAX_BACKOFF_MS = 60000;

// Persistence backend: ESP32 uses LittleFS, native tests use memory.
class QueueStorage {
public:
    virtual ~QueueStorage() = default;
    virtual bool load(void* data, size_t len) = 0;
    virtual bool save(const void* data, size_t len) = 0;
};

class EventQueue {
public:
    using Sender = SendResult (*)(const CrashEvent&, void* ctx);
    using DeliveredCallback = void (*)(const CrashEvent&, void* ctx);

    explicit EventQueue(QueueStorage* storage = nullptr) : storage_(storage) {}

    void begin();
    // Returns false if the queue is full of undelivered events (should never happen in practice).
    bool enqueue(const CrashEvent& e, uint32_t nowMs);
    // Attempts every due PENDING event. Returns number delivered.
    size_t flush(Sender send, void* ctx, uint32_t nowMs, bool force = false, DeliveredCallback onDelivered = nullptr,
                 void* cbCtx = nullptr);

    size_t pendingCount() const;
    const QueueItem* find(const char* eventId) const;
    // Most recent events first, any status (for BLE history replay).
    size_t recent(const QueueItem** out, size_t max) const;
    void clear();

private:
    void persist();
    QueueItem* slotForInsert();

    QueueStorage* storage_;
    std::array<QueueItem, EVENT_QUEUE_CAPACITY> items_{};
    uint32_t nextSeq_ = 1;
};

}  // namespace guardian
