package me.johardt.theseus.client;

import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.List;
import me.johardt.theseus.client.QuestAuthoringSession.RewardDraft;
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

/** Builds and renders reward editors and nested selectable rewards. */
final class QuestAuthoringPanelRewardEditor {
    private final QuestAuthoringPanel panel;

    QuestAuthoringPanelRewardEditor(QuestAuthoringPanel panel) {
        this.panel = panel;
    }

    void openRewardEditor(int index) {
        if (!isRewardEditable(panel.authoring.rewards.get(index))) {
            panel.host.dispatch(new ShowMessage(panel.unavailableReason(EditorTypeRegistry.Kind.REWARD, panel.authoring.rewards.get(index).type) + ". It is preserved read-only."));
            return;
        }
        panel.authoring.editReward(index);
        panel.modalHost.open(QuestModalHost.Modal.REWARD_EDITOR);
        panel.host.dispatch(new RebuildWidgets());
    }

    void addRewardEditorWidgets(QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        int width = 272;
        EditBox id = new EditBox(panel.font, left + 14, top + 38, width, 18, Component.translatable("gui.theseus.editor.reward_id"));
        id.setValue(reward.id);
        id.setResponder(value -> reward.id = value);
        panel.host.addWidget(id);
        EditBox title = new EditBox(panel.font, left + 14, top + 70, width, 18, Component.translatable("gui.theseus.editor.reward_title"));
        title.setValue(jsonString(reward.source, "title", ""));
        title.setResponder(value -> QuestScreenEditor.setOptionalString(reward.source, "title", value));
        panel.host.addWidget(title);
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.REWARD_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_reward_icon_override"));
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                reward.source.remove("icon");
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.use_the_default_reward_icon"));
        }));
        panel.addRawInspectorButton(left + QuestAuthoringPanel.REWARD_RAW_INSPECTOR_X, top + 101, 88, () -> panel.host.dispatch(new OpenRawInspector("Reward: " + reward.id, reward.source)));
        switch (reward.type) {
            case "theseus:xp" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRewardCycleButton(reward, left + 114, top + 139, 172, "xptype", "level", List.of("level", "points"));
            }
            case "theseus:item" -> {
                EditBox item = new EditBox(panel.font, left + 14, top + 142, 210, 18, Component.translatable("gui.theseus.editor.item"));
                item.setValue(rewardItemId(reward.source));
                item.setResponder(value -> setRewardItem(reward.source, value, rewardItemCount(reward.source)));
                panel.host.addWidget(item);
                panel.host.addWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 232, top + 139).withSize(54, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                    widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.REWARD_ITEM)));
                    widget.withTooltip(Component.translatable("gui.theseus.editor.choose_item"));
                }));
                addRewardAmountField(reward, left + 14, top + 181, 92, "item.count");
            }
            case "theseus:loottable" -> addRewardTextField(reward, left, top, "loot_table", "gui.theseus.editor.loot_table");
            case "theseus:command" -> addRewardTextField(reward, left, top, "command", "gui.theseus.editor.command");
            case "theseus:selectable" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                panel.host.addWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 114, top + 139).withSize(172, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable(
                        "gui.theseus.editor.manage_choices",
                        nestedRewards(reward).size()
                    )));
                    widget.withCallback(() -> {
                        panel.authoring.rewardEditorError = "";
                        panel.modalHost.open(QuestModalHost.Modal.NESTED_REWARDS);
                        panel.host.dispatch(new RebuildWidgets());
                    });
                }));
            }
            default -> { }
        }
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> panel.host.dispatch(new RequestModalDiscard(() -> closeRewardEditor(nested))));
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 158, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save_reward")));
            widget.withCallback(() -> saveRewardEditor(nested));
        }));
    }

    void addRewardTextField(QuestAuthoringSession.RewardDraft reward, int left, int top, String key, String labelKey) {
        EditBox field = new EditBox(panel.font, left + 14, top + 142, 272, 18, QuestScreenEditor.editorText(labelKey));
        field.setValue(jsonString(reward.source, key, ""));
        field.setResponder(value -> reward.source.addProperty(key, value));
        panel.host.addWidget(field);
    }

    void addRewardAmountField(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String path) {
        int current = path.equals("item.count") ? rewardItemCount(reward.source) : jsonInt(reward.source, path, 1);
        EditBox amount = new EditBox(panel.font, x, y, width, 18, Component.translatable("gui.theseus.editor.amount"));
        amount.setValue(Integer.toString(current));
        amount.setResponder(value -> {
            int parsed;
            try { parsed = Integer.parseInt(value); } catch (NumberFormatException ignored) { parsed = 0; }
            if (path.equals("item.count")) setRewardItem(reward.source, rewardItemId(reward.source), parsed);
            else reward.source.addProperty(path, parsed);
        });
        panel.host.addWidget(amount);
    }

    void addRewardCycleButton(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String key, String fallback, List<String> values) {
        String current = jsonString(reward.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(QuestScreenEditor.cycleValueLabel(key, current)));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                reward.source.addProperty(key, values.get((index + 1) % values.size()));
                panel.host.dispatch(new RebuildWidgets());
            });
        }));
    }

    void closeRewardEditor(boolean nested) {
        panel.authoring.closeRewardEditor(nested);
        panel.host.dispatch(new ClosePicker());
        if (panel.modalHost.is(QuestModalHost.Modal.PICKER)) panel.modalHost.close();
        if (nested) {
            if (panel.modalHost.is(QuestModalHost.Modal.NESTED_REWARD_EDITOR)) panel.modalHost.close();
        } else {
            if (panel.modalHost.is(QuestModalHost.Modal.NESTED_REWARDS)) panel.modalHost.close();
            if (panel.modalHost.is(QuestModalHost.Modal.REWARD_EDITOR)) panel.modalHost.close();
        }
        panel.host.dispatch(new RebuildWidgets());
    }

    void saveRewardEditor(boolean nested) {
        int previousCount = panel.authoring.rewards.size();
        if (!panel.authoring.saveReward(nested, panel.host.registryLookup())) return;
        if (panel.authoring.rewards.size() > previousCount) panel.createRewardScroll = panel.draftUi.maxCreateRewardScroll();
        closeRewardEditor(nested);
    }

    void addNestedRewardWidgets() {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        List<QuestAuthoringSession.RewardDraft> rewards = nestedRewards(panel.authoring.editingReward);
        int end = Math.min(rewards.size(), panel.authoring.nestedRewardScroll + 4);
        for (int index = panel.authoring.nestedRewardScroll; index < end; index++) {
            int nestedIndex = index;
            int rowY = top + 42 + (index - panel.authoring.nestedRewardScroll) * 42;
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(180, 34);
                widget.withRenderer(WidgetRenderers.text(Component.translatable(
                    "gui.theseus.editor.nested_reward_label",
                    rewards.get(nestedIndex).id,
                    QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.REWARD, rewards.get(nestedIndex).type, rewardChoice(rewards.get(nestedIndex)).label())
                )));
                widget.withCallback(() -> {
                    panel.authoring.editNestedReward(nestedIndex);
                    panel.modalHost.open(QuestModalHost.Modal.NESTED_REWARD_EDITOR);
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.active = isRewardEditable(rewards.get(nestedIndex));
                widget.withTooltip(widget.active
                    ? QuestScreenEditor.editorText("gui.theseus.editor.edit_choice")
                    : Component.literal(panel.unavailableReason(EditorTypeRegistry.Kind.REWARD, rewards.get(nestedIndex).type)));
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 200, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, -1));
                widget.active = nestedIndex > 0;
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 228, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, 1));
                widget.active = nestedIndex < rewards.size() - 1;
            }));
            panel.host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 256, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    panel.authoring.removeNestedReward(nestedIndex);
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_choice"));
            }));
        }
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_choice")));
            widget.withCallback(() -> {
                panel.rewardChooserSelectedIndex = 0;
                if (panel.modalHost.isNestedRewardChooserOpen()) panel.modalHost.close();
                else panel.modalHost.open(QuestModalHost.Modal.NESTED_REWARD_CHOOSER);
                panel.host.dispatch(new RebuildWidgets());
            });
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 148, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.done")));
            widget.withCallback(() -> {
                panel.modalHost.close();
                panel.host.dispatch(new RebuildWidgets());
            });
        }));
        panel.host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 14).withSize(272, 20);
            widget.withTexture(null);
            widget.withRenderer(WidgetRenderers.text(Component.literal(panel.authoring.editingReward.id)));
            widget.active = false;
        }));
    }

    void moveNestedReward(int index, int direction) {
        panel.authoring.moveNestedReward(index, direction);
        panel.host.dispatch(new RebuildWidgets());
    }

    void drawRewardEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, panel.width, panel.height, 0x88000000);
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        graphics.fill(left, top, left + 300, top + 280, 0xFF20242B);
        graphics.outline(left, top, 300, 280, 0xFF8A929F);
    }

    void drawRewardModalForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (panel.authoring.editingNestedReward != null) drawRewardEditorForeground(graphics, panel.authoring.editingNestedReward, true);
        else if (panel.modalHost.showsNestedRewards()) drawNestedRewardsForeground(graphics, mouseX, mouseY);
        else drawRewardEditorForeground(graphics, panel.authoring.editingReward, false);
    }

    void drawRewardEditorForeground(GuiGraphicsExtractor graphics, QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        RewardChoice choice = rewardChoice(reward);
        graphics.item(new ItemStack(choice.icon()), left + 14, top + 10);
        graphics.text(panel.font, Component.translatable(
            nested ? "gui.theseus.editor.edit_reward_choice" : "gui.theseus.editor.edit_reward_type",
            QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.REWARD, reward.type, choice.label())
        ), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.id"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.title_override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(panel.font, Component.translatable("gui.theseus.editor.icon_override"), left + QuestAuthoringPanel.REWARD_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftRewardIcon(graphics, reward, left + 23, top + 105);
        switch (reward.type) {
            case "theseus:xp" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.unit"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.item"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:loottable" -> graphics.text(panel.font, Component.translatable("gui.theseus.editor.loot_table"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:command" -> graphics.text(panel.font, Component.translatable("gui.theseus.editor.command"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:selectable" -> {
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.selection_amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(panel.font, Component.translatable("gui.theseus.editor.nested_rewards"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            default -> { }
        }
        if (!panel.authoring.rewardEditorError.isEmpty()) graphics.textWithWordWrap(panel.font, Component.literal(panel.authoring.rewardEditorError), left + 14, top + 210, 272, 0xFFFF7777, false);
    }

    void drawNestedRewardsForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        if (nestedRewards(panel.authoring.editingReward).isEmpty()) graphics.text(panel.font, Component.translatable("gui.theseus.editor.no_choices_yet"), left + 14, top + 48, 0xFF8E98A6, false);
        if (panel.modalHost.isNestedRewardChooserOpen()) panel.draftUi.drawRewardChooser(graphics, mouseX, mouseY, true);
    }

    int rewardEditorLeft() {
        return (panel.width - 300) / 2;
    }

    int rewardEditorTop() {
        return (panel.height - 280) / 2;
    }

    void renderDraftRewardIcon(GuiGraphicsExtractor graphics, QuestAuthoringSession.RewardDraft draft, int x, int y) {
        QuestDefinition.Reward parsed = QuestDefinition.parse("editor", rewardRoot(draft)).rewards().get(draft.id);
        if (parsed == null) graphics.item(new ItemStack(rewardDisplayIcon(draft)), x, y);
        else QuestPresentation.renderRewardIcon(graphics, parsed, x, y);
    }

    boolean isRewardEditable(QuestAuthoringSession.RewardDraft reward) {
        return panel.editorResolution(EditorTypeRegistry.Kind.REWARD, reward.type).editable();
    }

    String rewardDisplayLabel(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward)
            ? QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.REWARD, reward.type, rewardChoice(reward).label()).getString()
            : Component.translatable("gui.theseus.editor.unsupported_type", reward.type).getString();
    }

    Item rewardDisplayIcon(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward) ? rewardChoice(reward).icon() : Items.BARRIER;
    }
}
