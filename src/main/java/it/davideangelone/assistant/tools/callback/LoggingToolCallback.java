package it.davideangelone.assistant.tools.callback;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static it.davideangelone.assistant.constants.Constants.MESSAGE_COUNTER_TOOL_CONTEXT;

@Slf4j
public class LoggingToolCallback implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TARGET_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a", Locale.ENGLISH);

    private final ToolCallback delegate;
    private String serverName;

    public LoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    public LoggingToolCallback(ToolCallback delegate, String serverName) {
        this.delegate = delegate;
        this.serverName = serverName;
    }

    @Override
    public @NonNull ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public @NonNull ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public @NonNull String call(@NonNull String toolInput) {
        String normalizedInput = normalizeInput(toolInput);
        return execute(normalizedInput, () -> delegate.call(normalizedInput), null);
    }

    @Override
    public @NonNull String call(@NonNull String toolInput, ToolContext toolContext) {
        Map<String, Object> context = Optional.ofNullable(toolContext)
                .map(ToolContext::getContext)
                .orElse(null);
        Integer messageNumber = getCurrentMessageNumber(context);
        String normalizedInput = normalizeInput(toolInput);
        return execute(normalizedInput, () -> delegate.call(normalizedInput, toolContext), messageNumber);
    }

    private String normalizeInput(String toolInput) {
        if (!"convertTimezones".equalsIgnoreCase(delegate.getToolDefinition().name())) {
            return toolInput;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toolInput);
            if (root.has("dateTime") && root.get("dateTime").isTextual()) {
                String raw = root.get("dateTime").asText();
                String formatted = formatDateForMcp(raw);
                if (formatted != null && !formatted.equals(raw)) {
                    log.info("[TOOL-ADAPTER] Normalizzato dateTime per convertTimezones: '{}' -> '{}'", raw, formatted);
                    ((ObjectNode) root).put("dateTime", formatted);
                    return OBJECT_MAPPER.writeValueAsString(root);
                }
            }
        } catch (Exception e) {
            log.warn("Impossibile normalizzare dateTime per convertTimezones: {}", e.getMessage());
        }
        return toolInput;
    }

    static String formatDateForMcp(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (trimmed.toLowerCase(Locale.ENGLISH).endsWith("am") || trimmed.toLowerCase(Locale.ENGLISH).endsWith("pm")) {
            return trimmed.toLowerCase(Locale.ENGLISH);
        }

        List<DateTimeFormatter> candidates = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );

        for (DateTimeFormatter fmt : candidates) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, fmt);
                return ldt.format(TARGET_DATE_TIME_FORMATTER).toLowerCase(Locale.ENGLISH);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            LocalDate ld = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDateTime ldt = ld.atStartOfDay();
            return ldt.format(TARGET_DATE_TIME_FORMATTER).toLowerCase(Locale.ENGLISH);
        } catch (DateTimeParseException ignored) {
        }

        return raw;
    }

    private @NonNull String execute(@NonNull String toolInput, Supplier<String> invocation, Integer messageNumber) {
        String name = delegate.getToolDefinition().name();
        String msgNumber = (null == messageNumber) ? "N/A" : messageNumber.toString();
        String logToolInfo = getLogToolInfo(name, msgNumber);
        long start = System.nanoTime();

        log.info("{} - INPUT: {}", logToolInfo, toolInput);
        try {
            String result = invocation.get();
            log.info("{} - OUTPUT ({} ms): {}", logToolInfo, getElapsedNanos(start), result);
            return result;
        } catch (RuntimeException e) {
            log.error("{} - FAILED ({} ms), input: {}", logToolInfo, getElapsedNanos(start), toolInput, e);
            throw e;
        }
    }

    private String getLogToolInfo(String name, String messageNumber) {
        if (null == serverName) {
            return String.format("[TOOL] [%s] - Msg %s", name, messageNumber);
        }
        return String.format("[TOOL] [Server %s] [%s] - Msg %s", serverName, name, messageNumber);
    }

    private long getElapsedNanos(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private Integer getCurrentMessageNumber(Map<String, Object> context) {
        if (null == context) {
            return null;
        }
        Object val = context.get(MESSAGE_COUNTER_TOOL_CONTEXT);
        if (val instanceof Integer i) {
            return i;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}