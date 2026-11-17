package Software.ulpgc;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Owns the exclusive file lock, atomic replacement and backup lifecycle. */
public final class PropertiesAttendanceStore implements AttendanceStore {
    private final Path file;
    private final Clock clock;
    private final AttendancePropertiesCodec codec = new AttendancePropertiesCodec();
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    public PropertiesAttendanceStore(Path file, Clock clock) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.clock = Objects.requireNonNull(clock);
        Files.createDirectories(this.file.getParent());
        lockChannel = FileChannel.open(this.file.resolveSibling(this.file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock acquired;
            try { acquired = lockChannel.tryLock(); }
            catch (OverlappingFileLockException exception) { acquired = null; }
            if (acquired == null) throw new IOException("El registro ya esta abierto. Usa Otra ventana en la aplicacion.");
            lock = acquired;
        } catch (IOException | RuntimeException exception) {
            lockChannel.close();
            throw exception;
        }
    }

    @Override public AttendanceSnapshot load() throws IOException {
        ensureOpen();
        if (!exists()) return new AttendanceSnapshot(Map.of(), List.of(), List.of());
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(file)) { data.load(input); }
        return codec.decode(data);
    }

    @Override public void save(AttendanceSnapshot snapshot) throws IOException {
        ensureOpen();
        Properties data = codec.encode(snapshot);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "attendance-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { data.store(output, "Attendance broker"); }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    @Override public boolean exists() {
        ensureOpen();
        return Files.exists(file);
    }

    @Override public void deleteBackups() throws IOException {
        ensureOpen();
        for (Path backup : backupFiles()) Files.delete(backup);
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); }
        finally { lockChannel.close(); }
    }

    private List<Path> backupFiles() throws IOException {
        String prefix = file.getFileName() + ".backup-";
        try (var entries = Files.list(file.getParent())) {
            return entries.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
    }

    @Override public Path createBackup() throws IOException {
        ensureOpen();
        Path backup = file.resolveSibling(file.getFileName() + ".backup-" + UUID.randomUUID());
        Files.copy(file, backup);
        // Explicitly keep the newly created copy, even when timestamps are identical.
        List<Path> previous = new ArrayList<>(backupFiles());
        previous.remove(backup);
        Map<Path, java.nio.file.attribute.FileTime> times = new HashMap<>();
        for (Path path : previous) times.put(path, Files.getLastModifiedTime(path));
        previous.sort(Comparator.<Path, java.nio.file.attribute.FileTime>comparing(times::get).reversed()
                .thenComparing(p -> p.getFileName().toString()));
        // Files.copy may retain the source timestamp. Give each backup a distinct
        // creation order, even when the clock is fixed or the source is unchanged.
        Instant created = clock.instant();
        if (!previous.isEmpty() && !created.isAfter(times.get(previous.getFirst()).toInstant()))
            created = times.get(previous.getFirst()).toInstant().plusMillis(1);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.from(created));
        for (int i = 1; i < previous.size(); i++) Files.delete(previous.get(i));
        return backup;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("El registro esta cerrado");
    }
}
