package com.fiap.workshop.execution.domain.model.execution;

import com.fiap.workshop.execution.domain.exception.InvalidStateException;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The execution queue of one service order: its tasks and where they stand.
 * Created when the saga enqueues the order, so {@code createdAt} is when the
 * order entered execution.
 */
public class ExecutionOrder {

    private final UUID serviceOrderId;
    private final UUID sagaId;
    private final List<ExecutionTask> tasks;
    private final Instant createdAt;
    private ExecutionOrderStatus status;
    private Instant finishedAt;
    private final Long version;

    public ExecutionOrder(UUID serviceOrderId, UUID sagaId, List<ExecutionTask> tasks, ExecutionOrderStatus status,
                          Instant createdAt, Instant finishedAt, Long version) {
        this.serviceOrderId = serviceOrderId;
        this.sagaId = sagaId;
        this.tasks = List.copyOf(tasks);
        this.status = status;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.version = version;
    }

    /** @param sagaId the saga that asked for the execution; it is told when the order is completed */
    public static ExecutionOrder enqueue(UUID serviceOrderId, UUID sagaId, List<ExecutionTask> tasks) {
        if (serviceOrderId == null || sagaId == null) {
            throw new IllegalArgumentException("Service order id and saga id are required");
        }
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("An execution order needs at least one task");
        }
        if (new HashSet<>(tasks.stream().map(ExecutionTask::getId).toList()).size() != tasks.size()) {
            throw new IllegalArgumentException("Task ids must be unique");
        }
        return new ExecutionOrder(serviceOrderId, sagaId, tasks, ExecutionOrderStatus.QUEUED,
                Instant.now(), null, null);
    }

    public void startTask(UUID taskId, String mechanic) {
        findTask(taskId).start(mechanic);
        this.status = ExecutionOrderStatus.IN_PROGRESS;
    }

    /** Finishes the task; the order completes when its last task does. */
    public void finishTask(UUID taskId) {
        findTask(taskId).finish();
        if (tasks.stream().allMatch(ExecutionTask::isCompleted)) {
            this.status = ExecutionOrderStatus.COMPLETED;
            this.finishedAt = Instant.now();
        }
    }

    public boolean isTaskCompleted(UUID taskId) {
        return findTask(taskId).isCompleted();
    }

    public boolean isCompleted() {
        return status == ExecutionOrderStatus.COMPLETED;
    }

    /** Time from entering execution until the last task finished. */
    public Duration executionDuration() {
        if (!isCompleted()) {
            throw new InvalidStateException("Execution of service order " + serviceOrderId + " is not completed yet");
        }
        return Duration.between(createdAt, finishedAt);
    }

    private ExecutionTask findTask(UUID taskId) {
        return tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionTask", taskId));
    }

    public UUID getServiceOrderId() { return serviceOrderId; }
    public UUID getSagaId() { return sagaId; }
    public List<ExecutionTask> getTasks() { return Collections.unmodifiableList(tasks); }
    public ExecutionOrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }

    /** Optimistic-lock token owned by the persistence adapter; null until first saved. */
    public Long getVersion() { return version; }
}
