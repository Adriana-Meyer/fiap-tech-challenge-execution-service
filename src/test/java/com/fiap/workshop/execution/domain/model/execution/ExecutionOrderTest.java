package com.fiap.workshop.execution.domain.model.execution;

import com.fiap.workshop.execution.domain.exception.InvalidStateException;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOrderTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID taskA = UUID.randomUUID();
    private final UUID taskB = UUID.randomUUID();

    private ExecutionOrder queued() {
        return ExecutionOrder.enqueue(orderId, sagaId, List.of(
                ExecutionTask.pending(taskA, UUID.randomUUID(), "Oil change", "MECHANICAL"),
                ExecutionTask.pending(taskB, UUID.randomUUID(), "Alignment", "MECHANICAL")));
    }

    @Test
    void shouldEnqueueAsQueued() {
        ExecutionOrder order = queued();

        assertEquals(ExecutionOrderStatus.QUEUED, order.getStatus());
        assertEquals(2, order.getTasks().size());
        assertNotNull(order.getCreatedAt());
        assertNull(order.getFinishedAt());
        assertNull(order.getVersion());
        assertEquals(sagaId, order.getSagaId());
    }

    @Test
    void shouldRequireTheSagaThatAskedForTheExecution() {
        List<ExecutionTask> tasks = queued().getTasks();

        assertThrows(IllegalArgumentException.class, () -> ExecutionOrder.enqueue(orderId, null, tasks));
    }

    @Test
    void shouldTellWhichTasksAreCompleted() {
        ExecutionOrder order = queued();
        order.startTask(taskA, "mech1");
        order.finishTask(taskA);

        assertTrue(order.isTaskCompleted(taskA));
        assertFalse(order.isTaskCompleted(taskB));
        assertThrows(ResourceNotFoundException.class, () -> order.isTaskCompleted(UUID.randomUUID()));
    }

    @Test
    void shouldRejectInvalidQueues() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionOrder.enqueue(null, UUID.randomUUID(), queued().getTasks()));
        assertThrows(IllegalArgumentException.class, () -> ExecutionOrder.enqueue(orderId, sagaId, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ExecutionOrder.enqueue(orderId, sagaId, null));
        ExecutionTask task = ExecutionTask.pending(taskA, UUID.randomUUID(), "x", "GENERAL");
        assertThrows(IllegalArgumentException.class, () -> ExecutionOrder.enqueue(orderId, sagaId, List.of(task, task)));
    }

    @Test
    void shouldBecomeInProgressWhenATaskStarts() {
        ExecutionOrder order = queued();

        order.startTask(taskA, "mech1");

        assertEquals(ExecutionOrderStatus.IN_PROGRESS, order.getStatus());
    }

    @Test
    void shouldCompleteOnlyWhenTheLastTaskFinishes() {
        ExecutionOrder order = queued();
        order.startTask(taskA, "mech1");
        order.startTask(taskB, "mech2");

        order.finishTask(taskA);
        assertFalse(order.isCompleted());
        assertNull(order.getFinishedAt());

        order.finishTask(taskB);
        assertTrue(order.isCompleted());
        assertNotNull(order.getFinishedAt());
        assertTrue(order.executionDuration().toMillis() >= 0);
    }

    @Test
    void shouldFailForAnUnknownTask() {
        ExecutionOrder order = queued();

        assertThrows(ResourceNotFoundException.class, () -> order.startTask(UUID.randomUUID(), "mech1"));
        assertThrows(ResourceNotFoundException.class, () -> order.finishTask(UUID.randomUUID()));
    }

    @Test
    void shouldNotFinishATaskThatWasNotStarted() {
        ExecutionOrder order = queued();

        assertThrows(InvalidStateException.class, () -> order.finishTask(taskA));
        assertEquals(ExecutionOrderStatus.QUEUED, order.getStatus());
    }

    @Test
    void shouldNotReportADurationBeforeCompletion() {
        assertThrows(InvalidStateException.class, queued()::executionDuration);
    }

    @Test
    void shouldKeepTheTasksReadOnly() {
        ExecutionOrder order = queued();
        List<ExecutionTask> tasks = order.getTasks();

        assertThrows(UnsupportedOperationException.class, tasks::clear);
        assertEquals(Instant.class, order.getCreatedAt().getClass());
    }
}
