package software.ulpgc.presencia.frontend.fichaje;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import software.ulpgc.presencia.frontend.api.ApiModel.ClockEvent;
import software.ulpgc.presencia.frontend.api.ApiModel.ClockRequest;
import software.ulpgc.presencia.frontend.api.ApiModel.Person;
import software.ulpgc.presencia.frontend.api.ApiModel.Presence;
import software.ulpgc.presencia.frontend.api.PresenceApi;
import software.ulpgc.presencia.frontend.ui.Background;
import software.ulpgc.presencia.frontend.ui.Badge;
import software.ulpgc.presencia.frontend.ui.MessageBar;
import software.ulpgc.presencia.frontend.ui.Texts;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Registro de asistencia: elegir persona y origen, ver su estado y fichar entrada o salida.
 * Solo usa {@link PresenceApi}; no conoce HTTP ni la base de datos.
 */
public final class FichajeView extends VBox {
    /** Origenes que puede elegir quien ficha (IMPORT queda para cargas de datos). */
    record Source(String code, String label) {
        @Override public String toString() { return label; }
    }

    static final List<Source> SOURCES = List.of(
            new Source("KIOSK", "Terminal de fichaje (KIOSK)"),
            new Source("WEB", "Web o móvil (WEB)"),
            new Source("ADMIN", "Corrección de administración (ADMIN)"));

    private final PresenceApi api;
    private final Clock clock;

    final ComboBox<Person> person = new ComboBox<>();
    final ComboBox<Source> source = new ComboBox<>(FXCollections.observableList(SOURCES));
    final HBox presenceBox = new HBox(18);
    final Button entry = new Button("Fichar ENTRADA");
    final Button exit = new Button("Fichar SALIDA");
    final DatePicker manualDate = new DatePicker();
    final TextField manualTime = new TextField();
    final TextField note = new TextField();
    final MessageBar message = new MessageBar();
    final TableView<ClockEvent> events = Ui.table("Todavía no hay fichajes hoy");

    public FichajeView(PresenceApi api, Clock clock) {
        this.api = api;
        this.clock = clock;
        person.setId("person");
        source.setId("source");
        entry.setId("entry");
        exit.setId("exit");
        message.setId("message");
        events.setId("events");
        presenceBox.setId("presence");
        manualDate.setId("manual-date");
        manualTime.setId("manual-time");
        note.setId("note");

        person.setPrefWidth(300);
        source.setPrefWidth(290);
        source.getSelectionModel().selectFirst();
        presenceBox.setAlignment(Pos.CENTER_LEFT);
        entry.getStyleClass().addAll("big", "entry");
        exit.getStyleClass().addAll("big", "exit");
        manualTime.setPromptText("HH:mm");
        manualTime.setPrefColumnCount(6);
        note.setPromptText("p. ej. Olvido de fichaje");
        note.setPrefColumnCount(28);

        TitledPane manual = new TitledPane("Corrección manual o prueba (indicar hora y nota)", new VBox(8,
                Ui.hint("Si se indica una hora, el fichaje se registra con esa hora (no puede ser futura ni anterior "
                        + "al último fichaje) y queda auditado con la hora real en que se introdujo. "
                        + "Úsalo con origen ADMIN para corregir un olvido."),
                Ui.row(Ui.field("Fecha", manualDate), Ui.field("Hora", manualTime), Ui.field("Nota", note))));
        manual.setExpanded(false);
        manual.setAnimated(false);

        events.getColumns().setAll(List.of(
                Ui.column("Hora", (ClockEvent e) -> Texts.hour(e.occurredAt())),
                Ui.column("Tipo", (ClockEvent e) -> Texts.clockType(e.type())),
                Ui.column("Origen", ClockEvent::source),
                Ui.column("Nota", (ClockEvent e) -> e.note() == null ? "" : e.note()),
                Ui.column("Registrado", (ClockEvent e) -> Texts.dateTime(e.recordedAt()))));
        events.setPrefHeight(220);

        HBox buttons = new HBox(14, entry, exit);
        getChildren().setAll(Ui.page(
                Ui.card("¿Quién ficha?",
                        Ui.row(Ui.field("Persona", person), Ui.field("Origen del fichaje", source)),
                        presenceBox, buttons, manual, message),
                Ui.card("Fichajes de hoy", events)));

        person.valueProperty().addListener((obs, old, selected) -> {
            message.clear();
            refresh();
        });
        entry.setOnAction(e -> clockIn("ENTRY"));
        exit.setOnAction(e -> clockIn("EXIT"));
        loadPeople();
    }

    private void loadPeople() {
        Background.run(api::listPeople, people -> {
            person.setItems(FXCollections.observableList(people));
            if (!people.isEmpty()) person.getSelectionModel().selectFirst();
        }, error -> message.error("No se pudo cargar la aplicación.", error));
    }

    private void refresh() {
        Person selected = person.getValue();
        if (selected == null) return;
        LocalDate today = LocalDate.now(clock);
        Background.run(() -> new Snapshot(api.presenceOf(selected.id()), api.listClockEvents(selected.id(), today, today)),
                snapshot -> {
                    showPresence(snapshot.presence());
                    events.setItems(FXCollections.observableList(snapshot.events()));
                }, error -> message.error("Error al consultar.", error));
    }

    private record Snapshot(Presence presence, List<ClockEvent> events) { }

    private void clockIn(String type) {
        Person selected = person.getValue();
        if (selected == null) return;
        OffsetDateTime timestamp;
        try {
            timestamp = manualTimestamp();
        } catch (DateTimeParseException e) {
            message.error("Hora no válida.", new IllegalArgumentException("Usa el formato HH:mm, p. ej. 08:30"));
            return;
        }
        String text = note.getText() == null || note.getText().isBlank() ? null : note.getText().strip();
        ClockRequest request = new ClockRequest(selected.id(), type, source.getValue().code(), timestamp, text);
        setBusy(true);
        Background.run(() -> api.clock(request), result -> {
            setBusy(false);
            message.ok(Texts.clockType(type) + " registrada a las " + Texts.hour(result.event().occurredAt()) + ".");
            manualDate.setValue(null);
            manualTime.clear();
            note.clear();
            showPresence(result.presence());
            refresh();
        }, error -> {
            setBusy(false);
            message.error("Fichaje rechazado.", error);
        });
    }

    /** Fecha y hora indicadas a mano, en la zona del equipo; null si no se indicaron. */
    private OffsetDateTime manualTimestamp() {
        String time = manualTime.getText() == null ? "" : manualTime.getText().strip();
        if (time.isEmpty() && manualDate.getValue() == null) return null;
        LocalDate date = manualDate.getValue() == null ? LocalDate.now(clock) : manualDate.getValue();
        LocalTime parsed = LocalTime.parse(time.isEmpty() ? "00:00" : time);
        return date.atTime(parsed).atZone(clock.getZone()).toOffsetDateTime();
    }

    private void setBusy(boolean busy) {
        entry.setDisable(busy);
        exit.setDisable(busy);
    }

    private void showPresence(Presence p) {
        Label status = Badge.of(p.status());
        status.getStyleClass().add("large");
        status.setId("presence-status");
        Label since = new Label(p.inside() ? "Dentro desde " + Texts.dateTime(p.since())
                : p.since() != null ? "Fuera desde " + Texts.dateTime(p.since()) : "Sin fichajes");
        Label period = new Label(p.currentPeriod() == null ? "Sin periodo de trabajo en curso"
                : "Periodo actual: " + Texts.period(p.currentPeriod(), p.currentPeriodStart(), p.currentPeriodEnd()));
        period.getStyleClass().add("sub");
        Label today = Badge.of(p.todayStatus());
        today.setId("today-status");
        Label todayCaption = new Label("Hoy:");
        todayCaption.getStyleClass().add("sub");
        presenceBox.getChildren().setAll(status, new VBox(2, since, period), new HBox(6, todayCaption, today));
        // Se resalta la accion esperada; la otra sigue disponible (sus errores se explican).
        entry.getStyleClass().remove("dimmed");
        exit.getStyleClass().remove("dimmed");
        (p.inside() ? entry : exit).getStyleClass().add("dimmed");
    }
}
