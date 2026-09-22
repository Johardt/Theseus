package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestEditorDraftLogicTest {
    private static final QuestDraftValidation.RegistryLookup REGISTRIES =
        new QuestDraftValidation.RegistryLookup(
            (target, identifier) -> true,
            identifier -> true,
            identifier -> true
        );

    @Test
    void compositeTaskDraftStartsWithOneNestedCheckAndAvoidsSiblingIds() {
        var existing = new QuestAuthoringSession.TaskDraft(
            "composite",
            "theseus:composite",
            new JsonObject()
        );
        QuestEditorCatalog.TaskChoice choice = new QuestEditorCatalog.TaskChoice(
            "theseus:composite",
            "Composite",
            null,
            true
        );

        QuestAuthoringSession.TaskDraft draft = QuestEditorCatalog.createTaskDraft(
            choice,
            List.of(existing)
        );

        assertEquals("composite_2", draft.id);
        assertEquals(
            JsonParser.parseString("""
                {
                  "type":"theseus:composite",
                  "title":"Composite",
                  "amount":1,
                  "tasks":{"check":{"type":"theseus:check","components":{}}}
                }
                """).getAsJsonObject(),
            draft.source
        );
    }

    @Test
    void rewardDraftUsesStructuredItemDataAndUniqueSiblingId() {
        var existing = new QuestAuthoringSession.RewardDraft(
            "item",
            "theseus:item",
            new JsonObject()
        );
        QuestEditorCatalog.RewardChoice choice = new QuestEditorCatalog.RewardChoice(
            "theseus:item",
            "Item",
            null
        );

        QuestAuthoringSession.RewardDraft draft = QuestEditorCatalog.createRewardDraft(
            choice,
            List.of(existing)
        );

        assertEquals("item_2", draft.id);
        assertEquals(
            JsonParser.parseString("""
                {"type":"theseus:item","title":"Item","item":{"id":"minecraft:stone","count":1}}
                """).getAsJsonObject(),
            draft.source
        );
    }

    @Test
    void taskValidationNormalizesStructuredJsonBeforeParsing() {
        JsonObject source = JsonParser.parseString("""
            {"type":"theseus:check","components":"{\\"minecraft:stone\\":{}}"}
            """).getAsJsonObject();
        QuestAuthoringSession.TaskDraft task = new QuestAuthoringSession.TaskDraft(
            "check",
            "theseus:check",
            source
        );

        String error = QuestDraftValidation.validateTaskDraft(task, List.of(), -1, REGISTRIES);

        assertEquals("", error);
        assertTrue(task.source.get("components").isJsonObject());
        assertEquals(
            JsonParser.parseString("{\"minecraft:stone\":{}}").getAsJsonObject(),
            task.source.getAsJsonObject("components")
        );
    }

    @Test
    void taskValidationExplainsMalformedStructuredJson() {
        JsonObject source = new JsonObject();
        source.addProperty("type", "theseus:check");
        source.addProperty("components", "not json");
        QuestAuthoringSession.TaskDraft task = new QuestAuthoringSession.TaskDraft(
            "check",
            "theseus:check",
            source
        );

        assertEquals(
            "Components must be valid JSON.",
            QuestDraftValidation.validateTaskDraft(task, List.of(), -1, REGISTRIES)
        );
        assertEquals("not json", task.source.get("components").getAsString());
    }

    @Test
    void nestedTaskAndRewardHelpersRoundTripDetachedJson() {
        QuestAuthoringSession.TaskDraft parentTask = new QuestAuthoringSession.TaskDraft(
            "composite",
            "theseus:composite",
            JsonParser.parseString("""
                {"tasks":{"first":{"type":"theseus:check","components":{}}}}
                """).getAsJsonObject()
        );
        QuestAuthoringSession.RewardDraft parentReward = new QuestAuthoringSession.RewardDraft(
            "selectable",
            "theseus:selectable",
            JsonParser.parseString("""
                {"rewards":{"first":{"type":"theseus:item","item":{"id":"minecraft:diamond","count":2}}}}
                """).getAsJsonObject()
        );

        var tasks = QuestDraftValidation.nestedTasks(parentTask);
        var rewards = QuestDraftValidation.nestedRewards(parentReward);
        tasks.getFirst().source.addProperty("title", "Detached task");
        rewards.getFirst().source.getAsJsonObject("item").addProperty("count", 3);
        QuestDraftValidation.setNestedTasks(parentTask, tasks);
        QuestDraftValidation.setNestedRewards(parentReward, rewards);

        assertEquals("theseus:check", parentTask.source.getAsJsonObject("tasks")
            .getAsJsonObject("first").get("type").getAsString());
        assertEquals(3, parentReward.source.getAsJsonObject("rewards")
            .getAsJsonObject("first").getAsJsonObject("item").get("count").getAsInt());
        assertTrue(parentTask.source.getAsJsonObject("tasks").getAsJsonObject("first").has("title"));
    }

    @Test
    void rewardValidationReportsNestedSelectableRewards() {
        QuestAuthoringSession.RewardDraft reward = new QuestAuthoringSession.RewardDraft(
            "selectable",
            "theseus:selectable",
            JsonParser.parseString("""
                {
                  "type":"theseus:selectable",
                  "amount":1,
                  "rewards":{"nested":{"type":"theseus:selectable","amount":1,"rewards":{"item":{"type":"theseus:item","item":"minecraft:stone"}}}}
                }
                """).getAsJsonObject()
        );

        assertEquals(
            "Selectable rewards cannot contain selectable rewards.",
            QuestDraftValidation.validateRewardDraft(reward, List.of(), -1, false, REGISTRIES)
        );
    }
}
