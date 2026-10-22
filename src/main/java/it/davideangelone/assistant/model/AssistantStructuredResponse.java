package it.davideangelone.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AssistantStructuredResponse(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Processo logico interno e pianificazione dell'assistente. NON inserire qui la risposta finale per l'utente né le tabelle con i dati estratti.")
        String reasoning,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Risposta completa ed esauriente per l'utente. Include sempre tutti i dati e i record estratti (es. ordini) formattati in tabelle Markdown con intestazioni e righe separate da newline. NON omettere o troncare i dati e non includere riflessioni interne.")
        String answer
) {
}
