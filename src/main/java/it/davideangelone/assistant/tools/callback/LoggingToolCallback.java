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

    public LoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
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
        long start = System.nanoTime();

        log.info("[TOOL] [{}] - Msg {} - INPUT: {}", name, msgNumber, toolInput);
        try {
            String result = invocation.get();
            log.info("[TOOL] [{}] - Msg {} - OUTPUT ({} ms): {}", name, msgNumber, getElapsedNanos(start), result);
            return result;
        } catch (RuntimeException e) {
            log.error("[TOOL] [{}] - Msg {} - FAILED ({} ms), input: {}", name, msgNumber, getElapsedNanos(start), toolInput, e);
            throw e;
        }
    }

    private long getElapsedNanos(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private Integer getCurrentMessageNumber(Map<String, Object> context) {
        if (null == context) {
            return null;
        }
        return (Integer) context.get(MESSAGE_COUNTER_TOOL_CONTEXT);
    }
}