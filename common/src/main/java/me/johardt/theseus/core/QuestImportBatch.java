package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Per-file validation and rollback-safe commit for quest imports. */
public final class QuestImportBatch {
    public static final int MAX_IMPORT_BYTES = 1_048_576;
    private QuestImportBatch() {}

    public static List<FileResult> preflight(Map<String, String> files) {
        List<FileResult> results = new ArrayList<>();
        for (var entry : files.entrySet()) {
            String filename = entry.getKey();
            String source = entry.getValue();
            List<QuestDiagnostics.Diagnostic> diagnostics = new ArrayList<>();
            String id = proposedId(filename);
            if (id == null) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "invalid_filename", "", "filename", "Filename must be a .json file with a lowercase quest ID", "Rename the file."));
                results.add(new FileResult(filename, null, source == null ? 0 : source.getBytes(StandardCharsets.UTF_8).length, null, diagnostics));
                continue;
            }
            JsonObject root = null;
            long size = source == null ? 0 : source.getBytes(StandardCharsets.UTF_8).length;
            if (source == null) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "read_failed", id, "filename", "File is not readable", "Choose a readable file."));
                results.add(new FileResult(filename, id, 0, null, List.copyOf(diagnostics)));
                continue;
            }
            if (size > MAX_IMPORT_BYTES) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "file_too_large", id, "$", "File exceeds the 1 MiB import limit", "Reduce the file size and try again."));
                results.add(new FileResult(filename, id, size, null, List.copyOf(diagnostics)));
                continue;
            }
            try {
                List<String> duplicateKeys = JsonDuplicateKeyDetector.findDuplicates(source);
                if (!duplicateKeys.isEmpty()) diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "duplicate_json_key", id, duplicateKeys.getFirst(), "Duplicate JSON key(s): " + String.join(", ", duplicateKeys), "Remove duplicate keys and try again."));
                var parsed = JsonParser.parseString(source);
                if (!parsed.isJsonObject()) {
                    diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "invalid_quest_document", id, "$", "Quest document must be a JSON object", "Wrap the quest fields in a JSON object."));
                } else {
                    root = parsed.getAsJsonObject();
                }
                if (root == null) {
                    results.add(new FileResult(filename, id, size, null, List.copyOf(diagnostics)));
                    continue;
                }
                diagnostics.addAll(QuestDiagnostics.validate(id, root));
            } catch (Exception exception) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "malformed_json", id, "$", "Malformed quest JSON: " + exception.getMessage(), "Fix the JSON syntax and try again."));
            }
            results.add(new FileResult(filename, id, size, root, List.copyOf(diagnostics)));
        }
        return List.copyOf(results);
    }

    public static void commit(Path questsDirectory, Map<String, JsonObject> quests) throws IOException {
        QuestDocumentStore.forQuestDirectory(questsDirectory).importQuests(quests);
    }

    private static String proposedId(String filename) {
        if (filename == null || !filename.endsWith(".json")) return null;
        String id = filename.substring(0, filename.length() - 5);
        return id.matches("[a-z0-9_.-]+") ? id : null;
    }

    public record FileResult(String filename, String proposedId, long size, JsonObject root, List<QuestDiagnostics.Diagnostic> diagnostics) {
        public boolean valid() { return root != null && diagnostics.stream().noneMatch(QuestDiagnostics.Diagnostic::blocksSave); }
    }
}
