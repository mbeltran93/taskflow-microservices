package com.taskflow.projectservice.dto;

import com.taskflow.projectservice.model.Project;

public record ProjectResponse(Long id, String name, String description, Long ownerId) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getOwnerId());
    }
}
