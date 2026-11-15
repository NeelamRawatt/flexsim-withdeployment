package com.example.simulation_service.controller;

import com.example.simulation_service.event.SimulationStatusEvent;
import com.example.simulation_service.repository.SimulationRunRepository;
import com.example.simulation_service.sse.ProgressStreamHub;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live progress stream. SAME URL and event name as your team's code, so React's
 *   new EventSource(".../api/flexsim/progress/stream/" + experimentId, { withCredentials: true })
 * keeps working. Any logged-in user may watch (the login cookie is sent automatically).
 *
 * text/event-stream = the SSE format: the answer stays open, and messages are added to it over time.
 */
@RestController
@RequestMapping("/api/flexsim/progress")
public class ProgressStreamController {

    private final ProgressStreamHub hub;
    private final SimulationRunRepository runs;

    public ProgressStreamController(ProgressStreamHub hub, SimulationRunRepository runs) {
        this.hub = hub;
        this.runs = runs;
    }

    @GetMapping(path = "/stream/{experimentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Transactional(readOnly = true)
    public SseEmitter stream(@PathVariable Long experimentId) {
        // The latest run of this experiment, so the screen shows the right status at once.
        SimulationStatusEvent current = runs.findFirstByExperimentIdOrderByQueuedAtDesc(experimentId)
                .map(run -> new SimulationStatusEvent(null, run.getId(), run.getExperimentId(),
                        run.getStatus().name(), run.getStage(), run.getMessage(), null))
                .orElse(null);
        return hub.subscribe(experimentId, current);
    }
}