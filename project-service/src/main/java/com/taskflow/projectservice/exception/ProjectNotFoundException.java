package com.taskflow.projectservice.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long id) {
        super("No existe un proyecto con id " + id);
    }
}
