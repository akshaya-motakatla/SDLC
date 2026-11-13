package com.ai.sdlc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "architecture_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_architecture_project_version", columnNames = {"project_id", "version"})
)
public class ArchitectureDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String components;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String databaseDesign;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String apiEndpoints;

    @Column(columnDefinition = "TEXT")
    private String diagrams;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "architecture_style", length = 120)
    private String architectureStyle;

    @Column(name = "generation_provider", length = 40)
    private String generationProvider;

    @Column
    private Integer version;

    @Column(nullable = false, length = 50)
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "architecture_document_requirements",
            joinColumns = @JoinColumn(name = "architecture_document_id")
    )
    @Column(name = "requirement_id", nullable = false)
    private Set<UUID> sourceRequirementIds = new LinkedHashSet<>();

    public ArchitectureDocument() {
    }

    public ArchitectureDocument(String title, String summary, String components, String databaseDesign, String apiEndpoints, String diagrams, String status, Project project) {
        this.title = title;
        this.summary = summary;
        this.components = components;
        this.databaseDesign = databaseDesign;
        this.apiEndpoints = apiEndpoints;
        this.diagrams = diagrams;
        this.status = status;
        this.project = project;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getComponents() {
        return components;
    }

    public void setComponents(String components) {
        this.components = components;
    }

    public String getDatabaseDesign() {
        return databaseDesign;
    }

    public void setDatabaseDesign(String databaseDesign) {
        this.databaseDesign = databaseDesign;
    }

    public String getApiEndpoints() {
        return apiEndpoints;
    }

    public void setApiEndpoints(String apiEndpoints) {
        this.apiEndpoints = apiEndpoints;
    }

    public String getDiagrams() {
        return diagrams;
    }

    public void setDiagrams(String diagrams) {
        this.diagrams = diagrams;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getArchitectureStyle() {
        return architectureStyle;
    }

    public void setArchitectureStyle(String architectureStyle) {
        this.architectureStyle = architectureStyle;
    }

    public String getGenerationProvider() {
        return generationProvider;
    }

    public void setGenerationProvider(String generationProvider) {
        this.generationProvider = generationProvider;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Set<UUID> getSourceRequirementIds() {
        return sourceRequirementIds;
    }

    public void setSourceRequirementIds(Set<UUID> sourceRequirementIds) {
        this.sourceRequirementIds = sourceRequirementIds;
    }
}
