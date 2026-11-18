package com.fiap.workshop.execution.infrastructure.config;

import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * Creates the table when it does not exist. Only for local runs and tests:
 * on AWS the table belongs to execution-database-infrastructure (Terraform),
 * and this stays off.
 */
@Component
@ConditionalOnProperty(name = "app.dynamodb.auto-create-table", havingValue = "true")
public class DynamoTableInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DynamoTableInitializer.class);

    private final DynamoDbClient client;
    private final DynamoDbTable<ExecutionOrderItem> table;

    public DynamoTableInitializer(DynamoDbClient client, DynamoDbTable<ExecutionOrderItem> table) {
        this.client = client;
        this.table = table;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            client.describeTable(request -> request.tableName(table.tableName()));
        } catch (ResourceNotFoundException e) {
            log.info("Creating DynamoDB table {}", table.tableName());
            table.createTable();
            client.waiter().waitUntilTableExists(request -> request.tableName(table.tableName()));
        }
    }
}
