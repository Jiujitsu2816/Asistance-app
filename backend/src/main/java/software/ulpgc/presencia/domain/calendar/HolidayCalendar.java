package software.ulpgc.presencia.domain.calendar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HolidayCalendar {
    private final Map<LocalDate, Holiday> holidays;

    public HolidayCalendar(Collection<Holiday> holidays) {
        this.holidays = holidays.stream().collect(Collectors.toUnmodifiableMap(Holiday::date, Function.identity()));
    }

    public static HolidayCalendar empty() {
        return new HolidayCalendar(java.util.List.of());
    }

    public Optional<Holiday> on(LocalDate date) {
        return Optional.ofNullable(holidays.get(date));
    }
}
