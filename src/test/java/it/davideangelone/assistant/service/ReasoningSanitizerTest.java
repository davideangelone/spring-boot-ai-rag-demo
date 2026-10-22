package it.davideangelone.assistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReasoningSanitizerTest {

    @Test
    void preservesCleanReasoningAndAnswer() {
        String reasoning = "Ho verificato la tabella ordini per il cliente C001.";
        String answer = "Il cliente ha 3 ordini in stato completato.";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals(reasoning, result.reasoning());
        assertEquals(answer, result.answer());
    }

    @Test
    void extractsThinkTagsFromAnswerWhenReasoningIsEmpty() {
        String reasoning = null;
        String answer = "<think>Sto elaborando i dati del database...</think>Ecco il riassunto dell'ordine #1234.";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals("Sto elaborando i dati del database...", result.reasoning());
        assertEquals("Ecco il riassunto dell'ordine #1234.", result.answer());
    }

    @Test
    void appendsExtractedThinkToExistingReasoning() {
        String reasoning = "Primo passaggio completato.";
        String answer = "<think>Secondo passaggio di verifica.</think>Risposta finale.";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals("Primo passaggio completato.\n\nSecondo passaggio di verifica.", result.reasoning());
        assertEquals("Risposta finale.", result.answer());
    }

    @Test
    void extractsMultipleThinkTags() {
        String reasoning = null;
        String answer = "<think>Passo 1</think>Testo transitorio<think>Passo 2</think>Risposta definitiva.";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals("Passo 1\n\nPasso 2", result.reasoning());
        assertEquals("Testo transitorio Risposta definitiva.", result.answer());
    }

    @Test
    void handlesCaseInsensitiveAndThoughtTags() {
        String reasoning = null;
        String answer = "<THOUGHT>Analisi preliminare</THOUGHT>Risultato finale.";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals("Analisi preliminare", result.reasoning());
        assertEquals("Risultato finale.", result.answer());
    }

    @Test
    void handlesNullAndBlankInputsGracefully() {
        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(null, null);

        assertNull(result.reasoning());
        assertEquals("Il modello non ha restituito una risposta.", result.answer());

        ReasoningSanitizer.SanitizedOutput blankResult = ReasoningSanitizer.sanitize("  ", "   ");
        assertNull(blankResult.reasoning());
        assertEquals("Il modello non ha restituito una risposta.", blankResult.answer());
    }

    @Test
    void handlesUnclosedThinkTag() {
        String reasoning = null;
        String answer = "<think>Ragionamento non chiuso correttamente";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals("Ragionamento non chiuso correttamente", result.reasoning());
        assertEquals("Il modello non ha restituito una risposta.", result.answer());
    }

    @Test
    void preservesMarkdownTableAndNewlinesInAnswer() {
        String reasoning = "Query completata con successo.";
        String answer = """
                Ecco gli ordini trovati:
                
                | ID Ordine | Cliente | Importo |
                |---|---|---|
                | 1001 | Alfa S.r.l. | 12500.00 |
                | 1004 | Delta S.p.A. | 28750.00 |""";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertEquals(reasoning, result.reasoning());
        assertTrue(result.answer().contains("\n|---|---|---|\n"), "Newlines in markdown table must be preserved");
        assertTrue(result.answer().contains("| 1001 | Alfa S.r.l. | 12500.00 |"));
    }

    @Test
    void rescuesMarkdownTableFromReasoningWhenMissingInAnswer() {
        String reasoning = """
                Ho recuperato i dati dal database:
                | ID Ordine | Cliente | Stato |
                |---|---|---|
                | 1001 | Alfa S.r.l. | IN_PROGRESS |
                I record sono pronti per essere presentati.""";
        String answer = "Ecco il riepilogo degli ordini richiesti:";

        ReasoningSanitizer.SanitizedOutput result = ReasoningSanitizer.sanitize(reasoning, answer);

        assertTrue(result.answer().contains("| ID Ordine | Cliente | Stato |"));
        assertTrue(result.answer().contains("| 1001 | Alfa S.r.l. | IN_PROGRESS |"));
        assertFalse(result.reasoning().contains("| ID Ordine | Cliente | Stato |"), "Table should be moved out of reasoning");
    }
}
