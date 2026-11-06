package it.davideangelone.assistant.agent.executor;

import java.util.List;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
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
        // Nessun tool realmente invocato (mock): la traccia dell'output grezzo è vuota
        assertEquals("", result.rawOutput());
    }

    @Test
    void testExecuteWithRetryFeedback() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn("Dati aggiornati col retry");
        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                .system(any(String.class))
                .toolContext(any())
                .call()
                .chatResponse()).thenReturn(mockResponse);

        ToolExecutionAgent agent = new ToolExecutionAgent(chatClient);
        QueryPlan plan = new QueryPlan("Ordini", List.of("ordini"), List.of("DATABASE"), "Chiama findOrdersByStatus");
        RetrievedDocumentsContext context = new RetrievedDocumentsContext();

        ToolExecutionResult result = agent.execute("Mostrami gli ordini", plan, "Mancano gli ordini CANCELLED", context);

        assertNotNull(result);
        assertEquals("Dati aggiornati col retry", result.summary());
    }
}
