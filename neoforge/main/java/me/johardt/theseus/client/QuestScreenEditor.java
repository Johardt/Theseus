package me.johardt.theseus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestDraft;
import me.johardt.theseus.core.QuestMutation;
import me.johardt.theseus.core.QuestMutationCoordinator;
import me.johardt.theseus.core.RegistryValidation;
import me.johardt.theseus.core.QuestNetwork;
import me.johardt.theseus.client.description.DescriptionDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Owns draft editing transitions, validation, and mutation completion. */
final class QuestScreenEditor {
    private final QuestScreen screen;

    QuestScreenEditor(QuestScreen screen) {
        this.screen = screen;
    }

    void beginEditQuest(ClientQuest quest) {
        if (!ensureChapterDataLoaded()) return;
        QuestDefinition definition = quest.definition();
        screen.authoring.editingExisting = true;
        screen.authoring.originalId = definition.id();
        screen.selectedQuestId = definition.id();
        screen.authoring.id = definition.id();
        screen.authoring.title = definition.title();
        screen.authoring.subtitle = definition.subtitle();
        screen.authoring.body = String.join("\n", definition.description());
        screen.authoring.icon = definition.display().icon().item();
        screen.authoring.iconSize = definition.display().iconSize();
        screen.authoring.iconSizeText = Integer.toString(screen.authoring.iconSize);
        screen.authoring.iconSizeTouched = false;
        screen.authoring.iconSizeInvalid = false;
        screen.authoring.descriptionTouched = false;
        screen.authoring.iconTouched = false;
        screen.authoring.background = definition.display().iconBackground();
        screen.authoring.individualProgress = definition.settings().individualProgress();
        screen.authoring.hiddenUntil = definition.settings().hiddenUntil();
        screen.authoring.unlockNotification = definition.settings().unlockNotification();
        screen.authoring.showDependencyArrow = definition.settings().showDependencyArrow();
        screen.authoring.repeatable = definition.settings().repeatable();
        screen.authoring.autoClaimRewards = definition.settings().autoClaimRewards();
        screen.authoring.groups = new JsonObject();
        definition.display().groups().forEach((name, position) -> {
            JsonObject placement = new JsonObject();
            com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
            coordinates.add(position.x());
            coordinates.add(position.y());
            placement.add("position", coordinates);
            screen.authoring.groups.add(name, placement);
        });
        QuestDefinition.GroupDisplay position = definition.position(screen.group);
        screen.authoring.x = position.x();
        screen.authoring.y = position.y();
        screen.authoring.xText = Integer.toString(screen.authoring.x);
        screen.authoring.yText = Integer.toString(screen.authoring.y);
        screen.authoring.xInvalid = false;
        screen.authoring.yInvalid = false;
        screen.authoring.tasks.clear();
        definition.tasks().values().forEach(task -> screen.authoring.tasks.add(new QuestAuthoringSession.TaskDraft(task.id(), task.type(), task.source().deepCopy())));
        screen.authoring.rewards.clear();
        definition.rewards().values().forEach(reward -> screen.authoring.rewards.add(new QuestAuthoringSession.RewardDraft(reward.id(), reward.type(), reward.source().deepCopy())));
        screen.authoringPanel.createQuestTab = DetailTab.OVERVIEW;
        screen.authoringPanel.resetOverviewScroll();
        screen.authoringPanel.resetDraftScrolls();
        screen.detailsOpen = false;
        screen.authoring.open = true;
        screen.editorMessage = definition.issues().stream().anyMatch(issue -> issue.severity() == QuestDefinition.Severity.WARNING)
            ? "Unsupported configuration is preserved and shown read-only."
            : "";
        screen.editorMessageSuccess = false;
        screen.mutations.cancel();
        screen.authoring.begin(QuestDraft.fromClientSnapshot(definition.id(), quest.raw()));
        establishAuthoringBaseline();
        screen.rebuildWidgets();
    }

    boolean ensureChapterDataLoaded() {
        if (screen.loadedChapters.contains(screen.group)) return true;
        screen.snapshots.requestChapter(screen.group);
        screen.editorMessage = "Loading chapter data…";
        screen.editorMessageSuccess = false;
        screen.rebuildWidgets();
        return false;
    }

    /**
     * The form exposes parsed defaults for fields that may be omitted from a
     * quest document. Treat that populated form as the initial baseline so an
     * untouched quest is not reported as having changes.
     */
    void establishAuthoringBaseline() {
        QuestDraft baseline = currentAuthoringDraft();
        baseline.accept();
        screen.authoring.begin(baseline);
    }

    void beginCreateQuest(double treeX, double treeY) {
        screen.authoring.id = "";
        screen.authoring.title = "";
        screen.authoring.subtitle = "";
        screen.authoring.body = "";
        screen.authoring.icon = "minecraft:map";
        screen.authoring.iconSize = QuestSurfaceLayout.DEFAULT_ICON_SIZE;
        screen.authoring.iconSizeText = Integer.toString(screen.authoring.iconSize);
        screen.authoring.iconSizeTouched = false;
        screen.authoring.iconSizeInvalid = false;
        screen.authoring.descriptionTouched = false;
        screen.authoring.iconTouched = false;
        screen.authoring.background = "theseus:textures/gui/quest_backgrounds/default.png";
        screen.authoring.individualProgress = false;
        screen.authoring.hiddenUntil = QuestDefinition.Visibility.LOCKED;
        screen.authoring.unlockNotification = false;
        screen.authoring.showDependencyArrow = true;
        screen.authoring.repeatable = false;
        screen.authoring.autoClaimRewards = false;
        screen.authoring.tasks.clear();
        screen.authoring.rewards.clear();
        screen.authoring.editingExisting = false;
        screen.authoring.originalId = null;
        screen.authoring.groups = new JsonObject();
        screen.authoringPanel.resetDraftTaskScroll();
        screen.authoringPanel.createQuestTab = DetailTab.OVERVIEW;
        screen.authoringPanel.resetOverviewScroll();
        screen.authoring.x = (int) Math.round(treeX);
        screen.authoring.y = (int) Math.round(treeY);
        if (TheseusClientOptions.snapToGrid()) {
            QuestGraphLayout.Point snapped = QuestGraphLayout.snapPoint(screen.authoring.x, screen.authoring.y);
            screen.authoring.x = (int) snapped.x();
            screen.authoring.y = (int) snapped.y();
        }
        screen.authoring.xText = Integer.toString(screen.authoring.x);
        screen.authoring.yText = Integer.toString(screen.authoring.y);
        screen.authoring.xInvalid = false;
        screen.authoring.yInvalid = false;
        updateDraftGroupPosition();
        screen.detailsOpen = false;
        screen.authoring.open = true;
        screen.editorMessage = "";
        screen.editorMessageSuccess = false;
        screen.mutations.cancel();
        screen.authoring.begin(QuestDraft.create(null));
        screen.rebuildWidgets();
    }

    void updateDraftGroupPosition() {
        JsonObject placement = screen.authoring.groups.has(screen.group) && screen.authoring.groups.get(screen.group).isJsonObject()
            ? screen.authoring.groups.getAsJsonObject(screen.group) : new JsonObject();
        com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
        coordinates.add(screen.authoring.x);
        coordinates.add(screen.authoring.y);
        placement.add("position", coordinates);
        screen.authoring.groups.add(screen.group, placement);
        screen.authoring.setGroupPosition(screen.group, screen.authoring.x, screen.authoring.y);
    }

    void confirmProgressReset() {
        if (screen.progressResetTarget == null || screen.mutations.isPending()) return;
        QuestModalHost.ProgressResetTarget target = screen.progressResetTarget;
        JsonObject request = new JsonObject();
        request.addProperty("scope", target.scope());
        request.addProperty("quest", target.questId());
        request.addProperty("entry", target.entryId());
        screen.progressResetTarget = null;
        screen.editorMessage = "Resetting progress…";
        screen.editorMessageSuccess = false;
        sendEditorMutation(new QuestMutation.ResetProgress(request));
        screen.rebuildWidgets();
    }

    void confirmDeleteQuest() {
        screen.modalHost.close();
        JsonObject request = new JsonObject();
        request.addProperty("id", screen.authoring.originalId);
        screen.editorMessage = "Deleting…";
        screen.editorMessageSuccess = false;
        sendEditorMutation(new QuestMutation.DeleteQuest(request));
        screen.rebuildWidgets();
    }

    void confirmDeleteTask() {
        if (screen.authoring.taskDeleteConfirmation < screen.authoring.tasks.size()) {
            screen.authoring.tasks.remove(screen.authoring.taskDeleteConfirmation);
            screen.authoringPanel.clampDraftTaskScroll();
        }
        screen.authoring.taskDeleteConfirmation = -1;
        screen.modalHost.close();
        screen.rebuildWidgets();
    }

    void removeExistingQuestFromChapter() {
        JsonObject change = new JsonObject();
        change.addProperty("id", screen.authoring.originalId);
        change.addProperty("group", screen.group);
        sendEditorMutation(new QuestMutation.RemoveQuestGroup(change));
        screen.authoring.open = false;
        screen.authoring.editingExisting = false;
        screen.authoring.originalId = null;
        screen.rebuildWidgets();
    }

    /** The same editable task rows used at the quest root, scoped to a composite. */
    void openPicker(Picker value) {
        openPicker(value, PickerTarget.QUEST_ICON);
    }

    void openPicker(Picker value, PickerTarget target) {
        screen.picker = value;
        screen.pickerTarget = target;
        screen.pickerScroll = screen.pickerScrollByTarget.getOrDefault(target, 0);
        screen.pickerSearch = null;
        screen.modalHost.open(QuestModalHost.Modal.PICKER);
        screen.rebuildWidgets();
    }

    void closePicker() {
        if (screen.picker == Picker.NONE) return;
        screen.picker = Picker.NONE;
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER)) screen.modalHost.close();
    }

    QuestAuthoringSession.RewardDraft activeRewardDraft() {
        return screen.authoring.editingNestedReward != null ? screen.authoring.editingNestedReward : screen.authoring.editingReward;
    }

    boolean validCreateQuestDraft() {
        return draftValidationError().isEmpty() && !screen.mutations.isPending();
    }

    String draftValidationError() {
        String sessionError = screen.authoring.validationError(
            value -> clientContainsRegistryTarget(RegistryValidation.Target.ITEM, value),
            QuestScreenEditor::clientContainsRegistryTarget
        );
        if (!sessionError.isEmpty()) return sessionError;
        if (screen.quests.stream().anyMatch(quest -> quest.definition().id().equals(screen.authoring.id) &&
            (!screen.authoring.editingExisting || !quest.definition().id().equals(screen.authoring.originalId)))) {
            return "Another quest already uses this ID.";
        }
        for (int index = 0; index < screen.authoring.tasks.size(); index++) {
            QuestAuthoringSession.TaskDraft task = screen.authoring.tasks.get(index);
            if (screen.authoringPanel.taskEditor.isTaskEditable(task)) {
                String error = screen.authoringPanel.taskEditor.validateTaskDraft(task.copy(), index);
                if (!error.isEmpty()) return "Task '" + task.id + "': " + error;
            }
        }
        return "";
    }

    void confirmCreateQuest() {
        String validationError = draftValidationError();
        if (!validationError.isEmpty()) {
            screen.editorMessage = validationError;
            screen.editorMessageSuccess = false;
            screen.rebuildWidgets();
            return;
        }
        updateDraftGroupPosition();
        QuestDraft draft = currentAuthoringDraft();
        JsonObject request = screen.authoring.editingExisting
            ? draft.updateMutation()
            : draft.createMutation(screen.group, screen.authoring.x, screen.authoring.y);
        String json = GSON.toJson(request);
        if (json.length() > QuestNetwork.EditorMutationPayload.MAX_JSON_LENGTH) {
            screen.editorMessage = "This quest is too large to save (maximum 1 MiB).";
            screen.editorMessageSuccess = false;
            screen.rebuildWidgets();
            return;
        }
        screen.editorMessage = "Saving…";
        screen.editorMessageSuccess = false;
        sendEditorMutation(screen.authoring.editingExisting
            ? new QuestMutation.UpdateQuest(request)
            : new QuestMutation.CreateQuest(request));
        screen.rebuildWidgets();
    }

    JsonObject draftSnapshot() {
        return currentAuthoringDraft().snapshot();
    }

    QuestDraft currentAuthoringDraft() {
        return screen.authoring.draft();
    }

    JsonObject draftDisplay() {
        JsonElement display = currentAuthoringDraft().snapshot().get("display");
        return display != null && display.isJsonObject() ? display.getAsJsonObject() : new JsonObject();
    }

    QuestSurfaceLayout.Node authoringNodeLayout() {
        QuestDefinition definition = QuestDefinition.parse("editor", currentAuthoringDraft().snapshot());
        return QuestSurfaceLayout.node(new QuestSurfaceLayout.QuestNode(
            "__draft",
            screen.authoring.x,
            screen.authoring.y,
            definition.display().iconSize(),
            definition.display().iconBackground()
        ));
    }

    boolean hasUnsavedDraft() {
        if (hasUnsavedModal()) return true;
        return screen.authoring.open && screen.authoring.hasBaseline() && currentAuthoringDraft().isDirty();
    }

    boolean hasUnsavedModal() {
        if (screen.widgets.hasUnsavedChapterEditor()) return true;
        if (screen.authoring.editingTask != null && screen.authoring.editingTaskIndex >= 0 && screen.authoring.editingTaskIndex < screen.authoring.tasks.size() &&
            !screen.authoring.editingTask.sameAs(screen.authoring.tasks.get(screen.authoring.editingTaskIndex))) return true;
        if (screen.authoring.editingReward != null && screen.authoring.editingRewardIndex >= 0 && screen.authoring.editingRewardIndex < screen.authoring.rewards.size() &&
            !screen.authoring.editingReward.sameAs(screen.authoring.rewards.get(screen.authoring.editingRewardIndex))) return true;
        if (screen.authoring.editingNestedReward != null) {
            List<QuestAuthoringSession.RewardDraft> nested = nestedRewards(screen.authoring.editingReward);
            if (screen.authoring.editingNestedRewardIndex >= 0 && screen.authoring.editingNestedRewardIndex < nested.size() &&
                !screen.authoring.editingNestedReward.sameAs(nested.get(screen.authoring.editingNestedRewardIndex))) return true;
        }
        return false;
    }

    void requestDiscard(Runnable action) {
        if (screen.mutations.isPending()) return;
        if (screen.modalHost.requestDismissal(hasUnsavedDraft(), action)) screen.rebuildWidgets();
    }

    void requestModalDiscard(Runnable action) {
        if (screen.modalHost.requestDismissal(hasUnsavedModal(), action)) screen.rebuildWidgets();
    }

    void closeDraft() {
        screen.authoring.discard();
        screen.mutations.cancel();
    }

    void handleEditorResult(QuestNetwork.EditorResultPayload result) {
        QuestMutationCoordinator.Completion completion = screen.mutations.complete(result.requestId(), result.success(), result.message());
        if (completion == null) return;
        String operation = completion.pending().operation();
        screen.diagnostics = QuestDiagnostics.decode(result.diagnostics());
        screen.diagnosticsScroll = 0;
        screen.editorMessage = result.message();
        screen.editorMessageSuccess = result.success();
        if (result.success()) {
            if (screen.clipboardMutationPending) QuestScreenActions.clearClipboard();
            screen.clipboardMutationPending = false;
            screen.importController.clear();
            if ("reset_progress".equals(operation)) clearResetRewardSelections(completion.pending().request());
            if (List.of("create_quest", "update_quest", "delete_quest", "paste_quest", "import_quests").contains(operation)) {
                screen.authoring.discard();
                screen.mode.setEditorTool(EditorTool.SELECT);
            }
        }
        if (!result.success()) screen.clipboardMutationPending = false;
        if (!result.success() && "import_quests".equals(operation)) {
            screen.importController.applyServerDiagnostics(screen.diagnostics);
            screen.diagnostics = List.of();
            screen.modalHost.replace(QuestModalHost.Modal.FILE_IMPORT);
            screen.importScroll = 0;
        }
        if (!result.success() && "chapter_action".equals(operation)) {
            screen.modalHost.replace(QuestModalHost.Modal.CHAPTER_EDITOR);
        }
        if (!result.success() && "remove_quest_group".equals(operation)) screen.authoring.open = true;
        if (screen.modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) screen.imports.closeDiagnosticsModal();
        screen.rebuildWidgets();
    }

    /** Preserves the draft while detaching requests whose server outcome is unknown. */
    void handleConnectionLost() {
        QuestMutationCoordinator.Pending interrupted = screen.mutations.connectionLost();
        screen.pendingQuestFileRequestId = -1;
        screen.pendingQuestFileId = null;
        screen.clipboardMutationPending = false;
        if (interrupted == null) return;
        screen.editorMessage = "Connection lost while '" + interrupted.operation()
            + "' was pending. The server may have applied it; review the refreshed quests before retrying.";
        screen.editorMessageSuccess = false;
    }

    void clearResetRewardSelections(JsonObject request) {
        if (!request.has("scope") || !request.has("quest")) return;
        String scope = request.get("scope").getAsString();
        String questId = request.get("quest").getAsString();
        if ("reward".equals(scope) && request.has("entry")) {
            screen.rewardSelections.remove(questId + "|" + request.get("entry").getAsString());
        } else if ("quest".equals(scope)) {
            screen.rewardSelections.keySet().removeIf(key -> key.startsWith(questId + "|"));
        }
    }

    String descriptionDraftReference(DescriptionDocument.BlockKind kind, String id) {
        if (kind == DescriptionDocument.BlockKind.TASK) {
            return screen.authoring.tasks.stream().filter(task -> task.id.equals(id))
                .map(screen.authoringPanel.taskEditor::taskDisplayLabel).findFirst().orElse(id + " (missing)");
        }
        return screen.authoring.rewards.stream().filter(reward -> reward.id.equals(id))
            .map(screen.authoringPanel.rewardEditor::rewardDisplayLabel).findFirst().orElse(id + " (missing)");
    }

    QuestDraftValidation.RegistryLookup draftRegistryLookup() {
        return new QuestDraftValidation.RegistryLookup(
            QuestScreenEditor::clientContainsRegistryTarget,
            value -> registryContains(BuiltInRegistries.ITEM, value),
            value -> registryContains(BuiltInRegistries.ENTITY_TYPE, value)
        );
    }

    void nudgeCurrentDraftPosition(int deltaX, int deltaY) {
        screen.authoring.x += deltaX;
        screen.authoring.y += deltaY;
        screen.authoring.xText = Integer.toString(screen.authoring.x);
        screen.authoring.yText = Integer.toString(screen.authoring.y);
        screen.authoring.xInvalid = false;
        screen.authoring.yInvalid = false;
        updateDraftGroupPosition();
        screen.rebuildWidgets();
    }

    void sendEditorMutation(QuestMutation mutation) {
        QuestMutationCoordinator.Pending pending;
        try {
            pending = screen.mutations.begin(mutation);
        } catch (IllegalStateException exception) {
            screen.editorMessage = "Another editor operation is still pending.";
            screen.editorMessageSuccess = false;
            return;
        }
        screen.diagnostics = List.of();
        screen.modalHost.closeAll();
        ClientPacketDistributor.sendToServer(new QuestNetwork.EditorMutationPayload(pending.requestId(), mutation));
    }

    void onClose() {
        requestDiscard(() -> screen.closeScreen());
    }

    static boolean canEdit() {
        return Minecraft.getInstance().player != null &&
            Commands.LEVEL_GAMEMASTERS.check(Minecraft.getInstance().player.permissions());
    }

    static Component editorText(String translationKey) {
        return Component.translatable(translationKey);
    }

    static String editorString(String translationKey) {
        return editorText(translationKey).getString();
    }

    static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    static String jsonStringList(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonArray()) {
            return value.getAsJsonArray().asList().stream()
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .collect(java.util.stream.Collectors.joining(", "));
        }
        return value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    static void setOptionalString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value);
    }

    static Component cycleValueLabel(String key, String value) {
        return Component.translatable("gui.theseus.editor.value." + value.toLowerCase(java.util.Locale.ROOT));
    }

    static boolean registryContains(Registry<?> registry, String value) {
        try {
            return registry.containsKey(Identifier.parse(value));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean clientContainsRegistryTarget(RegistryValidation.Target target, String value) {
        if (value == null || value.isBlank()) return true;
        boolean tag = value.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        if (id == null) return false;
        try {
            return switch (target) {
                case ITEM -> tag ? registryTag(BuiltInRegistries.ITEM, Registries.ITEM, id) : BuiltInRegistries.ITEM.containsKey(id);
                case BLOCK -> tag ? registryTag(BuiltInRegistries.BLOCK, Registries.BLOCK, id) : BuiltInRegistries.BLOCK.containsKey(id);
                case ENTITY -> tag ? registryTag(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id) : BuiltInRegistries.ENTITY_TYPE.containsKey(id);
                case BIOME -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME), Registries.BIOME, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME).containsKey(id));
                case STRUCTURE -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE).containsKey(id));
                case DIMENSION -> Minecraft.getInstance().level == null
                    || Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.DIMENSION).containsKey(id)
                    || Minecraft.getInstance().getConnection().levels().stream().anyMatch(key -> key.identifier().equals(id));
                default -> true;
            };
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static <T> boolean registryTag(Registry<T> registry, net.minecraft.resources.ResourceKey<? extends Registry<T>> key, Identifier id) {
        return registry.getTagOrEmpty(net.minecraft.tags.TagKey.create(key, id)).iterator().hasNext();
    }
}
