package it.davideangelone.assistant.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.document.Document;

public class RetrievedDocumentsContext {

    private final List<Document> documents = new CopyOnWriteArrayList<>();

    public void addAll(List<Document> documents) {
        this.documents.addAll(documents);
    }

    public List<Document> getDocuments() {
        return List.copyOf(documents);
    }
}
