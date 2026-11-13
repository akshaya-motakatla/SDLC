package com.ai.sdlc.ai;

import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class DevelopmentTaskJsonParser {
    private final ObjectMapper objectMapper;

    public DevelopmentTaskJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GeneratedDevelopmentTask> parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalidResponse();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode tasks = root == null ? null : root.get("tasks");
            if (tasks == null || !tasks.isArray() || tasks.isEmpty() || tasks.size() > 50) {
                throw invalidResponse();
            }
            List<GeneratedDevelopmentTask> parsed = new ArrayList<>();
            for (int index = 0; index < tasks.size(); index++) {
                parsed.add(parseTask(tasks.get(index), index, tasks.size()));
            }
            validateDependencyGraph(parsed);
            return List.copyOf(parsed);
        } catch (JsonProcessingException exception) {
            throw invalidResponse(exception);
        }
    }

    private GeneratedDevelopmentTask parseTask(JsonNode node, int taskIndex, int taskCount) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        String title = requiredText(node, "title", 255);
        String description = requiredText(node, "description", 5000);
        TaskCategory category = enumValue(requiredText(node, "category", 50), TaskCategory.class);
        TaskPriority priority = enumValue(requiredText(node, "priority", 50), TaskPriority.class);
        int effort = requiredInteger(node, "estimatedEffortHours", 1, 400);
        String milestone = optionalText(node, "milestone", 100);
        List<String> criteria = textArray(node, "acceptanceCriteria", 1, 10, 500);
        List<String> requirementCodes = textArray(node, "relatedRequirementCodes", 1, 30, 50);
        List<UUID> architectureIds = uuidArray(node, "architectureReferenceIds", 30);
        List<Integer> dependencies = integerArray(node, "dependencyIndexes", 20);
        if (dependencies.stream().anyMatch(index -> index < 0 || index >= taskCount)
                || dependencies.stream().anyMatch(index -> index == taskIndex)) {
            throw invalidResponse();
        }
        if (new HashSet<>(dependencies).size() != dependencies.size()) {
            throw invalidResponse();
        }
        return new GeneratedDevelopmentTask(
                title,
                description,
                category,
                priority,
                effort,
                milestone,
                criteria,
                requirementCodes,
                architectureIds,
                dependencies
        );
    }

    private void validateDependencyGraph(List<GeneratedDevelopmentTask> tasks) {
        byte[] states = new byte[tasks.size()];
        for (int index = 0; index < tasks.size(); index++) {
            visit(index, tasks, states);
        }
    }

    private void visit(int index, List<GeneratedDevelopmentTask> tasks, byte[] states) {
        if (states[index] == 1) {
            throw invalidResponse();
        }
        if (states[index] == 2) {
            return;
        }
        states[index] = 1;
        for (Integer dependency : tasks.get(index).dependencyIndexes()) {
            if (dependency == index) {
                throw invalidResponse();
            }
            visit(dependency, tasks, states);
        }
        states[index] = 2;
    }

    private List<String> textArray(JsonNode node, String field, int min, int max, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.size() < min || value.size() > max) {
            throw invalidResponse();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxLength) {
                throw invalidResponse();
            }
            result.add(item.asText().trim());
        }
        if (new HashSet<>(result).size() != result.size()) {
            throw invalidResponse();
        }
        return List.copyOf(result);
    }

    private List<UUID> uuidArray(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.size() > max) {
            throw invalidResponse();
        }
        List<UUID> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) throw invalidResponse();
            try {
                result.add(UUID.fromString(item.asText()));
            } catch (IllegalArgumentException exception) {
                throw invalidResponse(exception);
            }
        }
        if (new HashSet<>(result).size() != result.size()) throw invalidResponse();
        return List.copyOf(result);
    }

    private List<Integer> integerArray(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.size() > max) {
            throw invalidResponse();
        }
        List<Integer> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.canConvertToInt()) throw invalidResponse();
            result.add(item.asInt());
        }
        return List.copyOf(result);
    }

    private int requiredInteger(JsonNode node, String field, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) {
            throw invalidResponse();
        }
        return value.asInt();
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw invalidResponse();
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().length() > maxLength) throw invalidResponse();
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private DevelopmentTaskGenerationException invalidResponse() {
        return new DevelopmentTaskGenerationException("The AI provider returned invalid development task data. Please retry.");
    }

    private DevelopmentTaskGenerationException invalidResponse(Throwable cause) {
        return new DevelopmentTaskGenerationException("The AI provider returned invalid development task data. Please retry.", cause);
    }
}
