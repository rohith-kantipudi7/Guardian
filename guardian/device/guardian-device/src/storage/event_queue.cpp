#include "storage/event_queue.h"

#include <algorithm>
#include <cstring>

namespace guardian {

void EventQueue::begin() {
    if (storage_ && storage_->load(items_.data(), sizeof(items_))) {
        for (const auto& it : items_) nextSeq_ = std::max(nextSeq_, it.seq + 1);
    } else {
        items_ = {};
    }
}

void EventQueue::persist() {
    if (storage_) storage_->save(items_.data(), sizeof(items_));
}

QueueItem* EventQueue::slotForInsert() {
    for (auto& it : items_)
        if (it.status == QueueStatus::EMPTY) return &it;
    // Evict the oldest delivered/failed event; never drop an undelivered one.
    QueueItem* oldest = nullptr;
    for (auto& it : items_) {
        if (it.status == QueueStatus::PENDING) continue;
        if (!oldest || it.seq < oldest->seq) oldest = &it;
    }
    return oldest;
}

bool EventQueue::enqueue(const CrashEvent& e, uint32_t nowMs) {
    if (find(e.eventId)) return true;
    QueueItem* slot = slotForInsert();
    if (!slot) return false;
    *slot = QueueItem{};
    slot->status = QueueStatus::PENDING;
    slot->nextAttemptMs = nowMs;
    slot->seq = nextSeq_++;
    slot->event = e;
    persist();
    return true;
}

size_t EventQueue::flush(Sender send, void* ctx, uint32_t nowMs, bool force, DeliveredCallback onDelivered,
                         void* cbCtx) {
    size_t delivered = 0;
    bool changed = false;
    for (auto& it : items_) {
        if (it.status != QueueStatus::PENDING) continue;
        if (!force && static_cast<int32_t>(nowMs - it.nextAttemptMs) < 0) continue;
        if (it.attempts < 255) it.attempts++;
        changed = true;
        switch (send(it.event, ctx)) {
            case SendResult::ACKED:
                it.status = QueueStatus::SENT;
                ++delivered;
                if (onDelivered) onDelivered(it.event, cbCtx);
                break;
            case SendResult::RETRY: {
                uint32_t shift = std::min<uint32_t>(it.attempts - 1, 16);
                uint32_t delay = std::min<uint32_t>(MAX_BACKOFF_MS, 1000u << shift);
                it.nextAttemptMs = nowMs + delay;
                break;
            }
            case SendResult::REJECTED:
                it.status = QueueStatus::FAILED;
                break;
        }
    }
    if (changed) persist();
    return delivered;
}

size_t EventQueue::pendingCount() const {
    return static_cast<size_t>(
        std::count_if(items_.begin(), items_.end(), [](const QueueItem& i) { return i.status == QueueStatus::PENDING; }));
}

const QueueItem* EventQueue::find(const char* eventId) const {
    for (const auto& it : items_)
        if (it.status != QueueStatus::EMPTY && std::strncmp(it.event.eventId, eventId, sizeof(it.event.eventId)) == 0)
            return &it;
    return nullptr;
}

size_t EventQueue::recent(const QueueItem** out, size_t max) const {
    std::array<const QueueItem*, EVENT_QUEUE_CAPACITY> all{};
    size_t n = 0;
    for (const auto& it : items_)
        if (it.status != QueueStatus::EMPTY) all[n++] = &it;
    std::sort(all.begin(), all.begin() + n, [](const QueueItem* a, const QueueItem* b) { return a->seq > b->seq; });
    size_t count = std::min(n, max);
    std::copy(all.begin(), all.begin() + count, out);
    return count;
}

void EventQueue::clear() {
    items_ = {};
    persist();
}

}  // namespace guardian
