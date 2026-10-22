package it.davideangelone.assistant.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record MemoryLogMessageMetadata(int messageNumber,
                                       Instant timestamp,
                                       String role,
                                       @JsonInclude(JsonInclude.Include.NON_NULL)
                                       String content,
                                       @JsonInclude(JsonInclude.Include.NON_NULL)
                                       String answer,
                                       @JsonInclude(JsonInclude.Include.NON_NULL)
                                       String thinking,
                                       @JsonInclude(JsonInclude.Include.NON_NULL)
                                       List<SourceReference> sources,
                                       @JsonInclude(JsonInclude.Include.NON_NULL)
                                       String modelName) {
}
