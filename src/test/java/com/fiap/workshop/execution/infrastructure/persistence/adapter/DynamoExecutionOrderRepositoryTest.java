package com.fiap.workshop.execution.infrastructure.persistence.adapter;

import com.fiap.workshop.execution.domain.exception.ConcurrentUpdateException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import com.fiap.workshop.execution.infrastructure.persistence.mapper.ExecutionOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoExecutionOrderRepositoryTest {

    @Mock
    private DynamoDbTable<ExecutionOrderItem> table;

    private final ExecutionOrderMapper mapper = new ExecutionOrderMapper();
    private final UUID orderId = UUID.randomUUID();

    private ExecutionOrder order() {
        return ExecutionOrder.enqueue(orderId,
                List.of(ExecutionTask.pending(UUID.randomUUID(), UUID.randomUUID(), "Oil change", "MECHANICAL")));
    }

    @Test
    void shouldReturnTheStoredItemWithItsNewVersionOnSave() {
        DynamoExecutionOrderRepository repository = new DynamoExecutionOrderRepository(table, mapper);
        ExecutionOrderItem stored = mapper.toItem(order());
        stored.setVersion(1L);
        when(table.updateItem(any(ExecutionOrderItem.class))).thenReturn(stored);

        ExecutionOrder saved = repository.save(order());

        assertEquals(1L, saved.getVersion());
    }

    @Test
    void shouldTranslateAFailedConditionIntoAConcurrentUpdate() {
        DynamoExecutionOrderRepository repository = new DynamoExecutionOrderRepository(table, mapper);
        when(table.updateItem(any(ExecutionOrderItem.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("version").build());

        assertThrows(ConcurrentUpdateException.class, () -> repository.save(order()));
    }

    @Test
    void shouldReturnEmptyWhenTheItemDoesNotExist() {
        DynamoExecutionOrderRepository repository = new DynamoExecutionOrderRepository(table, mapper);
        when(table.getItem(any(Key.class))).thenReturn(null);

        assertTrue(repository.findByServiceOrderId(orderId).isEmpty());
    }

    @Test
    void shouldMapAFoundItem() {
        DynamoExecutionOrderRepository repository = new DynamoExecutionOrderRepository(table, mapper);
        when(table.getItem(any(Key.class))).thenReturn(mapper.toItem(order()));

        assertEquals(orderId, repository.findByServiceOrderId(orderId).orElseThrow().getServiceOrderId());
    }
}
