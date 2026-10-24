package it.davideangelone.assistant.model;

public record SourceReference(
        String id,
        String source,
        Object chunk) {
}
