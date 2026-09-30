package com.taskflow.taskservice.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /**
     * Id del proyecto al que pertenece. Vive en la base de datos de
     * project-service; aca solo se guarda la referencia.
     */
    @Column(nullable = false)
    private Long projectId;

    /**
     * Id del usuario asignado. Nullable: una tarea puede no tener asignatario.
     * Antes de setearlo, task-service valida contra user-service que el id
     * exista de verdad.
     */
    @Column
    private Long assigneeId;

    @Column
    private LocalDate dueDate;

    protected Task() {
        // JPA
    }

    public Task(String title, String description, Long projectId, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.projectId = projectId;
        this.dueDate = dueDate;
        this.status = TaskStatus.TODO;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
