package me.johardt.theseus.client;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestGraphLayoutTest {
    private static final QuestGraphLayout.CanvasBounds CANVAS =
        new QuestGraphLayout.CanvasBounds(10, 20, 300, 200);

    @Test
    void worldAndScreenCoordinatesRoundTripAtSeveralViewports() {
        List<QuestGraphLayout.ViewportState> viewports = List.of(
            new QuestGraphLayout.ViewportState(0, 0, 0.15),
            new QuestGraphLayout.ViewportState(23.5, -17.25, 0.75),
            new QuestGraphLayout.ViewportState(-80, 45, 2.0)
        );

        for (QuestGraphLayout.ViewportState viewport : viewports) {
            QuestGraphLayout.Point screen = QuestGraphLayout.worldToScreen(
                CANVAS,
                viewport,
                42.5,
                -13.25
            );
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                CANVAS,
                viewport,
                screen.x(),
                screen.y()
            );

            assertEquals(42.5, world.x(), 0.000001);
            assertEquals(-13.25, world.y(), 0.000001);
        }
    }

    @Test
    void fitHandlesEmptySingleWideTallAndPaddedContent() {
        QuestGraphLayout.ViewportState empty = QuestGraphLayout.fitViewport(
            CANVAS,
            QuestGraphLayout.WorldBounds.empty()
        );
        assertEquals(0, empty.centerWorldX());
        assertEquals(0, empty.centerWorldY());
        assertEquals(1, empty.zoom());

        QuestGraphLayout.WorldBounds single = QuestGraphLayout.boundsOf(
            List.of(QuestGraphLayout.NodeBounds.centered(100, 50, 24, 24)),
            8
        );
        QuestGraphLayout.ViewportState singleFit = QuestGraphLayout.fitViewport(CANVAS, single);
        assertEquals(100, singleFit.centerWorldX());
        assertEquals(50, singleFit.centerWorldY());
        assertEquals(1, singleFit.zoom());

        QuestGraphLayout.WorldBounds wide = new QuestGraphLayout.WorldBounds(-1000, -10, 1000, 10);
        QuestGraphLayout.ViewportState wideFit = QuestGraphLayout.fitViewport(CANVAS, wide);
        assertEquals(0, wideFit.centerWorldX());
        assertEquals(0, wideFit.centerWorldY());
        assertEquals(0.15, wideFit.zoom());

        QuestGraphLayout.WorldBounds tall = new QuestGraphLayout.WorldBounds(-10, -1000, 10, 1000);
        QuestGraphLayout.ViewportState tallFit = QuestGraphLayout.fitViewport(CANVAS, tall);
        assertEquals(0.15, tallFit.zoom());

        assertTrue(single.minX() < 88);
        assertTrue(single.maxX() > 112);
        assertFalse(single.isEmpty());
    }

    @Test
    void visibleWorldIsFiniteForLargeSyntheticGraphs() {
        List<QuestGraphLayout.NodeBounds> nodes = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            nodes.add(QuestGraphLayout.NodeBounds.centered(
                (index % 25) * 96,
                (index / 25) * 72,
                24,
                24
            ));
        }

        QuestGraphLayout.WorldBounds bounds = QuestGraphLayout.boundsOf(nodes, 48);
        QuestGraphLayout.ViewportState viewport = QuestGraphLayout.fitViewport(CANVAS, bounds);
        QuestGraphLayout.WorldBounds visible = QuestGraphLayout.visibleWorld(CANVAS, viewport);

        assertTrue(Double.isFinite(bounds.minX()));
        assertTrue(Double.isFinite(bounds.maxY()));
        assertTrue(Double.isFinite(viewport.zoom()));
        assertTrue(Double.isFinite(visible.width()));
        assertTrue(Double.isFinite(visible.height()));
    }

    @Test
    void snapsPositiveNegativeAndHalfwayCoordinatesToTwentySevenUnitCells() {
        assertEquals(27, QuestGraphLayout.GRID_CELL_SIZE);
        assertEquals(0, QuestGraphLayout.snapCoordinate(0));
        assertEquals(27, QuestGraphLayout.snapCoordinate(14));
        assertEquals(27, QuestGraphLayout.snapCoordinate(13.5));
        assertEquals(0, QuestGraphLayout.snapCoordinate(-13.5));
        assertEquals(-27, QuestGraphLayout.snapCoordinate(-14));
        assertEquals(27, QuestGraphLayout.snapCoordinate(27));
        assertEquals(QuestGraphLayout.snapCoordinate(-14), QuestGraphLayout.snapCoordinate(QuestGraphLayout.snapCoordinate(-14)));
    }

    @Test
    void visibleGridRangeContainsOnlyOnScreenGridLines() {
        QuestGraphLayout.GridLineRange range = QuestGraphLayout.visibleGridLineRange(
            new QuestGraphLayout.WorldBounds(-30, -1, 55, 55)
        );

        assertEquals(-27, range.firstX());
        assertEquals(54, range.lastX());
        assertEquals(0, range.firstY());
        assertEquals(54, range.lastY());
        assertEquals(4, range.xCount());
        assertEquals(3, range.yCount());
    }

    @Test
    void visibleGridRangeUsesViewportCenterAndZoomWithoutExpandingUnboundedSpace() {
        QuestGraphLayout.GridLineRange range = QuestGraphLayout.visibleGridLineRange(
            CANVAS,
            new QuestGraphLayout.ViewportState(-40, 20, 2)
        );

        assertEquals(-108, range.firstX());
        assertEquals(27, range.lastX());
        assertEquals(-27, range.firstY());
        assertEquals(54, range.lastY());
    }

    @Test
    void visiblePathTilesSkipOffscreenSegmentsAndBoundCrossingSegments() {
        QuestGraphLayout.WorldBounds visible = new QuestGraphLayout.WorldBounds(
            0,
            0,
            100,
            100
        );

        QuestGraphLayout.PathTileRange offscreen = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(-1000, -100),
            new QuestGraphLayout.Point(-100, -100),
            visible,
            3,
            3
        );
        QuestGraphLayout.PathTileRange crossing = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(-10_000, 50),
            new QuestGraphLayout.Point(10_000, 50),
            visible,
            3,
            3
        );

        assertTrue(offscreen.isEmpty());
        assertEquals(3_332, crossing.firstTile());
        assertEquals(3_368, crossing.endTileExclusive());
        assertEquals(36, crossing.tileCount());
    }

    @Test
    void visiblePathTilesKeepTheOriginalTexturePhaseAndStrokeMargin() {
        QuestGraphLayout.WorldBounds visible = new QuestGraphLayout.WorldBounds(
            0,
            0,
            10,
            10
        );
        QuestGraphLayout.PathTileRange clipped = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(-20, 5),
            new QuestGraphLayout.Point(20, 5),
            visible,
            3,
            3
        );
        QuestGraphLayout.PathTileRange tangent = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(-10, -3),
            new QuestGraphLayout.Point(20, -3),
            visible,
            3,
            3
        );
        QuestGraphLayout.PathTileRange outsideStroke = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(-10, -4),
            new QuestGraphLayout.Point(20, -4),
            visible,
            3,
            3
        );

        assertEquals(5, clipped.firstTile());
        assertEquals(11, clipped.endTileExclusive());
        assertFalse(tangent.isEmpty());
        assertTrue(outsideStroke.isEmpty());
    }

    @Test
    void extremeIntegerEndpointDistanceKeepsOnlyVisibleTilesWithoutOverflow() {
        QuestGraphLayout.PathTileRange visibleTiles = QuestGraphLayout.visiblePathTiles(
            new QuestGraphLayout.Point(Integer.MIN_VALUE, 0),
            new QuestGraphLayout.Point(Integer.MAX_VALUE, 0),
            new QuestGraphLayout.WorldBounds(0, -10, 100, 10),
            3,
            3
        );

        assertTrue(visibleTiles.pixelLength() > Integer.MAX_VALUE);
        assertTrue(visibleTiles.firstTile() > 700_000_000);
        assertEquals(36, visibleTiles.tileCount());
    }

    @Test
    void zoomAroundCursorKeepsTheWorldPointStableAndClamps() {
        QuestGraphLayout.Point before = QuestGraphLayout.screenToWorld(
            CANVAS,
            QuestGraphLayout.ViewportState.DEFAULT,
            210,
            75
        );
        QuestGraphLayout.ViewportState zoomed = QuestGraphLayout.zoomAroundScreenPoint(
            CANVAS,
            QuestGraphLayout.ViewportState.DEFAULT,
            210,
            75,
            0.75
        );
        QuestGraphLayout.Point after = QuestGraphLayout.screenToWorld(CANVAS, zoomed, 210, 75);

        assertEquals(before.x(), after.x(), 0.000001);
        assertEquals(before.y(), after.y(), 0.000001);
        assertEquals(0.15, QuestGraphLayout.zoomAroundScreenPoint(
            CANVAS, zoomed, 210, 75, -10
        ).zoom());
        assertEquals(2.0, QuestGraphLayout.zoomAroundScreenPoint(
            CANVAS, zoomed, 210, 75, 10
        ).zoom());
    }

    @Test
    void viewportMemoryRestoresEachChapterAndCopiesIndependently() {
        QuestGraphLayout.WorldBounds large = new QuestGraphLayout.WorldBounds(-1000, -1000, 1000, 1000);
        QuestGraphLayout.ViewportMemory memory = new QuestGraphLayout.ViewportMemory();

        memory.activateChapter("one", CANVAS, large);
        memory.centerOn(120, -40);
        memory.activateChapter("two", CANVAS, QuestGraphLayout.WorldBounds.empty());
        memory.activateChapter("one", CANVAS, large);

        assertEquals(120, memory.state().centerWorldX());
        assertEquals(-40, memory.state().centerWorldY());

        QuestGraphLayout.ViewportMemory copy = memory.copy();
        memory.centerOn(0, 0);
        assertEquals(120, copy.state().centerWorldX());
        assertEquals(-40, copy.state().centerWorldY());
    }
}
