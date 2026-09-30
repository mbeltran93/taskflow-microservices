package com.taskflow.taskservice.dto;

import com.taskflow.taskservice.model.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "El status es obligatorio")
        TaskStatus status
) {
}
