package it.davideangelone.assistant.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import it.davideangelone.assistant.tools.AiTool;
import it.davideangelone.assistant.tools.callback.LoggingToolCallback;
import it.davideangelone.assistant.utils.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiConfiguration {

    @Bean
    public MessageTrackerAdvisor messageTrackerAdvisor(DocumentManager documentManager) {
        return new MessageTrackerAdvisor(documentManager);
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            AiTool[] localAiTools,
            List<McpSyncClient> mcpSyncClients,
            MessageTrackerAdvisor messageTrackerAdvisor) {

        for (McpSyncClient client : mcpSyncClients) {
            String connectionName = client.getClientInfo().title();
            String serverName = client.getServerInfo().name();

            client.listTools().tools().forEach(tool ->
                    log.info("[MCP Connection: {}] [MCP Server: {}] [Tool: {}] Descrizione: {}",
                            connectionName,
                            serverName,
                            tool.name(),
                            StringUtils.abbreviate(StringUtils.normalizeSpace(tool.description()), 200)
                    )
            );
        }

        List<LoggingToolCallback> loggingToolCallbackList = new ArrayList<>();
        // Toolbacks di Tool AI locali
        Arrays.stream(ToolCallbacks.from((Object[]) localAiTools))
                .map(LoggingToolCallback::new)
                .forEach(loggingToolCallbackList::add);

        // Toolbacks di Server MCP
        for (McpSyncClient client : mcpSyncClients) {
            SyncMcpToolCallbackProvider prov = SyncMcpToolCallbackProvider.builder().addMcpClient(client).build();
            List<LoggingToolCallback> mcpLoggingCallbacks = Arrays.stream(prov.getToolCallbacks())
                    .map(callback -> new LoggingToolCallback(callback, client.getClientInfo().title()))
                    .toList();
            loggingToolCallbackList.addAll(mcpLoggingCallbacks);
        }

        Object[] tools = loggingToolCallbackList.toArray();

        return builder
                .defaultTools(tools)
                .defaultAdvisors(messageTrackerAdvisor)
                .build();
    }
}
