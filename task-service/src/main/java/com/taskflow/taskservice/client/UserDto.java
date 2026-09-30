package com.taskflow.taskservice.client;

/**
 * Espejo minimo del UserResponse que expone user-service en GET /api/users/{id}.
 */
public record UserDto(Long id, String name, String email) {
}
