package com.ai.sdlc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "development_tasks")
public class DevelopmentTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String taskId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String priority;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 50)
    private String category;

    @Column(name = "estimated_effort_hours")
    private Integer estimatedEffortHours;

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "review_status", length = 50)
    private String reviewStatus;

    @Column(name = "generation_provider", length = 40)
    private String generationProvider;

    @Column(columnDefinition = "TEXT")
    private String dependencyIds;

    @Column(length = 100)
    private String milestone;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "development_task_requirements",
            joinColumns = @JoinColumn(name = "development_task_id"),
            inverseJoinColumns = @JoinColumn(name = "requirement_id")
    )
    private Set<Requirement> relatedRequirements = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "development_task_architecture_documents",
            joinColumns = @JoinColumn(name = "development_task_id"),
            inverseJoinColumns = @JoinColumn(name = "architecture_document_id")
    )
    private Set<ArchitectureDocument> architectureReferences = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "development_task_dependencies",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    private Set<DevelopmentTask> dependencies = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "dependencies", fetch = FetchType.LAZY)
    private List<DevelopmentTask> dependentTasks = new ArrayList<>();

    public DevelopmentTask() {
    }

    public DevelopmentTask(String taskId, String title, String description, String priority, String status, String dependencyIds, String milestone, Project project, Requirement requirement) {
        this.taskId = taskId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.dependencyIds = dependencyIds;
        this.milestone = milestone;
        this.project = project;
        this.requirement = requirement;
    }

    public UUID getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getEstimatedEffortHours() {
        return estimatedEffortHours;
    }

    public void setEstimatedEffortHours(Integer estimatedEffortHours) {
        this.estimatedEffortHours = estimatedEffortHours;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getGenerationProvider() {
        return generationProvider;
    }

    public void setGenerationProvider(String generationProvider) {
        this.generationProvider = generationProvider;
    }

    public String getDependencyIds() {
        return dependencyIds;
    }

    public void setDependencyIds(String dependencyIds) {
        this.dependencyIds = dependencyIds;
    }

    public String getMilestone() {
        return milestone;
    }

    public void setMilestone(String milestone) {
        this.milestone = milestone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Requirement getRequirement() {
        return requirement;
    }

    public void setRequirement(Requirement requirement) {
        this.requirement = requirement;
    }

    public Set<Requirement> getRelatedRequirements() {
        return relatedRequirements;
    }

    public void setRelatedRequirements(Set<Requirement> relatedRequirements) {
        this.relatedRequirements = relatedRequirements;
    }

    public Set<ArchitectureDocument> getArchitectureReferences() {
        return architectureReferences;
    }

    public void setArchitectureReferences(Set<ArchitectureDocument> architectureReferences) {
        this.architectureReferences = architectureReferences;
    }

    public Set<DevelopmentTask> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Set<DevelopmentTask> dependencies) {
        this.dependencies = dependencies;
    }

    public List<DevelopmentTask> getDependentTasks() {
        return dependentTasks;
    }
}
