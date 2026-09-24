package me.johardt.theseus.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Geometry and viewport state for the quest graph's canvas and world. */
public final class QuestGraphLayout {
    public static final double MIN_ZOOM = 0.15;
    public static final double MAX_ZOOM = 2.0;
    public static final int GRID_CELL_SIZE = 27;

    private QuestGraphLayout() {}

    public static Point worldToScreen(
        CanvasBounds canvas,
        ViewportState viewport,
        double worldX,
        double worldY
    ) {
        return new Point(
            canvas.centerX() + (worldX - viewport.centerWorldX()) * viewport.zoom(),
            canvas.centerY() + (worldY - viewport.centerWorldY()) * viewport.zoom()
        );
    }

    public static Point screenToWorld(
        CanvasBounds canvas,
        ViewportState viewport,
        double screenX,
        double screenY
    ) {
        return new Point(
            viewport.centerWorldX() + (screenX - canvas.centerX()) / viewport.zoom(),
            viewport.centerWorldY() + (screenY - canvas.centerY()) / viewport.zoom()
        );
    }

    public static ScreenBounds worldToScreen(
        CanvasBounds canvas,
        ViewportState viewport,
        NodeBounds node
    ) {
        Point topLeft = worldToScreen(canvas, viewport, node.x(), node.y());
        return new ScreenBounds(
            topLeft.x(),
            topLeft.y(),
            node.width() * viewport.zoom(),
            node.height() * viewport.zoom()
        );
    }

    public static WorldBounds visibleWorld(CanvasBounds canvas, ViewportState viewport) {
        Point topLeft = screenToWorld(canvas, viewport, canvas.x(), canvas.y());
        Point bottomRight = screenToWorld(
            canvas,
            viewport,
            canvas.maxX(),
            canvas.maxY()
        );
        return new WorldBounds(
            topLeft.x(),
            topLeft.y(),
            bottomRight.x(),
            bottomRight.y()
        );
    }

    /** Returns only the texture tiles for a segment that can reach the visible world. */
    static PathTileRange visiblePathTiles(
        Point start,
        Point end,
        WorldBounds visibleWorld,
        double strokeMargin,
        int tileSize
    ) {
        if (
            start == null ||
            end == null ||
            visibleWorld == null ||
            visibleWorld.isEmpty() ||
            tileSize <= 0 ||
            !Double.isFinite(start.x()) ||
            !Double.isFinite(start.y()) ||
            !Double.isFinite(end.x()) ||
            !Double.isFinite(end.y())
        ) return PathTileRange.EMPTY;

        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(length) || length < 1) return PathTileRange.EMPTY;

        double margin = Double.isFinite(strokeMargin) ? Math.max(0, strokeMargin) : 0;
        double minX = visibleWorld.minX() - margin;
        double minY = visibleWorld.minY() - margin;
        double maxX = visibleWorld.maxX() + margin;
        double maxY = visibleWorld.maxY() + margin;
        if (
            !Double.isFinite(minX) ||
            !Double.isFinite(minY) ||
            !Double.isFinite(maxX) ||
            !Double.isFinite(maxY)
        ) return PathTileRange.EMPTY;

        double[] interval = { 0, 1 };
        if (
            !clipSegmentAxis(start.x(), dx, minX, maxX, interval) ||
            !clipSegmentAxis(start.y(), dy, minY, maxY, interval) ||
            interval[0] > interval[1]
        ) return PathTileRange.EMPTY;

        long pixelLength = safeCeiling(length);
        if (pixelLength <= 0) return PathTileRange.EMPTY;
        long tileCount = pixelLength / tileSize + (pixelLength % tileSize == 0 ? 0 : 1);
        if (tileCount <= 0) return PathTileRange.EMPTY;

        double firstVisibleDistance = interval[0] * length;
        double lastVisibleDistance = interval[1] * length;
        long firstTile = Math.min(
            tileCount - 1,
            safeFloor(firstVisibleDistance / tileSize)
        );
        long endTile = Math.min(
            tileCount,
            safeCeiling(lastVisibleDistance / tileSize)
        );
        if (endTile <= firstTile) endTile = firstTile + 1;
        return new PathTileRange(firstTile, endTile, pixelLength);
    }

    private static boolean clipSegmentAxis(
        double start,
        double delta,
        double minimum,
        double maximum,
        double[] interval
    ) {
        if (delta == 0) return start >= minimum && start <= maximum;
        double first = (minimum - start) / delta;
        double last = (maximum - start) / delta;
        if (!Double.isFinite(first) || !Double.isFinite(last)) return false;
        if (first > last) {
            double swap = first;
            first = last;
            last = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], last);
        return interval[0] <= interval[1] && interval[1] >= 0 && interval[0] <= 1;
    }

    private static long safeFloor(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(value);
    }

    private static long safeCeiling(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.ceil(value);
    }

    /**
     * Snaps to the nearest graph grid line. Halfway values round toward the
     * positive grid line, matching {@link Math#round(double)} for negative
     * coordinates as well.
     */
    public static int snapCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)) return 0;
        double snapped = Math.round(coordinate / GRID_CELL_SIZE) * (double) GRID_CELL_SIZE;
        if (snapped > Integer.MAX_VALUE) return maxGridCoordinate();
        if (snapped < Integer.MIN_VALUE) return minGridCoordinate();
        return (int) snapped;
    }

    public static Point snapPoint(double x, double y) {
        return new Point(snapCoordinate(x), snapCoordinate(y));
    }

    public static Point snapPoint(Point point) {
        return point == null ? new Point(0, 0) : snapPoint(point.x(), point.y());
    }

    /** Returns only grid lines that can intersect the supplied visible world. */
    public static GridLineRange visibleGridLineRange(WorldBounds visibleWorld) {
        if (visibleWorld == null || visibleWorld.isEmpty()) return GridLineRange.EMPTY;
        return new GridLineRange(
            firstGridCoordinate(visibleWorld.minX()),
            lastGridCoordinate(visibleWorld.maxX()),
            firstGridCoordinate(visibleWorld.minY()),
            lastGridCoordinate(visibleWorld.maxY())
        );
    }

    public static GridLineRange visibleGridLineRange(
        CanvasBounds canvas,
        ViewportState viewport
    ) {
        return visibleGridLineRange(visibleWorld(canvas, viewport));
    }

    private static int firstGridCoordinate(double minimum) {
        double index = Math.ceil(minimum / GRID_CELL_SIZE);
        return gridCoordinate(index);
    }

    private static int lastGridCoordinate(double maximum) {
        double index = Math.floor(maximum / GRID_CELL_SIZE);
        return gridCoordinate(index);
    }

    private static int gridCoordinate(double index) {
        double coordinate = index * GRID_CELL_SIZE;
        if (coordinate > Integer.MAX_VALUE) return maxGridCoordinate();
        if (coordinate < Integer.MIN_VALUE) return minGridCoordinate();
        return (int) coordinate;
    }

    private static int maxGridCoordinate() {
        return Math.floorDiv(Integer.MAX_VALUE, GRID_CELL_SIZE) * GRID_CELL_SIZE;
    }

    private static int minGridCoordinate() {
        int remainder = Math.floorMod(Integer.MIN_VALUE, GRID_CELL_SIZE);
        return Integer.MIN_VALUE + (remainder == 0 ? 0 : GRID_CELL_SIZE - remainder);
    }

    public static WorldBounds boundsOf(Collection<NodeBounds> nodes, double padding) {
        if (nodes == null || nodes.isEmpty()) return WorldBounds.empty();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (NodeBounds node : nodes) {
            if (node == null) continue;
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.maxX());
            maxY = Math.max(maxY, node.maxY());
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) return WorldBounds.empty();

        double safePadding = Double.isFinite(padding) ? Math.max(0, padding) : 0;
        return new WorldBounds(
            minX - safePadding,
            minY - safePadding,
            maxX + safePadding,
            maxY + safePadding
        );
    }

    public static ViewportState fitViewport(CanvasBounds canvas, WorldBounds bounds) {
        if (canvas.isEmpty() || bounds == null || bounds.isEmpty()) {
            return ViewportState.DEFAULT;
        }

        double width = Math.max(1, bounds.width());
        double height = Math.max(1, bounds.height());
        double horizontal = canvas.width() / width;
        double vertical = canvas.height() / height;
        double zoom = Math.min(1.0, Math.min(horizontal, vertical));
        zoom = clampZoom(zoom);
        return new ViewportState(bounds.centerX(), bounds.centerY(), zoom);
    }

    public static boolean fitsAtZoomOne(CanvasBounds canvas, WorldBounds bounds) {
        if (bounds == null || bounds.isEmpty()) return true;
        return visibleWorld(canvas, ViewportState.DEFAULT).contains(bounds);
    }

    /** Zooms around a screen point while keeping the world point below it fixed. */
    public static ViewportState zoomAroundScreenPoint(
        CanvasBounds canvas,
        ViewportState viewport,
        double screenX,
        double screenY,
        double amount
    ) {
        Point worldUnderCursor = screenToWorld(canvas, viewport, screenX, screenY);
        double nextZoom = clampZoom(viewport.zoom() + amount);
        if (nextZoom == viewport.zoom()) return viewport;

        double nextCenterX = worldUnderCursor.x() -
            (screenX - canvas.centerX()) / nextZoom;
        double nextCenterY = worldUnderCursor.y() -
            (screenY - canvas.centerY()) / nextZoom;
        return new ViewportState(nextCenterX, nextCenterY, nextZoom);
    }

    public static double clampZoom(double zoom) {
        if (!Double.isFinite(zoom)) return 1.0;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    /** Mutable viewport state shared by rendering, input, and chapter navigation. */
    public static final class ViewportMemory {
        private ViewportState viewport = ViewportState.DEFAULT;
        private final Map<String, ViewportState> chapterViewports = new HashMap<>();
        private String activeChapter;

        public ViewportState state() { return viewport; }

        public void panByScreenDelta(double screenDeltaX, double screenDeltaY) {
            viewport = new ViewportState(
                viewport.centerWorldX() - screenDeltaX / viewport.zoom(),
                viewport.centerWorldY() - screenDeltaY / viewport.zoom(),
                viewport.zoom()
            );
        }

        public void centerOn(double worldX, double worldY) {
            viewport = new ViewportState(worldX, worldY, viewport.zoom());
        }

        public void zoomAroundScreenPoint(
            CanvasBounds canvas,
            double screenX,
            double screenY,
            double amount
        ) {
            viewport = QuestGraphLayout.zoomAroundScreenPoint(
                canvas, viewport, screenX, screenY, amount
            );
        }

        public void fitToContent(CanvasBounds canvas, WorldBounds bounds) {
            viewport = fitViewport(canvas, bounds);
            rememberActiveChapter();
        }

        /** Selects a chapter and restores or derives its viewport. */
        public void activateChapter(String chapter, CanvasBounds canvas, WorldBounds bounds) {
            if (chapter == null || chapter.equals(activeChapter)) return;
            rememberActiveChapter();
            activeChapter = chapter;
            ViewportState saved = chapterViewports.get(chapter);
            if (saved != null) {
                viewport = saved;
            } else if (fitsAtZoomOne(canvas, bounds)) {
                viewport = ViewportState.DEFAULT;
            } else {
                viewport = fitViewport(canvas, bounds);
            }
            rememberActiveChapter();
        }

        public void saveChapterViewport(String chapter) {
            if (chapter != null) chapterViewports.put(chapter, viewport);
        }

        private void rememberActiveChapter() {
            if (activeChapter != null) chapterViewports.put(activeChapter, viewport);
        }

        public ViewportMemory copy() {
            ViewportMemory copy = new ViewportMemory();
            copy.viewport = viewport;
            copy.chapterViewports.putAll(chapterViewports);
            copy.activeChapter = activeChapter;
            return copy;
        }
    }

    public record Point(double x, double y) {}

    record PathTileRange(long firstTile, long endTileExclusive, long pixelLength) {
        static final PathTileRange EMPTY = new PathTileRange(0, 0, 0);

        boolean isEmpty() {
            return firstTile >= endTileExclusive || pixelLength <= 0;
        }

        long tileCount() {
            return isEmpty() ? 0 : endTileExclusive - firstTile;
        }
    }

    public record GridLineRange(int firstX, int lastX, int firstY, int lastY) {
        public static final GridLineRange EMPTY = new GridLineRange(0, -27, 0, -27);

        public GridLineRange {
            if (firstX % GRID_CELL_SIZE != 0 || lastX % GRID_CELL_SIZE != 0 ||
                firstY % GRID_CELL_SIZE != 0 || lastY % GRID_CELL_SIZE != 0) {
                throw new IllegalArgumentException("Grid lines must be aligned to the grid cell size");
            }
        }

        public boolean isEmpty() {
            return firstX > lastX || firstY > lastY;
        }

        public int xCount() {
            return isEmpty() ? 0 : lastX / GRID_CELL_SIZE - firstX / GRID_CELL_SIZE + 1;
        }

        public int yCount() {
            return isEmpty() ? 0 : lastY / GRID_CELL_SIZE - firstY / GRID_CELL_SIZE + 1;
        }
    }

    public record CanvasBounds(double x, double y, double width, double height) {
        public CanvasBounds {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            width = Math.max(0, finiteOrZero(width));
            height = Math.max(0, finiteOrZero(height));
        }

        public double centerX() {
            return x + width / 2.0;
        }

        public double centerY() {
            return y + height / 2.0;
        }

        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0;
        }
    }

    public record WorldBounds(double minX, double minY, double maxX, double maxY) {
        public WorldBounds {
            minX = finiteOrZero(minX);
            minY = finiteOrZero(minY);
            maxX = finiteOrZero(maxX);
            maxY = finiteOrZero(maxY);
        }

        public static WorldBounds empty() {
            return new WorldBounds(0, 0, 0, 0);
        }

        public double width() {
            return Math.max(0, maxX - minX);
        }

        public double height() {
            return Math.max(0, maxY - minY);
        }

        public double centerX() {
            return (minX + maxX) / 2.0;
        }

        public double centerY() {
            return (minY + maxY) / 2.0;
        }

        public double maxX() {
            return maxX;
        }

        public double maxY() {
            return maxY;
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }

        public boolean contains(WorldBounds other) {
            return other != null &&
                minX <= other.minX &&
                minY <= other.minY &&
                maxX >= other.maxX &&
                maxY >= other.maxY;
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0;
        }
    }

    public record NodeBounds(double x, double y, double width, double height) {
        public NodeBounds {
            x = Double.isFinite(x) ? x : 0;
            y = Double.isFinite(y) ? y : 0;
            width = Math.max(0, Double.isFinite(width) ? width : 0);
            height = Math.max(0, Double.isFinite(height) ? height : 0);
        }

        public static NodeBounds centered(double centerX, double centerY, double width, double height) {
            return new NodeBounds(
                centerX - width / 2.0,
                centerY - height / 2.0,
                width,
                height
            );
        }

        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }
    }

    public record ScreenBounds(double x, double y, double width, double height) {
        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }
    }

    public record ViewportState(double centerWorldX, double centerWorldY, double zoom) {
        public static final ViewportState DEFAULT = new ViewportState(0, 0, 1);

        public ViewportState {
            centerWorldX = Double.isFinite(centerWorldX) ? centerWorldX : 0;
            centerWorldY = Double.isFinite(centerWorldY) ? centerWorldY : 0;
            zoom = clampZoom(zoom);
        }
    }
}
