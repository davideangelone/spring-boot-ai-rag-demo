package it.davideangelone.assistant.agent.planner;

import java.util.List;
import it.davideangelone.assistant.agent.model.QueryPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class QueryPlannerAgent {

    private static final String SYSTEM_PROMPT = """
            Sei un esperto analista di informazioni aziendali.
            Il tuo scopo è analizzare con estrema cura la domanda dell'utente e formulare un piano operativo.
            Identifica:
            1. intent: Scopo o intenzione principale dell'utente.
            2. informationNeeds: Elenco sintetico dei singoli dati necessari per rispondere con precisione.
            3. suggestedSources: Fonti suggerite (es. DATABASE per ordini/fatture/clienti, RAG_DOCUMENTS per regolamenti/procedure/politiche aziendali, MCP_TIME per date e orari).
            4. executionGuidance: Istruzioni operative chiare per l'agente esecutore che dovrà consultare i tool.
            Non rispondere alla domanda, limitati a compilare il piano.
            """;

    private final ChatClient chatClient;

    @Autowired
    public QueryPlannerAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    QueryPlannerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public QueryPlan plan(String question) {
        log.info("[PLANNER-AGENT] Analisi della domanda utente: {}", question);
        try {
            QueryPlan plan = chatClient.prompt()
                    .user(question)
                    .call()
                    .entity(QueryPlan.class);

            if (plan != null) {
                log.info("[PLANNER-AGENT] Piano generato: intento='{}', fonti={}", plan.intent(), plan.suggestedSources());
                return plan;
            }
        } catch (Exception e) {
            log.warn("[PLANNER-AGENT] Errore estrazione entity strutturata, fallback a piano generico: {}", e.getMessage());
        }

        return new QueryPlan(
                "Elaborazione richiesta utente",
                List.of("Dati specificati nella richiesta"),
                List.of("DATABASE", "RAG_DOCUMENTS"),
                "Valuta i tool disponibili e invoca quelli più pertinenti."
        );
    }
}
