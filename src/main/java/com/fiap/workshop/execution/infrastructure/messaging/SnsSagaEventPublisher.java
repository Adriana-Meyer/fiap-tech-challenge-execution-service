package com.fiap.workshop.execution.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fiap.workshop.execution.application.port.out.SagaEventPublisher;
import com.fiap.workshop.execution.application.saga.event.SagaEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.Map;

/**
 * Publishes the answers to the {@code saga-events} topic, which feeds the OS
 * service's queue. The body is the {@link MessageEnvelope} as JSON; the type
 * also goes as a message attribute, so a subscription can filter on it.
 */
@Component
public class SnsSagaEventPublisher implements SagaEventPublisher {

    private static final List<String> ENVELOPE_FIELDS = List.of("messageId", "sagaId", "serviceOrderId");

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;

    public SnsSagaEventPublisher(SnsClient snsClient, ObjectMapper objectMapper,
                                 @Value("${app.messaging.events-topic-arn:}") String topicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(SagaEvent event) {
        if (topicArn.isBlank()) {
            throw new IllegalStateException("app.messaging.events-topic-arn is not configured");
        }
        String type = event.getClass().getSimpleName();
        ObjectNode payload = objectMapper.valueToTree(event);
        payload.remove(ENVELOPE_FIELDS);
        try {
            String body = objectMapper.writeValueAsString(
                    new MessageEnvelope(event.messageId(), event.sagaId(), event.serviceOrderId(), type, payload));
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(body)
                    .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                            .dataType("String").stringValue(type).build()))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + type, e);
        }
    }
}
