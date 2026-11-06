package it.davideangelone.assistant.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionResultTest {

    @Test
    void testNullFieldsAreNormalized() {
        ToolExecutionResult result = new ToolExecutionResult(null, null, null);

        assertEquals("", result.summary());
        assertEquals("", result.rawOutput());
        assertTrue(result.retrievedDocuments().isEmpty());
    }

    @Test
    void testSummaryAndRawOutputAreKeptSeparate() {
        ToolExecutionResult result = new ToolExecutionResult("riepilogo", "[TOOL 1] findOrderById", null);

        assertEquals("riepilogo", result.summary());
        assertEquals("[TOOL 1] findOrderById", result.rawOutput());
    }
}
