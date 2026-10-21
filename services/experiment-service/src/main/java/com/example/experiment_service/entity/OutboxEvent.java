package com.example.experiment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One row of the outbox table = one Kafka message that must be sent.
 * Written in the same transaction as the business change (see OutboxService).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public OutboxEvent() {
    }

    public OutboxEvent(String topic, String eventKey, String payload) {
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kafka topic, example "experiment.deleted"
    @Column(nullable = false)
    private String topic;

    // Kafka key, example "1001"
    @Column(name = "event_key", nullable = false)
    private String eventKey;

    // The JSON message
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // null = waiting to be sent
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Integer attempts;

    @Column(length = 500)
    private String lastError;

    @PrePersist
    void beforeFirstSave() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (attempts == null) {
            attempts = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}