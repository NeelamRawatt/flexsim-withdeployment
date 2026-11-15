package com.example.simulation_service.sse;

import com.example.simulation_service.event.SimulationStatusEvent;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the open SSE connections ("pipes") of THIS copy of the service and pushes news into them.
 *
 * Fixes compared to your team's code:
 *   1. MANY viewers per experiment (a Set), not one. A second browser tab no longer kicks out the first.
 *   2. A TIMEOUT (30 min) instead of "never": forgotten pipes are closed and removed.
 *   3. A HEARTBEAT every 20 s (": ping"): proxies and load balancers close silent connections,
 *      so we never stay silent for long.
 *   4. Works with SEVERAL copies of the service (Kubernetes): every copy reads simulation.status from
 *      Kafka (see SimulationStatusSseListener) and pushes to the pipes IT holds. So the browser gets the
 *      news, whichever copy it is connected to.
 *   5. When the run is FINISHED (COMPLETED / FAILED / CANCELLED), the pipes are closed after the last message.
 *
 * ConcurrentHashMap + CopyOnWriteArraySet = safe when many threads use them at the same time
 * (web requests, the Kafka listener and the heartbeat all touch this map).
 */
@Component
public class ProgressStreamHub {

    private static final Logger log = LoggerFactory.getLogger(ProgressStreamHub.class);

    /** The event NAME React listens for (same as your team's code). */
    public static final String EVENT_NAME = "simulation-progress";

    private final Map<Long, Set<SseEmitter>> pipes = new ConcurrentHashMap<>();
    private final long timeoutMs;
    private final int maxViewersPerExperiment;

    public ProgressStreamHub(@Value("${app.sse.timeout-ms:1800000}") long timeoutMs,
                             @Value("${app.sse.max-viewers-per-experiment:20}") int maxViewersPerExperiment) {
        this.timeoutMs = timeoutMs;
        this.maxViewersPerExperiment = maxViewersPerExperiment;
    }

    /** A browser wants live news for this experiment. 'current' = the latest known status (may be null). */
    public SseEmitter subscribe(Long experimentId, SimulationStatusEvent current) {
        Set<SseEmitter> viewers = pipes.computeIfAbsent(experimentId, id -> new CopyOnWriteArraySet<>());
        if (viewers.size() >= maxViewersPerExperiment) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Too many live viewers for this experiment. Please try again later.");
        }
        SseEmitter emitter = newEmitter(timeoutMs);
        viewers.add(emitter);

        // Clean-up: whatever way the pipe ends, take it out of the map.
        emitter.onCompletion(() -> remove(experimentId, emitter));
        emitter.onTimeout(() -> {
            remove(experimentId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(experimentId, emitter));

        // First message at once: the current status, so the screen is right from the first second.
        if (current != null) {
            sendTo(experimentId, emitter, current);
        }
        return emitter;
    }

    /** Push one status message to every viewer of this experiment (on THIS copy of the service). */
    public void send(SimulationStatusEvent event) {
        Set<SseEmitter> viewers = pipes.get(event.experimentId());
        if (viewers == null || viewers.isEmpty()) {
            return;   // nobody is watching: nothing to do
        }
        boolean finished = isFinal(event.status());
        for (SseEmitter emitter : viewers) {
            boolean delivered = sendTo(event.experimentId(), emitter, event);
            if (delivered && finished) {
                emitter.complete();   // the story is over: close the pipe normally
            }
        }
    }

    /** Every 20 seconds: a tiny comment line to every pipe, so the connection is never idle for long. */
    @Scheduled(fixedDelayString = "${app.sse.heartbeat-ms:20000}")
    public void heartbeat() {
        pipes.forEach((experimentId, viewers) -> {
            for (SseEmitter emitter : viewers) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(experimentId, emitter);
                }
            }
        });
    }

    /** How many open pipes this copy holds for one experiment (for tests and monitoring). */
    public int viewerCount(Long experimentId) {
        Set<SseEmitter> viewers = pipes.get(experimentId);
        return viewers == null ? 0 : viewers.size();
    }

    /** Separate method so a test can give a fake pipe. */
    SseEmitter newEmitter(long timeout) {
        return new SseEmitter(timeout);
    }

    private boolean sendTo(Long experimentId, SseEmitter emitter, SimulationStatusEvent event) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(event));
            return true;
        } catch (IOException | IllegalStateException e) {
            // The browser is gone (tab closed, network lost). Remove the dead pipe.
            log.debug("SSE pipe for experiment {} is closed: {}", experimentId, e.toString());
            remove(experimentId, emitter);
            emitter.completeWithError(e);
            return false;
        }
    }

    private void remove(Long experimentId, SseEmitter emitter) {
        pipes.computeIfPresent(experimentId, (id, viewers) -> {
            viewers.remove(emitter);
            return viewers.isEmpty() ? null : viewers;   // drop empty sets, so the map does not grow for ever
        });
    }

    private static boolean isFinal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }
}