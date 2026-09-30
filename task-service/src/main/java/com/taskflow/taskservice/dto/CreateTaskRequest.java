package com.taskflow.taskservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank(message = "El titulo es obligatorio")
        String title,

        String description,

        @NotNull(message = "El projectId es obligatorio")
        Long projectId,

        LocalDate dueDate
) {
}
