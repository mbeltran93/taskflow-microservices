package com.taskflow.userservice.dto;

public record AuthResponse(String token, UserResponse user) {
}
