package me.johardt.theseus.client;

import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.base.renderer.WidgetRenderer;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.ArrayList;
import java.util.List;
import me.johardt.theseus.client.QuestClientSnapshot.ChapterDisplay;
import me.johardt.theseus.client.QuestClientSnapshot.ClientQuest;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestNetwork;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Calculates graph, header, sidebar, and dock geometry and builds layout widgets. */
final class QuestScreenLayout {
    private final QuestScreen screen;

    QuestScreenLayout(QuestScreen screen) {
        this.screen = screen;
    }

    QuestSurfaceLayout.QuestNode surfaceNode(ClientQuest quest) {
        QuestDefinition.GroupDisplay position = quest.definition().position(screen.group);
        return new QuestSurfaceLayout.QuestNode(
            quest.definition().id(),
            position.x(),
            position.y(),
            quest.definition().display().iconSize(),
            quest.definition().display().iconBackground()
        );
    }

    QuestSurfaceLayout.Layout surfaceLayout() {
        return QuestSurfaceLayout.layout(
            screen.actions.visibleQuests().stream()
                .filter(quest -> !screen.authoring.editingExisting || !screen.authoring.open
                    || !quest.definition().id().equals(screen.authoring.originalId))
                .map(this::surfaceNode)
                .toList(),
            graphCanvasBounds(),
            screen.graphViewport.state()
        );
    }

    QuestGraphLayout.Point questCenter(ClientQuest quest) {
        if (quest == null) return new QuestGraphLayout.Point(0, 0);
        QuestDefinition.GroupDisplay position = quest.definition().position(screen.group);
        return new QuestGraphLayout.Point(position.x(), position.y());
    }

    HeaderLayout headerLayout() {
        return headerLayout(canvasRight());
    }

    HeaderLayout graphHeaderLayout() {
        return headerLayout(graphCanvasRight());
    }

    HeaderLayout headerLayout(int right) {
        int editX = right - 23;
        int helpX = editX - 27;
        int fitX = helpX - 27;
        int gridX = fitX - 27;
        int snapX = gridX - 27;
        boolean editorDockOpen = screen.authoring.open;
        int nextActionX = editorDockOpen
            ? helpX
            : screen.mode.isAuthoring() ? snapX : fitX;
        int diagnosticsX = -1;
        int importX = -1;
        if (!screen.diagnostics.isEmpty()) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            diagnosticsX = nextActionX;
        }
        if (screen.mode.isAuthoring() && !editorDockOpen) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            importX = nextActionX;
        }

        int toolLeft = sidebarWidth() + 24;
        int toolRight = screen.mode.isAuthoring()
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
        int statusRow = !screen.editorMessage.isEmpty() && !screen.authoring.open
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

    void addGraphNavigationWidgets(HeaderLayout header) {
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(header.helpX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.text(Component.literal("⋮"))
            ));
            widget.withCallback(() -> screen.actions.openDisplayMenu(header.helpX(), header.actionY() + HEADER_ROW_HEIGHT));
            widget.withTooltip(Component.translatable("screen.theseus.display_menu.tooltip"));
        }));
        if (!screen.authoring.open) screen.addScreenWidget(Widgets.button(widget -> {
                widget.withPosition(header.fitX(), header.actionY()).withSize(22, HEADER_ROW_HEIGHT);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.f")));
                widget.withCallback(this::fitGraphToContent);
                widget.withTooltip(Component.translatable("gui.theseus.editor.fit_visible_quests_in_the_graph"));
            }));
        if (!TheseusClientOptions.disableMinimap()
            && screen.minimapPanel.hidden()
            && !screen.detailsOpen
            && !screen.authoring.open) {
            QuestGraphLayout.CanvasBounds canvas = graphCanvasBounds();
            if (canvas.width() >= 22 && canvas.height() >= HEADER_ROW_HEIGHT) {
                screen.addScreenWidget(Widgets.button(widget -> {
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
                        screen.minimapPanel.setHidden(false);
                        screen.minimapPanel.clearTransientState();
                        screen.rebuildWidgets();
                    });
                    widget.withTooltip(Component.translatable("gui.theseus.editor.show_quest_minimap"));
                }));
            }
        }
        if (screen.mode.isAuthoring() && !screen.authoring.open) {
            screen.addScreenWidget(Widgets.button(widget -> {
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
                    screen.rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(visible ? "gui.theseus.editor.hide_graph_grid" : "gui.theseus.editor.show_graph_grid"));
            }));
            screen.addScreenWidget(Widgets.button(widget -> {
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
                    screen.rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(enabled ? "gui.theseus.editor.disable_snap_to_grid" : "gui.theseus.editor.enable_snap_to_grid"));
            }));
        }
    }

    void fitGraphToContent() {
        screen.graphViewport.fitToContent(graphCanvasBounds(), graphWorldBounds());
        screen.graphViewport.saveChapterViewport(screen.group);
        screen.rebuildWidgets();
    }

    void addDockWidgets() {
        if (screen.authoring.open) {
            screen.authoringPanel.dockUi.addCreateQuestDockWidgets();
            return;
        }
        if (!screen.detailsOpen) return;
        ClientQuest selected = screen.actions.selected();
        int detailsWidth = detailsWidth();
        int detailsLeft = screen.guiWidth() - detailsWidth;
        int pinLeft = screen.guiWidth() - 50;
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
                        tab == screen.detailTab
                            ? new Color(ClientThemeLoader.active().questDetails().tabButtonSelected())
                            : new Color(ClientThemeLoader.active().questDetails().tabButton())
                    )
                );
                widget.withCallback(() -> {
                    screen.detailTab = tab;
                    screen.detailsPanel.resetScroll();
                    screen.rebuildWidgets();
                });
                widget.withTooltip(Component.translatable(tab.translationKey));
            });
            screen.addScreenWidget(tabButton);
        }
        Button closeDetails = Widgets.button(widget -> {
            widget.withPosition(screen.guiWidth() - 27, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.center(11, 11, WidgetRenderers.sprite(CLOSE_BUTTON))
            );
            widget.withCallback(() -> {
                screen.detailsOpen = false;
                screen.selectedQuestId = null;
                screen.rebuildWidgets();
            });
            widget.withTooltip(Component.translatable("gui.theseus.editor.close_quest_details"));
        });
        screen.addScreenWidget(closeDetails);
        Button pin = Widgets.button(widget -> {
            widget.withPosition(pinLeft, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(
                        selected != null && selected.pinned() ? "★" : "☆"
                    )
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                if (selected != null) ClientPacketDistributor.sendToServer(
                    new QuestNetwork.ActionPayload(
                        "pin",
                        selected.definition().id()
                    )
                );
            });
            widget.active = selected != null && selected.unlocked();
            widget.withTooltip(
                Component.translatable(selected != null && selected.pinned()
                    ? "gui.theseus.editor.unpin_quest"
                    : "gui.theseus.editor.pin_quest")
            );
        });
        screen.addScreenWidget(pin);
        QuestScreenActions.TaskRef submittable =
            selected == null || !selected.unlocked()
                ? null
                : screen.actions.findSubmittable(
                      selected.definition().tasks(),
                      selected.progress(),
                      ""
                  );
        int actionWidth =
            submittable == null ? detailsWidth - 18 : (detailsWidth - 27) / 2;
        Button claim = Widgets.button(widget -> {
            widget
                .withPosition(detailsLeft + 9, screen.guiHeight() - 36)
                .withSize(actionWidth, 20);
            widget.withRenderer(
                WidgetRenderers.text(Component.translatable("gui.theseus.editor.claim_rewards"))
            );
            widget.withCallback(screen.actions::claimSelected);
            widget.active =
                selected != null &&
                selected.complete() &&
                !selected.claimed() &&
                !screen.mutations.isPending() &&
                screen.actions.canClaimRewards(selected);
            widget.withTooltip(Component.translatable("gui.theseus.editor.claim_rewards"));
            if (
                selected != null &&
                selected.complete() &&
                !selected.claimed() &&
                !screen.actions.canClaimRewards(selected)
            ) {
                widget.withTooltip(
                    Component.literal(screen.actions.claimBlockedReason(selected))
                );
            }
        });
        screen.addScreenWidget(claim);
        if (submittable != null) {
            Button submit = Widgets.button(widget -> {
                widget
                    .withPosition(detailsLeft + 18 + actionWidth, screen.guiHeight() - 36)
                    .withSize(actionWidth, 20);
                widget.withRenderer(
                    WidgetRenderers.text(Component.translatable("gui.theseus.editor.submit_task"))
                );
                widget.withCallback(() -> screen.actions.submitTask(selected, submittable));
                widget.active = !screen.mutations.isPending();
                widget.withTooltip(Component.translatable("gui.theseus.editor.submit_task"));
            });
            screen.addScreenWidget(submit);
        }
    }

    Button editorButton(
        int x,
        String icon,
        boolean selected,
        Component tooltip,
        Runnable callback
    ) {
        return Widgets.button(widget -> {
            widget.withPosition(x, 1).withSize(19, 20);
            Identifier texture = QuestScreenRenderer.sprite("heading/editor/" + icon + (selected ? "_selected" : ""));
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(new WidgetSprites(texture, texture))
            ));
            widget.withCallback(callback);
            widget.withTooltip(tooltip);
        });
    }

    WidgetRenderer<Button> listActionRenderer(String action) {
        return WidgetRenderers.center(
            EDITOR_LIST_ACTION_ICON_SIZE,
            EDITOR_LIST_ACTION_ICON_SIZE,
            WidgetRenderers.sprite(new WidgetSprites(
                QuestScreenRenderer.sprite("heading/editor/" + action),
                QuestScreenRenderer.sprite("heading/editor/" + action)
            ))
        );
    }

    WidgetRenderer<Button> chapterButtonRenderer(String chapter, boolean selected) {
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
            ChapterDisplay display = screen.chapterDisplays.get(chapter);
            int labelX = contentX + (chapterListHasIcons() ? CHAPTER_ICON_COLUMN_WIDTH : 0);
            if (display != null && display.iconEnabled()) {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(display.icon()));
                    if (item != null && item != Items.AIR) {
                        graphics.item(new ItemStack(item), contentX, context.getY() + 2);
                    }
                } catch (RuntimeException ignored) { }
            }
            graphics.enableScissor(labelX, context.getY(), context.getX() + context.getWidth() - 3, context.getY() + context.getHeight());
            int available = Math.max(0, context.getX() + context.getWidth() - 3 - labelX);
            String label = chapter;
            if (screen.guiFont().width(label) > available) {
                label = screen.guiFont().plainSubstrByWidth(label, Math.max(0, available - screen.guiFont().width("…"))) + "…";
            }
            graphics.text(screen.guiFont(), Component.literal(label), labelX, context.getY() + 6, 0xFFFFFFFF, false);
            graphics.disableScissor();
        };
    }

    int canvasRight() {
        return screen.detailsOpen || screen.authoring.open
            ? screen.guiWidth() - detailsWidth()
            : screen.guiWidth();
    }

    int graphCanvasRight() {
        return screen.guiWidth();
    }

    int canvasTop() {
        return headerLayout().canvasTop();
    }

    int graphCanvasTop() {
        return graphHeaderLayout().canvasTop();
    }

    QuestGraphLayout.CanvasBounds graphCanvasBounds() {
        int left = sidebarWidth();
        int top = graphCanvasTop();
        int right = graphCanvasRight();
        return new QuestGraphLayout.CanvasBounds(
            left,
            top,
            Math.max(0, right - left),
            Math.max(0, screen.guiHeight() - top)
        );
    }

    boolean detailsDockContains(double mouseX, double mouseY) {
        return (screen.detailsOpen || screen.authoring.open)
            && mouseX >= screen.guiWidth() - detailsWidth()
            && mouseX < screen.guiWidth()
            && mouseY >= 0
            && mouseY < screen.guiHeight();
    }

    QuestGraphLayout.WorldBounds graphWorldBounds() {
        return surfaceLayout().worldBounds(48);
    }

    QuestMinimapPanel.Settings minimapSettings() {
        return new QuestMinimapPanel.Settings(
            TheseusClientOptions.disableMinimap(),
            TheseusClientOptions.defaultMinimapMode(),
            TheseusClientOptions.minimapX(),
            TheseusClientOptions.minimapY()
        );
    }

    int chapterListBottom() {
        return screen.mode.isAuthoring()
            ? Math.max(CHAPTER_LIST_TOP, screen.guiHeight() - CHAPTER_ADD_SLOT_HEIGHT)
            : screen.guiHeight();
    }

    boolean chapterLabelRequiresTooltip(String chapter, int buttonWidth) {
        int iconColumn = chapterListHasIcons() ? CHAPTER_ICON_COLUMN_WIDTH : 0;
        int available = Math.max(1, buttonWidth - 6 - iconColumn);
        return screen.guiFont().width(chapter) > available;
    }

    boolean chapterListHasIcons() {
        List<String> ordered = new ArrayList<>(screen.actions.groups());
        return screen.chapterListState.visibleIndices().stream()
            .anyMatch(index -> index >= 0 && index < ordered.size()
                && screen.chapterDisplays.get(ordered.get(index)) != null
                && screen.chapterDisplays.get(ordered.get(index)).iconEnabled());
    }

    int sidebarWidth() {
        if (!screen.sidebarOpen) return COLLAPSED_SIDEBAR_WIDTH;
        return Math.max(96, Math.min(110, Math.round(screen.guiWidth() * 0.17f)));
    }

    int detailsWidth() {
        if (screen.authoring.open) {
            return Math.max(320, Math.min(360, Math.round(screen.guiWidth() * 0.38f)));
        }
        return Math.max(220, Math.min(240, Math.round(screen.guiWidth() * 0.38f)));
    }
}
