package it.davideangelone.assistant.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import it.davideangelone.assistant.model.MemoryLogMessageMetadata;
import it.davideangelone.assistant.model.RawResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import it.davideangelone.assistant.model.SourceReference;
import it.davideangelone.assistant.service.ReasoningSanitizer;
import it.davideangelone.assistant.utils.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import static it.davideangelone.assistant.constants.Constants.CONVERSATION_ID;
import static it.davideangelone.assistant.constants.Constants.MESSAGE_COUNTER_TOOL_CONTEXT;
import static it.davideangelone.assistant.constants.Constants.SEARCH_DOCUMENT_TOOL_CONTEXT;

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
        if (!request.context().containsKey(MESSAGE_COUNTER_TOOL_CONTEXT)) {
            return request;
        }

        String conversationId = getConversationId(request.context());
        int messageNumber = getCurrentMessageNumber(conversationId, request.context());

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
        if (!response.context().containsKey(MESSAGE_COUNTER_TOOL_CONTEXT)) {
            return response;
        }

        String conversationId = getConversationId(response.context());
        int messageNumber = getCurrentMessageNumber(conversationId, response.context());
        RawResponse rawResponse = documentManager.getRawResponse(response.chatResponse());

        if (!StringUtils.isBlank(rawResponse.rawResponse())) {
            List<SourceReference> sources = getSources(response);
            String modelName = Optional.ofNullable(response.chatResponse())
                    .map(ChatResponse::getMetadata)
                    .map(ChatResponseMetadata::getModel)
                    .orElse(null);

            ReasoningSanitizer.SanitizedOutput sanitized = ReasoningSanitizer.sanitize(rawResponse.rawThinking(), rawResponse.rawResponse());

            logAnswer(conversationId, messageNumber, MessageType.ASSISTANT, sanitized.answer(), sanitized.reasoning(), sources, modelName);
        }

        return response;
    }

    public int recordUserMessage(String conversationId, String content) {
        int messageNumber = messageCounters.computeIfAbsent(conversationId, k -> new AtomicInteger(0)).incrementAndGet();
        logRequest(conversationId, messageNumber, MessageType.USER, content);
        return messageNumber;
    }

    public void recordAssistantAnswer(String conversationId, String answer, String thinking, List<SourceReference> sources, String modelName) {
        int messageNumber = messageCounters.computeIfAbsent(conversationId, k -> new AtomicInteger(0)).get();
        logAnswer(conversationId, messageNumber, MessageType.ASSISTANT, answer, thinking, sources, modelName);
    }

    private @NonNull List<SourceReference> getSources(@NonNull ChatClientResponse response) {
        RetrievedDocumentsContext retrievedContext = (RetrievedDocumentsContext) response.context().get(SEARCH_DOCUMENT_TOOL_CONTEXT);
        return Optional.ofNullable(retrievedContext)
                .map(RetrievedDocumentsContext::getDocuments)
                .map(documentManager::getDocumentsSourceReferences)
                .orElse(List.of());
    }

    private void logRequest(String conversationId, int messageNumber, MessageType role, String content) {
        Instant now = Instant.now();

        MemoryLogMessageMetadata meta = new MemoryLogMessageMetadata(messageNumber, now, role.toString(), content, null, null, null, null);
        memoryLog.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(meta);

        log.info("[CHAT-USER-REQUEST] [{}] Msg {} | {} | {}", now, messageNumber, role, StringUtils.abbreviate(StringUtils.normalizeSpace(content), 100));
    }

    private void logAnswer(String conversationId, int messageNumber, MessageType role, String answer, String thinking, List<SourceReference> sources, String modelName) {
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

    private int getCurrentMessageNumber(String conversationId, Map<String, Object> context) {
        Object val = context.get(MESSAGE_COUNTER_TOOL_CONTEXT);
        if (val instanceof Integer i) {
            return i;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        return messageCounters.computeIfAbsent(conversationId, k -> new AtomicInteger(0)).get();
    }

    public List<MemoryLogMessageMetadata> getLogs(String conversationId) {
        return memoryLog.getOrDefault(conversationId, List.of());
    }
}