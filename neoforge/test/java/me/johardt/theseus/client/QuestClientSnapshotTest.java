package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestClientSnapshotTest {
    @Test
    void indexChapterAndRefreshShareOneRawAndParsedLifecycle() {
        QuestClientSnapshot snapshots = new QuestClientSnapshot();

        assertEquals(QuestClientSnapshot.Kind.INDEX, snapshots.accept(index()));
        assertEquals(2, snapshots.chapters().size());
        assertEquals("Index title", snapshots.quests().getFirst().definition().title());
        assertTrue(snapshots.requestChapter("Main"));
        assertFalse(snapshots.requestChapter("Main"));

        assertEquals(QuestClientSnapshot.Kind.CHAPTER, snapshots.accept(chapter()));
        assertEquals("Chapter title", snapshots.quests().getFirst().definition().title());
        assertEquals(3, snapshots.quests().getFirst().progress().get("check"));
        assertTrue(snapshots.quests().getFirst().pinned());
        assertEquals("Chapter title", snapshots.raw().getAsJsonObject("quest")
            .getAsJsonObject("display").get("title").getAsString());
        assertTrue(snapshots.raw().getAsJsonObject("quest").get("pinned").getAsBoolean());
        assertEquals("Main", snapshots.raw().getAsJsonObject("__chapters")
            .getAsJsonArray("order").get(0).getAsString());
        assertFalse(snapshots.requestChapter("Main"));
        assertTrue(snapshots.requestChapter("Side"));

        assertEquals(QuestClientSnapshot.Kind.INDEX, snapshots.accept(emptyIndex()));
        assertTrue(snapshots.quests().isEmpty());
        assertFalse(snapshots.raw().has("quest"));
        assertTrue(snapshots.requestChapter("Side"));
    }

    @Test
    void fullSnapshotMarksAllKnownChaptersLoaded() {
        JsonObject full = index();
        full.remove("__snapshot_kind");
        QuestClientSnapshot snapshots = new QuestClientSnapshot();

        assertEquals(QuestClientSnapshot.Kind.FULL, snapshots.accept(full));

        assertFalse(snapshots.requestChapter("Main"));
        assertFalse(snapshots.requestChapter("Side"));
    }

    private static JsonObject index() {
        return JsonParser.parseString("""
            {
              "__snapshot_kind":"index",
              "__chapters":{"order":["Main","Side"],"settings":{}},
              "__editor_types":{"tasks":["theseus:check"],"rewards":[],"icons":[]},
              "quest":{
                "display":{"title":"Index title","groups":{"Main":{"position":[1,2]}}},
                "tasks":{},"rewards":{},"unlocked":true,"complete":false,"claimed":false
              }
            }
            """).getAsJsonObject();
    }

    private static JsonObject chapter() {
        return JsonParser.parseString("""
            {
              "__snapshot_kind":"chapter","__chapter":"Main",
              "__chapters":{"order":["Wrong"],"settings":{}},
              "quest":{
                "display":{"title":"Chapter title","groups":{"Main":{"position":[1,2]}}},
                "tasks":{"check":{"type":"theseus:check","components":{}}},"rewards":{},
                "progress":{"check":3},"unlocked":true,"complete":true,"claimed":false,"pinned":true
              }
            }
            """).getAsJsonObject();
    }

    private static JsonObject emptyIndex() {
        return JsonParser.parseString("""
            {"__snapshot_kind":"index","__chapters":{"order":["Side"],"settings":{}}}
            """).getAsJsonObject();
    }
}
