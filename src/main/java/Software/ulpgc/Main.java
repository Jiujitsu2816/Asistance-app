package Software.ulpgc;

import java.time.Clock;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path data = args.length == 0 ? Path.of("data", "attendance.properties") : Path.of(args[0]);
                AttendanceBroker broker = new AttendanceBroker(data, Clock.systemUTC());
                new AttendanceWindow(broker).setVisible(true);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(null, "No se pudo abrir el registro: " + exception.getMessage(),
                        "Error de inicio", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
