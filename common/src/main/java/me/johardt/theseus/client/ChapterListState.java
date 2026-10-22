package me.johardt.theseus.client;

import java.util.List;
import java.util.stream.IntStream;

/** Scroll and visible-index rules for the quest chapter list. */
public final class ChapterListState {
    private int firstVisibleRow;
    private int chapterCount;
    private int viewportTop;
    private int viewportBottom;
    private int rowHeight = 23;

    public void setViewport(int top, int bottom, int rowHeight) {
        viewportTop = Math.max(0, top);
        viewportBottom = Math.max(viewportTop, bottom);
        this.rowHeight = Math.max(1, rowHeight);
        clampFirstVisibleRow();
    }

    public void setChapterCount(int count) {
        chapterCount = Math.max(0, count);
        clampFirstVisibleRow();
    }

    public void reset() {
        firstVisibleRow = 0;
    }

    public int chapterCount() { return chapterCount; }
    public int firstVisibleRow() { return firstVisibleRow; }
    public int viewportTop() { return viewportTop; }
    public int viewportBottom() { return viewportBottom; }
    public int rowHeight() { return rowHeight; }

    /** Number of whole rows that fit in the current viewport. */
    public int visibleCapacity() {
        return Math.max(0, (viewportBottom - viewportTop) / rowHeight);
    }

    public int maxFirstVisibleRow() {
        return Math.max(0, chapterCount - visibleCapacity());
    }

    public boolean hasOverflow() {
        return chapterCount > visibleCapacity();
    }

    public void scrollByRows(int rows) {
        firstVisibleRow = Math.clamp(firstVisibleRow + rows, 0, maxFirstVisibleRow());
    }

    public void scrollByPage(int direction) {
        int page = Math.max(1, visibleCapacity());
        scrollByRows(direction * page);
    }

    public void ensureVisible(int index) {
        if (index < 0 || index >= chapterCount || visibleCapacity() == 0) return;
        if (index < firstVisibleRow) firstVisibleRow = index;
        else if (index >= firstVisibleRow + visibleCapacity()) {
            firstVisibleRow = index - visibleCapacity() + 1;
        }
        clampFirstVisibleRow();
    }

    public int indexAtRow(int row) {
        if (row < 0 || row >= visibleCapacity()) return -1;
        int index = firstVisibleRow + row;
        return index < chapterCount ? index : -1;
    }

    public int rowAt(double mouseY) {
        if (mouseY < viewportTop || mouseY >= viewportBottom) return -1;
        return (int) ((mouseY - viewportTop) / rowHeight);
    }

    public List<Integer> visibleIndices() {
        return IntStream.range(0, visibleCapacity())
            .map(this::indexAtRow)
            .filter(index -> index >= 0)
            .boxed()
            .toList();
    }

    public ChapterListState copy() {
        ChapterListState copy = new ChapterListState();
        copy.firstVisibleRow = firstVisibleRow;
        copy.chapterCount = chapterCount;
        copy.viewportTop = viewportTop;
        copy.viewportBottom = viewportBottom;
        copy.rowHeight = rowHeight;
        return copy;
    }

    private void clampFirstVisibleRow() {
        firstVisibleRow = Math.clamp(firstVisibleRow, 0, maxFirstVisibleRow());
    }
}
