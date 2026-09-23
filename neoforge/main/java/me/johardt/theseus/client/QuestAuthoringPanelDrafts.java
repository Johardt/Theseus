package me.johardt.theseus.client;

import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.List;
import me.johardt.theseus.client.QuestAuthoringSession.RewardDraft;
import me.johardt.theseus.client.QuestAuthoringSession.TaskDraft;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import me.johardt.theseus.core.EditorTypeRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestAuthoringPanel.*;

/** Builds draft task and reward lists and their type choosers. */
final class QuestAuthoringPanelDrafts {
    private final QuestAuthoringPanel panel;

    QuestAuthoringPanelDrafts(QuestAuthoringPanel panel) {
        this.panel = panel;
    }

    void addDraftTaskWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(panel.authoring.tasks.size(), panel.createTaskScroll + taskListCapacity());
        for (int index = panel.createTaskScroll; index < end; index++) {
            int taskIndex = index;
            int cardY = y + (index - panel.createTaskScroll) * 48;
            int actionY = cardY + (42 - QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT) / 2;
            int deleteX = x + width - QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH - 8;
            int editX = deleteX - 4 - QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH;
            Button edit = Widgets.button(widget -> {
                widget.withPosition(editX, actionY).withSize(QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH, QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(panel.listActionRenderer("edit"));
                widget.withCallback(() -> panel.taskEditor.openTaskEditor(taskIndex));
                widget.active = panel.taskEditor.isTaskEditable(panel.authoring.tasks.get(taskIndex));
                widget.withTooltip(widget.active
                    ? QuestScreenEditor.editorText("gui.theseus.editor.edit_task")
                    : Component.literal(panel.unavailableReason(EditorTypeRegistry.Kind.TASK, panel.authoring.tasks.get(taskIndex).type)));
            });
            panel.host.addWidget(edit);
            Button delete = Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH, QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(panel.listActionRenderer("delete"));
                widget.withCallback(() -> {
                    panel.authoring.taskDeleteConfirmation = taskIndex;
                    panel.modalHost.open(QuestModalHost.Modal.TASK_DELETE_CONFIRMATION);
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_task"));
            });
            panel.host.addWidget(delete);
        }
        int addIndex = panel.authoring.tasks.size();
        if (addIndex >= panel.createTaskScroll && addIndex < panel.createTaskScroll + taskListCapacity()) {
            int addY = y + (addIndex - panel.createTaskScroll) * 48;
            Button add = Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_task")));
                widget.withCallback(() -> {
                    panel.taskChooserScroll = 0;
                    panel.taskChooserSelectedIndex = 0;
                    if (panel.modalHost.isTaskChooserOpen()) panel.modalHost.close();
                    else panel.modalHost.open(QuestModalHost.Modal.TASK_CHOOSER);
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_task_type"));
            });
            panel.host.addWidget(add);
        }
    }

    void addDraftRewardWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(panel.authoring.rewards.size(), panel.createRewardScroll + rewardListCapacity());
        for (int index = panel.createRewardScroll; index < end; index++) {
            int rewardIndex = index;
            int cardY = y + (index - panel.createRewardScroll) * 48;
            int actionY = cardY + (42 - QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT) / 2;
            int deleteX = x + width - QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH - 8;
            int editX = deleteX - 4 - QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH;
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(editX, actionY).withSize(QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH, QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(panel.listActionRenderer("edit"));
                widget.withCallback(() -> panel.rewardEditor.openRewardEditor(rewardIndex));
                widget.active = panel.rewardEditor.isRewardEditable(panel.authoring.rewards.get(rewardIndex));
                widget.withTooltip(widget.active
                    ? QuestScreenEditor.editorText("gui.theseus.editor.edit_reward")
                    : Component.literal(panel.unavailableReason(EditorTypeRegistry.Kind.REWARD, panel.authoring.rewards.get(rewardIndex).type)));
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(QuestAuthoringPanel.EDITOR_LIST_ACTION_WIDTH, QuestAuthoringPanel.EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(panel.listActionRenderer("delete"));
                widget.withCallback(() -> {
                    panel.authoring.removeReward(rewardIndex);
                    panel.createRewardScroll = Math.min(panel.createRewardScroll, maxCreateRewardScroll());
                    if (panel.modalHost.isRewardChooserOpen()) panel.modalHost.close();
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_reward"));
            }));
        }
        int addIndex = panel.authoring.rewards.size();
        if (addIndex >= panel.createRewardScroll && addIndex < panel.createRewardScroll + rewardListCapacity()) {
            int addY = y + (addIndex - panel.createRewardScroll) * 48;
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_reward")));
                widget.withCallback(() -> {
                    panel.rewardChooserScroll = 0;
                    panel.rewardChooserSelectedIndex = 0;
                    if (panel.modalHost.isRewardChooserOpen()) panel.modalHost.close();
                    else panel.modalHost.open(QuestModalHost.Modal.REWARD_CHOOSER);
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_reward_type"));
            }));
        }
    }

    int rewardListCapacity() {
        return Math.max(1, (panel.height - 79) / 48);
    }

    int maxCreateRewardScroll() {
        return Math.max(0, panel.authoring.rewards.size() + 1 - rewardListCapacity());
    }

    int taskListCapacity() {
        return Math.max(1, (panel.height - 79) / 48);
    }

    int maxCreateTaskScroll() {
        return Math.max(0, panel.authoring.tasks.size() + 1 - taskListCapacity());
    }

    void drawDraftRewards(GuiGraphicsExtractor graphics) {
        int x = panel.width - panel.host.detailsWidth() + 12;
        int cardWidth = panel.host.detailsWidth() - 24;
        int y = 43;
        int end = Math.min(panel.authoring.rewards.size(), panel.createRewardScroll + rewardListCapacity());
        for (int index = panel.createRewardScroll; index < end; index++) {
            int cardY = y + (index - panel.createRewardScroll) * 48;
            QuestAuthoringSession.RewardDraft reward = panel.authoring.rewards.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            panel.rewardEditor.renderDraftRewardIcon(graphics, reward, x + 7, cardY + 13);
            panel.drawClippedText(graphics, panel.rewardEditor.rewardDisplayLabel(reward), x + 29, cardY + 9, cardWidth - 108, panel.rewardEditor.isRewardEditable(reward) ? 0xFFFFFFFF : 0xFFFFAA77);
            panel.drawClippedText(graphics, reward.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (panel.authoring.rewards.isEmpty()) graphics.text(panel.font, Component.translatable("gui.theseus.editor.no_rewards_yet"), x, 34, 0xFF8E98A6, false);
    }

    void drawRewardChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean nested) {
        int left = nested ? panel.rewardEditor.rewardEditorLeft() + 24 : panel.width - panel.host.detailsWidth() + 16;
        int top = nested ? panel.rewardEditor.rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : panel.host.detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        int chooserHeight = choices.size() * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int index = 0; index < choices.size(); index++) {
            RewardChoice choice = choices.get(index);
            int rowY = top + 2 + index * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 && mouseY >= rowY && mouseY < rowY + QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            if (index == panel.rewardChooserSelectedIndex) {
                graphics.outline(left + 2, rowY, chooserWidth - 4, QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1, ClientThemeLoader.active().genericControls().accent());
            }
            graphics.item(new ItemStack(choice.icon()), left + 4, rowY + 5);
            graphics.text(panel.font, QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.REWARD, choice.type(), choice.label()), left + 24, rowY + 9, 0xFFFFFFFF, false);
        }
    }

    void drawDraftTasks(GuiGraphicsExtractor graphics) {
        int x = panel.width - panel.host.detailsWidth() + 12;
        int cardWidth = panel.host.detailsWidth() - 24;
        int y = 43;
        int end = Math.min(panel.authoring.tasks.size(), panel.createTaskScroll + taskListCapacity());
        for (int index = panel.createTaskScroll; index < end; index++) {
            int cardY = y + (index - panel.createTaskScroll) * 48;
            QuestAuthoringSession.TaskDraft task = panel.authoring.tasks.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            panel.taskEditor.renderDraftTaskIcon(graphics, task, x + 7, cardY + 13);
            panel.drawClippedText(graphics, panel.taskEditor.taskDisplayLabel(task), x + 29, cardY + 9, cardWidth - 108, panel.taskEditor.isTaskEditable(task) ? 0xFFFFFFFF : 0xFFFFAA77);
            panel.drawClippedText(graphics, task.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (panel.authoring.tasks.isEmpty()) {
            graphics.text(
                panel.font,
                Component.translatable("gui.theseus.editor.no_tasks_yet"),
                x,
                34,
                0xFF8E98A6,
                false
            );
        }
    }

    void drawTaskChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawTaskChooser(graphics, mouseX, mouseY, false);
    }

    void drawTaskChooser(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        boolean nested
    ) {
        int left = nested ? panel.taskEditor.taskEditorLeft() + 14 : panel.width - panel.host.detailsWidth() + 16;
        int top = nested ? panel.taskEditor.taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : panel.host.detailsWidth() - 32;
        int visibleCount = Math.min(QuestAuthoringPanel.TASK_CHOOSER_VISIBLE, tasks().size() - panel.taskChooserScroll);
        int chooserHeight = visibleCount * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int visible = 0; visible < visibleCount; visible++) {
            TaskChoice choice = tasks().get(panel.taskChooserScroll + visible);
            int rowY = top + 2 + visible * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 &&
                mouseY >= rowY && mouseY < rowY + QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            if (panel.taskChooserScroll + visible == panel.taskChooserSelectedIndex) {
                graphics.outline(left + 2, rowY, chooserWidth - 4, QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT - 1, ClientThemeLoader.active().genericControls().accent());
            }
            graphics.item(new ItemStack(choice.icon()), left + 4, rowY + 5);
            graphics.text(
                panel.font,
                QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.TASK, choice.type(), choice.label()),
                left + 24,
                rowY + (choice.implemented() ? 9 : 3),
                choice.implemented() ? 0xFFFFFFFF : 0xFF9AA2AE,
                false
            );
            if (!choice.implemented()) graphics.text(
                panel.font,
                Component.translatable("gui.theseus.editor.not_yet_implemented"),
                left + 24,
                rowY + 14,
                0xFF9AA4B2,
                false
            );
        }
    }

    boolean handleChooserKey(KeyEvent event) {
        boolean taskChooser = panel.modalHost.isTaskChooserOpen() || panel.modalHost.isNestedTaskChooserOpen();
        boolean rewardChooser = panel.modalHost.isRewardChooserOpen() || panel.modalHost.isNestedRewardChooserOpen();
        if (!taskChooser && !rewardChooser) return false;
        int direction = switch (event.key()) {
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_DOWN -> 1;
            case InputConstants.KEY_TAB -> event.hasShiftDown() ? -1 : 1;
            default -> 0;
        };
        if (direction != 0) {
            if (taskChooser) {
                panel.taskChooserSelectedIndex = Math.floorMod(panel.taskChooserSelectedIndex + direction, tasks().size());
                if (panel.taskChooserSelectedIndex < panel.taskChooserScroll) panel.taskChooserScroll = panel.taskChooserSelectedIndex;
                else if (panel.taskChooserSelectedIndex >= panel.taskChooserScroll + QuestAuthoringPanel.TASK_CHOOSER_VISIBLE) {
                    panel.taskChooserScroll = panel.taskChooserSelectedIndex - QuestAuthoringPanel.TASK_CHOOSER_VISIBLE + 1;
                }
            } else {
                int choiceCount = panel.modalHost.isNestedRewardChooserOpen() ? rewards().size() - 1 : rewards().size();
                panel.rewardChooserSelectedIndex = Math.floorMod(panel.rewardChooserSelectedIndex + direction, choiceCount);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (taskChooser) chooseTask(panel.taskChooserSelectedIndex, panel.modalHost.isNestedTaskChooserOpen());
            else chooseReward(panel.rewardChooserSelectedIndex, panel.modalHost.isNestedRewardChooserOpen());
            return true;
        }
        return false;
    }

    void chooseTask(int index, boolean nested) {
        if (index < 0 || index >= tasks().size()) return;
        TaskChoice choice = tasks().get(index);
        if (!choice.implemented()) return;
        QuestAuthoringSession.TaskDraft previousTask = panel.authoring.editingTask;
        if (nested) addNestedDraftTask(choice);
        else addDraftTask(choice);
        if (panel.authoring.editingTask != previousTask) {
            panel.modalHost.close();
            panel.modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
        }
        panel.host.dispatch(new RebuildWidgets());
    }

    void chooseReward(int index, boolean nested) {
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        if (index < 0 || index >= choices.size()) return;
        addDraftReward(choices.get(index), nested);
        panel.host.dispatch(new RebuildWidgets());
    }

    boolean taskChooserClicked(MouseButtonEvent event) {
        return taskChooserClicked(event, false);
    }

    boolean taskChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? panel.taskEditor.taskEditorLeft() + 14 : panel.width - panel.host.detailsWidth() + 16;
        int top = nested ? panel.taskEditor.taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : panel.host.detailsWidth() - 32;
        int visibleCount = Math.min(QuestAuthoringPanel.TASK_CHOOSER_VISIBLE, tasks().size() - panel.taskChooserScroll);
        int chooserHeight = visibleCount * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth ||
            event.y() < top || event.y() >= top + chooserHeight) {
            panel.modalHost.close();
            panel.host.dispatch(new RebuildWidgets());
            return true;
        }
        int row = (int) (event.y() - top - 2) / QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < visibleCount) {
            panel.taskChooserSelectedIndex = panel.taskChooserScroll + row;
            chooseTask(panel.taskChooserSelectedIndex, nested);
        }
        return true;
    }

    boolean rewardChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? panel.rewardEditor.rewardEditorLeft() + 24 : panel.width - panel.host.detailsWidth() + 16;
        int top = nested ? panel.rewardEditor.rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : panel.host.detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        int chooserHeight = choices.size() * QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth || event.y() < top || event.y() >= top + chooserHeight) {
            panel.modalHost.close();
            panel.host.dispatch(new RebuildWidgets());
            return true;
        }
        int row = (int) (event.y() - top - 2) / QuestAuthoringPanel.TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < choices.size()) {
            panel.rewardChooserSelectedIndex = row;
            chooseReward(panel.rewardChooserSelectedIndex, nested);
        }
        return true;
    }

    void addDraftTask(TaskChoice choice) {
        QuestAuthoringSession.TaskDraft task = createTaskDraft(choice, panel.authoring.tasks);
        if (task == null) return;
        panel.authoring.createTask(task);
    }

    void addNestedDraftTask(TaskChoice choice) {
        QuestAuthoringSession.TaskDraft task = createTaskDraft(choice, nestedTasks(panel.authoring.editingTask));
        if (task == null) return;
        panel.authoring.createChildTask(task);
    }

    QuestAuthoringSession.TaskDraft createTaskDraft(
        TaskChoice choice,
        List<QuestAuthoringSession.TaskDraft> siblings
    ) {
        EditorTypeRegistry.Descriptor descriptor = EditorTypeRegistry.registered().resolve(EditorTypeRegistry.Kind.TASK, choice.type());
        if (!descriptor.editable()) {
            panel.host.dispatch(new ShowMessage(descriptor.availabilityReason()));
            return null;
        }
        return QuestEditorCatalog.createTaskDraft(choice, siblings);
    }

    void addDraftReward(RewardChoice choice, boolean nested) {
        EditorTypeRegistry.Descriptor descriptor = EditorTypeRegistry.registered().resolve(EditorTypeRegistry.Kind.REWARD, choice.type());
        if (!descriptor.editable()) {
            panel.host.dispatch(new ShowMessage(descriptor.availabilityReason()));
            return;
        }
        List<QuestAuthoringSession.RewardDraft> rewards = nested ? nestedRewards(panel.authoring.editingReward) : panel.authoring.rewards;
        QuestAuthoringSession.RewardDraft reward = QuestEditorCatalog.createRewardDraft(choice, rewards);
        if (nested) {
            panel.authoring.createNestedReward(reward);
            panel.modalHost.close();
            panel.modalHost.open(QuestModalHost.Modal.NESTED_REWARD_EDITOR);
        } else {
            panel.authoring.createReward(reward);
            panel.modalHost.close();
            panel.modalHost.open(QuestModalHost.Modal.REWARD_EDITOR);
        }
    }
}
