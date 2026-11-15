package com.barisozy.cleanarchitecture.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock the test can move.
 *
 * <p>The application reads the time through an injected {@link Clock}; this implementation lets a
 * test start at a fixed instant and advance it explicitly, so that no assertion depends on the
 * wall clock.
 */
public final class FixedClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public FixedClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private FixedClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Moves the clock forward. */
    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    /** Moves the clock to an absolute instant. */
    public void setInstant(Instant newInstant) {
        instant = newInstant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new FixedClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
