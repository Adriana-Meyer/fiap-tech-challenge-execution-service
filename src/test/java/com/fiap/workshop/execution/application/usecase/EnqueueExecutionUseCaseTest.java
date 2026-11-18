package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.dto.TaskCommand;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnqueueExecutionUseCaseTest {

    @Mock
    private ExecutionOrderRepository repository;

    @InjectMocks
    private EnqueueExecutionUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    private EnqueueExecutionCommand command() {
        return new EnqueueExecutionCommand(orderId, UUID.randomUUID(),
                List.of(new TaskCommand(taskId, UUID.randomUUID(), "Oil change", "MECHANICAL")));
    }

    @Test
    void shouldQueueANewOrder() {
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.empty());
        when(repository.save(any(ExecutionOrder.class))).thenAnswer(i -> i.getArgument(0));

        ExecutionOrderResponse response = useCase.execute(command());

        assertEquals(orderId, response.serviceOrderId());
        assertEquals("QUEUED", response.status());
        assertEquals(taskId, response.tasks().get(0).id());
        assertEquals("PENDING", response.tasks().get(0).status());
    }

    @Test
    void shouldReturnTheExistingOrderWithoutResettingIt() {
        ExecutionOrder existing = ExecutionUseCaseTestSupport.order(orderId, taskId);
        existing.startTask(taskId, "mech1");
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.of(existing));

        ExecutionOrderResponse response = useCase.execute(command());

        assertEquals("IN_PROGRESS", response.status());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicatedTaskIds() {
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.empty());
        UUID serviceId = UUID.randomUUID();
        EnqueueExecutionCommand duplicated = new EnqueueExecutionCommand(orderId, UUID.randomUUID(), List.of(
                new TaskCommand(taskId, serviceId, "A", "GENERAL"),
                new TaskCommand(taskId, serviceId, "B", "GENERAL")));

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(duplicated));
        verify(repository, never()).save(any());
    }
}
