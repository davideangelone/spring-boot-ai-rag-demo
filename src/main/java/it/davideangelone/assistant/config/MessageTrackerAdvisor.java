package it.davideangelone.assistant.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import it.davideangelone.assistant.model.MemoryLogMessageMetadata;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.model.SourceReference;
import it.davideangelone.assistant.utils.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

import static it.davideangelone.assistant.constants.Constants.CONVERSATION_ID;
import static it.davideangelone.assistant.constants.Constants.MESSAGE_COUNTER_TOOL_CONTEXT;
import static it.davideangelone.assistant.constants.Constants.SEARCH_DOCUMENT_TOOL_CONTEXT;

@Slf4j
public class MessageTrackerAdvisor implements BaseAdvisor {

    private final ConcurrentHashMap<String, List<MemoryLogMessageMetadata>> memoryLog = new ConcurrentHashMap<>();
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
        Integer messageNumber = getCurrentMessageNumber(request.context());

        if (!request.prompt().getInstructions().isEmpty()) {
            Message lastMessage = request.prompt().getInstructions().getLast();
            if (lastMessage.getMessageType() == MessageType.USER) {
                logRequest(conversationId, messageNumber, MessageType.USER, lastMessage.getText());
            }
        }

        return request;
    }

    @Override
    public @NonNull ChatClientResponse after(@NonNull ChatClientResponse response, @NonNull AdvisorChain chain) {
        String conversationId = getConversationId(response.context());
        Integer messageNumber = getCurrentMessageNumber(response.context());

        String answer = Optional.ofNullable(response.chatResponse())
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");

        String thinking = Optional.ofNullable(response.chatResponse())
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getMetadata)
                .map(metadata -> metadata.get("thinking"))
                .map(Object::toString)
                .orElse("");

        if (!answer.isBlank()) {
            List<SourceReference> sources = getSources(response);
            String modelName = response.chatResponse().getMetadata().getModel();

            logAnswer(conversationId, messageNumber, MessageType.ASSISTANT, answer, thinking, sources, modelName);
        }

        return response;
    }

    private @NonNull List<SourceReference> getSources(@NonNull ChatClientResponse response) {
        RetrievedDocumentsContext retrievedContext = (RetrievedDocumentsContext) response.context().get(SEARCH_DOCUMENT_TOOL_CONTEXT);
        return Optional.ofNullable(retrievedContext)
                .map(RetrievedDocumentsContext::getDocuments)
                .map(documentManager::getDocumentsSourceReferences)
                .orElse(List.of());
    }

    private void logRequest(String conversationId, Integer messageNumber, MessageType role, String content) {
        Instant now = Instant.now();

        MemoryLogMessageMetadata meta = new MemoryLogMessageMetadata(messageNumber, now, role.toString(), content, null, null, null, null);
        memoryLog.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(meta);

        log.info("[CHAT-USER-REQUEST] [{}] Msg {} | {} | {}", now, messageNumber, role, StringUtils.abbreviate(StringUtils.normalizeSpace(content), 100));
    }

    private void logAnswer(String conversationId, Integer messageNumber, MessageType role, String answer, String thinking, List<SourceReference> sources, String modelName) {
        Instant now = Instant.now();

        MemoryLogMessageMetadata meta = new MemoryLogMessageMetadata(messageNumber, now, role.toString(), null, answer, thinking, sources, modelName);
        memoryLog.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(meta);

        log.info("[CHAT-ASSISTANT-ANSWER] [{}] Msg {} | {} | {}", now, messageNumber, role, StringUtils.abbreviate(StringUtils.normalizeSpace(answer), 100));
        log.info("[CHAT-ASSISTANT-THINKING] [{}] Msg {} | {} | {}", now, messageNumber, role, StringUtils.abbreviate(StringUtils.normalizeSpace(thinking), 100));
    }

    private String getConversationId(Map<String, Object> context) {
        String convId = (String) context.get(CONVERSATION_ID);
        if (convId == null || convId.isBlank()) {
            convId = "default";
        }
        return convId;
    }

    private Integer getCurrentMessageNumber(Map<String, Object> context) {
        return (Integer) context.get(MESSAGE_COUNTER_TOOL_CONTEXT);
    }

    public List<MemoryLogMessageMetadata> getLogs(String conversationId) {
        return memoryLog.getOrDefault(conversationId, List.of());
    }
}