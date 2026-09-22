package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.base.BaseWidget;
import earth.terrarium.olympus.client.components.base.renderer.WidgetRenderer;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import earth.terrarium.olympus.client.components.string.TextWidget;
import com.teamresourceful.resourcefullib.common.color.Color;
import java.util.List;
import java.util.Set;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.QuestAuthoringSession.RewardDraft;
import me.johardt.theseus.client.QuestAuthoringSession.TaskDraft;
import me.johardt.theseus.client.QuestScreen.Picker;
import me.johardt.theseus.client.QuestScreen.PickerTarget;
import me.johardt.theseus.client.QuestScreen.DetailTab;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import me.johardt.theseus.core.EditorTypeRegistry;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.client.QuestPresentation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.GridLayout;
import earth.terrarium.olympus.client.components.compound.LayoutWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.TriState;
import com.mojang.blaze3d.platform.InputConstants;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;

/** Owns task and reward draft editors, their nested edit flows, and chooser state. */
final class QuestAuthoringPanel {
    private static final Gson GSON = new Gson();
    private static final int TASK_CHOOSER_VISIBLE = 6;
    private static final int TASK_CHOOSER_ROW_HEIGHT = 26;
    private static final int EDITOR_LIST_ACTION_WIDTH = 19;
    private static final int EDITOR_LIST_ACTION_HEIGHT = 20;
    private static final int EDITOR_LIST_ACTION_ICON_SIZE = 11;
    private static final int TASK_ICON_LABEL_X = 84;
    private static final int TASK_RAW_INSPECTOR_X = 158;
    private static final int REWARD_ICON_LABEL_X = 84;
    private static final int REWARD_RAW_INSPECTOR_X = 198;
    private static final WidgetSprites CLOSE_BUTTON = new WidgetSprites(
        sprite("heading/close"), sprite("heading/close_selected")
    );

    private final AuthorMode authoring;
    private final QuestModalHost modalHost;
    private final Set<String> serverTaskTypes;
    private final Set<String> serverRewardTypes;
    private final Host host;
    private DetailTab createQuestTab = DetailTab.OVERVIEW;
    private int draftOverviewScrollY;
    private LayoutWidget<GridLayout> draftOverviewScrollContainer;
    private Button createConfirmButton;
    private Font font;
    private int width;
    private int height;
    private int taskChooserScroll;
    private int taskChooserSelectedIndex;
    private int createTaskScroll;
    private int rewardChooserScroll;
    private int rewardChooserSelectedIndex;
    private int createRewardScroll;

    QuestAuthoringPanel(
        AuthorMode authoring,
        QuestModalHost modalHost,
        Set<String> serverTaskTypes,
        Set<String> serverRewardTypes,
        Host host
    ) {
        this.authoring = authoring;
        this.modalHost = modalHost;
        this.serverTaskTypes = serverTaskTypes;
        this.serverRewardTypes = serverRewardTypes;
        this.host = host;
    }

    QuestAuthoringPanel copyForRebuild(
        AuthorMode authoring,
        QuestModalHost modalHost,
        Set<String> serverTaskTypes,
        Set<String> serverRewardTypes,
        Host host
    ) {
        QuestAuthoringPanel copy = new QuestAuthoringPanel(
            authoring, modalHost, serverTaskTypes, serverRewardTypes, host
        );
        copy.taskChooserScroll = taskChooserScroll;
        copy.taskChooserSelectedIndex = taskChooserSelectedIndex;
        copy.createTaskScroll = createTaskScroll;
        copy.rewardChooserScroll = rewardChooserScroll;
        copy.rewardChooserSelectedIndex = rewardChooserSelectedIndex;
        copy.createRewardScroll = createRewardScroll;
        copy.createQuestTab = createQuestTab;
        copy.draftOverviewScrollY = draftOverviewScrollY;
        copy.font = font;
        copy.width = width;
        copy.height = height;
        return copy;
    }

    void setViewport(Font font, int width, int height) {
        this.font = font;
        this.width = width;
        this.height = height;
    }

    int taskChooserSelection() { return taskChooserSelectedIndex; }

    int rewardChooserSelection() { return rewardChooserSelectedIndex; }

    void resetDraftTaskScroll() { createTaskScroll = 0; }

    void resetDraftScrolls() {
        createTaskScroll = 0;
        createRewardScroll = 0;
    }

    void clampDraftTaskScroll() {
        createTaskScroll = Math.min(createTaskScroll, maxCreateTaskScroll());
    }

    void scrollTaskChooser(double scrollY) {
        int max = Math.max(0, tasks().size() - TASK_CHOOSER_VISIBLE);
        taskChooserScroll = Math.max(
            0,
            Math.min(max, taskChooserScroll - (int) Math.signum(scrollY))
        );
        taskChooserSelectedIndex = taskChooserScroll;
    }

    void scrollDraftTaskList(double scrollY) {
        createTaskScroll = Math.max(
            0,
            Math.min(maxCreateTaskScroll(), createTaskScroll - (int) Math.signum(scrollY))
        );
    }

    void scrollDraftRewardList(double scrollY) {
        createRewardScroll = Math.max(
            0,
            Math.min(maxCreateRewardScroll(), createRewardScroll - (int) Math.signum(scrollY))
        );
    }

    DetailTab createQuestTab() { return createQuestTab; }

    void setCreateQuestTab(DetailTab tab) { createQuestTab = tab; }

    void resetOverviewScroll() {
        draftOverviewScrollY = 0;
        draftOverviewScrollContainer = null;
    }

    void captureOverviewScroll() {
        if (draftOverviewScrollContainer != null) {
            draftOverviewScrollY = draftOverviewScrollContainer.getYScroll();
        }
    }

    private int detailsWidth() {
        return host.detailsWidth();
    }

    private static Component editorText(String key) {
        return Component.translatable(key);
    }

    private JsonObject draftDisplay() {
        JsonElement display = authoring.draft().snapshot().get("display");
        return display != null && display.isJsonObject() ? display.getAsJsonObject() : new JsonObject();
    }

    private static Component editorTypeLabel(EditorTypeRegistry.Kind kind, String type, String fallback) {
        if (type != null && type.startsWith("theseus:")) {
            String typeId = type.substring("theseus:".length());
            boolean known = (kind == EditorTypeRegistry.Kind.TASK && tasks().stream().anyMatch(choice -> choice.type().equals(type)))
                || (kind == EditorTypeRegistry.Kind.REWARD && rewards().stream().anyMatch(choice -> choice.type().equals(type)))
                || (kind == EditorTypeRegistry.Kind.ICON && type.equals("theseus:item"));
            if (known) return Component.translatable(
                "gui.theseus.editor.type." + kind.name().toLowerCase(java.util.Locale.ROOT) + "." + typeId
            );
        }
        return Component.literal(fallback == null || fallback.isBlank() ? String.valueOf(type) : fallback);
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "textures/gui/" + path + ".png");
    }

    private WidgetRenderer<Button> listActionRenderer(String action) {
        return WidgetRenderers.center(
            EDITOR_LIST_ACTION_ICON_SIZE,
            EDITOR_LIST_ACTION_ICON_SIZE,
            WidgetRenderers.sprite(new net.minecraft.client.gui.components.WidgetSprites(
                sprite("heading/editor/" + action), sprite("heading/editor/" + action)
            ))
        );
    }

    private void addRawInspectorButton(int x, int y, int width, Runnable open) {
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.raw_json")));
            widget.withCallback(open);
            widget.withTooltip(Component.translatable("gui.theseus.editor.inspect_this_configuration_without_editing_it"));
        }));
    }

    private void drawClippedText(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, Component.literal(text), x, y, color, false);
    }

    private static int parseInteger(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return 0; }
    }

    private static String jsonStringList(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonArray()) return value.getAsJsonArray().asList().stream()
            .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString)
            .collect(java.util.stream.Collectors.joining(", "));
        return value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static void setOptionalString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value);
    }

    private static Component cycleValueLabel(String key, String value) {
        return Component.translatable("gui.theseus.editor.value." + value.toLowerCase(java.util.Locale.ROOT));
    }

    private static Component visibilityLabel(QuestDefinition.Visibility visibility) {
        return switch (visibility) {
            case NEVER -> Component.translatable("gui.theseus.editor.visibility.never");
            case LOCKED -> Component.translatable("quest.theseus.locked");
            case DEPENDENCIES_VISIBLE -> Component.translatable("quest.theseus.dependencies_visible");
            case IN_PROGRESS -> Component.translatable("quest.theseus.in_progress");
            case COMPLETED -> Component.translatable("quest.theseus.completed");
        };
    }

    private static EditorTypeRegistry editorTypes() {
        return EditorTypeRegistry.registered();
    }

    void addCreateQuestDockWidgets() {
        int detailsLeft = width - detailsWidth();
        int pinLeft = width - 50;
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
                    tab == createQuestTab ? "#5A4300" : "#FFFFFF"
                )));
                widget.withCallback(() -> {
                    createQuestTab = tab;
                    host.dispatch(new ClosePicker());
                    if (modalHost.isOneOf(QuestModalHost.Modal.TASK_CHOOSER, QuestModalHost.Modal.REWARD_CHOOSER)
                        || modalHost.is(QuestModalHost.Modal.PICKER)) {
                        modalHost.close();
                    }
                    host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable(tab.translationKey));
            });
            host.addWidget(tabButton);
        }
        Button close = Widgets.button(widget -> {
            widget.withPosition(width - 27, 8).withSize(19, 20);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(CLOSE_BUTTON)
            ));
            widget.withCallback(() -> {
                host.dispatch(new RequestDiscard(() -> {
                    host.dispatch(new CloseDraft());
                    host.dispatch(new ClearSelectedQuest());
                    host.dispatch(new RebuildWidgets());
                }));
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.close_new_quest"));
        });
        host.addWidget(close);

        int x = detailsLeft + 12;
        int fieldWidth = detailsWidth() - 24;
        createConfirmButton = Widgets.button(widget -> {
            widget.withPosition(x, height - 30).withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(authoring.editingExisting ? "gui.theseus.editor.save_quest" : "gui.theseus.editor.create_quest")));
            widget.withCallback(() -> host.dispatch(new ConfirmCreateQuest()));
            String error = host.draftValidationError();
            widget.withTooltip(host.mutationPending()
                ? Component.translatable("gui.theseus.editor.waiting_for_the_server")
                : error.isEmpty() ? Component.translatable("gui.theseus.editor.save_this_quest") : Component.literal(error));
        });
        updateCreateConfirmButton();
        host.addWidget(createConfirmButton);

        if (createQuestTab == DetailTab.TASKS) {
            addDraftTaskWidgets(x, fieldWidth);
            return;
        }
        if (createQuestTab == DetailTab.REWARDS) {
            addDraftRewardWidgets(x, fieldWidth);
            return;
        }
        if (createQuestTab != DetailTab.OVERVIEW) return;
        // Reserve room for the scroll rail so fields never sit underneath it.
        addOverviewDockWidgets(x, fieldWidth - 8);
    }

    private void addOverviewDockWidgets(int x, int fieldWidth) {
        GridLayout layout = new GridLayout().rowSpacing(4);
        int row = 0;
        layout.addChild(dockLabel("gui.theseus.editor.id", fieldWidth), row++, 0);
        EditBox id = new EditBox(font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_id"));
        id.setValue(authoring.id);
        id.setResponder(value -> {
            authoring.id = value;
            updateCreateConfirmButton();
        });
        layout.addChild(id, row++, 0);

        layout.addChild(dockLabel("gui.theseus.editor.title", fieldWidth), row++, 0);
        EditBox title = new EditBox(font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_title"));
        title.setValue(authoring.title);
        title.setResponder(value -> {
            authoring.title = value;
            updateCreateConfirmButton();
        });
        layout.addChild(title, row++, 0);

        layout.addChild(dockLabel("gui.theseus.editor.subtitle", fieldWidth), row++, 0);
        EditBox subtitle = new EditBox(font, 0, 0, fieldWidth, 18, Component.translatable("gui.theseus.editor.quest_subtitle"));
        subtitle.setValue(authoring.subtitle);
        subtitle.setResponder(value -> authoring.subtitle = value);
        layout.addChild(subtitle, row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.description", fieldWidth), row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 32);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(authoring.body.isBlank()
                ? "gui.theseus.editor.write_rich_description"
                : "gui.theseus.editor.edit_rich_description")));
            widget.withCallback(() -> host.dispatch(new OpenDescriptionEditor()));
            widget.withTooltip(Component.translatable("gui.theseus.editor.markdown_editor_with_live_player_preview"));
        }), row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.appearance", fieldWidth), row++, 0);
        GridLayout appearance = new GridLayout().columnSpacing(6);
        Button icon = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.choose_icon")));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.QUEST_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_quest_icon"));
        });
        Button background = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.choose_background")));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.BACKGROUND, PickerTarget.QUEST_ICON)));
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
            widget.active = authoring.iconSize > QuestSurfaceLayout.MIN_ICON_SIZE;
            widget.withCallback(() -> adjustCreateQuestIconSize(-1));
            widget.withTooltip(Component.translatable("gui.theseus.editor.decrease_icon_size"));
        });
        EditBox iconSizeField = new EditBox(font, 0, 0, Math.max(44, fieldWidth - 68), 18, Component.translatable("gui.theseus.editor.icon_size"));
        iconSizeField.setValue(authoring.iconSizeText);
        iconSizeField.setResponder(this::updateCreateQuestIconSize);
        Button increaseIconSize = Widgets.button(widget -> {
            widget.withSize(28, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+")));
            widget.active = authoring.iconSize < QuestSurfaceLayout.MAX_ICON_SIZE;
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
            widget.withCallback(() -> host.dispatch(new OpenRawInspector("Display", draftDisplay())));
            widget.withTooltip(Component.translatable("gui.theseus.editor.read_the_generated_display_configuration"));
        }), row++, 0);

        layout.addChild(dockSeparator(fieldWidth), row++, 0);
        layout.addChild(dockLabel("gui.theseus.editor.position", fieldWidth), row++, 0);
        GridLayout position = new GridLayout().columnSpacing(6);
        int positionWidth = (fieldWidth - 6) / 2;
        EditBox positionX = new EditBox(font, 0, 0, positionWidth, 18, Component.translatable("gui.theseus.editor.x"));
        positionX.setValue(authoring.xText);
        positionX.setResponder(value -> updateCreateQuestPosition(true, value));
        EditBox positionY = new EditBox(font, 0, 0, positionWidth, 18, Component.translatable("gui.theseus.editor.y"));
        positionY.setValue(authoring.yText);
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
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.individual_progress", authoring.individualProgress,
            () -> authoring.individualProgress = !authoring.individualProgress), 0, 0);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.unlock_notification", authoring.unlockNotification,
            () -> authoring.unlockNotification = !authoring.unlockNotification), 0, 1);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.show_dependency_arrow", authoring.showDependencyArrow,
            () -> authoring.showDependencyArrow = !authoring.showDependencyArrow), 1, 0);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.repeatable", authoring.repeatable,
            () -> authoring.repeatable = !authoring.repeatable), 1, 1);
        settings.addChild(settingButton(settingWidth, "setting.theseus.quest.auto_claim_rewards", authoring.autoClaimRewards,
            () -> authoring.autoClaimRewards = !authoring.autoClaimRewards), 2, 0);
        settings.addChild(Widgets.button(widget -> {
            widget.withSize(settingWidth, 22);
            Component visibility = visibilityLabel(authoring.hiddenUntil);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.visibility", visibility)));
            widget.withCallback(() -> {
                QuestDefinition.Visibility[] values = QuestDefinition.Visibility.values();
                authoring.hiddenUntil = values[(authoring.hiddenUntil.ordinal() + 1) % values.length];
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.visibility_tooltip", visibility));
        }), 2, 1);
        layout.addChild(settings, row++, 0);

        if (authoring.editingExisting) {
            layout.addChild(dockSeparator(fieldWidth), row++, 0);
            layout.addChild(dockLabel("gui.theseus.editor.quest_actions", fieldWidth), row++, 0);
            GridLayout actions = new GridLayout().columnSpacing(6);
            Button delete = Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete_quest")));
                widget.withCallback(() -> {
                    modalHost.open(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION);
                    host.dispatch(new RebuildWidgets());
                });
            });
            actions.addChild(delete, 0, 0);
            if (authoring.groups.size() > 1) actions.addChild(Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.remove_from_chapter")));
                widget.withCallback(() -> host.dispatch(new RequestDiscard(() -> host.dispatch(new RemoveExistingQuestFromChapter()))));
            }), 0, 1);
            layout.addChild(actions, row, 0);
        }

        LayoutWidget<GridLayout> scrollable = new LayoutWidget<>(layout)
            .withScrollableY(TriState.DEFAULT)
            .withContents(ignored -> { });
        scrollable.setPosition(x, 38);
        scrollable.setSize(fieldWidth + 8, Math.max(40, height - 76));
        scrollable.withScrollY(draftOverviewScrollY);
        draftOverviewScrollContainer = scrollable;
        host.addWidget(scrollable);
    }

    private void updateCreateQuestPosition(boolean xAxis, String value) {
        boolean valid = value != null && !value.isBlank();
        int parsedValue = xAxis ? authoring.x : authoring.y;
        if (valid) {
            try {
                parsedValue = Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                valid = false;
            }
        }
        if (xAxis) {
            authoring.xText = value == null ? "" : value;
            authoring.xInvalid = !valid;
            if (valid) authoring.x = parsedValue;
        } else {
            authoring.yText = value == null ? "" : value;
            authoring.yInvalid = !valid;
            if (valid) authoring.y = parsedValue;
        }
        if (valid) host.dispatch(new UpdateDraftGroupPosition());
        updateCreateConfirmButton();
    }

    private void updateCreateQuestIconSize(String value) {
        authoring.iconSizeText = value == null ? "" : value;
        authoring.iconSizeTouched = true;
        try {
            int parsed = Integer.parseInt(authoring.iconSizeText.trim());
            authoring.iconSizeInvalid = parsed < QuestSurfaceLayout.MIN_ICON_SIZE || parsed > QuestSurfaceLayout.MAX_ICON_SIZE;
            if (!authoring.iconSizeInvalid) authoring.iconSize = parsed;
        } catch (NumberFormatException ignored) {
            authoring.iconSizeInvalid = true;
        }
        updateCreateConfirmButton();
    }

    private void adjustCreateQuestIconSize(int amount) {
        authoring.iconSize = Math.max(
            QuestSurfaceLayout.MIN_ICON_SIZE,
            Math.min(QuestSurfaceLayout.MAX_ICON_SIZE, authoring.iconSize + amount)
        );
        authoring.iconSizeText = Integer.toString(authoring.iconSize);
        authoring.iconSizeTouched = true;
        authoring.iconSizeInvalid = false;
        host.dispatch(new RebuildWidgets());
    }

    void snapCurrentDraftPosition() {
        QuestGraphLayout.Point snapped = QuestGraphLayout.snapPoint(authoring.x, authoring.y);
        authoring.x = (int) snapped.x();
        authoring.y = (int) snapped.y();
        authoring.xText = Integer.toString(authoring.x);
        authoring.yText = Integer.toString(authoring.y);
        authoring.xInvalid = false;
        authoring.yInvalid = false;
        host.dispatch(new UpdateDraftGroupPosition());
        host.dispatch(new RebuildWidgets());
    }

    private Button settingButton(int width, String labelKey, boolean value, Runnable toggle) {
        return Widgets.button(widget -> {
            widget.withSize(width, 22);
            Component label = Component.translatable(labelKey);
            Component state = Component.translatable(value ? "gui.theseus.editor.state_on" : "gui.theseus.editor.state_off");
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.setting_value", label, state)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.setting_narration", label, state));
            widget.withCallback(() -> {
                toggle.run();
                host.dispatch(new RebuildWidgets());
            });
        });
    }

    void drawCreateQuestDock(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String editorMessage, boolean editorMessageSuccess) {
        int x = width - detailsWidth() + 12;
        if (createQuestTab == DetailTab.OVERVIEW) {
        } else if (createQuestTab == DetailTab.TASKS) {
            drawDraftTasks(graphics);
        } else if (createQuestTab == DetailTab.REWARDS) {
            drawDraftRewards(graphics);
        }
        if (createQuestTab == DetailTab.TASKS && modalHost.isTaskChooserOpen()) {
            drawTaskChooser(graphics, mouseX, mouseY);
        }
        if (createQuestTab == DetailTab.REWARDS && modalHost.isRewardChooserOpen()) {
            drawRewardChooser(graphics, mouseX, mouseY, false);
        }
        if (!editorMessage.isEmpty()) {
            graphics.textWithWordWrap(
                font,
                Component.literal(editorMessage),
                x,
                height - 48,
                detailsWidth() - 24,
                editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999,
                false
            );
        }
    }

    private TextWidget dockLabel(String translationKey, int width) {
        return Widgets.text(editorText(translationKey), widget -> {
            widget.withLeftAlignment().withFont(font).withColor(Color.parse("#B8C0CC"));
            widget.setSize(width, 12);
        });
    }

    private BaseWidget dockSeparator(int width) {
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

    private void updateCreateConfirmButton() {
        if (createConfirmButton != null) createConfirmButton.active = host.validCreateQuestDraft();
    }

    void addDraftTaskWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(authoring.tasks.size(), createTaskScroll + taskListCapacity());
        for (int index = createTaskScroll; index < end; index++) {
            int taskIndex = index;
            int cardY = y + (index - createTaskScroll) * 48;
            int actionY = cardY + (42 - EDITOR_LIST_ACTION_HEIGHT) / 2;
            int deleteX = x + width - EDITOR_LIST_ACTION_WIDTH - 8;
            int editX = deleteX - 4 - EDITOR_LIST_ACTION_WIDTH;
            Button edit = Widgets.button(widget -> {
                widget.withPosition(editX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("edit"));
                widget.withCallback(() -> openTaskEditor(taskIndex));
                widget.active = isTaskEditable(authoring.tasks.get(taskIndex));
                widget.withTooltip(widget.active
                    ? editorText("gui.theseus.editor.edit_task")
                    : Component.literal(unavailableReason(EditorTypeRegistry.Kind.TASK, authoring.tasks.get(taskIndex).type)));
            });
            host.addWidget(edit);
            Button delete = Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("delete"));
                widget.withCallback(() -> {
                    authoring.taskDeleteConfirmation = taskIndex;
                    modalHost.open(QuestModalHost.Modal.TASK_DELETE_CONFIRMATION);
                    host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_task"));
            });
            host.addWidget(delete);
        }
        int addIndex = authoring.tasks.size();
        if (addIndex >= createTaskScroll && addIndex < createTaskScroll + taskListCapacity()) {
            int addY = y + (addIndex - createTaskScroll) * 48;
            Button add = Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_task")));
                widget.withCallback(() -> {
                    taskChooserScroll = 0;
                    taskChooserSelectedIndex = 0;
                    if (modalHost.isTaskChooserOpen()) modalHost.close();
                    else modalHost.open(QuestModalHost.Modal.TASK_CHOOSER);
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_task_type"));
            });
            host.addWidget(add);
        }
    }

    void addDraftRewardWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(authoring.rewards.size(), createRewardScroll + rewardListCapacity());
        for (int index = createRewardScroll; index < end; index++) {
            int rewardIndex = index;
            int cardY = y + (index - createRewardScroll) * 48;
            int actionY = cardY + (42 - EDITOR_LIST_ACTION_HEIGHT) / 2;
            int deleteX = x + width - EDITOR_LIST_ACTION_WIDTH - 8;
            int editX = deleteX - 4 - EDITOR_LIST_ACTION_WIDTH;
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(editX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("edit"));
                widget.withCallback(() -> openRewardEditor(rewardIndex));
                widget.active = isRewardEditable(authoring.rewards.get(rewardIndex));
                widget.withTooltip(widget.active
                    ? editorText("gui.theseus.editor.edit_reward")
                    : Component.literal(unavailableReason(EditorTypeRegistry.Kind.REWARD, authoring.rewards.get(rewardIndex).type)));
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("delete"));
                widget.withCallback(() -> {
                    authoring.rewards.remove(rewardIndex);
                    createRewardScroll = Math.min(createRewardScroll, maxCreateRewardScroll());
                    if (modalHost.isRewardChooserOpen()) modalHost.close();
                    host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_reward"));
            }));
        }
        int addIndex = authoring.rewards.size();
        if (addIndex >= createRewardScroll && addIndex < createRewardScroll + rewardListCapacity()) {
            int addY = y + (addIndex - createRewardScroll) * 48;
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_reward")));
                widget.withCallback(() -> {
                    rewardChooserScroll = 0;
                    rewardChooserSelectedIndex = 0;
                    if (modalHost.isRewardChooserOpen()) modalHost.close();
                    else modalHost.open(QuestModalHost.Modal.REWARD_CHOOSER);
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_reward_type"));
            }));
        }
    }

    private int rewardListCapacity() {
        return Math.max(1, (height - 79) / 48);
    }

    private int maxCreateRewardScroll() {
        return Math.max(0, authoring.rewards.size() + 1 - rewardListCapacity());
    }

    private void openRewardEditor(int index) {
        if (!isRewardEditable(authoring.rewards.get(index))) {
            host.dispatch(new ShowMessage(unavailableReason(EditorTypeRegistry.Kind.REWARD, authoring.rewards.get(index).type) + ". It is preserved read-only."));
            return;
        }
        authoring.editingRewardIndex = index;
        authoring.editingReward = authoring.rewards.get(index).copy();
        authoring.rewardEditorError = "";
        modalHost.open(QuestModalHost.Modal.REWARD_EDITOR);
        host.dispatch(new RebuildWidgets());
    }

    private void openTaskEditor(int index) {
        if (!isTaskEditable(authoring.tasks.get(index))) {
            host.dispatch(new ShowMessage(unavailableReason(EditorTypeRegistry.Kind.TASK, authoring.tasks.get(index).type) + ". It is preserved read-only."));
            return;
        }
        authoring.editingTaskIndex = index;
        authoring.editingTask = authoring.tasks.get(index).copy();
        authoring.taskEditorParents.clear();
        authoring.taskEditorParentIndexes.clear();
        authoring.taskEditorError = "";
        modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
        host.dispatch(new RebuildWidgets());
    }

    void addTaskEditorWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        int fieldWidth = 232;

        EditBox id = new EditBox(font, left + 14, top + 38, fieldWidth, 18, Component.translatable("gui.theseus.editor.task_id"));
        id.setValue(authoring.editingTask.id);
        id.setResponder(value -> authoring.editingTask.id = value);
        host.addWidget(id);

        EditBox title = new EditBox(font, left + 14, top + 70, fieldWidth, 18, Component.translatable("gui.theseus.editor.task_title"));
        title.setValue(jsonString(authoring.editingTask.source, "title", ""));
        title.setResponder(value -> setOptionalString(authoring.editingTask.source, "title", value));
        host.addWidget(title);

        Button icon = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.TASK_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_task_icon_override"));
        });
        host.addWidget(icon);
        Button clearIcon = Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                authoring.editingTask.source.remove("icon");
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.use_the_default_task_icon"));
        });
        host.addWidget(clearIcon);
        // Keep action controls outside the label lane drawn by the foreground pass.
        addRawInspectorButton(left + TASK_RAW_INSPECTOR_X, top + 101, 88, () -> host.dispatch(new OpenRawInspector("Task: " + authoring.editingTask.id, authoring.editingTask.source)));

        switch (authoring.editingTask.type) {
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
            widget.withCallback(() -> host.dispatch(new RequestModalDiscard(this::closeTaskEditor)));
        });
        host.addWidget(cancel);
        Button save = Widgets.button(widget -> {
            widget.withPosition(left + 138, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save_task")));
            widget.withCallback(this::saveTaskEditor);
        });
        host.addWidget(save);
    }

    private void addDummyTaskFields(int left, int top, int width) {
        EditBox value = new EditBox(font, left + 14, top + 142, width, 18, Component.translatable("gui.theseus.editor.trigger_value"));
        value.setValue(jsonString(authoring.editingTask.source, "value", ""));
        value.setResponder(text -> authoring.editingTask.source.addProperty("value", text));
        host.addWidget(value);
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.translatable("gui.theseus.editor.description"));
        description.setValue(jsonString(authoring.editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(authoring.editingTask.source, "description", text));
        host.addWidget(description);
    }

    private void addItemTaskFields(int left, int top, int width) {
        EditBox item = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.translatable("gui.theseus.editor.item_or_tag"));
        item.setValue(registryValueString(authoring.editingTask.source, "item", "minecraft:stone"));
        item.setResponder(text -> authoring.editingTask.source.addProperty("item", text));
        host.addWidget(item);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.TASK_ITEM)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_item"));
        });
        host.addWidget(choose);
        addAmountField(left, top + 181);
        addCycleButton(left + 104, top + 178, 142, "collection", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    private void addXpTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        addCycleButton(left + 104, top + 139, 142, "xpType", "level", List.of("level", "points"));
        addCycleButton(left + 14, top + 178, 232, "collectionType", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    private void addKillTaskFields(int left, int top, int width) {
        EditBox entity = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.translatable("gui.theseus.editor.entity"));
        entity.setValue(registryValueString(authoring.editingTask.source, "entity", "minecraft:pig"));
        entity.setResponder(text -> authoring.editingTask.source.addProperty("entity", text));
        host.addWidget(entity);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ENTITY, PickerTarget.TASK_ENTITY)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_entity"));
        });
        host.addWidget(choose);
        addAmountField(left, top + 181);
    }

    private void addIdentifierTaskField(int left, int top, int width, String key, String labelKey, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, editorText(labelKey));
        field.setValue(registryValueString(authoring.editingTask.source, key, fallback));
        field.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        host.addWidget(field);
    }

    private void addStringListTaskField(int left, int top, int width, String key, String labelKey, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, editorText(labelKey));
        field.setValue(jsonStringList(authoring.editingTask.source, key, fallback));
        field.setResponder(text -> authoring.editingTask.source.add(key, stringArray(text)));
        host.addWidget(field);
    }

    private void addPredicateTargetFields(
        int left, int top, int width, String key, String labelKey, String fallback, PickerTarget target
    ) {
        EditBox value = new EditBox(font, left + 14, top + 142, width - 40, 18, editorText(labelKey));
        value.setValue(registryValueString(authoring.editingTask.source, key, fallback));
        value.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        host.addWidget(value);
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> host.dispatch(new OpenPicker(target == PickerTarget.TASK_ENTITY ? Picker.ENTITY : Picker.ICON, target)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_target"));
        }));
        addJsonTaskField(left, top + 39, width, "components", "gui.theseus.editor.component_data_predicate", new JsonObject());
    }

    private void addBlockInteractionTaskFields(int left, int top, int width) {
        addPredicateTargetFields(left, top, width, "block", "gui.theseus.editor.block_or_tag", "minecraft:stone", PickerTarget.TASK_BLOCK);
        addJsonTaskField(left, top + 78, width, "state", "gui.theseus.editor.block_state_predicate", new JsonObject());
    }

    private void addLocationTaskFields(int left, int top, int width) {
        addJsonTaskField(left, top, width, "predicate", "gui.theseus.editor.location_predicate", defaultLocationPredicate());
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.translatable("gui.theseus.editor.description"));
        description.setValue(jsonString(authoring.editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(authoring.editingTask.source, "description", text));
        host.addWidget(description);
    }

    private void addDimensionTaskFields(int left, int top, int width) {
        EditBox from = new EditBox(font, left + 14, top + 142, 110, 18, Component.translatable("gui.theseus.editor.from_dimension"));
        from.setValue(jsonString(authoring.editingTask.source, "from", ""));
        from.setResponder(text -> setOptionalString(authoring.editingTask.source, "from", text));
        host.addWidget(from);
        EditBox to = new EditBox(font, left + 136, top + 142, 110, 18, Component.translatable("gui.theseus.editor.to_dimension"));
        to.setValue(jsonString(authoring.editingTask.source, "to", ""));
        to.setResponder(text -> setOptionalString(authoring.editingTask.source, "to", text));
        host.addWidget(to);
    }

    private void addJsonTaskField(int left, int top, int width, String key, String labelKey, JsonObject fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, editorText(labelKey));
        field.setMaxLength(2048);
        JsonElement current = authoring.editingTask.source.get(key);
        field.setValue(current == null ? GSON.toJson(fallback) : current.isJsonPrimitive() ? current.getAsString() : GSON.toJson(current));
        field.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        host.addWidget(field);
    }

    private void addCompositeTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 104, top + 139).withSize(142, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(
                "gui.theseus.editor.manage_children",
                nestedTasks(authoring.editingTask).size()
            )));
            widget.withCallback(() -> {
                authoring.nestedTaskScroll = 0;
                modalHost.open(QuestModalHost.Modal.NESTED_TASKS);
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.edit_this_composite_task_s_child_tasks"));
        }));
    }

    void addNestedTaskWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        List<QuestAuthoringSession.TaskDraft> children = nestedTasks(authoring.editingTask);
        int end = Math.min(children.size(), authoring.nestedTaskScroll + 4);
        String breadcrumbs = authoring.taskEditorParents.isEmpty() ? authoring.editingTask.id : authoring.taskEditorParents.stream()
            .map(parent -> parent.id).collect(java.util.stream.Collectors.joining(" › ")) + " › " + authoring.editingTask.id;
        for (int index = authoring.nestedTaskScroll; index < end; index++) {
            int childIndex = index;
            int rowY = top + 42 + (index - authoring.nestedTaskScroll) * 42;
            QuestAuthoringSession.TaskDraft child = children.get(index);
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(140, 34);
                widget.withRenderer(WidgetRenderers.text(Component.literal(child.id + "  ·  " + taskDisplayLabel(child))));
                widget.withCallback(() -> {
                    authoring.taskEditorParents.add(authoring.editingTask);
                    authoring.taskEditorParentIndexes.add(authoring.editingTaskIndex);
                    authoring.editingTask = children.get(childIndex).copy();
                    authoring.editingTaskIndex = childIndex;
                    modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
                    authoring.taskEditorError = "";
                    host.dispatch(new RebuildWidgets());
                });
                widget.active = isTaskEditable(child);
                widget.withTooltip(widget.active
                    ? editorText("gui.theseus.editor.edit_child_task")
                    : Component.literal(unavailableReason(EditorTypeRegistry.Kind.TASK, child.type)));
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 160, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedTask(childIndex, -1));
                widget.active = childIndex > 0;
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 188, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedTask(childIndex, 1));
                widget.active = childIndex < children.size() - 1;
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 216, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    List<QuestAuthoringSession.TaskDraft> updated = nestedTasks(authoring.editingTask);
                    updated.remove(childIndex);
                    setNestedTasks(authoring.editingTask, updated);
                    authoring.nestedTaskScroll = Math.min(authoring.nestedTaskScroll, Math.max(0, updated.size() - 4));
                    host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_child_task"));
            }));
        }
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_task")));
            widget.withCallback(() -> {
                taskChooserScroll = 0;
                taskChooserSelectedIndex = 0;
                if (modalHost.isNestedTaskChooserOpen()) modalHost.close();
                else modalHost.open(QuestModalHost.Modal.NESTED_TASK_CHOOSER);
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_a_task_type"));
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 133, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.done")));
            widget.withCallback(() -> {
                modalHost.close();
                host.dispatch(new RebuildWidgets());
            });
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 14).withSize(232, 20);
            widget.withTexture(null);
            widget.withRenderer(WidgetRenderers.text(Component.literal(breadcrumbs)));
            widget.active = false;
        }));
    }

    private void moveNestedTask(int index, int direction) {
        List<QuestAuthoringSession.TaskDraft> children = nestedTasks(authoring.editingTask);
        int target = index + direction;
        if (target < 0 || target >= children.size()) return;
        java.util.Collections.swap(children, index, target);
        setNestedTasks(authoring.editingTask, children);
        host.dispatch(new RebuildWidgets());
    }

    private void addStatTaskFields(int left, int top, int width) {
        EditBox stat = new EditBox(font, left + 14, top + 142, width - 100, 18, Component.translatable("gui.theseus.editor.statistic_id"));
        stat.setValue(jsonString(authoring.editingTask.source, "stat", "minecraft:jump"));
        stat.setResponder(text -> authoring.editingTask.source.addProperty("stat", text));
        host.addWidget(stat);
        EditBox target = new EditBox(font, left + width - 76, top + 142, 76, 18, Component.translatable("gui.theseus.editor.target"));
        target.setValue(Integer.toString(jsonInt(authoring.editingTask.source, "target", 1)));
        target.setResponder(text -> authoring.editingTask.source.addProperty("target", parseInteger(text)));
        host.addWidget(target);
    }

    private void addAmountField(int left, int y) {
        EditBox amount = new EditBox(font, left + 14, y, 82, 18, Component.translatable("gui.theseus.editor.amount"));
        amount.setValue(Integer.toString(jsonInt(authoring.editingTask.source, "amount", 1)));
        amount.setResponder(text -> {
            try {
                authoring.editingTask.source.addProperty("amount", Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                authoring.editingTask.source.addProperty("amount", 0);
            }
        });
        host.addWidget(amount);
    }

    private void addCycleButton(
        int x,
        int y,
        int width,
        String key,
        String fallback,
        List<String> values
    ) {
        String current = jsonString(authoring.editingTask.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        Button cycle = Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(cycleValueLabel(key, current)));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                authoring.editingTask.source.addProperty(key, values.get((index + 1) % values.size()));
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.click_to_change"));
        });
        host.addWidget(cycle);
    }

    void closeTaskEditor() {
        boolean hasParent = !authoring.taskEditorParents.isEmpty();
        if (hasParent) {
            authoring.editingTask = authoring.taskEditorParents.removeLast();
            authoring.editingTaskIndex = authoring.taskEditorParentIndexes.removeLast();
        } else {
            authoring.editingTask = null;
            authoring.editingTaskIndex = -1;
        }
        authoring.taskEditorError = "";
        host.dispatch(new ClosePicker());
        if (modalHost.isOneOf(QuestModalHost.Modal.TASK_EDITOR, QuestModalHost.Modal.NESTED_TASKS)) {
            modalHost.close();
        }
        if (!hasParent && modalHost.is(QuestModalHost.Modal.TASK_EDITOR)) modalHost.close();
        host.dispatch(new RebuildWidgets());
    }

    private void saveTaskEditor() {
        String error = validateTaskDraft(authoring.editingTask, authoring.editingTaskIndex);
        if (!error.isEmpty()) {
            authoring.taskEditorError = error;
            return;
        }
        if (authoring.taskEditorParents.isEmpty()) {
            if (authoring.editingTaskIndex < 0) {
                authoring.tasks.add(authoring.editingTask.copy());
                createTaskScroll = maxCreateTaskScroll();
            } else {
                authoring.tasks.set(authoring.editingTaskIndex, authoring.editingTask.copy());
            }
        } else {
            QuestAuthoringSession.TaskDraft parent = authoring.taskEditorParents.getLast();
            List<QuestAuthoringSession.TaskDraft> children = nestedTasks(parent);
            if (authoring.editingTaskIndex < 0) children.add(authoring.editingTask.copy());
            else children.set(authoring.editingTaskIndex, authoring.editingTask.copy());
            setNestedTasks(parent, children);
        }
        closeTaskEditor();
    }

    void addRewardEditorWidgets(QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        int width = 272;
        EditBox id = new EditBox(font, left + 14, top + 38, width, 18, Component.translatable("gui.theseus.editor.reward_id"));
        id.setValue(reward.id);
        id.setResponder(value -> reward.id = value);
        host.addWidget(id);
        EditBox title = new EditBox(font, left + 14, top + 70, width, 18, Component.translatable("gui.theseus.editor.reward_title"));
        title.setValue(jsonString(reward.source, "title", ""));
        title.setResponder(value -> setOptionalString(reward.source, "title", value));
        host.addWidget(title);
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.REWARD_ICON)));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_reward_icon_override"));
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                reward.source.remove("icon");
                host.dispatch(new RebuildWidgets());
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.use_the_default_reward_icon"));
        }));
        addRawInspectorButton(left + REWARD_RAW_INSPECTOR_X, top + 101, 88, () -> host.dispatch(new OpenRawInspector("Reward: " + reward.id, reward.source)));
        switch (reward.type) {
            case "theseus:xp" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRewardCycleButton(reward, left + 114, top + 139, 172, "xptype", "level", List.of("level", "points"));
            }
            case "theseus:item" -> {
                EditBox item = new EditBox(font, left + 14, top + 142, 210, 18, Component.translatable("gui.theseus.editor.item"));
                item.setValue(rewardItemId(reward.source));
                item.setResponder(value -> setRewardItem(reward.source, value, rewardItemCount(reward.source)));
                host.addWidget(item);
                host.addWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 232, top + 139).withSize(54, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                    widget.withCallback(() -> host.dispatch(new OpenPicker(Picker.ICON, PickerTarget.REWARD_ITEM)));
                    widget.withTooltip(Component.translatable("gui.theseus.editor.choose_item"));
                }));
                addRewardAmountField(reward, left + 14, top + 181, 92, "item.count");
            }
            case "theseus:loottable" -> addRewardTextField(reward, left, top, "loot_table", "gui.theseus.editor.loot_table");
            case "theseus:command" -> addRewardTextField(reward, left, top, "command", "gui.theseus.editor.command");
            case "theseus:selectable" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                host.addWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 114, top + 139).withSize(172, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable(
                        "gui.theseus.editor.manage_choices",
                        nestedRewards(reward).size()
                    )));
                    widget.withCallback(() -> {
                        authoring.rewardEditorError = "";
                        modalHost.open(QuestModalHost.Modal.NESTED_REWARDS);
                        host.dispatch(new RebuildWidgets());
                    });
                }));
            }
            default -> { }
        }
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> host.dispatch(new RequestModalDiscard(() -> closeRewardEditor(nested))));
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 158, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save_reward")));
            widget.withCallback(() -> saveRewardEditor(nested));
        }));
    }

    private void addRewardTextField(QuestAuthoringSession.RewardDraft reward, int left, int top, String key, String labelKey) {
        EditBox field = new EditBox(font, left + 14, top + 142, 272, 18, editorText(labelKey));
        field.setValue(jsonString(reward.source, key, ""));
        field.setResponder(value -> reward.source.addProperty(key, value));
        host.addWidget(field);
    }

    private void addRewardAmountField(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String path) {
        int current = path.equals("item.count") ? rewardItemCount(reward.source) : jsonInt(reward.source, path, 1);
        EditBox amount = new EditBox(font, x, y, width, 18, Component.translatable("gui.theseus.editor.amount"));
        amount.setValue(Integer.toString(current));
        amount.setResponder(value -> {
            int parsed;
            try { parsed = Integer.parseInt(value); } catch (NumberFormatException ignored) { parsed = 0; }
            if (path.equals("item.count")) setRewardItem(reward.source, rewardItemId(reward.source), parsed);
            else reward.source.addProperty(path, parsed);
        });
        host.addWidget(amount);
    }

    private void addRewardCycleButton(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String key, String fallback, List<String> values) {
        String current = jsonString(reward.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(cycleValueLabel(key, current)));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                reward.source.addProperty(key, values.get((index + 1) % values.size()));
                host.dispatch(new RebuildWidgets());
            });
        }));
    }

    void closeRewardEditor(boolean nested) {
        authoring.rewardEditorError = "";
        host.dispatch(new ClosePicker());
        if (modalHost.is(QuestModalHost.Modal.PICKER)) modalHost.close();
        if (nested) {
            authoring.editingNestedReward = null;
            authoring.editingNestedRewardIndex = -1;
            if (modalHost.is(QuestModalHost.Modal.NESTED_REWARD_EDITOR)) modalHost.close();
        } else {
            authoring.editingReward = null;
            authoring.editingRewardIndex = -1;
            if (modalHost.is(QuestModalHost.Modal.NESTED_REWARDS)) modalHost.close();
            if (modalHost.is(QuestModalHost.Modal.REWARD_EDITOR)) modalHost.close();
        }
        host.dispatch(new RebuildWidgets());
    }

    private void saveRewardEditor(boolean nested) {
        QuestAuthoringSession.RewardDraft reward = nested ? authoring.editingNestedReward : authoring.editingReward;
        String error = validateRewardDraft(reward, nested);
        if (!error.isEmpty()) {
            authoring.rewardEditorError = error;
            return;
        }
        if (nested) {
            List<QuestAuthoringSession.RewardDraft> rewards = nestedRewards(authoring.editingReward);
            if (authoring.editingNestedRewardIndex < 0) rewards.add(reward.copy());
            else rewards.set(authoring.editingNestedRewardIndex, reward.copy());
            setNestedRewards(authoring.editingReward, rewards);
        } else {
            if (authoring.editingRewardIndex < 0) {
                authoring.rewards.add(reward.copy());
                createRewardScroll = maxCreateRewardScroll();
            } else {
                authoring.rewards.set(authoring.editingRewardIndex, reward.copy());
            }
        }
        closeRewardEditor(nested);
    }

    void addNestedRewardWidgets() {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        List<QuestAuthoringSession.RewardDraft> rewards = nestedRewards(authoring.editingReward);
        int end = Math.min(rewards.size(), authoring.nestedRewardScroll + 4);
        for (int index = authoring.nestedRewardScroll; index < end; index++) {
            int nestedIndex = index;
            int rowY = top + 42 + (index - authoring.nestedRewardScroll) * 42;
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(180, 34);
                widget.withRenderer(WidgetRenderers.text(Component.translatable(
                    "gui.theseus.editor.nested_reward_label",
                    rewards.get(nestedIndex).id,
                    editorTypeLabel(EditorTypeRegistry.Kind.REWARD, rewards.get(nestedIndex).type, rewardChoice(rewards.get(nestedIndex)).label())
                )));
                widget.withCallback(() -> {
                    authoring.editingNestedRewardIndex = nestedIndex;
                    authoring.editingNestedReward = rewards.get(nestedIndex).copy();
                    modalHost.open(QuestModalHost.Modal.NESTED_REWARD_EDITOR);
                    host.dispatch(new RebuildWidgets());
                });
                widget.active = isRewardEditable(rewards.get(nestedIndex));
                widget.withTooltip(widget.active
                    ? editorText("gui.theseus.editor.edit_choice")
                    : Component.literal(unavailableReason(EditorTypeRegistry.Kind.REWARD, rewards.get(nestedIndex).type)));
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 200, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, -1));
                widget.active = nestedIndex > 0;
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 228, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, 1));
                widget.active = nestedIndex < rewards.size() - 1;
            }));
            host.addWidget(Widgets.button(widget -> {
                widget.withPosition(left + 256, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    List<QuestAuthoringSession.RewardDraft> updated = nestedRewards(authoring.editingReward);
                    updated.remove(nestedIndex);
                    setNestedRewards(authoring.editingReward, updated);
                    authoring.nestedRewardScroll = Math.min(authoring.nestedRewardScroll, Math.max(0, updated.size() - 4));
                    host.dispatch(new RebuildWidgets());
                });
                widget.withTooltip(Component.translatable("gui.theseus.editor.delete_choice"));
            }));
        }
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.add_choice")));
            widget.withCallback(() -> {
                rewardChooserSelectedIndex = 0;
                if (modalHost.isNestedRewardChooserOpen()) modalHost.close();
                else modalHost.open(QuestModalHost.Modal.NESTED_REWARD_CHOOSER);
                host.dispatch(new RebuildWidgets());
            });
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 148, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.done")));
            widget.withCallback(() -> {
                modalHost.close();
                host.dispatch(new RebuildWidgets());
            });
        }));
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 14).withSize(272, 20);
            widget.withTexture(null);
            widget.withRenderer(WidgetRenderers.text(Component.literal(authoring.editingReward.id)));
            widget.active = false;
        }));
    }

    private void moveNestedReward(int index, int direction) {
        List<QuestAuthoringSession.RewardDraft> rewards = nestedRewards(authoring.editingReward);
        int target = index + direction;
        if (target < 0 || target >= rewards.size()) return;
        java.util.Collections.swap(rewards, index, target);
        setNestedRewards(authoring.editingReward, rewards);
        host.dispatch(new RebuildWidgets());
    }

    private int taskListCapacity() {
        return Math.max(1, (height - 79) / 48);
    }

    private int maxCreateTaskScroll() {
        return Math.max(0, authoring.tasks.size() + 1 - taskListCapacity());
    }

    private void drawDraftRewards(GuiGraphicsExtractor graphics) {
        int x = width - detailsWidth() + 12;
        int cardWidth = detailsWidth() - 24;
        int y = 43;
        int end = Math.min(authoring.rewards.size(), createRewardScroll + rewardListCapacity());
        for (int index = createRewardScroll; index < end; index++) {
            int cardY = y + (index - createRewardScroll) * 48;
            QuestAuthoringSession.RewardDraft reward = authoring.rewards.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            renderDraftRewardIcon(graphics, reward, x + 7, cardY + 13);
            drawClippedText(graphics, rewardDisplayLabel(reward), x + 29, cardY + 9, cardWidth - 108, isRewardEditable(reward) ? 0xFFFFFFFF : 0xFFFFAA77);
            drawClippedText(graphics, reward.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (authoring.rewards.isEmpty()) graphics.text(font, Component.translatable("gui.theseus.editor.no_rewards_yet"), x, 34, 0xFF8E98A6, false);
    }

    void drawRewardChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean nested) {
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int index = 0; index < choices.size(); index++) {
            RewardChoice choice = choices.get(index);
            int rowY = top + 2 + index * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 && mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            if (index == rewardChooserSelectedIndex) {
                graphics.outline(left + 2, rowY, chooserWidth - 4, TASK_CHOOSER_ROW_HEIGHT - 1, ClientThemeLoader.active().genericControls().accent());
            }
            graphics.item(new ItemStack(choice.icon()), left + 4, rowY + 5);
            graphics.text(font, editorTypeLabel(EditorTypeRegistry.Kind.REWARD, choice.type(), choice.label()), left + 24, rowY + 9, 0xFFFFFFFF, false);
        }
    }

    private void drawDraftTasks(GuiGraphicsExtractor graphics) {
        int x = width - detailsWidth() + 12;
        int cardWidth = detailsWidth() - 24;
        int y = 43;
        int end = Math.min(authoring.tasks.size(), createTaskScroll + taskListCapacity());
        for (int index = createTaskScroll; index < end; index++) {
            int cardY = y + (index - createTaskScroll) * 48;
            QuestAuthoringSession.TaskDraft task = authoring.tasks.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            renderDraftTaskIcon(graphics, task, x + 7, cardY + 13);
            drawClippedText(graphics, taskDisplayLabel(task), x + 29, cardY + 9, cardWidth - 108, isTaskEditable(task) ? 0xFFFFFFFF : 0xFFFFAA77);
            drawClippedText(graphics, task.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (authoring.tasks.isEmpty()) {
            graphics.text(
                font,
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
        int left = nested ? taskEditorLeft() + 14 : width - detailsWidth() + 16;
        int top = nested ? taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, tasks().size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int visible = 0; visible < visibleCount; visible++) {
            TaskChoice choice = tasks().get(taskChooserScroll + visible);
            int rowY = top + 2 + visible * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 &&
                mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            if (taskChooserScroll + visible == taskChooserSelectedIndex) {
                graphics.outline(left + 2, rowY, chooserWidth - 4, TASK_CHOOSER_ROW_HEIGHT - 1, ClientThemeLoader.active().genericControls().accent());
            }
            graphics.item(new ItemStack(choice.icon()), left + 4, rowY + 5);
            graphics.text(
                font,
                editorTypeLabel(EditorTypeRegistry.Kind.TASK, choice.type(), choice.label()),
                left + 24,
                rowY + (choice.implemented() ? 9 : 3),
                choice.implemented() ? 0xFFFFFFFF : 0xFF9AA2AE,
                false
            );
            if (!choice.implemented()) graphics.text(
                font,
                Component.translatable("gui.theseus.editor.not_yet_implemented"),
                left + 24,
                rowY + 14,
                0xFF9AA4B2,
                false
            );
        }
    }

    void drawTaskEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = taskEditorLeft();
        int top = taskEditorTop();
        graphics.fill(left, top, left + 260, top + 300, 0xFF20242B);
        graphics.outline(left, top, 260, 300, 0xFF8A929F);
    }

    void drawRewardEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        graphics.fill(left, top, left + 300, top + 280, 0xFF20242B);
        graphics.outline(left, top, 300, 280, 0xFF8A929F);
    }

    void drawRewardModalForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (authoring.editingNestedReward != null) drawRewardEditorForeground(graphics, authoring.editingNestedReward, true);
        else if (modalHost.showsNestedRewards()) drawNestedRewardsForeground(graphics, mouseX, mouseY);
        else drawRewardEditorForeground(graphics, authoring.editingReward, false);
    }

    private void drawRewardEditorForeground(GuiGraphicsExtractor graphics, QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        RewardChoice choice = rewardChoice(reward);
        graphics.item(new ItemStack(choice.icon()), left + 14, top + 10);
        graphics.text(font, Component.translatable(
            nested ? "gui.theseus.editor.edit_reward_choice" : "gui.theseus.editor.edit_reward_type",
            editorTypeLabel(EditorTypeRegistry.Kind.REWARD, reward.type, choice.label())
        ), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.id"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.title_override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.icon_override"), left + REWARD_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftRewardIcon(graphics, reward, left + 23, top + 105);
        switch (reward.type) {
            case "theseus:xp" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.unit"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.item"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:loottable" -> graphics.text(font, Component.translatable("gui.theseus.editor.loot_table"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:command" -> graphics.text(font, Component.translatable("gui.theseus.editor.command"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:selectable" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.selection_amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.nested_rewards"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            default -> { }
        }
        if (!authoring.rewardEditorError.isEmpty()) graphics.textWithWordWrap(font, Component.literal(authoring.rewardEditorError), left + 14, top + 210, 272, 0xFFFF7777, false);
    }

    private void drawNestedRewardsForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        if (nestedRewards(authoring.editingReward).isEmpty()) graphics.text(font, Component.translatable("gui.theseus.editor.no_choices_yet"), left + 14, top + 48, 0xFF8E98A6, false);
        if (modalHost.isNestedRewardChooserOpen()) drawRewardChooser(graphics, mouseX, mouseY, true);
    }

    void drawTaskEditorForeground(GuiGraphicsExtractor graphics) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        TaskChoice choice = taskChoice(authoring.editingTask);
        graphics.item(new ItemStack(choice.icon()), left + 14, top + 10);
        graphics.text(font, Component.translatable(
            "gui.theseus.editor.edit_task_type",
            editorTypeLabel(EditorTypeRegistry.Kind.TASK, authoring.editingTask.type, choice.label())
        ), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.id"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.title_override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.icon_override"), left + TASK_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftTaskIcon(graphics, authoring.editingTask, left + 23, top + 105);
        switch (authoring.editingTask.type) {
            case "theseus:dummy" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.trigger_value"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.description"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.item_or_tag"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.collection"), left + 104, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:xp" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.unit"), left + 104, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.collection"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:kill_entity" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.entity"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:advancement" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.advancement_ids_comma_separated", null);
            case "theseus:biome" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.biome_or_tag", null);
            case "theseus:block_interaction" -> {
                taskFieldLabel(graphics, left, top, "gui.theseus.editor.block_or_tag", "gui.theseus.editor.component_data_predicate_json");
                graphics.text(font, Component.translatable("gui.theseus.editor.block_state_predicate_json"), left + 14, top + 209, 0xFFB8C0CC, false);
            }
            case "theseus:changed_dimension" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.from_dimension_optional"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.to_dimension_optional"), left + 136, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:check" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.player_data_predicate_json", null);
            case "theseus:composite" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.required_tasks"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.nested_tasks"), left + 104, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:entity_interaction" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.entity_or_tag", "gui.theseus.editor.component_data_predicate_json");
            case "theseus:item_interaction", "theseus:item_use" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.item_or_tag", "gui.theseus.editor.component_data_predicate_json");
            case "theseus:location" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.location_predicate_json", "gui.theseus.editor.description");
            case "theseus:recipe" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.recipe_ids_comma_separated", null);
            case "theseus:stat" -> {
                graphics.text(font, Component.translatable("gui.theseus.editor.statistic_id"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.translatable("gui.theseus.editor.target"), left + 170, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:structure" -> taskFieldLabel(graphics, left, top, "gui.theseus.editor.structure_or_tag", null);
            default -> {
            }
        }
        if (!authoring.taskEditorError.isEmpty()) graphics.textWithWordWrap(
            font,
            Component.literal(authoring.taskEditorError),
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
        if (nestedTasks(authoring.editingTask).isEmpty()) {
            graphics.text(
                font,
                Component.translatable("gui.theseus.editor.no_child_tasks_yet"),
                left + 14,
                top + 48,
                0xFF8E98A6,
                false
            );
        }
        if (modalHost.isNestedTaskChooserOpen()) drawTaskChooser(graphics, mouseX, mouseY, true);
    }

    private void taskFieldLabel(GuiGraphicsExtractor graphics, int left, int top, String firstKey, String secondKey) {
        graphics.text(font, editorText(firstKey), left + 14, top + 131, 0xFFB8C0CC, false);
        if (secondKey != null) graphics.text(font, editorText(secondKey), left + 14, top + 170, 0xFFB8C0CC, false);
    }

    private int taskEditorLeft() {
        return (width - 260) / 2;
    }

    private int taskEditorTop() {
        return (height - 300) / 2;
    }

    private int rewardEditorLeft() {
        return (width - 300) / 2;
    }

    private int rewardEditorTop() {
        return (height - 280) / 2;
    }

    private void renderDraftTaskIcon(GuiGraphicsExtractor graphics, QuestAuthoringSession.TaskDraft draft, int x, int y) {
        QuestDefinition.Task parsed = QuestDefinition.parse("editor", taskRoot(draft)).tasks().get(draft.id);
        if (parsed == null) graphics.item(new ItemStack(taskDisplayIcon(draft)), x, y);
        else QuestPresentation.renderTaskIcon(graphics, parsed, x, y);
    }

    private void renderDraftRewardIcon(GuiGraphicsExtractor graphics, QuestAuthoringSession.RewardDraft draft, int x, int y) {
        QuestDefinition.Reward parsed = QuestDefinition.parse("editor", rewardRoot(draft)).rewards().get(draft.id);
        if (parsed == null) graphics.item(new ItemStack(rewardDisplayIcon(draft)), x, y);
        else QuestPresentation.renderRewardIcon(graphics, parsed, x, y);
    }

    String validateTaskDraft(QuestAuthoringSession.TaskDraft task, int editedIndex) {
        List<QuestAuthoringSession.TaskDraft> peers = authoring.taskEditorParents.isEmpty() ? authoring.tasks : nestedTasks(authoring.taskEditorParents.getLast());
        return QuestDraftValidation.validateTaskDraft(task, peers, editedIndex, host.registryLookup());
    }

    private String validateRewardDraft(QuestAuthoringSession.RewardDraft reward, boolean nested) {
        List<QuestAuthoringSession.RewardDraft> peers = nested ? nestedRewards(authoring.editingReward) : authoring.rewards;
        int editedIndex = nested ? authoring.editingNestedRewardIndex : authoring.editingRewardIndex;
        return QuestDraftValidation.validateRewardDraft(reward, peers, editedIndex, nested, host.registryLookup());
    }

    boolean isTaskEditable(QuestAuthoringSession.TaskDraft task) {
        return editorResolution(EditorTypeRegistry.Kind.TASK, task.type).editable();
    }

    private boolean isRewardEditable(QuestAuthoringSession.RewardDraft reward) {
        return editorResolution(EditorTypeRegistry.Kind.REWARD, reward.type).editable();
    }

    String taskDisplayLabel(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task)
            ? editorTypeLabel(EditorTypeRegistry.Kind.TASK, task.type, taskChoice(task).label()).getString()
            : Component.translatable("gui.theseus.editor.unsupported_type", task.type).getString();
    }

    String rewardDisplayLabel(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward)
            ? editorTypeLabel(EditorTypeRegistry.Kind.REWARD, reward.type, rewardChoice(reward).label()).getString()
            : Component.translatable("gui.theseus.editor.unsupported_type", reward.type).getString();
    }

    private Item taskDisplayIcon(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task) ? taskChoice(task).icon() : Items.BARRIER;
    }

    private Item rewardDisplayIcon(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward) ? rewardChoice(reward).icon() : Items.BARRIER;
    }

    private EditorTypeRegistry.Resolution editorResolution(EditorTypeRegistry.Kind kind, String type) {
        Set<String> types = kind == EditorTypeRegistry.Kind.TASK ? serverTaskTypes : serverRewardTypes;
        return editorTypes().resolve(kind, type, types);
    }

    private String unavailableReason(EditorTypeRegistry.Kind kind, String type) {
        return switch (editorResolution(kind, type).availability()) {
            case EXECUTABLE_READ_ONLY -> Component.translatable("gui.theseus.editor.server_read_only_type", type).getString();
            case UNAVAILABLE_ON_SERVER -> Component.translatable("gui.theseus.editor.client_only_type", type).getString();
            case UNKNOWN_CONFIGURATION -> Component.translatable(
                "gui.theseus.editor.unknown_type",
                Component.translatable("gui.theseus.editor.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                type
            ).getString();
            case EXECUTABLE_EDITABLE -> Component.translatable("gui.theseus.editor.editable").getString();
        };
    }

    boolean handleChooserKey(KeyEvent event) {
        boolean taskChooser = modalHost.isTaskChooserOpen() || modalHost.isNestedTaskChooserOpen();
        boolean rewardChooser = modalHost.isRewardChooserOpen() || modalHost.isNestedRewardChooserOpen();
        if (!taskChooser && !rewardChooser) return false;
        int direction = switch (event.key()) {
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_DOWN -> 1;
            case InputConstants.KEY_TAB -> event.hasShiftDown() ? -1 : 1;
            default -> 0;
        };
        if (direction != 0) {
            if (taskChooser) {
                taskChooserSelectedIndex = Math.floorMod(taskChooserSelectedIndex + direction, tasks().size());
                if (taskChooserSelectedIndex < taskChooserScroll) taskChooserScroll = taskChooserSelectedIndex;
                else if (taskChooserSelectedIndex >= taskChooserScroll + TASK_CHOOSER_VISIBLE) {
                    taskChooserScroll = taskChooserSelectedIndex - TASK_CHOOSER_VISIBLE + 1;
                }
            } else {
                int choiceCount = modalHost.isNestedRewardChooserOpen() ? rewards().size() - 1 : rewards().size();
                rewardChooserSelectedIndex = Math.floorMod(rewardChooserSelectedIndex + direction, choiceCount);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (taskChooser) chooseTask(taskChooserSelectedIndex, modalHost.isNestedTaskChooserOpen());
            else chooseReward(rewardChooserSelectedIndex, modalHost.isNestedRewardChooserOpen());
            return true;
        }
        return false;
    }

    private void chooseTask(int index, boolean nested) {
        if (index < 0 || index >= tasks().size()) return;
        TaskChoice choice = tasks().get(index);
        if (!choice.implemented()) return;
        QuestAuthoringSession.TaskDraft previousTask = authoring.editingTask;
        if (nested) addNestedDraftTask(choice);
        else addDraftTask(choice);
        if (authoring.editingTask != previousTask) {
            modalHost.close();
            modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
        }
        host.dispatch(new RebuildWidgets());
    }

    private void chooseReward(int index, boolean nested) {
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        if (index < 0 || index >= choices.size()) return;
        addDraftReward(choices.get(index), nested);
        host.dispatch(new RebuildWidgets());
    }

    boolean taskChooserClicked(MouseButtonEvent event) {
        return taskChooserClicked(event, false);
    }

    boolean taskChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? taskEditorLeft() + 14 : width - detailsWidth() + 16;
        int top = nested ? taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, tasks().size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth ||
            event.y() < top || event.y() >= top + chooserHeight) {
            modalHost.close();
            host.dispatch(new RebuildWidgets());
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < visibleCount) {
            taskChooserSelectedIndex = taskChooserScroll + row;
            chooseTask(taskChooserSelectedIndex, nested);
        }
        return true;
    }

    boolean rewardChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
            : rewards();
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth || event.y() < top || event.y() >= top + chooserHeight) {
            modalHost.close();
            host.dispatch(new RebuildWidgets());
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < choices.size()) {
            rewardChooserSelectedIndex = row;
            chooseReward(rewardChooserSelectedIndex, nested);
        }
        return true;
    }

    private void addDraftTask(TaskChoice choice) {
        QuestAuthoringSession.TaskDraft task = createTaskDraft(choice, authoring.tasks);
        if (task == null) return;
        authoring.editingTaskIndex = -1;
        authoring.editingTask = task;
        authoring.taskEditorError = "";
    }

    private void addNestedDraftTask(TaskChoice choice) {
        QuestAuthoringSession.TaskDraft task = createTaskDraft(choice, nestedTasks(authoring.editingTask));
        if (task == null) return;
        authoring.taskEditorParents.add(authoring.editingTask);
        authoring.taskEditorParentIndexes.add(authoring.editingTaskIndex);
        authoring.editingTaskIndex = -1;
        authoring.editingTask = task;
        authoring.taskEditorError = "";
    }

    private QuestAuthoringSession.TaskDraft createTaskDraft(
        TaskChoice choice,
        List<QuestAuthoringSession.TaskDraft> siblings
    ) {
        EditorTypeRegistry.Descriptor descriptor = editorTypes().resolve(EditorTypeRegistry.Kind.TASK, choice.type());
        if (!descriptor.editable()) {
            host.dispatch(new ShowMessage(descriptor.availabilityReason()));
            return null;
        }
        return QuestEditorCatalog.createTaskDraft(choice, siblings);
    }

    private void addDraftReward(RewardChoice choice, boolean nested) {
        EditorTypeRegistry.Descriptor descriptor = editorTypes().resolve(EditorTypeRegistry.Kind.REWARD, choice.type());
        if (!descriptor.editable()) {
            host.dispatch(new ShowMessage(descriptor.availabilityReason()));
            return;
        }
        List<QuestAuthoringSession.RewardDraft> rewards = nested ? nestedRewards(authoring.editingReward) : authoring.rewards;
        QuestAuthoringSession.RewardDraft reward = QuestEditorCatalog.createRewardDraft(choice, rewards);
        if (nested) {
            authoring.editingNestedRewardIndex = -1;
            authoring.editingNestedReward = reward;
            modalHost.close();
            modalHost.open(QuestModalHost.Modal.NESTED_REWARD_EDITOR);
        } else {
            authoring.editingRewardIndex = -1;
            authoring.editingReward = reward;
            modalHost.close();
            modalHost.open(QuestModalHost.Modal.REWARD_EDITOR);
        }
    }
    interface Host {
        void addWidget(AbstractWidget widget);
        int detailsWidth();
        QuestDraftValidation.RegistryLookup registryLookup();
        String draftValidationError();
        boolean validCreateQuestDraft();
        boolean mutationPending();
        void dispatch(Action action);
    }

    sealed interface Action permits RebuildWidgets, OpenPicker, OpenRawInspector,
        RequestModalDiscard, RequestDiscard, ClosePicker, ShowMessage,
        OpenDescriptionEditor, CloseDraft, ClearSelectedQuest,
        RemoveExistingQuestFromChapter, ConfirmCreateQuest, UpdateDraftGroupPosition {}

    record RebuildWidgets() implements Action {}
    record OpenPicker(Picker picker, PickerTarget target) implements Action {}
    record OpenRawInspector(String title, JsonObject source) implements Action {}
    record RequestModalDiscard(Runnable action) implements Action {}
    record RequestDiscard(Runnable action) implements Action {}
    record ClosePicker() implements Action {}
    record ShowMessage(String message) implements Action {}
    record OpenDescriptionEditor() implements Action {}
    record CloseDraft() implements Action {}
    record ClearSelectedQuest() implements Action {}
    record RemoveExistingQuestFromChapter() implements Action {}
    record ConfirmCreateQuest() implements Action {}
    record UpdateDraftGroupPosition() implements Action {}

}
