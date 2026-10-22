package it.davideangelone.assistant.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ChatMessageDto(String name,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String text,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String answer,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String reasoning,
                             @JsonInclude(
                                     value = JsonInclude.Include.NON_NULL,
                                     content = JsonInclude.Include.NON_NULL
                             )
                             Map<String, Object> metadata) {
}
