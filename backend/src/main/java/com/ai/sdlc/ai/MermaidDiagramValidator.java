package com.ai.sdlc.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MermaidDiagramValidator {
    private static final Set<String> REQUIRED_DIAGRAMS = Set.of(
            "systemContext", "components", "entityRelationship", "workflow"
    );
    private static final Pattern NODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\[\"[^\"\\r\\n]+\"\\]$");
    private static final Pattern FLOW_EDGE = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9_]*)(?:\\[\"[^\"\\r\\n]+\"\\])?\\s+-->(?:\\|[^|\\r\\n]+\\|)?\\s+([A-Za-z][A-Za-z0-9_]*)(?:\\[\"[^\"\\r\\n]+\"\\])?$"
    );
    private static final Pattern PARTICIPANT = Pattern.compile("^(?:participant|actor)\\s+([A-Za-z][A-Za-z0-9_]*)(?:\\s+as\\s+[^\\r\\n]+)?$");
    private static final Pattern SEQUENCE_MESSAGE = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9_]*)\\s*(?:->>|-->>|->|-->)\\s*([A-Za-z][A-Za-z0-9_]*):\\s+[^\\r\\n]+$"
    );
    private static final Pattern ER_RELATIONSHIP = Pattern.compile(
            "^([A-Z][A-Z0-9_]*)\\s+(?:\\|\\|--o\\{|\\|o--\\|\\{|\\}o--\\|\\{|\\|\\|--\\|\\|)\\s+([A-Z][A-Z0-9_]*)\\s*:\\s*[A-Za-z][A-Za-z0-9_ ]*$"
    );

    public void validate(Map<String, String> diagrams) {
        if (diagrams == null || !diagrams.keySet().equals(REQUIRED_DIAGRAMS)) {
            throw new IllegalArgumentException("Architecture diagrams must include system context, components, entity relationships, and workflow.");
        }
        validateFlowchart("systemContext", diagrams.get("systemContext"));
        validateFlowchart("components", diagrams.get("components"));
        validateEntityRelationship(diagrams.get("entityRelationship"));
        validateSequence(diagrams.get("workflow"));
    }

    public void validateSingle(String name, String source) {
        if (source == null || source.isBlank() || source.length() > 12_000 || source.contains("%%{") || source.contains("<")) {
            throw new IllegalArgumentException("The %s Mermaid diagram is empty, oversized, or contains unsupported directives.".formatted(name));
        }
        if (name.equals("systemContext") || name.equals("components")) {
            validateFlowchart(name, source);
        } else if (name.equals("entityRelationship")) {
            validateEntityRelationship(source);
        } else if (name.equals("workflow")) {
            validateSequence(source);
        } else {
            throw new IllegalArgumentException("Unknown Mermaid diagram type.");
        }
    }

    private void validateFlowchart(String name, String source) {
        List<String> lines = sourceLines(name, source);
        if (!lines.get(0).matches("flowchart\\s+(LR|RL|TB|BT)")) {
            invalid(name);
        }
        Set<String> nodeIds = new HashSet<>();
        int edges = 0;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (NODE.matcher(line).matches()) {
                nodeIds.add(line.substring(0, line.indexOf('[')));
            } else {
                Matcher edge = FLOW_EDGE.matcher(line);
                if (!edge.matches()) {
                    invalid(name);
                }
                nodeIds.add(edge.group(1));
                nodeIds.add(edge.group(2));
                edges++;
            }
        }
        if (edges == 0 || nodeIds.size() < 2) {
            invalid(name);
        }
    }

    private void validateEntityRelationship(String source) {
        List<String> lines = sourceLines("entityRelationship", source);
        if (!lines.get(0).equals("erDiagram")) {
            invalid("entityRelationship");
        }
        int relationships = 0;
        for (int index = 1; index < lines.size(); index++) {
            if (!ER_RELATIONSHIP.matcher(lines.get(index)).matches()) {
                invalid("entityRelationship");
            }
            relationships++;
        }
        if (relationships < 2) {
            invalid("entityRelationship");
        }
    }

    private void validateSequence(String source) {
        List<String> lines = sourceLines("workflow", source);
        if (!lines.get(0).equals("sequenceDiagram")) {
            invalid("workflow");
        }
        List<String> participants = new ArrayList<>();
        int messages = 0;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher participant = PARTICIPANT.matcher(line);
            Matcher message = SEQUENCE_MESSAGE.matcher(line);
            if (participant.matches()) {
                participants.add(participant.group(1));
            } else if (message.matches()
                    && participants.contains(message.group(1))
                    && participants.contains(message.group(2))) {
                messages++;
            } else {
                invalid("workflow");
            }
        }
        if (participants.size() < 2 || messages < 2) {
            invalid("workflow");
        }
    }

    private List<String> sourceLines(String name, String source) {
        if (source == null || source.isBlank() || source.length() > 12_000 || source.contains("%%{") || source.contains("<")) {
            invalid(name);
        }
        List<String> lines = source.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (lines.size() < 2) {
            invalid(name);
        }
        return lines;
    }

    private void invalid(String name) {
        throw new IllegalArgumentException("The %s Mermaid diagram is not valid in the supported syntax.".formatted(name));
    }
}
