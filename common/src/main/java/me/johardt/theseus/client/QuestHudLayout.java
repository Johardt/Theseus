package me.johardt.theseus.client;

/** Pure geometry for the pinned quest tracker. */
public final class QuestHudLayout {
    public static final int SCREEN_MARGIN = 6;

    private QuestHudLayout() {}

    public static Bounds layout(
        int screenWidth,
        int screenHeight,
        int desiredWidth,
        int desiredHeight,
        TheseusClientOptions.TrackerAnchor anchor
    ) {
        int safeScreenWidth = Math.max(0, screenWidth);
        int safeScreenHeight = Math.max(0, screenHeight);
        int width = Math.clamp(desiredWidth, 0, safeScreenWidth);
        int height = Math.clamp(desiredHeight, 0, safeScreenHeight);
        TheseusClientOptions.TrackerAnchor safeAnchor = anchor == null
            ? TheseusClientOptions.TrackerAnchor.TOP_LEFT
            : anchor;

        boolean right = switch (safeAnchor) {
            case TOP_RIGHT, UPPER_RIGHT, LOWER_RIGHT, BOTTOM_RIGHT -> true;
            default -> false;
        };
        int x = right
            ? safeScreenWidth - desiredWidth - SCREEN_MARGIN
            : SCREEN_MARGIN;
        int y = switch (safeAnchor) {
            case UPPER_LEFT, UPPER_RIGHT -> Math.round(safeScreenHeight * 0.25f);
            case LOWER_LEFT, LOWER_RIGHT -> Math.round(safeScreenHeight * 0.75f) - desiredHeight;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> safeScreenHeight - desiredHeight - SCREEN_MARGIN;
            default -> SCREEN_MARGIN;
        };
        return new Bounds(
            clamp(x, 0, Math.max(0, safeScreenWidth - width)),
            clamp(y, 0, Math.max(0, safeScreenHeight - height)),
            width,
            height
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Bounds(int x, int y, int width, int height) {
        public int maxX() { return x + width; }
        public int maxY() { return y + height; }
    }
}
