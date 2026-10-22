package me.johardt.theseus.client;

/** Pure minimap placement and mapping helpers used by the screen adapter. */
public final class QuestMinimap {
    public static final int DEFAULT_WIDTH = 100;
    public static final int DEFAULT_HEIGHT = 66;
    public static final int HEADER_HEIGHT = 11;

    private QuestMinimap() {}

    public static MapBounds placement(
        TheseusClientOptions.MinimapMode mode,
        QuestGraphLayout.CanvasBounds graphCanvas,
        double normalizedX,
        double normalizedY,
        int width,
        int height
    ) {
        if (mode == null) return null;
        return mode == TheseusClientOptions.MinimapMode.DOCKED
            ? dockedPlacement(graphCanvas, width, height)
            : floatingPlacement(graphCanvas, normalizedX, normalizedY, width, height);
    }

    public static MapBounds floatingPlacement(
        QuestGraphLayout.CanvasBounds canvas,
        double normalizedX,
        double normalizedY,
        int width,
        int height
    ) {
        if (canvas == null || width <= 0 || height <= 0 ||
            canvas.width() < width || canvas.height() < height) return null;
        double x = clamp01(normalizedX) * (canvas.width() - width);
        double y = clamp01(normalizedY) * (canvas.height() - height);
        return new MapBounds(
            (int) Math.round(canvas.x() + x),
            (int) Math.round(canvas.y() + y),
            width,
            height
        );
    }

    public static MapBounds dockedPlacement(
        QuestGraphLayout.CanvasBounds canvas,
        int width,
        int height
    ) {
        if (canvas == null || width <= 0 || height <= 0 ||
            canvas.width() < width || canvas.height() < height + 4) return null;
        return new MapBounds(
            (int) Math.round(canvas.maxX() - width - 4),
            (int) Math.round(canvas.maxY() - height - 4),
            width,
            height
        );
    }

    public static double[] normalizedPosition(
        QuestGraphLayout.CanvasBounds canvas,
        MapBounds bounds
    ) {
        if (canvas == null || bounds == null) return new double[] {0, 0};
        double availableWidth = Math.max(0, canvas.width() - bounds.width());
        double availableHeight = Math.max(0, canvas.height() - bounds.height());
        double x = availableWidth == 0
            ? 0
            : (bounds.x() - canvas.x()) / availableWidth;
        double y = availableHeight == 0
            ? 0
            : (bounds.y() - canvas.y()) / availableHeight;
        return new double[] {clamp01(x), clamp01(y)};
    }

    public static Mapping mapping(
        QuestGraphLayout.WorldBounds worldBounds,
        MapBounds mapBounds
    ) {
        QuestGraphLayout.WorldBounds world = worldBounds == null || worldBounds.isEmpty()
            ? new QuestGraphLayout.WorldBounds(0, 0, 1, 1)
            : worldBounds;
        double width = Math.max(1, world.width());
        double height = Math.max(1, world.height());
        double contentWidth = Math.max(1, mapBounds.contentWidth());
        double contentHeight = Math.max(1, mapBounds.contentHeight());
        double scale = Math.min(contentWidth / width, contentHeight / height);
        double offsetX = mapBounds.contentX() + (contentWidth - width * scale) / 2.0;
        double offsetY = mapBounds.contentY() + (contentHeight - height * scale) / 2.0;
        return new Mapping(world, mapBounds, scale, offsetX, offsetY);
    }

    public static QuestGraphLayout.Point worldToMap(
        Mapping mapping,
        double worldX,
        double worldY
    ) {
        return new QuestGraphLayout.Point(
            mapping.offsetX() + (worldX - mapping.worldBounds().minX()) * mapping.scale(),
            mapping.offsetY() + (worldY - mapping.worldBounds().minY()) * mapping.scale()
        );
    }

    public static QuestGraphLayout.Point mapToWorld(
        Mapping mapping,
        double mapX,
        double mapY
    ) {
        return new QuestGraphLayout.Point(
            mapping.worldBounds().minX() + (mapX - mapping.offsetX()) / mapping.scale(),
            mapping.worldBounds().minY() + (mapY - mapping.offsetY()) / mapping.scale()
        );
    }

    public static MapBounds viewportRectangle(
        Mapping mapping,
        QuestGraphLayout.WorldBounds visibleWorld
    ) {
        if (visibleWorld == null || visibleWorld.isEmpty()) return null;
        QuestGraphLayout.Point topLeft = worldToMap(
            mapping,
            visibleWorld.minX(),
            visibleWorld.minY()
        );
        QuestGraphLayout.Point bottomRight = worldToMap(
            mapping,
            visibleWorld.maxX(),
            visibleWorld.maxY()
        );
        int left = (int) Math.round(Math.max(mapping.mapBounds().contentX(), topLeft.x()));
        int top = (int) Math.round(Math.max(mapping.mapBounds().contentY(), topLeft.y()));
        int right = (int) Math.round(Math.min(mapping.mapBounds().contentMaxX(), bottomRight.x()));
        int bottom = (int) Math.round(Math.min(mapping.mapBounds().contentMaxY(), bottomRight.y()));
        return new MapBounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public static boolean contains(MapBounds bounds, double x, double y) {
        return bounds != null && x >= bounds.x() && x < bounds.maxX() &&
            y >= bounds.y() && y < bounds.maxY();
    }

    public static boolean containsBody(MapBounds bounds, double x, double y) {
        return bounds != null && x >= bounds.contentX() && x < bounds.contentMaxX() &&
            y >= bounds.contentY() && y < bounds.contentMaxY();
    }

    public static boolean containsGrip(MapBounds bounds, double x, double y) {
        return bounds != null && x >= bounds.x() && x < bounds.maxX() &&
            y >= bounds.y() && y < bounds.contentY();
    }

    public static boolean containsHeaderMenu(MapBounds bounds, double x, double y) {
        return containsGrip(bounds, x, y)
            && x >= bounds.maxX() - 12
            && x < bounds.maxX() - 2;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    public record MapBounds(int x, int y, int width, int height) {
        public int maxX() {
            return x + width;
        }

        public int maxY() {
            return y + height;
        }

        public int contentX() {
            return x + 1;
        }

        public int contentY() {
            return y + HEADER_HEIGHT;
        }

        public int contentMaxX() {
            return Math.max(contentX(), maxX() - 1);
        }

        public int contentMaxY() {
            return Math.max(contentY(), maxY() - 1);
        }

        public int contentWidth() {
            return Math.max(1, contentMaxX() - contentX());
        }

        public int contentHeight() {
            return Math.max(1, contentMaxY() - contentY());
        }
    }

    public record Mapping(
        QuestGraphLayout.WorldBounds worldBounds,
        MapBounds mapBounds,
        double scale,
        double offsetX,
        double offsetY
    ) {}
}
