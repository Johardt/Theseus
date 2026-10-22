package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestMinimapPanelTest {
    private static final QuestGraphLayout.CanvasBounds CANVAS =
        new QuestGraphLayout.CanvasBounds(0, 0, 400, 300);
    @Test
    void bodyClickReturnsNavigationIntentAndDetailsDockTakesPrecedence() {
        QuestMinimapPanel panel = new QuestMinimapPanel();
        QuestMinimapPanel.Settings settings = settings(0.5, 0.5);
        QuestMinimap.MapBounds bounds = panel.bounds(CANVAS, settings);

        QuestMinimapPanel.Result ignored = panel.mouseClicked(
            CANVAS,
            settings,
            bounds.contentX() + 10,
            bounds.contentY() + 10,
            0,
            true
        );
        QuestMinimapPanel.Result clicked = panel.mouseClicked(
            CANVAS,
            settings,
            bounds.contentX() + 10,
            bounds.contentY() + 10,
            0,
            false
        );

        assertFalse(ignored.handled());
        assertTrue(clicked.handled());
        QuestMinimapPanel.Navigate navigation = assertInstanceOf(
            QuestMinimapPanel.Navigate.class,
            clicked.action()
        );
        assertEquals(bounds, navigation.mapBounds());
        assertEquals(bounds.contentX() + 10, navigation.mapX());
        assertEquals(bounds.contentY() + 10, navigation.mapY());
    }

    @Test
    void floatingDragPersistsNormalizedPositionOnlyOnRelease() {
        QuestMinimapPanel panel = new QuestMinimapPanel();
        QuestMinimapPanel.Settings settings = settings(0.5, 0.5);
        QuestMinimap.MapBounds initial = panel.bounds(CANVAS, settings);
        QuestMinimapPanel.Result started = panel.mouseClicked(
            CANVAS,
            settings,
            initial.x() + 2,
            initial.y() + 3,
            0,
            false
        );

        assertTrue(started.handled());
        QuestMinimapPanel.Result dragged = panel.mouseDragged(
            CANVAS,
            settings,
            212,
            210
        );
        assertTrue(dragged.handled());
        assertNull(dragged.action());

        QuestMinimapPanel.Result released = panel.mouseReleased(settings);
        QuestMinimapPanel.SavePosition saved = assertInstanceOf(
            QuestMinimapPanel.SavePosition.class,
            released.action()
        );
        assertEquals(0.7, saved.x(), 0.000001);
        assertEquals(207.0 / 234.0, saved.y(), 0.000001);
        assertTrue(released.handled());
    }

    @Test
    void rebuildKeepsCurrentPositionButDropsTheActiveGesture() {
        QuestMinimapPanel panel = new QuestMinimapPanel();
        QuestMinimapPanel.Settings settings = settings(0.5, 0.5);
        QuestMinimap.MapBounds initial = panel.bounds(CANVAS, settings);
        panel.mouseClicked(CANVAS, settings, initial.x() + 2, initial.y() + 3, 0, false);
        panel.mouseDragged(CANVAS, settings, 212, 210);

        QuestMinimapPanel copy = panel.copyForRebuild();
        QuestMinimap.MapBounds copiedBounds = copy.bounds(CANVAS, settings);

        assertEquals(210, copiedBounds.x());
        assertEquals(207, copiedBounds.y());
        assertFalse(copy.mouseReleased(settings).handled());
    }

    @Test
    void hiddenStateSurvivesRebuildAndCanBeShownAgain() {
        QuestMinimapPanel panel = new QuestMinimapPanel();
        panel.setHidden(true);

        QuestMinimapPanel copy = panel.copyForRebuild();

        assertNull(copy.bounds(CANVAS, settings(0.5, 0.5)));
        copy.setHidden(false);
        assertEquals(QuestMinimap.DEFAULT_WIDTH, copy.bounds(CANVAS, settings(0.5, 0.5)).width());
    }

    private static QuestMinimapPanel.Settings settings(double x, double y) {
        return new QuestMinimapPanel.Settings(
            false,
            TheseusClientOptions.MinimapMode.UNDOCKED,
            x,
            y
        );
    }
}
