package Software.ulpgc;

import java.time.ZoneId;
import java.util.List;

/** Requires an entry at the scheduled place, on the same local day, by the deadline. */
public final class OnTimeEntryPolicy implements AbsencePolicy {
    @Override public boolean isCovered(Missing expected, ZoneId zone, List<Attendance> sessions) {
        var deadline = expected.date().toInstant();
        var day = deadline.atZone(zone).toLocalDate();
        return sessions.stream().anyMatch(session ->
                session.employee().id() == expected.employe().id()
                        && session.place().equals(expected.place())
                        && session.checkIn().atZone(zone).toLocalDate().equals(day)
                        && !session.checkIn().isAfter(deadline));
    }
}
