package com.example.kpi_service.controller;

import com.example.kpi_service.kpi.SimulationKpi;
import com.example.kpi_service.service.BlockageIntervalService;
import com.example.kpi_service.service.BlockageIntervalService.ChuteBlockageInterval;
import com.example.kpi_service.service.KpiHistoryService;
import com.example.kpi_service.sse.MetricsStreamHub;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The KPI URLs. SAME URLs and JSON as the old app (React's api.js uses exactly these).
 *
 *   GET /api/metrics/{exp}                     whole history (one entry per simulation time)
 *   GET /api/metrics/blockage-intervals/{exp}  blocked periods per chute; 204 = no blockage at all
 *   GET /api/metrics/stream/{exp}              live snapshots (SSE, event name "metrics")
 *
 * Any logged-in user may read (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final KpiHistoryService historyService;
    private final BlockageIntervalService blockageIntervalService;
    private final MetricsStreamHub hub;

    public MetricsController(KpiHistoryService historyService, BlockageIntervalService blockageIntervalService,
                             MetricsStreamHub hub) {
        this.historyService = historyService;
        this.blockageIntervalService = blockageIntervalService;
        this.hub = hub;
    }

    @GetMapping(path = "/stream/{experimentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long experimentId) {
        return hub.subscribe(experimentId);
    }

    @GetMapping("/blockage-intervals/{experimentId}")
    public ResponseEntity<List<ChuteBlockageInterval>> blockageIntervals(@PathVariable Long experimentId) {
        List<ChuteBlockageInterval> intervals = blockageIntervalService.intervals(experimentId);
        return intervals.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(intervals);
    }

    @GetMapping("/{experimentId}")
    public ResponseEntity<List<SimulationKpi>> history(@PathVariable Long experimentId) {
        return ResponseEntity.ok(historyService.history(experimentId));
    }
}
