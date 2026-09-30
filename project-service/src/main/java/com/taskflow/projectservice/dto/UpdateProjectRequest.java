package com.taskflow.projectservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProjectRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String name,

        String description
) {
}
