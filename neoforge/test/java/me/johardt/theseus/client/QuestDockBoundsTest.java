package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestDockBoundsTest {
    @Test
    void sidebarSlidesAtItsOriginalWidthAndClipsAtThePresentedEdge() {
        var bounds = new QuestDockPresentation.Bounds(79, 140, 800, 450, true);
        assertEquals(-61, bounds.offset());
        assertEquals(0, bounds.clipLeft());
        assertEquals(79, bounds.clipRight());
        assertTrue(bounds.contains(78, 200));
        assertFalse(bounds.contains(79, 200));
        assertFalse(bounds.contains(-1, 200));
        assertEquals(65, bounds.contentX(4));
        var rail = new QuestDockPresentation.Bounds(18, 140, 800, 450, true);
        assertEquals(-122, rail.offset());
        assertEquals(140, rail.contentWidth());
    }

    @Test
    void rightDockPickingUsesPresentedCoordinatesWithFixedContentDimensions() {
        var details = new QuestDockPresentation.Bounds(680, 240, 800, 450, false);
        assertEquals(120, details.offset());
        assertEquals(580, details.contentX(700));
        assertTrue(details.contains(700, 20));
        assertFalse(details.contains(679, 20));
        assertFalse(details.contains(800, 20));
        assertFalse(details.contains(700, 450));
        var authoring = new QuestDockPresentation.Bounds(680, 360, 800, 450, false);
        assertEquals(details.clipLeft(), authoring.clipLeft());
        assertEquals(240, authoring.offset());
        assertEquals(460, authoring.contentX(700));
    }

    @Test
    void fullyClosedDockCannotOccludeAndNarrowScreensClipToTheirBoundary() {
        var closed = new QuestDockPresentation.Bounds(800, 240, 800, 450, false);
        assertFalse(closed.contains(799, 20));
        var narrow = new QuestDockPresentation.Bounds(-100, 360, 260, 450, false);
        assertEquals(0, narrow.clipLeft());
        assertTrue(narrow.contains(0, 20));
        assertFalse(narrow.contains(-1, 20));
    }
}
