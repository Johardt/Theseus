package me.johardt.theseus.client;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DockMotionTest {
    @Test
    void startsAtItsIntendedPositionAndMovesLinearlyForOneHundredMilliseconds() {
        AtomicLong now = new AtomicLong(7_000_000_000L);
        DockMotion motion = new DockMotion(18, now::get);
        assertEquals(18, motion.sample());
        motion.target(138, 100, now.get());
        for (int millis : new int[] {0, 7, 25, 50, 99, 100, 200}) {
            now.set(7_000_000_000L + millis * 1_000_000L);
            assertEquals(18 + 120 * Math.min(1.0, millis / 100.0), motion.sample(), 0.000001);
        }
    }

    @Test
    void reversingOrChangingWidthStartsAtTheCurrentlyPresentedEdge() {
        DockMotion motion = new DockMotion(800, () -> 0L);
        motion.target(560, 100, 0);
        assertEquals(704, motion.sample(40_000_000));
        motion.target(800, 100, 40_000_000);
        assertEquals(704, motion.sample(40_000_000));
        assertEquals(752, motion.sample(90_000_000));
        motion.target(440, 100, 90_000_000);
        assertEquals(752, motion.sample(90_000_000));
        assertEquals(596, motion.sample(140_000_000));
        assertEquals(440, motion.sample(190_000_000));
    }

    @Test
    void contentRebuildsDoNotRestartMotionAndCopiesPreserveItsTimeline() {
        DockMotion motion = new DockMotion(140, () -> 0L);
        motion.target(18, 100, 0);
        motion.target(18, 100, 60_000_000);
        DockMotion replacement = motion.copy();
        assertEquals(79, replacement.sample(50_000_000));
        assertEquals(18, replacement.sample(100_000_000));
        motion.settle(140);
        assertEquals(18, replacement.sample(100_000_000));
        assertEquals(140, motion.sample(100_000_000));
    }

    @Test
    void durationIsVariableAndZeroDisablesMotion() {
        DockMotion motion = new DockMotion(0, () -> 0L);
        motion.target(200, 250, 0);
        assertEquals(100, motion.sample(125_000_000));
        assertEquals(200, motion.sample(250_000_000));
        motion.target(18, 0, 250_000_000);
        assertEquals(18, motion.sample(250_000_000));
        motion.target(140, -1, 250_000_000);
        assertEquals(140, motion.sample(250_000_000));
    }
}
