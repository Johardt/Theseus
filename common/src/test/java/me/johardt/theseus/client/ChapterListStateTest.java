package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterListStateTest {
    @Test
    void mapsRowsToGlobalIndicesAndClampsScroll() {
        ChapterListState state = new ChapterListState();
        state.setViewport(34, 126, 23);
        state.setChapterCount(10);

        assertEquals(4, state.visibleCapacity());
        assertEquals(0, state.indexAtRow(0));
        assertEquals(3, state.indexAtRow(3));
        assertEquals(-1, state.indexAtRow(4));

        state.scrollByRows(99);
        assertEquals(6, state.firstVisibleRow());
        assertEquals(6, state.indexAtRow(0));
        state.scrollByRows(-99);
        assertEquals(0, state.firstVisibleRow());
    }

    @Test
    void pageScrollAndEnsureVisibleKeepSelectionInTheViewport() {
        ChapterListState state = new ChapterListState();
        state.setViewport(34, 126, 23);
        state.setChapterCount(20);

        state.scrollByPage(1);
        assertEquals(4, state.firstVisibleRow());
        state.ensureVisible(2);
        assertEquals(2, state.firstVisibleRow());
        state.ensureVisible(19);
        assertEquals(16, state.firstVisibleRow());
        assertTrue(state.visibleIndices().contains(19));
    }

    @Test
    void viewportAndCountChangesClampWithoutLosingValidSelectionRules() {
        ChapterListState state = new ChapterListState();
        state.setViewport(0, 100, 20);
        state.setChapterCount(9);
        state.scrollByRows(7);
        assertEquals(4, state.firstVisibleRow());

        state.setChapterCount(3);
        assertEquals(0, state.firstVisibleRow());
        state.setViewport(0, 40, 20);
        assertEquals(2, state.visibleCapacity());
        assertEquals(List.of(0, 1), state.visibleIndices());
    }
}
