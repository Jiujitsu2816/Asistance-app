package Software.ulpgc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** In-memory attendance management for a single-user application. */
public final class AttendanceService implements AutoCloseable {
    private final Clock clock;
    private final Map<Integer, Employe> employees = new HashMap<>();
    private final List<Attendance> sessions = new ArrayList<>();
    private final List<Missing> missing = new ArrayList<>();
    private final List<Missing> deadlines = new ArrayList<>();
    private final Map<Missing, ZoneId> deadlineZones = new HashMap<>();
    private final ScheduledExecutorService scheduler;

    public AttendanceService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "attendance-missing");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::checkMissing, 0, 1, TimeUnit.SECONDS);
    }

    public synchronized void registerEmployee(Employe employee) {
        Objects.requireNonNull(employee, "Employee is required");
        if (employees.putIfAbsent(employee.id(), employee) != null)
            throw new IllegalArgumentException("Employee ID is already registered");
    }

    public synchronized Attendance checkIn(int employeeId, Place place) {
        checkMissing();
        Employe employee = requireEmployee(employeeId);
        if (sessions.stream().anyMatch(s -> s.employee().id() == employeeId && s.isOpen()))
            throw new IllegalStateException("Employee is already checked in");
        Attendance attendance = new Attendance(employee, place, clock.instant(), null);
        sessions.add(attendance);
        return attendance;
    }

    public synchronized Attendance checkOut(int employeeId) {
        requireEmployee(employeeId);
        for (int i = 0; i < sessions.size(); i++) {
            Attendance current = sessions.get(i);
            if (current.employee().id() == employeeId && current.isOpen()) {
                Attendance closed = new Attendance(current.employee(), current.place(),
                        current.checkIn(), clock.instant());
                sessions.set(i, closed);
                return closed;
            }
        }
        throw new IllegalStateException("Employee has no open attendance session");
    }

    public synchronized List<Attendance> history(int employeeId) {
        requireEmployee(employeeId);
        return sessions.stream().filter(s -> s.employee().id() == employeeId).toList();
    }

    /** Registers the employee's expected entries from their working schedule. */
    public synchronized void registerQuadrant(Quadrant quadrant) {
        Objects.requireNonNull(quadrant, "Quadrant is required");
        Employe employee = requireEmployee(quadrant.employe().id());
        if (!employee.equals(quadrant.employe()))
            throw new IllegalArgumentException("Quadrant employee does not match registered employee");
        for (Instant entry : quadrant.expectedEntries())
            scheduleMissing(employee.id(), quadrant.place(), entry, quadrant.zone());
    }

    /** The deadline is the expected entry time, which is stored in Missing.date(). */
    public synchronized void scheduleMissing(int employeeId, Place place, Instant deadline) {
        scheduleMissing(employeeId, place, deadline, clock.getZone());
    }

    private void scheduleMissing(int employeeId, Place place, Instant deadline, ZoneId zone) {
        Missing expected = new Missing(requireEmployee(employeeId), place,
                Date.from(Objects.requireNonNull(deadline, "Deadline is required")));
        if (!deadlines.contains(expected) && !missing.contains(expected)) {
            deadlines.add(expected);
            deadlineZones.put(expected, zone);
        }
        checkMissing();
    }

    /** Also callable explicitly, for example when using a controlled clock. */
    public synchronized void checkMissing() {
        Instant now = clock.instant();
        deadlines.removeIf(expected -> {
            Instant deadline = expected.date().toInstant();
            if (now.isBefore(deadline)) return false;
            ZoneId zone = deadlineZones.get(expected);
            LocalDate day = deadline.atZone(zone).toLocalDate();
            boolean checkedIn = sessions.stream().anyMatch(session ->
                    session.employee().id() == expected.employe().id()
                            && session.place().equals(expected.place())
                            && session.checkIn().atZone(zone).toLocalDate().equals(day)
                            && !session.checkIn().isAfter(deadline));
            if (!checkedIn && !missing.contains(expected)) missing.add(expected);
            deadlineZones.remove(expected);
            return true;
        });
    }

    public synchronized List<Missing> missingHistory(int employeeId) {
        requireEmployee(employeeId);
        checkMissing();
        return missing.stream().filter(item -> item.employe().id() == employeeId).toList();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private Employe requireEmployee(int employeeId) {
        Employe employee = employees.get(employeeId);
        if (employee == null) throw new IllegalArgumentException("Unknown employee ID: " + employeeId);
        return employee;
    }
}
