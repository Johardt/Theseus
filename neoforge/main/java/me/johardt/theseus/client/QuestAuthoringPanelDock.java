package me.johardt.theseus.client;

import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.base.BaseWidget;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import earth.terrarium.olympus.client.components.string.TextWidget;
import com.teamresourceful.resourcefullib.common.color.Color;
import me.johardt.theseus.client.QuestScreen.Picker;
import me.johardt.theseus.client.QuestScreen.PickerTarget;
import me.johardt.theseus.client.QuestScreen.DetailTab;
import me.johardt.theseus.core.QuestDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.GridLayout;
import earth.terrarium.olympus.client.components.compound.LayoutWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.TriState;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestAuthoringPanel.*;

/** Builds and renders the new-quest dock and its overview controls. */
final class QuestAuthoringPanelDock {
    private final QuestAuthoringPanel panel;

    QuestAuthoringPanelDock(QuestAuthoringPanel panel) {
        this.panel = panel;
    }

    void addCreateQuestDockWidgets() {
        int detailsLeft = panel.width - panel.host.detailsWidth();
        int pinLeft = panel.width - 50;
        int tabRight = pinLeft - 4;
        int tabWidth = (tabRight - (detailsLeft + 8)) / DetailTab.values().length;
        for (int index = 0; index < DetailTab.values().length; index++) {
            DetailTab tab = DetailTab.values()[index];
            int tabX = detailsLeft + 8 + index * tabWidth;
            Button tabButton = Widgets.button(widget -> {
                widget.withPosition(tabX, 8).withSize(tabWidth - 3, 20);
                widget.withRenderer(WidgetRenderers.text(
                    Component.translatable(tab.translationKey)
                ).withColor(Color.parse(
                    tab == panel.createQuestTab ? "#5A4300" : "#FFFFFF"
                )));
                widget.withCallback(() -> {
                    panel.createQuestTab = tab;
                    panel.host.dispatch(new ClosePicker());
                    if (panel.modalHost.isOneOf(QuestModalHost.Modal.TASK_CHOOSER, QuestModalHost.Modal.REWARD_CHOOSER)
                        || panel.modalHost.is(QuestModalHost.Modal.PICKER)) {
                        panel.modalHost.close();
                    }
                    panel.host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable(tab.translationKey));
            });
            panel.host.addWidget(tabButton);
        }
        Button close = Widgets.button(widget -> {
            widget.withPosition(panel.width - 27, 8).withSize(19, 20);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(QuestAuthoringPanel.CLOSE_BUTTON)
            ));
            widget.withCallback(() -> {
                panel.host.dispatch(new RequestDiscard(() -> {
                    panel.host.dispatch(new CloseDraft());
                    panel.host.dispatch(new ClearSelectedQuest());
                    panel.host.dispatch(new RebuildWidgets());
                }));
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.close_new_quest"));
        });
        panel.host.addWidget(close);

        int x = detailsLeft + 12;
        int fieldWidth = panel.host.detailsWidth() - 24;
        panel.createConfirmButton = Widgets.button(widget -> {
            widget.withPosition(x, panel.height - 30).withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(panel.authoring.editingExisting ? "gui.theseus.editor.save_quest" : "gui.theseus.editor.create_quest")));
            widget.withCallback(() -> panel.host.dispatch(new ConfirmCreateQuest()));
            String error = panel.host.draftValidationError();
            widget.withTooltip(panel.host.mutationPending()
                ? Component.translatable("gui.theseus.editor.waiting_for_the_server")
                : error.isEmpty() ? Component.translatable("gui.theseus.editor.save_this_quest") : Component.literal(error));
        });
        updateCreateConfirmButton();
        panel.host.addWidget(panel.createConfirmButton);

        if (panel.createQuestTab == DetailTab.TASKS) {
            panel.draftUi.addDraftTaskWidgets(x, fieldWidth);
            return;
        }
        if (panel.createQuestTab == DetailTab.REWARDS) {
            panel.draftUi.addDraftRewardWidgets(x, fieldWidth);
            return;
        }
        if (panel.createQuestTab != DetailTab.OVERVIEW) return;
        // Reserve room for the scroll rail so fields never sit underneath it.
        addOverviewDockWidgets(x, fieldWidth - 8);
    }

    void addOverviewDockWidgets(int x, int fieldWidth) {
        GridLayout layout = new GridLayout().rowSpacing(4);
        int row = 0;
        layout.addChild(dockLabel("gui.theseus.editor.id", fieldWidth), row++, 0);
        EditBox id = new EditBox(panel.font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_id"));
        id.setValue(panel.authoring.id);
        id.setResponder(value -> {
            panel.authoring.id = value;
            updateCreateConfirmButton();
        });
        layout.addChild(id, row++, 0);

        layout.addChild(dockLabel("gui.theseus.editor.title", fieldWidth), row++, 0);
        EditBox title = new EditBox(panel.font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_title"));
        title.setValue(panel.authoring.title);
        title.setResponder(value -> {
            panel.authoring.title = value;
            updateCreateConfirmButton();
        });
        layout.addChild(title, row++, 0);

        layout.addChild(dockLabel("gui.theseus.editor.subtitle", fieldWidth), row++, 0);
        EditBox subtitle = new EditBox(panel.font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_subtitle"));
        subtitle.setValue(panel.authoring.subtitle);
        subtitle.setResponder(value -> panel.authoring.subtitle = value);
        layout.addChild(subtitle, row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.description", fieldWidth), row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 32);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(panel.authoring.body.isBlank()
                ? "gui.theseus.editor.write_rich_description"
                : "gui.theseus.editor.edit_rich_description")));
            widget.withCallback(() -> panel.host.dispatch(new OpenDescriptionEditor()));
            widget.withTooltip(Component.translatable("gui.theseus.editor.markdown_editor_with_live_player_preview"));
        }), row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.appearance", fieldWidth), row++, 0);
        GridLayout appearance = new GridLayout().columnSpacing(6);
        Button icon = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.choose_icon")));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.QUEST_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_quest_icon"));
        });
        Button background = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.choose_background")));
            widget.withCallback(() -> panel.host.dispatch(new OpenPicker(Picker.BACKGROUND, PickerTarget.QUEST_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_quest_background"));
        });
        appearance.addChild(icon, 0, 0);
        appearance.addChild(background, 0, 1);
        layout.addChild(appearance, row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.icon_size_range", fieldWidth), row++, 0);
        GridLayout iconSize = new GridLayout().columnSpacing(6);
        Button decreaseIconSize = Widgets.button(widget -> {
            widget.withSize(28, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("−")));
            widget.active = panel.authoring.iconSize > QuestSurfaceLayout.MIN_ICON_SIZE;
            widget.withCallback(() -> adjustCreateQuestIconSize(-1));
            widget.withTooltip(Component.translatable("gui.theseus.editor.decrease_icon_size"));
        });
        EditBox iconSizeField = new EditBox(panel.font, 0, 0, Math.max(44, fieldWidth - 68), 18, Component.translatable("gui.theseus.editor.icon_size"));
        iconSizeField.setValue(panel.authoring.iconSizeText);
        iconSizeField.setResponder(this::updateCreateQuestIconSize);
        Button increaseIconSize = Widgets.button(widget -> {
            widget.withSize(28, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+")));
            widget.active = panel.authoring.iconSize < QuestSurfaceLayout.MAX_ICON_SIZE;
            widget.withCallback(() -> adjustCreateQuestIconSize(1));
            widget.withTooltip(Component.translatable("gui.theseus.editor.increase_icon_size"));
        });
        iconSize.addChild(decreaseIconSize, 0, 0);
        iconSize.addChild(iconSizeField, 0, 1);
        iconSize.addChild(increaseIconSize, 0, 2);
        layout.addChild(iconSize, row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.inspect_display_json")));
            widget.withCallback(() -> panel.host.dispatch(new OpenRawInspector("Display", panel.draftDisplay())));
            widget.withTooltip(Component.translatable("gui.theseus.editor.read_the_generated_display_configuration"));
        }), row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.position", fieldWidth), row++, 0);
        GridLayout position = new GridLayout().columnSpacing(6);
        int positionWidth = (fieldWidth - 6) / 2;
        EditBox positionX = new EditBox(panel.font, 0, 0, positionWidth, 18, Component.translatable("gui.theseus.editor.x"));
        positionX.setValue(panel.authoring.xText);
        positionX.setResponder(value -> updateCreateQuestPosition(true, value));
        EditBox positionY = new EditBox(panel.font, 0, 0, positionWidth, 18, Component.translatable("gui.theseus.editor.y"));
        positionY.setValue(panel.authoring.yText);
        positionY.setResponder(value -> updateCreateQuestPosition(false, value));
        position.addChild(positionX, 0, 0);
        position.addChild(positionY, 0, 1);
        layout.addChild(position, row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.snap_position")));
            widget.withCallback(this::snapCurrentDraftPosition);
            widget.withTooltip(Component.translatable("gui.theseus.editor.snap_this_quest_center_to_the_27_unit_graph_grid"));
        }), row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.quest_settings", fieldWidth), row++, 0);
        GridLayout settings = new GridLayout().columnSpacing(6).rowSpacing(4);
        int settingWidth = (fieldWidth - 6) / 2;
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.individual_progress", panel.authoring.individualProgress,
            () -> panel.authoring.individualProgress = !panel.authoring.individualProgress), 0, 0);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.unlock_notification", panel.authoring.unlockNotification,
            () -> panel.authoring.unlockNotification = !panel.authoring.unlockNotification), 0, 1);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.show_dependency_arrow", panel.authoring.showDependencyArrow,
            () -> panel.authoring.showDependencyArrow = !panel.authoring.showDependencyArrow), 1, 0);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.repeatable", panel.authoring.repeatable,
            () -> panel.authoring.repeatable = !panel.authoring.repeatable), 1, 1);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.auto_claim_rewards", panel.authoring.autoClaimRewards,
            () -> panel.authoring.autoClaimRewards = !panel.authoring.autoClaimRewards), 2, 0);
        settings.addChild(Widgets.button(widget -> {
            widget.withSize(settingWidth, 22);
            Component visibility = QuestPresentation.visibilityLabel(panel.authoring.hiddenUntil);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.visibility", visibility)));
            widget.withCallback(() -> {
                QuestDefinition.Visibility[] values = QuestDefinition.Visibility.values();
                panel.authoring.hiddenUntil = values[(panel.authoring.hiddenUntil.ordinal() + 1) % values.length];
                panel.host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.visibility_tooltip", visibility));
        }), 2, 1);
        layout.addChild(settings, row++, 0);

        if (panel.authoring.editingExisting) {
            layout.addChild(dockSeparator(fieldWidth), row++, 0);
            layout.addChild(dockLabel("gui.theseus.editor.quest_actions", fieldWidth), row++, 0);
            GridLayout actions = new GridLayout().columnSpacing(6);
            Button delete = Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete_quest")));
                widget.withCallback(() -> {
                    panel.modalHost.open(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION);
                    panel.host.dispatch(new RebuildWidgets());
                });
            });
            actions.addChild(delete, 0, 0);
            if (panel.authoring.groups.size() > 1) actions.addChild(Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.remove_from_chapter")));
                widget.withCallback(() -> panel.host.dispatch(new RequestDiscard(() -> panel.host.dispatch(new RemoveExistingQuestFromChapter()))));
            }), 0, 1);
            layout.addChild(actions, row, 0);
        }

        LayoutWidget<GridLayout> scrollable = new LayoutWidget<>(layout)
            .withScrollableY(TriState.DEFAULT)
            .withContents(ignored -> { });
        scrollable.setPosition(x, 38);
        scrollable.setSize(fieldWidth + 8, Math.max(40, panel.height - 76));
        scrollable.withScrollY(panel.draftOverviewScrollY);
        panel.draftOverviewScrollContainer = scrollable;
        panel.host.addWidget(scrollable);
    }

    void updateCreateQuestPosition(boolean xAxis, String value) {
        boolean valid = value != null && !value.isBlank();
        int parsedValue = xAxis ? panel.authoring.x : panel.authoring.y;
        if (valid) {
            try {
                parsedValue = Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                valid = false;
            }
        }
        if (xAxis) {
            panel.authoring.xText = value == null ? "" : value;
            panel.authoring.xInvalid = !valid;
            if (valid) panel.authoring.x = parsedValue;
        } else {
            panel.authoring.yText = value == null ? "" : value;
            panel.authoring.yInvalid = !valid;
            if (valid) panel.authoring.y = parsedValue;
        }
        if (valid) panel.host.dispatch(new UpdateDraftGroupPosition());
        updateCreateConfirmButton();
    }

    void updateCreateQuestIconSize(String value) {
        panel.authoring.iconSizeText = value == null ? "" : value;
        panel.authoring.iconSizeTouched = true;
        try {
            int parsed = Integer.parseInt(panel.authoring.iconSizeText.trim());
            panel.authoring.iconSizeInvalid = parsed < QuestSurfaceLayout.MIN_ICON_SIZE || parsed > QuestSurfaceLayout.MAX_ICON_SIZE;
            if (!panel.authoring.iconSizeInvalid) panel.authoring.iconSize = parsed;
        } catch (NumberFormatException ignored) {
            panel.authoring.iconSizeInvalid = true;
        }
        updateCreateConfirmButton();
    }

    void adjustCreateQuestIconSize(int amount) {
        panel.authoring.iconSize = Math.max(
            QuestSurfaceLayout.MIN_ICON_SIZE,
            Math.min(QuestSurfaceLayout.MAX_ICON_SIZE, panel.authoring.iconSize + amount)
        );
        panel.authoring.iconSizeText = Integer.toString(panel.authoring.iconSize);
        panel.authoring.iconSizeTouched = true;
        panel.authoring.iconSizeInvalid = false;
        panel.host.dispatch(new RebuildWidgets());
    }

    void snapCurrentDraftPosition() {
        QuestGraphLayout.Point snapped = QuestGraphLayout.snapPoint(panel.authoring.x, panel.authoring.y);
        panel.authoring.x = (int) snapped.x();
        panel.authoring.y = (int) snapped.y();
        panel.authoring.xText = Integer.toString(panel.authoring.x);
        panel.authoring.yText = Integer.toString(panel.authoring.y);
        panel.authoring.xInvalid = false;
        panel.authoring.yInvalid = false;
        panel.host.dispatch(new UpdateDraftGroupPosition());
        panel.host.dispatch(new RebuildWidgets());
    }

    Button settingButton(int width, String labelKey, boolean value, Runnable toggle) {
        return Widgets.button(widget -> {
            widget.withSize(width, 22);
            Component label = Component.translatable(labelKey);
            Component state = Component.translatable(value ? "gui.theseus.editor.state_on" : "gui.theseus.editor.state_off");
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.setting_value", label, state)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.setting_narration", label, state));
            widget.withCallback(() -> {
                toggle.run();
                panel.host.dispatch(new RebuildWidgets());
            });
        });
    }

    void drawCreateQuestDock(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String editorMessage, boolean editorMessageSuccess) {
        int x = panel.width - panel.host.detailsWidth() + 12;
        if (panel.createQuestTab == DetailTab.OVERVIEW) {
        } else if (panel.createQuestTab == DetailTab.TASKS) {
            panel.draftUi.drawDraftTasks(graphics);
        } else if (panel.createQuestTab == DetailTab.REWARDS) {
            panel.draftUi.drawDraftRewards(graphics);
        }
        if (panel.createQuestTab == DetailTab.TASKS && panel.modalHost.isTaskChooserOpen()) {
            panel.draftUi.drawTaskChooser(graphics, mouseX, mouseY);
        }
        if (panel.createQuestTab == DetailTab.REWARDS && panel.modalHost.isRewardChooserOpen()) {
            panel.draftUi.drawRewardChooser(graphics, mouseX, mouseY, false);
        }
        if (!editorMessage.isEmpty()) {
            graphics.textWithWordWrap(
                panel.font,
                Component.literal(editorMessage),
                x,
                panel.height - 48,
                panel.host.detailsWidth() - 24,
                editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999,
                false
            );
        }
    }

    TextWidget dockLabel(String translationKey, int width) {
        return Widgets.text(QuestScreenEditor.editorText(translationKey), widget -> {
            widget.withLeftAlignment().withFont(panel.font).withColor(Color.parse("#B8C0CC"));
            widget.setSize(width, 12);
        });
    }

    BaseWidget dockSeparator(int width) {
        return new BaseWidget(width, 1) {
            {
                active = false;
            }

            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), 0x6649515E);
            }
        };
    }

    void updateCreateConfirmButton() {
        if (panel.createConfirmButton != null) panel.createConfirmButton.active = panel.host.validCreateQuestDraft();
    }

}
