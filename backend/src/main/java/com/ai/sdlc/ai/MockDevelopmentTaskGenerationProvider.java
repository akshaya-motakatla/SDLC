package com.ai.sdlc.ai;

import com.ai.sdlc.domain.TaskCategory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockDevelopmentTaskGenerationProvider implements DevelopmentTaskGenerationProvider {
    private final ObjectMapper objectMapper;

    public MockDevelopmentTaskGenerationProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(DevelopmentPlanningContext context) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<PlanningRequirementContext> requirements = context.requirements().stream().limit(50).toList();
        for (int index = 0; index < requirements.size(); index++) {
            PlanningRequirementContext requirement = requirements.get(index);
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("title", "Implement and verify: " + requirement.title());
            task.put("description", "For project \"%s\" (%s), implement the capability described by %s: %s. Align the implementation with the approved architecture and the declared stack (%s)."
                    .formatted(
                            context.projectName(),
                            context.domain(),
                            requirement.code(),
                            requirement.description(),
                            context.preferredStack()
                    ));
            task.put("category", categoryFor(requirement));
            task.put("priority", requirement.priority());
            task.put("estimatedEffortHours", 8);
            task.put("milestone", "Milestone 1");
            task.put("acceptanceCriteria", requirement.acceptanceCriteria().isEmpty()
                    ? List.of("Implementation satisfies " + requirement.code() + " and is reviewed against the approved architecture.")
                    : requirement.acceptanceCriteria());
            task.put("relatedRequirementCodes", List.of(requirement.code()));
            task.put("architectureReferenceIds", context.architectures().stream()
                    .map(PlanningArchitectureContext::id).toList());
            task.put("dependencyIndexes", index == 0 ? List.of() : List.of(index - 1));
            tasks.add(task);
        }
        try {
            return objectMapper.writeValueAsString(Map.of("tasks", tasks));
        } catch (JsonProcessingException exception) {
            throw new DevelopmentTaskGenerationException("The demo task response could not be serialized.", exception);
        }
    }

    private TaskCategory categoryFor(PlanningRequirementContext requirement) {
        String context = (requirement.title() + " " + requirement.description()).toLowerCase();
        if (context.contains("security") || context.contains("authorization") || context.contains("privacy")) {
            return TaskCategory.SECURITY;
        }
        if (context.contains("database") || context.contains("persist")) {
            return TaskCategory.DATABASE;
        }
        if (context.contains("api") || context.contains("endpoint")) {
            return TaskCategory.API;
        }
        return "NON_FUNCTIONAL".equals(requirement.type()) ? TaskCategory.INTEGRATION : TaskCategory.BACKEND;
    }

    @Override
    public String providerName() {
        return "MOCK_DEMO";
    }

    @Override
    public boolean isDemo() {
        return true;
    }
}
