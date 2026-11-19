package software.ulpgc.presencia.domain.calendar;

import software.ulpgc.presencia.domain.Require;

import java.time.LocalDate;

/** Festivo del calendario laboral. */
public record Holiday(LocalDate date, String description) {
    public Holiday {
        Require.present(date, "date");
        description = Require.text(description, "description", 80);
    }
}
