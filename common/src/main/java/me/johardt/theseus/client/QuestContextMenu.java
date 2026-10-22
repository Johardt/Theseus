package me.johardt.theseus.client;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Small stateful seam for transient graph menus. Rendering stays with the
 * screen, while layout, traversal, dismissal, and activation stay here.
 */
public final class QuestContextMenu {
    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_RETURN = InputConstants.KEY_RETURN;
    public static final int KEY_NUMPAD_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int ROW_HEIGHT = 20;
    public static final int SEPARATOR_HEIGHT = 7;
    public static final int PADDING = 4;
    public static final int MIN_WIDTH = 150;

    private final List<Entry> entries;
    private final List<Row> rows;
    private final Bounds bounds;
    private final Runnable onDismiss;
    private int selectedIndex;
    private int hoveredIndex = -1;
    private boolean open = true;

    public QuestContextMenu(
        int x,
        int y,
        int screenWidth,
        int screenHeight,
        List<Entry> entries,
        Runnable onDismiss
    ) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.bounds = layoutBounds(x, y, screenWidth, screenHeight, this.entries);
        this.rows = rows(this.bounds, this.entries);
        this.selectedIndex = firstEnabled(this.entries);
        this.onDismiss = onDismiss == null ? () -> {} : onDismiss;
    }

    public static Bounds layoutBounds(
        int x,
        int y,
        int screenWidth,
        int screenHeight,
        List<Entry> entries
    ) {
        List<Entry> values = entries == null ? List.of() : entries;
        int contentWidth = values.stream()
            .mapToInt(Entry::preferredWidth)
            .max()
            .orElse(MIN_WIDTH);
        int width = Math.max(MIN_WIDTH, contentWidth) + PADDING * 2;
        int height = PADDING * 2 + values.stream()
            .mapToInt(entry -> entry.isSeparator() ? SEPARATOR_HEIGHT : ROW_HEIGHT)
            .sum();
        int safeScreenWidth = Math.max(0, screenWidth);
        int safeScreenHeight = Math.max(0, screenHeight);
        width = Math.min(width, safeScreenWidth);
        height = Math.min(height, safeScreenHeight);
        int clampedX = Math.max(0, Math.min(x, Math.max(0, safeScreenWidth - width)));
        int clampedY = Math.max(0, Math.min(y, Math.max(0, safeScreenHeight - height)));
        return new Bounds(clampedX, clampedY, width, height);
    }

    public Bounds bounds() { return bounds; }
    public List<Entry> entries() { return entries; }
    public List<Row> rows() { return rows; }
    public int selectedIndex() { return selectedIndex; }
    public int hoveredIndex() { return hoveredIndex; }
    public boolean isOpen() { return open; }

    public void moveMouse(double x, double y) {
        hoveredIndex = rowAt(x, y);
    }

    /** Every click is consumed while open so it cannot leak to the graph. */
    public Result mouseClicked(double x, double y, int button) {
        if (!open) return Result.IGNORED;
        int rowIndex = rowAt(x, y);
        if (rowIndex < 0) {
            dismiss();
            return Result.DISMISSED;
        }
        Row row = rows.get(rowIndex);
        if (row.entry().isSeparator()) return Result.CONSUMED;
        if (button != 0) return Result.CONSUMED;
        selectedIndex = row.entryIndex();
        if (!row.entry().enabled()) return Result.CONSUMED;
        activate(selectedIndex);
        return Result.ACTIVATED;
    }

    /** Handles navigation and consumes all keyboard input while the menu is open. */
    public Result keyPressed(int keyCode) {
        return keyPressed(keyCode, false);
    }

    /** Handles navigation and consumes all keyboard input while the menu is open. */
    public Result keyPressed(int keyCode, boolean shiftDown) {
        if (!open) return Result.IGNORED;
        if (keyCode == KEY_ESCAPE) {
            dismiss();
            return Result.DISMISSED;
        }
        if (keyCode == KEY_UP || keyCode == KEY_DOWN || keyCode == KEY_TAB) {
            moveSelection(keyCode == KEY_DOWN || (keyCode == KEY_TAB && !shiftDown) ? 1 : -1);
            return Result.CONSUMED;
        }
        if (keyCode == KEY_RETURN || keyCode == KEY_NUMPAD_ENTER) {
            boolean activatable = selectedIndex >= 0
                && selectedIndex < entries.size()
                && entries.get(selectedIndex).enabled()
                && !entries.get(selectedIndex).isSeparator();
            if (activatable) activate(selectedIndex);
            return activatable ? Result.ACTIVATED : Result.CONSUMED;
        }
        return Result.CONSUMED;
    }

    public void dismiss() {
        if (!open) return;
        open = false;
        onDismiss.run();
    }

    private void moveSelection(int direction) {
        if (entries.isEmpty()) return;
        int current = selectedIndex < 0 ? (direction > 0 ? -1 : entries.size()) : selectedIndex;
        for (int step = 0; step < entries.size(); step++) {
            current = Math.floorMod(current + direction, entries.size());
            if (entries.get(current).enabled() && !entries.get(current).isSeparator()) {
                selectedIndex = current;
                return;
            }
        }
    }

    private void activate(int index) {
        Entry entry = entries.get(index);
        if (!entry.enabled() || entry.isSeparator()) return;
        dismiss();
        entry.action().run();
    }

    private int rowAt(double x, double y) {
        if (!bounds.contains(x, y)) return -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).contains(x, y)) return index;
        }
        return -1;
    }

    private static int firstEnabled(List<Entry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.enabled() && !entry.isSeparator()) return index;
        }
        return -1;
    }

    private static List<Row> rows(Bounds bounds, List<Entry> entries) {
        List<Row> result = new ArrayList<>();
        int y = bounds.y() + PADDING;
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            int height = entry.isSeparator() ? SEPARATOR_HEIGHT : ROW_HEIGHT;
            result.add(new Row(index, entry, bounds.x() + PADDING, y, Math.max(0, bounds.width() - PADDING * 2), height));
            y += height;
        }
        return List.copyOf(result);
    }

    public record Bounds(int x, int y, int width, int height) {
        public int maxX() { return x + width; }
        public int maxY() { return y + height; }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    public record Row(int entryIndex, Entry entry, int x, int y, int width, int height) {
        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    public record Entry(
        String label,
        String shortcut,
        boolean enabled,
        boolean divider,
        boolean danger,
        Runnable action
    ) {
        public Entry {
            label = label == null ? "" : label;
            shortcut = shortcut == null ? "" : shortcut;
            action = action == null ? () -> {} : action;
            if (divider) {
                label = "";
                shortcut = "";
                enabled = false;
                danger = false;
            }
        }

        public static Entry item(String label, String shortcut, boolean enabled, boolean danger, Runnable action) {
            return new Entry(label, shortcut, enabled, false, danger, action);
        }

        public static Entry item(String label, Runnable action) {
            return item(label, "", true, false, action);
        }

        public static Entry separator() {
            return new Entry("", "", false, true, false, () -> {});
        }

        public boolean isSeparator() {
            return divider;
        }

        private int preferredWidth() {
            if (divider) return 0;
            return Math.max(MIN_WIDTH, 16 + label.length() * 6 + (shortcut.isBlank() ? 0 : 12 + shortcut.length() * 6));
        }
    }

    public enum Result { IGNORED, CONSUMED, DISMISSED, ACTIVATED }
}
