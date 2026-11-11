package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.dto.TaskCommand;
import com.fiap.workshop.execution.application.port.in.EnqueueExecutionInputPort;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Idempotent per service order: a redelivered saga command returns the order
 * that already exists instead of resetting its progress.
 */
@Service
public class EnqueueExecutionUseCase implements EnqueueExecutionInputPort {

    private final ExecutionOrderRepository repository;

    public EnqueueExecutionUseCase(ExecutionOrderRepository repository) {
        this.repository = repository;
    }

    public ExecutionOrderResponse execute(EnqueueExecutionCommand command) {
        return repository.findByServiceOrderId(command.serviceOrderId())
                .map(ExecutionOrderResponse::from)
                .orElseGet(() -> enqueue(command));
    }

    private ExecutionOrderResponse enqueue(EnqueueExecutionCommand command) {
        List<ExecutionTask> tasks = command.tasks().stream().map(EnqueueExecutionUseCase::toTask).toList();
        ExecutionOrder order = ExecutionOrder.enqueue(command.serviceOrderId(), tasks);
        return ExecutionOrderResponse.from(repository.save(order));
    }

    private static ExecutionTask toTask(TaskCommand task) {
        return ExecutionTask.pending(task.taskId(), task.serviceId(), task.serviceName(), task.serviceType());
    }
}
