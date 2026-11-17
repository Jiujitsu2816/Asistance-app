package Software.ulpgc;

import java.util.*;

/** Immutable unit of persistence, preserving account display order. */
public record AttendanceSnapshot(Map<String, UserAccount> accounts,
                                 List<AttendanceRequest> requests, List<Quadrant> quadrants) {
    public AttendanceSnapshot {
        accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
        requests = List.copyOf(requests);
        quadrants = List.copyOf(quadrants);
    }
}
