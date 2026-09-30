package com.taskflow.taskservice.controller;

import com.taskflow.taskservice.dto.AssignTaskRequest;
import com.taskflow.taskservice.dto.CreateTaskRequest;
import com.taskflow.taskservice.dto.TaskResponse;
import com.taskflow.taskservice.dto.UpdateStatusRequest;
import com.taskflow.taskservice.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable Long id) {
        return taskService.getById(id);
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) Long projectId) {
        return projectId != null ? taskService.listByProject(projectId) : taskService.listAll();
    }

    /**
     * Asigna la tarea a un usuario. El Authorization header del caller se
     * reenvia a user-service para validar que el assigneeId exista antes de
     * persistir el cambio.
     */
    @PatchMapping("/{id}/assign")
    public TaskResponse assign(@PathVariable Long id,
                               @Valid @RequestBody AssignTaskRequest request,
                               @RequestHeader("Authorization") String authorizationHeader) {
        return taskService.assign(id, request.assigneeId(), authorizationHeader);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return taskService.updateStatus(id, request.status());
    }
}
