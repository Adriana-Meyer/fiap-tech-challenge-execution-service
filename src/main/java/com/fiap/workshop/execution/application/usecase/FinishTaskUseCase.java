package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.port.in.FinishTaskInputPort;
import com.fiap.workshop.execution.application.port.out.SagaEventPublisher;
import com.fiap.workshop.execution.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.domain.service.MetricsPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Finishes a task. When it is the last one the order is completed: the
 * duration goes to New Relic and the saga is told ({@link ExecutionCompleted}).
 *
 * <p>The order is saved before the event is published, and DynamoDB has no
 * transaction around both. If the publish fails the mechanic gets an error
 * and tries again; finishing a task of an order that is already completed
 * then publishes the event once more, which is harmless (same message id)
 * instead of a conflict that would leave the saga waiting for ever.
 */
@Service
public class FinishTaskUseCase implements FinishTaskInputPort {

    private static final String EXECUTION_STATUS = "EXECUTION";

    private final ExecutionOrderRepository repository;
    private final MetricsPublisher metricsPublisher;
    private final SagaEventPublisher eventPublisher;

    public FinishTaskUseCase(ExecutionOrderRepository repository, MetricsPublisher metricsPublisher,
                             SagaEventPublisher eventPublisher) {
        this.repository = repository;
        this.metricsPublisher = metricsPublisher;
        this.eventPublisher = eventPublisher;
    }

    public ExecutionOrderResponse execute(UUID serviceOrderId, UUID taskId) {
        ExecutionOrder order = repository.findByServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionOrder", serviceOrderId));

        if (order.isCompleted() && order.isTaskCompleted(taskId)) {
            publishCompleted(order);
            return ExecutionOrderResponse.from(order);
        }

        order.finishTask(taskId);
        ExecutionOrder saved = repository.save(order);

        if (saved.isCompleted()) {
            metricsPublisher.recordServiceOrderStatusDuration(
                    serviceOrderId, EXECUTION_STATUS, saved.executionDuration().toMinutes());
            publishCompleted(saved);
        }
        return ExecutionOrderResponse.from(saved);
    }

    private void publishCompleted(ExecutionOrder order) {
        eventPublisher.publish(ExecutionCompleted.of(order.getSagaId(), order.getServiceOrderId()));
    }
}
