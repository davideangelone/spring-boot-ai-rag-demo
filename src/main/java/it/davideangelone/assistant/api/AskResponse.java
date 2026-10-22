package it.davideangelone.assistant.api;

import java.util.List;

import it.davideangelone.assistant.model.SourceReference;

public record AskResponse(String answer, String reasoning, List<SourceReference> sources, String model, String conversationId) {
}
