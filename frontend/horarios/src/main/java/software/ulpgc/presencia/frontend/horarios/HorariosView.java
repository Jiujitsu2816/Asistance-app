package software.ulpgc.presencia.frontend.horarios;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import software.ulpgc.presencia.frontend.api.ApiModel.Assignment;
import software.ulpgc.presencia.frontend.api.ApiModel.AssignmentRequest;
import software.ulpgc.presencia.frontend.api.ApiModel.Holiday;
import software.ulpgc.presencia.frontend.api.ApiModel.Person;
import software.ulpgc.presencia.frontend.api.ApiModel.Schedule;
import software.ulpgc.presencia.frontend.api.PresenceApi;
import software.ulpgc.presencia.frontend.ui.Background;
import software.ulpgc.presencia.frontend.ui.MessageBar;
import software.ulpgc.presencia.frontend.ui.Texts;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Definicion de horarios: horarios corridos, partidos, turnos y guardias; su asignacion a
 * personas o equipos; el calendario de festivos y el alta de personas. Solo usa {@link PresenceApi}.
 */
public final class HorariosView extends VBox {
    private final PresenceApi api;

    final TabPane tabs = new TabPane();
    // Horarios
    final TableView<Schedule> schedules = Ui.table("Sin horarios");
    final ScheduleForm form;
    final MessageBar scheduleMessage = new MessageBar();
    // Asignaciones
    final ComboBox<Schedule> assignSchedule = new ComboBox<>();
    final ComboBox<String> assignTarget = new ComboBox<>(FXCollections.observableList(List.of("PERSON", "TEAM")));
    final ComboBox<Person> assignPerson = new ComboBox<>();
    final TextField assignTeam = new TextField();
    final DatePicker assignFrom = Ui.datePicker();
    final DatePicker assignTo = Ui.datePicker();
    final TableView<Assignment> assignments = Ui.table("Sin asignaciones");
    final MessageBar assignmentMessage = new MessageBar();
    // Festivos
    final DatePicker holidayDate = Ui.datePicker();
    final TextField holidayDescription = new TextField();
    final TableView<Holiday> holidays = Ui.table("Sin festivos");
    final MessageBar holidayMessage = new MessageBar();
    // Personas
    final TextField personId = new TextField();
    final TextField personName = new TextField();
    final TextField personTeam = new TextField();
    final TableView<Person> people = Ui.table("Sin personas");
    final MessageBar personMessage = new MessageBar();

    public HorariosView(PresenceApi api, Clock clock) {
        this.api = api;
        LocalDate today = LocalDate.now(clock);
        form = new ScheduleForm(today);
        tabs.setId("tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(
                new Tab("Horarios", schedulesTab()),
                new Tab("Asignaciones", assignmentsTab(today)),
                new Tab("Festivos", holidaysTab()),
                new Tab("Personas", peopleTab()));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().setAll(tabs);
        loadSchedules();
        loadPeople();
        loadHolidays();
    }

    private static Node scrollable(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // ---- Horarios ------------------------------------------------------------------------
    private Node schedulesTab() {
        schedules.getColumns().setAll(List.of(
                Ui.column("Nombre", Schedule::name),
                Ui.column("Tipo", (Schedule s) -> Texts.kind(s.kind())),
                Ui.column("Definición", Schedule::summary),
                Ui.numberColumn("Tolerancia", (Schedule s) -> s.toleranceMinutes() + " min")));
        schedules.getColumns().get(0).setPrefWidth(280);
        schedules.getColumns().get(1).setPrefWidth(150);
        schedules.getColumns().get(2).setPrefWidth(520);
        schedules.setPrefHeight(230);
        Button save = new Button("Guardar horario");
        save.setId("save-schedule");
        save.setOnAction(e -> saveSchedule());
        scheduleMessage.setId("schedule-message");
        return scrollable(Ui.page(
                Ui.card("Horarios definidos", schedules),
                Ui.card("Nuevo horario", Ui.hint("Corrido: un tramo. Partido: dos o más tramos separados por una pausa. "
                        + "Turnos: ciclo de N días que se repite (un turno puede cruzar la medianoche). Guardias: "
                        + "fechas concretas, de presencia física o localizadas."), form, save, scheduleMessage)));
    }

    private void saveSchedule() {
        Schedule body;
        try {
            body = form.build();
        } catch (RuntimeException e) {
            scheduleMessage.error("Revisa el formulario.", ScheduleForm.isTimeError(e)
                    ? new IllegalArgumentException("Las horas deben tener el formato HH:mm, p. ej. 08:00") : e);
            return;
        }
        Background.run(() -> api.createSchedule(body), created -> {
            scheduleMessage.ok("Horario guardado: " + created.name() + ". " + created.summary());
            loadSchedules();
        }, error -> scheduleMessage.error("No se pudo guardar el horario.", error));
    }

    void loadSchedules() {
        Background.runAtStartup(api::listSchedules, list -> {
            schedules.setItems(FXCollections.observableList(list));
            assignSchedule.setItems(FXCollections.observableList(list));
            if (assignSchedule.getValue() == null) assignSchedule.getSelectionModel().selectFirst();
            loadAssignments();
        }, error -> scheduleMessage.error("No se pudieron cargar los horarios.", error));
    }

    // ---- Asignaciones --------------------------------------------------------------------
    private Node assignmentsTab(LocalDate today) {
        assignments.getColumns().setAll(List.of(
                Ui.column("Horario", (Assignment a) -> scheduleName(a.scheduleId())),
                Ui.column("Destino", (Assignment a) -> ("TEAM".equals(a.targetType()) ? "Equipo " : "Persona ") + a.targetId()),
                Ui.column("Desde", (Assignment a) -> Texts.date(a.validFrom())),
                Ui.column("Hasta", (Assignment a) -> a.validTo() == null ? "sin fin" : Texts.date(a.validTo()))));
        assignTarget.setConverter(ScheduleForm.converter(t -> t.equals("PERSON") ? "Persona" : "Equipo"));
        assignTarget.getSelectionModel().selectFirst();
        assignTeam.setPromptText("p. ej. administracion");
        assignFrom.setValue(today);
        assignSchedule.setPrefWidth(300);
        assignPerson.setPrefWidth(250);
        VBox personField = Ui.field("Persona", assignPerson);
        VBox teamField = Ui.field("Equipo", assignTeam);
        teamField.setVisible(false);
        teamField.setManaged(false);
        assignTarget.valueProperty().addListener((obs, old, value) -> {
            boolean person = "PERSON".equals(value);
            personField.setVisible(person);
            personField.setManaged(person);
            teamField.setVisible(!person);
            teamField.setManaged(!person);
        });
        Button assign = new Button("Asignar");
        assign.setId("assign");
        assign.setOnAction(e -> assign());
        assignmentMessage.setId("assignment-message");
        VBox.setVgrow(assignments, Priority.ALWAYS);
        VBox card = Ui.card("Asignación de horarios",
                Ui.hint("Un horario se asigna a una persona o a un equipo durante un periodo. La asignación individual "
                        + "tiene prioridad sobre la del equipo. Los periodos de un mismo destino no pueden solaparse."),
                Ui.row(Ui.field("Horario", assignSchedule), Ui.field("Asignar a", assignTarget), personField, teamField),
                Ui.row(Ui.field("Desde", assignFrom), Ui.field("Hasta (opcional)", assignTo), assign),
                assignmentMessage, assignments);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    private void assign() {
        Schedule schedule = assignSchedule.getValue();
        boolean toTeam = "TEAM".equals(assignTarget.getValue());
        AssignmentRequest request = new AssignmentRequest(schedule == null ? null : schedule.id(),
                toTeam || assignPerson.getValue() == null ? null : assignPerson.getValue().id(),
                toTeam ? assignTeam.getText() : null, assignFrom.getValue(), assignTo.getValue());
        Background.run(() -> api.assignSchedule(request), done -> {
            assignmentMessage.ok("Horario asignado.");
            loadAssignments();
        }, error -> assignmentMessage.error("No se pudo asignar.", error));
    }

    void loadAssignments() {
        Background.run(api::listAssignments, list -> assignments.setItems(FXCollections.observableList(list)),
                error -> assignmentMessage.error("No se pudieron cargar las asignaciones.", error));
    }

    private String scheduleName(String id) {
        return schedules.getItems().stream().filter(s -> s.id().equals(id)).map(Schedule::name).findFirst().orElse(id);
    }

    // ---- Festivos ------------------------------------------------------------------------
    private Node holidaysTab() {
        holidays.getColumns().setAll(List.of(
                Ui.column("Fecha", (Holiday h) -> Texts.date(h.date())),
                Ui.column("Descripción", Holiday::description)));
        holidayDescription.setPrefColumnCount(24);
        Button add = new Button("Añadir festivo");
        add.setId("add-holiday");
        add.setOnAction(e -> Background.run(() -> api.createHoliday(new Holiday(holidayDate.getValue(),
                holidayDescription.getText())), done -> {
            holidayMessage.ok("Festivo añadido.");
            loadHolidays();
        }, error -> holidayMessage.error("No se pudo añadir el festivo.", error)));
        VBox.setVgrow(holidays, Priority.ALWAYS);
        VBox card = Ui.card("Calendario de festivos",
                Ui.hint("En festivo no se esperan los horarios corridos ni partidos. Los turnos y guardias mantienen la cobertura."),
                Ui.row(Ui.field("Fecha", holidayDate), Ui.field("Descripción", holidayDescription), add),
                holidayMessage, holidays);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadHolidays() {
        Background.runAtStartup(api::listHolidays, list -> holidays.setItems(FXCollections.observableList(list)),
                error -> holidayMessage.error("No se pudieron cargar los festivos.", error));
    }

    // ---- Personas ------------------------------------------------------------------------
    private Node peopleTab() {
        people.getColumns().setAll(List.of(
                Ui.column("Código", Person::id),
                Ui.column("Nombre", Person::displayName),
                Ui.column("Equipo", (Person p) -> p.team() == null ? "—" : p.team())));
        personId.setPromptText("EMP-009");
        personName.setPrefColumnCount(20);
        Button add = new Button("Dar de alta");
        add.setId("add-person");
        add.setOnAction(e -> {
            String team = personTeam.getText() == null || personTeam.getText().isBlank() ? null : personTeam.getText();
            Person person = new Person(personId.getText(), personName.getText(), team);
            Background.run(() -> api.createPerson(person), done -> {
                personMessage.ok("Persona dada de alta.");
                personId.clear();
                personName.clear();
                personTeam.clear();
                loadPeople();
            }, error -> personMessage.error("No se pudo dar de alta.", error));
        });
        VBox.setVgrow(people, Priority.ALWAYS);
        VBox card = Ui.card("Personas",
                Ui.hint("Usa códigos internos y nombres ficticios: no introduzcas datos personales reales."),
                Ui.row(Ui.field("Código", personId), Ui.field("Nombre visible", personName),
                        Ui.field("Equipo (opcional)", personTeam), add),
                personMessage, people);
        VBox.setVgrow(card, Priority.ALWAYS);
        return Ui.page(card);
    }

    void loadPeople() {
        Background.runAtStartup(api::listPeople, list -> {
            people.setItems(FXCollections.observableList(list));
            assignPerson.setItems(FXCollections.observableList(list));
            if (assignPerson.getValue() == null) assignPerson.getSelectionModel().selectFirst();
        }, error -> personMessage.error("No se pudieron cargar las personas.", error));
    }
}
