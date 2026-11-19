package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class WeekDays {
    private WeekDays() { }

    static Set<DayOfWeek> valid(Set<DayOfWeek> days) {
        Require.present(days, "workDays");
        Require.that(!days.isEmpty(), "Indica al menos un dia laborable");
        Require.that(days.stream().noneMatch(java.util.Objects::isNull), "Dia laborable no valido");
        return Set.copyOf(EnumSet.copyOf(days));
    }

    static String describe(Set<DayOfWeek> days) {
        return EnumSet.copyOf(days).stream()
                .map(d -> d.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")))
                .collect(Collectors.joining(","));
    }
}
