package com.bwc.travel_request_management.kafka;

import com.bwc.travel_request_management.dto.TravelRequestProxyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelRequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "travel-request-events";

    public void sendTravelRequest(TravelRequestProxyDTO travelRequestProxy) {
        kafkaTemplate.send(TOPIC, travelRequestProxy);
        log.info("📤 Sent Travel Request event to Kafka [{}]: {}", TOPIC, travelRequestProxy);
    }
}
