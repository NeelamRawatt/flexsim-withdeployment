package com.example.flexsim_worker.consumer;

import com.example.flexsim_worker.event.KafkaTopics;
import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import com.example.flexsim_worker.job.SimulationJobRunner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CONSUMER of "simulation.trigger.requested": receives the jobs.
 *
 * concurrency = 4 -> 4 threads, each reading its own partition (the topic has 4 partitions).
 * Each thread runs ONE job at a time and WAITS until FlexSim is finished.
 * So: max 4 FlexSim at the same time = the 4 licences. No extra counting needed.
 *
 * The job counts as "done" in Kafka only AFTER run() returns (ack-mode=record).
 * If the worker crashes in the middle, Kafka gives the job again -> SimulationJobRunner sees
 * "RUNNING" and marks it FAILED (see there).
 *
 * Consumer group "flexsim-worker": with 2 worker VMs later, each job is still done only ONCE.
 */
@Component
public class SimulationJobListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationJobListener.class);

    private final SimulationJobRunner jobRunner;
    private final JsonMapper json = JsonMapper.builder().build();

    public SimulationJobListener(SimulationJobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @KafkaListener(topics = KafkaTopics.SIMULATION_TRIGGER_REQUESTED,
            concurrency = "${app.worker.max-parallel-runs:4}")
    public void onJob(ConsumerRecord<String, String> record) {
        SimulationTriggerRequestedEvent job = parse(record.value());
        log.info("Job received: run {} of experiment {} by {} (eventId {}, partition {}, offset {})",
                job.runId(), job.experimentId(), job.requestedBy(), job.eventId(), record.partition(), record.offset());
        jobRunner.run(job);
    }

    SimulationTriggerRequestedEvent parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Empty simulation.trigger.requested message");
        }
        SimulationTriggerRequestedEvent job;
        try {
            job = json.readValue(value, SimulationTriggerRequestedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("simulation.trigger.requested message is not valid JSON", e);
        }
        if (job.runId() == null || job.experimentId() == null) {
            throw new InvalidEventException("simulation.trigger.requested message has no runId or experimentId");
        }
        return job;
    }
}
