package com.fiap.workshop.execution.infrastructure.persistence.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One DynamoDB item per service order (partition key {@code serviceOrderId}),
 * with its tasks nested. The version attribute makes every write an optimistic
 * lock, so two mechanics finishing tasks of the same order at once cannot
 * overwrite each other.
 */
@DynamoDbBean
public class ExecutionOrderItem {

    private String serviceOrderId;
    private String sagaId;
    private String status;
    private Instant createdAt;
    private Instant finishedAt;
    private List<TaskItem> tasks = new ArrayList<>();
    private Long version;

    @DynamoDbPartitionKey
    public String getServiceOrderId() { return serviceOrderId; }
    public void setServiceOrderId(String serviceOrderId) { this.serviceOrderId = serviceOrderId; }

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public List<TaskItem> getTasks() { return tasks; }
    public void setTasks(List<TaskItem> tasks) { this.tasks = tasks; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
