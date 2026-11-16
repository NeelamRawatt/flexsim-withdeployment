package com.example.kpi_service.consumer;

import com.example.kpi_service.event.KafkaTopics;
import com.example.kpi_service.event.SimulationKpiEvent;
import com.example.kpi_service.service.KpiIngestService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The CONSUMER that SAVES the KPI snapshots (topic simulation.kpi).
 *
 * Consumer group "kpi-service" (application.properties): ONE group for all copies of kpi-service,
 * so each snapshot is saved by ONE copy only. Saving twice is harmless anyway (eventId is unique).
 * Errors: broken message -> straight to simulation.kpi.DLT; database down -> 3 retries, then DLT.
 */
@Component
public class KpiSnapshotListener {

    private static final Logger log = LoggerFactory.getLogger(KpiSnapshotListener.class);

    private final KpiIngestService ingestService;

    public KpiSnapshotListener(KpiIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @KafkaListener(topics = KafkaTopics.SIMULATION_KPI)
    public void onKpi(ConsumerRecord<String, String> record) {
        SimulationKpiEvent event = KpiEventParser.parse(record.value());
        boolean saved = ingestService.save(event);
        if (!saved) {
            log.debug("KPI snapshot {} was already saved, skipped", event.eventId());
        }
    }
}
