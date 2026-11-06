package it.davideangelone.assistant.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import it.davideangelone.assistant.utils.DocumentManager;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 180;

    private final VectorStore vectorStore;
    private final DocumentManager documentManager;

    public DocumentIngestionService(VectorStore vectorStore, DocumentManager documentManager) {
        this.vectorStore = vectorStore;
        this.documentManager = documentManager;
    }

    public int ingest(MultipartFile multipartFile) throws IOException {

        try {
            String format = documentManager.detectFormat(multipartFile);
            String text = documentManager.extractText(multipartFile);
            String contentType = documentManager.detectContentType(multipartFile);
            String safeFilename = multipartFile.getOriginalFilename() == null ? "document" : multipartFile.getOriginalFilename();

            if (text.isBlank()) {
                throw new IllegalArgumentException("Il documento non contiene testo estraibile. Se è un PDF scansionato, è necessario applicare OCR.");
            }

            List<String> chunks = splitIntoChunks(text);
            List<Document> documents = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", safeFilename);
                metadata.put("chunk", i + 1);
                metadata.put("documentType", format.toLowerCase(Locale.ROOT));
                metadata.put("contentType", contentType);
                documents.add(new Document(chunks.get(i), metadata));
            }
            // Tutti i formati vengono aggiunti allo stesso VectorStore: la ricerca /ask
            // è quindi globale e non applica filtri sul tipo di documento.
            vectorStore.add(documents);
            return documents.size();
        } catch (Exception e) {
            throw new IOException("Errore durante il caricamento del documento: " + e.getMessage(), e);
        }
    }

    public static List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                int lineBreak = text.lastIndexOf('\n', end);
                int space = text.lastIndexOf(' ', end);
                int naturalBreak = Math.max(paragraphBreak, Math.max(lineBreak, space));
                if (naturalBreak > start + CHUNK_SIZE / 2) {
                    end = naturalBreak;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }
}
