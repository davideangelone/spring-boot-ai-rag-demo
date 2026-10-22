package it.davideangelone.assistant.api;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.davideangelone.assistant.model.ChatMessageDto;
import it.davideangelone.assistant.service.AssistantService;
import it.davideangelone.assistant.service.ChatHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static it.davideangelone.assistant.constants.Constants.CONVERSATION_ID;

@RestController
@RequestMapping("/api")
public class AssistantController {

    private final AssistantService assistantService;
    private final ChatHistoryService chatHistoryService;

    public AssistantController(AssistantService assistantService, ChatHistoryService chatHistoryService) {
        this.assistantService = assistantService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request, HttpSession session) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il campo 'question' è obbligatorio.");
        }
        String conversationId = (String) session.getAttribute(CONVERSATION_ID);

        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            session.setAttribute(CONVERSATION_ID, conversationId);
        }
        return assistantService.ask(request.question().trim(), conversationId);
    }

    @PostMapping("/new")
    public Map<String, String> newConversation(HttpSession session) {
        String conversationId = UUID.randomUUID().toString();
        session.setAttribute(CONVERSATION_ID, conversationId);
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il file è vuoto.");
        }
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        int chunks = assistantService.ingestDocument(file);
        return Map.of("filename", filename, "chunksIndexed", chunks, "status", "indexed");
    }

    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(HttpSession session) {

        String conversationId = (String) session.getAttribute(CONVERSATION_ID);

        if (conversationId == null) {
            return Collections.emptyList();
        }

        return chatHistoryService.getMessageTrackAdvisoryHistory(conversationId);
    }
}
