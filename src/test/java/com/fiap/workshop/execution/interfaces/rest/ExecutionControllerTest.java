package com.fiap.workshop.execution.interfaces.rest;

import com.fiap.workshop.execution.application.dto.AverageExecutionTimeResponse;
import com.fiap.workshop.execution.application.dto.ExecutionOrderResponse;
import com.fiap.workshop.execution.application.dto.TaskResponse;
import com.fiap.workshop.execution.application.port.in.FindExecutionInputPort;
import com.fiap.workshop.execution.application.port.in.FinishTaskInputPort;
import com.fiap.workshop.execution.application.port.in.GetAverageExecutionTimeInputPort;
import com.fiap.workshop.execution.application.port.in.StartTaskInputPort;
import com.fiap.workshop.execution.domain.exception.ConcurrentUpdateException;
import com.fiap.workshop.execution.domain.exception.InvalidStateException;
import com.fiap.workshop.execution.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.execution.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FindExecutionInputPort findUseCase;

    @MockBean
    private StartTaskInputPort startTask;

    @MockBean
    private FinishTaskInputPort finishTask;

    @MockBean
    private GetAverageExecutionTimeInputPort averageTime;

    @MockBean
    private JwtService jwtService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    private ExecutionOrderResponse response(String status) {
        TaskResponse task = new TaskResponse(taskId, UUID.randomUUID(), "Oil change", "MECHANICAL",
                "IN_PROGRESS", "mech1", Instant.now(), null, null);
        return new ExecutionOrderResponse(orderId, status, List.of(task), Instant.now(), null);
    }

    @Test
    void shouldListTheQueue() throws Exception {
        when(findUseCase.findQueue()).thenReturn(List.of(response("QUEUED")));

        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceOrderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].tasks[0].serviceName").value("Oil change"));
    }

    @Test
    void shouldFindAnOrder() throws Exception {
        when(findUseCase.findByServiceOrderId(orderId)).thenReturn(response("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/executions/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void shouldStartATaskAsTheAuthenticatedMechanic() throws Exception {
        when(startTask.execute(orderId, taskId, "mech1")).thenReturn(response("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{taskId}/start", orderId, taskId)
                        .principal(new TestingAuthenticationToken("mech1", "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].mechanic").value("mech1"));
    }

    @Test
    void shouldFinishATask() throws Exception {
        when(finishTask.execute(orderId, taskId)).thenReturn(response("COMPLETED"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{taskId}/finish", orderId, taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnTheAverageExecutionTimes() throws Exception {
        when(averageTime.execute()).thenReturn(List.of(
                new AverageExecutionTimeResponse(UUID.randomUUID(), "Oil change", "MECHANICAL", 40.0, 40.0 / 60, 2)));

        mockMvc.perform(get("/api/v1/executions/analytics/avg-execution-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averageMinutes").value(40.0))
                .andExpect(jsonPath("$[0].sampleCount").value(2));
    }

    @Test
    void shouldAnswer404WhenTheOrderIsNotQueued() throws Exception {
        when(findUseCase.findByServiceOrderId(orderId)).thenThrow(new ResourceNotFoundException("ExecutionOrder", orderId));

        mockMvc.perform(get("/api/v1/executions/{id}", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAnswer409ForAnInvalidTransition() throws Exception {
        when(finishTask.execute(orderId, taskId)).thenThrow(new InvalidStateException("not started"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{taskId}/finish", orderId, taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("not started"));
    }

    @Test
    void shouldAnswer409ForAConcurrentUpdate() throws Exception {
        when(finishTask.execute(orderId, taskId)).thenThrow(new ConcurrentUpdateException("retry"));

        mockMvc.perform(patch("/api/v1/executions/{id}/tasks/{taskId}/finish", orderId, taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("retry"));
    }

    @Test
    void shouldAnswer400ForAMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/executions/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
