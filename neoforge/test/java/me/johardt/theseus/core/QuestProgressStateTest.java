package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressStateTest {
    @Test
    void earnedEligibilityPendingGrantsAndCompletionHistorySurviveRoundTripAndTaskReset() {
        QuestProgressState state = new QuestProgressState();
        var source = new QuestProgressState.PartyRewardSource(new java.util.UUID(1, 1), "Builders", new java.util.UUID(0, 1));
        state.earnPartyRewards(source);
        state.recordCompletion();
        state.setTaskProgress("root/leaf", 1);
        state.markRewardClaimed("temporarily_removed_reward");
        state.beginRewardGrant("first");
        state.setPinned(true);
        QuestProgressState restored = QuestProgressState.fromJson(quest(), state.toJson());
        assertEquals(source, restored.partyRewardSource());
        assertTrue(restored.completionRecorded());
        assertEquals(Set.of("first"), restored.pendingRewards());
        restored.clearTasks();
        assertTrue(restored.taskProgress().isEmpty());
        assertTrue(restored.isPinned());
        assertEquals(Set.of("temporarily_removed_reward"), restored.claimedRewards());
        assertTrue(restored.unmarkRewardClaimed("first"));
        assertTrue(restored.pendingRewards().isEmpty());
    }

    private static QuestDefinition quest() {
        return QuestDefinition.parse("quest", JsonParser.parseString("""
            {
              "tasks": {
                "root": {
                  "type": "theseus:composite",
                  "amount": 1,
                  "tasks": {
                    "leaf": {"type": "theseus:check"},
                    "branch": {
                      "type": "theseus:composite",
                      "amount": 1,
                      "tasks": {"nested": {"type": "theseus:check"}}
                    }
                  }
                }
              },
              "rewards": {
                "first": {"type": "theseus:xp", "amount": 1},
                "second": {"type": "theseus:xp", "amount": 1},
                "choice": {"type": "theseus:selectable", "rewards": {"one": {"type": "theseus:xp", "amount": 1}}}
              }
            }
            """).getAsJsonObject());
    }

    @Test
    void migratesLegacyClaimedBooleanToCurrentTopLevelRewards() {
        QuestProgressState state = QuestProgressState.fromJson(quest(), JsonParser.parseString("""
            {"tasks":{"root/leaf":1},"claimed":true,"pinned":true}
            """).getAsJsonObject());

        assertEquals(Set.of("first", "second", "choice"), state.claimedRewards());
        assertTrue(state.allRewardsClaimed(quest()));
        assertTrue(state.isPinned());
        assertFalse(state.toJson().has("claimed"));
        assertEquals(3, state.toJson().getAsJsonArray("claimed_rewards").size());
    }

    @Test
    void prefersNewClaimedRewardsAndRoundTripsIt() {
        QuestDefinition quest = quest();
        QuestProgressState state = QuestProgressState.fromJson(quest, JsonParser.parseString("""
            {"claimed":true,"claimed_rewards":["second"],"pinned":false}
            """).getAsJsonObject());

        QuestProgressState roundTrip = QuestProgressState.fromJson(quest, state.toJson());

        assertEquals(Set.of("second"), roundTrip.claimedRewards());
        assertFalse(roundTrip.allRewardsClaimed(quest));
    }

    @Test
    void resetTaskPathRemovesOnlyThePathAndItsDescendants() {
        QuestProgressState state = new QuestProgressState();
        state.setTaskProgress("root", 1);
        state.setTaskProgress("root/leaf", 1);
        state.setTaskProgress("root/branch", 1);
        state.setTaskProgress("root/branch/nested", 1);
        state.setTaskProgress("other", 2);
        state.markRewardClaimed("first");
        state.setPinned(true);

        assertTrue(state.resetTaskPath("root/branch"));
        assertEquals(1, state.getTaskProgress("root"));
        assertEquals(1, state.getTaskProgress("root/leaf"));
        assertEquals(0, state.getTaskProgress("root/branch"));
        assertEquals(0, state.getTaskProgress("root/branch/nested"));
        assertEquals(2, state.getTaskProgress("other"));
        assertEquals(Set.of("first"), state.claimedRewards());

        state.clearProgress();
        assertTrue(state.taskProgress().isEmpty());
        assertTrue(state.claimedRewards().isEmpty());
        assertTrue(state.isPinned());
    }

    @Test
    void progressionOnlyQuestIsNeverMarkedClaimed() {
        QuestDefinition noRewards = QuestDefinition.parse("quest", JsonParser.parseString("{\"tasks\":{}}").getAsJsonObject());
        QuestProgressState state = QuestProgressState.fromJson(noRewards, JsonParser.parseString("{\"claimed\":true}").getAsJsonObject());

        assertTrue(state.claimedRewards().isEmpty());
        assertFalse(state.allRewardsClaimed(noRewards));
    }
}
