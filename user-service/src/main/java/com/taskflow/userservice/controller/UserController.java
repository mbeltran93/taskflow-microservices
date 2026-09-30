package com.taskflow.userservice.controller;

import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.LoginRequest;
import com.taskflow.userservice.dto.RegisterRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /**
     * Consultado por task-service para validar que un assigneeId existe
     * antes de asignarle una tarea.
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }
}
