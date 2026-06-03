package com.rostra.auction.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String auctionEndedTopic;

    public KafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.auction-ended}") String auctionEndedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.auctionEndedTopic = auctionEndedTopic;
    }

    public void publishAuctionEnded(AuctionEndedEvent event) {
        String key = event.auctionId().toString();
        kafkaTemplate.send(auctionEndedTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AuctionEndedEvent for auction {}", event.auctionId(), ex);
                    } else {
                        log.info("Published AuctionEndedEvent for auction {} to partition {}",
                                event.auctionId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
