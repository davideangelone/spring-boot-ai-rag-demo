package it.davideangelone.assistant.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIngestionServiceTest {

    @Test
    void splitsLongTextIntoOverlappingChunks() {
        String text = "parola ".repeat(500);

        List<String> chunks = DocumentIngestionService.splitIntoChunks(text);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().noneMatch(String::isBlank));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 1200));
    }

    @Test
    void returnsSingleChunkForShortText() {
        List<String> chunks = DocumentIngestionService.splitIntoChunks("Procedura breve.");

        assertEquals(1, chunks.size());
        assertEquals("Procedura breve.", chunks.getFirst());
    }
}
