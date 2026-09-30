package com.taskflow.userservice.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Ya existe un usuario registrado con el email " + email);
    }
}
