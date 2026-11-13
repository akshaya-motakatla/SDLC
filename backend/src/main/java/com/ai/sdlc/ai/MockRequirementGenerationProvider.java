package com.ai.sdlc.ai;

public class MockRequirementGenerationProvider implements RequirementGenerationProvider {
    @Override
    public String generate(RequirementContext context) {
        return """
                {
                  "requirements": [
                    {
                      "title": "Provide the core project workflow",
                      "description": "Users can complete the primary workflow for their project.",
                      "type": "FUNCTIONAL",
                      "priority": "HIGH",
                      "acceptanceCriteria": [
                        "A user can complete the primary workflow using the project interface.",
                        "The result is saved and available when the project is reopened."
                      ]
                    },
                    {
                      "title": "Protect project data",
                      "description": "Project data is available only to its authenticated owner.",
                      "type": "NON_FUNCTIONAL",
                      "priority": "HIGH",
                      "acceptanceCriteria": [
                        "Unauthenticated requests are denied.",
                        "A different account cannot read or change this project's data."
                      ]
                    }
                  ]
                }
                """;
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
