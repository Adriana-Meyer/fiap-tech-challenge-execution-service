package com.fiap.workshop.execution.application.usecase.saga;

import com.fiap.workshop.execution.application.dto.EnqueueExecutionCommand;
import com.fiap.workshop.execution.application.dto.TaskCommand;
import com.fiap.workshop.execution.application.port.in.EnqueueExecutionInputPort;
import com.fiap.workshop.execution.application.port.in.saga.HandleSagaCommandInputPort;
import com.fiap.workshop.execution.application.port.out.SagaEventPublisher;
import com.fiap.workshop.execution.application.saga.command.EnqueueExecution;
import com.fiap.workshop.execution.application.saga.command.SagaCommand;
import com.fiap.workshop.execution.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.execution.application.saga.event.ExecutionEnqueued;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Queues the order and answers. An order that cannot be queued because of its
 * content (no tasks, repeated task ids) is an answer ({@link ExecutionEnqueueFailed}),
 * which the saga undoes the payment and stock for; anything else unexpected is
 * thrown so the message goes back to the queue and, in the end, to the
 * dead-letter queue.
 */
@Service
public class HandleSagaCommandUseCase implements HandleSagaCommandInputPort {

    private final EnqueueExecutionInputPort enqueueExecution;
    private final SagaEventPublisher publisher;

    public HandleSagaCommandUseCase(EnqueueExecutionInputPort enqueueExecution, SagaEventPublisher publisher) {
        this.enqueueExecution = enqueueExecution;
        this.publisher = publisher;
    }

    @Override
    public void handle(UUID messageId, SagaCommand command) {
        if (command instanceof EnqueueExecution c) {
            onEnqueueExecution(messageId, c);
        } else {
            throw new IllegalArgumentException("Unsupported saga command " + command.getClass().getName());
        }
    }

    private void onEnqueueExecution(UUID messageId, EnqueueExecution command) {
        try {
            enqueueExecution.execute(new EnqueueExecutionCommand(command.serviceOrderId(), command.sagaId(),
                    command.tasks().stream()
                            .map(t -> new TaskCommand(t.taskId(), t.serviceId(), t.serviceName(), t.serviceType()))
                            .toList()));
        } catch (IllegalArgumentException e) {
            publisher.publish(new ExecutionEnqueueFailed(answerId(messageId, "ExecutionEnqueueFailed"),
                    command.sagaId(), command.serviceOrderId(), e.getMessage()));
            return;
        }
        publisher.publish(new ExecutionEnqueued(answerId(messageId, "ExecutionEnqueued"),
                command.sagaId(), command.serviceOrderId()));
    }

    /** Same command message, same answer id: the orchestrator recognizes a repeat. */
    private static UUID answerId(UUID commandMessageId, String eventType) {
        return UUID.nameUUIDFromBytes((commandMessageId + ":" + eventType).getBytes(StandardCharsets.UTF_8));
    }
}
