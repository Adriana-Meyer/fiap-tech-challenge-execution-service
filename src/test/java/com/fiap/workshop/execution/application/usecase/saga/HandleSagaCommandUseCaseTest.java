package com.fiap.workshop.execution.application.usecase.saga;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.port.in.EnqueueExecutionInputPort;
import com.fiap.workshop.execution.application.port.out.SagaEventPublisher;
import com.fiap.workshop.execution.application.saga.command.EnqueueExecution;
import com.fiap.workshop.execution.application.saga.command.SagaCommand;
import com.fiap.workshop.execution.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.execution.application.saga.event.ExecutionEnqueued;
import com.fiap.workshop.execution.application.saga.event.SagaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleSagaCommandUseCaseTest {

    @Mock
    private EnqueueExecutionInputPort enqueueExecution;
    @Mock
    private SagaEventPublisher publisher;

    private HandleSagaCommandUseCase useCase;

    private final UUID messageId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new HandleSagaCommandUseCase(enqueueExecution, publisher);
    }

    private EnqueueExecution command() {
        return new EnqueueExecution(sagaId, orderId,
                List.of(new EnqueueExecution.Task(UUID.randomUUID(), UUID.randomUUID(), "Oil change", "MECHANICAL")));
    }

    private SagaEvent published() {
        ArgumentCaptor<SagaEvent> captor = ArgumentCaptor.forClass(SagaEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldQueueTheOrderCarryingTheSagaAndAnswerEnqueued() {
        useCase.handle(messageId, command());

        ArgumentCaptor<EnqueueExecutionCommand> captor = ArgumentCaptor.forClass(EnqueueExecutionCommand.class);
        verify(enqueueExecution).execute(captor.capture());
        assertEquals(sagaId, captor.getValue().sagaId());
        assertEquals(orderId, captor.getValue().serviceOrderId());
        assertEquals("Oil change", captor.getValue().tasks().get(0).serviceName());
        ExecutionEnqueued event = (ExecutionEnqueued) published();
        assertEquals(sagaId, event.sagaId());
    }

    @Test
    void shouldAnswerFailedWithTheReasonWhenTheOrderCannotBeQueued() {
        when(enqueueExecution.execute(any())).thenThrow(new IllegalArgumentException("An execution order needs at least one task"));

        useCase.handle(messageId, new EnqueueExecution(sagaId, orderId, List.of()));

        ExecutionEnqueueFailed event = (ExecutionEnqueueFailed) published();
        assertEquals("An execution order needs at least one task", event.reason());
    }

    @Test
    void shouldNotAnswerWhenAnUnexpectedErrorOccurs() {
        when(enqueueExecution.execute(any())).thenThrow(new IllegalStateException("dynamo down"));

        assertThrows(IllegalStateException.class, () -> useCase.handle(messageId, command()));
        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldDeriveTheAnswerIdFromTheCommandMessageId() {
        useCase.handle(messageId, command());
        SagaEvent first = published();
        clearInvocations(publisher);
        useCase.handle(messageId, command());
        SagaEvent repeat = published();
        clearInvocations(publisher);
        useCase.handle(UUID.randomUUID(), command());
        SagaEvent other = published();

        assertEquals(first.messageId(), repeat.messageId());
        assertNotEquals(first.messageId(), other.messageId());
    }

    @Test
    void shouldRejectACommandItDoesNotKnow() {
        SagaCommand unknown = new SagaCommand() {
            public UUID sagaId() { return sagaId; }
            public UUID serviceOrderId() { return orderId; }
        };

        assertThrows(IllegalArgumentException.class, () -> useCase.handle(messageId, unknown));
        verifyNoInteractions(enqueueExecution, publisher);
    }
}
