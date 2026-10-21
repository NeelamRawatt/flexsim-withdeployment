package com.example.experiment_service.service;

import com.example.experiment_service.entity.OutboxEvent;
import com.example.experiment_service.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puts a message into the outbox table.
 *
 * Propagation.MANDATORY = this method REFUSES to run without a transaction that is already open.
 * Why: the whole point is "save the change and the message TOGETHER". If someone calls this
 * outside a transaction by mistake, it fails loudly instead of silently breaking that promise.
 */
@Service
public class OutboxService {

    // Turns a Java object (record) into JSON text. Jackson 3 (comes with Spring Boot 4).
    private final JsonMapper json = JsonMapper.builder().build();

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, String key, Object message) {
        String payload = json.writeValueAsString(message);
        outboxEventRepository.save(new OutboxEvent(topic, key, payload));
    }
}