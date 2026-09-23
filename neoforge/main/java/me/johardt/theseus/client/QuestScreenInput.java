package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestIconDefinition;
import me.johardt.theseus.core.QuestMutation;
import me.johardt.theseus.client.description.QuestDescriptionRenderer;
import me.johardt.theseus.client.description.MarkdownEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Routes keyboard, pointer, and scroll input to the active screen interaction. */
final class QuestScreenInput {
    private final QuestScreen screen;

    QuestScreenInput(QuestScreen screen) {
        this.screen = screen;
    }

    boolean keyPressed(KeyEvent event) {
        boolean modalOpen = screen.modalHost.blocksInput();
        if (modalOpen && screen.authoringPanel.draftUi.handleChooserKey(event)) return true;
        if (!modalOpen && screen.contextMenu != null && screen.contextMenu.isOpen()) {
            screen.contextMenu.keyPressed(event.key(), event.hasShiftDown());
            return true;
        }
        if (!modalOpen && screen.chapterListFocused && !isTextEditing()) {
            int direction = switch (event.key()) {
                case InputConstants.KEY_UP -> -1;
                case InputConstants.KEY_DOWN -> 1;
                case InputConstants.KEY_PAGEUP -> -Math.max(1, screen.chapterListState.visibleCapacity());
                case InputConstants.KEY_PAGEDOWN -> Math.max(1, screen.chapterListState.visibleCapacity());
                default -> 0;
            };
            if (direction != 0) {
                int current = screen.focusedChapterIndex >= 0
                    ? screen.focusedChapterIndex
                    : new ArrayList<>(screen.actions.groups()).indexOf(screen.group);
                screen.actions.selectChapterIndex(Math.clamp(current + direction, 0, Math.max(0, screen.chapterListState.chapterCount() - 1)));
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN) {
                screen.actions.selectChapterIndex(screen.focusedChapterIndex);
                return true;
            }
        }

        switch (screen.modalHost.handles(event.key())) {
            case CONSUMED -> {
                return true;
            }
            case CANCEL_DISMISSAL -> {
                screen.modalHost.cancelDismissal();
                screen.rebuildWidgets();
                return true;
            }
            case CLOSE -> {
                closeModalOnEscape();
                return true;
            }
            case REQUEST_DISMISSAL -> {
                requestModalEscapeDismissal();
                return true;
            }
            case PASS -> { }
        }

        if (screen.modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
        if (screen.modalHost.is(QuestModalHost.Modal.DESCRIPTION_EDITOR)) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
                screen.widgets.applyDescriptionEditor();
                if (screen.editor.validCreateQuestDraft()) screen.editor.confirmCreateQuest();
                return true;
            }
            return screen.parentKeyPressed(event);
        }
        if (screen.modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            if (event.key() == InputConstants.KEY_RETURN) {
                screen.imports.closeDiagnosticsModal();
                screen.rebuildWidgets();
            }
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && screen.importController.canSubmit()) {
                screen.imports.sendImport();
                return true;
            }
            return screen.parentKeyPressed(event);
        }
        if (screen.modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT) && event.key() == InputConstants.KEY_RETURN) {
            screen.actions.confirmPasteIdPrompt();
            return true;
        }
        if (!modalOpen && !isTextEditing() && event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && screen.importController.canSubmit()) {
            screen.imports.sendImport();
            return true;
        }
        if (!modalOpen && !isTextEditing() && event.hasControlDown() && !event.hasAltDown()) {
            if (event.key() == InputConstants.KEY_C && screen.mode.isAuthoring() && screen.actions.selected() != null) {
                screen.actions.copyQuestToClipboard(screen.actions.selected());
                return true;
            }
            if (event.key() == InputConstants.KEY_X && screen.mode.isAuthoring() && screen.actions.selected() != null) {
                screen.actions.cutQuestToClipboard(screen.actions.selected());
                return true;
            }
            if (event.key() == InputConstants.KEY_V && screen.mode.isAuthoring() && QuestScreenActions.hasClipboardContent()) {
                if (event.hasShiftDown() || QuestScreenActions.clipboardMove) screen.actions.sendClipboardPaste(event.hasShiftDown(), null);
                else screen.actions.openPasteIdPrompt();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_RETURN && !isTextEditing()) {
            if (modalOpen) {
                if (screen.modalHost.is(QuestModalHost.Modal.DISCARD_CONFIRMATION)) {
                    screen.modalHost.confirmDismissal();
                    return true;
                }
                if (screen.modalHost.is(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION)) {
                    screen.editor.confirmDeleteQuest();
                    return true;
                }
                if (screen.modalHost.is(QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION)) {
                    screen.editor.confirmProgressReset();
                    return true;
                }
                if (screen.modalHost.is(QuestModalHost.Modal.TASK_DELETE_CONFIRMATION)) {
                    screen.editor.confirmDeleteTask();
                    return true;
                }
                if (screen.modalHost.is(QuestModalHost.Modal.CHAPTER_EDITOR)) {
                    screen.widgets.saveChapter();
                    return true;
                }
                if (screen.modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT)) {
                    screen.actions.confirmPasteIdPrompt();
                    return true;
                }
            }
            if (!modalOpen && screen.authoring.open && screen.editor.validCreateQuestDraft()) {
                screen.editor.confirmCreateQuest();
                return true;
            }
        }
        if (!modalOpen && screen.mode.isAuthoring()
            && !isTextEditing() && !event.hasControlDown() && !event.hasAltDown()) {
            EditorTool shortcut = switch (event.key()) {
                case InputConstants.KEY_S -> EditorTool.SELECT;
                case InputConstants.KEY_H -> EditorTool.HAND;
                case InputConstants.KEY_A -> EditorTool.ADD;
                case InputConstants.KEY_L -> EditorTool.LINK;
                default -> null;
            };
            if (shortcut != null) {
                screen.mode.setEditorTool(shortcut);
                screen.rebuildWidgets();
                return true;
            }
        }
        if (!modalOpen && !isTextEditing()
            && !event.hasControlDown()
            && !event.hasAltDown()
            && event.key() == InputConstants.KEY_HOME) {
            screen.graphFocused = true;
            screen.setScreenFocused(null);
            screen.layout.fitGraphToContent();
            return true;
        }
        if (!modalOpen && event.hasControlDown() && event.key() == InputConstants.KEY_S && screen.authoring.open) {
            screen.editor.confirmCreateQuest();
            return true;
        }
        if (!modalOpen && screen.mode.isAuthoring()
            && screen.graphFocused
            && screen.authoring.editingExisting
            && screen.authoring.open
            && !isTextEditing()
            && !event.hasAltDown()) {
            int amount = event.hasShiftDown()
                ? QuestGraphLayout.GRID_CELL_SIZE
                : event.hasControlDown() ? 5 : 1;
            int deltaX = 0;
            int deltaY = 0;
            switch (event.key()) {
                case InputConstants.KEY_LEFT -> deltaX = -amount;
                case InputConstants.KEY_RIGHT -> deltaX = amount;
                case InputConstants.KEY_UP -> deltaY = -amount;
                case InputConstants.KEY_DOWN -> deltaY = amount;
                default -> { }
            }
            if (deltaX != 0 || deltaY != 0) {
                screen.editor.nudgeCurrentDraftPosition(deltaX, deltaY);
                return true;
            }
        }
        if (!modalOpen && screen.graphFocused && !screen.authoring.open && !isTextEditing()
            && !event.hasControlDown() && !event.hasAltDown()) {
            if (moveGraphSelection(event.key())) return true;
            if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                && screen.actions.selected() != null) {
                if (screen.mode.isAuthoring()) screen.actions.openQuestEditorFromMenu(screen.actions.selected());
                else screen.actions.openQuestDetails(screen.actions.selected());
                return true;
            }
        }
        if (!event.isEscape()) return screen.parentKeyPressed(event);
        if (screen.authoring.open) {
            screen.editor.requestDiscard(() -> {
                screen.editor.closeDraft();
                screen.rebuildWidgets();
            });
            return true;
        }
        screen.editor.onClose();
        return true;
    }

    boolean moveGraphSelection(int keyCode) {
        int directionX = switch (keyCode) {
            case InputConstants.KEY_LEFT -> -1;
            case InputConstants.KEY_RIGHT -> 1;
            default -> 0;
        };
        int directionY = switch (keyCode) {
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_DOWN -> 1;
            default -> 0;
        };
        if (directionX == 0 && directionY == 0) return false;
        List<ClientQuest> visible = screen.actions.visibleQuests();
        if (visible.isEmpty()) return true;
        ClientQuest current = screen.actions.selected();
        if (current == null || !visible.contains(current)) {
            screen.selectedQuestId = visible.getFirst().definition().id();
            screen.rebuildWidgets();
            return true;
        }
        QuestDefinition.GroupDisplay currentPosition = current.definition().position(screen.group);
        ClientQuest best = null;
        double bestScore = Double.MAX_VALUE;
        for (ClientQuest candidate : visible) {
            if (candidate == current) continue;
            QuestDefinition.GroupDisplay position = candidate.definition().position(screen.group);
            double deltaX = position.x() - currentPosition.x();
            double deltaY = position.y() - currentPosition.y();
            double forward = deltaX * directionX + deltaY * directionY;
            if (forward <= 0) continue;
            double cross = Math.abs(deltaX * directionY - deltaY * directionX);
            double score = forward + cross * 2.0;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            screen.selectedQuestId = best.definition().id();
            screen.rebuildWidgets();
        }
        return true;
    }

    void closeModalOnEscape() {
        switch (screen.modalHost.active()) {
            case FILE_IMPORT -> screen.imports.cancelImport();
            case PICKER -> {
                screen.editor.closePicker();
                screen.rebuildWidgets();
            }
            case DESCRIPTION_EDITOR -> screen.widgets.closeDescriptionEditor();
            case PROGRESS_RESET_CONFIRMATION -> {
                screen.progressResetTarget = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            }
            case TASK_DELETE_CONFIRMATION -> {
                screen.authoring.taskDeleteConfirmation = -1;
                screen.modalHost.close();
                screen.rebuildWidgets();
            }
            case PASTE_ID_PROMPT -> {
                screen.pasteIdField = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            }
            default -> {
                screen.modalHost.close();
                screen.rebuildWidgets();
            }
        }
    }

    void requestModalEscapeDismissal() {
        switch (screen.modalHost.active()) {
            case TASK_EDITOR, NESTED_TASKS -> screen.editor.requestModalDiscard(screen.authoringPanel.taskEditor::closeTaskEditor);
            case NESTED_REWARD_EDITOR -> screen.editor.requestModalDiscard(() -> screen.authoringPanel.rewardEditor.closeRewardEditor(true));
            case REWARD_EDITOR, NESTED_REWARDS -> screen.editor.requestModalDiscard(() -> screen.authoringPanel.rewardEditor.closeRewardEditor(false));
            case CHAPTER_EDITOR -> screen.editor.requestModalDiscard(() -> {
                screen.chapterEditorBaseline = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
            default -> { }
        }
    }

    boolean charTyped(CharacterEvent event) {
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
        return screen.parentCharTyped(event);
    }

    void focusPickerSearch() {
        // The picker can rebuild the widget tree inside its opener's click callback.
        if (screen.pickerSearch != null && screen.screenFocused() != screen.pickerSearch) {
            screen.setScreenFocused(screen.pickerSearch);
        }
    }

    boolean isTextEditing() {
        return screen.screenFocused() instanceof EditBox
            || screen.screenFocused() instanceof MultiLineEditBox
            || screen.screenFocused() instanceof MarkdownEditBox;
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        switch (screen.modalHost.active()) {
            case DIAGNOSTICS, FILE_IMPORT -> {
                screen.parentMouseClicked(event, doubleClick);
                return true;
            }
            case PICKER -> {
                if (screen.parentMouseClicked(event, doubleClick)) return true;
                return pickerClicked(event);
            }
            case NESTED_REWARD_CHOOSER -> {
                return screen.authoringPanel.draftUi.rewardChooserClicked(event, true);
            }
            case REWARD_CHOOSER -> {
                return screen.authoringPanel.draftUi.rewardChooserClicked(event, false);
            }
            case NESTED_TASK_CHOOSER -> {
                return screen.authoringPanel.draftUi.taskChooserClicked(event, true);
            }
            case TASK_CHOOSER -> {
                return screen.authoringPanel.draftUi.taskChooserClicked(event);
            }
            default -> { }
        }
        if (screen.contextMenu != null && screen.contextMenu.isOpen()) {
            screen.contextMenu.mouseClicked(event.x(), event.y(), event.input());
            return true;
        }
        if (!screen.modalHost.blocksInput() && screen.detailsOpen && recipeViewerClicked(event)) {
            return true;
        }
        if (!screen.modalHost.blocksInput() && event.input() == 1
            && screen.actions.openProgressCardContextMenu((int) Math.round(event.x()), (int) Math.round(event.y()))) {
            return true;
        }
        if (!screen.modalHost.blocksInput() && minimapClicked(event)) return true;
        if (!screen.modalHost.blocksInput()
            && event.input() == 1
            && !screen.layout.detailsDockContains(event.x(), event.y())
            && screen.layout.graphCanvasBounds().contains(event.x(), event.y())) {
            screen.graphFocused = true;
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                screen.layout.graphCanvasBounds(),
                screen.graphViewport.state(),
                event.x(),
                event.y()
            );
            int mouseX = (int) Math.round(event.x());
            int mouseY = (int) Math.round(event.y());
            ClientQuest quest = screen.layout.surfaceLayout().pick(event.x(), event.y())
                .map(hit -> screen.actions.questById(hit.questId()))
                .orElse(null);
            if (quest == null) screen.actions.openEmptyGraphContextMenu(world.x(), world.y(), mouseX, mouseY);
            else {
                screen.selectedQuestId = quest.definition().id();
                screen.actions.openQuestContextMenu(quest, mouseX, mouseY);
            }
            return true;
        }
        if (!screen.modalHost.blocksInput()
            && event.input() == 0
            && screen.sidebarOpen
            && event.x() < screen.layout.sidebarWidth()) {
            int row = screen.chapterListState.rowAt(event.y());
            int index = screen.chapterListState.indexAtRow(row);
            if (index >= 0) {
                screen.chapterListFocused = true;
                screen.focusedChapterIndex = index;
            }
        }
        if (screen.parentMouseClicked(event, doubleClick)) {
            if (screen.modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
            return true;
        }
        if (screen.modalHost.blocksInput()) return true;
        QuestSurfaceLayout.Layout surface = screen.layout.surfaceLayout();
        if (event.input() == 0 && screen.detailsOpen) {
            String questId = screen.detailsPanel.lockQuestAt(event.x(), event.y());
            if (questId != null) {
                screen.selectedQuestId = questId;
                screen.detailsPanel.resetScroll();
                screen.rebuildWidgets();
                return true;
            }
        }
        if (event.input() == 0 && screen.detailsOpen && screen.detailTab == DetailTab.OVERVIEW) {
            QuestDescriptionRenderer.Interaction interaction =
                screen.detailsPanel.descriptionInteractionAt(event.x(), event.y());
            if (interaction != null) {
                if (interaction.clickStyle() != null && interaction.clickStyle().getClickEvent() != null) {
                    screen.performDefaultClickEvent(interaction.clickStyle().getClickEvent(), screen.guiMinecraft(), screen);
                }
                return true;
            }
        }
        if (event.input() == 0 && screen.detailsOpen && screen.detailTab == DetailTab.REWARDS
            && event.x() >= screen.guiWidth() - screen.layout.detailsWidth()) {
            QuestDetailsPanel.RewardChoiceTarget choice = screen.detailsPanel.rewardChoiceAt(event.x(), event.y());
            if (choice != null) {
                Set<String> selectedChoices = screen.rewardSelections.computeIfAbsent(
                    choice.selectionKey(), ignored -> new LinkedHashSet<>()
                );
                if (selectedChoices.remove(choice.choiceId())) {
                    screen.rebuildWidgets();
                    return true;
                }
                if (choice.maximumSelections() == 1) {
                    selectedChoices.clear();
                    selectedChoices.add(choice.choiceId());
                    screen.rebuildWidgets();
                } else if (selectedChoices.size() < choice.maximumSelections()) {
                    selectedChoices.add(choice.choiceId());
                    screen.rebuildWidgets();
                }
                return true;
            }
        }
        if (
            event.input() == 0 &&
            event.x() > screen.layout.sidebarWidth() &&
            event.x() < screen.layout.canvasRight() &&
            event.y() >= screen.layout.graphCanvasTop()
        ) {
            screen.graphFocused = true;
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                screen.layout.graphCanvasBounds(),
                screen.graphViewport.state(),
                event.x(),
                event.y()
            );
            double treeX = world.x();
            double treeY = world.y();
            if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.HAND) {
                screen.panning = true;
                return true;
            }
            if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.ADD) {
                boolean occupied = surface.pick(event.x(), event.y()).isPresent();
                if (!occupied) {
                    screen.editor.requestDiscard(() -> screen.editor.beginCreateQuest(treeX, treeY));
                    return true;
                }
                return true;
            }
            if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.SELECT && screen.authoring.editingExisting && screen.authoring.open) {
                QuestGraphLayout.NodeBounds draftBounds = screen.editor.authoringNodeLayout().bounds();
                if (draftBounds.contains(treeX, treeY)) {
                    screen.draggingQuestId = screen.authoring.originalId;
                    screen.questMoved = false;
                    return true;
                }
            }
            ClientQuest quest = surface.pick(event.x(), event.y())
                .map(hit -> screen.actions.questById(hit.questId()))
                .orElse(null);
            if (quest != null) {
                if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.LINK) {
                    linkQuest(quest.definition().id(), event.hasShiftDown());
                    return true;
                }
                if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.SELECT) {
                    screen.editor.requestDiscard(() -> {
                        screen.editor.beginEditQuest(quest);
                        screen.draggingQuestId = quest.definition().id();
                        screen.questMoved = false;
                    });
                    return true;
                }
                screen.selectedQuestId = quest.definition().id();
                screen.detailsPanel.resetScroll();
                screen.authoring.open = false;
                screen.detailsOpen = true;
                screen.rebuildWidgets();
                return true;
            }
            if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.SELECT) {
                return true;
            }
            if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.LINK) {
                screen.linkSourceId = null;
                return true;
            }
            if (!screen.mode.isAuthoring() && screen.detailsOpen) {
                screen.detailsOpen = false;
                screen.selectedQuestId = null;
                screen.rebuildWidgets();
            }
            screen.panning = !screen.mode.isAuthoring() || screen.mode.editorTool() == EditorTool.HAND;
            return true;
        }
        return false;
    }

    boolean recipeViewerClicked(MouseButtonEvent event) {
        if (event.input() != 0 && event.input() != 1) return false;
        ItemStack stack = screen.detailsPanel.recipeViewerItemAt(event.x(), event.y()).orElse(null);
        if (stack == null) return false;
        return event.input() == 0
            ? RecipeViewer.showRecipes(stack)
            : RecipeViewer.showUses(stack);
    }

    void linkQuest(String questId, boolean remove) {
        if (screen.linkSourceId == null) {
            screen.linkSourceId = questId;
            return;
        }
        if (screen.linkSourceId.equals(questId)) {
            screen.linkSourceId = null;
            return;
        }
        JsonObject change = new JsonObject();
        change.addProperty("prerequisite", screen.linkSourceId);
        change.addProperty("dependent", questId);
        change.addProperty("remove", remove);
        screen.editor.sendEditorMutation(new QuestMutation.SetDependency(change));
    }

    boolean pickerClicked(MouseButtonEvent event) {
        if (event.input() != 0) return true;
        int left = screen.renderer.pickerLeft();
        int top = screen.renderer.pickerTop();
        if (event.x() < left || event.x() >= left + 200 || event.y() < top || event.y() >= top + 176) {
            screen.editor.closePicker();
            screen.rebuildWidgets();
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER) && screen.picker == Picker.ICON) {
            List<Item> items = screen.renderer.filteredPickerItems();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) {
                return true;
            }
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            if (column >= 0 && column < 8 && row >= 0 && row < 5) {
                int index = screen.pickerScroll + row * 8 + column;
                if (index < items.size()) {
                    String id = BuiltInRegistries.ITEM.getKey(items.get(index)).toString();
                    switch (screen.pickerTarget) {
                        case QUEST_ICON -> {
                            screen.authoring.icon = id;
                            screen.authoring.iconTouched = true;
                        }
                        case TASK_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("type", QuestIconDefinition.ITEM_TYPE);
                            icon.addProperty("item", id);
                            screen.authoring.editingTask.source.add("icon", icon);
                        }
                        case TASK_ITEM -> screen.authoring.editingTask.source.addProperty("item", id);
                        case TASK_BLOCK -> screen.authoring.editingTask.source.addProperty("block", id);
                        case TASK_ENTITY -> { }
                        case REWARD_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("type", QuestIconDefinition.ITEM_TYPE);
                            icon.addProperty("item", id);
                            screen.editor.activeRewardDraft().source.add("icon", icon);
                        }
                        case REWARD_ITEM -> setRewardItem(screen.editor.activeRewardDraft().source, id, rewardItemCount(screen.editor.activeRewardDraft().source));
                        case CHAPTER_ICON -> screen.chapterEditorIcon = id;
                    }
                    screen.editor.closePicker();
                    screen.rebuildWidgets();
                }
            }
        } else if (screen.picker == Picker.ENTITY) {
            List<EntityType<?>> entities = screen.renderer.filteredPickerEntities();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) return true;
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            int index = screen.pickerScroll + row * 8 + column;
            if (column >= 0 && column < 8 && row >= 0 && row < 5 && index < entities.size()) {
                screen.authoring.editingTask.source.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entities.get(index)).toString());
                screen.editor.closePicker();
                screen.rebuildWidgets();
            }
        } else {
            int gridX = left + 12;
            int gridY = top + 34;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 126) {
                return true;
            }
            int column = (int) (event.x() - gridX) / 44;
            int row = (int) (event.y() - gridY) / 42;
            int index = row * 4 + column;
            if (column >= 0 && column < 4 && row >= 0 && index < QuestScreenRenderer.QUEST_BACKGROUNDS.size()) {
                screen.authoring.background = QuestScreenRenderer.QUEST_BACKGROUNDS.get(index).toString();
                screen.editor.closePicker();
                screen.rebuildWidgets();
            }
        }
        return true;
    }

    boolean minimapClicked(MouseButtonEvent event) {
        return applyMinimapResult(screen.minimapPanel.mouseClicked(
            screen.layout.graphCanvasBounds(),
            screen.layout.minimapSettings(),
            event.x(),
            event.y(),
            event.input(),
            screen.layout.detailsDockContains(event.x(), event.y())
        ));
    }

    boolean applyMinimapResult(QuestMinimapPanel.Result result) {
        if (result.action() instanceof QuestMinimapPanel.Navigate navigate) {
            QuestGraphLayout.Point world = QuestMinimap.mapToWorld(
                QuestMinimap.mapping(screen.layout.surfaceLayout().worldBounds(16), navigate.mapBounds()),
                navigate.mapX(),
                navigate.mapY()
            );
            screen.graphViewport.centerOn(world.x(), world.y());
            screen.graphViewport.saveChapterViewport(screen.group);
        } else if (result.action() instanceof QuestMinimapPanel.OpenContextMenu menu) {
            openMinimapContextMenu(menu.screenX(), menu.screenY());
        } else if (result.action() instanceof QuestMinimapPanel.SavePosition position) {
            TheseusClientOptions.setMinimapPosition(position.x(), position.y());
            screen.rebuildWidgets();
        }
        return result.handled();
    }

    void openMinimapContextMenu(int mouseX, int mouseY) {
        boolean docked = TheseusClientOptions.defaultMinimapMode() == TheseusClientOptions.MinimapMode.DOCKED;
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            QuestScreenEditor.editorString(docked
                ? "gui.theseus.editor.undock_minimap"
                : "gui.theseus.editor.dock_minimap"),
            "",
            true,
            false,
            screen.actions::toggleMinimapDocking
        ));
        entries.add(QuestContextMenu.Entry.item(
            QuestScreenEditor.editorString("gui.theseus.editor.hide_minimap"),
            "",
            true,
            false,
            () -> {
                screen.minimapPanel.setHidden(true);
                screen.minimapPanel.clearTransientState();
                screen.rebuildWidgets();
            }
        ));
        screen.actions.showContextMenu(mouseX, mouseY, entries);
    }

    boolean minimapDragged(double mouseX, double mouseY) {
        return applyMinimapResult(screen.minimapPanel.mouseDragged(
            screen.layout.graphCanvasBounds(),
            screen.layout.minimapSettings(),
            mouseX,
            mouseY
        ));
    }

    boolean minimapReleased() {
        return applyMinimapResult(screen.minimapPanel.mouseReleased(screen.layout.minimapSettings()));
    }

    boolean mouseReleased(MouseButtonEvent event) {
        if (minimapReleased()) return true;
        if (screen.questMoved && TheseusClientOptions.snapToGrid()) {
            screen.authoringPanel.dockUi.snapCurrentDraftPosition();
        }
        screen.panning = false;
        screen.draggingQuestId = null;
        screen.questMoved = false;
        return screen.parentMouseReleased(event);
    }

    boolean mouseDragged(
        MouseButtonEvent event,
        double dragX,
        double dragY
    ) {
        if (screen.modalHost.blocksInput() && !screen.modalHost.ownsWidgetTree()) return true;
        if (minimapDragged(event.x(), event.y())) return true;
        if (screen.panning) {
            screen.graphViewport.panByScreenDelta(dragX, dragY);
            screen.rebuildWidgets();
            return true;
        }
        if (screen.draggingQuestId != null && screen.authoring.editingExisting && screen.mode.editorTool() == EditorTool.SELECT) {
            int deltaX = (int) Math.round(dragX / screen.graphViewport.state().zoom());
            int deltaY = (int) Math.round(dragY / screen.graphViewport.state().zoom());
            if (deltaX == 0 && deltaY == 0) return true;
            screen.questMoved = true;
            screen.authoring.x += deltaX;
            screen.authoring.y += deltaY;
            screen.authoring.xText = Integer.toString(screen.authoring.x);
            screen.authoring.yText = Integer.toString(screen.authoring.y);
            screen.authoring.xInvalid = false;
            screen.authoring.yInvalid = false;
            screen.editor.updateDraftGroupPosition();
            screen.rebuildWidgets();
            return true;
        }
        return screen.parentMouseDragged(event, dragX, dragY);
    }

    boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        if (screen.modalHost.is(QuestModalHost.Modal.DESCRIPTION_EDITOR)) {
            int modalWidth = Math.min(760, screen.guiWidth() - 24);
            int left = (screen.guiWidth() - modalWidth) / 2;
            int paneWidth = (modalWidth - 40) / 2;
            int previewX = left + 20 + paneWidth;
            if (mouseX >= previewX) {
                screen.descriptionPreviewScroll = Math.max(0, Math.min(
                    screen.descriptionPreviewMaxScroll,
                    screen.descriptionPreviewScroll - (int)Math.round(scrollY * 18)
                ));
                return true;
            }
            return screen.parentMouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (screen.modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            int max = Math.max(0, screen.imports.diagnosticLines(416).size() - 13);
            screen.diagnosticsScroll = Math.max(0, Math.min(max, screen.diagnosticsScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            int max = Math.max(0, screen.importController.entries().size() - screen.imports.importVisibleRows());
            screen.importScroll = Math.max(0, Math.min(max, screen.importScroll - (int) Math.signum(scrollY)));
            screen.rebuildWidgets();
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER) && screen.picker == Picker.ICON) {
            int itemCount = screen.renderer.filteredPickerItems().size();
            int maxRow = Math.max(0, (itemCount + 7) / 8 - 5);
            int row = screen.pickerScroll / 8 - (int) Math.signum(scrollY);
            screen.pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            screen.pickerScrollByTarget.put(screen.pickerTarget, screen.pickerScroll);
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER) && screen.picker == Picker.ENTITY) {
            int entityCount = screen.renderer.filteredPickerEntities().size();
            int maxRow = Math.max(0, (entityCount + 7) / 8 - 5);
            int row = screen.pickerScroll / 8 - (int) Math.signum(scrollY);
            screen.pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            screen.pickerScrollByTarget.put(screen.pickerTarget, screen.pickerScroll);
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.PICKER)) return true;
        if (screen.modalHost.is(QuestModalHost.Modal.NESTED_REWARDS)
            && !screen.modalHost.isNestedRewardChooserOpen() && screen.authoring.editingNestedReward == null) {
            int max = Math.max(0, nestedRewards(screen.authoring.editingReward).size() - 4);
            screen.authoring.nestedRewardScroll = Math.max(0, Math.min(max, screen.authoring.nestedRewardScroll - (int) Math.signum(scrollY)));
            screen.rebuildWidgets();
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.NESTED_TASK_CHOOSER)) {
            screen.authoringPanel.scrollTaskChooser(scrollY);
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.NESTED_TASKS)) {
            int max = Math.max(0, nestedTasks(screen.authoring.editingTask).size() - 4);
            screen.authoring.nestedTaskScroll = Math.max(
                0,
                Math.min(max, screen.authoring.nestedTaskScroll - (int) Math.signum(scrollY))
            );
            screen.rebuildWidgets();
            return true;
        }
        if (screen.modalHost.is(QuestModalHost.Modal.TASK_CHOOSER)) {
            screen.authoringPanel.scrollTaskChooser(scrollY);
            return true;
        }
        if (screen.modalHost.blocksInput()) return true;
        if (QuestMinimap.contains(
            screen.minimapPanel.bounds(screen.layout.graphCanvasBounds(), screen.layout.minimapSettings()),
            mouseX,
            mouseY
        )) return true;
        if (screen.sidebarOpen
            && mouseX >= 0
            && mouseX < screen.layout.sidebarWidth()
            && screen.chapterListState.rowAt(mouseY) >= 0
            && screen.chapterListState.hasOverflow()) {
            screen.chapterListFocused = true;
            int delta = -(int) Math.signum(scrollY);
            if (delta != 0) {
                screen.chapterListState.scrollByRows(delta);
                screen.rebuildWidgets();
            }
            return true;
        }
        if (screen.authoring.open && screen.layout.detailsDockContains(mouseX, mouseY)) {
            if (screen.authoringPanel.createQuestTab == DetailTab.TASKS) {
                screen.authoringPanel.scrollDraftTaskList(scrollY);
                screen.rebuildWidgets();
            } else if (screen.authoringPanel.createQuestTab == DetailTab.REWARDS) {
                screen.authoringPanel.scrollDraftRewardList(scrollY);
                screen.rebuildWidgets();
            } else if (screen.authoringPanel.createQuestTab == DetailTab.OVERVIEW) {
                screen.parentMouseScrolled(mouseX, mouseY, scrollX, scrollY);
                screen.authoringPanel.captureOverviewScroll();
            }
            return true;
        }
        if (screen.detailsOpen && mouseX >= screen.guiWidth() - screen.layout.detailsWidth()) {
            screen.detailsPanel.scroll(scrollY);
            return true;
        }
        QuestGraphLayout.CanvasBounds canvas = screen.layout.graphCanvasBounds();
        if (canvas.contains(mouseX, mouseY)) {
            screen.graphViewport.zoomAroundScreenPoint(canvas, mouseX, mouseY, scrollY * 0.1);
            screen.rebuildWidgets();
            return true;
        }
        return screen.parentMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
