package it.davideangelone.assistant.agent.grader;

import java.util.List;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
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

    @Test
    void testEvaluateFallbackOnException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(any(String.class)).call().entity(EvaluationResult.class))
                .thenThrow(new RuntimeException("Parsing failure"));

        RelevanceGraderAgent agent = new RelevanceGraderAgent(chatClient);
        QueryPlan plan = new QueryPlan("Cerca ordine", List.of("id"), List.of("DATABASE"), "Cerca ordine");
        ToolExecutionResult exec = new ToolExecutionResult("Dati estratti dal db", "", List.of());

        EvaluationResult eval = agent.evaluate("Dov'è il mio ordine 101?", plan, exec);

        assertNotNull(eval);
        assertTrue(eval.isRelevant());
        assertTrue(eval.isSufficient());
    }
}
