package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCatalogDependencyTest {
    @TempDir
    Path configDirectory;

    @Test
    void writesSortedDependenciesWithoutReplacingOtherQuestData() throws Exception {
        Path quest = configDirectory.resolve("theseus/quests/chapter/dependent.json");
        Files.createDirectories(quest.getParent());
        Files.writeString(quest, """
            {
              "display": {"title": "Dependent"},
              "tasks": {"check": {"type": "theseus:check"}}
            }
            """);

        QuestCatalog.writeDependencies(
            configDirectory,
            "dependent",
            Set.of("second", "first")
        );

        JsonObject written = JsonParser.parseString(Files.readString(quest)).getAsJsonObject();
        assertEquals("Dependent", written.getAsJsonObject("display").get("title").getAsString());
        assertEquals("theseus:check", written.getAsJsonObject("tasks")
            .getAsJsonObject("check").get("type").getAsString());
        assertEquals("first", written.getAsJsonArray("dependencies").get(0).getAsString());
        assertEquals("second", written.getAsJsonArray("dependencies").get(1).getAsString());
    }

    @Test
    void detectsDirectAndTransitiveCycles() {
        Map<String, QuestDefinition> quests = Map.of(
            "root", quest("root"),
            "middle", quest("middle", "root"),
            "leaf", quest("leaf", "middle")
        );

        assertTrue(QuestCatalog.wouldCreateCycle(quests, "leaf", "root"));
        assertTrue(QuestCatalog.wouldCreateCycle(quests, "middle", "root"));
        assertFalse(QuestCatalog.wouldCreateCycle(quests, "root", "leaf"));
        assertEquals(java.util.List.of("root", "leaf", "middle", "root"), QuestCatalog.dependencyCyclePath(quests, "leaf", "root"));
    }

    @Test
    void validatesMissingReferencesAndReturnsExactCyclePath() {
        Map<String, QuestDefinition> quests = Map.of(
            "alpha", quest("alpha", "beta"),
            "beta", quest("beta", "alpha"),
            "orphan", quest("orphan", "missing")
        );

        var issues = QuestCatalog.validateDependencies(quests);
        assertTrue(issues.stream().anyMatch(issue -> issue.message().equals("Missing quest missing")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().equals("Dependency cycle: alpha → beta → alpha")));
    }

    @Test
    void loadsPersistentChapterOrderAndAppearance() throws Exception {
        Path theseus = configDirectory.resolve("theseus");
        Path quest = theseus.resolve("quests/chapter/quest.json");
        Files.createDirectories(quest.getParent());
        Files.writeString(quest, """
            {"display":{"groups":{"First":{"position":[0,0]}}}}
            """);
        Files.writeString(theseus.resolve("groups.txt"), "Second\nFirst\n");
        Files.writeString(theseus.resolve("group_settings.json"), """
            {"Second":{"icon":"minecraft:chest","background":"example:textures/chapter.png"}}
            """);

        var order = QuestCatalog.loadGroupOrder(theseus.resolve("groups.txt"));
        var settings = QuestCatalog.loadChapterSettings(theseus.resolve("group_settings.json"));

        assertEquals(java.util.List.of("Second", "First"), order);
        assertEquals("minecraft:chest", settings.get("Second").icon());
        assertEquals("example:textures/chapter.png", settings.get("Second").background());
    }

    @Test
    void retainsLosslessQuestDocumentsAfterCatalogLoad() throws Exception {
        Path quest = configDirectory.resolve("theseus/quests/chapter/quest.json");
        Files.createDirectories(quest.getParent());
        Files.writeString(quest, """
            {"display":{"title":"Quest"},"tasks":{},"rewards":{},"custom":{"keep":true}}
            """);

        QuestCatalog catalog = QuestCatalog.load(configDirectory);
        Files.delete(quest);

        JsonObject retained = catalog.rawQuest("quest");
        assertTrue(retained.getAsJsonObject("custom").get("keep").getAsBoolean());
        retained.addProperty("mutated", true);
        assertFalse(catalog.rawQuest("quest").has("mutated"));
    }

    private static QuestDefinition quest(String id, String... dependencies) {
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray values = new com.google.gson.JsonArray();
        for (String dependency : dependencies) values.add(dependency);
        root.add("dependencies", values);
        return QuestDefinition.parse(id, root);
    }
}
