package it.davideangelone.assistant.tools.callback;

import java.util.Map;

import it.davideangelone.assistant.constants.Constants;
import it.davideangelone.assistant.model.ToolOutputTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoggingToolCallbackTest {

    @Test
    @DisplayName("Dovrebbe formattare correttamente date ISO per convertTimezones")
    void shouldFormatIsoDateForMcp() {
        assertThat(LoggingToolCallback.formatDateForMcp("2026-09-20 00:00:00"))
                .isEqualTo("2026-09-20 12:00:00 am");

        assertThat(LoggingToolCallback.formatDateForMcp("2026-09-20 14:30:00"))
                .isEqualTo("2026-09-20 02:30:00 pm");

        assertThat(LoggingToolCallback.formatDateForMcp("2026-09-20T00:00:00"))
                .isEqualTo("2026-09-20 12:00:00 am");

        assertThat(LoggingToolCallback.formatDateForMcp("2026-09-20"))
                .isEqualTo("2026-09-20 12:00:00 am");

        assertThat(LoggingToolCallback.formatDateForMcp("2025-06-16 10:00:00 PM"))
                .isEqualTo("2025-06-16 10:00:00 pm");
    }

    @Test
    @DisplayName("Dovrebbe intercettare e normalizzare il payload JSON per il tool convertTimezones")
    void shouldNormalizePayloadForConvertTimezonesTool() {
        ToolCallback mockDelegate = mock(ToolCallback.class);
        ToolDefinition toolDef = ToolDefinition.builder()
                .name("convertTimezones")
                .description("Convert time between timezones")
                .inputSchema("{}")
                .build();
        when(mockDelegate.getToolDefinition()).thenReturn(toolDef);
        when(mockDelegate.call(anyString())).thenReturn("{\"result\":\"success\"}");

        LoggingToolCallback callback = new LoggingToolCallback(mockDelegate, "date-time-tools");

        String rawInput = "{\"dateTime\":\"2026-09-20 00:00:00\",\"fromZone\":\"Europe/Rome\",\"toZone\":\"UTC\"}";
        callback.call(rawInput);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockDelegate).call(captor.capture());

        assertThat(captor.getValue()).contains("\"dateTime\":\"2026-09-20 12:00:00 am\"");
        assertThat(captor.getValue()).contains("\"fromZone\":\"Europe/Rome\"");
        assertThat(captor.getValue()).contains("\"toZone\":\"UTC\"");
    }

    @Test
    @DisplayName("Dovrebbe registrare l'output grezzo del tool nella traccia presente nel ToolContext")
    void shouldRecordRawOutputInToolOutputTrace() {
        ToolCallback mockDelegate = mock(ToolCallback.class);
        ToolDefinition toolDef = ToolDefinition.builder()
                .name("findOrdersByStatus")
                .description("Find orders")
                .inputSchema("{}")
                .build();
        when(mockDelegate.getToolDefinition()).thenReturn(toolDef);
        when(mockDelegate.call(anyString(), any(ToolContext.class))).thenReturn("[{\"id\":1001}]");

        ToolOutputTrace trace = new ToolOutputTrace();
        ToolContext toolContext = new ToolContext(Map.of(Constants.TOOL_OUTPUT_TRACE_TOOL_CONTEXT, trace));

        LoggingToolCallback callback = new LoggingToolCallback(mockDelegate);
        String result = callback.call("{\"status\":\"IN_PROGRESS\"}", toolContext);

        assertThat(result).isEqualTo("[{\"id\":1001}]");
        assertThat(trace.getEntries()).hasSize(1);
        assertThat(trace.getEntries().getFirst().toolName()).isEqualTo("findOrdersByStatus");
        assertThat(trace.getEntries().getFirst().input()).isEqualTo("{\"status\":\"IN_PROGRESS\"}");
        assertThat(trace.getEntries().getFirst().output()).isEqualTo("[{\"id\":1001}]");
    }

    @Test
    @DisplayName("Non dovrebbe fallire se il ToolContext non contiene una traccia")
    void shouldWorkWithoutToolOutputTrace() {
        ToolCallback mockDelegate = mock(ToolCallback.class);
        ToolDefinition toolDef = ToolDefinition.builder()
                .name("getCurrentDateTime")
                .description("Current date time")
                .inputSchema("{}")
                .build();
        when(mockDelegate.getToolDefinition()).thenReturn(toolDef);
        when(mockDelegate.call(anyString(), any(ToolContext.class))).thenReturn("ok");

        LoggingToolCallback callback = new LoggingToolCallback(mockDelegate);

        assertThat(callback.call("{}", new ToolContext(Map.of()))).isEqualTo("ok");
    }
}
