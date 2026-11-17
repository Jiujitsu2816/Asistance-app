package Software.ulpgc;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Encodes the version-1 file format independently of filesystem operations. */
final class AttendancePropertiesCodec {
    Properties encode(AttendanceSnapshot snapshot) {
        var users = snapshot.accounts();
        var items = snapshot.requests();
        var schedules = snapshot.quadrants();
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("users", Integer.toString(users.size()));
        int i = 0;
        for (UserAccount account : users.values()) {
            String prefix = "user." + i++ + ".";
            data.setProperty(prefix + "name", account.username());
            data.setProperty(prefix + "salt", account.salt());
            data.setProperty(prefix + "hash", account.passwordHash());
            data.setProperty(prefix + "role", account.role().name());
            if (account.employee() != null) writeEmployee(data, prefix, account.employee());
        }
        data.setProperty("requests", Integer.toString(items.size()));
        for (i = 0; i < items.size(); i++) {
            AttendanceRequest r = items.get(i);
            String prefix = "request." + i + ".";
            data.setProperty(prefix + "id", r.id().toString());
            writeEmployee(data, prefix, r.employee());
            data.setProperty(prefix + "place", r.place().name());
            data.setProperty(prefix + "placeType", r.place().type());
            data.setProperty(prefix + "x", Integer.toString(r.place().x()));
            data.setProperty(prefix + "y", Integer.toString(r.place().y()));
            data.setProperty(prefix + "type", r.type().name());
            data.setProperty(prefix + "time", r.requestedAt().toString());
            data.setProperty(prefix + "status", r.status().name());
            data.setProperty(prefix + "reason", r.reason());
            if (r.decidedAt() != null) {
                data.setProperty(prefix + "decidedAt", r.decidedAt().toString());
                data.setProperty(prefix + "decidedBy", r.decidedBy());
            }
        }
        data.setProperty("quadrants", Integer.toString(schedules.size()));
        for (int q = 0; q < schedules.size(); q++) {
            Quadrant schedule = schedules.get(q);
            String p = "quadrant." + q + ".";
            writeEmployee(data, p, schedule.employe());
            data.setProperty(p + "place", schedule.place().name());
            data.setProperty(p + "placeType", schedule.place().type());
            data.setProperty(p + "x", Integer.toString(schedule.place().x()));
            data.setProperty(p + "y", Integer.toString(schedule.place().y()));
            data.setProperty(p + "zone", schedule.zone().getId());
            data.setProperty(p + "entries", Integer.toString(schedule.labor().size()));
            for (int e = 0; e < schedule.labor().size(); e++) {
                data.setProperty(p + "date." + e, schedule.labor().get(e).toString());
                data.setProperty(p + "time." + e, schedule.timetable().get(e).toString());
                if (!schedule.endTimes().isEmpty())
                    data.setProperty(p + "end." + e, schedule.endTimes().get(e).toString());
            }
        }
        return data;
    }

    AttendanceSnapshot decode(Properties data) throws IOException {
        var accounts = new LinkedHashMap<String, UserAccount>();
        var requests = new ArrayList<AttendanceRequest>();
        var quadrants = new ArrayList<Quadrant>();
        try {
            if (!"1".equals(data.getProperty("version"))) throw new IllegalArgumentException("Version desconocida");
            for (int i = 0, count = storedCount(data, "users", null); i < count; i++) {
                String p = "user." + i + ".";
                AttendanceBroker.Role role = AttendanceBroker.Role.valueOf(data.getProperty(p + "role"));
                String username = Objects.requireNonNull(data.getProperty(p + "name"));
                UserAccount account = new UserAccount(username, Objects.requireNonNull(data.getProperty(p + "salt")),
                        Objects.requireNonNull(data.getProperty(p + "hash")), role,
                        role == AttendanceBroker.Role.EMPLOYEE ? readEmployee(data, p) : null);
                if (accounts.putIfAbsent(username, account) != null) throw new IllegalArgumentException("Usuario duplicado");
            }
            for (int i = 0, count = storedCount(data, "requests", null); i < count; i++) {
                String p = "request." + i + ".";
                String decidedAt = data.getProperty(p + "decidedAt");
                requests.add(new AttendanceRequest(UUID.fromString(data.getProperty(p + "id")), readEmployee(data, p),
                        new Place(data.getProperty(p + "place"), data.getProperty(p + "placeType"),
                                Integer.parseInt(data.getProperty(p + "x")), Integer.parseInt(data.getProperty(p + "y"))),
                        AttendanceRequest.Type.valueOf(data.getProperty(p + "type")), Instant.parse(data.getProperty(p + "time")),
                        AttendanceRequest.Status.valueOf(data.getProperty(p + "status")), data.getProperty(p + "decidedBy"),
                        decidedAt == null ? null : Instant.parse(decidedAt), data.getProperty(p + "reason")));
            }
            for (int q = 0, count = storedCount(data, "quadrants", "0"); q < count; q++) {
                String p = "quadrant." + q + ".";
                var dates = new ArrayList<java.time.LocalDate>();
                var times = new ArrayList<java.time.LocalTime>();
                var ends = new ArrayList<java.time.LocalTime>();
                for (int e = 0, countEntries = storedCount(data, p + "entries", null); e < countEntries; e++) {
                    dates.add(java.time.LocalDate.parse(data.getProperty(p + "date." + e)));
                    times.add(java.time.LocalTime.parse(data.getProperty(p + "time." + e)));
                    String end = data.getProperty(p + "end." + e);
                    if (end != null) ends.add(java.time.LocalTime.parse(end));
                }
                quadrants.add(new Quadrant(readEmployee(data, p),
                        new Place(data.getProperty(p + "place"), data.getProperty(p + "placeType"),
                                Integer.parseInt(data.getProperty(p + "x")), Integer.parseInt(data.getProperty(p + "y"))),
                        dates, times, ends, java.time.ZoneId.of(data.getProperty(p + "zone"))));
            }
            return new AttendanceSnapshot(accounts, requests, quadrants);
        } catch (RuntimeException exception) { throw new IOException("El registro guardado no es valido", exception); }
    }

    private static int storedCount(Properties data, String key, String fallback) {
        int count = Integer.parseInt(data.getProperty(key, fallback));
        if (count < 0) throw new IllegalArgumentException("Recuento negativo: " + key);
        return count;
    }

    private static void writeEmployee(Properties data, String prefix, Employe employee) {
        data.setProperty(prefix + "employeeId", Integer.toString(employee.id()));
        data.setProperty(prefix + "firstName", employee.name());
        data.setProperty(prefix + "lastName", employee.lastname());
    }

    private static Employe readEmployee(Properties data, String prefix) {
        return new Employe(data.getProperty(prefix + "firstName"), data.getProperty(prefix + "lastName"),
                Integer.parseInt(data.getProperty(prefix + "employeeId")));
    }
}
