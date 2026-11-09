package com.fiap.workshop.execution.application.port.in;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;

import java.util.UUID;

public interface FinishTaskInputPort {
    ExecutionOrderResponse execute(UUID serviceOrderId, UUID taskId);
}
