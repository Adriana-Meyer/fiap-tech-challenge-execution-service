package com.fiap.workshop.execution.application.saga.event;

import java.util.UUID;

/** Every service of the order is done. */
public record ExecutionCompleted(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {

    /**
     * One completion per saga, so the id is derived from it: publishing the
     * event again (after a failed publish) yields the same message and the
     * orchestrator drops the repeat.
     */
    public static ExecutionCompleted of(UUID sagaId, UUID serviceOrderId) {
        return new ExecutionCompleted(
                UUID.nameUUIDFromBytes(("ExecutionCompleted:" + sagaId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sagaId, serviceOrderId);
    }
}
