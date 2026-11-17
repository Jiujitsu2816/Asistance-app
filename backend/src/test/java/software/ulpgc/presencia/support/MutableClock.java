package software.ulpgc.presencia.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Reloj controlable para reproducir fechas en las pruebas. */
public final class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override public ZoneId getZone() { return zone; }

    @Override public Clock withZone(ZoneId zone) { return new MutableClock(now, zone); }

    @Override public Instant instant() { return now; }
}
