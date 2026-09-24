package me.johardt.theseus.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestScreenImportsTest {
    @TempDir
    Path directory;

    @Test
    void importControlsTrackInvalidAndValidIdEditsAndPendingRequests() throws Exception {
        Path file = directory.resolve("source.json");
        Files.writeString(file, validQuest());
        QuestImportController controller = new QuestImportController();
        controller.addFiles(List.of(file));
        String key = file.toString();

        var initial = QuestScreenImports.importControlState(controller, false);
        assertTrue(initial.importEnabled());
        assertFalse(initial.diagnosticsEnabled().get(key));

        assertTrue(controller.changeId(key, "Bad ID"));
        var invalid = QuestScreenImports.importControlState(controller, false);
        assertFalse(invalid.importEnabled());
        assertTrue(invalid.diagnosticsEnabled().get(key));
        assertNull(QuestScreenImports.buildImportRequest(controller, false));

        assertTrue(controller.changeId(key, "renamed"));
        var valid = QuestScreenImports.importControlState(controller, false);
        assertTrue(valid.importEnabled());
        assertFalse(valid.diagnosticsEnabled().get(key));
        assertNotNull(QuestScreenImports.buildImportRequest(controller, false));

        var pending = QuestScreenImports.importControlState(controller, true);
        assertFalse(pending.importEnabled());
        assertNull(QuestScreenImports.buildImportRequest(controller, true));
    }

    @Test
    void resolvingDuplicateIdsRefreshesDiagnosticsForBothRows() throws Exception {
        Path firstDirectory = Files.createDirectories(directory.resolve("first"));
        Path secondDirectory = Files.createDirectories(directory.resolve("second"));
        Path first = firstDirectory.resolve("shared.json");
        Path second = secondDirectory.resolve("shared.json");
        Files.writeString(first, validQuest());
        Files.writeString(second, validQuest());
        QuestImportController controller = new QuestImportController();
        controller.addFiles(List.of(first, second));

        var duplicate = QuestScreenImports.importControlState(controller, false);
        assertFalse(duplicate.importEnabled());
        assertTrue(duplicate.diagnosticsEnabled().get(first.toString()));
        assertTrue(duplicate.diagnosticsEnabled().get(second.toString()));

        assertTrue(controller.changeId(first.toString(), "first_quest"));
        var resolved = QuestScreenImports.importControlState(controller, false);
        assertTrue(resolved.importEnabled());
        assertFalse(resolved.diagnosticsEnabled().get(first.toString()));
        assertFalse(resolved.diagnosticsEnabled().get(second.toString()));
    }

    private static String validQuest() {
        return """
            {"display":{"title":"Import test"},"tasks":{"task":{"type":"theseus:dummy","value":"test"}},"rewards":{"reward":{"type":"theseus:xp","amount":1}}}
            """;
    }
}
