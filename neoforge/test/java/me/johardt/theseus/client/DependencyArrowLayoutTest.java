package me.johardt.theseus.client;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyArrowLayoutTest {
    @Test
    void spacingStaysUniformAcrossTheLoopSeamAtEveryAnimationPhase() {
        long length = 180;
        int count = 4;
        for (int travel = 0; travel < length * 2; travel++) {
            long[] offsets = new long[count];
            for (int marker = 1; marker <= count; marker++) {
                offsets[marker - 1] = DependencyArrowLayout.offset(length, count, marker, travel);
            }
            Arrays.sort(offsets);
            for (int i = 0; i < count; i++) {
                long next = i + 1 == count ? offsets[0] + length : offsets[i + 1];
                assertEquals(45, next - offsets[i], "Uneven gap at travel=" + travel);
            }
        }
    }
    @Test
    void fractionalSpacingStaysWithinOnePixelWithThePerformanceCap() {
        for (long length : new long[] {17, 97, 503, 10_000}) {
            int count = 6;
            for (double travel : new double[] {0, 0.25, 17.75, 1000.5, 1_000_000.125}) {
                long[] offsets = new long[count];
                for (int marker = 1; marker <= count; marker++) {
                    offsets[marker - 1] = DependencyArrowLayout.offset(length, count, marker, travel);
                    assertTrue(offsets[marker - 1] >= 0 && offsets[marker - 1] < length);
                }
                Arrays.sort(offsets);
                for (int i = 0; i < count; i++) {
                    long next = i + 1 == count ? offsets[0] + length : offsets[i + 1];
                    assertTrue(Math.abs(next - offsets[i] - (double) length / count) < 1.0);
                }
            }
        }
    }
}
