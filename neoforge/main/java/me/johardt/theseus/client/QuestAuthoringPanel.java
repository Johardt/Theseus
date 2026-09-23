package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.base.renderer.WidgetRenderer;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.List;
import java.util.Set;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.QuestScreen.Picker;
import me.johardt.theseus.client.QuestScreen.PickerTarget;
import me.johardt.theseus.client.QuestScreen.DetailTab;
import me.johardt.theseus.core.EditorTypeRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.GridLayout;
import earth.terrarium.olympus.client.components.compound.LayoutWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;

/** Coordinates the quest-authoring state and its focused UI collaborators. */
final class QuestAuthoringPanel {
    final QuestAuthoringPanelDrafts draftUi = new QuestAuthoringPanelDrafts(this);
    final QuestAuthoringPanelTaskEditor taskEditor = new QuestAuthoringPanelTaskEditor(this);
    final QuestAuthoringPanelRewardEditor rewardEditor = new QuestAuthoringPanelRewardEditor(this);
    final QuestAuthoringPanelDock dockUi = new QuestAuthoringPanelDock(this);
    static final Gson GSON = new Gson();
    static final int TASK_CHOOSER_VISIBLE = 6;
    static final int TASK_CHOOSER_ROW_HEIGHT = 26;
    static final int EDITOR_LIST_ACTION_WIDTH = 19;
    static final int EDITOR_LIST_ACTION_HEIGHT = 20;
    static final int EDITOR_LIST_ACTION_ICON_SIZE = 11;
    static final int TASK_ICON_LABEL_X = 84;
    static final int TASK_RAW_INSPECTOR_X = 158;
    static final int REWARD_ICON_LABEL_X = 84;
    static final int REWARD_RAW_INSPECTOR_X = 198;
    static final WidgetSprites CLOSE_BUTTON = new WidgetSprites(
        sprite("heading/close"), sprite("heading/close_selected")
    );

    final AuthorMode authoring;
    final QuestModalHost modalHost;
    final Set<String> serverTaskTypes;
    final Set<String> serverRewardTypes;
    final Host host;
    DetailTab createQuestTab = DetailTab.OVERVIEW;
    int draftOverviewScrollY;
    LayoutWidget<GridLayout> draftOverviewScrollContainer;
    Button createConfirmButton;
    Font font;
    int width;
    int height;
    int taskChooserScroll;
    int taskChooserSelectedIndex;
    int createTaskScroll;
    int rewardChooserScroll;
    int rewardChooserSelectedIndex;
    int createRewardScroll;

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

    void resetDraftTaskScroll() { createTaskScroll = 0; }

    void resetDraftScrolls() {
        createTaskScroll = 0;
        createRewardScroll = 0;
    }

    void clampDraftTaskScroll() {
        createTaskScroll = Math.min(createTaskScroll, draftUi.maxCreateTaskScroll());
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
            Math.min(draftUi.maxCreateTaskScroll(), createTaskScroll - (int) Math.signum(scrollY))
        );
    }

    void scrollDraftRewardList(double scrollY) {
        createRewardScroll = Math.max(
            0,
            Math.min(draftUi.maxCreateRewardScroll(), createRewardScroll - (int) Math.signum(scrollY))
        );
    }

    void resetOverviewScroll() {
        draftOverviewScrollY = 0;
        draftOverviewScrollContainer = null;
    }

    void captureOverviewScroll() {
        if (draftOverviewScrollContainer != null) {
            draftOverviewScrollY = draftOverviewScrollContainer.getYScroll();
        }
    }

    JsonObject draftDisplay() {
        JsonElement display = authoring.draft().snapshot().get("display");
        return display != null && display.isJsonObject() ? display.getAsJsonObject() : new JsonObject();
    }

    static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "textures/gui/" + path + ".png");
    }

    WidgetRenderer<Button> listActionRenderer(String action) {
        return WidgetRenderers.center(
            EDITOR_LIST_ACTION_ICON_SIZE,
            EDITOR_LIST_ACTION_ICON_SIZE,
            WidgetRenderers.sprite(new net.minecraft.client.gui.components.WidgetSprites(
                sprite("heading/editor/" + action), sprite("heading/editor/" + action)
            ))
        );
    }

    void addRawInspectorButton(int x, int y, int width, Runnable open) {
        host.addWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.raw_json")));
            widget.withCallback(open);
            widget.withTooltip(Component.translatable("gui.theseus.editor.inspect_this_configuration_without_editing_it"));
        }));
    }

    void drawClippedText(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, Component.literal(text), x, y, color, false);
    }

    EditorTypeRegistry.Resolution editorResolution(EditorTypeRegistry.Kind kind, String type) {
        Set<String> types = kind == EditorTypeRegistry.Kind.TASK ? serverTaskTypes : serverRewardTypes;
        return EditorTypeRegistry.registered().resolve(kind, type, types);
    }

    String unavailableReason(EditorTypeRegistry.Kind kind, String type) {
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
