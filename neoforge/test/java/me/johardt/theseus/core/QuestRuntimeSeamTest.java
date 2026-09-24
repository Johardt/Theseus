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
import java.nio.file.Files;
import java.util.LinkedHashMap;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @Test
    void ordinaryExperienceRefreshProgressesAutomaticPointAndLevelTasks() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("automatic_points", itemQuest(itemTasks(
            "xp", xpTask(10, "points", "automatic")
        )));
        documents.createQuest("automatic_levels", itemQuest(itemTasks(
            "xp", xpTask(5, "levels", "automatic")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = runtime(catalog);
        MutableExperienceAccount account = new MutableExperienceAccount(3, 7);

        assertTrue(new QuestRuntimeProgression(runtime).updateExperienceTasks(null, account));

        assertEquals(7, taskProgress(runtime, "automatic_points", "xp"));
        assertEquals(3, taskProgress(runtime, "automatic_levels", "xp"));
        assertEquals(3, account.levels);
        assertEquals(7, account.points);
    }

    @Test
    void consumingExperienceTasksDoNotReusePointsAcrossQuests() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("first_xp_consumer", itemQuest(itemTasks(
            "xp", xpTask(2, "points", "consume")
        )));
        documents.createQuest("second_xp_consumer", itemQuest(itemTasks(
            "xp", xpTask(2, "points", "consume")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = runtime(catalog);
        MutableExperienceAccount account = new MutableExperienceAccount(0, 3);

        new QuestRuntimeProgression(runtime).updateExperienceTasks(null, account);

        int awardedProgress = catalog.quests().keySet().stream()
            .mapToInt(questId -> taskProgress(runtime, questId, "xp"))
            .sum();
        assertEquals(2, awardedProgress);
        assertEquals(1, account.points);
    }

    @Test
    void nestedExperienceConsumersUseRemainingLevelsAndCapInsufficientProgress() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        JsonObject children = itemTasks(
            "first", xpTask(1, "levels", "consume"),
            "second", xpTask(1, "levels", "consume")
        );
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "theseus:composite");
        inner.addProperty("amount", 2);
        inner.add("tasks", children);
        JsonObject outer = new JsonObject();
        outer.addProperty("type", "theseus:composite");
        outer.addProperty("amount", 1);
        outer.add("tasks", itemTasks("inner", inner));
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("nested_xp_consumers", itemQuest(itemTasks("outer", outer)));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = runtime(catalog);
        MutableExperienceAccount account = new MutableExperienceAccount(1, 0);

        new QuestRuntimeProgression(runtime).updateExperienceTasks(null, account);

        assertEquals(1, taskProgress(runtime, "nested_xp_consumers", "outer/inner/first"));
        assertEquals(0, taskProgress(runtime, "nested_xp_consumers", "outer/inner/second"));
        assertEquals(1, taskProgress(runtime, "nested_xp_consumers", "outer/inner"));
        assertEquals(0, account.levels);
    }

    @Test
    void manualExperienceTaskWaitsForExplicitSubmission() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("manual_xp", itemQuest(itemTasks(
            "xp", xpTask(2, "points", "manual")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = runtime(catalog);
        QuestRuntimeProgression progression = new QuestRuntimeProgression(runtime);
        MutableExperienceAccount account = new MutableExperienceAccount(0, 3);

        assertFalse(progression.updateExperienceTasks(null, account));
        assertEquals(0, taskProgress(runtime, "manual_xp", "xp"));
        assertEquals(3, account.points);

        assertTrue(progression.submit(
            null,
            "manual_xp",
            "xp",
            new MutableItemInventory(),
            account
        ));

        assertEquals(2, taskProgress(runtime, "manual_xp", "xp"));
        assertEquals(1, account.points);
    }

    @Test
    void spendableExperiencePointConversionUsesVanillaLevelThresholds() {
        assertEquals(0, QuestRuntimeProgression.spendableExperiencePoints(0, 0, 7));
        assertEquals(352, QuestRuntimeProgression.spendableExperiencePoints(16, 0, 37));
        assertEquals(394, QuestRuntimeProgression.spendableExperiencePoints(17, 0, 42));
        assertEquals(1_395, QuestRuntimeProgression.spendableExperiencePoints(30, 0, 112));
        assertEquals(1_507, QuestRuntimeProgression.spendableExperiencePoints(31, 0, 121));
        assertEquals(1_568, QuestRuntimeProgression.spendableExperiencePoints(31, 0.5f, 121));
        assertEquals(Integer.MAX_VALUE, QuestRuntimeProgression.spendableExperiencePoints(
            Integer.MAX_VALUE,
            1,
            Integer.MAX_VALUE
        ));
    }

    @Test
    void competingConsumingTasksAcrossQuestsUseOnlyRemovedItems() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("first_item_quest", itemQuest(itemTasks(
            "items", itemTask("\"minecraft:oak_log\"", 2, "consume")
        )));
        documents.createQuest("second_item_quest", itemQuest(itemTasks(
            "items", itemTask("\"minecraft:oak_log\"", 2, "consume")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        MutableItemInventory inventory = new MutableItemInventory();
        inventory.add("minecraft:oak_log", Set.of("minecraft:logs"), 3);

        new QuestRuntimeProgression(runtime).signal(
            null,
            new TaskEngine.Signal.Inventory(List.of(), false),
            inventory
        );

        int awardedProgress = catalog.quests().keySet().stream()
            .mapToInt(questId -> taskProgress(runtime, questId, "items"))
            .sum();
        int removedItems = 3 - inventory.count("minecraft:oak_log");
        assertEquals(2, awardedProgress);
        assertEquals(removedItems, awardedProgress);
        assertEquals(1, inventory.count("minecraft:oak_log"));
    }

    @Test
    void customTaskHandlerReceivesTheOriginalInventorySignal() throws Exception {
        JsonObject task = new JsonObject();
        task.addProperty("type", "example:inventory_observer");
        JsonObject document = questDocument("Custom inventory", false);
        document.getAsJsonObject("tasks").add("observer", task);
        new QuestDocumentStore(directory).createQuest("custom_inventory_quest", document);
        QuestCatalog catalog = QuestCatalog.load(directory);
        TaskEngine.Signal[] observedSignal = { null };
        TaskEngine engine = TaskEngine.defaultBuilder()
            .register("example:inventory_observer", (definition, progress, signal) -> {
                observedSignal[0] = signal;
                return new TaskEngine.Result(progress, 0);
            })
            .build();
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            engine,
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        TaskEngine.Signal.Inventory suppliedSignal = new TaskEngine.Signal.Inventory(
            List.of(new TaskEngine.Signal.RegistryEntry(
                "minecraft:diamond",
                Set.of(),
                new JsonObject(),
                7
            )),
            false
        );

        new QuestRuntimeProgression(runtime).signal(
            null,
            suppliedSignal,
            new MutableItemInventory()
        );

        assertEquals(suppliedSignal, observedSignal[0]);
    }

    @Test
    void nestedCompositeChildrenSeeTheRemainingInventory() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        JsonObject children = itemTasks(
            "first", itemTask("\"minecraft:oak_log\"", 1, "consume"),
            "second", itemTask("\"minecraft:oak_log\"", 1, "consume")
        );
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "theseus:composite");
        inner.addProperty("amount", 2);
        inner.add("tasks", children);
        JsonObject outerTasks = itemTasks("inner", inner);
        JsonObject outer = new JsonObject();
        outer.addProperty("type", "theseus:composite");
        outer.addProperty("amount", 1);
        outer.add("tasks", outerTasks);
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("nested_item_quest", itemQuest(itemTasks("outer", outer)));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        MutableItemInventory inventory = new MutableItemInventory();
        inventory.add("minecraft:oak_log", Set.of("minecraft:logs"), 1);

        new QuestRuntimeProgression(runtime).signal(
            null,
            new TaskEngine.Signal.Inventory(List.of(), false),
            inventory
        );

        assertEquals(1, taskProgress(runtime, "nested_item_quest", "outer/inner/first"));
        assertEquals(0, taskProgress(runtime, "nested_item_quest", "outer/inner/second"));
        assertEquals(1, taskProgress(runtime, "nested_item_quest", "outer/inner"));
        assertEquals(0, inventory.count("minecraft:oak_log"));
    }

    @Test
    void enoughItemsCompleteEveryConsumingTaskAndAreRemovedExactly() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("first_item_quest", itemQuest(itemTasks(
            "items", itemTask("\"minecraft:oak_log\"", 2, "consume")
        )));
        documents.createQuest("second_item_quest", itemQuest(itemTasks(
            "items", itemTask("\"minecraft:oak_log\"", 2, "consume")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        MutableItemInventory inventory = new MutableItemInventory();
        inventory.add("minecraft:oak_log", Set.of("minecraft:logs"), 4);

        new QuestRuntimeProgression(runtime).signal(
            null,
            new TaskEngine.Signal.Inventory(List.of(), false),
            inventory
        );

        assertEquals(4, catalog.quests().keySet().stream()
            .mapToInt(questId -> taskProgress(runtime, questId, "items"))
            .sum());
        assertEquals(0, inventory.count("minecraft:oak_log"));
    }

    @Test
    void overlappingItemPredicatesAllocateInExistingQuestTraversalOrder() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        JsonObject taggedItem = new JsonObject();
        taggedItem.addProperty("tag", "minecraft:logs");
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("tagged_item_quest", itemQuest(itemTasks(
            "items", itemTask(taggedItem.toString(), 1, "consume")
        )));
        documents.createQuest("exact_item_quest", itemQuest(itemTasks(
            "items", itemTask("\"minecraft:oak_log\"", 1, "consume")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        List<String> traversalOrder = List.copyOf(catalog.quests().keySet());
        MutableItemInventory inventory = new MutableItemInventory();
        inventory.add("minecraft:oak_log", Set.of("minecraft:logs"), 1);

        new QuestRuntimeProgression(runtime).signal(
            null,
            new TaskEngine.Signal.Inventory(List.of(), false),
            inventory
        );

        assertEquals(1, taskProgress(runtime, traversalOrder.getFirst(), "items"));
        assertEquals(0, taskProgress(runtime, traversalOrder.get(1), "items"));
        assertEquals(0, inventory.count("minecraft:oak_log"));
    }

    @Test
    void automaticObservationLeavesManualTasksAndExplicitSubmissionConsumesAvailableItems() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("manual_item_quest", itemQuest(itemTasks(
            "manual_items", itemTask("\"minecraft:oak_log\"", 3, "manual")
        )));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        QuestRuntimeProgression progression = new QuestRuntimeProgression(runtime);
        MutableItemInventory inventory = new MutableItemInventory();
        inventory.add("minecraft:oak_log", Set.of("minecraft:logs"), 2);

        progression.signal(
            null,
            new TaskEngine.Signal.Inventory(List.of(), false),
            inventory
        );

        assertEquals(0, taskProgress(runtime, "manual_item_quest", "manual_items"));
        assertEquals(2, inventory.count("minecraft:oak_log"));
        assertTrue(progression.submit(null, "manual_item_quest", "manual_items", inventory));
        assertEquals(2, taskProgress(runtime, "manual_item_quest", "manual_items"));
        assertEquals(0, inventory.count("minecraft:oak_log"));
    }

    @Test
    void serverSameIdMoveRetainsSourceAndAppliesRequestedChapterPlacement() throws Exception {
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("server_move", questDocument("Server move", true));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );

        for (String chapter : List.of("Main", "Other")) {
            JsonObject request = new JsonObject();
            request.addProperty("source_id", "server_move");
            request.addProperty("id", "server_move");
            request.addProperty("chapter", chapter);
            request.addProperty("chapter_only", false);
            request.addProperty("move", true);
            request.addProperty("x", chapter.equals("Main") ? 3 : 9);
            request.addProperty("y", chapter.equals("Main") ? 4 : 10);
            request.add("quest", documents.readQuest("server_move"));

            assertTrue(runtime.pasteQuest(null, request).success());
            assertTrue(runtime.catalog.quests().containsKey("server_move"));
            var placement = documents.readQuest("server_move")
                .getAsJsonObject("display")
                .getAsJsonObject("groups")
                .getAsJsonObject(chapter)
                .getAsJsonArray("position");
            assertEquals(chapter.equals("Main") ? 3 : 9, placement.get(0).getAsInt());
            assertEquals(chapter.equals("Main") ? 4 : 10, placement.get(1).getAsInt());
        }

        String beforeCopyCollision = documents.readQuest("server_move").toString();
        JsonObject copyCollision = new JsonObject();
        copyCollision.addProperty("source_id", "server_move");
        copyCollision.addProperty("id", "server_move");
        copyCollision.addProperty("chapter", "Main");
        copyCollision.addProperty("move", false);
        copyCollision.add("quest", documents.readQuest("server_move"));
        assertFalse(runtime.pasteQuest(null, copyCollision).success());
        assertEquals(beforeCopyCollision, documents.readQuest("server_move").toString());

        JsonObject missingSource = new JsonObject();
        missingSource.addProperty("source_id", "missing_source");
        missingSource.addProperty("id", "missing_source");
        missingSource.addProperty("chapter", "Main");
        missingSource.addProperty("move", true);
        missingSource.add("quest", documents.readQuest("server_move"));
        assertFalse(runtime.pasteQuest(null, missingSource).success());
    }

    @Test
    void chapterMutationResultsRejectInvalidRequestsAndAcknowledgeSuccessfulWrites() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("chapter_results", questDocument("Chapter results", false));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );

        Map<String, String> beforeInvalidRequests = fileContents(directory);
        JsonObject invalidName = new JsonObject();
        invalidName.addProperty("operation", "create");
        invalidName.addProperty("name", "x".repeat(65));
        assertFalse(runtime.applyEditorMutation(
            null,
            QuestMutation.of(QuestMutation.Kind.CHAPTER_ACTION, invalidName)
        ).success());

        JsonObject invalidOrder = new JsonObject();
        invalidOrder.addProperty("operation", "reorder");
        com.google.gson.JsonArray missingChapter = new com.google.gson.JsonArray();
        missingChapter.add("Missing");
        invalidOrder.add("order", missingChapter);
        QuestRuntime.MutationResult reorderResult = runtime.applyEditorMutation(
            null,
            QuestMutation.of(QuestMutation.Kind.CHAPTER_ACTION, invalidOrder)
        );
        assertFalse(reorderResult.success());
        assertEquals(beforeInvalidRequests, fileContents(directory));

        JsonObject createChapter = new JsonObject();
        createChapter.addProperty("operation", "create");
        createChapter.addProperty("name", "Side");
        QuestRuntime.MutationResult success = runtime.applyEditorMutation(
            null,
            QuestMutation.of(QuestMutation.Kind.CHAPTER_ACTION, createChapter)
        );
        assertTrue(success.success());
        assertTrue(QuestDocumentStore.readGroupOrder(directory.resolve("theseus/groups.txt")).contains("Side"));
    }

    @Test
    void chapterMutationReturnsFailureWhenStorageCannotWrite() throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("chapter_write_failure", questDocument("Chapter write failure", false));
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
        Path configDirectory = directory.resolve("theseus");
        java.nio.file.FileStore fileStore = Files.getFileStore(configDirectory);
        org.junit.jupiter.api.Assumptions.assumeTrue(
            fileStore.supportsFileAttributeView("posix"),
            "This regression requires POSIX directory permissions"
        );
        var originalPermissions = Files.getPosixFilePermissions(configDirectory);
        var readOnlyPermissions = new java.util.HashSet<>(originalPermissions);
        readOnlyPermissions.remove(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        readOnlyPermissions.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
        readOnlyPermissions.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
        Map<String, String> before = fileContents(directory);

        JsonObject request = new JsonObject();
        request.addProperty("operation", "create");
        request.addProperty("name", "Blocked");
        QuestRuntime.MutationResult result;
        try {
            Files.setPosixFilePermissions(configDirectory, readOnlyPermissions);
            result = runtime.applyEditorMutation(
                null,
                QuestMutation.of(QuestMutation.Kind.CHAPTER_ACTION, request)
            );
        } finally {
            Files.setPosixFilePermissions(configDirectory, originalPermissions);
        }

        assertFalse(result.success());
        assertEquals(before, fileContents(directory));
    }

    @ParameterizedTest(name = "{0} requires dedicated-server editor permission")
    @EnumSource(QuestMutation.Kind.class)
    void unauthorizedDedicatedServerMutationLeavesQuestFilesUnchanged(QuestMutation.Kind kind) throws Exception {
        MinecraftTestBootstrap.ensureBootstrapped();
        QuestDocumentStore documents = new QuestDocumentStore(directory);
        documents.createQuest("protected", questDocument("Protected", true));
        documents.createQuest("dependent", questDocument("Dependent", false));
        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld dedicatedWorld = new FakeWorld(catalog, false, false);
        Path progressFile = directory.resolve("world/data/theseus_progress.json");
        Files.createDirectories(progressFile.getParent());
        Files.writeString(progressFile, "{\"sentinel\":true}\n");
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new FileProgressStore(progressFile),
            dedicatedWorld,
            new RecordingSync()
        );
        Map<String, String> before = fileContents(directory);

        QuestRuntime.MutationResult result = runtime.applyEditorMutation(
            null,
            QuestMutation.of(kind, unauthorizedRequest(kind))
        );

        assertFalse(result.success());
        assertEquals("You do not have permission to edit quests", result.message());
        assertEquals(1, dedicatedWorld.canEditChecks);
        assertFalse(dedicatedWorld.isIntegratedServer());
        assertEquals(before, fileContents(directory));

        FakeWorld authorizedDedicatedWorld = new FakeWorld(catalog, true, false);
        QuestRuntime authorizedRuntime = new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new FileProgressStore(progressFile),
            authorizedDedicatedWorld,
            new RecordingSync()
        );
        assertTrue(authorizedRuntime.applyEditorMutation(
            null,
            QuestMutation.of(kind, unauthorizedRequest(kind))
        ).success(), "security fixture must be a valid mutating request");
        assertFalse(before.equals(fileContents(directory)), "authorized fixture must change persistent state");
    }

    private static JsonObject unauthorizedRequest(QuestMutation.Kind kind) {
        JsonObject request = new JsonObject();
        switch (kind) {
            case CREATE_QUEST -> {
                request.addProperty("id", "unauthorized_create");
                request.add("document", questDocument("Created", false));
            }
            case UPDATE_QUEST -> {
                QuestDraft draft = QuestDraft.open("protected", questDocument("Protected", true));
                draft.setDisplayBasics("Unauthorized update", null, null, null);
                return draft.updateMutation();
            }
            case IMPORT_QUESTS -> {
                JsonObject files = new JsonObject();
                files.add("unauthorized_import", questDocument("Imported", false));
                request.add("files", files);
            }
            case PASTE_QUEST -> {
                request.addProperty("source_id", "protected");
                request.addProperty("id", "unauthorized_copy");
            }
            case DELETE_QUEST -> request.addProperty("id", "protected");
            case CHAPTER_ACTION -> {
                request.addProperty("operation", "create");
                request.addProperty("name", "Unauthorized");
            }
            case SET_DEPENDENCY -> {
                request.addProperty("prerequisite", "protected");
                request.addProperty("dependent", "dependent");
            }
            case REMOVE_QUEST_GROUP -> {
                request.addProperty("id", "protected");
                request.addProperty("group", "Other");
            }
            case RESET_PROGRESS -> {
                request.addProperty("scope", "quest");
                request.addProperty("quest", "protected");
            }
        }
        return request;
    }

    private static JsonObject questDocument(String title, boolean secondChapter) {
        JsonObject document = JsonParser.parseString("""
            {
              "display":{"title":"placeholder","groups":{"Main":{"position":[0,0]}}},
              "tasks":{},
              "rewards":{}
            }
            """).getAsJsonObject();
        document.getAsJsonObject("display").addProperty("title", title);
        if (secondChapter) {
            JsonObject placement = new JsonObject();
            placement.add("position", JsonParser.parseString("[27,0]"));
            document.getAsJsonObject("display").getAsJsonObject("groups").add("Other", placement);
        }
        return document;
    }

    private JsonObject itemQuest(JsonObject tasks) {
        JsonObject document = questDocument("Items", false);
        document.add("tasks", tasks);
        return document;
    }

    private QuestRuntime runtime(QuestCatalog catalog) {
        return new QuestRuntime(
            catalog,
            TaskEngine.defaults(),
            new InMemoryProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );
    }

    private static JsonObject itemTasks(Object... namesAndTasks) {
        JsonObject tasks = new JsonObject();
        for (int index = 0; index < namesAndTasks.length; index += 2) {
            tasks.add((String) namesAndTasks[index], (JsonObject) namesAndTasks[index + 1]);
        }
        return tasks;
    }

    private static JsonObject itemTask(String itemJson, int amount, String collection) {
        JsonObject task = new JsonObject();
        task.addProperty("type", "theseus:item");
        task.add("item", JsonParser.parseString(itemJson));
        task.addProperty("amount", amount);
        task.addProperty("collection", collection);
        return task;
    }

    private static JsonObject xpTask(int amount, String unit, String collection) {
        JsonObject task = new JsonObject();
        task.addProperty("type", "theseus:xp");
        task.addProperty("amount", amount);
        task.addProperty("xpType", unit);
        task.addProperty("collectionType", collection);
        return task;
    }

    private static int taskProgress(QuestRuntime runtime, String questId, String taskPath) {
        return runtime.progress.get(new UUID(0, 1))
            .get(questId)
            .getTaskProgress(taskPath);
    }

    private static Map<String, String> fileContents(Path root) throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                contents.put(root.relativize(path).toString(), Files.readString(path));
            }
        }
        return contents;
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

    private static final class MutableItemInventory implements QuestRuntimeProgression.ItemInventory {
        private final List<MutableItem> items = new java.util.ArrayList<>();

        private void add(String id, Set<String> tags, int count) {
            items.add(new MutableItem(id, tags, count));
        }

        private int count(String id) {
            return items.stream()
                .filter(item -> item.id.equals(id))
                .mapToInt(item -> item.count)
                .sum();
        }

        @Override
        public List<QuestRuntimeProgression.ItemSlot> slots() {
            List<QuestRuntimeProgression.ItemSlot> slots = new java.util.ArrayList<>();
            for (MutableItem item : items) {
                if (item.count == 0) continue;
                TaskEngine.Signal.RegistryEntry entry = new TaskEngine.Signal.RegistryEntry(
                    item.id,
                    item.tags,
                    new JsonObject(),
                    item.count
                );
                slots.add(new QuestRuntimeProgression.ItemSlot(entry, amount -> {
                    item.count -= amount;
                }));
            }
            return slots;
        }
    }

    private static final class MutableItem {
        private final String id;
        private final Set<String> tags;
        private int count;

        private MutableItem(String id, Set<String> tags, int count) {
            this.id = id;
            this.tags = Set.copyOf(tags);
            this.count = count;
        }
    }

    private static final class MutableExperienceAccount implements QuestRuntimeProgression.ExperienceAccount {
        private int levels;
        private int points;

        private MutableExperienceAccount(int levels, int points) {
            this.levels = levels;
            this.points = points;
        }

        @Override
        public int levels() {
            return levels;
        }

        @Override
        public int points() {
            return points;
        }

        @Override
        public int consume(boolean consumePoints, int amount) {
            int available = consumePoints ? points : levels;
            int consumed = Math.min(available, amount);
            if (consumePoints) points -= consumed;
            else levels -= consumed;
            return consumed;
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
        private final boolean canEdit;
        private final boolean integratedServer;
        private int catalogLoads;
        private int canEditChecks;
        private int experienceGranted;
        private boolean experienceWasPoints;
        private final java.util.Map<String, Integer> itemCounts = new java.util.LinkedHashMap<>();
        private final java.util.List<String> commands = new java.util.ArrayList<>();
        private String generatedLootTable;

        private FakeWorld(QuestCatalog catalog) {
            this(catalog, true, true);
        }

        private FakeWorld(QuestCatalog catalog, boolean canEdit, boolean integratedServer) {
            this.catalog = catalog;
            this.canEdit = canEdit;
            this.integratedServer = integratedServer;
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
            canEditChecks++;
            return canEdit;
        }

        @Override
        public boolean isIntegratedServer() {
            return integratedServer;
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
