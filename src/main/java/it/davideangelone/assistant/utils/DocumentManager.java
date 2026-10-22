package it.davideangelone.assistant.utils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import it.davideangelone.assistant.model.SourceReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class DocumentManager {

    private final Tika tika = new Tika();

    private final MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();

    public String extractText(MultipartFile file) throws IOException, TikaException {
        return tika.parseToString(file.getInputStream());
    }

    public String detectContentType(MultipartFile file) throws IOException {
        return tika.detect(
                file.getInputStream(),
                file.getOriginalFilename()
        );
    }

    public String detectFormat(MultipartFile file) throws IOException, MimeTypeException {
        try (var inputStream = file.getInputStream()) {
            String mimeType = tika.detect(
                    inputStream,
                    file.getOriginalFilename()
            );

            if ("application/octet-stream".equals(mimeType)) {
                return "UNKNOWN";
            }

            MimeType detectedType = mimeTypes.forName(mimeType);
            String extension = detectedType.getExtension();

            if (extension == null || extension.isBlank()) {
                return "UNKNOWN";
            }

            return extension
                    .replaceFirst("^\\.", "")
                    .toUpperCase(Locale.ROOT);
        }
    }

    public List<SourceReference> getDocumentsSourceReferences(List<Document> documents) {
        if (null == documents) {
            return null;
        }
        return documents.stream()
                .map(this::getSourceReference)
                .distinct()
                .toList();

    }

    private SourceReference getSourceReference(Document document) {
        return new SourceReference(
                UUID.fromString(document.getId()),
                String.valueOf(document.getMetadata().getOrDefault("source", "documento")),
                String.valueOf(document.getMetadata().getOrDefault("chunk", "n/d"))
        );
    }
}