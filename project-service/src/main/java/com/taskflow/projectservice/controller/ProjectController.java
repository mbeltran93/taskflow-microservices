package com.taskflow.projectservice.controller;

import com.taskflow.projectservice.dto.CreateProjectRequest;
import com.taskflow.projectservice.dto.ProjectResponse;
import com.taskflow.projectservice.dto.UpdateProjectRequest;
import com.taskflow.projectservice.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request,
                                   @RequestAttribute("userId") Long userId) {
        return projectService.create(request, userId);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Long id) {
        return projectService.getById(id);
    }

    @GetMapping
    public List<ProjectResponse> list(@RequestParam(required = false) Long ownerId) {
        return ownerId != null ? projectService.listByOwner(ownerId) : projectService.listAll();
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateProjectRequest request,
                                   @RequestAttribute("userId") Long userId) {
        return projectService.update(id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        projectService.delete(id, userId);
    }
}
