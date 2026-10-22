package it.davideangelone.assistant.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.davideangelone.assistant.config.MessageTrackerAdvisor;
import it.davideangelone.assistant.model.ChatMessageDto;
import it.davideangelone.assistant.model.MemoryLogMessageMetadata;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {
    private final MessageTrackerAdvisor messageTrackerAdvisor;

    public ChatHistoryService(MessageTrackerAdvisor messageTrackerAdvisor) {
        this.messageTrackerAdvisor = messageTrackerAdvisor;
    }

    public List<ChatMessageDto> getMessageTrackAdvisoryHistory(String conversationId) {
        return messageTrackerAdvisor.getLogs(conversationId).stream()
                .map(this::mapToChatMessageDto)
                .toList();
    }

    private ChatMessageDto mapToChatMessageDto(MemoryLogMessageMetadata meta) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sources", meta.sources());
        map.put("model", meta.modelName());
        map.put("timestamp", meta.timestamp());

        return new ChatMessageDto(meta.role(),
                                  meta.content(),
                                  meta.answer(),
                                  meta.reasoning(),
                                  map
                                  );
    }
}
