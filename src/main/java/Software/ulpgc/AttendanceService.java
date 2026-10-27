package Software.ulpgc;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** In-memory attendance management for a single-user application. */
public final class AttendanceService {
    private final Clock clock;
    private final Map<Integer, Employe> employees = new HashMap<>();
    private final List<Attendance> sessions = new ArrayList<>();

    public AttendanceService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public void registerEmployee(Employe employee) {
        Objects.requireNonNull(employee, "Employee is required");
        if (employees.putIfAbsent(employee.id(), employee) != null)
            throw new IllegalArgumentException("Employee ID is already registered");
    }

    public Attendance checkIn(int employeeId, Place place) {
        return checkInAt(employeeId, place, clock.instant());
    }

    Attendance checkInAt(int employeeId, Place place, Instant time) {
        Employe employee = requireEmployee(employeeId);
        if (sessions.stream().anyMatch(s -> s.employee().id() == employeeId && s.isOpen()))
            throw new IllegalStateException("Employee is already checked in");
        Attendance attendance = new Attendance(employee, place, time, null);
        sessions.add(attendance);
        return attendance;
    }

    public Attendance checkOut(int employeeId) {
        return checkOutAt(employeeId, clock.instant());
    }

    Attendance checkOutAt(int employeeId, Instant time) {
        requireEmployee(employeeId);
        for (int i = 0; i < sessions.size(); i++) {
            Attendance current = sessions.get(i);
            if (current.employee().id() == employeeId && current.isOpen()) {
                Attendance closed = new Attendance(current.employee(), current.place(),
                        current.checkIn(), time);
                sessions.set(i, closed);
                return closed;
            }
        }
        throw new IllegalStateException("Employee has no open attendance session");
    }

    public List<Attendance> history(int employeeId) {
        requireEmployee(employeeId);
        return sessions.stream().filter(s -> s.employee().id() == employeeId).toList();
    }

    private Employe requireEmployee(int employeeId) {
        Employe employee = employees.get(employeeId);
        if (employee == null) throw new IllegalArgumentException("Unknown employee ID: " + employeeId);
        return employee;
    }
}
