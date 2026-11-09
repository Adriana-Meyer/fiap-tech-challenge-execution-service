package com.fiap.workshop.execution.application.usecase;

import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.port.in.FindExecutionInputPort;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FindExecutionUseCase implements FindExecutionInputPort {

    private static final Set<ExecutionOrderStatus> OPEN =
            Set.of(ExecutionOrderStatus.QUEUED, ExecutionOrderStatus.IN_PROGRESS);

    private final ExecutionOrderRepository repository;

    public FindExecutionUseCase(ExecutionOrderRepository repository) {
        this.repository = repository;
    }

    public ExecutionOrderResponse findByServiceOrderId(UUID serviceOrderId) {
        return repository.findByServiceOrderId(serviceOrderId)
                .map(ExecutionOrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionOrder", serviceOrderId));
    }

    public List<ExecutionOrderResponse> findQueue() {
        return repository.findByStatusIn(OPEN).stream()
                .sorted(Comparator.comparing(ExecutionOrder::getCreatedAt))
                .map(ExecutionOrderResponse::from)
                .toList();
    }
}
