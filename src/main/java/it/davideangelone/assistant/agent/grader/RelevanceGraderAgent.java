package it.davideangelone.assistant.agent.grader;

import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RelevanceGraderAgent {

    private static final String SYSTEM_PROMPT = """
            Sei un controllore di qualità ed accuratezza dei dati aziendali (Grader/Critic).
            Il tuo compito è confrontare la domanda dell'utente e il piano informativo con i risultati ottenuti dall'esecuzione dei tool.
            Ricevi due fonti: l'OUTPUT GREZZO DEI TOOL (dati reali restituiti dagli strumenti, fonte di verità) e il RIEPILOGO dell'agente esecutore (testo generato da un LLM, che può essere incompleto, troncato o impreciso).
            Basa la valutazione sull'output grezzo. Se il riepilogo omette, tronca o altera record o informazioni presenti nell'output grezzo, segnalalo nel feedback.
            Se l'output grezzo riporta il marker 'OUTPUT TROCATO', considera che i dati possono essere incompleti.
            Se nessun tool è stato invocato, valuta solo il riepilogo.
            Valuta con rigore:
            1. isRelevant (boolean): Le informazioni raccolte sono pertinenti alla domanda posta?
            2. isSufficient (boolean): I dati sono sufficienti e completi per redigere una risposta finale affidabile e precisa senza dover ipotizzare nulla?
            3. confidenceScore: Livello di confidenza complessivo (HIGH, MEDIUM, LOW).
            4. feedback: Se isSufficient è false o mancano elementi, spiega sinteticamente cosa manca o quale tool/ricerca specifica dovrebbe essere tentata. Se sufficiente, indica brevemente 'COMPLETO'.
            """;

    private final ChatClient chatClient;

    @Autowired
    public RelevanceGraderAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    RelevanceGraderAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public EvaluationResult evaluate(String question, QueryPlan plan, ToolExecutionResult executionResult, int messageNumber) {
        log.info("[GRADER-AGENT] - Msg {} - Valutazione pertinenza e completezza per la domanda: {}", messageNumber, question);
        String promptText = """
                DOMANDA UTENTE: %s
                
                PIANO INFORMATIVO:
                - Intento: %s
                - Dati necessari: %s
                
                OUTPUT GREZZO DEI TOOL (fonte di verità):
                %s

                RIEPILOGO DELL'AGENTE ESECUTORE (può essere impreciso):
                %s
                """.formatted(
                question,
                plan.intent(),
                plan.informationNeeds(),
                executionResult.rawOutput().isBlank() ? "(nessun tool invocato)" : executionResult.rawOutput(),
                executionResult.summary()
        );

        try {
            EvaluationResult result = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .entity(EvaluationResult.class);

            if (result != null) {
                log.info("[GRADER-AGENT] - Msg {} - Risultato valutazione: relevant={}, sufficient={}, score={}, feedback={}",
                        messageNumber, result.isRelevant(), result.isSufficient(), result.confidenceScore(), result.feedback());
                return result;
            }
        } catch (Exception e) {
            log.warn("[GRADER-AGENT] - Msg {} - Errore durante grading strutturato: {}", messageNumber, e.getMessage());
        }

        boolean hasData = !executionResult.rawOutput().isBlank() || !executionResult.summary().isBlank();
        return new EvaluationResult(hasData, hasData, hasData ? "MEDIUM" : "LOW", hasData ? "OK" : "Nessun dato recuperato");
    }
}
