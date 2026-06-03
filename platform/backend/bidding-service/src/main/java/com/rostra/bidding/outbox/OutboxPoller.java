package com.rostra.bidding.outbox;

import com.rostra.bidding.entity.OutboxEvent;
import com.rostra.bidding.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 5000)
    @Transactional
    public void drainOutbox() {
        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        log.info("Draining {} outbox events", batch.size());

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getAggregateId().toString(),
                        event.getPayload()
                ).get();

                event.markPublished();
                log.info("Published outbox event {} ({})", event.getId(), event.getTopic());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox poller interrupted while publishing event {}", event.getId());
                return;
            } catch (ExecutionException e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getCause().getMessage());
            }
        }
    }
}