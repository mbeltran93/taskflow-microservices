package com.taskflow.taskservice.service;

import com.taskflow.taskservice.client.UserClient;
import com.taskflow.taskservice.dto.CreateTaskRequest;
import com.taskflow.taskservice.dto.TaskResponse;
import com.taskflow.taskservice.exception.TaskNotFoundException;
import com.taskflow.taskservice.model.Task;
import com.taskflow.taskservice.model.TaskStatus;
import com.taskflow.taskservice.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final UserClient userClient;

    public TaskService(TaskRepository taskRepository, UserClient userClient) {
        this.taskRepository = taskRepository;
        this.userClient = userClient;
    }

    public TaskResponse create(CreateTaskRequest request) {
        Task task = new Task(request.title(), request.description(), request.projectId(), request.dueDate());
        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse getById(Long id) {
        return TaskResponse.from(findOrThrow(id));
    }

    public List<TaskResponse> listByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> listAll() {
        return taskRepository.findAll().stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Antes de asignar la tarea, valida contra user-service (via UserClient)
     * que el assigneeId exista de verdad. Esta es la llamada sincronica
     * entre microservicios que pide el enunciado del portafolio.
     */
    public TaskResponse assign(Long taskId, Long assigneeId, String authorizationHeader) {
        log.info("Asignando tarea taskId={} a assigneeId={}", taskId, assigneeId);
        Task task = findOrThrow(taskId);
        userClient.getUserById(assigneeId, authorizationHeader);
        task.setAssigneeId(assigneeId);
        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse updateStatus(Long taskId, TaskStatus status) {
        Task task = findOrThrow(taskId);
        task.setStatus(status);
        return TaskResponse.from(taskRepository.save(task));
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
