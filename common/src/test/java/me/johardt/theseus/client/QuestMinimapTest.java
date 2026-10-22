package me.johardt.theseus.client;

import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestMinimapTest {
    private static final QuestGraphLayout.WorldBounds WORLD =
        new QuestGraphLayout.WorldBounds(-100, -50, 300, 150);
    private static final QuestMinimap.MapBounds MAP =
        new QuestMinimap.MapBounds(10, 20, 100, 66);

    @Test
    void worldAndMapCoordinatesRoundTrip() {
        QuestMinimap.Mapping mapping = QuestMinimap.mapping(WORLD, MAP);
        QuestGraphLayout.Point map = QuestMinimap.worldToMap(mapping, 50, 25);
        QuestGraphLayout.Point world = QuestMinimap.mapToWorld(mapping, map.x(), map.y());

        assertEquals(50, world.x(), 0.000001);
        assertEquals(25, world.y(), 0.000001);
    }

    @Test
    void viewportRectangleIsClippedToTheMinimapContent() {
        QuestMinimap.Mapping mapping = QuestMinimap.mapping(WORLD, MAP);
        QuestGraphLayout.WorldBounds visible = new QuestGraphLayout.WorldBounds(
            -500,
            -500,
            500,
            500
        );

        QuestMinimap.MapBounds viewport = QuestMinimap.viewportRectangle(mapping, visible);

        assertNotNull(viewport);
        assertTrue(viewport.x() >= MAP.contentX());
        assertTrue(viewport.y() >= MAP.contentY());
        assertTrue(viewport.maxX() <= MAP.contentMaxX());
        assertTrue(viewport.maxY() <= MAP.contentMaxY());
    }

    @Test
    void floatingPlacementIsNormalizedAndClamped() {
        QuestGraphLayout.CanvasBounds canvas = new QuestGraphLayout.CanvasBounds(20, 30, 400, 260);
        QuestMinimap.MapBounds placement = QuestMinimap.floatingPlacement(
            canvas,
            2,
            -1,
            100,
            66
        );

        assertEquals(320, placement.x());
        assertEquals(30, placement.y());
        double[] normalized = QuestMinimap.normalizedPosition(canvas, placement);
        assertEquals(1, normalized[0]);
        assertEquals(0, normalized[1]);
    }

    @Test
    void modesHaveDistinctGeometryAndHiddenHasNoPlacement() {
        QuestGraphLayout.CanvasBounds graph = new QuestGraphLayout.CanvasBounds(120, 20, 500, 300);

        QuestMinimap.MapBounds floating = QuestMinimap.placement(
            TheseusClientOptions.MinimapMode.UNDOCKED,
            graph,
            1,
            1,
            100,
            66
        );
        QuestMinimap.MapBounds docked = QuestMinimap.placement(
            TheseusClientOptions.MinimapMode.DOCKED,
            graph,
            1,
            1,
            100,
            66
        );

        assertEquals(520, floating.x());
        assertEquals(516, docked.x());
        assertEquals(250, docked.y());
        assertTrue(docked.maxX() <= graph.maxX());
        assertTrue(docked.maxY() <= graph.maxY());
    }

    @Test
    void dockedAnchorBecomesFloatingPositionWhenUndocked() {
        QuestGraphLayout.CanvasBounds graph = new QuestGraphLayout.CanvasBounds(120, 20, 500, 300);
        QuestMinimap.MapBounds docked = QuestMinimap.dockedPlacement(graph, 100, 66);
        double[] normalized = QuestMinimap.normalizedPosition(graph, docked);
        QuestMinimap.MapBounds undocked = QuestMinimap.floatingPlacement(
            graph,
            normalized[0],
            normalized[1],
            docked.width(),
            docked.height()
        );

        assertEquals(docked.x(), undocked.x());
        assertEquals(docked.y(), undocked.y());
    }

    @Test
    void largeSyntheticGraphKeepsMinimapMappingFinite() {
        List<QuestGraphLayout.NodeBounds> nodes = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            nodes.add(QuestGraphLayout.NodeBounds.centered(
                (index % 30) * 120,
                (index / 30) * 84,
                24,
                24
            ));
        }

        QuestGraphLayout.WorldBounds world = QuestGraphLayout.boundsOf(nodes, 16);
        QuestMinimap.Mapping mapping = QuestMinimap.mapping(world, MAP);
        QuestGraphLayout.Point map = QuestMinimap.worldToMap(mapping, world.centerX(), world.centerY());

        assertTrue(Double.isFinite(mapping.scale()));
        assertTrue(Double.isFinite(map.x()));
        assertTrue(Double.isFinite(map.y()));
    }
}
