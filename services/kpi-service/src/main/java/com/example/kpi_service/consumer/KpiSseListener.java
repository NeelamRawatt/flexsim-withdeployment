package com.example.kpi_service.consumer;

import com.example.kpi_service.event.KafkaTopics;
import com.example.kpi_service.event.SimulationKpiEvent;
import com.example.kpi_service.kpi.KpiNormalizer;
import com.example.kpi_service.sse.MetricsStreamHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The LIVE stream: reads simulation.kpi and pushes each snapshot to the browsers THIS copy holds.
 *
 *   groupId "kpi-service-sse-<random>" -> every copy has its OWN group, so EVERY copy gets EVERY snapshot
 *                                         (a browser may be connected to any copy).
 *   auto.offset.reset = latest         -> only new snapshots: old ones are not "live".
 *
 * Separate from KpiSnapshotListener on purpose: a slow database must never delay the live screen,
 * and a broken message here is only skipped (it is screen news; the saving listener handles errors).
 */
@Component
public class KpiSseListener {

    private static final Logger log = LoggerFactory.getLogger(KpiSseListener.class);

    private final MetricsStreamHub hub;

    public KpiSseListener(MetricsStreamHub hub) {
        this.hub = hub;
    }

    @KafkaListener(
            topics = KafkaTopics.SIMULATION_KPI,
            groupId = "kpi-service-sse-${random.uuid}",
            properties = "auto.offset.reset=latest")
    public void onKpi(String value) {
        SimulationKpiEvent event;
        try {
            event = KpiEventParser.parse(value);
        } catch (InvalidEventException e) {
            log.warn("Skipping a broken simulation.kpi message for the live stream: {}", e.getMessage());
            return;
        }
        hub.send(KpiNormalizer.normalize(event.kpi()));
    }
}
