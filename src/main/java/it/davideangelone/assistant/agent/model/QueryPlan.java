package it.davideangelone.assistant.agent.model;

import java.util.List;

public record QueryPlan(
        String intent,
        List<String> informationNeeds,
        List<String> suggestedSources,
        String executionGuidance
) {
    public QueryPlan {
        if (informationNeeds == null) {
            informationNeeds = List.of();
        }
        if (suggestedSources == null) {
            suggestedSources = List.of();
        }
    }
}
