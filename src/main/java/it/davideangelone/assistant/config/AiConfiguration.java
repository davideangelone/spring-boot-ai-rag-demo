package it.davideangelone.assistant.config;

import it.davideangelone.assistant.utils.DocumentManager;
import org.springframework.ai.chat.client.ChatClient;
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
            MessageTrackerAdvisor messageTrackerAdvisor) {

        return builder
                .defaultAdvisors(
                        messageTrackerAdvisor
                )
                .build();
    }
}
