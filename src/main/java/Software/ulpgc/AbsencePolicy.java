package Software.ulpgc;

import java.time.ZoneId;
import java.util.List;

/** Determines whether approved attendance covers an expected entry. */
@FunctionalInterface
public interface AbsencePolicy {
    boolean isCovered(Missing expected, ZoneId zone, List<Attendance> sessions);
}
