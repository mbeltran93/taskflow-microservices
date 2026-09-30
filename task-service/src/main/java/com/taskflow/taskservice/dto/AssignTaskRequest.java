package com.taskflow.taskservice.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTaskRequest(
        @NotNull(message = "El assigneeId es obligatorio")
        Long assigneeId
) {
}
