package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.base.BaseWidget;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.base.renderer.WidgetRenderer;
import earth.terrarium.olympus.client.components.compound.LayoutWidget;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import earth.terrarium.olympus.client.components.string.TextWidget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestDraft;
import me.johardt.theseus.core.QuestIconDefinition;
import me.johardt.theseus.core.QuestMutation;
import me.johardt.theseus.core.QuestMutationCoordinator;
import me.johardt.theseus.core.RegistryValidation;
import me.johardt.theseus.core.EditorTypeRegistry;
import me.johardt.theseus.core.QuestNetwork;
import me.johardt.theseus.client.description.DescriptionDocument;
import me.johardt.theseus.client.description.DescriptionParser;
import me.johardt.theseus.client.description.QuestDescriptionRenderer;
import me.johardt.theseus.client.description.MarkdownEditBox;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.fml.loading.FMLPaths;

import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;

/** Player-facing quest graph. Olympus supplies controls; Theseus owns graph semantics. */
public final class QuestScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static QuestDraft clipboardDraft;
    private static String clipboardSourceId;
    private static boolean clipboardMove;
    private final QuestImportController importController = new QuestImportController();
    private static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    private static final int NODE_WIDTH = 24;
    private static final int NODE_HEIGHT = 24;
    private static final int TASK_CHOOSER_VISIBLE = 6;
    private static final int TASK_CHOOSER_ROW_HEIGHT = 26;
    private static final int HEADER_ROW_Y = 1;
    private static final int HEADER_ROW_HEIGHT = 20;
    private static final int HEADER_ROW_GAP = 3;
    private static final int HEADER_CANVAS_GAP = 9;
    private static final int HEADER_ACTION_WIDTH = 78;
    private static final int HEADER_ACTION_GAP = 7;
    private static final int EDITOR_LIST_ACTION_WIDTH = 19;
    private static final int EDITOR_LIST_ACTION_HEIGHT = 20;
    private static final int EDITOR_LIST_ACTION_ICON_SIZE = 11;
    private static final int CHAPTER_LIST_TOP = 34;
    private static final int CHAPTER_ROW_HEIGHT = 23;
    private static final int CHAPTER_ROW_CONTENT_HEIGHT = 20;
    private static final int CHAPTER_ADD_SLOT_HEIGHT = 28;
    private static final int CHAPTER_ICON_COLUMN_WIDTH = 18;
    // Shared by widget construction and manual foreground labels. Keeping the
    // lanes explicit prevents a later action button from occupying label space.
    private static final int TASK_ICON_LABEL_X = 84;
    private static final int TASK_RAW_INSPECTOR_X = 158;
    private static final int REWARD_ICON_LABEL_X = 84;
    private static final int REWARD_RAW_INSPECTOR_X = 198;
    private static final Identifier DEPENDENCY_ARROW = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/arrow.png"
    );
    private static final Identifier DEFAULT_QUEST_FRAME = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/quest_backgrounds/default.png"
    );
    private static final int MARKDOWN_ACTION_SIZE = 18;
    private static final int MARKDOWN_ACTION_GAP = 2;
    private static final WidgetSprites CLOSE_BUTTON = new WidgetSprites(
        sprite("heading/close"),
        sprite("heading/close_selected")
    );
    private static final Identifier MINIMAP_TOGGLE = sprite("heading/toggle_minimap");
    private static final Identifier MINIMAP_TOGGLE_SELECTED = sprite("heading/toggle_minimap_selected");
    private static final Identifier SHOW_GRID = sprite("heading/show_grid");
    private static final Identifier SHOW_GRID_SELECTED = sprite("heading/show_grid_selected");
    private static final Identifier SNAP_TO_GRID = sprite("heading/snap_to_grid");
    private static final Identifier SNAP_TO_GRID_SELECTED = sprite("heading/snap_to_grid_selected");
    private static final List<Identifier> QUEST_BACKGROUNDS = List.of(
        "default", "circles", "diamonds", "gears", "hearts", "hexagons",
        "octagons", "pentagons", "rounded_squares"
    ).stream().map(name -> Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/quest_backgrounds/" + name + ".png"
    )).toList();
    private final List<ClientQuest> quests = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final Map<String, ChapterDisplay> chapterDisplays = new HashMap<>();
    private final Set<String> loadedChapters = new LinkedHashSet<>();
    private final Set<String> pendingChapterLoads = new LinkedHashSet<>();
    private final ChapterListState chapterListState;
    private final Set<String> serverTaskTypes = new LinkedHashSet<>();
    private final Set<String> serverRewardTypes = new LinkedHashSet<>();
    private final Set<String> serverIconTypes = new LinkedHashSet<>();
    private final Map<String, Set<String>> rewardSelections = new HashMap<>();
    private final QuestGraphLayout.ViewportMemory graphViewport;
    private String selectedQuestId;
    private String linkSourceId;
    private String draggingQuestId;
    private boolean questMoved;
    private boolean panning;
    private String group;
    private QuestMode mode;
    private final AuthorMode authoring;
    private MarkdownEditBox descriptionEditor;
    private String descriptionEditorValue = "";
    private int descriptionPreviewScroll;
    private int descriptionPreviewMaxScroll;
    private String rawInspectorTitle = "Raw JSON";
    private String rawInspectorJson = "{}";
    private Picker picker = Picker.NONE;
    private PickerTarget pickerTarget = PickerTarget.QUEST_ICON;
    private EditBox pickerSearch;
    private String editorMessage = "";
    private boolean editorMessageSuccess;
    private boolean clipboardMutationPending;
    private EditBox pasteIdField;
    private final QuestMutationCoordinator mutations;
    private final QuestModalHost modalHost;
    private final LocalQuestFileOpener questFileOpener;
    private List<QuestDiagnostics.Diagnostic> diagnostics = List.of();
    private int diagnosticsScroll;
    private int importScroll;
    private final Map<String, EditBox> importIdFields = new HashMap<>();
    private int pickerScroll;
    private final Map<PickerTarget, Integer> pickerScrollByTarget = new HashMap<>();
    private final QuestMinimapPanel minimapPanel;
    private final QuestAuthoringPanel authoringPanel;
    private final QuestDetailsPanel detailsPanel;
    private DetailTab detailTab = DetailTab.OVERVIEW;
    /** Retained while widget trees rebuild so settings buttons do not jump the draft back to the top. */
    private boolean detailsOpen;
    private boolean sidebarOpen = true;
    private String chapterEditorOriginal;
    private String chapterEditorName = "";
    private String chapterEditorIcon = "minecraft:map";
    private boolean chapterEditorIconEnabled = true;
    private String chapterEditorBackground = "";
    private int chapterEditorBackgroundOpacity = 100;
    private String chapterEditorError = "";
    private boolean chapterDeleteArmed;
    private String chapterEditorBaseline;
    private boolean graphFocused;
    private boolean chapterListFocused;
    private int focusedChapterIndex = -1;
    private QuestContextMenu contextMenu;
    private double pendingPasteWorldX;
    private double pendingPasteWorldY;
    private boolean pendingPastePosition;
    private int nextQuestFileRequestId;
    private int pendingQuestFileRequestId = -1;
    private String pendingQuestFileId;
    private QuestModalHost.ProgressResetTarget progressResetTarget;

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
            TaskChoice choice = tasks().get(Math.clamp(authoringPanel.taskChooserSelection(), 0, tasks().size() - 1));
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.editor.chooser_selection",
                editorTypeLabel(EditorTypeRegistry.Kind.TASK, choice.type(), choice.label())
            ));
            output.add(NarratedElementType.USAGE, Component.translatable("gui.theseus.editor.chooser_keyboard_usage"));
        } else if (modalHost.isRewardChooserOpen() || modalHost.isNestedRewardChooserOpen()) {
            List<RewardChoice> choices = modalHost.isNestedRewardChooserOpen()
                ? rewards().stream().filter(choice -> !choice.type().equals("theseus:selectable")).toList()
                : rewards();
            RewardChoice choice = choices.get(Math.clamp(authoringPanel.rewardChooserSelection(), 0, choices.size() - 1));
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.editor.chooser_selection",
                editorTypeLabel(EditorTypeRegistry.Kind.REWARD, choice.type(), choice.label())
            ));
            output.add(NarratedElementType.USAGE, Component.translatable("gui.theseus.editor.chooser_keyboard_usage"));
        } else if (graphFocused && selected() != null) {
            ClientQuest quest = selected();
            output.add(NarratedElementType.TITLE, Component.translatable(
                "gui.theseus.quest.graph_selection",
                quest.definition.title(),
                status(quest)
            ));
            output.add(NarratedElementType.USAGE, Component.translatable(
                mode.isAuthoring()
                    ? "gui.theseus.quest.graph_editor_keyboard_usage"
                    : "gui.theseus.quest.graph_keyboard_usage"
            ));
        }
    }

    private static Component editorText(String translationKey) {
        return Component.translatable(translationKey);
    }

    private static String editorString(String translationKey) {
        return editorText(translationKey).getString();
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

    private static Component editorTypeLabel(EditorTypeRegistry.Kind kind, String type, String fallback) {
        if (type != null && type.startsWith("theseus:")) {
            String typeId = type.substring("theseus:".length());
            boolean known = (kind == EditorTypeRegistry.Kind.TASK && tasks().stream().anyMatch(choice -> choice.type().equals(type)))
                || (kind == EditorTypeRegistry.Kind.REWARD && rewards().stream().anyMatch(choice -> choice.type().equals(type)))
                || (kind == EditorTypeRegistry.Kind.ICON && type.equals("theseus:item"));
            if (known) {
                return Component.translatable("gui.theseus.editor.type." + kind.name().toLowerCase(java.util.Locale.ROOT) + "." + typeId);
            }
        }
        return Component.literal(fallback == null || fallback.isBlank() ? String.valueOf(type) : fallback);
    }

    public QuestScreen(JsonObject snapshot) {
        this(snapshot, null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        super(Component.translatable("gui.theseus.editor.theseus_quests"));
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
        readSnapshot(snapshot);
        Set<String> groups = groups();
        this.group =
            previous != null && groups.contains(previous.group)
                ? previous.group
                : groups.stream().findFirst().orElse("Main");
        this.chapterListState.setChapterCount(groups.size());
        if (previous != null
            && !new ArrayList<>(previous.groups()).equals(new ArrayList<>(groups))) {
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

    private void readSnapshot(JsonObject snapshot) {
        String snapshotKind = snapshot.has("__snapshot_kind") && snapshot.get("__snapshot_kind").isJsonPrimitive()
            ? snapshot.get("__snapshot_kind").getAsString()
            : "full";
        boolean indexSnapshot = snapshotKind.equals("index");
        boolean chapterSnapshot = snapshotKind.equals("chapter");
        if (indexSnapshot || !chapterSnapshot) {
            quests.clear();
            chapters.clear();
            chapterDisplays.clear();
            loadedChapters.clear();
            pendingChapterLoads.clear();
        }
        if (snapshot.has("__editor_types") && snapshot.get("__editor_types").isJsonObject()) {
            JsonObject types = snapshot.getAsJsonObject("__editor_types");
            readServerTypes(types, "tasks", serverTaskTypes);
            readServerTypes(types, "rewards", serverRewardTypes);
            readServerTypes(types, "icons", serverIconTypes);
        }
        if (snapshot.has("__chapters") && snapshot.get("__chapters").isJsonObject()) {
            JsonObject metadata = snapshot.getAsJsonObject("__chapters");
            if (!chapterSnapshot && metadata.has("order") && metadata.get("order").isJsonArray()) {
                metadata.getAsJsonArray("order").forEach(value -> chapters.add(value.getAsString()));
            }
            if (metadata.has("settings") && metadata.get("settings").isJsonObject()) {
                metadata.getAsJsonObject("settings").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    chapterDisplays.put(entry.getKey(), new ChapterDisplay(
                        jsonString(value, "icon", "minecraft:map"),
                        jsonString(value, "background", ""),
                        !value.has("iconEnabled") || value.get("iconEnabled").getAsBoolean(),
                        value.has("backgroundOpacity") ? Math.clamp(value.get("backgroundOpacity").getAsInt(), 0, 100) : 100
                    ));
                });
            }
        }
        snapshot.entrySet().forEach(entry -> {
            if (entry.getKey().startsWith("__") || !entry.getValue().isJsonObject()) return;
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = QuestDefinition.parse(entry.getKey(), json);
            Map<String, Integer> progress = new HashMap<>();
            if (json.has("progress") && json.get("progress").isJsonObject()) {
                json.getAsJsonObject("progress")
                    .entrySet()
                    .forEach(task -> progress.put(task.getKey(), task.getValue().getAsInt()));
            }
            Set<String> claimedRewards = new LinkedHashSet<>();
            if (json.has("claimed_rewards") && json.get("claimed_rewards").isJsonArray()) {
                json.getAsJsonArray("claimed_rewards").forEach(reward -> {
                    if (reward.isJsonPrimitive() && reward.getAsJsonPrimitive().isString()) {
                        String id = reward.getAsString();
                        if (definition.rewards().containsKey(id)) claimedRewards.add(id);
                    }
                });
            } else if (json.has("claimed") && json.get("claimed").isJsonPrimitive()
                && json.get("claimed").getAsBoolean()) {
                claimedRewards.addAll(definition.rewards().keySet());
            }
            quests.removeIf(existing -> existing.definition.id().equals(definition.id()));
            quests.add(new ClientQuest(
                definition,
                Map.copyOf(progress),
                json.get("unlocked").getAsBoolean(),
                json.get("complete").getAsBoolean(),
                json.get("claimed").getAsBoolean(),
                json.has("pinned") && json.get("pinned").getAsBoolean(),
                Set.copyOf(claimedRewards),
                json.deepCopy()
            )
            );
        });
        quests.sort(Comparator.comparing(quest -> quest.definition.id()));
        if (!chapterSnapshot && !indexSnapshot) loadedChapters.addAll(groups());
        else if (snapshot.has("__chapter") && snapshot.get("__chapter").isJsonPrimitive()) {
            String chapter = snapshot.get("__chapter").getAsString();
            loadedChapters.add(chapter);
            pendingChapterLoads.remove(chapter);
        }
    }

    private static void readServerTypes(JsonObject root, String key, Set<String> target) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return;
        root.getAsJsonArray(key).forEach(value -> {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) target.add(value.getAsString());
        });
    }

    /** Requests full task/reward/description data for the active chapter. */
    public void requestActiveChapter() {
        requestChapter(group);
    }

    private void requestChapter(String chapter) {
        if (chapter == null || loadedChapters.contains(chapter) || !pendingChapterLoads.add(chapter)) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("load_chapter", chapter));
    }

    public void mergeSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        readSnapshot(snapshot);
        graphViewport.activateChapter(group, graphCanvasBounds(), graphWorldBounds());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        authoringPanel.setViewport(font, width, height);
        graphViewport.activateChapter(group, graphCanvasBounds(), graphWorldBounds());
        // Overlay policy decides which widget tree is eligible for focus and
        // input. The screen only adapts that decision into Minecraft widgets.
        switch (modalHost.active()) {
            case DIAGNOSTICS -> {
                addDiagnosticsModalWidgets();
                return;
            }
            case FILE_IMPORT -> {
                addImportModalWidgets();
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
                authoringPanel.addRewardEditorWidgets(authoring.editingNestedReward, true);
                return;
            }
            case NESTED_REWARDS, NESTED_REWARD_CHOOSER -> {
                authoringPanel.addNestedRewardWidgets();
                return;
            }
            case REWARD_EDITOR -> {
                authoringPanel.addRewardEditorWidgets(authoring.editingReward, false);
                return;
            }
            case NESTED_TASKS, NESTED_TASK_CHOOSER -> {
                authoringPanel.addNestedTaskWidgets();
                return;
            }
            case TASK_EDITOR -> {
                authoringPanel.addTaskEditorWidgets();
                return;
            }
            default -> { }
        }
        int sidebarWidth = sidebarWidth();
        Button sidebarToggle = Widgets.button(widget -> {
            widget
                .withPosition(sidebarOpen ? sidebarWidth - 13 : 3, 2)
                .withSize(11, 11);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(sidebarOpen ? "‹" : "›")
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                sidebarOpen = !sidebarOpen;
                rebuildWidgets();
            });
            widget.withTooltip(
                Component.translatable(sidebarOpen
                    ? "gui.theseus.editor.collapse_quest_groups"
                    : "gui.theseus.editor.show_quest_groups")
            );
        });
        addRenderableWidget(sidebarToggle);

        if (!canEdit()) {
            mode = new PlayMode();
            authoring.open = false;
            closePicker();
        } else {
            HeaderLayout header = headerLayout();
            if (!diagnostics.isEmpty()) {
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(header.diagnosticsX(), header.diagnosticsY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.diagnostics")));
                    widget.withCallback(() -> {
                        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
                        diagnosticsScroll = 0;
                        rebuildWidgets();
                    });
                    widget.withTooltip(Component.translatable("gui.theseus.editor.view_validation_diagnostics"));
                }));
            }
            if (mode.isAuthoring() && !authoring.open) addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(header.importX(), header.importY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.import")));
                    widget.withCallback(this::openNativeFilePicker);
                    widget.withTooltip(Component.translatable("gui.theseus.editor.choose_one_or_more_quest_json_files"));
                }));
            addRenderableWidget(editorButton(
                header.editX(),
                "edit",
                mode.isAuthoring(),
                Component.translatable(mode.isAuthoring() ? "gui.theseus.editor.leave_quest_edit_mode" : "gui.theseus.editor.edit_quests"),
                () -> {
                    requestDiscard(() -> {
                        boolean enteringEditMode = !mode.isAuthoring();
                        mode = enteringEditMode ? authoring : new PlayMode();
                        authoring.setEditorTool(EditorTool.SELECT);
                        linkSourceId = null;
                        closePicker();
                        panning = false;
                        ClientQuest focusedQuest = selected();
                        if (enteringEditMode && focusedQuest != null) {
                            beginEditQuest(focusedQuest);
                        }
                        if (!enteringEditMode) {
                            closeDraft();
                            if (focusedQuest != null) detailsOpen = true;
                        }
                        rebuildWidgets();
                        if (enteringEditMode && QuestTutorial.shouldAutoShow(
                            canEdit(),
                            TheseusClientOptions.tutorialAutoShow(),
                            TheseusClientOptions.tutorialSeen()
                        )) openTutorial();
                    });
                }
            ));
        }
        addGraphNavigationWidgets(headerLayout());
        if (mode.isAuthoring() && !authoring.open) {
            int toolX = sidebarWidth + 24;
            for (EditorTool tool : EditorTool.values()) {
                addRenderableWidget(editorButton(
                    toolX,
                    tool.icon,
                    mode.editorTool() == tool,
                    Component.translatable(tool.tooltipKey, tool.shortcut),
                    () -> {
                        requestDiscard(() -> {
                            mode.setEditorTool(tool);
                            closeDraft();
                            linkSourceId = null;
                            panning = false;
                            rebuildWidgets();
                        });
                    }
                ));
                toolX += 22;
            }
        }
        if (sidebarOpen) {
            List<String> orderedGroups = new ArrayList<>(groups());
            chapterListState.setViewport(CHAPTER_LIST_TOP, chapterListBottom(), CHAPTER_ROW_HEIGHT);
            chapterListState.setChapterCount(orderedGroups.size());
            int y = CHAPTER_LIST_TOP;
            for (int chapterIndex : chapterListState.visibleIndices()) {
                String candidate = orderedGroups.get(chapterIndex);
                int index = chapterIndex;
                int groupY = y + (chapterIndex - chapterListState.firstVisibleRow()) * CHAPTER_ROW_HEIGHT;
                Button button = Widgets.button(widget -> {
                    int buttonWidth = sidebarWidth - (mode.isAuthoring() ? 51 : chapterListState.hasOverflow() ? 10 : 8);
                    widget
                        .withPosition(4, groupY)
                        .withSize(Math.max(1, buttonWidth), CHAPTER_ROW_CONTENT_HEIGHT);
                    widget.withTexture(null);
                    widget.withRenderer(chapterButtonRenderer(candidate, candidate.equals(group)));
                    widget.withTooltip(Component.literal(candidate));
                    widget.withCallback(() -> {
                        chapterListFocused = true;
                        focusedChapterIndex = index;
                        selectChapterIndex(index);
                    });
                });
                addRenderableWidget(button);
                if (mode.isAuthoring()) {
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 45, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                        widget.withCallback(() -> reorderChapter(index, -1));
                        widget.active = index > 0;
                        widget.withTooltip(Component.translatable("gui.theseus.editor.move_chapter_up"));
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 32, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                        widget.withCallback(() -> reorderChapter(index, 1));
                        widget.active = index < orderedGroups.size() - 1;
                        widget.withTooltip(Component.translatable("gui.theseus.editor.move_chapter_down"));
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 19, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                        widget.withCallback(() -> openChapterEditor(candidate));
                        widget.withTooltip(Component.translatable("gui.theseus.editor.edit_chapter"));
                    }));
                }
            }
            int addChapterY = Math.max(CHAPTER_LIST_TOP, height - 24);
            if (mode.isAuthoring()) addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(4, addChapterY).withSize(sidebarWidth - 8, 20);
                widget.withTexture(null);
                widget.withRenderer(chapterButtonRenderer("+  Add chapter", false));
                widget.withCallback(() -> openChapterEditor(null));
                widget.withTooltip(Component.translatable("gui.theseus.editor.add_chapter"));
            }));
        }
        addDockWidgets();
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

    private QuestSurfaceLayout.QuestNode surfaceNode(ClientQuest quest) {
        QuestDefinition.GroupDisplay position = quest.definition.position(group);
        return new QuestSurfaceLayout.QuestNode(
            quest.definition.id(),
            position.x(),
            position.y(),
            quest.definition.display().iconSize(),
            quest.definition.display().iconBackground()
        );
    }

    private QuestSurfaceLayout.Layout surfaceLayout() {
        return QuestSurfaceLayout.layout(
            visibleQuests().stream()
                .filter(quest -> !authoring.editingExisting || !authoring.open
                    || !quest.definition.id().equals(authoring.originalId))
                .map(this::surfaceNode)
                .toList(),
            graphCanvasBounds(),
            graphViewport.state()
        );
    }

    private QuestGraphLayout.Point questCenter(ClientQuest quest) {
        if (quest == null) return new QuestGraphLayout.Point(0, 0);
        QuestDefinition.GroupDisplay position = quest.definition.position(group);
        return new QuestGraphLayout.Point(position.x(), position.y());
    }

    private HeaderLayout headerLayout() {
        return headerLayout(canvasRight());
    }

    private HeaderLayout graphHeaderLayout() {
        return headerLayout(graphCanvasRight());
    }

    private HeaderLayout headerLayout(int right) {
        int editX = right - 23;
        int helpX = editX - 27;
        int fitX = helpX - 27;
        int gridX = fitX - 27;
        int snapX = gridX - 27;
        boolean editorDockOpen = authoring.open;
        int nextActionX = editorDockOpen
            ? helpX
            : mode.isAuthoring() ? snapX : fitX;
        int diagnosticsX = -1;
        int importX = -1;
        if (!diagnostics.isEmpty()) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            diagnosticsX = nextActionX;
        }
        if (mode.isAuthoring() && !editorDockOpen) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            importX = nextActionX;
        }

        int toolLeft = sidebarWidth() + 24;
        int toolRight = mode.isAuthoring()
            ? toolLeft + (EditorTool.values().length - 1) * 22 + 19
            : toolLeft;
        boolean actionsOnSecondRow = (importX >= 0 || diagnosticsX >= 0)
            && nextActionX < toolRight + HEADER_ACTION_GAP;
        int actionRow = !editorDockOpen && actionsOnSecondRow ? 1 : 0;
        int diagnosticsRow = diagnosticsX < 0
            ? -1
            : editorDockOpen && diagnosticsX < toolRight + HEADER_ACTION_GAP
                ? 1
                : actionRow;
        int importRow = importX < 0 ? -1 : actionRow;
        int statusRow = !editorMessage.isEmpty() && !authoring.open
            ? actionRow + 1
            : -1;
        int rows = Math.max(1, Math.max(actionRow + 1, statusRow + 1));
        if (diagnosticsRow >= 0) rows = Math.max(rows, diagnosticsRow + 1);
        if (importRow >= 0) rows = Math.max(rows, importRow + 1);
        int canvasTop = HEADER_ROW_Y
            + rows * HEADER_ROW_HEIGHT
            + (rows - 1) * HEADER_ROW_GAP
            + HEADER_CANVAS_GAP;
        return new HeaderLayout(
            editX,
            helpX,
            fitX,
            gridX,
            snapX,
            importX,
            diagnosticsX,
            HEADER_ROW_Y + actionRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            importRow < 0
                ? -1
                : HEADER_ROW_Y + importRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            diagnosticsRow < 0
                ? -1
                : HEADER_ROW_Y + diagnosticsRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            statusRow < 0
                ? -1
                : HEADER_ROW_Y + statusRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            canvasTop
        );
    }

    private void addGraphNavigationWidgets(HeaderLayout header) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(header.helpX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.text(Component.literal("⋮"))
            ));
            widget.withCallback(() -> openDisplayMenu(header.helpX(), header.actionY() + HEADER_ROW_HEIGHT));
            widget.withTooltip(Component.translatable("screen.theseus.display_menu.tooltip"));
        }));
        if (!authoring.open) addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(header.fitX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.f")));
                widget.withCallback(this::fitGraphToContent);
                widget.withTooltip(Component.translatable("gui.theseus.editor.fit_visible_quests_in_the_graph"));
            }));
        if (!TheseusClientOptions.disableMinimap()
            && minimapPanel.hidden()
            && !detailsOpen
            && !authoring.open) {
            QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
            if (canvas.width() >= 22 && canvas.height() >= HEADER_ROW_HEIGHT) {
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(
                        (int) Math.round(canvas.maxX()) - 26,
                        (int) Math.round(canvas.maxY()) - 24
                    )
                        .withSize(22, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.center(
                        11,
                        11,
                        WidgetRenderers.sprite(new WidgetSprites(
                            MINIMAP_TOGGLE,
                            MINIMAP_TOGGLE_SELECTED
                        ))
                    ));
                    widget.withCallback(() -> {
                        minimapPanel.setHidden(false);
                        minimapPanel.clearTransientState();
                        rebuildWidgets();
                    });
                    widget.withTooltip(Component.translatable("gui.theseus.editor.show_quest_minimap"));
                }));
            }
        }
        if (mode.isAuthoring() && !authoring.open) {
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(header.gridX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
                boolean visible = TheseusClientOptions.showGrid();
                widget.withRenderer(WidgetRenderers.center(
                    11,
                    11,
                    WidgetRenderers.sprite(new WidgetSprites(
                        visible ? SHOW_GRID_SELECTED : SHOW_GRID,
                        SHOW_GRID_SELECTED
                    ))
                ));
                widget.withCallback(() -> {
                    TheseusClientOptions.setShowGrid(!TheseusClientOptions.showGrid());
                    rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(visible ? "gui.theseus.editor.hide_graph_grid" : "gui.theseus.editor.show_graph_grid"));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(header.snapX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
                boolean enabled = TheseusClientOptions.snapToGrid();
                widget.withRenderer(WidgetRenderers.center(
                    11,
                    11,
                    WidgetRenderers.sprite(new WidgetSprites(
                        enabled ? SNAP_TO_GRID_SELECTED : SNAP_TO_GRID,
                        SNAP_TO_GRID_SELECTED
                    ))
                ));
                widget.withCallback(() -> {
                    TheseusClientOptions.setSnapToGrid(!TheseusClientOptions.snapToGrid());
                    rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(enabled ? "gui.theseus.editor.disable_snap_to_grid" : "gui.theseus.editor.enable_snap_to_grid"));
            }));
        }
    }

    private void fitGraphToContent() {
        graphViewport.fitToContent(graphCanvasBounds(), graphWorldBounds());
        graphViewport.saveChapterViewport(group);
        rebuildWidgets();
    }

    private void addDockWidgets() {
        if (authoring.open) {
            authoringPanel.addCreateQuestDockWidgets();
            return;
        }
        if (!detailsOpen) return;
        ClientQuest selected = selected();
        int detailsWidth = detailsWidth();
        int detailsLeft = width - detailsWidth;
        int pinLeft = width - 50;
        int tabRight = pinLeft - 4;
        int tabWidth = (tabRight - (detailsLeft + 8)) / DetailTab.values().length;
        for (int index = 0; index < DetailTab.values().length; index++) {
            DetailTab tab = DetailTab.values()[index];
            int tabX = detailsLeft + 8 + index * tabWidth;
            Button tabButton = Widgets.button(widget -> {
                widget.withPosition(tabX, 8).withSize(tabWidth - 3, 20);
                widget.withRenderer(
                    WidgetRenderers.text(
                        Component.translatable(tab.translationKey)
                    ).withColor(
                        tab == detailTab
                            ? new Color(ClientThemeLoader.active().questDetails().tabButtonSelected())
                            : new Color(ClientThemeLoader.active().questDetails().tabButton())
                    )
                );
                widget.withCallback(() -> {
                    detailTab = tab;
                    detailsPanel.resetScroll();
                    rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(tab.translationKey));
            });
            addRenderableWidget(tabButton);
        }
        Button closeDetails = Widgets.button(widget -> {
            widget.withPosition(width - 27, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.center(11, 11, WidgetRenderers.sprite(CLOSE_BUTTON))
            );
            widget.withCallback(() -> {
                detailsOpen = false;
                selectedQuestId = null;
                rebuildWidgets();
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.close_quest_details"));
        });
        addRenderableWidget(closeDetails);
        Button pin = Widgets.button(widget -> {
            widget.withPosition(pinLeft, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(
                        selected != null && selected.pinned ? "★" : "☆"
                    )
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                if (selected != null) ClientPacketDistributor.sendToServer(
                    new QuestNetwork.ActionPayload(
                        "pin",
                        selected.definition.id()
                    )
                );
            });
            widget.active = selected != null && selected.unlocked;
            widget.withTooltip(
                Component.translatable(selected != null && selected.pinned
                    ? "gui.theseus.editor.unpin_quest"
                    : "gui.theseus.editor.pin_quest")
            );
        });
        addRenderableWidget(pin);
        TaskRef submittable =
            selected == null || !selected.unlocked
                ? null
                : findSubmittable(
                      selected.definition.tasks(),
                      selected.progress,
                      ""
                  );
        int actionWidth =
            submittable == null ? detailsWidth - 18 : (detailsWidth - 27) / 2;
        Button claim = Widgets.button(widget -> {
            widget
                .withPosition(detailsLeft + 9, height - 36)
                .withSize(actionWidth, 20);
            widget.withRenderer(
                WidgetRenderers.text(Component.translatable("gui.theseus.editor.claim_rewards"))
            );
            widget.withCallback(this::claimSelected);
            widget.active =
                selected != null &&
                selected.complete &&
                !selected.claimed &&
                !mutations.isPending() &&
                canClaimRewards(selected);
            widget.withTooltip(Component.translatable("gui.theseus.editor.claim_rewards"));
            if (
                selected != null &&
                selected.complete &&
                !selected.claimed &&
                !canClaimRewards(selected)
            ) {
                widget.withTooltip(
                    Component.literal(claimBlockedReason(selected))
                );
            }
        });
        addRenderableWidget(claim);
        if (submittable != null) {
            Button submit = Widgets.button(widget -> {
                widget
                    .withPosition(detailsLeft + 18 + actionWidth, height - 36)
                    .withSize(actionWidth, 20);
                widget.withRenderer(
                    WidgetRenderers.text(Component.translatable("gui.theseus.editor.submit_task"))
                );
                widget.withCallback(() -> submitTask(selected, submittable));
                widget.active = !mutations.isPending();
                widget.withTooltip(Component.translatable("gui.theseus.editor.submit_task"));
            });
            addRenderableWidget(submit);
        }
    }

    private Button editorButton(
        int x,
        String icon,
        boolean selected,
        Component tooltip,
        Runnable callback
    ) {
        return Widgets.button(widget -> {
            widget.withPosition(x, 1).withSize(19, 20);
            Identifier texture = sprite("heading/editor/" + icon + (selected ? "_selected" : ""));
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(new WidgetSprites(texture, texture))
            ));
            widget.withCallback(callback);
            widget.withTooltip(tooltip);
        });
    }

    private WidgetRenderer<Button> listActionRenderer(String action) {
        return WidgetRenderers.center(
            EDITOR_LIST_ACTION_ICON_SIZE,
            EDITOR_LIST_ACTION_ICON_SIZE,
            WidgetRenderers.sprite(new WidgetSprites(
                sprite("heading/editor/" + action),
                sprite("heading/editor/" + action)
            ))
        );
    }

    private WidgetRenderer<Button> chapterButtonRenderer(String chapter, boolean selected) {
        return (graphics, context, partialTick) -> {
            if (context.getWidget().isHoveredOrFocused()) {
                graphics.fill(
                    context.getX(),
                    context.getY(),
                    context.getX() + context.getWidth(),
                    context.getY() + context.getHeight(),
                    0x224C9AFF
                );
            }
            if (selected || context.getWidget().isFocused()) {
                graphics.outline(
                    context.getX(),
                    context.getY(),
                    context.getWidth(),
                    context.getHeight(),
                    selected ? 0xFFFFD966 : ClientThemeLoader.active().genericControls().accent()
                );
            }
            int contentX = context.getX() + 3;
            ChapterDisplay display = chapterDisplays.get(chapter);
            int labelX = contentX + (chapterListHasIcons() ? CHAPTER_ICON_COLUMN_WIDTH : 0);
            if (display != null && display.iconEnabled) {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(display.icon));
                    if (item != null && item != Items.AIR) {
                        graphics.item(new ItemStack(item), contentX, context.getY() + 2);
                    }
                } catch (RuntimeException ignored) { }
            }
            graphics.enableScissor(labelX, context.getY(), context.getX() + context.getWidth() - 3, context.getY() + context.getHeight());
            int available = Math.max(0, context.getX() + context.getWidth() - 3 - labelX);
            String label = chapter;
            if (font.width(label) > available) {
                label = font.plainSubstrByWidth(label, Math.max(0, available - font.width("…"))) + "…";
            }
            graphics.text(font, Component.literal(label), labelX, context.getY() + 6, 0xFFFFFFFF, false);
            graphics.disableScissor();
        };
    }

    /** Snaps the active authoring draft once, leaving the change for Save. */
    private void addRawInspectorButton(int x, int y, int width, Runnable open) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.raw_json")));
            widget.withCallback(open);
            widget.withTooltip(Component.translatable("gui.theseus.editor.inspect_this_configuration_without_editing_it"));
        }));
    }

    private void openRawInspector(String title, JsonObject source) {
        rawInspectorTitle = title;
        rawInspectorJson = GSON.toJson(source);
        modalHost.open(QuestModalHost.Modal.RAW_INSPECTOR);
        rebuildWidgets();
    }

    private void addRawInspectorWidgets() {
        int inspectorWidth = Math.min(480, width - 32);
        int inspectorHeight = Math.min(280, height - 48);
        int left = (width - inspectorWidth) / 2;
        int top = (height - inspectorHeight) / 2;
        MultiLineEditBox value = MultiLineEditBox.builder()
            .setX(left + 12).setY(top + 34)
            .build(font, inspectorWidth - 24, inspectorHeight - 70, Component.literal(rawInspectorTitle));
        value.setValue(rawInspectorJson);
        value.active = false;
        addRenderableWidget(value);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + inspectorWidth - 92, top + inspectorHeight - 28).withSize(80, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.close")));
            widget.withCallback(() -> {
                modalHost.close();
                rebuildWidgets();
            });
        }));
    }

    private void openDescriptionEditor() {
        descriptionEditorValue = authoring.body;
        descriptionPreviewScroll = 0;
        modalHost.open(QuestModalHost.Modal.DESCRIPTION_EDITOR);
        rebuildWidgets();
    }

    private void addDescriptionEditorWidgets() {
        int modalWidth = Math.min(760, width - 24);
        int modalHeight = Math.min(420, height - 24);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        int gutter = 8;
        int paneWidth = (modalWidth - 32 - gutter) / 2;
        int editorTop = top + 61;
        int editorHeight = modalHeight - 103;

        descriptionEditor = new MarkdownEditBox(
            font, left + 12, editorTop, paneWidth, editorHeight,
            Component.translatable("gui.theseus.editor.quest_markdown_description")
        );
        descriptionEditor.setValue(descriptionEditorValue);
        descriptionEditor.setValueListener(value -> descriptionEditorValue = value);
        addRenderableWidget(descriptionEditor);
        setInitialFocus(descriptionEditor);

        int actionX = left + 12;
        int toolbarY = top + 31;
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.h1", "header1", () -> descriptionEditor.prefixLine("# "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.h2", "header2", () -> descriptionEditor.prefixLine("## "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.bold", "B", () -> descriptionEditor.surround("**"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.italic", "I", () -> descriptionEditor.surround("--"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.underline", "U", () -> descriptionEditor.surround("__"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.strikethrough", "S", () -> descriptionEditor.surround("~~"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.spoiler", "||", () -> descriptionEditor.surround("||"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.color", "C", () -> descriptionEditor.surround("/e/"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.list", "list", () -> descriptionEditor.prefixLine("- "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "gui.theseus.editor.blockquote", ">", () -> descriptionEditor.prefixLine("> "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.link", "link", () -> descriptionEditor.insertLink(null, "https://"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "gui.theseus.editor.horizontal_rule", "horizontalline", () -> descriptionEditor.insert("\n---\n"));

        int objectX = actionX;
        if (!authoring.tasks.isEmpty()) {
            objectX = addMarkdownSpriteAction(objectX, toolbarY, "gui.theseus.editor.insert_task", "task", () ->
                descriptionEditor.insertObject("task", authoring.tasks.getFirst().id));
        }
        if (!authoring.rewards.isEmpty()) {
            addMarkdownSpriteAction(objectX, toolbarY, "gui.theseus.editor.insert_reward", "reward", () ->
                descriptionEditor.insertObject("reward", authoring.rewards.getFirst().id));
        }

        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 174, top + modalHeight - 31).withSize(76, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(this::closeDescriptionEditor);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 92, top + modalHeight - 31).withSize(80, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.apply")));
            widget.withCallback(this::applyDescriptionEditor);
        }));
    }

    private int addMarkdownSpriteAction(int x, int y, String tooltipKey, String icon, Runnable action) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withTexture(null);
            Identifier normal = sprite("editor/" + icon + "/normal");
            Identifier hovered = sprite("editor/" + icon + "/hovered");
            widget.withRenderer(WidgetRenderers.sprite(new WidgetSprites(normal, hovered)));
            widget.withCallback(action);
            widget.withTooltip(editorText(tooltipKey));
        }));
        return x + MARKDOWN_ACTION_SIZE + MARKDOWN_ACTION_GAP;
    }

    private int addMarkdownTextAction(int x, int y, String tooltipKey, String label, Runnable action) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withRenderer(WidgetRenderers.center(
                MARKDOWN_ACTION_SIZE,
                MARKDOWN_ACTION_SIZE,
                WidgetRenderers.text(Component.literal(label))
            ));
            widget.withCallback(action);
            widget.withTooltip(editorText(tooltipKey));
        }));
        return x + MARKDOWN_ACTION_SIZE + MARKDOWN_ACTION_GAP;
    }

    private void applyDescriptionEditor() {
        authoring.body = descriptionEditorValue;
        authoring.descriptionTouched = true;
        closeDescriptionEditor();
    }

    private void closeDescriptionEditor() {
        descriptionEditor = null;
        modalHost.close();
        rebuildWidgets();
    }

    private void openChapterEditor(String name) {
        modalHost.open(QuestModalHost.Modal.CHAPTER_EDITOR);
        chapterEditorOriginal = name;
        chapterEditorName = name == null ? "" : name;
        ChapterDisplay display = name == null ? null : chapterDisplays.get(name);
        chapterEditorIcon = display == null ? "minecraft:map" : display.icon;
        chapterEditorIconEnabled = display == null || display.iconEnabled;
        chapterEditorBackground = display == null ? "" : display.background;
        chapterEditorBackgroundOpacity = display == null ? 100 : display.backgroundOpacity;
        chapterEditorError = "";
        chapterDeleteArmed = false;
        chapterEditorBaseline = chapterEditorSnapshot();
        rebuildWidgets();
    }

    private String chapterEditorSnapshot() {
        return chapterEditorName + "\u0000" + chapterEditorIcon + "\u0000" + chapterEditorIconEnabled + "\u0000" + chapterEditorBackground + "\u0000" + chapterEditorBackgroundOpacity;
    }

    private boolean hasUnsavedChapterEditor() {
        return modalHost.isChapterEditorOpen()
            && chapterEditorBaseline != null
            && !chapterEditorBaseline.equals(chapterEditorSnapshot());
    }

    private void addChapterEditorWidgets() {
        int left = (width - 280) / 2;
        int top = chapterEditorTop();
        EditBox name = new EditBox(font, left + 14, top + 48, 252, 18, Component.translatable("gui.theseus.editor.chapter_name"));
        name.setValue(chapterEditorName);
        name.setResponder(value -> chapterEditorName = value);
        addRenderableWidget(name);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 81).withSize(34, 24);
            widget.withRenderer((graphics, context, partialTick) -> {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(chapterEditorIcon));
                    graphics.item(new ItemStack(item == null ? Items.MAP : item), context.getX() + 9, context.getY() + 4);
                } catch (RuntimeException ignored) { }
            });
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.CHAPTER_ICON));
            widget.withTooltip(Component.translatable("gui.theseus.editor.choose_chapter_icon"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 54, top + 81).withSize(100, 24);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(
                "gui.theseus.editor.chapter_icon_state",
                Component.translatable(chapterEditorIconEnabled ? "gui.theseus.editor.state_on" : "gui.theseus.editor.state_off")
            )));
            widget.withCallback(() -> {
                chapterEditorIconEnabled = !chapterEditorIconEnabled;
                rebuildWidgets();
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.show_or_hide_this_chapter_s_icon"));
        }));
        EditBox background = new EditBox(font, left + 14, top + 126, 252, 18, Component.translatable("gui.theseus.editor.background_path_or_url"));
        background.setValue(chapterEditorBackground);
        background.setResponder(value -> chapterEditorBackground = value);
        addRenderableWidget(background);
        EditBox opacity = new EditBox(font, left + 14, top + 158, 90, 18, Component.translatable("gui.theseus.editor.opacity"));
        opacity.setValue(Integer.toString(chapterEditorBackgroundOpacity));
        opacity.setResponder(value -> {
            try { chapterEditorBackgroundOpacity = Math.clamp(Integer.parseInt(value), 0, 100); }
            catch (NumberFormatException ignored) { chapterEditorBackgroundOpacity = 100; }
        });
        addRenderableWidget(opacity);
        if (chapterEditorOriginal != null) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 201).withSize(72, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(this::deleteChapter);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 94, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> requestModalDiscard(() -> {
                chapterEditorBaseline = null;
                modalHost.close();
                rebuildWidgets();
            }));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 184, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.save")));
            widget.withCallback(this::saveChapter);
        }));
    }

    private void saveChapter() {
        String name = chapterEditorName.trim();
        if (name.isEmpty() || (groups().contains(name) && !name.equals(chapterEditorOriginal))) {
            chapterEditorError = name.isEmpty() ? "Chapter name is required." : "That chapter already exists.";
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", chapterEditorOriginal == null ? "create" : "update");
        if (chapterEditorOriginal != null) action.addProperty("old_name", chapterEditorOriginal);
        action.addProperty("name", name);
        action.addProperty("icon", chapterEditorIcon);
        action.addProperty("icon_enabled", chapterEditorIconEnabled);
        action.addProperty("background", chapterEditorBackground);
        action.addProperty("background_opacity", chapterEditorBackgroundOpacity);
        sendChapterAction(action);
        modalHost.close();
        chapterEditorBaseline = null;
        group = name;
        chapterListState.setChapterCount(groups().size());
        chapterListState.ensureVisible(new ArrayList<>(groups()).indexOf(group));
        rebuildWidgets();
    }

    private void deleteChapter() {
        if (!chapterDeleteArmed) {
            chapterDeleteArmed = true;
            chapterEditorError = "Click Delete again to confirm.";
            rebuildWidgets();
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", "delete");
        action.addProperty("name", chapterEditorOriginal);
        sendChapterAction(action);
        modalHost.close();
        chapterEditorBaseline = null;
        rebuildWidgets();
    }

    private void reorderChapter(int index, int direction) {
        List<String> order = new ArrayList<>(groups());
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) return;
        java.util.Collections.swap(order, index, target);
        JsonObject action = new JsonObject();
        action.addProperty("operation", "reorder");
        action.add("order", GSON.toJsonTree(order));
        sendChapterAction(action);
        chapters.clear();
        chapters.addAll(order);
        chapterListState.setChapterCount(order.size());
        if (group.equals(order.get(target))) chapterListState.ensureVisible(target);
        else chapterListState.ensureVisible(order.indexOf(group));
        rebuildWidgets();
    }

    private void sendChapterAction(JsonObject action) {
        sendEditorMutation(new QuestMutation.ChapterAction(action));
    }

    private void addPickerSearchWidget() {
        if (picker == Picker.ICON || picker == Picker.ENTITY) {
            pickerSearch = new EditBox(
                font,
                pickerLeft() + 12,
                pickerTop() + 30,
                176,
                18,
                editorText(picker == Picker.ENTITY
                    ? "gui.theseus.editor.search_entities"
                    : "gui.theseus.editor.search_blocks_and_items")
            );
            pickerSearch.setResponder(ignored -> pickerScroll = 0);
            addRenderableWidget(pickerSearch);
            setInitialFocus(pickerSearch);
        }
    }

    private void beginEditQuest(ClientQuest quest) {
        if (!ensureChapterDataLoaded()) return;
        QuestDefinition definition = quest.definition;
        authoring.editingExisting = true;
        authoring.originalId = definition.id();
        selectedQuestId = definition.id();
        authoring.id = definition.id();
        authoring.title = definition.title();
        authoring.subtitle = definition.subtitle();
        authoring.body = String.join("\n", definition.description());
        authoring.icon = definition.display().icon().item();
        authoring.iconSize = definition.display().iconSize();
        authoring.iconSizeText = Integer.toString(authoring.iconSize);
        authoring.iconSizeTouched = false;
        authoring.iconSizeInvalid = false;
        authoring.descriptionTouched = false;
        authoring.iconTouched = false;
        authoring.background = definition.display().iconBackground();
        authoring.individualProgress = definition.settings().individualProgress();
        authoring.hiddenUntil = definition.settings().hiddenUntil();
        authoring.unlockNotification = definition.settings().unlockNotification();
        authoring.showDependencyArrow = definition.settings().showDependencyArrow();
        authoring.repeatable = definition.settings().repeatable();
        authoring.autoClaimRewards = definition.settings().autoClaimRewards();
        authoring.groups = new JsonObject();
        definition.display().groups().forEach((name, position) -> {
            JsonObject placement = new JsonObject();
            com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
            coordinates.add(position.x());
            coordinates.add(position.y());
            placement.add("position", coordinates);
            authoring.groups.add(name, placement);
        });
        QuestDefinition.GroupDisplay position = definition.position(group);
        authoring.x = position.x();
        authoring.y = position.y();
        authoring.xText = Integer.toString(authoring.x);
        authoring.yText = Integer.toString(authoring.y);
        authoring.xInvalid = false;
        authoring.yInvalid = false;
        authoring.tasks.clear();
        definition.tasks().values().forEach(task -> authoring.tasks.add(new QuestAuthoringSession.TaskDraft(task.id(), task.type(), task.source().deepCopy())));
        authoring.rewards.clear();
        definition.rewards().values().forEach(reward -> authoring.rewards.add(new QuestAuthoringSession.RewardDraft(reward.id(), reward.type(), reward.source().deepCopy())));
        authoringPanel.setCreateQuestTab(DetailTab.OVERVIEW);
        authoringPanel.resetOverviewScroll();
        authoringPanel.resetDraftScrolls();
        detailsOpen = false;
        authoring.open = true;
        editorMessage = definition.issues().stream().anyMatch(issue -> issue.severity() == QuestDefinition.Severity.WARNING)
            ? "Unsupported configuration is preserved and shown read-only."
            : "";
        editorMessageSuccess = false;
        mutations.cancel();
        authoring.begin(QuestDraft.fromClientSnapshot(definition.id(), quest.raw()));
        establishAuthoringBaseline();
        rebuildWidgets();
    }

    private boolean ensureChapterDataLoaded() {
        if (loadedChapters.contains(group)) return true;
        requestChapter(group);
        editorMessage = "Loading chapter data…";
        editorMessageSuccess = false;
        rebuildWidgets();
        return false;
    }

    /**
     * The form exposes parsed defaults for fields that may be omitted from a
     * quest document. Treat that populated form as the initial baseline so an
     * untouched quest is not reported as having changes.
     */
    private void establishAuthoringBaseline() {
        QuestDraft baseline = currentAuthoringDraft();
        baseline.accept();
        authoring.begin(baseline);
    }

    private void beginCreateQuest(double treeX, double treeY) {
        authoring.id = "";
        authoring.title = "";
        authoring.subtitle = "";
        authoring.body = "";
        authoring.icon = "minecraft:map";
        authoring.iconSize = QuestSurfaceLayout.DEFAULT_ICON_SIZE;
        authoring.iconSizeText = Integer.toString(authoring.iconSize);
        authoring.iconSizeTouched = false;
        authoring.iconSizeInvalid = false;
        authoring.descriptionTouched = false;
        authoring.iconTouched = false;
        authoring.background = "theseus:textures/gui/quest_backgrounds/default.png";
        authoring.individualProgress = false;
        authoring.hiddenUntil = QuestDefinition.Visibility.LOCKED;
        authoring.unlockNotification = false;
        authoring.showDependencyArrow = true;
        authoring.repeatable = false;
        authoring.autoClaimRewards = false;
        authoring.tasks.clear();
        authoring.rewards.clear();
        authoring.editingExisting = false;
        authoring.originalId = null;
        authoring.groups = new JsonObject();
        authoringPanel.resetDraftTaskScroll();
        authoringPanel.setCreateQuestTab(DetailTab.OVERVIEW);
        authoringPanel.resetOverviewScroll();
        authoring.x = (int) Math.round(treeX);
        authoring.y = (int) Math.round(treeY);
        if (TheseusClientOptions.snapToGrid()) {
            QuestGraphLayout.Point snapped = QuestGraphLayout.snapPoint(authoring.x, authoring.y);
            authoring.x = (int) snapped.x();
            authoring.y = (int) snapped.y();
        }
        authoring.xText = Integer.toString(authoring.x);
        authoring.yText = Integer.toString(authoring.y);
        authoring.xInvalid = false;
        authoring.yInvalid = false;
        updateDraftGroupPosition();
        detailsOpen = false;
        authoring.open = true;
        editorMessage = "";
        editorMessageSuccess = false;
        mutations.cancel();
        authoring.begin(QuestDraft.create(null));
        rebuildWidgets();
    }

    private void updateDraftGroupPosition() {
        JsonObject placement = authoring.groups.has(group) && authoring.groups.get(group).isJsonObject()
            ? authoring.groups.getAsJsonObject(group) : new JsonObject();
        com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
        coordinates.add(authoring.x);
        coordinates.add(authoring.y);
        placement.add("position", coordinates);
        authoring.groups.add(group, placement);
        authoring.setGroupPosition(group, authoring.x, authoring.y);
    }

    private void addDeleteQuestConfirmationWidgets() {
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(() -> {
                confirmDeleteQuest();
            });
        }));
    }

    private void addProgressResetConfirmationWidgets() {
        int left = (width - 280) / 2;
        int top = (height - 142) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 102).withSize(122, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                progressResetTarget = null;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 146, top + 102).withSize(122, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.reset_progress")));
            widget.withCallback(this::confirmProgressReset);
            widget.active = progressResetTarget != null && !mutations.isPending();
        }));
    }

    private void confirmProgressReset() {
        if (progressResetTarget == null || mutations.isPending()) return;
        QuestModalHost.ProgressResetTarget target = progressResetTarget;
        JsonObject request = new JsonObject();
        request.addProperty("scope", target.scope());
        request.addProperty("quest", target.questId());
        request.addProperty("entry", target.entryId());
        progressResetTarget = null;
        editorMessage = "Resetting progress…";
        editorMessageSuccess = false;
        sendEditorMutation(new QuestMutation.ResetProgress(request));
        rebuildWidgets();
    }

    private void addDeleteTaskConfirmationWidgets() {
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                authoring.taskDeleteConfirmation = -1;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.delete")));
            widget.withCallback(() -> {
                confirmDeleteTask();
            });
        }));
    }

    private void confirmDeleteQuest() {
        modalHost.close();
        JsonObject request = new JsonObject();
        request.addProperty("id", authoring.originalId);
        editorMessage = "Deleting…";
        editorMessageSuccess = false;
        sendEditorMutation(new QuestMutation.DeleteQuest(request));
        rebuildWidgets();
    }

    private void confirmDeleteTask() {
        if (authoring.taskDeleteConfirmation < authoring.tasks.size()) {
            authoring.tasks.remove(authoring.taskDeleteConfirmation);
            authoringPanel.clampDraftTaskScroll();
        }
        authoring.taskDeleteConfirmation = -1;
        modalHost.close();
        rebuildWidgets();
    }

    private void addDiscardConfirmationWidgets() {
        int left = (width - 260) / 2;
        int top = (height - 116) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.keep_editing")));
            widget.withCallback(() -> {
                modalHost.cancelDismissal();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 136, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.discard_changes")));
            widget.withCallback(() -> {
                modalHost.confirmDismissal();
            });
        }));
    }

    private void removeExistingQuestFromChapter() {
        JsonObject change = new JsonObject();
        change.addProperty("id", authoring.originalId);
        change.addProperty("group", group);
        sendEditorMutation(new QuestMutation.RemoveQuestGroup(change));
        authoring.open = false;
        authoring.editingExisting = false;
        authoring.originalId = null;
        rebuildWidgets();
    }

    /** The same editable task rows used at the quest root, scoped to a composite. */
    private void openPicker(Picker value) {
        openPicker(value, PickerTarget.QUEST_ICON);
    }

    private void openPicker(Picker value, PickerTarget target) {
        picker = value;
        pickerTarget = target;
        pickerScroll = pickerScrollByTarget.getOrDefault(target, 0);
        pickerSearch = null;
        modalHost.open(QuestModalHost.Modal.PICKER);
        rebuildWidgets();
    }

    private void closePicker() {
        if (picker == Picker.NONE) return;
        picker = Picker.NONE;
        if (modalHost.is(QuestModalHost.Modal.PICKER)) modalHost.close();
    }

    private QuestAuthoringSession.RewardDraft activeRewardDraft() {
        return authoring.editingNestedReward != null ? authoring.editingNestedReward : authoring.editingReward;
    }

    private boolean validCreateQuestDraft() {
        return draftValidationError().isEmpty() && !mutations.isPending();
    }

    private String draftValidationError() {
        String sessionError = authoring.validationError(
            value -> clientContainsRegistryTarget(RegistryValidation.Target.ITEM, value),
            QuestScreen::clientContainsRegistryTarget
        );
        if (!sessionError.isEmpty()) return sessionError;
        if (quests.stream().anyMatch(quest -> quest.definition.id().equals(authoring.id) &&
            (!authoring.editingExisting || !quest.definition.id().equals(authoring.originalId)))) {
            return "Another quest already uses this ID.";
        }
        for (int index = 0; index < authoring.tasks.size(); index++) {
            QuestAuthoringSession.TaskDraft task = authoring.tasks.get(index);
            if (authoringPanel.isTaskEditable(task)) {
                String error = authoringPanel.validateTaskDraft(task.copy(), index);
                if (!error.isEmpty()) return "Task '" + task.id + "': " + error;
            }
        }
        return "";
    }

    private void confirmCreateQuest() {
        String validationError = draftValidationError();
        if (!validationError.isEmpty()) {
            editorMessage = validationError;
            editorMessageSuccess = false;
            rebuildWidgets();
            return;
        }
        updateDraftGroupPosition();
        QuestDraft draft = currentAuthoringDraft();
        JsonObject request = authoring.editingExisting
            ? draft.updateMutation()
            : draft.createMutation(group, authoring.x, authoring.y);
        String json = GSON.toJson(request);
        if (json.length() > QuestNetwork.EditorMutationPayload.MAX_JSON_LENGTH) {
            editorMessage = "This quest is too large to save (maximum 1 MiB).";
            editorMessageSuccess = false;
            rebuildWidgets();
            return;
        }
        editorMessage = "Saving…";
        editorMessageSuccess = false;
        sendEditorMutation(authoring.editingExisting
            ? new QuestMutation.UpdateQuest(request)
            : new QuestMutation.CreateQuest(request));
        rebuildWidgets();
    }

    private JsonObject draftSnapshot() {
        return currentAuthoringDraft().snapshot();
    }

    private QuestDraft currentAuthoringDraft() {
        return authoring.draft();
    }

    private JsonObject draftDisplay() {
        JsonElement display = currentAuthoringDraft().snapshot().get("display");
        return display != null && display.isJsonObject() ? display.getAsJsonObject() : new JsonObject();
    }

    private QuestSurfaceLayout.Node authoringNodeLayout() {
        QuestDefinition definition = QuestDefinition.parse("editor", currentAuthoringDraft().snapshot());
        return QuestSurfaceLayout.node(new QuestSurfaceLayout.QuestNode(
            "__draft",
            authoring.x,
            authoring.y,
            definition.display().iconSize(),
            definition.display().iconBackground()
        ));
    }

    private boolean hasUnsavedDraft() {
        if (hasUnsavedModal()) return true;
        return authoring.open && authoring.hasBaseline() && currentAuthoringDraft().isDirty();
    }

    private boolean hasUnsavedModal() {
        if (hasUnsavedChapterEditor()) return true;
        if (authoring.editingTask != null && authoring.editingTaskIndex >= 0 && authoring.editingTaskIndex < authoring.tasks.size() &&
            !authoring.editingTask.sameAs(authoring.tasks.get(authoring.editingTaskIndex))) return true;
        if (authoring.editingReward != null && authoring.editingRewardIndex >= 0 && authoring.editingRewardIndex < authoring.rewards.size() &&
            !authoring.editingReward.sameAs(authoring.rewards.get(authoring.editingRewardIndex))) return true;
        if (authoring.editingNestedReward != null) {
            List<QuestAuthoringSession.RewardDraft> nested = nestedRewards(authoring.editingReward);
            if (authoring.editingNestedRewardIndex >= 0 && authoring.editingNestedRewardIndex < nested.size() &&
                !authoring.editingNestedReward.sameAs(nested.get(authoring.editingNestedRewardIndex))) return true;
        }
        return false;
    }

    private void requestDiscard(Runnable action) {
        if (mutations.isPending()) return;
        if (modalHost.requestDismissal(hasUnsavedDraft(), action)) rebuildWidgets();
    }

    private void requestModalDiscard(Runnable action) {
        if (modalHost.requestDismissal(hasUnsavedModal(), action)) rebuildWidgets();
    }

    private void closeDraft() {
        authoring.discard();
        mutations.cancel();
    }

    public void handleEditorResult(QuestNetwork.EditorResultPayload result) {
        QuestMutationCoordinator.Completion completion = mutations.complete(result.requestId(), result.success(), result.message());
        if (completion == null) return;
        String operation = completion.pending().operation();
        diagnostics = QuestDiagnostics.decode(result.diagnostics());
        diagnosticsScroll = 0;
        editorMessage = result.message();
        editorMessageSuccess = result.success();
        if (result.success()) {
            if (clipboardMutationPending) clearClipboard();
            clipboardMutationPending = false;
            importController.clear();
            if ("reset_progress".equals(operation)) clearResetRewardSelections(completion.pending().request());
            if (List.of("create_quest", "update_quest", "delete_quest", "paste_quest", "import_quests").contains(operation)) {
                authoring.discard();
                mode.setEditorTool(EditorTool.SELECT);
            }
        }
        if (!result.success()) clipboardMutationPending = false;
        if (!result.success() && "import_quests".equals(operation)) {
            importController.applyServerDiagnostics(diagnostics);
            diagnostics = List.of();
            modalHost.replace(QuestModalHost.Modal.FILE_IMPORT);
            importScroll = 0;
        }
        if (!result.success() && "chapter_action".equals(operation)) {
            modalHost.replace(QuestModalHost.Modal.CHAPTER_EDITOR);
        }
        if (!result.success() && "remove_quest_group".equals(operation)) authoring.open = true;
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) closeDiagnosticsModal();
        rebuildWidgets();
    }

    /** Preserves the draft while detaching requests whose server outcome is unknown. */
    public void handleConnectionLost() {
        QuestMutationCoordinator.Pending interrupted = mutations.connectionLost();
        pendingQuestFileRequestId = -1;
        pendingQuestFileId = null;
        clipboardMutationPending = false;
        if (interrupted == null) return;
        editorMessage = "Connection lost while '" + interrupted.operation()
            + "' was pending. The server may have applied it; review the refreshed quests before retrying.";
        editorMessageSuccess = false;
    }

    private void clearResetRewardSelections(JsonObject request) {
        if (!request.has("scope") || !request.has("quest")) return;
        String scope = request.get("scope").getAsString();
        String questId = request.get("quest").getAsString();
        if ("reward".equals(scope) && request.has("entry")) {
            rewardSelections.remove(questId + "|" + request.get("entry").getAsString());
        } else if ("quest".equals(scope)) {
            rewardSelections.keySet().removeIf(key -> key.startsWith(questId + "|"));
        }
    }

    private static boolean canEdit() {
        return Minecraft.getInstance().player != null &&
            Commands.LEVEL_GAMEMASTERS.check(Minecraft.getInstance().player.permissions());
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
        authoringPanel.setViewport(font, width, height);
        ChapterDisplay chapterDisplay = chapterDisplays.get(group);
        if (chapterDisplay != null && !chapterDisplay.background.isBlank()) {
            try {
                Identifier texture = Identifier.parse(chapterDisplay.background);
                int backgroundX = sidebarWidth();
                int backgroundWidth = Math.max(1, graphCanvasRight() - backgroundX);
                int backgroundY = graphCanvasTop();
                int backgroundHeight = Math.max(1, height - backgroundY);
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    backgroundX,
                    backgroundY,
                    0.0f,
                    0.0f,
                    backgroundWidth,
                    backgroundHeight,
                    backgroundWidth,
                    backgroundHeight,
                    (chapterDisplay.backgroundOpacity * 255 / 100 << 24) | 0x00FFFFFF
                );
            } catch (RuntimeException ignored) { }
        }
        QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
        QuestSurfaceLayout.Layout surface = surfaceLayout();
        graphics.enableScissor(
            (int) canvas.x(),
            (int) canvas.y(),
            (int) canvas.maxX(),
            (int) canvas.maxY()
        );
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) canvas.centerX(), (float) canvas.centerY());
        graphics.pose().scale((float) graphViewport.state().zoom());
        graphics.pose().translate((float) -graphViewport.state().centerWorldX(), (float) -graphViewport.state().centerWorldY());
        drawGraphGrid(graphics);
        drawDependencyPaths(graphics, surface);
        QuestGraphLayout.Point mouseWorld = QuestGraphLayout.screenToWorld(
            canvas, graphViewport.state(), mouseX, mouseY
        );
        drawLinkPreview(graphics, mouseWorld.x(), mouseWorld.y());
        drawQuestNodes(
            graphics,
            surface,
            mouseWorld.x(),
            mouseWorld.y(),
            !detailsDockContains(mouseX, mouseY)
        );
        drawCreateQuestPreview(graphics);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        renderMinimap(graphics, surface);
        drawPanelScrims(graphics);
        QuestModalHost.Modal activeOverlay = modalHost.active();
        boolean diagnosticsModal = activeOverlay == QuestModalHost.Modal.DIAGNOSTICS;
        boolean importModal = activeOverlay == QuestModalHost.Modal.FILE_IMPORT;
        boolean rawInspectorModal = activeOverlay == QuestModalHost.Modal.RAW_INSPECTOR;
        boolean taskModal = activeOverlay == QuestModalHost.Modal.TASK_EDITOR
            || activeOverlay == QuestModalHost.Modal.NESTED_TASKS
            || activeOverlay == QuestModalHost.Modal.NESTED_TASK_CHOOSER
            || (activeOverlay == QuestModalHost.Modal.PICKER && modalHost.contains(QuestModalHost.Modal.TASK_EDITOR))
            || (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION && modalHost.contains(QuestModalHost.Modal.TASK_EDITOR));
        boolean rewardModal = activeOverlay == QuestModalHost.Modal.REWARD_EDITOR
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARDS
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARD_CHOOSER
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARD_EDITOR
            || (activeOverlay == QuestModalHost.Modal.PICKER && modalHost.contains(QuestModalHost.Modal.REWARD_EDITOR))
            || (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION && modalHost.contains(QuestModalHost.Modal.REWARD_EDITOR));
        boolean modalVisible = modalHost.rendersOverlay();
        if (modalVisible) {
            drawBaseForeground(graphics, mouseX, mouseY);
            if (diagnosticsModal) {
                drawDiagnosticsModal(graphics);
            } else if (importModal) {
                drawImportModal(graphics);
            } else if (rawInspectorModal) {
                drawRawInspector(graphics);
            } else if (activeOverlay == QuestModalHost.Modal.DESCRIPTION_EDITOR) {
                drawDescriptionEditor(graphics, mouseX, mouseY);
            } else {
                if (taskModal) {
                    authoringPanel.drawTaskEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER && !modalHost.showsNestedTasks()) authoringPanel.drawTaskEditorForeground(graphics);
                }
                if (rewardModal) {
                    authoringPanel.drawRewardEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER) authoringPanel.drawRewardModalForeground(graphics, mouseX, mouseY);
                }
                if (activeOverlay == QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION) drawDeleteQuestConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION) drawProgressResetConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION) drawDiscardConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.TASK_DELETE_CONFIRMATION) drawDeleteTaskConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.CHAPTER_EDITOR) drawChapterEditor(graphics);
                if (activeOverlay == QuestModalHost.Modal.PASTE_ID_PROMPT) drawPasteIdPrompt(graphics);
                if (activeOverlay == QuestModalHost.Modal.PICKER) drawPickerPanel(graphics);
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            // An overlay may own the widget tree but leave its parent editor's
            // state populated. Never redraw that parent's manual foreground.
            if (picker == Picker.NONE && !rawInspectorModal && !diagnosticsModal && !importModal) {
                if (taskModal) {
                    if (modalHost.showsNestedTasks()) authoringPanel.drawNestedTasksForeground(graphics, mouseX, mouseY);
                    else authoringPanel.drawTaskEditorForeground(graphics);
                }
                if (rewardModal) authoringPanel.drawRewardModalForeground(graphics, mouseX, mouseY);
            } else if (activeOverlay == QuestModalHost.Modal.PICKER) {
                drawPickerContents(graphics, mouseX, mouseY);
            }
            return;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawBaseForeground(graphics, mouseX, mouseY);
    }

    private void drawBaseForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!editorMessage.isEmpty() && !authoring.open) {
            HeaderLayout header = headerLayout();
            int x = sidebarWidth() + 8;
            int right = Math.max(x + 1, canvasRight() - 4);
            graphics.fill(x - 4, header.statusY() - 3, right, header.statusY() + HEADER_ROW_HEIGHT, 0xAA20242B);
            graphics.enableScissor(x, header.statusY() - 2, right, header.statusY() + HEADER_ROW_HEIGHT);
            drawClippedText(
                graphics,
                editorMessage,
                x,
                header.statusY(),
                Math.max(1, right - x - 4),
                editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999
            );
            graphics.disableScissor();
        }
        if (sidebarOpen) graphics.text(
            font,
            Component.literal("Theseus"),
            8,
            4,
            0xFFFFFFFF,
            true
        );
        if (!mode.isAuthoring()) {
            graphics.text(
                font,
                Component.literal(group),
                sidebarWidth() + 10,
                10,
                0xFFB8C0CC,
                false
            );
        }
        if (mode.isAuthoring() && mode.editorTool() == EditorTool.LINK) {
            graphics.text(
                font,
                Component.translatable(linkSourceId == null
                    ? "gui.theseus.editor.link_select_prerequisite"
                    : "gui.theseus.editor.link_select_dependent"),
                sidebarWidth() + 112,
                10,
                0xFF9FDFFF,
                false
            );
        }
        drawChapterScrollbar(graphics);
        if (authoring.open) authoringPanel.drawCreateQuestDock(
            graphics, mouseX, mouseY, editorMessage, editorMessageSuccess
        );
        else if (detailsOpen) detailsPanel.render(
            graphics, font, width, height, detailsWidth(), detailPanelModel(), mouseX, mouseY
        );
        drawContextMenu(graphics, mouseX, mouseY);
    }

    private void drawChapterScrollbar(GuiGraphicsExtractor graphics) {
        if (!sidebarOpen || !chapterListState.hasOverflow()) return;
        int top = chapterListState.viewportTop();
        int bottom = chapterListState.viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int thumbHeight = Math.max(
            8,
            trackHeight * chapterListState.visibleCapacity() / Math.max(1, chapterListState.chapterCount())
        );
        int maxScroll = chapterListState.maxFirstVisibleRow();
        int thumbY = top + (trackHeight - thumbHeight) * chapterListState.firstVisibleRow()
            / Math.max(1, maxScroll);
        int x = Math.max(0, sidebarWidth() - 5);
        graphics.fill(x, top, x + 2, bottom, 0x6649515E);
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight,
            ClientThemeLoader.active().genericControls().accent());
    }

    private void renderMinimap(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface
    ) {
        QuestMinimap.MapBounds mapBounds = minimapPanel.bounds(graphCanvasBounds(), minimapSettings());
        if (mapBounds == null) return;
        minimapPanel.render(
            graphics,
            font,
            mapBounds,
            minimapScene(surface),
            ClientThemeLoader.active().genericControls().accent(),
            ClientThemeLoader.active().genericControls().text()
        );
    }

    private QuestMinimapPanel.Scene minimapScene(QuestSurfaceLayout.Layout surface) {
        List<QuestMinimapPanel.Edge> edges = new ArrayList<>();
        for (ClientQuest quest : visibleQuests()) {
            QuestSurfaceLayout.Node child = surface.find(quest.definition.id()).orElse(null);
            if (child == null || !quest.definition.settings().showDependencyArrow()) continue;
            QuestGraphLayout.Point childCenter = questCenter(quest);
            for (String dependency : quest.definition.dependencies()) {
                if (surface.find(dependency).isEmpty()) continue;
                QuestGraphLayout.Point parentCenter = questCenter(questById(dependency));
                edges.add(new QuestMinimapPanel.Edge(
                    parentCenter.x(),
                    parentCenter.y(),
                    childCenter.x(),
                    childCenter.y(),
                    0xAA9AA4B2
                ));
            }
        }

        List<QuestMinimapPanel.Marker> markers = new ArrayList<>();
        for (ClientQuest quest : visibleQuests()) {
            QuestSurfaceLayout.Node node = surface.find(quest.definition.id()).orElse(null);
            if (node == null) continue;
            QuestGraphLayout.Point center = questCenter(quest);
            markers.add(new QuestMinimapPanel.Marker(
                center.x(),
                center.y(),
                node.minimapMarkSize(),
                nodeStateColor(quest),
                quest.definition.id().equals(selectedQuestId)
            ));
        }
        return new QuestMinimapPanel.Scene(
            surface.worldBounds(16),
            QuestGraphLayout.visibleWorld(graphCanvasBounds(), graphViewport.state()),
            edges,
            markers
        );
    }

    private void toggleMinimapDocking() {
        TheseusClientOptions.MinimapMode currentMode = TheseusClientOptions.defaultMinimapMode();
        QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
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
        minimapPanel.clearTransientState();
        rebuildWidgets();
    }

    private void addDiagnosticsModalWidgets() {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 330, top + 264).withSize(96, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.close")));
            widget.withCallback(() -> {
                closeDiagnosticsModal();
                rebuildWidgets();
            });
        }));
    }

    private void closeDiagnosticsModal() {
        modalHost.close();
    }

    private void openImportDiagnostics(String key) {
        QuestImportController.Entry entry = importController.entries().stream()
            .filter(candidate -> candidate.key().equals(key))
            .findFirst()
            .orElse(null);
        if (entry == null || entry.diagnostics().isEmpty()) return;
        diagnostics = entry.diagnostics();
        diagnosticsScroll = 0;
        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        rebuildWidgets();
    }

    private void openBatchDiagnostics() {
        if (importController.batchDiagnostics().isEmpty()) return;
        diagnostics = importController.batchDiagnostics();
        diagnosticsScroll = 0;
        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        rebuildWidgets();
    }

    private List<String> diagnosticLines(int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (QuestDiagnostics.Diagnostic diagnostic : diagnostics) {
            String prefix = "[" + diagnostic.severity() + "] "
                + (diagnostic.questId() == null || diagnostic.questId().isBlank() ? "" : diagnostic.questId() + " ")
                + diagnostic.path();
            addWrappedDiagnosticLine(lines, prefix, maxWidth);
            addWrappedDiagnosticLine(lines, diagnostic.message(), maxWidth);
            if (diagnostic.suggestedFix() != null && !diagnostic.suggestedFix().isBlank()) {
                addWrappedDiagnosticLine(lines, "Fix: " + diagnostic.suggestedFix(), maxWidth);
            }
        }
        return List.copyOf(lines);
    }

    private int importVisibleRows() {
        return importController.batchDiagnostics().isEmpty() ? 8 : 7;
    }

    private void addWrappedDiagnosticLine(List<String> lines, String value, int maxWidth) {
        String remaining = value == null ? "" : value;
        if (remaining.isEmpty()) {
            lines.add("");
            return;
        }
        while (!remaining.isEmpty()) {
            String line = font.plainSubstrByWidth(remaining, maxWidth);
            if (line.isEmpty()) line = remaining.substring(0, 1);
            lines.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
    }

    private void drawDiagnosticsModal(GuiGraphicsExtractor graphics) {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + 440, top + 300, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 439, top + 28, 0xFF303640);
        graphics.text(font, Component.translatable("gui.theseus.editor.validation_diagnostics"), left + 12, top + 9, 0xFFFFFFFF, true);
        int visibleRows = 13;
        List<String> lines = diagnosticLines(416);
        int maxScroll = Math.max(0, lines.size() - visibleRows);
        diagnosticsScroll = Math.max(0, Math.min(maxScroll, diagnosticsScroll));
        graphics.enableScissor(left + 8, top + 34, left + 432, top + 254);
        for (int index = diagnosticsScroll; index < lines.size() && index < diagnosticsScroll + visibleRows; index++) {
            int y = top + 38 + (index - diagnosticsScroll) * 16;
            String line = lines.get(index);
            int color = line.startsWith("[ERROR]") ? 0xFFFF9999
                : line.startsWith("[WARNING]") ? 0xFFFFD27D
                : line.startsWith("Fix:") ? 0xFF9FDFFF : 0xFFB8C0CC;
            graphics.text(font, Component.literal(line), left + 12, y, color, false);
        }
        graphics.disableScissor();
        if (diagnostics.isEmpty()) graphics.text(font, Component.translatable("gui.theseus.editor.no_diagnostics_reported"), left + 12, top + 42, 0xFFB8C0CC, false);
        else if (maxScroll > 0) graphics.text(font, Component.translatable("gui.theseus.editor.scroll_for_more"), left + 12, top + 270, 0xFF8893A3, false);
    }

    private void drawRawInspector(GuiGraphicsExtractor graphics) {
        int inspectorWidth = Math.min(480, width - 32);
        int inspectorHeight = Math.min(280, height - 48);
        int left = (width - inspectorWidth) / 2;
        int top = (height - inspectorHeight) / 2;
        graphics.fill(0, 0, width, height, 0xCC000000);
        graphics.fill(left, top, left + inspectorWidth, top + inspectorHeight, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + inspectorWidth - 1, top + 28, 0xFF303640);
        graphics.text(font, Component.translatable("gui.theseus.editor.read_only_title", rawInspectorTitle), left + 12, top + 9, 0xFFFFFFFF, true);
    }

    private void addImportModalWidgets() {
        int left = (width - 500) / 2;
        int top = (height - 340) / 2;
        importIdFields.clear();
        List<QuestImportController.Entry> entries = importController.entries();
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(importScroll, Math.max(0, entries.size() - visibleRows)));
        int listTop = top + (importController.batchDiagnostics().isEmpty() ? 52 : 64);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + (index - first) * 32;
            EditBox id = new EditBox(font, left + 250, y, 100, 18, Component.translatable("gui.theseus.editor.quest_id"));
            id.setValue(entry.id() == null ? "" : entry.id());
            id.setResponder(value -> {
                importController.changeId(entry.key(), value);
                updateImportMessage();
            });
            importIdFields.put(entry.key(), id);
            addRenderableWidget(id);
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 354, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.details")));
                widget.active = !entry.diagnostics().isEmpty();
                widget.withCallback(() -> openImportDiagnostics(entry.key()));
                widget.withTooltip(Component.translatable("gui.theseus.editor.view_every_diagnostic_for_this_file"));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 420, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.remove")));
                widget.withCallback(() -> removeImportFile(entry.key()));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(this::cancelImport);
        }));
        if (!importController.batchDiagnostics().isEmpty()) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 120, top + 304).withSize(120, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.batch_details")));
            widget.withCallback(this::openBatchDiagnostics);
            widget.withTooltip(Component.translatable("gui.theseus.editor.view_batch_level_server_diagnostics"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 388, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.import")));
            widget.active = importController.canSubmit() && !mutations.isPending();
            widget.withCallback(this::sendImport);
        }));
    }

    private void drawImportModal(GuiGraphicsExtractor graphics) {
        int left = (width - 500) / 2;
        int top = (height - 340) / 2;
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + 500, top + 340, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 499, top + 28, 0xFF303640);
        graphics.text(font, Component.translatable("gui.theseus.editor.import_quests"), left + 12, top + 9, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.each_file_is_checked_independently_import_is_all_or_nothing"), left + 12, top + 30, 0xFFB8C0CC, false);
        if (!importController.batchDiagnostics().isEmpty()) {
            long errors = importController.batchDiagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            graphics.text(font, Component.translatable("gui.theseus.editor.batch_rejected", errors), left + 12, top + 42, 0xFFFF9999, false);
        }
        List<QuestImportController.Entry> entries = importController.entries();
        int listTop = top + (importController.batchDiagnostics().isEmpty() ? 52 : 64);
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(importScroll, Math.max(0, entries.size() - visibleRows)));
        graphics.enableScissor(left + 8, listTop - 4, left + 492, top + 292);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + 4 + (index - first) * 32;
            int color = entry.valid() ? 0xFF77DD99 : 0xFFFF9999;
            String label = entry.key() + " (" + entry.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes)";
            if (label.length() > 42) label = label.substring(0, 41) + "…";
            graphics.text(font, Component.literal(label), left + 12, y, 0xFFFFFFFF, false);
            long errors = entry.diagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            long warnings = entry.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).count();
            String detail = entry.diagnostics().isEmpty() ? "ready" : errors + " error(s), " + warnings + " warning(s) — Details";
            if (detail.length() > 42) detail = detail.substring(0, 41) + "…";
            graphics.text(font, Component.literal(detail), left + 12, y + 14, color, false);
        }
        graphics.disableScissor();
    }

    private void cancelImport() {
        importController.clear();
        modalHost.close();
        importIdFields.clear();
        rebuildWidgets();
    }

    private void drawPanelScrims(GuiGraphicsExtractor graphics) {
        int sidebarWidth = sidebarWidth();
        graphics.fill(0, 0, sidebarWidth, height, 0xF020242B);
        graphics.verticalLine(sidebarWidth, 0, height, 0xFF49515E);
        if (detailsOpen || authoring.open) {
            int detailsLeft = width - detailsWidth();
            graphics.fill(detailsLeft, 0, width, height, 0xD020242B);
            graphics.verticalLine(detailsLeft, 0, height, 0xAA49515E);
        }
    }

    private void drawGraphGrid(GuiGraphicsExtractor graphics) {
        if (!TheseusClientOptions.showGrid()) return;
        double screenSpacing = QuestGraphLayout.GRID_CELL_SIZE * graphViewport.state().zoom();
        if (screenSpacing < 1.5) return;
        QuestGraphLayout.WorldBounds visible = QuestGraphLayout.visibleWorld(
            graphCanvasBounds(), graphViewport.state()
        );
        QuestGraphLayout.GridLineRange range = QuestGraphLayout.visibleGridLineRange(visible);
        if (range.isEmpty()) return;
        int configured = ClientThemeLoader.active().questTree().grid();
        int alpha = configured >>> 24;
        if (screenSpacing < 8) alpha = (int) Math.round(alpha * Math.max(0.2, screenSpacing / 8.0));
        int color = (Math.max(1, Math.min(255, alpha)) << 24) | (configured & 0x00FFFFFF);
        int minY = (int) Math.floor(visible.minY());
        int maxY = (int) Math.ceil(visible.maxY());
        int minX = (int) Math.floor(visible.minX());
        int maxX = (int) Math.ceil(visible.maxX());
        for (long x = range.firstX(); x <= range.lastX(); x += QuestGraphLayout.GRID_CELL_SIZE) {
            graphics.fill((int) x, minY, (int) x + 1, maxY, color);
        }
        for (long y = range.firstY(); y <= range.lastY(); y += QuestGraphLayout.GRID_CELL_SIZE) {
            graphics.fill(minX, (int) y, maxX, (int) y + 1, color);
        }
    }

    private void drawCreateQuestPreview(GuiGraphicsExtractor graphics) {
        if (!mode.isAuthoring() || !authoring.open) return;
        QuestDefinition definition = QuestDefinition.parse("editor", currentAuthoringDraft().snapshot());
        QuestSurfaceLayout.Node node = authoringNodeLayout();
        QuestBackground background = questBackground(authoring.background);
        drawQuestBackground(graphics, node, background.texture, 0, 0xCCFFFFFF);
        QuestGraphLayout.NodeBounds icon = node.iconBounds();
        QuestPresentation.renderQuestIcon(
            graphics,
            definition,
            (int) Math.round(icon.x()),
            (int) Math.round(icon.y()),
            (int) Math.round(icon.width())
        );
        QuestGraphLayout.NodeBounds bounds = node.bounds();
        graphics.outline(
            (int) Math.round(bounds.x()) - 2,
            (int) Math.round(bounds.y()) - 2,
            (int) Math.round(bounds.width()) + 4,
            (int) Math.round(bounds.height()) + 4,
            0x99FFD966
        );
    }

    private void drawPickerPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = pickerLeft();
        int top = pickerTop();
        graphics.fill(left, top, left + 200, top + 176, 0xFF20242B);
        graphics.outline(left, top, 200, 176, 0xFF8A929F);
        graphics.text(
            font,
            Component.translatable(switch (picker) {
                case ICON -> "gui.theseus.editor.choose_item";
                case ENTITY -> "gui.theseus.editor.choose_entity";
                default -> "gui.theseus.editor.choose_background";
            }),
            left + 12,
            top + 10,
            0xFFFFFFFF,
            true
        );
    }

    private void drawDescriptionEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int modalWidth = Math.min(760, width - 24);
        int modalHeight = Math.min(420, height - 24);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        int gutter = 8;
        int paneWidth = (modalWidth - 32 - gutter) / 2;
        int previewX = left + 12 + paneWidth + gutter;
        int previewY = top + 61;
        int previewHeight = modalHeight - 103;
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(left, top, left + modalWidth, top + modalHeight, 0xFF20242B);
        graphics.outline(left, top, modalWidth, modalHeight, 0xFF8A929F);
        graphics.text(font, Component.translatable("gui.theseus.editor.rich_description"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.markdown"), left + 12, top + 52, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.player_preview"), previewX, top + 52, 0xFFB8C0CC, false);
        int editorX = left + 12;
        int editorY = top + 61;
        graphics.fill(editorX, editorY, editorX + paneWidth, editorY + (modalHeight - 103), 0xFF171A20);
        graphics.outline(editorX, editorY, paneWidth, modalHeight - 103, 0xFF49515E);
        graphics.fill(previewX, previewY, previewX + paneWidth, previewY + previewHeight, 0xFF171A20);
        graphics.enableScissor(previewX + 1, previewY + 1, previewX + paneWidth - 1, previewY + previewHeight - 1);
        DescriptionDocument document = DescriptionParser.parse(List.of(descriptionEditorValue.split("\n", -1)));
        QuestDescriptionRenderer.Result result = QuestDescriptionRenderer.render(
            graphics, font, document, previewX + 7, previewY + 7 - descriptionPreviewScroll, paneWidth - 14,
            (kind, id) -> descriptionDraftReference(kind, id)
        );
        descriptionPreviewMaxScroll = Math.max(0, result.height() - previewHeight + 14);
        descriptionPreviewScroll = Math.min(descriptionPreviewScroll, descriptionPreviewMaxScroll);
        graphics.disableScissor();
        if (!document.warnings().isEmpty()) {
            drawClippedText(
                graphics,
                document.warnings().size() + " preview warning" + (document.warnings().size() == 1 ? "" : "s"),
                left + 12,
                top + modalHeight - 25,
                Math.max(40, modalWidth - 200),
                0xFFFFAA77
            );
        }
    }

    private String descriptionDraftReference(DescriptionDocument.BlockKind kind, String id) {
        if (kind == DescriptionDocument.BlockKind.TASK) {
            return authoring.tasks.stream().filter(task -> task.id.equals(id))
                .map(authoringPanel::taskDisplayLabel).findFirst().orElse(id + " (missing)");
        }
        return authoring.rewards.stream().filter(reward -> reward.id.equals(id))
            .map(authoringPanel::rewardDisplayLabel).findFirst().orElse(id + " (missing)");
    }

    private void drawPickerContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (picker == Picker.ICON) drawItemPicker(graphics, mouseX, mouseY);
        else if (picker == Picker.ENTITY) drawEntityPicker(graphics, mouseX, mouseY);
        else drawBackgroundPicker(graphics, mouseX, mouseY);
    }

    private void drawDeleteQuestConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.translatable("gui.theseus.editor.confirm_delete_quest"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.translatable("gui.theseus.editor.this_deletes_the_quest_file_and_resets_its_player_progress"), left + 12, top + 32, 216, 0xFFFFAAAA, false);
    }

    private void drawProgressResetConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 280) / 2;
        int top = (height - 142) / 2;
        graphics.fill(left, top, left + 280, top + 142, 0xFF20242B);
        graphics.outline(left, top, 280, 142, 0xFFFF6B6B);
        QuestModalHost.ProgressResetTarget target = progressResetTarget;
        Component title = Component.translatable(target == null ? "gui.theseus.editor.confirm_reset_progress" : switch (target.scope()) {
            case "quest" -> "gui.theseus.editor.confirm_reset_quest_progress";
            case "task" -> "gui.theseus.editor.confirm_reset_task_progress";
            case "reward" -> "gui.theseus.editor.confirm_reset_reward_progress";
            default -> "gui.theseus.editor.confirm_reset_progress";
        });
        Component detail = target == null
            ? Component.translatable("gui.theseus.editor.no_reset_target")
            : switch (target.scope()) {
                case "quest" -> Component.translatable("gui.theseus.editor.reset_quest_progress_body", target.questTitle());
                case "task" -> Component.translatable("gui.theseus.editor.reset_task_progress_body", target.displayLabel(), target.entryId(), target.questTitle());
                case "reward" -> Component.translatable("gui.theseus.editor.reset_reward_progress_body", target.displayLabel(), target.entryId(), target.questTitle());
                default -> Component.translatable("gui.theseus.editor.reset_selected_progress_body");
        };
        graphics.text(font, title, left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, detail, left + 12, top + 34, 256, 0xFFFFC4C4, false);
    }

    private void drawDeleteTaskConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.translatable("gui.theseus.editor.confirm_delete_task"), left + 12, top + 12, 0xFFFFFFFF, true);
        String id = authoring.taskDeleteConfirmation >= 0 && authoring.taskDeleteConfirmation < authoring.tasks.size()
            ? authoring.tasks.get(authoring.taskDeleteConfirmation).id : "this task";
        graphics.textWithWordWrap(font, Component.translatable("gui.theseus.editor.confirm_delete_task_body", id), left + 12, top + 34, 216, 0xFFFFAAAA, false);
    }

    private void drawDiscardConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 260) / 2;
        int top = (height - 116) / 2;
        graphics.fill(left, top, left + 260, top + 116, 0xFF20242B);
        graphics.outline(left, top, 260, 116, 0xFF8A929F);
        graphics.text(font, Component.translatable("gui.theseus.editor.discard_unsaved_changes"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.translatable("gui.theseus.editor.the_quest_draft_has_changes_that_have_not_been_saved"), left + 12, top + 34, 236, 0xFFFFCC88, false);
    }

    private void drawChapterEditor(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 280) / 2;
        int top = chapterEditorTop();
        graphics.fill(left, top, left + 280, top + 250, 0xFF20242B);
        graphics.outline(left, top, 280, 250, 0xFF8A929F);
        graphics.text(font, Component.translatable(chapterEditorOriginal == null
            ? "gui.theseus.editor.create_chapter"
            : "gui.theseus.editor.edit_chapter"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.name"), left + 14, top + 36, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.chapter_icon"), left + 56, top + 88, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.background"), left + 14, top + 114, 0xFFB8C0CC, false);
        graphics.text(font, Component.translatable("gui.theseus.editor.background_opacity"), left + 14, top + 146, 0xFFB8C0CC, false);
        if (!chapterEditorError.isEmpty()) graphics.text(font, Component.literal(chapterEditorError), left + 14, top + 185, 0xFFFF7777, false);
    }

    private void drawPasteIdPrompt(GuiGraphicsExtractor graphics) {
        int left = (width - 280) / 2;
        int top = (height - 130) / 2;
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(left, top, left + 280, top + 130, 0xFF20242B);
        graphics.outline(left, top, 280, 130, 0xFF8A929F);
        graphics.text(font, Component.translatable("gui.theseus.editor.paste_quest"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.theseus.editor.choose_the_id_for_the_cloned_quest"), left + 14, top + 34, 0xFFB8C0CC, false);
    }

    private void drawEntityPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<EntityType<?>> entities = filteredPickerEntities();
        int end = Math.min(entities.size(), pickerScroll + 40);
        for (int index = pickerScroll; index < end; index++) {
            int visible = index - pickerScroll;
            int x = left + visible % 8 * 22;
            int y = top + visible / 8 * 22;
            EntityType<?> entity = entities.get(index);
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 20, y + 20, hovered ? 0xFF59616E : 0xFF343A44);
            graphics.outline(x, y, 20, 20, 0xFF707987);
            graphics.item(entityIcon(entity), x + 2, y + 2);
            if (hovered) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
                String label = entity.getDescription().getString() + (id == null ? "" : " (" + id + ")");
                drawClippedText(graphics, label, pickerLeft() + 12, pickerTop() + 164, 176, 0xFFFFFFFF);
            }
        }
    }

    private void drawItemPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<Item> items = filteredPickerItems();
        int end = Math.min(items.size(), pickerScroll + 40);
        for (int index = pickerScroll; index < end; index++) {
            int visible = index - pickerScroll;
            int x = left + visible % 8 * 22;
            int y = top + visible / 8 * 22;
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 20, y + 20, hovered ? 0xFF59616E : 0xFF343A44);
            graphics.outline(x, y, 20, 20, 0xFF707987);
            ItemStack stack = new ItemStack(items.get(index));
            graphics.item(stack, x + 2, y + 2);
            if (hovered) {
                String label = items.get(index).getName(stack).getString() + " (" + BuiltInRegistries.ITEM.getKey(items.get(index)) + ")";
                drawClippedText(graphics, label, pickerLeft() + 12, pickerTop() + 164, 176, 0xFFFFFFFF);
            }
        }
    }

    private void drawBackgroundPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 34;
        for (int index = 0; index < QUEST_BACKGROUNDS.size(); index++) {
            Identifier texture = QUEST_BACKGROUNDS.get(index);
            int x = left + index % 4 * 44;
            int y = top + index / 4 * 42;
            boolean hovered = mouseX >= x && mouseX < x + 40 && mouseY >= y && mouseY < y + 38;
            graphics.fill(x, y, x + 40, y + 38, hovered ? 0xFF59616E : 0xFF343A44);
            QuestBackground background = questBackground(texture.toString());
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + (40 - background.width) / 2,
                y + (34 - background.height) / 2,
                0.0f,
                0.0f,
                background.width,
                background.height,
                background.width * 5,
                background.height,
                0xFFFFFFFF
            );
        }
    }

    private void drawDependencyPaths(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface
    ) {
        for (ClientQuest quest : visibleQuests()) {
            QuestSurfaceLayout.Node child = dependencyNode(surface, quest.definition.id());
            boolean showArrow = quest.definition.settings().showDependencyArrow();
            if (authoring.open && authoring.editingExisting
                && quest.definition.id().equals(authoring.originalId)) {
                showArrow = authoring.showDependencyArrow;
            }
            if (
                child == null ||
                !showArrow
            ) continue;
            for (String dependency : quest.definition.dependencies()) {
                QuestSurfaceLayout.Node parent = dependencyNode(surface, dependency);
                if (parent == null) continue;
                PathPoint parentCenter = new PathPoint(parent.centerX(), parent.centerY());
                PathPoint childCenter = new PathPoint(child.centerX(), child.centerY());
                // Nodes render after connectors, so center-to-center paths disappear cleanly beneath the frames.
                PathPoint start = parentCenter;
                PathPoint tip = childCenter;
                double dx = tip.x - start.x;
                double dy = tip.y - start.y;
                double length = Math.hypot(dx, dy);
                if (length < 4.0) continue;
                drawTexturedPath(graphics, start, tip, quest.unlocked);
            }
        }
    }

    private QuestSurfaceLayout.Node dependencyNode(
        QuestSurfaceLayout.Layout surface,
        String questId
    ) {
        QuestSurfaceLayout.Node node = surface.find(questId).orElse(null);
        if (node != null) return node;
        if (authoring.open && authoring.editingExisting && questId.equals(authoring.originalId)) {
            return authoringNodeLayout();
        }
        return null;
    }

    private void drawLinkPreview(
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        if (!mode.isAuthoring() || mode.editorTool() != EditorTool.LINK || linkSourceId == null) return;
        ClientQuest source = questById(linkSourceId);
        if (source == null) return;
        QuestGraphLayout.Point sourceCenter = questCenter(source);
        drawTexturedPath(
            graphics,
            new PathPoint(sourceCenter.x(), sourceCenter.y()),
            new PathPoint(mouseX, mouseY),
            true
        );
    }

    private void drawQuestNodes(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface,
        double mouseX,
        double mouseY,
        boolean hoverEnabled
    ) {
        for (ClientQuest quest : visibleQuests()) {
            if (authoring.editingExisting && authoring.open && quest.definition.id().equals(authoring.originalId)) continue;
            QuestSurfaceLayout.Node node = surface.find(quest.definition.id()).orElse(null);
            if (node == null) continue;
            QuestGraphLayout.NodeBounds bounds = node.bounds();
            QuestBackground background = questBackground(quest.definition);
            int frame = quest.claimed ? 3 : quest.complete ? 2 : quest.unlocked ? 1 : 0;
            drawQuestBackground(graphics, node, background.texture, frame, 0xFFFFFFFF);
            if (hoverEnabled && node.contains(mouseX, mouseY)) {
                drawQuestBackground(graphics, node, background.texture, 4, 0xFFFFFFFF);
            }
            int nodeX = (int) Math.round(bounds.x());
            int nodeY = (int) Math.round(bounds.y());
            int nodeWidth = (int) Math.round(bounds.width());
            int nodeHeight = (int) Math.round(bounds.height());
            if (quest.definition.id().equals(selectedQuestId)) {
                graphics.outline(
                    nodeX - 2,
                    nodeY - 2,
                    nodeWidth + 4,
                    nodeHeight + 4,
                    0xFFFFD966
                );
            }
            if (quest.definition.id().equals(linkSourceId)) {
                graphics.outline(
                    nodeX - 4,
                    nodeY - 4,
                    nodeWidth + 8,
                    nodeHeight + 8,
                    0xFF6CCBFF
                );
            }
            QuestGraphLayout.NodeBounds icon = node.iconBounds();
            QuestPresentation.renderQuestIcon(
                graphics,
                quest.definition,
                (int) Math.round(icon.x()),
                (int) Math.round(icon.y()),
                (int) Math.round(icon.width())
            );
        }
    }

    private static void drawQuestBackground(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Node node,
        Identifier texture,
        int frame,
        int color
    ) {
        QuestGraphLayout.NodeBounds background = node.backgroundBounds();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) background.x(), (float) background.y());
        graphics.pose().scale(
            (float) background.width() / node.textureFrameWidth(),
            (float) background.height() / node.textureFrameHeight()
        );
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            0,
            0,
            frame * node.textureFrameWidth(),
            0.0f,
            node.textureFrameWidth(),
            node.textureFrameHeight(),
            node.textureFrameWidth() * 5,
            node.textureFrameHeight(),
            color
        );
        graphics.pose().popMatrix();
    }

    private static QuestBackground questBackground(QuestDefinition definition) {
        return questBackground(definition.display().iconBackground());
    }

    private static QuestBackground questBackground(String value) {
        try {
            Identifier texture = Identifier.parse(value);
            String path = texture.getPath();
            if (path.endsWith("/diamonds.png")) return new QuestBackground(texture, -4, -4, 32, 32);
            if (path.endsWith("/hearts.png")) return new QuestBackground(texture, -4, -2, 32, 32);
            if (path.endsWith("/pentagons.png")) return new QuestBackground(texture, 0, -2, 24, 24);
            return new QuestBackground(texture, 0, 0, NODE_WIDTH, NODE_HEIGHT);
        } catch (RuntimeException exception) {
            return new QuestBackground(DEFAULT_QUEST_FRAME, 0, 0, NODE_WIDTH, NODE_HEIGHT);
        }
    }

    private int pickerLeft() {
        return (width - 200) / 2;
    }

    private int pickerTop() {
        return (height - 176) / 2;
    }

    private int chapterEditorTop() {
        return (height - 250) / 2;
    }

    private List<Item> filteredPickerItems() {
        String query = pickerSearch == null
            ? ""
            : pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return BuiltInRegistries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .filter(item -> {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                return query.isEmpty() ||
                    id.toString().contains(query) ||
                    item.getName(new ItemStack(item)).getString().toLowerCase(java.util.Locale.ROOT).contains(query);
            })
            .toList();
    }

    private List<EntityType<?>> filteredPickerEntities() {
        String query = pickerSearch == null
            ? ""
            : pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(entity -> {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
                return query.isEmpty() || id.toString().contains(query) ||
                    entity.getDescription().getString().toLowerCase(java.util.Locale.ROOT).contains(query);
            })
            .sorted(Comparator.comparing(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString()))
            .toList();
    }

    private static ItemStack entityIcon(EntityType<?> entity) {
        return SpawnEggItem.byId(entity)
            .map(holder -> new ItemStack(holder.value()))
            .orElseGet(() -> new ItemStack(Items.ARMOR_STAND));
    }

    private QuestDraftValidation.RegistryLookup draftRegistryLookup() {
        return new QuestDraftValidation.RegistryLookup(
            QuestScreen::clientContainsRegistryTarget,
            value -> registryContains(BuiltInRegistries.ITEM, value),
            value -> registryContains(BuiltInRegistries.ENTITY_TYPE, value)
        );
    }

    private static boolean registryContains(net.minecraft.core.Registry<?> registry, String value) {
        try {
            return registry.containsKey(Identifier.parse(value));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean clientContainsRegistryTarget(RegistryValidation.Target target, String value) {
        if (value == null || value.isBlank()) return true;
        boolean tag = value.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        if (id == null) return false;
        try {
            return switch (target) {
                case ITEM -> tag ? registryTag(BuiltInRegistries.ITEM, Registries.ITEM, id) : BuiltInRegistries.ITEM.containsKey(id);
                case BLOCK -> tag ? registryTag(BuiltInRegistries.BLOCK, Registries.BLOCK, id) : BuiltInRegistries.BLOCK.containsKey(id);
                case ENTITY -> tag ? registryTag(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id) : BuiltInRegistries.ENTITY_TYPE.containsKey(id);
                case BIOME -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME), Registries.BIOME, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME).containsKey(id));
                case STRUCTURE -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE).containsKey(id));
                case DIMENSION -> Minecraft.getInstance().level == null
                    || Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.DIMENSION).containsKey(id)
                    || Minecraft.getInstance().getConnection().levels().stream().anyMatch(key -> key.identifier().equals(id));
                default -> true;
            };
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static <T> boolean registryTag(Registry<T> registry, net.minecraft.resources.ResourceKey<? extends Registry<T>> key, Identifier id) {
        return registry.getTagOrEmpty(net.minecraft.tags.TagKey.create(key, id)).iterator().hasNext();
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String jsonStringList(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonArray()) {
            return value.getAsJsonArray().asList().stream()
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .collect(java.util.stream.Collectors.joining(", "));
        }
        return value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static void setOptionalString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value);
    }

    private static Component cycleValueLabel(String key, String value) {
        return Component.translatable("gui.theseus.editor.value." + value.toLowerCase(java.util.Locale.ROOT));
    }

    private static EditorTypeRegistry editorTypes() {
        return EditorTypeRegistry.registered();
    }

    private void drawClippedText(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, Component.literal(text), x, y, color, false);
    }

    private static void drawTexturedPath(
        GuiGraphicsExtractor graphics,
        PathPoint start,
        PathPoint end,
        boolean unlocked
    ) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) return;
        int pixelLength = (int) Math.ceil(length);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) start.x, (float) start.y);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        graphics.fill(0, -3, pixelLength, 3, 0xB0111318);
        graphics.fill(
            0,
            -2,
            pixelLength,
            2,
            unlocked ? 0x80636F66 : 0x80535A64
        );
        int tint = unlocked ? 0x8876A77B : 0x776F7782;
        for (int x = 0; x < pixelLength; x += 3) {
            int tileWidth = Math.min(3, pixelLength - x);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                DEPENDENCY_ARROW,
                x,
                -2,
                0.0f,
                0.0f,
                tileWidth,
                5,
                3,
                5,
                tint
            );
        }
        graphics.pose().popMatrix();
    }

    private record PathPoint(double x, double y) {}

    private ClientQuest questById(String id) {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    private static String chapterName(QuestDefinition definition) {
        return definition
            .display()
            .groups()
            .keySet()
            .stream()
            .sorted()
            .findFirst()
            .orElse("Main");
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, path);
    }

    private List<ClientQuest> visibleQuests() {
        return quests
            .stream()
            .filter(quest ->
                quest.definition.display().groups().containsKey(group)
            )
            .filter(this::isVisible)
            .toList();
    }

    private boolean isVisible(ClientQuest quest) {
        return switch (quest.definition.settings().hiddenUntil()) {
            case LOCKED -> true;
            case IN_PROGRESS -> quest.unlocked;
            case COMPLETED -> quest.complete;
            case DEPENDENCIES_VISIBLE -> quest.definition
                .dependencies()
                .isEmpty() ||
                quest.unlocked ||
                quest.definition
                    .dependencies()
                    .stream()
                    .anyMatch(this::isComplete);
            case NEVER -> true;
        };
    }

    private boolean isComplete(String id) {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(id))
            .findFirst()
            .map(ClientQuest::complete)
            .orElse(false);
    }

    private Set<String> groups() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(chapters);
        quests.forEach(quest ->
            result.addAll(quest.definition.display().groups().keySet())
        );
        return result;
    }

    private ClientQuest selected() {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(selectedQuestId))
            .findFirst()
            .orElse(null);
    }

    private void selectChapterIndex(int index) {
        List<String> ordered = new ArrayList<>(groups());
        if (index < 0 || index >= ordered.size()) return;
        chapterListFocused = true;
        focusedChapterIndex = index;
        chapterListState.ensureVisible(index);
        String candidate = ordered.get(index);
        if (candidate.equals(group)) {
            requestChapter(candidate);
            rebuildWidgets();
            return;
        }
        requestDiscard(() -> {
            group = candidate;
            requestChapter(group);
            linkSourceId = null;
            closeDraft();
            chapterListState.ensureVisible(index);
            rebuildWidgets();
        });
    }

    private int canvasRight() {
        return detailsOpen || authoring.open
            ? width - detailsWidth()
            : width;
    }

    private int graphCanvasRight() {
        return width;
    }

    private int canvasTop() {
        return headerLayout().canvasTop();
    }

    private int graphCanvasTop() {
        return graphHeaderLayout().canvasTop();
    }

    private QuestGraphLayout.CanvasBounds graphCanvasBounds() {
        int left = sidebarWidth();
        int top = graphCanvasTop();
        int right = graphCanvasRight();
        return new QuestGraphLayout.CanvasBounds(
            left,
            top,
            Math.max(0, right - left),
            Math.max(0, height - top)
        );
    }

    private boolean detailsDockContains(double mouseX, double mouseY) {
        return (detailsOpen || authoring.open)
            && mouseX >= width - detailsWidth()
            && mouseX < width
            && mouseY >= 0
            && mouseY < height;
    }

    private QuestGraphLayout.WorldBounds graphWorldBounds() {
        return surfaceLayout().worldBounds(48);
    }

    private QuestMinimapPanel.Settings minimapSettings() {
        return new QuestMinimapPanel.Settings(
            TheseusClientOptions.disableMinimap(),
            TheseusClientOptions.defaultMinimapMode(),
            TheseusClientOptions.minimapX(),
            TheseusClientOptions.minimapY()
        );
    }

    private int chapterListBottom() {
        return mode.isAuthoring()
            ? Math.max(CHAPTER_LIST_TOP, height - CHAPTER_ADD_SLOT_HEIGHT)
            : height;
    }

    private boolean chapterLabelRequiresTooltip(String chapter, int buttonWidth) {
        int iconColumn = chapterListHasIcons() ? CHAPTER_ICON_COLUMN_WIDTH : 0;
        int available = Math.max(1, buttonWidth - 6 - iconColumn);
        return font.width(chapter) > available;
    }

    private boolean chapterListHasIcons() {
        List<String> ordered = new ArrayList<>(groups());
        return chapterListState.visibleIndices().stream()
            .anyMatch(index -> index >= 0 && index < ordered.size()
                && chapterDisplays.get(ordered.get(index)) != null
                && chapterDisplays.get(ordered.get(index)).iconEnabled);
    }

    private int sidebarWidth() {
        if (!sidebarOpen) return COLLAPSED_SIDEBAR_WIDTH;
        return Math.max(96, Math.min(110, Math.round(width * 0.17f)));
    }

    private int detailsWidth() {
        if (authoring.open) {
            return Math.max(320, Math.min(360, Math.round(width * 0.38f)));
        }
        return Math.max(220, Math.min(240, Math.round(width * 0.38f)));
    }

    private void claimSelected() {
        ClientQuest selected = selected();
        if (selected == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("quest", selected.definition.id());
        JsonObject selections = new JsonObject();
        for (QuestDefinition.Reward reward : selected.definition
            .rewards()
            .values()) {
            if (selected.claimedRewards.contains(reward.id())) continue;
            if (
                reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            ) continue;
            selections.add(
                reward.id(),
                GSON.toJsonTree(
                    rewardSelections.getOrDefault(
                        selected.definition.id() + "|" + reward.id(),
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

    private static void submitTask(ClientQuest quest, TaskRef task) {
        if (quest != null && task != null) {
            ClientPacketDistributor.sendToServer(
                new QuestNetwork.ActionPayload(
                    "submit",
                    quest.definition.id() + "|" + task.path()
                )
            );
        }
    }

    private boolean canClaimRewards(ClientQuest quest) {
        if (quest.definition.rewards().isEmpty()) return false;
        for (QuestDefinition.Reward reward : quest.definition
            .rewards()
            .values()) {
            if (quest.claimedRewards.contains(reward.id())) continue;
            if (!isRewardTypeAvailable(reward)) return false;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                Set<String> selected = rewardSelections.getOrDefault(
                    quest.definition.id() + "|" + reward.id(),
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

    private String claimBlockedReason(ClientQuest quest) {
        if (quest.definition.rewards().isEmpty()) return "This quest has no rewards";
        if (
            quest.definition
                .rewards()
                .values()
                .stream()
                .anyMatch(reward -> !isRewardTypeAvailable(reward))
        ) {
            return "This quest contains a reward type that is not supported by this server";
        }
        return "Select the required quest reward before claiming";
    }

    private boolean isRewardTypeAvailable(QuestDefinition.Reward reward) {
        if (reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
            && !serverRewardTypes.contains(reward.type())) return false;
        return reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            || reward.rewards().values().stream().allMatch(this::isRewardTypeAvailable);
    }

    private static TaskRef findSubmittable(
        Map<String, QuestDefinition.Task> tasks,
        Map<String, Integer> progress,
        String prefix
    ) {
        for (QuestDefinition.Task task : tasks.values()) {
            String path = prefix.isEmpty()
                ? task.id()
                : prefix + "/" + task.id();
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
        if (
            task.kind() != QuestDefinition.TaskKind.ITEM &&
            task.kind() != QuestDefinition.TaskKind.XP
        ) return false;
        String key =
            task.kind() == QuestDefinition.TaskKind.XP
                ? "collectionType"
                : "collection";
        String collection = task.source().has(key)
            ? task
                  .source()
                  .get(key)
                  .getAsString()
                  .toLowerCase(java.util.Locale.ROOT)
            : "automatic";
        return collection.endsWith("manual");
    }

    private void copyQuestToClipboard(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        captureClipboard(quest, false);
        editorMessage = "Copied quest '" + quest.definition.id() + "'.";
        editorMessageSuccess = true;
        rebuildWidgets();
    }

    private void cutQuestToClipboard(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        captureClipboard(quest, true);
        editorMessage = "Cut quest '" + quest.definition.id() + "' (paste to complete the move).";
        editorMessageSuccess = true;
        rebuildWidgets();
    }

    private static void captureClipboard(ClientQuest quest, boolean move) {
        clipboardDraft = QuestDraft.fromClientSnapshot(quest.definition.id(), quest.raw());
        clipboardSourceId = quest.definition.id();
        clipboardMove = move;
    }

    private static boolean hasClipboardContent() {
        return clipboardDraft != null;
    }

    private static JsonObject clipboardTransferSnapshot() {
        return clipboardDraft == null ? null : clipboardDraft.transferSnapshot();
    }

    private static void clearClipboard() {
        clipboardDraft = null;
        clipboardSourceId = null;
        clipboardMove = false;
    }

    private void copyQuestId(ClientQuest quest) {
        if (quest == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(quest.definition.id());
        editorMessage = "Copied quest ID '" + quest.definition.id() + "'.";
        editorMessageSuccess = true;
        rebuildWidgets();
    }

    private void openQuestDetails(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        selectedQuestId = quest.definition.id();
        detailsPanel.resetScroll();
        authoring.open = false;
        detailsOpen = true;
        graphFocused = true;
        rebuildWidgets();
    }

    private void toggleQuestPinned(ClientQuest quest) {
        if (quest == null || !quest.unlocked) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("pin", quest.definition.id()));
    }

    private void openQuestEditorFromMenu(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        requestDiscard(() -> beginEditQuest(quest));
    }

    private void snapQuestFromMenu(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        requestDiscard(() -> {
            beginEditQuest(quest);
            authoringPanel.snapCurrentDraftPosition();
        });
    }

    private void deleteQuestFromMenu(ClientQuest quest) {
        if (quest == null || !ensureChapterDataLoaded()) return;
        requestDiscard(() -> {
            beginEditQuest(quest);
            modalHost.open(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION);
            rebuildWidgets();
        });
    }

    private void resetQuestProgressFromMenu(ClientQuest quest) {
        if (quest == null || !canEdit()) return;
        requestProgressReset(new QuestModalHost.ProgressResetTarget(
            "quest",
            quest.definition.id(),
            quest.definition.title(),
            "",
            quest.definition.title()
        ));
    }

    private boolean canOpenQuestFile(ClientQuest quest) {
        return canEdit()
            && mode.isAuthoring()
            && quest != null
            && Minecraft.getInstance().getSingleplayerServer() != null;
    }

    private void requestQuestFileOpen(ClientQuest quest) {
        if (!canOpenQuestFile(quest) || pendingQuestFileRequestId >= 0) return;
        pendingQuestFileRequestId = ++nextQuestFileRequestId;
        pendingQuestFileId = quest.definition.id();
        editorMessage = "Requesting quest file…";
        editorMessageSuccess = false;
        ClientPacketDistributor.sendToServer(new QuestNetwork.OpenQuestFilePayload(
            pendingQuestFileRequestId,
            pendingQuestFileId
        ));
        rebuildWidgets();
    }

    public void handleOpenQuestFileResult(QuestNetwork.OpenQuestFileResultPayload result) {
        if (result == null || result.requestId() != pendingQuestFileRequestId) return;
        String requestedQuestId = pendingQuestFileId;
        pendingQuestFileRequestId = -1;
        pendingQuestFileId = null;
        ClientQuest quest = questById(requestedQuestId);
        if (quest == null || !requestedQuestId.equals(selected() == null ? null : selected().definition.id())) {
            return;
        }
        if (!result.success()) {
            editorMessage = result.message();
            editorMessageSuccess = false;
            rebuildWidgets();
            return;
        }
        LocalQuestFileOpener.Result opened = questFileOpener.open(
            FMLPaths.CONFIGDIR.get().resolve(Theseus.MOD_ID).resolve("quests"),
            result.relativePath()
        );
        editorMessage = opened.success()
            ? "Opened quest file '" + result.relativePath() + "'."
            : opened.message();
        editorMessageSuccess = opened.success();
        rebuildWidgets();
    }

    private void copyProgressEntry(String kind, String entry) {
        Minecraft.getInstance().keyboardHandler.setClipboard(entry);
        editorMessage = "Copied " + kind + " '" + entry + "'.";
        editorMessageSuccess = true;
        rebuildWidgets();
    }

    private QuestDetailsPanel.Model detailPanelModel() {
        ClientQuest quest = selected();
        QuestDetailsPanel.QuestData selectedView = quest == null ? null : new QuestDetailsPanel.QuestData(
            quest.definition,
            quest.progress,
            quest.unlocked,
            quest.complete,
            quest.claimed,
            quest.claimedRewards
        );
        QuestSurfaceLayout.LockExplanation lockExplanation = null;
        if (quest != null) {
            Map<String, QuestSurfaceLayout.LockState> states = new HashMap<>();
            for (ClientQuest candidate : quests) states.put(candidate.definition.id(),
                new QuestSurfaceLayout.LockState(
                    candidate.definition.title(),
                    candidate.complete,
                    candidate.definition.display().groups().keySet()
                ));
            lockExplanation = QuestSurfaceLayout.explainLock(quest.definition, states, group);
        }
        return new QuestDetailsPanel.Model(
            selectedView,
            lockExplanation,
            detailTab,
            rewardSelections,
            serverRewardTypes
        );
    }

    private boolean openProgressCardContextMenu(int mouseX, int mouseY) {
        if (!canEdit() || !detailsOpen) return false;
        ClientQuest quest = selected();
        QuestDetailsPanel.ProgressCardTarget target = detailsPanel.progressCardAt(mouseX, mouseY, detailTab);
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
                quest.definition.id(),
                quest.definition.title(),
                target.id(),
                target.displayLabel()
            ))
        ));
        showContextMenu(mouseX, mouseY, entries);
        return true;
    }

    private void requestProgressReset(QuestModalHost.ProgressResetTarget target) {
        if (!canEdit() || target == null || mutations.isPending()) return;
        progressResetTarget = target;
        modalHost.open(QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION);
        rebuildWidgets();
    }

    private void openQuestContextMenu(ClientQuest quest, int mouseX, int mouseY) {
        graphFocused = true;
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        if (!mode.isAuthoring()) {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.open_details"), "Enter", true, false, () -> openQuestDetails(quest)));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.copy_quest_id"), "", true, false, () -> copyQuestId(quest)));
            entries.add(QuestContextMenu.Entry.item(
                editorString(quest != null && quest.pinned
                    ? "gui.theseus.editor.unpin_quest"
                    : "gui.theseus.editor.pin_quest"),
                "",
                quest != null && quest.unlocked,
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

    private void openEmptyGraphContextMenu(double worldX, double worldY, int mouseX, int mouseY) {
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        if (mode.isAuthoring()) {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.add_quest_here"), "", true, false, () ->
                requestDiscard(() -> beginCreateQuest(worldX, worldY))
            ));
            if (hasClipboardContent()) entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.paste_here"), "Ctrl+V", true, false, () -> {
                if (clipboardMove) sendClipboardPaste(false, null, worldX, worldY);
                else openPasteIdPrompt(worldX, worldY);
            }));
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.fit_to_content"), "Home", true, false, this::fitGraphToContent));
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
                    rebuildWidgets();
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
                    rebuildWidgets();
                }
            ));
        } else {
            entries.add(QuestContextMenu.Entry.item(editorString("gui.theseus.editor.fit_to_content"), "Home", true, false, this::fitGraphToContent));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    private void openDisplayMenu(int mouseX, int mouseY) {
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            Component.translatable("screen.theseus.display_menu.move_tracker").getString(),
            "",
            true,
            false,
            () -> Minecraft.getInstance().gui.setScreen(new TrackerPlacementScreen(this))
        ));
        if (canEdit()) entries.add(QuestContextMenu.Entry.item(
            Component.translatable("screen.theseus.display_menu.tutorial").getString(),
            "",
            true,
            false,
            this::openTutorial
        ));
        if (authoring.open) {
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(
                editorString("gui.theseus.editor.import_quests"),
                "",
                true,
                false,
                this::openNativeFilePicker
            ));
            entries.add(QuestContextMenu.Entry.item(
                editorString("gui.theseus.editor.fit_graph_to_content"),
                "Home",
                true,
                false,
                this::fitGraphToContent
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
                    rebuildWidgets();
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
                    rebuildWidgets();
                }
            ));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    private void openTutorial() {
        if (!canEdit()) return;
        TheseusClientOptions.setTutorialSeen(true);
        Minecraft.getInstance().gui.setScreen(new QuestTutorialScreen(this));
    }

    private void setEditorTool(EditorTool tool) {
        requestDiscard(() -> {
            mode = authoring;
            mode.setEditorTool(tool);
            closeDraft();
            linkSourceId = null;
            panning = false;
            rebuildWidgets();
        });
    }

    private void showContextMenu(int mouseX, int mouseY, List<QuestContextMenu.Entry> entries) {
        contextMenu = new QuestContextMenu(
            mouseX,
            mouseY,
            width,
            height,
            entries,
            () -> {
                contextMenu = null;
                graphFocused = true;
                setFocused(null);
            }
        );
    }

    private void drawContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (modalHost.blocksInput() || contextMenu == null || !contextMenu.isOpen()) return;
        contextMenu.moveMouse(mouseX, mouseY);
        QuestContextMenu.Bounds menu = contextMenu.bounds();
        int accent = ClientThemeLoader.active().genericControls().accent();
        graphics.fill(menu.x(), menu.y(), menu.maxX(), menu.maxY(), 0xF020242B);
        graphics.outline(menu.x(), menu.y(), menu.width(), menu.height(), accent);
        for (QuestContextMenu.Row row : contextMenu.rows()) {
            QuestContextMenu.Entry entry = row.entry();
            if (entry.isSeparator()) {
                graphics.fill(row.x() + 4, row.y() + 3, row.x() + row.width() - 4, row.y() + 4, 0xFF49515E);
                continue;
            }
            boolean active = entry.enabled() &&
                (row.entryIndex() == contextMenu.hoveredIndex() || row.entryIndex() == contextMenu.selectedIndex());
            if (active) graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF454C58);
            if (row.entryIndex() == contextMenu.selectedIndex()) {
                graphics.outline(row.x(), row.y(), row.width(), row.height(), ClientThemeLoader.active().genericControls().accent());
            }
            int labelColor = !entry.enabled() ? 0xFF9AA4B2 : entry.danger() ? 0xFFFF9999 : 0xFFFFFFFF;
            Component label = entry.enabled()
                ? Component.literal(entry.label())
                : Component.translatable("gui.theseus.editor.disabled_menu_label", entry.label());
            graphics.text(font, label, row.x() + 6, row.y() + 6, labelColor, false);
            if (!entry.shortcut().isBlank()) {
                graphics.text(font, Component.literal(entry.shortcut()), row.x() + row.width() - font.width(entry.shortcut()) - 6, row.y() + 6, 0xFF9AA4B2, false);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean modalOpen = modalHost.blocksInput();
        if (modalOpen && authoringPanel.handleChooserKey(event)) return true;
        if (!modalOpen && contextMenu != null && contextMenu.isOpen()) {
            contextMenu.keyPressed(event.key(), event.hasShiftDown());
            return true;
        }
        if (!modalOpen && chapterListFocused && !isTextEditing()) {
            int direction = switch (event.key()) {
                case InputConstants.KEY_UP -> -1;
                case InputConstants.KEY_DOWN -> 1;
                case InputConstants.KEY_PAGEUP -> -Math.max(1, chapterListState.visibleCapacity());
                case InputConstants.KEY_PAGEDOWN -> Math.max(1, chapterListState.visibleCapacity());
                default -> 0;
            };
            if (direction != 0) {
                int current = focusedChapterIndex >= 0
                    ? focusedChapterIndex
                    : new ArrayList<>(groups()).indexOf(group);
                selectChapterIndex(Math.clamp(current + direction, 0, Math.max(0, chapterListState.chapterCount() - 1)));
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN) {
                selectChapterIndex(focusedChapterIndex);
                return true;
            }
        }

        switch (modalHost.handles(event.key())) {
            case CONSUMED -> {
                return true;
            }
            case CANCEL_DISMISSAL -> {
                modalHost.cancelDismissal();
                rebuildWidgets();
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

        if (modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
        if (modalHost.is(QuestModalHost.Modal.DESCRIPTION_EDITOR)) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
                applyDescriptionEditor();
                if (validCreateQuestDraft()) confirmCreateQuest();
                return true;
            }
            return super.keyPressed(event);
        }
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            if (event.key() == InputConstants.KEY_RETURN) {
                closeDiagnosticsModal();
                rebuildWidgets();
            }
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && importController.canSubmit()) {
                sendImport();
                return true;
            }
            return super.keyPressed(event);
        }
        if (modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT) && event.key() == InputConstants.KEY_RETURN) {
            confirmPasteIdPrompt();
            return true;
        }
        if (!modalOpen && !isTextEditing() && event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && importController.canSubmit()) {
            sendImport();
            return true;
        }
        if (!modalOpen && !isTextEditing() && event.hasControlDown() && !event.hasAltDown()) {
            if (event.key() == InputConstants.KEY_C && mode.isAuthoring() && selected() != null) {
                copyQuestToClipboard(selected());
                return true;
            }
            if (event.key() == InputConstants.KEY_X && mode.isAuthoring() && selected() != null) {
                cutQuestToClipboard(selected());
                return true;
            }
            if (event.key() == InputConstants.KEY_V && mode.isAuthoring() && hasClipboardContent()) {
                if (event.hasShiftDown() || clipboardMove) sendClipboardPaste(event.hasShiftDown(), null);
                else openPasteIdPrompt();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_RETURN && !isTextEditing()) {
            if (modalOpen) {
                if (modalHost.is(QuestModalHost.Modal.DISCARD_CONFIRMATION)) {
                    modalHost.confirmDismissal();
                    return true;
                }
                if (modalHost.is(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION)) {
                    confirmDeleteQuest();
                    return true;
                }
                if (modalHost.is(QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION)) {
                    confirmProgressReset();
                    return true;
                }
                if (modalHost.is(QuestModalHost.Modal.TASK_DELETE_CONFIRMATION)) {
                    confirmDeleteTask();
                    return true;
                }
                if (modalHost.is(QuestModalHost.Modal.CHAPTER_EDITOR)) {
                    saveChapter();
                    return true;
                }
                if (modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT)) {
                    confirmPasteIdPrompt();
                    return true;
                }
            }
            if (!modalOpen && authoring.open && validCreateQuestDraft()) {
                confirmCreateQuest();
                return true;
            }
        }
        if (!modalOpen && mode.isAuthoring()
            && !isTextEditing() && !event.hasControlDown() && !event.hasAltDown()) {
            EditorTool shortcut = switch (event.key()) {
                case InputConstants.KEY_S -> EditorTool.SELECT;
                case InputConstants.KEY_H -> EditorTool.HAND;
                case InputConstants.KEY_A -> EditorTool.ADD;
                case InputConstants.KEY_L -> EditorTool.LINK;
                default -> null;
            };
            if (shortcut != null) {
                mode.setEditorTool(shortcut);
                rebuildWidgets();
                return true;
            }
        }
        if (!modalOpen && !isTextEditing()
            && !event.hasControlDown()
            && !event.hasAltDown()
            && event.key() == InputConstants.KEY_HOME) {
            graphFocused = true;
            setFocused(null);
            fitGraphToContent();
            return true;
        }
        if (!modalOpen && event.hasControlDown() && event.key() == InputConstants.KEY_S && authoring.open) {
            confirmCreateQuest();
            return true;
        }
        if (!modalOpen && mode.isAuthoring()
            && graphFocused
            && authoring.editingExisting
            && authoring.open
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
                nudgeCurrentDraftPosition(deltaX, deltaY);
                return true;
            }
        }
        if (!modalOpen && graphFocused && !authoring.open && !isTextEditing()
            && !event.hasControlDown() && !event.hasAltDown()) {
            if (moveGraphSelection(event.key())) return true;
            if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                && selected() != null) {
                if (mode.isAuthoring()) openQuestEditorFromMenu(selected());
                else openQuestDetails(selected());
                return true;
            }
        }
        if (!event.isEscape()) return super.keyPressed(event);
        if (authoring.open) {
            requestDiscard(() -> {
                closeDraft();
                rebuildWidgets();
            });
            return true;
        }
        onClose();
        return true;
    }

    private boolean moveGraphSelection(int keyCode) {
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
        List<ClientQuest> visible = visibleQuests();
        if (visible.isEmpty()) return true;
        ClientQuest current = selected();
        if (current == null || !visible.contains(current)) {
            selectedQuestId = visible.getFirst().definition.id();
            rebuildWidgets();
            return true;
        }
        QuestDefinition.GroupDisplay currentPosition = current.definition.position(group);
        ClientQuest best = null;
        double bestScore = Double.MAX_VALUE;
        for (ClientQuest candidate : visible) {
            if (candidate == current) continue;
            QuestDefinition.GroupDisplay position = candidate.definition.position(group);
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
            selectedQuestId = best.definition.id();
            rebuildWidgets();
        }
        return true;
    }

    private void closeModalOnEscape() {
        switch (modalHost.active()) {
            case FILE_IMPORT -> cancelImport();
            case PICKER -> {
                closePicker();
                rebuildWidgets();
            }
            case DESCRIPTION_EDITOR -> closeDescriptionEditor();
            case PROGRESS_RESET_CONFIRMATION -> {
                progressResetTarget = null;
                modalHost.close();
                rebuildWidgets();
            }
            case TASK_DELETE_CONFIRMATION -> {
                authoring.taskDeleteConfirmation = -1;
                modalHost.close();
                rebuildWidgets();
            }
            case PASTE_ID_PROMPT -> {
                pasteIdField = null;
                modalHost.close();
                rebuildWidgets();
            }
            default -> {
                modalHost.close();
                rebuildWidgets();
            }
        }
    }

    private void requestModalEscapeDismissal() {
        switch (modalHost.active()) {
            case TASK_EDITOR, NESTED_TASKS -> requestModalDiscard(authoringPanel::closeTaskEditor);
            case NESTED_REWARD_EDITOR -> requestModalDiscard(() -> authoringPanel.closeRewardEditor(true));
            case REWARD_EDITOR, NESTED_REWARDS -> requestModalDiscard(() -> authoringPanel.closeRewardEditor(false));
            case CHAPTER_EDITOR -> requestModalDiscard(() -> {
                chapterEditorBaseline = null;
                modalHost.close();
                rebuildWidgets();
            });
            default -> { }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
        return super.charTyped(event);
    }

    private void focusPickerSearch() {
        // The picker can rebuild the widget tree inside its opener's click callback.
        if (pickerSearch != null && getFocused() != pickerSearch) {
            setFocused(pickerSearch);
        }
    }

    private boolean isTextEditing() {
        return getFocused() instanceof EditBox
            || getFocused() instanceof MultiLineEditBox
            || getFocused() instanceof MarkdownEditBox;
    }

    private void nudgeCurrentDraftPosition(int deltaX, int deltaY) {
        authoring.x += deltaX;
        authoring.y += deltaY;
        authoring.xText = Integer.toString(authoring.x);
        authoring.yText = Integer.toString(authoring.y);
        authoring.xInvalid = false;
        authoring.yInvalid = false;
        updateDraftGroupPosition();
        rebuildWidgets();
    }

    private void sendClipboardPaste(boolean chapterOnly, String requestedId) {
        sendClipboardPaste(chapterOnly, requestedId, null, null);
    }

    private void sendClipboardPaste(
        boolean chapterOnly,
        String requestedId,
        Double worldX,
        Double worldY
    ) {
        String sourceId = clipboardSourceId;
        JsonObject request = new JsonObject();
        request.addProperty("source_id", sourceId);
        request.addProperty("chapter", group);
        request.addProperty("chapter_only", chapterOnly);
        ClientQuest source = quests.stream().filter(quest -> quest.definition.id().equals(sourceId)).findFirst().orElse(null);
        if (source == null && !chapterOnly) {
            editorMessage = "The copied quest is no longer available.";
            editorMessageSuccess = false;
            return;
        }
        if (!chapterOnly) {
            String id = clipboardMove ? sourceId : requestedId;
            if (id == null || !id.matches("[a-z0-9_.-]+") || questById(id) != null) {
                editorMessage = "Choose a new, unused lowercase quest ID.";
                editorMessageSuccess = false;
                return;
            }
            request.addProperty("id", id);
            JsonObject quest = clipboardTransferSnapshot();
            request.add("quest", quest);
            request.addProperty("move", clipboardMove);
            QuestDefinition.GroupDisplay position = source == null
                ? new QuestDefinition.GroupDisplay(0, 0)
                : source.definition.position(group);
            request.addProperty("x", position.x());
            request.addProperty("y", position.y());
            if (worldX != null && worldY != null) {
                request.addProperty("x", Math.round(worldX));
                request.addProperty("y", Math.round(worldY));
            }
        } else if (source != null) {
            request.addProperty("x", source.definition.position(group).x());
            request.addProperty("y", source.definition.position(group).y());
            if (worldX != null && worldY != null) {
                request.addProperty("x", Math.round(worldX));
                request.addProperty("y", Math.round(worldY));
            }
        }
        clipboardMutationPending = true;
        sendEditorMutation(new QuestMutation.PasteQuest(request));
    }

    private void openPasteIdPrompt() {
        pendingPastePosition = false;
        pendingPasteWorldX = 0;
        pendingPasteWorldY = 0;
        pasteIdField = null;
        modalHost.open(QuestModalHost.Modal.PASTE_ID_PROMPT);
        rebuildWidgets();
    }

    private void openPasteIdPrompt(double worldX, double worldY) {
        pendingPastePosition = true;
        pendingPasteWorldX = worldX;
        pendingPasteWorldY = worldY;
        pasteIdField = null;
        modalHost.open(QuestModalHost.Modal.PASTE_ID_PROMPT);
        rebuildWidgets();
    }

    private void addPasteIdPromptWidgets() {
        int left = (width - 280) / 2;
        int top = (height - 130) / 2;
        pasteIdField = new EditBox(font, left + 14, top + 52, 252, 18, Component.translatable("gui.theseus.editor.new_quest_id"));
        pasteIdField.setValue(clipboardSourceId + "_copy");
        addRenderableWidget(pasteIdField);
        setInitialFocus(pasteIdField);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(() -> {
                pasteIdField = null;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 166, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.paste")));
            widget.withCallback(this::confirmPasteIdPrompt);
        }));
    }

    private void confirmPasteIdPrompt() {
        if (pasteIdField == null) return;
        String id = pasteIdField.getValue().trim();
        pasteIdField = null;
        if (modalHost.is(QuestModalHost.Modal.PASTE_ID_PROMPT)) modalHost.close();
        if (pendingPastePosition) {
            sendClipboardPaste(false, id, pendingPasteWorldX, pendingPasteWorldY);
        } else {
            sendClipboardPaste(false, id);
        }
        pendingPastePosition = false;
        rebuildWidgets();
    }

    /** Parsing is independent per file and failed files remain removable. */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        importController.addFiles(paths);
        updateImportMessage();
        if (!paths.isEmpty()) modalHost.open(QuestModalHost.Modal.FILE_IMPORT);
        rebuildWidgets();
    }

    private void updateImportMessage() {
        boolean canSubmit = importController.canSubmit();
        String summary = importController.summary().replace('\n', ' ').replace('\r', ' ').strip();
        String status = canSubmit
            ? "Import ready (Ctrl-Enter to submit)."
            : "Import contains invalid files; remove or correct them.";
        editorMessage = summary.isEmpty() ? status : summary + " " + status;
        editorMessageSuccess = canSubmit;
    }

    private void openNativeFilePicker() {
        Theseus.LOGGER.info(
            "Quest import button clicked on thread '{}' (headless property='{}')",
            Thread.currentThread().getName(),
            System.getProperty("java.awt.headless")
        );
        NativeFilePicker.open(
            paths -> {
                Theseus.LOGGER.info(
                    "Quest import picker returned {} file(s) on thread '{}'; scheduling processing on Minecraft thread",
                    paths.size(),
                    Thread.currentThread().getName()
                );
                Minecraft.getInstance().execute(() -> {
                    Theseus.LOGGER.info("Quest import processing started on thread '{}'", Thread.currentThread().getName());
                    try {
                        onFilesDrop(paths);
                        Theseus.LOGGER.info("Quest import processing completed for {} file(s)", paths.size());
                    } catch (RuntimeException exception) {
                        Theseus.LOGGER.error("Quest import processing failed for {} file(s)", paths.size(), exception);
                        editorMessage = "Import failed; check the game log for details.";
                        editorMessageSuccess = false;
                        rebuildWidgets();
                    }
                });
            },
            error -> {
                Theseus.LOGGER.error("Quest import picker reported an error on thread '{}': {}", Thread.currentThread().getName(), error);
                Minecraft.getInstance().execute(() -> {
                    Theseus.LOGGER.info("Quest import error is being displayed on thread '{}'", Thread.currentThread().getName());
                    editorMessage = error;
                    editorMessageSuccess = false;
                    rebuildWidgets();
                });
            }
        );
    }

    public void removeImportFile(String key) {
        importController.remove(key);
        updateImportMessage();
        rebuildWidgets();
    }

    public boolean changeImportId(String key, String id) {
        boolean changed = importController.changeId(key, id);
        if (changed) {
            updateImportMessage();
            rebuildWidgets();
        }
        return changed;
    }

    private void sendImport() {
        editorMessage = "Importing…";
        editorMessageSuccess = false;
        JsonObject request = importController.request();
        request.addProperty("chapter", group);
        sendEditorMutation(new QuestMutation.ImportQuests(request));
    }

    private void sendEditorMutation(QuestMutation mutation) {
        QuestMutationCoordinator.Pending pending;
        try {
            pending = mutations.begin(mutation);
        } catch (IllegalStateException exception) {
            editorMessage = "Another editor operation is still pending.";
            editorMessageSuccess = false;
            return;
        }
        diagnostics = List.of();
        modalHost.closeAll();
        ClientPacketDistributor.sendToServer(new QuestNetwork.EditorMutationPayload(pending.requestId(), mutation));
    }

    @Override
    public void onClose() {
        requestDiscard(() -> QuestScreen.super.onClose());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        switch (modalHost.active()) {
            case DIAGNOSTICS, FILE_IMPORT -> {
                super.mouseClicked(event, doubleClick);
                return true;
            }
            case PICKER -> {
                if (super.mouseClicked(event, doubleClick)) return true;
                return pickerClicked(event);
            }
            case NESTED_REWARD_CHOOSER -> {
                return authoringPanel.rewardChooserClicked(event, true);
            }
            case REWARD_CHOOSER -> {
                return authoringPanel.rewardChooserClicked(event, false);
            }
            case NESTED_TASK_CHOOSER -> {
                return authoringPanel.taskChooserClicked(event, true);
            }
            case TASK_CHOOSER -> {
                return authoringPanel.taskChooserClicked(event);
            }
            default -> { }
        }
        if (contextMenu != null && contextMenu.isOpen()) {
            contextMenu.mouseClicked(event.x(), event.y(), event.input());
            return true;
        }
        if (!modalHost.blocksInput() && detailsOpen && recipeViewerClicked(event)) {
            return true;
        }
        if (!modalHost.blocksInput() && event.input() == 1
            && openProgressCardContextMenu((int) Math.round(event.x()), (int) Math.round(event.y()))) {
            return true;
        }
        if (!modalHost.blocksInput() && minimapClicked(event)) return true;
        if (!modalHost.blocksInput()
            && event.input() == 1
            && !detailsDockContains(event.x(), event.y())
            && graphCanvasBounds().contains(event.x(), event.y())) {
            graphFocused = true;
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                graphCanvasBounds(),
                graphViewport.state(),
                event.x(),
                event.y()
            );
            int mouseX = (int) Math.round(event.x());
            int mouseY = (int) Math.round(event.y());
            ClientQuest quest = surfaceLayout().pick(event.x(), event.y())
                .map(hit -> questById(hit.questId()))
                .orElse(null);
            if (quest == null) openEmptyGraphContextMenu(world.x(), world.y(), mouseX, mouseY);
            else {
                selectedQuestId = quest.definition.id();
                openQuestContextMenu(quest, mouseX, mouseY);
            }
            return true;
        }
        if (!modalHost.blocksInput()
            && event.input() == 0
            && sidebarOpen
            && event.x() < sidebarWidth()) {
            int row = chapterListState.rowAt(event.y());
            int index = chapterListState.indexAtRow(row);
            if (index >= 0) {
                chapterListFocused = true;
                focusedChapterIndex = index;
            }
        }
        if (super.mouseClicked(event, doubleClick)) {
            if (modalHost.is(QuestModalHost.Modal.PICKER)) focusPickerSearch();
            return true;
        }
        if (modalHost.blocksInput()) return true;
        QuestSurfaceLayout.Layout surface = surfaceLayout();
        if (event.input() == 0 && detailsOpen) {
            String questId = detailsPanel.lockQuestAt(event.x(), event.y());
            if (questId != null) {
                selectedQuestId = questId;
                detailsPanel.resetScroll();
                rebuildWidgets();
                return true;
            }
        }
        if (event.input() == 0 && detailsOpen && detailTab == DetailTab.OVERVIEW) {
            QuestDescriptionRenderer.Interaction interaction =
                detailsPanel.descriptionInteractionAt(event.x(), event.y());
            if (interaction != null) {
                if (interaction.clickStyle() != null && interaction.clickStyle().getClickEvent() != null) {
                    defaultHandleClickEvent(interaction.clickStyle().getClickEvent(), minecraft, this);
                }
                return true;
            }
        }
        if (event.input() == 0 && detailsOpen && detailTab == DetailTab.REWARDS
            && event.x() >= width - detailsWidth()) {
            QuestDetailsPanel.RewardChoiceTarget choice = detailsPanel.rewardChoiceAt(event.x(), event.y());
            if (choice != null) {
                Set<String> selectedChoices = rewardSelections.computeIfAbsent(
                    choice.selectionKey(), ignored -> new LinkedHashSet<>()
                );
                if (selectedChoices.remove(choice.choiceId())) {
                    rebuildWidgets();
                    return true;
                }
                if (choice.maximumSelections() == 1) {
                    selectedChoices.clear();
                    selectedChoices.add(choice.choiceId());
                    rebuildWidgets();
                } else if (selectedChoices.size() < choice.maximumSelections()) {
                    selectedChoices.add(choice.choiceId());
                    rebuildWidgets();
                }
                return true;
            }
        }
        if (
            event.input() == 0 &&
            event.x() > sidebarWidth() &&
            event.x() < canvasRight() &&
            event.y() >= graphCanvasTop()
        ) {
            graphFocused = true;
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                graphCanvasBounds(),
                graphViewport.state(),
                event.x(),
                event.y()
            );
            double treeX = world.x();
            double treeY = world.y();
            if (mode.isAuthoring() && mode.editorTool() == EditorTool.HAND) {
                panning = true;
                return true;
            }
            if (mode.isAuthoring() && mode.editorTool() == EditorTool.ADD) {
                boolean occupied = surface.pick(event.x(), event.y()).isPresent();
                if (!occupied) {
                    requestDiscard(() -> beginCreateQuest(treeX, treeY));
                    return true;
                }
                return true;
            }
            if (mode.isAuthoring() && mode.editorTool() == EditorTool.SELECT && authoring.editingExisting && authoring.open) {
                QuestGraphLayout.NodeBounds draftBounds = authoringNodeLayout().bounds();
                if (draftBounds.contains(treeX, treeY)) {
                    draggingQuestId = authoring.originalId;
                    questMoved = false;
                    return true;
                }
            }
            ClientQuest quest = surface.pick(event.x(), event.y())
                .map(hit -> questById(hit.questId()))
                .orElse(null);
            if (quest != null) {
                if (mode.isAuthoring() && mode.editorTool() == EditorTool.LINK) {
                    linkQuest(quest.definition.id(), event.hasShiftDown());
                    return true;
                }
                if (mode.isAuthoring() && mode.editorTool() == EditorTool.SELECT) {
                    requestDiscard(() -> {
                        beginEditQuest(quest);
                        draggingQuestId = quest.definition.id();
                        questMoved = false;
                    });
                    return true;
                }
                selectedQuestId = quest.definition.id();
                detailsPanel.resetScroll();
                authoring.open = false;
                detailsOpen = true;
                rebuildWidgets();
                return true;
            }
            if (mode.isAuthoring() && mode.editorTool() == EditorTool.SELECT) {
                return true;
            }
            if (mode.isAuthoring() && mode.editorTool() == EditorTool.LINK) {
                linkSourceId = null;
                return true;
            }
            if (!mode.isAuthoring() && detailsOpen) {
                detailsOpen = false;
                selectedQuestId = null;
                rebuildWidgets();
            }
            panning = !mode.isAuthoring() || mode.editorTool() == EditorTool.HAND;
            return true;
        }
        return false;
    }

    private boolean recipeViewerClicked(MouseButtonEvent event) {
        if (event.input() != 0 && event.input() != 1) return false;
        ItemStack stack = detailsPanel.recipeViewerItemAt(event.x(), event.y()).orElse(null);
        if (stack == null) return false;
        return event.input() == 0
            ? RecipeViewer.showRecipes(stack)
            : RecipeViewer.showUses(stack);
    }

    private void linkQuest(String questId, boolean remove) {
        if (linkSourceId == null) {
            linkSourceId = questId;
            return;
        }
        if (linkSourceId.equals(questId)) {
            linkSourceId = null;
            return;
        }
        JsonObject change = new JsonObject();
        change.addProperty("prerequisite", linkSourceId);
        change.addProperty("dependent", questId);
        change.addProperty("remove", remove);
        sendEditorMutation(new QuestMutation.SetDependency(change));
    }

    private boolean pickerClicked(MouseButtonEvent event) {
        if (event.input() != 0) return true;
        int left = pickerLeft();
        int top = pickerTop();
        if (event.x() < left || event.x() >= left + 200 || event.y() < top || event.y() >= top + 176) {
            closePicker();
            rebuildWidgets();
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.PICKER) && picker == Picker.ICON) {
            List<Item> items = filteredPickerItems();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) {
                return true;
            }
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            if (column >= 0 && column < 8 && row >= 0 && row < 5) {
                int index = pickerScroll + row * 8 + column;
                if (index < items.size()) {
                    String id = BuiltInRegistries.ITEM.getKey(items.get(index)).toString();
                    switch (pickerTarget) {
                        case QUEST_ICON -> {
                            authoring.icon = id;
                            authoring.iconTouched = true;
                        }
                        case TASK_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("type", QuestIconDefinition.ITEM_TYPE);
                            icon.addProperty("item", id);
                            authoring.editingTask.source.add("icon", icon);
                        }
                        case TASK_ITEM -> authoring.editingTask.source.addProperty("item", id);
                        case TASK_BLOCK -> authoring.editingTask.source.addProperty("block", id);
                        case TASK_ENTITY -> { }
                        case REWARD_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("type", QuestIconDefinition.ITEM_TYPE);
                            icon.addProperty("item", id);
                            activeRewardDraft().source.add("icon", icon);
                        }
                        case REWARD_ITEM -> setRewardItem(activeRewardDraft().source, id, rewardItemCount(activeRewardDraft().source));
                        case CHAPTER_ICON -> chapterEditorIcon = id;
                    }
                    closePicker();
                    rebuildWidgets();
                }
            }
        } else if (picker == Picker.ENTITY) {
            List<EntityType<?>> entities = filteredPickerEntities();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) return true;
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            int index = pickerScroll + row * 8 + column;
            if (column >= 0 && column < 8 && row >= 0 && row < 5 && index < entities.size()) {
                authoring.editingTask.source.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entities.get(index)).toString());
                closePicker();
                rebuildWidgets();
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
            if (column >= 0 && column < 4 && row >= 0 && index < QUEST_BACKGROUNDS.size()) {
                authoring.background = QUEST_BACKGROUNDS.get(index).toString();
                closePicker();
                rebuildWidgets();
            }
        }
        return true;
    }

    private boolean minimapClicked(MouseButtonEvent event) {
        return applyMinimapResult(minimapPanel.mouseClicked(
            graphCanvasBounds(),
            minimapSettings(),
            event.x(),
            event.y(),
            event.input(),
            detailsDockContains(event.x(), event.y())
        ));
    }

    private boolean applyMinimapResult(QuestMinimapPanel.Result result) {
        if (result.action() instanceof QuestMinimapPanel.Navigate navigate) {
            QuestGraphLayout.Point world = QuestMinimap.mapToWorld(
                QuestMinimap.mapping(surfaceLayout().worldBounds(16), navigate.mapBounds()),
                navigate.mapX(),
                navigate.mapY()
            );
            graphViewport.centerOn(world.x(), world.y());
            graphViewport.saveChapterViewport(group);
        } else if (result.action() instanceof QuestMinimapPanel.OpenContextMenu menu) {
            openMinimapContextMenu(menu.screenX(), menu.screenY());
        } else if (result.action() instanceof QuestMinimapPanel.SavePosition position) {
            TheseusClientOptions.setMinimapPosition(position.x(), position.y());
            rebuildWidgets();
        }
        return result.handled();
    }

    private void openMinimapContextMenu(int mouseX, int mouseY) {
        boolean docked = TheseusClientOptions.defaultMinimapMode() == TheseusClientOptions.MinimapMode.DOCKED;
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            editorString(docked
                ? "gui.theseus.editor.undock_minimap"
                : "gui.theseus.editor.dock_minimap"),
            "",
            true,
            false,
            this::toggleMinimapDocking
        ));
        entries.add(QuestContextMenu.Entry.item(
            editorString("gui.theseus.editor.hide_minimap"),
            "",
            true,
            false,
            () -> {
                minimapPanel.setHidden(true);
                minimapPanel.clearTransientState();
                rebuildWidgets();
            }
        ));
        showContextMenu(mouseX, mouseY, entries);
    }

    private boolean minimapDragged(double mouseX, double mouseY) {
        return applyMinimapResult(minimapPanel.mouseDragged(
            graphCanvasBounds(),
            minimapSettings(),
            mouseX,
            mouseY
        ));
    }

    private boolean minimapReleased() {
        return applyMinimapResult(minimapPanel.mouseReleased(minimapSettings()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (minimapReleased()) return true;
        if (questMoved && TheseusClientOptions.snapToGrid()) {
            authoringPanel.snapCurrentDraftPosition();
        }
        panning = false;
        draggingQuestId = null;
        questMoved = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(
        MouseButtonEvent event,
        double dragX,
        double dragY
    ) {
        if (modalHost.blocksInput() && !modalHost.ownsWidgetTree()) return true;
        if (minimapDragged(event.x(), event.y())) return true;
        if (panning) {
            graphViewport.panByScreenDelta(dragX, dragY);
            rebuildWidgets();
            return true;
        }
        if (draggingQuestId != null && authoring.editingExisting && mode.editorTool() == EditorTool.SELECT) {
            int deltaX = (int) Math.round(dragX / graphViewport.state().zoom());
            int deltaY = (int) Math.round(dragY / graphViewport.state().zoom());
            if (deltaX == 0 && deltaY == 0) return true;
            questMoved = true;
            authoring.x += deltaX;
            authoring.y += deltaY;
            authoring.xText = Integer.toString(authoring.x);
            authoring.yText = Integer.toString(authoring.y);
            authoring.xInvalid = false;
            authoring.yInvalid = false;
            updateDraftGroupPosition();
            rebuildWidgets();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        if (modalHost.is(QuestModalHost.Modal.DESCRIPTION_EDITOR)) {
            int modalWidth = Math.min(760, width - 24);
            int left = (width - modalWidth) / 2;
            int paneWidth = (modalWidth - 40) / 2;
            int previewX = left + 20 + paneWidth;
            if (mouseX >= previewX) {
                descriptionPreviewScroll = Math.max(0, Math.min(
                    descriptionPreviewMaxScroll,
                    descriptionPreviewScroll - (int)Math.round(scrollY * 18)
                ));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            int max = Math.max(0, diagnosticLines(416).size() - 13);
            diagnosticsScroll = Math.max(0, Math.min(max, diagnosticsScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            int max = Math.max(0, importController.entries().size() - importVisibleRows());
            importScroll = Math.max(0, Math.min(max, importScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.PICKER) && picker == Picker.ICON) {
            int itemCount = filteredPickerItems().size();
            int maxRow = Math.max(0, (itemCount + 7) / 8 - 5);
            int row = pickerScroll / 8 - (int) Math.signum(scrollY);
            pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            pickerScrollByTarget.put(pickerTarget, pickerScroll);
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.PICKER) && picker == Picker.ENTITY) {
            int entityCount = filteredPickerEntities().size();
            int maxRow = Math.max(0, (entityCount + 7) / 8 - 5);
            int row = pickerScroll / 8 - (int) Math.signum(scrollY);
            pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            pickerScrollByTarget.put(pickerTarget, pickerScroll);
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.PICKER)) return true;
        if (modalHost.is(QuestModalHost.Modal.NESTED_REWARDS)
            && !modalHost.isNestedRewardChooserOpen() && authoring.editingNestedReward == null) {
            int max = Math.max(0, nestedRewards(authoring.editingReward).size() - 4);
            authoring.nestedRewardScroll = Math.max(0, Math.min(max, authoring.nestedRewardScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.NESTED_TASK_CHOOSER)) {
            authoringPanel.scrollTaskChooser(scrollY);
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.NESTED_TASKS)) {
            int max = Math.max(0, nestedTasks(authoring.editingTask).size() - 4);
            authoring.nestedTaskScroll = Math.max(
                0,
                Math.min(max, authoring.nestedTaskScroll - (int) Math.signum(scrollY))
            );
            rebuildWidgets();
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.TASK_CHOOSER)) {
            authoringPanel.scrollTaskChooser(scrollY);
            return true;
        }
        if (modalHost.blocksInput()) return true;
        if (QuestMinimap.contains(
            minimapPanel.bounds(graphCanvasBounds(), minimapSettings()),
            mouseX,
            mouseY
        )) return true;
        if (sidebarOpen
            && mouseX >= 0
            && mouseX < sidebarWidth()
            && chapterListState.rowAt(mouseY) >= 0
            && chapterListState.hasOverflow()) {
            chapterListFocused = true;
            int delta = -(int) Math.signum(scrollY);
            if (delta != 0) {
                chapterListState.scrollByRows(delta);
                rebuildWidgets();
            }
            return true;
        }
        if (authoring.open && detailsDockContains(mouseX, mouseY)) {
            if (authoringPanel.createQuestTab() == DetailTab.TASKS) {
                authoringPanel.scrollDraftTaskList(scrollY);
                rebuildWidgets();
            } else if (authoringPanel.createQuestTab() == DetailTab.REWARDS) {
                authoringPanel.scrollDraftRewardList(scrollY);
                rebuildWidgets();
            } else if (authoringPanel.createQuestTab() == DetailTab.OVERVIEW) {
                super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                authoringPanel.captureOverviewScroll();
            }
            return true;
        }
        if (detailsOpen && mouseX >= width - detailsWidth()) {
            detailsPanel.scroll(scrollY);
            return true;
        }
        QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
        if (canvas.contains(mouseX, mouseY)) {
            graphViewport.zoomAroundScreenPoint(canvas, mouseX, mouseY, scrollY * 0.1);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static Component status(ClientQuest quest) {
        if (!quest.unlocked) return Component.translatable("quest.theseus.locked");
        if (quest.claimed) return Component.translatable("quest.theseus.completed_claimed");
        if (quest.complete) return Component.translatable("quest.theseus.completed");
        return Component.translatable("quest.theseus.in_progress");
    }

    private static int nodeStateColor(ClientQuest quest) {
        if (!quest.unlocked) return 0xFF737B87;
        if (quest.claimed) return 0xFF55D86A;
        if (quest.complete) return 0xFFFFD966;
        return 0xFF4C9AFF;
    }

    private final class AuthoringPanelHost implements QuestAuthoringPanel.Host {
        @Override public void addWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
            QuestScreen.this.addRenderableWidget(widget);
        }
        @Override public int detailsWidth() { return QuestScreen.this.detailsWidth(); }
        @Override public QuestDraftValidation.RegistryLookup registryLookup() {
            return QuestScreen.this.draftRegistryLookup();
        }
        @Override public String draftValidationError() { return QuestScreen.this.draftValidationError(); }
        @Override public boolean validCreateQuestDraft() { return QuestScreen.this.validCreateQuestDraft(); }
        @Override public boolean mutationPending() { return mutations.isPending(); }
        @Override public void dispatch(QuestAuthoringPanel.Action action) {
            if (action instanceof QuestAuthoringPanel.RebuildWidgets) QuestScreen.this.rebuildWidgets();
            else if (action instanceof QuestAuthoringPanel.OpenPicker open) {
                QuestScreen.this.openPicker(open.picker(), open.target());
            } else if (action instanceof QuestAuthoringPanel.OpenRawInspector open) {
                QuestScreen.this.openRawInspector(open.title(), open.source());
            } else if (action instanceof QuestAuthoringPanel.RequestModalDiscard request) {
                QuestScreen.this.requestModalDiscard(request.action());
            } else if (action instanceof QuestAuthoringPanel.RequestDiscard request) {
                QuestScreen.this.requestDiscard(request.action());
            } else if (action instanceof QuestAuthoringPanel.ClosePicker) QuestScreen.this.closePicker();
            else if (action instanceof QuestAuthoringPanel.ShowMessage message) {
                editorMessage = message.message();
                editorMessageSuccess = false;
            } else if (action instanceof QuestAuthoringPanel.OpenDescriptionEditor) {
                QuestScreen.this.openDescriptionEditor();
            } else if (action instanceof QuestAuthoringPanel.CloseDraft) QuestScreen.this.closeDraft();
            else if (action instanceof QuestAuthoringPanel.ClearSelectedQuest) selectedQuestId = null;
            else if (action instanceof QuestAuthoringPanel.RemoveExistingQuestFromChapter) {
                QuestScreen.this.removeExistingQuestFromChapter();
            } else if (action instanceof QuestAuthoringPanel.ConfirmCreateQuest) {
                QuestScreen.this.confirmCreateQuest();
            } else if (action instanceof QuestAuthoringPanel.UpdateDraftGroupPosition) {
                QuestScreen.this.updateDraftGroupPosition();
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

    private record TaskRef(String path, QuestDefinition.Task task) {}

    private record QuestBackground(
        Identifier texture,
        int xOffset,
        int yOffset,
        int width,
        int height
    ) {}

    private record HeaderLayout(
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
        int canvasTop
    ) {}

    private record ChapterDisplay(String icon, String background, boolean iconEnabled, int backgroundOpacity) {}

    private record ClientQuest(
        QuestDefinition definition,
        Map<String, Integer> progress,
        boolean unlocked,
        boolean complete,
        boolean claimed,
        boolean pinned,
        Set<String> claimedRewards,
        JsonObject raw
    ) {}
}
