package me.johardt.theseus.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.QuestClientSnapshot.ChapterDisplay;
import me.johardt.theseus.client.QuestClientSnapshot.ClientQuest;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.client.description.DescriptionDocument;
import me.johardt.theseus.client.description.DescriptionParser;
import me.johardt.theseus.client.description.QuestDescriptionRenderer;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.EntityType;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Renders the graph, panels, minimap, and editor overlays. */
final class QuestScreenRenderer {
    private static final int DEPENDENCY_TILE_SIZE = 3;
    private static final double MIN_DEPENDENCY_TEXTURE_ZOOM = 0.75;
    private static final double DEPENDENCY_TEXTURE_MARKER_SPACING = 32.0;
    private static final int MAX_DEPENDENCY_TEXTURE_MARKERS_PER_PATH = 6;
    private static final double DEPENDENCY_STROKE_MARGIN = 3.0;
    private static final int CHAPTER_BACKGROUND_BOTTOM_TRIM = 18;
    private static final PathStyle GRAY_PATH = new PathStyle(0xB0111318, 0x80535A64, 0x776F7782);
    private static final PathStyle UNLOCKED_PATH = new PathStyle(0xB0111318, 0x80636F66, 0x8876A77B);
    private static final PathStyle SELECTED_INCOMPLETE_PATH = new PathStyle(0xFF8A6818, 0xDDFFD966, 0xFFFFD966);
    private static final PathStyle COMPLETED_PATH = new PathStyle(0xFF1F702E, 0xDD55D86A, 0xFF55D86A);

    private record PathStyle(int borderColor, int strokeColor, int arrowTint) {}

    static final Identifier DEPENDENCY_ARROW = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/arrow.png"
    );
    static final Identifier DEFAULT_QUEST_FRAME = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/gui/quest_backgrounds/default.png"
    );
    static final List<Identifier> QUEST_BACKGROUNDS = List.of(
        "default", "circles", "diamonds", "gears", "hearts", "hexagons",
        "octagons", "pentagons", "rounded_squares"
    ).stream().map(name -> sprite("textures/gui/quest_backgrounds/" + name + ".png")).toList();

    private final QuestScreen screen;

    QuestScreenRenderer(QuestScreen screen) {
        this.screen = screen;
    }

    void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        screen.authoringPanel.setViewport(screen.guiFont(), screen.guiWidth(), screen.guiHeight());
        ChapterDisplay chapterDisplay = screen.chapterDisplays.get(screen.group);
        if (chapterDisplay != null && !chapterDisplay.background().isBlank()) {
            try {
                Identifier texture = Identifier.parse(chapterDisplay.background());
                int backgroundX = screen.layout.sidebarWidth();
                int backgroundWidth = Math.max(1, screen.layout.graphCanvasRight() - backgroundX);
                int backgroundY = screen.layout.graphCanvasTop();
                int fullBackgroundHeight = Math.max(1, screen.guiHeight() - backgroundY);
                int backgroundHeight = Math.max(
                    1,
                    fullBackgroundHeight - CHAPTER_BACKGROUND_BOTTOM_TRIM
                );
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
                    fullBackgroundHeight,
                    (chapterDisplay.backgroundOpacity() * 255 / 100 << 24) | 0x00FFFFFF
                );
            } catch (RuntimeException ignored) { }
        }
        QuestGraphLayout.CanvasBounds canvas = screen.layout.graphCanvasBounds();
        QuestSurfaceLayout.Layout surface = screen.layout.surfaceLayout();
        graphics.enableScissor(
            (int) canvas.x(),
            (int) canvas.y(),
            (int) canvas.maxX(),
            (int) canvas.maxY()
        );
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) canvas.centerX(), (float) canvas.centerY());
        graphics.pose().scale((float) screen.graphViewport.state().zoom());
        graphics.pose().translate((float) -screen.graphViewport.state().centerWorldX(), (float) -screen.graphViewport.state().centerWorldY());
        QuestGraphLayout.WorldBounds visibleWorld = QuestGraphLayout.visibleWorld(
            canvas,
            screen.graphViewport.state()
        );
        drawGraphGrid(graphics);
        drawDependencyPaths(graphics, surface, visibleWorld);
        QuestGraphLayout.Point mouseWorld = QuestGraphLayout.screenToWorld(
            canvas, screen.graphViewport.state(), mouseX, mouseY
        );
        drawLinkPreview(graphics, mouseWorld.x(), mouseWorld.y(), visibleWorld);
        drawQuestNodes(
            graphics,
            surface,
            mouseWorld.x(),
            mouseWorld.y(),
            !screen.layout.detailsDockContains(mouseX, mouseY)
        );
        drawCreateQuestPreview(graphics);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        renderMinimap(graphics, surface);
        drawChapterName(graphics);
        drawPanelScrims(graphics);
        QuestModalHost.Modal activeOverlay = screen.modalHost.active();
        boolean diagnosticsModal = activeOverlay == QuestModalHost.Modal.DIAGNOSTICS;
        boolean importModal = activeOverlay == QuestModalHost.Modal.FILE_IMPORT;
        boolean rawInspectorModal = activeOverlay == QuestModalHost.Modal.RAW_INSPECTOR;
        boolean taskModal = activeOverlay == QuestModalHost.Modal.TASK_EDITOR
            || activeOverlay == QuestModalHost.Modal.NESTED_TASKS
            || activeOverlay == QuestModalHost.Modal.NESTED_TASK_CHOOSER
            || (activeOverlay == QuestModalHost.Modal.PICKER && screen.modalHost.contains(QuestModalHost.Modal.TASK_EDITOR))
            || (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION && screen.modalHost.contains(QuestModalHost.Modal.TASK_EDITOR));
        boolean rewardModal = activeOverlay == QuestModalHost.Modal.REWARD_EDITOR
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARDS
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARD_CHOOSER
            || activeOverlay == QuestModalHost.Modal.NESTED_REWARD_EDITOR
            || (activeOverlay == QuestModalHost.Modal.PICKER && screen.modalHost.contains(QuestModalHost.Modal.REWARD_EDITOR))
            || (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION && screen.modalHost.contains(QuestModalHost.Modal.REWARD_EDITOR));
        boolean modalVisible = screen.modalHost.rendersOverlay();
        if (modalVisible) {
            drawBaseForeground(graphics, mouseX, mouseY);
            if (diagnosticsModal) {
                screen.imports.drawDiagnosticsModal(graphics);
            } else if (importModal) {
                screen.imports.drawImportModal(graphics);
            } else if (rawInspectorModal) {
                drawRawInspector(graphics);
            } else if (activeOverlay == QuestModalHost.Modal.DESCRIPTION_EDITOR) {
                drawDescriptionEditor(graphics, mouseX, mouseY);
            } else {
                if (taskModal) {
                    screen.authoringPanel.taskEditor.drawTaskEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER && !screen.modalHost.showsNestedTasks()) screen.authoringPanel.taskEditor.drawTaskEditorForeground(graphics);
                }
                if (rewardModal) {
                    screen.authoringPanel.rewardEditor.drawRewardEditorPanel(graphics);
                    if (activeOverlay == QuestModalHost.Modal.PICKER) screen.authoringPanel.rewardEditor.drawRewardModalForeground(graphics, mouseX, mouseY);
                }
                if (activeOverlay == QuestModalHost.Modal.DELETE_QUEST_CONFIRMATION) drawDeleteQuestConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION) drawProgressResetConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.DISCARD_CONFIRMATION) drawDiscardConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.TASK_DELETE_CONFIRMATION) drawDeleteTaskConfirmation(graphics);
                if (activeOverlay == QuestModalHost.Modal.CHAPTER_EDITOR) drawChapterEditor(graphics);
                if (activeOverlay == QuestModalHost.Modal.PASTE_ID_PROMPT) drawPasteIdPrompt(graphics);
                if (activeOverlay == QuestModalHost.Modal.PICKER) drawPickerPanel(graphics);
            }
            screen.parentExtractRenderState(graphics, mouseX, mouseY, partialTick);
            // An overlay may own the widget tree but leave its parent editor's
            // state populated. Never redraw that parent's manual foreground.
            if (screen.picker == Picker.NONE && !rawInspectorModal && !diagnosticsModal && !importModal) {
                if (taskModal) {
                    if (screen.modalHost.showsNestedTasks()) screen.authoringPanel.taskEditor.drawNestedTasksForeground(graphics, mouseX, mouseY);
                    else screen.authoringPanel.taskEditor.drawTaskEditorForeground(graphics);
                }
                if (rewardModal) screen.authoringPanel.rewardEditor.drawRewardModalForeground(graphics, mouseX, mouseY);
            } else if (activeOverlay == QuestModalHost.Modal.PICKER) {
                drawPickerContents(graphics, mouseX, mouseY);
            }
            return;
        }
        screen.parentExtractRenderState(graphics, mouseX, mouseY, partialTick);
        drawBaseForeground(graphics, mouseX, mouseY);
    }

    static void drawQuestBackground(
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

    static QuestBackground questBackground(QuestDefinition definition) {
        return questBackground(definition.display().iconBackground());
    }

    static QuestBackground questBackground(String value) {
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

    static ItemStack entityIcon(EntityType<?> entity) {
        return SpawnEggItem.byId(entity)
            .map(holder -> new ItemStack(holder.value()))
            .orElseGet(() -> new ItemStack(Items.ARMOR_STAND));
    }

    void drawTexturedPath(
        GuiGraphicsExtractor graphics,
        PathPoint start,
        PathPoint end,
        PathStyle style,
        QuestGraphLayout.WorldBounds visibleWorld
    ) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.hypot(dx, dy);
        QuestGraphLayout.PathTileRange tiles = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(start.x, start.y),
            new QuestGraphLayout.Point(end.x, end.y),
            visibleWorld,
            DEPENDENCY_STROKE_MARGIN,
            DEPENDENCY_TILE_SIZE
        );
        if (tiles.isEmpty() || !Double.isFinite(length) || length < 1) return;

        long firstPixel = safeTilePixel(tiles.firstTile());
        long endPixel = Math.min(
            tiles.pixelLength(),
            safeTilePixel(tiles.endTileExclusive())
        );
        long visibleLength = Math.max(0, endPixel - firstPixel);
        if (visibleLength == 0) return;
        double firstDistance = firstPixel;
        double firstX = start.x + dx / length * firstDistance;
        double firstY = start.y + dy / length * firstDistance;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) firstX, (float) firstY);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        fillPathStroke(graphics, visibleLength, -3, 4, style.borderColor());
        double zoom = screen.graphViewport.state().zoom();
        fillPathStroke(graphics, visibleLength, -2, 3, style.strokeColor());
        // Keep overview graphs clean; the 3x5 texture becomes a visible block when enlarged.
        if (zoom >= MIN_DEPENDENCY_TEXTURE_ZOOM) {
            long visibleTileCount = tiles.endTileExclusive() - tiles.firstTile();
            long tilesPerMarker = Math.max(
                1,
                (long) Math.ceil(DEPENDENCY_TEXTURE_MARKER_SPACING / (zoom * DEPENDENCY_TILE_SIZE))
            );
            int markerCount = (int) Math.min(
                MAX_DEPENDENCY_TEXTURE_MARKERS_PER_PATH,
                Math.max(1, visibleTileCount / tilesPerMarker)
            );
            long markerStride = Math.max(1, visibleTileCount / (markerCount + 1L));
            for (int marker = 1; marker <= markerCount; marker++) {
                long tileOffset = Math.min(visibleTileCount - 1, markerStride * marker);
                long pathPixel = safeTilePixel(tiles.firstTile() + tileOffset);
                int tileWidth = (int) Math.min(
                    DEPENDENCY_TILE_SIZE,
                    tiles.pixelLength() - pathPixel
                );
                if (tileWidth <= 0) continue;
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    DEPENDENCY_ARROW,
                    (int) safeTilePixel(tileOffset),
                    -2,
                    0.0f,
                    0.0f,
                    tileWidth,
                    5,
                    DEPENDENCY_TILE_SIZE,
                    5,
                    style.arrowTint()
                );
            }
        }
        graphics.pose().popMatrix();
    }

    private static long safeTilePixel(long tileIndex) {
        if (tileIndex <= 0) return 0;
        if (tileIndex > Long.MAX_VALUE / DEPENDENCY_TILE_SIZE) return Long.MAX_VALUE;
        return tileIndex * DEPENDENCY_TILE_SIZE;
    }

    private static void fillPathStroke(
        GuiGraphicsExtractor graphics,
        long length,
        int top,
        int bottom,
        int color
    ) {
        long offset = 0;
        while (offset < length) {
            int chunkLength = (int) Math.min(1_000_000_000L, length - offset);
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) offset, 0);
            graphics.fill(0, top, chunkLength, bottom, color);
            graphics.pose().popMatrix();
            offset += chunkLength;
        }
    }

    static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, path);
    }

    private void drawChapterName(GuiGraphicsExtractor graphics) {
        if (!screen.mode.isAuthoring()) graphics.text(
            screen.guiFont(),
            Component.literal(screen.group),
            screen.layout.sidebarWidth() + 10,
            10,
            0xFFB8C0CC,
            false
        );
    }

    void drawBaseForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!screen.editorMessage.isEmpty() && !screen.authoring.open) {
            HeaderLayout header = screen.layout.headerLayout();
            int x = screen.layout.sidebarWidth() + 8;
            int right = Math.max(x + 1, screen.layout.canvasRight() - 4);
            int boxTop = header.statusY() - HEADER_STATUS_PADDING;
            int boxBottom = boxTop + header.statusBoxHeight();
            graphics.fill(x - 4, boxTop, right, boxBottom, 0xAA20242B);
            graphics.enableScissor(x, boxTop, right, boxBottom);
            for (int line = 0; line < header.statusLines().size(); line++) {
                graphics.text(
                    screen.guiFont(),
                    Component.literal(header.statusLines().get(line)),
                    x,
                    header.statusY() + line * HEADER_STATUS_LINE_HEIGHT,
                    screen.editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999,
                    false
                );
            }
            graphics.disableScissor();
        }
        if (screen.sidebarOpen) graphics.text(
            screen.guiFont(),
            Component.literal("Theseus"),
            8,
            4,
            0xFFFFFFFF,
            true
        );
        if (screen.mode.isAuthoring() && screen.mode.editorTool() == EditorTool.LINK) {
            graphics.text(
                screen.guiFont(),
                Component.translatable(screen.linkSourceId == null
                    ? "gui.theseus.editor.link_select_prerequisite"
                    : "gui.theseus.editor.link_select_dependent"),
                screen.layout.sidebarWidth() + 112,
                10,
                0xFF9FDFFF,
                false
            );
        }
        drawChapterScrollbar(graphics);
        if (screen.authoring.open) screen.authoringPanel.dockUi.drawCreateQuestDock(
            graphics, mouseX, mouseY, screen.editorMessage, screen.editorMessageSuccess
        );
        else if (screen.detailsOpen) screen.detailsPanel.render(
            graphics, screen.guiFont(), screen.guiWidth(), screen.guiHeight(), screen.layout.detailsWidth(), screen.actions.detailPanelModel(), mouseX, mouseY
        );
        drawContextMenu(graphics, mouseX, mouseY);
    }

    void drawChapterScrollbar(GuiGraphicsExtractor graphics) {
        if (!screen.sidebarOpen || !screen.chapterListState.hasOverflow()) return;
        int top = screen.chapterListState.viewportTop();
        int bottom = screen.chapterListState.viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int thumbHeight = Math.max(
            8,
            trackHeight * screen.chapterListState.visibleCapacity() / Math.max(1, screen.chapterListState.chapterCount())
        );
        int maxScroll = screen.chapterListState.maxFirstVisibleRow();
        int thumbY = top + (trackHeight - thumbHeight) * screen.chapterListState.firstVisibleRow()
            / Math.max(1, maxScroll);
        int x = Math.max(0, screen.layout.sidebarWidth() - 5);
        graphics.fill(x, top, x + 2, bottom, 0x6649515E);
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight,
            ClientThemeLoader.active().genericControls().accent());
    }

    void renderMinimap(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface
    ) {
        QuestMinimap.MapBounds mapBounds = screen.minimapPanel.bounds(screen.layout.graphCanvasBounds(), screen.layout.minimapSettings());
        if (mapBounds == null) return;
        screen.minimapPanel.render(
            graphics,
            screen.guiFont(),
            mapBounds,
            minimapScene(surface),
            ClientThemeLoader.active().genericControls().accent(),
            ClientThemeLoader.active().genericControls().text()
        );
    }

    QuestMinimapPanel.Scene minimapScene(QuestSurfaceLayout.Layout surface) {
        List<QuestMinimapPanel.Edge> edges = new ArrayList<>();
        for (ClientQuest quest : screen.actions.visibleQuests()) {
            QuestSurfaceLayout.Node child = surface.find(quest.definition().id()).orElse(null);
            if (child == null || !quest.definition().settings().showDependencyArrow()) continue;
            QuestGraphLayout.Point childCenter = screen.layout.questCenter(quest);
            for (String dependency : quest.definition().dependencies()) {
                if (surface.find(dependency).isEmpty()) continue;
                QuestGraphLayout.Point parentCenter = screen.layout.questCenter(screen.actions.questById(dependency));
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
        for (ClientQuest quest : screen.actions.visibleQuests()) {
            QuestSurfaceLayout.Node node = surface.find(quest.definition().id()).orElse(null);
            if (node == null) continue;
            QuestGraphLayout.Point center = screen.layout.questCenter(quest);
            markers.add(new QuestMinimapPanel.Marker(
                center.x(),
                center.y(),
                node.minimapMarkSize(),
                QuestPresentation.nodeStateColor(quest.unlocked(), quest.claimed(), quest.complete()),
                quest.definition().id().equals(screen.selectedQuestId)
            ));
        }
        return new QuestMinimapPanel.Scene(
            surface.worldBounds(16),
            QuestGraphLayout.visibleWorld(screen.layout.graphCanvasBounds(), screen.graphViewport.state()),
            edges,
            markers
        );
    }

    void drawRawInspector(GuiGraphicsExtractor graphics) {
        int inspectorWidth = Math.min(480, screen.guiWidth() - 32);
        int inspectorHeight = Math.min(280, screen.guiHeight() - 48);
        int left = (screen.guiWidth() - inspectorWidth) / 2;
        int top = (screen.guiHeight() - inspectorHeight) / 2;
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0xCC000000);
        graphics.fill(left, top, left + inspectorWidth, top + inspectorHeight, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + inspectorWidth - 1, top + 28, 0xFF303640);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.read_only_title", screen.rawInspectorTitle), left + 12, top + 9, 0xFFFFFFFF, true);
    }

    void drawPanelScrims(GuiGraphicsExtractor graphics) {
        int sidebarWidth = screen.layout.sidebarWidth();
        graphics.fill(0, 0, sidebarWidth, screen.guiHeight(), 0xF020242B);
        graphics.verticalLine(sidebarWidth, 0, screen.guiHeight(), 0xFF49515E);
        if (screen.detailsOpen || screen.authoring.open) {
            int detailsLeft = screen.guiWidth() - screen.layout.detailsWidth();
            graphics.fill(detailsLeft, 0, screen.guiWidth(), screen.guiHeight(), 0xD020242B);
            graphics.verticalLine(detailsLeft, 0, screen.guiHeight(), 0xAA49515E);
        }
    }

    void drawGraphGrid(GuiGraphicsExtractor graphics) {
        if (!TheseusClientOptions.showGrid()) return;
        double screenSpacing = QuestGraphLayout.GRID_CELL_SIZE * screen.graphViewport.state().zoom();
        if (screenSpacing < 1.5) return;
        QuestGraphLayout.WorldBounds visible = QuestGraphLayout.visibleWorld(
            screen.layout.graphCanvasBounds(), screen.graphViewport.state()
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

    void drawCreateQuestPreview(GuiGraphicsExtractor graphics) {
        if (!screen.mode.isAuthoring() || !screen.authoring.open) return;
        QuestDefinition definition = QuestDefinition.parse("editor", screen.editor.currentAuthoringDraft().snapshot());
        QuestSurfaceLayout.Node node = screen.editor.authoringNodeLayout();
        QuestBackground background = questBackground(screen.authoring.background);
        drawQuestBackground(graphics, node, background.texture(), 0, 0xCCFFFFFF);
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

    void drawPickerPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = pickerLeft();
        int top = pickerTop();
        graphics.fill(left, top, left + 200, top + 176, 0xFF20242B);
        graphics.outline(left, top, 200, 176, 0xFF8A929F);
        graphics.text(
            screen.guiFont(),
            Component.translatable(switch (screen.picker) {
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

    void drawDescriptionEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int modalWidth = Math.min(760, screen.guiWidth() - 24);
        int modalHeight = Math.min(420, screen.guiHeight() - 24);
        int left = (screen.guiWidth() - modalWidth) / 2;
        int top = (screen.guiHeight() - modalHeight) / 2;
        int gutter = 8;
        int paneWidth = (modalWidth - 32 - gutter) / 2;
        int previewX = left + 12 + paneWidth + gutter;
        int previewY = top + 61;
        int previewHeight = modalHeight - 103;
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        graphics.fill(left, top, left + modalWidth, top + modalHeight, 0xFF20242B);
        graphics.outline(left, top, modalWidth, modalHeight, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.rich_description"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.markdown"), left + 12, top + 52, 0xFFB8C0CC, false);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.player_preview"), previewX, top + 52, 0xFFB8C0CC, false);
        int editorX = left + 12;
        int editorY = top + 61;
        graphics.fill(editorX, editorY, editorX + paneWidth, editorY + (modalHeight - 103), 0xFF171A20);
        graphics.outline(editorX, editorY, paneWidth, modalHeight - 103, 0xFF49515E);
        graphics.fill(previewX, previewY, previewX + paneWidth, previewY + previewHeight, 0xFF171A20);
        graphics.enableScissor(previewX + 1, previewY + 1, previewX + paneWidth - 1, previewY + previewHeight - 1);
        DescriptionDocument document = DescriptionParser.parse(List.of(screen.descriptionEditorValue.split("\n", -1)));
        QuestDescriptionRenderer.Result result = QuestDescriptionRenderer.render(
            graphics, screen.guiFont(), document, previewX + 7, previewY + 7 - screen.descriptionPreviewScroll, paneWidth - 14,
            (kind, id) -> screen.editor.descriptionDraftReference(kind, id)
        );
        screen.descriptionPreviewMaxScroll = Math.max(0, result.height() - previewHeight + 14);
        screen.descriptionPreviewScroll = Math.min(screen.descriptionPreviewScroll, screen.descriptionPreviewMaxScroll);
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

    void drawPickerContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen.picker == Picker.ICON) drawItemPicker(graphics, mouseX, mouseY);
        else if (screen.picker == Picker.ENTITY) drawEntityPicker(graphics, mouseX, mouseY);
        else drawBackgroundPicker(graphics, mouseX, mouseY);
    }

    void drawDeleteQuestConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = (screen.guiWidth() - 240) / 2;
        int top = (screen.guiHeight() - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.confirm_delete_quest"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(screen.guiFont(), Component.translatable("gui.theseus.editor.this_deletes_the_quest_file_and_resets_its_player_progress"), left + 12, top + 32, 216, 0xFFFFAAAA, false);
    }

    void drawProgressResetConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = (screen.guiWidth() - 280) / 2;
        int top = (screen.guiHeight() - 142) / 2;
        graphics.fill(left, top, left + 280, top + 142, 0xFF20242B);
        graphics.outline(left, top, 280, 142, 0xFFFF6B6B);
        QuestModalHost.ProgressResetTarget target = screen.progressResetTarget;
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
        graphics.text(screen.guiFont(), title, left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(screen.guiFont(), detail, left + 12, top + 34, 256, 0xFFFFC4C4, false);
    }

    void drawDeleteTaskConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = (screen.guiWidth() - 240) / 2;
        int top = (screen.guiHeight() - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.confirm_delete_task"), left + 12, top + 12, 0xFFFFFFFF, true);
        String id = screen.authoring.taskDeleteConfirmation >= 0 && screen.authoring.taskDeleteConfirmation < screen.authoring.tasks.size()
            ? screen.authoring.tasks.get(screen.authoring.taskDeleteConfirmation).id : "this task";
        graphics.textWithWordWrap(screen.guiFont(), Component.translatable("gui.theseus.editor.confirm_delete_task_body", id), left + 12, top + 34, 216, 0xFFFFAAAA, false);
    }

    void drawDiscardConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = (screen.guiWidth() - 260) / 2;
        int top = (screen.guiHeight() - 116) / 2;
        graphics.fill(left, top, left + 260, top + 116, 0xFF20242B);
        graphics.outline(left, top, 260, 116, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.discard_unsaved_changes"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(screen.guiFont(), Component.translatable("gui.theseus.editor.the_quest_draft_has_changes_that_have_not_been_saved"), left + 12, top + 34, 236, 0xFFFFCC88, false);
    }

    void drawChapterEditor(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        int left = (screen.guiWidth() - 280) / 2;
        int top = chapterEditorTop();
        graphics.fill(left, top, left + 280, top + 250, 0xFF20242B);
        graphics.outline(left, top, 280, 250, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable(screen.chapterEditorOriginal == null
            ? "gui.theseus.editor.create_chapter"
            : "gui.theseus.editor.edit_chapter"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.name"), left + 14, top + 36, 0xFFB8C0CC, false);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.chapter_icon"), left + 56, top + 88, 0xFFB8C0CC, false);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.background"), left + 14, top + 114, 0xFFB8C0CC, false);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.background_opacity"), left + 14, top + 146, 0xFFB8C0CC, false);
        if (!screen.chapterEditorError.isEmpty()) graphics.text(screen.guiFont(), Component.literal(screen.chapterEditorError), left + 14, top + 185, 0xFFFF7777, false);
    }

    void drawPasteIdPrompt(GuiGraphicsExtractor graphics) {
        int left = (screen.guiWidth() - 280) / 2;
        int top = (screen.guiHeight() - 130) / 2;
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x88000000);
        graphics.fill(left, top, left + 280, top + 130, 0xFF20242B);
        graphics.outline(left, top, 280, 130, 0xFF8A929F);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.paste_quest"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.choose_the_id_for_the_cloned_quest"), left + 14, top + 34, 0xFFB8C0CC, false);
    }

    void drawEntityPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<EntityType<?>> entities = filteredPickerEntities();
        int end = Math.min(entities.size(), screen.pickerScroll + 40);
        for (int index = screen.pickerScroll; index < end; index++) {
            int visible = index - screen.pickerScroll;
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

    void drawItemPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<Item> items = filteredPickerItems();
        int end = Math.min(items.size(), screen.pickerScroll + 40);
        for (int index = screen.pickerScroll; index < end; index++) {
            int visible = index - screen.pickerScroll;
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

    void drawBackgroundPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
                x + (40 - background.width()) / 2,
                y + (34 - background.height()) / 2,
                0.0f,
                0.0f,
                background.width(),
                background.height(),
                background.width() * 5,
                background.height(),
                0xFFFFFFFF
            );
        }
    }

    void drawDependencyPaths(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface,
        QuestGraphLayout.WorldBounds visibleWorld
    ) {
        Map<String, ClientQuest> questsById = new HashMap<>();
        for (ClientQuest quest : screen.quests) {
            questsById.put(quest.definition().id(), quest);
        }
        for (ClientQuest quest : screen.actions.visibleQuests()) {
            QuestSurfaceLayout.Node child = dependencyNode(surface, quest.definition().id());
            boolean showArrow = quest.definition().settings().showDependencyArrow();
            if (screen.authoring.open && screen.authoring.editingExisting
                && quest.definition().id().equals(screen.authoring.originalId)) {
                showArrow = screen.authoring.showDependencyArrow;
            }
            if (
                child == null ||
                !showArrow
            ) continue;
            for (String dependency : quest.definition().dependencies()) {
                QuestSurfaceLayout.Node parent = dependencyNode(surface, dependency);
                if (parent == null) continue;
                PathPoint parentCenter = new PathPoint(parent.centerX(), parent.centerY());
                PathPoint childCenter = new PathPoint(child.centerX(), child.centerY());
                ClientQuest prerequisiteQuest = questsById.get(dependency);
                PathStyle style = dependencyPathStyle(prerequisiteQuest, quest);
                // Nodes render after connectors, so center-to-center paths disappear cleanly beneath the frames.
                PathPoint start = parentCenter;
                PathPoint tip = childCenter;
                double dx = tip.x() - start.x();
                double dy = tip.y() - start.y();
                double length = Math.hypot(dx, dy);
                if (length < 4.0) continue;
                drawTexturedPath(graphics, start, tip, style, visibleWorld);
            }
        }
    }

    private PathStyle dependencyPathStyle(ClientQuest prerequisite, ClientQuest child) {
        if (prerequisite == null) return child.unlocked() ? UNLOCKED_PATH : GRAY_PATH;
        if (prerequisite.complete()) return COMPLETED_PATH;
        return prerequisite.definition().id().equals(screen.selectedQuestId)
            ? SELECTED_INCOMPLETE_PATH
            : GRAY_PATH;
    }

    QuestSurfaceLayout.Node dependencyNode(
        QuestSurfaceLayout.Layout surface,
        String questId
    ) {
        QuestSurfaceLayout.Node node = surface.find(questId).orElse(null);
        if (node != null) return node;
        if (screen.authoring.open && screen.authoring.editingExisting && questId.equals(screen.authoring.originalId)) {
            return screen.editor.authoringNodeLayout();
        }
        return null;
    }

    void drawLinkPreview(
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY,
        QuestGraphLayout.WorldBounds visibleWorld
    ) {
        if (!screen.mode.isAuthoring() || screen.mode.editorTool() != EditorTool.LINK || screen.linkSourceId == null) return;
        ClientQuest source = screen.actions.questById(screen.linkSourceId);
        if (source == null) return;
        QuestGraphLayout.Point sourceCenter = screen.layout.questCenter(source);
        drawTexturedPath(
            graphics,
            new PathPoint(sourceCenter.x(), sourceCenter.y()),
            new PathPoint(mouseX, mouseY),
            UNLOCKED_PATH,
            visibleWorld
        );
    }

    void drawQuestNodes(
        GuiGraphicsExtractor graphics,
        QuestSurfaceLayout.Layout surface,
        double mouseX,
        double mouseY,
        boolean hoverEnabled
    ) {
        for (ClientQuest quest : screen.actions.visibleQuests()) {
            if (screen.authoring.editingExisting && screen.authoring.open && quest.definition().id().equals(screen.authoring.originalId)) continue;
            QuestSurfaceLayout.Node node = surface.find(quest.definition().id()).orElse(null);
            if (node == null) continue;
            QuestGraphLayout.NodeBounds bounds = node.bounds();
            QuestBackground background = questBackground(quest.definition());
            int frame = quest.claimed() ? 3 : quest.complete() ? 2 : quest.unlocked() ? 1 : 0;
            drawQuestBackground(graphics, node, background.texture(), frame, 0xFFFFFFFF);
            if (hoverEnabled && node.contains(mouseX, mouseY)) {
                drawQuestBackground(graphics, node, background.texture(), 4, 0xFFFFFFFF);
            }
            int nodeX = (int) Math.round(bounds.x());
            int nodeY = (int) Math.round(bounds.y());
            int nodeWidth = (int) Math.round(bounds.width());
            int nodeHeight = (int) Math.round(bounds.height());
            if (quest.definition().id().equals(screen.selectedQuestId)) {
                graphics.outline(
                    nodeX - 2,
                    nodeY - 2,
                    nodeWidth + 4,
                    nodeHeight + 4,
                    0xFFFFD966
                );
            }
            if (quest.definition().id().equals(screen.linkSourceId)) {
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
                quest.definition(),
                (int) Math.round(icon.x()),
                (int) Math.round(icon.y()),
                (int) Math.round(icon.width())
            );
        }
    }

    int pickerLeft() {
        return (screen.guiWidth() - 200) / 2;
    }

    int pickerTop() {
        return (screen.guiHeight() - 176) / 2;
    }

    int chapterEditorTop() {
        return (screen.guiHeight() - 250) / 2;
    }

    List<Item> filteredPickerItems() {
        String query = screen.pickerSearch == null
            ? ""
            : screen.pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
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

    List<EntityType<?>> filteredPickerEntities() {
        String query = screen.pickerSearch == null
            ? ""
            : screen.pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(entity -> {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
                return query.isEmpty() || id.toString().contains(query) ||
                    entity.getDescription().getString().toLowerCase(java.util.Locale.ROOT).contains(query);
            })
            .sorted(Comparator.comparing(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString()))
            .toList();
    }

    void drawClippedText(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (screen.guiFont().width(text) > maxWidth) {
            text = screen.guiFont().plainSubstrByWidth(text, Math.max(0, maxWidth - screen.guiFont().width("…"))) + "…";
        }
        graphics.text(screen.guiFont(), Component.literal(text), x, y, color, false);
    }

    void drawContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen.modalHost.blocksInput() || screen.contextMenu == null || !screen.contextMenu.isOpen()) return;
        screen.contextMenu.moveMouse(mouseX, mouseY);
        QuestContextMenu.Bounds menu = screen.contextMenu.bounds();
        int accent = ClientThemeLoader.active().genericControls().accent();
        graphics.fill(menu.x(), menu.y(), menu.maxX(), menu.maxY(), 0xF020242B);
        graphics.outline(menu.x(), menu.y(), menu.width(), menu.height(), accent);
        for (QuestContextMenu.Row row : screen.contextMenu.rows()) {
            QuestContextMenu.Entry entry = row.entry();
            if (entry.isSeparator()) {
                graphics.fill(row.x() + 4, row.y() + 3, row.x() + row.width() - 4, row.y() + 4, 0xFF49515E);
                continue;
            }
            boolean active = entry.enabled() &&
                (row.entryIndex() == screen.contextMenu.hoveredIndex() || row.entryIndex() == screen.contextMenu.selectedIndex());
            if (active) graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF454C58);
            if (row.entryIndex() == screen.contextMenu.selectedIndex()) {
                graphics.outline(row.x(), row.y(), row.width(), row.height(), ClientThemeLoader.active().genericControls().accent());
            }
            int labelColor = !entry.enabled() ? 0xFF9AA4B2 : entry.danger() ? 0xFFFF9999 : 0xFFFFFFFF;
            Component label = entry.enabled()
                ? Component.literal(entry.label())
                : Component.translatable("gui.theseus.editor.disabled_menu_label", entry.label());
            graphics.text(screen.guiFont(), label, row.x() + 6, row.y() + 6, labelColor, false);
            if (!entry.shortcut().isBlank()) {
                graphics.text(screen.guiFont(), Component.literal(entry.shortcut()), row.x() + row.width() - screen.guiFont().width(entry.shortcut()) - 6, row.y() + 6, 0xFF9AA4B2, false);
            }
        }
    }

    record PathPoint(double x, double y) {}

    record QuestBackground(Identifier texture, int xOffset, int yOffset, int width, int height) {}
}
