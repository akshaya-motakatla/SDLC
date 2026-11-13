package com.ai.sdlc.ai;

import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RequirementJsonParser {
    private final ObjectMapper objectMapper;

    public RequirementJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GeneratedRequirement> parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalidResponse();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode requirements = root == null ? null : root.get("requirements");
            if (requirements == null || !requirements.isArray() || requirements.isEmpty() || requirements.size() > 30) {
                throw invalidResponse();
            }

            List<GeneratedRequirement> parsed = new ArrayList<>();
            for (JsonNode requirement : requirements) {
                parsed.add(parseRequirement(requirement));
            }
            return List.copyOf(parsed);
        } catch (JsonProcessingException exception) {
            throw invalidResponse();
        }
    }

    private GeneratedRequirement parseRequirement(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        String title = requiredText(node, "title", 255);
        String description = requiredText(node, "description", Integer.MAX_VALUE);
        RequirementType type = enumValue(requiredText(node, "type", 50), RequirementType.class);
        RequirementPriority priority = enumValue(requiredText(node, "priority", 50), RequirementPriority.class);
        JsonNode criteriaNode = node.get("acceptanceCriteria");
        if (criteriaNode == null || !criteriaNode.isArray() || criteriaNode.isEmpty() || criteriaNode.size() > 10) {
            throw invalidResponse();
        }

        List<String> criteria = new ArrayList<>();
        for (JsonNode criterion : criteriaNode) {
            if (!criterion.isTextual() || criterion.asText().isBlank() || criterion.asText().length() > 500) {
                throw invalidResponse();
            }
            criteria.add(criterion.asText().trim());
        }
        return new GeneratedRequirement(title, description, type, priority, List.copyOf(criteria));
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw invalidResponse();
        }
        return value.asText().trim();
    }

    private <T extends Enum<T>> T enumValue(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private RequirementGenerationException invalidResponse() {
        return new RequirementGenerationException("The AI provider returned invalid requirement data. Please retry.");
    }
}
