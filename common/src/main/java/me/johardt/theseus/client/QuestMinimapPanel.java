package me.johardt.theseus.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Owns minimap placement, drawing, and pointer state; the screen applies returned actions. */
final class QuestMinimapPanel {
    private boolean hidden;
    private boolean navigating;
    private boolean repositioning;
    private double positionX = Double.NaN;
    private double positionY = Double.NaN;
    private double dragOffsetX;
    private double dragOffsetY;

    QuestMinimapPanel copyForRebuild() {
        QuestMinimapPanel copy = new QuestMinimapPanel();
        copy.hidden = hidden;
        copy.positionX = positionX;
        copy.positionY = positionY;
        return copy;
    }

    boolean hidden() {
        return hidden;
    }

    void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    void clearTransientState() {
        navigating = false;
        repositioning = false;
        positionX = Double.NaN;
        positionY = Double.NaN;
        dragOffsetX = 0;
        dragOffsetY = 0;
    }

    QuestMinimap.MapBounds bounds(
        QuestGraphLayout.CanvasBounds canvas,
        Settings settings
    ) {
        if (settings.disabled() || hidden) return null;
        return QuestMinimap.placement(
            settings.mode(),
            canvas,
            Double.isFinite(positionX) ? positionX : settings.positionX(),
            Double.isFinite(positionY) ? positionY : settings.positionY(),
            QuestMinimap.DEFAULT_WIDTH,
            QuestMinimap.DEFAULT_HEIGHT
        );
    }

    void render(
        GuiGraphicsExtractor graphics,
        Font font,
        QuestMinimap.MapBounds mapBounds,
        Scene scene,
        int borderColor,
        int textColor
    ) {
        if (mapBounds == null) return;
        QuestMinimap.Mapping mapping = QuestMinimap.mapping(scene.worldBounds(), mapBounds);
        graphics.enableScissor(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.maxY());
        graphics.fill(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.maxY(), 0xE820242B);
        graphics.fill(mapBounds.x(), mapBounds.y(), mapBounds.maxX(), mapBounds.contentY(), 0xFF303640);
        graphics.text(font, Component.translatable("gui.theseus.editor.map"), mapBounds.x() + 4, mapBounds.y() + 2, textColor, false);
        graphics.text(
            font,
            Component.literal("⋮"),
            mapBounds.maxX() - 9,
            mapBounds.y() + 1,
            textColor,
            false
        );

        for (Edge edge : scene.edges()) {
            QuestGraphLayout.Point start = QuestMinimap.worldToMap(
                mapping,
                edge.startX(),
                edge.startY()
            );
            QuestGraphLayout.Point end = QuestMinimap.worldToMap(
                mapping,
                edge.endX(),
                edge.endY()
            );
            drawLine(graphics, start, end, edge.color());
        }

        for (Marker marker : scene.markers()) {
            QuestGraphLayout.Point point = QuestMinimap.worldToMap(
                mapping,
                marker.worldX(),
                marker.worldY()
            );
            int x = (int) Math.round(point.x()) - marker.size() / 2;
            int y = (int) Math.round(point.y()) - marker.size() / 2;
            graphics.fill(x, y, x + marker.size(), y + marker.size(), marker.color());
            if (marker.selected()) {
                graphics.outline(x - 2, y - 2, marker.size() + 4, marker.size() + 4, 0xFFFFFFFF);
            }
        }

        QuestMinimap.MapBounds viewport = QuestMinimap.viewportRectangle(
            mapping,
            scene.visibleWorld()
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
        graphics.outline(mapBounds.x(), mapBounds.y(), mapBounds.width(), mapBounds.height(), borderColor);
    }

    Result mouseClicked(
        QuestGraphLayout.CanvasBounds canvas,
        Settings settings,
        double mouseX,
        double mouseY,
        int button,
        boolean detailsDockContains
    ) {
        if (detailsDockContains) return Result.ignored();
        QuestMinimap.MapBounds mapBounds = bounds(canvas, settings);
        if (!QuestMinimap.contains(mapBounds, mouseX, mouseY)) return Result.ignored();

        if (QuestMinimap.containsHeaderMenu(mapBounds, mouseX, mouseY)) {
            return Result.withAction(new OpenContextMenu(
                (int) Math.round(mouseX),
                (int) Math.round(mouseY)
            ));
        }

        if (settings.mode() == TheseusClientOptions.MinimapMode.UNDOCKED
            && button == 0
            && QuestMinimap.containsGrip(mapBounds, mouseX, mouseY)) {
            repositioning = true;
            navigating = false;
            dragOffsetX = mouseX - mapBounds.x();
            dragOffsetY = mouseY - mapBounds.y();
            positionX = settings.positionX();
            positionY = settings.positionY();
            return Result.consumed();
        }

        if (button == 0 && QuestMinimap.containsBody(mapBounds, mouseX, mouseY)) {
            navigating = true;
            return Result.withAction(new Navigate(mapBounds, mouseX, mouseY));
        }
        return Result.consumed();
    }

    Result mouseDragged(
        QuestGraphLayout.CanvasBounds canvas,
        Settings settings,
        double mouseX,
        double mouseY
    ) {
        if (settings.disabled()) {
            clearTransientState();
            return Result.ignored();
        }
        QuestMinimap.MapBounds mapBounds = bounds(canvas, settings);
        if (repositioning) {
            QuestMinimap.MapBounds next = new QuestMinimap.MapBounds(
                (int) Math.round(mouseX - dragOffsetX),
                (int) Math.round(mouseY - dragOffsetY),
                mapBounds == null ? QuestMinimap.DEFAULT_WIDTH : mapBounds.width(),
                mapBounds == null ? QuestMinimap.DEFAULT_HEIGHT : mapBounds.height()
            );
            double[] normalized = QuestMinimap.normalizedPosition(canvas, next);
            positionX = normalized[0];
            positionY = normalized[1];
            return Result.consumed();
        }
        if (navigating && QuestMinimap.containsBody(mapBounds, mouseX, mouseY)) {
            return Result.withAction(new Navigate(mapBounds, mouseX, mouseY));
        }
        return navigating ? Result.consumed() : Result.ignored();
    }

    Result mouseReleased(Settings settings) {
        if (settings.disabled()) {
            clearTransientState();
            return Result.ignored();
        }
        if (repositioning) {
            double savedX = Double.isFinite(positionX) ? positionX : settings.positionX();
            double savedY = Double.isFinite(positionY) ? positionY : settings.positionY();
            clearTransientState();
            return Result.withAction(new SavePosition(savedX, savedY));
        }
        if (navigating) {
            navigating = false;
            return Result.consumed();
        }
        return Result.ignored();
    }

    private static void drawLine(
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

    record Settings(
        boolean disabled,
        TheseusClientOptions.MinimapMode mode,
        double positionX,
        double positionY
    ) {}

    record Scene(
        QuestGraphLayout.WorldBounds worldBounds,
        QuestGraphLayout.WorldBounds visibleWorld,
        List<Edge> edges,
        List<Marker> markers
    ) {
        Scene {
            edges = List.copyOf(edges);
            markers = List.copyOf(markers);
        }
    }

    record Edge(double startX, double startY, double endX, double endY, int color) {}

    record Marker(double worldX, double worldY, int size, int color, boolean selected) {}

    sealed interface Action permits Navigate, OpenContextMenu, SavePosition {}

    record Navigate(QuestMinimap.MapBounds mapBounds, double mapX, double mapY) implements Action {}

    record OpenContextMenu(int screenX, int screenY) implements Action {}

    record SavePosition(double x, double y) implements Action {}

    record Result(boolean handled, Action action) {
        static Result ignored() {
            return new Result(false, null);
        }

        static Result consumed() {
            return new Result(true, null);
        }

        static Result withAction(Action action) {
            return new Result(true, action);
        }
    }
}
