package com.fiap.workshop.execution.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.execution.application.port.in.saga.HandleSagaCommandInputPort;
import com.fiap.workshop.execution.application.saga.command.EnqueueExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SagaCommandListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final HandleSagaCommandInputPort handler = mock(HandleSagaCommandInputPort.class);
    private final SagaCommandListener listener = new SagaCommandListener(objectMapper, handler);

    private final UUID messageId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private String envelope(String type, String payload) {
        return """
                {"messageId":"%s","sagaId":"%s","serviceOrderId":"%s","type":"%s","payload":%s}
                """.formatted(messageId, sagaId, orderId, type, payload);
    }

    @Test
    void shouldHandleEnqueueExecutionMergingTheEnvelopeIds() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        listener.onMessage(envelope("EnqueueExecution", """
                {"tasks":[{"taskId":"%s","serviceId":"%s","serviceName":"Oil change","serviceType":"MECHANICAL"}]}
                """.formatted(taskId, serviceId)));

        ArgumentCaptor<EnqueueExecution> captor = ArgumentCaptor.forClass(EnqueueExecution.class);
        verify(handler).handle(org.mockito.ArgumentMatchers.eq(messageId), captor.capture());
        EnqueueExecution command = captor.getValue();
        assertEquals(sagaId, command.sagaId());
        assertEquals(orderId, command.serviceOrderId());
        assertEquals(taskId, command.tasks().get(0).taskId());
        assertEquals("MECHANICAL", command.tasks().get(0).serviceType());
    }

    @Test
    void shouldFailOnAnUnknownTypeSoTheMessageGoesToTheDeadLetterQueue() {
        assertThrows(IllegalArgumentException.class, () -> listener.onMessage(envelope("ReserveStock", "{}")));
        verifyNoInteractions(handler);
    }

    @Test
    void shouldFailOnAMessageThatIsNotJson() {
        assertThrows(JsonProcessingException.class, () -> listener.onMessage("not json"));
        verifyNoInteractions(handler);
    }

    @Test
    void shouldFailWhenTheHandlerFailsSoTheMessageIsRetried() {
        doThrow(new IllegalStateException("dynamo down")).when(handler).handle(any(), any());

        assertThrows(IllegalStateException.class, () -> listener.onMessage(envelope("EnqueueExecution", "{\"tasks\":[]}")));
    }
}
