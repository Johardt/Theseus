package me.johardt.theseus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.johardt.theseus.Theseus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import static me.johardt.theseus.core.QuestRuntime.*;

/** Applies quest creation, editing, import, and chapter mutations. */
final class QuestRuntimeMutations {
    private final QuestRuntime runtime;

    QuestRuntimeMutations(QuestRuntime runtime) {
        this.runtime = runtime;
    }

    MutationResult applyEditorMutation(ServerPlayer player, QuestMutation mutation) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
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

    MutationResult createQuest(ServerPlayer player, JsonObject draft) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return createQuest(draft);
    }

    MutationResult createQuest(JsonObject draft) {
        if (hasCanonicalDocument(draft)) return createDocumentQuest(draft);
        String id = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!id.matches("[a-z0-9_.-]+")) {
            return MutationResult.failure("Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        }
        try {
            if (runtime.catalog.documents().contains(id) || runtime.catalog.quests().containsKey(id)) {
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
            runtime.catalog.documents().createQuest(id, root);
            runtime.reload();
            return MutationResult.success("Quest '" + id + "' created" + warningSuffix(validationWarnings), finalValidation.diagnostics());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error("Failed to create quest {}", id, exception);
            return MutationResult.failure("Failed to write quest '" + id + "'");
        }
    }

    MutationResult updateQuest(ServerPlayer player, JsonObject draft) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return updateQuest(draft);
    }

    MutationResult updateQuest(JsonObject draft) {
        if (hasCanonicalDocument(draft)) return updateDocumentQuest(draft);
        String oldId = draft.has("original_id") ? draft.get("original_id").getAsString() : "";
        String newId = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!runtime.catalog.quests().containsKey(oldId)) return MutationResult.failure("The original quest no longer exists");
        if (runtime.catalog.hasConflict(oldId)) return MutationResult.failure("Quest ID '" + oldId + "' is duplicated; resolve the conflicting files before editing it");
        if (!newId.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
        if (!oldId.equals(newId) && runtime.catalog.quests().containsKey(newId)) {
            return MutationResult.failure("A quest with ID '" + newId + "' already exists");
        }
        JsonObject changedFields = draft.has("changed_fields") && draft.get("changed_fields").isJsonObject()
            ? draft.getAsJsonObject("changed_fields") : new JsonObject();
        MutationResult basicValidation = validateDraftDisplay(draft, changedFields);
        if (!basicValidation.success()) return basicValidation;
        try {
            JsonObject root = runtime.catalog.documents().readQuest(oldId);
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
            runtime.catalog.documents().saveQuest(oldId, newId, root);
            boolean progressAffecting = !previousTasks.equals(root.getAsJsonObject("tasks")) ||
                !previousRewards.equals(root.getAsJsonObject("rewards"));
            if (progressAffecting) resetQuestProgress(oldId, newId);
            else if (!oldId.equals(newId)) migrateQuestProgress(oldId, newId);
            runtime.reload();
            return MutationResult.success("Quest '" + newId + "' saved" + warningSuffix(validationWarnings), finalValidation.diagnostics());
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to update quest {}", oldId, exception);
            return MutationResult.failure("Failed to update quest '" + oldId + "'");
        }
    }

    MutationResult createDocumentQuest(JsonObject request) {
        String id = request.has("id") ? request.get("id").getAsString().trim() : "";
        if (!id.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        try {
            if (runtime.catalog.documents().contains(id) || runtime.catalog.quests().containsKey(id)) return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            return MutationResult.failure("Failed to inspect quest storage");
        }
        JsonObject root = authoredDocument(request.getAsJsonObject("document"));
        applyPlacement(root, request);
        MutationResult validation = validateQuest(id, root);
        if (!validation.success()) return validation;
        try {
            runtime.catalog.documents().createQuest(id, root);
            runtime.reload();
            return MutationResult.success("Quest '" + id + "' created" + warningSuffix(validation.message()), validation.diagnostics());
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.error("Failed to create quest {}", id, exception);
            return MutationResult.failure("Failed to write quest '" + id + "'");
        }
    }

    MutationResult updateDocumentQuest(JsonObject request) {
        String oldId = request.has("original_id") ? request.get("original_id").getAsString() : "";
        String newId = request.has("id") ? request.get("id").getAsString().trim() : "";
        if (!runtime.catalog.quests().containsKey(oldId)) return MutationResult.failure("The original quest no longer exists");
        if (runtime.catalog.hasConflict(oldId)) return MutationResult.failure("Quest ID '" + oldId + "' is duplicated; resolve the conflicting files before editing it");
        if (!newId.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
        if (!oldId.equals(newId) && runtime.catalog.quests().containsKey(newId)) return MutationResult.failure("A quest with ID '" + newId + "' already exists");
        if (!request.get("document").isJsonObject()) return MutationResult.failure("Quest document is invalid");
        try {
            JsonObject previousRoot = runtime.catalog.documents().readQuest(oldId);
            JsonObject proposed = authoredDocument(request.getAsJsonObject("document"));
            JsonArray changedPaths = request.has("changed_paths") && request.get("changed_paths").isJsonArray()
                ? request.getAsJsonArray("changed_paths") : new JsonArray();
            String conflict = QuestDraft.firstConflict(previousRoot, changedPaths);
            if (conflict != null) return MutationResult.failure(conflict);
            JsonObject root = QuestDraft.merge(previousRoot, proposed, changedPaths);
            JsonObject previousTasks = object(previousRoot, "tasks");
            JsonObject previousRewards = object(previousRoot, "rewards");
            MutationResult validation = validateQuest(newId, root);
            if (!validation.success()) return validation;
            runtime.catalog.documents().saveQuest(oldId, newId, root);
            boolean progressAffecting = !previousTasks.equals(object(root, "tasks")) || !previousRewards.equals(object(root, "rewards"));
            if (progressAffecting) resetQuestProgress(oldId, newId);
            else if (!oldId.equals(newId)) migrateQuestProgress(oldId, newId);
            runtime.reload();
            return MutationResult.success("Quest '" + newId + "' saved" + warningSuffix(validation.message()), validation.diagnostics());
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to update quest {}", oldId, exception);
            return MutationResult.failure("Failed to update quest '" + oldId + "'");
        }
    }

    static boolean hasCanonicalDocument(JsonObject request) {
        return request != null && request.has("document") && request.get("document").isJsonObject();
    }

    static boolean isString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
            && object.get(key).getAsJsonPrimitive().isString();
    }

    static JsonObject authoredDocument(JsonObject source) {
        JsonObject document = source == null ? new JsonObject() : source.deepCopy();
        List.of("progress", "unlocked", "complete", "claimed", "claimed_rewards", "pinned", "issues", "__chapters", "__editor_types")
            .forEach(document::remove);
        return document;
    }

    static void applyPlacement(JsonObject root, JsonObject request) {
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

    static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key).deepCopy() : new JsonObject();
    }

    MutationResult importQuests(ServerPlayer player, JsonObject request) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return importQuests(request);
    }

    MutationResult importQuests(JsonObject request) {
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
            if (runtime.catalog.quests().containsKey(id) || runtime.catalog.hasConflict(id)) {
                diagnostics.add(new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "duplicate_catalog_id", id, "id", "A quest with ID '" + id + "' already exists", "Choose a different ID."));
                return;
            }
            JsonObject root = entry.getValue().getAsJsonObject().deepCopy();
            if (!defaultChapter.isBlank() && runtime.catalog.groupOrder().contains(defaultChapter)) {
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
            diagnostics.addAll(RegistryValidation.validate(id, root, runtime.world::containsRegistryTarget));
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
        Map<String, QuestDefinition> combined = new java.util.LinkedHashMap<>(runtime.catalog.quests());
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
            runtime.catalog.documents().importQuests(quests);
            runtime.reload();
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

    MutationResult pasteQuest(ServerPlayer player, JsonObject request) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return pasteQuest(request);
    }

    MutationResult pasteQuest(JsonObject request) {
        String sourceId = request.has("source_id") ? request.get("source_id").getAsString() : "";
        String chapter = request.has("chapter") ? request.get("chapter").getAsString() : "";
        boolean chapterOnly = request.has("chapter_only") && request.get("chapter_only").getAsBoolean();
        try {
            if (chapterOnly) {
                if (!runtime.catalog.quests().containsKey(sourceId) || !runtime.catalog.groupOrder().contains(chapter)) return MutationResult.failure("Unknown quest or chapter");
                JsonObject root = runtime.catalog.documents().readQuest(sourceId);
                addChapterPlacement(root, chapter, request);
                runtime.catalog.documents().writeQuest(sourceId, root);
                runtime.reload();
                return MutationResult.success("Added '" + sourceId + "' to chapter '" + chapter + "'");
            }
            String id = request.has("id") ? request.get("id").getAsString().trim() : "";
            if (!id.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
            JsonObject root = request.has("quest") && request.get("quest").isJsonObject()
                ? request.getAsJsonObject("quest").deepCopy() : null;
            boolean move = request.has("move") && request.get("move").getAsBoolean();
            if (move) {
                if (!runtime.catalog.quests().containsKey(sourceId) || runtime.catalog.hasConflict(sourceId)) return MutationResult.failure("The source quest is no longer available for moving");
            }
            if (root == null && runtime.catalog.quests().containsKey(sourceId)) {
                root = runtime.catalog.documents().readQuest(sourceId);
            }
            if (root == null) return MutationResult.failure("Clipboard quest data is missing");
            if (!chapter.isBlank()) addChapterPlacement(root, chapter, request);
            MutationResult validation = validateQuest(id, root);
            if (!validation.success()) return validation;
            if (move && sourceId.equals(id) && runtime.catalog.quests().containsKey(sourceId)) {
                runtime.catalog.documents().writeQuest(sourceId, root);
                runtime.reload();
                return MutationResult.success("Moved '" + sourceId + "'");
            }
            runtime.catalog.documents().transferQuest(sourceId, id, root, move);
            runtime.reload();
            return MutationResult.success((move ? "Moved '" + sourceId : "Copied '" + sourceId) + "' as '" + id + "'");
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to paste quest", exception);
            return MutationResult.failure("Paste failed: " + exception.getMessage());
        }
    }

    static void addChapterPlacement(JsonObject root, String chapter, JsonObject request) {
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

    static String firstValidationError(QuestDefinition definition) {
        return definition.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(issue -> issue.path() + ": " + issue.message())
            .findFirst()
            .orElse("Quest contains invalid configuration");
    }

    MutationResult validateQuest(String id, JsonObject root) {
        List<QuestDiagnostics.Diagnostic> diagnostics = new java.util.ArrayList<>(QuestDiagnostics.validate(id, root, icon -> {
            try { return BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon)); }
            catch (RuntimeException exception) { return false; }
        }));
        diagnostics.addAll(RegistryValidation.validate(id, root, runtime.world::containsRegistryTarget));
        List<QuestDiagnostics.Diagnostic> errors = diagnostics.stream().filter(QuestDiagnostics.Diagnostic::blocksSave).toList();
        if (errors.isEmpty()) return MutationResult.success(diagnostics.stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).map(diagnostic -> diagnostic.path() + ": " + diagnostic.message()).collect(Collectors.joining("\n")), diagnostics);
        return MutationResult.failure(errors.stream().map(diagnostic -> diagnostic.path() + ": " + diagnostic.message()).collect(Collectors.joining("\n")), diagnostics);
    }

    static String warningSuffix(String warnings) { return warnings == null || warnings.isBlank() ? "" : " (warnings: " + warnings.replace('\n', ';') + ")"; }

    QuestFileResult openQuestFileResult(ServerPlayer player, String questId) {
        if (!runtime.world.canEdit(player)) {
            return QuestFileResult.failure("You do not have permission to open quest files");
        }
        if (!runtime.world.isIntegratedServer()) {
            return QuestFileResult.failure("Open quest file is only available in an integrated server");
        }
        if (questId == null || !runtime.catalog.quests().containsKey(questId) || runtime.catalog.hasConflict(questId)) {
            return QuestFileResult.failure("Quest file is unavailable");
        }
        try {
            return QuestFileResult.success(runtime.catalog.documents().relativeQuestPath(questId));
        } catch (java.io.IOException exception) {
            Theseus.LOGGER.warn("Failed to resolve quest file for {}", questId, exception);
            return QuestFileResult.failure("Quest file is unavailable");
        }
    }

    void deleteQuest(ServerPlayer player, String id) {
        deleteQuestResult(player, id);
    }

    MutationResult deleteQuestResult(ServerPlayer player, String id) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return deleteQuestResult(id);
    }

    MutationResult deleteQuestResult(JsonObject request) {
        String id = request.has("id") && request.get("id").isJsonPrimitive() ? request.get("id").getAsString() : "";
        return deleteQuestResult(id);
    }

    MutationResult deleteQuestResult(String id) {
        if (!runtime.catalog.quests().containsKey(id)) return MutationResult.failure("Quest '" + id + "' does not exist");
        if (runtime.catalog.hasConflict(id)) return MutationResult.failure("Quest ID '" + id + "' is duplicated; resolve the conflicting files first");
        try {
            runtime.catalog.documents().deleteQuest(id);
            resetQuestProgress(id, null);
            runtime.reload();
            return MutationResult.success("Quest '" + id + "' deleted");
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to delete quest {}", id, exception);
            return MutationResult.failure("Failed to delete quest '" + id + "'");
        }
    }

    MutationResult chapterMutationResult(ServerPlayer player, JsonObject action) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return chapterMutationResult(action);
    }

    MutationResult chapterMutationResult(JsonObject action) {
        String operation = action.has("operation") ? action.get("operation").getAsString() : "";
        String name = action.has("name") ? action.get("name").getAsString().trim() : "";
        if (!List.of("create", "update", "delete", "reorder").contains(operation)) return MutationResult.failure("Unknown chapter operation");
        if ((operation.equals("create") || operation.equals("update")) && name.isEmpty()) return MutationResult.failure("Chapter name is required");
        if (operation.equals("create") && runtime.catalog.groupOrder().contains(name)) return MutationResult.failure("That chapter already exists");
        if (operation.equals("update") && (!runtime.catalog.groupOrder().contains(action.has("old_name") ? action.get("old_name").getAsString() : "") || runtime.catalog.groupOrder().contains(name) && !name.equals(action.get("old_name").getAsString()))) return MutationResult.failure("Invalid chapter rename");
        if (operation.equals("delete") && !runtime.catalog.groupOrder().contains(name)) return MutationResult.failure("That chapter does not exist");
        try { chapterActionAuthorized(action); return MutationResult.success("Chapter change applied"); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Invalid chapter change" : exception.getMessage()); }
    }

    MutationResult removeQuestGroupResult(ServerPlayer player, JsonObject action) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return removeQuestGroupResult(action);
    }

    MutationResult removeQuestGroupResult(JsonObject action) {
        String id = action.has("id") ? action.get("id").getAsString() : "";
        String group = action.has("group") ? action.get("group").getAsString() : "";
        QuestDefinition quest = runtime.catalog.quests().get(id);
        if (quest == null || !quest.display().groups().containsKey(group)) return MutationResult.failure("Quest is not in that chapter");
        if (quest.display().groups().size() <= 1) return MutationResult.failure("A quest must remain in at least one chapter");
        try { removeQuestFromGroupAuthorized(id, group); return MutationResult.success("Quest removed from chapter"); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Chapter removal failed" : exception.getMessage()); }
    }

    MutationResult dependencyMutationResult(ServerPlayer player, JsonObject action) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return dependencyMutationResultAuthorized(player, action);
    }

    MutationResult dependencyMutationResultAuthorized(ServerPlayer player, JsonObject action) {
        String prerequisite = action.has("prerequisite") ? action.get("prerequisite").getAsString() : "";
        String dependent = action.has("dependent") ? action.get("dependent").getAsString() : "";
        boolean remove = action.has("remove") && action.get("remove").getAsBoolean();
        if (!runtime.catalog.quests().containsKey(prerequisite) || !runtime.catalog.quests().containsKey(dependent)) return MutationResult.failure("Unknown quest in dependency link");
        if (!remove && QuestCatalog.wouldCreateCycle(runtime.catalog.quests(), prerequisite, dependent)) {
            return MutationResult.failure("Dependency cycle: " + String.join(" → ", QuestCatalog.dependencyCyclePath(runtime.catalog.quests(), prerequisite, dependent)));
        }
        try { return setDependencyAuthorized(player, prerequisite, dependent, remove); }
        catch (RuntimeException exception) { return MutationResult.failure(exception.getMessage() == null ? "Dependency change failed" : exception.getMessage()); }
    }

    MutationResult resetProgressResult(ServerPlayer player, JsonObject action) {
        if (!runtime.world.canEdit(player)) {
            return MutationResult.failure("You do not have permission to reset quest progress");
        }
        return resetProgressResultAuthorized(player, action);
    }

    MutationResult resetProgressResultAuthorized(ServerPlayer player, JsonObject action) {
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
        QuestDefinition quest = runtime.catalog.quests().get(questId);
        if (quest == null || runtime.catalog.hasConflict(questId)) {
            return MutationResult.failure("Unknown quest '" + questId + "'");
        }

        QuestProgressState state = runtime.progress(player, questId);
        switch (scope) {
            case "quest" -> {
                if (!entry.isBlank()) return MutationResult.failure("Quest reset does not accept an entry");
                state.clearProgress();
                runtime.changed(player);
                return MutationResult.success("Reset quest progress for '" + quest.title() + "' (" + questId + ") for the current player");
            }
            case "task" -> {
                if (entry.isBlank()) return MutationResult.failure("Task reset requires a task path");
                QuestDefinition.Task task = QuestRuntime.resolveTask(quest.tasks(), entry);
                if (task == null) return MutationResult.failure("Unknown task path '" + entry + "' in quest '" + questId + "'");
                state.resetTaskPath(entry);
                runtime.refreshCompositeProgress(player, quest);
                runtime.changed(player);
                return MutationResult.success("Reset task progress for '" + entry + "' in quest '" + questId + "' for the current player");
            }
            case "reward" -> {
                if (entry.isBlank()) return MutationResult.failure("Reward reset requires a reward ID");
                if (!quest.rewards().containsKey(entry)) {
                    return MutationResult.failure("Unknown top-level reward '" + entry + "' in quest '" + questId + "'");
                }
                state.unmarkRewardClaimed(entry);
                runtime.changed(player);
                return MutationResult.success("Reset reward progress for '" + entry + "' in quest '" + questId + "' for the current player");
            }
            default -> {
                return MutationResult.failure("Unknown reset progress scope '" + scope + "'");
            }
        }
    }

    void removeQuestFromGroup(ServerPlayer player, String id, String group) {
        if (!runtime.world.canEdit(player)) return;
        removeQuestFromGroupAuthorized(id, group);
    }

    void removeQuestFromGroupAuthorized(String id, String group) {
        try {
            JsonObject root = runtime.catalog.documents().readQuest(id);
            JsonObject groups = root.getAsJsonObject("display").getAsJsonObject("groups");
            if (groups.size() <= 1 || !groups.has(group)) return;
            groups.remove(group);
            runtime.catalog.documents().writeQuest(id, root);
            runtime.reload();
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed to remove quest {} from chapter {}", id, group, exception);
        }
    }

    void chapterAction(ServerPlayer player, JsonObject action) {
        if (!runtime.world.canEdit(player)) return;
        chapterActionAuthorized(action);
    }

    void chapterActionAuthorized(JsonObject action) {
        String operation = action.has("operation") ? action.get("operation").getAsString() : "";
        List<String> order = new java.util.ArrayList<>(runtime.catalog.groupOrder());
        Map<String, QuestCatalog.ChapterSettings> settings = new java.util.LinkedHashMap<>(runtime.catalog.chapterSettings());
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
            runtime.catalog.documents().updateChapters(order, settings, change);
            runtime.reload();
        } catch (Exception exception) {
            Theseus.LOGGER.error("Failed chapter operation {}", operation, exception);
        }
    }

    static String validChapterName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 64 || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("Invalid chapter name");
        }
        return name;
    }

    static QuestCatalog.ChapterSettings chapterSettings(JsonObject action) {
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

    void resetQuestProgress(String oldId, String newId) {
        runtime.progress.values().forEach(quests -> {
            quests.remove(oldId);
            if (newId != null) quests.remove(newId);
        });
        runtime.deferredProgress.values().forEach(quests -> {
            quests.remove(oldId);
            if (newId != null) quests.remove(newId);
        });
        runtime.saveProgress();
    }

    void migrateQuestProgress(String oldId, String newId) {
        runtime.progress.values().forEach(quests -> {
            QuestProgressState state = quests.remove(oldId);
            if (state != null) quests.put(newId, state);
        });
        runtime.deferredProgress.values().forEach(quests -> {
            JsonElement state = quests.remove(oldId);
            if (state != null) quests.put(newId, state);
        });
        runtime.saveProgress();
    }

    MutationResult setDependency(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        if (!runtime.world.canEdit(player)) return MutationResult.failure("You do not have permission to edit quests");
        return setDependencyAuthorized(player, prerequisiteId, dependentId, remove);
    }

    MutationResult setDependencyAuthorized(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        QuestDefinition prerequisite = runtime.catalog.quests().get(prerequisiteId);
        QuestDefinition dependent = runtime.catalog.quests().get(dependentId);
        if (prerequisite == null || dependent == null) {
            runtime.world.message(player, "Unknown quest in dependency link");
            return MutationResult.failure("Unknown quest in dependency link");
        }
        if (prerequisiteId.equals(dependentId)) {
            runtime.world.message(player, "A quest cannot depend on itself");
            return MutationResult.failure("A quest cannot depend on itself");
        }
        Set<String> dependencies = new java.util.LinkedHashSet<>(dependent.dependencies());
        if (remove) {
            if (!dependencies.remove(prerequisiteId)) return MutationResult.failure("Dependency does not exist");
        } else {
            if (dependencies.contains(prerequisiteId)) return MutationResult.failure("Dependency already exists");
            if (QuestCatalog.wouldCreateCycle(
                runtime.catalog.quests(),
                prerequisiteId,
                dependentId
            )) {
                runtime.world.message(player, "That link would create a dependency cycle");
                return MutationResult.failure("Dependency cycle: " + String.join(" → ", QuestCatalog.dependencyCyclePath(runtime.catalog.quests(), prerequisiteId, dependentId)));
            }
            dependencies.add(prerequisiteId);
        }
        try {
            runtime.catalog.documents().updateDependencies(dependentId, dependencies);
            runtime.reload();
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

    List<QuestDefinition.ValidationIssue> validationIssues() {
        return runtime.catalog.issues();
    }
}
