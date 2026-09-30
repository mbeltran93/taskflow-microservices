package com.taskflow.taskservice.exception;

public class AssigneeNotFoundException extends RuntimeException {

    public AssigneeNotFoundException(Long userId) {
        super("No existe un usuario con id " + userId + " en user-service; no se puede asignar la tarea");
    }
}
