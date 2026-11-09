package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.AverageExecutionTimeResponse;
import com.fiap.workshop.execution.application.port.in.GetAverageExecutionTimeInputPort;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Average time per service over every completed task, whatever the order's own status. */
@Service
public class GetAverageExecutionTimeUseCase implements GetAverageExecutionTimeInputPort {

    private final ExecutionOrderRepository repository;

    public GetAverageExecutionTimeUseCase(ExecutionOrderRepository repository) {
        this.repository = repository;
    }

    public List<AverageExecutionTimeResponse> execute() {
        Map<UUID, List<ExecutionTask>> byService = repository.findAll().stream()
                .flatMap(order -> order.getTasks().stream())
                .filter(ExecutionTask::isCompleted)
                .collect(Collectors.groupingBy(ExecutionTask::getServiceId));

        return byService.values().stream()
                .map(GetAverageExecutionTimeUseCase::summarize)
                .toList();
    }

    private static AverageExecutionTimeResponse summarize(List<ExecutionTask> tasks) {
        ExecutionTask first = tasks.get(0);
        double averageMinutes = tasks.stream()
                .mapToLong(task -> task.duration().toMinutes())
                .average()
                .orElse(0);
        return new AverageExecutionTimeResponse(first.getServiceId(), first.getServiceName(),
                first.getServiceType(), averageMinutes, averageMinutes / 60.0, tasks.size());
    }
}
