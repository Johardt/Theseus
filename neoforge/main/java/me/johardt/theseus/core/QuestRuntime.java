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
    final PartyLookup parties;
    private JsonObject legacyPartyProgress = new JsonObject();
    QuestCatalog catalog;
    final Map<UUID, Map<String, QuestProgressState>> progress =
        new HashMap<>();
    /** Raw progress held until a quest that failed catalog loading is available again. */
    final Map<UUID, Map<String, JsonElement>> deferredProgress = new HashMap<>();
    final Set<UUID> suppressNotifications = new java.util.HashSet<>();
    private final Map<UUID, PartyLookup.Party> visibleParties = new HashMap<>();
    private final Set<UUID> failedPartyLookups = new java.util.HashSet<>();
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
        this(catalog, taskEngine, rewardEngine, progressStore, world, questSync, PartyLookup.NONE);
    }

    public QuestRuntime(
        QuestCatalog catalog,
        TaskEngine taskEngine,
        RewardEngine rewardEngine,
        ProgressStore progressStore,
        QuestWorld world,
        QuestSync questSync,
        PartyLookup parties
    ) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.taskEngine = java.util.Objects.requireNonNull(taskEngine, "taskEngine");
        this.rewardEngine = java.util.Objects.requireNonNull(rewardEngine, "rewardEngine");
        this.progressStore = java.util.Objects.requireNonNull(progressStore, "progressStore");
        this.world = java.util.Objects.requireNonNull(world, "world");
        this.questSync = java.util.Objects.requireNonNull(questSync, "questSync");
        this.parties = java.util.Objects.requireNonNull(parties, "parties");
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
            new PacketQuestSync(),
            net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")
                ? new me.johardt.theseus.compat.opac.OpacPartyLookup(server)
                : PartyLookup.NONE
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
        if (!partyProgressReady(player)) return;
        reconcilePartyProgress(player);
        progression.initialize(player);
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        if (!partyProgressReady(player)) return false;
        reconcilePartyProgress(player);
        return progression.triggerDummy(player, value);
    }

    public String lockedDummyReason(ServerPlayer player, String value) {
        return progression.lockedDummyReason(player, value);
    }

    public void updateInventoryTasks(ServerPlayer player) {
        if (!partyProgressReady(player)) return;
        reconcilePartyProgress(player);
        refreshPartyMembership(player);
        progression.updateInventoryTasks(player);
    }

    void refreshPartyMembership(ServerPlayer player) {
        if (!parties.available()) return;
        PartyLookup.Party party = parties.find(world.playerId(player));
        PartyLookup.Party previous = visibleParties.put(world.playerId(player), party);
        if (!java.util.Objects.equals(previous, party)) sync(player, false);
    }

    public boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        if (!partyProgressReady(player)) return false;
        reconcilePartyProgress(player);
        return progression.signal(player, signal);
    }

    public boolean submit(ServerPlayer player, String questId, String taskId) {
        if (!partyProgressReady(player)) return false;
        reconcilePartyProgress(player);
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
        if (partyProgressReady(player)) reconcilePartyProgress(player);
        return progression.claim(player, questId);
    }

    public boolean claim(
        ServerPlayer player,
        String questId,
        Map<String, List<String>> selections
    ) {
        if (partyProgressReady(player)) reconcilePartyProgress(player);
        return progression.claim(player, questId, selections);
    }

    public boolean togglePinned(ServerPlayer player, String questId) {
        return progression.togglePinned(player, questId);
    }

    public void reset(ServerPlayer player) {
        if (!partyProgressReady(player)) return;
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
        return progress(world.playerId(player), questId);
    }

    QuestProgressState progress(UUID playerId, String questId) {
        return progress
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(questId, ignored -> new QuestProgressState());
    }

    PartyLookup.Party progressParty(UUID player, QuestDefinition quest) {
        if (quest.settings().individualProgress() || !parties.available()) return null;
        PartyLookup.Party party = parties.find(player);
        if (party != null && !party.members().contains(player)) throw new IllegalStateException("Player is not in party roster");
        return party;
    }

    boolean partyProgressReady(ServerPlayer player) {
        if (!parties.available() || catalog.quests().values().stream().allMatch(quest -> quest.settings().individualProgress())) return true;
        UUID id = world.playerId(player);
        try {
            parties.find(id);
            failedPartyLookups.remove(id);
            return true;
        } catch (RuntimeException exception) {
            if (failedPartyLookups.add(id)) {
                Theseus.LOGGER.error("OPAC lookup failed; quest contributions paused for {}", id, exception);
                world.message(player, "OPAC party progress is temporarily unavailable. Quest contributions are paused; try again later.");
            }
            return false;
        }
    }

    boolean prerequisitesMet(UUID player, QuestDefinition quest) {
        return prerequisitesMet(player, quest, new java.util.HashSet<>());
    }

    private boolean prerequisitesMet(UUID player, QuestDefinition quest, Set<String> visiting) {
        if (!visiting.add(quest.id())) return false;
        boolean met = quest.dependencies().stream().allMatch(id -> {
            QuestDefinition required = catalog.quests().get(id);
            return required != null && tasksComplete(progress(player, id), required) && prerequisitesMet(player, required, visiting);
        });
        visiting.remove(quest.id());
        return met;
    }

    void resetTasks(ServerPlayer player, QuestDefinition quest, String path) {
        resetTasks(player, quest, path, false);
    }

    void resetTasks(ServerPlayer player, QuestDefinition quest, String path, boolean resetRewards) {
        PartyLookup.Party party = progressParty(world.playerId(player), quest);
        Set<UUID> members = party == null ? Set.of(world.playerId(player)) : party.members();
        for (UUID member : members) {
            QuestProgressState state = progress(member, quest.id());
            if (path == null) state.clearTasks();
            else state.resetTaskPath(path);
            if (resetRewards) {
                state.clearProgress();
                state.pendingRewards().forEach(state::unmarkRewardClaimed);
            }
            // Reset composite parents too, so an explicit reset can decrease their summaries.
            if (path != null) {
                String parent = path;
                while (parent.contains("/")) {
                    parent = parent.substring(0, parent.lastIndexOf('/'));
                    state.setTaskProgress(parent, 0);
                }
                refreshSharedComposites(state, quest.tasks(), "");
            }
        }
    }

    void syncPartyProgress(ServerPlayer player) {
        if (!parties.available()) return;
        PartyLookup.Party party;
        try {
            party = parties.find(world.playerId(player));
        } catch (RuntimeException exception) {
            Theseus.LOGGER.warn("Could not sync party progress: {}", exception.toString());
            return;
        }
        if (party == null) return;
        for (ServerPlayer member : world.onlinePlayers()) {
            if (!world.playerId(member).equals(world.playerId(player)) && party.members().contains(world.playerId(member))) sync(member, false);
        }
    }

    void changed(ServerPlayer player) {
        saveProgress();
        sync(player, false);
        syncPartyProgress(player);
    }

    void changed(
        ServerPlayer player,
        Map<String, Boolean> wasUnlocked,
        Map<String, Boolean> wasComplete
    ) {
        reconcilePartyProgress(player);
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!wasComplete.getOrDefault(quest.id(), false) && isComplete(player, quest)) recordCompletion(world.playerId(player), quest);
        }
        saveProgress();
        sync(player, false);
        syncPartyProgress(player);
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
        boolean partyReady = partyProgressReady(player);
        if (partyReady) reconcilePartyProgress(player);
        JsonObject root = new JsonObject();
        root.add("__party", partyContext(world.playerId(player)));
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
            json.addProperty("reward_eligible", rewardEligible(player, quest));
            PartyLookup.Party progressParty = partyReady ? progressParty(world.playerId(player), quest) : null;
            json.addProperty("progress_scope", progressParty == null ? "individual" : "shared");
            json.addProperty("progress_party", progressParty == null ? "" : progressParty.name());
            json.remove("party_reward_source");
            if (state.partyRewardSource() != null) {
                json.addProperty("party_reward_source", state.partyRewardSource().partyName());
            }
            json.add("pending_rewards", GSON.toJsonTree(state.pendingRewards()));
            json.addProperty("reward_claim_pending", state.pendingRewards().stream().anyMatch(quest.rewards()::containsKey));
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
            JsonObject json = catalog.rawQuest(quest.id());
            if (json.has("settings") && json.get("settings").isJsonObject()) json.getAsJsonObject("settings").remove("reward_audience");
            return json;
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
        settings.addProperty("individual_progress", quest.settings().individualProgress());

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
            int loadedVersion = storedProgress.has("version") ? storedProgress.get("version").getAsInt() : 1;
            if (storedProgress.has("version")) {
                int version = storedProgress.get("version").getAsInt();
                if ((version != 2 && version != 3 && version != 4) || !storedProgress.has("players")
                    || !storedProgress.get("players").isJsonObject()) {
                    throw new IllegalArgumentException("Unsupported progress document version");
                }
                if (storedProgress.has("parties")) legacyPartyProgress = storedProgress.getAsJsonObject("parties").deepCopy();
                if (storedProgress.has("legacy_parties")) legacyPartyProgress = storedProgress.getAsJsonObject("legacy_parties").deepCopy();
                storedProgress = storedProgress.getAsJsonObject("players");
            }
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
            if (loadedVersion < 4) deferredProgress.values().forEach(quests -> quests.values().forEach(value -> {
                if (value.isJsonObject() && value.getAsJsonObject().has("party_reward_source")) {
                    value.getAsJsonObject().addProperty("legacy_reward_eligible", true);
                }
            }));
            migrateLegacyRecipients(loadedVersion < 4);
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
                deferred.put(entry.getKey(), entry.getValue().deepCopy());
                return;
            }
            try {
                if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Progress entry must be an object");
                QuestProgressState state = QuestProgressState.fromJson(quest, entry.getValue().getAsJsonObject());
                if (entry.getValue().getAsJsonObject().has("legacy_reward_eligible")
                    && entry.getValue().getAsJsonObject().get("legacy_reward_eligible").getAsBoolean()) completeLegacyTasks(state, quest.tasks(), "");
                result.put(entry.getKey(), state);
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
                    continue;
                }
                try {
                    if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Progress entry must be an object");
                    QuestProgressState state = QuestProgressState.fromJson(quest, entry.getValue().getAsJsonObject());
                    if (entry.getValue().getAsJsonObject().has("legacy_reward_eligible")
                        && entry.getValue().getAsJsonObject().get("legacy_reward_eligible").getAsBoolean()) completeLegacyTasks(state, quest.tasks(), "");
                    progress.computeIfAbsent(playerId, ignored -> new HashMap<>()).putIfAbsent(entry.getKey(), state);
                    iterator.remove();
                } catch (RuntimeException exception) {
                    Theseus.LOGGER.warn("Keeping deferred progress for quest '{}': {}", entry.getKey(), exception.getMessage());
                }
            }
        });
        deferredProgress.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    boolean saveProgress() {
        if (progressLoadFailed) return false;
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
            JsonObject document = new JsonObject();
            document.addProperty("version", 4);
            document.add("players", root);
            if (!legacyPartyProgress.isEmpty()) document.add("legacy_parties", legacyPartyProgress.deepCopy());
            progressStore.save(document);
            return true;
        } catch (Exception exception) {
            Theseus.LOGGER.error(
                "Failed to save quest progress to {}",
                progressStore,
                exception
            );
            return false;
        }
    }

    public boolean rewardEligible(ServerPlayer player, QuestDefinition quest) {
        return !quest.rewards().isEmpty() && isComplete(player, quest) && isUnlocked(player, quest);
    }

    void baselineCompletions(ServerPlayer player) {
        for (QuestDefinition quest : catalog.quests().values()) {
            if (isComplete(player, quest)) progress(player, quest.id()).recordCompletion();
        }
    }

    void recordCompletion(UUID completer, QuestDefinition quest) {
        progress(completer, quest.id()).recordCompletion();
    }

    /** Reconcile shared achievements by maximum, never adding potentially duplicated history. */
    void reconcilePartyProgress(ServerPlayer player) {
        if (!parties.available()) return;
        PartyLookup.Party party = parties.find(world.playerId(player));
        if (party == null) return;
        if (!party.members().contains(world.playerId(player))) throw new IllegalStateException("Player is not in party roster");
        Map<UUID, Map<String, Boolean>> wasComplete = new HashMap<>();
        for (ServerPlayer online : world.onlinePlayers()) {
            UUID member = world.playerId(online);
            if (!party.members().contains(member) || member.equals(world.playerId(player))) continue;
            Map<String, Boolean> completed = new HashMap<>();
            catalog.quests().forEach((id, quest) -> completed.put(id, tasksComplete(progress(member, id), quest)));
            wasComplete.put(member, completed);
        }
        JsonObject legacy = legacyPartyProgress.getAsJsonObject(party.id().toString());
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (quest.settings().individualProgress()) continue;
            Map<String, Integer> maximum = new HashMap<>();
            for (UUID member : party.members()) progress(member, quest.id()).taskProgress().forEach((path, value) -> maximum.merge(path, value, Math::max));
            if (legacy != null && legacy.has(quest.id())) {
                QuestProgressState old = QuestProgressState.fromJson(quest, legacy.getAsJsonObject(quest.id()));
                old.taskProgress().forEach((path, value) -> maximum.merge(path, value, Math::max));
                // Preserve completed legacy recipients even if they already left the party.
                for (var member : progress.entrySet()) {
                    QuestProgressState state = member.getValue().get(quest.id());
                    if (state != null && state.partyRewardSource() != null && state.partyRewardSource().partyId().equals(party.id())) {
                        changed |= mergeSharedTasks(state, quest, maximum);
                    }
                }
                legacy.remove(quest.id());
                changed = true;
            }
            var source = new QuestProgressState.PartyRewardSource(party.id(), party.name(), world.playerId(player));
            for (UUID member : party.members()) {
                QuestProgressState state = progress(member, quest.id());
                boolean copied = mergeSharedTasks(state, quest, maximum);
                if (copied || maximum.values().stream().anyMatch(value -> value > 0)) changed |= state.earnPartyRewards(source);
                changed |= copied;
                if (tasksComplete(state, quest)) state.recordCompletion();
            }
        }
        if (legacy != null && legacy.isEmpty()) legacyPartyProgress.remove(party.id().toString());
        if (changed) {
            saveProgress();
            for (ServerPlayer online : world.onlinePlayers()) {
                UUID member = world.playerId(online);
                Map<String, Boolean> completed = wasComplete.get(member);
                if (completed == null) continue;
                sync(online, false);
                if (suppressNotifications.contains(member)) continue;
                for (QuestDefinition quest : catalog.quests().values()) {
                    if (!completed.getOrDefault(quest.id(), false) && tasksComplete(progress(member, quest.id()), quest)) {
                        notify(online, "complete", "Quest completed", quest.title());
                    }
                }
            }
        }
    }

    private void migrateLegacyRecipients(boolean legacyRewardEligibility) {
        progress.values().forEach(quests -> quests.forEach((id, state) -> {
            if (state.partyRewardSource() == null) return;
            QuestDefinition definition = catalog.quests().get(id);
            if (legacyRewardEligibility && definition != null) completeLegacyTasks(state, definition.tasks(), "");
            JsonObject legacy = legacyPartyProgress.getAsJsonObject(state.partyRewardSource().partyId().toString());
            QuestDefinition quest = catalog.quests().get(id);
            if (legacy != null && quest != null && legacy.has(id)) {
                mergeSharedTasks(state, quest, QuestProgressState.fromJson(quest, legacy.getAsJsonObject(id)).taskProgress());
            }
        }));
    }

    private static void completeLegacyTasks(QuestProgressState state, Map<String, QuestDefinition.Task> tasks, String prefix) {
        tasks.forEach((id, task) -> {
            state.setTaskProgress(prefix + id, Math.max(state.getTaskProgress(prefix + id), task.target()));
            completeLegacyTasks(state, task.tasks(), prefix + id + "/");
        });
    }

    private static boolean mergeSharedTasks(QuestProgressState state, QuestDefinition quest, Map<String, Integer> maximum) {
        Map<String, Integer> before = state.taskProgress();
        maximum.forEach((path, value) -> state.setTaskProgress(path, Math.max(value, state.getTaskProgress(path))));
        refreshSharedComposites(state, quest.tasks(), "");
        return !before.equals(state.taskProgress());
    }

    private static void refreshSharedComposites(QuestProgressState state, Map<String, QuestDefinition.Task> tasks, String prefix) {
        tasks.forEach((id, task) -> {
            String path = prefix + id;
            if (task.kind() != QuestDefinition.TaskKind.COMPOSITE) return;
            refreshSharedComposites(state, task.tasks(), path + "/");
            double total = task.tasks().values().stream().mapToDouble(child -> Math.min(1,
                state.getTaskProgress(path + "/" + child.id()) / (double) Math.max(1, child.target()))).sum();
            state.setTaskProgress(path, Math.max(state.getTaskProgress(path), Math.min(task.target(), (int) Math.floor(total + 0.000001))));
        });
    }

    private static boolean tasksComplete(QuestProgressState state, QuestDefinition quest) {
        return quest.tasks().values().stream().allMatch(task -> state.getTaskProgress(task.id()) >= task.target());
    }

    void editLegacyPartyProgress(String oldId, String newId, boolean reset) {
        legacyPartyProgress.entrySet().forEach(party -> {
            JsonObject quests = party.getValue().getAsJsonObject();
            if (!quests.has(oldId)) return;
            JsonObject state = quests.getAsJsonObject(oldId);
            if (reset) state.remove("tasks");
            if (newId != null && !oldId.equals(newId)) { quests.remove(oldId); quests.add(newId, state); }
        });
    }

    private JsonObject partyContext(UUID player) {
        JsonObject result = new JsonObject();
        result.addProperty("available", parties.available());
        try {
            PartyLookup.Party party = parties.find(player);
            if (party != null) {
                result.addProperty("name", party.name());
                result.addProperty("members", party.members().size());
            }
        } catch (RuntimeException exception) {
            result.addProperty("available", false);
            result.addProperty("error", true);
            Theseus.LOGGER.warn("Could not read OPAC party context for {}: {}", player, exception.toString());
        }
        return result;
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

}
