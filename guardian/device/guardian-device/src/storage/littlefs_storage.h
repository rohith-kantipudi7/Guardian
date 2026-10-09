#pragma once
// Flash persistence for the event queue (survives power loss after a crash).

#include <LittleFS.h>

#include "storage/event_queue.h"

namespace guardian {

class LittleFsQueueStorage : public QueueStorage {
public:
    bool begin() { return LittleFS.begin(true); }

    bool load(void* data, size_t len) override {
        File f = LittleFS.open(PATH, "r");
        if (!f || f.size() != len) return false;
        bool ok = f.read(static_cast<uint8_t*>(data), len) == len;
        f.close();
        return ok;
    }

    bool save(const void* data, size_t len) override {
        File f = LittleFS.open(TMP, "w");
        if (!f) return false;
        bool ok = f.write(static_cast<const uint8_t*>(data), len) == len;
        f.close();
        // Write-then-rename so a power cut never leaves a half-written queue.
        return ok && LittleFS.rename(TMP, PATH);
    }

private:
    static constexpr const char* PATH = "/queue.bin";
    static constexpr const char* TMP = "/queue.tmp";
};

}  // namespace guardian
