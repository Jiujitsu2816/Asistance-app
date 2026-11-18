package software.ulpgc.presencia.frontend.horarios;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import software.ulpgc.presencia.frontend.api.ApiModel.CycleEntry;
import software.ulpgc.presencia.frontend.api.ApiModel.Schedule;
import software.ulpgc.presencia.frontend.api.ApiModel.Slot;
import software.ulpgc.presencia.frontend.ui.Texts;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Formulario de un horario. Muestra solo los campos del tipo elegido y construye el cuerpo
 * de POST /api/schedules. Las reglas (tramos separados, solapes...) las valida el backend;
 * aqui solo se comprueba el formato de las horas.
 */
final class ScheduleForm extends VBox {
    static final List<String> KINDS = List.of("CONTINUOUS", "SPLIT", "ROTATING_SHIFT", "ON_CALL");
    private static final String[] DAY_NAMES = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    final TextField name = new TextField();
    final ComboBox<String> kind = new ComboBox<>(FXCollections.observableList(KINDS));
    final Spinner<Integer> tolerance = new Spinner<>(0, 120, 10, 5);
    // Corrido y partido
    final List<CheckBox> days = new ArrayList<>();
    final VBox slots = new VBox(6);
    final Button addSlot = new Button("Añadir tramo");
    private final VBox weeklyPane;
    // Turnos rotatorios
    final DatePicker cycleStart = Ui.datePicker();
    final VBox cycle = new VBox(6);
    private final VBox rotatingPane;
    // Guardias
    final TextField onCallStart = new TextField("08:00");
    final Spinner<Integer> onCallHours = new Spinner<>(1, 24, 24);
    final ComboBox<String> onCallMode = new ComboBox<>(FXCollections.observableList(List.of("PHYSICAL", "LOCALIZED")));
    final DatePicker onCallDate = Ui.datePicker();
    final TreeSet<LocalDate> onCallDates = new TreeSet<>();
    final FlowPane onCallDateChips = new FlowPane(6, 6);
    private final VBox onCallPane;

    ScheduleForm(LocalDate today) {
        super(12);
        name.setId("schedule-name");
        kind.setId("schedule-kind");
        name.setPromptText("p. ej. Oficina central");
        name.setPrefColumnCount(22);
        tolerance.setPrefWidth(100);
        tolerance.setEditable(true);
        kind.setConverter(converter(code -> switch (code) {
            case "CONTINUOUS" -> "Corrido (oficina)";
            case "SPLIT" -> "Partido (tienda / restaurante)";
            case "ROTATING_SHIFT" -> "Turnos rotatorios (hospital)";
            default -> "Guardias (hospital)";
        }));

        for (int i = 0; i < 7; i++) {
            CheckBox day = new CheckBox(DAY_NAMES[i]);
            day.setSelected(i < 5);
            days.add(day);
        }
        slots.getChildren().add(slotRow("08:00", "15:00"));
        addSlot.getStyleClass().addAll("secondary", "small");
        addSlot.setOnAction(e -> {
            slots.getChildren().add(slotRow("17:00", "21:00"));
            refreshSlotButtons();
        });
        HBox dayBox = new HBox(12);
        dayBox.getChildren().addAll(days);
        weeklyPane = new VBox(10, Ui.field("Días laborables", dayBox), Ui.field("Tramos horarios", slots), addSlot);

        cycleStart.setValue(today);
        cycle.getChildren().addAll(cycleRow("Mañana", "07:00", 8), cycleRow("Tarde", "15:00", 8),
                cycleRow("Noche", "23:00", 8), cycleRow(null, "07:00", 8), cycleRow(null, "07:00", 8));
        Button addDay = new Button("Añadir día");
        addDay.getStyleClass().addAll("secondary", "small");
        addDay.setOnAction(e -> cycle.getChildren().add(cycleRow("Mañana", "07:00", 8)));
        rotatingPane = new VBox(10, Ui.field("Inicio del ciclo", cycleStart),
                Ui.field("Días del ciclo (se repite indefinidamente)", cycle), addDay);

        onCallMode.setConverter(converter(code -> code.equals("PHYSICAL")
                ? "Presencia física (exige fichar)" : "Localizada (no exige presencia)"));
        onCallMode.getSelectionModel().selectFirst();
        onCallStart.setPrefColumnCount(6);
        onCallHours.setPrefWidth(90);
        Button addDate = new Button("Añadir fecha");
        addDate.setId("add-date");
        addDate.getStyleClass().add("secondary");
        addDate.setOnAction(e -> {
            if (onCallDate.getValue() != null) onCallDates.add(onCallDate.getValue());
            renderDates();
        });
        renderDates();
        onCallPane = new VBox(10,
                Ui.row(Ui.field("Hora de inicio (HH:mm)", onCallStart), Ui.field("Duración (horas)", onCallHours),
                        Ui.field("Modalidad", onCallMode)),
                Ui.row(Ui.field("Fecha de guardia", onCallDate), addDate),
                onCallDateChips);

        kind.valueProperty().addListener((obs, old, selected) -> updateKind());
        kind.getSelectionModel().selectFirst();
        getChildren().addAll(
                Ui.row(Ui.field("Nombre", name), Ui.field("Tipo", kind), Ui.field("Tolerancia (min)", tolerance)),
                weeklyPane, rotatingPane, onCallPane);
        updateKind();
    }

    private void updateKind() {
        String selected = kind.getValue();
        show(weeklyPane, "CONTINUOUS".equals(selected) || "SPLIT".equals(selected));
        show(rotatingPane, "ROTATING_SHIFT".equals(selected));
        show(onCallPane, "ON_CALL".equals(selected));
        if ("CONTINUOUS".equals(selected)) {
            while (slots.getChildren().size() > 1) slots.getChildren().removeLast();
        } else if ("SPLIT".equals(selected)) {
            while (slots.getChildren().size() < 2) slots.getChildren().add(slotRow("17:00", "21:00"));
        }
        show(addSlot, "SPLIT".equals(selected));
        refreshSlotButtons();
    }

    /** Un horario corrido tiene exactamente un tramo: no se puede quitar. */
    private void refreshSlotButtons() {
        boolean removable = "SPLIT".equals(kind.getValue());
        slots.getChildren().forEach(row -> show(((HBox) row).getChildren().getLast(), removable));
    }

    private HBox slotRow(String start, String end) {
        TextField from = timeField(start);
        TextField to = timeField(end);
        Button remove = new Button("Quitar");
        remove.getStyleClass().addAll("secondary", "small");
        HBox row = new HBox(8, new Label("De"), from, new Label("a"), to, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        remove.setOnAction(e -> slots.getChildren().remove(row));
        return row;
    }

    private HBox cycleRow(String label, String start, int hours) {
        ComboBox<String> type = new ComboBox<>(FXCollections.observableList(List.of("Trabajo", "Descanso")));
        type.getSelectionModel().select(label == null ? 1 : 0);
        TextField shift = new TextField(label == null ? "" : label);
        shift.setPromptText("Turno");
        shift.setPrefColumnCount(9);
        TextField from = timeField(start);
        Spinner<Integer> duration = new Spinner<>(1, 24, hours);
        duration.setPrefWidth(80);
        Label hoursLabel = new Label("h");
        Button remove = new Button("Quitar");
        remove.getStyleClass().addAll("secondary", "small");
        HBox row = new HBox(8, type, shift, new Label("desde"), from, duration, hoursLabel, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        Runnable sync = () -> {
            boolean work = type.getValue().equals("Trabajo");
            for (int i = 1; i <= 5; i++) show(row.getChildren().get(i), work);
        };
        type.valueProperty().addListener((obs, old, value) -> sync.run());
        sync.run();
        remove.setOnAction(e -> cycle.getChildren().remove(row));
        return row;
    }

    private void renderDates() {
        onCallDateChips.getChildren().clear();
        if (onCallDates.isEmpty()) {
            Label none = new Label("Ninguna fecha añadida");
            none.getStyleClass().add("sub");
            onCallDateChips.getChildren().add(none);
        }
        for (LocalDate date : onCallDates) {
            Button chip = new Button(Texts.date(date) + "  ×");
            chip.getStyleClass().addAll("secondary", "small");
            chip.setOnAction(e -> {
                onCallDates.remove(date);
                renderDates();
            });
            onCallDateChips.getChildren().add(chip);
        }
    }

    /** Cuerpo JSON del contrato. Lanza DateTimeParseException si una hora no es HH:mm. */
    @SuppressWarnings("unchecked")
    Schedule build() {
        String selected = kind.getValue();
        int toleranceMinutes = tolerance.getValue();
        String scheduleName = name.getText() == null ? "" : name.getText().strip();
        return switch (selected) {
            case "CONTINUOUS", "SPLIT" -> {
                List<DayOfWeek> workDays = new ArrayList<>();
                for (int i = 0; i < 7; i++) if (days.get(i).isSelected()) workDays.add(DayOfWeek.of(i + 1));
                List<Slot> slotList = slots.getChildren().stream().map(node -> {
                    HBox row = (HBox) node;
                    return new Slot(time((TextField) row.getChildren().get(1)), time((TextField) row.getChildren().get(3)));
                }).toList();
                yield new Schedule(null, scheduleName, selected, toleranceMinutes, workDays, slotList,
                        null, null, null, null, null, null, null);
            }
            case "ROTATING_SHIFT" -> {
                List<CycleEntry> entries = cycle.getChildren().stream().map(node -> {
                    HBox row = (HBox) node;
                    if (((ComboBox<String>) row.getChildren().get(0)).getValue().equals("Descanso"))
                        return CycleEntry.restDay();
                    return new CycleEntry(null, ((TextField) row.getChildren().get(1)).getText(),
                            time((TextField) row.getChildren().get(3)),
                            ((Spinner<Integer>) row.getChildren().get(4)).getValue() * 60);
                }).toList();
                yield new Schedule(null, scheduleName, selected, toleranceMinutes, null, null, cycleStart.getValue(),
                        entries, null, null, null, null, null);
            }
            default -> new Schedule(null, scheduleName, selected, toleranceMinutes, null, null, null, null,
                    time(onCallStart), onCallHours.getValue() * 60, onCallMode.getValue(),
                    List.copyOf(onCallDates), null);
        };
    }

    private static LocalTime time(TextField field) {
        return LocalTime.parse(field.getText() == null ? "" : field.getText().strip());
    }

    private static TextField timeField(String value) {
        TextField field = new TextField(value);
        field.setPromptText("HH:mm");
        field.setPrefColumnCount(5);
        return field;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : text.apply(value); }

            @Override public T fromString(String value) { return null; }
        };
    }

    static boolean isTimeError(Throwable error) {
        return error instanceof DateTimeParseException;
    }
}
