package com.fiap.workshop.execution.application.port.in;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;

import java.util.UUID;

public interface StartTaskInputPort {
    ExecutionOrderResponse execute(UUID serviceOrderId, UUID taskId, String mechanic);
}
