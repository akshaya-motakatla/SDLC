package com.ai.sdlc;

import com.ai.sdlc.ai.ArchitectureContext;
import com.ai.sdlc.ai.ArchitectureJsonParser;
import com.ai.sdlc.ai.ArchitectureRequirementContext;
import com.ai.sdlc.ai.ArchitectureGenerationException;
import com.ai.sdlc.ai.MermaidDiagramValidator;
import com.ai.sdlc.ai.MockArchitectureGenerationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureJsonParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArchitectureJsonParser parser = new ArchitectureJsonParser(objectMapper, new MermaidDiagramValidator());

    @Test
    void parsesContextualDemoArchitectureWithRequiredSectionsAndFourDiagrams() {
        ArchitectureContext context = new ArchitectureContext(
                UUID.randomUUID(),
                "Inventory Control",
                "Tracks warehouse stock and reorder thresholds.",
                "Reduce stockouts for warehouse operators.",
                "Retail operations",
                "React, Spring Boot, PostgreSQL",
                List.of(new ArchitectureRequirementContext(
                        UUID.randomUUID(),
                        "REQ-0042",
                        "Track stock levels",
                        "Operators update inventory counts by warehouse location.",
                        "FUNCTIONAL",
                        "HIGH",
                        List.of("A saved count is visible after reopening the project.")
                ))
        );

        var result = parser.parse(new MockArchitectureGenerationProvider(objectMapper).generate(context));

        assertEquals("Layered modular monolith", result.architectureStyle());
        assertTrue(result.content().contains("Inventory Control"));
        assertTrue(result.content().contains("REQ-0042"));
        assertEquals(4, result.diagrams().size());
    }

    @Test
    void rejectsMalformedJsonAndIncompleteArchitectureSections() {
        assertThrows(ArchitectureGenerationException.class, () -> parser.parse("not json"));

        String incomplete = """
                {"title":"Architecture","architectureStyle":"Layered","content":"## Project overview\\nOnly an overview.","diagrams":{}}
                """;
        assertThrows(ArchitectureGenerationException.class, () -> parser.parse(incomplete));
    }

    @Test
    void rejectsInvalidMermaidBeforeItCanBePersisted() {
        ArchitectureContext context = new ArchitectureContext(
                UUID.randomUUID(), "Project", "Description", "Objectives", "Domain", "Stack", List.of()
        );
        String response = new MockArchitectureGenerationProvider(objectMapper).generate(context)
                .replace("flowchart LR", "flowchart INVALID");

        assertThrows(ArchitectureGenerationException.class, () -> parser.parse(response));
    }
}
