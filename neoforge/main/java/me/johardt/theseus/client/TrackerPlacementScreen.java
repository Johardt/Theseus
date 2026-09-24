package me.johardt.theseus.client;

import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import com.teamresourceful.resourcefullib.common.color.Color;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/** Small editor for the pinned tracker anchor with a live preview. */
public final class TrackerPlacementScreen extends Screen {
    private static final int SAMPLE_WIDTH = 168;
    private static final int SAMPLE_HEIGHT = 19 + 15 + 2 * 11;
    private static final int SCREEN_MARGIN = 16;
    private static final int PANEL_PADDING = 8;
    private static final int PANEL_HEADER_HEIGHT = 34;
    private static final int GRID_GAP = 8;
    private static final int CELL_HEIGHT = 25;
    private static final int DONE_HEIGHT = 20;
    private static final int DONE_GAP = 8;
    private static final Color BUTTON_TEXT = Color.parse("#26313D");
    private static final Color BUTTON_HOVER = Color.parse("#164A7A");
    private static final Color BUTTON_SELECTED_HOVER = Color.parse("#12529E");
    private static final Identifier HEADER = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "pinned/pinned_fake_popup_background"
    );
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "pinned/pinned_fake_popup_border"
    );

    private final Screen parent;
    private TheseusClientOptions.TrackerAnchor selected;
    private int gridLeft;
    private int gridTop;
    private int cellWidth;
    private int cellHeight;
    private int panelTop;
    private int panelBottom;
    private int panelWidth;
    private int doneY;

    public TrackerPlacementScreen(Screen parent) {
        super(Component.translatable("screen.theseus.tracker_placement"));
        this.parent = parent;
        this.selected = TheseusClientOptions.trackerAnchor();
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.max(1, Math.min(360, width - SCREEN_MARGIN * 2));
        int panelHeight = PANEL_PADDING + PANEL_HEADER_HEIGHT + 4 * CELL_HEIGHT
            + DONE_GAP + DONE_HEIGHT + PANEL_PADDING;
        panelTop = Math.max(0, (height - panelHeight) / 2);
        panelBottom = Math.min(height, panelTop + panelHeight);
        gridLeft = Math.max(0, (width - panelWidth) / 2);
        gridTop = panelTop + PANEL_PADDING + PANEL_HEADER_HEIGHT;
        cellWidth = Math.max(1, (panelWidth - GRID_GAP) / 2);
        cellHeight = CELL_HEIGHT;
        doneY = Math.max(gridTop, panelBottom - PANEL_PADDING - DONE_HEIGHT);
        TheseusClientOptions.TrackerAnchor[] anchors = TheseusClientOptions.TrackerAnchor.values();
        for (int index = 0; index < anchors.length; index++) {
            TheseusClientOptions.TrackerAnchor anchor = anchors[index];
            int column = index / 4;
            int row = index % 4;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(gridLeft + column * (cellWidth + 8), gridTop + row * cellHeight)
                    .withSize(cellWidth, cellHeight - 3);
                Color normal = anchor == selected
                    ? new Color(ClientThemeLoader.active().genericControls().accent())
                    : BUTTON_TEXT;
                Color hover = anchor == selected ? BUTTON_SELECTED_HOVER : BUTTON_HOVER;
                widget.withRenderer(WidgetRenderers.withColors(
                    WidgetRenderers.text(Component.translatable(anchorKey(anchor))),
                    normal,
                    normal,
                    hover
                ));
                widget.withCallback(() -> select(anchor));
            }));
        }
        Button done = Widgets.button(widget -> {
            widget.withPosition(gridLeft + panelWidth - 100 - PANEL_PADDING, doneY)
                .withSize(100, DONE_HEIGHT);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.done")));
            widget.withCallback(this::closeToParent);
        });
        addRenderableWidget(done);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0xF015171C);
        graphics.fill(gridLeft - 4, panelTop, gridLeft + panelWidth + 4, panelBottom, 0xE820242B);
        graphics.outline(gridLeft - 4, panelTop, panelWidth + 8, panelBottom - panelTop,
            ClientThemeLoader.active().genericControls().accent());
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        int textLeft = gridLeft + PANEL_PADDING;
        graphics.text(font, getTitle(), textLeft, panelTop + 7, ClientThemeLoader.active().modals().title(), true);
        graphics.text(
            font,
            Component.translatable("screen.theseus.tracker_placement.instructions"),
            textLeft,
            panelTop + 19,
            ClientThemeLoader.active().genericControls().text(),
            false
        );
        QuestHudLayout.Bounds preview = QuestHudLayout.layout(
            width,
            height,
            SAMPLE_WIDTH,
            SAMPLE_HEIGHT,
            selected
        );
        if (preview.width() > 0 && preview.height() > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEADER, preview.x(), preview.y(), preview.width(), Math.min(10, preview.height()));
            if (preview.height() > 10) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BODY, preview.x(), preview.y() + 10, preview.width(), preview.height() - 10);
            graphics.text(font, Component.translatable("hud.theseus.pinned_quests.title"), preview.x() + 6, preview.y() + 2, ClientThemeLoader.active().tracker().title(), true);
            int questY = preview.y() + 13;
            graphics.text(
                font,
                Component.translatable("screen.theseus.tracker.preview_quest_title"),
                preview.x() + 6,
                questY,
                ClientThemeLoader.active().tracker().quest(),
                true
            );
            graphics.text(
                font,
                Component.translatable("screen.theseus.tracker.preview_task_one"),
                preview.x() + 10,
                questY + 12,
                ClientThemeLoader.active().tracker().task(),
                false
            );
            graphics.text(
                font,
                Component.translatable("screen.theseus.tracker.preview_task_one_progress"),
                preview.x() + preview.width() - 6 - font.width(
                    Component.translatable("screen.theseus.tracker.preview_task_one_progress")
                ),
                questY + 12,
                ClientThemeLoader.active().tracker().progress(),
                false
            );
            graphics.text(
                font,
                Component.translatable("screen.theseus.tracker.preview_task_two"),
                preview.x() + 10,
                questY + 23,
                ClientThemeLoader.active().tracker().completed(),
                false
            );
            graphics.text(
                font,
                Component.translatable("screen.theseus.tracker.preview_task_two_progress"),
                preview.x() + preview.width() - 6 - font.width(
                    Component.translatable("screen.theseus.tracker.preview_task_two_progress")
                ),
                questY + 23,
                ClientThemeLoader.active().tracker().completed(),
                false
            );
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            closeToParent();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void select(TheseusClientOptions.TrackerAnchor anchor) {
        selected = anchor;
        TheseusClientOptions.setTrackerAnchor(anchor);
        rebuildWidgets();
    }

    private void closeToParent() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static String anchorKey(TheseusClientOptions.TrackerAnchor anchor) {
        return "screen.theseus.tracker.anchor." + anchor.name().toLowerCase(java.util.Locale.ROOT);
    }
}
