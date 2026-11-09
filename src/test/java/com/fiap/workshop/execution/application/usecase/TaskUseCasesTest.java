package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.domain.exception.InvalidStateException;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.domain.service.MetricsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskUseCasesTest {

    @Mock
    private ExecutionOrderRepository repository;

    @Mock
    private MetricsPublisher metricsPublisher;

    private StartTaskUseCase start;
    private FinishTaskUseCase finish;

    private final UUID orderId = UUID.randomUUID();
    private final UUID taskA = UUID.randomUUID();
    private final UUID taskB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        start = new StartTaskUseCase(repository);
        finish = new FinishTaskUseCase(repository, metricsPublisher);
        lenient().when(repository.save(any(ExecutionOrder.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void shouldStartATaskRecordingTheMechanic() {
        when(repository.findByServiceOrderId(orderId))
                .thenReturn(Optional.of(ExecutionUseCaseTestSupport.order(orderId, taskA, taskB)));

        ExecutionOrderResponse response = start.execute(orderId, taskA, "mech1");

        assertEquals("IN_PROGRESS", response.status());
        assertEquals("mech1", response.tasks().get(0).mechanic());
        assertEquals("PENDING", response.tasks().get(1).status());
    }

    @Test
    void shouldFailToStartInAnUnknownOrder() {
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> start.execute(orderId, taskA, "mech1"));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFinishATaskWithoutPublishingWhileOthersAreOpen() {
        ExecutionOrder order = ExecutionUseCaseTestSupport.order(orderId, taskA, taskB);
        order.startTask(taskA, "mech1");
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.of(order));

        ExecutionOrderResponse response = finish.execute(orderId, taskA);

        assertEquals("IN_PROGRESS", response.status());
        assertEquals("COMPLETED", response.tasks().get(0).status());
        verify(metricsPublisher, never()).recordServiceOrderStatusDuration(any(), any(), anyLong());
    }

    @Test
    void shouldPublishTheExecutionDurationWhenTheLastTaskFinishes() {
        ExecutionOrder order = ExecutionUseCaseTestSupport.order(orderId, taskA);
        order.startTask(taskA, "mech1");
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.of(order));

        ExecutionOrderResponse response = finish.execute(orderId, taskA);

        assertEquals("COMPLETED", response.status());
        verify(metricsPublisher).recordServiceOrderStatusDuration(eq(orderId), eq("EXECUTION"), anyLong());
    }

    @Test
    void shouldNotFinishATaskThatWasNotStarted() {
        when(repository.findByServiceOrderId(orderId))
                .thenReturn(Optional.of(ExecutionUseCaseTestSupport.order(orderId, taskA)));

        assertThrows(InvalidStateException.class, () -> finish.execute(orderId, taskA));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFailToFinishInAnUnknownOrder() {
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> finish.execute(orderId, taskA));
    }
}
