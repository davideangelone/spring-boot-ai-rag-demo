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
