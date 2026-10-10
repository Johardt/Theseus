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

class PartyRewardEligibilityTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CHARLIE = new UUID(0, 3);
    private static final UUID LATE = new UUID(0, 4);
    private static final UUID PARTY = new UUID(1, 1);
    @TempDir Path directory;

    @Test
    void completionCapturesOfflineRosterWithoutCompletingTheirTasksOrUnlocks() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, BOB, CHARLIE));
        assertTrue(f.runtime.triggerDummy(null, "finish"));
        assertTrue(f.runtime.progress(ALICE, "quest").completionRecorded());
        f.world.player = BOB;
        assertFalse(f.runtime.isComplete(null, f.quest()));
        assertTrue(f.runtime.rewardEligible(null, f.quest()));
        assertEquals(0, f.runtime.progress(BOB, "quest").getTaskProgress("task"));
        assertFalse(f.runtime.isUnlocked(null, f.runtime.catalog.quests().get("child")));
        JsonObject snapshot = JsonParser.parseString(f.runtime.snapshot(null, "Main")).getAsJsonObject();
        assertTrue(snapshot.getAsJsonObject("quest").get("reward_eligible").getAsBoolean());
        assertFalse(snapshot.getAsJsonObject("quest").get("complete").getAsBoolean());
        assertTrue(f.runtime.claim(null, "quest"));
        assertFalse(f.runtime.claim(null, "quest"));
        f.world.player = CHARLIE;
        assertTrue(f.runtime.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(BOB));
        assertEquals(3, f.world.xp.get(CHARLIE));
        assertNull(f.world.xp.get(ALICE));
    }

    @Test
    void lateJoinersAndRepeatedCompletionSignalsDoNotReceiveHistoricalRewards() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, BOB, CHARLIE, LATE));
        assertFalse(f.runtime.triggerDummy(null, "finish"));
        f.world.player = LATE;
        assertFalse(f.runtime.claim(null, "quest"));
        f.world.player = ALICE;
        f.runtime.reset(null);
        assertTrue(f.runtime.triggerDummy(null, "finish"));
        f.world.player = LATE;
        assertFalse(f.runtime.claim(null, "quest"));
        // A different member's first legitimate completion can include the new member.
        f.world.player = BOB;
        assertTrue(f.runtime.triggerDummy(null, "finish"));
        f.world.player = LATE;
        assertTrue(f.runtime.claim(null, "quest"));
    }

    @Test
    void earnedEligibilitySurvivesDisbandingProviderRemovalAndRestart() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.triggerDummy(null, "finish");
        f.runtime.close();
        f.lookup.party = null;
        QuestRuntime restarted = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        restarted.loadProgress();
        f.world.player = CHARLIE;
        assertTrue(restarted.claim(null, "quest"));
        restarted.close();
        QuestRuntime again = new QuestRuntime(f.runtime.catalog, TaskEngine.defaults(), RewardEngine.builder().build(),
            f.store, f.world, new Sync(), PartyLookup.NONE);
        again.loadProgress();
        assertFalse(again.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(CHARLIE));
        assertEquals(PARTY, again.progress(CHARLIE, "quest").partyRewardSource().partyId());
    }

    @Test
    void personalThenPartyAndPartyThenPersonalPayOnlyOnce() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.lookup.party = null;
        f.runtime.triggerDummy(null, "finish");
        assertTrue(f.runtime.claim(null, "quest"));
        f.lookup.party = new PartyLookup.Party(PARTY, "Builders", Set.of(ALICE, BOB, CHARLIE));
        f.world.player = BOB;
        f.runtime.triggerDummy(null, "finish");
        f.world.player = ALICE;
        assertFalse(f.runtime.claim(null, "quest"));
        f.world.player = CHARLIE;
        assertTrue(f.runtime.claim(null, "quest"));
        f.runtime.triggerDummy(null, "finish");
        assertFalse(f.runtime.claim(null, "quest"));
        assertEquals(3, f.world.xp.get(ALICE));
        assertEquals(3, f.world.xp.get(CHARLIE));
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
    void selfAndSoloPartyQuestsKeepPersonalBehavior() throws Exception {
        Fixture self = fixture("self", xpRewards());
        self.runtime.triggerDummy(null, "finish");
        self.world.player = BOB;
        assertFalse(self.runtime.claim(null, "quest"));
        assertNull(self.runtime.progress(BOB, "quest").partyRewardSource());
        self.world.player = ALICE;
        assertTrue(self.runtime.claim(null, "quest"));
    }

    @Test
    void soloCompletionDoesNotDistributeWhenPartyIsCreatedLater() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.lookup.party = null;
        f.runtime.triggerDummy(null, "finish");
        f.lookup.party = new PartyLookup.Party(PARTY, "New party", Set.of(ALICE, BOB));
        f.runtime.baselineCompletions(null);
        f.world.player = BOB;
        assertFalse(f.runtime.claim(null, "quest"));
    }

    @Test
    void legacyCompletionsAreBaselinedBeforeLoginSignals() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.store.value = json("""
            {"00000000-0000-0000-0000-000000000001":{"quest":{"tasks":{"task":1},"pinned":true}}}
            """);
        f.runtime.loadProgress();
        f.runtime.baselineCompletions(null);
        assertTrue(f.runtime.progress(ALICE, "quest").completionRecorded());
        assertTrue(f.runtime.progress(ALICE, "quest").isPinned());
        f.world.player = BOB;
        assertFalse(f.runtime.claim(null, "quest"));
    }

    @Test
    void suppressionDoesNotPreventNewCompletionEligibility() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.runtime.suppressNotifications.add(ALICE);
        f.runtime.triggerDummy(null, "finish");
        f.world.player = BOB;
        assertTrue(f.runtime.claim(null, "quest"));
    }

    @Test
    void failingProviderKeepsPersonalCompletionAndDoesNotInventRecipients() throws Exception {
        Fixture f = fixture("party", xpRewards());
        f.lookup.fail = true;
        assertTrue(f.runtime.triggerDummy(null, "finish"));
        assertTrue(f.runtime.claim(null, "quest"));
        assertTrue(f.runtime.progress(ALICE, "quest").completionRecorded());
        f.world.player = BOB;
        assertFalse(f.runtime.claim(null, "quest"));
        assertNull(f.runtime.progress(BOB, "quest").partyRewardSource());
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
        assertEquals(2, f.store.value.get("version").getAsInt());
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

    private Fixture fixture(String audience, String rewards) throws Exception {
        JsonObject document = json("""
            {"display":{"title":"Party quest","groups":{"Main":{"position":[0,0]}}},
             "settings":{"reward_audience":"self"},
             "tasks":{"task":{"type":"theseus:dummy","value":"finish"}},"rewards":{}}
            """);
        document.getAsJsonObject("settings").addProperty("reward_audience", audience);
        document.add("rewards", json(rewards));
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("quest", document);
        documents.createQuest("child", json("""
            {"display":{"title":"Child"},"dependencies":["quest"],"tasks":{"next":{"type":"theseus:check"}},"rewards":{}}
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
        public void snapshot(ServerPlayer player, String json, boolean open) {}
        public void notification(ServerPlayer player, String kind, String title, String detail) {}
    }
    static final class World implements QuestWorld {
        final QuestCatalog catalog;
        final Path directory;
        UUID player = ALICE;
        final Map<UUID, Integer> xp = new HashMap<>();
        boolean commandFails;
        World(QuestCatalog catalog, Path directory) { this.catalog = catalog; this.directory = directory; }
        public QuestCatalog loadCatalog() { return QuestCatalog.load(directory); }
        public UUID playerId(ServerPlayer player) { return this.player; }
        public List<ServerPlayer> onlinePlayers() { return List.of(); }
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
