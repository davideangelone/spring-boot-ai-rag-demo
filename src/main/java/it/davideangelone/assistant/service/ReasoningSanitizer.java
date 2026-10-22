package it.davideangelone.assistant.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to isolate internal chain-of-thought / reasoning processes from the primary answer field.
 * Handles native model think tokens (e.g., <think>...</think> or <thought>...</thought>), preserves
 * all Markdown formatting (tables, newlines, lists) in the answer, and rescues tables mistakenly
 * emitted inside reasoning.
 */
public final class ReasoningSanitizer {

    public static final String DEFAULT_NO_ANSWER = "Il modello non ha restituito una risposta.";

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
            "<(?:think|thought)>(.*?)</(?:think|thought)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern UNCLOSED_THINK_PATTERN = Pattern.compile(
            "<(?:think|thought)>(.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern TABLE_DELIMITER_PATTERN = Pattern.compile(
            "(?m)^[ \\t]*\\|?[ \\t]*:?-{3,}:?[ \\t]*(?:\\|[ \\t]*:?-{3,}:?[ \\t]*)+\\|?[ \\t]*$"
    );

    private ReasoningSanitizer() {
    }

    public record SanitizedOutput(String reasoning, String answer) {
    }

    private record TableExtraction(String table, String remainingText) {
    }

    public static SanitizedOutput sanitize(String rawReasoning, String rawAnswer) {
        String reasoning = (rawReasoning == null || rawReasoning.isBlank()) ? null : rawReasoning.trim();
        String answer = (rawAnswer == null || rawAnswer.isBlank()) ? null : rawAnswer.trim();

        if (answer == null) {
            // Check if reasoning holds an orphaned table or content
            if (hasTable(reasoning)) {
                TableExtraction extraction = extractTable(reasoning);
                if (extraction != null && !extraction.table().isBlank()) {
                    return new SanitizedOutput(extraction.remainingText(), extraction.table());
                }
            }
            return new SanitizedOutput(reasoning, DEFAULT_NO_ANSWER);
        }

        List<String> extractedThoughts = new ArrayList<>();

        // 1. Extract closed <think>...</think> or <thought>...</thought> tags
        Matcher matcher = THINK_TAG_PATTERN.matcher(answer);
        StringBuilder cleanAnswerBuffer = new StringBuilder();
        while (matcher.find()) {
            String thoughtContent = matcher.group(1).trim();
            if (!thoughtContent.isBlank()) {
                extractedThoughts.add(thoughtContent);
            }
            int start = matcher.start();
            int end = matcher.end();
            boolean needsSpace = start > 0 && !Character.isWhitespace(answer.charAt(start - 1))
                    && end < answer.length() && !Character.isWhitespace(answer.charAt(end));
            matcher.appendReplacement(cleanAnswerBuffer, needsSpace ? " " : "");
        }
        matcher.appendTail(cleanAnswerBuffer);
        answer = cleanAnswerBuffer.toString().trim();

        // 2. Handle unclosed <think> or <thought> tag if any remains
        Matcher unclosedMatcher = UNCLOSED_THINK_PATTERN.matcher(answer);
        if (unclosedMatcher.find()) {
            String trailingThought = unclosedMatcher.group(1).trim();
            if (!trailingThought.isBlank()) {
                extractedThoughts.add(trailingThought);
            }
            answer = answer.substring(0, unclosedMatcher.start()).trim();
        }

        // 3. Merge existing reasoning with newly extracted thoughts
        String finalReasoning = reasoning;
        if (!extractedThoughts.isEmpty()) {
            String joinedExtracted = String.join("\n\n", extractedThoughts);
            if (finalReasoning == null || finalReasoning.isBlank()) {
                finalReasoning = joinedExtracted;
            } else {
                finalReasoning = finalReasoning + "\n\n" + joinedExtracted;
            }
        }

        // 4. Rescue markdown table from reasoning if missing in answer
        if (!hasTable(answer) && hasTable(finalReasoning)) {
            TableExtraction extraction = extractTable(finalReasoning);
            if (extraction != null && !extraction.table().isBlank()) {
                finalReasoning = extraction.remainingText();
                answer = answer.isEmpty() || answer.equals(DEFAULT_NO_ANSWER)
                        ? extraction.table()
                        : answer + "\n\n" + extraction.table();
            }
        }

        String finalAnswer = answer.isBlank() ? DEFAULT_NO_ANSWER : answer;

        return new SanitizedOutput(finalReasoning, finalAnswer);
    }

    private static boolean hasTable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return TABLE_DELIMITER_PATTERN.matcher(text).find();
    }

    private static TableExtraction extractTable(String text) {
        String[] lines = text.split("\\r?\\n");
        int delimiterIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (TABLE_DELIMITER_PATTERN.matcher(lines[i]).matches()) {
                delimiterIndex = i;
                break;
            }
        }

        if (delimiterIndex <= 0) {
            return null;
        }

        // Find header: look backwards for lines containing '|'
        int startIndex = delimiterIndex - 1;
        while (startIndex > 0 && lines[startIndex - 1].contains("|")) {
            startIndex--;
        }

        // Find rows: look forwards for lines containing '|'
        int endIndex = delimiterIndex + 1;
        while (endIndex < lines.length && lines[endIndex].contains("|")) {
            endIndex++;
        }

        List<String> tableLines = new ArrayList<>(Arrays.asList(lines).subList(startIndex, endIndex));

        List<String> remainingLines = new ArrayList<>();
        remainingLines.addAll(Arrays.asList(lines).subList(0, startIndex));
        remainingLines.addAll(Arrays.asList(lines).subList(endIndex, lines.length));

        String extractedTable = String.join("\n", tableLines).trim();
        String remainingText = String.join("\n", remainingLines).trim();
        if (remainingText.isBlank()) {
            remainingText = null;
        }

        return new TableExtraction(extractedTable, remainingText);
    }
}
