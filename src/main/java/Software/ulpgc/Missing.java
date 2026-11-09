package Software.ulpgc;

import java.util.Date;
import java.util.Objects;

/** An absence; date is the scheduled entry time, not the detection time. */
public record Missing(Employe employe, Place place, Date date) {
    public Missing {
        Objects.requireNonNull(employe, "Employee is required");
        Objects.requireNonNull(place, "Place is required");
        date = new Date(Objects.requireNonNull(date, "Date is required").getTime());
    }

    @Override
    public Date date() {
        return new Date(date.getTime());
    }
}
