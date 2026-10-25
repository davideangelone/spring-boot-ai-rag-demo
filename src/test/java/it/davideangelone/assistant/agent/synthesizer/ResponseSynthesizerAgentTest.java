package it.davideangelone.assistant.agent.synthesizer;

import java.util.List;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.model.RawResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.utils.DocumentManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResponseSynthesizerAgentTest {

    @Test
    void testSynthesizeFormatsResponse() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
        DocumentManager documentManager = mock(DocumentManager.class);

        when(chatModel.getOptions().getModel()).thenReturn("test-model");
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                .system(any(String.class))
                .call()
                .chatResponse()).thenReturn(mockChatResponse);

        when(documentManager.getRawResponse(mockChatResponse))
                .thenReturn(new RawResponse("Ecco la risposta finale formattata", "Ragionamento dell'agente"));
        when(documentManager.getDocumentsSourceReferences(any()))
                .thenReturn(List.of());

        ResponseSynthesizerAgent agent = new ResponseSynthesizerAgent(chatClient, chatModel, documentManager);
        QueryPlan plan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult exec = new ToolExecutionResult("Dati", "Dati", List.of());
        EvaluationResult eval = new EvaluationResult(true, true, "HIGH", "OK");

        AskResponse response = agent.synthesize("Domanda", plan, exec, eval, new RetrievedDocumentsContext(), "conv-1");

        assertNotNull(response);
        assertEquals("Ecco la risposta finale formattata", response.answer());
        assertEquals("Ragionamento dell'agente", response.reasoning());
        assertEquals("conv-1", response.conversationId());
        assertEquals("test-model", response.model());
    }
}
