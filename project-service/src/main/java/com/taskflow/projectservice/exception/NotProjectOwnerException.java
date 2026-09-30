package com.taskflow.projectservice.exception;

public class NotProjectOwnerException extends RuntimeException {

    public NotProjectOwnerException() {
        super("Solo el dueno del proyecto puede modificarlo o eliminarlo");
    }
}
