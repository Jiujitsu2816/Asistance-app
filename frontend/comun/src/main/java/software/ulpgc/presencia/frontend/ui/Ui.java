package software.ulpgc.presencia.frontend.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.converter.LocalDateStringConverter;

import java.time.format.DateTimeFormatter;

import java.util.Objects;
import java.util.function.Function;

/** Construccion comun de ventanas, tarjetas, campos y tablas con el mismo estilo. */
public final class Ui {
    public static final String STYLESHEET = Objects.requireNonNull(
            Ui.class.getResource("presencia.css"), "Falta presencia.css").toExternalForm();

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Ui() { }

    /** Ventana con cabecera (titulo, descripcion y servidor) y el contenido indicado. */
    public static Scene scene(String title, String subtitle, String server, Parent content, double width,
                              double height) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("app-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("app-subtitle");
        Label serverLabel = new Label("API: " + server);
        serverLabel.getStyleClass().add("app-server");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(14, titleLabel, subtitleLabel, spacer, serverLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");

        BorderPane root = new BorderPane(content);
        root.setTop(header);
        root.getStyleClass().add("app-root");
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(STYLESHEET);
        return scene;
    }

    public static VBox card(String title, Node... children) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");
        VBox card = new VBox(12, heading);
        card.getChildren().addAll(children);
        card.getStyleClass().add("card");
        return card;
    }

    public static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("hint");
        return label;
    }

    /** Campo de formulario: etiqueta encima del control. */
    public static VBox field(String label, Node control) {
        Label caption = new Label(label);
        caption.getStyleClass().add("field-label");
        caption.setLabelFor(control);
        VBox box = new VBox(4, caption, control);
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    public static HBox row(Node... nodes) {
        HBox row = new HBox(14, nodes);
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    public static VBox page(Node... cards) {
        VBox page = new VBox(16, cards);
        page.setPadding(new Insets(18));
        return page;
    }

    public static <T> TableView<T> table(String placeholder) {
        TableView<T> table = new TableView<>();
        // Reparte el ancho en proporcion al ancho preferido de cada columna.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label(placeholder));
        return table;
    }

    /** Columna de texto; el texto largo se ajusta en varias lineas en lugar de cortarse. */
    public static <T> TableColumn<T, String> column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            private final Text text = new Text();

            {
                text.getStyleClass().add("cell-text");
                text.wrappingWidthProperty().bind(c.widthProperty().subtract(18));
            }

            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                text.setText(item);
                setGraphic(empty || item == null ? null : text);
            }
        });
        column.setSortable(false);
        return column;
    }

    /** Columna con un nodo (por ejemplo, una {@link Badge}). */
    public static <T> TableColumn<T, T> nodeColumn(String title, Function<T, Node> node) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setPrefWidth(150);
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : node.apply(item));
            }
        });
        column.setSortable(false);
        return column;
    }

    /** Columna numerica alineada a la derecha y sin ajuste de linea. */
    public static <T> TableColumn<T, String> numberColumn(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.getStyleClass().add("number");
        column.setSortable(false);
        column.setPrefWidth(110);
        column.setMinWidth(95);
        return column;
    }

    /** Selector de fecha con formato dd/MM/yyyy, independiente del idioma del sistema. */
    public static DatePicker datePicker() {
        DatePicker picker = new DatePicker();
        picker.setConverter(new LocalDateStringConverter(DATE, DATE));
        picker.setPromptText("dd/mm/aaaa");
        picker.setPrefWidth(150);
        return picker;
    }
}
