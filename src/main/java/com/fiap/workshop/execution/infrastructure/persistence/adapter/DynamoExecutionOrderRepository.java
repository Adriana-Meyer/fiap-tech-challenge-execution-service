package com.fiap.workshop.execution.infrastructure.persistence.adapter;

import com.fiap.workshop.execution.domain.exception.ConcurrentUpdateException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import com.fiap.workshop.execution.infrastructure.persistence.mapper.ExecutionOrderMapper;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Listing methods use a table scan: the table holds the orders of one
 * workshop, and the queue and the averages are small, infrequent reads. An
 * index on status would replace it if the volume ever justified one.
 */
@Repository
public class DynamoExecutionOrderRepository implements ExecutionOrderRepository {

    private final DynamoDbTable<ExecutionOrderItem> table;
    private final ExecutionOrderMapper mapper;

    public DynamoExecutionOrderRepository(DynamoDbTable<ExecutionOrderItem> table, ExecutionOrderMapper mapper) {
        this.table = table;
        this.mapper = mapper;
    }

    @Override
    public ExecutionOrder save(ExecutionOrder order) {
        try {
            // updateItem returns the stored item with the incremented version.
            return mapper.toDomain(table.updateItem(mapper.toItem(order)));
        } catch (ConditionalCheckFailedException e) {
            throw new ConcurrentUpdateException(
                    "Execution order " + order.getServiceOrderId() + " was changed by another request; retry");
        }
    }

    @Override
    public Optional<ExecutionOrder> findByServiceOrderId(UUID serviceOrderId) {
        ExecutionOrderItem item = table.getItem(Key.builder().partitionValue(serviceOrderId.toString()).build());
        return Optional.ofNullable(item).map(mapper::toDomain);
    }

    @Override
    public List<ExecutionOrder> findByStatusIn(Set<ExecutionOrderStatus> statuses) {
        return findAll().stream().filter(order -> statuses.contains(order.getStatus())).toList();
    }

    @Override
    public List<ExecutionOrder> findAll() {
        return table.scan().items().stream().map(mapper::toDomain).toList();
    }
}
