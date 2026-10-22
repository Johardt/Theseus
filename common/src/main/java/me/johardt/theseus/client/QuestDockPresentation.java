package me.johardt.theseus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Fixed-size dock content and disposable widgets share one sampled moving edge. */
final class QuestDockPresentation {
    enum Layer { NONE, LEFT, RIGHT, HEADER_LEFT, HEADER_RIGHT }
    private record PlacedWidget(AbstractWidget widget, int x) {}

    /** A fixed-width content layout behind its sampled, clipped screen edge. */
    record Bounds(int edge, int contentWidth, int screenWidth, int screenHeight, boolean sidebar) {
        int offset() { return sidebar ? edge - contentWidth : edge - (screenWidth - contentWidth); }
        int clipLeft() { return sidebar ? 0 : Math.max(0, edge); }
        int clipRight() { return sidebar ? edge : screenWidth; }
        boolean contains(double x, double y) {
            return x >= clipLeft() && x < clipRight() && y >= 0 && y < screenHeight;
        }
        double contentX(double x) { return x - offset(); }
    }
    private static final class Group {
        final List<PlacedWidget> widgets = new ArrayList<>();
        final Map<AbstractWidget, Boolean> visibility = new IdentityHashMap<>();
        QuestScreen visual;
        QuestDetailsPanel.Model details;
        int width;
        boolean outgoing;

        void rememberVisibility(GuiEventListener listener) {
            if (listener instanceof AbstractWidget widget) visibility.put(widget, widget.visible);
            if (listener instanceof ContainerEventHandler parent) parent.children().forEach(this::rememberVisibility);
        }

        void retire() {
            outgoing = true;
            for (AbstractWidget widget : visibility.keySet()) {
                widget.setFocused(false);
                widget.setTooltip(null);
            }
        }
    }

    private final QuestScreen screen;
    private DockMotion left;
    private DockMotion right;
    private Group leftGroup;
    private Group rightGroup;
    private Group buildingLeft;
    private Group buildingRight;
    private AbstractWidget toggle;
    private final List<PlacedWidget> leftHeader = new ArrayList<>();
    private final List<PlacedWidget> rightHeader = new ArrayList<>();
    private int builtLeftEdge;
    private int builtRightEdge;
    private int viewportWidth = -1;
    private int viewportHeight;
    private int leftEdge;
    private int rightEdge;
    private boolean leftOpen;
    private boolean rightOpen;
    boolean building;
    Layer layer = Layer.NONE;

    QuestDockPresentation(QuestScreen screen) { this.screen = screen; }

    // A launch option keeps the duration variable without introducing another settings UI.
    private int durationMillis() {
        return Math.max(0, Integer.getInteger("theseus.dockAnimationMillis", DockMotion.DEFAULT_DURATION_MILLIS));
    }

    void inherit(QuestDockPresentation previous) {
        if (previous.left == null) return;
        left = previous.left.copy();
        right = previous.right.copy();
        viewportWidth = previous.viewportWidth;
        viewportHeight = previous.viewportHeight;
        leftOpen = previous.leftOpen;
        rightOpen = previous.rightOpen;
        // Only closing content has no live replacement in the new screen.
        if (!leftOpen) leftGroup = previous.leftGroup;
        if (!rightOpen) rightGroup = previous.rightGroup;
    }

    void beginBuild() {
        long now = System.nanoTime();
        boolean resized = viewportWidth != screen.guiWidth() || viewportHeight != screen.guiHeight();
        int leftTarget = screen.sidebarOpen ? screen.layout.sidebarContentWidth() : QuestScreen.COLLAPSED_SIDEBAR_WIDTH;
        boolean open = screen.detailsOpen || screen.authoring.open;
        int rightTarget = open ? screen.guiWidth() - screen.layout.detailsWidth() : screen.guiWidth();
        if (left == null || resized) {
            left = new DockMotion(leftTarget);
            right = new DockMotion(rightTarget);
            leftGroup = rightGroup = null;
        } else {
            left.target(leftTarget, durationMillis(), now);
            right.target(rightTarget, durationMillis(), now);
            if (leftOpen && !screen.sidebarOpen && leftGroup != null) leftGroup.retire();
            if (rightOpen && !open && rightGroup != null) rightGroup.retire();
        }
        if (leftOpen && !screen.sidebarOpen) screen.chapterListFocused = false;
        if (rightOpen && !open) screen.setScreenFocused(null);
        leftOpen = screen.sidebarOpen;
        rightOpen = open;
        viewportWidth = screen.guiWidth();
        viewportHeight = screen.guiHeight();
        leftHeader.clear();
        rightHeader.clear();
        builtLeftEdge = leftTarget;
        builtRightEdge = rightTarget;
        buildingLeft = new Group();
        buildingLeft.width = screen.layout.sidebarContentWidth();
        buildingRight = new Group();
        buildingRight.width = screen.layout.detailsWidth();
        building = true;
        layer = Layer.NONE;
        sample(now);
    }

    void add(AbstractWidget widget) {
        if (!building) return;
        Group group = layer == Layer.LEFT ? buildingLeft : layer == Layer.RIGHT ? buildingRight : null;
        if (layer == Layer.HEADER_LEFT) leftHeader.add(new PlacedWidget(widget, widget.getX()));
        if (layer == Layer.HEADER_RIGHT && widget.getY() < screen.layout.canvasTop()) rightHeader.add(new PlacedWidget(widget, widget.getX()));
        if (group != null) {
            group.widgets.add(new PlacedWidget(widget, widget.getX()));
            group.rememberVisibility(widget);
        }
    }

    void toggle(AbstractWidget widget) { toggle = widget; }

    void endBuild() {
        building = false;
        layer = Layer.NONE;
        if (!screen.modalHost.blocksInput()) {
            QuestScreen visual = screen.copyDockVisual();
            if (leftOpen) {
                buildingLeft.visual = visual;
                leftGroup = buildingLeft;
            }
            if (rightOpen) {
                buildingRight.visual = visual;
                if (screen.detailsOpen && !screen.authoring.open) buildingRight.details = screen.actions.detailPanelModel();
                rightGroup = buildingRight;
            }
        }
        sample();
    }

    void sample() { sample(System.nanoTime()); }

    private void sample(long now) {
        if (left == null) return;
        leftEdge = (int) Math.round(left.sample(now));
        rightEdge = (int) Math.round(right.sample(now));
        if (!leftOpen && left.finished(now)) leftGroup = null;
        if (!rightOpen && right.finished(now)) rightGroup = null;
        move(leftGroup, bounds(leftGroup, true));
        move(rightGroup, bounds(rightGroup, false));
        for (PlacedWidget placed : leftHeader) placed.widget().setX(placed.x() + leftEdge - builtLeftEdge);
        for (PlacedWidget placed : rightHeader) placed.widget().setX(placed.x() + rightEdge - builtRightEdge);
        if (toggle != null) {
            int width = screen.layout.sidebarContentWidth();
            int rail = QuestScreen.COLLAPSED_SIDEBAR_WIDTH;
            toggle.setX((int) Math.round(3.0 + (leftEdge - rail) * (width - 16.0) / (width - rail)));
        }
    }

    private Bounds bounds(Group group, boolean sidebar) {
        int contentWidth = group == null ? 0 : group.width;
        return new Bounds(sidebar ? leftEdge : rightEdge, contentWidth,
            screen.guiWidth(), screen.guiHeight(), sidebar);
    }

    private void move(Group group, Bounds bounds) {
        if (group == null) return;
        for (PlacedWidget placed : group.widgets) {
            AbstractWidget widget = placed.widget();
            widget.setX(placed.x() + bounds.offset());
        }
        for (var entry : group.visibility.entrySet()) {
            AbstractWidget widget = entry.getKey();
            widget.visible = entry.getValue() && widget.getX() < bounds.clipRight()
                && widget.getX() + widget.getWidth() > bounds.clipLeft();
            if (!widget.visible) {
                widget.setFocused(false);
                if (screen.screenFocused() == widget) screen.setScreenFocused(null);
            }
        }
    }

    int leftEdge() { return left == null ? screen.layout.sidebarContentWidth() : leftEdge; }
    int rightEdge() { return right == null ? screen.guiWidth() : rightEdge; }
    int leftOffset() { return leftGroup == null ? 0 : bounds(leftGroup, true).offset(); }
    int rightOffset() { return rightGroup == null ? 0 : bounds(rightGroup, false).offset(); }
    double contentX(double x) { return x - rightOffset(); }
    boolean rightContains(double x, double y) {
        return bounds(rightGroup, false).contains(x, y);
    }
    boolean closingContains(double x, double y) {
        return (!rightOpen && rightContains(x, y))
            || (!leftOpen && x >= 0 && x < leftEdge() && y >= 13 && y < screen.guiHeight());
    }

    boolean owns(AbstractWidget widget) {
        return owns(leftGroup, widget) || owns(rightGroup, widget);
    }
    private boolean owns(Group group, AbstractWidget widget) {
        return group != null && group.widgets.stream().anyMatch(placed -> placed.widget() == widget);
    }

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderGroup(graphics, leftGroup, true, mouseX, mouseY, partialTick);
        renderGroup(graphics, rightGroup, false, mouseX, mouseY, partialTick);
    }

    private void renderGroup(GuiGraphicsExtractor graphics, Group group, boolean sidebar,
                             int mouseX, int mouseY, float partialTick) {
        if (group == null || group.visual == null) return;
        Bounds bounds = bounds(group, sidebar);
        int offset = bounds.offset();
        int clipLeft = bounds.clipLeft();
        int clipRight = bounds.clipRight();
        graphics.enableScissor(clipLeft, 0, clipRight, screen.guiHeight());
        int pointerX = group.outgoing || screen.modalHost.blocksInput() ? Integer.MIN_VALUE / 2 : mouseX;
        for (PlacedWidget placed : group.widgets) {
            placed.widget().extractRenderState(graphics, pointerX, mouseY, partialTick);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(offset, 0);
        QuestScreen source = group.outgoing || screen.modalHost.blocksInput() ? group.visual : screen;
        if (sidebar) source.renderer.drawSidebarForeground(graphics);
        else if (source.authoring.open) source.renderer.drawDockForeground(graphics, pointerX - offset, mouseY);
        else {
            if (!group.outgoing && !screen.modalHost.blocksInput()) group.details = screen.actions.detailPanelModel();
            source.detailsPanel.render(graphics, screen.guiFont(), screen.guiWidth(), screen.guiHeight(),
                group.width, group.details, pointerX - offset, mouseY);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    void clear() {
        leftGroup = rightGroup = buildingLeft = buildingRight = null;
        leftHeader.clear();
        rightHeader.clear();
        toggle = null;
    }
}
