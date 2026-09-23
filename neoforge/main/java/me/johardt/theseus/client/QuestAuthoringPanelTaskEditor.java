package me.johardt.theseus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.List;
import me.johardt.theseus.client.QuestAuthoringSession.TaskDraft;
import me.johardt.theseus.client.QuestScreen.Picker;
import me.johardt.theseus.client.QuestScreen.PickerTarget;
import me.johardt.theseus.core.EditorTypeRegistry;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.client.QuestPresentation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestAuthoringPanel.*;

/** Builds and renders task editors, including nested composite tasks. */
final class QuestAuthoringPanelTaskEditor {
    private final QuestAuthoringPanel panel;

    QuestAuthoringPanelTaskEditor(QuestAuthoringPanel panel) {
        this.panel = panel;
    }

    void openTaskEditor(int index) {
        if (!isTaskEditable(panel.authoring.tasks.get(index))) {
            panel.host.dispatch(new ShowMessage(panel.unavailableReason(EditorTypeRegistry.Kind.TASK, panel.authoring.tasks.get(index).type) + ". It is preserved read-only."));
            return;
        }
        panel.authoring.editTask(index);
        panel.modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
        panel.host.dispatch(new RebuildWidgets());
    }

    void addTaskEditorWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        int fieldWidth = 232;

        EditBox id = new EditBox(panel.font, left + 14, top + 38, fieldWidth, 18, Component.translatable("gui.theseus.editor.task_id"));
        id.setValue(panel.authoring.editingTask.id);
        id.setResponder(value -> panel.authoring.editingTask.id = value);
        panel.host.addWidget(id);

        EditBox title = new EditBox(panel.font, left + 14, top + 70, fieldWidth, 18, Component.translatable("gui.theseus.editor.task_title"));
        title.setValue(jsonString(panel.authoring.editingTask.source, "title", ""));
        title.setResponder(value -> QuestScreenEditor.setOptionalString(panel.authoring.editingTask.source, "title", value));
        panel.host.addWidget(title);

        Button icon = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.TASK_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_task_icon_override"));
        });
        panel.host.addWidget(icon);
        Button clearIcon = Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                panel.authoring.editingTask.source.remove("icon");
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.use_the_default_task_icon"));
        });
        panel.host.addWidget(clearIcon);
        // Keep action controls outside the label lane drawn by the foreground pass.
        panel.addRawInspectorButton(left + QuestAuthoringPanel.TASK_RAW_INSPECTOR_X, top + 101, 88, () -> panel.host.dispatch(new OpenRawInspector("Task: " + panel.authoring.editingTask.id, panel.authoring.editingTask.source)));

        switch (panel.authoring.editingTask.type) {
            case "theseus:dummy" -> addDummyTaskFields(left, top, fieldWidth);
            case "theseus:item" -> addItemTaskFields(left, top, fieldWidth);
            case "theseus:xp" -> addXpTaskFields(left, top, fieldWidth);
            case "theseus:kill_entity" -> addKillTaskFields(left, top, fieldWidth);
            case "theseus:advancement" -> addStringListTaskField(left, top, fieldWidth, "advancements", "gui.theseus.editor.advancement_ids", "minecraft:story/mine_stone");
            case "theseus:biome" -> addIdentifierTaskField(left, top, fieldWidth, "biomes", "gui.theseus.editor.biome_or_tag", "minecraft:plains");
            case "theseus:block_interaction" -> addBlockInteractionTaskFields(left, top, fieldWidth);
            case "theseus:changed_dimension" -> addDimensionTaskFields(left, top, fieldWidth);
            case "theseus:check" -> addJsonTaskField(left, top, fieldWidth, "components", "gui.theseus.editor.player_data_predicate", new JsonObject());
            case "theseus:composite" -> addCompositeTaskFields(left, top, fieldWidth);
            case "theseus:entity_interaction" -> addPredicateTargetFields(left, top, fieldWidth, "entity", "gui.theseus.editor.entity_or_tag", "minecraft:pig", PickerTarget.TASK_ENTITY);
            case "theseus:item_interaction", "theseus:item_use" -> addPredicateTargetFields(left, top, fieldWidth, "item", "gui.theseus.editor.item_or_tag", "minecraft:stick", PickerTarget.TASK_ITEM);
            case "theseus:location" -> addLocationTaskFields(left, top, fieldWidth);
            case "theseus:recipe" -> addStringListTaskField(left, top, fieldWidth, "recipes", "gui.theseus.editor.recipe_ids", "minecraft:crafting_table");
            case "theseus:stat" -> addStatTaskFields(left, top, fieldWidth);
            case "theseus:structure" -> addIdentifierTaskField(left, top, fieldWidth, "structures", "gui.theseus.editor.structure_or_tag", "#minecraft:village");
            default -> {
            }
        }

        Button cancel = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> panel.host.dispatch(new RequestModalDiscard(this::closeTaskEditor)));
        });
        panel.host.addWidget(cancel);
        Button save = Widgets.button(widget -> {
            widget.withPosition(left + 138, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save_task")));
            widget.withCallback(this::saveTaskEditor);
        });
        panel.host.addWidget(save);
    }

    void addDummyTaskFields(int left, int top, int width) {
        EditBox value = new EditBox(panel.font, left + 14, top + 142, width, 18, Component.translatable("gui.theseus.editor.trigger_value"));
        value.setValue(jsonString(panel.authoring.editingTask.source, "value", ""));
        value.setResponder(text -> panel.authoring.editingTask.source.addProperty("value", text));
        panel.host.addWidget(value);
        EditBox description = new EditBox(panel.font, left + 14, top + 181, width, 18, Component.translatable("gui.theseus.editor.description"));
        description.setValue(jsonString(panel.authoring.editingTask.source, "description", ""));
        description.setResponder(text -> QuestScreenEditor.setOptionalString(panel.authoring.editingTask.source, "description", text));
        panel.host.addWidget(description);
    }

    void addItemTaskFields(int left, int top, int width) {
        EditBox item = new EditBox(panel.font, left + 14, top + 142, width - 40, 18, Component.translatable("gui.theseus.editor.item_or_tag"));
        item.setValue(registryValueString(panel.authoring.editingTask.source, "item", "minecraft:stone"));
        item.setResponder(text -> panel.authoring.editingTask.source.addProperty("item", text));
        panel.host.addWidget(item);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.TASK_ITEM)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_item"));
        });
        panel.host.addWidget(choose);
        addAmountField(left, top + 181);
        addCycleButton(left + 104, top + 178, 142, "collection", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    void addXpTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        addCycleButton(left + 104, top + 139, 142, "xpType", "level", List.of("level", "points"));
        addCycleButton(left + 14, top + 178, 232, "collectionType", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    void addKillTaskFields(int left, int top, int width) {
        EditBox entity = new EditBox(panel.font, left + 14, top + 142, width - 40, 18, Component.translatable("gui.theseus.editor.entity"));
        entity.setValue(registryValueString(panel.authoring.editingTask.source, "entity", "minecraft:pig"));
        entity.setResponder(text -> panel.authoring.editingTask.source.addProperty("entity", text));
        panel.host.addWidget(entity);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ENTITY, PickerTarget.TASK_ENTITY)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_entity"));
        });
        panel.host.addWidget(choose);
        addAmountField(left, top + 181);
    }

    void addIdentifierTaskField(int left, int top, int width, String key, String labelKey, String fallback) {
        EditBox field = new EditBox(panel.font, left + 14, top + 142, width, 18, QuestScreenEditor.editorText(labelKey));
        field.setValue(registryValueString(panel.authoring.editingTask.source, key, fallback));
        field.setResponder(text -> panel.authoring.editingTask.source.addProperty(key, text));
        panel.host.addWidget(field);
    }

    void addStringListTaskField(int left, int top, int width, String key, String labelKey, String fallback) {
        EditBox field = new EditBox(panel.font, left + 14, top + 142, width, 18, QuestScreenEditor.editorText(labelKey));
        field.setValue(QuestScreenEditor.jsonStringList(panel.authoring.editingTask.source, key, fallback));
        field.setResponder(text -> panel.authoring.editingTask.source.add(key, stringArray(text)));
        panel.host.addWidget(field);
    }

    void addPredicateTargetFields(
        int left, int top, int width, String key, String labelKey, String fallback, PickerTarget target
    ) {
        EditBox value = new EditBox(panel.font, left + 14, top + 142, width - 40, 18, QuestScreenEditor.editorText(labelKey));
        value.setValue(registryValueString(panel.authoring.editingTask.source, key, fallback));
        value.setResponder(text -> panel.authoring.editingTask.source.addProperty(key, text));
        panel.host.addWidget(value);
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(target == PickerTarget.TASK_ENTITY ? Picker.ENTITY : Picker.ICON, target)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_target"));
        }));
        addJsonTaskField(left, top + 39, width, "components", "gui.theseus.editor.component_data_predicate", new JsonObject());
    }

    void addBlockInteractionTaskFields(int left, int top, int width) {
        addPredicateTargetFields(left, top, width, "block", "gui.theseus.editor.block_or_tag", "minecraft:stone", PickerTarget.TASK_BLOCK);
        addJsonTaskField(left, top + 78, width, "state", "gui.theseus.editor.block_state_predicate", new JsonObject());
    }

    void addLocationTaskFields(int left, int top, int width) {
        addJsonTaskField(left, top, width, "predicate", "gui.theseus.editor.location_predicate", defaultLocationPredicate());
        EditBox description = new EditBox(panel.font, left + 14, top + 181, width, 18, Component.translatable("gui.theseus.editor.description"));
        description.setValue(jsonString(panel.authoring.editingTask.source, "description", ""));
        description.setResponder(text -> QuestScreenEditor.setOptionalString(panel.authoring.editingTask.source, "description", text));
        panel.host.addWidget(description);
    }

    void addDimensionTaskFields(int left, int top, int width) {
        EditBox from = new EditBox(panel.font, left + 14, top + 142, 110, 18, Component.translatable("gui.theseus.editor.from_dimension"));
        from.setValue(jsonString(panel.authoring.editingTask.source, "from", ""));
        from.setResponder(text -> QuestScreenEditor.setOptionalString(panel.authoring.editingTask.source, "from", text));
        panel.host.addWidget(from);
        EditBox to = new EditBox(panel.font, left + 136, top + 142, 110, 18, Component.translatable("gui.theseus.editor.to_dimension"));
        to.setValue(jsonString(panel.authoring.editingTask.source, "to", ""));
        to.setResponder(text -> QuestScreenEditor.setOptionalString(panel.authoring.editingTask.source, "to", text));
        panel.host.addWidget(to);
    }

    void addJsonTaskField(int left, int top, int width, String key, String labelKey, JsonObject fallback) {
        EditBox field = new EditBox(panel.font, left + 14, top + 142, width, 18, QuestScreenEditor.editorText(labelKey));
        field.setMaxLength(2048);
        JsonElement current = panel.authoring.editingTask.source.get(key);
        field.setValue(current == null ? QuestAuthoringPanel.GSON.toJson(fallback) : current.isJsonPrimitive() ? current.getAsString() : QuestAuthoringPanel.GSON.toJson(current));
        field.setResponder(text -> panel.authoring.editingTask.source.addProperty(key, text));
        panel.host.addWidget(field);
    }

    void addCompositeTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 104, top + 139).withSize(142, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(
                "gui.theseus.editor.manage_children",
                nestedTasks(panel.authoring.editingTask).size()
            )));
            widget.withCallback(() -> {
                panel.authoring.nestedTaskScroll = 0;
                panel.modalHost.open(QuestModalHost.Modal.NESTED_TASKS);
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.edit_this_composite_task_s_child_tasks"));
        }));
    }

    void addNestedTaskWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        List<QuestAuthoringSession.TaskDraft> children = nestedTasks(panel.authoring.editingTask);
        int end = Math.min(children.size(), panel.authoring.nestedTaskScroll + 4);
        String breadcrumbs = panel.authoring.taskBreadcrumbs();
        for (int index = panel.authoring.nestedTaskScroll; index < end; index++) {
            int childIndex = index;
            int rowY = top + 42 + (index - panel.authoring.nestedTaskScroll) * 42;
            QuestAuthoringSession.TaskDraft child = children.get(index);
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(140, 34);
                widget.withRenderer(WidgetRenderers.text(Component.literal(child.id + "  ·  " + taskDisplayLabel(child))));
                widget.withCallback(() -> {
                    panel.authoring.editChildTask(childIndex);
                    panel.modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.active = isTaskEditable(child);
                widget.withTooltip(widget.active
                    ? QuestScreenEditor.editorText("gui.theseus.editor.edit_child_task")
                    : Component.literal(panel.unavailableReason(EditorTypeRegistry.Kind.TASK, child.type)));
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 160, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedTask(childIndex, -1));
                widget.active = childIndex > 0;
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 188, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedTask(childIndex, 1));
                widget.active = childIndex < children.size() - 1;
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 216, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    panel.authoring.removeNestedTask(childIndex);
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_child_task"));
            }));
        }
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_task")));
            widget.withCallback(() -> {
                panel.taskChooserScroll = 0;
                panel.taskChooserSelectedIndex = 0;
                if (panel.modalHost.isNestedTaskChooserOpen()) panel.modalHost.close();
                else panel.modalHost.open(QuestModalHost.Modal.NESTED_TASK_CHOOSER);
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_task_type"));
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 133, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.done")));
            widget.withCallback(() -> {
                panel.modalHost.close();
                panel.host.dispatch(new RebuildWidgets());
            });
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 14).withSize(232, 20);
            widget.withTexture(null);
            widget.withRenderer(WidgetRenderers.text(Component.literal(breadcrumbs)));
            widget.active = false;
        }));
    }

    void moveNestedTask(int index, int direction) {
        panel.authoring.moveNestedTask(index, direction);
        panel.host.dispatch(new RebuildWidgets());
    }

    void addStatTaskFields(int left, int top, int width) {
        EditBox stat = new EditBox(panel.font, left + 14, top + 142, width - 100, 18, Component.translatable("gui.theseus.editor.statistic_id"));
        stat.setValue(jsonString(panel.authoring.editingTask.source, "stat", "minecraft:jump"));
        stat.setResponder(text -> panel.authoring.editingTask.source.addProperty("stat", text));
        panel.host.addWidget(stat);
        EditBox target = new EditBox(panel.font, left + width - 76, top + 142, 76, 18, Component.translatable("gui.theseus.editor.target"));
        target.setValue(Integer.toString(jsonInt(panel.authoring.editingTask.source, "target", 1)));
        target.setResponder(text -> panel.authoring.editingTask.source.addProperty("target", QuestScreenEditor.parseInteger(text)));
        panel.host.addWidget(target);
    }

    void addAmountField(int left, int y) {
        EditBox amount = new EditBox(panel.font, left + 14, y, 82, 18, Component.translatable("gui.theseus.editor.amount"));
        amount.setValue(Integer.toString(jsonInt(panel.authoring.editingTask.source, "amount", 1)));
        amount.setResponder(text -> {
            try {
                panel.authoring.editingTask.source.addProperty("amount", Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                panel.authoring.editingTask.source.addProperty("amount", 0);
            }
        });
        panel.host.addWidget(amount);
    }

    void addCycleButton(
        int x,
        int y,
        int width,
        String key,
        String fallback,
        List<String> values
    ) {
        String current = jsonString(panel.authoring.editingTask.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        Button cycle = Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(QuestScreenEditor.cycleValueLabel(key, current)));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                panel.authoring.editingTask.source.addProperty(key, values.get((index + 1) % values.size()));
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.click_to_change"));
        });
        panel.host.addWidget(cycle);
    }

    void closeTaskEditor() {
        boolean hasParent = panel.authoring.closeTaskEditor();
        panel.host.dispatch(new ClosePicker());
        if (panel.modalHost.isOneOf(QuestModalHost.Modal.TASK_EDITOR, QuestModalHost.Modal.NESTED_TASKS)) {
            panel.modalHost.close();
        }
        if (!hasParent && panel.modalHost.is(QuestModalHost.Modal.TASK_EDITOR)) panel.modalHost.close();
        panel.host.dispatch(new RebuildWidgets());
    }

    void saveTaskEditor() {
        int previousCount = panel.authoring.tasks.size();
        if (!panel.authoring.saveTask(panel.host.registryLookup())) return;
        if (panel.authoring.tasks.size() > previousCount) panel.createTaskScroll = panel.draftUi.maxCreateTaskScroll();
        closeTaskEditor();
    }

    void drawTaskEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, panel.width, panel.height, 0x88000000);
        int left = taskEditorLeft();
        int top = taskEditorTop();
        graphics.fill(left, top, left + 260, top + 300, 0xFF20242B);
        graphics.outline(left, top, 260, 300, 0xFF8A929F);
    }

    void drawTaskEditorForeground(GuiGraphicsExtractor graphics) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        TaskChoice choice = taskChoice(panel.authoring.editingTask);
        graphics.item(new ItemStack(choice.icon()), left + 14, top + 10);
        graphics.text(panel.font, Component.translatable(
            "gui.theseus.editor.edit_task_type",
            QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.TASK, panel.authoring.editingTask.type, choice.label())
        ), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.id"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.title_override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.icon_override"), left + QuestAuthoringPanel.TASK_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftTaskIcon(graphics, panel.authoring.editingTask, left + 23, top + 105);
        switch (panel.authoring.editingTask.type) {
            case "theseus:dummy" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.trigger_value"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.description"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.item_or_tag"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.collection"), left + 104, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:xp" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.unit"), left + 104, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.collection"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:kill_entity" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.entity"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:advancement" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.advancement_ids_comma_separated", null);
            case "theseus:biome" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.biome_or_tag", null);
            case "theseus:block_interaction" -> {
                taskFieldLabel(graphics, left, top, "gui.theseus.editor.block_or_tag", "gui.theseus.editor.component_data_predicate_json");
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.block_state_predicate_json"), left + 14, top + 209, 0xFFB8C0CC, false);
            }
            case "theseus:changed_dimension" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.from_dimension_optional"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.to_dimension_optional"), left + 136, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:check" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.player_data_predicate_json", null);
            case "theseus:composite" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.required_tasks"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.nested_tasks"), left + 104, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:entity_interaction" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.entity_or_tag", "gui.theseus.editor.component_data_predicate_json");
            case "theseus:item_interaction", "theseus:item_use" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.item_or_tag", "gui.theseus.editor.component_data_predicate_json");
            case "theseus:location" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.location_predicate_json", "gui.theseus.editor.description");
            case "theseus:recipe" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.recipe_ids_comma_separated", null);
            case "theseus:stat" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.statistic_id"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.target"), left + 170, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:structure" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.structure_or_tag", null);
            default -> {
            }
        }
        if (!panel.authoring.taskEditorError.isEmpty()) graphics.textWithWordWrap(
            panel.font,
            Component.literal(panel.authoring.taskEditorError),
            left + 14,
            top + 245,
            232,
            0xFFFF7777,
            false
        );
    }

    void drawNestedTasksForeground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY
    ) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        if (nestedTasks(panel.authoring.editingTask).isEmpty()) {
            graphics.text(
                panel.font,
                Component.translatable("gui.theseus.editor.no_child_tasks_yet"),
                left + 14,
                top + 48,
                0xFF8E98A6,
                false
            );
        }
        if (panel.modalHost.isNestedTaskChooserOpen()) panel.draftUi.drawTaskChooser(graphics, mouseX, mouseY, true);
    }

    void taskFieldLabel(GuiGraphicsExtractor graphics, int left, int top, String firstKey, String secondKey) {
        graphics.text(panel.font, QuestScreenEditor.editorText(firstKey), left + 14, top + 131, 0xFFB8C0CC, false);
        if (secondKey != null) graphics.text(panel.font, QuestScreenEditor.editorText(secondKey), left + 14, top + 170, 0xFFB8C0CC, false);
    }

    int taskEditorLeft() {
        return (panel.width - 260) / 2;
    }

    int taskEditorTop() {
        return (panel.height - 300) / 2;
    }

    void renderDraftTaskIcon(GuiGraphicsExtractor graphics, QuestAuthoringSession.TaskDraft draft, int x, int y) {
        QuestDefinition.Task parsed = QuestDefinition.parse("editor", taskRoot(draft)).tasks().get(draft.id);
        if (parsed == null) graphics.item(new ItemStack(taskDisplayIcon(draft)), x, y);
        else QuestPresentation.renderTaskIcon(graphics, parsed, x, y);
    }

    boolean isTaskEditable(QuestAuthoringSession.TaskDraft task) {
        return panel.editorResolution(EditorTypeRegistry.Kind.TASK, task.type).editable();
    }

    String taskDisplayLabel(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task)
            ? QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.TASK, task.type, taskChoice(task).label()).getString()
            : Component.translatable("gui.theseus.editor.unsupported_type", task.type).getString();
    }

    Item taskDisplayIcon(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task) ? taskChoice(task).icon() : Items.BARRIER;
    }
}
