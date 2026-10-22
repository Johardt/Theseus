package me.johardt.theseus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestScreenActionsTest {

    @Test
    void moveRequestKeepsItsSourceIdForSameAndDifferentChapters() {
        for (String chapter : Set.of("Main", "Other")) {
            JsonObject quest = new JsonObject();
            quest.addProperty("title", "Moved quest");

            JsonObject request = QuestScreenActions.buildClipboardPasteRequest(
                "existing_quest",
                chapter,
                false,
                true,
                null,
                Set.of("existing_quest"),
                true,
                quest,
                12,
                20,
                null,
                null
            );

            assertNotNull(request);
            assertEquals("existing_quest", request.get("source_id").getAsString());
            assertEquals("existing_quest", request.get("id").getAsString());
            assertTrue(request.get("move").getAsBoolean());
            assertEquals(chapter, request.get("chapter").getAsString());
            assertEquals(12, request.get("x").getAsInt());
            assertEquals(20, request.get("y").getAsInt());
            assertEquals(quest, request.getAsJsonObject("quest"));
        }
    }

    @Test
    void copyStillRejectsExistingIdsAndAcceptsUnusedIds() {
        JsonObject quest = new JsonObject();

        assertNull(QuestScreenActions.buildClipboardPasteRequest(
            "existing_quest",
            "Main",
            false,
            false,
            "existing_quest",
            Set.of("existing_quest"),
            true,
            quest,
            0,
            0,
            null,
            null
        ));

        JsonObject request = QuestScreenActions.buildClipboardPasteRequest(
            "existing_quest",
            "Main",
            false,
            false,
            "new_quest",
            Set.of("existing_quest"),
            true,
            quest,
            0,
            0,
            null,
            null
        );

        assertNotNull(request);
        assertEquals("new_quest", request.get("id").getAsString());
        assertFalse(request.get("move").getAsBoolean());
    }

    @Test
    void missingSourceRejectsMovesAndCopies() {
        JsonObject quest = new JsonObject();

        assertNull(QuestScreenActions.buildClipboardPasteRequest(
            "missing_quest",
            "Main",
            false,
            true,
            null,
            Set.of(),
            false,
            quest,
            0,
            0,
            null,
            null
        ));
        assertNull(QuestScreenActions.buildClipboardPasteRequest(
            "missing_quest",
            "Main",
            false,
            false,
            "copy_quest",
            Set.of(),
            false,
            quest,
            0,
            0,
            null,
            null
        ));
    }
}
