package it.davideangelone.assistant.tools.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
}
