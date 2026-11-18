package software.ulpgc.presencia.frontend.consulta;

import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import software.ulpgc.presencia.frontend.api.ApiModel.ClockRequest;
import software.ulpgc.presencia.frontend.api.ApiModel.Incident;
import software.ulpgc.presencia.frontend.api.TestBackend;
import software.ulpgc.presencia.frontend.ui.Screenshots;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.nio.file.Files;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consulta de presencia contra el backend real con los datos de ejemplo. */
@ExtendWith(ApplicationExtension.class)
class ConsultaViewTest {
    private TestBackend backend;
    private ConsultaView view;
    private Scene scene;

    @Start
    void start(Stage stage) throws Exception {
        backend = new TestBackend(Files.createTempDirectory("consulta-test"));
        // Una entrada tardia de hoy para que la consulta muestre a alguien presente con retraso.
        backend.api("prueba").clock(new ClockRequest("EMP-001", "ENTRY", "KIOSK", null, null));
        view = new ConsultaView(backend.api("app-consulta"), Clock.fixed(TestBackend.NOW, ZoneId.of("Atlantic/Canary")));
        scene = Ui.scene("Consulta de presencia", "Presencia, informes e incidencias", backend.url().toString(),
                view, 1280, 820);
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

    private void selectTab(FxRobot robot, int index) throws Exception {
        robot.interact(() -> view.tabs.getSelectionModel().select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void currentPresenceOfEveryone() throws Exception {
        waitUntil(() -> view.presence.getItems().size() == 8);
        assertEquals("PRESENT", view.presence.getItems().get(0).status());
        assertEquals("CHECKOUT_PENDING", view.presence.getItems().get(3).status());
        Screenshots.save(scene.getRoot(), "consulta-presencia");
    }

    @Test
    void reportByPersonAndPeriod(FxRobot robot) throws Exception {
        waitUntil(() -> view.reportPerson.getItems().size() == 8);
        selectTab(robot, 1);
        robot.interact(() -> view.reportPerson.getSelectionModel().select(1));
        press(robot, "#report-query");
        waitUntil(() -> view.report.getItems().size() == 7);
        assertTrue(view.report.getItems().stream().anyMatch(d -> d.status().equals("LATE")));
        assertEquals(4, view.reportTotals.getChildren().size());
        Screenshots.save(scene.getRoot(), "consulta-informe");
    }

    @Test
    void justifyAnIncident(FxRobot robot) throws Exception {
        waitUntil(() -> view.incidentPerson.getItems().size() == 9);
        selectTab(robot, 2);
        robot.interact(() -> view.incidentType.getSelectionModel().select("LATE_ARRIVAL"));
        press(robot, "#incidents-search");
        waitUntil(() -> !view.incidents.getItems().isEmpty());
        assertTrue(view.incidents.getItems().stream().allMatch(i -> i.type().equals("LATE_ARRIVAL")));

        robot.interact(() -> view.incidentType.getSelectionModel().select(""));
        press(robot, "#incidents-search");
        waitUntil(() -> view.incidents.getItems().stream().anyMatch(i -> !i.type().equals("LATE_ARRIVAL")));
        WaitForAsyncUtils.waitForFxEvents();
        Incident first = view.incidents.getItems().getFirst();
        HBox form = robot.lookup(".table-row-cell").queryAll().stream()
                .flatMap(row -> robot.from(row).lookup(".text-field").queryAll().stream())
                .map(node -> (HBox) node.getParent()).findFirst().orElseThrow();
        robot.interact(() -> ((TextField) form.getChildren().get(0)).setText("Cita médica justificada"));
        robot.interact(() -> ((Button) form.getChildren().get(1)).fire());
        waitUntil(() -> "OK".equals(view.incidentMessage.code())
                && view.incidents.getItems().stream().anyMatch(i -> i.justified() && i.key().equals(first.key())));
        Screenshots.save(scene.getRoot(), "consulta-incidencias");

        selectTab(robot, 4);
        waitUntil(() -> !view.audit.getItems().isEmpty()
                && view.audit.getItems().getFirst().action().equals("INCIDENT_JUSTIFIED"));
        assertEquals("app-consulta", view.audit.getItems().getFirst().actor());
        Screenshots.save(scene.getRoot(), "consulta-auditoria");
    }

    @Test
    void clockEventsByPeriod(FxRobot robot) throws Exception {
        waitUntil(() -> view.eventPerson.getItems().size() == 9);
        selectTab(robot, 3);
        robot.interact(() -> view.eventFrom.setValue(view.eventTo.getValue().minusDays(6)));
        press(robot, "#events-search");
        waitUntil(() -> view.events.getItems().size() > 20);
        robot.interact(() -> view.eventPerson.getSelectionModel().select(1));
        press(robot, "#events-search");
        waitUntil(() -> view.events.getItems().stream().allMatch(e -> e.personId().equals("EMP-001")));
    }
}
