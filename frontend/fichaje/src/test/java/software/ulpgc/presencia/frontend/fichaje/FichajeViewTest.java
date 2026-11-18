package software.ulpgc.presencia.frontend.fichaje;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import software.ulpgc.presencia.frontend.api.TestBackend;
import software.ulpgc.presencia.frontend.ui.Screenshots;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba de interfaz de extremo a extremo: app de fichaje + backend real con datos de ejemplo. */
@ExtendWith(ApplicationExtension.class)
class FichajeViewTest {
    private TestBackend backend;
    private FichajeView view;
    private Scene scene;

    @Start
    void start(Stage stage) throws Exception {
        Path dir = Files.createTempDirectory("fichaje-test");
        backend = new TestBackend(dir);
        view = new FichajeView(backend.api("app-fichaje"), Clock.fixed(TestBackend.NOW, ZoneId.of("Atlantic/Canary")));
        scene = Ui.scene("Registro de asistencia", "Fichar entrada y salida", backend.url().toString(), view, 1000, 760);
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void stop() {
        backend.close();
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, condition::getAsBoolean);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String presenceStatus(FxRobot robot) {
        Node badge = robot.lookup("#presence-status").tryQuery().orElse(null);
        return badge == null ? null : (String) badge.getUserData();
    }

    private void choose(FxRobot robot, String personId) throws Exception {
        robot.interact(() -> view.person.getItems().stream().filter(p -> p.id().equals(personId)).findFirst()
                .ifPresent(view.person::setValue));
        waitUntil(() -> view.person.getValue() != null && view.person.getValue().id().equals(personId)
                && !view.presenceBox.getChildren().isEmpty());
    }

    @Test
    void clockInShowsPresenceAndRejectsDuplicates(FxRobot robot) throws Exception {
        waitUntil(() -> view.person.getItems().size() == 8);
        choose(robot, "EMP-001");
        robot.clickOn("#entry");
        waitUntil(() -> "OK".equals(view.message.code()) && view.events.getItems().size() == 1);
        assertEquals("PRESENT", presenceStatus(robot));
        assertEquals("LATE", robot.lookup("#today-status").query().getUserData());
        assertTrue(view.message.getText().contains("11:00"));
        Screenshots.save(scene.getRoot(), "fichaje");

        robot.clickOn("#entry");
        waitUntil(() -> "DUPLICATE_CLOCK_EVENT".equals(view.message.code()));
        Screenshots.save(scene.getRoot(), "fichaje-error");
        robot.clickOn("#exit");
        waitUntil(() -> "OK".equals(view.message.code()) && view.events.getItems().size() == 2);
    }

    @Test
    void showsTheErrorsOfTheContract(FxRobot robot) throws Exception {
        waitUntil(() -> view.person.getItems().size() == 8);
        choose(robot, "EMP-007");
        robot.clickOn("#entry");
        waitUntil(() -> "SCHEDULE_NOT_ASSIGNED".equals(view.message.code()));
        choose(robot, "EMP-008");
        robot.clickOn("#entry");
        waitUntil(() -> "DATE_OUT_OF_PERIOD".equals(view.message.code()));
        choose(robot, "EMP-001");
        robot.clickOn("#exit");
        waitUntil(() -> "INCONSISTENT_CLOCK_EVENT".equals(view.message.code()));
    }

    @Test
    void manualCorrectionOfAForgottenCheckout(FxRobot robot) throws Exception {
        waitUntil(() -> view.person.getItems().size() == 8);
        choose(robot, "EMP-004");
        assertEquals("CHECKOUT_PENDING", presenceStatus(robot));
        robot.interact(() -> {
            view.source.getSelectionModel().select(2);
            view.manualDate.setValue(java.time.LocalDate.of(2026, 11, 16));
            view.manualTime.setText("15:00");
            view.note.setText("Olvido de fichaje");
        });
        robot.clickOn("#exit");
        waitUntil(() -> "OK".equals(view.message.code()));
        assertEquals("ABSENT", presenceStatus(robot), "hoy no ha venido a su turno de mañana");
        robot.interact(() -> view.manualTime.setText("8.30"));
        robot.clickOn("#entry");
        waitUntil(() -> view.message.getText().startsWith("Hora no válida"));
    }
}
