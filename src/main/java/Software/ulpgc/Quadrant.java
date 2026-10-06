package Software.ulpgc;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;

public record Quadrant(ArrayList<Date> labor, ArrayList<Clock> timetable) { }
