# Orchestrated Agentic RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the Spring AI Enterprise Assistant into an orchestrated multi-agent RAG system where specialized agents (QueryPlanner, ToolExecution with native `@Tool`/MCP, RelevanceGrader with retry loop, ResponseSynthesizer) are orchestrated by a central engine to answer user queries with high precision and transparency.

**Architecture:** A 4-stage pipeline orchestrated by `AgenticRagOrchestrator`:
1. `QueryPlannerAgent`: Analyzes user intent, required facts, and candidate sources without calling tools.
2. `ToolExecutionAgent`: Decides and executes native Spring `@Tool` (OrderTools, DocumentTools, OtherTools) and MCP tools via Spring AI tool-calling.
3. `RelevanceGraderAgent`: Critically evaluates relevance, accuracy, and sufficiency of retrieved context, initiating a feedback retry (up to 2 iterations) if gaps exist.
4. `ResponseSynthesizerAgent`: Synthesizes the final verified response strictly grounded in retrieved evidence, formatting markdown tables for DB records and referencing document chunks.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Starter 3.x/4.x), Spring AI 2.0.1, Ollama (with thinking options), pgvector, Jackson, JUnit 5, Mockito.

## Global Constraints
- Native Spring AI `@Tool` annotation must be used for tool calls.
- Preserves `/api/ask` returning `AskResponse(answer, reasoning, sources, model, conversationId)`.
- All record collections from DB must be rendered as Markdown tables in the final answer without truncation.
- Code style: follows existing project conventions in `it.davideangelone.assistant`.

---

### Task 1: Core Models for Multi-Agent Communication

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/model/QueryPlan.java`
- Create: `src/main/java/it/davideangelone/assistant/agent/model/EvaluationResult.java`
- Create: `src/main/java/it/davideangelone/assistant/agent/model/ToolExecutionResult.java`
- Test: `src/test/java/it/davideangelone/assistant/agent/model/AgentModelSerializationTest.java`

**Interfaces:**
- Produces:
  - `QueryPlan(String intent, List<String> informationNeeds, List<String> suggestedSources, String executionGuidance)`
  - `EvaluationResult(boolean isRelevant, boolean isSufficient, String confidenceScore, String feedback)`
  - `ToolExecutionResult(String summary, String rawOutput, List<Document> retrievedDocuments)`

- [ ] **Step 1: Write the failing unit test for model records**

```java
package it.davideangelone.assistant.agent.model;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testQueryPlanSerialization() throws Exception {
        QueryPlan plan = new QueryPlan("Verifica ordini", List.of("ordini recenti"), List.of("DATABASE"), "Cerca ordini con stato NEW");
        String json = objectMapper.writeValueAsString(plan);
        QueryPlan deserialized = objectMapper.readValue(json, QueryPlan.class);
        assertEquals("Verifica ordini", deserialized.intent());
        assertEquals("DATABASE", deserialized.suggestedSources().getFirst());
    }

    @Test
    void testEvaluationResultSerialization() throws Exception {
        EvaluationResult result = new EvaluationResult(true, true, "HIGH", "Tutti i dati sono completi");
        String json = objectMapper.writeValueAsString(result);
        EvaluationResult deserialized = objectMapper.readValue(json, EvaluationResult.class);
        assertTrue(deserialized.isRelevant());
        assertTrue(deserialized.isSufficient());
        assertEquals("HIGH", deserialized.confidenceScore());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentModelSerializationTest`
Expected: Compilation failure due to missing classes.

- [ ] **Step 3: Implement QueryPlan, EvaluationResult, and ToolExecutionResult**

`src/main/java/it/davideangelone/assistant/agent/model/QueryPlan.java`:
```java
package it.davideangelone.assistant.agent.model;

import java.util.List;

public record QueryPlan(
        String intent,
        List<String> informationNeeds,
        List<String> suggestedSources,
        String executionGuidance
) {
    public QueryPlan {
        if (informationNeeds == null) informationNeeds = List.of();
        if (suggestedSources == null) suggestedSources = List.of();
    }
}
```

`src/main/java/it/davideangelone/assistant/agent/model/EvaluationResult.java`:
```java
package it.davideangelone.assistant.agent.model;

public record EvaluationResult(
        boolean isRelevant,
        boolean isSufficient,
        String confidenceScore,
        String feedback
) {}
```

`src/main/java/it/davideangelone/assistant/agent/model/ToolExecutionResult.java`:
```java
package it.davideangelone.assistant.agent.model;

import java.util.List;
import org.springframework.ai.document.Document;

public record ToolExecutionResult(
        String summary,
        String rawOutput,
        List<Document> retrievedDocuments
) {
    public ToolExecutionResult {
        if (retrievedDocuments == null) retrievedDocuments = List.of();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentModelSerializationTest`
Expected: BUILD SUCCESS (100% tests pass).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/model/ src/test/java/it/davideangelone/assistant/agent/model/
git commit -m "feat(agent): add core agentic models QueryPlan, EvaluationResult, ToolExecutionResult"
```

---

### Task 2: QueryPlannerAgent Implementation

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/planner/QueryPlannerAgent.java`
- Test: `src/test/java/it/davideangelone/assistant/agent/planner/QueryPlannerAgentTest.java`

**Interfaces:**
- Consumes: `ChatClient.Builder`, `ChatModel`
- Produces: `QueryPlan plan(String userQuestion)`

- [ ] **Step 1: Write unit test for QueryPlannerAgent**

```java
package it.davideangelone.assistant.agent.planner;

import it.davideangelone.assistant.agent.model.QueryPlan;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryPlannerAgentTest {

    @Test
    void testPlanReturnsValidPlan() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        QueryPlan expectedPlan = new QueryPlan("Cerca ordine", java.util.List.of("id ordine"), java.util.List.of("DATABASE"), "Chiama findOrderById");

        when(chatClient.prompt().user(any(String.class)).call().entity(QueryPlan.class))
                .thenReturn(expectedPlan);

        QueryPlannerAgent agent = new QueryPlannerAgent(chatClient);
        QueryPlan plan = agent.plan("Dov'è il mio ordine 101?");

        assertNotNull(plan);
        assertEquals("Cerca ordine", plan.intent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=QueryPlannerAgentTest`
Expected: Compilation failure (missing `QueryPlannerAgent`).

- [ ] **Step 3: Implement QueryPlannerAgent**

`src/main/java/it/davideangelone/assistant/agent/planner/QueryPlannerAgent.java`:
```java
package it.davideangelone.assistant.agent.planner;

import it.davideangelone.assistant.agent.model.QueryPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    public QueryPlannerAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    // Costruttore package-private per test
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
                java.util.List.of("Dati specificati nella richiesta"),
                java.util.List.of("DATABASE", "RAG_DOCUMENTS"),
                "Valuta i tool disponibili e invoca quelli più pertinenti."
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=QueryPlannerAgentTest`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/planner/QueryPlannerAgent.java src/test/java/it/davideangelone/assistant/agent/planner/QueryPlannerAgentTest.java
git commit -m "feat(agent): implement QueryPlannerAgent with structured output"
```

---

### Task 3: RelevanceGraderAgent Implementation

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/grader/RelevanceGraderAgent.java`
- Test: `src/test/java/it/davideangelone/assistant/agent/grader/RelevanceGraderAgentTest.java`

**Interfaces:**
- Consumes: `ChatClient.Builder`
- Produces: `EvaluationResult evaluate(String question, QueryPlan plan, ToolExecutionResult executionResult)`

- [ ] **Step 1: Write unit test for RelevanceGraderAgent**

```java
package it.davideangelone.assistant.agent.grader;

import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelevanceGraderAgentTest {

    @Test
    void testEvaluateReturnsEvaluation() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        EvaluationResult mockResult = new EvaluationResult(true, true, "HIGH", "Trovate tutte le informazioni");

        when(chatClient.prompt().user(any(String.class)).call().entity(EvaluationResult.class))
                .thenReturn(mockResult);

        RelevanceGraderAgent agent = new RelevanceGraderAgent(chatClient);
        QueryPlan plan = new QueryPlan("Cerca ordine", List.of("id"), List.of("DATABASE"), "Cerca ordine");
        ToolExecutionResult exec = new ToolExecutionResult("Ordine trovato: ID 101, Status SHIPPED", "", List.of());

        EvaluationResult eval = agent.evaluate("Dov'è il mio ordine 101?", plan, exec);

        assertNotNull(eval);
        assertTrue(eval.isRelevant());
        assertTrue(eval.isSufficient());
        assertEquals("HIGH", eval.confidenceScore());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=RelevanceGraderAgentTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement RelevanceGraderAgent**

`src/main/java/it/davideangelone/assistant/agent/grader/RelevanceGraderAgent.java`:
```java
package it.davideangelone.assistant.agent.grader;

import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RelevanceGraderAgent {

    private static final String SYSTEM_PROMPT = """
            Sei un controllore di qualità ed accuratezza dei dati aziendali (Grader/Critic).
            Il tuo compito è confrontare la domanda dell'utente e il piano informativo con i risultati ottenuti dall'esecuzione dei tool.
            Valuta con rigore:
            1. isRelevant (boolean): Le informazioni raccolte sono pertinenti alla domanda posta?
            2. isSufficient (boolean): I dati sono sufficienti e completi per redigere una risposta finale affidabile e precisa senza dover ipotizzare nulla?
            3. confidenceScore: Livello di confidenza complessivo (HIGH, MEDIUM, LOW).
            4. feedback: Se isSufficient è false o mancano elementi, spiega sinteticamente cosa manca o quale tool/ricerca specifica dovrebbe essere tentata. Se sufficiente, indica brevemente 'COMPLETO'.
            """;

    private final ChatClient chatClient;

    public RelevanceGraderAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    RelevanceGraderAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public EvaluationResult evaluate(String question, QueryPlan plan, ToolExecutionResult executionResult) {
        log.info("[GRADER-AGENT] Valutazione pertinenza e completezza per la domanda: {}", question);
        String promptText = """
                DOMANDA UTENTE: %s
                
                PIANO INFORMATIVO:
                - Intento: %s
                - Dati necessari: %s
                
                DATI RECUPERATI DAI TOOL / CONTESTO:
                %s
                """.formatted(
                question,
                plan.intent(),
                plan.informationNeeds(),
                executionResult.summary()
        );

        try {
            EvaluationResult result = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .entity(EvaluationResult.class);

            if (result != null) {
                log.info("[GRADER-AGENT] Risultato valutazione: relevant={}, sufficient={}, score={}, feedback={}",
                        result.isRelevant(), result.isSufficient(), result.confidenceScore(), result.feedback());
                return result;
            }
        } catch (Exception e) {
            log.warn("[GRADER-AGENT] Errore durante grading strutturato: {}", e.getMessage());
        }

        // Fallback conservativo: se abbiamo dati non vuoti assumiamo sufficiente per procedere
        boolean hasData = executionResult.summary() != null && !executionResult.summary().isBlank();
        return new EvaluationResult(hasData, hasData, hasData ? "MEDIUM" : "LOW", hasData ? "OK" : "Nessun dato recuperato");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=RelevanceGraderAgentTest`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/grader/RelevanceGraderAgent.java src/test/java/it/davideangelone/assistant/agent/grader/RelevanceGraderAgentTest.java
git commit -m "feat(agent): implement RelevanceGraderAgent"
```

---

### Task 4: ToolExecutionAgent Implementation with Native `@Tool` & MCP

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/executor/ToolExecutionAgent.java`
- Modify: `src/main/java/it/davideangelone/assistant/config/AiConfiguration.java` (qualify/provide ChatClient for ToolExecutionAgent)
- Test: `src/test/java/it/davideangelone/assistant/agent/executor/ToolExecutionAgentTest.java`

**Interfaces:**
- Consumes: `ChatClient` with configured native `@Tool` and MCP callbacks
- Produces: `ToolExecutionResult execute(String question, QueryPlan plan, String retryFeedback, RetrievedDocumentsContext context)`

- [ ] **Step 1: Write unit test for ToolExecutionAgent**

```java
package it.davideangelone.assistant.agent.executor;

import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolExecutionAgentTest {

    @Test
    void testExecuteCallsChatClientWithToolContext() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn("Record ordini recuperati con successo");
        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                .system(any(String.class))
                .toolContext(any())
                .call()
                .chatResponse()).thenReturn(mockResponse);

        ToolExecutionAgent agent = new ToolExecutionAgent(chatClient);
        QueryPlan plan = new QueryPlan("Ordini", List.of("ordini"), List.of("DATABASE"), "Chiama findOrdersByStatus");
        RetrievedDocumentsContext context = new RetrievedDocumentsContext();

        ToolExecutionResult result = agent.execute("Mostrami gli ordini", plan, null, context);

        assertNotNull(result);
        assertEquals("Record ordini recuperati con successo", result.summary());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolExecutionAgentTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement ToolExecutionAgent**

`src/main/java/it/davideangelone/assistant/agent/executor/ToolExecutionAgent.java`:
```java
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

    public ToolExecutionResult execute(String question, QueryPlan plan, String retryFeedback, RetrievedDocumentsContext context) {
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
                .toolContext(Map.of(Constants.SEARCH_DOCUMENT_TOOL_CONTEXT, context))
                .call()
                .chatResponse();

        String outputText = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            outputText = response.getResult().getOutput().getText();
        }

        log.info("[TOOL-AGENT] Esecuzione completata. Documenti nel contesto: {}", context.getDocuments().size());

        return new ToolExecutionResult(outputText, outputText, context.getDocuments());
    }
}
```

- [ ] **Step 4: Update AiConfiguration to provide `@Qualifier("toolChatClient")` and plain `ChatClient.Builder`**

In [AiConfiguration.java](file:///c:/Users/I/Intellij%20Projects/spring-ai-enterprise-assistant/src/main/java/it/davideangelone/assistant/config/AiConfiguration.java):
Name the ChatClient bean with tools `@Bean("toolChatClient")` and provide a primary `@Bean("chatClient")` or alias so both are injected smoothly.

- [ ] **Step 5: Run tests to verify**

Run: `mvn test -Dtest=ToolExecutionAgentTest`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/executor/ToolExecutionAgent.java src/main/java/it/davideangelone/assistant/config/AiConfiguration.java src/test/java/it/davideangelone/assistant/agent/executor/ToolExecutionAgentTest.java
git commit -m "feat(agent): implement ToolExecutionAgent with native @Tool and MCP execution"
```

---

### Task 5: ResponseSynthesizerAgent Implementation

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/synthesizer/ResponseSynthesizerAgent.java`
- Test: `src/test/java/it/davideangelone/assistant/agent/synthesizer/ResponseSynthesizerAgentTest.java`

**Interfaces:**
- Consumes: `ChatClient.Builder`, `ChatModel`, `DocumentManager`
- Produces: `SynthesizerOutput synthesize(String question, QueryPlan plan, ToolExecutionResult executionResult, EvaluationResult evaluation, RetrievedDocumentsContext context, String conversationId)`

- [ ] **Step 1: Write unit test for ResponseSynthesizerAgent**

```java
package it.davideangelone.assistant.agent.synthesizer;

import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.model.RawResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.utils.DocumentManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResponseSynthesizerAgentTest {

    @Test
    void testSynthesizeFormatsResponse() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
        DocumentManager documentManager = mock(DocumentManager.class);

        when(chatModel.getOptions().getModel()).thenReturn("test-model");
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                .system(any(String.class))
                .call()
                .chatResponse()).thenReturn(mockChatResponse);

        when(documentManager.getRawResponse(mockChatResponse))
                .thenReturn(new RawResponse("Ecco la risposta finale formattata", "Ragionamento dell'agente"));
        when(documentManager.getDocumentsSourceReferences(any()))
                .thenReturn(List.of());

        ResponseSynthesizerAgent agent = new ResponseSynthesizerAgent(chatClient, chatModel, documentManager);
        QueryPlan plan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult exec = new ToolExecutionResult("Dati", "Dati", List.of());
        EvaluationResult eval = new EvaluationResult(true, true, "HIGH", "OK");

        AskResponse response = agent.synthesize("Domanda", plan, exec, eval, new RetrievedDocumentsContext(), "conv-1");

        assertNotNull(response);
        assertEquals("Ecco la risposta finale formattata", response.answer());
        assertEquals("Ragionamento dell'agente", response.reasoning());
        assertEquals("conv-1", response.conversationId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResponseSynthesizerAgentTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement ResponseSynthesizerAgent**

`src/main/java/it/davideangelone/assistant/agent/synthesizer/ResponseSynthesizerAgent.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResponseSynthesizerAgentTest`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/synthesizer/ResponseSynthesizerAgent.java src/test/java/it/davideangelone/assistant/agent/synthesizer/ResponseSynthesizerAgentTest.java
git commit -m "feat(agent): implement ResponseSynthesizerAgent"
```

---

### Task 6: Orchestrator Implementation & AssistantService Integration

**Files:**
- Create: `src/main/java/it/davideangelone/assistant/agent/orchestrator/AgenticRagOrchestrator.java`
- Modify: `src/main/java/it/davideangelone/assistant/service/AssistantService.java`
- Test: `src/test/java/it/davideangelone/assistant/agent/orchestrator/AgenticRagOrchestratorTest.java`

**Interfaces:**
- Produces: `AskResponse orchestrate(String question, String conversationId)`

- [ ] **Step 1: Write unit test for AgenticRagOrchestrator**

```java
package it.davideangelone.assistant.agent.orchestrator;

import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.api.AskResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgenticRagOrchestratorTest {

    @Test
    void testOrchestrationFlowSuccessfulOnFirstTry() {
        QueryPlannerAgent planner = mock(QueryPlannerAgent.class);
        ToolExecutionAgent executor = mock(ToolExecutionAgent.class);
        RelevanceGraderAgent grader = mock(RelevanceGraderAgent.class);
        ResponseSynthesizerAgent synthesizer = mock(ResponseSynthesizerAgent.class);

        QueryPlan mockPlan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult mockResult = new ToolExecutionResult("Dati", "Dati", List.of());
        EvaluationResult mockEval = new EvaluationResult(true, true, "HIGH", "OK");
        AskResponse mockResponse = new AskResponse("Risposta", "Ragionamento", List.of(), "model", "conv-1");

        when(planner.plan("domanda")).thenReturn(mockPlan);
        when(executor.execute(eq("domanda"), eq(mockPlan), isNull(), any())).thenReturn(mockResult);
        when(grader.evaluate("domanda", mockPlan, mockResult)).thenReturn(mockEval);
        when(synthesizer.synthesize(eq("domanda"), eq(mockPlan), eq(mockResult), eq(mockEval), any(), eq("conv-1")))
                .thenReturn(mockResponse);

        AgenticRagOrchestrator orchestrator = new AgenticRagOrchestrator(planner, executor, grader, synthesizer);
        AskResponse response = orchestrator.orchestrate("domanda", "conv-1");

        assertNotNull(response);
        assertEquals("Risposta", response.answer());
        verify(executor, times(1)).execute(any(), any(), any(), any());
    }

    @Test
    void testOrchestrationFlowRetriesWhenInsufficient() {
        QueryPlannerAgent planner = mock(QueryPlannerAgent.class);
        ToolExecutionAgent executor = mock(ToolExecutionAgent.class);
        RelevanceGraderAgent grader = mock(RelevanceGraderAgent.class);
        ResponseSynthesizerAgent synthesizer = mock(ResponseSynthesizerAgent.class);

        QueryPlan mockPlan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult mockResult1 = new ToolExecutionResult("Dati parziali", "Dati parziali", List.of());
        ToolExecutionResult mockResult2 = new ToolExecutionResult("Dati completi", "Dati completi", List.of());
        EvaluationResult mockEvalFail = new EvaluationResult(true, false, "LOW", "Cerca anche stato ordine");
        EvaluationResult mockEvalPass = new EvaluationResult(true, true, "HIGH", "OK");
        AskResponse mockResponse = new AskResponse("Risposta", "Ragionamento", List.of(), "model", "conv-1");

        when(planner.plan("domanda")).thenReturn(mockPlan);
        when(executor.execute(eq("domanda"), eq(mockPlan), isNull(), any())).thenReturn(mockResult1);
        when(executor.execute(eq("domanda"), eq(mockPlan), eq("Cerca anche stato ordine"), any())).thenReturn(mockResult2);
        when(grader.evaluate("domanda", mockPlan, mockResult1)).thenReturn(mockEvalFail);
        when(grader.evaluate("domanda", mockPlan, mockResult2)).thenReturn(mockEvalPass);
        when(synthesizer.synthesize(any(), any(), any(), any(), any(), any())).thenReturn(mockResponse);

        AgenticRagOrchestrator orchestrator = new AgenticRagOrchestrator(planner, executor, grader, synthesizer);
        AskResponse response = orchestrator.orchestrate("domanda", "conv-1");

        assertNotNull(response);
        verify(executor, times(2)).execute(any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgenticRagOrchestratorTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement AgenticRagOrchestrator**

`src/main/java/it/davideangelone/assistant/agent/orchestrator/AgenticRagOrchestrator.java`:
```java
package it.davideangelone.assistant.agent.orchestrator;

import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgenticRagOrchestrator {

    private static final int MAX_ITERATIONS = 2;

    private final QueryPlannerAgent plannerAgent;
    private final ToolExecutionAgent executionAgent;
    private final RelevanceGraderAgent graderAgent;
    private final ResponseSynthesizerAgent synthesizerAgent;

    public AgenticRagOrchestrator(QueryPlannerAgent plannerAgent,
                                  ToolExecutionAgent executionAgent,
                                  RelevanceGraderAgent graderAgent,
                                  ResponseSynthesizerAgent synthesizerAgent) {
        this.plannerAgent = plannerAgent;
        this.executionAgent = executionAgent;
        this.graderAgent = graderAgent;
        this.synthesizerAgent = synthesizerAgent;
    }

    public AskResponse orchestrate(String question, String conversationId) {
        log.info("[ORCHESTRATOR] Avvio ciclo multi-agente per conversazione '{}'", conversationId);
        RetrievedDocumentsContext retrievedContext = new RetrievedDocumentsContext();

        // 1. Fase di Pianificazione
        QueryPlan plan = plannerAgent.plan(question);

        // 2. Ciclo di Esecuzione Tool & Grading con Retroazione
        ToolExecutionResult currentExecution = null;
        EvaluationResult currentEvaluation = null;
        String feedback = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("[ORCHESTRATOR] Iterazione di esecuzione {}/{}", iteration, MAX_ITERATIONS);

            currentExecution = executionAgent.execute(question, plan, feedback, retrievedContext);
            currentEvaluation = graderAgent.evaluate(question, plan, currentExecution);

            if (currentEvaluation.isSufficient() || iteration == MAX_ITERATIONS) {
                if (!currentEvaluation.isSufficient()) {
                    log.warn("[ORCHESTRATOR] Raggiunto limite massimo iterazioni ({}) con dati ancora parziali.", MAX_ITERATIONS);
                } else {
                    log.info("[ORCHESTRATOR] Valutazione positiva al tentativo {}.", iteration);
                }
                break;
            }

            feedback = currentEvaluation.feedback();
            log.info("[ORCHESTRATOR] Dati insufficienti. Avvio ciclo correttivo con feedback: {}", feedback);
        }

        // 3. Fase di Sintesi Finale
        return synthesizerAgent.synthesize(question, plan, currentExecution, currentEvaluation, retrievedContext, conversationId);
    }
}
```

- [ ] **Step 4: Update AssistantService to use AgenticRagOrchestrator**

In [AssistantService.java](file:///c:/Users/I/Intellij%20Projects/spring-ai-enterprise-assistant/src/main/java/it/davideangelone/assistant/service/AssistantService.java):
Inject `AgenticRagOrchestrator` and delegate `ask(question, conversationId)`:
```java
public AskResponse ask(String question, String conversationId) {
    return orchestrator.orchestrate(question, conversationId);
}
```

- [ ] **Step 5: Run all unit and integration tests**

Run: `mvn clean test`
Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/it/davideangelone/assistant/agent/orchestrator/AgenticRagOrchestrator.java src/main/java/it/davideangelone/assistant/service/AssistantService.java src/test/java/it/davideangelone/assistant/agent/orchestrator/AgenticRagOrchestratorTest.java
git commit -m "feat(orchestrator): implement AgenticRagOrchestrator and connect AssistantService"
```

---

### Task 7: Full Verification and Cleanup

**Files:**
- Modify: `pom.xml` (if any configuration tweak needed)
- Run: full test suite and build verification

- [ ] **Step 1: Run complete Maven build and verify zero regressions**

Run: `mvn clean package -DskipTests=false`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Commit any final changes**

```powershell
git status
git commit -am "chore: finalize multi-agent orchestrated RAG setup"
```
