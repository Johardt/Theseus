package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.johardt.theseus.core.QuestCatalog;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Acceptance coverage for a realistic multi-chapter quest document tree. */
class Beta8LargeTreeAcceptanceTest {
    private static final int CHAPTER_COUNT = 32;
    private static final int QUESTS_PER_CHAPTER = 22;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path tempDirectory;

    @Test
    void largeGeneratedTreeLoadsRoundTripsAndFeedsGraphChapterAndMinimapModels() throws Exception {
        Path configDirectory = tempDirectory.resolve("config");
        List<String> chapters = writeFixture(configDirectory);

        QuestCatalog catalog = QuestCatalog.load(configDirectory);

        assertEquals(CHAPTER_COUNT * QUESTS_PER_CHAPTER, catalog.quests().size());
        assertTrue(catalog.issues().isEmpty(), () -> catalog.issues().toString());
        assertEquals(chapters, catalog.groupOrder());
        assertEquals(CHAPTER_COUNT, catalog.chapterSettings().size());
        assertEquals(
            QuestDefinition.Visibility.LOCKED,
            catalog.quests().get("chapter_00_quest_00").settings().hiddenUntil()
        );
        assertTrue(catalog.quests().get("chapter_00_quest_00").dependencies().isEmpty());
        assertEquals(
            "chapter_00_quest_21",
            catalog.quests().get("chapter_01_quest_00").dependencies().iterator().next()
        );
        assertTrue(catalog.documents().readQuest("chapter_00_quest_00").get("pinned").getAsBoolean());

        QuestDocumentStore.Snapshot before = catalog.documents().load();
        Map<String, JsonObject> originalRoots = new LinkedHashMap<>();
        before.documents().forEach((id, document) -> originalRoots.put(id, document.root()));
        catalog.documents().writeQuest(
            "chapter_00_quest_00",
            originalRoots.get("chapter_00_quest_00")
        );
        catalog.documents().updateChapters(
            chapters,
            catalog.chapterSettings(),
            QuestDocumentStore.ChapterChange.none()
        );

        QuestCatalog reloaded = QuestCatalog.load(configDirectory);
        assertEquals(catalog.groupOrder(), reloaded.groupOrder());
        assertEquals(catalog.chapterSettings(), reloaded.chapterSettings());
        assertEquals(
            originalRoots.get("chapter_00_quest_00"),
            reloaded.documents().readQuest("chapter_00_quest_00")
        );
        assertEquals(originalRoots.size(), reloaded.quests().size());

        assertGraphGeometry(reloaded);
        assertChapterScrolling();
    }

    private static List<String> writeFixture(Path configDirectory) throws Exception {
        Path theseusDirectory = configDirectory.resolve("theseus");
        Path questsDirectory = theseusDirectory.resolve("quests");
        Files.createDirectories(questsDirectory);

        List<String> chapters = new ArrayList<>();
        JsonObject settings = new JsonObject();
        for (int chapter = 0; chapter < CHAPTER_COUNT; chapter++) {
            String name = "chapter_%02d_with_a_long_stable_name".formatted(chapter);
            chapters.add(name);
            JsonObject chapterSettings = new JsonObject();
            chapterSettings.addProperty("icon", chapter % 2 == 0 ? "minecraft:map" : "minecraft:chest");
            chapterSettings.addProperty("background", "");
            chapterSettings.addProperty("iconEnabled", chapter % 3 != 0);
            chapterSettings.addProperty("backgroundOpacity", 40 + chapter % 61);
            settings.add(name, chapterSettings);

            for (int questIndex = 0; questIndex < QUESTS_PER_CHAPTER; questIndex++) {
                String id = "chapter_%02d_quest_%02d".formatted(chapter, questIndex);
                JsonObject root = quest(chapter, questIndex, name);
                Files.writeString(
                    questsDirectory.resolve(id + ".json"),
                    JSON.toJson(root),
                    StandardCharsets.UTF_8
                );
            }
        }
        Files.writeString(
            theseusDirectory.resolve("groups.txt"),
            String.join("\n", chapters) + "\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(
            theseusDirectory.resolve("group_settings.json"),
            JSON.toJson(settings),
            StandardCharsets.UTF_8
        );
        return chapters;
    }

    private static JsonObject quest(int chapter, int questIndex, String group) {
        String id = "chapter_%02d_quest_%02d".formatted(chapter, questIndex);
        JsonObject root = new JsonObject();

        JsonObject display = new JsonObject();
        JsonObject icon = new JsonObject();
        icon.addProperty("type", "theseus:item");
        icon.addProperty("item", questIndex % 2 == 0 ? "minecraft:diamond" : "minecraft:emerald");
        icon.addProperty("count", 1 + questIndex % 4);
        icon.add("components", new JsonObject());
        display.add("icon", icon);
        display.addProperty("icon_background", questIndex % 4 == 0
            ? "theseus:textures/gui/quest_backgrounds/diamonds.png"
            : "theseus:textures/gui/quest_backgrounds/default.png");
        display.addProperty("icon_size", 8 + (questIndex % 3) * 28);
        display.addProperty("title", "Chapter " + chapter + " quest " + questIndex + " with a deliberately long title");
        display.addProperty("subtitle", "A stable beta acceptance fixture");
        JsonArray description = new JsonArray();
        description.add("A rich description with enough text to exercise wrapping and persistence.");
        description.add("Coordinates, dependencies, icons, and unknown metadata must survive the round trip.");
        display.add("description", description);
        JsonObject groups = new JsonObject();
        JsonObject placement = new JsonObject();
        JsonArray position = new JsonArray();
        position.add((questIndex - 10) * 96 + chapter * 7);
        position.add((chapter % 2 == 0 ? questIndex : -questIndex) * 72 - chapter * 5);
        placement.add("position", position);
        groups.add(group, placement);
        display.add("groups", groups);
        root.add("display", display);

        JsonObject task = new JsonObject();
        if (questIndex % 3 == 0) {
            task.addProperty("type", "theseus:item");
            task.addProperty("item", "minecraft:diamond");
            task.addProperty("amount", 2);
        } else if (questIndex % 3 == 1) {
            task.addProperty("type", "theseus:check");
        } else {
            task.addProperty("type", "theseus:xp");
            task.addProperty("amount", 3);
        }
        JsonObject tasks = new JsonObject();
        tasks.add("primary", task);
        root.add("tasks", tasks);

        JsonObject reward = new JsonObject();
        if (questIndex % 4 == 0) {
            reward.addProperty("type", "theseus:item");
            reward.addProperty("item", questIndex % 8 == 0 ? "minecraft:emerald" : "minecraft:gold_ingot");
        } else {
            reward.addProperty("type", "theseus:xp");
            reward.addProperty("amount", 2);
        }
        JsonObject rewards = new JsonObject();
        rewards.add("primary", reward);
        root.add("rewards", rewards);

        JsonObject questSettings = new JsonObject();
        questSettings.addProperty("hidden", "locked");
        questSettings.addProperty("showDependencyArrow", true);
        root.add("settings", questSettings);

        JsonArray dependencies = new JsonArray();
        if (questIndex > 0) {
            dependencies.add("chapter_%02d_quest_%02d".formatted(chapter, questIndex - 1));
        } else if (chapter > 0) {
            dependencies.add("chapter_%02d_quest_%02d".formatted(chapter - 1, QUESTS_PER_CHAPTER - 1));
        }
        root.add("dependencies", dependencies);
        root.addProperty("pinned", chapter == 0 && questIndex == 0);
        JsonObject custom = new JsonObject();
        custom.addProperty("fixtureVersion", 8);
        custom.addProperty("longKey", "preserve this editor-unknown field");
        root.add("custom_fixture_data", custom);
        return root;
    }

    private static void assertGraphGeometry(QuestCatalog catalog) {
        List<QuestSurfaceLayout.QuestNode> nodes = new ArrayList<>();
        for (QuestDefinition quest : catalog.quests().values()) {
            QuestDefinition.GroupDisplay position = quest.position(catalog.groupOrder().stream()
                .filter(quest.display().groups()::containsKey)
                .findFirst()
                .orElse(catalog.groupOrder().getFirst()));
            nodes.add(new QuestSurfaceLayout.QuestNode(
                quest.id(),
                position.x(),
                position.y(),
                quest.display().iconSize(),
                quest.display().iconBackground()
            ));
        }

        QuestGraphLayout.CanvasBounds canvas = new QuestGraphLayout.CanvasBounds(0, 0, 960, 540);
        QuestSurfaceLayout.Layout surface = QuestSurfaceLayout.layout(
            nodes,
            canvas,
            QuestGraphLayout.ViewportState.DEFAULT
        );
        for (QuestSurfaceLayout.Node node : surface.nodes()) {
            assertTrue(node.iconBounds().width() >= 8 && node.iconBounds().width() <= 64);
            assertTrue(node.bounds().width() > 0);
            assertTrue(Double.isFinite(node.centerX()));
        }
        QuestGraphLayout.WorldBounds world = surface.worldBounds(24);
        QuestGraphLayout.ViewportState fit = QuestGraphLayout.fitViewport(canvas, world);
        assertTrue(Double.isFinite(fit.zoom()));
        assertTrue(fit.zoom() >= QuestGraphLayout.MIN_ZOOM);

        QuestMinimap.Mapping mapping = QuestMinimap.mapping(
            world,
            new QuestMinimap.MapBounds(10, 20, 220, 140)
        );
        assertTrue(Double.isFinite(mapping.scale()));
        QuestGraphLayout.Point mapCenter = QuestMinimap.worldToMap(mapping, world.centerX(), world.centerY());
        QuestGraphLayout.Point worldCenter = QuestMinimap.mapToWorld(mapping, mapCenter.x(), mapCenter.y());
        assertEquals(world.centerX(), worldCenter.x(), 0.000001);
        assertEquals(world.centerY(), worldCenter.y(), 0.000001);
        assertNotNull(QuestMinimap.viewportRectangle(mapping, QuestGraphLayout.visibleWorld(canvas, fit)));
        assertFalse(world.width() == 0 || world.height() == 0);
    }

    private static void assertChapterScrolling() {
        ChapterListState state = new ChapterListState();
        state.setViewport(0, 192, 24);
        state.setChapterCount(CHAPTER_COUNT);
        state.ensureVisible(CHAPTER_COUNT - 1);
        assertEquals(CHAPTER_COUNT - state.visibleCapacity(), state.firstVisibleRow());
        assertEquals(CHAPTER_COUNT - 1, state.visibleIndices().getLast());
        state.ensureVisible(0);
        assertEquals(0, state.firstVisibleRow());
        assertEquals(0, state.visibleIndices().getFirst());
    }
}
