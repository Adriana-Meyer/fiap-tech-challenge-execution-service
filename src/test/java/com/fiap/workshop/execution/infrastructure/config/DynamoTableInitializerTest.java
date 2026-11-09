package com.fiap.workshop.execution.infrastructure.config;

import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoTableInitializerTest {

    @Mock
    private DynamoDbClient client;

    @Mock
    private DynamoDbTable<ExecutionOrderItem> table;

    @Mock
    private DynamoDbWaiter waiter;

    @Test
    void shouldLeaveAnExistingTableAlone() {
        when(client.describeTable(ArgumentMatchersSupport.describeConsumer()))
                .thenReturn(DescribeTableResponse.builder().build());

        new DynamoTableInitializer(client, table).afterPropertiesSet();

        verify(table, never()).createTable();
    }

    @Test
    void shouldCreateAMissingTableAndWaitForIt() {
        when(table.tableName()).thenReturn("execution-orders");
        when(client.describeTable(ArgumentMatchersSupport.describeConsumer()))
                .thenThrow(ResourceNotFoundException.builder().message("missing").build());
        when(client.waiter()).thenReturn(waiter);

        new DynamoTableInitializer(client, table).afterPropertiesSet();

        verify(table).createTable();
        verify(waiter).waitUntilTableExists(any(Consumer.class));
    }

    private static final class ArgumentMatchersSupport {
        @SuppressWarnings("unchecked")
        static Consumer<DescribeTableRequest.Builder> describeConsumer() {
            return org.mockito.ArgumentMatchers.<Consumer<DescribeTableRequest.Builder>>any();
        }
    }
}
