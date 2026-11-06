package it.davideangelone.assistant.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolOutputTraceTest {

    @Test
    void testEmptyTraceRendersEmptyString() {
        ToolOutputTrace trace = new ToolOutputTrace();

        assertTrue(trace.isEmpty());
        assertEquals("", trace.render());
    }

    @Test
    void testRenderIncludesToolNameInputAndFullOutput() {
        ToolOutputTrace trace = new ToolOutputTrace();
        trace.record("findOrdersByStatus", "{\"status\":\"IN_PROGRESS\"}", "[{\"id\":1001},{\"id\":1004}]");
        trace.record("getCurrentDateTime", "{}", "2026-10-25T17:49:00");

        String rendered = trace.render();

        assertFalse(trace.isEmpty());
        assertEquals(2, trace.getEntries().size());
        assertTrue(rendered.contains("[TOOL 1] findOrdersByStatus"));
        assertTrue(rendered.contains("{\"status\":\"IN_PROGRESS\"}"));
        assertTrue(rendered.contains("[{\"id\":1001},{\"id\":1004}]"));
        assertTrue(rendered.contains("[TOOL 2] getCurrentDateTime"));
        assertFalse(rendered.contains("TROCATO"));
    }

    @Test
    void testRenderTruncatesLongOutputWithExplicitMarkerAndKeepsEveryTool() {
        ToolOutputTrace trace = new ToolOutputTrace();
        trace.record("searchDocuments", "{}", "A".repeat(5_000));
        trace.record("findOrderById", "{}", "ordine 1001");

        String rendered = trace.render(2_000);

        assertTrue(rendered.contains("OUTPUT TROCATO: omessi 4000 caratteri"));
        assertTrue(rendered.contains("[TOOL 2] findOrderById"));
        assertTrue(rendered.contains("ordine 1001"));
    }

    @Test
    void testNullOutputIsRenderedAsEmpty() {
        ToolOutputTrace trace = new ToolOutputTrace();
        trace.record("tool", null, null);

        String rendered = trace.render();

        assertTrue(rendered.contains("[TOOL 1] tool"));
    }
}
