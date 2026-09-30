package com.taskflow.userservice.service;

import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.LoginRequest;
import com.taskflow.userservice.dto.RegisterRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.exception.EmailAlreadyUsedException;
import com.taskflow.userservice.exception.InvalidCredentialsException;
import com.taskflow.userservice.exception.UserNotFoundException;
import com.taskflow.userservice.model.User;
import com.taskflow.userservice.repository.UserRepository;
import com.taskflow.userservice.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        String hashed = passwordEncoder.encode(request.password());
        User user = new User(request.name(), request.email(), hashed);
        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token, UserResponse.from(user));
    }

    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserResponse.from(user);
    }
}
