package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.calendar.Holiday;

import java.time.LocalDate;

public record HolidayDto(LocalDate date, String description) {
    public static HolidayDto from(Holiday holiday) {
        return new HolidayDto(holiday.date(), holiday.description());
    }
}
