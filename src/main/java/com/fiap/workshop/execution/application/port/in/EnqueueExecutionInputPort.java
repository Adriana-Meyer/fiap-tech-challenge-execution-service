package com.fiap.workshop.execution.application.port.in;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;

public interface EnqueueExecutionInputPort {
    ExecutionOrderResponse execute(EnqueueExecutionCommand command);
}
