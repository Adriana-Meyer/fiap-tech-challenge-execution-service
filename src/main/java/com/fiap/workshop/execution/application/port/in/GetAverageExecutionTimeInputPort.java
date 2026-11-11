package com.fiap.workshop.execution.application.port.in;

import com.fiap.workshop.execution.application.dto.AverageExecutionTimeResponse;

import java.util.List;

public interface GetAverageExecutionTimeInputPort {
    List<AverageExecutionTimeResponse> execute();
}
