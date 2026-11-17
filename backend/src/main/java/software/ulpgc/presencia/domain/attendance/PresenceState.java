package software.ulpgc.presencia.domain.attendance;

import java.time.Instant;

public record PresenceState(String personId, PresenceStatus status, boolean inside, Instant since,
                            String currentPeriod, Instant currentPeriodStart, Instant currentPeriodEnd,
                            DayStatus todayStatus) { }
