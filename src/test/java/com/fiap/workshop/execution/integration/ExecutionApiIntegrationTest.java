package com.fiap.workshop.execution.integration;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.dto.TaskCommand;
import com.fiap.workshop.execution.application.port.in.EnqueueExecutionInputPort;
import com.fiap.workshop.execution.domain.exception.ConcurrentUpdateException;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.infrastructure.security.JwtTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fiap.workshop.execution.application.port.out.SagaEventPublisher;
import com.fiap.workshop.execution.application.saga.event.ExecutionCompleted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole service against DynamoDB Local (the table is created by the
 * service itself) and exercises security, REST and persistence together.
 * Orders are queued through the input port, as the saga handler will do.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.jwt.secret=" + JwtTestTokens.SECRET, "app.messaging.enabled=false"})
@DisplayName("Execution API (integration)")
class ExecutionApiIntegrationTest {

    @SuppressWarnings("resource")
    static final GenericContainer<?> dynamodb = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
            .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
            .withExposedPorts(8000)
            .waitingFor(Wait.forListeningPort());

    static {
        dynamodb.start();
    }

    @DynamicPropertySource
    static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint", () -> "http://" + dynamodb.getHost() + ":" + dynamodb.getMappedPort(8000));
        registry.add("app.dynamodb.auto-create-table", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    /** The REST flow runs without a topic; the real publisher is exercised in SagaMessagingIntegrationTest. */
    @MockBean
    private SagaEventPublisher eventPublisher;

    @Autowired
    private EnqueueExecutionInputPort enqueue;

    @Autowired
    private ExecutionOrderRepository repository;

    private static String bearer(String user, String role) {
        return "Bearer " + JwtTestTokens.token(user, role);
    }

    private UUID enqueueOrder(UUID... taskIds) {
        UUID orderId = UUID.randomUUID();
        enqueue.execute(new EnqueueExecutionCommand(orderId, UUID.randomUUID(), java.util.Arrays.stream(taskIds)
                .map(id -> new TaskCommand(id, UUID.randomUUID(), "Service " + id.toString().substring(0, 4), "MECHANICAL"))
                .toList()));
        return orderId;
    }

    @Test
    @DisplayName("should expose health and OpenAPI without a token")
    void shouldExposeHealthAndOpenApiWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should answer 401 without a token and 403 for roles outside the workshop floor")
    void shouldProtectTheEndpoints() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/executions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/executions")
                        .header("Authorization", "Bearer " + JwtTestTokens.token("u", null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/executions").header("Authorization", bearer("s1", "STOCKIST")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, UUID.randomUUID())
                        .header("Authorization", bearer("c1", "CONSULTANT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/executions/analytics/avg-execution-time")
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should answer 404 for an order that was never queued")
    void shouldAnswer404ForUnknownOrders() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/executions/{id}", orderId).header("Authorization", bearer("c1", "CONSULTANT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, UUID.randomUUID())
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should work a queued order from the first task to completion")
    void shouldWorkAnOrderToCompletion() throws Exception {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID orderId = enqueueOrder(taskA, taskB);

        mockMvc.perform(get("/api/v1/executions/{id}", orderId).header("Authorization", bearer("c1", "CONSULTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.tasks.length()").value(2));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, taskA)
                        .header("Authorization", bearer("mech-ana", "MECHANIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/executions").header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceOrderId=='" + orderId + "')].status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/finish", orderId, taskA)
                        .header("Authorization", bearer("mech-ana", "MECHANIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, taskB)
                        .header("Authorization", bearer("mech-bia", "MECHANIC")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/finish", orderId, taskB)
                        .header("Authorization", bearer("mech-bia", "MECHANIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishedAt").exists())
                .andExpect(jsonPath("$.tasks[0].mechanic").value("mech-ana"))
                .andExpect(jsonPath("$.tasks[1].mechanic").value("mech-bia"))
                .andExpect(jsonPath("$.tasks[1].durationMinutes").value(0));
        org.mockito.Mockito.verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(ExecutionCompleted.class));

        mockMvc.perform(get("/api/v1/executions/analytics/avg-execution-time")
                        .header("Authorization", bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("should refuse to start a task twice or finish one that was not started")
    void shouldRefuseInvalidTransitions() throws Exception {
        UUID taskA = UUID.randomUUID();
        UUID orderId = enqueueOrder(taskA);

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/finish", orderId, taskA)
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, taskA)
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, taskA)
                        .header("Authorization", bearer("m2", "MECHANIC")))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, UUID.randomUUID())
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should not reset the progress when the enqueue command is redelivered")
    void shouldBeIdempotentOnEnqueue() throws Exception {
        UUID taskA = UUID.randomUUID();
        UUID orderId = enqueueOrder(taskA);
        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{t}/start", orderId, taskA)
                        .header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(status().isOk());

        enqueue.execute(new EnqueueExecutionCommand(orderId, UUID.randomUUID(),
                List.of(new TaskCommand(taskA, UUID.randomUUID(), "Other", "GENERAL"))));

        mockMvc.perform(get("/api/v1/executions/{id}", orderId).header("Authorization", bearer("m1", "MECHANIC")))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.tasks[0].mechanic").value("m1"));
    }

    @Test
    @DisplayName("should reject a stale write instead of overwriting the other mechanic's change")
    void shouldDetectConcurrentUpdates() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID orderId = enqueueOrder(taskA, taskB);

        ExecutionOrder first = repository.findByServiceOrderId(orderId).orElseThrow();
        ExecutionOrder second = repository.findByServiceOrderId(orderId).orElseThrow();
        first.startTask(taskA, "mech-ana");
        second.startTask(taskB, "mech-bia");

        ExecutionOrder saved = repository.save(first);
        assertThrows(ConcurrentUpdateException.class, () -> repository.save(second));

        ExecutionOrder stored = repository.findByServiceOrderId(orderId).orElseThrow();
        assertEquals(saved.getVersion(), stored.getVersion());
        assertTrue(stored.getTasks().stream().anyMatch(t -> "mech-ana".equals(t.getMechanic())));
        assertTrue(stored.getTasks().stream().noneMatch(t -> "mech-bia".equals(t.getMechanic())));
    }
}
