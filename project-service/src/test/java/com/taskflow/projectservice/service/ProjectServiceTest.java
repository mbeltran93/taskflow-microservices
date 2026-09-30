package com.taskflow.projectservice.service;

import com.taskflow.projectservice.dto.CreateProjectRequest;
import com.taskflow.projectservice.dto.ProjectResponse;
import com.taskflow.projectservice.dto.UpdateProjectRequest;
import com.taskflow.projectservice.exception.NotProjectOwnerException;
import com.taskflow.projectservice.exception.ProjectNotFoundException;
import com.taskflow.projectservice.model.Project;
import com.taskflow.projectservice.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createGuardaElProyectoConElOwnerIdDelToken() {
        CreateProjectRequest request = new CreateProjectRequest("TaskFlow", "Proyecto de portafolio");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            setId(p, 1L);
            return p;
        });

        ProjectResponse response = projectService.create(request, 42L);

        assertThat(response.ownerId()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("TaskFlow");
    }

    @Test
    void getByIdFallaSiNoExiste() {
        when(projectRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(5L))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void updateFallaSiElRequesterNoEsElOwner() {
        Project project = new Project("TaskFlow", "desc", 42L);
        setId(project, 1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.update(1L, new UpdateProjectRequest("Nuevo", "d"), 99L))
                .isInstanceOf(NotProjectOwnerException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updatePermiteAlOwnerModificarElProyecto() {
        Project project = new Project("TaskFlow", "desc", 42L);
        setId(project, 1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.update(1L, new UpdateProjectRequest("Nuevo nombre", "nueva desc"), 42L);

        assertThat(response.name()).isEqualTo("Nuevo nombre");
        assertThat(response.description()).isEqualTo("nueva desc");
    }

    @Test
    void deleteFallaSiElRequesterNoEsElOwner() {
        Project project = new Project("TaskFlow", "desc", 42L);
        setId(project, 1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.delete(1L, 1L))
                .isInstanceOf(NotProjectOwnerException.class);

        verify(projectRepository, never()).delete(any());
    }

    private static void setId(Project project, Long id) {
        try {
            Field field = Project.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(project, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
