package com.ai.sdlc;

import com.ai.sdlc.ai.MermaidDiagramValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MermaidDiagramValidatorTest {
    private final MermaidDiagramValidator validator = new MermaidDiagramValidator();

    @Test
    void acceptsSupportedDiagramGrammarAndRejectsBrokenSyntaxAndDirectives() {
        assertDoesNotThrow(() -> validator.validate(validDiagrams()));

        Map<String, String> invalid = new java.util.LinkedHashMap<>(validDiagrams());
        invalid.put("components", "flowchart LR\n  this is not a valid statement\n  A --> B");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));

        invalid.put("components", "flowchart LR\n  A[\"UI\"] --> B[\"API\"]\n  %%{init: {}}%%");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));
    }

    private Map<String, String> validDiagrams() {
        return Map.of(
                "systemContext", "flowchart LR\n  A[\"Owner\"] -->|Uses| B[\"Application\"]",
                "components", "flowchart LR\n  A[\"Workspace\"] --> B[\"API\"]",
                "entityRelationship", "erDiagram\n  PROJECT ||--o{ REQUIREMENT : contains\n  PROJECT ||--o{ ARCHITECTURE : has",
                "workflow", "sequenceDiagram\n  participant owner as Owner\n  participant api as API\n  owner->>api: Generate\n  api-->>owner: Draft"
        );
    }
}
