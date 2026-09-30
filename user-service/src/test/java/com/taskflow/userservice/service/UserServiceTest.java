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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserService userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void registerCreaUnUsuarioNuevoConPasswordHasheado() {
        RegisterRequest request = new RegisterRequest("Ada Lovelace", "ada@taskflow.dev", "secret123");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            setId(u, 1L);
            return u;
        });

        UserResponse response = userService.register(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ada@taskflow.dev");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerFallaSiElEmailYaExiste() {
        RegisterRequest request = new RegisterRequest("Ada", "ada@taskflow.dev", "secret123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginDevuelveTokenConCredencialesCorrectas() {
        User user = new User("Ada", "ada@taskflow.dev", encoder.encode("secret123"));
        setId(user, 7L);
        when(userRepository.findByEmail("ada@taskflow.dev")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(7L, "ada@taskflow.dev", "Ada")).thenReturn("fake-jwt-token");

        AuthResponse response = userService.login(new LoginRequest("ada@taskflow.dev", "secret123"));

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void loginFallaConPasswordIncorrecto() {
        User user = new User("Ada", "ada@taskflow.dev", encoder.encode("secret123"));
        setId(user, 7L);
        when(userRepository.findByEmail("ada@taskflow.dev")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(new LoginRequest("ada@taskflow.dev", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFallaSiElUsuarioNoExiste() {
        when(userRepository.findByEmail("nadie@taskflow.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("nadie@taskflow.dev", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getByIdFallaSiNoExiste() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
