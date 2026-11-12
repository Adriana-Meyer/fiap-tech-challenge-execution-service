package com.fiap.workshop.execution.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.repository.ExecutionOrderRepository;
import com.fiap.workshop.execution.infrastructure.security.JwtTestTokens;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the service against a real SQS and SNS (LocalStack) and a real
 * PostgreSQL: a command is sent to the {@code execution-commands} queue exactly
 * as the OS service would send it, and the answer is read from a queue
 * subscribed to the {@code saga-events} topic, as the OS service reads it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Saga messaging (integration)")
class SagaMessagingIntegrationTest {

    @SuppressWarnings("resource")
    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
                    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS,
                            LocalStackContainer.Service.DYNAMODB);

    private static final String COMMANDS_QUEUE = "execution-commands";
    private static final String DEAD_LETTERS_QUEUE = "execution-commands-dlq";
    private static final String TEST_EVENTS_QUEUE = "test-saga-events";

    private static SqsClient sqs;
    private static String topicArn;
    private static String commandsUrl;
    private static String deadLettersUrl;
    private static String eventsUrl;

    static {
        localstack.start();
        createInfrastructure();
    }

    /** What the infrastructure repositories will create: queue + DLQ, topic, and a queue subscribed to the topic. */
    private static void createInfrastructure() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());
        sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();
        SnsClient sns = SnsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();

        deadLettersUrl = sqs.createQueue(r -> r.queueName(DEAD_LETTERS_QUEUE)).queueUrl();
        String deadLettersArn = arnOf(deadLettersUrl);
        commandsUrl = sqs.createQueue(r -> r.queueName(COMMANDS_QUEUE).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"2\"}".formatted(deadLettersArn)))).queueUrl();

        topicArn = sns.createTopic(r -> r.name("saga-events")).topicArn();
        eventsUrl = sqs.createQueue(r -> r.queueName(TEST_EVENTS_QUEUE)).queueUrl();
        sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(arnOf(eventsUrl))
                .attributes(Map.of("RawMessageDelivery", "true")));
    }

    private static String arnOf(String queueUrl) {
        return sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.sns.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
        registry.add("app.messaging.events-topic-arn", () -> topicArn);
        registry.add("app.dynamodb.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("app.dynamodb.auto-create-table", () -> "true");
        registry.add("app.jwt.secret", () -> JwtTestTokens.SECRET);
    }

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExecutionOrderRepository repository;
    @Autowired
    private MockMvc mockMvc;

    private final List<JsonNode> received = new ArrayList<>();

    // ---- helpers ----------------------------------------------------------

    private void send(UUID messageId, UUID sagaId, UUID orderId, String type, String payload) {
        String body = """
                {"messageId":"%s","sagaId":"%s","serviceOrderId":"%s","type":"%s","payload":%s}
                """.formatted(messageId, sagaId, orderId, type, payload);
        sqs.sendMessage(r -> r.queueUrl(commandsUrl).messageBody(body));
    }

    private void send(UUID sagaId, UUID orderId, String type, String payload) {
        send(UUID.randomUUID(), sagaId, orderId, type, payload);
    }

    private final UUID oilTask = UUID.randomUUID();
    private final UUID alignTask = UUID.randomUUID();

    private String tasks() {
        return """
                {"tasks":[{"taskId":"%s","serviceId":"%s","serviceName":"Oil change","serviceType":"MECHANICAL"},
                          {"taskId":"%s","serviceId":"%s","serviceName":"Alignment","serviceType":"MECHANICAL"}]}
                """.formatted(oilTask, UUID.randomUUID(), alignTask, UUID.randomUUID());
    }

    private void patch(UUID orderId, UUID taskId, String action) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/executions/{id}/tasks/{task}/{action}", orderId, taskId, action)
                        .header("Authorization", "Bearer " + JwtTestTokens.token("mech-ana", "MECHANIC")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    /** Waits for an answer about this order of this type, keeping the others it reads along the way. */
    private JsonNode awaitEvent(UUID orderId, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode event : received) {
                if (matches(event, orderId, type)) {
                    return event;
                }
            }
            for (Message message : sqs.receiveMessage(r -> r.queueUrl(eventsUrl).waitTimeSeconds(2)
                    .maxNumberOfMessages(10)).messages()) {
                received.add(objectMapper.readTree(message.body()));
                sqs.deleteMessage(r -> r.queueUrl(eventsUrl).receiptHandle(message.receiptHandle()));
            }
        }
        throw new AssertionError("No " + type + " for " + orderId + " within 30s; received " + received);
    }

    private static boolean matches(JsonNode event, UUID orderId, String type) {
        return orderId.toString().equals(event.get("serviceOrderId").asText()) && type.equals(event.get("type").asText());
    }

    private long countEvents(UUID orderId, String type) {
        return received.stream().filter(e -> matches(e, orderId, type)).count();
    }

    // ---- tests ------------------------------------------------------------

    @Test
    @DisplayName("should queue the order and answer enqueued")
    void shouldQueueTheOrderAndAnswerEnqueued() throws Exception {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        send(sagaId, orderId, "EnqueueExecution", tasks());

        JsonNode event = awaitEvent(orderId, "ExecutionEnqueued");
        assertEquals(sagaId.toString(), event.get("sagaId").asText());
        ExecutionOrder order = repository.findByServiceOrderId(orderId).orElseThrow();
        assertEquals(ExecutionOrderStatus.QUEUED, order.getStatus());
        assertEquals(sagaId, order.getSagaId());
        assertEquals(2, order.getTasks().size());
    }

    @Test
    @DisplayName("should answer failed, not fail, when the order has no tasks")
    void shouldAnswerFailedNotFailWhenTheOrderHasNoTasks() throws Exception {
        UUID orderId = UUID.randomUUID();

        send(UUID.randomUUID(), orderId, "EnqueueExecution", "{\"tasks\":[]}");

        JsonNode failed = awaitEvent(orderId, "ExecutionEnqueueFailed");
        assertTrue(failed.get("payload").get("reason").asText().contains("at least one task"));
        assertTrue(repository.findByServiceOrderId(orderId).isEmpty());
    }

    @Test
    @DisplayName("should answer a repeated command with the same message id and keep the progress")
    void shouldAnswerARepeatedCommandWithTheSameMessageIdAndKeepTheProgress() throws Exception {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        send(commandId, sagaId, orderId, "EnqueueExecution", tasks());
        JsonNode first = awaitEvent(orderId, "ExecutionEnqueued");
        patch(orderId, oilTask, "start");

        send(commandId, sagaId, orderId, "EnqueueExecution", tasks());
        long deadline = System.currentTimeMillis() + 15_000;
        while (countEvents(orderId, "ExecutionEnqueued") < 2 && System.currentTimeMillis() < deadline) {
            awaitEventQuietly(orderId);
        }

        assertEquals(2, countEvents(orderId, "ExecutionEnqueued"));
        List<String> ids = received.stream().filter(e -> matches(e, orderId, "ExecutionEnqueued"))
                .map(e -> e.get("messageId").asText()).distinct().toList();
        assertEquals(List.of(first.get("messageId").asText()), ids);
        assertEquals(ExecutionOrderStatus.IN_PROGRESS, repository.findByServiceOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("should tell the saga when the last task is finished")
    void shouldTellTheSagaWhenTheLastTaskIsFinished() throws Exception {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        send(sagaId, orderId, "EnqueueExecution", tasks());
        awaitEvent(orderId, "ExecutionEnqueued");

        patch(orderId, oilTask, "start");
        patch(orderId, oilTask, "finish");
        patch(orderId, alignTask, "start");
        assertEquals(0, countEvents(orderId, "ExecutionCompleted"));
        patch(orderId, alignTask, "finish");

        JsonNode completed = awaitEvent(orderId, "ExecutionCompleted");
        assertEquals(sagaId.toString(), completed.get("sagaId").asText());
        assertEquals(ExecutionOrderStatus.COMPLETED, repository.findByServiceOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("should publish the completion again, with the same message id, when the last finish is repeated")
    void shouldPublishTheCompletionAgainWithTheSameMessageIdWhenTheLastFinishIsRepeated() throws Exception {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        send(sagaId, orderId, "EnqueueExecution", tasks());
        awaitEvent(orderId, "ExecutionEnqueued");
        patch(orderId, oilTask, "start");
        patch(orderId, oilTask, "finish");
        patch(orderId, alignTask, "start");
        patch(orderId, alignTask, "finish");
        JsonNode first = awaitEvent(orderId, "ExecutionCompleted");

        patch(orderId, alignTask, "finish");

        long deadline = System.currentTimeMillis() + 15_000;
        while (countEvents(orderId, "ExecutionCompleted") < 2 && System.currentTimeMillis() < deadline) {
            awaitEventQuietly(orderId);
        }
        assertEquals(2, countEvents(orderId, "ExecutionCompleted"));
        assertEquals(1, received.stream().filter(e -> matches(e, orderId, "ExecutionCompleted"))
                .map(e -> e.get("messageId").asText()).distinct().count());
        assertEquals(first.get("messageId").asText(), received.stream()
                .filter(e -> matches(e, orderId, "ExecutionCompleted")).findFirst().orElseThrow().get("messageId").asText());
    }

    @Test
    @DisplayName("should move a message it cannot handle to the dead-letter queue")
    void shouldMoveAMessageItCannotHandleToTheDeadLetterQueue() {
        UUID orderId = UUID.randomUUID();
        // An unknown command type fails in the listener on every attempt.
        send(UUID.randomUUID(), orderId, "ReserveStock", "{}");

        long deadline = System.currentTimeMillis() + 45_000;
        List<Message> dead = List.of();
        while (dead.isEmpty() && System.currentTimeMillis() < deadline) {
            dead = sqs.receiveMessage(r -> r.queueUrl(deadLettersUrl).waitTimeSeconds(2)).messages();
        }

        assertEquals(1, dead.size());
        assertTrue(dead.get(0).body().contains(orderId.toString()));
    }

    /** One short read of the events queue, only to let a late answer arrive. */
    private void awaitEventQuietly(UUID orderId) throws Exception {
        for (Message message : sqs.receiveMessage(r -> r.queueUrl(eventsUrl).waitTimeSeconds(2)
                .maxNumberOfMessages(10)).messages()) {
            received.add(objectMapper.readTree(message.body()));
            sqs.deleteMessage(r -> r.queueUrl(eventsUrl).receiptHandle(message.receiptHandle()));
        }
    }
}
