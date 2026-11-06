package it.davideangelone.assistant.service;

import java.io.IOException;

import it.davideangelone.assistant.agent.orchestrator.AgenticRagOrchestrator;
import it.davideangelone.assistant.api.AskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class AssistantService {

    private final AgenticRagOrchestrator orchestrator;
    private final DocumentIngestionService ingestionService;

    public AssistantService(AgenticRagOrchestrator orchestrator,
                            DocumentIngestionService ingestionService) {
        this.orchestrator = orchestrator;
        this.ingestionService = ingestionService;
    }

    public AskResponse ask(String question, String conversationId) {
        return orchestrator.orchestrate(question, conversationId);
    }

    public int ingestDocument(MultipartFile multipartFile) throws IOException {
        return ingestionService.ingest(multipartFile);
    }
}
