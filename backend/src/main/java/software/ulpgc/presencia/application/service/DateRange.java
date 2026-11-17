package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.domain.Require;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class DateRange {
    private DateRange() { }

    static void validate(LocalDate from, LocalDate to) {
        Require.present(from, "from");
        Require.present(to, "to");
        Require.that(!to.isBefore(from), "La fecha 'to' (" + to + ") es anterior a 'from' (" + from + ")");
        Require.that(ChronoUnit.DAYS.between(from, to) < AttendanceQueryUseCase.MAX_RANGE_DAYS,
                "El periodo consultado no puede superar " + AttendanceQueryUseCase.MAX_RANGE_DAYS + " dias");
    }
}
