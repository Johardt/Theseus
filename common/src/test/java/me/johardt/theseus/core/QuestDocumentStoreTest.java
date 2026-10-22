package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestDocumentStoreTest {
    @TempDir
    Path configDirectory;

    @Test
    void discoversDuplicateIdsWithoutDiscardingLosslessDocuments() throws Exception {
        Path first = configDirectory.resolve("theseus/quests/first/quest.json");
        Path second = configDirectory.resolve("theseus/quests/second/quest.json");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.writeString(first, "{\"custom\":{\"keep\":true},\"tasks\":{},\"rewards\":{}}");
        Files.writeString(second, "{\"tasks\":{},\"rewards\":{},\"display\":{\"title\":\"Second\"}}");

        QuestDocumentStore.Snapshot snapshot = new QuestDocumentStore(configDirectory).load();

        assertTrue(snapshot.conflictingPaths().containsKey("quest"));
        assertEquals(2, snapshot.conflictingPaths().get("quest").size());
        assertEquals(true, snapshot.documents().get("quest").root().getAsJsonObject("custom").get("keep").getAsBoolean());
    }

    @Test
    void rejectsExcessiveRawNestingThroughTheCheckedReadErrorPath() throws Exception {
        Path quest = configDirectory.resolve("theseus/quests/too_deep.json");
        Files.createDirectories(quest.getParent());
        int depth = JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1;
        Files.writeString(quest, "[".repeat(depth) + "0" + "]".repeat(depth));

        java.io.IOException exception = assertThrows(
            java.io.IOException.class,
            () -> new QuestDocumentStore(configDirectory).readQuest("too_deep")
        );

        assertTrue(exception.getMessage().contains("maximum raw depth"));
    }

    @Test
    void renamePropagatesDependenciesAndKeepsUnknownFields() throws Exception {
        Path source = configDirectory.resolve("theseus/quests/chapters/source.json");
        Path dependent = configDirectory.resolve("theseus/quests/chapters/dependent.json");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            {"display":{"title":"Source"},"tasks":{},"rewards":{},"custom":{"keep":"yes"}}
            """);
        Files.writeString(dependent, """
            {"display":{"title":"Dependent"},"tasks":{},"rewards":{},"dependencies":["source","other"]}
            """);

        QuestDocumentStore store = new QuestDocumentStore(configDirectory);
        store.saveQuest("source", "renamed", store.readQuest("source"));

        assertFalse(Files.exists(source));
        JsonObject renamed = store.readQuest("renamed");
        assertEquals("yes", renamed.getAsJsonObject("custom").get("keep").getAsString());
        assertEquals(List.of("renamed", "other"), store.readQuest("dependent").getAsJsonArray("dependencies").asList().stream().map(value -> value.getAsString()).toList());
    }

    @Test
    void chapterChangesPropagateWithMetadataInOneStorageOperation() throws Exception {
        Path quest = configDirectory.resolve("theseus/quests/quest.json");
        Path metadata = configDirectory.resolve("theseus/group_settings.json");
        Files.createDirectories(quest.getParent());
        Files.createDirectories(metadata.getParent());
        Files.writeString(quest, """
            {"display":{"groups":{"Old":{"position":[1,2]}}},"tasks":{},"rewards":{}}
            """);
        Files.writeString(metadata, """
            {"Old":{"icon":"minecraft:map","background":"","custom":"keep"}}
            """);

        QuestDocumentStore store = new QuestDocumentStore(configDirectory);
        store.updateChapters(
            List.of("Renamed"),
            Map.of("Renamed", new QuestCatalog.ChapterSettings("minecraft:chest", "example:background", true, 75)),
            new QuestDocumentStore.ChapterChange("Old", "Renamed")
        );

        JsonObject groups = store.readQuest("quest").getAsJsonObject("display").getAsJsonObject("groups");
        assertFalse(groups.has("Old"));
        assertEquals(1, groups.getAsJsonObject("Renamed").getAsJsonArray("position").get(0).getAsInt());
        JsonObject writtenMetadata = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        assertEquals("keep", writtenMetadata.getAsJsonObject("Renamed").get("custom").getAsString());
        assertEquals("minecraft:chest", writtenMetadata.getAsJsonObject("Renamed").get("icon").getAsString());
        assertEquals("Renamed\n", Files.readString(configDirectory.resolve("theseus/groups.txt")));
    }

    @Test
    void dependencyRemovalIsRollbackSafeBeforeAnyFileIsChanged() throws Exception {
        Path quest = configDirectory.resolve("theseus/quests/quest.json");
        Files.createDirectories(quest.getParent());
        Files.writeString(quest, "{\"tasks\":{},\"rewards\":{}}");
        String before = Files.readString(quest);

        QuestDocumentStore store = new QuestDocumentStore(configDirectory);
        try {
            store.updateDependencies("missing", Set.of("quest"));
        } catch (java.io.IOException expected) {
            // The failed lookup is intentionally before transaction staging.
        }

        assertEquals(before, Files.readString(quest));
    }

    @Test
    void returnsNormalizedRelativePathsForRootAndNestedQuestFiles() throws Exception {
        Path quests = configDirectory.resolve("theseus/quests");
        Path root = quests.resolve("root.json");
        Path nested = quests.resolve("chapter/nested.json");
        Files.createDirectories(nested.getParent());
        Files.writeString(root, "{\"tasks\":{},\"rewards\":{}}");
        Files.writeString(nested, "{\"tasks\":{},\"rewards\":{}}");

        QuestDocumentStore store = QuestDocumentStore.forQuestDirectory(quests);

        assertEquals("root.json", store.relativeQuestPath("root"));
        assertEquals("chapter/nested.json", store.relativeQuestPath("nested"));
    }

    @Test
    void rejectsMissingDuplicateAndSymlinkEscapingQuestFiles() throws Exception {
        Path quests = configDirectory.resolve("theseus/quests");
        Files.createDirectories(quests.resolve("one"));
        Files.createDirectories(quests.resolve("two"));
        Files.writeString(quests.resolve("one/duplicate.json"), "{}");
        Files.writeString(quests.resolve("two/duplicate.json"), "{}");
        Files.writeString(quests.resolve("inside.json"), "{}");
        Path outside = configDirectory.resolve("outside.json");
        Files.writeString(outside, "{}");
        Files.createSymbolicLink(quests.resolve("escape.json"), outside);

        QuestDocumentStore store = QuestDocumentStore.forQuestDirectory(quests);

        assertThrows(java.io.IOException.class, () -> store.relativeQuestPath("missing"));
        assertThrows(java.io.IOException.class, () -> store.relativeQuestPath("duplicate"));
        assertThrows(java.io.IOException.class, () -> store.relativeQuestPath("escape"));
        assertThrows(java.io.IOException.class, () -> store.relativeQuestPath("../inside"));
    }
}
