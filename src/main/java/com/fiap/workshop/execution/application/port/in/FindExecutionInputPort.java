package com.fiap.workshop.execution.application.port.in;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;

import java.util.List;
import java.util.UUID;

public interface FindExecutionInputPort {
    ExecutionOrderResponse findByServiceOrderId(UUID serviceOrderId);

    /** Orders still to be done (queued or in progress), oldest first. */
    List<ExecutionOrderResponse> findQueue();
}
