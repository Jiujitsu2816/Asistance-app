package software.ulpgc.presencia.frontend.consulta;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;
import software.ulpgc.presencia.frontend.api.ApiModel.AuditEntry;
import software.ulpgc.presencia.frontend.api.ApiModel.ClockEvent;
import software.ulpgc.presencia.frontend.api.ApiModel.Day;
import software.ulpgc.presencia.frontend.api.ApiModel.Incident;
import software.ulpgc.presencia.frontend.api.ApiModel.Person;
import software.ulpgc.presencia.frontend.api.ApiModel.Presence;
import software.ulpgc.presencia.frontend.api.ApiModel.Report;
import software.ulpgc.presencia.frontend.api.PresenceApi;
import software.ulpgc.presencia.frontend.ui.Background;
import software.ulpgc.presencia.frontend.ui.Badge;
import software.ulpgc.presencia.frontend.ui.MessageBar;
import software.ulpgc.presencia.frontend.ui.Texts;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consulta de presencia: presencia actual, informe por persona y periodo, incidencias (con
 * justificacion), registro de fichajes y auditoria. Solo usa {@link PresenceApi}.
 */
public final class ConsultaView extends VBox {
    /** Opcion "todas las personas" de los filtros. */
    static final Person ALL = new Person("", "Todas las personas", null);
    static final List<String> INCIDENT_TYPES =
            List.of("", "ABSENCE", "LATE_ARRIVAL", "EARLY_DEPARTURE", "MISSING_CHECKOUT", "UNSCHEDULED_WORK");

    private final PresenceApi api;
    private final Clock clock;
    private final Map<String, String> names = new HashMap<>();

    final TabPane tabs = new TabPane();
    final MessageBar message = new MessageBar();
    // Presencia actual
    final TableView<Presence> presence = Ui.table("Sin personas");
    // Informe
    final ComboBox<Person> reportPerson = new ComboBox<>();
    final DatePicker reportFrom = Ui.datePicker();
    final DatePicker reportTo = Ui.datePicker();
    final HBox reportTotals = new HBox(28);
    final TableView<Day> report = Ui.table("Elige una persona y un periodo");
    // Incidencias
    final ComboBox<Person> incidentPerson = new ComboBox<>();
    final DatePicker incidentFrom = Ui.datePicker();
    final DatePicker incidentTo = Ui.datePicker();
    final ComboBox<String> incidentType = new ComboBox<>(FXCollections.observableList(INCIDENT_TYPES));
    final TableView<Incident> incidents = Ui.table("Sin incidencias en el periodo");
    final MessageBar incidentMessage = new MessageBar();
    // Fichajes
    final ComboBox<Person> eventPerson = new ComboBox<>();
    final DatePicker eventFrom = Ui.datePicker();
    final DatePicker eventTo = Ui.datePicker();
    final TableView<ClockEvent> events = Ui.table("Sin fichajes en el periodo");
    // Auditoria
    final TableView<AuditEntry> audit = Ui.table("Sin cambios registrados");

    public ConsultaView(PresenceApi api, Clock clock) {
        this.api = api;
        this.clock = clock;
        tabs.setId("tabs");
        message.setId("message");
        presence.setId("presence");
        report.setId("report");
        incidents.setId("incidents");
        events.setId("events");
        audit.setId("audit");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(
                tab("Presencia actual", presenceTab()),
                tab("Informe por persona", reportTab()),
                tab("Incidencias", incidentsTab()),
                tab("Fichajes", eventsTab()),
                tab("Auditoría", auditTab()));
        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, old, index) -> {
            if (index.intValue() == 0) loadPresence();
            if (index.intValue() == 4) loadAudit();
        });
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox bottom = new VBox(message);
        bottom.setStyle("-fx-padding: 0 18 12 18;");
        getChildren().setAll(tabs, bottom);

        LocalDate today = LocalDate.now(clock);
        for (DatePicker from : List.of(reportFrom, incidentFrom)) from.setValue(today.minusDays(6));
        for (DatePicker to : List.of(reportTo, incidentTo, eventFrom, eventTo)) to.setValue(today);
        incidentType.setConverter(converter(code -> code.isEmpty() ? "Todos los tipos" : Texts.status(code)));
        incidentType.getSelectionModel().selectFirst();
        loadPeople();

        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (tabs.getSelectionModel().getSelectedIndex() == 0) loadPresence();
        }));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    private static Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setId("tab-" + title);
        return tab;
    }

    // ---- Presencia actual ----------------------------------------------------------------
    private Node presenceTab() {
        presence.getColumns().setAll(List.of(
                Ui.nodeColumn("Persona", (Presence p) -> person(p.personId(), p.displayName())),
                Ui.nodeColumn("Ahora", (Presence p) -> Badge.of(p.status())),
                Ui.column("Último fichaje", (Presence p) -> p.since() == null ? "—"
                        : (p.inside() ? "Dentro desde " : "Fuera desde ") + Texts.dateTime(p.since())),
                Ui.column("Periodo en curso", (Presence p) -> p.currentPeriod() == null ? "—"
                        : Texts.period(p.currentPeriod(), p.currentPeriodStart(), p.currentPeriodEnd())),
                Ui.nodeColumn("Estado del día", (Presence p) -> Badge.of(p.todayStatus()))));
        setWidths(presence, 220, 190, 230, 230, 170);
        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("secondary");
        refresh.setOnAction(e -> loadPresence());
        VBox.setVgrow(presence, Priority.ALWAYS);
        VBox card = Ui.card("Presencia actual",
                Ui.hint("Estado de cada persona en este momento según su horario asignado. Se actualiza cada 30 s."),
                refresh, presence);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadPresence() {
        Background.run(api::currentPresence, list -> presence.setItems(FXCollections.observableList(list)),
                error -> message.error("Error al consultar la presencia.", error));
    }

    // ---- Informe -------------------------------------------------------------------------
    private Node reportTab() {
        report.getColumns().setAll(List.of(
                Ui.nodeColumn("Fecha", (Day d) -> twoLines(Texts.date(d.date()),
                        d.holiday() == null ? null : "Festivo: " + d.holiday())),
                Ui.column("Horario", (Day d) -> d.scheduleName() == null ? "—" : d.scheduleName()),
                Ui.nodeColumn("Periodos (esperado → fichado)", ConsultaView::periods),
                Ui.numberColumn("Esperado", (Day d) -> Texts.minutes(d.expectedMinutes())),
                Ui.numberColumn("Trabajado", (Day d) -> Texts.minutes(d.workedMinutes())
                        + (d.openSession() ? " *" : "")),
                Ui.numberColumn("Desviación", (Day d) -> Texts.minutes(d.deviationMinutes())),
                Ui.nodeColumn("Estado", (Day d) -> Badge.of(d.status())),
                Ui.nodeColumn("Incidencias", ConsultaView::incidentSummary)));
        setWidths(report, 105, 160, 220, 110, 115, 115, 150, 300);
        Button query = new Button("Consultar");
        query.setId("report-query");
        query.setOnAction(e -> loadReport());
        reportTotals.getStyleClass().add("totals");
        VBox.setVgrow(report, Priority.ALWAYS);
        VBox card = Ui.card("Informe por persona y periodo",
                Ui.row(Ui.field("Persona", reportPerson), Ui.field("Desde", reportFrom), Ui.field("Hasta", reportTo), query),
                reportTotals, report, Ui.hint("* hay una entrada abierta: no suma horas hasta registrar la salida."));
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadReport() {
        Person selected = reportPerson.getValue();
        if (selected == null) return;
        LocalDate from = reportFrom.getValue();
        LocalDate to = reportTo.getValue();
        Background.run(() -> api.dailyReport(selected.id(), from, to), this::showReport,
                error -> message.error("No se pudo obtener el informe.", error));
    }

    private void showReport(Report r) {
        message.clear();
        reportTotals.getChildren().clear();
        total("Esperado", Texts.minutes(r.expectedMinutes()), reportTotals);
        total("Trabajado", Texts.minutes(r.workedMinutes()), reportTotals);
        Label deviation = total("Desviación (días cerrados)", Texts.minutes(r.deviationMinutes()), reportTotals);
        if (r.deviationMinutes() < 0) deviation.getStyleClass().add("negative");
        total("Incidencias", String.valueOf(r.incidents()), reportTotals);
        report.setItems(FXCollections.observableList(r.days()));
    }

    private static Label total(String caption, String value, HBox into) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("sub");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("total-value");
        into.getChildren().add(new VBox(2, captionLabel, valueLabel));
        return valueLabel;
    }

    private static Node periods(Day day) {
        if (day.periods().isEmpty()) return new Label("—");
        VBox box = new VBox(2);
        day.periods().forEach(p -> box.getChildren().add(new Label(
                Texts.period(p.label(), p.expectedStart(), p.expectedEnd()) + " → "
                        + (p.firstEntry() == null ? "sin fichaje"
                        : Texts.hour(p.firstEntry()) + "–" + (p.open() ? "…" : Texts.hour(p.lastExit())))
                        + (p.presenceRequired() ? "" : " (localizada)"))));
        return box;
    }

    private static Node incidentSummary(Day day) {
        VBox box = new VBox(4);
        for (Incident i : day.incidents()) {
            Text detail = new Text(i.description() + (i.justified() ? " · justificada" : ""));
            detail.getStyleClass().addAll("cell-text", "sub-text");
            detail.setWrappingWidth(270);
            box.getChildren().addAll(Badge.of(i.type()), detail);
        }
        return box;
    }

    // ---- Incidencias ---------------------------------------------------------------------
    private Node incidentsTab() {
        incidents.getColumns().setAll(List.of(
                Ui.column("Fecha", (Incident i) -> Texts.date(i.date())),
                Ui.nodeColumn("Persona", (Incident i) -> person(i.personId(), names.get(i.personId()))),
                Ui.nodeColumn("Tipo", (Incident i) -> Badge.of(i.type())),
                Ui.column("Periodo", Incident::periodLabel),
                Ui.column("Detalle", Incident::description),
                Ui.nodeColumn("Justificación", this::justification)));
        setWidths(incidents, 100, 190, 160, 140, 260, 310);
        Button search = new Button("Buscar");
        search.setId("incidents-search");
        search.setOnAction(e -> loadIncidents());
        incidentMessage.setId("incident-message");
        VBox.setVgrow(incidents, Priority.ALWAYS);
        VBox card = Ui.card("Incidencias",
                Ui.hint("Las incidencias se calculan a partir de los fichajes y el horario. Se pueden justificar "
                        + "(cita médica, formación...); la justificación queda auditada y no se puede repetir."),
                Ui.row(Ui.field("Persona", incidentPerson), Ui.field("Desde", incidentFrom),
                        Ui.field("Hasta", incidentTo), Ui.field("Tipo", incidentType), search),
                incidentMessage, incidents);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadIncidents() {
        String personId = incidentPerson.getValue() == null ? null : blankToNull(incidentPerson.getValue().id());
        String type = blankToNull(incidentType.getValue());
        LocalDate from = incidentFrom.getValue();
        LocalDate to = incidentTo.getValue();
        Background.run(() -> api.listIncidents(personId, from, to, type),
                list -> incidents.setItems(FXCollections.observableList(list)),
                error -> incidentMessage.error("No se pudieron consultar las incidencias.", error));
    }

    private Node justification(Incident incident) {
        if (incident.justified()) {
            Label detail = new Label(incident.justification().reason() + " · " + incident.justification().justifiedBy());
            detail.getStyleClass().add("sub");
            return new VBox(2, Badge.of("JUSTIFIED"), detail);
        }
        TextField reason = new TextField();
        reason.setPromptText("Motivo");
        reason.setPrefColumnCount(14);
        Button justify = new Button("Justificar");
        justify.getStyleClass().add("small");
        justify.setOnAction(e -> Background.run(() -> api.justifyIncident(incident.key(), reason.getText()), done -> {
            incidentMessage.ok("Incidencia justificada.");
            loadIncidents();
        }, error -> incidentMessage.error("No se pudo justificar.", error)));
        HBox box = new HBox(6, reason, justify);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ---- Fichajes ------------------------------------------------------------------------
    private Node eventsTab() {
        events.getColumns().setAll(List.of(
                Ui.column("Fecha y hora", (ClockEvent e) -> Texts.dateTime(e.occurredAt())),
                Ui.column("Persona", (ClockEvent e) -> e.personId() + " · " + names.getOrDefault(e.personId(), "")),
                Ui.column("Tipo", (ClockEvent e) -> Texts.clockType(e.type())),
                Ui.column("Origen", ClockEvent::source),
                Ui.column("Nota", (ClockEvent e) -> e.note() == null ? "" : e.note()),
                Ui.column("Registrado el", (ClockEvent e) -> Texts.dateTime(e.recordedAt()))));
        Button search = new Button("Buscar");
        search.setId("events-search");
        search.setOnAction(e -> loadEvents());
        VBox.setVgrow(events, Priority.ALWAYS);
        VBox card = Ui.card("Registro de fichajes",
                Ui.row(Ui.field("Persona", eventPerson), Ui.field("Desde", eventFrom), Ui.field("Hasta", eventTo), search),
                events);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadEvents() {
        String personId = eventPerson.getValue() == null ? null : blankToNull(eventPerson.getValue().id());
        LocalDate from = eventFrom.getValue();
        LocalDate to = eventTo.getValue();
        Background.run(() -> api.listClockEvents(personId, from, to),
                list -> events.setItems(FXCollections.observableList(list)),
                error -> message.error("No se pudieron consultar los fichajes.", error));
    }

    // ---- Auditoria -----------------------------------------------------------------------
    private Node auditTab() {
        audit.getColumns().setAll(List.of(
                Ui.numberColumn("#", (AuditEntry a) -> String.valueOf(a.sequence())),
                Ui.column("Fecha", (AuditEntry a) -> Texts.dateTime(a.at())),
                Ui.column("Quién", AuditEntry::actor),
                Ui.column("Acción", AuditEntry::action),
                Ui.column("Entidad", (AuditEntry a) -> a.entityType() + " " + a.entityId()),
                Ui.column("Detalle", (AuditEntry a) -> a.detail() == null ? "" : a.detail())));
        setWidths(audit, 50, 140, 130, 170, 260, 420);
        Button refresh = new Button("Actualizar");
        refresh.getStyleClass().add("secondary");
        refresh.setOnAction(e -> loadAudit());
        VBox.setVgrow(audit, Priority.ALWAYS);
        VBox card = Ui.card("Auditoría de modificaciones",
                Ui.hint("Últimos 50 cambios: altas, horarios, asignaciones, festivos, fichajes y justificaciones."),
                refresh, audit);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadAudit() {
        Background.run(() -> api.audit(50), list -> audit.setItems(FXCollections.observableList(list)),
                error -> message.error("No se pudo consultar la auditoría.", error));
    }

    // ---- Comun ---------------------------------------------------------------------------
    private void loadPeople() {
        Background.runAtStartup(api::listPeople, people -> {
            names.clear();
            names.putAll(people.stream().collect(Collectors.toMap(Person::id, Person::displayName)));
            reportPerson.setItems(FXCollections.observableList(people));
            reportPerson.getSelectionModel().selectFirst();
            List<Person> withAll = new ArrayList<>(people);
            withAll.addFirst(ALL);
            for (ComboBox<Person> filter : List.of(incidentPerson, eventPerson)) {
                filter.setItems(FXCollections.observableList(withAll));
                filter.setConverter(converter(p -> p == ALL ? ALL.displayName() : p.toString()));
                filter.getSelectionModel().selectFirst();
            }
            loadPresence();
        }, error -> message.error("No se pudo cargar la aplicación.", error));
    }

    private static Node person(String id, String name) {
        Label idLabel = new Label(id);
        idLabel.setStyle("-fx-font-weight: bold;");
        return twoLines(idLabel, name);
    }

    private static Node twoLines(String main, String secondary) {
        return twoLines(new Label(main), secondary);
    }

    private static Node twoLines(Label main, String secondary) {
        if (secondary == null) return main;
        Label sub = new Label(secondary);
        sub.getStyleClass().add("sub");
        return new VBox(1, main, sub);
    }

    private static void setWidths(TableView<?> table, double... widths) {
        for (int i = 0; i < widths.length; i++) table.getColumns().get(i).setPrefWidth(widths[i]);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : text.apply(value); }

            @Override public T fromString(String value) { return null; }
        };
    }
}
