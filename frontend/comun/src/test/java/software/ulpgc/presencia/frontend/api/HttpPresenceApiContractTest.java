package software.ulpgc.presencia.frontend.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.presencia.frontend.api.ApiModel.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El cliente de los frontends contra el backend real: comprueba que ambos lados hablan el mismo contrato. */
class HttpPresenceApiContractTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @TempDir
    Path dir;
    private TestBackend backend;
    private PresenceApi api;

    @BeforeEach
    void start() throws Exception {
        backend = new TestBackend(dir);
        api = backend.api("prueba-contrato");
    }

    @AfterEach
    void stop() {
        backend.close();
    }

    @Test
    void readsTheDemoData() {
        assertEquals(8, api.listPeople().size());
        List<Schedule> schedules = api.listSchedules();
        assertEquals(5, schedules.size());
        Schedule office = schedules.stream().filter(s -> s.kind().equals("CONTINUOUS")).findFirst().orElseThrow();
        assertEquals(LocalTime.of(8, 0), office.slots().getFirst().start());
        assertTrue(schedules.stream().anyMatch(s -> s.kind().equals("ROTATING_SHIFT") && s.cycle().size() == 7));
        assertEquals(6, api.listAssignments().size());
        assertEquals(4, api.listHolidays().size());
        assertEquals(8, api.currentPresence().size());
        assertEquals("CHECKOUT_PENDING", api.presenceOf("EMP-004").status());
    }

    @Test
    void clockQueryAndJustify() {
        ClockResult result = api.clock(new ClockRequest("EMP-001", "ENTRY", "KIOSK", null, "prueba"));
        assertEquals("PRESENT", result.presence().status());
        assertEquals("LATE", result.presence().todayStatus());
        assertEquals("11:00", result.event().occurredAt().toLocalTime().toString());

        Report report = api.dailyReport("EMP-002", TODAY.minusDays(6), TODAY);
        assertEquals(7, report.days().size());
        assertTrue(report.days().stream().anyMatch(d -> d.status().equals("LATE")));

        Incident late = api.listIncidents(null, TODAY.minusDays(6), TODAY, "LATE_ARRIVAL").stream()
                .filter(i -> i.personId().equals("EMP-002")).findFirst().orElseThrow();
        Justification justification = api.justifyIncident(late.key(), "Atasco");
        assertEquals("prueba-contrato", justification.justifiedBy());
        assertEquals("INCIDENT_JUSTIFIED", api.audit(1).getFirst().action());
        assertEquals(1, api.listClockEvents("EMP-001", TODAY, TODAY).size());
    }

    @Test
    void createsEveryKindOfData() {
        api.createPerson(new Person("EMP-100", "Persona Nueva", "tienda"));
        Schedule split = api.createSchedule(new Schedule(null, "Tienda", "SPLIT", 5,
                List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
                List.of(new Slot(LocalTime.of(10, 0), LocalTime.of(14, 0)), new Slot(LocalTime.of(17, 0), LocalTime.of(21, 0))),
                null, null, null, null, null, null, null));
        assertTrue(split.summary().startsWith("Partido"));
        Schedule shifts = api.createSchedule(new Schedule(null, "UCI", "ROTATING_SHIFT", 10, null, null, TODAY,
                List.of(new CycleEntry(null, "Dia", LocalTime.of(8, 0), 720), CycleEntry.restDay()),
                null, null, null, null, null));
        assertEquals(2, shifts.cycle().size());
        api.createSchedule(new Schedule(null, "Guardia", "ON_CALL", 15, null, null, null, null,
                LocalTime.of(8, 0), 1440, "LOCALIZED", List.of(TODAY), null));
        assertEquals("TEAM", api.assignSchedule(new AssignmentRequest(split.id(), null, "tienda", TODAY, null)).targetType());
        api.createHoliday(new Holiday(LocalDate.of(2027, 5, 30), "Dia de Canarias"));
        assertEquals(5, api.listHolidays().size());
    }

    @Test
    void errorsKeepTheContractCode() {
        ApiException duplicated = assertThrows(ApiException.class,
                () -> api.createPerson(new Person("EMP-001", "Repetida", null)));
        assertEquals("PERSON_ALREADY_EXISTS", duplicated.code());
        assertEquals(409, duplicated.status());
        assertEquals("SCHEDULE_NOT_ASSIGNED",
                assertThrows(ApiException.class, () -> api.clock(new ClockRequest("EMP-007", "ENTRY", "KIOSK", null, null))).code());
        assertEquals("PERSON_NOT_FOUND", assertThrows(ApiException.class, () -> api.presenceOf("NO EXISTE")).code());
    }

    @Test
    void unreachableBackendIsANetworkError() {
        PresenceApi offline = new HttpPresenceApi(URI.create("http://localhost:1/api"), "x");
        assertEquals(ApiException.NETWORK_ERROR, assertThrows(ApiException.class, offline::listPeople).code());
    }
}
