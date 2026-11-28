package software.ulpgc.presencia.domain;

import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockingPolicy;
import software.ulpgc.presencia.domain.error.ErrorCode;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;
import static software.ulpgc.presencia.support.Errors.assertError;

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
        assertError(ErrorCode.DUPLICATE_CLOCK_EVENT, () -> policy.check(
                Optional.of(entry("p", at(MONDAY, 8, 0))), ClockEventType.ENTRY, at(MONDAY, 8, 1), now));
    }

    @Test
    void exitWithoutEntryIsInconsistent() {
        assertError(ErrorCode.INCONSISTENT_CLOCK_EVENT, () -> policy.check(Optional.empty(), ClockEventType.EXIT, at(MONDAY, 8, 0), now));
        assertError(ErrorCode.INCONSISTENT_CLOCK_EVENT, () -> policy.check(
                Optional.of(exit("p", at(MONDAY, 9, 0))), ClockEventType.EXIT, at(MONDAY, 10, 0), now));
    }

    @Test
    void eventsMustBeChronologicalAndNotInTheFuture() {
        assertError(ErrorCode.INCONSISTENT_CLOCK_EVENT, () -> policy.check(
                Optional.of(exit("p", at(MONDAY, 9, 0))), ClockEventType.ENTRY, at(MONDAY, 8, 0), now));
        assertError(ErrorCode.INVALID_DATA, () -> policy.check(Optional.empty(), ClockEventType.ENTRY, at(MONDAY, 12, 5), now));
    }
}
