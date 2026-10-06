package Software.ulpgc;

import java.time.Clock;

public class Main {
    public static void main(String[] args) {
        AttendanceService service = new AttendanceService(Clock.systemUTC());
        Employe employee = new Employe("Ana", "Garcia", 1);
        Place office = new Place("Head office", "Office", 0, 0);

        service.registerEmployee(employee);
        System.out.println("Checked in: " + service.checkIn(employee.id(), office));
        System.out.println("Checked out: " + service.checkOut(employee.id()));
        System.out.println("History: " + service.history(employee.id()));
    }
}
