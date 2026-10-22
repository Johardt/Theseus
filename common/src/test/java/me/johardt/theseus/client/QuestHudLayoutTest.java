package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestHudLayoutTest {
    @Test
    void everyAnchorUsesItsExpectedSideAndVerticalBand() {
        for (TheseusClientOptions.TrackerAnchor anchor : TheseusClientOptions.TrackerAnchor.values()) {
            QuestHudLayout.Bounds bounds = QuestHudLayout.layout(400, 240, 168, 60, anchor);
            boolean right = anchor.name().endsWith("RIGHT");
            assertEquals(right ? 226 : 6, bounds.x(), anchor.name());
            switch (anchor) {
                case TOP_LEFT, TOP_RIGHT -> assertEquals(6, bounds.y());
                case UPPER_LEFT, UPPER_RIGHT -> assertEquals(60, bounds.y());
                case LOWER_LEFT, LOWER_RIGHT -> assertEquals(120, bounds.y());
                case BOTTOM_LEFT, BOTTOM_RIGHT -> assertEquals(174, bounds.y());
            }
        }
    }

    @Test
    void oversizedTrackerIsClampedInsideTinyScreen() {
        QuestHudLayout.Bounds bounds = QuestHudLayout.layout(
            40,
            24,
            168,
            120,
            TheseusClientOptions.TrackerAnchor.LOWER_RIGHT
        );

        assertEquals(new QuestHudLayout.Bounds(0, 0, 40, 24), bounds);
        assertTrue(bounds.maxX() <= 40);
        assertTrue(bounds.maxY() <= 24);
    }
}
