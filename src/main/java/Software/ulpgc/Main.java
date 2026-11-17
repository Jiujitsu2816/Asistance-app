package Software.ulpgc;

import java.time.Clock;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "--recover-users".equals(args[0])) {
            Path data = args.length > 1 ? Path.of(args[1]) : Path.of("data", "attendance.properties");
            try (AttendanceBroker broker = createBroker(data)) {
                broker.recoverUsers().forEach((user, password) -> System.out.println(user + ": " + password));
            } catch (Exception exception) {
                throw new IllegalStateException("No se pudo recuperar el acceso", exception);
            }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                Path data = args.length == 0 ? Path.of("data", "attendance.properties") : Path.of(args[0]);
                AttendanceBroker broker = createBroker(data);
                new AttendanceWindow(broker).setVisible(true);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(null, "No se pudo abrir el registro: " + exception.getMessage(),
                        "Error de inicio", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Composition root: selects the application's infrastructure implementations. */
    private static AttendanceBroker createBroker(Path data) throws java.io.IOException {
        Clock clock = Clock.systemUTC();
        return new AttendanceBroker(new PropertiesAttendanceStore(data, clock), clock,
                new Pbkdf2PasswordHasher(), new OnTimeEntryPolicy());
    }
}
