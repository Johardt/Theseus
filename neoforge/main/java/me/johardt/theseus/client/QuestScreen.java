package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestMutationCoordinator;
import me.johardt.theseus.core.EditorTypeRegistry;
import me.johardt.theseus.core.QuestNetwork;
import me.johardt.theseus.client.QuestClientSnapshot.ChapterDisplay;
import me.johardt.theseus.client.QuestClientSnapshot.ClientQuest;
import me.johardt.theseus.client.description.MarkdownEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;

public final class QuestScreen extends Screen {

    static final Gson GSON = new Gson();
    final QuestImportController importController = new QuestImportController();
    static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    static final int NODE_WIDTH = 24;
    static final int NODE_HEIGHT = 24;
    static final int TASK_CHOOSER_VISIBLE = 6;
    static final int TASK_CHOOSER_ROW_HEIGHT = 26;
    static final int HEADER_ROW_Y = 1;
    static final int HEADER_ROW_HEIGHT = 20;
    static final int HEADER_ROW_GAP = 3;
    static final int HEADER_CANVAS_GAP = 9;
    static final int HEADER_STATUS_MAX_LINES = 3;
    static final int HEADER_STATUS_LINE_HEIGHT = 10;
    static final int HEADER_STATUS_TEXT_HEIGHT = 9;
    static final int HEADER_STATUS_PADDING = 4;
    static final int HEADER_ACTION_WIDTH = 78;
    static final int HEADER_ACTION_GAP = 7;
    static final int EDITOR_LIST_ACTION_WIDTH = 19;
    static final int EDITOR_LIST_ACTION_HEIGHT = 20;
    static final int EDITOR_LIST_ACTION_ICON_SIZE = 11;
    static final int CHAPTER_LIST_TOP = 34;
    static final int CHAPTER_ROW_HEIGHT = 23;
    static final int CHAPTER_ROW_CONTENT_HEIGHT = 20;
    static final int CHAPTER_ADD_SLOT_HEIGHT = 28;
    static final int CHAPTER_ICON_COLUMN_WIDTH = 18;
    // Shared by widget construction and manual foreground labels. Keeping the
    // lanes explicit prevents a later action button from occupying label space.
    static final int TASK_ICON_LABEL_X = 84;
    static final int TASK_RAW_INSPECTOR_X = 158;
    static final int REWARD_ICON_LABEL_X = 84;
    static final int REWARD_RAW_INSPECTOR_X = 198;
    static final int MARKDOWN_ACTION_SIZE = 18;
    static final int MARKDOWN_ACTION_GAP = 2;
    static final WidgetSprites CLOSE_BUTTON = new WidgetSprites(
        QuestScreenRenderer.sprite("heading/close"),
        QuestScreenRenderer.sprite("heading/close_selected")
    );
    static final Identifier MINIMAP_TOGGLE = QuestScreenRenderer.sprite("heading/toggle_minimap");
    static final Identifier MINIMAP_TOGGLE_SELECTED = QuestScreenRenderer.sprite("heading/toggle_minimap_selected");
    static final Identifier SHOW_GRID = QuestScreenRenderer.sprite("heading/show_grid");
    static final Identifier SHOW_GRID_SELECTED = QuestScreenRenderer.sprite("heading/show_grid_selected");
    static final Identifier SNAP_TO_GRID = QuestScreenRenderer.sprite("heading/snap_to_grid");
    static final Identifier SNAP_TO_GRID_SELECTED = QuestScreenRenderer.sprite("heading/snap_to_grid_selected");
    final List<ClientQuest> quests;
    final List<String> chapters;
    final Map<String, ChapterDisplay> chapterDisplays;
    final Set<String> loadedChapters;
    final ChapterListState chapterListState;
    final Set<String> serverTaskTypes;
    final Set<String> serverRewardTypes;
    final Set<String> serverIconTypes;
    final Map<String, Set<String>> rewardSelections = new HashMap<>();
    final QuestGraphLayout.ViewportMemory graphViewport;
    String selectedQuestId;
    String linkSourceId;
    String draggingQuestId;
    boolean questMoved;
    boolean panning;
    String group;
    QuestMode mode;
    final AuthorMode authoring;
    MarkdownEditBox descriptionEditor;
    String descriptionEditorValue = "";
    int descriptionPreviewScroll;
    int descriptionPreviewMaxScroll;
    String rawInspectorTitle = "Raw JSON";
    String rawInspectorJson = "{}";
    Picker picker = Picker.NONE;
    PickerTarget pickerTarget = PickerTarget.QUEST_ICON;
    EditBox pickerSearch;
    String editorMessage = "";
    boolean editorMessageSuccess;
    boolean clipboardMutationPending;
    EditBox pasteIdField;
    final QuestMutationCoordinator mutations;
    final QuestModalHost modalHost;
    final LocalQuestFileOpener questFileOpener;
    List<QuestDiagnostics.Diagnostic> diagnostics = List.of();
    int diagnosticsScroll;
    int importScroll;
    final Map<String, EditBox> importIdFields = new HashMap<>();
    int pickerScroll;
    final Map<PickerTarget, Integer> pickerScrollByTarget = new HashMap<>();
    final QuestMinimapPanel minimapPanel;
    final QuestAuthoringPanel authoringPanel;
    final QuestDetailsPanel detailsPanel;
    DetailTab detailTab = DetailTab.OVERVIEW;
    /** Retained while widget trees rebuild so settings buttons do not jump the draft back to the top. */
    boolean detailsOpen;
    boolean sidebarOpen = true;
    String chapterEditorOriginal;
    String chapterEditorName = "";
    String chapterEditorIcon = "minecraft:map";
    boolean chapterEditorIconEnabled = true;
    String chapterEditorBackground = "";
    int chapterEditorBackgroundOpacity = 100;
    String chapterEditorError = "";
    boolean chapterDeleteArmed;
    String chapterEditorBaseline;
    boolean graphFocused;
    boolean chapterListFocused;
    int focusedChapterIndex = -1;
    QuestContextMenu contextMenu;
    double pendingPasteWorldX;
    double pendingPasteWorldY;
    boolean pendingPastePosition;
    int nextQuestFileRequestId;
    int pendingQuestFileRequestId = -1;
    String pendingQuestFileId;
    QuestModalHost.ProgressResetTarget progressResetTarget;

    final QuestClientSnapshot snapshots;
    final QuestScreenLayout layout = new QuestScreenLayout(this);
    final QuestScreenWidgets widgets = new QuestScreenWidgets(this);
    final QuestScreenEditor editor = new QuestScreenEditor(this);
    final QuestScreenRenderer renderer = new QuestScreenRenderer(this);
    final QuestScreenActions actions = new QuestScreenActions(this);
    final QuestScreenInput input = new QuestScreenInput(this);
    final QuestScreenImports imports = new QuestScreenImports(this);

    int guiWidth() { return width; }
    int guiHeight() { return height; }
    net.minecraft.client.gui.Font guiFont() { return font; }
    Minecraft guiMinecraft() { return minecraft; }
    <T extends net.minecraft.client.gui.components.AbstractWidget> T addScreenWidget(T widget) { return addRenderableWidget(widget); }
    java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> screenChildren() { return children(); }
    net.minecraft.client.gui.components.events.GuiEventListener screenFocused() { return getFocused(); }
    void setScreenInitialFocus(net.minecraft.client.gui.components.events.GuiEventListener target) { setInitialFocus(target); }
    void setScreenFocused(net.minecraft.client.gui.components.events.GuiEventListener target) { setFocused(target); }
    boolean parentKeyPressed(KeyEvent event) { return super.keyPressed(event); }
    boolean parentCharTyped(CharacterEvent event) { return super.charTyped(event); }
    boolean parentMouseClicked(MouseButtonEvent event, boolean doubleClick) { return super.mouseClicked(event, doubleClick); }
    boolean parentMouseReleased(MouseButtonEvent event) { return super.mouseReleased(event); }
    boolean parentMouseDragged(MouseButtonEvent event, double dragX, double dragY) { return super.mouseDragged(event, dragX, dragY); }
    boolean parentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY); }
    void parentExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) { super.extractRenderState(graphics, mouseX, mouseY, partialTick); }
    void closeScreen() { super.onClose(); }
    void performDefaultClickEvent(net.minecraft.network.chat.ClickEvent clickEvent, Minecraft client, QuestScreen listener) { defaultHandleClickEvent(clickEvent, client, listener); }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        if (contextMenu != null && contextMenu.isOpen()) {
            int selected = contextMenu.selectedIndex();
            if (selected >= 0 && selected < contextMenu.entries().size()) {
                output.add(NarratedElementType.TITLE, Component.translatable(
                    "gui.theseus.editor.menu_selection",
                    contextMenu.entries().get(selected).label()
                ));
                output.add(NarratedElementType.USAGE, Component.translatable("gui.theseus.editor.menu_keyboard_usage"));
            }
        } else if (modalHost.isTaskChooserOpen() || modalHost.isNestedTaskChooserOpen()) {
            TaskChoice choice = tasks().get(Math.clamp(authoringPanel.taskChooserSelectedIndex, 0, tasks().size() - 1));
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.editor.chooser_selection",
                QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.TASK, choice.type(), choice.label())
            ));
            output.add(NarratedElementType.USAGE, Component.translatable("gui.theseus.editor.chooser_keyboard_usage"));
        } else if (modalHost.isRewardChooserOpen() || modalHost.isNestedRewardChooserOpen()) {
            List<RewardChoice> choices = modalHost.isNestedRewardChooserOpen()
                ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
                : rewards();
            RewardChoice choice = choices.get(Math.clamp(authoringPanel.rewardChooserSelectedIndex, 0, choices.size() - 1));
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.editor.chooser_selection",
                QuestEditorCatalog.editorTypeLabel(EditorTypeRegistry.Kind.REWARD, choice.type(), choice.label())
            ));
            output.add(NarratedElementType.USAGE, Component.translatable("gui.theseus.editor.chooser_keyboard_usage"));
        } else if (graphFocused && actions.selected() != null) {
            ClientQuest quest = actions.selected();
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.quest.graph_selection",
                quest.definition().title(),
                QuestPresentation.status(quest.unlocked(), quest.claimed(), quest.complete())
            ));
            output.add(NarratedElementType.USAGE, Component.translatable(
                mode.isAuthoring()
                    ? "gui.theseus.quest.graph_editor_keyboard_usage"
                    : "gui.theseus.quest.graph_keyboard_usage"
            ));
        }
    }

    public QuestScreen(JsonObject snapshot) {
        this(new QuestClientSnapshot(snapshot), null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        this(new QuestClientSnapshot(snapshot), previous);
    }

    QuestScreen(QuestClientSnapshot snapshots, QuestScreen previous) {
        super(Component.translatable("gui.theseus.editor.theseus_quests"));
        this.snapshots = snapshots;
        this.quests = snapshots.quests();
        this.chapters = snapshots.chapters();
        this.chapterDisplays = snapshots.chapterDisplays();
        this.loadedChapters = snapshots.loadedChapters();
        this.serverTaskTypes = snapshots.serverTaskTypes();
        this.serverRewardTypes = snapshots.serverRewardTypes();
        this.serverIconTypes = snapshots.serverIconTypes();
        this.authoring = previous == null
            ? new AuthorMode(QuestSurfaceLayout.DEFAULT_ICON_SIZE)
            : previous.authoring.copy();
        this.mode = previous != null && previous.mode.isAuthoring()
            ? this.authoring
            : new PlayMode();
        this.graphViewport = previous == null
            ? new QuestGraphLayout.ViewportMemory()
            : previous.graphViewport.copy();
        this.minimapPanel = previous == null
            ? new QuestMinimapPanel()
            : previous.minimapPanel.copyForRebuild();
        this.detailsPanel = previous == null
            ? new QuestDetailsPanel()
            : previous.detailsPanel.copyForRebuild();
        this.selectedQuestId = previous == null ? null : previous.selectedQuestId;
        this.linkSourceId = previous == null ? null : previous.linkSourceId;
        this.draggingQuestId = previous == null ? null : previous.draggingQuestId;
        this.questMoved = previous != null && previous.questMoved;
        this.panning = previous != null && previous.panning;
        this.chapterListState = previous == null
            ? new ChapterListState()
            : previous.chapterListState.copy();
        this.mutations = previous == null ? new QuestMutationCoordinator() : previous.mutations.copy();
        this.modalHost = previous == null ? new QuestModalHost() : previous.modalHost.copy();
        QuestAuthoringPanel.Host authoringHost = new AuthoringPanelHost();
        this.authoringPanel = previous == null
            ? new QuestAuthoringPanel(authoring, modalHost, serverTaskTypes, serverRewardTypes, authoringHost)
            : previous.authoringPanel.copyForRebuild(
                authoring, modalHost, serverTaskTypes, serverRewardTypes, authoringHost
            );
        this.questFileOpener = previous == null ? new LocalQuestFileOpener() : previous.questFileOpener;
        this.nextQuestFileRequestId = previous == null ? 0 : previous.nextQuestFileRequestId;
        this.pendingQuestFileRequestId = previous == null ? -1 : previous.pendingQuestFileRequestId;
        this.pendingQuestFileId = previous == null ? null : previous.pendingQuestFileId;
        this.progressResetTarget = previous == null ? null : previous.progressResetTarget;
        this.rawInspectorTitle = previous == null ? "Raw JSON" : previous.rawInspectorTitle;
        this.rawInspectorJson = previous == null ? "{}" : previous.rawInspectorJson;
        this.diagnostics = previous == null ? List.of() : previous.diagnostics;
        this.diagnosticsScroll = previous == null ? 0 : previous.diagnosticsScroll;
        this.importScroll = previous == null ? 0 : previous.importScroll;
        Set<String> groups = actions.groups();
        this.group =
            previous != null && groups.contains(previous.group)
                ? previous.group
                : groups.stream().findFirst().orElse("Main");
        this.chapterListState.setChapterCount(groups.size());
        if (previous != null
            && !new ArrayList<>(previous.actions.groups()).equals(new ArrayList<>(groups))) {
            this.chapterListState.reset();
        }
        this.chapterListState.ensureVisible(new ArrayList<>(groups).indexOf(this.group));
        this.chapterListFocused = previous != null && previous.chapterListFocused;
        this.focusedChapterIndex = previous == null ? -1 : previous.focusedChapterIndex;
        this.detailTab =
            previous == null ? DetailTab.OVERVIEW : previous.detailTab;
        this.detailsOpen = previous != null && previous.detailsOpen;
        this.sidebarOpen = previous == null || previous.sidebarOpen;
        this.descriptionEditorValue = previous == null ? "" : previous.descriptionEditorValue;
        this.descriptionPreviewScroll = previous == null ? 0 : previous.descriptionPreviewScroll;
        this.editorMessage = previous == null ? "" : previous.editorMessage;
        this.editorMessageSuccess = previous != null && previous.editorMessageSuccess;
        this.clipboardMutationPending = previous != null && previous.clipboardMutationPending;
        if (previous != null) this.pickerScrollByTarget.putAll(previous.pickerScrollByTarget);
        this.chapterEditorBaseline = previous == null ? null : previous.chapterEditorBaseline;
        this.graphFocused = previous != null && previous.graphFocused;
        this.contextMenu = null;
        this.pendingPastePosition = previous != null && previous.pendingPastePosition;
        this.pendingPasteWorldX = previous == null ? 0 : previous.pendingPasteWorldX;
        this.pendingPasteWorldY = previous == null ? 0 : previous.pendingPasteWorldY;
        if (previous != null) previous.rewardSelections.forEach((key, value) ->
            this.rewardSelections.put(key, new LinkedHashSet<>(value))
        );
    }

    /** Requests full task/reward/description data for the active chapter. */
    public void requestActiveChapter() {
        requestChapter(group);
    }

    void requestChapter(String chapter) {
        if (snapshots.requestChapter(chapter)) {
            ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("load_chapter", chapter));
        }
    }

    public void mergeSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        snapshots.accept(snapshot);
        snapshotChanged();
    }

    void snapshotChanged() {
        graphViewport.activateChapter(group, layout.graphCanvasBounds(), layout.graphWorldBounds());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        widgets.initialize();
    }

    @Override
    protected void rebuildWidgets() {
        authoringPanel.captureOverviewScroll();
        boolean closingPicker = picker == Picker.NONE && pickerSearch != null;
        super.rebuildWidgets();
        if (closingPicker) {
            pickerSearch = null;
        }
        // Rebuilding an overlay replaces the widget tree. Restore focus only
        // after an overlay transition so keyboard navigation cannot fall into
        // the graph below it.
        if (modalHost.consumeFocusRestoreRequest() || closingPicker) {
            children().stream().findFirst().ifPresent(this::setInitialFocus);
        }
    }

    public void handleEditorResult(QuestNetwork.EditorResultPayload result) {
        editor.handleEditorResult(result);
    }

    /** Preserves the draft while detaching requests whose server outcome is unknown. */
    public void handleConnectionLost() {
        editor.handleConnectionLost();
    }

    @Override
    public void extractBackground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0xD915171C);
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        renderer.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public void handleOpenQuestFileResult(QuestNetwork.OpenQuestFileResultPayload result) {
        actions.handleOpenQuestFileResult(result);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return input.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return input.charTyped(event);
    }

    /** Parsing is independent per file and failed files remain removable. */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        imports.onFilesDrop(paths);
    }

    public void removeImportFile(String key) {
        imports.removeImportFile(key);
    }

    public boolean changeImportId(String key, String id) {
        return imports.changeImportId(key, id);
    }

    @Override
    public void onClose() {
        editor.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return input.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return input.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(
        MouseButtonEvent event,
        double dragX,
        double dragY
    ) {
        return input.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        return input.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private final class AuthoringPanelHost implements QuestAuthoringPanel.Host {
        @Override public void addWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
            QuestScreen.this.addRenderableWidget(widget);
        }
        @Override public int detailsWidth() { return QuestScreen.this.layout.detailsWidth(); }
        @Override public QuestDraftValidation.RegistryLookup registryLookup() {
            return QuestScreen.this.editor.draftRegistryLookup();
        }
        @Override public String draftValidationError() { return QuestScreen.this.editor.draftValidationError(); }
        @Override public boolean validCreateQuestDraft() { return QuestScreen.this.editor.validCreateQuestDraft(); }
        @Override public boolean mutationPending() { return mutations.isPending(); }
        @Override public void dispatch(QuestAuthoringPanel.Action action) {
            if (action instanceof QuestAuthoringPanel.RebuildWidgets) QuestScreen.this.rebuildWidgets();
            else if (action instanceof QuestAuthoringPanel.OpenPicker open) {
                QuestScreen.this.editor.openPicker(open.picker(), open.target());
            } else if (action instanceof QuestAuthoringPanel.OpenRawInspector open) {
                QuestScreen.this.widgets.openRawInspector(open.title(), open.source());
            } else if (action instanceof QuestAuthoringPanel.RequestModalDiscard request) {
                QuestScreen.this.editor.requestModalDiscard(request.action());
            } else if (action instanceof QuestAuthoringPanel.RequestDiscard request) {
                QuestScreen.this.editor.requestDiscard(request.action());
            } else if (action instanceof QuestAuthoringPanel.ClosePicker) QuestScreen.this.editor.closePicker();
            else if (action instanceof QuestAuthoringPanel.ShowMessage message) {
                editorMessage = message.message();
                editorMessageSuccess = false;
            } else if (action instanceof QuestAuthoringPanel.OpenDescriptionEditor) {
                QuestScreen.this.widgets.openDescriptionEditor();
            } else if (action instanceof QuestAuthoringPanel.CloseDraft) QuestScreen.this.editor.closeDraft();
            else if (action instanceof QuestAuthoringPanel.ClearSelectedQuest) selectedQuestId = null;
            else if (action instanceof QuestAuthoringPanel.RemoveExistingQuestFromChapter) {
                QuestScreen.this.editor.removeExistingQuestFromChapter();
            } else if (action instanceof QuestAuthoringPanel.ConfirmCreateQuest) {
                QuestScreen.this.editor.confirmCreateQuest();
            } else if (action instanceof QuestAuthoringPanel.UpdateDraftGroupPosition) {
                QuestScreen.this.editor.updateDraftGroupPosition();
            }
        }
    }

    enum DetailTab {
        OVERVIEW("gui.theseus.editor.overview"),
        TASKS("gui.theseus.editor.tasks"),
        REWARDS("gui.theseus.editor.rewards");

        final String translationKey;

        DetailTab(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    enum Picker {
        NONE,
        ICON,
        ENTITY,
        BACKGROUND
    }

    enum PickerTarget {
        QUEST_ICON,
        TASK_ICON,
        TASK_ITEM,
        TASK_BLOCK,
        TASK_ENTITY,
        REWARD_ICON,
        REWARD_ITEM,
        CHAPTER_ICON
    }

    record HeaderLayout(
        int editX,
        int helpX,
        int fitX,
        int gridX,
        int snapX,
        int importX,
        int diagnosticsX,
        int actionY,
        int importY,
        int diagnosticsY,
        int statusY,
        int canvasTop,
        List<String> statusLines,
        int statusBoxHeight
    ) {}

}
