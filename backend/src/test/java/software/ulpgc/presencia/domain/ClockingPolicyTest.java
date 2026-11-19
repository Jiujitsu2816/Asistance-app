package software.ulpgc.presencia.domain;

import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockingPolicy;
import software.ulpgc.presencia.domain.error.DuplicateClockEventException;
import software.ulpgc.presencia.domain.error.InconsistentClockEventException;
import software.ulpgc.presencia.domain.error.InvalidDataException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;

class ClockingPolicyTest {
    private final ClockingPolicy policy = new ClockingPolicy(ZONE);
    private final Instant now = at(MONDAY, 12, 0);

    @Test
    void entryThenExitIsValid() {
        assertDoesNotThrow(() -> policy.check(Optional.empty(), ClockEventType.ENTRY, at(MONDAY, 8, 0), now));
        assertDoesNotThrow(() -> policy.check(Optional.of(entry("p", at(MONDAY, 8, 0))), ClockEventType.EXIT,
                at(MONDAY, 11, 0), now));
    }

    @Test
    void secondEntryWithoutExitIsDuplicate() {
        assertThrows(DuplicateClockEventException.class, () -> policy.check(
                Optional.of(entry("p", at(MONDAY, 8, 0))), ClockEventType.ENTRY, at(MONDAY, 8, 1), now));
    }

    @Test
    void exitWithoutEntryIsInconsistent() {
        assertThrows(InconsistentClockEventException.class,
                () -> policy.check(Optional.empty(), ClockEventType.EXIT, at(MONDAY, 8, 0), now));
        assertThrows(InconsistentClockEventException.class, () -> policy.check(
                Optional.of(exit("p", at(MONDAY, 9, 0))), ClockEventType.EXIT, at(MONDAY, 10, 0), now));
    }

    @Test
    void eventsMustBeChronologicalAndNotInTheFuture() {
        assertThrows(InconsistentClockEventException.class, () -> policy.check(
                Optional.of(exit("p", at(MONDAY, 9, 0))), ClockEventType.ENTRY, at(MONDAY, 8, 0), now));
        assertThrows(InvalidDataException.class,
                () -> policy.check(Optional.empty(), ClockEventType.ENTRY, at(MONDAY, 12, 5), now));
    }
}
