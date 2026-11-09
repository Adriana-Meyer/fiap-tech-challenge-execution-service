package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.port.in.StartTaskInputPort;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StartTaskUseCase implements StartTaskInputPort {

    private final ExecutionOrderRepository repository;

    public StartTaskUseCase(ExecutionOrderRepository repository) {
        this.repository = repository;
    }

    public ExecutionOrderResponse execute(UUID serviceOrderId, UUID taskId, String mechanic) {
        ExecutionOrder order = repository.findByServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionOrder", serviceOrderId));
        order.startTask(taskId, mechanic);
        return ExecutionOrderResponse.from(repository.save(order));
    }
}
