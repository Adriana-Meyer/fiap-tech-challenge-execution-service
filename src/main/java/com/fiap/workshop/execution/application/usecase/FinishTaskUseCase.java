package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.port.in.FinishTaskInputPort;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.domain.service.MetricsPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FinishTaskUseCase implements FinishTaskInputPort {

    private static final String EXECUTION_STATUS = "EXECUTION";

    private final ExecutionOrderRepository repository;
    private final MetricsPublisher metricsPublisher;

    public FinishTaskUseCase(ExecutionOrderRepository repository, MetricsPublisher metricsPublisher) {
        this.repository = repository;
        this.metricsPublisher = metricsPublisher;
    }

    public ExecutionOrderResponse execute(UUID serviceOrderId, UUID taskId) {
        ExecutionOrder order = repository.findByServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionOrder", serviceOrderId));
        order.finishTask(taskId);
        ExecutionOrder saved = repository.save(order);

        if (saved.isCompleted()) {
            metricsPublisher.recordServiceOrderStatusDuration(
                    serviceOrderId, EXECUTION_STATUS, saved.executionDuration().toMinutes());
        }
        return ExecutionOrderResponse.from(saved);
    }
}
