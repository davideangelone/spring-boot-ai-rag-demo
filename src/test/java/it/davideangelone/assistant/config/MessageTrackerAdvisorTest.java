package it.davideangelone.assistant.config;

import java.util.Map;
import it.davideangelone.assistant.utils.DocumentManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageTrackerAdvisorTest {

    @Test
    void testBeforeDoesNotThrowNpeWhenMessageNumberIsNull() {
        DocumentManager documentManager = mock(DocumentManager.class);
        MessageTrackerAdvisor advisor = new MessageTrackerAdvisor(documentManager);

        ChatClientRequest request = mock(ChatClientRequest.class);
        AdvisorChain chain = mock(AdvisorChain.class);
        Prompt prompt = new Prompt(new UserMessage("Qual è la procedura?"));

        when(request.context()).thenReturn(Map.of());
        when(request.prompt()).thenReturn(prompt);

        assertDoesNotThrow(() -> advisor.before(request, chain));
    }
}
