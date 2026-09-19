package me.johardt.theseus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.johardt.theseus.Theseus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.fml.loading.FMLPaths;

public final class QuestRuntime {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final TaskEngine.Builder TASKS = TaskEngine.defaultBuilder();
    private static final RewardEngine.Builder REWARDS = RewardEngine.builder();
    private static boolean taskHandlersLocked;
    private static boolean rewardHandlersLocked;

    private final TaskEngine taskEngine;
    private final RewardEngine rewardEngine;
    private final ProgressStore progressStore;
    private final QuestWorld world;
    private final QuestSync questSync;
    private QuestCatalog catalog;
    private final Map<UUID, Map<String, QuestProgressState>> progress =
        new HashMap<>();
    private final Set<UUID> suppressNotifications = new java.util.HashSet<>();

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
        taskHandlersLocked = true;
        RewardEngine rewards = lockRewardHandlers();
        ServerQuestWorld world = new ServerQuestWorld(
            server,
            FMLPaths.CONFIGDIR.get()
        );
        QuestRuntime runtime = new QuestRuntime(
            world.loadCatalog(),
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
        runtime.loadProgress();
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
        world.onlinePlayers().forEach(player -> sync(player, false));
        return catalog.quests().size();
    }

    /**
     * Applies one acknowledged editor mutation.  This is the single entry
     * point for authoring requests, so edit permission is checked once before
     * the exhaustive mutation dispatch.
     */
    public MutationResult applyEditorMutation(ServerPlayer player, QuestMutation mutation) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        if (mutation == null) return MutationResult.failure("Editor mutation is required");
        return switch (mutation) {
            case QuestMutation.CreateQuest value -> createQuest(value.request());
            case QuestMutation.UpdateQuest value -> updateQuest(value.request());
            case QuestMutation.ImportQuests value -> importQuests(value.request());
            case QuestMutation.PasteQuest value -> pasteQuest(value.request());
            case QuestMutation.DeleteQuest value -> deleteQuestResult(value.request());
            case QuestMutation.ChapterAction value -> chapterMutationResult(value.request());
            case QuestMutation.SetDependency value -> dependencyMutationResultAuthorized(player, value.request());
            case QuestMutation.RemoveQuestGroup value -> removeQuestGroupResult(value.request());
            case QuestMutation.ResetProgress value -> resetProgressResultAuthorized(player, value.request());
        };
    }

    public MutationResult createQuest(ServerPlayer player, JsonObject draft) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return createQuest(draft);
    }

    private MutationResult createQuest(JsonObject draft) {
        if (hasCanonicalDocument(draft)) return createDocumentQuest(draft);
        String id = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!id.matches("[a-z0-9_.-]+")) {
            return MutationResult.failure("Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        }
        try {
            if (catalog.documents().contains(id) || catalog.quests().containsKey(id)) {
                return MutationResult.failure("A quest with ID '" + id + "' already exists");
            }
        } catch (java.io.IOException exception) {
            return MutationResult.failure("Failed to inspect quest storage");
        }
        MutationResult basicValidation = validateDraftDisplay(draft, null);
        if (!basicValidation.success()) return basicValidation;
        String title = draft.get("title").getAsString().trim();
        String icon = draft.has("icon") ? draft.get("icon").getAsString() : "minecraft:map";
        String background = draft.has("background")
            ? draft.get("background").getAsString()
            : "theseus:textures/gui/quest_backgrounds/default.png";

        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("type", "theseus:item");
        iconJson.addProperty("item", icon);
        display.add("icon", iconJson);
        display.addProperty("icon_background", background);
        if (draft.has("icon_size")) display.add("icon_size", draft.get("icon_size").deepCopy());
        display.addProperty("title", title);
        display.addProperty("subtitle", draft.has("subtitle") ? draft.get("subtitle").getAsString() : "");
        com.google.gson.JsonArray description = new com.google.gson.JsonArray();
        String body = draft.has("body") ? draft.get("body").getAsString() : "";
        body.lines().forEach(description::add);
        display.add("description", description);
        JsonObject groups = new JsonObject();
        JsonObject placement = new JsonObject();
        com.google.gson.JsonArray position = new com.google.gson.JsonArray();
        position.add(draft.has("x") ? draft.get("x").getAsInt() : 0);
        position.add(draft.has("y") ? draft.get("y").getAsInt() : 0);
        placement.add("position", position);
        groups.add(draft.has("group") ? draft.get("group").getAsString() : "Main", placement);
        display.add("groups", groups);
        root.add("display", display);
        root.add(
            "tasks",
            draft.has("tasks") && draft.get("tasks").isJsonObject()
                ? draft.getAsJsonObject("tasks").deepCopy()
                : new JsonObject()
        );
        root.add(
            "rewards",
            draft.has("rewards") && draft.get("rewards").isJsonObject()
                ? draft.getAsJsonObject("rewards").deepCopy()
                : new JsonObject()
        );
        root.add("settings", draft.has("settings") && draft.get("settings").isJsonObject()
            ? draft.getAsJsonObject("settings").deepCopy() : new JsonObject());

        MutationResult finalValidation = validateQuest(id, root);
        if (!finalValidation.success()) return finalValidation;
        String validationWarnings = finalValidation.message();

        try {
            catalog.documents().createQuest(id, root);
            reload();
            return MutationResult.success("Quest '" + id + "' created" + warningSuffix(validationWarnings), finalValidation.diagnostics());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error("Failed to create quest {}", id, exception);
            return MutationResult.failure("Failed to write quest '" + id + "'");
        }
    }

    public MutationResult updateQuest(ServerPlayer player, JsonObject draft) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return updateQuest(draft);
    }

    private MutationResult updateQuest(JsonObject draft) {
        if (hasCanonicalDocument(draft)) return updateDocumentQuest(draft);
        String oldId = draft.has("original_id") ? draft.get("original_id").getAsString() : "";
        String newId = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!catalog.quests().containsKey(oldId)) return MutationResult.failure("The original quest no longer exists");
        if (catalog.hasConflict(oldId)) return MutationResult.failure("Quest ID '" + oldId + "' is duplicated; resolve the conflicting files before editing it");
        if (!newId.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
        if (!oldId.equals(newId) && catalog.quests().containsKey(newId)) {
            return MutationResult.failure("A quest with ID '" + newId + "' already exists");
        }
        JsonObject changedFields = draft.has("changed_fields") && draft.get("changed_fields").isJsonObject()
            ? draft.getAsJsonObject("changed_fields") : new JsonObject();
        MutationResult basicValidation = validateDraftDisplay(draft, changedFields);
        if (!basicValidation.success()) return basicValidation;
        try {
            JsonObject root = catalog.documents().readQuest(oldId);
            JsonObject previousTasks = root.has("tasks") && root.get("tasks").isJsonObject() ? root.getAsJsonObject("tasks").deepCopy() : new JsonObject();
            JsonObject previousRewards = root.has("rewards") && root.get("rewards").isJsonObject() ? root.getAsJsonObject("rewards").deepCopy() : new JsonObject();
            JsonObject display = root.has("display") && root.get("display").isJsonObject()
                ? root.getAsJsonObject("display") : new JsonObject();
            JsonObject changed = changedFields;
            if (changed.has("title")) display.addProperty("title", draft.get("title").getAsString());
            if (changed.has("subtitle")) display.addProperty("subtitle", draft.has("subtitle") ? draft.get("subtitle").getAsString() : "");
            if (changed.has("body")) {
                com.google.gson.JsonArray description = new com.google.gson.JsonArray();
                String body = draft.has("body") ? draft.get("body").getAsString() : "";
                body.lines().forEach(description::add);
                display.add("description", description);
            }
            if (changed.has("icon")) {
                JsonObject icon = new JsonObject();
                icon.addProperty("type", "theseus:item");
                icon.addProperty("item", draft.get("icon").getAsString());
                display.add("icon", icon);
            }
            if (changed.has("background")) display.addProperty("icon_background", draft.get("background").getAsString());
            if (changed.has("icon_size") && draft.has("icon_size")) display.add("icon_size", draft.get("icon_size").deepCopy());
            if (changed.has("settings") && draft.has("settings") && draft.get("settings").isJsonObject()) {
                root.add("settings", draft.getAsJsonObject("settings").deepCopy());
            }
            if (changed.has("groups") && draft.has("groups") && draft.get("groups").isJsonObject()) {
                JsonObject groups = display.has("groups") && display.get("groups").isJsonObject()
                    ? display.getAsJsonObject("groups") : new JsonObject();
                draft.getAsJsonObject("groups").entrySet().forEach(entry -> {
                    if (!entry.getValue().isJsonObject()) return;
                    JsonObject placement = groups.has(entry.getKey()) && groups.get(entry.getKey()).isJsonObject()
                        ? groups.getAsJsonObject(entry.getKey()) : new JsonObject();
                    JsonObject edited = entry.getValue().getAsJsonObject();
                    if (edited.has("position")) placement.add("position", edited.get("position").deepCopy());
                    groups.add(entry.getKey(), placement);
                });
                display.add("groups", groups);
            }
            root.add("display", display);
            root.add("tasks", draft.getAsJsonObject("tasks").deepCopy());
            root.add("rewards", draft.getAsJsonObject("rewards").deepCopy());
            MutationResult finalValidation = validateQuest(newId, root);
            if (!finalValidation.success()) return finalValidation;
            String validationWarnings = finalValidation.message();
            catalog.documents().saveQuest(oldId, newId, root);
            boolean progressAffecting = !previousTasks.equals(root.getAsJsonObject("tasks")) ||
                !previousRewards.equals(root.getAsJsonObject("rewards"));
            if (progressAffecting) resetQuestProgress(oldId, newId);
            else if (!oldId.equals(newId)) migrateQuestProgress(oldId, newId);
            reload();
            return MutationResult.success("Quest '" + newId + "' saved" + warningSuffix(validationWarnings), finalValidation.diagnostics());
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to update quest {}", oldId, exception);
            return MutationResult.failure("Failed to update quest '" + oldId + "'");
        }
    }

    private MutationResult createDocumentQuest(JsonObject request) {
        String id = request.has("id") ? request.get("id").getAsString().trim() : "";
        if (!id.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        try {
            if (catalog.documents().contains(id) || catalog.quests().containsKey(id)) return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            return MutationResult.failure("Failed to inspect quest storage");
        }
        JsonObject root = authoredDocument(request.getAsJsonObject("document"));
        applyPlacement(root, request);
        MutationResult validation = validateQuest(id, root);
        if (!validation.success()) return validation;
        try {
            catalog.documents().createQuest(id, root);
            reload();
            return MutationResult.success("Quest '" + id + "' created" + warningSuffix(validation.message()), validation.diagnostics());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error("Failed to create quest {}", id, exception);
            return MutationResult.failure("Failed to write quest '" + id + "'");
        }
    }

    private MutationResult updateDocumentQuest(JsonObject request) {
        String oldId = request.has("original_id") ? request.get("original_id").getAsString() : "";
        String newId = request.has("id") ? request.get("id").getAsString().trim() : "";
        if (!catalog.quests().containsKey(oldId)) return MutationResult.failure("The original quest no longer exists");
        if (catalog.hasConflict(oldId)) return MutationResult.failure("Quest ID '" + oldId + "' is duplicated; resolve the conflicting files before editing it");
        if (!newId.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
        if (!oldId.equals(newId) && catalog.quests().containsKey(newId)) return MutationResult.failure("A quest with ID '" + newId + "' already exists");
        if (!request.get("document").isJsonObject()) return MutationResult.failure("Quest document is invalid");
        try {
            JsonObject previousRoot = catalog.documents().readQuest(oldId);
            JsonObject proposed = authoredDocument(request.getAsJsonObject("document"));
            JsonArray changedPaths = request.has("changed_paths") && request.get("changed_paths").isJsonArray()
                ? request.getAsJsonArray("changed_paths") : new JsonArray();
            JsonObject root = QuestDraft.merge(previousRoot, proposed, changedPaths);
            JsonObject previousTasks = object(previousRoot, "tasks");
            JsonObject previousRewards = object(previousRoot, "rewards");
            MutationResult validation = validateQuest(newId, root);
            if (!validation.success()) return validation;
            catalog.documents().saveQuest(oldId, newId, root);
            boolean progressAffecting = !previousTasks.equals(object(root, "tasks")) || !previousRewards.equals(object(root, "rewards"));
            if (progressAffecting) resetQuestProgress(oldId, newId);
            else if (!oldId.equals(newId)) migrateQuestProgress(oldId, newId);
            reload();
            return MutationResult.success("Quest '" + newId + "' saved" + warningSuffix(validation.message()), validation.diagnostics());
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to update quest {}", oldId, exception);
            return MutationResult.failure("Failed to update quest '" + oldId + "'");
        }
    }

    private static boolean hasCanonicalDocument(JsonObject request) {
        return request != null && request.has("document") && request.get("document").isJsonObject();
    }

    private static boolean isString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
            && object.get(key).getAsJsonPrimitive().isString();
    }

    private static JsonObject authoredDocument(JsonObject source) {
        JsonObject document = source == null ? new JsonObject() : source.deepCopy();
        List.of("progress", "unlocked", "complete", "claimed", "claimed_rewards", "pinned", "issues", "__chapters", "__editor_types")
            .forEach(document::remove);
        return document;
    }

    private static void applyPlacement(JsonObject root, JsonObject request) {
        if (request == null) return;
        String group = request.has("group") ? request.get("group").getAsString() : "Main";
        if (group == null || group.isBlank()) group = "Main";
        int x = request.has("x") ? request.get("x").getAsInt() : 0;
        int y = request.has("y") ? request.get("y").getAsInt() : 0;
        QuestDraft draft = QuestDraft.open(root);
        draft.setGroupPosition(group, x, y);
        JsonObject placed = draft.snapshot();
        root.entrySet().clear();
        placed.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue().deepCopy()));
    }

    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key).deepCopy() : new JsonObject();
    }

    /** Imports a validated batch. No file is created unless every entry passes preflight. */
    public MutationResult importQuests(ServerPlayer player, JsonObject request) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return importQuests(request);
    }

    private MutationResult importQuests(JsonObject request) {
        if (!request.has("files") || !request.get("files").isJsonObject()) return MutationResult.failure("Import requires a files object");
        Map<String, JsonObject> quests = new java.util.LinkedHashMap<>();
        List<QuestDiagnostics.Diagnostic> diagnostics = new java.util.ArrayList<>();
        String defaultChapter = request.has("chapter") ? request.get("chapter").getAsString() : "";
        request.getAsJsonObject("files").entrySet().forEach(entry -> {
            String id = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "invalid_quest_document", id, "$", "Quest document must be an object", "Provide a JSON object."));
                return;
            }
            if (!id.matches("[a-z0-9_.-]+")) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "invalid_quest_id", id, "id", "Invalid quest ID '" + id + "'", "Choose a lowercase ID."));
                return;
            }
            if (catalog.quests().containsKey(id) || catalog.hasConflict(id)) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "duplicate_catalog_id", id, "id", "A quest with ID '" + id + "' already exists", "Choose a different ID."));
                return;
            }
            JsonObject root = entry.getValue().getAsJsonObject().deepCopy();
            if (!defaultChapter.isBlank() && catalog.groupOrder().contains(defaultChapter)) {
                JsonObject display = root.has("display") && root.get("display").isJsonObject() ? root.getAsJsonObject("display") : null;
                if (display != null && (!display.has("groups") || !display.get("groups").isJsonObject() || display.getAsJsonObject("groups").isEmpty())) {
                    JsonObject groups = new JsonObject();
                    JsonObject placement = new JsonObject();
                    com.google.gson.JsonArray position = new com.google.gson.JsonArray(); position.add(0); position.add(0);
                    placement.add("position", position); groups.add(defaultChapter, placement); display.add("groups", groups);
                }
            }
            diagnostics.addAll(QuestDiagnostics.validate(id, root, icon -> {
                try { return BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon)); }
                catch (RuntimeException exception) { return false; }
            }));
            diagnostics.addAll(RegistryValidation.validate(id, root, world::containsRegistryTarget));
            quests.put(id, root);
        });
        if (quests.isEmpty() && diagnostics.isEmpty()) {
            diagnostics.add(new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR,
                "empty_import",
                "",
                "files",
                "Import must contain at least one quest file",
                "Choose one or more .json quest files."
            ));
        }
        Map<String, QuestDefinition> combined = new java.util.LinkedHashMap<>(catalog.quests());
        quests.forEach((id, root) -> combined.put(id, QuestDefinition.parse(id, root)));
        Set<String> importedIds = quests.keySet();
        QuestCatalog.validateDependencies(combined).forEach(issue -> {
            String issueId = importedIds.stream()
                .filter(id -> issue.path().equals(id + ".dependencies"))
                .findFirst()
                .orElse(null);
            if (issueId == null) return;
            String path = issue.path().startsWith(issueId + ".") ? issue.path().substring(issueId.length() + 1) : issue.path();
            diagnostics.add(new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR,
                "invalid_dependency",
                issueId,
                path,
                issue.message(),
                "Add the referenced quest, remove the dependency, or break the cycle."
            ));
        });
        if (diagnostics.stream().anyMatch(QuestDiagnostics.Diagnostic::blocksSave)) {
            return MutationResult.failure(diagnostics.stream().filter(QuestDiagnostics.Diagnostic::blocksSave).map(d -> d.path() + ": " + d.message()).collect(Collectors.joining("\n")), diagnostics);
        }
        try {
            catalog.documents().importQuests(quests);
            reload();
            return MutationResult.success("Imported " + quests.size() + " quest" + (quests.size() == 1 ? "" : "s"), diagnostics);
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error("Failed to import quest batch", exception);
            QuestDiagnostics.Diagnostic failure = new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR,
                "import_commit_failed",
                "",
                "files",
                "Import failed: " + exception.getMessage(),
                "Resolve the reported file conflict or filesystem error and retry."
            );
            diagnostics.add(failure);
            return MutationResult.failure(failure.message(), diagnostics);
        }
    }

    /** Clones or moves a quest snapshot, or adds an existing quest to a chapter. */
    public MutationResult pasteQuest(ServerPlayer player, JsonObject request) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return pasteQuest(request);
    }

    private MutationResult pasteQuest(JsonObject request) {
        String sourceId = request.has("source_id") ? request.get("source_id").getAsString() : "";
        String chapter = request.has("chapter") ? request.get("chapter").getAsString() : "";
        boolean chapterOnly = request.has("chapter_only") && request.get("chapter_only").getAsBoolean();
        try {
            if (chapterOnly) {
                if (!catalog.quests().containsKey(sourceId) || !catalog.groupOrder().contains(chapter)) return MutationResult.failure("Unknown quest or chapter");
                JsonObject root = catalog.documents().readQuest(sourceId);
                addChapterPlacement(root, chapter, request);
                catalog.documents().writeQuest(sourceId, root);
                reload();
                return MutationResult.success("Added '" + sourceId + "' to chapter '" + chapter + "'");
            }
            String id = request.has("id") ? request.get("id").getAsString().trim() : "";
            if (!id.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
            JsonObject root = request.has("quest") && request.get("quest").isJsonObject()
                ? request.getAsJsonObject("quest").deepCopy() : null;
            boolean move = request.has("move") && request.get("move").getAsBoolean();
            if (move) {
                if (!catalog.quests().containsKey(sourceId) || catalog.hasConflict(sourceId)) return MutationResult.failure("The source quest is no longer available for moving");
            }
            if (root == null && catalog.quests().containsKey(sourceId)) {
                root = catalog.documents().readQuest(sourceId);
            }
            if (root == null) return MutationResult.failure("Clipboard quest data is missing");
            if (!chapter.isBlank()) addChapterPlacement(root, chapter, request);
            MutationResult validation = validateQuest(id, root);
            if (!validation.success()) return validation;
            if (move && sourceId.equals(id) && catalog.quests().containsKey(sourceId)) {
                catalog.documents().writeQuest(sourceId, root);
                reload();
                return MutationResult.success("Moved '" + sourceId + "'");
            }
            catalog.documents().transferQuest(sourceId, id, root, move);
            reload();
            return MutationResult.success((move ? "Moved '" + sourceId : "Copied '" + sourceId) + "' as '" + id + "'");
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to paste quest", exception);
            return MutationResult.failure("Paste failed: " + exception.getMessage());
        }
    }

    private static void addChapterPlacement(JsonObject root, String chapter, JsonObject request) {
        JsonObject display = root.has("display") && root.get("display").isJsonObject() ? root.getAsJsonObject("display") : new JsonObject();
        JsonObject groups = display.has("groups") && display.get("groups").isJsonObject() ? display.getAsJsonObject("groups") : new JsonObject();
        JsonObject placement = groups.has(chapter) && groups.get(chapter).isJsonObject() ? groups.getAsJsonObject(chapter) : new JsonObject();
        com.google.gson.JsonArray position = new com.google.gson.JsonArray();
        position.add(request.has("x") ? request.get("x").getAsInt() : 0);
        position.add(request.has("y") ? request.get("y").getAsInt() : 0);
        placement.add("position", position);
        groups.add(chapter, placement); display.add("groups", groups); root.add("display", display);
    }

    static MutationResult validateDraftDisplay(JsonObject draft, JsonObject changedFields) {
        String error = QuestDiagnostics.validateDisplay(draft, changedFields, icon -> {
            try {
                return BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon));
            } catch (RuntimeException exception) {
                return false;
            }
        }).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst()
            .orElse("");
        return error.isEmpty() ? MutationResult.success("") : MutationResult.failure(error);
    }

    private static String firstValidationError(QuestDefinition definition) {
        return definition.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(issue -> issue.path() + ": " + issue.message())
            .findFirst()
            .orElse("Quest contains invalid configuration");
    }

    private MutationResult validateQuest(String id, JsonObject root) {
        List<QuestDiagnostics.Diagnostic> diagnostics = new java.util.ArrayList<>(QuestDiagnostics.validate(id, root, icon -> {
            try { return BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon)); }
            catch (RuntimeException exception) { return false; }
        }));
        diagnostics.addAll(RegistryValidation.validate(id, root, world::containsRegistryTarget));
        List<QuestDiagnostics.Diagnostic> errors = diagnostics.stream().filter(QuestDiagnostics.Diagnostic::blocksSave).toList();
        if (errors.isEmpty()) return MutationResult.success(diagnostics.stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).map(diagnostic -> diagnostic.path() + ": " + diagnostic.message()).collect(Collectors.joining("\n")), diagnostics);
        return MutationResult.failure(errors.stream().map(diagnostic -> diagnostic.path() + ": " + diagnostic.message()).collect(Collectors.joining("\n")), diagnostics);
    }

    private static String warningSuffix(String warnings) { return warnings == null || warnings.isBlank() ? "" : " (warnings: " + warnings.replace('\n', ';') + ")"; }

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
        if (!world.canEdit(player)) {
            return QuestFileResult.failure("You do not have permission to open quest files");
        }
        if (!world.isIntegratedServer()) {
            return QuestFileResult.failure("Open quest file is only available in an integrated server");
        }
        if (questId == null || !catalog.quests().containsKey(questId) || catalog.hasConflict(questId)) {
            return QuestFileResult.failure("Quest file is unavailable");
        }
        try {
            return QuestFileResult.success(catalog.documents().relativeQuestPath(questId));
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.warn("Failed to resolve quest file for {}", questId, exception);
            return QuestFileResult.failure("Quest file is unavailable");
        }
    }

    public void deleteQuest(ServerPlayer player, String id) {
        deleteQuestResult(player, id);
    }

    public MutationResult deleteQuestResult(ServerPlayer player, String id) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return deleteQuestResult(id);
    }

    private MutationResult deleteQuestResult(JsonObject request) {
        String id = request.has("id") && request.get("id").isJsonPrimitive() ? request.get("id").getAsString() : "";
        return deleteQuestResult(id);
    }

    private MutationResult deleteQuestResult(String id) {
        if (!catalog.quests().containsKey(id)) return MutationResult.failure("Quest '" + id + "' does not exist");
        if (catalog.hasConflict(id)) return MutationResult.failure("Quest ID '" + id + "' is duplicated; resolve the conflicting files first");
        try {
            catalog.documents().deleteQuest(id);
            resetQuestProgress(id, null);
            reload();
            return MutationResult.success("Quest '" + id + "' deleted");
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to delete quest {}", id, exception);
            return MutationResult.failure("Failed to delete quest '" + id + "'");
        }
    }

    public MutationResult chapterMutationResult(ServerPlayer player, JsonObject action) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return chapterMutationResult(action);
    }

    private MutationResult chapterMutationResult(JsonObject action) {
        String operation = action.has("operation") ? action.get("operation").getAsString() : "";
        String name = action.has("name") ? action.get("name").getAsString().trim() : "";
        if (!List.of("create", "update", "delete", "reorder").contains(operation)) return MutationResult.failure("Unknown chapter operation");
        if ((operation.equals("create") || operation.equals("update")) && name.isEmpty()) return MutationResult.failure("Chapter name is required");
        if (operation.equals("create") && catalog.groupOrder().contains(name)) return MutationResult.failure("That chapter already exists");
        if (operation.equals("update") && (!catalog.groupOrder().contains(action.has("old_name") ? action.get("old_name").getAsString() : "") || catalog.groupOrder().contains(name) && !name.equals(action.get("old_name").getAsString()))) return MutationResult.failure("Invalid chapter rename");
        if (operation.equals("delete") && !catalog.groupOrder().contains(name)) return MutationResult.failure("That chapter does not exist");
        try { chapterActionAuthorized(action); return MutationResult.success("Chapter change applied"); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Invalid chapter change" : exception.getMessage()); }
    }

    public MutationResult removeQuestGroupResult(ServerPlayer player, JsonObject action) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return removeQuestGroupResult(action);
    }

    private MutationResult removeQuestGroupResult(JsonObject action) {
        String id = action.has("id") ? action.get("id").getAsString() : "";
        String group = action.has("group") ? action.get("group").getAsString() : "";
        QuestDefinition quest = catalog.quests().get(id);
        if (quest == null || !quest.display().groups().containsKey(group)) return MutationResult.failure("Quest is not in that chapter");
        if (quest.display().groups().size() <= 1) return MutationResult.failure("A quest must remain in at least one chapter");
        try { removeQuestFromGroupAuthorized(id, group); return MutationResult.success("Quest removed from chapter"); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Chapter removal failed" : exception.getMessage()); }
    }

    public MutationResult dependencyMutationResult(ServerPlayer player, JsonObject action) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return dependencyMutationResultAuthorized(player, action);
    }

    private MutationResult dependencyMutationResultAuthorized(ServerPlayer player, JsonObject action) {
        String prerequisite = action.has("prerequisite") ? action.get("prerequisite").getAsString() : "";
        String dependent = action.has("dependent") ? action.get("dependent").getAsString() : "";
        boolean remove = action.has("remove") && action.get("remove").getAsBoolean();
        if (!catalog.quests().containsKey(prerequisite) || !catalog.quests().containsKey(dependent)) return MutationResult.failure("Unknown quest in dependency link");
        if (!remove && QuestCatalog.wouldCreateCycle(catalog.quests(), prerequisite, dependent)) {
            return MutationResult.failure("Dependency cycle: " + String.join(" → ", QuestCatalog.dependencyCyclePath(catalog.quests(), prerequisite, dependent)));
        }
        try { return setDependencyAuthorized(player, prerequisite, dependent, remove); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Dependency change failed" : exception.getMessage()); }
    }

    public MutationResult resetProgressResult(ServerPlayer player, JsonObject action) {
        if (!world.canEdit(player)) {
            return MutationResult.failure("You do not have permission to reset quest progress");
        }
        return resetProgressResultAuthorized(player, action);
    }

    private MutationResult resetProgressResultAuthorized(ServerPlayer player, JsonObject action) {
        if (action == null || !action.has("scope") || !action.has("quest")) {
            return MutationResult.failure("Reset progress requires a scope and quest");
        }
        if (!isString(action, "scope") || !isString(action, "quest")
            || (action.has("entry") && !isString(action, "entry"))) {
            return MutationResult.failure("Reset progress request is malformed");
        }

        String scope = action.get("scope").getAsString();
        String questId = action.get("quest").getAsString();
        String entry = action.has("entry") ? action.get("entry").getAsString() : "";
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || catalog.hasConflict(questId)) {
            return MutationResult.failure("Unknown quest '" + questId + "'");
        }

        QuestProgressState state = progress(player, questId);
        switch (scope) {
            case "quest" -> {
                if (!entry.isBlank()) return MutationResult.failure("Quest reset does not accept an entry");
                state.clearProgress();
                changed(player);
                return MutationResult.success("Reset quest progress for '" + quest.title() + "' (" + questId + ") for the current player");
            }
            case "task" -> {
                if (entry.isBlank()) return MutationResult.failure("Task reset requires a task path");
                QuestDefinition.Task task = resolveTask(quest.tasks(), entry);
                if (task == null) return MutationResult.failure("Unknown task path '" + entry + "' in quest '" + questId + "'");
                state.resetTaskPath(entry);
                refreshCompositeProgress(player, quest);
                changed(player);
                return MutationResult.success("Reset task progress for '" + entry + "' in quest '" + questId + "' for the current player");
            }
            case "reward" -> {
                if (entry.isBlank()) return MutationResult.failure("Reward reset requires a reward ID");
                if (!quest.rewards().containsKey(entry)) {
                    return MutationResult.failure("Unknown top-level reward '" + entry + "' in quest '" + questId + "'");
                }
                state.unmarkRewardClaimed(entry);
                changed(player);
                return MutationResult.success("Reset reward progress for '" + entry + "' in quest '" + questId + "' for the current player");
            }
            default -> {
                return MutationResult.failure("Unknown reset progress scope '" + scope + "'");
            }
        }
    }

    public void removeQuestFromGroup(ServerPlayer player, String id, String group) {
        if (!world.canEdit(player)) return;
        removeQuestFromGroupAuthorized(id, group);
    }

    private void removeQuestFromGroupAuthorized(String id, String group) {
        try {
            JsonObject root = catalog.documents().readQuest(id);
            JsonObject groups = root.getAsJsonObject("display").getAsJsonObject("groups");
            if (groups.size() <= 1 || !groups.has(group)) return;
            groups.remove(group);
            catalog.documents().writeQuest(id, root);
            reload();
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to remove quest {} from chapter {}", id, group, exception);
        }
    }

    public void chapterAction(ServerPlayer player, JsonObject action) {
        if (!world.canEdit(player)) return;
        chapterActionAuthorized(action);
    }

    private void chapterActionAuthorized(JsonObject action) {
        String operation = action.has("operation") ? action.get("operation").getAsString() : "";
        List<String> order = new java.util.ArrayList<>(catalog.groupOrder());
        Map<String, QuestCatalog.ChapterSettings> settings = new java.util.LinkedHashMap<>(catalog.chapterSettings());
        QuestDocumentStore.ChapterChange change = QuestDocumentStore.ChapterChange.none();
        try {
            switch (operation) {
                case "create" -> {
                    String name = validChapterName(action.get("name").getAsString());
                    if (order.contains(name)) return;
                    order.add(name);
                    settings.put(name, chapterSettings(action));
                }
                case "update" -> {
                    String oldName = action.get("old_name").getAsString();
                    String newName = validChapterName(action.get("name").getAsString());
                    if (!order.contains(oldName) || (!oldName.equals(newName) && order.contains(newName))) return;
                    order.set(order.indexOf(oldName), newName);
                    settings.remove(oldName);
                    settings.put(newName, chapterSettings(action));
                    if (!oldName.equals(newName)) change = new QuestDocumentStore.ChapterChange(oldName, newName);
                }
                case "delete" -> {
                    String name = action.get("name").getAsString();
                    if (!order.remove(name)) return;
                    settings.remove(name);
                    change = new QuestDocumentStore.ChapterChange(name, null);
                    if (order.isEmpty()) {
                        order.add("Main");
                        settings.put("Main", new QuestCatalog.ChapterSettings("minecraft:map", ""));
                    }
                }
                case "reorder" -> {
                    List<String> requested = new java.util.ArrayList<>();
                    action.getAsJsonArray("order").forEach(value -> requested.add(value.getAsString()));
                    if (requested.size() != order.size() || !new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(order))) return;
                    order = requested;
                }
                default -> { return; }
            }
            catalog.documents().updateChapters(order, settings, change);
            reload();
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed chapter operation {}", operation, exception);
        }
    }

    private static String validChapterName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 64 || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("Invalid chapter name");
        }
        return name;
    }

    private static QuestCatalog.ChapterSettings chapterSettings(JsonObject action) {
        String icon = action.has("icon") ? action.get("icon").getAsString() : "minecraft:map";
        if (!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon))) icon = "minecraft:map";
        boolean iconEnabled = !action.has("icon_enabled") || action.get("icon_enabled").getAsBoolean();
        int opacity = action.has("background_opacity") ? action.get("background_opacity").getAsInt() : 100;
        return new QuestCatalog.ChapterSettings(
            icon,
            action.has("background") ? action.get("background").getAsString() : "",
            iconEnabled,
            Math.clamp(opacity, 0, 100)
        );
    }

    private void resetQuestProgress(String oldId, String newId) {
        progress.values().forEach(quests -> {
            quests.remove(oldId);
            if (newId != null) quests.remove(newId);
        });
        saveProgress();
    }

    private void migrateQuestProgress(String oldId, String newId) {
        progress.values().forEach(quests -> {
            QuestProgressState state = quests.remove(oldId);
            if (state != null) quests.put(newId, state);
        });
        saveProgress();
    }

    public MutationResult setDependency(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        if (!world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return setDependencyAuthorized(player, prerequisiteId, dependentId, remove);
    }

    private MutationResult setDependencyAuthorized(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        QuestDefinition prerequisite = catalog.quests().get(prerequisiteId);
        QuestDefinition dependent = catalog.quests().get(dependentId);
        if (prerequisite == null || dependent == null) {
            world.message(player, "Unknown quest in dependency link");
            return MutationResult.failure("Unknown quest in dependency link");
        }
        if (prerequisiteId.equals(dependentId)) {
            world.message(player, "A quest cannot depend on itself");
            return MutationResult.failure("A quest cannot depend on itself");
        }
        Set<String> dependencies = new java.util.LinkedHashSet<>(dependent.dependencies());
        if (remove) {
            if (!dependencies.remove(prerequisiteId)) return MutationResult.failure("Dependency does not exist");
        } else {
            if (dependencies.contains(prerequisiteId)) return MutationResult.failure("Dependency already exists");
            if (QuestCatalog.wouldCreateCycle(
                catalog.quests(),
                prerequisiteId,
                dependentId
            )) {
                world.message(player, "That link would create a dependency cycle");
                return MutationResult.failure("Dependency cycle: " + String.join(" → ", QuestCatalog.dependencyCyclePath(catalog.quests(), prerequisiteId, dependentId)));
            }
            dependencies.add(prerequisiteId);
        }
        try {
            catalog.documents().updateDependencies(dependentId, dependencies);
            reload();
            return MutationResult.success("Dependency change applied");
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error(
                "Failed to update dependencies for quest {}",
                dependentId,
                exception
            );
            return MutationResult.failure("Failed to update quest dependencies");
        }
    }


    public List<QuestDefinition.ValidationIssue> validationIssues() {
        return catalog.issues();
    }

    public void initialize(ServerPlayer player) {
        suppressNotifications.add(world.playerId(player));
        try {
            updateInventoryTasks(player);
            for (QuestDefinition quest : catalog.quests().values()) {
                if (!isUnlocked(player, quest)) continue;
                for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                    if (
                        task.kind() != QuestDefinition.TaskKind.ADVANCEMENT
                    ) continue;
                    for (String advancement : configuredStrings(
                        task,
                        "advancements",
                        task.value()
                    )) {
                        if (world.advancementGranted(player, advancement)) {
                            signal(
                                player,
                                new TaskEngine.Signal.AdvancementGranted(
                                    advancement
                                )
                            );
                        }
                    }
                }
            }
            updatePassiveTasks(player);
        } finally {
            suppressNotifications.remove(world.playerId(player));
        }
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        return signal(player, new TaskEngine.Signal.Manual(value));
    }

    public String lockedDummyReason(ServerPlayer player, String value) {
        for (QuestDefinition quest : catalog.quests().values()) {
            boolean matches = flattenTasks(quest.tasks())
                .stream()
                .anyMatch(
                    task ->
                        task.kind() == QuestDefinition.TaskKind.DUMMY &&
                        task.value().equals(value)
                );
            if (!matches || isUnlocked(player, quest)) continue;
            String dependencies = quest
                .dependencies()
                .stream()
                .filter(dependency -> {
                    QuestDefinition required = catalog.quests().get(dependency);
                    return required == null || !isComplete(player, required);
                })
                .map(dependency -> {
                    QuestDefinition required = catalog.quests().get(dependency);
                    if (required == null) return (
                        "Unknown chapter › " + dependency
                    );
                    String chapter = required
                        .display()
                        .groups()
                        .keySet()
                        .stream()
                        .sorted()
                        .findFirst()
                        .orElse("Main");
                    return chapter + " › " + required.display().title();
                })
                .collect(Collectors.joining(", "));
            return (
                "Task '" +
                value +
                "' is locked by: " +
                (dependencies.isBlank() ? "quest dependencies" : dependencies) +
                "."
            );
        }
        return null;
    }

    public void updateInventoryTasks(ServerPlayer player) {
        TaskEngine.Signal.Inventory inventory = inventory(player, false);
        signal(player, inventory);
        signal(player, playerState(player));
        updatePassiveTasks(player);
    }

    public boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        Map<String, Boolean> wasUnlocked = questStates(player, false);
        Map<String, Boolean> wasComplete = questStates(player, true);
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyTask(player, quest, task, signal);
            }
        }
        if (changed) changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    public boolean submit(ServerPlayer player, String questId, String taskId) {
        Map<String, Boolean> wasUnlocked = questStates(player, false);
        Map<String, Boolean> wasComplete = questStates(player, true);
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestDefinition.Task task = resolveTask(quest.tasks(), taskId);
        if (task == null) return false;
        TaskEngine.Signal signal;
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            signal = inventory(player, true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            signal = new TaskEngine.Signal.Experience(
                player.experienceLevel,
                player.totalExperience,
                true
            );
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(playerData(player), true);
        } else {
            return false;
        }
        boolean changed = applyTask(player, quest, task, taskId, signal);
        if (changed) refreshCompositeProgress(player, quest);
        if (changed) changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    private static QuestDefinition.Task resolveTask(
        Map<String, QuestDefinition.Task> tasks,
        String path
    ) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.split("/", -1);
        Map<String, QuestDefinition.Task> current = tasks;
        QuestDefinition.Task task = null;
        for (String part : parts) {
            if (part.isBlank()) return null;
            task = current.get(part);
            if (task == null) return null;
            current = task.tasks();
        }
        return task;
    }

    private void refreshCompositeProgress(
        ServerPlayer player,
        QuestDefinition quest
    ) {
        for (QuestDefinition.Task task : quest.tasks().values()) {
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) updateCompositeSummary(player, quest, task, task.id());
        }
    }

    private boolean updateCompositeSummary(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey
    ) {
        boolean changed = false;
        for (QuestDefinition.Task child : task.tasks().values()) {
            if (child.kind() == QuestDefinition.TaskKind.COMPOSITE) {
                changed |= updateCompositeSummary(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id()
                );
            }
        }
        double total = task
            .tasks()
            .values()
            .stream()
            .mapToDouble(child ->
                taskFraction(
                    player,
                    quest.id(),
                    child,
                    progressKey + "/" + child.id()
                )
            )
            .sum();
        return (
            setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                Math.min(task.target(), (int) Math.floor(total + 0.000001))
            ) || changed
        );
    }

    public boolean claim(ServerPlayer player, String questId) {
        return claim(player, questId, Map.of());
    }

    public boolean claim(
        ServerPlayer player,
        String questId,
        Map<String, List<String>> selections
    ) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isComplete(player, quest) || quest.rewards().isEmpty()) return false;
        QuestProgressState state = progress(player, questId);
        List<QuestDefinition.Reward> missing = quest.rewards().values().stream()
            .filter(reward -> !state.claimedRewards().contains(reward.id()))
            .toList();
        if (missing.isEmpty()) return false;
        for (QuestDefinition.Reward reward : missing) {
            if (
                !canClaimReward(
                    player,
                    reward,
                    selections.getOrDefault(reward.id(), List.of())
                )
            ) {
                world.message(
                    player,
                    "Cannot claim unsupported or incomplete reward: " +
                        reward.title()
                );
                return false;
            }
        }
        List<String> granted = new java.util.ArrayList<>();
        for (QuestDefinition.Reward reward : missing) {
            grantReward(
                player,
                reward,
                selections.getOrDefault(reward.id(), List.of()),
                granted
            );
            state.markRewardClaimed(reward.id());
        }
        changed(player);
        notify(
            player,
            "reward",
            "Rewards claimed",
            granted.isEmpty() ? quest.title() : String.join(", ", granted)
        );
        return true;
    }

    public boolean togglePinned(ServerPlayer player, String questId) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestProgressState state = progress(player, questId);
        state.setPinned(!state.isPinned());
        changed(player);
        return true;
    }

    public void reset(ServerPlayer player) {
        progress.remove(world.playerId(player));
        changed(player);
    }

    public boolean isUnlocked(ServerPlayer player, QuestDefinition quest) {
        return quest
            .dependencies()
            .stream()
            .allMatch(dependency -> {
                QuestDefinition required = catalog.quests().get(dependency);
                return required != null && isComplete(player, required);
            });
    }

    public boolean isComplete(ServerPlayer player, QuestDefinition quest) {
        QuestProgressState progress = progress(player, quest.id());
        return quest
            .tasks()
            .values()
            .stream()
            .allMatch(
                task ->
                    progress.getTaskProgress(task.id()) >= task.target()
            );
    }

    private boolean setTaskProgress(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        int value
    ) {
        QuestProgressState progress = progress(player, quest.id());
        int previous = progress.getTaskProgress(progressKey);
        if (previous == value) return false;
        progress.setTaskProgress(progressKey, value);
        return true;
    }

    private boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        TaskEngine.Signal signal
    ) {
        return applyTask(player, quest, task, task.id(), signal);
    }

    private boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            boolean changed = false;
            for (QuestDefinition.Task child : task.tasks().values()) {
                changed |= applyTask(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id(),
                    signal
                );
            }
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        quest.id(),
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            int summarized = Math.min(
                task.target(),
                (int) Math.floor(total + 0.000001)
            );
            changed |= setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                summarized
            );
            return changed;
        }
        int current = progress(player, quest.id()).getTaskProgress(progressKey);
        TaskEngine.Result result = taskEngine.apply(task, current, signal);
        if (result.consumeAmount() > 0) consume(
            player,
            task,
            result.consumeAmount()
        );
        return setTaskProgress(
            player,
            quest,
            task,
            progressKey,
            result.progress()
        );
    }

    private double taskFraction(
        ServerPlayer player,
        String questId,
        QuestDefinition.Task task,
        String progressKey
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        questId,
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            return Math.min(1, total / Math.max(1, task.target()));
        }
        return Math.min(
            1,
            progress(player, questId).getTaskProgress(progressKey) /
                (double) Math.max(1, task.target())
        );
    }

    private void updatePassiveTasks(ServerPlayer player) {
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                if (task.kind() == QuestDefinition.TaskKind.RECIPE) {
                    for (String recipe : configuredStrings(
                        task,
                        "recipes",
                        task.value()
                    )) {
                        ResourceKey<Recipe<?>> key = ResourceKey.create(
                            Registries.RECIPE,
                            net.minecraft.resources.Identifier.parse(recipe)
                        );
                        if (player.getRecipeBook().contains(key)) signal(
                            player,
                            new TaskEngine.Signal.RecipeUnlocked(recipe)
                        );
                    }
                } else if (
                    task.kind() == QuestDefinition.TaskKind.STAT &&
                    !task.value().isBlank()
                ) {
                    var id = net.minecraft.resources.Identifier.parse(
                        task.value()
                    );
                    signal(
                        player,
                        new TaskEngine.Signal.Statistic(
                            task.value(),
                            player.getStats().getValue(Stats.CUSTOM, id)
                        )
                    );
                }
            }
        }
        Set<TaskEngine.Signal.RegistryEntry> structures = structuresAt(player);
        if (!structures.isEmpty()) signal(
            player,
            new TaskEngine.Signal.Structures(structures)
        );
    }

    private static void consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount
    ) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            String unit = task.source().has("xpType")
                ? task
                      .source()
                      .get("xpType")
                      .getAsString()
                      .toLowerCase(java.util.Locale.ROOT)
                : "level";
            if (unit.endsWith("points")) player.giveExperiencePoints(-amount);
            else player.giveExperienceLevels(-amount);
            return;
        }
        if (task.kind() != QuestDefinition.TaskKind.ITEM) return;
        int remaining = amount;
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize() && remaining > 0;
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            TaskEngine.Signal.RegistryEntry entry = itemEntry(player, stack);
            if (
                !RegistryPredicate.matches(
                    task.source().get("item"),
                    task.value(),
                    entry
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("components"),
                    entry.data()
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("nbt"),
                    entry.data()
                )
            ) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private boolean canClaimReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected
    ) {
        return switch (reward.kind()) {
            case XP, COMMAND -> true;
            case ITEM -> validIdentifier(reward.value());
            case LOOT_TABLE -> {
                if (!validIdentifier(reward.value())) yield false;
                yield world.hasLootTable(reward.value());
            }
            case SELECTABLE -> !selected.isEmpty() &&
                selected.size() <= reward.amount() &&
                selected.stream().distinct().count() == selected.size() &&
                selected.stream().allMatch(id -> {
                    QuestDefinition.Reward choice = reward.rewards().get(id);
                    return (
                        choice != null &&
                        choice.kind() !=
                            QuestDefinition.RewardKind.SELECTABLE &&
                        canClaimReward(player, choice, List.of())
                    );
                });
            case UNSUPPORTED -> rewardEngine.canClaim(reward, player, world);
        };
    }

    private void grantReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected,
        List<String> granted
    ) {
        switch (reward.kind()) {
            case XP -> {
                if (
                    reward
                        .value()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith("points")
                ) world.grantExperience(player, reward.amount(), true);
                else world.grantExperience(player, reward.amount(), false);
                granted.add(
                    reward.amount() +
                        (reward
                            .value()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith("points")
                            ? " XP"
                            : " levels")
                );
            }
            case ITEM -> {
                ItemStack stack = new ItemStack(
                    BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse(reward.value())
                    ),
                    reward.amount()
                );
                world.giveItem(player, stack);
                granted.add(
                    stack.getCount() + "× " + stack.getHoverName().getString()
                );
            }
            case COMMAND -> world.runCommand(player, reward.value());
            case LOOT_TABLE -> {
                world.generateLoot(player, reward.value(), stack -> {
                    world.giveItem(player, stack.copy());
                    granted.add(
                        stack.getCount() +
                            "× " +
                            stack.getHoverName().getString()
                    );
                });
            }
            case SELECTABLE -> selected
                .stream()
                .map(reward.rewards()::get)
                .forEach(choice ->
                    grantReward(player, choice, List.of(), granted)
                );
            case UNSUPPORTED -> {
                String detail = rewardEngine.grant(reward, player, world);
                if (!detail.isBlank()) granted.add(detail);
            }
        }
    }

    private static boolean validIdentifier(String value) {
        try {
            net.minecraft.resources.Identifier.parse(value);
            return !value.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TaskEngine.Signal.WorldState playerState(
        ServerPlayer player
    ) {
        var biome = player.level().getBiome(player.blockPosition());
        return new TaskEngine.Signal.WorldState(
            player.level().dimension().identifier().toString(),
            registryEntry(biome, new JsonObject(), 1),
            player.getX(),
            player.getY(),
            player.getZ()
        );
    }

    private static TaskEngine.Signal.Inventory inventory(
        ServerPlayer player,
        boolean submit
    ) {
        java.util.List<TaskEngine.Signal.RegistryEntry> entries =
            new java.util.ArrayList<>();
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize();
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) entries.add(itemEntry(player, stack));
        }
        return new TaskEngine.Signal.Inventory(entries, submit);
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

    private Set<TaskEngine.Signal.RegistryEntry> structuresAt(
        ServerPlayer player
    ) {
        java.util.List<QuestDefinition.Task> tasks = catalog
            .quests()
            .values()
            .stream()
            .filter(quest -> isUnlocked(player, quest))
            .flatMap(quest -> flattenTasks(quest.tasks()).stream())
            .filter(task -> task.kind() == QuestDefinition.TaskKind.STRUCTURE)
            .toList();
        if (tasks.isEmpty()) return Set.of();
        return world.structuresAt(player)
            .stream()
            .filter(entry -> tasks.stream().anyMatch(task ->
                RegistryPredicate.matches(
                    task.source().get("structures"),
                    task.value(),
                    entry
                )
            ))
            .collect(Collectors.toSet());
    }

    private static JsonObject playerData(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING,
            player.registryAccess()
        );
        player.saveWithoutId(output);
        com.google.gson.JsonElement json = NbtOps.INSTANCE.convertTo(
            JsonOps.INSTANCE,
            output.buildResult()
        );
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    private static java.util.List<String> configuredStrings(
        QuestDefinition.Task task,
        String key,
        String fallback
    ) {
        if (!task.source().has(key)) return fallback.isBlank()
            ? java.util.List.of()
            : java.util.List.of(fallback);
        var value = task.source().get(key);
        if (value.isJsonArray()) return value
            .getAsJsonArray()
            .asList()
            .stream()
            .map(com.google.gson.JsonElement::getAsString)
            .toList();
        return java.util.List.of(value.getAsString());
    }

    private static List<QuestDefinition.Task> flattenTasks(
        Map<String, QuestDefinition.Task> tasks
    ) {
        List<QuestDefinition.Task> result = new java.util.ArrayList<>();
        for (QuestDefinition.Task task : tasks.values()) {
            result.add(task);
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) result.addAll(flattenTasks(task.tasks()));
        }
        return result;
    }

    private QuestProgressState progress(ServerPlayer player, String questId) {
        return progress
            .computeIfAbsent(world.playerId(player), ignored -> new HashMap<>())
            .computeIfAbsent(questId, ignored -> new QuestProgressState());
    }

    private void changed(ServerPlayer player) {
        saveProgress();
        sync(player, false);
    }

    private void changed(
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

    private Map<String, Boolean> questStates(
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

    private void notify(
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
    private String snapshot(ServerPlayer player, String chapter) {
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

    private JsonObject fullQuest(QuestDefinition quest) {
        try {
            return catalog.rawQuest(quest.id());
        } catch (Exception ignored) {
            return GSON.toJsonTree(quest).getAsJsonObject();
        }
    }

    private static JsonObject lightweightQuest(QuestDefinition quest) {
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

    private void loadProgress() {
        try {
            progressStore.load().entrySet().forEach(player -> {
                try {
                    if (!player.getValue().isJsonObject()) throw new IllegalArgumentException("Player progress must be an object");
                    progress.put(
                        UUID.fromString(player.getKey()),
                        parsePlayerProgress(player.getValue().getAsJsonObject())
                    );
                } catch (RuntimeException exception) {
                    Theseus.LOGGER.warn("Ignoring malformed progress for player '{}': {}", player.getKey(), exception.getMessage());
                }
            });
            // Re-emit legacy entries in the current explicit shape, while
            // retaining valid progress from other players.
            saveProgress();
        } catch (Exception exception) {
            Theseus.LOGGER.error(
                "Failed to load quest progress from {}",
                progressStore,
                exception
            );
        }
    }

    private Map<String, QuestProgressState> parsePlayerProgress(JsonObject root) {
        Map<String, QuestProgressState> result = new HashMap<>();
        root.entrySet().forEach(entry -> {
            QuestDefinition quest = catalog.quests().get(entry.getKey());
            if (quest == null) {
                Theseus.LOGGER.warn("Ignoring progress for unknown quest '{}'", entry.getKey());
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

    private void saveProgress() {
        try {
            JsonObject root = new JsonObject();
            progress.forEach((playerId, quests) -> {
                JsonObject player = new JsonObject();
                quests.forEach((questId, state) -> player.add(questId, state.toJson()));
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

}
