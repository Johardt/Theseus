package me.johardt.theseus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.theseus.client.MinecraftTestBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestRuntimeSeamTest {

    @TempDir
    Path directory;

    @Test
    void constructionDoesNotTouchExternalAdapters() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new FailingProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );

        assertNotNull(runtime);
    }

    @Test
    void reloadUsesTheInjectedWorld() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new InMemoryProgressStore(),
            world,
            new RecordingSync()
        );

        assertEquals(catalog.quests().size(), runtime.reload());
        assertEquals(1, world.catalogLoads);
    }

    @Test
    void snapshotAdvertisesHandlersFromTheRuntimeTaskEngine() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        RecordingSync sync = new RecordingSync();
        TaskEngine engine = TaskEngine.defaultBuilder()
            .register("example:counter", (task, progress, signal) -> new TaskEngine.Result(progress, 0))
            .build();
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            engine,
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            sync
        );

        runtime.sync(null, true);

        JsonObject snapshot = JsonParser.parseString(sync.lastSnapshot).getAsJsonObject();
        var taskTypes = snapshot.getAsJsonObject("__editor_types").getAsJsonArray("tasks");
        assertTrue(taskTypes.asList().stream().anyMatch(type -> type.getAsString().equals("example:counter")));
    }

    @Test
    void claimRulesAndRewardsRunThroughTestAdapters() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new InMemoryProgressStore(),
            world,
            new RecordingSync()
        );

        assertTrue(runtime.triggerDummy(null, "demo_welcome"));
        assertTrue(runtime.claim(null, "welcome"));
        assertEquals(1, world.experienceGranted);
        assertFalse(world.experienceWasPoints);
    }

    @Test
    void everyBuiltInRewardExecutorRunsThroughTheWorldPort() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        JsonObject root = compatibilityFixture();
        JsonObject tasks = new JsonObject();
        JsonObject gate = new JsonObject();
        gate.addProperty("type", "theseus:dummy");
        gate.addProperty("value", "claim_all");
        tasks.add("gate", gate);
        root.add("tasks", tasks);
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("reward_contract", root);

        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            world,
            new RecordingSync()
        );

        assertTrue(runtime.triggerDummy(null, "claim_all"));
        assertTrue(runtime.claim(null, "reward_contract", Map.of("choice", List.of("selected_item"))));
        assertEquals(5, world.experienceGranted);
        assertTrue(world.experienceWasPoints);
        assertEquals(Map.of("minecraft:diamond", 2, "minecraft:emerald", 1, "minecraft:gold_ingot", 2), world.itemCounts);
        assertEquals("say Contract reward", world.commands.getFirst());
        assertEquals("minecraft:chests/simple_dungeon", world.generatedLootTable);
    }

    @Test
    void compositeTaskExecutesItsChildrenThroughTheRuntime() throws Exception {
        JsonObject root = JsonParser.parseString("""
            {
              "display":{"title":"Composite","groups":{"Main":{"position":[0,0]}}},
              "tasks":{"outer":{"type":"theseus:composite","amount":1,"tasks":{"child":{"type":"theseus:dummy","value":"nested_done"}}}},
              "rewards":{}
            }
            """).getAsJsonObject();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("composite_contract", root);
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );

        assertTrue(runtime.triggerDummy(null, "nested_done"));
        assertTrue(runtime.isComplete(null, catalog.quests().get("composite_contract")));
    }

    private static JsonObject compatibilityFixture() throws Exception {
        try (var stream = QuestRuntimeSeamTest.class.getResourceAsStream("/fixtures/compatibility/current_builtins.json")) {
            if (stream == null) throw new AssertionError("Missing current built-in fixture");
            return JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static final class InMemoryProgressStore implements ProgressStore {
        private JsonObject value = new JsonObject();

        @Override
        public JsonObject load() {
            return value.deepCopy();
        }

        @Override
        public void save(JsonObject progress) {
            value = progress.deepCopy();
        }
    }

    private static final class FailingProgressStore implements ProgressStore {
        @Override
        public JsonObject load() throws IOException {
            throw new IOException("constructor performed I/O");
        }

        @Override
        public void save(JsonObject progress) throws IOException {
            throw new IOException("constructor performed I/O");
        }
    }

    private static final class RecordingSync implements QuestSync {
        private String lastSnapshot;

        @Override
        public void snapshot(ServerPlayer player, String json, boolean open) {
            lastSnapshot = json;
        }

        @Override
        public void notification(
            ServerPlayer player,
            String kind,
            String title,
            String detail
        ) {}
    }

    private static final class FakeWorld implements QuestWorld {
        private final QuestCatalog catalog;
        private int catalogLoads;
        private int experienceGranted;
        private boolean experienceWasPoints;
        private final java.util.Map<String, Integer> itemCounts = new java.util.LinkedHashMap<>();
        private final java.util.List<String> commands = new java.util.ArrayList<>();
        private String generatedLootTable;

        private FakeWorld(QuestCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public QuestCatalog loadCatalog() {
            catalogLoads++;
            return catalog;
        }

        @Override
        public UUID playerId(ServerPlayer player) {
            return new UUID(0, 1);
        }

        @Override
        public List<ServerPlayer> onlinePlayers() {
            return List.of();
        }

        @Override
        public boolean canEdit(ServerPlayer player) {
            return true;
        }

        @Override
        public boolean isIntegratedServer() {
            return true;
        }

        @Override
        public boolean containsRegistryTarget(
            RegistryValidation.Target target,
            String value
        ) {
            return true;
        }

        @Override
        public boolean advancementGranted(
            ServerPlayer player,
            String advancement
        ) {
            return false;
        }

        @Override
        public boolean hasLootTable(String id) {
            return true;
        }

        @Override
        public void message(ServerPlayer player, String message) {}

        @Override
        public void grantExperience(
            ServerPlayer player,
            int amount,
            boolean points
        ) {
            experienceGranted += amount;
            experienceWasPoints = points;
        }

        @Override
        public void giveItem(ServerPlayer player, ItemStack stack) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            itemCounts.merge(id, stack.getCount(), Integer::sum);
        }

        @Override
        public void runCommand(ServerPlayer player, String command) {
            commands.add(command);
        }

        @Override
        public void generateLoot(
            ServerPlayer player,
            String lootTable,
            Consumer<ItemStack> receiver
        ) {
            generatedLootTable = lootTable;
            receiver.accept(new ItemStack(Items.GOLD_INGOT, 2));
        }

        @Override
        public Set<TaskEngine.Signal.RegistryEntry> structuresAt(
            ServerPlayer player
        ) {
            return Set.of();
        }
    }
}
