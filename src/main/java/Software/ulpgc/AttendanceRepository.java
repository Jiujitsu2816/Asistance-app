package Software.ulpgc;

import java.io.IOException;

/** Saves complete snapshots. A failed save must preserve the previous snapshot. */
public interface AttendanceRepository {
    AttendanceSnapshot load() throws IOException;
    void save(AttendanceSnapshot snapshot) throws IOException;
}
