package software.ulpgc.presencia.frontend.horarios;

import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import software.ulpgc.presencia.frontend.api.TestBackend;
import software.ulpgc.presencia.frontend.ui.Screenshots;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Definicion de horarios desde la interfaz contra el backend real. */
@ExtendWith(ApplicationExtension.class)
class HorariosViewTest {
    private TestBackend backend;
    private HorariosView view;
    private Scene scene;

    @Start
    void start(Stage stage) throws Exception {
        backend = new TestBackend(Files.createTempDirectory("horarios-test"));
        view = new HorariosView(backend.api("app-horarios"), Clock.fixed(TestBackend.NOW, ZoneId.of("Atlantic/Canary")));
        scene = Ui.scene("Definición de horarios", "Horarios, turnos, guardias y calendario", backend.url().toString(),
                view, 1200, 860);
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void stop() {
        backend.close();
    }

    /** Pulsa un boton aunque haya quedado fuera de la zona visible de un panel con scroll. */
    private static void press(FxRobot robot, String id) {
        robot.interact(() -> ((javafx.scene.control.Button) robot.lookup(id).query()).fire());
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, condition::getAsBoolean);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void save(FxRobot robot, String name, String kind) {
        robot.interact(() -> {
            view.form.name.setText(name);
            view.form.kind.getSelectionModel().select(kind);
        });
        press(robot, "#save-schedule");
    }

    @Test
    void createsTheFourKindsOfSchedule(FxRobot robot) throws Exception {
        waitUntil(() -> view.schedules.getItems().size() == 5);
        Screenshots.save(scene.getRoot(), "horarios");

        save(robot, "Oficina de prueba", "CONTINUOUS");
        waitUntil(() -> view.schedules.getItems().size() == 6);
        assertTrue(view.scheduleMessage.getText().contains("Corrido lun,mar,mié,jue,vie 08:00-15:00"));

        save(robot, "Tienda de prueba", "SPLIT");
        waitUntil(() -> view.schedules.getItems().size() == 7);
        assertTrue(view.scheduleMessage.getText().contains("08:00-15:00 y 17:00-21:00"));

        save(robot, "UCI de prueba", "ROTATING_SHIFT");
        waitUntil(() -> view.schedules.getItems().size() == 8);
        assertTrue(view.scheduleMessage.getText().contains("Mañana, Tarde, Noche, Descanso, Descanso"));
        Screenshots.save(scene.getRoot(), "horarios-turnos");

        save(robot, "Guardia sin fechas", "ON_CALL");
        waitUntil(() -> "INVALID_DATA".equals(view.scheduleMessage.code()));
        robot.interact(() -> view.form.onCallDate.setValue(LocalDate.of(2026, 11, 20)));
        press(robot, "#add-date");
        press(robot, "#save-schedule");
        waitUntil(() -> view.schedules.getItems().size() == 9);
        assertTrue(view.scheduleMessage.getText().contains("Guardia física 24 h"));
    }

    @Test
    void validatesTheFormBeforeSending(FxRobot robot) throws Exception {
        waitUntil(() -> view.schedules.getItems().size() == 5);
        robot.interact(() -> {
            view.form.name.setText("Hora mal escrita");
            ((TextField) ((HBox) view.form.slots.getChildren().getFirst()).getChildren().get(1)).setText("8h");
        });
        press(robot, "#save-schedule");
        waitUntil(() -> view.scheduleMessage.getText().contains("HH:mm"));
        assertEquals(5, view.schedules.getItems().size());
    }

    @Test
    void assignmentsHolidaysAndPeople(FxRobot robot) throws Exception {
        waitUntil(() -> view.assignments.getItems().size() == 6 && view.people.getItems().size() == 8);
        robot.interact(() -> view.tabs.getSelectionModel().select(3));
        robot.interact(() -> {
            view.personId.setText("EMP-009");
            view.personName.setText("Persona Nueva");
            view.personTeam.setText("tienda");
        });
        press(robot, "#add-person");
        waitUntil(() -> view.people.getItems().size() == 9);
        press(robot, "#add-person");
        waitUntil(() -> "INVALID_DATA".equals(view.personMessage.code()));

        robot.interact(() -> view.tabs.getSelectionModel().select(1));
        robot.interact(() -> {
            view.assignTarget.getSelectionModel().select("TEAM");
            view.assignTeam.setText("tienda");
        });
        press(robot, "#assign");
        waitUntil(() -> view.assignments.getItems().size() == 7);
        press(robot, "#assign");
        waitUntil(() -> "ASSIGNMENT_OVERLAP".equals(view.assignmentMessage.code()));

        robot.interact(() -> view.tabs.getSelectionModel().select(2));
        robot.interact(() -> {
            view.holidayDate.setValue(LocalDate.of(2027, 5, 30));
            view.holidayDescription.setText("Día de Canarias");
        });
        press(robot, "#add-holiday");
        waitUntil(() -> view.holidays.getItems().size() == 5);
        press(robot, "#add-holiday");
        waitUntil(() -> "HOLIDAY_ALREADY_EXISTS".equals(view.holidayMessage.code()));
    }
}
