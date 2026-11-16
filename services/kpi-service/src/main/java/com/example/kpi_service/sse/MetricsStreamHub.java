package com.example.kpi_service.sse;

import com.example.kpi_service.kpi.SimulationKpi;
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
 * Keeps the open live-KPI connections ("pipes") of THIS copy of kpi-service and pushes snapshots into them.
 * Same design as simulation-service's ProgressStreamHub:
 *   - many viewers per experiment
 *   - a 30-minute timeout (the old code had NONE: forgotten pipes stayed open for ever);
 *     the browser's EventSource reconnects by itself if the page is still open
 *   - a heartbeat every 20 s, so proxies do not close a quiet connection
 *   - works with several copies of the service (every copy reads every snapshot, see KpiSseListener)
 * Event name "metrics": the SAME name React listens for.
 */
@Component
public class MetricsStreamHub {

    private static final Logger log = LoggerFactory.getLogger(MetricsStreamHub.class);

    public static final String EVENT_NAME = "metrics";

    private final Map<Long, Set<SseEmitter>> pipes = new ConcurrentHashMap<>();
    private final long timeoutMs;
    private final int maxViewersPerExperiment;

    public MetricsStreamHub(@Value("${app.sse.timeout-ms:1800000}") long timeoutMs,
                            @Value("${app.sse.max-viewers-per-experiment:20}") int maxViewersPerExperiment) {
        this.timeoutMs = timeoutMs;
        this.maxViewersPerExperiment = maxViewersPerExperiment;
    }

    public SseEmitter subscribe(Long experimentId) {
        Set<SseEmitter> viewers = pipes.computeIfAbsent(experimentId, id -> new CopyOnWriteArraySet<>());
        if (viewers.size() >= maxViewersPerExperiment) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Too many live viewers for this experiment. Please try again later.");
        }
        SseEmitter emitter = newEmitter(timeoutMs);
        viewers.add(emitter);
        emitter.onCompletion(() -> remove(experimentId, emitter));
        emitter.onTimeout(() -> {
            remove(experimentId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(experimentId, emitter));
        return emitter;
    }

    /** Push one snapshot to every viewer of this experiment (on THIS copy). */
    public void send(SimulationKpi snapshot) {
        Set<SseEmitter> viewers = pipes.get(snapshot.experimentId());
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : viewers) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_NAME).data(snapshot));
            } catch (IOException | IllegalStateException e) {
                log.debug("Live KPI pipe for experiment {} is closed: {}", snapshot.experimentId(), e.toString());
                remove(snapshot.experimentId(), emitter);
                emitter.completeWithError(e);
            }
        }
    }

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

    public int viewerCount(Long experimentId) {
        Set<SseEmitter> viewers = pipes.get(experimentId);
        return viewers == null ? 0 : viewers.size();
    }

    SseEmitter newEmitter(long timeout) {
        return new SseEmitter(timeout);
    }

    private void remove(Long experimentId, SseEmitter emitter) {
        pipes.computeIfPresent(experimentId, (id, viewers) -> {
            viewers.remove(emitter);
            return viewers.isEmpty() ? null : viewers;
        });
    }
}
