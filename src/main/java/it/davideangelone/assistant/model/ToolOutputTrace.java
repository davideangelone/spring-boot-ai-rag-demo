package it.davideangelone.assistant.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Traccia delle invocazioni di tool effettuate durante una singola esecuzione dell'agente esecutore.
 * <p>
 * Conserva l'output <em>grezzo</em> restituito da ciascun tool (record del database, chunk documentali, risposte MCP),
 * in modo che grader e synthesizer possano basarsi sui dati reali e non solo sul riepilogo testuale prodotto
 * dall'LLM dell'executor, che potrebbe troncarlo o parafrasarlo.
 */
public class ToolOutputTrace {

    /** Budget complessivo (in caratteri) per la resa testuale della traccia. */
    public static final int DEFAULT_MAX_CHARS = 16_000;

    private static final int MIN_CHARS_PER_ENTRY = 500;

    public record Entry(String toolName, String input, String output) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String toolName, String input, String output) {
        entries.add(new Entry(toolName, input, output));
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String render() {
        return render(DEFAULT_MAX_CHARS);
    }

    /**
     * Rende la traccia in forma testuale. Il budget {@code maxChars} è ripartito equamente tra le invocazioni
     * (minimo {@value #MIN_CHARS_PER_ENTRY} caratteri ciascuna) così che ogni tool resti rappresentato; quando un
     * output viene troncato lo si segnala esplicitamente nel testo.
     *
     * @return la traccia, oppure stringa vuota se nessun tool è stato invocato
     */
    public String render(int maxChars) {
        List<Entry> snapshot = getEntries();
        if (snapshot.isEmpty()) {
            return "";
        }

        int budgetPerEntry = Math.max(maxChars / snapshot.size(), MIN_CHARS_PER_ENTRY);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshot.size(); i++) {
            Entry entry = snapshot.get(i);
            String output = entry.output() == null ? "" : entry.output();

            sb.append("[TOOL ").append(i + 1).append("] ").append(entry.toolName()).append('\n');
            sb.append("INPUT: ").append(entry.input() == null ? "" : entry.input()).append('\n');
            sb.append("OUTPUT:\n");
            if (output.length() > budgetPerEntry) {
                sb.append(output, 0, budgetPerEntry)
                        .append("\n[... OUTPUT TROCATO: omessi ")
                        .append(output.length() - budgetPerEntry)
                        .append(" caratteri ...]");
            } else {
                sb.append(output);
            }
            sb.append("\n\n");
        }
        return sb.toString().stripTrailing();
    }
}
