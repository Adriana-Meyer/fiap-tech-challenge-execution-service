package com.fiap.workshop.execution.domain.repository;

import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExecutionOrderRepository {

    /** @throws com.fiap.workshop.execution.domain.exception.ConcurrentUpdateException if the stored order changed meanwhile */
    ExecutionOrder save(ExecutionOrder order);

    Optional<ExecutionOrder> findByServiceOrderId(UUID serviceOrderId);

    List<ExecutionOrder> findByStatusIn(Set<ExecutionOrderStatus> statuses);

    List<ExecutionOrder> findAll();
}
