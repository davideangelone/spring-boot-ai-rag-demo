package it.davideangelone.assistant.api;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank(message = "Il campo 'question' è obbligatorio.") String question) {
}
