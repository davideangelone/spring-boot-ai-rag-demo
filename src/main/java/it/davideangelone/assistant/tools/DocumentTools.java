package it.davideangelone.assistant.tools;

import java.util.List;

import it.davideangelone.assistant.constants.Constants;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DocumentTools {

    private final VectorStore vectorStore;
    private final int retrievalTopK;

    public DocumentTools(VectorStore vectorStore, @Value("${app.rag.top-k:6}") int retrievalTopK) {
        this.vectorStore = vectorStore;
        this.retrievalTopK = retrievalTopK;
    }

    @Tool(description = """
            Cerca nei documenti aziendali informazioni sulle procedure,
            sulle regole operative e sulle politiche aziendali.
            Usa questo strumento quando la risposta richiede informazioni
            contenute nella documentazione aziendale.
            """)
    public List<Document> searchDocuments(
            @ToolParam(description = "Argomento da cercare nei documenti aziendali") String query,
            ToolContext toolContext) {
        log.info("[TOOL] Usato tool searchDocuments");
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(retrievalTopK)
                        .build()
        );

        Object value = toolContext.getContext().get(Constants.SEARCH_DOCUMENT_TOOL_CONTEXT);

        if (value instanceof RetrievedDocumentsContext context) {
            context.addAll(documents);
        }

        return documents;
    }
}