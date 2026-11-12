package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.AverageExecutionTimeResponse;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.domain.model.execution.TaskStatus;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryUseCasesTest {

    @Mock
    private ExecutionOrderRepository repository;

    private final UUID orderId = UUID.randomUUID();

    @Test
    void shouldFindAnOrder() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByServiceOrderId(orderId))
                .thenReturn(Optional.of(ExecutionUseCaseTestSupport.order(orderId, taskId)));

        ExecutionOrderResponse response = new FindExecutionUseCase(repository).findByServiceOrderId(orderId);

        assertEquals(orderId, response.serviceOrderId());
        assertEquals(taskId, response.tasks().get(0).id());
    }

    @Test
    void shouldFailWhenTheOrderIsNotQueued() {
        when(repository.findByServiceOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> new FindExecutionUseCase(repository).findByServiceOrderId(orderId));
    }

    @Test
    void shouldListTheOpenQueueOldestFirst() {
        Instant now = Instant.now();
        ExecutionOrder newer = queuedAt(now);
        ExecutionOrder older = queuedAt(now.minusSeconds(600));
        when(repository.findByStatusIn(anySet())).thenReturn(List.of(newer, older));

        List<ExecutionOrderResponse> queue = new FindExecutionUseCase(repository).findQueue();

        assertEquals(List.of(older.getServiceOrderId(), newer.getServiceOrderId()),
                queue.stream().map(ExecutionOrderResponse::serviceOrderId).toList());
        org.mockito.Mockito.verify(repository).findByStatusIn(
                Set.of(ExecutionOrderStatus.QUEUED, ExecutionOrderStatus.IN_PROGRESS));
    }

    @Test
    void shouldAverageCompletedTasksPerService() {
        UUID oilId = UUID.randomUUID();
        UUID alignId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        ExecutionOrder first = orderOf(
                completed(oilId, "Oil change", t0, 30),
                completed(alignId, "Alignment", t0, 60));
        ExecutionOrder second = orderOf(
                completed(oilId, "Oil change", t0, 50),
                ExecutionTask.pending(UUID.randomUUID(), oilId, "Oil change", "MECHANICAL"));
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<AverageExecutionTimeResponse> result = new GetAverageExecutionTimeUseCase(repository).execute();

        assertEquals(2, result.size());
        AverageExecutionTimeResponse oil = result.stream()
                .filter(r -> r.serviceId().equals(oilId)).findFirst().orElseThrow();
        assertEquals(40.0, oil.averageMinutes());
        assertEquals(40.0 / 60.0, oil.averageHours(), 1e-9);
        assertEquals(2, oil.sampleCount());
        assertEquals("Oil change", oil.serviceName());
        assertEquals("MECHANICAL", oil.serviceType());
    }

    @Test
    void shouldReturnNothingWhenNoTaskIsCompleted() {
        when(repository.findAll()).thenReturn(List.of(ExecutionUseCaseTestSupport.order(orderId, UUID.randomUUID())));

        assertTrue(new GetAverageExecutionTimeUseCase(repository).execute().isEmpty());
    }

    private static ExecutionOrder queuedAt(Instant createdAt) {
        return new ExecutionOrder(UUID.randomUUID(), UUID.randomUUID(),
                List.of(ExecutionTask.pending(UUID.randomUUID(), UUID.randomUUID(), "x", "GENERAL")),
                ExecutionOrderStatus.QUEUED, createdAt, null, 1L);
    }

    private static ExecutionTask completed(UUID serviceId, String name, Instant start, long minutes) {
        return new ExecutionTask(UUID.randomUUID(), serviceId, name, "MECHANICAL", TaskStatus.COMPLETED,
                "mech", start, start.plusSeconds(minutes * 60));
    }

    private static ExecutionOrder orderOf(ExecutionTask... tasks) {
        return new ExecutionOrder(UUID.randomUUID(), UUID.randomUUID(), List.of(tasks), ExecutionOrderStatus.IN_PROGRESS,
                Instant.now(), null, 1L);
    }
}
