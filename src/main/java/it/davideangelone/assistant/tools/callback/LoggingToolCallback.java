package it.davideangelone.assistant.tools.callback;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static it.davideangelone.assistant.constants.Constants.MESSAGE_COUNTER_TOOL_CONTEXT;

@Slf4j
public class LoggingToolCallback implements ToolCallback {

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
        return execute(toolInput, () -> delegate.call(toolInput), null);
    }

    @Override
    public @NonNull String call(@NonNull String toolInput, ToolContext toolContext) {
        Map<String, Object> context = Optional.ofNullable(toolContext)
                .map(ToolContext::getContext)
                .orElse(null);
        Integer messageNumber = getCurrentMessageNumber(context);
        return execute(toolInput, () -> delegate.call(toolInput, toolContext), messageNumber);
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