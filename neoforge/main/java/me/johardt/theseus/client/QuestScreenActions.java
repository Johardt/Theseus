package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.QuestClientSnapshot.ClientQuest;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDraft;
import me.johardt.theseus.core.QuestMutation;
import me.johardt.theseus.core.QuestNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.fml.loading.FMLPaths;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;
import static me.johardt.theseus.client.QuestScreenEditor.*;

/** Handles quest selection, claiming, clipboard operations, and context-menu actions. */
final class QuestScreenActions {
    static QuestDraft clipboardDraft;
    static String clipboardSourceId;
    static boolean clipboardMove;

    private final QuestScreen screen;

    QuestScreenActions(QuestScreen screen) {
        this.screen = screen;
    }

    static void captureClipboard(ClientQuest quest, boolean move) {
        clipboardDraft = QuestDraft.fromClientSnapshot(quest.definition().id(), quest.raw());
        clipboardSourceId = quest.definition().id();
        clipboardMove = move;
    }

    static boolean hasClipboardContent() {
        return clipboardDraft != null;
    }

    static JsonObject clipboardTransferSnapshot() {
        return clipboardDraft == null ? null : clipboardDraft.transferSnapshot();
    }

    static void clearClipboard() {
        clipboardDraft = null;
        clipboardSourceId = null;
        clipboardMove = false;
    }

    TaskRef findSubmittable(
        Map<String, QuestDefinition.Task> tasks,
        Map<String, Integer> progress,
        String prefix
    ) {
        for (QuestDefinition.Task task : tasks.values()) {
            String path = prefix.isEmpty() ? task.id() : prefix + "/" + task.id();
            if (progress.getOrDefault(path, 0) >= task.target()) continue;
            if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
                TaskRef nested = findSubmittable(task.tasks(), progress, path);
                if (nested != null) return nested;
            } else if (isSubmittable(task)) {
                return new TaskRef(path, task);
            }
        }
        return null;
    }

    private static boolean isSubmittable(QuestDefinition.Task task) {
        if (task.kind() == QuestDefinition.TaskKind.CHECK) return true;
        if (task.kind() != QuestDefinition.TaskKind.ITEM && task.kind() != QuestDefinition.TaskKind.XP) return false;
        String key = task.kind() == QuestDefinition.TaskKind.XP ? "collectionType" : "collection";
        String collection = task.source().has(key)
            ? task.source().get(key).getAsString().toLowerCase(java.util.Locale.ROOT)
            : "automatic";
        return collection.endsWith("manual");
    }

    void submitTask(ClientQuest quest, TaskRef task) {
        if (quest == null || task == null) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload(
            "submit",
            quest.definition().id() + "|" + task.path()
        ));
    }

    void toggleMinimapDocking() {
        TheseusClientOptions.MinimapMode currentMode = TheseusClientOptions.defaultMinimapMode();
        QuestGraphLayout.CanvasBounds canvas = screen.layout.graphCanvasBounds();
        QuestMinimap.MapBounds dockedBounds = QuestMinimap.dockedPlacement(
            canvas,
            QuestMinimap.DEFAULT_WIDTH,
            QuestMinimap.DEFAULT_HEIGHT
        );
        if (dockedBounds != null) {
            double[] normalized = QuestMinimap.normalizedPosition(canvas, dockedBounds);
            TheseusClientOptions.setMinimapPosition(normalized[0], normalized[1]);
        }
        TheseusClientOptions.setDefaultMinimapMode(
            currentMode == TheseusClientOptions.MinimapMode.DOCKED
                ? TheseusClientOptions.MinimapMode.UNDOCKED
                : TheseusClientOptions.MinimapMode.DOCKED
        );
        screen.minimapPanel.clearTransientState();
        screen.rebuildWidgets();
    }

    ClientQuest questById(String id) {
        return screen.quests
            .stream()
            .filter(quest -> quest.definition().id().equals(id))
            .findFirst()
            .orElse(null);
    }

    List<ClientQuest> visibleQuests() {
        return screen.quests
            .stream()
            .filter(quest ->
                quest.definition().display().groups().containsKey(screen.group)
            )
            .filter(this::isVisible)
            .toList();
    }

    boolean isVisible(ClientQuest quest) {
        return switch (quest.definition().settings().hiddenUntil()) {
            case LOCKED -> true;
            case IN_PROGRESS -> quest.unlocked();
            case COMPLETED -> quest.complete();
            case DEPENDENCIES_VISIBLE -> quest.definition()
                .dependencies()
                .isEmpty() ||
                quest.unlocked() ||
                quest.definition()
                    .dependencies()
                    .stream()
                    .anyMatch(this::isComplete);
            case NEVER -> true;
        };
    }

    boolean isComplete(String id) {
        return screen.quests
            .stream()
            .filter(quest -> quest.definition().id().equals(id))
            .findFirst()
            .map(ClientQuest::complete)
            .orElse(false);
    }

    Set<String> groups() {
        return screen.snapshots.groups();
    }

    ClientQuest selected() {
        return screen.quests
            .stream()
            .filter(quest -> quest.definition().id().equals(screen.selectedQuestId))
            .findFirst()
            .orElse(null);
    }

    void selectChapterIndex(int index) {
        List<String> ordered = new ArrayList<>(groups());
        if (index < 0 || index >= ordered.size()) return;
        screen.chapterListFocused = true;
        screen.focusedChapterIndex = index;
        screen.chapterListState.ensureVisible(index);
        String candidate = ordered.get(index);
        if (candidate.equals(screen.group)) {
            screen.requestChapter(candidate);
            screen.rebuildWidgets();
            return;
        }
        screen.editor.requestDiscard(() -> {
            screen.group = candidate;
            screen.requestChapter(screen.group);
            screen.linkSourceId = null;
            screen.editor.closeDraft();
            screen.chapterListState.ensureVisible(index);
            screen.rebuildWidgets();
        });
    }

    void claimSelected() {
        ClientQuest selected = selected();
        if (selected == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("quest", selected.definition().id());
        JsonObject selections = new JsonObject();
        for (QuestDefinition.Reward reward : selected.definition()
            .rewards()
            .values()) {
            if (selected.claimedRewards().contains(reward.id())) continue;
            if (
                reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            ) continue;
            selections.add(
                reward.id(),
                GSON.toJsonTree(
                    screen.rewardSelections.getOrDefault(
                        selected.definition().id() + "|" + reward.id(),
                        Set.of()
                    )
                )
            );
        }
        payload.add("selections", selections);
        ClientPacketDistributor.sendToServer(
            new QuestNetwork.ActionPayload("claim", GSON.toJson(payload))
        );
    }

    boolean canClaimRewards(ClientQuest quest) {
        if (quest.definition().rewards().isEmpty()) return false;
        for (QuestDefinition.Reward reward : quest.definition()
            .rewards()
            .values()) {
            if (quest.claimedRewards().contains(reward.id())) continue;
            if (!isRewardTypeAvailable(reward)) return false;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                Set<String> selected = screen.rewardSelections.getOrDefault(
                    quest.definition().id() + "|" + reward.id(),
                    Set.of()
                );
                if (
                    selected.isEmpty() || selected.size() > reward.amount()
                ) return false;
                if (
                    selected
                        .stream()
                        .map(reward.rewards()::get)
                        .anyMatch(
                            choice ->
                                choice == null ||
                                !isRewardTypeAvailable(choice) ||
                                choice.kind() ==
                                    QuestDefinition.RewardKind.SELECTABLE
                        )
                ) return false;
            }
        }
        return true;
    }

    String claimBlockedReason(ClientQuest quest) {
        if (quest.definition().rewards().isEmpty()) return "This quest has no rewards";
        if (
            quest.definition()
                .rewards()
                .values()
                .stream()
                .anyMatch(reward -> !isRewardTypeAvailable(reward))
        ) {
            return "This quest contains a reward type that is not supported by this server";
        }
        return "Select the required quest reward before claiming";
    }

    boolean isRewardTypeAvailable(QuestDefinition.Reward reward) {
        if (reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
            && !screen.serverRewardTypes.contains(reward.type())) return false;
        return reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            || reward.rewards().values().stream().allMatch(this::isRewardTypeAvailable);
    }

    void copyQuestToClipboard(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        captureClipboard(quest, false);
        screen.editorMessage = "Copied quest '" + quest.definition().id() + "'.";
        screen.editorMessageSuccess = true;
        screen.rebuildWidgets();
    }

    void cutQuestToClipboard(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        captureClipboard(quest, true);
        screen.editorMessage = "Cut quest '" + quest.definition().id() + "' (paste to complete the move).";
        screen.editorMessageSuccess = true;
        screen.rebuildWidgets();
    }

    void copyQuestId(ClientQuest quest) {
        if (quest == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(quest.definition().id());
        screen.editorMessage = "Copied quest ID '" + quest.definition().id() + "'.";
        screen.editorMessageSuccess = true;
        screen.rebuildWidgets();
    }

    void openQuestDetails(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        screen.selectedQuestId = quest.definition().id();
        screen.detailsPanel.resetScroll();
        screen.authoring.open = false;
        screen.detailsOpen = true;
        screen.graphFocused = true;
        screen.rebuildWidgets();
    }

    void toggleQuestPinned(ClientQuest quest) {
        if (quest == null || !quest.unlocked()) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("pin", quest.definition().id()));
    }

    void openQuestEditorFromMenu(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        screen.editor.requestDiscard(() -> screen.editor.beginEditQuest(quest));
    }

    void snapQuestFromMenu(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        screen.editor.requestDiscard(() -> {
            screen.editor.beginEditQuest(quest);
            screen.authoringPanel.dockUi.snapCurrentDraftPosition();
        });
    }

    void deleteQuestFromMenu(ClientQuest quest) {
        if (quest == null || !screen.editor.ensureChapterDataLoaded()) return;
        screen.editor.requestDiscard(() -> {
            screen.editor.beginEditQuest(quest);
            screen.modalHost.open(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION);
            screen.rebuildWidgets();
        });
    }

    void resetQuestProgressFromMenu(ClientQuest quest) {
        if (quest == null || !canEdit()) return;
        requestProgressReset(new QuestModalHost.ProgressResetTarget(
            "quest",
            quest.definition().id(),
            quest.definition().title(),
            "",
            quest.definition().title()
        ));
    }

    boolean canOpenQuestFile(ClientQuest quest) {
        return canEdit()
            && screen.mode.isAuthoring()
            && quest != null
            && Minecraft.getInstance().getSingleplayerServer() != null;
    }

    void requestQuestFileOpen(ClientQuest quest) {
        if (!canOpenQuestFile(quest) || screen.pendingQuestFileRequestId >= 0) return;
        screen.pendingQuestFileRequestId = ++screen.nextQuestFileRequestId;
        screen.pendingQuestFileId = quest.definition().id();
        screen.editorMessage = "Requesting quest file…";
        screen.editorMessageSuccess = false;
        ClientPacketDistributor.sendToServer(new QuestNetwork.OpenQuestFilePayload(
            screen.pendingQuestFileRequestId,
            screen.pendingQuestFileId
        ));
        screen.rebuildWidgets();
    }

    void handleOpenQuestFileResult(QuestNetwork.OpenQuestFileResultPayload result) {
        if (result == null || result.requestId() != screen.pendingQuestFileRequestId) return;
        String requestedQuestId = screen.pendingQuestFileId;
        screen.pendingQuestFileRequestId = -1;
        screen.pendingQuestFileId = null;
        ClientQuest quest = questById(requestedQuestId);
        if (quest == null || !requestedQuestId.equals(selected() == null ? null : selected().definition().id())) {
            return;
        }
        if (!result.success()) {
            screen.editorMessage = result.message();
            screen.editorMessageSuccess = false;
            screen.rebuildWidgets();
            return;
        }
        LocalQuestFileOpener.Result opened = screen.questFileOpener.open(
            FMLPaths.CONFIGDIR.get().resolve(Theseus.MOD_ID).resolve("quests"),
            result.relativePath()
        );
        screen.editorMessage = opened.success()
            ? "Opened quest file '" + result.relativePath() + "'."
            : opened.message();
        screen.editorMessageSuccess = opened.success();
        screen.rebuildWidgets();
    }

    void copyProgressEntry(String kind, String entry) {
        Minecraft.getInstance().keyboardHandler.setClipboard(entry);
        screen.editorMessage = "Copied " + kind + " '" + entry + "'.";
        screen.editorMessageSuccess = true;
        screen.rebuildWidgets();
    }

    QuestDetailsPanel.Model detailPanelModel() {
        ClientQuest quest = selected();
        QuestDetailsPanel.QuestData selectedView = quest == null ? null : new QuestDetailsPanel.QuestData(
            quest.definition(),
            quest.progress(),
            quest.unlocked(),
            quest.complete(),
            quest.claimed(),
            quest.claimedRewards()
        );
        QuestSurfaceLayout.LockExplanation lockExplanation = null;
        if (quest != null) {
            Map<String, QuestSurfaceLayout.LockState> states = new HashMap<>();
            for (ClientQuest candidate : screen.quests) states.put(candidate.definition().id(),
                new QuestSurfaceLayout.LockState(
                    candidate.definition().title(),
                    candidate.complete(),
                    candidate.definition().display().groups().keySet()
                ));
            lockExplanation = QuestSurfaceLayout.explainLock(quest.definition(), states, screen.group);
        }
        return new QuestDetailsPanel.Model(
            selectedView,
            lockExplanation,
            screen.detailTab,
            screen.rewardSelections,
            screen.serverRewardTypes
        );
    }

    boolean openProgressCardContextMenu(int mouseX, int mouseY) {
        if (!canEdit() || !screen.detailsOpen) return false;
        ClientQuest quest = selected();
        QuestDetailsPanel.ProgressCardTarget target = screen.detailsPanel.progressCardAt(mouseX, mouseY, screen.detailTab);
        if (quest == null || target == null) return false;
        boolean task = target.kind().equals("task");
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            editorString(task ? "gui.theseus.editor.copy_task_path" : "gui.theseus.editor.copy_reward_id"),
            "",
            true,
            false,
            () -> copyProgressEntry(task ? "task path" : "reward ID", target.id())
        ));
        entries.add(QuestContextMenu.Entry.separator());
        entries.add(QuestContextMenu.Entry.item(
            editorString(task ? "gui.theseus.editor.reset_task_progress" : "gui.theseus.editor.reset_reward_progress"),
            "",
            true,
            true,
            () -> requestProgressReset(new QuestModalHost.ProgressResetTarget(
                task ? "task" : "reward",
                quest.definition().id(),
                quest.definition().title(),
                target.id(),
                target.displayLabel()
            ))
        ));
        showContextMenu(mouseX, mouseY, entries);
        return true;
    }

    void requestProgressReset(QuestModalHost.ProgressResetTarget target) {
        if (!canEdit() || target == null || screen.mutations.isPending()) return;
        screen.progressResetTarget = target;
        screen.modalHost.open(QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION);
        screen.rebuildWidgets();
    }

    void openQuestContextMenu(ClientQuest quest, int mouseX, int mouseY) {
        screen.graphFocused = true;
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        if (!screen.mode.isAuthoring()) {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.open_details"), "Enter", true, false, () -> openQuestDetails(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.copy_quest_id"), "", true, false, () -> copyQuestId(quest)));
            entries.add(QuestContextMenu.Entry.item(
                editorString(quest != null && quest.pinned()
                    ? "gui.theseus.editor.unpin_quest"
                    : "gui.theseus.editor.pin_quest"),
                "",
                quest != null && quest.unlocked(),
                false,
                () -> toggleQuestPinned(quest)
            ));
        } else {
            if (canOpenQuestFile(quest)) entries.add(QuestContextMenu.Entry.item(
                editorString("gui.theseus.editor.open_quest_file"),
                "",
                true,
                false,
                () -> requestQuestFileOpen(quest)
            ));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.edit_quest"), "Enter", true, false, () -> openQuestEditorFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.copy_quest_id"), "", true, false, () -> copyQuestId(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.reset_quest_progress"), "", true, true, () -> resetQuestProgressFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.copy_quest"), "Ctrl+C", true, false, () -> copyQuestToClipboard(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.cut_quest"), "Ctrl+X", true, false, () -> cutQuestToClipboard(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.snap_selected_quest"), "", true, false, () -> snapQuestFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.delete_quest"), "", true, true, () -> deleteQuestFromMenu(quest)));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    void openEmptyGraphContextMenu(double worldX, double worldY, int mouseX, int mouseY) {
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        if (screen.mode.isAuthoring()) {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.add_quest_here"), "", true, false, () ->
                screen.editor.requestDiscard(() -> screen.editor.beginCreateQuest(worldX, worldY))
            ));
            if (hasClipboardContent()) entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.paste_here"), "Ctrl+V", true, false, () -> {
                if (clipboardMove) sendClipboardPaste(false, null, worldX, worldY);
                else openPasteIdPrompt(worldX, worldY);
            }));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.fit_to_content"), "Home", true, false, screen.layout::fitGraphToContent));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.select_tool"), "S", true, false, () -> setEditorTool(EditorTool.SELECT)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.hand_tool"), "H", true, false, () -> setEditorTool(EditorTool.HAND)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.add_tool"), "A", true, false, () -> setEditorTool(EditorTool.ADD)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.link_tool"), "L", true, false, () -> setEditorTool(EditorTool.LINK)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(
                editorString(TheseusClientOptions.showGrid()
                    ? "gui.theseus.editor.hide_graph_grid"
                    : "gui.theseus.editor.show_graph_grid"),
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setShowGrid(!TheseusClientOptions.showGrid());
                    screen.rebuildWidgets();
                }
            ));
            entries.add(QuestContextMenu.Entry.item(
                editorString(TheseusClientOptions.snapToGrid()
                    ? "gui.theseus.editor.disable_snap_to_grid"
                    : "gui.theseus.editor.enable_snap_to_grid"),
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setSnapToGrid(!TheseusClientOptions.snapToGrid());
                    screen.rebuildWidgets();
                }
            ));
        } else {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.fit_to_content"), "Home", true, false, screen.layout::fitGraphToContent));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    void openDisplayMenu(int mouseX, int mouseY) {
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            Component.translatable("screen.theseus.display_menu.move_tracker").getString(),
            "",
            true,
            false,
            () -> Minecraft.getInstance().setScreen(new TrackerPlacementScreen(screen))
        ));
        if (canEdit()) entries.add(QuestContextMenu.Entry.item(
            Component.translatable("screen.theseus.display_menu.tutorial").getString(),
            "",
            true,
            false,
            this::openTutorial
        ));
        if (screen.authoring.open) {
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(
                editorString("gui.theseus.editor.import_quests"),
                "",
                true,
                false,
                screen.imports::openNativeFilePicker
            ));
            entries.add(QuestContextMenu.Entry.item(
                editorString("gui.theseus.editor.fit_graph_to_content"),
                "Home",
                true,
                false,
                screen.layout::fitGraphToContent
            ));
            entries.add(QuestContextMenu.Entry.item(
                editorString(TheseusClientOptions.showGrid()
                    ? "gui.theseus.editor.hide_graph_grid"
                    : "gui.theseus.editor.show_graph_grid"),
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setShowGrid(!TheseusClientOptions.showGrid());
                    screen.rebuildWidgets();
                }
            ));
            entries.add(QuestContextMenu.Entry.item(
                editorString(TheseusClientOptions.snapToGrid()
                    ? "gui.theseus.editor.disable_snap_to_grid"
                    : "gui.theseus.editor.enable_snap_to_grid"),
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setSnapToGrid(!TheseusClientOptions.snapToGrid());
                    screen.rebuildWidgets();
                }
            ));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    void openTutorial() {
        if (!canEdit()) return;
        TheseusClientOptions.setTutorialSeen(true);
        Minecraft.getInstance().setScreen(new QuestTutorialScreen(screen));
    }

    void setEditorTool(EditorTool tool) {
        screen.editor.requestDiscard(() -> {
            screen.mode = screen.authoring;
            screen.mode.setEditorTool(tool);
            screen.editor.closeDraft();
            screen.linkSourceId = null;
            screen.panning = false;
            screen.rebuildWidgets();
        });
    }

    void showContextMenu(int mouseX, int mouseY, List<QuestContextMenu.Entry> entries) {
        screen.contextMenu = new QuestContextMenu(
            mouseX,
            mouseY,
            screen.guiWidth(),
            screen.guiHeight(),
            entries,
            () -> {
                screen.contextMenu = null;
                screen.graphFocused = true;
                screen.setScreenFocused(null);
            }
        );
    }

    void sendClipboardPaste(boolean chapterOnly, String requestedId) {
        sendClipboardPaste(chapterOnly, requestedId, null, null);
    }

    static JsonObject buildClipboardPasteRequest(
        String sourceId,
        String chapter,
        boolean chapterOnly,
        boolean move,
        String requestedId,
        Set<String> existingQuestIds,
        boolean sourceAvailable,
        JsonObject quest,
        int sourceX,
        int sourceY,
        Double worldX,
        Double worldY
    ) {
        if (!chapterOnly && !sourceAvailable) return null;

        String id = move ? sourceId : requestedId;
        if (!chapterOnly) {
            boolean sameSourceMove = move && sourceId != null && sourceId.equals(id);
            if (
                id == null ||
                !id.matches("[a-z0-9_.-]+") ||
                (existingQuestIds.contains(id) && !sameSourceMove)
            ) return null;
        }

        JsonObject request = new JsonObject();
        request.addProperty("source_id", sourceId);
        request.addProperty("chapter", chapter);
        request.addProperty("chapter_only", chapterOnly);
        if (!chapterOnly) {
            request.addProperty("id", id);
            request.add("quest", quest);
            request.addProperty("move", move);
        }
        if (!chapterOnly || sourceAvailable) {
            long x = sourceX;
            long y = sourceY;
            if (worldX != null && worldY != null) {
                x = Math.round(worldX);
                y = Math.round(worldY);
            }
            request.addProperty("x", x);
            request.addProperty("y", y);
        }
        return request;
    }

    void sendClipboardPaste(
        boolean chapterOnly,
        String requestedId,
        Double worldX,
        Double worldY
    ) {
        String sourceId = clipboardSourceId;
        ClientQuest source = screen.quests.stream().filter(quest -> quest.definition().id().equals(sourceId)).findFirst().orElse(null);
        QuestDefinition.GroupDisplay position = source == null
            ? new QuestDefinition.GroupDisplay(0, 0)
            : source.definition().position(screen.group);
        JsonObject request = buildClipboardPasteRequest(
            sourceId,
            screen.group,
            chapterOnly,
            clipboardMove,
            requestedId,
            screen.quests.stream()
                .map(quest -> quest.definition().id())
                .collect(java.util.stream.Collectors.toSet()),
            source != null,
            !chapterOnly && source != null ? clipboardTransferSnapshot() : null,
            position.x(),
            position.y(),
            worldX,
            worldY
        );
        if (request == null) {
            screen.editorMessage = source == null && !chapterOnly
                ? "The copied quest is no longer available."
                : "Choose a new, unused lowercase quest ID.";
            screen.editorMessageSuccess = false;
            return;
        }
        screen.clipboardMutationPending = true;
        screen.editor.sendEditorMutation(new QuestMutation.PasteQuest(request));
    }

    void openPasteIdPrompt() {
        screen.pendingPastePosition = false;
        screen.pendingPasteWorldX = 0;
        screen.pendingPasteWorldY = 0;
        screen.pasteIdField = null;
        screen.modalHost.open(QuestModalHost.Modal.PASTE_ID_PROMPT);
        screen.rebuildWidgets();
    }

    void openPasteIdPrompt(double worldX, double worldY) {
        screen.pendingPastePosition = true;
        screen.pendingPasteWorldX = worldX;
        screen.pendingPasteWorldY = worldY;
        screen.pasteIdField = null;
        screen.modalHost.open(QuestModalHost.Modal.PASTE_ID_PROMPT);
        screen.rebuildWidgets();
    }

    void confirmPasteIdPrompt() {
        if (screen.pasteIdField == null) return;
        String id = screen.pasteIdField.getValue().trim();
        screen.pasteIdField = null;
        if (screen.modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT)) screen.modalHost.close();
        if (screen.pendingPastePosition) {
            sendClipboardPaste(false, id, screen.pendingPasteWorldX, screen.pendingPasteWorldY);
        } else {
            sendClipboardPaste(false, id);
        }
        screen.pendingPastePosition = false;
        screen.rebuildWidgets();
    }

    record TaskRef(String path, QuestDefinition.Task task) {}
}
