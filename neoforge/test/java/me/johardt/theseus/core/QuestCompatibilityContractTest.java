package me.johardt.theseus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCompatibilityContractTest {
    private static final List<String> FIXTURES = List.of(
        "legacy_aliases",
        "current_builtins",
        "unknown_extensions",
        "heracles_namespace"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path temp;

    @Test
    void compatibilityCorpusSurvivesLoadNoOpSaveCopyImportAndExport() throws Exception {
        QuestDocumentStore saved = new QuestDocumentStore(temp.resolve("saved"));
        Path importDirectory = temp.resolve("imported/theseus/quests");

        for (String fixture : FIXTURES) {
            JsonObject source = fixture(fixture);
            QuestDefinition loaded = QuestDefinition.parse(fixture, source);
            QuestDraft draft = QuestDraft.open(fixture, source);

            assertFalse(draft.isDirty(), fixture);
            assertTrue(loaded.issues().stream().noneMatch(issue -> issue.severity() == QuestDefinition.Severity.ERROR), fixture);
            assertEquals(source, draft.copy().snapshot(), fixture);

            JsonObject mutation = draft.updateMutation();
            assertEquals(0, mutation.getAsJsonArray("changed_paths").size(), fixture);
            saved.createQuest(fixture, source);
            JsonObject latest = saved.readQuest(fixture);
            JsonObject merged = QuestDraft.merge(
                latest,
                mutation.getAsJsonObject("document"),
                mutation.getAsJsonArray("changed_paths")
            );
            saved.saveQuest(fixture, fixture, merged);
            assertEquals(source, saved.readQuest(fixture), fixture + " no-op save");

            Path exportedFile = temp.resolve(fixture + "_export.json");
            Files.writeString(exportedFile, GSON.toJson(draft.transferSnapshot()), StandardCharsets.UTF_8);
            String exportedText = Files.readString(exportedFile, StandardCharsets.UTF_8);
            JsonObject exported = JsonParser.parseString(exportedText).getAsJsonObject();
            assertEquals(source, exported, fixture + " export");

            saved.transferQuest(fixture, fixture + "_copy", exported, false);
            assertEquals(source, saved.readQuest(fixture + "_copy"), fixture + " copy");

            Map<String, String> importFiles = new LinkedHashMap<>();
            importFiles.put(fixture + ".json", exportedText);
            QuestImportBatch.FileResult preflight = QuestImportBatch.preflight(importFiles).getFirst();
            assertTrue(preflight.valid(), () -> fixture + " import diagnostics: " + preflight.diagnostics());
            QuestImportBatch.commit(importDirectory, Map.of(fixture, preflight.root()));
            assertEquals(source, QuestDocumentStore.forQuestDirectory(importDirectory).readQuest(fixture), fixture + " import");
        }

        QuestCatalog reloaded = QuestCatalog.load(temp.resolve("imported"));
        assertEquals(FIXTURES.size(), reloaded.quests().size());
        for (String fixture : FIXTURES) {
            assertEquals(fixture(fixture), reloaded.rawQuest(fixture), fixture + " catalog load");
        }
    }

    @Test
    void recognizedLegacyAliasesLoadAsBuiltInsAndStaySpelledTheSameOnNoOp() throws Exception {
        JsonObject source = fixture("legacy_aliases");
        QuestDefinition definition = QuestDefinition.parse("legacy_aliases", source);
        QuestDraft draft = QuestDraft.open("legacy_aliases", source);

        assertEquals(5, definition.tasks().size());
        assertEquals(2, definition.rewards().size());
        assertTrue(definition.issues().isEmpty(), () -> definition.issues().toString());
        assertEquals("Legacy field spellings", definition.display().title());
        assertEquals("minecraft:emerald", definition.display().icon().item());
        assertEquals(List.of("welcome"), List.copyOf(definition.dependencies()));
        assertEquals("theseus:check", definition.tasks().get("check").type());
        assertTrue(draft.snapshot().getAsJsonObject("settings").has("unlock_notification"));
        assertFalse(draft.snapshot().getAsJsonObject("settings").has("unlockNotification"));
    }

    @Test
    void clientRuntimeAndSyncEnvelopeFieldsAreRemovedFromTransferSnapshots() throws Exception {
        JsonObject source = fixture("current_builtins");
        source.add("progress", JsonParser.parseString("{\"dummy\":1}"));
        source.addProperty("complete", true);
        source.add("claimed_rewards", JsonParser.parseString("[\"item\"]"));
        source.add("__chapters", JsonParser.parseString("{\"order\":[\"Main\"]}"));
        source.add("__editor_types", JsonParser.parseString("{\"tasks\":[]}"));

        QuestDraft draft = QuestDraft.fromClientSnapshot("current_builtins", source);

        assertEquals(fixture("current_builtins"), draft.transferSnapshot());
        assertFalse(draft.snapshot().has("progress"));
        assertFalse(draft.snapshot().has("__editor_types"));
    }

    @Test
    void oldHeraclesIdentifiersRemainRawButAreNotSilentlyMigrated() throws Exception {
        JsonObject source = fixture("heracles_namespace");
        QuestDefinition definition = QuestDefinition.parse("heracles_namespace", source);

        assertEquals(QuestDefinition.TaskKind.UNSUPPORTED, definition.tasks().get("old_task").kind());
        assertEquals(QuestDefinition.RewardKind.UNSUPPORTED, definition.rewards().get("old_reward").kind());
        assertEquals(source, QuestDraft.open(source).transferSnapshot());
    }

    private static JsonObject fixture(String name) throws Exception {
        String resource = "/fixtures/compatibility/" + name + ".json";
        try (var stream = QuestCompatibilityContractTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Missing fixture " + resource);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
