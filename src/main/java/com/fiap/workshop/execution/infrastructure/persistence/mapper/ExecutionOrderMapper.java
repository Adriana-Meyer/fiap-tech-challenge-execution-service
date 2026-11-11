package com.fiap.workshop.execution.infrastructure.persistence.mapper;

import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.domain.model.execution.TaskStatus;
import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import com.fiap.workshop.execution.infrastructure.persistence.entity.TaskItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ExecutionOrderMapper {

    public ExecutionOrder toDomain(ExecutionOrderItem item) {
        List<ExecutionTask> tasks = item.getTasks().stream().map(this::toDomain).toList();
        return new ExecutionOrder(UUID.fromString(item.getServiceOrderId()), tasks,
                ExecutionOrderStatus.valueOf(item.getStatus()), item.getCreatedAt(),
                item.getFinishedAt(), item.getVersion());
    }

    public ExecutionOrderItem toItem(ExecutionOrder order) {
        ExecutionOrderItem item = new ExecutionOrderItem();
        item.setServiceOrderId(order.getServiceOrderId().toString());
        item.setStatus(order.getStatus().name());
        item.setCreatedAt(order.getCreatedAt());
        item.setFinishedAt(order.getFinishedAt());
        item.setTasks(new ArrayList<>(order.getTasks().stream().map(this::toItem).toList()));
        item.setVersion(order.getVersion());
        return item;
    }

    private ExecutionTask toDomain(TaskItem item) {
        return new ExecutionTask(UUID.fromString(item.getId()), UUID.fromString(item.getServiceId()),
                item.getServiceName(), item.getServiceType(), TaskStatus.valueOf(item.getStatus()),
                item.getMechanic(), item.getStartedAt(), item.getFinishedAt());
    }

    private TaskItem toItem(ExecutionTask task) {
        TaskItem item = new TaskItem();
        item.setId(task.getId().toString());
        item.setServiceId(task.getServiceId().toString());
        item.setServiceName(task.getServiceName());
        item.setServiceType(task.getServiceType());
        item.setStatus(task.getStatus().name());
        item.setMechanic(task.getMechanic());
        item.setStartedAt(task.getStartedAt());
        item.setFinishedAt(task.getFinishedAt());
        return item;
    }
}
