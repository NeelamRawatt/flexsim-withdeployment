package com.example.flexsim_worker;

import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;

/** Ready-made job messages for the tests. */
public final class TestJobs {

    private TestJobs() {
    }

    /** Night shift 23:00 -> 06:30, all settings filled in. */
    public static SimulationTriggerRequestedEvent nightShift(long runId, long experimentId) {
        return new SimulationTriggerRequestedEvent("evt-" + runId, runId, experimentId, "Pune test",
                "2026-10-21T23:00:00", "2026-10-21T06:30:00", 4,
                2, 1, 1, 0, 1, 85, 60, 80,
                "neelam", "2026-11-15T05:00:00Z");
    }

    /** Only the required fields; every setting is null (= use the defaults). */
    public static SimulationTriggerRequestedEvent withoutSettings(long runId, long experimentId) {
        return new SimulationTriggerRequestedEvent("evt-" + runId, runId, experimentId, "Defaults",
                "2026-10-21T08:00:00", "2026-10-21T16:00:00", null,
                null, null, null, null, null, null, null, null,
                "neelam", "2026-11-15T05:00:00Z");
    }
}
