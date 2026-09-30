package com.taskflow.projectservice.service;

import com.taskflow.projectservice.dto.CreateProjectRequest;
import com.taskflow.projectservice.dto.ProjectResponse;
import com.taskflow.projectservice.dto.UpdateProjectRequest;
import com.taskflow.projectservice.exception.NotProjectOwnerException;
import com.taskflow.projectservice.exception.ProjectNotFoundException;
import com.taskflow.projectservice.model.Project;
import com.taskflow.projectservice.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse create(CreateProjectRequest request, Long ownerId) {
        Project project = new Project(request.name(), request.description(), ownerId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse getById(Long id) {
        return ProjectResponse.from(findOrThrow(id));
    }

    public List<ProjectResponse> listAll() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public List<ProjectResponse> listByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse update(Long id, UpdateProjectRequest request, Long requesterId) {
        Project project = findOrThrow(id);
        assertOwner(project, requesterId);

        project.setName(request.name());
        project.setDescription(request.description());
        return ProjectResponse.from(projectRepository.save(project));
    }

    public void delete(Long id, Long requesterId) {
        Project project = findOrThrow(id);
        assertOwner(project, requesterId);
        projectRepository.delete(project);
    }

    private Project findOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private void assertOwner(Project project, Long requesterId) {
        if (!project.getOwnerId().equals(requesterId)) {
            throw new NotProjectOwnerException();
        }
    }
}
