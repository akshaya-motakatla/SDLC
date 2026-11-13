package com.ai.sdlc;

import com.ai.sdlc.ai.DevelopmentTaskGenerationException;
import com.ai.sdlc.ai.DevelopmentTaskJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevelopmentTaskJsonParserTest {
    private final DevelopmentTaskJsonParser parser = new DevelopmentTaskJsonParser(new ObjectMapper());

    @Test
    void parsesStructuredTasksAndDependencies() {
        var tasks = parser.parse("""
                {"tasks":[
                  {
                    "title":"Implement owner-scoped API",
                    "description":"Add the API route for the approved capability.",
                    "category":"API",
                    "priority":"HIGH",
                    "estimatedEffortHours":8,
                    "milestone":"Milestone 1",
                    "acceptanceCriteria":["The owner can retrieve the saved result."],
                    "relatedRequirementCodes":["REQ-0001"],
                    "architectureReferenceIds":[],
                    "dependencyIndexes":[]
                  },
                  {
                    "title":"Verify API workflow",
                    "description":"Exercise the owner-scoped API workflow.",
                    "category":"INTEGRATION",
                    "priority":"MEDIUM",
                    "estimatedEffortHours":4,
                    "milestone":"Milestone 1",
                    "acceptanceCriteria":["The saved result can be reopened."],
                    "relatedRequirementCodes":["REQ-0001"],
                    "architectureReferenceIds":[],
                    "dependencyIndexes":[0]
                  }
                ]}
                """);

        assertEquals(2, tasks.size());
        assertEquals(0, tasks.get(1).dependencyIndexes().get(0));
    }

    @Test
    void rejectsCircularDependenciesAndInvalidPriority() {
        String circular = """
                {"tasks":[
                  {"title":"First","description":"First task","category":"API","priority":"HIGH","estimatedEffortHours":1,"milestone":null,"acceptanceCriteria":["Done"],"relatedRequirementCodes":["REQ-0001"],"architectureReferenceIds":[],"dependencyIndexes":[1]},
                  {"title":"Second","description":"Second task","category":"API","priority":"HIGH","estimatedEffortHours":1,"milestone":null,"acceptanceCriteria":["Done"],"relatedRequirementCodes":["REQ-0001"],"architectureReferenceIds":[],"dependencyIndexes":[0]}
                ]}
                """;
        assertThrows(DevelopmentTaskGenerationException.class, () -> parser.parse(circular));

        String invalidPriority = """
                {"tasks":[{"title":"Task","description":"Do the work","category":"API","priority":"URGENT","estimatedEffortHours":1,"milestone":null,"acceptanceCriteria":["Done"],"relatedRequirementCodes":["REQ-0001"],"architectureReferenceIds":[],"dependencyIndexes":[]}]}
                """;
        assertThrows(DevelopmentTaskGenerationException.class, () -> parser.parse(invalidPriority));
        assertThrows(DevelopmentTaskGenerationException.class, () -> parser.parse("{not valid json"));
    }
}
