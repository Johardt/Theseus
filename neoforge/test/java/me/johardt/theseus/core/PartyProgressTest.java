package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PartyProgressTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CHARLIE = new UUID(0, 3);
    private static final UUID LATE = new UUID(0, 4);
    private static final UUID PARTY = new UUID(1, 1);
    @TempDir Path directory;

    @Test
    void completingSharedQuestNotifiesOtherOnlineMembersExactlyOnce() throws Exception {
        Fixture f = fixture("party", xpRewards());
        ServerPlayer bob = playerIdentity();
        f.world.identities.put(bob, BOB);
        f.world.online = List.of(bob);
        Sync sync = (Sync) f.runtime.questSync;
        f.runtime.triggerDummy(null, "finish");
        assertTrue(f.runtime.isComplete(bob, f.quest()));
        assertEquals(1, sync.notifications.stream().filter(notification -> notification.player() == null && notification.kind().equals("complete")).count());
        assertEquals(1, sync.notifications.stream().filter(notification -> notification.player() == bob && notification.kind().equals("complete")).count());
        f.runtime.triggerDummy(null, "finish");
        f.runtime.snapshot(bob, "Main");
        assertEquals(1, sync.notifications.stream().filter(notification -> notification.player() == bob && notification.kind().equals("complete")).count());
    }

    @Test
    void copiedCompletionRespectsRecipientNotificationSuppression() throws Exception {
        Fixture f = fixture("party", xpRewards());
        ServerPlayer bob = playerIdentity();
        f.world.identities.put(bob, BOB);
        f.world.online = List.of(bob);
        f.runtime.suppressNotifications.add(BOB);
        f.runtime.triggerDummy(null, "finish");
        assertTrue(f.runtime.isComplete(bob, f.quest()));
        f.runtime.suppressNotifications.remove(BOB);
        f.runtime.snapshot(bob, "Main");
        Sync sync = (Sync) f.runtime.questSync;
        assertTrue(sync.notifications.stream().noneMatch(notification -> notification.player() == bob));
    }

    @Test
    void individualCompletionDoesNotNotifyOtherPartyMembers() throws Exception {
        Fixture f = fixture("self", xpRewards());
        ServerPlayer bob = playerIdentity();
        f.world.identities.put(bob, BOB);
        f.world.online = List.of(bob);
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.isComplete(bob, f.quest()));
        Sync sync = (Sync) f.runtime.questSync;
        assertTrue(sync.notifications.stream().noneMatch(notification -> notification.player() == bob));
    }

    private static ServerPlayer playerIdentity() throws Exception {
        // The runtime uses QuestWorld for identity; no game methods are invoked on this token.
        me.johardt.theseus.client.MinecraftTestBootstrap.ensureBootstrapped();
        Class<?> type = Class.forName("sun.misc.Unsafe");
        var field = type.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (ServerPlayer) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), ServerPlayer.class);
    }

    @Test
    void independentChoicesAreNotChosenByTheCompleter() throws Exception {
        Fixture f = fixture("party", """
            {"choice":{"type":"theseus:selectable","amount":1,"rewards":{
              "small":{"type":"theseus:xp","amount":2},
              "large":{"type":"theseus:xp","amount":7}
            }}}
            """);
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.claim(null, "quest"));
        assertTrue(f.runtime.claim(null, "quest", Map.of("choice", List.of("small"))));
        f.world.player = BOB;
        assertTrue(f.runtime.claim(null, "quest", Map.of("choice", List.of("large"))));
        assertEquals(2, f.world.xp.get(ALICE));
        assertEquals(7, f.world.xp.get(BOB));
    }

    @Test
    void interruptedGrantKeepsSuccessfulReceiptsAndRequiresExplicitRecovery() throws Exception {
        Fixture f = fixture("party", """
            {"first":{"type":"theseus:xp","amount":3},"second":{"type":"theseus:command","command":"test"}}
            """);
        f.world.commandFails = true;
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.claim(null, "quest"));
        assertEquals(Set.of("first"), f.runtime.progress(ALICE, "quest").claimedRewards());
        assertEquals(Set.of("second"), f.runtime.progress(ALICE, "quest").pendingRewards());
        QuestRuntime restarted = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        assertFalse(restarted.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(ALICE));
        // A separate recipient can still claim normally.
        f.world.commandFails = false;
        f.world.player = BOB;
        assertTrue(restarted.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(BOB));
    }

    @Test
    void saveFailureBeforeGrantDoesNotDeliverRewards() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.store.failSave = true;
        assertFalse(f.runtime.claim(null, "quest"));
        assertTrue(f.world.xp.isEmpty());
        assertTrue(f.runtime.progress(ALICE, "quest").claimedRewards().isEmpty());
        f.store.failSave = false;
        assertTrue(f.runtime.claim(null, "quest"));
    }

    @Test
    void saveFailureAfterDeliveryLeavesDurableReservationForOperatorReview() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.store.failFrom = f.store.saves + 2;
        assertFalse(f.runtime.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(ALICE));
        f.store.failFrom = Integer.MAX_VALUE;
        QuestRuntime restarted = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        assertEquals(Set.of("xp"), restarted.progress(ALICE, "quest").pendingRewards());
        assertFalse(restarted.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(ALICE));
    }

    @Test
    void editingTasksAndRenamingDoNotEraseEarnedRewardsOrReceipts() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.world.player = BOB;
        f.runtime.claim(null, "quest");
        new QuestRuntimeMutations(f.runtime).resetQuestProgress("quest", "renamed");
        assertFalse(f.runtime.progress.get(BOB).containsKey("quest"));
        assertEquals(Set.of("xp"), f.runtime.progress(BOB, "renamed").claimedRewards());
        assertNotNull(f.runtime.progress(CHARLIE, "renamed").partyRewardSource());
        assertTrue(f.runtime.progress(ALICE, "renamed").completionRecorded());
        assertTrue(f.runtime.progress(ALICE, "renamed").taskProgress().isEmpty());
    }

    @Test
    void unsupportedRewardDoesNotEraseOtherRecipientsEligibility() throws Exception {
        Fixture f = fixture("party", "{\"missing\":{\"type\":\"example:missing\"}}");
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.claim(null, "quest"));
        assertTrue(f.runtime.progress(ALICE, "quest").claimedRewards().isEmpty());
        assertTrue(f.runtime.progress(ALICE, "quest").pendingRewards().isEmpty());
        assertNotNull(f.runtime.progress(BOB, "quest").partyRewardSource());
        assertNotNull(f.runtime.progress(CHARLIE, "quest").partyRewardSource());
    }

    @Test
    void resetPreservesEligibilityReceiptsAndCompletionHistory() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.world.player = BOB;
        f.runtime.claim(null, "quest");
        f.runtime.reset(null);
        assertNotNull(f.runtime.progress(BOB, "quest").partyRewardSource());
        assertFalse(f.runtime.claim(null, "quest"));
        f.world.player = ALICE;
        f.runtime.reset(null);
        assertTrue(f.runtime.progress(ALICE, "quest").completionRecorded());
        assertTrue(f.runtime.progress(ALICE, "quest").taskProgress().isEmpty());
    }

    @Test
    void versionedMigrationPreservesLegacyAndDeferredQuestRecords() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.store.value = json("""
            {"00000000-0000-0000-0000-000000000001":{
              "quest":{"tasks":{"task":1},"claimed":true,"pinned":true},
              "missing":{"tasks":{"unknown":4},"claimed_rewards":["saved"]}
            }}
            """);
        f.runtime.loadProgress();
        assertEquals(4, f.store.value.get("version").getAsInt());
        JsonObject player = f.store.value.getAsJsonObject("players").getAsJsonObject(ALICE.toString());
        assertEquals(4, player.getAsJsonObject("missing").getAsJsonObject("tasks").get("unknown").getAsInt());
        assertEquals(Set.of("xp"), f.runtime.progress(ALICE, "quest").claimedRewards());
        assertTrue(f.runtime.progress(ALICE, "quest").isPinned());
        assertFalse(f.runtime.claim(null, "quest"));
    }

    @Test
    void unsupportedFutureVersionIsNeverOverwrittenOrUsedForPayout() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.store.value = json("{\"version\":99,\"players\":{}}");
        f.runtime.loadProgress();
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.claim(null, "quest"));
        f.runtime.close();
        assertEquals(99, f.store.value.get("version").getAsInt());
    }

    @Test
    void authoredPartySourceCannotImpersonateEarnedEligibility() throws Exception {
        Fixture f = fixture("party", xpRewards(), document -> document.addProperty("party_reward_source", "Forged party"));
        JsonObject quest = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject().getAsJsonObject("quest");
        assertFalse(quest.has("party_reward_source"));
        assertFalse(quest.get("reward_eligible").getAsBoolean());
        assertFalse(f.runtime.claim(null, "quest"));
        f.runtime.triggerDummy(null, "finish");
        f.world.player = BOB;
        quest = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject().getAsJsonObject("quest");
        assertEquals("Builders", quest.get("party_reward_source").getAsString());
        assertTrue(quest.get("reward_eligible").getAsBoolean());
    }

    @Test
    void differentMembersCompleteSharedTasksAndOfflineMembersClaimIndependently() throws Exception {
        Fixture f = sharedFixture("party", """
            {"first":{"type":"theseus:dummy","value":"first"},"second":{"type":"theseus:dummy","value":"second"}}
            """);
        f.runtime.triggerDummy(null, "first");
        f.world.player = BOB;
        assertEquals(1, f.runtime.progress((ServerPlayer) null, "quest").getTaskProgress("first"));
        assertFalse(f.runtime.isComplete(null, f.quest()));
        assertFalse(f.runtime.isUnlocked(null, f.runtime.catalog.quests().get("child")));
        f.runtime.triggerDummy(null, "second");
        f.world.player = CHARLIE;
        assertTrue(f.runtime.isComplete(null, f.quest()));
        assertTrue(f.runtime.isUnlocked(null, f.runtime.catalog.quests().get("child")));
        assertEquals(1, f.runtime.progress(CHARLIE, "quest").getTaskProgress("first"));
        JsonObject snapshot = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject().getAsJsonObject("quest");
        assertEquals("shared", snapshot.get("progress_scope").getAsString());
        assertEquals("Builders", snapshot.get("progress_party").getAsString());
        assertTrue(f.runtime.claim(null, "quest"));
        assertFalse(f.runtime.claim(null, "quest"));
        f.world.player = BOB;
        assertTrue(f.runtime.claim(null, "quest"));
        f.world.player = ALICE;
        assertTrue(f.runtime.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(CHARLIE));
        assertEquals(3, f.world.xp.get(BOB));
        assertEquals(3, f.world.xp.get(ALICE));
    }

    @Test
    void sharedCountersAccumulateAndInventoryChecksDoNotAddOrUndoProgress() throws Exception {
        Fixture f = sharedFixture("party", """
            {"kills":{"type":"theseus:kill_entity","entity":"minecraft:zombie","amount":2},
             "items":{"type":"theseus:item","item":"minecraft:stone","amount":5,"collection":"automatic"}}
            """);
        f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        new QuestRuntimeProgression(f.runtime).signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 3, false), null);
        f.world.player = BOB;
        f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        new QuestRuntimeProgression(f.runtime).signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 2, false), null);
        assertEquals(2, f.runtime.progress((ServerPlayer) null, "quest").getTaskProgress("kills"));
        assertEquals(3, f.runtime.progress((ServerPlayer) null, "quest").getTaskProgress("items"));
        assertFalse(f.runtime.isComplete(null, f.quest()));
        new QuestRuntimeProgression(f.runtime).signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 5, false), null);
        f.world.player = CHARLIE;
        new QuestRuntimeProgression(f.runtime).signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 0, false), null);
        assertTrue(f.runtime.isComplete(null, f.quest()));
    }

    @Test
    void sharedSettingFallsBackToPersonalTasksWithoutOpac() throws Exception {
        Fixture f = sharedFixture("party", "{\"task\":{\"type\":\"theseus:dummy\",\"value\":\"finish\"}}");
        QuestRuntime solo = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        solo.triggerDummy(null, "finish");
        assertTrue(solo.isComplete(null, f.quest()));
        f.world.player = BOB;
        assertFalse(solo.isComplete(null, f.quest()));
        assertFalse(solo.claim(null, "quest"));
    }

    @Test
    void sharedManualSubmissionConsumesOnlyTheContributorsInventory() throws Exception {
        me.johardt.theseus.client.MinecraftTestBootstrap.ensureBootstrapped();
        Fixture f = sharedFixture("party", """
            {"items":{"type":"theseus:item","item":"minecraft:stone","amount":4,"collection":"manual"}}
            """);
        int[] alice = {2};
        int[] bob = {3};
        QuestRuntimeProgression.ItemInventory aliceInventory = () -> List.of(new QuestRuntimeProgression.ItemSlot(
            new TaskEngine.Signal.RegistryEntry("minecraft:stone", Set.of(), new JsonObject(), alice[0]), amount -> alice[0] -= amount));
        QuestRuntimeProgression.ItemInventory bobInventory = () -> List.of(new QuestRuntimeProgression.ItemSlot(
            new TaskEngine.Signal.RegistryEntry("minecraft:stone", Set.of(), new JsonObject(), bob[0]), amount -> bob[0] -= amount));
        QuestRuntimeProgression progression = new QuestRuntimeProgression(f.runtime);
        assertTrue(progression.submit(null, "quest", "items", aliceInventory));
        assertEquals(0, alice[0]);
        assertEquals(3, bob[0]);
        f.world.player = BOB;
        assertTrue(progression.submit(null, "quest", "items", bobInventory));
        assertEquals(1, bob[0]);
        assertEquals(4, f.runtime.progress((ServerPlayer) null, "quest").getTaskProgress("items"));
        assertFalse(progression.submit(null, "quest", "items", bobInventory));
        assertEquals(1, bob[0]);
    }

    @Test
    void resettingSharedCompositeChildRecomputesTheParentWithoutErasingClaims() throws Exception {
        Fixture f = sharedFixture("party", """
            {"both":{"type":"theseus:composite","amount":2,"tasks":{
              "first":{"type":"theseus:dummy","value":"first"},"second":{"type":"theseus:dummy","value":"second"}}}}
            """);
        f.runtime.triggerDummy(null, "first");
        f.world.player = BOB;
        f.runtime.triggerDummy(null, "second");
        assertTrue(f.runtime.claim(null, "quest"));
        JsonObject request = json("{\"scope\":\"task\",\"quest\":\"quest\",\"entry\":\"both/first\"}");
        assertTrue(f.runtime.resetProgressResult(null, request).success());
        f.world.player = ALICE;
        assertFalse(f.runtime.isComplete(null, f.quest()));
        assertEquals(1, f.runtime.progress((ServerPlayer) null, "quest").getTaskProgress("both"));
        assertTrue(f.runtime.progress(BOB, "quest").claimedRewards().contains("xp"));
    }

    @Test
    void failedPartyLookupPausesContributionsAndRetainsEarnedClaims() throws Exception {
        Fixture f = sharedFixture("party", "{\"task\":{\"type\":\"theseus:dummy\",\"value\":\"finish\"}}");
        f.lookup.fail = true;
        assertFalse(f.runtime.triggerDummy(null, "finish"));
        assertEquals(0, f.runtime.progress(ALICE, "quest").getTaskProgress("task"));
        JsonObject snapshot = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject().getAsJsonObject("quest");
        assertEquals("individual", snapshot.get("progress_scope").getAsString());
        assertFalse(snapshot.get("reward_eligible").getAsBoolean());
        f.lookup.fail = false;
        assertTrue(f.runtime.triggerDummy(null, "finish"));
        f.lookup.fail = true;
        f.world.player = BOB;
        assertTrue(f.runtime.claim(null, "quest"));
        assertFalse(f.runtime.claim(null, "quest"));
    }

    @Test
    void lockedSharedProgressIsVisibleButCannotBeContributedToOrClaimedUntilPrerequisitesAreMet() throws Exception {
        Fixture f = fixture("party", xpRewards());
        new QuestDocumentStore(directory).createQuest("b", json("""
            {"display":{"title":"Individual B"},"settings":{"individual_progress":true},
             "tasks":{"b":{"type":"theseus:dummy","value":"b"}},"rewards":{}}
            """));
        new QuestDocumentStore(directory).createQuest("c", json("""
            {"display":{"title":"Shared C","groups":{"Main":{"position":[0,0]}}},"dependencies":["b"],
             "settings":{"individual_progress":false,"hidden":"in_progress"},
             "tasks":{"c":{"type":"theseus:kill_entity","entity":"minecraft:zombie","amount":2}},
             "rewards":{"xp":{"type":"theseus:xp","amount":3}}}
            """));
        new QuestDocumentStore(directory).createQuest("d", json("""
            {"display":{"title":"After C"},"dependencies":["c"],"settings":{"individual_progress":false},
             "tasks":{"d":{"type":"theseus:dummy","value":"d"}},"rewards":{}}
            """));
        f.runtime.catalog = QuestCatalog.load(directory);
        f.runtime.triggerDummy(null, "b");
        f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        f.world.player = BOB;
        QuestDefinition c = f.runtime.catalog.quests().get("c");
        assertEquals(1, f.runtime.progress(BOB, "c").getTaskProgress("c"));
        assertFalse(f.runtime.isUnlocked(null, c));
        assertFalse(f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie")));
        assertEquals(1, f.runtime.progress(ALICE, "c").getTaskProgress("c"));
        f.world.player = ALICE;
        f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        f.world.player = BOB;
        assertTrue(f.runtime.isComplete(null, c));
        assertFalse(f.runtime.isUnlocked(null, f.runtime.catalog.quests().get("d")));
        assertFalse(f.runtime.triggerDummy(null, "d"));
        assertFalse(f.runtime.rewardEligible(null, c));
        assertFalse(f.runtime.claim(null, "c"));
        JsonObject snapshot = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject().getAsJsonObject("c");
        assertTrue(snapshot.get("complete").getAsBoolean());
        assertFalse(snapshot.get("unlocked").getAsBoolean());
        assertFalse(snapshot.get("reward_eligible").getAsBoolean());
        // Copied completion and locked rewards remain Bob's after leaving.
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, CHARLIE));
        assertTrue(f.runtime.isComplete(null, c));
        assertFalse(f.runtime.claim(null, "c"));
        f.runtime.triggerDummy(null, "b");
        assertTrue(f.runtime.isUnlocked(null, c));
        assertTrue(f.runtime.isUnlocked(null, f.runtime.catalog.quests().get("d")));
        assertTrue(f.runtime.claim(null, "c"));
        assertFalse(f.runtime.claim(null, "c"));
    }

    @Test
    void leavingAndJoiningNeverDiscardProgressAndReconciliationUsesMaximum() throws Exception {
        Fixture f = sharedFixture("party", "{\"kills\":{\"type\":\"theseus:kill_entity\",\"entity\":\"minecraft:zombie\",\"amount\":10}}");
        for (int i = 0; i < 3; i++) f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        f.world.player = BOB;
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, CHARLIE));
        assertEquals(3, f.runtime.progress(BOB, "quest").getTaskProgress("kills"));
        f.runtime.progress(LATE, "quest").setTaskProgress("kills", 5);
        f.lookup.party = new PartyLookup.Party(new UUID(1, 2), "New party", Set.of(BOB, LATE));
        f.runtime.reconcilePartyProgress(null);
        assertEquals(5, f.runtime.progress(BOB, "quest").getTaskProgress("kills"));
        assertEquals(5, f.runtime.progress(LATE, "quest").getTaskProgress("kills"));
        f.runtime.signal(null, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));
        assertEquals(6, f.runtime.progress(LATE, "quest").getTaskProgress("kills"));
        assertEquals(3, f.runtime.progress(ALICE, "quest").getTaskProgress("kills"));
    }

    @Test
    void individualQuestsAreNotCopiedOnContributionsOrJoining() throws Exception {
        Fixture f = fixture("self", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.world.player = BOB;
        f.runtime.reconcilePartyProgress(null);
        assertFalse(f.runtime.isComplete(null, f.quest()));
        assertFalse(f.runtime.claim(null, "quest"));
        f.lookup.party = null;
        f.world.player = ALICE;
        assertTrue(f.runtime.isComplete(null, f.quest()));
        assertTrue(f.runtime.claim(null, "quest"));
    }

    @Test
    void lateJoinersReceiveHistoricalSharedAchievementsAndTheirOwnClaims() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.runtime.claim(null, "quest");
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, BOB, CHARLIE, LATE));
        f.world.player = LATE;
        f.runtime.reconcilePartyProgress(null);
        assertTrue(f.runtime.isComplete(null, f.quest()));
        assertTrue(f.runtime.claim(null, "quest"));
        f.world.player = ALICE;
        assertFalse(f.runtime.claim(null, "quest"));
    }

    @Test
    void copiedProgressAndClaimsSurviveRestartWithoutOpac() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.runtime.close();
        f.world.player = BOB;
        QuestRuntime restarted = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        assertTrue(restarted.isComplete(null, f.quest()));
        assertTrue(restarted.claim(null, "quest"));
        assertFalse(restarted.claim(null, "quest"));
        assertFalse(f.store.value.has("parties"));
    }

    @Test
    void versionThreePartyCountersMigrateIntoMemberStatesWithoutLosingReceipts() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.store.value = json("""
            {"version":3,"players":{"00000000-0000-0000-0000-000000000002":{
              "quest":{"tasks":{},"claimed_rewards":["xp"],"pinned":true}}},
              "parties":{"00000000-0000-0001-0000-000000000001":{
                "quest":{"tasks":{"task":1},"completion_recorded":true},
                "missing":{"tasks":{"old":4}}}}}
            """);
        f.runtime.loadProgress();
        f.runtime.reconcilePartyProgress(null);
        assertEquals(1, f.runtime.progress(BOB, "quest").getTaskProgress("task"));
        assertTrue(f.runtime.progress(BOB, "quest").isPinned());
        assertEquals(Set.of("xp"), f.runtime.progress(BOB, "quest").claimedRewards());
        assertEquals(4, f.store.value.get("version").getAsInt());
        assertFalse(f.store.value.getAsJsonObject("legacy_parties").getAsJsonObject(PARTY.toString()).has("quest"));
        assertTrue(f.store.value.getAsJsonObject("legacy_parties").getAsJsonObject(PARTY.toString()).has("missing"));
    }

    @Test
    void oldRewardOnlyEligibilityBecomesRetainedCompletedTasksWithoutOpac() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.progress(BOB, "quest").earnPartyRewards(new QuestProgressState.PartyRewardSource(PARTY, "Builders", ALICE));
        f.runtime.close();
        f.store.value.addProperty("version", 2);
        f.world.player = BOB;
        QuestRuntime restarted = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        assertTrue(restarted.isComplete(null, f.quest()));
        assertTrue(restarted.claim(null, "quest"));
        assertFalse(restarted.claim(null, "quest"));
    }

    @Test
    void completedIndividualInventoryTasksDoNotDisappearAfterLeavingAndSpendingItems() throws Exception {
        Fixture f = fixture("self", xpRewards(), document -> document.add("tasks", json("""
            {"items":{"type":"theseus:item","item":"minecraft:stone","amount":2,"collection":"automatic"}}
            """)));
        QuestRuntimeProgression progression = new QuestRuntimeProgression(f.runtime);
        progression.signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 2, false), null);
        f.lookup.party = null;
        progression.signal(null, new TaskEngine.Signal.Inventory("minecraft:stone", 0, false), null);
        assertTrue(f.runtime.isComplete(null, f.quest()));
        assertTrue(f.runtime.claim(null, "quest"));
    }

    @Test
    void deferredOldRewardRecipientsRetainTheirCompletionWhenTheQuestReturns() throws Exception {
        Fixture f = fixture("party", xpRewards());
        QuestProgressState state = new QuestProgressState();
        state.earnPartyRewards(new QuestProgressState.PartyRewardSource(PARTY, "Builders", ALICE));
        JsonObject player = new JsonObject();
        player.add("missing", state.toJson());
        JsonObject players = new JsonObject();
        players.add(BOB.toString(), player);
        f.store.value.addProperty("version", 2);
        f.store.value.add("players", players);
        f.runtime.loadProgress();
        new QuestDocumentStore(directory).createQuest("missing", json("""
            {"display":{"title":"Restored"},"tasks":{"task":{"type":"theseus:dummy","value":"finish"}},
             "rewards":{"xp":{"type":"theseus:xp","amount":3}}}
            """));
        QuestCatalog restored = QuestCatalog.load(directory);
        f.world.player = BOB;
        QuestRuntime restarted = new QuestRuntime(restored, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        assertTrue(restarted.isComplete(null, restored.quests().get("missing")));
        assertTrue(restarted.claim(null, "missing"));
    }

    private Fixture sharedFixture(String ignored, String tasks) throws Exception {
        return fixture("party", xpRewards(), document -> {
            document.getAsJsonObject("settings").addProperty("individual_progress", false);
            document.add("tasks", json(tasks));
        });
    }

    private Fixture fixture(String audience, String rewards) throws Exception {
        return fixture(audience, rewards, document -> {});
    }

    private Fixture fixture(String audience, String rewards, Consumer<JsonObject> customize) throws Exception {
        JsonObject document = json("""
            {"display":{"title":"Party quest","groups":{"Main":{"position":[0,0]}}},
             "settings":{"individual_progress":false},
             "tasks":{"task":{"type":"theseus:dummy","value":"finish"}},"rewards":{}}
            """);
        document.getAsJsonObject("settings").addProperty("individual_progress", audience.equals("self"));
        document.add("rewards", json(rewards));
        customize.accept(document);
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("quest", document);
        documents.createQuest("child", json("""
            {"display":{"title":"Child"},"settings":{"individual_progress":true},"dependencies":["quest"],"tasks":{"next":{"type":"theseus:check"}},"rewards":{}}
            """));
        QuestCatalog catalog = QuestCatalog.load(directory);
        World world = new World(catalog, directory);
        Store store = new Store();
        Lookup lookup = new Lookup();
        QuestRuntime runtime = new QuestRuntime(catalog, TaskEngine.defaults(), RewardEngine.builder().build(), store, world, new Sync(), lookup);
        return new Fixture(runtime, world, store, lookup);
    }

    private static String xpRewards() { return "{\"xp\":{\"type\":\"theseus:xp\",\"amount\":3}}"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    record Fixture(QuestRuntime runtime, World world, Store store, Lookup lookup) {
        QuestDefinition quest() { return runtime.catalog.quests().get("quest"); }
    }
    static final class Lookup implements PartyLookup {
        Party party = new Party(PARTY, "Builders", Set.of(ALICE, BOB, CHARLIE));
        boolean fail;
        public boolean available() { return true; }
        public Party find(UUID member) {
            if (fail) throw new IllegalStateException("provider unavailable");
            return party != null && party.members().contains(member) ? party : null;
        }
    }
    static final class Store implements ProgressStore {
        JsonObject value = new JsonObject();
        boolean failSave;
        int saves;
        int failFrom = Integer.MAX_VALUE;
        public JsonObject load() { return value.deepCopy(); }
        public void save(JsonObject value) throws IOException {
            if (++saves >= failFrom || failSave) throw new IOException("simulated disk failure");
            this.value = value.deepCopy();
        }
    }
    static final class Sync implements QuestSync {
        record Notification(ServerPlayer player, String kind, String title, String detail) {}
        final List<Notification> notifications = new java.util.ArrayList<>();
        public void snapshot(ServerPlayer player, String json, boolean open) {}
        public void notification(ServerPlayer player, String kind, String title, String detail) {
            notifications.add(new Notification(player, kind, title, detail));
        }
    }
    static final class World implements QuestWorld {
        final QuestCatalog catalog;
        final Path directory;
        UUID player = ALICE;
        final Map<ServerPlayer, UUID> identities = new java.util.IdentityHashMap<>();
        List<ServerPlayer> online = List.of();
        final Map<UUID, Integer> xp = new HashMap<>();
        boolean commandFails;
        World(QuestCatalog catalog, Path directory) { this.catalog = catalog; this.directory = directory; }
        public QuestCatalog loadCatalog() { return QuestCatalog.load(directory); }
        public UUID playerId(ServerPlayer player) { return identities.getOrDefault(player, this.player); }
        public List<ServerPlayer> onlinePlayers() { return online; }
        public boolean canEdit(ServerPlayer player) { return true; }
        public boolean isIntegratedServer() { return false; }
        public boolean containsRegistryTarget(RegistryValidation.Target target, String value) { return true; }
        public boolean advancementGranted(ServerPlayer player, String advancement) { return false; }
        public boolean hasLootTable(String id) { return true; }
        public void message(ServerPlayer player, String message) {}
        public void grantExperience(ServerPlayer player, int amount, boolean points) { xp.merge(this.player, amount, Integer::sum); }
        public void giveItem(ServerPlayer player, ItemStack stack) {}
        public void runCommand(ServerPlayer player, String command) {
            if (commandFails) throw new IllegalStateException("simulated command failure");
        }
        public void generateLoot(ServerPlayer player, String table, Consumer<ItemStack> receiver) {}
        public Set<TaskEngine.Signal.RegistryEntry> structuresAt(ServerPlayer player) { return Set.of(); }
    }
}
