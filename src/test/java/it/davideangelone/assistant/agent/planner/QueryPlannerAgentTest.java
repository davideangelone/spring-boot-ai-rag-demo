package it.davideangelone.assistant.agent.planner;

import java.util.List;
import it.davideangelone.assistant.agent.model.QueryPlan;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryPlannerAgentTest {

    @Test
    void testPlanReturnsValidPlan() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        QueryPlan expectedPlan = new QueryPlan("Cerca ordine", List.of("id ordine"), List.of("DATABASE"), "Chiama findOrderById");

        when(chatClient.prompt().user(any(String.class)).call().entity(QueryPlan.class))
                .thenReturn(expectedPlan);

        QueryPlannerAgent agent = new QueryPlannerAgent(chatClient);
        QueryPlan plan = agent.plan("Dov'è il mio ordine 101?");

        assertNotNull(plan);
        assertEquals("Cerca ordine", plan.intent());
        assertEquals("DATABASE", plan.suggestedSources().getFirst());
    }

    @Test
    void testPlanFallbackOnException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(any(String.class)).call().entity(QueryPlan.class))
                .thenThrow(new RuntimeException("Parsing error"));

        QueryPlannerAgent agent = new QueryPlannerAgent(chatClient);
        QueryPlan plan = agent.plan("Quali sono i resi?");

        assertNotNull(plan);
        assertNotNull(plan.intent());
        assertFalse(plan.suggestedSources().isEmpty());
    }
}
