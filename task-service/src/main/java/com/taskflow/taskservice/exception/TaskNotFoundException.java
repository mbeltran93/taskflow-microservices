package com.taskflow.taskservice.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("No existe una tarea con id " + id);
    }
}
