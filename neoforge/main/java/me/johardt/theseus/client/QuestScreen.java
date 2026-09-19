package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
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
    private static final int CARD_HEIGHT = 48;
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
    private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/item/check.png"
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
    private static final Identifier PROGRESS_ACTIVE = sprite("widgets/progress_bar_0");
    private static final Identifier PROGRESS_COMPLETE = sprite("widgets/progress_bar_1");
    private static final Identifier PROGRESS_FILL = sprite("widgets/progress_bar_2");
    private static final Identifier HEADING_IN_PROGRESS_LEFT = sprite("headings/in_progress_left");
    private static final Identifier HEADING_IN_PROGRESS_RIGHT = sprite("headings/in_progress_right");
    private static final Identifier HEADING_COMPLETED_LEFT = sprite("headings/claimed_left");
    private static final Identifier HEADING_COMPLETED_RIGHT = sprite("headings/claimed_right");
    private static final List<Identifier> QUEST_BACKGROUNDS = List.of(
        "default", "circles", "diamonds", "gears", "hearts", "hexagons",
        "octagons", "pentagons", "rounded_squares"
    ).stream().map(name -> Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/quest_backgrounds/" + name + ".png"
    )).toList();
    private static final List<TaskChoice> TASK_CHOICES = List.of(
        new TaskChoice("theseus:dummy", "Dummy", Items.PAPER, true),
        new TaskChoice("theseus:item", "Acquire Item", Items.CHEST, true),
        new TaskChoice("theseus:xp", "Experience", Items.EXPERIENCE_BOTTLE, true),
        new TaskChoice("theseus:kill_entity", "Kill Entity", Items.IRON_SWORD, true),
        new TaskChoice("theseus:advancement", "Advancement", Items.WRITABLE_BOOK, true),
        new TaskChoice("theseus:biome", "Biome", Items.GRASS_BLOCK, true),
        new TaskChoice("theseus:block_interaction", "Block Interaction", Items.STONE_BUTTON, true),
        new TaskChoice("theseus:changed_dimension", "Changed Dimension", Items.ENDER_PEARL, true),
        new TaskChoice("theseus:check", "Check", Items.EMERALD, true),
        new TaskChoice("theseus:composite", "Composite", Items.BUNDLE, true),
        new TaskChoice("theseus:entity_interaction", "Entity Interaction", Items.LEAD, true),
        new TaskChoice("theseus:item_interaction", "Item Interaction", Items.STICK, true),
        new TaskChoice("theseus:item_use", "Item Use", Items.CARROT_ON_A_STICK, true),
        new TaskChoice("theseus:location", "Location", Items.COMPASS, true),
        new TaskChoice("theseus:recipe", "Recipe", Items.KNOWLEDGE_BOOK, true),
        new TaskChoice("theseus:stat", "Stat", Items.FEATHER, true),
        new TaskChoice("theseus:structure", "Structure", Items.STRUCTURE_BLOCK, true)
    );
    private static final List<RewardChoice> REWARD_CHOICES = List.of(
        new RewardChoice("theseus:xp", "Experience", Items.EXPERIENCE_BOTTLE),
        new RewardChoice("theseus:item", "Item", Items.CHEST),
        new RewardChoice("theseus:loottable", "Loot Table", Items.CHEST),
        new RewardChoice("theseus:command", "Command", Items.COMMAND_BLOCK),
        new RewardChoice("theseus:selectable", "Selectable Reward", Items.BUNDLE)
    );

    private final List<ClientQuest> quests = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final Map<String, ChapterDisplay> chapterDisplays = new HashMap<>();
    private final Set<String> loadedChapters = new LinkedHashSet<>();
    private final Set<String> pendingChapterLoads = new LinkedHashSet<>();
    private final ChapterListState chapterListState;
    private final Set<String> serverTaskTypes = new LinkedHashSet<>();
    private final Set<String> serverRewardTypes = new LinkedHashSet<>();
    private final Set<String> serverIconTypes = new LinkedHashSet<>();
    private final List<RewardChoiceBounds> rewardChoiceBounds =
        new ArrayList<>();
    private final List<TaskCardBounds> taskCardBounds = new ArrayList<>();
    private final List<RewardCardBounds> rewardCardBounds = new ArrayList<>();
    private final List<RecipeViewerTarget> recipeViewerTargets = new ArrayList<>();
    private final List<DetailTextBounds> detailTextBounds = new ArrayList<>();
    private final List<QuestDescriptionRenderer.Interaction> descriptionInteractions = new ArrayList<>();
    private final List<LockQuestBounds> lockQuestBounds = new ArrayList<>();
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
    private DetailTab createQuestTab = DetailTab.OVERVIEW;
    private MarkdownEditBox descriptionEditor;
    private String descriptionEditorValue = "";
    private int descriptionPreviewScroll;
    private int descriptionPreviewMaxScroll;
    private int detailContentTop;
    private int detailContentBottom;
    private int taskChooserScroll;
    private int createTaskScroll;
    private String rawInspectorTitle = "Raw JSON";
    private String rawInspectorJson = "{}";
    private int rewardChooserScroll;
    private int createRewardScroll;
    private Picker picker = Picker.NONE;
    private PickerTarget pickerTarget = PickerTarget.QUEST_ICON;
    private EditBox pickerSearch;
    private Button createConfirmButton;
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
    private DetailTab detailTab = DetailTab.OVERVIEW;
    private int detailScroll;
    private int detailMaxScroll;
    /** Retained while widget trees rebuild so settings buttons do not jump the draft back to the top. */
    private int draftOverviewScrollY;
    private LayoutWidget<GridLayout> draftOverviewScrollContainer;
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
    private boolean minimapNavigating;
    private boolean minimapHidden;
    private boolean minimapRepositioning;
    private double minimapPositionX;
    private double minimapPositionY;
    private double minimapDragOffsetX;
    private double minimapDragOffsetY;
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

    public QuestScreen(JsonObject snapshot) {
        this(snapshot, null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        super(Component.literal("Theseus Quests"));
        this.authoring = previous == null
            ? new AuthorMode(QuestSurfaceLayout.DEFAULT_ICON_SIZE)
            : previous.authoring.copy();
        this.mode = previous != null && previous.mode.isAuthoring()
            ? this.authoring
            : new PlayMode();
        this.graphViewport = previous == null
            ? new QuestGraphLayout.ViewportMemory()
            : previous.graphViewport.copy();
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
        this.detailScroll = previous == null ? 0 : previous.detailScroll;
        this.draftOverviewScrollY = previous == null ? 0 : previous.draftOverviewScrollY;
        this.detailsOpen = previous != null && previous.detailsOpen;
        this.sidebarOpen = previous == null || previous.sidebarOpen;
        this.createQuestTab = previous == null ? DetailTab.OVERVIEW : previous.createQuestTab;
        this.descriptionEditorValue = previous == null ? "" : previous.descriptionEditorValue;
        this.descriptionPreviewScroll = previous == null ? 0 : previous.descriptionPreviewScroll;
        this.createTaskScroll = previous == null ? 0 : previous.createTaskScroll;
        this.createRewardScroll = previous == null ? 0 : previous.createRewardScroll;
        this.editorMessage = previous == null ? "" : previous.editorMessage;
        this.editorMessageSuccess = previous != null && previous.editorMessageSuccess;
        this.clipboardMutationPending = previous != null && previous.clipboardMutationPending;
        if (previous != null) this.pickerScrollByTarget.putAll(previous.pickerScrollByTarget);
        this.chapterEditorBaseline = previous == null ? null : previous.chapterEditorBaseline;
        this.minimapNavigating = false;
        this.minimapHidden = previous != null && previous.minimapHidden;
        this.minimapRepositioning = false;
        this.minimapPositionX = previous == null ? Double.NaN : previous.minimapPositionX;
        this.minimapPositionY = previous == null ? Double.NaN : previous.minimapPositionY;
        this.minimapDragOffsetX = 0;
        this.minimapDragOffsetY = 0;
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
                addRewardEditorWidgets(authoring.editingNestedReward, true);
                return;
            }
            case NESTED_REWARDS, NESTED_REWARD_CHOOSER -> {
                addNestedRewardWidgets();
                return;
            }
            case REWARD_EDITOR -> {
                addRewardEditorWidgets(authoring.editingReward, false);
                return;
            }
            case NESTED_TASKS, NESTED_TASK_CHOOSER -> {
                addNestedTaskWidgets();
                return;
            }
            case TASK_EDITOR -> {
                addTaskEditorWidgets();
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
                Component.literal(
                    sidebarOpen ? "Collapse quest groups" : "Show quest groups"
                )
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
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Diagnostics")));
                    widget.withCallback(() -> {
                        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
                        diagnosticsScroll = 0;
                        rebuildWidgets();
                    });
                    widget.withTooltip(Component.literal("View validation diagnostics"));
                }));
            }
            if (mode.isAuthoring() && !authoring.open) addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(header.importX(), header.importY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Import")));
                    widget.withCallback(this::openNativeFilePicker);
                    widget.withTooltip(Component.literal("Choose one or more quest JSON files"));
                }));
            addRenderableWidget(editorButton(
                header.editX(),
                "edit",
                mode.isAuthoring(),
                mode.isAuthoring() ? "Leave quest edit mode" : "Edit quests",
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
                    tool.tooltip + " (" + tool.shortcut + ")",
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
                    if (chapterLabelRequiresTooltip(candidate, buttonWidth)) {
                        widget.withTooltip(Component.literal(candidate));
                    }
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
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 32, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                        widget.withCallback(() -> reorderChapter(index, 1));
                        widget.active = index < orderedGroups.size() - 1;
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 19, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                        widget.withCallback(() -> openChapterEditor(candidate));
                        widget.withTooltip(Component.literal("Edit chapter"));
                    }));
                }
            }
            int addChapterY = Math.max(CHAPTER_LIST_TOP, height - 24);
            if (mode.isAuthoring()) addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(4, addChapterY).withSize(sidebarWidth - 8, 20);
                widget.withTexture(null);
                widget.withRenderer(chapterButtonRenderer("+  Add chapter", false));
                widget.withCallback(() -> openChapterEditor(null));
            }));
        }
        addDockWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (draftOverviewScrollContainer != null) {
            draftOverviewScrollY = draftOverviewScrollContainer.getYScroll();
        }
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
                widget.withRenderer(WidgetRenderers.text(Component.literal("F")));
                widget.withCallback(this::fitGraphToContent);
                widget.withTooltip(Component.literal("Fit visible quests in the graph"));
            }));
        if (!TheseusClientOptions.disableMinimap()
            && minimapHidden
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
                        minimapHidden = false;
                        clearMinimapTransientState();
                        rebuildWidgets();
                    });
                    widget.withTooltip(Component.literal("Show quest minimap"));
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
                widget.withTooltip(Component.literal(visible ? "Hide graph grid" : "Show graph grid"));
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
                widget.withTooltip(Component.literal(enabled ? "Disable snap to grid" : "Enable snap to grid"));
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
            addCreateQuestDockWidgets();
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
                        Component.literal(tab.label)
                    ).withColor(
                        tab == detailTab
                            ? new Color(ClientThemeLoader.active().questDetails().tabButtonSelected())
                            : new Color(ClientThemeLoader.active().questDetails().tabButton())
                    )
                );
                widget.withCallback(() -> {
                    detailTab = tab;
                    detailScroll = 0;
                    rebuildWidgets();
                });
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
            widget.withTooltip(Component.literal("Close quest details"));
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
                Component.literal(
                    selected != null && selected.pinned
                        ? "Unpin quest"
                        : "Pin quest"
                )
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
                WidgetRenderers.text(Component.literal("Claim rewards"))
            );
            widget.withCallback(this::claimSelected);
            widget.active =
                selected != null &&
                selected.complete &&
                !selected.claimed &&
                !mutations.isPending() &&
                canClaimRewards(selected);
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
                    WidgetRenderers.text(Component.literal("Submit task"))
                );
                widget.withCallback(() -> submitTask(selected, submittable));
                widget.active = !mutations.isPending();
            });
            addRenderableWidget(submit);
        }
    }

    private Button editorButton(
        int x,
        String icon,
        boolean selected,
        String tooltip,
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
            widget.withTooltip(Component.literal(tooltip));
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
            if (selected) {
                graphics.outline(
                    context.getX(),
                    context.getY(),
                    context.getWidth(),
                    context.getHeight(),
                    0xFF8A929F
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

    private void addCreateQuestDockWidgets() {
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
                    Component.literal(tab.label)
                ).withColor(Color.parse(
                    tab == createQuestTab ? "#5A4300" : "#FFFFFF"
                )));
                widget.withCallback(() -> {
                    createQuestTab = tab;
                    closePicker();
                    if (modalHost.isOneOf(QuestModalHost.Modal.TASK_CHOOSER, QuestModalHost.Modal.REWARD_CHOOSER)
                        || modalHost.is(QuestModalHost.Modal.PICKER)) {
                        modalHost.close();
                    }
                    rebuildWidgets();
                });
            });
            addRenderableWidget(tabButton);
        }
        Button close = Widgets.button(widget -> {
            widget.withPosition(width - 27, 8).withSize(19, 20);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(CLOSE_BUTTON)
            ));
            widget.withCallback(() -> {
                requestDiscard(() -> {
                    closeDraft();
                    selectedQuestId = null;
                    rebuildWidgets();
                });
            });
            widget.withTooltip(Component.literal("Close new quest"));
        });
        addRenderableWidget(close);

        int x = detailsLeft + 12;
        int fieldWidth = detailsWidth() - 24;
        createConfirmButton = Widgets.button(widget -> {
            widget.withPosition(x, height - 30).withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal(authoring.editingExisting ? "Save quest" : "Create quest")));
            widget.withCallback(this::confirmCreateQuest);
            String error = draftValidationError();
            widget.withTooltip(Component.literal(mutations.isPending() ? "Waiting for the server" : error.isEmpty() ? "Save this quest" : error));
        });
        updateCreateConfirmButton();
        addRenderableWidget(createConfirmButton);

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
        layout.addChild(dockLabel("ID", fieldWidth), row++, 0);
        EditBox id = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest ID"));
        id.setValue(authoring.id);
        id.setResponder(value -> {
            authoring.id = value;
            updateCreateConfirmButton();
        });
        layout.addChild(id, row++, 0);

        layout.addChild(dockLabel("Title", fieldWidth), row++, 0);
        EditBox title = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest title"));
        title.setValue(authoring.title);
        title.setResponder(value -> {
            authoring.title = value;
            updateCreateConfirmButton();
        });
        layout.addChild(title, row++, 0);

        layout.addChild(dockLabel("Subtitle", fieldWidth), row++, 0);
        EditBox subtitle = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest subtitle"));
        subtitle.setValue(authoring.subtitle);
        subtitle.setResponder(value -> authoring.subtitle = value);
        layout.addChild(subtitle, row++, 0);

        layout.addChild(dockLabel("Description", fieldWidth), row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 32);
            widget.withRenderer(WidgetRenderers.text(Component.literal(
                authoring.body.isBlank() ? "Write rich description…" : "Edit rich description…"
            )));
            widget.withCallback(this::openDescriptionEditor);
            widget.withTooltip(Component.literal("Markdown editor with live player preview"));
        }), row++, 0);

        layout.addChild(dockLabel("Appearance", fieldWidth), row++, 0);
        GridLayout appearance = new GridLayout().columnSpacing(6);
        Button icon = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Choose icon")));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.QUEST_ICON));
            widget.withTooltip(Component.literal("Choose quest icon"));
        });
        Button background = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Choose background")));
            widget.withCallback(() -> openPicker(Picker.BACKGROUND));
            widget.withTooltip(Component.literal("Choose quest background"));
        });
        appearance.addChild(icon, 0, 0);
        appearance.addChild(background, 0, 1);
        layout.addChild(appearance, row++, 0);
        layout.addChild(dockLabel("Icon size (8–64)", fieldWidth), row++, 0);
        GridLayout iconSize = new GridLayout().columnSpacing(6);
        Button decreaseIconSize = Widgets.button(widget -> {
            widget.withSize(28, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("−")));
            widget.active = authoring.iconSize > QuestSurfaceLayout.MIN_ICON_SIZE;
            widget.withCallback(() -> adjustCreateQuestIconSize(-1));
        });
        EditBox iconSizeField = new EditBox(font, 0, 0, Math.max(44, fieldWidth - 68), 18, Component.literal("Icon size"));
        iconSizeField.setValue(authoring.iconSizeText);
        iconSizeField.setResponder(this::updateCreateQuestIconSize);
        Button increaseIconSize = Widgets.button(widget -> {
            widget.withSize(28, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+")));
            widget.active = authoring.iconSize < QuestSurfaceLayout.MAX_ICON_SIZE;
            widget.withCallback(() -> adjustCreateQuestIconSize(1));
        });
        iconSize.addChild(decreaseIconSize, 0, 0);
        iconSize.addChild(iconSizeField, 0, 1);
        iconSize.addChild(increaseIconSize, 0, 2);
        layout.addChild(iconSize, row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Inspect display JSON")));
            widget.withCallback(() -> openRawInspector("Display", draftDisplay()));
            widget.withTooltip(Component.literal("Read the generated display configuration"));
        }), row++, 0);

        layout.addChild(dockLabel("Position", fieldWidth), row++, 0);
        GridLayout position = new GridLayout().columnSpacing(6);
        int positionWidth = (fieldWidth - 6) / 2;
        EditBox positionX = new EditBox(font, 0, 0, positionWidth, 18, Component.literal("X"));
        positionX.setValue(authoring.xText);
        positionX.setResponder(value -> updateCreateQuestPosition(true, value));
        EditBox positionY = new EditBox(font, 0, 0, positionWidth, 18, Component.literal("Y"));
        positionY.setValue(authoring.yText);
        positionY.setResponder(value -> updateCreateQuestPosition(false, value));
        position.addChild(positionX, 0, 0);
        position.addChild(positionY, 0, 1);
        layout.addChild(position, row++, 0);
        layout.addChild(Widgets.button(widget -> {
            widget.withSize(fieldWidth, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Snap position")));
            widget.withCallback(this::snapCurrentDraftPosition);
            widget.withTooltip(Component.literal("Snap this quest center to the 27-unit graph grid"));
        }), row++, 0);

        layout.addChild(dockLabel("Quest settings", fieldWidth), row++, 0);
        GridLayout settings = new GridLayout().columnSpacing(6).rowSpacing(4);
        int settingWidth = (fieldWidth - 6) / 2;
        settings.addChild(settingButton(settingWidth, "Individual progress", authoring.individualProgress,
            () -> authoring.individualProgress = !authoring.individualProgress), 0, 0);
        settings.addChild(settingButton(settingWidth, "Unlock notification", authoring.unlockNotification,
            () -> authoring.unlockNotification = !authoring.unlockNotification), 0, 1);
        settings.addChild(settingButton(settingWidth, "Dependency arrows", authoring.showDependencyArrow,
            () -> authoring.showDependencyArrow = !authoring.showDependencyArrow), 1, 0);
        settings.addChild(settingButton(settingWidth, "Repeatable", authoring.repeatable,
            () -> authoring.repeatable = !authoring.repeatable), 1, 1);
        settings.addChild(settingButton(settingWidth, "Auto-claim rewards", authoring.autoClaimRewards,
            () -> authoring.autoClaimRewards = !authoring.autoClaimRewards), 2, 0);
        String hidden = friendly(authoring.hiddenUntil.name().toLowerCase(java.util.Locale.ROOT));
        settings.addChild(Widgets.button(widget -> {
            widget.withSize(settingWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Visible: " + hidden)));
            widget.withCallback(() -> {
                QuestDefinition.Visibility[] values = QuestDefinition.Visibility.values();
                authoring.hiddenUntil = values[(authoring.hiddenUntil.ordinal() + 1) % values.length];
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Choose when the quest becomes visible"));
        }), 2, 1);
        layout.addChild(settings, row++, 0);

        if (authoring.editingExisting) {
            layout.addChild(dockLabel("Quest actions", fieldWidth), row++, 0);
            GridLayout actions = new GridLayout().columnSpacing(6);
            Button delete = Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Delete quest")));
                widget.withCallback(() -> {
                    modalHost.open(QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION);
                    rebuildWidgets();
                });
            });
            actions.addChild(delete, 0, 0);
            if (authoring.groups.size() > 1) actions.addChild(Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Remove from chapter")));
                widget.withCallback(() -> requestDiscard(this::removeExistingQuestFromChapter));
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
        addRenderableWidget(scrollable);
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
        if (valid) updateDraftGroupPosition();
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
        rebuildWidgets();
    }

    /** Snaps the active authoring draft once, leaving the change for Save. */
    private void snapCurrentDraftPosition() {
        QuestGraphLayout.Point snapped = QuestGraphLayout.snapPoint(authoring.x, authoring.y);
        authoring.x = (int) snapped.x();
        authoring.y = (int) snapped.y();
        authoring.xText = Integer.toString(authoring.x);
        authoring.yText = Integer.toString(authoring.y);
        authoring.xInvalid = false;
        authoring.yInvalid = false;
        updateDraftGroupPosition();
        rebuildWidgets();
    }

    private Button settingButton(int width, String label, boolean value, Runnable toggle) {
        return Widgets.button(widget -> {
            widget.withSize(width, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal(label + ": " + (value ? "On" : "Off"))));
            widget.withCallback(() -> {
                toggle.run();
                rebuildWidgets();
            });
        });
    }

    private void addRawInspectorButton(int x, int y, int width, Runnable open) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Raw JSON")));
            widget.withCallback(open);
            widget.withTooltip(Component.literal("Inspect this configuration without editing it"));
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
            widget.withRenderer(WidgetRenderers.text(Component.literal("Close")));
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
            Component.literal("Quest Markdown description")
        );
        descriptionEditor.setValue(descriptionEditorValue);
        descriptionEditor.setValueListener(value -> descriptionEditorValue = value);
        addRenderableWidget(descriptionEditor);
        setInitialFocus(descriptionEditor);

        int actionX = left + 12;
        int toolbarY = top + 31;
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "H1", "header1", () -> descriptionEditor.prefixLine("# "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "H2", "header2", () -> descriptionEditor.prefixLine("## "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Bold", "B", () -> descriptionEditor.surround("**"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Italic", "I", () -> descriptionEditor.surround("--"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Underline", "U", () -> descriptionEditor.surround("__"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Strikethrough", "S", () -> descriptionEditor.surround("~~"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Spoiler", "||", () -> descriptionEditor.surround("||"));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Color", "C", () -> descriptionEditor.surround("/e/"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "List", "list", () -> descriptionEditor.prefixLine("- "));
        actionX = addMarkdownTextAction(actionX, toolbarY, "Blockquote", ">", () -> descriptionEditor.prefixLine("> "));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "Link", "link", () -> descriptionEditor.insertLink(null, "https://"));
        actionX = addMarkdownSpriteAction(actionX, toolbarY, "Horizontal rule", "horizontalline", () -> descriptionEditor.insert("\n---\n"));

        int objectX = actionX;
        if (!authoring.tasks.isEmpty()) {
            objectX = addMarkdownSpriteAction(objectX, toolbarY, "Insert task", "task", () ->
                descriptionEditor.insertObject("task", authoring.tasks.getFirst().id));
        }
        if (!authoring.rewards.isEmpty()) {
            addMarkdownSpriteAction(objectX, toolbarY, "Insert reward", "reward", () ->
                descriptionEditor.insertObject("reward", authoring.rewards.getFirst().id));
        }

        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 174, top + modalHeight - 31).withSize(76, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(this::closeDescriptionEditor);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + modalWidth - 92, top + modalHeight - 31).withSize(80, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Apply")));
            widget.withCallback(this::applyDescriptionEditor);
        }));
    }

    private int addMarkdownSpriteAction(int x, int y, String tooltip, String icon, Runnable action) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withTexture(null);
            Identifier normal = sprite("editor/" + icon + "/normal");
            Identifier hovered = sprite("editor/" + icon + "/hovered");
            widget.withRenderer(WidgetRenderers.sprite(new WidgetSprites(normal, hovered)));
            widget.withCallback(action);
            widget.withTooltip(Component.literal(tooltip));
        }));
        return x + MARKDOWN_ACTION_SIZE + MARKDOWN_ACTION_GAP;
    }

    private int addMarkdownTextAction(int x, int y, String tooltip, String label, Runnable action) {
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(MARKDOWN_ACTION_SIZE, MARKDOWN_ACTION_SIZE);
            widget.withRenderer(WidgetRenderers.center(
                MARKDOWN_ACTION_SIZE,
                MARKDOWN_ACTION_SIZE,
                WidgetRenderers.text(Component.literal(label))
            ));
            widget.withCallback(action);
            widget.withTooltip(Component.literal(tooltip));
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

    private TextWidget dockLabel(String text, int width) {
        return Widgets.text(Component.literal(text), widget -> {
            widget.withLeftAlignment().withFont(font).withColor(Color.parse("#B8C0CC"));
            widget.setSize(width, 12);
        });
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
        EditBox name = new EditBox(font, left + 14, top + 48, 252, 18, Component.literal("Chapter name"));
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
            widget.withTooltip(Component.literal("Choose chapter icon"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 54, top + 81).withSize(100, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal(chapterEditorIconEnabled ? "Icon: On" : "Icon: Off")));
            widget.withCallback(() -> {
                chapterEditorIconEnabled = !chapterEditorIconEnabled;
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Show or hide this chapter's icon"));
        }));
        EditBox background = new EditBox(font, left + 14, top + 126, 252, 18, Component.literal("Background path or URL"));
        background.setValue(chapterEditorBackground);
        background.setResponder(value -> chapterEditorBackground = value);
        addRenderableWidget(background);
        EditBox opacity = new EditBox(font, left + 14, top + 158, 90, 18, Component.literal("Opacity"));
        opacity.setValue(Integer.toString(chapterEditorBackgroundOpacity));
        opacity.setResponder(value -> {
            try { chapterEditorBackgroundOpacity = Math.clamp(Integer.parseInt(value), 0, 100); }
            catch (NumberFormatException ignored) { chapterEditorBackgroundOpacity = 100; }
        });
        addRenderableWidget(opacity);
        if (chapterEditorOriginal != null) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 201).withSize(72, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
            widget.withCallback(this::deleteChapter);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 94, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(() -> {
                chapterEditorBaseline = null;
                modalHost.close();
                rebuildWidgets();
            }));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 184, top + 201).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save")));
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
                Component.literal(picker == Picker.ENTITY
                    ? "Search entities"
                    : "Search blocks and items")
            );
            pickerSearch.setResponder(ignored -> pickerScroll = 0);
            addRenderableWidget(pickerSearch);
            setInitialFocus(pickerSearch);
        }
    }

    private void addDraftTaskWidgets(int x, int width) {
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
                widget.withTooltip(Component.literal(widget.active ? "Edit task" : unavailableReason(EditorTypeRegistry.Kind.TASK, authoring.tasks.get(taskIndex).type)));
            });
            addRenderableWidget(edit);
            Button delete = Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("delete"));
                widget.withCallback(() -> {
                    authoring.taskDeleteConfirmation = taskIndex;
                    modalHost.open(QuestModalHost.Modal.TASK_DELETE_CONFIRMATION);
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete task"));
            });
            addRenderableWidget(delete);
        }
        int addIndex = authoring.tasks.size();
        if (addIndex >= createTaskScroll && addIndex < createTaskScroll + taskListCapacity()) {
            int addY = y + (addIndex - createTaskScroll) * 48;
            Button add = Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add task")));
                widget.withCallback(() -> {
                    taskChooserScroll = 0;
                    if (modalHost.isTaskChooserOpen()) modalHost.close();
                    else modalHost.open(QuestModalHost.Modal.TASK_CHOOSER);
                });
                widget.withTooltip(Component.literal("Choose a task type"));
            });
            addRenderableWidget(add);
        }
    }

    private void addDraftRewardWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(authoring.rewards.size(), createRewardScroll + rewardListCapacity());
        for (int index = createRewardScroll; index < end; index++) {
            int rewardIndex = index;
            int cardY = y + (index - createRewardScroll) * 48;
            int actionY = cardY + (42 - EDITOR_LIST_ACTION_HEIGHT) / 2;
            int deleteX = x + width - EDITOR_LIST_ACTION_WIDTH - 8;
            int editX = deleteX - 4 - EDITOR_LIST_ACTION_WIDTH;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(editX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("edit"));
                widget.withCallback(() -> openRewardEditor(rewardIndex));
                widget.active = isRewardEditable(authoring.rewards.get(rewardIndex));
                widget.withTooltip(Component.literal(widget.active ? "Edit reward" : unavailableReason(EditorTypeRegistry.Kind.REWARD, authoring.rewards.get(rewardIndex).type)));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(deleteX, actionY).withSize(EDITOR_LIST_ACTION_WIDTH, EDITOR_LIST_ACTION_HEIGHT);
                widget.withRenderer(listActionRenderer("delete"));
                widget.withCallback(() -> {
                    authoring.rewards.remove(rewardIndex);
                    createRewardScroll = Math.min(createRewardScroll, maxCreateRewardScroll());
                    if (modalHost.isRewardChooserOpen()) modalHost.close();
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete reward"));
            }));
        }
        int addIndex = authoring.rewards.size();
        if (addIndex >= createRewardScroll && addIndex < createRewardScroll + rewardListCapacity()) {
            int addY = y + (addIndex - createRewardScroll) * 48;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add reward")));
                widget.withCallback(() -> {
                    rewardChooserScroll = 0;
                    if (modalHost.isRewardChooserOpen()) modalHost.close();
                    else modalHost.open(QuestModalHost.Modal.REWARD_CHOOSER);
                });
                widget.withTooltip(Component.literal("Choose a reward type"));
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
            editorMessage = unavailableReason(EditorTypeRegistry.Kind.REWARD, authoring.rewards.get(index).type) + ". It is preserved read-only.";
            editorMessageSuccess = false;
            return;
        }
        authoring.editingRewardIndex = index;
        authoring.editingReward = authoring.rewards.get(index).copy();
        authoring.rewardEditorError = "";
        modalHost.open(QuestModalHost.Modal.REWARD_EDITOR);
        rebuildWidgets();
    }

    private void openTaskEditor(int index) {
        if (!isTaskEditable(authoring.tasks.get(index))) {
            editorMessage = unavailableReason(EditorTypeRegistry.Kind.TASK, authoring.tasks.get(index).type) + ". It is preserved read-only.";
            editorMessageSuccess = false;
            return;
        }
        authoring.editingTaskIndex = index;
        authoring.editingTask = authoring.tasks.get(index).copy();
        authoring.taskEditorParents.clear();
        authoring.taskEditorParentIndexes.clear();
        authoring.taskEditorError = "";
        modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
        rebuildWidgets();
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
        createQuestTab = DetailTab.OVERVIEW;
        draftOverviewScrollY = 0;
        draftOverviewScrollContainer = null;
        createTaskScroll = 0;
        createRewardScroll = 0;
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
        createTaskScroll = 0;
        createQuestTab = DetailTab.OVERVIEW;
        draftOverviewScrollY = 0;
        draftOverviewScrollContainer = null;
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
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> {
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
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
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> {
                progressResetTarget = null;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 146, top + 102).withSize(122, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Reset progress")));
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
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> {
                authoring.taskDeleteConfirmation = -1;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
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
            createTaskScroll = Math.min(createTaskScroll, maxCreateTaskScroll());
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
            widget.withRenderer(WidgetRenderers.text(Component.literal("Keep editing")));
            widget.withCallback(() -> {
                modalHost.cancelDismissal();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 136, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Discard changes")));
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

    private void addTaskEditorWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        int fieldWidth = 232;

        EditBox id = new EditBox(font, left + 14, top + 38, fieldWidth, 18, Component.literal("Task ID"));
        id.setValue(authoring.editingTask.id);
        id.setResponder(value -> authoring.editingTask.id = value);
        addRenderableWidget(id);

        EditBox title = new EditBox(font, left + 14, top + 70, fieldWidth, 18, Component.literal("Task title"));
        title.setValue(jsonString(authoring.editingTask.source, "title", ""));
        title.setResponder(value -> setOptionalString(authoring.editingTask.source, "title", value));
        addRenderableWidget(title);

        Button icon = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.TASK_ICON));
            widget.withTooltip(Component.literal("Choose task icon override"));
        });
        addRenderableWidget(icon);
        Button clearIcon = Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                authoring.editingTask.source.remove("icon");
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Use the default task icon"));
        });
        addRenderableWidget(clearIcon);
        // Keep action controls outside the label lane drawn by the foreground pass.
        addRawInspectorButton(left + TASK_RAW_INSPECTOR_X, top + 101, 88, () -> openRawInspector("Task: " + authoring.editingTask.id, authoring.editingTask.source));

        switch (authoring.editingTask.type) {
            case "theseus:dummy" -> addDummyTaskFields(left, top, fieldWidth);
            case "theseus:item" -> addItemTaskFields(left, top, fieldWidth);
            case "theseus:xp" -> addXpTaskFields(left, top, fieldWidth);
            case "theseus:kill_entity" -> addKillTaskFields(left, top, fieldWidth);
            case "theseus:advancement" -> addStringListTaskField(left, top, fieldWidth, "advancements", "Advancement IDs", "minecraft:story/mine_stone");
            case "theseus:biome" -> addIdentifierTaskField(left, top, fieldWidth, "biomes", "Biome or #tag", "minecraft:plains");
            case "theseus:block_interaction" -> addBlockInteractionTaskFields(left, top, fieldWidth);
            case "theseus:changed_dimension" -> addDimensionTaskFields(left, top, fieldWidth);
            case "theseus:check" -> addJsonTaskField(left, top, fieldWidth, "components", "Player data predicate", new JsonObject());
            case "theseus:composite" -> addCompositeTaskFields(left, top, fieldWidth);
            case "theseus:entity_interaction" -> addPredicateTargetFields(left, top, fieldWidth, "entity", "Entity or #tag", "minecraft:pig", PickerTarget.TASK_ENTITY);
            case "theseus:item_interaction", "theseus:item_use" -> addPredicateTargetFields(left, top, fieldWidth, "item", "Item or #tag", "minecraft:stick", PickerTarget.TASK_ITEM);
            case "theseus:location" -> addLocationTaskFields(left, top, fieldWidth);
            case "theseus:recipe" -> addStringListTaskField(left, top, fieldWidth, "recipes", "Recipe IDs", "minecraft:crafting_table");
            case "theseus:stat" -> addStatTaskFields(left, top, fieldWidth);
            case "theseus:structure" -> addIdentifierTaskField(left, top, fieldWidth, "structures", "Structure or #tag", "#minecraft:village");
            default -> {
            }
        }

        Button cancel = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(this::closeTaskEditor));
        });
        addRenderableWidget(cancel);
        Button save = Widgets.button(widget -> {
            widget.withPosition(left + 138, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save task")));
            widget.withCallback(this::saveTaskEditor);
        });
        addRenderableWidget(save);
    }

    private void addDummyTaskFields(int left, int top, int width) {
        EditBox value = new EditBox(font, left + 14, top + 142, width, 18, Component.literal("Trigger value"));
        value.setValue(jsonString(authoring.editingTask.source, "value", ""));
        value.setResponder(text -> authoring.editingTask.source.addProperty("value", text));
        addRenderableWidget(value);
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.literal("Description"));
        description.setValue(jsonString(authoring.editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(authoring.editingTask.source, "description", text));
        addRenderableWidget(description);
    }

    private void addItemTaskFields(int left, int top, int width) {
        EditBox item = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal("Item or #tag"));
        item.setValue(registryValueString(authoring.editingTask.source, "item", "minecraft:stone"));
        item.setResponder(text -> authoring.editingTask.source.addProperty("item", text));
        addRenderableWidget(item);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.TASK_ITEM));
            widget.withTooltip(Component.literal("Choose item"));
        });
        addRenderableWidget(choose);
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
        EditBox entity = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal("Entity"));
        entity.setValue(registryValueString(authoring.editingTask.source, "entity", "minecraft:pig"));
        entity.setResponder(text -> authoring.editingTask.source.addProperty("entity", text));
        addRenderableWidget(entity);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(Picker.ENTITY, PickerTarget.TASK_ENTITY));
            widget.withTooltip(Component.literal("Choose entity"));
        });
        addRenderableWidget(choose);
        addAmountField(left, top + 181);
    }

    private void addIdentifierTaskField(int left, int top, int width, String key, String label, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setValue(registryValueString(authoring.editingTask.source, key, fallback));
        field.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        addRenderableWidget(field);
    }

    private void addStringListTaskField(int left, int top, int width, String key, String label, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setValue(jsonStringList(authoring.editingTask.source, key, fallback));
        field.setResponder(text -> authoring.editingTask.source.add(key, stringArray(text)));
        addRenderableWidget(field);
    }

    private void addPredicateTargetFields(
        int left, int top, int width, String key, String label, String fallback, PickerTarget target
    ) {
        EditBox value = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal(label));
        value.setValue(registryValueString(authoring.editingTask.source, key, fallback));
        value.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        addRenderableWidget(value);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(target == PickerTarget.TASK_ENTITY ? Picker.ENTITY : Picker.ICON, target));
            widget.withTooltip(Component.literal("Choose target"));
        }));
        addJsonTaskField(left, top + 39, width, "components", "Component/data predicate", new JsonObject());
    }

    private void addBlockInteractionTaskFields(int left, int top, int width) {
        addPredicateTargetFields(left, top, width, "block", "Block or #tag", "minecraft:stone", PickerTarget.TASK_BLOCK);
        addJsonTaskField(left, top + 78, width, "state", "Block state predicate", new JsonObject());
    }

    private void addLocationTaskFields(int left, int top, int width) {
        addJsonTaskField(left, top, width, "predicate", "Location predicate", defaultLocationPredicate());
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.literal("Description"));
        description.setValue(jsonString(authoring.editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(authoring.editingTask.source, "description", text));
        addRenderableWidget(description);
    }

    private void addDimensionTaskFields(int left, int top, int width) {
        EditBox from = new EditBox(font, left + 14, top + 142, 110, 18, Component.literal("From dimension"));
        from.setValue(jsonString(authoring.editingTask.source, "from", ""));
        from.setResponder(text -> setOptionalString(authoring.editingTask.source, "from", text));
        addRenderableWidget(from);
        EditBox to = new EditBox(font, left + 136, top + 142, 110, 18, Component.literal("To dimension"));
        to.setValue(jsonString(authoring.editingTask.source, "to", ""));
        to.setResponder(text -> setOptionalString(authoring.editingTask.source, "to", text));
        addRenderableWidget(to);
    }

    private void addJsonTaskField(int left, int top, int width, String key, String label, JsonObject fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setMaxLength(2048);
        JsonElement current = authoring.editingTask.source.get(key);
        field.setValue(current == null ? GSON.toJson(fallback) : current.isJsonPrimitive() ? current.getAsString() : GSON.toJson(current));
        field.setResponder(text -> authoring.editingTask.source.addProperty(key, text));
        addRenderableWidget(field);
    }

    private void addCompositeTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 104, top + 139).withSize(142, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Manage children (" + nestedTasks(authoring.editingTask).size() + ")")));
            widget.withCallback(() -> {
                authoring.nestedTaskScroll = 0;
                modalHost.open(QuestModalHost.Modal.NESTED_TASKS);
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Edit this composite task's child tasks"));
        }));
    }

    /** The same editable task rows used at the quest root, scoped to a composite. */
    private void addNestedTaskWidgets() {
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
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(140, 34);
                widget.withRenderer(WidgetRenderers.text(Component.literal(child.id + "  ·  " + taskDisplayLabel(child))));
                widget.withCallback(() -> {
                    authoring.taskEditorParents.add(authoring.editingTask);
                    authoring.taskEditorParentIndexes.add(authoring.editingTaskIndex);
                    authoring.editingTask = children.get(childIndex).copy();
                    authoring.editingTaskIndex = childIndex;
                    modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
                    authoring.taskEditorError = "";
                    rebuildWidgets();
                });
                widget.active = isTaskEditable(child);
                widget.withTooltip(Component.literal(widget.active ? "Edit child task" : unavailableReason(EditorTypeRegistry.Kind.TASK, child.type)));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 160, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedTask(childIndex, -1));
                widget.active = childIndex > 0;
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 188, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedTask(childIndex, 1));
                widget.active = childIndex < children.size() - 1;
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 216, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    List<QuestAuthoringSession.TaskDraft> updated = nestedTasks(authoring.editingTask);
                    updated.remove(childIndex);
                    setNestedTasks(authoring.editingTask, updated);
                    authoring.nestedTaskScroll = Math.min(authoring.nestedTaskScroll, Math.max(0, updated.size() - 4));
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete child task"));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+ Add task")));
            widget.withCallback(() -> {
                taskChooserScroll = 0;
                if (modalHost.isNestedTaskChooserOpen()) modalHost.close();
                else modalHost.open(QuestModalHost.Modal.NESTED_TASK_CHOOSER);
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Choose a task type"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 133, top + 218).withSize(113, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Done")));
            widget.withCallback(() -> {
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
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
        rebuildWidgets();
    }

    private void addStatTaskFields(int left, int top, int width) {
        EditBox stat = new EditBox(font, left + 14, top + 142, width - 100, 18, Component.literal("Statistic ID"));
        stat.setValue(jsonString(authoring.editingTask.source, "stat", "minecraft:jump"));
        stat.setResponder(text -> authoring.editingTask.source.addProperty("stat", text));
        addRenderableWidget(stat);
        EditBox target = new EditBox(font, left + width - 76, top + 142, 76, 18, Component.literal("Target"));
        target.setValue(Integer.toString(jsonInt(authoring.editingTask.source, "target", 1)));
        target.setResponder(text -> authoring.editingTask.source.addProperty("target", parseInteger(text)));
        addRenderableWidget(target);
    }

    private void addAmountField(int left, int y) {
        EditBox amount = new EditBox(font, left + 14, y, 82, 18, Component.literal("Amount"));
        amount.setValue(Integer.toString(jsonInt(authoring.editingTask.source, "amount", 1)));
        amount.setResponder(text -> {
            try {
                authoring.editingTask.source.addProperty("amount", Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                authoring.editingTask.source.addProperty("amount", 0);
            }
        });
        addRenderableWidget(amount);
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
            widget.withRenderer(WidgetRenderers.text(Component.literal(friendly(current))));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                authoring.editingTask.source.addProperty(key, values.get((index + 1) % values.size()));
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Click to change"));
        });
        addRenderableWidget(cycle);
    }

    private void closeTaskEditor() {
        boolean hasParent = !authoring.taskEditorParents.isEmpty();
        if (hasParent) {
            authoring.editingTask = authoring.taskEditorParents.removeLast();
            authoring.editingTaskIndex = authoring.taskEditorParentIndexes.removeLast();
        } else {
            authoring.editingTask = null;
            authoring.editingTaskIndex = -1;
        }
        authoring.taskEditorError = "";
        closePicker();
        if (modalHost.isOneOf(QuestModalHost.Modal.TASK_EDITOR, QuestModalHost.Modal.NESTED_TASKS)) {
            modalHost.close();
        }
        if (!hasParent && modalHost.is(QuestModalHost.Modal.TASK_EDITOR)) modalHost.close();
        rebuildWidgets();
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

    private void addRewardEditorWidgets(QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        int width = 272;
        EditBox id = new EditBox(font, left + 14, top + 38, width, 18, Component.literal("Reward ID"));
        id.setValue(reward.id);
        id.setResponder(value -> reward.id = value);
        addRenderableWidget(id);
        EditBox title = new EditBox(font, left + 14, top + 70, width, 18, Component.literal("Reward title"));
        title.setValue(jsonString(reward.source, "title", ""));
        title.setResponder(value -> setOptionalString(reward.source, "title", value));
        addRenderableWidget(title);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.REWARD_ICON));
            widget.withTooltip(Component.literal("Choose reward icon override"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                reward.source.remove("icon");
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Use the default reward icon"));
        }));
        addRawInspectorButton(left + REWARD_RAW_INSPECTOR_X, top + 101, 88, () -> openRawInspector("Reward: " + reward.id, reward.source));
        switch (reward.type) {
            case "theseus:xp" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRewardCycleButton(reward, left + 114, top + 139, 172, "xptype", "level", List.of("level", "points"));
            }
            case "theseus:item" -> {
                EditBox item = new EditBox(font, left + 14, top + 142, 210, 18, Component.literal("Item"));
                item.setValue(rewardItemId(reward.source));
                item.setResponder(value -> setRewardItem(reward.source, value, rewardItemCount(reward.source)));
                addRenderableWidget(item);
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 232, top + 139).withSize(54, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                    widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.REWARD_ITEM));
                    widget.withTooltip(Component.literal("Choose item"));
                }));
                addRewardAmountField(reward, left + 14, top + 181, 92, "item.count");
            }
            case "theseus:loottable" -> addRewardTextField(reward, left, top, "loot_table", "Loot table");
            case "theseus:command" -> addRewardTextField(reward, left, top, "command", "Command");
            case "theseus:selectable" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 114, top + 139).withSize(172, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Manage choices (" + nestedRewards(reward).size() + ")")));
                    widget.withCallback(() -> {
                        authoring.rewardEditorError = "";
                        modalHost.open(QuestModalHost.Modal.NESTED_REWARDS);
                        rebuildWidgets();
                    });
                }));
            }
            default -> { }
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(() -> closeRewardEditor(nested)));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 158, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save reward")));
            widget.withCallback(() -> saveRewardEditor(nested));
        }));
    }

    private void addRewardTextField(QuestAuthoringSession.RewardDraft reward, int left, int top, String key, String label) {
        EditBox field = new EditBox(font, left + 14, top + 142, 272, 18, Component.literal(label));
        field.setValue(jsonString(reward.source, key, ""));
        field.setResponder(value -> reward.source.addProperty(key, value));
        addRenderableWidget(field);
    }

    private void addRewardAmountField(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String path) {
        int current = path.equals("item.count") ? rewardItemCount(reward.source) : jsonInt(reward.source, path, 1);
        EditBox amount = new EditBox(font, x, y, width, 18, Component.literal("Amount"));
        amount.setValue(Integer.toString(current));
        amount.setResponder(value -> {
            int parsed;
            try { parsed = Integer.parseInt(value); } catch (NumberFormatException ignored) { parsed = 0; }
            if (path.equals("item.count")) setRewardItem(reward.source, rewardItemId(reward.source), parsed);
            else reward.source.addProperty(path, parsed);
        });
        addRenderableWidget(amount);
    }

    private void addRewardCycleButton(QuestAuthoringSession.RewardDraft reward, int x, int y, int width, String key, String fallback, List<String> values) {
        String current = jsonString(reward.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal(friendly(current))));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                reward.source.addProperty(key, values.get((index + 1) % values.size()));
                rebuildWidgets();
            });
        }));
    }

    private void closeRewardEditor(boolean nested) {
        authoring.rewardEditorError = "";
        closePicker();
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
        rebuildWidgets();
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

    private void addNestedRewardWidgets() {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        List<QuestAuthoringSession.RewardDraft> rewards = nestedRewards(authoring.editingReward);
        int end = Math.min(rewards.size(), authoring.nestedRewardScroll + 4);
        for (int index = authoring.nestedRewardScroll; index < end; index++) {
            int nestedIndex = index;
            int rowY = top + 42 + (index - authoring.nestedRewardScroll) * 42;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(180, 34);
                widget.withRenderer(WidgetRenderers.text(Component.literal(rewards.get(nestedIndex).id + "  ·  " + rewardChoice(rewards.get(nestedIndex)).label)));
                widget.withCallback(() -> {
                    authoring.editingNestedRewardIndex = nestedIndex;
                    authoring.editingNestedReward = rewards.get(nestedIndex).copy();
                    modalHost.open(QuestModalHost.Modal.NESTED_REWARD_EDITOR);
                    rebuildWidgets();
                });
                widget.active = isRewardEditable(rewards.get(nestedIndex));
                widget.withTooltip(Component.literal(widget.active ? "Edit choice" : unavailableReason(EditorTypeRegistry.Kind.REWARD, rewards.get(nestedIndex).type)));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 200, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, -1));
                widget.active = nestedIndex > 0;
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 228, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                widget.withCallback(() -> moveNestedReward(nestedIndex, 1));
                widget.active = nestedIndex < rewards.size() - 1;
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 256, rowY + 5).withSize(24, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("×")));
                widget.withCallback(() -> {
                    List<QuestAuthoringSession.RewardDraft> updated = nestedRewards(authoring.editingReward);
                    updated.remove(nestedIndex);
                    setNestedRewards(authoring.editingReward, updated);
                    authoring.nestedRewardScroll = Math.min(authoring.nestedRewardScroll, Math.max(0, updated.size() - 4));
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete choice"));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add choice")));
            widget.withCallback(() -> {
                if (modalHost.isNestedRewardChooserOpen()) modalHost.close();
                else modalHost.open(QuestModalHost.Modal.NESTED_REWARD_CHOOSER);
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 148, top + 218).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Done")));
            widget.withCallback(() -> {
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
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
        rebuildWidgets();
    }

    private int taskListCapacity() {
        return Math.max(1, (height - 79) / 48);
    }

    private int maxCreateTaskScroll() {
        return Math.max(0, authoring.tasks.size() + 1 - taskListCapacity());
    }

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
            if (isTaskEditable(task)) {
                String error = validateTaskDraft(task.copy(), index);
                if (!error.isEmpty()) return "Task '" + task.id + "': " + error;
            }
        }
        return "";
    }

    private void updateCreateConfirmButton() {
        if (createConfirmButton != null) createConfirmButton.active = validCreateQuestDraft();
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
        drawMinimap(graphics, surface, mouseX, mouseY);
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
                    drawTaskEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER && !modalHost.showsNestedTasks()) drawTaskEditorForeground(graphics);
                }
                if (rewardModal) {
                    drawRewardEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER) drawRewardModalForeground(graphics, mouseX, mouseY);
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
                    if (modalHost.showsNestedTasks()) drawNestedTasksForeground(graphics, mouseX, mouseY);
                    else drawTaskEditorForeground(graphics);
                }
                if (rewardModal) drawRewardModalForeground(graphics, mouseX, mouseY);
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
                Component.literal(linkSourceId == null
                    ? "Link: select prerequisite"
                    : "Link: select dependent"),
                sidebarWidth() + 112,
                10,
                0xFF9FDFFF,
                false
            );
        }
        drawChapterScrollbar(graphics);
        if (authoring.open) drawCreateQuestDock(graphics, mouseX, mouseY);
        else if (detailsOpen) drawDetails(graphics, mouseX, mouseY);
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

    private void drawMinimap(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface,
        int mouseX,
        int mouseY
    ) {
        QuestMinimap.MapBounds mapBounds = minimapBounds();
        if (mapBounds == null) return;

        QuestGraphLayout.WorldBounds worldBounds = surface.worldBounds(16);
        QuestMinimap.Mapping mapping = QuestMinimap.mapping(worldBounds, mapBounds);
        int background = 0xE820242B;
        int header = 0xFF303640;
        int border = ClientThemeLoader.active().genericControls().accent();
        int text = ClientThemeLoader.active().genericControls().text();

        graphics.enableScissor(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.maxY());
        graphics.fill(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.maxY(), background);
        graphics.fill(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.contentY(), header);
        graphics.text(font, Component.literal("Map"), mapBounds.x() + 4, mapBounds.y() + 2, text, false);
        graphics.text(
            font,
            Component.literal("⋮"),
            mapBounds.maxX() - 9,
            mapBounds.y() + 1,
            text,
            false
        );

        for (ClientQuest quest : visibleQuests()) {
            QuestSurfaceLayout.Node child = surface.find(quest.definition.id()).orElse(null);
            if (child == null || !quest.definition.settings().showDependencyArrow()) continue;
            QuestGraphLayout.Point childCenter = questCenter(quest);
            QuestGraphLayout.Point childPoint = QuestMinimap.worldToMap(
                mapping,
                childCenter.x(),
                childCenter.y()
            );
            for (String dependency : quest.definition.dependencies()) {
                QuestSurfaceLayout.Node parent = surface.find(dependency).orElse(null);
                if (parent == null) continue;
                QuestGraphLayout.Point parentCenter = questCenter(questById(dependency));
                QuestGraphLayout.Point parentPoint = QuestMinimap.worldToMap(
                    mapping,
                    parentCenter.x(),
                    parentCenter.y()
                );
                drawMinimapLine(graphics, parentPoint, childPoint, 0xAA9AA4B2);
            }
        }

        for (ClientQuest quest : visibleQuests()) {
            QuestSurfaceLayout.Node node = surface.find(quest.definition.id()).orElse(null);
            if (node == null) continue;
            QuestGraphLayout.Point center = questCenter(quest);
            QuestGraphLayout.Point point = QuestMinimap.worldToMap(
                mapping,
                center.x(),
                center.y()
            );
            int markSize = node.minimapMarkSize();
            int x = (int) Math.round(point.x()) - markSize / 2;
            int y = (int) Math.round(point.y()) - markSize / 2;
            graphics.fill(x, y, x + markSize, y + markSize, nodeStateColor(quest));
            if (quest.definition.id().equals(selectedQuestId)) {
                graphics.outline(x - 2, y - 2, markSize + 4, markSize + 4, 0xFFFFFFFF);
            }
        }

        QuestMinimap.MapBounds viewport = QuestMinimap.viewportRectangle(
            mapping,
            QuestGraphLayout.visibleWorld(graphCanvasBounds(), graphViewport.state())
        );
        if (viewport != null) {
            graphics.fill(
                viewport.x(),
                viewport.y(),
                viewport.maxX(),
                viewport.maxY(),
                0x332E9FE6
            );
            graphics.outline(
                viewport.x(),
                viewport.y(),
                viewport.width(),
                viewport.height(),
                0xDDFFFFFF
            );
        }
        graphics.disableScissor();
        graphics.outline(mapBounds.x(), mapBounds.y(), mapBounds.width(), mapBounds.height(), border);
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
        clearMinimapTransientState();
        rebuildWidgets();
    }

    private static void drawMinimapLine(
        GuiGraphicsExtractor graphics,
        QuestGraphLayout.Point start,
        QuestGraphLayout.Point end,
        int color
    ) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double length = Math.hypot(dx, dy);
        if (length < 1) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) start.x(), (float) start.y());
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        graphics.fill(0, -1, (int) Math.ceil(length), 1, color);
        graphics.pose().popMatrix();
    }

    private void addDiagnosticsModalWidgets() {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 330, top + 264).withSize(96, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Close")));
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
        graphics.text(font, Component.literal("Validation diagnostics"), left + 12, top + 9, 0xFFFFFFFF, true);
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
        if (diagnostics.isEmpty()) graphics.text(font, Component.literal("No diagnostics reported."), left + 12, top + 42, 0xFFB8C0CC, false);
        else if (maxScroll > 0) graphics.text(font, Component.literal("Scroll for more"), left + 12, top + 270, 0xFF8893A3, false);
    }

    private void drawRawInspector(GuiGraphicsExtractor graphics) {
        int inspectorWidth = Math.min(480, width - 32);
        int inspectorHeight = Math.min(280, height - 48);
        int left = (width - inspectorWidth) / 2;
        int top = (height - inspectorHeight) / 2;
        graphics.fill(0, 0, width, height, 0xCC000000);
        graphics.fill(left, top, left + inspectorWidth, top + inspectorHeight, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + inspectorWidth - 1, top + 28, 0xFF303640);
        graphics.text(font, Component.literal(rawInspectorTitle + " (read-only)"), left + 12, top + 9, 0xFFFFFFFF, true);
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
            EditBox id = new EditBox(font, left + 250, y, 100, 18, Component.literal("Quest ID"));
            id.setValue(entry.id() == null ? "" : entry.id());
            id.setResponder(value -> {
                importController.changeId(entry.key(), value);
                updateImportMessage();
            });
            importIdFields.put(entry.key(), id);
            addRenderableWidget(id);
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 354, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Details")));
                widget.active = !entry.diagnostics().isEmpty();
                widget.withCallback(() -> openImportDiagnostics(entry.key()));
                widget.withTooltip(Component.literal("View every diagnostic for this file"));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 420, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Remove")));
                widget.withCallback(() -> removeImportFile(entry.key()));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(this::cancelImport);
        }));
        if (!importController.batchDiagnostics().isEmpty()) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 120, top + 304).withSize(120, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Batch details")));
            widget.withCallback(this::openBatchDiagnostics);
            widget.withTooltip(Component.literal("View batch-level server diagnostics"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 388, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Import")));
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
        graphics.text(font, Component.literal("Import quests"), left + 12, top + 9, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Each file is checked independently; Import is all-or-nothing."), left + 12, top + 30, 0xFFB8C0CC, false);
        if (!importController.batchDiagnostics().isEmpty()) {
            long errors = importController.batchDiagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            graphics.text(font, Component.literal("Batch rejected: " + errors + " error(s) — Batch details"), left + 12, top + 42, 0xFFFF9999, false);
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

    private void drawCreateQuestDock(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = width - detailsWidth() + 12;
        if (createQuestTab == DetailTab.OVERVIEW) {
        } else if (createQuestTab == DetailTab.TASKS) {
            drawDraftTasks(graphics);
        } else if (createQuestTab == DetailTab.REWARDS) {
            drawDraftRewards(graphics);
        } else {
            graphics.textWithWordWrap(
                font,
                Component.literal(createQuestTab.label + " will be implemented in a later editor iteration."),
                x,
                42,
                detailsWidth() - 24,
                0xFFB8C0CC,
                false
            );
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
        if (authoring.rewards.isEmpty()) graphics.text(font, Component.literal("No rewards yet"), x, 34, 0xFF8E98A6, false);
    }

    private void drawRewardChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean nested) {
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? REWARD_CHOICES.stream().filter(choice -> !choice.type.equals("theseus:selectable")).toList()
            : REWARD_CHOICES;
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int index = 0; index < choices.size(); index++) {
            RewardChoice choice = choices.get(index);
            int rowY = top + 2 + index * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 && mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            graphics.item(new ItemStack(choice.icon), left + 4, rowY + 5);
            graphics.text(font, Component.literal(choice.label), left + 24, rowY + 9, 0xFFFFFFFF, false);
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
                Component.literal("No tasks yet"),
                x,
                34,
                0xFF8E98A6,
                false
            );
        }
    }

    private void drawTaskChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawTaskChooser(graphics, mouseX, mouseY, false);
    }

    private void drawNestedTaskChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawTaskChooser(graphics, mouseX, mouseY, true);
    }

    private void drawTaskChooser(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        boolean nested
    ) {
        int left = nested ? taskEditorLeft() + 14 : width - detailsWidth() + 16;
        int top = nested ? taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, TASK_CHOICES.size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int visible = 0; visible < visibleCount; visible++) {
            TaskChoice choice = TASK_CHOICES.get(taskChooserScroll + visible);
            int rowY = top + 2 + visible * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 &&
                mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            graphics.item(new ItemStack(choice.icon), left + 4, rowY + 5);
            graphics.text(
                font,
                Component.literal(choice.label),
                left + 24,
                rowY + (choice.implemented ? 9 : 3),
                choice.implemented ? 0xFFFFFFFF : 0xFF9AA2AE,
                false
            );
            if (!choice.implemented) graphics.text(
                font,
                Component.literal("Not yet implemented"),
                left + 24,
                rowY + 14,
                0xFF707987,
                false
            );
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
            Component.literal(switch (picker) {
                case ICON -> "Choose item";
                case ENTITY -> "Choose entity";
                default -> "Choose background";
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
        graphics.text(font, Component.literal("Rich description"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Markdown"), left + 12, top + 52, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Player preview"), previewX, top + 52, 0xFFB8C0CC, false);
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
                .map(this::taskDisplayLabel).findFirst().orElse(id + " (missing)");
        }
        return authoring.rewards.stream().filter(reward -> reward.id.equals(id))
            .map(this::rewardDisplayLabel).findFirst().orElse(id + " (missing)");
    }

    private void drawPickerContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (picker == Picker.ICON) drawItemPicker(graphics, mouseX, mouseY);
        else if (picker == Picker.ENTITY) drawEntityPicker(graphics, mouseX, mouseY);
        else drawBackgroundPicker(graphics, mouseX, mouseY);
    }

    private void drawTaskEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = taskEditorLeft();
        int top = taskEditorTop();
        graphics.fill(left, top, left + 260, top + 300, 0xFF20242B);
        graphics.outline(left, top, 260, 300, 0xFF8A929F);
    }

    private void drawRewardEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        graphics.fill(left, top, left + 300, top + 280, 0xFF20242B);
        graphics.outline(left, top, 300, 280, 0xFF8A929F);
    }

    private void drawDeleteQuestConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.literal("Delete quest?"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.literal("This deletes the quest file and resets its player progress."), left + 12, top + 32, 216, 0xFFFFAAAA, false);
    }

    private void drawProgressResetConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 280) / 2;
        int top = (height - 142) / 2;
        graphics.fill(left, top, left + 280, top + 142, 0xFF20242B);
        graphics.outline(left, top, 280, 142, 0xFFFF6B6B);
        QuestModalHost.ProgressResetTarget target = progressResetTarget;
        String title = target == null ? "Reset progress?" : switch (target.scope()) {
            case "quest" -> "Reset quest progress?";
            case "task" -> "Reset task progress?";
            case "reward" -> "Reset reward progress?";
            default -> "Reset progress?";
        };
        String detail = target == null ? "No reset target is selected." : switch (target.scope()) {
            case "quest" -> "Clear all task and reward progress for '" + target.questTitle() + "'? The quest pin will be preserved.";
            case "task" -> "Clear progress for task '" + target.displayLabel() + "' (" + target.entryId() + ") in '" + target.questTitle() + "'?";
            case "reward" -> "Clear the claim for reward '" + target.displayLabel() + "' (" + target.entryId() + ") in '" + target.questTitle() + "'?";
            default -> "Clear the selected progress?";
        };
        graphics.text(font, Component.literal(title), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.literal(detail + " This affects the current player."), left + 12, top + 34, 256, 0xFFFFC4C4, false);
    }

    private void drawDeleteTaskConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.literal("Delete task?"), left + 12, top + 12, 0xFFFFFFFF, true);
        String id = authoring.taskDeleteConfirmation >= 0 && authoring.taskDeleteConfirmation < authoring.tasks.size()
            ? authoring.tasks.get(authoring.taskDeleteConfirmation).id : "this task";
        graphics.textWithWordWrap(font, Component.literal("Delete '" + id + "' and its configuration?"), left + 12, top + 34, 216, 0xFFFFAAAA, false);
    }

    private void drawDiscardConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 260) / 2;
        int top = (height - 116) / 2;
        graphics.fill(left, top, left + 260, top + 116, 0xFF20242B);
        graphics.outline(left, top, 260, 116, 0xFF8A929F);
        graphics.text(font, Component.literal("Discard unsaved changes?"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.literal("The quest draft has changes that have not been saved."), left + 12, top + 34, 236, 0xFFFFCC88, false);
    }

    private void drawChapterEditor(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 280) / 2;
        int top = chapterEditorTop();
        graphics.fill(left, top, left + 280, top + 250, 0xFF20242B);
        graphics.outline(left, top, 280, 250, 0xFF8A929F);
        graphics.text(font, Component.literal(chapterEditorOriginal == null ? "Create chapter" : "Edit chapter"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Name"), left + 14, top + 36, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Chapter icon"), left + 56, top + 88, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Background"), left + 14, top + 114, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Background opacity"), left + 14, top + 146, 0xFFB8C0CC, false);
        if (!chapterEditorError.isEmpty()) graphics.text(font, Component.literal(chapterEditorError), left + 14, top + 185, 0xFFFF7777, false);
    }

    private void drawPasteIdPrompt(GuiGraphicsExtractor graphics) {
        int left = (width - 280) / 2;
        int top = (height - 130) / 2;
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(left, top, left + 280, top + 130, 0xFF20242B);
        graphics.outline(left, top, 280, 130, 0xFF8A929F);
        graphics.text(font, Component.literal("Paste quest"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Choose the ID for the cloned quest."), left + 14, top + 34, 0xFFB8C0CC, false);
    }

    private void drawRewardModalForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (authoring.editingNestedReward != null) drawRewardEditorForeground(graphics, authoring.editingNestedReward, true);
        else if (modalHost.showsNestedRewards()) drawNestedRewardsForeground(graphics, mouseX, mouseY);
        else drawRewardEditorForeground(graphics, authoring.editingReward, false);
    }

    private void drawRewardEditorForeground(GuiGraphicsExtractor graphics, QuestAuthoringSession.RewardDraft reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        RewardChoice choice = rewardChoice(reward);
        graphics.item(new ItemStack(choice.icon), left + 14, top + 10);
        graphics.text(font, Component.literal((nested ? "Edit choice: " : "Edit ") + choice.label), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("ID"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Title override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Icon override"), left + REWARD_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftRewardIcon(graphics, reward, left + 23, top + 105);
        switch (reward.type) {
            case "theseus:xp" -> {
                graphics.text(font, Component.literal("Amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Unit"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(font, Component.literal("Item"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:loottable" -> graphics.text(font, Component.literal("Loot table"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:command" -> graphics.text(font, Component.literal("Command"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "theseus:selectable" -> {
                graphics.text(font, Component.literal("Selection amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Nested rewards"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            default -> { }
        }
        if (!authoring.rewardEditorError.isEmpty()) graphics.textWithWordWrap(font, Component.literal(authoring.rewardEditorError), left + 14, top + 210, 272, 0xFFFF7777, false);
    }

    private void drawNestedRewardsForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        if (nestedRewards(authoring.editingReward).isEmpty()) graphics.text(font, Component.literal("No choices yet"), left + 14, top + 48, 0xFF8E98A6, false);
        if (modalHost.isNestedRewardChooserOpen()) drawRewardChooser(graphics, mouseX, mouseY, true);
    }

    private void drawTaskEditorForeground(GuiGraphicsExtractor graphics) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        TaskChoice choice = taskChoice(authoring.editingTask);
        graphics.item(new ItemStack(choice.icon), left + 14, top + 10);
        graphics.text(font, Component.literal("Edit " + choice.label), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("ID"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Title override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Icon override"), left + TASK_ICON_LABEL_X, top + 108, 0xFFB8C0CC, false);
        renderDraftTaskIcon(graphics, authoring.editingTask, left + 23, top + 105);
        switch (authoring.editingTask.type) {
            case "theseus:dummy" -> {
                graphics.text(font, Component.literal("Trigger value"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Description"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:item" -> {
                graphics.text(font, Component.literal("Item or #tag"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Collection"), left + 104, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:xp" -> {
                graphics.text(font, Component.literal("Amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Unit"), left + 104, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Collection"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:kill_entity" -> {
                graphics.text(font, Component.literal("Entity"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "theseus:advancement" -> taskFieldLabel(graphics, left, top, "Advancement IDs (comma separated)", null);
            case "theseus:biome" -> taskFieldLabel(graphics, left, top, "Biome or #tag", null);
            case "theseus:block_interaction" -> {
                taskFieldLabel(graphics, left, top, "Block or #tag", "Component/data predicate (JSON)");
                graphics.text(font, Component.literal("Block state predicate (JSON)"), left + 14, top + 209, 0xFFB8C0CC, false);
            }
            case "theseus:changed_dimension" -> {
                graphics.text(font, Component.literal("From dimension (optional)"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("To dimension (optional)"), left + 136, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:check" -> taskFieldLabel(graphics, left, top, "Player data predicate (JSON)", null);
            case "theseus:composite" -> {
                graphics.text(font, Component.literal("Required tasks"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Nested tasks"), left + 104, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:entity_interaction" -> taskFieldLabel(graphics, left, top, "Entity or #tag", "Component/data predicate (JSON)");
            case "theseus:item_interaction", "theseus:item_use" -> taskFieldLabel(graphics, left, top, "Item or #tag", "Component/data predicate (JSON)");
            case "theseus:location" -> taskFieldLabel(graphics, left, top, "Location predicate (JSON)", "Description");
            case "theseus:recipe" -> taskFieldLabel(graphics, left, top, "Recipe IDs (comma separated)", null);
            case "theseus:stat" -> {
                graphics.text(font, Component.literal("Statistic ID"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Target"), left + 170, top + 131, 0xFFB8C0CC, false);
            }
            case "theseus:structure" -> taskFieldLabel(graphics, left, top, "Structure or #tag", null);
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

    private void drawNestedTasksForeground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY
    ) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        if (nestedTasks(authoring.editingTask).isEmpty()) {
            graphics.text(
                font,
                Component.literal("No child tasks yet"),
                left + 14,
                top + 48,
                0xFF8E98A6,
                false
            );
        }
        if (modalHost.isNestedTaskChooserOpen()) drawNestedTaskChooser(graphics, mouseX, mouseY);
    }

    private void taskFieldLabel(GuiGraphicsExtractor graphics, int left, int top, String first, String second) {
        graphics.text(font, Component.literal(first), left + 14, top + 131, 0xFFB8C0CC, false);
        if (second != null) graphics.text(font, Component.literal(second), left + 14, top + 170, 0xFFB8C0CC, false);
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

    private static JsonObject taskRoot(QuestAuthoringSession.TaskDraft task) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject tasks = new JsonObject();
        tasks.add(task.id, task.source.deepCopy());
        root.add("tasks", tasks);
        return root;
    }

    private String validateTaskDraft(QuestAuthoringSession.TaskDraft task, int editedIndex) {
        if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) return "ID may only contain lowercase letters, numbers, ., _, and -.";
        List<QuestAuthoringSession.TaskDraft> peers = authoring.taskEditorParents.isEmpty() ? authoring.tasks : nestedTasks(authoring.taskEditorParents.getLast());
        for (int index = 0; index < peers.size(); index++) {
            if (index != editedIndex && peers.get(index).id.equals(task.id)) return "Another task already uses this ID.";
        }
        String structuredError = normalizeStructuredTaskFields(task);
        if (!structuredError.isEmpty()) return structuredError;
        String registryError = RegistryValidation.validate("editor", taskRoot(task), QuestScreen::clientContainsRegistryTarget).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst().orElse("");
        if (!registryError.isEmpty()) return registryError;
        if (task.type.equals("theseus:dummy") && jsonString(task.source, "value", "").isBlank()) return "Trigger value is required.";
        if (List.of("theseus:item", "theseus:xp", "theseus:kill_entity", "theseus:composite").contains(task.type)
            && jsonInt(task.source, "amount", 0) < 1) return "Amount must be at least 1.";
        if (task.type.equals("theseus:stat") && jsonInt(task.source, "target", 0) < 1) return "Target must be at least 1.";
        if (List.of("theseus:item", "theseus:item_interaction", "theseus:item_use").contains(task.type)) {
            String item = registryValueString(task.source, "item", "");
            if (!validIdentifier(item.startsWith("#") ? item.substring(1) : item)) return "Enter a valid item or #tag identifier.";
            if (!item.startsWith("#") && !registryContains(BuiltInRegistries.ITEM, item)) return "That item does not exist.";
        }
        if (List.of("theseus:kill_entity", "theseus:entity_interaction").contains(task.type)) {
            String entity = registryValueString(task.source, "entity", "");
            String id = entity.startsWith("#") ? entity.substring(1) : entity;
            if (!validIdentifier(id) || (!entity.startsWith("#") && !registryContains(BuiltInRegistries.ENTITY_TYPE, entity))) return "That entity does not exist.";
        }
        String identifierError = validateTaskIdentifiers(task);
        if (!identifierError.isEmpty()) return identifierError;
        return QuestDefinition.parse("editor", taskRoot(task)).issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    private static String normalizeStructuredTaskFields(QuestAuthoringSession.TaskDraft task) {
        List<String> keys = switch (task.type) {
            case "theseus:check", "theseus:entity_interaction",
                 "theseus:item_interaction", "theseus:item_use" -> List.of("components");
            case "theseus:block_interaction" -> List.of("components", "state");
            case "theseus:location" -> List.of("predicate");
            case "theseus:composite" -> List.of("tasks");
            default -> List.of();
        };
        for (String key : keys) {
            JsonElement value = task.source.get(key);
            if (value == null) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                try {
                    value = JsonParser.parseString(value.getAsString());
                    task.source.add(key, value);
                } catch (RuntimeException exception) {
                    return friendly(key) + " must be valid JSON.";
                }
            }
            if (!value.isJsonObject()) return friendly(key) + " must be a JSON object.";
        }
        return "";
    }

    private static String validateTaskIdentifiers(QuestAuthoringSession.TaskDraft task) {
        List<String> scalarKeys = switch (task.type) {
            case "theseus:biome" -> List.of("biomes");
            case "theseus:block_interaction" -> List.of("block");
            case "theseus:changed_dimension" -> List.of("from", "to");
            case "theseus:stat" -> List.of("stat");
            case "theseus:structure" -> List.of("structures");
            default -> List.of();
        };
        for (String key : scalarKeys) {
            String value = registryValueString(task.source, key, "");
            if (task.type.equals("theseus:changed_dimension") && value.isBlank()) continue;
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (!validIdentifier(id)) return friendly(key) + " must be a valid identifier" + (key.equals("from") || key.equals("to") ? " or blank." : ".");
        }
        for (String key : List.of("advancements", "recipes")) {
            if (!task.source.has(key)) continue;
            JsonElement values = task.source.get(key);
            if (!values.isJsonArray() || values.getAsJsonArray().isEmpty()) return friendly(key) + " must contain at least one identifier.";
            for (JsonElement value : values.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !validIdentifier(value.getAsString())) return friendly(key) + " contains an invalid identifier.";
            }
        }
        return "";
    }

    private String validateRewardDraft(QuestAuthoringSession.RewardDraft reward, boolean nested) {
        if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) return "ID may only contain lowercase letters, numbers, ., _, and -.";
        List<QuestAuthoringSession.RewardDraft> peers = nested ? nestedRewards(authoring.editingReward) : authoring.rewards;
        int editedIndex = nested ? authoring.editingNestedRewardIndex : authoring.editingRewardIndex;
        for (int index = 0; index < peers.size(); index++) {
            if (index != editedIndex && peers.get(index).id.equals(reward.id)) return "Another reward already uses this ID.";
        }
        if (nested && reward.type.equals("theseus:selectable")) return "Selectable rewards cannot contain selectable rewards.";
        if (reward.type.equals("theseus:item")) {
            if (!registryContains(BuiltInRegistries.ITEM, rewardItemId(reward.source))) return "That item does not exist.";
            if (rewardItemCount(reward.source) < 1) return "Amount must be at least 1.";
        }
        if (reward.type.equals("theseus:xp") && jsonInt(reward.source, "amount", 0) < 1) return "Amount must be at least 1.";
        if (reward.type.equals("theseus:loottable") && !validIdentifier(jsonString(reward.source, "loot_table", ""))) return "Enter a valid loot table identifier.";
        if (reward.type.equals("theseus:command") && jsonString(reward.source, "command", "").isBlank()) return "Command is required.";
        if (reward.type.equals("theseus:selectable")) {
            List<QuestAuthoringSession.RewardDraft> choices = nestedRewards(reward);
            int amount = jsonInt(reward.source, "amount", 0);
            if (choices.isEmpty()) return "Add at least one selectable reward choice.";
            if (amount < 1 || amount > choices.size()) return "Selection amount must be between 1 and the number of choices.";
            if (choices.stream().anyMatch(choice -> choice.type.equals("theseus:selectable"))) return "Selectable rewards cannot contain selectable rewards.";
        }
        QuestDefinition parsed = QuestDefinition.parse("editor", rewardRoot(reward));
        return parsed.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    private static JsonObject rewardRoot(QuestAuthoringSession.RewardDraft reward) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject rewards = new JsonObject();
        rewards.add(reward.id, reward.source.deepCopy());
        root.add("rewards", rewards);
        return root;
    }

    private static String rewardItemId(JsonObject source) {
        if (!source.has("item")) return "minecraft:stone";
        if (source.get("item").isJsonObject()) return jsonString(source.getAsJsonObject("item"), "id", "minecraft:stone");
        return source.get("item").getAsString();
    }

    private static int rewardItemCount(JsonObject source) {
        return source.has("item") && source.get("item").isJsonObject()
            ? jsonInt(source.getAsJsonObject("item"), "count", 1) : 1;
    }

    private static void setRewardItem(JsonObject source, String id, int count) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("count", count);
        source.add("item", item);
    }

    private static List<QuestAuthoringSession.RewardDraft> nestedRewards(QuestAuthoringSession.RewardDraft parent) {
        List<QuestAuthoringSession.RewardDraft> rewards = new ArrayList<>();
        if (parent == null || !parent.source.has("rewards") || !parent.source.get("rewards").isJsonObject()) return rewards;
        parent.source.getAsJsonObject("rewards").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                JsonObject source = entry.getValue().getAsJsonObject();
                rewards.add(new QuestAuthoringSession.RewardDraft(entry.getKey(), jsonString(source, "type", "theseus:item"), source.deepCopy()));
            }
        });
        return rewards;
    }

    private static void setNestedRewards(QuestAuthoringSession.RewardDraft parent, List<QuestAuthoringSession.RewardDraft> rewards) {
        JsonObject object = new JsonObject();
        rewards.forEach(reward -> object.add(reward.id, reward.source.deepCopy()));
        parent.source.add("rewards", object);
    }

    private static List<QuestAuthoringSession.TaskDraft> nestedTasks(QuestAuthoringSession.TaskDraft parent) {
        List<QuestAuthoringSession.TaskDraft> tasks = new ArrayList<>();
        if (parent == null || !parent.source.has("tasks") || !parent.source.get("tasks").isJsonObject()) return tasks;
        parent.source.getAsJsonObject("tasks").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject source = entry.getValue().getAsJsonObject();
            tasks.add(new QuestAuthoringSession.TaskDraft(entry.getKey(), jsonString(source, "type", "theseus:unknown"), source.deepCopy()));
        });
        return tasks;
    }

    private static void setNestedTasks(QuestAuthoringSession.TaskDraft parent, List<QuestAuthoringSession.TaskDraft> tasks) {
        JsonObject object = new JsonObject();
        tasks.forEach(task -> object.add(task.id, task.source.deepCopy()));
        parent.source.add("tasks", object);
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

    private static boolean validIdentifier(String value) {
        return value != null && value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String registryValueString(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive()) return value.getAsString();
        if (!value.isJsonObject()) return fallback;
        JsonObject registryValue = value.getAsJsonObject();
        if (registryValue.has("tag")) return "#" + jsonString(registryValue, "tag", "");
        return jsonString(registryValue, "id", fallback);
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static JsonArray stringArray(String values) {
        JsonArray array = new JsonArray();
        for (String value : values.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) array.add(trimmed);
        }
        return array;
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

    private static JsonObject defaultLocationPredicate() {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("dimension", "minecraft:overworld");
        return predicate;
    }

    private static void setOptionalString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value);
    }

    private static String friendly(String value) {
        String text = value.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private boolean isTaskEditable(QuestAuthoringSession.TaskDraft task) {
        return editorResolution(EditorTypeRegistry.Kind.TASK, task.type).editable();
    }

    private boolean isRewardEditable(QuestAuthoringSession.RewardDraft reward) {
        return editorResolution(EditorTypeRegistry.Kind.REWARD, reward.type).editable();
    }

    private static TaskChoice taskChoice(QuestAuthoringSession.TaskDraft task) {
        return TASK_CHOICES.stream().filter(choice -> choice.type.equals(task.type))
            .findFirst().orElse(TASK_CHOICES.getFirst());
    }

    private static RewardChoice rewardChoice(QuestAuthoringSession.RewardDraft reward) {
        return REWARD_CHOICES.stream().filter(choice -> choice.type.equals(reward.type))
            .findFirst().orElse(REWARD_CHOICES.getFirst());
    }

    private String taskDisplayLabel(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task) ? taskChoice(task).label : "Unsupported: " + task.type;
    }

    private String rewardDisplayLabel(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward) ? rewardChoice(reward).label : "Unsupported: " + reward.type;
    }

    private Item taskDisplayIcon(QuestAuthoringSession.TaskDraft task) {
        return isTaskEditable(task) ? taskChoice(task).icon : Items.BARRIER;
    }

    private Item rewardDisplayIcon(QuestAuthoringSession.RewardDraft reward) {
        return isRewardEditable(reward) ? rewardChoice(reward).icon : Items.BARRIER;
    }

    private EditorTypeRegistry.Resolution editorResolution(EditorTypeRegistry.Kind kind, String type) {
        Set<String> types = kind == EditorTypeRegistry.Kind.TASK ? serverTaskTypes : serverRewardTypes;
        return editorTypes().resolve(kind, type, types);
    }

    private String unavailableReason(EditorTypeRegistry.Kind kind, String type) {
        return switch (editorResolution(kind, type).availability()) {
            case EXECUTABLE_READ_ONLY -> "The server can execute '" + type + "', but this client has no editable descriptor";
            case UNAVAILABLE_ON_SERVER -> "This client can edit '" + type + "', but the server does not provide it";
            case UNKNOWN_CONFIGURATION -> "Unknown " + kind.name().toLowerCase(java.util.Locale.ROOT) + " type '" + type + "'";
            case EXECUTABLE_EDITABLE -> "Editable";
        };
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

    private void drawClippedDetailText(
        GuiGraphicsExtractor graphics,
        String value,
        int x,
        int y,
        int maxWidth,
        int color
    ) {
        String text = value == null ? "" : value;
        drawClippedText(graphics, text, x, y, maxWidth, color);
        if (font.width(text) > maxWidth) {
            detailTextBounds.add(new DetailTextBounds(
                new UiBounds(x, y - 2, Math.max(1, maxWidth), font.lineHeight + 4),
                text
            ));
        }
    }

    private void drawDetailTextTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        DetailTextBounds hovered = detailTextBounds.stream()
            .filter(value -> value.bounds().contains(mouseX, mouseY))
            .reduce((first, second) -> second)
            .orElse(null);
        if (hovered == null) return;
        int maximumWidth = Math.min(280, Math.max(120, width - 24));
        Component text = Component.literal(hovered.text());
        int tooltipWidth = Math.min(maximumWidth, Math.max(48, Math.min(font.width(hovered.text()) + 12, maximumWidth)));
        int tooltipHeight = font.wordWrapHeight(text, tooltipWidth - 12) + 10;
        int x = Math.max(6, Math.min(mouseX + 10, width - tooltipWidth - 6));
        int y = Math.max(6, Math.min(mouseY + 10, height - tooltipHeight - 6));
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xF020242B);
        graphics.outline(x, y, tooltipWidth, tooltipHeight, 0xFF8A929F);
        graphics.textWithWordWrap(font, text, x + 6, y + 5, tooltipWidth - 12, 0xFFFFFFFF, false);
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

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        detailTextBounds.clear();
        lockQuestBounds.clear();
        taskCardBounds.clear();
        rewardCardBounds.clear();
        recipeViewerTargets.clear();
        int detailsWidth = detailsWidth();
        int panelLeft = width - detailsWidth;
        int x = panelLeft + 12;
        int contentWidth = detailsWidth - 24;
        ClientQuest quest = selected();
        if (quest == null) {
            graphics.text(
                font,
                Component.literal("Select a quest"),
                x,
                42,
                0xFFAAAAAA,
                false
            );
            return;
        }
        graphics.textWithWordWrap(
            font,
            Component.literal(quest.definition.title()),
            x,
            38,
            contentWidth,
            0xFFFFFFFF,
            true
        );
        int y =
            50 +
            font.wordWrapHeight(
                Component.literal(quest.definition.title()),
                contentWidth
            );
        if (!quest.definition.subtitle().isBlank()) {
            graphics.textWithWordWrap(
                font,
                Component.literal(quest.definition.subtitle()),
                x,
                y,
                contentWidth,
                0xFFB8C0CC
            );
            y +=
                font.wordWrapHeight(
                    Component.literal(quest.definition.subtitle()),
                    contentWidth
                ) + 6;
        }
        graphics.horizontalLine(x, x + contentWidth, y, 0xFF49515E);
        int contentTop = y + 7;
        int contentBottom = height - 38;
        detailContentTop = contentTop;
        detailContentBottom = contentBottom;
        graphics.enableScissor(panelLeft + 1, contentTop, width, contentBottom);
        int contentHeight = switch (detailTab) {
            case OVERVIEW -> drawOverview(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
            case TASKS -> drawTasks(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
            case REWARDS -> drawRewards(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
        };
        graphics.disableScissor();
        detailMaxScroll = Math.max(
            0,
            contentHeight - (contentBottom - contentTop)
        );
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        drawDetailTextTooltip(graphics, mouseX, mouseY);
        drawRecipeViewerTooltip(graphics, mouseX, mouseY);
        if (detailTab == DetailTab.OVERVIEW) {
            descriptionInteractions.stream()
                .filter(interaction -> interaction.contains(mouseX, mouseY))
                .findFirst()
                .ifPresent(interaction -> graphics.setTooltipForNextFrame(interaction.tooltip(), mouseX, mouseY));
        }
    }

    private void drawRecipeViewerTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!RecipeViewer.isAvailable()) return;
        recipeViewerTargets.stream()
            .filter(target -> target.contains(mouseX, mouseY))
            .reduce((first, second) -> second)
            .ifPresent(target -> {
                List<Component> tooltip = new ArrayList<>(target.stack().getTooltipLines(
                    Item.TooltipContext.EMPTY,
                    minecraft.player,
                    TooltipFlag.NORMAL
                ));
                tooltip.add(Component.translatable("screen.theseus.recipe_viewer_hint"));
                graphics.setTooltipForNextFrame(
                    font,
                    tooltip,
                    target.stack().getTooltipImage(),
                    mouseX,
                    mouseY
                );
            });
    }

    private void registerRecipeViewerTarget(java.util.Optional<ItemStack> stack, int x, int y) {
        if (stack.isEmpty()) return;
        if (y < detailContentTop || y + 16 > detailContentBottom) return;
        recipeViewerTargets.add(new RecipeViewerTarget(stack.get().copy(), x, y, 16, 16));
    }

    private int drawOverview(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked) y += drawLockedBanner(
            graphics,
            quest,
            x,
            y,
            contentWidth
        );
        int completed = (int) quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) >= task.target()
            )
            .count();
        graphics.text(
            font,
            Component.literal("Quest progress"),
            x,
            y,
            ClientThemeLoader.active().questDetails().summaryTitle(),
            true
        );
        graphics.text(
            font,
            Component.literal(
                completed + "/" + quest.definition.tasks().size()
            ),
            x + contentWidth - 34,
            y,
            0xFFFFFFFF,
            false
        );
        y += 14;
        drawProgressBar(
            graphics,
            x,
            y,
            contentWidth,
            questProgress(quest)
        );
        y += 13;
        descriptionInteractions.clear();
        DescriptionDocument description = DescriptionParser.parse(quest.definition.description());
        QuestDescriptionRenderer.Result rendered = QuestDescriptionRenderer.render(
            graphics, font, description, x, y, contentWidth,
            (kind, id) -> descriptionReference(quest, kind, id)
        );
        descriptionInteractions.addAll(rendered.interactions());
        y += rendered.height();
        y += 4;
        graphics.text(
            font,
            Component.literal("Status"),
            x,
            y,
            0xFFFFD966,
            true
        );
        y += 14;
        graphics.text(
            font,
            Component.literal(status(quest).trim()),
            x,
            y,
            nodeStateColor(quest),
            false
        );
        return y - startY + 18;
    }

    private int drawTasks(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked) y += drawLockedBanner(
            graphics,
            quest,
            x,
            y,
            contentWidth
        );
        List<QuestDefinition.Task> active = quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) < task.target()
            )
            .toList();
        List<QuestDefinition.Task> complete = quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) >= task.target()
            )
            .toList();
        if (!active.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                "In progress",
                active.size(),
                x,
                y,
                contentWidth,
                0xFF4C9AFF
            );
            for (QuestDefinition.Task task : active) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    false
                );
            }
        }
        if (!complete.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                "Completed",
                complete.size(),
                x,
                y + (active.isEmpty() ? 0 : 4),
                contentWidth,
                0xFF55D86A
            );
            for (QuestDefinition.Task task : complete) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    true
                );
            }
        }
        if (active.isEmpty() && complete.isEmpty()) graphics.text(
            font,
            Component.literal("No tasks"),
            x,
            y,
            0xFF9AA1AC,
            false
        );
        return y - startY + 6;
    }

    private int drawLockedBanner(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int width
    ) {
        Map<String, QuestSurfaceLayout.LockState> states = new HashMap<>();
        for (ClientQuest candidate : quests) states.put(candidate.definition.id(),
            new QuestSurfaceLayout.LockState(
                candidate.definition.title(), candidate.complete,
                candidate.definition.display().groups().keySet()
            ));
        QuestSurfaceLayout.LockExplanation explanation = QuestSurfaceLayout.explainLock(
            quest.definition, states, group
        );
        List<Component> lines = explanation.blockers().isEmpty()
            ? List.of(Component.literal(explanation.summary()))
            : explanation.blockers().stream().<Component>map(blocker -> Component.literal(
                (blocker.selectable() ? "Open " : "Complete ") + blocker.label()
            )).toList();
        int textWidth = width - 14;
        int height =
            17 +
            lines
                .stream()
                .mapToInt(line -> font.wordWrapHeight(line, textWidth) + 3)
                .sum();
        graphics.fill(x, y, x + width, y + height, 0xFF302D27);
        graphics.outline(x, y, width, height, 0xFFFFD966);
        graphics.text(
            font,
            Component.literal(explanation.kind() == QuestSurfaceLayout.LockKind.DEPENDENCY
                ? "Locked — prerequisites" : "Locked — policy"),
            x + 7,
            y + 5,
            0xFFFFD966,
            true
        );
        int lineY = y + 16;
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            int lineHeight = font.wordWrapHeight(line, textWidth) + 3;
            graphics.textWithWordWrap(
                font,
                line,
                x + 7,
                lineY,
                textWidth,
                explanation.blockers().isEmpty() || !explanation.blockers().get(index).selectable()
                    ? 0xFFB8C0CC : 0xFF69A7FF
            );
            if (!explanation.blockers().isEmpty() && explanation.blockers().get(index).selectable()) {
                lockQuestBounds.add(new LockQuestBounds(
                    new UiBounds(x + 7, lineY, textWidth, lineHeight),
                    explanation.blockers().get(index).questId()
                ));
            }
            lineY += lineHeight;
        }
        return height + 6;
    }

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

    private int drawRewards(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        rewardChoiceBounds.clear();
        int startY = y;
        if (quest.definition.rewards().isEmpty()) {
            graphics.text(
                font,
                Component.literal("No rewards"),
                x,
                y,
                0xFF9AA1AC,
                false
            );
            return 18;
        }
        y = drawSectionHeading(
            graphics,
            quest.claimed ? "Claimed" : "Quest rewards",
            quest.definition.rewards().size(),
            x,
            y,
            contentWidth,
            quest.claimed ? 0xFF55D86A : 0xFFFFD966
        );
        for (QuestDefinition.Reward reward : quest.definition
            .rewards()
            .values()) {
            boolean rewardClaimed = quest.claimedRewards.contains(reward.id());
            boolean rewardTypeAvailable = isRewardTypeAvailable(reward);
            int border =
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED && !rewardTypeAvailable
                    ? 0xFFE57373
                    : rewardClaimed
                      ? 0xFF55D86A
                      : 0xFF626A76;
            graphics.fill(x, y, x + contentWidth, y + 40, 0xFF30353D);
            graphics.outline(x, y, contentWidth, 40, border);
            rewardCardBounds.add(new RewardCardBounds(
                reward.id(),
                QuestPresentation.rewardTitle(reward),
                new UiBounds(x, y, contentWidth, 40)
            ));
            QuestPresentation.renderRewardIcon(graphics, reward, x + 7, y + 11);
            registerRecipeViewerTarget(QuestPresentation.rewardIconTarget(reward), x + 7, y + 11);
            int textWidth = Math.max(1, contentWidth - 36);
            drawClippedDetailText(
                graphics,
                QuestPresentation.rewardTitle(reward),
                x + 30,
                y + 8,
                textWidth,
                0xFFFFFFFF
            );
            String detail = rewardClaimed ? "Claimed" : switch (reward.kind()) {
                case SELECTABLE -> "Choose up to " + reward.amount();
                case UNSUPPORTED -> rewardTypeAvailable
                    ? "Add-on reward: " + reward.type()
                    : "Not supported by this server: " + reward.type();
                default -> "Amount: " + reward.amount();
            };
            drawClippedDetailText(
                graphics,
                detail,
                x + 30,
                y + 22,
                textWidth,
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED && !rewardTypeAvailable
                    ? 0xFFFFA0A0
                    : 0xFFB8C0CC
            );
            y += 45;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                String selectionKey = quest.definition.id() + "|" + reward.id();
                Set<String> selected = rewardSelections.computeIfAbsent(
                    selectionKey,
                    ignored -> new LinkedHashSet<>()
                );
                for (QuestDefinition.Reward choice : reward
                    .rewards()
                    .values()) {
                    boolean chosen = selected.contains(choice.id());
                    int choiceHeight = 34;
                    graphics.fill(
                        x + 10,
                        y,
                        x + contentWidth,
                        y + choiceHeight,
                        chosen ? 0xFF344637 : 0xFF292E35
                    );
                    graphics.outline(
                        x + 10,
                        y,
                        contentWidth - 10,
                        choiceHeight,
                        chosen ? 0xFF55D86A : 0xFF626A76
                    );
                    QuestPresentation.renderRewardIcon(graphics, choice, x + 16, y + 9);
                    registerRecipeViewerTarget(QuestPresentation.rewardIconTarget(choice), x + 16, y + 9);
                    drawClippedDetailText(
                        graphics,
                        QuestPresentation.rewardTitle(choice),
                        x + 39,
                        y + 7,
                        Math.max(1, contentWidth - 45),
                        0xFFFFFFFF
                    );
                    graphics.text(
                        font,
                        Component.literal(
                            chosen ? "Selected" : "Click to select"
                        ),
                        x + 39,
                        y + 20,
                        chosen ? 0xFF7DE68D : 0xFFADB4BF,
                        false
                    );
                    rewardChoiceBounds.add(
                        new RewardChoiceBounds(
                            selectionKey,
                            choice.id(),
                            new UiBounds(
                                x + 10,
                                y,
                                contentWidth - 10,
                                choiceHeight
                            )
                        )
                    );
                    y += choiceHeight + 4;
                }
                y += 3;
            }
        }
        return y - startY;
    }

    private int drawSectionHeading(
        GuiGraphicsExtractor graphics,
        String title,
        int count,
        int x,
        int y,
        int width,
        int color
    ) {
        boolean completed = title.equals("Completed");
        Identifier left = completed ? HEADING_COMPLETED_LEFT : HEADING_IN_PROGRESS_LEFT;
        Identifier right = completed ? HEADING_COMPLETED_RIGHT : HEADING_IN_PROGRESS_RIGHT;
        int titleWidth = font.width(title) + 14;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left, x, y + 1, titleWidth, 13);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            right,
            x + titleWidth,
            y + 1,
            width - titleWidth,
            13
        );
        graphics.text(
            font,
            Component.literal(title),
            x + 7,
            y + 4,
            0xFFFFFFFF,
            true
        );
        String amount = Integer.toString(count);
        graphics.text(
            font,
            Component.literal(amount),
            x + width - font.width(amount) - 5,
            y + 4,
            0xFFB8C0CC,
            false
        );
        return y + 19;
    }

    private int drawTaskTree(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        y = drawTaskCard(
            graphics,
            quest,
            task,
            progressKey,
            x,
            y,
            width,
            complete
        );
        if (
            task.kind() != QuestDefinition.TaskKind.COMPOSITE ||
            task.tasks().isEmpty()
        ) return y;

        int branchTop = y;
        int nestedX = x + 10;
        int nestedWidth = width - 10;
        String requirement =
            "Options · complete " +
            task.target() +
            " of " +
            task.tasks().size();
        graphics.fill(nestedX, y, nestedX + nestedWidth, y + 15, 0xFF252A31);
        graphics.text(
            font,
            Component.literal(requirement),
            nestedX + 7,
            y + 3,
            0xFFB8C0CC,
            false
        );
        y += 19;
        for (QuestDefinition.Task child : task.tasks().values()) {
            String childKey = progressKey + "/" + child.id();
            boolean childComplete =
                quest.progress.getOrDefault(childKey, 0) >= child.target();
            y = drawTaskTree(
                graphics,
                quest,
                child,
                childKey,
                nestedX,
                y,
                nestedWidth,
                childComplete
            );
        }
        graphics.fill(
            x + 3,
            branchTop,
            x + 5,
            y - 5,
            complete ? 0xFF55D86A : 0xFF4C9AFF
        );
        return y + 2;
    }

    private int drawTaskCard(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        int progress = quest.progress.getOrDefault(progressKey, 0);
        int state = complete ? 0xFF55D86A : 0xFF626A76;
        graphics.fill(
            x,
            y,
            x + width,
            y + CARD_HEIGHT,
            complete ? 0xFF2D3932 : 0xFF30353D
        );
        graphics.outline(x, y, width, CARD_HEIGHT, state);
        taskCardBounds.add(new TaskCardBounds(
            progressKey,
            QuestPresentation.taskTitle(task),
            new UiBounds(x, y, width, CARD_HEIGHT)
        ));
        if (
            task.kind() == QuestDefinition.TaskKind.CHECK &&
            !hasCustomTaskIcon(task)
        ) {
            graphics.blit(CHECK_ICON, x + 7, y + 11, x + 23, y + 27, 0, 0, 1, 1);
        } else {
            QuestPresentation.renderTaskIcon(graphics, task, x + 7, y + 11);
            registerRecipeViewerTarget(QuestPresentation.taskIconTarget(task), x + 7, y + 11);
        }
        String progressText = progress + "/" + task.target();
        int textX = x + 30;
        drawClippedDetailText(
            graphics,
            QuestPresentation.taskTitle(task),
            textX,
            y + 6,
            Math.max(1, x + width - font.width(progressText) - 10 - textX),
            complete ? 0xFFD8F5DD : 0xFFFFFFFF
        );
        drawClippedDetailText(
            graphics,
            QuestPresentation.taskDescription(task),
            textX,
            y + 18,
            Math.max(1, x + width - 6 - textX),
            0xFFADB4BF
        );
        graphics.text(
            font,
            Component.literal(progressText),
            x + width - font.width(progressText) - 5,
            y + 6,
            0xFFFFFFFF,
            false
        );
        drawProgressBar(
            graphics,
            x + 30,
            y + CARD_HEIGHT - 9,
            width - 36,
            task.target() == 0 ? 0 : progress / (double) task.target()
        );
        return y + CARD_HEIGHT + 5;
    }

    private static void drawProgressBar(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        double progress
    ) {
        double clamped = Math.max(0, Math.min(1, progress));
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            clamped >= 1 ? PROGRESS_COMPLETE : PROGRESS_ACTIVE,
            x,
            y,
            width,
            5
        );
        int fill = (int) Math.round(width * clamped);
        if (fill > 0 && clamped < 1) graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            PROGRESS_FILL,
            x,
            y,
            fill,
            5
        );
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, path);
    }

    private static boolean hasCustomTaskIcon(QuestDefinition.Task task) {
        return task.source().has("icon") && !task.source().get("icon").isJsonNull();
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

    private QuestMinimap.MapBounds minimapBounds() {
        if (TheseusClientOptions.disableMinimap() || minimapHidden) return null;
        TheseusClientOptions.MinimapMode mode = TheseusClientOptions.defaultMinimapMode();
        int mapWidth = QuestMinimap.DEFAULT_WIDTH;
        if (graphCanvasBounds().width() < mapWidth ||
            graphCanvasBounds().height() < QuestMinimap.DEFAULT_HEIGHT) return null;
        return QuestMinimap.placement(
            mode,
            graphCanvasBounds(),
            minimapPositionX(),
            minimapPositionY(),
            mapWidth,
            QuestMinimap.DEFAULT_HEIGHT
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

    private double minimapPositionX() {
        return Double.isFinite(minimapPositionX)
            ? minimapPositionX
            : TheseusClientOptions.minimapX();
    }

    private double minimapPositionY() {
        return Double.isFinite(minimapPositionY)
            ? minimapPositionY
            : TheseusClientOptions.minimapY();
    }

    private void clearMinimapTransientState() {
        minimapNavigating = false;
        minimapRepositioning = false;
        minimapPositionX = Double.NaN;
        minimapPositionY = Double.NaN;
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
        detailScroll = 0;
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
            snapCurrentDraftPosition();
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

    private boolean openProgressCardContextMenu(int mouseX, int mouseY) {
        if (!canEdit() || !detailsOpen) return false;
        ClientQuest quest = selected();
        if (quest == null) return false;
        if (detailTab == DetailTab.TASKS) {
            for (TaskCardBounds card : taskCardBounds) {
                if (!card.bounds().contains(mouseX, mouseY)) continue;
                List<QuestContextMenu.Entry> entries = new ArrayList<>();
                entries.add(QuestContextMenu.Entry.item(
                    "Copy task path",
                    "",
                    true,
                    false,
                    () -> copyProgressEntry("task path", card.path())
                ));
                entries.add(QuestContextMenu.Entry.separator());
                entries.add(QuestContextMenu.Entry.item(
                    "Reset task progress",
                    "",
                    true,
                    true,
                    () -> requestProgressReset(new QuestModalHost.ProgressResetTarget(
                        "task",
                        quest.definition.id(),
                        quest.definition.title(),
                        card.path(),
                        card.displayLabel()
                    ))
                ));
                showContextMenu(mouseX, mouseY, entries);
                return true;
            }
        }
        if (detailTab == DetailTab.REWARDS) {
            for (RewardCardBounds card : rewardCardBounds) {
                if (!card.bounds().contains(mouseX, mouseY)) continue;
                List<QuestContextMenu.Entry> entries = new ArrayList<>();
                entries.add(QuestContextMenu.Entry.item(
                    "Copy reward ID",
                    "",
                    true,
                    false,
                    () -> copyProgressEntry("reward ID", card.id())
                ));
                entries.add(QuestContextMenu.Entry.separator());
                entries.add(QuestContextMenu.Entry.item(
                    "Reset reward progress",
                    "",
                    true,
                    true,
                    () -> requestProgressReset(new QuestModalHost.ProgressResetTarget(
                        "reward",
                        quest.definition.id(),
                        quest.definition.title(),
                        card.id(),
                        card.displayLabel()
                    ))
                ));
                showContextMenu(mouseX, mouseY, entries);
                return true;
            }
        }
        return false;
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
            entries.add(QuestContextMenu.Entry.item("Open details", "Enter", true, false, () -> openQuestDetails(quest)));
            entries.add(QuestContextMenu.Entry.item("Copy quest ID", "", true, false, () -> copyQuestId(quest)));
            entries.add(QuestContextMenu.Entry.item(
                quest != null && quest.pinned ? "Unpin quest" : "Pin quest",
                "",
                quest != null && quest.unlocked,
                false,
                () -> toggleQuestPinned(quest)
            ));
        } else {
            if (canOpenQuestFile(quest)) entries.add(QuestContextMenu.Entry.item(
                "Open quest file",
                "",
                true,
                false,
                () -> requestQuestFileOpen(quest)
            ));
            entries.add(QuestContextMenu.Entry.item("Edit quest", "Enter", true, false, () -> openQuestEditorFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.item("Copy quest ID", "", true, false, () -> copyQuestId(quest)));
            entries.add(QuestContextMenu.Entry.item("Reset quest progress", "", true, true, () -> resetQuestProgressFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item("Copy quest", "Ctrl+C", true, false, () -> copyQuestToClipboard(quest)));
            entries.add(QuestContextMenu.Entry.item("Cut quest", "Ctrl+X", true, false, () -> cutQuestToClipboard(quest)));
            entries.add(QuestContextMenu.Entry.item("Snap selected quest", "", true, false, () -> snapQuestFromMenu(quest)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item("Delete quest", "", true, true, () -> deleteQuestFromMenu(quest)));
        }
        showContextMenu(mouseX, mouseY, entries);
    }

    private void openEmptyGraphContextMenu(double worldX, double worldY, int mouseX, int mouseY) {
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        if (mode.isAuthoring()) {
            entries.add(QuestContextMenu.Entry.item("Add quest here", "", true, false, () ->
                requestDiscard(() -> beginCreateQuest(worldX, worldY))
            ));
            if (hasClipboardContent()) entries.add(QuestContextMenu.Entry.item("Paste here", "Ctrl+V", true, false, () -> {
                if (clipboardMove) sendClipboardPaste(false, null, worldX, worldY);
                else openPasteIdPrompt(worldX, worldY);
            }));
            entries.add(QuestContextMenu.Entry.item("Fit to content", "Home", true, false, this::fitGraphToContent));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item("Select tool", "S", true, false, () -> setEditorTool(EditorTool.SELECT)));
            entries.add(QuestContextMenu.Entry.item("Hand tool", "H", true, false, () -> setEditorTool(EditorTool.HAND)));
            entries.add(QuestContextMenu.Entry.item("Add tool", "A", true, false, () -> setEditorTool(EditorTool.ADD)));
            entries.add(QuestContextMenu.Entry.item("Link tool", "L", true, false, () -> setEditorTool(EditorTool.LINK)));
            entries.add(QuestContextMenu.Entry.separator());
            entries.add(QuestContextMenu.Entry.item(
                TheseusClientOptions.showGrid() ? "Hide grid" : "Show grid",
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setShowGrid(!TheseusClientOptions.showGrid());
                    rebuildWidgets();
                }
            ));
            entries.add(QuestContextMenu.Entry.item(
                TheseusClientOptions.snapToGrid() ? "Disable snap to grid" : "Enable snap to grid",
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setSnapToGrid(!TheseusClientOptions.snapToGrid());
                    rebuildWidgets();
                }
            ));
        } else {
            entries.add(QuestContextMenu.Entry.item("Fit to content", "Home", true, false, this::fitGraphToContent));
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
                "Import quests",
                "",
                true,
                false,
                this::openNativeFilePicker
            ));
            entries.add(QuestContextMenu.Entry.item(
                "Fit graph to content",
                "Home",
                true,
                false,
                this::fitGraphToContent
            ));
            entries.add(QuestContextMenu.Entry.item(
                TheseusClientOptions.showGrid() ? "Hide grid" : "Show grid",
                "",
                true,
                false,
                () -> {
                    TheseusClientOptions.setShowGrid(!TheseusClientOptions.showGrid());
                    rebuildWidgets();
                }
            ));
            entries.add(QuestContextMenu.Entry.item(
                TheseusClientOptions.snapToGrid() ? "Disable snap to grid" : "Enable snap to grid",
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
            int labelColor = !entry.enabled() ? 0xFF68717F : entry.danger() ? 0xFFFF9999 : 0xFFFFFFFF;
            graphics.text(font, Component.literal(entry.label()), row.x() + 6, row.y() + 6, labelColor, false);
            if (!entry.shortcut().isBlank()) {
                graphics.text(font, Component.literal(entry.shortcut()), row.x() + row.width() - font.width(entry.shortcut()) - 6, row.y() + 6, 0xFF9AA4B2, false);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean modalOpen = modalHost.blocksInput();
        if (!modalOpen && contextMenu != null && contextMenu.isOpen()) {
            contextMenu.keyPressed(event.key());
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
            case TASK_EDITOR, NESTED_TASKS -> requestModalDiscard(this::closeTaskEditor);
            case NESTED_REWARD_EDITOR -> requestModalDiscard(() -> closeRewardEditor(true));
            case REWARD_EDITOR, NESTED_REWARDS -> requestModalDiscard(() -> closeRewardEditor(false));
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
        pasteIdField = new EditBox(font, left + 14, top + 52, 252, 18, Component.literal("New quest ID"));
        pasteIdField.setValue(clipboardSourceId + "_copy");
        addRenderableWidget(pasteIdField);
        setInitialFocus(pasteIdField);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> {
                pasteIdField = null;
                modalHost.close();
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 166, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Paste")));
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
                return rewardChooserClicked(event, true);
            }
            case REWARD_CHOOSER -> {
                return rewardChooserClicked(event, false);
            }
            case NESTED_TASK_CHOOSER -> {
                return taskChooserClicked(event, true);
            }
            case TASK_CHOOSER -> {
                return taskChooserClicked(event);
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
            for (LockQuestBounds target : lockQuestBounds) {
                if (!target.bounds().contains(event.x(), event.y())) continue;
                selectedQuestId = target.questId();
                detailScroll = 0;
                rebuildWidgets();
                return true;
            }
        }
        if (event.input() == 0 && detailsOpen && detailTab == DetailTab.OVERVIEW) {
            for (QuestDescriptionRenderer.Interaction interaction : descriptionInteractions) {
                if (!interaction.contains(event.x(), event.y())) continue;
                if (interaction.clickStyle() != null && interaction.clickStyle().getClickEvent() != null) {
                    defaultHandleClickEvent(interaction.clickStyle().getClickEvent(), minecraft, this);
                }
                return true;
            }
        }
        if (
            event.input() == 0 &&
            detailsOpen &&
            detailTab == DetailTab.REWARDS &&
            event.x() >= width - detailsWidth()
        ) {
            for (RewardChoiceBounds choice : rewardChoiceBounds) {
                if (!choice.bounds().contains(event.x(), event.y())) continue;
                Set<String> selected = rewardSelections.computeIfAbsent(
                    choice.selectionKey(),
                    ignored -> new LinkedHashSet<>()
                );
                ClientQuest quest = selected();
                QuestDefinition.Reward parent =
                    quest == null
                        ? null
                        : quest.definition
                              .rewards()
                              .values()
                              .stream()
                              .filter(reward ->
                                  (
                                      quest.definition.id() +
                                      "|" +
                                      reward.id()
                                  ).equals(choice.selectionKey())
                              )
                              .findFirst()
                              .orElse(null);
                if (selected.remove(choice.choiceId())) {
                    rebuildWidgets();
                    return true;
                }
                if (parent != null) {
                    if (parent.amount() == 1) {
                        selected.clear();
                        selected.add(choice.choiceId());
                        rebuildWidgets();
                    } else if (selected.size() < parent.amount()) {
                        selected.add(choice.choiceId());
                        rebuildWidgets();
                    }
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
                detailScroll = 0;
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
        RecipeViewerTarget target = null;
        for (RecipeViewerTarget candidate : recipeViewerTargets) {
            if (candidate.contains(event.x(), event.y())) target = candidate;
        }
        if (target == null) return false;
        return event.input() == 0
            ? RecipeViewer.showRecipes(target.stack())
            : RecipeViewer.showUses(target.stack());
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

    private boolean taskChooserClicked(MouseButtonEvent event) {
        return taskChooserClicked(event, false);
    }

    private boolean taskChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? taskEditorLeft() + 14 : width - detailsWidth() + 16;
        int top = nested ? taskEditorTop() + 38 : 47;
        int chooserWidth = nested ? 232 : detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, TASK_CHOICES.size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth ||
            event.y() < top || event.y() >= top + chooserHeight) {
            modalHost.close();
            rebuildWidgets();
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < visibleCount) {
            TaskChoice choice = TASK_CHOICES.get(taskChooserScroll + row);
            if (choice.implemented) {
                QuestAuthoringSession.TaskDraft previousTask = authoring.editingTask;
                if (nested) addNestedDraftTask(choice);
                else addDraftTask(choice);
                if (authoring.editingTask != previousTask) {
                    modalHost.close();
                    modalHost.open(QuestModalHost.Modal.TASK_EDITOR);
                }
                rebuildWidgets();
            }
        }
        return true;
    }

    private boolean rewardChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? REWARD_CHOICES.stream().filter(choice -> !choice.type.equals("theseus:selectable")).toList()
            : REWARD_CHOICES;
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth || event.y() < top || event.y() >= top + chooserHeight) {
            modalHost.close();
            rebuildWidgets();
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < choices.size()) {
            addDraftReward(choices.get(row), nested);
            rebuildWidgets();
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
        EditorTypeRegistry.Descriptor descriptor = editorTypes().resolve(EditorTypeRegistry.Kind.TASK, choice.type);
        if (!descriptor.editable()) {
            editorMessage = descriptor.availabilityReason();
            editorMessageSuccess = false;
            return null;
        }
        String base = choice.type.substring(choice.type.indexOf(':') + 1);
        int suffix = 1;
        String id = base;
        while (taskIdExists(siblings, id)) id = base + "_" + ++suffix;
        JsonObject source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "theseus:dummy" -> source.addProperty("value", id);
            case "theseus:item" -> {
                source.addProperty("item", "minecraft:stone");
                source.addProperty("amount", 1);
                source.addProperty("collection", "automatic");
            }
            case "theseus:xp" -> {
                source.addProperty("amount", 1);
                source.addProperty("xpType", "level");
                source.addProperty("collectionType", "automatic");
            }
            case "theseus:kill_entity" -> {
                source.addProperty("entity", "minecraft:pig");
                source.addProperty("amount", 1);
            }
            case "theseus:advancement" -> source.add("advancements", stringArray("minecraft:story/mine_stone"));
            case "theseus:biome" -> source.addProperty("biomes", "minecraft:plains");
            case "theseus:block_interaction" -> source.addProperty("block", "minecraft:stone");
            case "theseus:changed_dimension" -> source.addProperty("to", "minecraft:the_nether");
            case "theseus:check" -> source.add("components", new JsonObject());
            case "theseus:composite" -> {
                source.addProperty("amount", 1);
                JsonObject tasks = new JsonObject();
                JsonObject child = new JsonObject();
                child.addProperty("type", "theseus:check");
                child.add("components", new JsonObject());
                tasks.add("check", child);
                source.add("tasks", tasks);
            }
            case "theseus:entity_interaction" -> source.addProperty("entity", "minecraft:pig");
            case "theseus:item_interaction", "theseus:item_use" -> source.addProperty("item", "minecraft:stick");
            case "theseus:location" -> {
                source.addProperty("description", "Reach the configured location");
                source.add("predicate", defaultLocationPredicate());
            }
            case "theseus:recipe" -> source.add("recipes", stringArray("minecraft:crafting_table"));
            case "theseus:stat" -> {
                source.addProperty("stat", "minecraft:jump");
                source.addProperty("target", 1);
            }
            case "theseus:structure" -> source.addProperty("structures", "#minecraft:village");
            default -> throw new IllegalArgumentException("Task type is not implemented: " + choice.type);
        }
        return new QuestAuthoringSession.TaskDraft(id, choice.type, source);
    }

    private void addDraftReward(RewardChoice choice, boolean nested) {
        EditorTypeRegistry.Descriptor descriptor = editorTypes().resolve(EditorTypeRegistry.Kind.REWARD, choice.type);
        if (!descriptor.editable()) {
            editorMessage = descriptor.availabilityReason();
            editorMessageSuccess = false;
            return;
        }
        List<QuestAuthoringSession.RewardDraft> rewards = nested ? nestedRewards(authoring.editingReward) : authoring.rewards;
        String base = choice.type.substring(choice.type.indexOf(':') + 1);
        int suffix = 1;
        String id = base;
        while (rewardIdExists(rewards, id)) id = base + "_" + ++suffix;
        JsonObject source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "theseus:xp" -> {
                source.addProperty("xptype", "level");
                source.addProperty("amount", 1);
            }
            case "theseus:item" -> setRewardItem(source, "minecraft:stone", 1);
            case "theseus:loottable" -> source.addProperty("loot_table", "minecraft:chests/simple_dungeon");
            case "theseus:command" -> source.addProperty("command", "say Quest complete");
            case "theseus:selectable" -> {
                source.addProperty("amount", 1);
                source.add("rewards", new JsonObject());
            }
            default -> throw new IllegalArgumentException("Unknown reward type " + choice.type);
        }
        QuestAuthoringSession.RewardDraft reward = new QuestAuthoringSession.RewardDraft(id, choice.type, source);
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

    private static boolean rewardIdExists(List<QuestAuthoringSession.RewardDraft> rewards, String id) {
        return rewards.stream().anyMatch(reward -> reward.id.equals(id));
    }

    private static boolean taskIdExists(List<QuestAuthoringSession.TaskDraft> tasks, String id) {
        return tasks.stream().anyMatch(task -> task.id.equals(id));
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

    private QuestMinimap.Mapping minimapMapping(QuestMinimap.MapBounds bounds) {
        return QuestMinimap.mapping(
            surfaceLayout().worldBounds(16),
            bounds
        );
    }

    private boolean minimapClicked(MouseButtonEvent event) {
        if (detailsDockContains(event.x(), event.y())) return false;
        QuestMinimap.MapBounds bounds = minimapBounds();
        if (!QuestMinimap.contains(bounds, event.x(), event.y())) return false;

        if (QuestMinimap.containsHeaderMenu(bounds, event.x(), event.y())) {
            openMinimapContextMenu((int) Math.round(event.x()), (int) Math.round(event.y()));
            return true;
        }

        if (
            TheseusClientOptions.defaultMinimapMode() == TheseusClientOptions.MinimapMode.UNDOCKED &&
            event.input() == 0 &&
            QuestMinimap.containsGrip(bounds, event.x(), event.y())
        ) {
            minimapRepositioning = true;
            minimapNavigating = false;
            minimapDragOffsetX = event.x() - bounds.x();
            minimapDragOffsetY = event.y() - bounds.y();
            minimapPositionX = TheseusClientOptions.minimapX();
            minimapPositionY = TheseusClientOptions.minimapY();
            return true;
        }

        if (event.input() == 0 && QuestMinimap.containsBody(bounds, event.x(), event.y())) {
            centerOnMinimap(bounds, event.x(), event.y());
            minimapNavigating = true;
            return true;
        }
        return true;
    }

    private void openMinimapContextMenu(int mouseX, int mouseY) {
        boolean docked = TheseusClientOptions.defaultMinimapMode() == TheseusClientOptions.MinimapMode.DOCKED;
        List<QuestContextMenu.Entry> entries = new ArrayList<>();
        entries.add(QuestContextMenu.Entry.item(
            docked ? "Undock minimap" : "Dock minimap",
            "",
            true,
            false,
            this::toggleMinimapDocking
        ));
        entries.add(QuestContextMenu.Entry.item(
            "Hide minimap",
            "",
            true,
            false,
            () -> {
                minimapHidden = true;
                clearMinimapTransientState();
                rebuildWidgets();
            }
        ));
        showContextMenu(mouseX, mouseY, entries);
    }

    private void centerOnMinimap(QuestMinimap.MapBounds bounds, double mouseX, double mouseY) {
        QuestGraphLayout.Point world = QuestMinimap.mapToWorld(
            minimapMapping(bounds),
            mouseX,
            mouseY
        );
        graphViewport.centerOn(world.x(), world.y());
        graphViewport.saveChapterViewport(group);
    }

    private boolean minimapDragged(double mouseX, double mouseY) {
        if (TheseusClientOptions.disableMinimap()) {
            clearMinimapTransientState();
            return false;
        }
        QuestMinimap.MapBounds bounds = minimapBounds();
        if (minimapRepositioning) {
            QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
            QuestMinimap.MapBounds next = new QuestMinimap.MapBounds(
                (int) Math.round(mouseX - minimapDragOffsetX),
                (int) Math.round(mouseY - minimapDragOffsetY),
                bounds == null ? QuestMinimap.DEFAULT_WIDTH : bounds.width(),
                bounds == null ? QuestMinimap.DEFAULT_HEIGHT : bounds.height()
            );
            double[] normalized = QuestMinimap.normalizedPosition(canvas, next);
            minimapPositionX = normalized[0];
            minimapPositionY = normalized[1];
            return true;
        }
        if (minimapNavigating && QuestMinimap.containsBody(bounds, mouseX, mouseY)) {
            centerOnMinimap(bounds, mouseX, mouseY);
            return true;
        }
        return minimapNavigating;
    }

    private boolean minimapReleased() {
        if (TheseusClientOptions.disableMinimap()) {
            clearMinimapTransientState();
            return false;
        }
        if (minimapRepositioning) {
            TheseusClientOptions.setMinimapPosition(minimapPositionX, minimapPositionY);
            clearMinimapTransientState();
            rebuildWidgets();
            return true;
        }
        if (minimapNavigating) {
            minimapNavigating = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (minimapReleased()) return true;
        if (questMoved && TheseusClientOptions.snapToGrid()) {
            snapCurrentDraftPosition();
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
            int max = Math.max(0, TASK_CHOICES.size() - TASK_CHOOSER_VISIBLE);
            taskChooserScroll = Math.max(
                0,
                Math.min(max, taskChooserScroll - (int) Math.signum(scrollY))
            );
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
            int max = Math.max(0, TASK_CHOICES.size() - TASK_CHOOSER_VISIBLE);
            taskChooserScroll = Math.max(
                0,
                Math.min(max, taskChooserScroll - (int) Math.signum(scrollY))
            );
            return true;
        }
        if (modalHost.blocksInput()) return true;
        if (QuestMinimap.contains(minimapBounds(), mouseX, mouseY)) return true;
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
            if (createQuestTab == DetailTab.TASKS) {
                createTaskScroll = Math.max(
                    0,
                    Math.min(maxCreateTaskScroll(), createTaskScroll - (int) Math.signum(scrollY))
                );
                rebuildWidgets();
            } else if (createQuestTab == DetailTab.REWARDS) {
                createRewardScroll = Math.max(
                    0,
                    Math.min(maxCreateRewardScroll(), createRewardScroll - (int) Math.signum(scrollY))
                );
                rebuildWidgets();
            } else if (createQuestTab == DetailTab.OVERVIEW) {
                super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                if (draftOverviewScrollContainer != null) {
                    draftOverviewScrollY = draftOverviewScrollContainer.getYScroll();
                }
            }
            return true;
        }
        if (detailsOpen && mouseX >= width - detailsWidth()) {
            detailScroll = Math.max(
                0,
                Math.min(
                    detailMaxScroll,
                    detailScroll - (int) Math.round(scrollY * 18)
                )
            );
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

    private static String status(ClientQuest quest) {
        if (!quest.unlocked) return "[Locked]";
        if (quest.claimed) return "[Claimed]";
        if (quest.complete) return "[Complete]";
        return "[Active]";
    }

    private static int nodeStateColor(ClientQuest quest) {
        if (!quest.unlocked) return 0xFF737B87;
        if (quest.claimed) return 0xFF55D86A;
        if (quest.complete) return 0xFFFFD966;
        return 0xFF4C9AFF;
    }

    private static double questProgress(ClientQuest quest) {
        if (quest.definition.tasks().isEmpty()) return quest.complete ? 1 : 0;
        double progress = 0;
        for (QuestDefinition.Task task : quest.definition.tasks().values()) {
            progress += Math.min(
                1,
                quest.progress.getOrDefault(task.id(), 0) /
                    (double) Math.max(1, task.target())
            );
        }
        return progress / quest.definition.tasks().size();
    }

    private static String descriptionReference(
        ClientQuest quest,
        DescriptionDocument.BlockKind kind,
        String id
    ) {
        if (kind == DescriptionDocument.BlockKind.TASK) {
            QuestDefinition.Task task = quest.definition.tasks().get(id);
            return task == null ? id + " (missing)" : task.title();
        }
        QuestDefinition.Reward reward = quest.definition.rewards().get(id);
        return reward == null ? id + " (missing)" : reward.title();
    }

    private enum DetailTab {
        OVERVIEW("Overview"),
        TASKS("Tasks"),
        REWARDS("Rewards");

        private final String label;

        DetailTab(String label) {
            this.label = label;
        }
    }

    private enum Picker {
        NONE,
        ICON,
        ENTITY,
        BACKGROUND
    }

    private enum PickerTarget {
        QUEST_ICON,
        TASK_ICON,
        TASK_ITEM,
        TASK_BLOCK,
        TASK_ENTITY,
        REWARD_ICON,
        REWARD_ITEM,
        CHAPTER_ICON
    }

    private record UiBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return (
                mouseX >= x &&
                mouseX < x + width &&
                mouseY >= y &&
                mouseY < y + height
            );
        }
    }

    private record TaskRef(String path, QuestDefinition.Task task) {}

    private record TaskChoice(
        String type,
        String label,
        Item icon,
        boolean implemented
    ) {}

    private record RewardChoice(String type, String label, Item icon) {}

    private record QuestBackground(
        Identifier texture,
        int xOffset,
        int yOffset,
        int width,
        int height
    ) {}

    private record RewardChoiceBounds(
        String selectionKey,
        String choiceId,
        UiBounds bounds
    ) {}

    private record TaskCardBounds(String path, String displayLabel, UiBounds bounds) {}

    private record RewardCardBounds(String id, String displayLabel, UiBounds bounds) {}

    private record DetailTextBounds(UiBounds bounds, String text) {}

    private record LockQuestBounds(UiBounds bounds, String questId) {}

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
