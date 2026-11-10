import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/** College assignment and study session tracker. Run with: java AssignmentTracker.java */
public class AssignmentTracker extends JFrame {
    private static final String APP_VERSION = "Beta 0.2";
    private static final Path DATA_FILE = Path.of(System.getProperty("user.home"), ".assignment-tracker.tsv");
    private static final Path SESSION_FILE = Path.of(System.getProperty("user.home"), ".assignment-tracker-sessions.tsv");
    private static final Path STUDY_LOG_FILE = Path.of(System.getProperty("user.home"), ".assignment-tracker-study-logs.tsv");
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".assignment-tracker-settings");
    private static final Path MOTION_SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".assignment-tracker-motion-settings");
    private static final DateTimeFormatter INPUT_DATE = new DateTimeFormatterBuilder()
            .appendPattern("dd/MM/").appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.UK).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.UK);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final List<RoomOption> ROOM_OPTIONS = List.of(
            new RoomOption("Seminar room", "Waterford", "Luke Wadding Library", "Seminar rooms (6)", "https://wit-ie.libcal.com/spaces?gid=8131&lid=3684"),
            new RoomOption("Study booth", "Waterford", "Luke Wadding Library", "Study Booth 1", "https://wit-ie.libcal.com/spaces?gid=8528&lid=3684"),
            new RoomOption("Study booth", "Waterford", "Luke Wadding Library", "Study Booth 2", "https://wit-ie.libcal.com/spaces?gid=8528&lid=3684")
    );
    private record RoomOption(String type, String campus, String library, String room, String url) { }

    private final JTextField titleField = new JTextField();
    private final JTextField moduleField = new JTextField();
    private final JTextField dateField = new JTextField();
    private final JTextField briefField = new JTextField();
    private final JComboBox<String> priorityBox = new JComboBox<>(new String[]{"High", "Medium", "Low"});
    private final JTextField sessionTitleField = new JTextField();
    private final JTextField sessionModuleField = new JTextField();
    private final JTextField sessionDateField = new JTextField();
    private final JTextField sessionTimeField = new JTextField();
    private final JTextField sessionNotesField = new JTextField();
    private final JCheckBox assignmentReminderCheck = new JCheckBox("Remind me when this assignment is due");
    private final JTextField assignmentSearchField = new JTextField();
    private final JComboBox<String> assignmentPriorityFilter = new JComboBox<>(new String[]{"All priorities", "High", "Medium", "Low"});
    private final JComboBox<String> assignmentDateFilter = new JComboBox<>(new String[]{"All deadlines", "Date set", "No date yet"});
    private final JComboBox<String> sessionTypeBox = new JComboBox<>(new String[]{"Theory", "Practical"});
    private final JComboBox<String> roomTypeFilter = new JComboBox<>(new String[]{"All room types", "Seminar room", "Study booth"});
    private final JComboBox<String> campusFilter = new JComboBox<>(new String[]{"All campuses", "Waterford"});
    private final JComboBox<String> libraryFilter = new JComboBox<>(new String[]{"All libraries", "Luke Wadding Library"});
    private final JLabel countLabel = new JLabel("0 assignments");
    private boolean darkMode = loadDarkMode();
    private boolean transitionsEnabled = loadTransitionsEnabled();
    private String activeTimerTitle;
    private String activeTimerModule;
    private long activeTimerStartedAt;
    private LocalDate activeTimerDate;
    private int pomodoroFocusMinutes = 25;
    private int pomodoroBreakMinutes = 5;
    private boolean pomodoroBreak;
    private final JLabel timerElapsedLabel = new JLabel("00:00:00");
    private JLabel timerStatusLabel;
    private JButton focusTimerButton;
    private Timer focusTimer;
    private LocalDate lastReminderShownDate;
    private int selectedTab = 4;
    private JPanel sidebarPanel;
    private JPanel mainPanel;
    private JScrollPane pageSurface;
    private JLabel sidebarBrandName;
    private JButton moodleButton;
    private final List<JButton> navigationButtons = new ArrayList<>();
    private boolean compactLayout;
    private SlideGlassPane slidePane;
    private int calendarYear = LocalDate.now().getYear();
    private int calendarMonth = LocalDate.now().getMonthValue();
    private LocalDate selectedCalendarDate = LocalDate.now();

    private final DefaultTableModel assignmentModel = new DefaultTableModel(
            new String[]{"Assignment", "Module", "Due date", "Days left", "Priority", "Brief", "Reminder"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 2 ? LocalDate.class : column == 3 ? Integer.class : String.class;
        }
    };
    private final DefaultTableModel sessionModel = new DefaultTableModel(
            new String[]{"Studying", "Module", "Work type", "Date", "Time", "Notes"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 3 ? LocalDate.class : column == 4 ? LocalTime.class : String.class;
        }
    };
    private final DefaultTableModel roomModel = new DefaultTableModel(
            new String[]{"Date", "Room type", "Campus", "Library", "Room", "Availability", "Calendar URL"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? LocalDate.class : String.class; }
    };
    private final DefaultTableModel studyLogModel = new DefaultTableModel(
            new String[]{"Date", "Studying", "Module", "Seconds"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? LocalDate.class : column == 3 ? Long.class : String.class; }
    };

    public AssignmentTracker() {
        super("Course Companion");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(720, 520));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());
        loadAssignments();
        loadSessions();
        loadStudyLogs();
        buildUi();
        updateCount();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { applyResponsiveLayout(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { if (activeTimerTitle != null) stopFocusTimer(); }
        });
        applyResponsiveLayout();
        Timer reminderClock = new Timer(60_000, e -> checkDeadlineReminders());
        reminderClock.setInitialDelay(1200); reminderClock.start();
        SwingUtilities.invokeLater(this::checkDeadlineReminders);
    }

    private void buildUi() {
        navigationButtons.clear();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(appBackground());
        sidebarPanel = createSidebar();
        root.add(sidebarPanel, BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout(0, 18));
        mainPanel = main;
        main.setBackground(appBackground());
        main.setBorder(new EmptyBorder(24, 26, 18, 26));
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Course Companion");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 28));
        heading.setForeground(ink());
        JLabel description = new JLabel(selectedTab == 0 ? "Assignments and deadlines"
                : selectedTab == 1 ? "Study sessions and plans" : selectedTab == 2 ? "Room availability and booking links"
                : selectedTab == 3 ? "Your month at a glance" : selectedTab == 5 ? "Study time recorded this week" : "Your study overview");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        description.setForeground(muted());
        description.setBorder(new EmptyBorder(5, 1, 0, 0));
        titles.add(heading);
        titles.add(description);
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        countLabel.setForeground(accent());
        countLabel.setBorder(new EmptyBorder(8, 13, 8, 13));
        countLabel.setOpaque(true);
        countLabel.setBackground(chipBackground());
        header.add(titles, BorderLayout.WEST);
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerActions.setOpaque(false);
        JLabel themeLabel = new JLabel("Dark mode");
        themeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        themeLabel.setForeground(muted());
        ThemeSwitch themeSwitch = new ThemeSwitch();
        themeSwitch.setSelected(darkMode);
        themeSwitch.setToolTipText("Switch between dark and light appearance");
        themeSwitch.getAccessibleContext().setAccessibleName("Dark mode");
        themeSwitch.addActionListener(e -> {
            darkMode = themeSwitch.isSelected();
            saveDarkMode();
            buildUi();
            revalidate();
            repaint();
        });
        headerActions.add(themeLabel);
        headerActions.add(themeSwitch);
        headerActions.add(countLabel);
        header.add(headerActions, BorderLayout.EAST);

        JPanel pages = new JPanel(new CardLayout());
        pages.setOpaque(false);
        pages.add(createAssignmentsWorkspace(), "assignments");
        pages.add(createSessionsPage(), "sessions");
        pages.add(createRoomFinderPage(), "rooms");
        pages.add(createCalendarPage(), "calendar");
        pages.add(createDashboardPage(), "dashboard");
        pages.add(createWeeklyStatsPage(), "stats");
        ((CardLayout) pages.getLayout()).show(pages, pageName(selectedTab));
        main.add(header, BorderLayout.NORTH);
        JScrollPane workspaceScroll = new JScrollPane(pages,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageSurface = workspaceScroll;
        workspaceScroll.setBorder(BorderFactory.createEmptyBorder());
        workspaceScroll.setOpaque(false);
        workspaceScroll.getViewport().setOpaque(false);
        workspaceScroll.getViewport().setBackground(appBackground());
        workspaceScroll.getViewport().setViewPosition(new Point(0, 0));
        main.add(workspaceScroll, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        compactLayout = getWidth() >= 1020;
        applyResponsiveLayout();
        getRootPane().setDefaultButton(selectedTab == 0 ? addAssignmentButton : null);
        updateCount();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebarPanel = sidebar;
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(sidebarBackground());
        sidebar.setBorder(new EmptyBorder(25, 15, 18, 15));
        sidebar.setPreferredSize(new Dimension(250, 0));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        JLabel icon = new JLabel(new ImageIcon(createAppIcon().getScaledInstance(34, 34, Image.SCALE_SMOOTH)));
        JLabel name = new JLabel("Course Companion");
        sidebarBrandName = name;
        name.setFont(new Font("Segoe UI", Font.BOLD, 18));
        name.setForeground(ink());
        brand.add(icon);
        brand.add(name);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(34));

        JLabel navigationLabel = new JLabel("WORKSPACE");
        navigationLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        navigationLabel.setForeground(muted());
        navigationLabel.setBorder(new EmptyBorder(0, 12, 10, 0));
        navigationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(navigationLabel);
        sidebar.add(createNavigationButton("", "Dashboard", 4));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavigationButton("", "Assignments", 0));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavigationButton("", "Study schedule", 1));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavigationButton("", "Room finder", 2));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavigationButton("", "Calendar", 3));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavigationButton("", "Weekly stats", 5));
        sidebar.add(Box.createVerticalGlue());

        JSeparator separator = new JSeparator();
        separator.setForeground(borderColor());
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(separator);
        sidebar.add(Box.createVerticalStrut(10));
        JButton moodle = new JButton("↗   Open Moodle");
        moodleButton = moodle;
        moodle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        moodle.setForeground(accent());
        moodle.setBackground(sidebarBackground());
        moodle.setHorizontalAlignment(SwingConstants.LEFT);
        moodle.setUI(new BasicButtonUI());
        moodle.setFocusPainted(false);
        moodle.setBorder(new EmptyBorder(12, 12, 12, 8));
        moodle.setBorderPainted(false);
        moodle.setContentAreaFilled(false);
        moodle.setOpaque(false);
        moodle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        moodle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        moodle.setAlignmentX(Component.LEFT_ALIGNMENT);
        moodle.addActionListener(e -> openMoodle());
        sidebar.add(moodle);
        return sidebar;
    }

    private void applyResponsiveLayout() {
        if (sidebarPanel == null || mainPanel == null || getWidth() <= 0) return;
        if (pageSurface != null) SwingUtilities.invokeLater(() -> {
            JViewport viewport = pageSurface.getViewport();
            Component view = viewport.getView();
            if (view != null && view.getHeight() <= viewport.getExtentSize().height) {
                viewport.setViewPosition(new Point(0, 0));
            }
        });
        boolean compact = getWidth() < 1020;
        if (compact == compactLayout) return;
        compactLayout = compact;
        sidebarPanel.setPreferredSize(new Dimension(compact ? 70 : 230, 0));
        sidebarPanel.setBorder(new EmptyBorder(22, compact ? 10 : 15, 18, compact ? 10 : 15));
        if (sidebarBrandName != null) sidebarBrandName.setVisible(!compact);
        for (JButton button : navigationButtons) {
            String label = String.valueOf(button.getClientProperty("navigationLabel"));
            button.setText(compact ? "" : label);
            button.setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
            button.setIconTextGap(compact ? 0 : 13);
            button.setToolTipText(compact ? label : null);
            button.setBorder(compact ? new EmptyBorder(12, 4, 12, 4) : new EmptyBorder(13, 14, 13, 10));
            if (selectedTab == Integer.parseInt(button.getActionCommand() == null ? "-1" : button.getActionCommand())) {
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, accent()),
                        new EmptyBorder(12, compact ? 1 : 11, 12, 7)));
            }
        }
        if (moodleButton != null) {
            moodleButton.setText(compact ? "↗" : "↗   Open Moodle");
            moodleButton.setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
            moodleButton.setToolTipText(compact ? "Open Moodle" : null);
        }
        mainPanel.setBorder(new EmptyBorder(compact ? 16 : 24, compact ? 14 : 26, compact ? 12 : 18, compact ? 14 : 26));
        mainPanel.revalidate(); sidebarPanel.revalidate();
        getContentPane().revalidate(); getContentPane().repaint();
    }

    private void navigateTo(int page) {
        if (page == selectedTab) return;
        BufferedImage from = capturePage();
        Rectangle area = mainPanel == null ? new Rectangle() : SwingUtilities.convertRectangle(
                mainPanel, new Rectangle(0, 0, mainPanel.getWidth(), mainPanel.getHeight()), getRootPane());
        if (!transitionsEnabled || from == null || area.width <= 0 || area.height <= 0) {
            selectedTab = page;
            buildUi();
            return;
        }
        slidePane = new SlideGlassPane(from, area, appBackground());
        setGlassPane(slidePane);
        slidePane.setVisible(true);
        slidePane.fadeOut(() -> {
            selectedTab = page;
            buildUi();
            validate();
            BufferedImage incoming = capturePage();
            if (incoming == null) slidePane.setVisible(false);
            else slidePane.fadeIn(incoming, () -> slidePane.setVisible(false));
        });
    }

    private BufferedImage capturePage() {
        if (mainPanel == null) return null;
        int width = mainPanel.getWidth(), height = mainPanel.getHeight();
        if (width <= 0 || height <= 0) return null;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        mainPanel.paint(g);
        g.dispose();
        return image;
    }

    private JButton createNavigationButton(String symbol, String label, int page) {
        JButton button = new JButton(label, new NavGlyph(page, selectedTab == page ? accent() : (darkMode ? ink() : muted())));
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(13);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setBorder(new EmptyBorder(13, 14, 13, 10));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.putClientProperty("navigationLabel", label);
        button.setActionCommand(Integer.toString(page));
        button.setToolTipText(label);
        navigationButtons.add(button);
        boolean active = selectedTab == page;
        button.setBackground(active ? chipBackground() : sidebarBackground());
        button.setForeground(active ? accent() : (darkMode ? ink() : muted()));
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        if (active) button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent()), new EmptyBorder(13, 11, 13, 10)));
        button.addActionListener(e -> {
            navigateTo(page);
        });
        return button;
    }

    private void openMoodle() {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create("https://moodle.setu.ie"));
            else throw new IOException("Opening web links is not supported on this computer.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open Moodle: " + ex.getMessage(),
                    "Moodle link", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton addAssignmentButton;

    private JPanel createAssignmentsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        JPanel form = cardPanel();
        form.setLayout(new BorderLayout(0, 14));
        JComponent formTitle = sectionHeader("Add an assignment", "Record the task, module, due date, and priority.");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = fieldConstraints();
        addField(fields, c, 0, "ASSIGNMENT NAME", titleField, 1.6);
        addField(fields, c, 1, "MODULE", moduleField, 1.0);
        addField(fields, c, 2, "DUE DATE  ·  DD/MM/YY (OPTIONAL)", dateField, 1.2);
        addField(fields, c, 3, "PRIORITY", priorityBox, 0.8);
        addAssignmentButton = new JButton("Add assignment");
        styleButton(addAssignmentButton, true);
        addAssignmentButton.addActionListener(e -> addAssignment());
        c.gridx = 4; c.gridy = 0; c.weightx = 0; c.insets = new Insets(20, 0, 0, 0);
        fields.add(addAssignmentButton, c);

        briefField.setEditable(false);
        briefField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        briefField.setForeground(ink());
        briefField.setBackground(inputBackground());
        briefField.setOpaque(true);
        briefField.setToolTipText("No brief selected");
        JPanel briefGroup = new JPanel(new BorderLayout(0, 7));
        briefGroup.setOpaque(false);
        JLabel briefCaption = new JLabel("BRIEF FILE  ·  OPTIONAL");
        briefCaption.setFont(new Font("Segoe UI", Font.BOLD, 10));
        briefCaption.setForeground(muted());
        JPanel briefControl = new JPanel(new BorderLayout(8, 0));
        briefControl.setOpaque(false);
        briefControl.add(briefField, BorderLayout.CENTER);
        JPanel briefActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        briefActions.setOpaque(false);
        JButton chooseBriefButton = new JButton("Choose brief");
        styleButton(chooseBriefButton, false);
        chooseBriefButton.addActionListener(e -> chooseBrief());
        JButton clearBriefButton = new JButton("Clear");
        styleButton(clearBriefButton, false);
        clearBriefButton.addActionListener(e -> clearBrief());
        briefActions.add(chooseBriefButton);
        briefActions.add(clearBriefButton);
        briefControl.add(briefActions, BorderLayout.EAST);
        briefGroup.add(briefCaption, BorderLayout.NORTH);
        briefGroup.add(briefControl, BorderLayout.CENTER);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 5; c.weightx = 1; c.insets = new Insets(14, 0, 0, 0);
        fields.add(briefGroup, c);
        assignmentReminderCheck.setText("Remind me when this assignment is due");
        assignmentReminderCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        assignmentReminderCheck.setForeground(ink()); assignmentReminderCheck.setOpaque(false);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 5; c.weightx = 1; c.insets = new Insets(10, 0, 0, 0);
        fields.add(assignmentReminderCheck, c);
        form.add(formTitle, BorderLayout.NORTH);
        form.add(fields, BorderLayout.CENTER);

        JPanel list = cardPanel();
        list.setLayout(new BorderLayout(0, 14));
        JPanel bar = new JPanel(new BorderLayout(0, 10));
        bar.setOpaque(false);
        bar.add(sectionBar("Assignment deadlines", "Select an assignment to open, attach, or remove its brief"), BorderLayout.NORTH);
        JPanel filters = new JPanel(new GridLayout(1, 3, 10, 0));
        filters.setOpaque(false);
        assignmentSearchField.setToolTipText("Search assignment name or module");
        assignmentSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        assignmentSearchField.setForeground(ink()); assignmentSearchField.setBackground(inputBackground());
        assignmentSearchField.setCaretColor(ink());
        assignmentSearchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor()), new EmptyBorder(7, 9, 7, 9)));
        filters.add(assignmentSearchField);
        styleFilterCombo(assignmentPriorityFilter);
        filters.add(assignmentPriorityFilter);
        styleFilterCombo(assignmentDateFilter);
        filters.add(assignmentDateFilter);
        bar.add(filters, BorderLayout.CENTER);
        JTable table = createTable(assignmentModel);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(assignmentModel);
        table.setRowSorter(sorter);
        Runnable applyAssignmentFilters = () -> {
            List<RowFilter<Object, Object>> terms = new ArrayList<>();
            String query = assignmentSearchField.getText().trim();
            if (!query.isEmpty()) terms.add(RowFilter.regexFilter("(?i)" + Pattern.quote(query), 0, 1));
            String priority = String.valueOf(assignmentPriorityFilter.getSelectedItem());
            if (!"All priorities".equals(priority)) terms.add(RowFilter.regexFilter("^" + Pattern.quote(priority) + "$", 4));
            String dateChoice = String.valueOf(assignmentDateFilter.getSelectedItem());
            if ("Date set".equals(dateChoice)) terms.add(new RowFilter<>() { public boolean include(Entry<?, ?> e) { return e.getValue(2) != null; } });
            if ("No date yet".equals(dateChoice)) terms.add(new RowFilter<>() { public boolean include(Entry<?, ?> e) { return e.getValue(2) == null; } });
            sorter.setRowFilter(terms.isEmpty() ? null : RowFilter.andFilter(terms));
        };
        assignmentSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyAssignmentFilters.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyAssignmentFilters.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyAssignmentFilters.run(); }
        });
        assignmentPriorityFilter.addActionListener(e -> applyAssignmentFilters.run());
        assignmentDateFilter.addActionListener(e -> applyAssignmentFilters.run());
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(145);
        table.getColumnModel().getColumn(1).setCellRenderer(moduleColorRenderer(1));
        table.getColumnModel().getColumn(2).setPreferredWidth(115);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(95);
        table.getColumnModel().getColumn(6).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setCellRenderer(dateRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(countdownRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(priorityRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                boolean attached = value != null && !value.toString().isBlank();
                setText(attached ? "Attached" : "No brief");
                setForeground(attached ? accent() : muted());
            }
        });
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                boolean enabled = Boolean.TRUE.equals(value); setText(enabled ? "On" : "Off"); setForeground(enabled ? accent() : muted());
            }
        });
        list.add(bar, BorderLayout.NORTH);
        list.add(createScrollPane(table), BorderLayout.CENTER);
        list.add(createFooter("Assignments save automatically.", "Delete selected", table, assignmentModel, false), BorderLayout.SOUTH);
        page.setBorder(new EmptyBorder(20, 0, 0, 0));
        page.add(form, BorderLayout.NORTH);
        page.add(list, BorderLayout.CENTER);
        return page;
    }

    private JPanel createSessionsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        JPanel form = cardPanel();
        form.setLayout(new BorderLayout(0, 14));
        JComponent formTitle = sectionHeader("Schedule a study session", "Choose what to study, the work type, date, and start time.");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = fieldConstraints();
        addField(fields, c, 0, "STUDYING", sessionTitleField, 1.4);
        addField(fields, c, 1, "MODULE", sessionModuleField, 1.0);
        addField(fields, c, 2, "WORK TYPE", sessionTypeBox, 0.9);
        addField(fields, c, 3, "DATE  ·  DD/MM/YY (OPTIONAL)", sessionDateField, 1.0);
        addField(fields, c, 4, "TIME  ·  HH:MM (OPTIONAL)", sessionTimeField, 0.8);
        JButton addSessionButton = new JButton("Schedule session");
        styleButton(addSessionButton, true);
        addSessionButton.addActionListener(e -> addSession());
        c.gridx = 5; c.gridy = 0; c.weightx = 0; c.insets = new Insets(20, 0, 0, 0);
        fields.add(addSessionButton, c);
        JPanel notesGroup = new JPanel(new BorderLayout(0, 7)); notesGroup.setOpaque(false);
        JLabel notesCaption = new JLabel("NOTES  ·  OPTIONAL"); notesCaption.setFont(new Font("Segoe UI", Font.BOLD, 10)); notesCaption.setForeground(muted());
        sessionNotesField.setFont(new Font("Segoe UI", Font.PLAIN, 14)); sessionNotesField.setForeground(ink()); sessionNotesField.setBackground(inputBackground());
        sessionNotesField.setCaretColor(ink()); sessionNotesField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor()), new EmptyBorder(7, 9, 7, 9)));
        notesGroup.add(notesCaption, BorderLayout.NORTH); notesGroup.add(sessionNotesField, BorderLayout.CENTER);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 6; c.weightx = 1; c.insets = new Insets(14, 0, 0, 0);
        fields.add(notesGroup, c);
        form.add(formTitle, BorderLayout.NORTH);
        form.add(fields, BorderLayout.CENTER);

        JPanel timerCard = cardPanel(); timerCard.setLayout(new BorderLayout(12, 0));
        JPanel timerInfo = new JPanel(); timerInfo.setOpaque(false); timerInfo.setLayout(new BoxLayout(timerInfo, BoxLayout.Y_AXIS));
        timerInfo.add(sectionTitle("Focus timer"));
        timerStatusLabel = new JLabel("Pomodoro intervals are saved to your weekly stats."); timerStatusLabel.setForeground(muted()); timerStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timerInfo.add(timerStatusLabel);
        timerElapsedLabel.setFont(new Font("Segoe UI", Font.BOLD, 22)); timerElapsedLabel.setForeground(accent());
        focusTimerButton = new JButton(activeTimerTitle == null ? "Start study timer" : "Stop & save time"); styleButton(focusTimerButton, true);
        focusTimerButton.addActionListener(e -> { if (activeTimerTitle == null) startFocusTimer(); else stopFocusTimer(); });
        timerCard.add(timerInfo, BorderLayout.CENTER);
        JPanel timerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); timerActions.setOpaque(false);
        timerActions.add(timerElapsedLabel); timerActions.add(focusTimerButton); timerCard.add(timerActions, BorderLayout.EAST);

        JPanel list = cardPanel();
        list.setLayout(new BorderLayout(0, 14));
        JTable table = createTable(sessionModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setCellRenderer(moduleColorRenderer(1));
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setCellRenderer(dateRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(timeRenderer());
        table.getColumnModel().getColumn(5).setPreferredWidth(220);
        JPanel bar = sectionBar("Your study schedule", "Select a row to delete a session");
        list.add(bar, BorderLayout.NORTH);
        list.add(createScrollPane(table), BorderLayout.CENTER);
        list.add(createFooter("Study sessions save automatically.", "Delete selected", table, sessionModel, true), BorderLayout.SOUTH);
        JPanel pomodoroCard = cardPanel();
        pomodoroCard.setLayout(new BorderLayout(0, 6));
        pomodoroCard.add(sectionTitle("Pomodoro technique"), BorderLayout.NORTH);
        JLabel pomodoroHint = new JLabel("Choose the focus and break lengths when you start the study timer. Course Companion will remind you when each interval ends.");
        pomodoroHint.setForeground(muted()); pomodoroHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pomodoroCard.add(pomodoroHint, BorderLayout.CENTER);
        page.setBorder(new EmptyBorder(20, 0, 0, 0));
        JPanel scheduleTop = new JPanel(); scheduleTop.setOpaque(false); scheduleTop.setLayout(new BoxLayout(scheduleTop, BoxLayout.Y_AXIS));
        scheduleTop.add(timerCard); scheduleTop.add(Box.createVerticalStrut(14)); scheduleTop.add(form);
        page.add(scheduleTop, BorderLayout.NORTH);
        page.add(list, BorderLayout.CENTER);
        page.add(pomodoroCard, BorderLayout.SOUTH);
        return page;
    }

    private String pageName(int page) {
        return switch (page) { case 0 -> "assignments"; case 1 -> "sessions"; case 2 -> "rooms"; case 3 -> "calendar"; case 5 -> "stats"; default -> "dashboard"; };
    }

    private void startFocusTimer() {
        JTextField title = new JTextField(22), module = new JTextField(18);
        JSpinner focusMinutes = new JSpinner(new SpinnerNumberModel(pomodoroFocusMinutes, 1, 180, 1));
        JSpinner breakMinutes = new JSpinner(new SpinnerNumberModel(pomodoroBreakMinutes, 1, 60, 1));
        JPanel prompt = new JPanel(new GridLayout(4, 2, 8, 8));
        prompt.add(new JLabel("What are you studying?")); prompt.add(title);
        prompt.add(new JLabel("Module (optional)")); prompt.add(module);
        prompt.add(new JLabel("Focus interval (minutes)")); prompt.add(focusMinutes);
        prompt.add(new JLabel("Break interval (minutes)")); prompt.add(breakMinutes);
        if (JOptionPane.showConfirmDialog(this, prompt, "Start Pomodoro timer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (title.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Enter what you are studying.", "Missing details", JOptionPane.WARNING_MESSAGE); return; }
        activeTimerTitle = title.getText().trim(); activeTimerModule = module.getText().trim();
        pomodoroFocusMinutes = (Integer) focusMinutes.getValue(); pomodoroBreakMinutes = (Integer) breakMinutes.getValue();
        pomodoroBreak = false;
        activeTimerDate = LocalDate.now(); activeTimerStartedAt = System.nanoTime();
        if (timerStatusLabel != null) timerStatusLabel.setText("Focus · " + pomodoroFocusMinutes + " min, then " + pomodoroBreakMinutes + " min break");
        if (focusTimerButton != null) { focusTimerButton.setText("Stop & save time"); styleButton(focusTimerButton, true); }
        if (focusTimer == null) focusTimer = new Timer(1000, e -> updateTimerDisplay());
        focusTimer.start(); updateTimerDisplay();
    }

    private void stopFocusTimer() {
        if (activeTimerTitle == null) return;
        if (focusTimer != null) focusTimer.stop();
        if (!pomodoroBreak) saveCurrentFocusInterval();
        clearPomodoroTimer();
        if (selectedTab == 5) { buildUi(); revalidate(); repaint(); }
    }

    private void saveCurrentFocusInterval() {
        long seconds = Math.max(1, (System.nanoTime() - activeTimerStartedAt) / 1_000_000_000L);
        studyLogModel.addRow(new Object[]{activeTimerDate, activeTimerTitle, activeTimerModule, seconds});
        saveStudyLogs();
    }

    private void clearPomodoroTimer() {
        activeTimerTitle = null; activeTimerModule = null; activeTimerDate = null; pomodoroBreak = false;
        if (focusTimerButton != null) { focusTimerButton.setText("Start study timer"); styleButton(focusTimerButton, true); focusTimerButton.setToolTipText(null); }
        if (timerStatusLabel != null) timerStatusLabel.setText("Pomodoro intervals are saved to your weekly stats.");
        timerElapsedLabel.setText("00:00:00");
    }

    private void updateTimerDisplay() {
        if (activeTimerTitle == null) return;
        long seconds = (System.nanoTime() - activeTimerStartedAt) / 1_000_000_000L;
        int intervalSeconds = (pomodoroBreak ? pomodoroBreakMinutes : pomodoroFocusMinutes) * 60;
        if (timerStatusLabel != null) timerStatusLabel.setText(pomodoroBreak
                ? "Break time · next focus interval is " + pomodoroFocusMinutes + " min"
                : "Focus · " + pomodoroFocusMinutes + " min, then " + pomodoroBreakMinutes + " min break");
        if (seconds >= intervalSeconds) {
            completePomodoroInterval();
            return;
        }
        timerElapsedLabel.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        if (focusTimerButton != null) focusTimerButton.setToolTipText((pomodoroBreak ? "Break for " : "Studying: ") + activeTimerTitle);
    }

    private void completePomodoroInterval() {
        if (focusTimer != null) focusTimer.stop();
        Toolkit.getDefaultToolkit().beep();
        if (!pomodoroBreak) {
            studyLogModel.addRow(new Object[]{activeTimerDate, activeTimerTitle, activeTimerModule, (long) pomodoroFocusMinutes * 60});
            saveStudyLogs();
            pomodoroBreak = true;
            if (timerStatusLabel != null) timerStatusLabel.setText("Break time · next focus interval is " + pomodoroFocusMinutes + " min");
            timerElapsedLabel.setText("00:00:00");
            JOptionPane.showMessageDialog(this, "Focus interval complete. Time for a " + pomodoroBreakMinutes + " minute break.", "Pomodoro reminder", JOptionPane.INFORMATION_MESSAGE);
        } else {
            pomodoroBreak = false;
            if (timerStatusLabel != null) timerStatusLabel.setText("Focus · " + pomodoroFocusMinutes + " min, then " + pomodoroBreakMinutes + " min break");
            timerElapsedLabel.setText("00:00:00");
            JOptionPane.showMessageDialog(this, "Break complete. Ready for another " + pomodoroFocusMinutes + " minute focus interval?", "Pomodoro reminder", JOptionPane.INFORMATION_MESSAGE);
        }
        if (activeTimerTitle != null) {
            activeTimerStartedAt = System.nanoTime();
            if (focusTimerButton != null) focusTimerButton.setToolTipText((pomodoroBreak ? "Break for " : "Studying: ") + activeTimerTitle);
            if (focusTimer == null) focusTimer = new Timer(1000, e -> updateTimerDisplay());
            focusTimer.start();
        }
    }

    private JPanel createWeeklyStatsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 16)); page.setOpaque(false);
        page.setBorder(new EmptyBorder(18, 0, 0, 0));
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        JPanel chartCard = cardPanel(); chartCard.setLayout(new BorderLayout(0, 14));
        long total = 0, max = 0;
        long[] daily = new long[7];
        for (int r = 0; r < studyLogModel.getRowCount(); r++) {
            Object dateValue = studyLogModel.getValueAt(r, 0);
            if (!(dateValue instanceof LocalDate date) || date.isBefore(monday) || date.isAfter(monday.plusDays(6))) continue;
            long seconds = ((Number) studyLogModel.getValueAt(r, 3)).longValue();
            daily[date.getDayOfWeek().getValue() - 1] += seconds; total += seconds;
        }
        for (long seconds : daily) max = Math.max(max, seconds);
        JPanel chart = new JPanel(); chart.setOpaque(false); chart.setLayout(new BoxLayout(chart, BoxLayout.Y_AXIS));
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i); JPanel row = new JPanel(new BorderLayout(12, 0)); row.setOpaque(false);
            JLabel dayLabel = new JLabel(date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + "  " + date.format(DateTimeFormatter.ofPattern("dd/MM")));
            dayLabel.setForeground(ink()); dayLabel.setPreferredSize(new Dimension(95, 28));
            JProgressBar bar = new JProgressBar(0, (int) Math.max(1, max)); bar.setValue((int) Math.min(Integer.MAX_VALUE, daily[i]));
            bar.setForeground(accent()); bar.setBackground(chipBackground()); bar.setBorderPainted(false); bar.setStringPainted(false); bar.setPreferredSize(new Dimension(300, 16));
            JLabel time = new JLabel(formatDuration(daily[i])); time.setForeground(muted()); time.setHorizontalAlignment(SwingConstants.RIGHT); time.setPreferredSize(new Dimension(80, 28));
            row.add(dayLabel, BorderLayout.WEST); row.add(bar, BorderLayout.CENTER); row.add(time, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); chart.add(row); chart.add(Box.createVerticalStrut(8));
        }
        JLabel totalLabel = sectionTitle("This week · " + formatDuration(total));
        JLabel range = new JLabel(monday.format(DISPLAY_DATE) + " – " + monday.plusDays(6).format(DISPLAY_DATE)); range.setForeground(muted()); range.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false); head.add(totalLabel, BorderLayout.WEST); head.add(range, BorderLayout.EAST);
        chartCard.add(head, BorderLayout.NORTH); chartCard.add(chart, BorderLayout.CENTER);

        JTable logTable = createTable(studyLogModel);
        logTable.getColumnModel().getColumn(0).setCellRenderer(dateRenderer());
        logTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() { @Override protected void setValue(Object v) { setText(formatDuration(v instanceof Number n ? n.longValue() : 0)); setForeground(ink()); } });
        logTable.getColumnModel().getColumn(1).setPreferredWidth(220); logTable.getColumnModel().getColumn(2).setPreferredWidth(150); logTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        JPanel logsCard = cardPanel(); logsCard.setLayout(new BorderLayout(0, 12));
        JPanel logsHeader = new JPanel(new BorderLayout(12, 0)); logsHeader.setOpaque(false);
        JPanel logsHeading = new JPanel(); logsHeading.setOpaque(false); logsHeading.setLayout(new BoxLayout(logsHeading, BoxLayout.Y_AXIS));
        logsHeading.add(sectionTitle("Recorded study time"));
        JLabel logsHint = new JLabel("Start and stop the focus timer on the Study Schedule page");
        logsHint.setFont(new Font("Segoe UI", Font.PLAIN, 12)); logsHint.setForeground(muted());
        logsHeading.add(logsHint);
        JButton clearStudyTimeButton = new JButton("Clear study time");
        styleButton(clearStudyTimeButton, false);
        clearStudyTimeButton.setEnabled(studyLogModel.getRowCount() > 0);
        clearStudyTimeButton.addActionListener(e -> clearStudyTime());
        logsHeader.add(logsHeading, BorderLayout.CENTER); logsHeader.add(clearStudyTimeButton, BorderLayout.EAST);
        logsCard.add(logsHeader, BorderLayout.NORTH);
        logsCard.add(createScrollPane(logTable), BorderLayout.CENTER);
        page.add(chartCard, BorderLayout.NORTH); page.add(logsCard, BorderLayout.CENTER); return page;
    }

    private void clearStudyTime() {
        if (studyLogModel.getRowCount() == 0) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Clear all recorded study time? This cannot be undone.",
                "Clear study time", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        studyLogModel.setRowCount(0);
        saveStudyLogs();
        buildUi(); revalidate(); repaint();
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0 min";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60, remainder = minutes % 60;
        return hours + " hr" + (remainder > 0 ? " " + remainder + " min" : "");
    }

    private void checkDeadlineReminders() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastReminderShownDate)) return;
        List<String> dueSoon = new ArrayList<>();
        for (int r = 0; r < assignmentModel.getRowCount(); r++) {
            Object due = assignmentModel.getValueAt(r, 2);
            if (!Boolean.TRUE.equals(assignmentModel.getValueAt(r, 6)) || !(due instanceof LocalDate date)) continue;
            if (!date.isBefore(today) && !date.isAfter(today.plusDays(1))) {
                dueSoon.add(assignmentModel.getValueAt(r, 0) + " · " + (date.equals(today) ? "due today" : "due tomorrow"));
            }
        }
        if (!dueSoon.isEmpty()) {
            lastReminderShownDate = today;
            JOptionPane.showMessageDialog(this, String.join("\n", dueSoon), "Assignment reminders", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private JPanel createAssignmentsWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 12)); workspace.setOpaque(false);
        JPanel titleChip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleChip.setOpaque(true); titleChip.setBackground(chipBackground());
        titleChip.setBorder(new EmptyBorder(7, 13, 7, 13));
        JLabel title = new JLabel("Assignments");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(darkMode ? new Color(220, 232, 255) : new Color(36, 78, 150));
        titleChip.add(title);
        workspace.setBorder(new EmptyBorder(8, 0, 0, 0));
        workspace.add(titleChip, BorderLayout.NORTH);
        workspace.add(createAssignmentsPage(), BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createDashboardPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 7));
        top.setOpaque(false);
        JPanel dashboardTitle = new JPanel(new BorderLayout()); dashboardTitle.setOpaque(false);
        dashboardTitle.add(sectionTitle("Welcome to Course Companion"), BorderLayout.WEST);
        JButton settings = new JButton("⚙  Settings");
        settings.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13)); styleButton(settings, false);
        settings.addActionListener(e -> showSettingsDialog());
        dashboardTitle.add(settings, BorderLayout.EAST);
        top.add(dashboardTitle, BorderLayout.NORTH);
        JLabel intro = new JLabel("Keep assignments, study sessions, deadlines, and weekly study time together in one clear place.");
        intro.setForeground(muted()); intro.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        top.add(intro, BorderLayout.CENTER);
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0)); stats.setOpaque(false);
        stats.add(statCard("Due in the next 7 days", countUpcomingAssignments()));
        stats.add(statCard("Overdue assignments", countOverdueAssignments()));
        stats.add(statCard("Study sessions today", countTodaySessions()));
        stats.setPreferredSize(new Dimension(780, 108));
        stats.setMinimumSize(new Dimension(0, 108));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        JPanel actions = new JPanel(new GridLayout(1, 3, 12, 0)); actions.setOpaque(false);
        actions.add(dashboardAction("Assignments", "Search, filter, and manage your work", 0));
        actions.add(dashboardAction("Study schedule", "Plan theory and practical sessions", 1));
        JButton booking = new JButton("<html><b>Find a library room</b><br>Open SETU room finder  ↗</html>");
        styleButton(booking, false); booking.setHorizontalAlignment(SwingConstants.LEFT);
        booking.addActionListener(e -> navigateTo(2));
        actions.add(booking);
        actions.setPreferredSize(new Dimension(780, 78));
        actions.setMinimumSize(new Dimension(0, 78));
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        JPanel content = new JPanel(); content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(stats);
        content.add(Box.createVerticalStrut(12));
        content.add(actions);
        content.add(Box.createVerticalGlue());
        page.setBorder(new EmptyBorder(14, 0, 0, 0));
        page.add(top, BorderLayout.NORTH); page.add(content, BorderLayout.CENTER);
        return page;
    }

    private JPanel statCard(String title, int value) {
        JPanel card = cardPanel(); card.setLayout(new BorderLayout(0, 7));
        card.setPreferredSize(new Dimension(220, 108));
        card.setMinimumSize(new Dimension(0, 108));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        JLabel number = new JLabel(Integer.toString(value)); number.setFont(new Font("Segoe UI", Font.BOLD, 24)); number.setForeground(accent());
        JLabel caption = new JLabel(title); caption.setFont(new Font("Segoe UI", Font.PLAIN, 12)); caption.setForeground(muted());
        card.add(number, BorderLayout.CENTER); card.add(caption, BorderLayout.SOUTH);
        return card;
    }

    private JButton dashboardAction(String title, String detail, int page) {
        JButton button = new JButton("<html><b>" + title + "</b><br><span style='font-size:10px'>" + detail + "</span></html>");
        styleButton(button, false); button.setHorizontalAlignment(SwingConstants.LEFT);
        button.addActionListener(e -> navigateTo(page));
        return button;
    }

    private int countUpcomingAssignments() {
        LocalDate today = LocalDate.now(); int count = 0;
        for (int r = 0; r < assignmentModel.getRowCount(); r++) {
            Object due = assignmentModel.getValueAt(r, 2);
            if (due instanceof LocalDate date && !date.isBefore(today) && !date.isAfter(today.plusDays(7))) count++;
        }
        return count;
    }
    private int countOverdueAssignments() {
        int count = 0;
        for (int r = 0; r < assignmentModel.getRowCount(); r++) {
            Object due = assignmentModel.getValueAt(r, 2);
            if (due instanceof LocalDate date && date.isBefore(LocalDate.now())) count++;
        }
        return count;
    }
    private int countTodaySessions() {
        int count = 0;
        for (int r = 0; r < sessionModel.getRowCount(); r++) if (LocalDate.now().equals(sessionModel.getValueAt(r, 3))) count++;
        return count;
    }

    private JPanel createCalendarPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14)); page.setOpaque(false);
        JPanel card = cardPanel(); card.setLayout(new BorderLayout(0, 12));
        JPanel monthHeader = new JPanel(new BorderLayout()); monthHeader.setOpaque(false);
        JButton previous = new JButton("‹"); styleButton(previous, false);
        JButton next = new JButton("›"); styleButton(next, false);
        JLabel monthLabel = sectionTitle("");
        JPanel monthControls = new JPanel(new BorderLayout(10, 0)); monthControls.setOpaque(false);
        monthControls.add(previous, BorderLayout.WEST); monthControls.add(monthLabel, BorderLayout.CENTER); monthControls.add(next, BorderLayout.EAST);
        monthHeader.add(monthControls, BorderLayout.WEST);
        JTextArea dayEvents = new JTextArea(5, 40); dayEvents.setEditable(false); dayEvents.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dayEvents.setForeground(ink()); dayEvents.setBackground(inputBackground()); dayEvents.setBorder(new EmptyBorder(10, 12, 10, 12)); dayEvents.setLineWrap(true); dayEvents.setWrapStyleWord(true);
        JPanel grid = new JPanel(new GridLayout(0, 7, 7, 7)); grid.setOpaque(false);
        Runnable renderCalendar = () -> {
            grid.removeAll();
            monthLabel.setText(java.time.Month.of(calendarMonth).getDisplayName(java.time.format.TextStyle.FULL, Locale.UK) + " " + calendarYear);
            for (String day : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
                JLabel label = new JLabel(day, SwingConstants.CENTER); label.setForeground(muted()); label.setFont(new Font("Segoe UI", Font.BOLD, 12)); grid.add(label);
            }
            LocalDate first = LocalDate.of(calendarYear, calendarMonth, 1);
            int offset = first.getDayOfWeek().getValue() - 1;
            for (int i = 0; i < offset; i++) grid.add(new JLabel(""));
            for (int day = 1; day <= first.lengthOfMonth(); day++) {
                LocalDate date = first.withDayOfMonth(day); int events = countEventsOn(date);
                JButton cell = new JButton("<html><center>" + day + (events > 0 ? "<br><font color='" + (darkMode ? "#9dbeff" : "#2f69de") + "'>" + events + (events == 1 ? " item" : " items") + "</font>" : "") + "</center></html>");
                styleButton(cell, date.equals(selectedCalendarDate)); cell.setPreferredSize(new Dimension(72, 52));
                cell.addActionListener(e -> { selectedCalendarDate = date; dayEvents.setText(eventsForDate(date)); updateCount(); });
                grid.add(cell);
            }
            dayEvents.setText(eventsForDate(selectedCalendarDate));
            grid.revalidate(); grid.repaint();
        };
        previous.addActionListener(e -> { LocalDate d = LocalDate.of(calendarYear, calendarMonth, 1).minusMonths(1); calendarYear = d.getYear(); calendarMonth = d.getMonthValue(); renderCalendar.run(); });
        next.addActionListener(e -> { LocalDate d = LocalDate.of(calendarYear, calendarMonth, 1).plusMonths(1); calendarYear = d.getYear(); calendarMonth = d.getMonthValue(); renderCalendar.run(); });
        card.add(monthHeader, BorderLayout.NORTH); card.add(grid, BorderLayout.CENTER);
        JPanel detail = new JPanel(new BorderLayout(0, 7)); detail.setOpaque(false); detail.add(sectionTitle("Selected day"), BorderLayout.NORTH); detail.add(new JScrollPane(dayEvents), BorderLayout.CENTER);
        JPanel contents = new JPanel(new BorderLayout(0, 15)); contents.setOpaque(false); contents.add(card, BorderLayout.CENTER); contents.add(detail, BorderLayout.SOUTH);
        renderCalendar.run(); page.setBorder(new EmptyBorder(20, 0, 0, 0)); page.add(contents, BorderLayout.CENTER); return page;
    }

    private int countEventsOn(LocalDate date) {
        int count = 0;
        for (int r = 0; r < assignmentModel.getRowCount(); r++) if (date.equals(assignmentModel.getValueAt(r, 2))) count++;
        for (int r = 0; r < sessionModel.getRowCount(); r++) if (date.equals(sessionModel.getValueAt(r, 3))) count++;
        return count;
    }
    private String eventsForDate(LocalDate date) {
        StringBuilder result = new StringBuilder(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.UK))).append("\n\n");
        boolean found = false;
        for (int r = 0; r < assignmentModel.getRowCount(); r++) if (date.equals(assignmentModel.getValueAt(r, 2))) { result.append("Assignment due: ").append(assignmentModel.getValueAt(r, 0)).append(" · ").append(assignmentModel.getValueAt(r, 1)).append("\n"); found = true; }
        for (int r = 0; r < sessionModel.getRowCount(); r++) if (date.equals(sessionModel.getValueAt(r, 3))) { result.append("Study session: ").append(sessionModel.getValueAt(r, 0)).append(" · ").append(sessionModel.getValueAt(r, 2)); Object time = sessionModel.getValueAt(r, 4); if (time instanceof LocalTime t) result.append(" at ").append(t.format(TIME_FORMAT)); result.append("\n"); found = true; }
        if (!found) result.append("No assignments or study sessions scheduled.");
        return result.toString();
    }

    private DefaultTableCellRenderer moduleColorRenderer(int column) {
        return new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                String module = String.valueOf(value); setText("  " + module); setFont(getFont().deriveFont(Font.BOLD));
                Color[] palette = darkMode ? new Color[]{new Color(170,205,255),new Color(157,220,190),new Color(238,190,132),new Color(216,180,245),new Color(242,160,174),new Color(145,218,220)} : new Color[]{new Color(37,91,170),new Color(32,119,82),new Color(155,91,28),new Color(116,67,158),new Color(171,59,79),new Color(26,112,122)};
                setForeground(palette[(module.toLowerCase(Locale.ROOT).hashCode() & 0x7fffffff) % palette.length]);
            }
        };
    }

    private JPanel createRoomFinderPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        JPanel filterCard = cardPanel();
        filterCard.setLayout(new BorderLayout(0, 14));
        filterCard.add(sectionHeader("Find a library room", "Browse the next 30 days. SETU's calendar shows live slots and may not release every date that far ahead."), BorderLayout.NORTH);
        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(false);
        GridBagConstraints c = fieldConstraints();
        addField(filters, c, 0, "ROOM TYPE", roomTypeFilter, 1.0);
        addField(filters, c, 1, "CAMPUS", campusFilter, 0.9);
        addField(filters, c, 2, "LIBRARY", libraryFilter, 1.2);
        JButton refresh = new JButton("Show next 30 days");
        styleButton(refresh, true);
        refresh.addActionListener(e -> rebuildRoomRows());
        c.gridx = 3; c.gridy = 0; c.weightx = 0; c.insets = new Insets(20, 0, 0, 0);
        filters.add(refresh, c);
        filterCard.add(filters, BorderLayout.CENTER);

        JPanel list = cardPanel();
        list.setLayout(new BorderLayout(0, 14));
        JPanel bar = sectionBar("Bookable spaces", "Sort by clicking a column heading");
        JTable table = createTable(roomModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(95);
        table.getColumnModel().getColumn(1).setPreferredWidth(125);
        table.getColumnModel().getColumn(2).setPreferredWidth(95);
        table.getColumnModel().getColumn(3).setPreferredWidth(165);
        table.getColumnModel().getColumn(4).setPreferredWidth(170);
        table.getColumnModel().getColumn(5).setPreferredWidth(160);
        table.getColumnModel().getColumn(0).setCellRenderer(dateRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(String.valueOf(value));
                setForeground(accent());
            }
        });
        table.removeColumn(table.getColumnModel().getColumn(6)); // Keep the destination URL out of view.
        rebuildRoomRows();

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        JLabel note = new JLabel("Choose a row to check live availability and book on SETU's website.");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        note.setForeground(muted());
        JButton openButton = new JButton("Check availability on SETU");
        styleButton(openButton, false);
        openButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected < 0) {
                JOptionPane.showMessageDialog(this, "Select a date and room first.", "Choose a room", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int row = table.convertRowIndexToModel(selected);
            openRoomCalendar(roomModel.getValueAt(row, 6).toString());
        });
        footer.add(note, BorderLayout.WEST);
        footer.add(openButton, BorderLayout.EAST);
        list.add(bar, BorderLayout.NORTH);
        list.add(createScrollPane(table), BorderLayout.CENTER);
        list.add(footer, BorderLayout.SOUTH);
        page.setBorder(new EmptyBorder(20, 0, 0, 0));
        page.add(filterCard, BorderLayout.NORTH);
        page.add(list, BorderLayout.CENTER);
        roomTypeFilter.addActionListener(e -> rebuildRoomRows());
        campusFilter.addActionListener(e -> rebuildRoomRows());
        libraryFilter.addActionListener(e -> rebuildRoomRows());
        return page;
    }

    private void rebuildRoomRows() {
        roomModel.setRowCount(0);
        String type = String.valueOf(roomTypeFilter.getSelectedItem());
        String campus = String.valueOf(campusFilter.getSelectedItem());
        String library = String.valueOf(libraryFilter.getSelectedItem());
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 30; day++) {
            LocalDate date = today.plusDays(day);
            for (RoomOption room : ROOM_OPTIONS) {
                if (!"All room types".equals(type) && !room.type().equals(type)) continue;
                if (!"All campuses".equals(campus) && !room.campus().equals(campus)) continue;
                if (!"All libraries".equals(library) && !room.library().equals(library)) continue;
                roomModel.addRow(new Object[]{date, room.type(), room.campus(), room.library(), room.room(), "Check live calendar", room.url()});
            }
        }
        if (selectedTab == 2) updateCount();
    }

    private void openRoomCalendar(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
            else throw new IOException("Opening web links is not supported on this computer.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open SETU's booking site: " + ex.getMessage(),
                    "Room availability", JOptionPane.ERROR_MESSAGE);
        }
    }

    private GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 12);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private JPanel createFooter(String hint, String buttonText, JTable table, DefaultTableModel model, boolean sessions) {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        JLabel storageHint = new JLabel(hint);
        storageHint.setForeground(muted());
        storageHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JButton deleteButton = new JButton(buttonText);
        styleButton(deleteButton, false);
        deleteButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected < 0) {
                JOptionPane.showMessageDialog(this, "Select an item to delete.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            model.removeRow(table.convertRowIndexToModel(selected));
            if (sessions) { saveSessions(); updateCount(); } else { saveAssignments(); updateCount(); }
        });
        storageHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel actions = new JPanel(new GridLayout(sessions ? 1 : 2, sessions ? 1 : 3, 8, 6));
        actions.setOpaque(false);
        if (!sessions) {
            JButton openBriefButton = new JButton("Open brief");
            styleButton(openBriefButton, false);
            openBriefButton.addActionListener(e -> openSelectedBrief(table));
            actions.add(openBriefButton);
            JButton attachBriefButton = new JButton("Attach/change brief");
            styleButton(attachBriefButton, false);
            attachBriefButton.addActionListener(e -> attachBriefToSelected(table));
            actions.add(attachBriefButton);
            JButton removeBriefButton = new JButton("Remove brief");
            styleButton(removeBriefButton, false);
            removeBriefButton.addActionListener(e -> removeSelectedBrief(table));
            actions.add(removeBriefButton);
            JButton changeDateButton = new JButton("Change due date");
            styleButton(changeDateButton, false);
            changeDateButton.addActionListener(e -> changeDueDate(table));
            actions.add(changeDateButton);
        }
        actions.add(deleteButton);
        actions.setAlignmentX(Component.RIGHT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, sessions ? 42 : 88));
        footer.add(actions);
        footer.add(Box.createVerticalStrut(6));
        footer.add(storageHint);
        return footer;
    }

    private void changeDueDate(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select an assignment first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(selected);
        Object existing = assignmentModel.getValueAt(row, 2);
        String initial = existing instanceof LocalDate date ? date.format(DISPLAY_DATE) : "";
        String input = (String) JOptionPane.showInputDialog(this,
                "Enter the new due date as DD/MM/YY. Leave blank to remove the date.",
                "Change due date", JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (input == null) return;
        input = input.trim();
        LocalDate dueDate = null;
        if (!input.isEmpty()) {
            try { dueDate = LocalDate.parse(input, INPUT_DATE); }
            catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Enter the date as DD/MM/YY, for example 15/11/26.",
                        "Check due date", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        assignmentModel.setValueAt(dueDate, row, 2);
        assignmentModel.setValueAt(dueDate == null ? null : daysUntil(dueDate), row, 3);
        saveAssignments();
        checkDeadlineReminders();
    }

    private void openSelectedBrief(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select an assignment first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(selected);
        Object savedPath = assignmentModel.getValueAt(row, 5);
        if (savedPath == null || savedPath.toString().isBlank()) {
            JOptionPane.showMessageDialog(this, "This assignment does not have a brief attached.", "No brief attached", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path path = Path.of(savedPath.toString());
        if (!Files.isRegularFile(path)) {
            JOptionPane.showMessageDialog(this, "The attached brief could not be found:\n" + path,
                    "Brief unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile());
            else throw new IOException("Opening files is not supported on this computer.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open the brief:\n" + e.getMessage(),
                    "Could not open brief", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void attachBriefToSelected(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select an assignment first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path path = selectBriefFile();
        if (path == null) return;
        int row = table.convertRowIndexToModel(selected);
        assignmentModel.setValueAt(path.toString(), row, 5);
        saveAssignments();
    }

    private void removeSelectedBrief(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select an assignment first.", "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(selected);
        assignmentModel.setValueAt("", row, 5);
        saveAssignments();
    }

    private void chooseBrief() {
        Path path = selectBriefFile();
        if (path != null) {
            briefField.setText(path.toString());
            briefField.setToolTipText(path.toString());
        }
    }

    private Path selectBriefFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select this assignment's brief");
        chooser.setFileFilter(new FileNameExtensionFilter("Briefs and documents (PDF, Word, text)", "pdf", "doc", "docx", "rtf", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toPath().toAbsolutePath();
        }
        return null;
    }

    private void clearBrief() {
        briefField.setText("");
        briefField.setToolTipText("No brief selected");
    }

    private JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setForeground(ink());
        table.setBackground(cardBackground());
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(selectionBackground());
        table.setSelectionForeground(ink());
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setUI(new BasicTableHeaderUI());
        table.getTableHeader().setForeground(muted());
        table.getTableHeader().setBackground(tableHeaderBackground());
        table.getTableHeader().setOpaque(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private JScrollPane createScrollPane(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(cardBackground());
        scroll.getViewport().setBackground(cardBackground());
        return scroll;
    }

    private JPanel sectionBar(String title, String note) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(sectionTitle(title), BorderLayout.WEST);
        JLabel hint = new JLabel(note);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(muted());
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    private JComponent sectionHeader(String title, String description) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(sectionTitle(title));
        JLabel detail = new JLabel(description);
        detail.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        detail.setForeground(muted());
        detail.setBorder(new EmptyBorder(4, 1, 0, 0));
        header.add(detail);
        return header;
    }

    private JPanel createMoodleFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton moodleLink = new JButton("Open Moodle  ↗");
        moodleLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
        moodleLink.setForeground(accent());
        moodleLink.setBorder(new EmptyBorder(8, 10, 0, 0));
        moodleLink.setContentAreaFilled(false);
        moodleLink.setFocusPainted(false);
        moodleLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        moodleLink.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create("https://moodle.setu.ie"));
                else throw new IOException("Opening web links is not supported on this computer.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open Moodle: " + ex.getMessage(),
                        "Moodle link", JOptionPane.ERROR_MESSAGE);
            }
        });
        footer.add(moodleLink);
        return footer;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(ink());
        return label;
    }

    private DefaultTableCellRenderer dateRenderer() {
        return new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(value instanceof LocalDate d ? d.format(DISPLAY_DATE) : "Not set");
                setForeground(value == null ? muted() : ink());
            }
        };
    }

    private DefaultTableCellRenderer timeRenderer() {
        return new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText(value instanceof LocalTime t ? t.format(TIME_FORMAT) : "Not set");
                setForeground(value == null ? muted() : ink());
            }
        };
    }

    private DefaultTableCellRenderer countdownRenderer() {
        return new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (!(value instanceof Integer days)) { setText("No date yet"); setForeground(muted()); return; }
                setText(days < 0 ? (-days) + (days == -1 ? " day overdue" : " days overdue")
                        : days == 0 ? "Due today" : days + (days == 1 ? " day" : " days"));
                setForeground(days < 0 ? (darkMode ? new Color(240, 119, 119) : new Color(183, 56, 56)) : days == 0 ? (darkMode ? new Color(235, 185, 86) : new Color(164, 107, 20)) : ink());
            }
        };
    }

    private DefaultTableCellRenderer priorityRenderer() {
        return new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText("  " + value + "  ");
                setFont(getFont().deriveFont(Font.BOLD, 12f));
                setForeground("High".equals(value) ? (darkMode ? new Color(240, 119, 119) : new Color(183, 56, 56))
                        : "Medium".equals(value) ? (darkMode ? new Color(235, 185, 86) : new Color(164, 107, 20))
                        : (darkMode ? new Color(108, 202, 157) : new Color(49, 126, 91)));
            }
        };
    }

    private Color appBackground() { return darkMode ? new Color(15, 20, 28) : new Color(246, 248, 251); }
    private Color sidebarBackground() { return darkMode ? new Color(19, 26, 36) : new Color(238, 242, 247); }
    private Color cardBackground() { return darkMode ? new Color(27, 36, 49) : Color.WHITE; }
    private Color inputBackground() { return darkMode ? new Color(10, 13, 18) : Color.WHITE; }
    private Color ink() { return darkMode ? new Color(250, 252, 255) : new Color(31, 42, 55); }
    private Color muted() { return darkMode ? new Color(213, 222, 234) : new Color(105, 117, 132); }
    private Color accent() { return darkMode ? new Color(157, 190, 255) : new Color(47, 105, 222); }
    private Color borderColor() { return darkMode ? new Color(63, 77, 96) : new Color(229, 233, 239); }
    private Color chipBackground() { return darkMode ? new Color(37, 49, 66) : new Color(230, 238, 253); }
    private Color selectionBackground() { return darkMode ? new Color(47, 66, 92) : new Color(232, 240, 252); }
    private Color tableHeaderBackground() { return darkMode ? new Color(33, 43, 58) : new Color(248, 250, 252); }

    private final class ThemeSwitch extends JToggleButton {
        ThemeSwitch() {
            setPreferredSize(new Dimension(34, 20));
            setMinimumSize(new Dimension(34, 20));
            setMaximumSize(new Dimension(34, 20));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(null);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            int trackY = 2;
            int trackH = h - 4;
            g.setColor(isSelected() ? accent() : (darkMode ? new Color(66, 76, 91) : new Color(196, 205, 217)));
            g.fillRoundRect(1, trackY, getWidth() - 2, trackH, trackH, trackH);
            int knob = trackH - 4;
            int x = isSelected() ? getWidth() - knob - 4 : 4;
            g.setColor(Color.WHITE);
            g.fillOval(x, trackY + 2, knob, knob);
            g.dispose();
        }
    }

    private static boolean loadDarkMode() {
        if (!Files.exists(SETTINGS_FILE)) return true;
        try { return Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8).trim().equalsIgnoreCase("dark"); }
        catch (IOException ignored) { return true; }
    }

    private void saveDarkMode() {
        try { Files.writeString(SETTINGS_FILE, darkMode ? "dark" : "light", StandardCharsets.UTF_8); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not save the theme preference: " + e.getMessage(), "Settings", JOptionPane.WARNING_MESSAGE); }
    }

    private static boolean loadTransitionsEnabled() {
        try { return !Files.exists(MOTION_SETTINGS_FILE) || Files.readString(MOTION_SETTINGS_FILE, StandardCharsets.UTF_8).trim().equalsIgnoreCase("on"); }
        catch (IOException ignored) { return true; }
    }

    private void saveTransitionsEnabled() {
        try { Files.writeString(MOTION_SETTINGS_FILE, transitionsEnabled ? "on" : "off", StandardCharsets.UTF_8); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not save transition settings: " + e.getMessage(), "Settings", JOptionPane.WARNING_MESSAGE); }
    }

    private void showSettingsDialog() {
        JCheckBox darkOption = new JCheckBox("Use dark mode", darkMode);
        JCheckBox motionOption = new JCheckBox("Fade between tabs", transitionsEnabled);
        for (JCheckBox option : List.of(darkOption, motionOption)) {
            option.setOpaque(false); option.setForeground(ink()); option.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        }
        JPanel body = new JPanel(); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(cardBackground()); body.setBorder(new EmptyBorder(18, 20, 14, 20));
        body.add(sectionHeader("Appearance", "Choose how Course Companion looks and moves."));
        body.add(Box.createVerticalStrut(18)); body.add(darkOption); body.add(Box.createVerticalStrut(8)); body.add(motionOption);
        JButton cancel = new JButton("Cancel"); styleButton(cancel, false);
        JButton save = new JButton("Save settings"); styleButton(save, true);
        JButton export = new JButton("Export data…"); styleButton(export, false);
        JPanel actionRow = new JPanel(new BorderLayout()); actionRow.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false); actions.add(cancel); actions.add(save);
        actionRow.add(export, BorderLayout.WEST); actionRow.add(actions, BorderLayout.EAST);
        JLabel version = new JLabel("Course Companion  ·  " + APP_VERSION);
        version.setFont(new Font("Segoe UI", Font.PLAIN, 11)); version.setForeground(muted());
        JPanel dialogFooter = new JPanel(new BorderLayout(0, 8)); dialogFooter.setOpaque(false);
        dialogFooter.add(actionRow, BorderLayout.NORTH); dialogFooter.add(version, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(0, 8)); content.setBackground(cardBackground());
        content.add(body, BorderLayout.CENTER); content.add(dialogFooter, BorderLayout.SOUTH);
        JDialog dialog = new JDialog(this, "Course Companion settings", true);
        dialog.setContentPane(content); dialog.setSize(410, 275); dialog.setResizable(false); dialog.setLocationRelativeTo(this);
        cancel.addActionListener(e -> dialog.dispose());
        export.addActionListener(e -> exportData());
        save.addActionListener(e -> {
            boolean themeChanged = darkMode != darkOption.isSelected();
            darkMode = darkOption.isSelected(); transitionsEnabled = motionOption.isSelected();
            saveDarkMode(); saveTransitionsEnabled(); dialog.dispose();
            if (themeChanged) { buildUi(); revalidate(); repaint(); }
        });
        dialog.getRootPane().setDefaultButton(save); dialog.setVisible(true);
    }

    private void exportData() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Course Companion data");
        chooser.setSelectedFile(Path.of("Course-Companion-export-" + LocalDate.now() + ".json").toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        if (!destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) destination = destination.resolveSibling(destination.getFileName() + ".json");
        StringBuilder json = new StringBuilder("{\n  \"assignments\": [");
        for (int r = 0; r < assignmentModel.getRowCount(); r++) {
            if (r > 0) json.append(',');
            Object due = assignmentModel.getValueAt(r, 2);
            json.append("\n    {\"title\":\"").append(jsonEscape(assignmentModel.getValueAt(r, 0).toString()))
                    .append("\",\"module\":\"").append(jsonEscape(assignmentModel.getValueAt(r, 1).toString()))
                    .append("\",\"dueDate\":").append(due == null ? "null" : "\"" + due + "\"")
                    .append(",\"priority\":\"").append(jsonEscape(assignmentModel.getValueAt(r, 4).toString()))
                    .append("\",\"briefPath\":\"").append(jsonEscape(String.valueOf(assignmentModel.getValueAt(r, 5))))
                    .append("\",\"reminderEnabled\":").append(Boolean.TRUE.equals(assignmentModel.getValueAt(r, 6))).append('}');
        }
        json.append("\n  ],\n  \"studySessions\": [");
        for (int r = 0; r < sessionModel.getRowCount(); r++) {
            if (r > 0) json.append(',');
            Object date = sessionModel.getValueAt(r, 3), time = sessionModel.getValueAt(r, 4);
            json.append("\n    {\"studying\":\"").append(jsonEscape(sessionModel.getValueAt(r, 0).toString()))
                    .append("\",\"module\":\"").append(jsonEscape(sessionModel.getValueAt(r, 1).toString()))
                    .append("\",\"workType\":\"").append(jsonEscape(sessionModel.getValueAt(r, 2).toString()))
                    .append("\",\"date\":").append(date == null ? "null" : "\"" + date + "\"")
                    .append(",\"time\":").append(time == null ? "null" : "\"" + time + "\"")
                    .append(",\"notes\":\"").append(jsonEscape(sessionModel.getValueAt(r, 5).toString())).append("\"}");
        }
        json.append("\n  ],\n  \"studyTimeLogs\": [");
        for (int r = 0; r < studyLogModel.getRowCount(); r++) {
            if (r > 0) json.append(',');
            json.append("\n    {\"date\":\"").append(studyLogModel.getValueAt(r, 0))
                    .append("\",\"studying\":\"").append(jsonEscape(studyLogModel.getValueAt(r, 1).toString()))
                    .append("\",\"module\":\"").append(jsonEscape(studyLogModel.getValueAt(r, 2).toString()))
                    .append("\",\"seconds\":").append(studyLogModel.getValueAt(r, 3)).append('}');
        }
        json.append("\n  ]\n}\n");
        try { Files.writeString(destination, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); JOptionPane.showMessageDialog(this, "Your Course Companion data was exported.", "Export complete", JOptionPane.INFORMATION_MESSAGE); }
        catch (IOException e) { showStorageError("Could not export Course Companion data", e); }
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        return out.toString();
    }

    private final class SlideGlassPane extends JComponent {
        private static final long FADE_OUT_MS = 190;
        private static final long FADE_IN_MS = 240;
        private BufferedImage frame;
        private final Rectangle area;
        private final Color background;
        private final Timer timer;
        private long startedAt;
        private long duration;
        private boolean fadingIn;
        private Runnable finished;

        SlideGlassPane(BufferedImage outgoing, Rectangle area, Color background) {
            this.frame = outgoing;
            this.area = area;
            this.background = background;
            setOpaque(false);
            setDoubleBuffered(true);
            timer = new Timer(16, e -> {
                if ((System.nanoTime() - startedAt) / 1_000_000L >= duration) {
                    ((Timer) e.getSource()).stop();
                    repaint();
                    if (finished != null) finished.run();
                } else repaint();
            });
        }

        void fadeOut(Runnable finished) { startPhase(false, FADE_OUT_MS, finished); }
        void fadeIn(BufferedImage incoming, Runnable finished) {
            frame = incoming;
            startPhase(true, FADE_IN_MS, finished);
        }
        private void startPhase(boolean fadeIn, long duration, Runnable finished) {
            this.fadingIn = fadeIn;
            this.duration = duration;
            this.finished = finished;
            startedAt = System.nanoTime();
            timer.start();
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (frame == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double p = Math.min(1.0, (System.nanoTime() - startedAt) / (duration * 1_000_000.0));
            double eased = p * p * (3 - 2 * p);
            float alpha = (float) (fadingIn ? eased : 1.0 - eased);
            g.clipRect(area.x, area.y, area.width, area.height);
            g.setColor(background);
            g.fillRect(area.x, area.y, area.width, area.height);
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g.drawImage(frame, area.x, area.y, area.width, area.height, null);
            g.dispose();
        }
    }

    private final class NavGlyph implements Icon {
        private final int page;
        private final Color color;
        NavGlyph(int page, Color color) { this.page = page; this.color = color; }
        public int getIconWidth() { return 19; }
        public int getIconHeight() { return 19; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y); g.setColor(color); g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (page == 4) { // dashboard tiles
                g.drawRoundRect(2, 2, 6, 6, 2, 2); g.drawRoundRect(11, 2, 6, 6, 2, 2);
                g.drawRoundRect(2, 11, 6, 6, 2, 2); g.drawRoundRect(11, 11, 6, 6, 2, 2);
            } else if (page == 0) { // open module book
                g.drawLine(9, 4, 9, 16); g.drawLine(9, 5, 4, 3); g.drawLine(4, 3, 2, 4); g.drawLine(2, 4, 2, 15); g.drawLine(2, 15, 9, 17);
                g.drawLine(9, 5, 14, 3); g.drawLine(14, 3, 17, 4); g.drawLine(17, 4, 17, 15); g.drawLine(17, 15, 9, 17);
            } else if (page == 1) { // study clock
                g.drawOval(2, 2, 15, 15); g.drawLine(9, 5, 9, 10); g.drawLine(9, 10, 13, 12);
            } else if (page == 2) { // room / doorway
                g.drawRoundRect(3, 2, 12, 15, 2, 2); g.drawLine(6, 17, 6, 5); g.drawLine(6, 5, 13, 5); g.drawLine(13, 5, 13, 17); g.fillOval(10, 10, 1, 1);
            } else if (page == 5) { // weekly study stats
                g.drawLine(2, 17, 18, 17); g.drawLine(4, 15, 4, 10); g.drawLine(8, 15, 8, 6); g.drawLine(12, 15, 12, 8); g.drawLine(16, 15, 16, 3);
            } else { // calendar
                g.drawRoundRect(2, 3, 15, 14, 2, 2); g.drawLine(2, 7, 17, 7); g.drawLine(6, 1, 6, 5); g.drawLine(13, 1, 13, 5);
                g.drawLine(6, 10, 6, 11); g.drawLine(10, 10, 10, 11); g.drawLine(14, 10, 14, 11); g.drawLine(6, 14, 6, 15);
            }
            g.dispose();
        }
    }

    private static BufferedImage createAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(31, 79, 171));
        g.fill(new RoundRectangle2D.Float(2, 2, 60, 60, 16, 16));
        g.setColor(new Color(55, 119, 231));
        g.fill(new RoundRectangle2D.Float(2, 2, 60, 54, 16, 16));
        g.setColor(new Color(255, 255, 255, 238));
        g.fillRoundRect(14, 11, 34, 41, 5, 5);
        g.setColor(new Color(210, 222, 242));
        g.fillRect(18, 16, 3, 31);
        g.setColor(new Color(123, 151, 194));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(25, 22, 41, 22);
        g.drawLine(25, 28, 41, 28);
        g.drawLine(25, 34, 37, 34);
        g.setColor(new Color(245, 181, 72));
        g.fillOval(35, 35, 24, 24);
        g.setColor(new Color(116, 74, 16));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(35, 35, 24, 24);
        g.drawLine(47, 41, 47, 47);
        g.drawLine(47, 47, 52, 50);
        g.dispose();
        return image;
    }

    private JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(cardBackground());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()), new EmptyBorder(18, 20, 18, 20)));
        return panel;
    }

    private void styleFilterCombo(JComboBox<String> combo) {
        Color fieldBackground = darkMode ? Color.BLACK : inputBackground();
        Color fieldForeground = darkMode ? Color.WHITE : ink();
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new BasicArrowButton(SwingConstants.SOUTH, fieldBackground, borderColor(), fieldForeground, muted());
                arrow.setBackground(fieldBackground); arrow.setForeground(fieldForeground); arrow.setOpaque(true);
                arrow.setBorder(BorderFactory.createEmptyBorder()); return arrow;
            }
        });
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setForeground(fieldForeground); combo.setBackground(fieldBackground); combo.setOpaque(true);
        combo.setBorder(BorderFactory.createLineBorder(borderColor()));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setOpaque(true); setForeground(fieldForeground);
                setBackground(selected ? selectionBackground() : fieldBackground);
                setBorder(new EmptyBorder(4, 7, 4, 7)); return this;
            }
        });
    }

    private void addField(JPanel panel, GridBagConstraints c, int x, String label, JComponent input, double weight) {
        addFieldAt(panel, c, x, 0, label, input, weight);
    }

    private void addFieldAt(JPanel panel, GridBagConstraints c, int x, int y, String label, JComponent input, double weight) {
        JPanel group = new JPanel(new BorderLayout(0, 7));
        group.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setFont(new Font("Segoe UI", Font.BOLD, 10));
        caption.setForeground(muted());
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setForeground(ink());
        input.setBackground(inputBackground());
        input.setOpaque(true);
        if (input instanceof JTextField field) field.setCaretColor(ink());
        if (input instanceof JComboBox<?> combo) {
            combo.setUI(new BasicComboBoxUI() {
                @Override protected JButton createArrowButton() {
                    JButton arrow = new BasicArrowButton(SwingConstants.SOUTH, inputBackground(), borderColor(), ink(), muted());
                    arrow.setBackground(inputBackground());
                    arrow.setForeground(ink());
                    arrow.setOpaque(true);
                    arrow.setBorder(BorderFactory.createEmptyBorder());
                    return arrow;
                }
            });
            combo.setBackground(inputBackground());
            combo.setForeground(darkMode ? Color.WHITE : ink());
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                        boolean selected, boolean focused) {
                    super.getListCellRendererComponent(list, value, index, selected, focused);
                    setOpaque(true);
                    setForeground(darkMode ? Color.WHITE : ink());
                    setBackground(selected ? selectionBackground() : inputBackground());
                    setBorder(new EmptyBorder(4, 7, 4, 7));
                    return this;
                }
            });
            combo.setBorder(BorderFactory.createLineBorder(borderColor()));
        }
        input.setPreferredSize(new Dimension(100, 38));
        if (input instanceof JTextField field) field.setMargin(new Insets(5, 9, 5, 9));
        group.add(caption, BorderLayout.NORTH);
        group.add(input, BorderLayout.CENTER);
        c.gridx = x; c.gridy = y; c.weightx = weight; c.gridwidth = 1;
        panel.add(group, c);
    }

    private void styleButton(JButton button, boolean primary) {
        button.setUI(new BasicButtonUI());
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        if (!primary) {
            button.setBackground(cardBackground());
            button.setForeground(ink());
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor()), new EmptyBorder(9, 14, 9, 14)));
        }
        if (primary) {
            button.setBackground(accent());
            button.setForeground(Color.WHITE);
            button.setBorderPainted(false);
            button.setBorder(new EmptyBorder(10, 15, 10, 15));
        }
    }

    private void addAssignment() {
        String title = titleField.getText().trim();
        String module = moduleField.getText().trim();
        if (title.isEmpty() || module.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter an assignment name and module.", "Missing details", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalDate dueDate = null;
        if (!dateField.getText().trim().isEmpty()) {
            try { dueDate = LocalDate.parse(dateField.getText().trim(), INPUT_DATE); }
            catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Enter the due date as DD/MM/YY, for example 15/11/26, or leave it blank.", "Check due date", JOptionPane.WARNING_MESSAGE);
                dateField.requestFocusInWindow();
                return;
            }
        }
        String briefPath = briefField.getText().trim();
        assignmentModel.addRow(new Object[]{title, module, dueDate, dueDate == null ? null : daysUntil(dueDate), priorityBox.getSelectedItem(), briefPath, assignmentReminderCheck.isSelected()});
        saveAssignments();
        updateCount();
        titleField.setText(""); moduleField.setText(""); dateField.setText(""); priorityBox.setSelectedIndex(0); clearBrief(); assignmentReminderCheck.setSelected(false);
        checkDeadlineReminders();
        titleField.requestFocusInWindow();
    }

    private void addSession() {
        String title = sessionTitleField.getText().trim();
        String module = sessionModuleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter what you plan to study.", "Missing details", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalDate date = null;
        String dateText = sessionDateField.getText().trim();
        if (!dateText.isEmpty()) {
            try { date = LocalDate.parse(dateText, INPUT_DATE); }
            catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Enter the session date as DD/MM/YY, for example 15/11/26, or leave it blank.", "Check date", JOptionPane.WARNING_MESSAGE);
                sessionDateField.requestFocusInWindow(); return;
            }
        }
        LocalTime time = null;
        String timeText = sessionTimeField.getText().trim();
        if (!timeText.isEmpty()) {
            try { time = LocalTime.parse(timeText, TIME_FORMAT); }
            catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Enter the start time in 24-hour format, such as 14:30, or leave it blank.", "Check time", JOptionPane.WARNING_MESSAGE);
                sessionTimeField.requestFocusInWindow(); return;
            }
        }
        sessionModel.addRow(new Object[]{title, module.isEmpty() ? "—" : module, sessionTypeBox.getSelectedItem(), date, time, sessionNotesField.getText().trim()});
        saveSessions();
        updateCount();
        sessionTitleField.setText(""); sessionModuleField.setText(""); sessionDateField.setText(""); sessionTimeField.setText(""); sessionNotesField.setText("");
        sessionTypeBox.setSelectedIndex(0);
        sessionTitleField.requestFocusInWindow();
    }

    private static int daysUntil(LocalDate date) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    private void updateCount() {
        if (selectedTab == 5) { countLabel.setText(formatDuration(weeklyStudySeconds()) + " this week"); return; }
        int n;
        String noun;
        if (selectedTab == 0) {
            n = assignmentModel.getRowCount(); noun = " assignment";
        } else if (selectedTab == 1) {
            n = sessionModel.getRowCount(); noun = " session";
        } else if (selectedTab == 2) {
            n = roomModel.getRowCount(); noun = " room/date match";
        } else if (selectedTab == 3) {
            n = countEventsOn(selectedCalendarDate); noun = " calendar item";
        } else {
            n = countUpcomingAssignments(); noun = " upcoming deadline";
        }
        countLabel.setText(n + (n == 1 ? noun : noun + "s"));
    }

    private void loadAssignments() {
        if (!Files.exists(DATA_FILE)) return;
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 4) continue;
                try {
                    LocalDate due = p[2].isBlank() ? null : LocalDate.parse(p[2]);
                    String briefPath = p.length > 4 && !p[4].isBlank() ? unescape(p[4]) : "";
                    boolean remind = p.length > 5 && Boolean.parseBoolean(p[5]);
                    assignmentModel.addRow(new Object[]{unescape(p[0]), unescape(p[1]), due, due == null ? null : daysUntil(due), unescape(p[3]), briefPath, remind});
                } catch (RuntimeException ignored) { /* Ignore malformed saved rows. */ }
            }
        } catch (IOException e) { showStorageError("Could not read saved assignments", e); }
    }

    private void saveAssignments() {
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < assignmentModel.getRowCount(); r++) {
            Object due = assignmentModel.getValueAt(r, 2);
            lines.add(escape(assignmentModel.getValueAt(r, 0).toString()) + "\t"
                    + escape(assignmentModel.getValueAt(r, 1).toString()) + "\t"
                    + (due == null ? "" : due) + "\t"
                    + escape(assignmentModel.getValueAt(r, 4).toString()) + "\t"
                    + escape(assignmentModel.getValueAt(r, 5) == null ? "" : assignmentModel.getValueAt(r, 5).toString()) + "\t"
                    + Boolean.TRUE.equals(assignmentModel.getValueAt(r, 6)));
        }
        writeLines(DATA_FILE, lines, "Could not save assignments");
    }

    private void loadSessions() {
        if (!Files.exists(SESSION_FILE)) return;
        try {
            for (String line : Files.readAllLines(SESSION_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length != 5 && p.length != 6) continue;
                try {
                    // Earlier versions saved date, time and duration without a work type.
                    LocalDate legacyDate = null;
                    try { legacyDate = LocalDate.parse(p[2]); } catch (RuntimeException ignored) { }
                    if (legacyDate != null) {
                        sessionModel.addRow(new Object[]{unescape(p[0]), unescape(p[1]), "Not set", legacyDate, LocalTime.parse(p[3]), ""});
                } else {
                        LocalDate date = p[3].isBlank() ? null : LocalDate.parse(p[3]);
                        LocalTime time = p[4].isBlank() ? null : LocalTime.parse(p[4]);
                    String notes = p.length > 5 ? unescape(p[5]) : "";
                    sessionModel.addRow(new Object[]{unescape(p[0]), unescape(p[1]), unescape(p[2]), date, time, notes});
                    }
                } catch (RuntimeException ignored) { /* Ignore malformed saved sessions. */ }
            }
        } catch (IOException e) { showStorageError("Could not read saved study sessions", e); }
    }

    private void loadStudyLogs() {
        if (!Files.exists(STUDY_LOG_FILE)) return;
        try {
            for (String line : Files.readAllLines(STUDY_LOG_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1); if (p.length != 4) continue;
                try { studyLogModel.addRow(new Object[]{LocalDate.parse(p[0]), unescape(p[1]), unescape(p[2]), Long.parseLong(p[3])}); }
                catch (RuntimeException ignored) { }
            }
        } catch (IOException e) { showStorageError("Could not read saved study time", e); }
    }

    private void saveStudyLogs() {
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < studyLogModel.getRowCount(); r++) {
            lines.add(studyLogModel.getValueAt(r, 0) + "\t" + escape(studyLogModel.getValueAt(r, 1).toString()) + "\t"
                    + escape(studyLogModel.getValueAt(r, 2).toString()) + "\t" + studyLogModel.getValueAt(r, 3));
        }
        writeLines(STUDY_LOG_FILE, lines, "Could not save study time");
    }

    private long weeklyStudySeconds() {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY); long total = 0;
        for (int r = 0; r < studyLogModel.getRowCount(); r++) {
            Object date = studyLogModel.getValueAt(r, 0);
            if (date instanceof LocalDate d && !d.isBefore(monday) && !d.isAfter(monday.plusDays(6))) total += ((Number) studyLogModel.getValueAt(r, 3)).longValue();
        }
        return total;
    }

    private void saveSessions() {
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < sessionModel.getRowCount(); r++) {
            lines.add(escape(sessionModel.getValueAt(r, 0).toString()) + "\t"
                    + escape(sessionModel.getValueAt(r, 1).toString()) + "\t"
                    + escape(sessionModel.getValueAt(r, 2).toString()) + "\t"
                    + (sessionModel.getValueAt(r, 3) == null ? "" : sessionModel.getValueAt(r, 3)) + "\t"
                    + (sessionModel.getValueAt(r, 4) == null ? "" : sessionModel.getValueAt(r, 4)) + "\t"
                    + escape(sessionModel.getValueAt(r, 5) == null ? "" : sessionModel.getValueAt(r, 5).toString()));
        }
        writeLines(SESSION_FILE, lines, "Could not save study sessions");
    }

    private void writeLines(Path path, List<String> lines, String error) {
        try { Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException e) { showStorageError(error, e); }
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                out.append(next == 't' ? '\t' : next == 'n' ? '\n' : next);
            } else out.append(ch);
        }
        return out.toString();
    }

    private void showStorageError(String message, IOException e) {
        JOptionPane.showMessageDialog(this, message + ":\n" + e.getMessage(), "Storage error", JOptionPane.ERROR_MESSAGE);
    }

    private static void exportAppIcon(Path destination) throws IOException {
        ByteArrayOutputStream pngBuffer = new ByteArrayOutputStream();
        ImageIO.write(createAppIcon(), "png", pngBuffer);
        byte[] png = pngBuffer.toByteArray();
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(destination))) {
            writeLittleEndian(out, 0, 2); // reserved
            writeLittleEndian(out, 1, 2); // icon resource
            writeLittleEndian(out, 1, 2); // one image
            out.writeByte(64); out.writeByte(64); out.writeByte(0); out.writeByte(0);
            writeLittleEndian(out, 1, 2); // color planes
            writeLittleEndian(out, 32, 2); // bits per pixel
            writeLittleEndian(out, png.length, 4);
            writeLittleEndian(out, 22, 4); // image data offset
            out.write(png);
        }
    }

    private static void writeLittleEndian(DataOutputStream out, int value, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) out.writeByte((value >>> (8 * i)) & 0xff);
    }

    public static void main(String[] args) {
        if (args.length == 2 && "--export-icon".equals(args[0])) {
            try { exportAppIcon(Path.of(args[1])); }
            catch (IOException e) { System.err.println("Could not create app icon: " + e.getMessage()); System.exit(1); }
            return;
        }
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new AssignmentTracker().setVisible(true));
    }
}

