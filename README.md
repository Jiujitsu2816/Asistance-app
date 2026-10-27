# Company attendance — school project

Start with a small use case: register an employee, check in at a workplace,
check out, and view attendance history. The current Java model implements this
flow in memory. Data is lost when the application exits.

## Run

Use JDK 26, as configured in `pom.xml`. In IntelliJ IDEA, select that project SDK
and run `Software.ulpgc.Main`. The example checks an employee in and out immediately;
it demonstrates the flow, not a full working day.

## Tests

Run `mvn test` with JDK 26, or run the JUnit tests in `src/test/java` from IntelliJ.
The tests cover employee registration, check-in/check-out, worked duration,
history, scheduled absences, working schedules, and time zones. Controlled clocks
allow time-dependent rules to be checked without waiting for real deadlines.

## Design

- `Employe`: employee identity and name (existing class name retained).
- `Place`: workplace name, type, and local map coordinates.
- `Attendance`: one session with check-in and optional check-out timestamps.
- `AttendanceService`: employee registration, attendance rules, and history.
- `Main`: runnable demonstration.

Employee IDs must be unique. An employee can have only one open session and must
check in before checking out. Completed sessions provide a worked duration.
Times are stored as UTC instants; a future UI should display them in the company's
chosen time zone. The clock is injectable so tests can use predictable times.

## Suggested next steps

1. Add a console menu for registration, check-in, check-out, and history. Catch
   validation errors and display readable messages.
3. Introduce a repository interface and SQLite storage so records survive restarts.
4. Add daily/monthly reports, filtering sessions by dates in the company's time zone.
5. Add a GUI only after the core flow and storage work.

Confirm the assignment's requirements before adding login, shifts, absence tracking,
or GPS. No recorded session alone does not establish an absence: that requires a
work schedule. The current service is intended for a single-user school prototype;
multi-user deployment would need transactional storage and access control.
