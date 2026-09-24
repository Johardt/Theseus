package me.johardt.theseus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import me.johardt.theseus.Theseus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

public final class QuestRuntime {
    private final QuestRuntimeMutations mutations = new QuestRuntimeMutations(this);
    private final QuestRuntimeProgression progression = new QuestRuntimeProgression(this);

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final TaskEngine.Builder TASKS = TaskEngine.defaultBuilder();
    private static final RewardEngine.Builder REWARDS = RewardEngine.builder();
    static boolean taskHandlersLocked;
    static boolean rewardHandlersLocked;

    final TaskEngine taskEngine;
    final RewardEngine rewardEngine;
    final ProgressStore progressStore;
    final QuestWorld world;
    final QuestSync questSync;
    QuestCatalog catalog;
    final Map<UUID, Map<String, QuestProgressState>> progress =
        new HashMap<>();
    /** Raw progress held until a quest that failed catalog loading is available again. */
    final Map<UUID, Map<String, JsonElement>> deferredProgress = new HashMap<>();
    final Set<UUID> suppressNotifications = new java.util.HashSet<>();
    private boolean progressLoadFailed;

    /** Builds a runtime from handlers registered with QuestRuntime before server startup. */
    public QuestRuntime(
        QuestCatalog catalog,
        ProgressStore progressStore,
        QuestWorld world,
        QuestSync questSync
    ) {
        this(catalog, TASKS.build(), REWARDS.build(), progressStore, world, questSync);
    }

    public QuestRuntime(
        QuestCatalog catalog,
        TaskEngine taskEngine,
        ProgressStore progressStore,
        QuestWorld world,
        QuestSync questSync
    ) {
        this(catalog, taskEngine, REWARDS.build(), progressStore, world, questSync);
    }

    public QuestRuntime(
        QuestCatalog catalog,
        TaskEngine taskEngine,
        RewardEngine rewardEngine,
        ProgressStore progressStore,
        QuestWorld world,
        QuestSync questSync
    ) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.taskEngine = java.util.Objects.requireNonNull(taskEngine, "taskEngine");
        this.rewardEngine = java.util.Objects.requireNonNull(rewardEngine, "rewardEngine");
        this.progressStore = java.util.Objects.requireNonNull(progressStore, "progressStore");
        this.world = java.util.Objects.requireNonNull(world, "world");
        this.questSync = java.util.Objects.requireNonNull(questSync, "questSync");
    }

    public static QuestRuntime create(MinecraftServer server) {
        long initializationStarted = System.nanoTime();
        Theseus.LOGGER.info("Initializing Theseus quest runtime");
        taskHandlersLocked = true;
        RewardEngine rewards = lockRewardHandlers();
        ServerQuestWorld world = new ServerQuestWorld(
            server,
            FMLPaths.CONFIGDIR.get()
        );
        long catalogStarted = System.nanoTime();
        QuestCatalog catalog = world.loadCatalog();
        long catalogMillis = elapsedMillis(catalogStarted);
        Theseus.LOGGER.info(
            "Quest catalog phase completed in {} ms ({} quests, {} validation issues)",
            catalogMillis,
            catalog.quests().size(),
            catalog.issues().size()
        );
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TASKS.build(),
            rewards,
            new FileProgressStore(
                server
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("data/theseus_progress.json")
            ),
            world,
            new PacketQuestSync()
        );
        long progressStarted = System.nanoTime();
        runtime.loadProgress();
        Theseus.LOGGER.info(
            "Theseus quest runtime ready in {} ms (catalog={} ms, progress={} ms)",
            elapsedMillis(initializationStarted),
            catalogMillis,
            elapsedMillis(progressStarted)
        );
        return runtime;
    }

    /** Registers an additional task handler. Call during mod initialization, before a server starts. */
    public static void registerTaskHandler(
        String type,
        TaskEngine.Handler handler
    ) {
        if (taskHandlersLocked) throw new IllegalStateException(
            "Task handlers must be registered before the server starts"
        );
        TASKS.register(type, handler);
    }

    /** Registers an additional reward executor. Call during mod initialization, before a server starts. */
    public static synchronized void registerRewardHandler(
        String type,
        RewardEngine.Handler handler
    ) {
        if (rewardHandlersLocked) throw new IllegalStateException(
            "Reward handlers must be registered before the server starts"
        );
        REWARDS.register(type, handler);
    }

    private static synchronized RewardEngine lockRewardHandlers() {
        rewardHandlersLocked = true;
        return REWARDS.build();
    }

    public void close() {
        saveProgress();
    }

    public int reload() {
        catalog = world.loadCatalog();
        restoreDeferredProgress();
        world.onlinePlayers().forEach(player -> sync(player, false));
        return catalog.quests().size();
    }

    /**
    * Applies one acknowledged editor mutation.  This is the single entry
    * point for authoring requests, so edit permission is checked once before
    * the exhaustive mutation dispatch.
    */
    public MutationResult applyEditorMutation(ServerPlayer player, QuestMutation mutation) {
        return mutations.applyEditorMutation(player, mutation);
    }

    MutationResult applyEditorMutationLazy(
        ServerPlayer player,
        Supplier<QuestMutation> mutationSupplier
    ) {
        if (!world.canEdit(player)) {
            return MutationResult.failure("You do not have permission to edit quests");
        }
        return mutations.applyEditorMutation(player, mutationSupplier.get());
    }

    public MutationResult createQuest(ServerPlayer player, JsonObject draft) {
        return mutations.createQuest(player, draft);
    }

    MutationResult createQuest(JsonObject draft) {
        return mutations.createQuest(draft);
    }

    public MutationResult updateQuest(ServerPlayer player, JsonObject draft) {
        return mutations.updateQuest(player, draft);
    }

    MutationResult updateQuest(JsonObject draft) {
        return mutations.updateQuest(draft);
    }

    MutationResult createDocumentQuest(JsonObject request) {
        return mutations.createDocumentQuest(request);
    }

    MutationResult updateDocumentQuest(JsonObject request) {
        return mutations.updateDocumentQuest(request);
    }

    static boolean hasCanonicalDocument(JsonObject request) {
        return QuestRuntimeMutations.hasCanonicalDocument(request);
    }

    static boolean isString(JsonObject object, String key) {
        return QuestRuntimeMutations.isString(object, key);
    }

    static JsonObject authoredDocument(JsonObject source) {
        return QuestRuntimeMutations.authoredDocument(source);
    }

    static void applyPlacement(JsonObject root, JsonObject request) {
        QuestRuntimeMutations.applyPlacement(root, request);
    }

    static JsonObject object(JsonObject root, String key) {
        return QuestRuntimeMutations.object(root, key);
    }

    /** Imports a validated batch. No file is created unless every entry passes preflight. */
    public MutationResult importQuests(ServerPlayer player, JsonObject request) {
        return mutations.importQuests(player, request);
    }

    MutationResult importQuests(JsonObject request) {
        return mutations.importQuests(request);
    }

    /** Clones or moves a quest snapshot, or adds an existing quest to a chapter. */
    public MutationResult pasteQuest(ServerPlayer player, JsonObject request) {
        return mutations.pasteQuest(player, request);
    }

    MutationResult pasteQuest(JsonObject request) {
        return mutations.pasteQuest(request);
    }

    static void addChapterPlacement(JsonObject root, String chapter, JsonObject request) {
        QuestRuntimeMutations.addChapterPlacement(root, chapter, request);
    }

    static MutationResult validateDraftDisplay(JsonObject draft, JsonObject changedFields) {
        return QuestRuntimeMutations.validateDraftDisplay(draft, changedFields);
    }

    static String firstValidationError(QuestDefinition definition) {
        return QuestRuntimeMutations.firstValidationError(definition);
    }

    MutationResult validateQuest(String id, JsonObject root) {
        return mutations.validateQuest(id, root);
    }

    static String warningSuffix(String warnings) {
        return QuestRuntimeMutations.warningSuffix(warnings);
    }

    public record MutationResult(boolean success, String message, List<QuestDiagnostics.Diagnostic> diagnostics) {
        public MutationResult(boolean success, String message) { this(success, message, List.of()); }
        public static MutationResult success(String message) { return new MutationResult(true, message); }
        public static MutationResult success(String message, List<QuestDiagnostics.Diagnostic> diagnostics) { return new MutationResult(true, message, List.copyOf(diagnostics)); }
        public static MutationResult failure(String message) { return new MutationResult(false, message); }
        public static MutationResult failure(String message, List<QuestDiagnostics.Diagnostic> diagnostics) { return new MutationResult(false, message, List.copyOf(diagnostics)); }
    }

    public record QuestFileResult(boolean success, String message, String relativePath) {
        public static QuestFileResult success(String relativePath) {
            return new QuestFileResult(true, "Quest file resolved", relativePath);
        }

        public static QuestFileResult failure(String message) {
            return new QuestFileResult(false, message, "");
        }
    }

    public QuestFileResult openQuestFileResult(ServerPlayer player, String questId) {
        return mutations.openQuestFileResult(player, questId);
    }

    public void deleteQuest(ServerPlayer player, String id) {
        mutations.deleteQuest(player, id);
    }

    public MutationResult deleteQuestResult(ServerPlayer player, String id) {
        return mutations.deleteQuestResult(player, id);
    }

    MutationResult deleteQuestResult(JsonObject request) {
        return mutations.deleteQuestResult(request);
    }

    MutationResult deleteQuestResult(String id) {
        return mutations.deleteQuestResult(id);
    }

    public MutationResult chapterMutationResult(ServerPlayer player, JsonObject action) {
        return mutations.chapterMutationResult(player, action);
    }

    MutationResult chapterMutationResult(JsonObject action) {
        return mutations.chapterMutationResult(action);
    }

    public MutationResult removeQuestGroupResult(ServerPlayer player, JsonObject action) {
        return mutations.removeQuestGroupResult(player, action);
    }

    MutationResult removeQuestGroupResult(JsonObject action) {
        return mutations.removeQuestGroupResult(action);
    }

    public MutationResult dependencyMutationResult(ServerPlayer player, JsonObject action) {
        return mutations.dependencyMutationResult(player, action);
    }

    MutationResult dependencyMutationResultAuthorized(ServerPlayer player, JsonObject action) {
        return mutations.dependencyMutationResultAuthorized(player, action);
    }

    public MutationResult resetProgressResult(ServerPlayer player, JsonObject action) {
        return mutations.resetProgressResult(player, action);
    }

    MutationResult resetProgressResultAuthorized(ServerPlayer player, JsonObject action) {
        return mutations.resetProgressResultAuthorized(player, action);
    }

    public void removeQuestFromGroup(ServerPlayer player, String id, String group) {
        mutations.removeQuestFromGroup(player, id, group);
    }

    void removeQuestFromGroupAuthorized(String id, String group) {
        mutations.removeQuestFromGroupAuthorized(id, group);
    }

    public void chapterAction(ServerPlayer player, JsonObject action) {
        mutations.chapterAction(player, action);
    }

    void chapterActionAuthorized(JsonObject action) {
        mutations.chapterActionAuthorized(action);
    }

    static String validChapterName(String value) {
        return QuestRuntimeMutations.validChapterName(value);
    }

    static QuestCatalog.ChapterSettings chapterSettings(JsonObject action) {
        return QuestRuntimeMutations.chapterSettings(action);
    }

    void resetQuestProgress(String oldId, String newId) {
        mutations.resetQuestProgress(oldId, newId);
    }

    void migrateQuestProgress(String oldId, String newId) {
        mutations.migrateQuestProgress(oldId, newId);
    }

    public MutationResult setDependency(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        return mutations.setDependency(player, prerequisiteId, dependentId, remove);
    }

    MutationResult setDependencyAuthorized(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        return mutations.setDependencyAuthorized(player, prerequisiteId, dependentId, remove);
    }


    public List<QuestDefinition.ValidationIssue> validationIssues() {
        return mutations.validationIssues();
    }

    public void initialize(ServerPlayer player) {
        progression.initialize(player);
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        return progression.triggerDummy(player, value);
    }

    public String lockedDummyReason(ServerPlayer player, String value) {
        return progression.lockedDummyReason(player, value);
    }

    public void updateInventoryTasks(ServerPlayer player) {
        progression.updateInventoryTasks(player);
    }

    public boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        return progression.signal(player, signal);
    }

    public boolean submit(ServerPlayer player, String questId, String taskId) {
        return progression.submit(player, questId, taskId);
    }

    static QuestDefinition.Task resolveTask(
        Map<String, QuestDefinition.Task> tasks,
        String path
    ) {
        return QuestRuntimeProgression.resolveTask(tasks, path);
    }

    void refreshCompositeProgress(
        ServerPlayer player,
        QuestDefinition quest
    ) {
        progression.refreshCompositeProgress(player, quest);
    }

    boolean updateCompositeSummary(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey
    ) {
        return progression.updateCompositeSummary(player, quest, task, progressKey);
    }

    public boolean claim(ServerPlayer player, String questId) {
        return progression.claim(player, questId);
    }

    public boolean claim(
        ServerPlayer player,
        String questId,
        Map<String, List<String>> selections
    ) {
        return progression.claim(player, questId, selections);
    }

    public boolean togglePinned(ServerPlayer player, String questId) {
        return progression.togglePinned(player, questId);
    }

    public void reset(ServerPlayer player) {
        progression.reset(player);
    }

    public boolean isUnlocked(ServerPlayer player, QuestDefinition quest) {
        return progression.isUnlocked(player, quest);
    }

    public boolean isComplete(ServerPlayer player, QuestDefinition quest) {
        return progression.isComplete(player, quest);
    }

    boolean setTaskProgress(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        int value
    ) {
        return progression.setTaskProgress(player, quest, task, progressKey, value);
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        TaskEngine.Signal signal
    ) {
        return progression.applyTask(player, quest, task, signal);
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal
    ) {
        return progression.applyTask(player, quest, task, progressKey, signal);
    }

    double taskFraction(
        ServerPlayer player,
        String questId,
        QuestDefinition.Task task,
        String progressKey
    ) {
        return progression.taskFraction(player, questId, task, progressKey);
    }

    void updatePassiveTasks(ServerPlayer player) {
        progression.updatePassiveTasks(player);
    }

    static void consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount
    ) {
        QuestRuntimeProgression.consume(player, task, amount);
    }

    boolean canClaimReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected
    ) {
        return progression.canClaimReward(player, reward, selected);
    }

    void grantReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected,
        List<String> granted
    ) {
        progression.grantReward(player, reward, selected, granted);
    }

    static boolean validIdentifier(String value) {
        return QuestRuntimeProgression.validIdentifier(value);
    }

    static TaskEngine.Signal.WorldState playerState(
        ServerPlayer player
    ) {
        return QuestRuntimeProgression.playerState(player);
    }

    static TaskEngine.Signal.Inventory inventory(
        ServerPlayer player,
        boolean submit
    ) {
        return QuestRuntimeProgression.inventory(player, submit);
    }

    public static TaskEngine.Signal.RegistryEntry itemEntry(
        ServerPlayer player,
        ItemStack stack
    ) {
        JsonObject data = new JsonObject();
        ItemStack.CODEC.encodeStart(
            player
                .registryAccess()
                .createSerializationContext(JsonOps.INSTANCE),
            stack
        )
            .result()
            .filter(com.google.gson.JsonElement::isJsonObject)
            .map(com.google.gson.JsonElement::getAsJsonObject)
            .ifPresent(encoded -> {
                if (
                    encoded.has("components") &&
                    encoded.get("components").isJsonObject()
                ) data.add("components", encoded.get("components"));
                if (
                    encoded.has("components") &&
                    encoded.get("components").isJsonObject()
                ) {
                    encoded
                        .getAsJsonObject("components")
                        .entrySet()
                        .forEach(entry ->
                            data.add(entry.getKey(), entry.getValue())
                        );
                }
            });
        return registryEntry(stack.typeHolder(), data, stack.getCount());
    }

    public static <T> TaskEngine.Signal.RegistryEntry registryEntry(
        net.minecraft.core.Holder<T> holder,
        JsonObject data,
        int count
    ) {
        String id = holder
            .unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("");
        Set<String> tags = holder
            .tags()
            .map(tag -> tag.location().toString())
            .collect(Collectors.toSet());
        return new TaskEngine.Signal.RegistryEntry(id, tags, data, count);
    }

    Set<TaskEngine.Signal.RegistryEntry> structuresAt(
        ServerPlayer player
    ) {
        return progression.structuresAt(player);
    }

    static JsonObject playerData(ServerPlayer player) {
        return QuestRuntimeProgression.playerData(player);
    }

    static java.util.List<String> configuredStrings(
        QuestDefinition.Task task,
        String key,
        String fallback
    ) {
        return QuestRuntimeProgression.configuredStrings(task, key, fallback);
    }

    static List<QuestDefinition.Task> flattenTasks(
        Map<String, QuestDefinition.Task> tasks
    ) {
        return QuestRuntimeProgression.flattenTasks(tasks);
    }

    QuestProgressState progress(ServerPlayer player, String questId) {
        return progress
            .computeIfAbsent(world.playerId(player), ignored -> new HashMap<>())
            .computeIfAbsent(questId, ignored -> new QuestProgressState());
    }

    void changed(ServerPlayer player) {
        saveProgress();
        sync(player, false);
    }

    void changed(
        ServerPlayer player,
        Map<String, Boolean> wasUnlocked,
        Map<String, Boolean> wasComplete
    ) {
        saveProgress();
        sync(player, false);
        if (suppressNotifications.contains(world.playerId(player))) return;
        for (QuestDefinition quest : catalog.quests().values()) {
            boolean unlocked = isUnlocked(player, quest);
            boolean complete = isComplete(player, quest);
            if (
                !wasComplete.getOrDefault(quest.id(), false) && complete
            ) notify(player, "complete", "Quest completed", quest.title());
            if (
                !wasUnlocked.getOrDefault(quest.id(), false) &&
                unlocked &&
                quest.settings().unlockNotification()
            ) notify(player, "unlock", "Quest unlocked", quest.title());
        }
    }

    Map<String, Boolean> questStates(
        ServerPlayer player,
        boolean complete
    ) {
        Map<String, Boolean> states = new HashMap<>();
        catalog
            .quests()
            .forEach((id, quest) ->
                states.put(
                    id,
                    complete
                        ? isComplete(player, quest)
                        : isUnlocked(player, quest)
                )
            );
        return states;
    }

    void notify(
        ServerPlayer player,
        String kind,
        String title,
        String detail
    ) {
        questSync.notification(player, kind, title, detail);
    }

    public void sync(ServerPlayer player, boolean open) {
        questSync.snapshot(player, snapshot(player, null), open);
    }

    public void syncChapter(ServerPlayer player, String chapter) {
        if (chapter == null || !catalog.groupOrder().contains(chapter)) return;
        questSync.snapshot(player, snapshot(player, chapter), false);
    }

    /**
    * Creates either the lightweight quest index or the full contents for one
    * chapter. The index is enough to draw the graph and resolve lock states;
    * task, reward, and description data is sent only for a requested chapter.
    */
    String snapshot(ServerPlayer player, String chapter) {
        JsonObject root = new JsonObject();
        JsonObject editorTypes = new JsonObject();
        java.util.Set<String> taskTypes = new java.util.LinkedHashSet<>(taskEngine.types());
        // Composite tasks are evaluated structurally by QuestRuntime, not by a TaskEngine handler.
        taskTypes.add("theseus:composite");
        editorTypes.add("tasks", GSON.toJsonTree(taskTypes));
        java.util.Set<String> rewardTypes = new java.util.LinkedHashSet<>(List.of(
            "theseus:xp", "theseus:item", "theseus:loottable", "theseus:command", "theseus:selectable"
        ));
        rewardTypes.addAll(rewardEngine.types());
        editorTypes.add("rewards", GSON.toJsonTree(rewardTypes));
        editorTypes.add("icons", GSON.toJsonTree(QuestIconTypes.types()));
        root.add("__editor_types", editorTypes);
        JsonObject chapters = new JsonObject();
        chapters.add("order", GSON.toJsonTree(catalog.groupOrder()));
        chapters.add("settings", GSON.toJsonTree(catalog.chapterSettings()));
        root.add("__chapters", chapters);
        root.addProperty("__snapshot_kind", chapter == null ? "index" : "chapter");
        if (chapter != null) root.addProperty("__chapter", chapter);
        for (QuestDefinition quest : catalog.quests().values()) {
            if (chapter != null && !quest.display().groups().containsKey(chapter)) continue;
            JsonObject json = chapter == null
                ? lightweightQuest(quest)
                : fullQuest(quest);
            QuestProgressState state = progress(player, quest.id());
            json.addProperty("unlocked", isUnlocked(player, quest));
            json.addProperty("complete", isComplete(player, quest));
            json.addProperty("claimed", state.allRewardsClaimed(quest));
            json.addProperty("pinned", state.isPinned());
            if (chapter != null) {
                json.add("claimed_rewards", GSON.toJsonTree(state.claimedRewards()));
                json.add("progress", GSON.toJsonTree(state.taskProgress()));
            }
            root.add(quest.id(), json);
        }
        return GSON.toJson(root);
    }

    JsonObject fullQuest(QuestDefinition quest) {
        try {
            return catalog.rawQuest(quest.id());
        } catch (Exception ignored) {
            return GSON.toJsonTree(quest).getAsJsonObject();
        }
    }

    static JsonObject lightweightQuest(QuestDefinition quest) {
        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        display.add("icon", quest.display().icon().source());
        display.addProperty("icon_background", quest.display().iconBackground());
        display.addProperty("icon_size", quest.display().iconSize());
        display.addProperty("title", quest.display().title());
        display.addProperty("subtitle", quest.display().subtitle());
        JsonObject groups = new JsonObject();
        quest.display().groups().forEach((name, position) -> {
            JsonObject placement = new JsonObject();
            JsonArray coordinates = new JsonArray();
            coordinates.add(position.x());
            coordinates.add(position.y());
            placement.add("position", coordinates);
            groups.add(name, placement);
        });
        display.add("groups", groups);
        root.add("display", display);

        JsonObject settings = new JsonObject();
        settings.addProperty(
            "hidden",
            quest.settings().hiddenUntil().name().toLowerCase(java.util.Locale.ROOT)
        );
        settings.addProperty("showDependencyArrow", quest.settings().showDependencyArrow());
        root.add("settings", settings);

        JsonArray dependencies = new JsonArray();
        quest.dependencies().forEach(dependencies::add);
        root.add("dependencies", dependencies);
        return root;
    }

    void loadProgress() {
        long loadStarted = System.nanoTime();
        Theseus.LOGGER.info("Loading Theseus player progress from {}", progressStore);
        progressLoadFailed = true;
        try {
            JsonObject storedProgress = progressStore.load();
            long fileReadMillis = elapsedMillis(loadStarted);
            Theseus.LOGGER.info(
                "Read progress data for {} player records in {} ms",
                storedProgress.size(),
                fileReadMillis
            );
            storedProgress.entrySet().forEach(player -> {
                try {
                    if (!player.getValue().isJsonObject()) throw new IllegalArgumentException("Player progress must be an object");
                    UUID playerId = UUID.fromString(player.getKey());
                    Map<String, JsonElement> deferred = new HashMap<>();
                    progress.put(playerId, parsePlayerProgress(player.getValue().getAsJsonObject(), deferred));
                    if (!deferred.isEmpty()) deferredProgress.put(playerId, deferred);
                } catch (RuntimeException exception) {
                    Theseus.LOGGER.warn("Ignoring malformed progress for player '{}': {}", player.getKey(), exception.getMessage());
                }
            });
            progressLoadFailed = false;
            // Re-emit legacy entries in the current explicit shape, while
            // retaining valid progress from other players.
            saveProgress();
            Theseus.LOGGER.info(
                "Loaded Theseus progress in {} ms ({} players, {} quest states, {} deferred quest states)",
                elapsedMillis(loadStarted),
                progress.size(),
                progress.values().stream().mapToInt(Map::size).sum(),
                deferredProgress.values().stream().mapToInt(Map::size).sum()
            );
        } catch (Exception exception) {
            Theseus.LOGGER.error(
                "Failed to load quest progress from {} after {} ms",
                progressStore,
                elapsedMillis(loadStarted),
                exception
            );
        }
    }

    private Map<String, QuestProgressState> parsePlayerProgress(JsonObject root, Map<String, JsonElement> deferred) {
        Map<String, QuestProgressState> result = new HashMap<>();
        root.entrySet().forEach(entry -> {
            QuestDefinition quest = catalog.quests().get(entry.getKey());
            if (quest == null) {
                if (catalog.failedQuestIds().contains(entry.getKey())) {
                    deferred.put(entry.getKey(), entry.getValue().deepCopy());
                    Theseus.LOGGER.warn("Deferring progress for quest '{}' because its quest file failed to load", entry.getKey());
                } else {
                    Theseus.LOGGER.warn("Ignoring progress for unknown quest '{}'", entry.getKey());
                }
                return;
            }
            try {
                if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Progress entry must be an object");
                result.put(entry.getKey(), QuestProgressState.fromJson(quest, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException exception) {
                Theseus.LOGGER.warn("Ignoring malformed progress for quest '{}': {}", entry.getKey(), exception.getMessage());
            }
        });
        return result;
    }

    private void restoreDeferredProgress() {
        deferredProgress.forEach((playerId, quests) -> {
            var iterator = quests.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonElement> entry = iterator.next();
                QuestDefinition quest = catalog.quests().get(entry.getKey());
                if (quest == null) {
                    if (!catalog.failedQuestIds().contains(entry.getKey())) iterator.remove();
                    continue;
                }
                try {
                    if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Progress entry must be an object");
                    QuestProgressState state = QuestProgressState.fromJson(quest, entry.getValue().getAsJsonObject());
                    progress.computeIfAbsent(playerId, ignored -> new HashMap<>()).putIfAbsent(entry.getKey(), state);
                    iterator.remove();
                } catch (RuntimeException exception) {
                    Theseus.LOGGER.warn("Keeping deferred progress for quest '{}': {}", entry.getKey(), exception.getMessage());
                }
            }
        });
        deferredProgress.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    void saveProgress() {
        if (progressLoadFailed) return;
        try {
            JsonObject root = new JsonObject();
            Set<UUID> playerIds = new java.util.HashSet<>(progress.keySet());
            playerIds.addAll(deferredProgress.keySet());
            playerIds.forEach(playerId -> {
                JsonObject player = new JsonObject();
                Map<String, JsonElement> deferred = deferredProgress.get(playerId);
                if (deferred != null) {
                    deferred.forEach((questId, state) -> player.add(questId, state.deepCopy()));
                }
                Map<String, QuestProgressState> quests = progress.get(playerId);
                if (quests != null) {
                    quests.forEach((questId, state) -> player.add(questId, state.toJson()));
                }
                root.add(playerId.toString(), player);
            });
            progressStore.save(root);
        } catch (Exception exception) {
            Theseus.LOGGER.error(
                "Failed to save quest progress to {}",
                progressStore,
                exception
            );
        }
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

}
