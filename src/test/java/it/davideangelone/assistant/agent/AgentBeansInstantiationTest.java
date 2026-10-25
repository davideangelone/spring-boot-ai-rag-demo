package it.davideangelone.assistant.agent;

import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.orchestrator.AgenticRagOrchestrator;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.utils.DocumentManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(classes = {
        QueryPlannerAgent.class,
        ToolExecutionAgent.class,
        RelevanceGraderAgent.class,
        ResponseSynthesizerAgent.class,
        AgenticRagOrchestrator.class,
        AgentBeansInstantiationTest.TestConfig.class
})
class AgentBeansInstantiationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            return mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        }

        @Bean(name = {"toolChatClient", "chatClient"})
        @Primary
        ChatClient toolChatClient() {
            return mock(ChatClient.class, RETURNS_DEEP_STUBS);
        }

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class, RETURNS_DEEP_STUBS);
        }

        @Bean
        DocumentManager documentManager() {
            return mock(DocumentManager.class);
        }
    }

    @Autowired
    private RelevanceGraderAgent relevanceGraderAgent;

    @Autowired
    private QueryPlannerAgent queryPlannerAgent;

    @Autowired
    private ToolExecutionAgent toolExecutionAgent;

    @Autowired
    private ResponseSynthesizerAgent responseSynthesizerAgent;

    @Autowired
    private AgenticRagOrchestrator agenticRagOrchestrator;

    @Test
    void testAllAgentsInstantiateSuccessfullyInSpringContext() {
        assertNotNull(relevanceGraderAgent, "RelevanceGraderAgent must be instantiated by Spring container");
        assertNotNull(queryPlannerAgent, "QueryPlannerAgent must be instantiated by Spring container");
        assertNotNull(toolExecutionAgent, "ToolExecutionAgent must be instantiated by Spring container");
        assertNotNull(responseSynthesizerAgent, "ResponseSynthesizerAgent must be instantiated by Spring container");
        assertNotNull(agenticRagOrchestrator, "AgenticRagOrchestrator must be instantiated by Spring container");
    }
}
