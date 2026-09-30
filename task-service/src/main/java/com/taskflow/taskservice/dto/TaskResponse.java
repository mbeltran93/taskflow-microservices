package com.taskflow.taskservice.dto;

import com.taskflow.taskservice.model.Task;
import com.taskflow.taskservice.model.TaskStatus;

import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Long projectId,
        Long assigneeId,
        LocalDate dueDate
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getProjectId(),
                task.getAssigneeId(),
                task.getDueDate()
        );
    }
}
