package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDefinitionCompatibilityTest {
    @Test
    void bundledWorldlyKnowledgeQuestIsValid() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/config/theseus/quests/getting_started/compatibility.json")) {
            json = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
        QuestDefinition quest = parse(json);
        assertTrue(quest.issues().isEmpty(), () -> quest.issues().toString());
    }

    @Test
    void parsesOriginalIconBackgroundField() {
        QuestDefinition quest = parse("""
            {"display":{"icon_background":"theseus:textures/gui/quest_backgrounds/diamonds.png"}}
            """);

        assertEquals("theseus:textures/gui/quest_backgrounds/diamonds.png", quest.display().iconBackground());
    }

    @Test
    void parsesIconSizeWithSixteenPixelCompatibilityDefault() {
        assertEquals(16, parse("{\"display\":{}}").display().iconSize());
        assertEquals(8, parse("{\"display\":{\"icon_size\":8}}").display().iconSize());
        assertEquals(64, parse("{\"display\":{\"icon_size\":64}}").display().iconSize());
        assertEquals(16, parse("{\"display\":{\"icon_size\":7}}").display().iconSize());
        assertEquals(16, parse("{\"display\":{\"icon_size\":16.5}}").display().iconSize());
    }

    @Test
    void parsesAllQuestSettingsThatTheEditorAuthors() {
        QuestDefinition quest = parse("""
            {"settings":{"individual_progress":true,"hidden":"completed","unlockNotification":true,
              "showDependencyArrow":false,"repeatable":true,"autoClaimRewards":true}}
            """);

        assertTrue(quest.settings().individualProgress());
        assertEquals(QuestDefinition.Visibility.COMPLETED, quest.settings().hiddenUntil());
        assertTrue(quest.settings().unlockNotification());
        assertFalse(quest.settings().showDependencyArrow());
        assertTrue(quest.settings().repeatable());
        assertTrue(quest.settings().autoClaimRewards());
    }

    @Test
    void parsesCompositeTasksAndAllBuiltInRewardTypes() {
        QuestDefinition quest = parse("""
            {
              "tasks": {
                "combined": {
                  "type": "theseus:composite",
                  "amount": 1,
                  "tasks": {
                    "logs": {"type":"theseus:item","item":"minecraft:oak_log","amount":2},
                    "check": {"type":"theseus:check"}
                  }
                }
              },
              "rewards": {
                "command": {"type":"theseus:command","command":"say complete"},
                "loot": {"type":"theseus:loottable","loot_table":"minecraft:chests/simple_dungeon"},
                "choice": {
                  "type":"theseus:selectable",
                  "amount":1,
                  "rewards": {
                    "item":{"type":"theseus:item","item":{"id":"minecraft:diamond","count":1}},
                    "xp":{"type":"theseus:xp","amount":3}
                  }
                }
              }
            }
            """);

        QuestDefinition.Task composite = quest.tasks().get("combined");
        assertEquals(QuestDefinition.TaskKind.COMPOSITE, composite.kind());
        assertEquals(2, composite.tasks().size());
        assertEquals(QuestDefinition.RewardKind.COMMAND, quest.rewards().get("command").kind());
        assertEquals(QuestDefinition.RewardKind.LOOT_TABLE, quest.rewards().get("loot").kind());
        assertEquals(2, quest.rewards().get("choice").rewards().size());
        assertTrue(quest.issues().isEmpty(), () -> quest.issues().toString());
    }

    @Test
    void preservesUnsupportedTypesAsWarnings() {
        QuestDefinition quest = parse("""
            {
              "tasks":{"custom":{"type":"example:machine_task"}},
              "rewards":{"custom":{"type":"example:currency_reward"}}
            }
            """);

        assertEquals(QuestDefinition.TaskKind.UNSUPPORTED, quest.tasks().get("custom").kind());
        assertEquals(QuestDefinition.RewardKind.UNSUPPORTED, quest.rewards().get("custom").kind());
        assertEquals(2, quest.issues().stream().filter(issue -> issue.severity() == QuestDefinition.Severity.WARNING).count());
    }

    @Test
    void updateValidationAllowsUnchangedCustomDisplayAssets() {
        JsonObject draft = JsonParser.parseString("""
            {"title":"Custom quest","icon":"minecraft:map","background":"example:custom_frame.png"}
            """).getAsJsonObject();

        String error = QuestDiagnostics.validateDisplay(draft, new JsonObject(), ignored -> true).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst()
            .orElse("");

        assertTrue(error.isEmpty(), () -> error);
    }

    @Test
    void updateValidationRejectsChangedInvalidDisplayAssets() {
        JsonObject draft = JsonParser.parseString("""
            {"title":"Custom quest","icon":"minecraft:map","background":"example:custom_frame.png"}
            """).getAsJsonObject();
        JsonObject changed = new JsonObject();
        changed.addProperty("background", true);

        String error = QuestDiagnostics.validateDisplay(draft, changed, ignored -> true).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst()
            .orElse("");

        assertFalse(error.isEmpty());
        assertEquals("Invalid quest background", error);
    }

    @Test
    void reportsPreciseMalformedNestedPaths() {
        QuestDefinition quest = parse("""
            {
              "display":{"groups":{"Main":{"position":[0]}}},
              "tasks":{"combined":{"type":"theseus:composite","amount":3,"tasks":{"bad":false}}},
              "rewards":{"choice":{"type":"theseus:selectable","amount":2,"rewards":{}}}
            }
            """);

        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("display.groups.Main.position")));
        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("tasks.combined.tasks.bad")));
        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("rewards.choice.rewards")));
    }

    @Test
    void rejectsRecursiveSelectableRewards() {
        QuestDefinition quest = parse("""
            {
              "rewards": {
                "outer": {
                  "type":"theseus:selectable",
                  "amount":1,
                  "rewards": {
                    "inner": {
                      "type":"theseus:selectable",
                      "amount":1,
                      "rewards":{"item":{"type":"theseus:item","item":"minecraft:diamond"}}
                    }
                  }
                }
              }
            }
            """);

        assertTrue(quest.issues().stream().anyMatch(issue ->
            issue.path().equals("rewards.outer.rewards") &&
                issue.message().contains("cannot contain another selectable reward")
        ));
    }

    private static QuestDefinition parse(String json) {
        return QuestDefinition.parse("compatibility", JsonParser.parseString(json).getAsJsonObject());
    }
}
