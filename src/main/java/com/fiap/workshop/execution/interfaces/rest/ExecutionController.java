package com.fiap.workshop.execution.interfaces.rest;

import com.fiap.workshop.execution.application.dto.AverageExecutionTimeResponse;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.port.in.FindExecutionInputPort;
import com.fiap.workshop.execution.application.port.in.FinishTaskInputPort;
import com.fiap.workshop.execution.application.port.in.GetAverageExecutionTimeInputPort;
import com.fiap.workshop.execution.application.port.in.StartTaskInputPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Mechanics work the queue over HTTP. Orders enter the queue through the
 * saga's EnqueueExecution command, never through this API.
 */
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final FindExecutionInputPort findUseCase;
    private final StartTaskInputPort startTaskUseCase;
    private final FinishTaskInputPort finishTaskUseCase;
    private final GetAverageExecutionTimeInputPort averageTimeUseCase;

    public ExecutionController(FindExecutionInputPort findUseCase,
                               StartTaskInputPort startTaskUseCase,
                               FinishTaskInputPort finishTaskUseCase,
                               GetAverageExecutionTimeInputPort averageTimeUseCase) {
        this.findUseCase = findUseCase;
        this.startTaskUseCase = startTaskUseCase;
        this.finishTaskUseCase = finishTaskUseCase;
        this.averageTimeUseCase = averageTimeUseCase;
    }

    @GetMapping
    public List<ExecutionOrderResponse> queue() {
        return findUseCase.findQueue();
    }

    @GetMapping("/analytics/avg-execution-time")
    public List<AverageExecutionTimeResponse> averageExecutionTime() {
        return averageTimeUseCase.execute();
    }

    @GetMapping("/{serviceOrderId}")
    public ExecutionOrderResponse findByServiceOrderId(@PathVariable UUID serviceOrderId) {
        return findUseCase.findByServiceOrderId(serviceOrderId);
    }

    @PatchMapping("/{serviceOrderId}/tasks/{taskId}/start")
    public ExecutionOrderResponse startTask(@PathVariable UUID serviceOrderId, @PathVariable UUID taskId,
                                            Principal principal) {
        return startTaskUseCase.execute(serviceOrderId, taskId, principal.getName());
    }

    @PatchMapping("/{serviceOrderId}/tasks/{taskId}/finish")
    public ExecutionOrderResponse finishTask(@PathVariable UUID serviceOrderId, @PathVariable UUID taskId) {
        return finishTaskUseCase.execute(serviceOrderId, taskId);
    }
}
