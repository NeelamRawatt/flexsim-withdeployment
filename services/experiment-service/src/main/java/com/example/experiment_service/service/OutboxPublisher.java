package com.example.experiment_service.service;

import com.example.experiment_service.entity.OutboxEvent;
import com.example.experiment_service.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE SENDER of the outbox ("the helper who takes the letters to the post office").
 *
 * Every 2 seconds:
 *   1. take the oldest NOT-sent rows from outbox_event (max 100), and lock them
 *   2. send each one to Kafka and WAIT for Kafka's "received" (ack)
 *   3. ack OK    -> fill sent_at (done)
 *      ack FAIL  -> attempts + 1, save the error, and STOP this round
 *                   (stop, not skip: so message 2 is never sent before message 1)
 *
 * If Kafka is down, nothing is lost: the rows stay "not sent" and the next round tries again.
 *
 * Can be switched off with app.outbox.enabled=false (the integration test does that,
 * because it has no Kafka).
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long sendTimeoutSeconds;
    private final int retentionDays;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${app.outbox.batch-size:100}") int batchSize,
                           @Value("${app.outbox.send-timeout-seconds:25}") long sendTimeoutSeconds,
                           @Value("${app.outbox.retention-days:7}") int retentionDays) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.retentionDays = retentionDays;
    }

    /**
     * fixedDelay = wait 2 seconds AFTER one round finishes, then start the next.
     * (So two rounds never run at the same time inside one service copy.)
     * @Transactional = the row locks and the sent_at updates are one unit.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}",
               initialDelayString = "${app.outbox.initial-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> rows = outboxEventRepository.lockNextUnsent(batchSize);
        for (OutboxEvent row : rows) {
            try {
                // send(topic, key, value) and wait for Kafka's ack
                kafkaTemplate.send(row.getTopic(), row.getEventKey(), row.getPayload())
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                row.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
                log.info("Outbox row {} sent to topic {} with key {}", row.getId(), row.getTopic(), row.getEventKey());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();     // the service is stopping; give back the "stop" signal
                recordFailure(row, e);
                return;
            } catch (Exception e) {
                recordFailure(row, e);
                log.warn("Outbox row {} NOT sent (attempt {}), will retry: {}",
                        row.getId(), row.getAttempts(), e.getMessage());
                return;                                 // stop this round, keep the order
            }
        }
        // End of method = COMMIT: sent_at / attempts / last_error are saved, locks are released.
    }

    /** Once a day (03:30 UTC): delete rows that were SENT more than 7 days ago. Unsent rows are never deleted. */
    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void deleteOldSentRows() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int deleted = outboxEventRepository.deleteSentBefore(before);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} sent rows older than {} days", deleted, retentionDays);
        }
    }

    private void recordFailure(OutboxEvent row, Exception e) {
        row.setAttempts(row.getAttempts() + 1);
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        row.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
    }
}