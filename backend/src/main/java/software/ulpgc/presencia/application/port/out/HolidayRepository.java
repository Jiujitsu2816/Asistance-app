package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.calendar.Holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository {
    void save(Holiday holiday);

    Optional<Holiday> findByDate(LocalDate date);

    List<Holiday> findAll();
}
