package me.johardt.theseus.client;

import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import me.johardt.theseus.client.description.DescriptionDocument;
import me.johardt.theseus.client.description.DescriptionParser;
import me.johardt.theseus.client.description.QuestDescriptionRenderer;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.List;

/** Scrollable, resource-backed onboarding for quest editors. */
public final class QuestTutorialScreen extends Screen {
    private static final int CONTENT_MARGIN = 16;
    private static final int FOOTER_HEIGHT = 38;
    private final Screen parent;
    private final DescriptionDocument document;
    private List<QuestDescriptionRenderer.Interaction> interactions = List.of();
    private int contentScroll;
    private int contentHeight;
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int contentBottom;

    public QuestTutorialScreen(Screen parent) {
        super(Component.translatable("screen.theseus.quest_tutorial"));
        this.parent = parent;
        this.document = DescriptionParser.parse(
            Arrays.asList(QuestTutorialContent.text().split("\\R", -1))
        );
    }

    @Override
    protected void init() {
        super.init();
        contentLeft = Math.max(CONTENT_MARGIN, (width - Math.min(720, width - CONTENT_MARGIN * 2)) / 2);
        contentWidth = Math.max(1, Math.min(720, width - CONTENT_MARGIN * 2));
        contentTop = 28;
        contentBottom = Math.max(contentTop + 1, height - FOOTER_HEIGHT);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(contentLeft, height - 28).withSize(146, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable(
                TheseusClientOptions.tutorialAutoShow()
                    ? "screen.theseus.tutorial.auto_show_on"
                    : "screen.theseus.tutorial.auto_show_off"
            )));
            widget.withCallback(() -> {
                TheseusClientOptions.setTutorialAutoShow(!TheseusClientOptions.tutorialAutoShow());
                rebuildWidgets();
            });
            widget.withTooltip(Component.translatable("screen.theseus.tutorial.auto_show.tooltip"));
        }));
        Button done = Widgets.button(widget -> {
            widget.withPosition(width - contentLeft - 100, height - 28).withSize(100, 20);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.done")));
            widget.withCallback(this::closeToParent);
        });
        addRenderableWidget(done);
    }

    @Override
    public void extractBackground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        var theme = ClientThemeLoader.active();
        graphics.fill(0, 0, width, height, 0xF015171C);
        graphics.fill(contentLeft - 8, 20, contentLeft + contentWidth + 8, contentBottom + 4,
            theme.modals().background());
        graphics.outline(contentLeft - 8, 20, contentWidth + 16, contentBottom - 16,
            theme.genericControls().accent());
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        graphics.text(font, getTitle(), contentLeft, 8, ClientThemeLoader.active().modals().title(), true);
        graphics.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentBottom);
        QuestDescriptionRenderer.Result result = QuestDescriptionRenderer.render(
            graphics,
            font,
            document,
            contentLeft,
            contentTop - contentScroll,
            contentWidth,
            (kind, id) -> id
        );
        contentHeight = result.height();
        interactions = result.interactions();
        graphics.disableScissor();
        drawScrollbar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int trackX = contentLeft + contentWidth + 4;
        int trackHeight = Math.max(1, contentBottom - contentTop);
        int thumbHeight = Math.max(12, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
        int thumbY = contentTop + (trackHeight - thumbHeight) * contentScroll / maxScroll;
        graphics.fill(trackX, contentTop, trackX + 2, contentBottom, 0x6649515E);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
            ClientThemeLoader.active().genericControls().accent());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= contentLeft && mouseX < contentLeft + contentWidth
            && mouseY >= contentTop && mouseY < contentBottom) {
            contentScroll = Math.clamp(
                contentScroll - (int) Math.round(scrollY * 18),
                0,
                maxScroll()
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.input() == 0 && event.x() >= contentLeft && event.x() < contentLeft + contentWidth
            && event.y() >= contentTop && event.y() < contentBottom) {
            for (QuestDescriptionRenderer.Interaction interaction : interactions) {
                if (!interaction.contains(event.x(), event.y())) continue;
                Style style = interaction.clickStyle();
                if (style != null && style.getClickEvent() != null) {
                    defaultHandleClickEvent(style.getClickEvent(), Minecraft.getInstance(), this);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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

    private int maxScroll() {
        return Math.max(0, contentHeight - Math.max(0, contentBottom - contentTop));
    }

    private void closeToParent() {
        Minecraft.getInstance().setScreen(parent);
    }
}
