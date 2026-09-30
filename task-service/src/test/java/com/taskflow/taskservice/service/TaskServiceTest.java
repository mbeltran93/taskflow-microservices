package com.taskflow.taskservice.service;

import com.taskflow.taskservice.client.UserClient;
import com.taskflow.taskservice.client.UserDto;
import com.taskflow.taskservice.dto.CreateTaskRequest;
import com.taskflow.taskservice.dto.TaskResponse;
import com.taskflow.taskservice.exception.AssigneeNotFoundException;
import com.taskflow.taskservice.exception.TaskNotFoundException;
import com.taskflow.taskservice.exception.UserServiceUnavailableException;
import com.taskflow.taskservice.model.Task;
import com.taskflow.taskservice.model.TaskStatus;
import com.taskflow.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createGuardaUnaTareaEnEstadoTodo() {
        CreateTaskRequest request = new CreateTaskRequest("Diseniar API", "desc", 10L, LocalDate.now().plusDays(3));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            setId(t, 1L);
            return t;
        });

        TaskResponse response = taskService.create(request);

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.assigneeId()).isNull();
    }

    @Test
    void assignValidaElUsuarioContraUserServiceAntesDeGuardar() {
        Task task = new Task("Diseniar API", "desc", 10L, null);
        setId(task, 1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userClient.getUserById(5L, "Bearer abc")).thenReturn(new UserDto(5L, "Ada", "ada@taskflow.dev"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.assign(1L, 5L, "Bearer abc");

        assertThat(response.assigneeId()).isEqualTo(5L);
        verify(userClient).getUserById(5L, "Bearer abc");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void assignFallaSiElUsuarioNoExisteYNoGuardaNada() {
        Task task = new Task("Diseniar API", "desc", 10L, null);
        setId(task, 1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userClient.getUserById(999L, "Bearer abc")).thenThrow(new AssigneeNotFoundException(999L));

        assertThatThrownBy(() -> taskService.assign(1L, 999L, "Bearer abc"))
                .isInstanceOf(AssigneeNotFoundException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignPropagaLaExcepcionSiUserServiceNoResponde() {
        Task task = new Task("Diseniar API", "desc", 10L, null);
        setId(task, 1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userClient.getUserById(anyLong(), any())).thenThrow(new UserServiceUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> taskService.assign(1L, 5L, "Bearer abc"))
                .isInstanceOf(UserServiceUnavailableException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignFallaSiLaTareaNoExiste() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.assign(404L, 5L, "Bearer abc"))
                .isInstanceOf(TaskNotFoundException.class);

        verifyNoInteractions(userClient);
    }

    @Test
    void updateStatusCambiaElEstadoDeLaTarea() {
        Task task = new Task("Diseniar API", "desc", 10L, null);
        setId(task, 1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.updateStatus(1L, TaskStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    private static void setId(Task task, Long id) {
        try {
            Field field = Task.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(task, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
