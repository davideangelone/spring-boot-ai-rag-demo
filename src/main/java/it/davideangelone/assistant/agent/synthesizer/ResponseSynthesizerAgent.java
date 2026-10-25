package it.davideangelone.assistant.agent.synthesizer;

import java.util.List;

import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.model.RawResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.model.SourceReference;
import it.davideangelone.assistant.service.ReasoningSanitizer;
import it.davideangelone.assistant.utils.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResponseSynthesizerAgent {

    private static final String SYSTEM_PROMPT = """
            Sei l'Agente di Sintesi Finale dell'Assistente Aziendale (Response Synthesizer Agent).
            Il tuo obiettivo è redigere la risposta finale destinata all'utente, in lingua italiana, in modo chiaro, autorevole, verificabile e rigoroso.
            
            REGOLE FONDAMENTALI:
            1. TABELLE E RECORD DI DATABASE: se sono presenti record di database (ordini, clienti, importi), DEVI SEMPRE riportare TUTTI i record in tabelle Markdown complete con intestazioni e righe separate da a capo. Non omettere e non troncare mai i dati.
            2. DISTINZIONE DELLE FONTI: distingui con chiarezza i dati provenienti dal database transazionale dalle informazioni desunte dai documenti/politiche aziendali.
            3. VERIFICABILITÀ: non inventare mai fatti non presenti nei dati recuperati. Se le informazioni sono insufficienti, dichiaralo esplicitamente senza congetturare.
            4. DIVIETO ASSOLUTO: non includere frasi da processo mentale interno come 'Prima ho controllato...' o menzionare nomi di metodi Java, annotazioni @Tool o API interne.
            5. FORMALITÀ: mantieni un tono professionale e aziendale.
            """;

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final DocumentManager documentManager;

    @Autowired
    public ResponseSynthesizerAgent(ChatClient.Builder chatClientBuilder,
                                    ChatModel chatModel,
                                    DocumentManager documentManager) {
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
        this.documentManager = documentManager;
    }

    ResponseSynthesizerAgent(ChatClient chatClient, ChatModel chatModel, DocumentManager documentManager) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.documentManager = documentManager;
    }

    public AskResponse synthesize(String question,
                                  QueryPlan plan,
                                  ToolExecutionResult executionResult,
                                  EvaluationResult evaluation,
                                  RetrievedDocumentsContext context,
                                  String conversationId) {
        log.info("[SYNTHESIZER-AGENT] Composizione risposta finale per: '{}'", question);

        String userPrompt = """
                DOMANDA DELL'UTENTE:
                %s
                
                RISULTATI RECUPERATI DAGLI STRUMENTI:
                %s
                
                NOTE DI QUALITÀ / VALUTAZIONE:
                - Livello di confidenza: %s
                - Stato completezza: %s
                
                Componi la risposta finale rispettando scrupolosamente le regole di formattazione (tabelle markdown per ordini, separazione fonti, divieto di allucinazione).
                """.formatted(
                question,
                executionResult.summary(),
                evaluation.confidenceScore(),
                evaluation.isSufficient() ? "Completa" : "Parziale (" + evaluation.feedback() + ")"
        );

        Prompt prompt = new Prompt(
                userPrompt,
                OllamaChatOptions.builder()
                        .model(chatModel.getOptions().getModel())
                        .enableThinking()
                        .build()
        );

        ChatResponse response = chatClient.prompt(prompt)
                .system(SYSTEM_PROMPT)
                .call()
                .chatResponse();

        RawResponse rawResponse = documentManager.getRawResponse(response);
        ReasoningSanitizer.SanitizedOutput sanitized = ReasoningSanitizer.sanitize(rawResponse.rawThinking(), rawResponse.rawResponse());
        List<SourceReference> sources = documentManager.getDocumentsSourceReferences(context.getDocuments());

        return new AskResponse(
                sanitized.answer(),
                sanitized.reasoning(),
                sources,
                chatModel.getOptions().getModel(),
                conversationId
        );
    }
}
