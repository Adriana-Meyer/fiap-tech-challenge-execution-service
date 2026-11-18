package com.fiap.workshop.execution.application.dto;

import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecutionOrderResponse(
        UUID serviceOrderId,
        String status,
        List<TaskResponse> tasks,
        Instant createdAt,
        Instant finishedAt
) {

    public static ExecutionOrderResponse from(ExecutionOrder order) {
        return new ExecutionOrderResponse(
                order.getServiceOrderId(),
                order.getStatus().name(),
                order.getTasks().stream().map(TaskResponse::from).toList(),
                order.getCreatedAt(),
                order.getFinishedAt());
    }
}
