package com.taskflow.taskservice.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(Throwable cause) {
        super("user-service no esta disponible en este momento; no se pudo validar el assigneeId", cause);
    }
}
