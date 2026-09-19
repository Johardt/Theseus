package me.johardt.theseus.addonexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import me.johardt.theseus.client.QuestIconRegistry;
import me.johardt.theseus.core.EditorTypeRegistry;
import me.johardt.theseus.core.ProgressStore;
import me.johardt.theseus.core.QuestCatalog;
import me.johardt.theseus.core.QuestDocumentStore;
import me.johardt.theseus.core.QuestRuntime;
import me.johardt.theseus.core.QuestSync;
import me.johardt.theseus.core.QuestWorld;
import me.johardt.theseus.core.RegistryValidation;
import me.johardt.theseus.core.TaskEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThirdPartyAddonContractTest {
    @TempDir
    Path directory;

    @Test
    void addonTaskRewardAndIconRegisterAndRoundTripThroughTheRuntime() throws Exception {
        ExampleAddon.registerServerTypes();
        ExampleAddonClient.registerClientTypes();

        JsonObject quest = JsonParser.parseString("""
            {
              "display": {
                "title": "Deliver a package",
                "groups": {"Main": {"position": [0, 0]}},
                "icon": {"type": "exampleaddon:badge", "badge": "gold"}
              },
              "tasks": {
                "delivery": {
                  "type": "exampleaddon:deliver_package",
                  "trigger": "shipment_delivered",
                  "amount": 1
                }
              },
              "rewards": {
                "coins": {
                  "type": "exampleaddon:coins",
                  "title": "Courier coins",
                  "amount": 3,
                  "currency": "guild"
                }
              }
            }
            """).getAsJsonObject();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("addon_contract", quest);
        documents.createQuest("unknown_reward", JsonParser.parseString("""
            {
              "display": {"title": "Unknown reward", "groups": {"Main": {"position": [0, 0]}}},
              "tasks": {"gate": {"type": "theseus:dummy", "value": "unknown_reward_done"}},
              "rewards": {"artifact": {"type": "unknownaddon:artifact", "title": "Artifact"}}
            }
            """).getAsJsonObject());

        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        RecordingSync sync = new RecordingSync();
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            new InMemoryProgressStore(),
            world,
            sync
        );

        runtime.sync(null, true);
        JsonObject snapshot = JsonParser.parseString(sync.lastSnapshot).getAsJsonObject();
        JsonObject editorTypes = snapshot.getAsJsonObject("__editor_types");
        Set<String> taskTypes = strings(editorTypes.getAsJsonArray("tasks"));
        Set<String> rewardTypes = strings(editorTypes.getAsJsonArray("rewards"));
        Set<String> iconTypes = strings(editorTypes.getAsJsonArray("icons"));
        EditorTypeRegistry editors = EditorTypeRegistry.registered();

        assertTrue(taskTypes.contains(ExampleAddon.TASK_TYPE));
        assertTrue(rewardTypes.contains(ExampleAddon.REWARD_TYPE));
        assertTrue(iconTypes.contains(ExampleAddon.ICON_TYPE));
        assertTrue(editors.resolve(EditorTypeRegistry.Kind.TASK, ExampleAddon.TASK_TYPE, taskTypes).executable());
        assertTrue(editors.resolve(EditorTypeRegistry.Kind.REWARD, ExampleAddon.REWARD_TYPE, rewardTypes).executable());
        assertTrue(editors.resolve(EditorTypeRegistry.Kind.ICON, ExampleAddon.ICON_TYPE, iconTypes).editable());
        assertTrue(QuestIconRegistry.descriptors().containsKey(ExampleAddon.ICON_TYPE));
        assertEquals(ExampleAddon.ICON_TYPE, QuestIconRegistry.descriptor(ExampleAddon.ICON_TYPE).createDefault().getAsJsonObject().get("type").getAsString());
        assertEquals(ExampleAddon.ICON_TYPE, catalog.quests().get("addon_contract").display().icon().type());

        assertTrue(runtime.signal(null, new TaskEngine.Signal.Manual("shipment_delivered")));
        assertTrue(runtime.isComplete(null, catalog.quests().get("addon_contract")));
        assertTrue(runtime.claim(null, "addon_contract"));
        assertEquals(List.of("Granted 3 example coins"), world.messages);
        assertFalse(runtime.claim(null, "addon_contract"));
        assertTrue(runtime.triggerDummy(null, "unknown_reward_done"));
        assertFalse(runtime.claim(null, "unknown_reward"));
        assertEquals(2, world.messages.size());
        assertTrue(world.messages.getLast().startsWith("Cannot claim unsupported or incomplete reward:"));
    }

    private static Set<String> strings(com.google.gson.JsonArray values) {
        return values.asList().stream().map(value -> value.getAsString()).collect(java.util.stream.Collectors.toSet());
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

    private static final class RecordingSync implements QuestSync {
        private String lastSnapshot;

        @Override
        public void snapshot(ServerPlayer player, String json, boolean open) {
            lastSnapshot = json;
        }

        @Override
        public void notification(ServerPlayer player, String kind, String title, String detail) {}
    }

    private static final class FakeWorld implements QuestWorld {
        private final QuestCatalog catalog;
        private final java.util.ArrayList<String> messages = new java.util.ArrayList<>();

        private FakeWorld(QuestCatalog catalog) {
            this.catalog = catalog;
        }

        @Override public QuestCatalog loadCatalog() { return catalog; }
        @Override public UUID playerId(ServerPlayer player) { return new UUID(0, 42); }
        @Override public List<ServerPlayer> onlinePlayers() { return List.of(); }
        @Override public boolean canEdit(ServerPlayer player) { return true; }
        @Override public boolean isIntegratedServer() { return true; }
        @Override public boolean containsRegistryTarget(RegistryValidation.Target target, String value) { return true; }
        @Override public boolean advancementGranted(ServerPlayer player, String advancement) { return false; }
        @Override public boolean hasLootTable(String id) { return true; }
        @Override public void message(ServerPlayer player, String message) { messages.add(message); }
        @Override public void grantExperience(ServerPlayer player, int amount, boolean points) {}
        @Override public void giveItem(ServerPlayer player, ItemStack stack) {}
        @Override public void runCommand(ServerPlayer player, String command) {}
        @Override public void generateLoot(ServerPlayer player, String lootTable, Consumer<ItemStack> receiver) {}
        @Override public Set<TaskEngine.Signal.RegistryEntry> structuresAt(ServerPlayer player) { return Set.of(); }
    }
}
