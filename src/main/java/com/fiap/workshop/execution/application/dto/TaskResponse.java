package com.fiap.workshop.execution.application.dto;

import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        String serviceType,
        String status,
        String mechanic,
        Instant startedAt,
        Instant finishedAt,
        Long durationMinutes
) {

    public static TaskResponse from(ExecutionTask task) {
        return new TaskResponse(
                task.getId(),
                task.getServiceId(),
                task.getServiceName(),
                task.getServiceType(),
                task.getStatus().name(),
                task.getMechanic(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.isCompleted() ? task.duration().toMinutes() : null);
    }
}
