package it.davideangelone.assistant.config;

import java.util.Arrays;

import it.davideangelone.assistant.tools.AiTool;
import it.davideangelone.assistant.tools.callback.LoggingToolCallback;
import it.davideangelone.assistant.utils.DocumentManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    @Bean
    public MessageTrackerAdvisor messageTrackerAdvisor(DocumentManager documentManager) {
        return new MessageTrackerAdvisor(documentManager);
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            AiTool[] aiTools,
            MessageTrackerAdvisor messageTrackerAdvisor) {

        Object[] toolsArray = Arrays.stream(ToolCallbacks.from((Object[]) aiTools))
                .map(LoggingToolCallback::new)
                .toArray();

        return builder
                .defaultTools(toolsArray)
                .defaultAdvisors(
                        messageTrackerAdvisor
                )
                .build();
    }
}
