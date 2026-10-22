package me.johardt.theseus.client;

import java.util.function.LongSupplier;

/** Interruptible linear motion, measured in monotonic nanoseconds. */
final class DockMotion {
    static final int DEFAULT_DURATION_MILLIS = 100;
    private final LongSupplier clock;
    private double start;
    private double target;
    private long started;
    private long duration;

    DockMotion(double position) { this(position, System::nanoTime); }

    DockMotion(double position, LongSupplier clock) {
        this.clock = clock;
        settle(position);
    }

    void settle(double position) {
        start = target = position;
        duration = 0;
    }

    double sample() { return sample(clock.getAsLong()); }

    double sample(long now) {
        if (duration == 0) return target;
        double progress = Math.clamp((double) (now - started) / duration, 0.0, 1.0);
        return start + (target - start) * progress;
    }

    void target(double position, int durationMillis, long now) {
        if (position == target) return;
        start = sample(now);
        target = position;
        started = now;
        duration = Math.max(0L, durationMillis) * 1_000_000L;
    }

    boolean finished(long now) { return duration == 0 || now - started >= duration; }

    DockMotion copy() {
        DockMotion copy = new DockMotion(target, clock);
        copy.start = start;
        copy.started = started;
        copy.duration = duration;
        return copy;
    }
}
