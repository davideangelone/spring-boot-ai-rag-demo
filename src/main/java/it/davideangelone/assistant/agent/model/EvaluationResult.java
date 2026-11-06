package it.davideangelone.assistant.agent.model;

public record EvaluationResult(
        boolean isRelevant,
        boolean isSufficient,
        String confidenceScore,
        String feedback
) {}
