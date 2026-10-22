package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestImportBatchTest {
    @TempDir Path temp;

    @Test
    void preflightReportsEachFileIndependently() {
        var files = new LinkedHashMap<String, String>();
        files.put("good.json", "{\"display\":{\"title\":\"Good\"},\"tasks\":{},\"rewards\":{}}");
        files.put("bad.json", "{\"tasks\":{\"x\":1},\"tasks\":{}}");
        var results = QuestImportBatch.preflight(files);
        assertTrue(results.get(0).valid());
        assertFalse(results.get(1).valid());
        assertTrue(results.get(1).diagnostics().stream().anyMatch(d -> d.code().equals("duplicate_json_key")));
    }

    @Test
    void commitWritesAllOrNoneWhenTargetConflicts() throws Exception {
        Path quests = temp.resolve("quests");
        Files.createDirectories(quests);
        Files.writeString(quests.resolve("existing.json"), "{}\n");
        var batch = Map.of("new_one", JsonParser.parseString("{}").getAsJsonObject(), "existing", JsonParser.parseString("{}").getAsJsonObject());
        try { QuestImportBatch.commit(quests, batch); } catch (java.io.IOException expected) {}
        assertFalse(Files.exists(quests.resolve("new_one.json")));
        assertTrue(Files.exists(quests.resolve("existing.json")));
    }

    @Test
    void preflightRejectsNonObjectAndReportsUtf8Size() {
        var files = new LinkedHashMap<String, String>();
        files.put("primitive.json", "true");
        String oversized = "é".repeat(QuestImportBatch.MAX_IMPORT_BYTES / 2 + 1);
        files.put("large.json", oversized);

        var results = QuestImportBatch.preflight(files);
        assertTrue(results.get(0).diagnostics().stream().anyMatch(d -> d.code().equals("invalid_quest_document")));
        assertEquals(oversized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, results.get(1).size());
        assertTrue(results.get(1).diagnostics().stream().anyMatch(d -> d.code().equals("file_too_large")));
    }

    @Test
    void preflightReportsExcessiveJsonDepthAsBlockingDiagnostic() {
        int depth = JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1;
        String nestedArrays = "[".repeat(depth) + "0" + "]".repeat(depth);

        var result = QuestImportBatch.preflight(Map.of("too_deep.json", nestedArrays)).getFirst();

        assertFalse(result.valid());
        assertNull(result.root());
        var diagnostic = result.diagnostics().stream()
            .filter(value -> value.code().equals("malformed_json"))
            .findFirst()
            .orElseThrow();
        assertTrue(diagnostic.blocksSave());
        assertEquals("$", diagnostic.path());
        assertTrue(diagnostic.message().contains("maximum raw depth"));
    }

    @Test
    void commitRejectsOversizedEncodedQuestWithoutCreatingFiles() throws Exception {
        Path quests = temp.resolve("quests");
        var root = new com.google.gson.JsonObject();
        root.addProperty("payload", "x".repeat(QuestImportBatch.MAX_IMPORT_BYTES));
        try {
            QuestImportBatch.commit(quests, Map.of("large", root));
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("1 MiB"));
        }
        assertFalse(Files.exists(quests.resolve("large.json")));
    }
}
