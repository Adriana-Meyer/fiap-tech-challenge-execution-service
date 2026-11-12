package com.fiap.workshop.execution.application.port.in.saga;

import com.fiap.workshop.execution.application.saga.command.SagaCommand;

import java.util.UUID;

public interface HandleSagaCommandInputPort {

    /**
     * Runs the command and answers the OS service. Repeating a command is
     * harmless: the use case is idempotent and the answer carries an id
     * derived from the command's, so the orchestrator drops the duplicate.
     *
     * @param messageId id of the message that carried the command
     */
    void handle(UUID messageId, SagaCommand command);
}
