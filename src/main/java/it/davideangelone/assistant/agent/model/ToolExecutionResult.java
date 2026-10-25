package it.davideangelone.assistant.agent.model;

import java.util.List;
import org.springframework.ai.document.Document;

public record ToolExecutionResult(
        String summary,
        String rawOutput,
        List<Document> retrievedDocuments
) {
    public ToolExecutionResult {
        if (retrievedDocuments == null) {
            retrievedDocuments = List.of();
        }
    }
}
