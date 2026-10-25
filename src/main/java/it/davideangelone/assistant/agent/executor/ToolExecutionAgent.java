package it.davideangelone.assistant.agent.executor;

import java.util.Map;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.constants.Constants;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ToolExecutionAgent {

    private static final String SYSTEM_PROMPT = """
            Sei l'Agente Esecutore di Strumenti Aziendali (Tool Execution Agent).
            Disponi di strumenti locali e MCP annotati nativamente con @Tool:
            - Ricerca documentale (searchDocuments): per regolamenti, politiche, procedure aziendali.
            - Strumenti ordini (findOrdersByStatus, findOrderById, sumOrderAmountsByStatus): per dati del database relazionale.
            - Strumenti data e ora (locali o MCP): per orario e data correnti.
            
            IL TUO COMPITO:
            Analizza la richiesta dell'utente e il piano di esecuzione fornito.
            Decidi in autonomia quali tool invocare per raccogliere tutti i dati necessari con la massima precisione.
            Esegui i tool opportuni. Se ti viene fornito un feedback di retry dal supervisore, focalizzati sulle informazioni mancanti indicate.
            Riporta in modo fedele e completo tutti i dati, record ed esiti ottenuti dall'invocazione dei tool.
            """;

    private final ChatClient toolChatClient;

    public ToolExecutionAgent(@Qualifier("toolChatClient") ChatClient toolChatClient) {
        this.toolChatClient = toolChatClient;
    }

    public ToolExecutionResult execute(String question, QueryPlan plan, String retryFeedback, RetrievedDocumentsContext context, int messageNumber) {
        log.info("[TOOL-AGENT] Valutazione e decisione tool per richiesta: '{}'", question);

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("DOMANDA UTENTE: ").append(question).append("\n\n");
        userMessage.append("PIANO DI ESECUZIONE:\n");
        userMessage.append("- Intento: ").append(plan.intent()).append("\n");
        userMessage.append("- Dati necessari: ").append(plan.informationNeeds()).append("\n");
        userMessage.append("- Istruzioni: ").append(plan.executionGuidance()).append("\n");

        if (retryFeedback != null && !retryFeedback.isBlank()) {
            userMessage.append("\nATTENZIONE - FEEDBACK DAL GRADER SUL TENTATIVO PRECEDENTE:\n");
            userMessage.append(retryFeedback).append("\n");
            userMessage.append("Esegui le ricerche o i tool necessari per colmare le lacune indicate.\n");
        }

        Prompt prompt = new Prompt(userMessage.toString());

        ChatResponse response = toolChatClient.prompt(prompt)
                .system(SYSTEM_PROMPT)
                .toolContext(Map.of(
                        Constants.SEARCH_DOCUMENT_TOOL_CONTEXT, context,
                        Constants.MESSAGE_COUNTER_TOOL_CONTEXT, messageNumber
                ))
                .call()
                .chatResponse();

        String outputText = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            outputText = response.getResult().getOutput().getText();
        }

        log.info("[TOOL-AGENT] Esecuzione completata. Documenti nel contesto: {}", context.getDocuments().size());
        context.getDocuments().forEach(doc -> log.info("[TOOL-AGENT] Documento: {}", doc.));

        return new ToolExecutionResult(outputText, outputText, context.getDocuments());
    }

    public ToolExecutionResult execute(String question, QueryPlan plan, String retryFeedback, RetrievedDocumentsContext context) {
        return execute(question, plan, retryFeedback, context, 0);
    }
}
