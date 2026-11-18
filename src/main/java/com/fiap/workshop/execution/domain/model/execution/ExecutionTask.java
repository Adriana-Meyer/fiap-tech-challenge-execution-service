package com.fiap.workshop.execution.domain.model.execution;

import com.fiap.workshop.execution.domain.exception.InvalidStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One service to be performed on a vehicle. The id is the id of the item in
 * the OS service; service name and type are a snapshot, so Execution never
 * calls the catalog.
 */
public class ExecutionTask {

    private final UUID id;
    private final UUID serviceId;
    private final String serviceName;
    private final String serviceType;
    private TaskStatus status;
    private String mechanic;
    private Instant startedAt;
    private Instant finishedAt;

    public ExecutionTask(UUID id, UUID serviceId, String serviceName, String serviceType,
                         TaskStatus status, String mechanic, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.status = status;
        this.mechanic = mechanic;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static ExecutionTask pending(UUID id, UUID serviceId, String serviceName, String serviceType) {
        if (id == null || serviceId == null) {
            throw new IllegalArgumentException("Task id and service id are required");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Task service name is required");
        }
        return new ExecutionTask(id, serviceId, serviceName, serviceType, TaskStatus.PENDING, null, null, null);
    }

    public void start(String mechanicName) {
        if (mechanicName == null || mechanicName.isBlank()) {
            throw new IllegalArgumentException("The mechanic is required to start a task");
        }
        if (status != TaskStatus.PENDING) {
            throw new InvalidStateException("Task " + id + " cannot be started in status " + status);
        }
        this.status = TaskStatus.IN_PROGRESS;
        this.mechanic = mechanicName;
        this.startedAt = Instant.now();
    }

    public void finish() {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new InvalidStateException("Task " + id + " cannot be finished in status " + status);
        }
        this.status = TaskStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    /** Time spent on the task; only defined once it is completed. */
    public Duration duration() {
        if (!isCompleted()) {
            throw new InvalidStateException("Task " + id + " is not completed yet");
        }
        return Duration.between(startedAt, finishedAt);
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getServiceType() { return serviceType; }
    public TaskStatus getStatus() { return status; }
    public String getMechanic() { return mechanic; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
