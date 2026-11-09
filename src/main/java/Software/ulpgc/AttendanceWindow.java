package Software.ulpgc;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Separate authenticated employee and manager views for the local desktop application. */
public final class AttendanceWindow extends JFrame {
    private final AttendanceBroker broker;
    private AttendanceBroker.Session session;
    private final DateTimeFormatter times = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public AttendanceWindow(AttendanceBroker broker) {
        super("Registro de asistencia");
        this.broker = broker;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {
                broker.logout(session);
                if (Arrays.stream(Window.getWindows()).noneMatch(Window::isDisplayable)) {
                    try { broker.close(); }
                    catch (java.io.IOException exception) {
                        JOptionPane.showMessageDialog(null, exception.getMessage(), "Error al cerrar", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        setMinimumSize(new Dimension(1000, 650));
        setSize(1150, 720);
        setLocationByPlatform(true);
        showLogin();
    }

    private void showLogin() {
        boolean setup = broker.needsSetup();
        JPanel root = new JPanel(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
        form.setPreferredSize(new Dimension(380, 300));
        form.add(new JLabel(setup ? "Crea la cuenta del responsable" : "Iniciar sesion"));
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        form.add(new JLabel("Usuario"));
        form.add(username);
        form.add(new JLabel("Clave" + (setup ? " (minimo 8 caracteres)" : "")));
        form.add(password);
        JButton login = new JButton(setup ? "Crear responsable y entrar" : "Entrar");
        login.addActionListener(event -> runAction(() -> {
            char[] secret = password.getPassword();
            try {
                if (setup && !"root".equals(username.getText().trim()))
                    broker.createFirstManager(username.getText(), secret);
                session = broker.login(username.getText(), secret);
            } finally {
                Arrays.fill(secret, '\0');
                password.setText("");
            }
            showDashboard();
        }));
        form.add(login);
        root.add(form);
        setContentPane(root);
        getRootPane().setDefaultButton(login);
        revalidate();
        repaint();
    }

    private void showDashboard() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel header = new JPanel(new BorderLayout());
        boolean manager = broker.role(session) == AttendanceBroker.Role.MANAGER;
        header.add(new JLabel((manager ? "Responsable: " : "Empleado: ") + broker.displayName(session)
                + " | Horario: " + ZoneId.systemDefault()), BorderLayout.WEST);
        JPanel actions = new JPanel();
        JButton another = new JButton("Otra ventana");
        another.addActionListener(event -> new AttendanceWindow(broker).setVisible(true));
        actions.add(another);
        if (broker.isRoot(session)) {
            JButton backup = new JButton("Crear backup");
            backup.addActionListener(event -> runAction(() -> {
                var path = broker.createBackup(session);
                JOptionPane.showMessageDialog(this, "Backup creado: " + path.getFileName()
                        + "\nSe conservan las dos copias mas recientes.");
            }));
            actions.add(backup);
            JButton reset = new JButton("Reset");
            reset.addActionListener(event -> runAction(() -> {
                int result = JOptionPane.showConfirmDialog(this,
                        "Se borraran todos los usuarios excepto root, el historial y todos los backups.\n"
                                + "La clave de root se conserva. Esta operacion no se puede deshacer.",
                        "Reset de la aplicacion", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result != JOptionPane.OK_OPTION) return;
                try {
                    broker.resetApplication(session);
                } finally {
                    // Refresh every window, including sessions invalidated by the reset.
                    for (Window window : Window.getWindows()) {
                        if (window instanceof AttendanceWindow view && view.broker == broker && view.isDisplayable()) {
                            view.broker.logout(view.session);
                            view.session = null;
                            view.showLogin();
                        }
                    }
                }
            }));
            actions.add(reset);
        }
        JButton logout = new JButton("Cerrar sesion");
        logout.addActionListener(event -> {
            broker.logout(session);
            session = null;
            showLogin();
        });
        actions.add(logout);
        header.add(actions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        root.add(manager ? managerView() : employeeView(), BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(null);
        revalidate();
        repaint();
    }

    private JComponent employeeView() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel();
        JTextField place = new JTextField("Oficina principal", 18);
        controls.add(new JLabel("Lugar de trabajo:"));
        controls.add(place);
        JButton checkIn = new JButton("Solicitar entrada");
        JButton checkOut = new JButton("Solicitar salida");
        JButton refresh = new JButton("Actualizar");
        controls.add(checkIn);
        controls.add(checkOut);
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);

        DefaultTableModel requests = model("Tipo", "Hora de solicitud", "Estado", "Responsable", "Hora de decision", "Motivo");
        DefaultTableModel history = model("Lugar", "Entrada aprobada", "Salida aprobada", "Horas trabajadas");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Mis solicitudes", table(requests));
        tabs.addTab("Mi registro de horas", table(history));
        tabs.addTab("Mis ausencias", absenceView(false));
        panel.add(tabs, BorderLayout.CENTER);
        JLabel total = new JLabel();
        panel.add(total, BorderLayout.SOUTH);

        Runnable reload = () -> {
            requests.setRowCount(0);
            for (AttendanceRequest r : broker.myRequests(session))
                requests.addRow(new Object[]{type(r), time(r.requestedAt()), status(r),
                        r.decidedBy(), time(r.decidedAt()), r.reason()});
            history.setRowCount(0);
            for (Attendance a : broker.myHistory(session))
                history.addRow(new Object[]{a.place().name(), time(a.checkIn()),
                        a.isOpen() ? "Abierta" : time(a.checkOut()),
                        a.isOpen() ? "En curso" : duration(a.workedDuration())});
            total.setText("Total aprobado (sesiones cerradas): " + duration(broker.myWorkedHours(session))
                    + " | Las solicitudes pendientes no se suman hasta su aprobacion.");
        };
        refresh.addActionListener(event -> runAction(reload::run));
        checkIn.addActionListener(event -> runAction(() -> {
            broker.requestCheckIn(session, new Place(place.getText().trim(), "Oficina", 0, 0));
            reload.run();
            JOptionPane.showMessageDialog(this, "Entrada enviada al responsable.");
        }));
        checkOut.addActionListener(event -> runAction(() -> {
            broker.requestCheckOut(session);
            reload.run();
            JOptionPane.showMessageDialog(this, "Salida enviada al responsable.");
        }));
        reload.run();
        return panel;
    }

    private JComponent managerView() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Solicitudes pendientes", requestReview(true));
        tabs.addTab("Registro de solicitudes y decisiones", requestReview(false));
        tabs.addTab("Horarios y ausencias", scheduleView());
        tabs.addTab("Alta de empleados", employeeRegistration());
        tabs.addTab("Usuarios y claves", userManagement());
        return tabs;
    }

    private JComponent requestReview(boolean pendingOnly) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        DefaultTableModel model = model("Empleado", "ID", "Lugar", "Tipo", "Hora de solicitud", "Estado",
                "Responsable", "Hora de decision", "Motivo");
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton refresh = new JButton("Actualizar");
        actions.add(refresh);
        panel.add(actions, BorderLayout.SOUTH);

        // Keep the exact snapshot backing the table, including after sorting.
        class Rows {
            List<AttendanceRequest> items = List.of();
            void reload() {
                items = pendingOnly ? broker.pendingRequests(session) : broker.allRequests(session);
                model.setRowCount(0);
                for (AttendanceRequest r : items)
                    model.addRow(new Object[]{r.employee().name() + " " + r.employee().lastname(),
                            r.employee().id(), r.place().name(), type(r), time(r.requestedAt()), status(r),
                            r.decidedBy(), time(r.decidedAt()), r.reason()});
            }
            void decide(boolean approve) throws Exception {
                int selected = table.getSelectedRow();
                if (selected < 0) throw new IllegalStateException("Selecciona una solicitud");
                AttendanceRequest request = items.get(table.convertRowIndexToModel(selected));
                String reason = "";
                if (!approve) {
                    reason = JOptionPane.showInputDialog(AttendanceWindow.this, "Motivo de la denegacion:");
                    if (reason == null) return;
                }
                broker.decide(session, request.id(), approve, reason);
                reload();
            }
        }
        Rows rows = new Rows();
        refresh.addActionListener(event -> runAction(rows::reload));
        if (pendingOnly) {
            JButton approve = new JButton("Aceptar");
            JButton deny = new JButton("Denegar");
            approve.addActionListener(event -> runAction(() -> rows.decide(true)));
            deny.addActionListener(event -> runAction(() -> rows.decide(false)));
            actions.add(approve);
            actions.add(deny);
        }
        rows.reload();
        return panel;
    }

    private JComponent absenceView(boolean manager) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        DefaultTableModel rows = model("Empleado", "ID", "Lugar", "Entrada prevista");
        panel.add(table(rows), BorderLayout.CENTER);
        Runnable reload = () -> {
            rows.setRowCount(0);
            for (Missing item : manager ? broker.allMissing(session) : broker.myMissing(session))
                rows.addRow(new Object[]{item.employe().name() + " " + item.employe().lastname(),
                        item.employe().id(), item.place().name(), time(item.date().toInstant())});
        };
        JButton refresh = new JButton("Actualizar ausencias");
        refresh.addActionListener(event -> runAction(reload::run));
        panel.add(refresh, BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private JComponent scheduleView() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<Employe> employee = new JComboBox<>();
        employee.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                Object label = value instanceof Employe e ? e.id() + " - " + e.name() + " " + e.lastname() : value;
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        Runnable loadEmployees = () -> {
            employee.removeAllItems();
            for (AttendanceBroker.UserInfo user : broker.allUsers(session))
                if (user.employee() != null) employee.addItem(user.employee());
        };
        JButton refreshEmployees = new JButton("Actualizar empleados");
        refreshEmployees.addActionListener(event -> runAction(loadEmployees::run));
        JTextField place = new JTextField("Oficina principal", 12);
        JTextField date = new JTextField(java.time.LocalDate.now().toString(), 10);
        JTextField hour = new JTextField("09:00", 5);
        JTextField zone = new JTextField(ZoneId.systemDefault().getId(), 14);
        form.add(employee); form.add(refreshEmployees);
        form.add(new JLabel("Lugar")); form.add(place);
        form.add(new JLabel("Fecha AAAA-MM-DD")); form.add(date);
        form.add(new JLabel("Hora HH:mm")); form.add(hour);
        form.add(new JLabel("Zona")); form.add(zone);
        JButton save = new JButton("Guardar horario");
        form.add(save);
        form.setPreferredSize(new Dimension(1000, 100));
        panel.add(form, BorderLayout.NORTH);
        DefaultTableModel schedules = model("Empleado", "ID", "Lugar", "Fecha", "Hora", "Zona");
        Runnable reload = () -> {
            schedules.setRowCount(0);
            for (Quadrant q : broker.allQuadrants(session))
                for (int i = 0; i < q.labor().size(); i++)
                    schedules.addRow(new Object[]{q.employe().name(), q.employe().id(), q.place().name(),
                            q.labor().get(i), q.timetable().get(i), q.zone()});
        };
        JTabbedPane tabs = new JTabbedPane();
        JPanel schedulePanel = new JPanel(new BorderLayout());
        schedulePanel.add(table(schedules), BorderLayout.CENTER);
        JButton refresh = new JButton("Actualizar horarios");
        refresh.addActionListener(event -> runAction(reload::run));
        schedulePanel.add(refresh, BorderLayout.SOUTH);
        tabs.addTab("Horarios", schedulePanel);
        tabs.addTab("Ausencias", absenceView(true));
        panel.add(tabs, BorderLayout.CENTER);
        save.addActionListener(event -> runAction(() -> {
            Employe selected = (Employe) employee.getSelectedItem();
            if (selected == null) throw new IllegalStateException("Selecciona un empleado");
            broker.registerQuadrant(session, new Quadrant(selected,
                    new Place(place.getText().trim(), "Oficina", 0, 0),
                    List.of(java.time.LocalDate.parse(date.getText().trim())),
                    List.of(java.time.LocalTime.parse(hour.getText().trim())), ZoneId.of(zone.getText().trim())));
            reload.run();
        }));
        loadEmployees.run();
        reload.run();
        return panel;
    }

    private JComponent userManagement() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        DefaultTableModel users = model("Usuario", "Rol", "ID", "Nombre", "Apellidos");
        JTable table = new JTable(users);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        Runnable reload = () -> {
            users.setRowCount(0);
            for (AttendanceBroker.UserInfo user : broker.allUsers(session)) {
                Employe employee = user.employee();
                users.addRow(new Object[]{user.username(), user.role(), employee == null ? "" : employee.id(),
                        employee == null ? "" : employee.name(), employee == null ? "" : employee.lastname()});
            }
        };
        JPanel actions = new JPanel();
        JButton refresh = new JButton("Actualizar");
        refresh.addActionListener(event -> runAction(reload::run));
        JButton reset = new JButton("Restablecer clave");
        reset.addActionListener(event -> runAction(() -> {
            int selected = table.getSelectedRow();
            if (selected < 0) throw new IllegalStateException("Selecciona un usuario");
            String username = (String) users.getValueAt(table.convertRowIndexToModel(selected), 0);
            JPasswordField password = new JPasswordField(20);
            int result = JOptionPane.showConfirmDialog(this,
                    new Object[]{"Nueva clave para " + username + " (minimo 8 caracteres)", password},
                    "Restablecer clave", JOptionPane.OK_CANCEL_OPTION);
            char[] secret = password.getPassword();
            try {
                if (result != JOptionPane.OK_OPTION) return;
                broker.resetPassword(session, username, secret);
                JOptionPane.showMessageDialog(this, "Clave actualizada. Las otras sesiones del usuario se han cerrado.");
            } finally { Arrays.fill(secret, '\0'); password.setText(""); }
        }));
        actions.add(refresh);
        actions.add(reset);
        actions.add(new JLabel("Las claves se protegen con hash y no se pueden consultar."));
        panel.add(actions, BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private JComponent employeeRegistration() {
        JPanel container = new JPanel(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        JTextField id = new JTextField(20);
        JTextField name = new JTextField();
        JTextField lastName = new JTextField();
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        form.add(new JLabel("ID de empleado")); form.add(id);
        form.add(new JLabel("Nombre")); form.add(name);
        form.add(new JLabel("Apellidos")); form.add(lastName);
        form.add(new JLabel("Usuario")); form.add(username);
        form.add(new JLabel("Clave (minimo 8 caracteres)")); form.add(password);
        JButton create = new JButton("Crear empleado");
        create.addActionListener(event -> runAction(() -> {
            char[] secret = password.getPassword();
            try {
                broker.registerEmployee(session, username.getText(), secret,
                        new Employe(name.getText().trim(), lastName.getText().trim(), Integer.parseInt(id.getText().trim())));
            } finally { Arrays.fill(secret, '\0'); password.setText(""); }
            id.setText(""); name.setText(""); lastName.setText(""); username.setText("");
            JOptionPane.showMessageDialog(this, "Empleado creado. Ya puede iniciar sesion.");
        }));
        form.add(new JLabel()); form.add(create);
        container.add(form);
        return container;
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static JScrollPane table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        return new JScrollPane(table);
    }

    private String time(Instant time) { return time == null ? "" : times.format(time); }
    private static String type(AttendanceRequest request) {
        return request.type() == AttendanceRequest.Type.CHECK_IN ? "Entrada" : "Salida";
    }
    private static String status(AttendanceRequest request) {
        return switch (request.status()) {
            case PENDING -> "Pendiente";
            case APPROVED -> "Aprobada";
            case DENIED -> "Denegada";
        };
    }
    private static String duration(Duration duration) {
        return "%d h %02d min %02d s".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private void runAction(Action action) {
        try { action.run(); }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "No se pudo completar", JOptionPane.ERROR_MESSAGE);
        }
    }
}
