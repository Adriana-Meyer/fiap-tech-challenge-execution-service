package com.fiap.workshop.execution.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.execution.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.execution.application.saga.event.ExecutionEnqueueFailed;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SnsSagaEventPublisherTest {

    private static final String TOPIC = "arn:aws:sns:us-east-1:000000000000:saga-events";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SnsClient snsClient = mock(SnsClient.class);
    private final UUID messageId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private PublishRequest sent() {
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldPublishTheEnvelopeWithOnlyTheEventFieldsInThePayload() throws Exception {
        new SnsSagaEventPublisher(snsClient, objectMapper, TOPIC)
                .publish(new ExecutionEnqueueFailed(messageId, sagaId, orderId, "no tasks"));

        PublishRequest request = sent();
        JsonNode body = objectMapper.readTree(request.message());
        assertEquals(TOPIC, request.topicArn());
        assertEquals(messageId.toString(), body.get("messageId").asText());
        assertEquals(sagaId.toString(), body.get("sagaId").asText());
        assertEquals(orderId.toString(), body.get("serviceOrderId").asText());
        assertEquals("ExecutionEnqueueFailed", body.get("type").asText());
        assertEquals("no tasks", body.get("payload").get("reason").asText());
        assertFalse(body.get("payload").has("sagaId"));
        assertFalse(body.get("payload").has("messageId"));
        assertEquals("ExecutionEnqueueFailed", request.messageAttributes().get("type").stringValue());
    }

    @Test
    void shouldPublishTheSameCompletionMessageEveryTime() throws Exception {
        SnsSagaEventPublisher publisher = new SnsSagaEventPublisher(snsClient, objectMapper, TOPIC);
        publisher.publish(ExecutionCompleted.of(sagaId, orderId));
        publisher.publish(ExecutionCompleted.of(sagaId, orderId));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, org.mockito.Mockito.times(2)).publish(captor.capture());
        String first = objectMapper.readTree(captor.getAllValues().get(0).message()).get("messageId").asText();
        String second = objectMapper.readTree(captor.getAllValues().get(1).message()).get("messageId").asText();
        assertEquals(first, second);
        assertEquals("ExecutionCompleted", objectMapper.readTree(captor.getAllValues().get(0).message()).get("type").asText());
    }

    @Test
    void shouldFailLoudlyWhenTheTopicIsNotConfigured() {
        SnsSagaEventPublisher publisher = new SnsSagaEventPublisher(snsClient, objectMapper, "");

        assertThrows(IllegalStateException.class,
                () -> publisher.publish(ExecutionCompleted.of(sagaId, orderId)));
        verifyNoInteractions(snsClient);
    }
}
