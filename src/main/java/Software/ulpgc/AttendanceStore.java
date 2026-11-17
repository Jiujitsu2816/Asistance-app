package Software.ulpgc;

import java.io.IOException;
import java.nio.file.Path;

/** Exclusive repository lifecycle and local administration. Close is idempotent;
 * operations after close must fail rather than access a repository without its lock. */
public interface AttendanceStore extends AttendanceRepository, AutoCloseable {
    boolean exists();
    Path createBackup() throws IOException;
    void deleteBackups() throws IOException;
    @Override void close() throws IOException;
}
