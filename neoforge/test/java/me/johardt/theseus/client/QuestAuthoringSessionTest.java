package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestAuthoringSessionTest {
    @Test
    void composesTheAuthoringStateIntoOneLosslessDraft() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.begin(QuestDraft.create(null));
        session.id = "first_quest";
        session.title = "First quest";
        session.iconSize = 24;
        session.iconSizeTouched = true;
        JsonObject task = new JsonObject();
        task.addProperty("type", "theseus:dummy");
        session.tasks.add(new QuestAuthoringSession.TaskDraft("check", "theseus:dummy", task));

        QuestDraft draft = session.draft();

        assertEquals("first_quest", draft.id());
        assertEquals("First quest", draft.definition().title());
        assertEquals(24, draft.definition().display().iconSize());
        assertTrue(draft.snapshot().getAsJsonObject("tasks").has("check"));
        assertTrue(draft.isDirty());
    }

    @Test
    void copyDetachesNestedEditorState() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.begin(QuestDraft.create(null));
        JsonObject source = new JsonObject();
        source.addProperty("type", "theseus:item");
        session.rewards.add(new QuestAuthoringSession.RewardDraft("reward", "theseus:item", source));

        QuestAuthoringSession copy = session.copy();
        copy.rewards.getFirst().source.addProperty("item", "minecraft:diamond");
        copy.discard();

        assertFalse(session.rewards.getFirst().source.has("item"));
        assertTrue(session.open);
        assertFalse(copy.open);
    }

    @Test
    void openingExistingQuestKeepsParsedDefaultsOutOfTheDirtyDraft() {
        JsonObject source = JsonParser.parseString("""
            {
              "display":{"title":"Original","groups":{"Main":{"position":[12,34]}}},
              "tasks":{},"rewards":{},"custom":{"keep":true}
            }
            """).getAsJsonObject();
        QuestAuthoringSession session = new QuestAuthoringSession(16);

        session.beginExisting(QuestDefinition.parse("quest", source), source, "Main");

        assertFalse(session.draft().isDirty());
        assertEquals(12, session.x);
        assertEquals(34, session.y);
        assertTrue(session.draft().snapshot().has("custom"));

        session.beginNew("Side", 7, 9);
        JsonObject fresh = session.draft().snapshot();
        assertFalse(fresh.has("custom"));
        assertEquals(7, fresh.getAsJsonObject("display").getAsJsonObject("groups")
            .getAsJsonObject("Side").getAsJsonArray("position").get(0).getAsInt());
    }

    @Test
    void nestedTaskSaveAndCancelStayWithinTheSession() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.beginNew("Main", 4, 8);
        JsonObject composite = JsonParser.parseString("""
            {"type":"theseus:composite","amount":1,"tasks":{"check":{"type":"theseus:check","components":{}}}}
            """).getAsJsonObject();
        session.createTask(new QuestAuthoringSession.TaskDraft("composite", "theseus:composite", composite));

        session.editChildTask(0);
        session.editingTask.source.addProperty("title", "Discarded");
        assertTrue(session.closeTaskEditor());
        assertFalse(session.editingTask.source.getAsJsonObject("tasks").getAsJsonObject("check").has("title"));

        session.editChildTask(0);
        session.editingTask.source.addProperty("title", "Kept");
        assertTrue(session.saveTask(registries()));
        assertTrue(session.closeTaskEditor());
        assertTrue(session.saveTask(registries()));
        assertFalse(session.closeTaskEditor());

        assertEquals("Kept", session.draft().snapshot().getAsJsonObject("tasks")
            .getAsJsonObject("composite").getAsJsonObject("tasks")
            .getAsJsonObject("check").get("title").getAsString());
    }

    @Test
    void nestedRewardValidationAndSaveKeepTheParentDetachedUntilSaved() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.beginNew("Main", 0, 0);
        JsonObject selectable = JsonParser.parseString("""
            {"type":"theseus:selectable","amount":1,"rewards":{"item":{"type":"theseus:item","item":{"id":"minecraft:stone","count":1}}}}
            """).getAsJsonObject();
        session.createReward(new QuestAuthoringSession.RewardDraft("choice", "theseus:selectable", selectable));
        session.editNestedReward(0);
        session.editingNestedReward.source.getAsJsonObject("item").addProperty("count", 0);
        assertFalse(session.saveReward(true, registries()));
        assertEquals("Amount must be at least 1.", session.rewardEditorError);
        session.editingNestedReward.source.getAsJsonObject("item").addProperty("count", 2);
        assertTrue(session.saveReward(true, registries()));
        session.closeRewardEditor(true);
        assertTrue(session.rewards.isEmpty());
        assertTrue(session.saveReward(false, registries()));
        session.closeRewardEditor(false);

        assertEquals(2, session.draft().snapshot().getAsJsonObject("rewards")
            .getAsJsonObject("choice").getAsJsonObject("rewards")
            .getAsJsonObject("item").getAsJsonObject("item").get("count").getAsInt());
    }

    private static QuestDraftValidation.RegistryLookup registries() {
        return new QuestDraftValidation.RegistryLookup(
            (target, identifier) -> true,
            identifier -> true,
            identifier -> true
        );
    }
}
