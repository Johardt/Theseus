package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.ArrayList;
import java.util.List;
import me.johardt.theseus.core.QuestMutation;
import me.johardt.theseus.client.description.MarkdownEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Builds the screen and its transient editor widgets. */
final class QuestScreenWidgets {
    private final QuestScreen screen;

    QuestScreenWidgets(QuestScreen screen) {
        this.screen = screen;
    }

    void initialize() {
        screen.authoringPanel.setViewport(screen.guiFont(), screen.guiWidth(), screen.guiHeight());
        screen.graphViewport.activateChapter(screen.group, screen.layout.graphCanvasBounds(), screen.layout.graphWorldBounds());
        // Overlay policy decides which widget tree is eligible for focus and
        // input. The screen only adapts that decision into Minecraft widgets.
        switch (screen.modalHost.active()) {
            case DIAGNOSTICS -> {
                addDiagnosticsModalWidgets();
                return;
            }
            case FILE_IMPORT -> {
                screen.imports.addImportModalWidgets();
                return;
            }
            case PICKER -> {
                addPickerSearchWidget();
                return;
            }
            case DELETE_QUEST_CONFIRMATION -> {
                addDeleteQuestConfirmationWidgets();
                return;
            }
            case PROGRESS_RESET_CONFIRMATION -> {
                addProgressResetConfirmationWidgets();
                return;
            }
            case DISCARD_CONFIRMATION -> {
                addDiscardConfirmationWidgets();
                return;
            }
            case TASK_DELETE_CONFIRMATION -> {
                addDeleteTaskConfirmationWidgets();
                return;
            }
            case CHAPTER_EDITOR -> {
                addChapterEditorWidgets();
                return;
            }
            case PASTE_ID_PROMPT -> {
                addPasteIdPromptWidgets();
                return;
            }
            case RAW_INSPECTOR -> {
                addRawInspectorWidgets();
                return;
            }
            case DESCRIPTION_EDITOR -> {
                addDescriptionEditorWidgets();
                return;
            }
            case NESTED_REWARD_EDITOR -> {
                screen.authoringPanel.rewardEditor.addRewardEditorWidgets(screen.authoring.editingNestedReward, true);
                return;
            }
            case NESTED_REWARDS, NESTED_REWARD_CHOOSER -> {
                screen.authoringPanel.rewardEditor.addNestedRewardWidgets();
                return;
            }
            case REWARD_EDITOR -> {
                screen.authoringPanel.rewardEditor.addRewardEditorWidgets(screen.authoring.editingReward, false);
                return;
            }
            case NESTED_TASKS, NESTED_TASK_CHOOSER -> {
                screen.authoringPanel.taskEditor.addNestedTaskWidgets();
                return;
            }
            case TASK_EDITOR -> {
                screen.authoringPanel.taskEditor.addTaskEditorWidgets();
                return;
            }
            default -> { }
        }
        int sidebarWidth = screen.layout.sidebarWidth();
        Button sidebarToggle = Widgets.button(widget -> {
            widget
                .withPosition(screen.sidebarOpen ? sidebarWidth - 13 : 3, 2)
                .withSize(11, 11);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(screen.sidebarOpen ? "‹" : "›")
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                screen.sidebarOpen = !screen.sidebarOpen;
                screen.rebuildWidgets();
            });
            widget.withTooltip(
                Component.translatable(screen.sidebarOpen
                    ? "gui.theseus.editor.collapse_quest_groups"
                    : "gui.theseus.editor.show_quest_groups")
            );
        });
        screen.addScreenWidget(sidebarToggle);

        if (!QuestScreenEditor.canEdit()) {
            screen.mode = new PlayMode();
            screen.authoring.open = false;
            screen.editor.closePicker();
        } else {
            HeaderLayout header = screen.layout.headerLayout();
            if (!screen.diagnostics.isEmpty()) {
                screen.addScreenWidget(Widgets.button(widget -> {
                    widget.withPosition(header.diagnosticsX(), header.diagnosticsY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.diagnostics")));
                    widget.withCallback(() -> {
                        screen.modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
                        screen.diagnosticsScroll = 0;
                        screen.rebuildWidgets();
                    });
                    widget.withTooltip(Component.translatable("gui.theseus.editor.view_validation_diagnostics"));
                }));
            }
            if (screen.mode.isAuthoring() && !screen.authoring.open) screen.addScreenWidget(Widgets.button(widget -> {
                    widget.withPosition(header.importX(), header.importY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.import")));
                    widget.withCallback(screen.imports::openNativeFilePicker);
                    widget.withTooltip(Component.translatable("gui.theseus.editor.choose_one_or_more_quest_json_files"));
                }));
            screen.addScreenWidget(screen.layout.editorButton(
                header.editX(),
                "edit",
                screen.mode.isAuthoring(),
                Component.translatable(screen.mode.isAuthoring() ? "gui.theseus.editor.leave_quest_edit_mode" : "gui.theseus.editor.edit_quests"),
                () -> {
                    screen.editor.requestDiscard(() -> {
                        boolean enteringEditMode = !screen.mode.isAuthoring();
                        screen.mode = enteringEditMode ? screen.authoring : new PlayMode();
                        screen.authoring.setEditorTool(EditorTool.SELECT);
                        screen.linkSourceId = null;
                        screen.editor.closePicker();
                        screen.panning = false;
                        ClientQuest focusedQuest = screen.actions.selected();
                        if (enteringEditMode && focusedQuest != null) {
                            screen.editor.beginEditQuest(focusedQuest);
                        }
                        if (!enteringEditMode) {
                            screen.editor.closeDraft();
                            if (focusedQuest != null) screen.detailsOpen = true;
                        }
                        screen.rebuildWidgets();
                        if (enteringEditMode && QuestTutorial.shouldAutoShow(
                            QuestScreenEditor.canEdit(),
                            TheseusClientOptions.tutorialAutoShow(),
                            TheseusClientOptions.tutorialSeen()
                        )) screen.actions.openTutorial();
                    });
                }
            ));
        }
        screen.layout.addGraphNavigationWidgets(screen.layout.headerLayout());
        if (screen.mode.isAuthoring() && !screen.authoring.open) {
            int toolX = sidebarWidth + 24;
            for (EditorTool tool : EditorTool.values()) {
                screen.addScreenWidget(screen.layout.editorButton(
                    toolX,
                    tool.icon,
                    screen.mode.editorTool() == tool,
                    Component.translatable(tool.tooltipKey, tool.shortcut),
                    () -> {
                        screen.editor.requestDiscard(() -> {
                            screen.mode.setEditorTool(tool);
                            screen.editor.closeDraft();
                            screen.linkSourceId = null;
                            screen.panning = false;
                            screen.rebuildWidgets();
                        });
                    }
                ));
                toolX += 22;
            }
        }
        if (screen.sidebarOpen) {
            List<String> orderedGroups = new ArrayList<>(screen.actions.groups());
            screen.chapterListState.setViewport(CHAPTER_LIST_TOP, screen.layout.chapterListBottom(), CHAPTER_ROW_HEIGHT);
            screen.chapterListState.setChapterCount(orderedGroups.size());
            int y = CHAPTER_LIST_TOP;
            for (int chapterIndex : screen.chapterListState.visibleIndices()) {
                String candidate = orderedGroups.get(chapterIndex);
                int index = chapterIndex;
                int groupY = y + (chapterIndex - screen.chapterListState.firstVisibleRow()) * CHAPTER_ROW_HEIGHT;
                Button button = Widgets.button(widget -> {
                    int buttonWidth = sidebarWidth - (screen.mode.isAuthoring() ? 51 : screen.chapterListState.hasOverflow() ? 10 : 8);
                    widget
                        .withPosition(4, groupY)
                        .withSize(Math.max(1, buttonWidth), CHAPTER_ROW_CONTENT_HEIGHT);
                    widget.withTexture(null);
                    widget.withRenderer(screen.layout.chapterButtonRenderer(candidate, candidate.equals(screen.group)));
                    widget.withTooltip(Component.literal(candidate));
                    widget.withCallback(() -> {
                        screen.chapterListFocused = true;
                        screen.focusedChapterIndex = index;
                        screen.actions.selectChapterIndex(index);
                    });
                });
                screen.addScreenWidget(button);
                if (screen.mode.isAuthoring()) {
                    screen.addScreenWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 45, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                        widget.withCallback(() -> reorderChapter(index, -1));
                        widget.active = index > 0;
                        widget.withTooltip(Component.translatable("gui.theseus.editor.move_chapter_up"));
                    }));
                    screen.addScreenWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 32, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                        widget.withCallback(() -> reorderChapter(index, 1));
                        widget.active = index < orderedGroups.size() - 1;
                        widget.withTooltip(Component.translatable("gui.theseus.editor.move_chapter_down"));
                    }));
                    screen.addScreenWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 19, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                        widget.withCallback(() -> openChapterEditor(candidate));
                        widget.withTooltip(Component.translatable("gui.theseus.editor.edit_chapter"));
                    }));
                }
            }
            int addChapterY = Math.max(CHAPTER_LIST_TOP, screen.guiHeight() - 24);
            if (screen.mode.isAuthoring()) screen.addScreenWidget(Widgets.button(widget -> {
                widget.withPosition(4, addChapterY).withSize(sidebarWidth - 8, 20);
                widget.withTexture(null);
                widget.withRenderer(screen.layout.chapterButtonRenderer("+  Add chapter", false));
                widget.withCallback(() -> openChapterEditor(null));
                widget.withTooltip(Component.translatable("gui.theseus.editor.add_chapter"));
            }));
        }
        screen.layout.addDockWidgets();
    }

    /** Snaps the active authoring draft once, leaving the change for Save. */
    void addRawInspectorButton(int x, int y, int width, Runnable open) {
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(screen.guiWidth(), 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.raw_json")));
            widget.withCallback(open);
            widget.withTooltip(Component.translatable("gui.theseus.editor.inspect_this_configuration_without_editing_it"));
        }));
    }

    void openRawInspector(String title, JsonObject source) {
        screen.rawInspectorTitle = title;
        screen.rawInspectorJson = GSON.toJson(source);
        screen.modalHost.open(QuestModalHost.Modal.RAW_INSPECTOR);
        screen.rebuildWidgets();
    }

    void addRawInspectorWidgets() {
        int inspectorWidth = Math.min(480, screen.guiWidth() - 32);
        int inspectorHeight = Math.min(280, screen.guiHeight() - 48);
        int left = (screen.guiWidth() - inspectorWidth) / 2;
        int top = (screen.guiHeight() - inspectorHeight) / 2;
        MultiLineEditBox value = MultiLineEditBox.builder()
            .setX(left + 12).setY(top + 34)
            .build(screen.guiFont(), inspectorWidth - 24, inspectorHeight - 70, Component.literal(screen.rawInspectorTitle));
        value.setValue(screen.rawInspectorJson);
        value.active = false;
        screen.addScreenWidget(value);
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + inspectorWidth - 92, top + inspectorHeight - 28).withSize(80, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.close")));
            widget.withCallback(() -> {
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
        }));
    }

    void openDescriptionEditor() {
        screen.descriptionEditorValue = screen.authoring.body;
        screen.descriptionPreviewScroll = 0;
        screen.modalHost.open(QuestModalHost.Modal.DESCRIPTION_EDITOR);
        screen.rebuildWidgets();
    }

    void addDescriptionEditorWidgets() {
        int modalWidth = Math.min(760, screen.guiWidth() - 24);
        int modalHeight = Math.min(420, screen.guiHeight() - 24);
        int left = (screen.guiWidth() - modalWidth) / 2;
        int top = (screen.guiHeight() - modalHeight) / 2;
        int gutter = 8;
        int paneWidth = (modalWidth - 32 - gutter) / 2;
        int editorTop = top + 61;
        int editorHeight = modalHeight - 103;

        screen.descriptionEditor = new MarkdownEditBox(
            screen.guiFont(), left + 12, editorTop, paneWidth, editorHeight,
            Component.translatable("gui.theseus.editor.quest_markdown_description")
        );
        screen.descriptionEditor.setValue(screen.descriptionEditorValue);
        screen.descriptionEditor.setValueListener(value -> screen.descriptionEditorValue = value);
        screen.addScreenWidget(screen.descriptionEditor);
        screen.setScreenInitialFocus(screen.descriptionEditor);

        int actionX = left + 12;
        int toolbarY = top + 31;
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.h1", "header1", () -> screen.descriptionEditor.prefixLine("# "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.h2", "header2", () -> screen.descriptionEditor.prefixLine("## "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.bold", "B", () -> screen.descriptionEditor.surround("**"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.italic", "I", () -> screen.descriptionEditor.surround("--"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.underline", "U", () -> screen.descriptionEditor.surround("__"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.strikethrough", "S", () -> screen.descriptionEditor.surround("~~"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.spoiler", "||", () -> screen.descriptionEditor.surround("||"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.color", "C", () -> screen.descriptionEditor.surround("/e/"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.list", "list", () -> screen.descriptionEditor.prefixLine("- "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.blockquote", ">", () -> screen.descriptionEditor.prefixLine("> "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.link", "link", () -> screen.descriptionEditor.insertLink(null, "https://"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.horizontal_rule", "horizontalline", () -> screen.descriptionEditor.insert("\n---\n"));

        int objectX = actionX;
        if (!screen.authoring.tasks.isEmpty()) {
            objectX = addMarkdownSpriteAction(objectX, toolbarY, "gui.theseus.editor.insert_task", "task", () ->
                screen.descriptionEditor.insertObject("task", screen.authoring.tasks.getFirst().id));
        }
        if (!screen.authoring.rewards.isEmpty()) {
            addMarkdownSpriteAction(objectX, toolbarY, "gui.theseus.editor.insert_reward", "reward", () ->
                screen.descriptionEditor.insertObject("reward", screen.authoring.rewards.getFirst().id));
        }

        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 174, top + modalHeight - 31).withSize(76, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(this::closeDescriptionEditor);
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 92, top + modalHeight - 31).withSize(80, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.apply")));
            widget.withCallback(this::applyDescriptionEditor);
        }));
    }

    int addMarkdownSpriteAction(int x, int y, String tooltipKey, String icon, Runnable action) {
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withTexture(null);
            Identifier normal = QuestScreenRenderer.sprite("editor/" + icon + "/normal");
            Identifier hovered = QuestScreenRenderer.sprite("editor/" + icon + "/hovered");
            widget.withRenderer(WidgetRenderers.sprite(new WidgetSprites(normal, hovered)));
            widget.withCallback(action);
            widget.withTooltip(QuestScreenEditor.editorText(tooltipKey));
        }));
        return x + MARKDOWN_ACTION_SIZE + MARKDOWN_ACTION_GAP;
    }

    int addMarkdownTextAction(int x, int y, String tooltipKey, String label, Runnable action) {
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withRenderer(WidgetRenderers.center(
                MARKDOWN_ACTION_SIZE,
                MARKDOWN_ACTION_SIZE,
                WidgetRenderers.text(Component.literal(label))
            ));
            widget.withCallback(action);
            widget.withTooltip(QuestScreenEditor.editorText(tooltipKey));
        }));
        return x + MARKDOWN_ACTION_SIZE + MARKDOWN_ACTION_GAP;
    }

    void applyDescriptionEditor() {
        screen.authoring.body = screen.descriptionEditorValue;
        screen.authoring.descriptionTouched = true;
        closeDescriptionEditor();
    }

    void closeDescriptionEditor() {
        screen.descriptionEditor = null;
        screen.modalHost.close();
        screen.rebuildWidgets();
    }

    void openChapterEditor(String name) {
        screen.modalHost.open(QuestModalHost.Modal.CHAPTER_EDITOR);
        screen.chapterEditorOriginal = name;
        screen.chapterEditorName = name == null ? "" : name;
        ChapterDisplay display = name == null ? null : screen.chapterDisplays.get(name);
        screen.chapterEditorIcon = display == null ? "minecraft:map" : display.icon();
        screen.chapterEditorIconEnabled = display == null || display.iconEnabled();
        screen.chapterEditorBackground = display == null ? "" : display.background();
        screen.chapterEditorBackgroundOpacity = display == null ? 100 : display.backgroundOpacity();
        screen.chapterEditorError = "";
        screen.chapterDeleteArmed = false;
        screen.chapterEditorBaseline = chapterEditorSnapshot();
        screen.rebuildWidgets();
    }

    String chapterEditorSnapshot() {
        return screen.chapterEditorName + "\u0000" + screen.chapterEditorIcon + "\u0000" + screen.chapterEditorIconEnabled + "\u0000" + screen.chapterEditorBackground + "\u0000" + screen.chapterEditorBackgroundOpacity;
    }

    boolean hasUnsavedChapterEditor() {
        return screen.modalHost.isChapterEditorOpen()
            && screen.chapterEditorBaseline != null
            && !screen.chapterEditorBaseline.equals(chapterEditorSnapshot());
    }

    void addChapterEditorWidgets() {
        int left = (screen.guiWidth() - 280) / 2;
        int top = screen.renderer.chapterEditorTop();
        EditBox name = new EditBox(screen.guiFont(), left + 14, top + 48, 252, 18, Component.translatable("gui.theseus.editor.chapter_name"));
        name.setValue(screen.chapterEditorName);
        name.setResponder(value -> screen.chapterEditorName = value);
        screen.addScreenWidget(name);
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 81).withSize(34, 24);
            widget.withRenderer((graphics, context, partialTick) -> {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(screen.chapterEditorIcon));
                    graphics.item(new ItemStack(item == null ? Items.MAP : item), context.getX() + 9, context.getY() + 4);
                } catch (RuntimeException ignored) { }
            });
            widget.withCallback(() -> screen.editor.openPicker(Picker.ICON, PickerTarget.CHAPTER_ICON));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_chapter_icon"));
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 54, top + 81).withSize(100, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(
                "gui.theseus.editor.chapter_icon_state",
                Component.translatable(screen.chapterEditorIconEnabled ? "gui.theseus.editor.state_on" : "gui.theseus.editor.state_off")
            )));
            widget.withCallback(() -> {
                screen.chapterEditorIconEnabled = !screen.chapterEditorIconEnabled;
                screen.rebuildWidgets();
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.show_or_hide_this_chapter_s_icon"));
        }));
        EditBox background = new EditBox(screen.guiFont(), left + 14, top + 126, 252, 18, Component.translatable("gui.theseus.editor.background_path_or_url"));
        background.setValue(screen.chapterEditorBackground);
        background.setResponder(value -> screen.chapterEditorBackground = value);
        screen.addScreenWidget(background);
        EditBox opacity = new EditBox(screen.guiFont(), left + 14, top + 158, 90, 18, Component.translatable("gui.theseus.editor.opacity"));
        opacity.setValue(Integer.toString(screen.chapterEditorBackgroundOpacity));
        opacity.setResponder(value -> {
            try { screen.chapterEditorBackgroundOpacity = Math.clamp(Integer.parseInt(value), 0, 100); }
            catch (NumberFormatException ignored) { screen.chapterEditorBackgroundOpacity = 100; }
        });
        screen.addScreenWidget(opacity);
        if (screen.chapterEditorOriginal != null) screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 201).withSize(72, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(this::deleteChapter);
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 94, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> screen.editor.requestModalDiscard(() -> {
                screen.chapterEditorBaseline = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            }));
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 184, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save")));
            widget.withCallback(this::saveChapter);
        }));
    }

    void saveChapter() {
        String name = screen.chapterEditorName.trim();
        if (name.isEmpty() || (screen.actions.groups().contains(name) && !name.equals(screen.chapterEditorOriginal))) {
            screen.chapterEditorError = name.isEmpty() ? "Chapter name is required." : "That chapter already exists.";
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", screen.chapterEditorOriginal == null ? "create" : "update");
        if (screen.chapterEditorOriginal != null) action.addProperty("old_name", screen.chapterEditorOriginal);
        action.addProperty("name", name);
        action.addProperty("icon", screen.chapterEditorIcon);
        action.addProperty("icon_enabled", screen.chapterEditorIconEnabled);
        action.addProperty("background", screen.chapterEditorBackground);
        action.addProperty("background_opacity", screen.chapterEditorBackgroundOpacity);
        sendChapterAction(action);
        screen.modalHost.close();
        screen.chapterEditorBaseline = null;
        screen.group = name;
        screen.chapterListState.setChapterCount(screen.actions.groups().size());
        screen.chapterListState.ensureVisible(new ArrayList<>(screen.actions.groups()).indexOf(screen.group));
        screen.rebuildWidgets();
    }

    void deleteChapter() {
        if (!screen.chapterDeleteArmed) {
            screen.chapterDeleteArmed = true;
            screen.chapterEditorError = "Click Delete again to confirm.";
            screen.rebuildWidgets();
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", "delete");
        action.addProperty("name", screen.chapterEditorOriginal);
        sendChapterAction(action);
        screen.modalHost.close();
        screen.chapterEditorBaseline = null;
        screen.rebuildWidgets();
    }

    void reorderChapter(int index, int direction) {
        List<String> order = new ArrayList<>(screen.actions.groups());
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) return;
        java.util.Collections.swap(order, index, target);
        JsonObject action = new JsonObject();
        action.addProperty("operation", "reorder");
        action.add("order", GSON.toJsonTree(order));
        sendChapterAction(action);
        screen.chapters.clear();
        screen.chapters.addAll(order);
        screen.chapterListState.setChapterCount(order.size());
        if (screen.group.equals(order.get(target))) screen.chapterListState.ensureVisible(target);
        else screen.chapterListState.ensureVisible(order.indexOf(screen.group));
        screen.rebuildWidgets();
    }

    void sendChapterAction(JsonObject action) {
        screen.editor.sendEditorMutation(new QuestMutation.ChapterAction(action));
    }

    void addPickerSearchWidget() {
        if (screen.picker == Picker.ICON || screen.picker == Picker.ENTITY) {
            screen.pickerSearch = new EditBox(
                screen.guiFont(),
                screen.renderer.pickerLeft() + 12,
                screen.renderer.pickerTop() + 30,
                176,
                18,
                QuestScreenEditor.editorText(screen.picker == Picker.ENTITY
                    ? "gui.theseus.editor.search_entities"
                    : "gui.theseus.editor.search_blocks_and_items")
            );
            screen.pickerSearch.setResponder(ignored -> screen.pickerScroll = 0);
            screen.addScreenWidget(screen.pickerSearch);
            screen.setScreenInitialFocus(screen.pickerSearch);
        }
    }

    void addDeleteQuestConfirmationWidgets() {
        int left = (screen.guiWidth() - 240) / 2;
        int top = (screen.guiHeight() - 110) / 2;
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(() -> {
                screen.editor.confirmDeleteQuest();
            });
        }));
    }

    void addProgressResetConfirmationWidgets() {
        int left = (screen.guiWidth() - 280) / 2;
        int top = (screen.guiHeight() - 142) / 2;
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 102).withSize(122, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                screen.progressResetTarget = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 146, top + 102).withSize(122, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.reset_progress")));
            widget.withCallback(screen.editor::confirmProgressReset);
            widget.active = screen.progressResetTarget != null && !screen.mutations.isPending();
        }));
    }

    void addDeleteTaskConfirmationWidgets() {
        int left = (screen.guiWidth() - 240) / 2;
        int top = (screen.guiHeight() - 110) / 2;
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                screen.authoring.taskDeleteConfirmation = -1;
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(() -> {
                screen.editor.confirmDeleteTask();
            });
        }));
    }

    void addDiscardConfirmationWidgets() {
        int left = (screen.guiWidth() - 260) / 2;
        int top = (screen.guiHeight() - 116) / 2;
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.keep_editing")));
            widget.withCallback(() -> {
                screen.modalHost.cancelDismissal();
                screen.rebuildWidgets();
            });
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 136, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.discard_changes")));
            widget.withCallback(() -> {
                screen.modalHost.confirmDismissal();
            });
        }));
    }

    void addDiagnosticsModalWidgets() {
        int left = (screen.guiWidth() - 440) / 2;
        int top = (screen.guiHeight() - 300) / 2;
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 330, top + 264).withSize(96, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.close")));
            widget.withCallback(() -> {
                screen.imports.closeDiagnosticsModal();
                screen.rebuildWidgets();
            });
        }));
    }

    void addPasteIdPromptWidgets() {
        int left = (screen.guiWidth() - 280) / 2;
        int top = (screen.guiHeight() - 130) / 2;
        screen.pasteIdField = new EditBox(screen.guiFont(), left + 14, top + 52, 252, 18, Component.translatable("gui.theseus.editor.new_quest_id"));
        screen.pasteIdField.setValue(QuestScreenActions.clipboardSourceId + "_copy");
        screen.addScreenWidget(screen.pasteIdField);
        screen.setScreenInitialFocus(screen.pasteIdField);
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                screen.pasteIdField = null;
                screen.modalHost.close();
                screen.rebuildWidgets();
            });
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 166, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.paste")));
            widget.withCallback(screen.actions::confirmPasteIdPrompt);
        }));
    }
}
