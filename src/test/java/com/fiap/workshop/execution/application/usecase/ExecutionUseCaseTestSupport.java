package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;

import java.util.UUID;

final class ExecutionUseCaseTestSupport {

    private ExecutionUseCaseTestSupport() {}

    static ExecutionTask task(UUID id, UUID serviceId, String name) {
        return ExecutionTask.pending(id, serviceId, name, "MECHANICAL");
    }

    static ExecutionOrder order(UUID orderId, UUID... taskIds) {
        return ExecutionOrder.enqueue(orderId, UUID.randomUUID(), java.util.Arrays.stream(taskIds)
                .map(id -> task(id, UUID.randomUUID(), "Service " + id.toString().substring(0, 4)))
                .toList());
    }
}
