package me.johardt.theseus.core;

import me.johardt.theseus.client.QuestImportController;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestImportControllerTest {
    @Test
    void failedEntriesCanBeRemovedAndValidEntriesSubmitted() throws Exception {
        Path good = Files.createTempFile("theseus-good", ".json");
        Path bad = Files.createTempFile("theseus-bad", ".json");
        try {
            Files.writeString(good, "{\"display\":{\"title\":\"Good\"},\"tasks\":{},\"rewards\":{}}");
            Files.writeString(bad, "not json");
            var controller = new me.johardt.theseus.client.QuestImportController();
            controller.addFiles(List.of(good, bad));
            assertFalse(controller.canSubmit());
            controller.remove(bad.toString());
            assertTrue(controller.canSubmit());
            assertEquals("Good", controller.request().getAsJsonObject("files").entrySet().iterator().next().getValue().getAsJsonObject().getAsJsonObject("display").get("title").getAsString());
        } finally { Files.deleteIfExists(good); Files.deleteIfExists(bad); }
    }

    @Test
    void duplicateIdsAreReportedOnEveryConflictingRow() throws Exception {
        Path firstDirectory = Files.createTempDirectory("theseus-first");
        Path secondDirectory = Files.createTempDirectory("theseus-second");
        Path first = firstDirectory.resolve("shared.json");
        Path second = secondDirectory.resolve("shared.json");
        String json = "{\"display\":{\"title\":\"Quest\"},\"tasks\":{},\"rewards\":{}}";
        try {
            Files.writeString(first, json);
            Files.writeString(second, json);
            QuestImportController controller = new QuestImportController();
            controller.addFiles(List.of(first, second));

            assertFalse(controller.canSubmit());
            assertTrue(controller.entries().stream().allMatch(entry -> entry.diagnostics().stream().anyMatch(d -> d.code().equals("duplicate_import_id"))));
            assertTrue(controller.summary().contains(first.toString()));
            assertTrue(controller.summary().contains(second.toString()));
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            Files.deleteIfExists(firstDirectory);
            Files.deleteIfExists(secondDirectory);
        }
    }

    @Test
    void serverDiagnosticsReturnToMatchingFile() throws Exception {
        Path file = Files.createTempFile("theseus-server", ".json");
        try {
            Files.writeString(file, "{\"display\":{\"title\":\"Quest\"},\"tasks\":{},\"rewards\":{}}");
            QuestImportController controller = new QuestImportController();
            controller.addFiles(List.of(file));
            String id = controller.entries().getFirst().id();
            controller.applyServerDiagnostics(List.of(new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR, "unknown_item", id, "tasks.item", "Unknown item", "Choose a registered item"
            )));
            assertTrue(controller.entries().getFirst().diagnostics().stream().anyMatch(d -> d.code().equals("unknown_item")));
            assertFalse(controller.canSubmit());
        } finally { Files.deleteIfExists(file); }
    }

    @Test
    void idFieldCanBeEditedThroughInvalidIntermediateValues() throws Exception {
        Path file = Files.createTempFile("theseus-edit", ".json");
        try {
            Files.writeString(file, "{\"display\":{\"title\":\"Quest\"},\"tasks\":{},\"rewards\":{}}");
            QuestImportController controller = new QuestImportController();
            controller.addFiles(List.of(file));
            assertTrue(controller.changeId(file.toString(), ""));
            assertFalse(controller.canSubmit());
            assertTrue(controller.entries().getFirst().diagnostics().stream().anyMatch(d -> d.code().equals("invalid_quest_id")));
            assertTrue(controller.changeId(file.toString(), "renamed"));
            assertTrue(controller.canSubmit());
        } finally { Files.deleteIfExists(file); }
    }
}
