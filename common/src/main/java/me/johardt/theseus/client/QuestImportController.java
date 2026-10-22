package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestImportBatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns importer state independently from rendering, allowing files to be removed/revalidated in place. */
public final class QuestImportController {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private List<QuestDiagnostics.Diagnostic> batchDiagnostics = List.of();

    public void addFiles(List<Path> paths) {
        batchDiagnostics = List.of();
        for (Path path : paths) {
            try {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                QuestImportBatch.FileResult result = QuestImportBatch.preflight(Map.of(path.getFileName().toString(), source)).getFirst();
                entries.put(path.toString(), new Entry(path.toString(), source, result.proposedId(), result.root(), result.diagnostics()));
            } catch (IOException exception) {
                entries.put(path.toString(), new Entry(path.toString(), "", null, null, List.of(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "read_failed", "", "filename", "Could not read file: " + exception.getMessage(), "Choose a readable file."))));
            }
        }
        refreshDuplicateIds();
    }

    public void remove(String key) { entries.remove(key); refreshDuplicateIds(); }
    public void clear() { entries.clear(); batchDiagnostics = List.of(); }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public List<QuestDiagnostics.Diagnostic> batchDiagnostics() { return batchDiagnostics; }

    /**
     * Applies authoritative server diagnostics to the matching import rows.
     * Diagnostics without a quest ID remain attached to the batch so no
     * rejection disappears when the server performs checks unavailable to the client.
     */
    public void applyServerDiagnostics(List<QuestDiagnostics.Diagnostic> diagnostics) {
        Map<String, List<QuestDiagnostics.Diagnostic>> byQuest = new LinkedHashMap<>();
        List<QuestDiagnostics.Diagnostic> unmatched = new ArrayList<>();
        if (diagnostics != null) {
            for (QuestDiagnostics.Diagnostic diagnostic : diagnostics) {
                String questId = diagnostic.questId();
                if (questId == null || questId.isBlank()) {
                    unmatched.add(diagnostic);
                } else {
                    byQuest.computeIfAbsent(questId, ignored -> new ArrayList<>()).add(diagnostic);
                }
            }
        }
        entries.replaceAll((key, entry) -> {
            List<QuestDiagnostics.Diagnostic> additions = byQuest.get(entry.id());
            if (additions == null || additions.isEmpty()) return entry;
            return entry.withAdditionalDiagnostics(additions);
        });
        refreshDuplicateIds();
        batchDiagnostics = List.copyOf(unmatched);
    }
    public boolean canSubmit() {
        return !entries.isEmpty() && entries.values().stream().allMatch(Entry::valid)
            && entries.values().stream().map(Entry::id).distinct().count() == entries.size()
            && batchDiagnostics.stream().noneMatch(QuestDiagnostics.Diagnostic::blocksSave);
    }

    public String summary() {
        StringBuilder summary = new StringBuilder();
        for (Entry entry : entries.values()) {
            if (summary.length() > 0) summary.append('\n');
            summary.append(entry.key()).append(" — ").append(entry.id() == null ? "no quest ID" : entry.id());
            if (entry.diagnostics().isEmpty()) summary.append(" (ready)");
            else entry.diagnostics().forEach(diagnostic -> summary.append("\n  ").append(diagnostic.severity()).append(" ").append(diagnostic.path()).append(": ").append(diagnostic.message()));
        }
        batchDiagnostics.forEach(diagnostic -> {
            if (summary.length() > 0) summary.append('\n');
            summary.append("BATCH ").append(diagnostic.severity()).append(' ')
                .append(diagnostic.path()).append(": ").append(diagnostic.message());
            if (diagnostic.suggestedFix() != null && !diagnostic.suggestedFix().isBlank()) {
                summary.append(" — Fix: ").append(diagnostic.suggestedFix());
            }
        });
        return summary.toString();
    }

    public boolean changeId(String key, String id) {
        Entry entry = entries.get(key);
        if (entry == null || id == null) return false;
        entries.put(key, entry.withId(id.trim()));
        refreshDuplicateIds();
        batchDiagnostics = List.of();
        return true;
    }

    private void refreshDuplicateIds() {
        Map<String, List<String>> sourcesById = new LinkedHashMap<>();
        entries.values().forEach(entry -> {
            if (entry.id() != null && !entry.id().isBlank()) {
                sourcesById.computeIfAbsent(entry.id(), ignored -> new ArrayList<>()).add(entry.key());
            }
        });
        entries.replaceAll((key, entry) -> {
            List<String> sources = sourcesById.get(entry.id());
            return entry.withDuplicateId(sources != null && sources.size() > 1, sources == null ? List.of() : sources);
        });
    }

    /** Builds the exact request consumed by QuestRuntime#importQuests. */
    public JsonObject request() {
        if (!canSubmit()) throw new IllegalStateException("Import contains invalid files");
        JsonObject files = new JsonObject();
        entries.values().forEach(entry -> files.add(entry.id(), entry.root().deepCopy()));
        JsonObject request = new JsonObject(); request.add("files", files); return request;
    }

    public record Entry(String key, String source, String id, JsonObject root, List<QuestDiagnostics.Diagnostic> diagnostics) {
        private Entry withId(String newId) {
            if (root == null) return new Entry(key, source, newId, root, diagnostics);
            List<QuestDiagnostics.Diagnostic> refreshed = new ArrayList<>();
            diagnostics.stream()
                .filter(diagnostic -> List.of("duplicate_json_key", "malformed_json", "invalid_filename", "read_failed").contains(diagnostic.code()))
                .forEach(refreshed::add);
            QuestDiagnostics.validate(newId, root).forEach(refreshed::add);
            return new Entry(key, source, newId, root, List.copyOf(refreshed));
        }
        private Entry withAdditionalDiagnostics(List<QuestDiagnostics.Diagnostic> additions) {
            List<QuestDiagnostics.Diagnostic> merged = new ArrayList<>(diagnostics);
            additions.forEach(addition -> {
                boolean alreadyPresent = merged.stream().anyMatch(existing ->
                    existing.code().equals(addition.code()) &&
                    existing.path().equals(addition.path()) &&
                    existing.message().equals(addition.message())
                );
                if (!alreadyPresent) merged.add(addition);
            });
            return new Entry(key, source, id, root, List.copyOf(merged));
        }
        private Entry withDuplicateId(boolean duplicate, List<String> sources) {
            List<QuestDiagnostics.Diagnostic> refreshed = diagnostics.stream()
                .filter(diagnostic -> !diagnostic.code().equals("duplicate_import_id"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (duplicate) refreshed.add(new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR,
                "duplicate_import_id",
                id,
                "id",
                "Quest ID '" + id + "' is used by: " + String.join(", ", sources),
                "Change this ID so every imported file has a unique quest ID."
            ));
            return new Entry(key, source, id, root, List.copyOf(refreshed));
        }
        public boolean valid() { return root != null && id != null && diagnostics.stream().noneMatch(QuestDiagnostics.Diagnostic::blocksSave); }
    }
}
