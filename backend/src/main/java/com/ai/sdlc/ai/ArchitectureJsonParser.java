package com.ai.sdlc.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArchitectureJsonParser {
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## Project overview",
            "## Architecture goals",
            "## Functional and non-functional requirement mapping",
            "## Recommended architecture style",
            "## High-level system architecture",
            "## Frontend architecture",
            "## Backend architecture",
            "## Database architecture",
            "## API design and responsibilities",
            "## Major components and their interactions",
            "## Data flow",
            "## Authentication and authorization design",
            "## Security considerations",
            "## Scalability and performance considerations",
            "## Technology recommendations and justifications",
            "## External integrations and dependencies",
            "## Risks, assumptions, and trade-offs"
    );

    private final ObjectMapper objectMapper;
    private final MermaidDiagramValidator diagramValidator;

    public ArchitectureJsonParser(ObjectMapper objectMapper, MermaidDiagramValidator diagramValidator) {
        this.objectMapper = objectMapper;
        this.diagramValidator = diagramValidator;
    }

    public GeneratedArchitecture parse(String json) {
        try {
            GeneratedArchitecture architecture = objectMapper.readValue(json, GeneratedArchitecture.class);
            validate(architecture);
            return architecture;
        } catch (JsonProcessingException exception) {
            throw new ArchitectureGenerationException("The AI provider returned invalid architecture data. Please retry.", exception);
        } catch (IllegalArgumentException exception) {
            throw new ArchitectureGenerationException(exception.getMessage(), exception);
        }
    }

    public void validateEdited(String title, String architectureStyle, String content, java.util.Map<String, String> diagrams) {
        validateFields(title, architectureStyle, content);
        diagramValidator.validate(diagrams);
    }

    private void validate(GeneratedArchitecture architecture) {
        if (architecture == null
                || !validFields(architecture.title(), architecture.architectureStyle(), architecture.content())) {
            throw new IllegalArgumentException("The AI provider returned incomplete or oversized architecture content. Please retry.");
        }
        validateSections(architecture.content());
        diagramValidator.validate(architecture.diagrams());
    }

    private void validateFields(String title, String architectureStyle, String content) {
        if (!validFields(title, architectureStyle, content)) {
            throw new IllegalArgumentException("Architecture title, style, or content is incomplete or oversized.");
        }
        validateSections(content);
    }

    private boolean validFields(String title, String architectureStyle, String content) {
        return !blank(title) && title.length() <= 255
                && !blank(architectureStyle) && architectureStyle.length() <= 120
                && !blank(content) && content.length() <= 50_000;
    }

    private void validateSections(String content) {
        String normalized = content.toLowerCase();
        if (REQUIRED_SECTIONS.stream().anyMatch(section -> !normalized.contains(section.toLowerCase()))) {
            throw new IllegalArgumentException("The architecture document must retain all required sections.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
