package it.davideangelone.assistant.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import it.davideangelone.assistant.constants.Constants;
import it.davideangelone.assistant.model.AssistantStructuredResponse;
import it.davideangelone.assistant.model.MemoryLogMessageMetadata;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.model.SourceReference;
import it.davideangelone.assistant.utils.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import static it.davideangelone.assistant.constants.Constants.CONVERSATION_ID;

@Slf4j
public class MessageTrackerAdvisor implements BaseAdvisor {

    private final ConcurrentHashMap<String, List<MemoryLogMessageMetadata>> memoryLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> messageCounters = new ConcurrentHashMap<>();
    private final DocumentManager documentManager;

    public MessageTrackerAdvisor(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    @Override
    public @NonNull String getName() {
        return "MessageTrackerAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public @NonNull ChatClientRequest before(@NonNull ChatClientRequest request, @NonNull AdvisorChain chain) {
        String conversationId = getConversationId(request.context());

        if (!request.prompt().getInstructions().isEmpty()) {
            Message lastMessage = request.prompt().getInstructions().getLast();
            if (lastMessage.getMessageType() == MessageType.USER) {
                logRequest(conversationId, MessageType.USER, lastMessage.getText());
            }
        }

        return request;
    }

    @Override
    public @NonNull ChatClientResponse after(@NonNull ChatClientResponse response, @NonNull AdvisorChain chain) {
        String conversationId = getConversationId(response.context());

        String aiContent = Optional.ofNullable(response.chatResponse())
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");

        if (!aiContent.isBlank()) {
            RetrievedDocumentsContext retrievedContext = (RetrievedDocumentsContext) response.context().get(Constants.SEARCH_DOCUMENT_TOOL_CONTEXT);
            List<SourceReference> sources = Optional.ofNullable(retrievedContext)
                    .map(RetrievedDocumentsContext::getDocuments)
                    .map(documentManager::getDocumentsSourceReferences)
                    .orElse(List.of());
            String modelName = getModelName(response.context());

            AssistantStructuredResponse structuredResponse = documentManager.getStructuredResponse(aiContent);
            logAnswer(conversationId, MessageType.ASSISTANT, aiContent, structuredResponse, sources, modelName);
        }

        return response;
    }

    private void logRequest(String conversationId, MessageType role, String content) {
        int msgNumber = getCurrentMessageNumber(conversationId);
        Instant now = Instant.now();

        MemoryLogMessageMetadata meta = new MemoryLogMessageMetadata(msgNumber, now, role.toString(), content, null, null, null, null);
        memoryLog.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(meta);

        log.info("[CHAT-REQUEST] [{}] Msg {} | {} | {}", now, msgNumber, role, content);
    }

    private void logAnswer(String conversationId, MessageType role, String content, AssistantStructuredResponse structuredResponse, List<SourceReference> sources, String modelName) {
        int msgNumber = getCurrentMessageNumber(conversationId);
        Instant now = Instant.now();

        String answer = null;
        String reasoning = null;
        if (null != structuredResponse) {
            content = null;
            answer = structuredResponse.answer();
            reasoning = structuredResponse.reasoning();
        }
        MemoryLogMessageMetadata meta = new MemoryLogMessageMetadata(msgNumber, now, role.toString(), content, answer, reasoning, sources, modelName);
        memoryLog.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(meta);

        log.info("[CHAT-ANSWER] [{}] Msg {} | {} | {}", now, msgNumber, role, content);
    }

    private int getCurrentMessageNumber(String conversationId) {
        AtomicInteger counter = messageCounters.computeIfAbsent(conversationId, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    private String getConversationId(Map<String, Object> context) {
        String convId = (String) context.get(CONVERSATION_ID);
        if (convId == null || convId.isBlank()) {
            convId = "default";
        }
        return convId;
    }

    private String getModelName(Map<String, Object> context) {
        ChatOptions chatOptions = (ChatOptions) context.get(Constants.CHAT_OPTIONS);
        return chatOptions != null ? chatOptions.getModel() : null;
    }

    public List<MemoryLogMessageMetadata> getLogs(String conversationId) {
        return memoryLog.getOrDefault(conversationId, List.of());
    }
}