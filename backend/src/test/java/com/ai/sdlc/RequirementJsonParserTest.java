package com.ai.sdlc;

import com.ai.sdlc.ai.RequirementGenerationException;
import com.ai.sdlc.ai.RequirementJsonParser;
import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementJsonParserTest {
    private final RequirementJsonParser parser = new RequirementJsonParser(new ObjectMapper());

    @Test
    void parsesStructuredRequirementOutput() {
        var parsed = parser.parse("""
                {
                  "requirements": [{
                    "title": "Account access",
                    "description": "Users can sign in.",
                    "type": "functional",
                    "priority": "high",
                    "acceptanceCriteria": ["Valid credentials open the workspace."]
                  }]
                }
                """);

        assertEquals(1, parsed.size());
        assertEquals(RequirementType.FUNCTIONAL, parsed.get(0).type());
        assertEquals(RequirementPriority.HIGH, parsed.get(0).priority());
        assertEquals("Valid credentials open the workspace.", parsed.get(0).acceptanceCriteria().get(0));
    }

    @Test
    void rejectsMalformedOrIncompleteProviderOutput() {
        assertThrows(RequirementGenerationException.class, () -> parser.parse("not json"));
        assertThrows(RequirementGenerationException.class, () -> parser.parse(" "));
        assertThrows(RequirementGenerationException.class, () -> parser.parse("""
                {"requirements":[{"title":"Missing criteria","description":"No criteria","type":"OTHER","priority":"HIGH","acceptanceCriteria":[]}]}
                """));
    }
}
