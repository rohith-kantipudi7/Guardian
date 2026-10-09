// Native unit tests: pio test -e native
#include <unity.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "communication/response.h"
#include "crash/crash_detector.h"
#include "sim/scenario_player.h"
#include "storage/event_queue.h"

using namespace guardian;

namespace {

struct RunResult {
    int emergencies = 0;
    CrashDetector det;
    std::vector<CrashState> states;
};

void record(CrashState s, void* ctx) { static_cast<RunResult*>(ctx)->states.push_back(s); }

void runScenario(RunResult& rr, const char* name, bool withSpeed = true, const CrashConfig& cfg = CrashConfig()) {
    rr.det = CrashDetector(cfg);
    rr.det.setListener(record, &rr);
    ScenarioPlayer p;
    TEST_ASSERT_TRUE(p.load(name, 7));
    uint32_t t = 0;
    while (!p.finished()) {
        SensorSample s;
        float speed;
        p.next(t, s, speed);
        if (rr.det.process(s, withSpeed, speed)) {
            rr.emergencies++;
            rr.det.onTransmitStarted();
            rr.det.onAck();
        }
        t += ScenarioPlayer::DT_MS;
    }
}

bool contains(const std::vector<CrashState>& v, CrashState s) {
    for (auto x : v)
        if (x == s) return true;
    return false;
}

}  // namespace

void test_normal_driving_no_trigger() {
    RunResult rr;
    runScenario(rr, "normal");
    TEST_ASSERT_EQUAL(0, rr.emergencies);
    TEST_ASSERT_FALSE(rr.det.hasResult());
}

void test_hard_brake_no_trigger() {
    RunResult rr;
    runScenario(rr, "hard_brake");
    TEST_ASSERT_EQUAL(0, rr.emergencies);
    TEST_ASSERT_FALSE(rr.det.hasResult());
}

void test_pothole_single_spike_false_positive() {
    RunResult rr;
    runScenario(rr, "pothole");
    TEST_ASSERT_EQUAL(0, rr.emergencies);
    TEST_ASSERT_TRUE(rr.det.hasResult());
    TEST_ASSERT_EQUAL(static_cast<int>(Severity::NORMAL), static_cast<int>(rr.det.lastResult().severity));
    TEST_ASSERT_TRUE(rr.det.lastResult().reasons & REASON_SINGLE_SPIKE);
    TEST_ASSERT_TRUE(contains(rr.states, CrashState::FALSE_POSITIVE));
}

void test_minor_impact_no_emergency() {
    RunResult rr;
    runScenario(rr, "minor_impact");
    TEST_ASSERT_EQUAL(0, rr.emergencies);
    TEST_ASSERT_EQUAL(static_cast<int>(Severity::MINOR), static_cast<int>(rr.det.lastResult().severity));
    TEST_ASSERT_EQUAL(1, rr.det.minorEventCount());
}

void test_severe_crash_critical_once() {
    RunResult rr;
    runScenario(rr, "severe_crash");
    TEST_ASSERT_EQUAL(1, rr.emergencies);
    const CrashResult& r = rr.det.lastResult();
    TEST_ASSERT_EQUAL(static_cast<int>(Severity::CRITICAL), static_cast<int>(r.severity));
    TEST_ASSERT_TRUE(r.confidence >= 0.85f);
    TEST_ASSERT_TRUE(r.impactCount >= 2);
    TEST_ASSERT_TRUE(r.reasons & REASON_RAPID_ROTATION);
    TEST_ASSERT_TRUE(r.reasons & REASON_LARGE_SPEED_DROP);
}

void test_severe_crash_without_vehicle_speed() {
    RunResult rr;
    runScenario(rr, "severe_crash", false);
    TEST_ASSERT_EQUAL(1, rr.emergencies);
    TEST_ASSERT_EQUAL(static_cast<int>(Severity::CRITICAL), static_cast<int>(rr.det.lastResult().severity));
    TEST_ASSERT_TRUE(rr.det.lastResult().reasons & REASON_SPEED_UNAVAILABLE);
}

void test_rollover_verification_then_emergency() {
    RunResult rr;
    runScenario(rr, "rollover");
    TEST_ASSERT_EQUAL(0, rr.emergencies);
    TEST_ASSERT_EQUAL(static_cast<int>(CrashState::VERIFYING), static_cast<int>(rr.det.state()));
    TEST_ASSERT_EQUAL(static_cast<int>(Severity::MODERATE), static_cast<int>(rr.det.lastResult().severity));
    TEST_ASSERT_TRUE(rr.det.tick(rr.det.verifyDeadlineMs()));
    TEST_ASSERT_EQUAL(static_cast<int>(CrashState::TRANSMIT_EVENT), static_cast<int>(rr.det.state()));
}

void test_rollover_cancel() {
    RunResult rr;
    runScenario(rr, "rollover");
    TEST_ASSERT_TRUE(rr.det.cancel());
    TEST_ASSERT_EQUAL(static_cast<int>(CrashState::NORMAL), static_cast<int>(rr.det.state()));
    TEST_ASSERT_FALSE(rr.det.tick(rr.det.verifyDeadlineMs() + 1));
}

void test_repeated_impacts_counted() {
    uint32_t times[100];
    float dyn[100], rot[100];
    for (int i = 0; i < 100; ++i) {
        times[i] = i * 10;
        dyn[i] = 0.1f;
        rot[i] = 0;
    }
    for (int start : {0, 30, 60})
        for (int i = start; i < start + 4; ++i) dyn[i] = 4.0f;
    CrashResult r = analyzeWindow(times, dyn, rot, 100, true, 50, 10, 0, CrashConfig());
    TEST_ASSERT_EQUAL(3, r.impactCount);
    TEST_ASSERT_TRUE(r.reasons & REASON_REPEATED_IMPACTS);
}

// ---- event queue -------------------------------------------------------------

struct MemStorage : QueueStorage {
    std::vector<uint8_t> data;
    bool load(void* d, size_t len) override {
        if (data.size() != len) return false;
        std::memcpy(d, data.data(), len);
        return true;
    }
    bool save(const void* d, size_t len) override {
        data.assign(static_cast<const uint8_t*>(d), static_cast<const uint8_t*>(d) + len);
        return true;
    }
};

struct FakeBackend {
    std::vector<std::string> stored;
    bool online = true;
    int forceStatus = 0;
};

SendResult fakeSend(const CrashEvent& e, void* ctx) {
    auto* b = static_cast<FakeBackend*>(ctx);
    if (!b->online) return classifyResponse(-1);
    if (b->forceStatus) return classifyResponse(b->forceStatus);
    for (auto& id : b->stored)
        if (id == e.eventId) return classifyResponse(200);  // duplicate
    b->stored.emplace_back(e.eventId);
    return classifyResponse(201);
}

CrashEvent makeEvent(const char* id) {
    CrashEvent e;
    std::strncpy(e.eventId, id, sizeof(e.eventId) - 1);
    e.severity = Severity::CRITICAL;
    return e;
}

void test_queue_offline_then_exactly_once() {
    FakeBackend backend;
    backend.online = false;
    EventQueue q;
    q.begin();
    q.enqueue(makeEvent("EVT-1"), 0);
    q.enqueue(makeEvent("EVT-1"), 0);  // same id not queued twice
    TEST_ASSERT_EQUAL(0, q.flush(fakeSend, &backend, 0));
    TEST_ASSERT_EQUAL(1, q.pendingCount());
    backend.online = true;
    TEST_ASSERT_EQUAL(0, q.flush(fakeSend, &backend, 500));  // backoff not elapsed
    TEST_ASSERT_EQUAL(1, q.flush(fakeSend, &backend, 1000));
    TEST_ASSERT_EQUAL(0, q.flush(fakeSend, &backend, 5000, true));
    TEST_ASSERT_EQUAL(1, backend.stored.size());
    TEST_ASSERT_EQUAL(0, q.pendingCount());
}

void test_queue_persists_across_reboot() {
    MemStorage storage;
    {
        EventQueue q(&storage);
        q.begin();
        q.enqueue(makeEvent("EVT-2"), 0);
    }
    EventQueue q2(&storage);
    q2.begin();
    TEST_ASSERT_EQUAL(1, q2.pendingCount());
    TEST_ASSERT_NOT_NULL(q2.find("EVT-2"));
}

void test_queue_rejected_is_permanent_and_5xx_retried() {
    FakeBackend backend;
    EventQueue q;
    q.begin();
    backend.forceStatus = 503;
    q.enqueue(makeEvent("EVT-3"), 0);
    q.flush(fakeSend, &backend, 0, true);
    TEST_ASSERT_EQUAL(1, q.pendingCount());
    backend.forceStatus = 422;
    q.flush(fakeSend, &backend, 0, true);
    TEST_ASSERT_EQUAL(0, q.pendingCount());
    TEST_ASSERT_EQUAL(static_cast<int>(QueueStatus::FAILED), static_cast<int>(q.find("EVT-3")->status));
}

void test_queue_never_evicts_pending() {
    FakeBackend backend;
    backend.online = false;
    EventQueue q;
    q.begin();
    char id[16];
    for (size_t i = 0; i < EVENT_QUEUE_CAPACITY; ++i) {
        std::snprintf(id, sizeof(id), "EVT-P%u", static_cast<unsigned>(i));
        TEST_ASSERT_TRUE(q.enqueue(makeEvent(id), 0));
    }
    TEST_ASSERT_FALSE(q.enqueue(makeEvent("EVT-OVER"), 0));
    backend.online = true;
    q.flush(fakeSend, &backend, 0, true);
    TEST_ASSERT_TRUE(q.enqueue(makeEvent("EVT-NEW"), 0));  // evicts the oldest SENT
}

void test_response_classification() {
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::ACKED), static_cast<int>(classifyResponse(201)));
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::ACKED), static_cast<int>(classifyResponse(200)));
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::RETRY), static_cast<int>(classifyResponse(-1)));
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::RETRY), static_cast<int>(classifyResponse(503)));
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::REJECTED), static_cast<int>(classifyResponse(401)));
    TEST_ASSERT_EQUAL(static_cast<int>(SendResult::REJECTED), static_cast<int>(classifyResponse(422)));
}

void setUp() {}
void tearDown() {}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_normal_driving_no_trigger);
    RUN_TEST(test_hard_brake_no_trigger);
    RUN_TEST(test_pothole_single_spike_false_positive);
    RUN_TEST(test_minor_impact_no_emergency);
    RUN_TEST(test_severe_crash_critical_once);
    RUN_TEST(test_severe_crash_without_vehicle_speed);
    RUN_TEST(test_rollover_verification_then_emergency);
    RUN_TEST(test_rollover_cancel);
    RUN_TEST(test_repeated_impacts_counted);
    RUN_TEST(test_queue_offline_then_exactly_once);
    RUN_TEST(test_queue_persists_across_reboot);
    RUN_TEST(test_queue_rejected_is_permanent_and_5xx_retried);
    RUN_TEST(test_queue_never_evicts_pending);
    RUN_TEST(test_response_classification);
    return UNITY_END();
}
