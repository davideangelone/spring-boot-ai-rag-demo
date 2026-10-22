package it.davideangelone.assistant.model;

import java.util.UUID;

public record SourceReference(
        UUID id,
        String source,
        Object chunk) {
}
