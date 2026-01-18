import com.seedfinding.mccore.rand.seed.StructureSeed;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Box;
import nl.jellejurre.seedchecker.SeedChecker;
import nl.jellejurre.seedchecker.SeedCheckerDimension;
import nl.jellejurre.seedchecker.TargetState;
import nl.kallestruik.noisesampler.minecraft.NoiseColumnSampler;
import nl.kallestruik.noisesampler.minecraft.NoiseParameterKey;
import nl.kallestruik.noisesampler.minecraft.Xoroshiro128PlusPlusRandom;
import nl.kallestruik.noisesampler.minecraft.noise.LazyDoublePerlinNoiseSampler;
import nl.kallestruik.noisesampler.minecraft.util.MathHelper;
import nl.kallestruik.noisesampler.minecraft.util.Util;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class CavefinderGUI extends JFrame {
    static {
        try {
            System.out.println("CavefinderGUI static initialization starting...");
            SeedCheckerInitializer.initialize();
        } catch (Exception e) {
            System.err.println("CavefinderGUI static initialization warning: " + e.getMessage());
        }
    }
    enum Language {
        CHINESE("中文"),
        ENGLISH("English");
        private final String displayName;
        Language(String displayName) {
            this.displayName = displayName;
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
    enum ParameterType {
        TEMPERATURE, HUMIDITY, EROSION, RIDGE, ENTRANCE, CHEESE, CONTINENTALNESS, AQUIFER
    }
    enum ConditionType {
        BETWEEN, GREATER_THAN, LESS_THAN, NOT_IN_RANGE, ABS_IN_RANGE, ABS_NOT_IN_RANGE;
        public String getDisplayName(Language lang) {
            return LanguageResources.getConditionTypeName(this, lang);
        }
    }
    // Language management
    private Language currentLanguage = Language.CHINESE; // Default to Chinese
    private JComboBox<Language> languageComboBox;
    // UI component references for text updates
    private JLabel languageLabel;
    private JLabel caveDepthLabel;
    private JLabel threadCountLabel;
    private JLabel xCoordinateLabel;
    private JLabel zCoordinateLabel;
    private JLabel segmentSizeLabel;
    private JLabel segmentSizeHintLabel;
    private JLabel heightTypeLabel;
    private JLabel rangeHeightTypeLabel;
    private JLabel rangeCoordinatesLabel;
    private JLabel startSeedLabel;
    private JLabel endSeedLabel;
    private JLabel seedListLabel;
    private JLabel exportPathLabel;
    private JPanel heightCheckOptionsPanel;
    private JPanel filterModePanel;
    private JPanel searchModePanel;
    private JPanel seedTypePanel;
    private JPanel seedInputPanel;
    private JPanel biomeParamsPanel;
    private JPanel logPanel;
    private JComboBox<Integer> depthComboBox;
    private JCheckBox checkHeightCheckBox;
    private JCheckBox bedrockImpossibleCheckBox;
    private JCheckBox entrance1OnlyCheckBox;
    // Height check type selection
    private JRadioButton surfaceHeightRadio;
    private JRadioButton underwaterHeightRadio;
    // Range check options
    private JCheckBox rangeCheckCheckBox;
    private JComboBox<String> rangeHeightTypeComboBox; // 最低高度, 平均高度, 最高高度
    private JTextField rangeCoordinatesField; // Format: "x1 z1 x2 z2"
    private JRadioButton incrementModeRadio;
    private JRadioButton structureSeedRadio;
    private JRadioButton worldSeedRadio;
    private JPanel parameterPanel;
    private final List<ParameterControl> parameterControls;
    private JTextField startSeedField;
    private JTextField endSeedField;
    private JTextArea seedListArea;
    private final JFileChooser fileChooser;
    private JSpinner xCoordinateSpinner;
    private JSpinner zCoordinateSpinner;
    private JSpinner threadCountSpinner;
    private JSpinner segmentSizeSpinner;
    private JTextField exportPathField;
    private JButton startButton;
    private JButton stopButton;
    private JButton loadFileButton;
    private JButton browseExportPathButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    private ExecutorService executor;
    private volatile boolean isRunning = false;
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong totalTasks = new AtomicLong(0);
    private Font customFont;
    private long filteringStartTime = 0;
    private volatile long lastUpdateTime = 0;
    private volatile long lastUpdateCompleted = 0;
    private static final long UPDATE_INTERVAL_MS = 100; // GUI update interval: 100ms
    private static final long UPDATE_INTERVAL_COUNT = 1000; // or update every 1000 tasks
    private String getJarDirectory() {
        try {
            // Get jar file path
            String jarPath = CavefinderGUI.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            // If it's a Windows path, remove the leading slash
            if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
                jarPath = jarPath.substring(1);
            }
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                // If it's a jar file, return its directory
                return jarFile.getParent();
            } else {
                // If not a jar file (maybe running in IDE), return current working directory
                return System.getProperty("user.dir");
            }
        } catch (Exception e) {
            // If failed to get, return current working directory
            return System.getProperty("user.dir");
        }
    }
    private String getDefaultExportPath() {
        String jarDir = getJarDirectory();
        return new File(jarDir, "result.txt").getAbsolutePath();
    }
    public CavefinderGUI() {
        updateTitle();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        parameterControls = new ArrayList<>();
        fileChooser = new JFileChooser();
        loadCustomFont();
        createUI();
        applyFontToComponent(this);
        updateParameterLockState();
        pack();
        setLocationRelativeTo(null);
        setSize(1300, 800); // Increase width to accommodate wider parameter panel
    }
    private void updateTitle() {
        setTitle(LanguageResources.get("title", currentLanguage));
    }
    private void loadCustomFont() {
        try {
            InputStream fontStream = getClass().getResourceAsStream("/font.ttf");
            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                customFont = baseFont.deriveFont(Font.PLAIN, 12);
                fontStream.close();
            } else {
                // If loading fails, use default font
                customFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
        } catch (Exception e) {
            // If loading fails, use default font
            customFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            e.printStackTrace();
        }
    }
    private void applyFontToComponent(Component component) {
        if (customFont == null) return;
        // Skip log area, it has its own font setting
        if (component == logArea) {
            return;
        }
        if (component instanceof JComponent) {
            component.setFont(customFont);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyFontToComponent(child);
            }
        }
    }
    private void createUI() {
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // Language selection panel (top right)
        JPanel languagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        languageLabel = new JLabel(LanguageResources.get("language", currentLanguage));
        languagePanel.add(languageLabel);
        languageComboBox = new JComboBox<>(Language.values());
        languageComboBox.setSelectedItem(currentLanguage);
        languageComboBox.addActionListener(e -> {
            currentLanguage = (Language) languageComboBox.getSelectedItem();
            updateAllUITexts();
        });
        languagePanel.add(languageComboBox);
        mainPanel.add(languagePanel, BorderLayout.NORTH);
        // Left panel - Parameter settings
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("param_settings", currentLanguage)));
        leftPanel.setPreferredSize(new Dimension(600, Integer.MAX_VALUE));
        // First row: Cave depth and thread count (aligned with coordinates)
        JPanel firstRowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc1 = new GridBagConstraints();
        gbc1.insets = new Insets(2, 2, 2, 2);
        gbc1.anchor = GridBagConstraints.WEST;
        // Cave depth
        gbc1.gridx = 0;
        gbc1.gridy = 0;
        caveDepthLabel = new JLabel(LanguageResources.get("cave_depth", currentLanguage));
        firstRowPanel.add(caveDepthLabel, gbc1);
        Integer[] depths = new Integer[6];
        for (int i = 0; i < 6; i++) {
            depths[i] = -50 + i * 10;
        }
        depthComboBox = new JComboBox<>(depths);
        depthComboBox.setSelectedIndex(0);
        depthComboBox.setPreferredSize(new Dimension(100, 25));
        gbc1.gridx = 1;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        gbc1.weightx = 0;
        firstRowPanel.add(depthComboBox, gbc1);
        // Thread count setting
        gbc1.gridx = 2;
        gbc1.fill = GridBagConstraints.NONE;
        gbc1.weightx = 0;
        threadCountLabel = new JLabel(LanguageResources.get("thread_count", currentLanguage));
        firstRowPanel.add(threadCountLabel, gbc1);
        int maxThreadCount = Runtime.getRuntime().availableProcessors();
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(maxThreadCount, 1, maxThreadCount, 1));
        threadCountSpinner.setPreferredSize(new Dimension(100, 25));
        gbc1.gridx = 3;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        gbc1.weightx = 0;
        firstRowPanel.add(threadCountSpinner, gbc1);
        leftPanel.add(firstRowPanel);
        // Second row: Coordinate input (aligned with first row)
        JPanel coordinatePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(2, 2, 2, 2);
        gbc2.anchor = GridBagConstraints.WEST;
        gbc2.gridx = 0;
        gbc2.gridy = 0;
        xCoordinateLabel = new JLabel(LanguageResources.get("x_coordinate", currentLanguage));
        coordinatePanel.add(xCoordinateLabel, gbc2);
        xCoordinateSpinner = new JSpinner(new SpinnerNumberModel(0, -30000000, 30000000, 1));
        xCoordinateSpinner.setPreferredSize(new Dimension(100, 25));
        gbc2.gridx = 1;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.weightx = 0;
        coordinatePanel.add(xCoordinateSpinner, gbc2);
        gbc2.gridx = 2;
        gbc2.fill = GridBagConstraints.NONE;
        gbc2.weightx = 0;
        zCoordinateLabel = new JLabel(LanguageResources.get("z_coordinate", currentLanguage));
        coordinatePanel.add(zCoordinateLabel, gbc2);
        zCoordinateSpinner = new JSpinner(new SpinnerNumberModel(0, -30000000, 30000000, 1));
        zCoordinateSpinner.setPreferredSize(new Dimension(100, 25));
        gbc2.gridx = 3;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.weightx = 0;
        coordinatePanel.add(zCoordinateSpinner, gbc2);
        leftPanel.add(coordinatePanel);
        // Third row: Segment size setting (only effective in increment mode)
        JPanel segmentSizePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc3 = new GridBagConstraints();
        gbc3.insets = new Insets(2, 2, 2, 2);
        gbc3.anchor = GridBagConstraints.WEST;
        gbc3.gridx = 0;
        gbc3.gridy = 0;
        segmentSizeLabel = new JLabel(LanguageResources.get("segment_size", currentLanguage));
        segmentSizePanel.add(segmentSizeLabel, gbc3);
        segmentSizeSpinner = new JSpinner(new SpinnerNumberModel(10_000_000L, 1_000_000L, 1_000_000_000L, 1_000_000L));
        segmentSizeSpinner.setPreferredSize(new Dimension(120, 25));
        gbc3.gridx = 1;
        gbc3.fill = GridBagConstraints.HORIZONTAL;
        gbc3.weightx = 0;
        segmentSizePanel.add(segmentSizeSpinner, gbc3);
        gbc3.gridx = 2;
        gbc3.fill = GridBagConstraints.NONE;
        gbc3.weightx = 0;
        segmentSizeHintLabel = new JLabel(LanguageResources.get("segment_size_hint", currentLanguage));
        segmentSizePanel.add(segmentSizeHintLabel, gbc3);
        leftPanel.add(segmentSizePanel);
        // Option checkboxes (placed in the same row)
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        checkHeightCheckBox = new JCheckBox(LanguageResources.get("check_height", currentLanguage));
        checkboxPanel.add(checkHeightCheckBox);
        bedrockImpossibleCheckBox = new JCheckBox(LanguageResources.get("filter_be_impossible", currentLanguage));
        bedrockImpossibleCheckBox.addActionListener(e -> updateParameterLockState());
        checkboxPanel.add(bedrockImpossibleCheckBox);
        entrance1OnlyCheckBox = new JCheckBox(LanguageResources.get("entrance1_only", currentLanguage));
        checkboxPanel.add(entrance1OnlyCheckBox);
        leftPanel.add(checkboxPanel);
        // Height check options panel
        JPanel heightCheckPanel = new JPanel();
        heightCheckPanel.setLayout(new BoxLayout(heightCheckPanel, BoxLayout.Y_AXIS));
        heightCheckOptionsPanel = heightCheckPanel;
        heightCheckPanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("height_check_options", currentLanguage)));
        // Height type selection (Surface/Underwater)
        JPanel heightTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        heightTypeLabel = new JLabel(LanguageResources.get("height_type", currentLanguage));
        heightTypePanel.add(heightTypeLabel);
        ButtonGroup heightTypeGroup = new ButtonGroup();
        surfaceHeightRadio = new JRadioButton(LanguageResources.get("surface_height", currentLanguage), true);
        underwaterHeightRadio = new JRadioButton(LanguageResources.get("underwater_height", currentLanguage));
        heightTypeGroup.add(surfaceHeightRadio);
        heightTypeGroup.add(underwaterHeightRadio);
        heightTypePanel.add(surfaceHeightRadio);
        heightTypePanel.add(underwaterHeightRadio);
        heightCheckPanel.add(heightTypePanel);
        // Range check option
        JPanel rangeCheckPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcRange = new GridBagConstraints();
        gbcRange.insets = new Insets(2, 2, 2, 2);
        gbcRange.anchor = GridBagConstraints.WEST;
        rangeCheckCheckBox = new JCheckBox(LanguageResources.get("check_height_in_range", currentLanguage));
        gbcRange.gridx = 0;
        gbcRange.gridy = 0;
        gbcRange.gridwidth = 2;
        rangeCheckPanel.add(rangeCheckCheckBox, gbcRange);
        // Range height type selection
        gbcRange.gridx = 0;
        gbcRange.gridy = 1;
        gbcRange.gridwidth = 1;
        rangeHeightTypeLabel = new JLabel(LanguageResources.get("range_height_type", currentLanguage));
        rangeCheckPanel.add(rangeHeightTypeLabel, gbcRange);
        rangeHeightTypeComboBox = new JComboBox<>(new String[]{
            LanguageResources.get("min_height", currentLanguage),
            LanguageResources.get("avg_height", currentLanguage),
            LanguageResources.get("max_height", currentLanguage)
        });
        rangeHeightTypeComboBox.setSelectedIndex(0);
        rangeHeightTypeComboBox.setPreferredSize(new Dimension(100, 25));
        gbcRange.gridx = 1;
        rangeCheckPanel.add(rangeHeightTypeComboBox, gbcRange);
        // Range coordinates input
        gbcRange.gridx = 0;
        gbcRange.gridy = 2;
        rangeCoordinatesLabel = new JLabel(LanguageResources.get("range_coordinates", currentLanguage));
        rangeCheckPanel.add(rangeCoordinatesLabel, gbcRange);
        rangeCoordinatesField = new JTextField("0 0 1 1", 15);
        rangeCoordinatesField.setToolTipText(LanguageResources.get("range_coord_tooltip", currentLanguage));
        gbcRange.gridx = 1;
        rangeCheckPanel.add(rangeCoordinatesField, gbcRange);
        heightCheckPanel.add(rangeCheckPanel);
        // Enable/disable range check components based on checkbox
        rangeCheckCheckBox.addActionListener(e -> {
            // Only enable range check components if both height check and range check are enabled
            boolean enabled = checkHeightCheckBox.isSelected() && rangeCheckCheckBox.isSelected();
            rangeHeightTypeComboBox.setEnabled(enabled);
            rangeCoordinatesField.setEnabled(enabled);
        });
        rangeHeightTypeComboBox.setEnabled(false);
        rangeCoordinatesField.setEnabled(false);
        // Enable/disable height type selection based on checkHeightCheckBox
        checkHeightCheckBox.addActionListener(e -> {
            boolean enabled = checkHeightCheckBox.isSelected();
            surfaceHeightRadio.setEnabled(enabled && !bedrockImpossibleCheckBox.isSelected());
            underwaterHeightRadio.setEnabled(enabled && !bedrockImpossibleCheckBox.isSelected());
            rangeCheckCheckBox.setEnabled(enabled);
            if (!enabled) {
                rangeCheckCheckBox.setSelected(false);
                // Also disable range check components when height check is disabled
                rangeHeightTypeComboBox.setEnabled(false);
                rangeCoordinatesField.setEnabled(false);
            } else {
                // When height check is enabled, range check components state depends on rangeCheckCheckBox
                boolean rangeEnabled = rangeCheckCheckBox.isSelected();
                rangeHeightTypeComboBox.setEnabled(rangeEnabled);
                rangeCoordinatesField.setEnabled(rangeEnabled);
            }
        });
        surfaceHeightRadio.setEnabled(false);
        underwaterHeightRadio.setEnabled(false);
        rangeCheckCheckBox.setEnabled(false);
        leftPanel.add(heightCheckPanel);
        // Filter mode
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new GridLayout(1, 2, 10, 0));
        filterModePanel = modePanel;
        modePanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("filter_mode", currentLanguage)));
        // First column: Search mode
        JPanel searchModePanel = new JPanel();
        this.searchModePanel = searchModePanel;
        searchModePanel.setLayout(new BoxLayout(searchModePanel, BoxLayout.Y_AXIS));
        searchModePanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("search_mode", currentLanguage)));
        ButtonGroup searchModeGroup = new ButtonGroup();
        incrementModeRadio = new JRadioButton(LanguageResources.get("incremental", currentLanguage), true);
        JRadioButton listModeRadio = new JRadioButton(LanguageResources.get("filter_from_list", currentLanguage));
        // Store reference for language updates
        searchModeGroup.add(incrementModeRadio);
        searchModeGroup.add(listModeRadio);
        searchModePanel.add(incrementModeRadio);
        searchModePanel.add(listModeRadio);
        // Second column: Seed type
        JPanel seedTypePanel = new JPanel();
        this.seedTypePanel = seedTypePanel;
        seedTypePanel.setLayout(new BoxLayout(seedTypePanel, BoxLayout.Y_AXIS));
        seedTypePanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("seed_type", currentLanguage)));
        ButtonGroup seedTypeGroup = new ButtonGroup();
        structureSeedRadio = new JRadioButton(LanguageResources.get("structure_seed", currentLanguage), true);
        worldSeedRadio = new JRadioButton(LanguageResources.get("world_seed", currentLanguage));
        seedTypeGroup.add(structureSeedRadio);
        seedTypeGroup.add(worldSeedRadio);
        seedTypePanel.add(structureSeedRadio);
        seedTypePanel.add(worldSeedRadio);
        modePanel.add(searchModePanel);
        modePanel.add(seedTypePanel);
        leftPanel.add(modePanel);
        // Seed input area
        JPanel seedInputPanel = new JPanel();
        this.seedInputPanel = seedInputPanel;
        seedInputPanel.setLayout(new BoxLayout(seedInputPanel, BoxLayout.Y_AXIS));
        seedInputPanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("seed_input", currentLanguage)));
        JPanel incrementPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        startSeedLabel = new JLabel(LanguageResources.get("start_seed", currentLanguage));
        incrementPanel.add(startSeedLabel, gbc);
        startSeedField = new JTextField(20);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        incrementPanel.add(startSeedField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        endSeedLabel = new JLabel(LanguageResources.get("end_seed", currentLanguage));
        incrementPanel.add(endSeedLabel, gbc);
        endSeedField = new JTextField(20);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        incrementPanel.add(endSeedField, gbc);
        seedInputPanel.add(incrementPanel);
        JPanel listPanel = new JPanel(new BorderLayout());
        seedListLabel = new JLabel(LanguageResources.get("seed_list", currentLanguage));
        listPanel.add(seedListLabel, BorderLayout.NORTH);
        seedListArea = new JTextArea(12, 40); // Increase height from 8 to 12 rows, width from 30 to 40 columns
        seedListArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(seedListArea);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        loadFileButton = new JButton(LanguageResources.get("load_from_file", currentLanguage));
        loadFileButton.addActionListener(e -> loadSeedFile());
        listPanel.add(loadFileButton, BorderLayout.SOUTH);
        seedInputPanel.add(listPanel);
        leftPanel.add(seedInputPanel);
        // Parameter condition panel
        parameterPanel = new JPanel();
        parameterPanel.setLayout(new BoxLayout(parameterPanel, BoxLayout.Y_AXIS));
        biomeParamsPanel = parameterPanel;
        parameterPanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("biome_params", currentLanguage)));
        // Add parameters in order
        addParameterControl(ParameterType.TEMPERATURE, LanguageResources.get("param_temperature", currentLanguage));
        addParameterControl(ParameterType.HUMIDITY, LanguageResources.get("param_humidity", currentLanguage));
        addParameterControl(ParameterType.EROSION, LanguageResources.get("param_erosion", currentLanguage));
        addParameterControl(ParameterType.RIDGE, LanguageResources.get("param_ridge", currentLanguage));
        addParameterControl(ParameterType.ENTRANCE, LanguageResources.get("param_entrance", currentLanguage));
        addParameterControl(ParameterType.CHEESE, LanguageResources.get("param_cheese", currentLanguage));
        addParameterControl(ParameterType.CONTINENTALNESS, LanguageResources.get("param_continentalness", currentLanguage));
        addParameterControl(ParameterType.AQUIFER, LanguageResources.get("param_aquifer", currentLanguage));
        JScrollPane paramScrollPane = new JScrollPane(parameterPanel);
        paramScrollPane.setPreferredSize(new Dimension(750, 300)); // Increase width to accommodate all components including second value
        // Right panel - Parameters and log
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(750, Integer.MAX_VALUE));
        rightPanel.add(paramScrollPane, BorderLayout.CENTER);
        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        this.logPanel = logPanel;
        logPanel.setBorder(BorderFactory.createTitledBorder(LanguageResources.get("log", currentLanguage)));
        logArea = new JTextArea(15, 25); // Increase height from 10 to 15 rows
        logArea.setEditable(false);
        // Log area uses custom font (if loaded), otherwise use monospaced font
        logArea.setFont(Objects.requireNonNullElseGet(customFont, () -> new Font(Font.MONOSPACED, Font.PLAIN, 12)));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        rightPanel.add(logPanel, BorderLayout.SOUTH);
        // Control buttons and progress
        JPanel controlPanel = new JPanel(new BorderLayout());
        // Export path selection
        JPanel exportPanel = new JPanel(new BorderLayout(5, 0));
        exportPathLabel = new JLabel(LanguageResources.get("export_path", currentLanguage));
        exportPanel.add(exportPathLabel, BorderLayout.WEST);
        exportPathField = new JTextField(getDefaultExportPath());
        exportPanel.add(exportPathField, BorderLayout.CENTER);
        browseExportPathButton = new JButton(LanguageResources.get("browse", currentLanguage));
        browseExportPathButton.addActionListener(e -> browseExportPath());
        exportPanel.add(browseExportPathButton, BorderLayout.EAST);
        // Button panel (placed below export path, centered)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        startButton = new JButton(LanguageResources.get("start_filtering", currentLanguage));
        startButton.addActionListener(e -> startFiltering());
        stopButton = new JButton(LanguageResources.get("stop", currentLanguage));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopFiltering());
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel(LanguageResources.get("ready", currentLanguage));
        // Assemble control panel
        JPanel topControlPanel = new JPanel(new BorderLayout());
        topControlPanel.add(exportPanel, BorderLayout.NORTH);
        topControlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(topControlPanel, BorderLayout.NORTH);
        controlPanel.add(progressBar, BorderLayout.CENTER);
        controlPanel.add(statusLabel, BorderLayout.SOUTH);
        // Assemble main panel
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        add(mainPanel);
    }
    // Maximum width for all parameter labels (for alignment)
    private static final int MAX_LABEL_WIDTH = 280;
    private void addParameterControl(ParameterType type, String label) {
        ParameterControl control = new ParameterControl(type, label, MAX_LABEL_WIDTH);
        // Set default values based on parameter type
        switch (type) {
            case CONTINENTALNESS ->
                // Default exclude <-0.11 (i.e., only accept >= -0.11, use BETWEEN [-0.11, 1.0])
                    control.setDefaultValues(true, ConditionType.GREATER_THAN, -0.11, 1.0);
            case RIDGE ->
                // Default exclude values between -0.16 and 0.16
                    control.setDefaultValues(true, ConditionType.NOT_IN_RANGE, -0.16, 0.16);
            case ENTRANCE, CHEESE ->
                // Default is <0 (less than 0)
                    control.setDefaultValues(true, ConditionType.LESS_THAN, 0.0, 0.0);
            case AQUIFER ->
                // Default is <0.4 (use BETWEEN [-1.0, 0.4])
                    control.setDefaultValues(true, ConditionType.LESS_THAN, 0.4, 0.4);
            default -> {
            }
            // Other parameters default to not enabled
        }
        parameterControls.add(control);
        parameterPanel.add(control.getPanel());
    }
    private void loadSeedFile() {
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                seedListArea.setText(content);
                log(LanguageResources.get("file_loaded", currentLanguage) + file.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, LanguageResources.get("load_file_failed", currentLanguage) + e.getMessage(),
                        LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void browseExportPath() {
        JFileChooser exportChooser = new JFileChooser();
        exportChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        exportChooser.setDialogTitle(LanguageResources.get("select_export_path", currentLanguage));
        String currentPath = exportPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                exportChooser.setCurrentDirectory(currentFile.getParentFile());
            }
            if (!currentFile.getName().isEmpty()) {
                exportChooser.setSelectedFile(currentFile);
            }
        }
        int returnVal = exportChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            exportPathField.setText(exportChooser.getSelectedFile().getAbsolutePath());
        }
    }
    private void updateParameterLockState() {
        boolean locked = bedrockImpossibleCheckBox.isSelected();
        for (ParameterControl control : parameterControls) {
            control.setEnabled(!locked);
        }
        // Lock "Only filter Entrance1" option
        entrance1OnlyCheckBox.setEnabled(!locked);
        // Lock height type selection when filtering bedrock impossible seeds
        boolean heightCheckEnabled = checkHeightCheckBox.isSelected() && !locked;
        surfaceHeightRadio.setEnabled(heightCheckEnabled);
        underwaterHeightRadio.setEnabled(heightCheckEnabled);
    }
    // Helper method to safely set text on a component
    private void setTextSafely(JComponent component, String key) {
        if (component != null) {
            if (component instanceof JLabel) {
                ((JLabel) component).setText(LanguageResources.get(key, currentLanguage));
            } else if (component instanceof AbstractButton) {
                ((AbstractButton) component).setText(LanguageResources.get(key, currentLanguage));
            }
        }
    }
    // Helper method to safely set title on a panel's border
    private void setBorderTitleSafely(JPanel panel, String key) {
        if (panel != null && panel.getBorder() instanceof TitledBorder) {
            ((TitledBorder) panel.getBorder()).setTitle(LanguageResources.get(key, currentLanguage));
        }
    }
    private void updateAllUITexts() {
        updateTitle();
        // Update labels
        setTextSafely(languageLabel, "language");
        setTextSafely(caveDepthLabel, "cave_depth");
        setTextSafely(threadCountLabel, "thread_count");
        setTextSafely(xCoordinateLabel, "x_coordinate");
        setTextSafely(zCoordinateLabel, "z_coordinate");
        setTextSafely(segmentSizeLabel, "segment_size");
        setTextSafely(segmentSizeHintLabel, "segment_size_hint");
        setTextSafely(heightTypeLabel, "height_type");
        setTextSafely(rangeHeightTypeLabel, "range_height_type");
        setTextSafely(rangeCoordinatesLabel, "range_coordinates");
        setTextSafely(startSeedLabel, "start_seed");
        setTextSafely(endSeedLabel, "end_seed");
        setTextSafely(seedListLabel, "seed_list");
        setTextSafely(exportPathLabel, "export_path");
        setTextSafely(statusLabel, "ready");
        // Update checkboxes and buttons
        setTextSafely(checkHeightCheckBox, "check_height");
        setTextSafely(bedrockImpossibleCheckBox, "filter_be_impossible");
        setTextSafely(entrance1OnlyCheckBox, "entrance1_only");
        setTextSafely(rangeCheckCheckBox, "check_height_in_range");
        setTextSafely(surfaceHeightRadio, "surface_height");
        setTextSafely(underwaterHeightRadio, "underwater_height");
        setTextSafely(incrementModeRadio, "incremental");
        setTextSafely(structureSeedRadio, "structure_seed");
        setTextSafely(worldSeedRadio, "world_seed");
        setTextSafely(startButton, "start_filtering");
        setTextSafely(stopButton, "stop");
        setTextSafely(loadFileButton, "load_from_file");
        setTextSafely(browseExportPathButton, "browse");
        // Update panel borders
        setBorderTitleSafely(heightCheckOptionsPanel, "height_check_options");
        setBorderTitleSafely(filterModePanel, "filter_mode");
        setBorderTitleSafely(searchModePanel, "search_mode");
        setBorderTitleSafely(seedTypePanel, "seed_type");
        setBorderTitleSafely(seedInputPanel, "seed_input");
        setBorderTitleSafely(biomeParamsPanel, "biome_params");
        setBorderTitleSafely(logPanel, "log");
        // Update range coordinates tooltip
        if (rangeCoordinatesField != null) {
            rangeCoordinatesField.setToolTipText(LanguageResources.get("range_coord_tooltip", currentLanguage));
        }
        // Update range height type combo box
        updateRangeHeightTypeComboBox();
        // Update listModeRadio - need to find it in the searchModePanel
        if (searchModePanel != null) {
            for (Component comp : searchModePanel.getComponents()) {
                if (comp instanceof JRadioButton && comp != incrementModeRadio) {
                    ((JRadioButton) comp).setText(LanguageResources.get("filter_from_list", currentLanguage));
                    break;
                }
            }
        }
        // Update parameter controls
        updateParameterControls();
    }
    private void updateRangeHeightTypeComboBox() {
        if (rangeHeightTypeComboBox == null) return;
        String[] items = new String[]{
            LanguageResources.get("min_height", currentLanguage),
            LanguageResources.get("avg_height", currentLanguage),
            LanguageResources.get("max_height", currentLanguage)
        };
        Object selected = rangeHeightTypeComboBox.getSelectedItem();
        int selectedIndex = -1;
        // Determine current selection index by checking the selected item
        if (selected != null) {
            String selectedStr = selected.toString();
            // Check for Chinese or English keywords
            if (selectedStr.contains("最低") || selectedStr.contains("Minimum")) {
                selectedIndex = 0;
            } else if (selectedStr.contains("平均") || selectedStr.contains("Average")) {
                selectedIndex = 1;
            } else if (selectedStr.contains("最高") || selectedStr.contains("Maximum")) {
                selectedIndex = 2;
            }
        }
        rangeHeightTypeComboBox.removeAllItems();
        for (String item : items) {
            rangeHeightTypeComboBox.addItem(item);
        }
        // Restore selection if valid
        if (selectedIndex >= 0 && selectedIndex < rangeHeightTypeComboBox.getItemCount()) {
            rangeHeightTypeComboBox.setSelectedIndex(selectedIndex);
        }
    }
    private void updateParameterControls() {
        for (ParameterControl control : parameterControls) {
            control.updateLanguage();
        }
        // Update condition type combo boxes in parameter controls
        SwingUtilities.invokeLater(() -> {
            for (ParameterControl control : parameterControls) {
                control.updateConditionTypes();
            }
        });
    }
    private void startFiltering() {
        if (isRunning) {
            return;
        }
        // Validate input
        if (incrementModeRadio.isSelected()) {
            try {
                Long.parseLong(startSeedField.getText());
                Long.parseLong(endSeedField.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, LanguageResources.get("invalid_seed_numbers", currentLanguage),
                        LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            if (seedListArea.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, LanguageResources.get("enter_seed_list", currentLanguage),
                        LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        // Check result file
        String exportPath = exportPathField.getText().trim();
        if (exportPath.isEmpty()) {
            exportPath = getDefaultExportPath();
            exportPathField.setText(exportPath);
        }
        Path resultPath = Paths.get(exportPath);
        // Check if path is accessible
        try {
            // Check if parent directory exists and is writable
            Path parentPath = resultPath.getParent();
            if (parentPath != null) {
                if (!Files.exists(parentPath)) {
                    // Try to create directory
                    Files.createDirectories(parentPath);
                }
                // Check if directory is writable
                if (!Files.isWritable(parentPath)) {
                    JOptionPane.showMessageDialog(this,
                            LanguageResources.get("cannot_access_dir", currentLanguage) + parentPath + LanguageResources.get("please_select_other", currentLanguage),
                            LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        } catch (AccessDeniedException e) {
            JOptionPane.showMessageDialog(this,
                    LanguageResources.get("cannot_access_path", currentLanguage) + exportPath + LanguageResources.get("access_denied", currentLanguage),
                    LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    LanguageResources.get("cannot_access_path", currentLanguage) + exportPath + LanguageResources.get("error_colon", currentLanguage) + e.getMessage(),
                    LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (Files.exists(resultPath)) {
            int result = JOptionPane.showConfirmDialog(this,
                    LanguageResources.get("file_exists_overwrite", currentLanguage), LanguageResources.get("reminder", currentLanguage),
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setValue(0);
        logArea.setText("");
        filteringStartTime = System.currentTimeMillis();
        lastUpdateTime = 0;
        lastUpdateCompleted = 0;
        // Start filtering thread
        new Thread(this::runFiltering).start();
    }
    private void stopFiltering() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null; // Release reference to help GC
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        log(LanguageResources.get("filtering_stopped", currentLanguage));
    }
    private void runFiltering() {
        try {
            String exportPath = exportPathField.getText().trim();
            if (exportPath.isEmpty()) {
                exportPath = getDefaultExportPath();
            }
            final String finalExportPath = exportPath; // For lambda expression
            Path resultPath = Paths.get(exportPath);
            // Check path accessibility again (in background thread)
            try {
                Path parentPath = resultPath.getParent();
                if (parentPath != null) {
                    if (!Files.exists(parentPath)) {
                        Files.createDirectories(parentPath);
                    }
                    if (!Files.isWritable(parentPath)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                LanguageResources.get("cannot_access_dir", currentLanguage) + parentPath + LanguageResources.get("please_select_other", currentLanguage),
                                LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE));
                        SwingUtilities.invokeLater(() -> {
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            isRunning = false;
                        });
                        return;
                    }
                }
            } catch (AccessDeniedException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        LanguageResources.get("cannot_access_path", currentLanguage) + finalExportPath + LanguageResources.get("access_denied", currentLanguage),
                        LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE));
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    isRunning = false;
                });
                return;
            } catch (Exception e) {
                final Exception finalException = e; // For lambda expression
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        LanguageResources.get("cannot_access_path", currentLanguage) + finalExportPath + LanguageResources.get("error_colon", currentLanguage) + finalException.getMessage(),
                        LanguageResources.get("error", currentLanguage), JOptionPane.ERROR_MESSAGE));
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    isRunning = false;
                });
                return;
            }
            if (Files.exists(resultPath)) {
                Files.delete(resultPath);
            }
            // Ensure directory exists
            if (resultPath.getParent() != null) {
                Files.createDirectories(resultPath.getParent());
            }
            // Get coordinates
            int x = (Integer) xCoordinateSpinner.getValue();
            int z = (Integer) zCoordinateSpinner.getValue();

            // Calculate total task count (for progress display)
            long totalTaskCount = 0;
            if (incrementModeRadio.isSelected()) {
                long start = Long.parseLong(startSeedField.getText());
                long end = Long.parseLong(endSeedField.getText());
                // Calculate range, handle possible overflow
                if (end >= start) {
                    totalTaskCount = end - start + 1;
                    // If range is too large causing overflow, set to -1 for unknown total
                    if (totalTaskCount < 0) {
                        totalTaskCount = -1;
                    }
                }
            } else {
                String[] lines = seedListArea.getText().split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        try {
                            Long.parseLong(line);
                            totalTaskCount++;
                        } catch (NumberFormatException e) {
                            // Skip invalid seed
                        }
                    }
                }
            }
            totalTasks.set(totalTaskCount);
            completedTasks.set(0);
            // If height check is enabled, pre-initialize SeedCheckerSettings in main thread
            // This avoids ExceptionInInitializerError caused by concurrent initialization in multi-threading
            if (checkHeightCheckBox.isSelected()) {
                try {
                    log(LanguageResources.get("checking_seedchecker", currentLanguage));
                    // If not initialized yet, try to initialize
                    if (!SeedCheckerInitializer.isInitialized()) {
                        log(LanguageResources.get("attempting_init_seedchecker", currentLanguage));
                        SeedCheckerInitializer.initialize();
                    }
                    if (SeedCheckerInitializer.isInitialized()) {
                        log(LanguageResources.get("seedchecker_ready", currentLanguage));
                    } else {
                        log(LanguageResources.get("seedchecker_init_failed", currentLanguage));
                    }
                } catch (Exception e) {
                    log(LanguageResources.get("seedchecker_check_exception", currentLanguage) + e.getMessage());
                }
            }
            int threadCount = (Integer) threadCountSpinner.getValue();
            // SpinnerNumberModel may return Integer, Long or Double, use Number type for safe conversion
            long segmentSize = ((Number) segmentSizeSpinner.getValue()).longValue();
            ReentrantLock fileLock = new ReentrantLock();

            try (BufferedWriter writer = Files.newBufferedWriter(resultPath)) {
                if (incrementModeRadio.isSelected()) {
                    // Increment mode
                    long start = Long.parseLong(startSeedField.getText());
                    long end = Long.parseLong(endSeedField.getText());
                    long totalCount = end - start + 1;

                    log(String.format(LanguageResources.get("starting_filtering", currentLanguage), start, end, totalCount));

                    if (totalCount <= segmentSize) {
                        // Less than or equal to segment size: use simple efficient solution
                        executor = Executors.newFixedThreadPool(threadCount);
                        if (structureSeedRadio.isSelected()) {
                            // StructureSeed mode: each task processes 1 structureSeed (internally handles 65536 worldSeeds)
                            for (long seed = start; seed <= end && isRunning; seed++) {
                                final long finalSeed = seed;
                                executor.execute(() -> processStructureSeed(finalSeed, x, z, writer, fileLock));
                            }
                        } else {
                            // WorldSeed mode: batch processing to reduce task count and scheduling overhead
                            final long BATCH_SIZE_WS = 1000; // Process 1000 worldSeeds per batch
                            long currentBatchStart = start;
                            while (currentBatchStart <= end && isRunning) {
                                final long batchStart = currentBatchStart;
                                final long batchEnd = Math.min(currentBatchStart + BATCH_SIZE_WS - 1, end);
                                executor.execute(() -> processWorldSeedsBatch(batchStart, batchEnd, x, z, writer, fileLock));
                                currentBatchStart = batchEnd + 1;
                            }
                        }
                        executor.shutdown();
                        try {
                            if (!executor.awaitTermination(365, TimeUnit.DAYS)) {
                                executor.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            executor.shutdownNow();
                            Thread.currentThread().interrupt();
                        } finally {
                            executor = null;
                        }
                    } else {
                        // Greater than segment size: segment processing, thoroughly clean up after each segment
                        long segmentCount = (totalCount + segmentSize - 1) / segmentSize;
                        log(String.format(LanguageResources.get("seed_count_exceeds", currentLanguage), segmentSize, segmentCount));

                        long currentStart = start;
                        int segmentIndex = 1;

                        while (currentStart <= end && isRunning) {
                            long currentEnd = Math.min(currentStart + segmentSize - 1, end);
                            log(String.format(LanguageResources.get("processing_segment", currentLanguage), segmentIndex, segmentCount, currentStart, currentEnd));

                            // Create new thread pool for current segment
                            executor = Executors.newFixedThreadPool(threadCount);
                            if (structureSeedRadio.isSelected()) {
                                // StructureSeed mode: each task processes 1 structureSeed
                                for (long seed = currentStart; seed <= currentEnd && isRunning; seed++) {
                                    final long finalSeed = seed;
                                    executor.execute(() -> processStructureSeed(finalSeed, x, z, writer, fileLock));
                                }
                            } else {
                                // WorldSeed mode: batch processing to reduce task count and scheduling overhead
                                final long BATCH_SIZE_WS = 1000; // Process 1000 worldSeeds per batch
                                long batchStart = currentStart;
                                while (batchStart <= currentEnd && isRunning) {
                                    final long batchStartFinal = batchStart;
                                    final long batchEnd = Math.min(batchStart + BATCH_SIZE_WS - 1, currentEnd);
                                    executor.execute(() -> processWorldSeedsBatch(batchStartFinal, batchEnd, x, z, writer, fileLock));
                                    batchStart = batchEnd + 1;
                                }
                            }

                            // Wait for current segment to complete
                            executor.shutdown();
                            try {
                                if (!executor.awaitTermination(365, TimeUnit.DAYS)) {
                                    executor.shutdownNow();
                                }
                            } catch (InterruptedException e) {
                                executor.shutdownNow();
                                Thread.currentThread().interrupt();
                                break;
                            }
                            // Thoroughly clean up memory
                            executor = null;
                            System.gc(); // Suggest JVM to perform garbage collection

                            currentStart = currentEnd + 1;
                            segmentIndex++;
                        }
                    }
                } else {
                    // List mode
                    executor = Executors.newFixedThreadPool(threadCount);
                    String[] lines = seedListArea.getText().split("\n");
                    log(String.format(LanguageResources.get("starting_list_filtering", currentLanguage), lines.length));

                    if (structureSeedRadio.isSelected()) {
                        // StructureSeed mode: each task processes 1 structureSeed
                        for (String line : lines) {
                            if (!isRunning) break;
                            line = line.trim();
                            if (!line.isEmpty()) {
                                try {
                                    final long seed = Long.parseLong(line);
                                    executor.execute(() -> processStructureSeed(seed, x, z, writer, fileLock));
                                } catch (NumberFormatException e) {
                                    log(LanguageResources.get("skipping_invalid_seed", currentLanguage) + line);
                                }
                            }
                        }
                    } else {
                        // WorldSeed mode: batch processing to reduce task count and scheduling overhead
                        final long BATCH_SIZE_WS = 1000; // Process 1000 worldSeeds per batch
                        List<Long> batch = new ArrayList<>();
                        for (String line : lines) {
                            if (!isRunning) break;
                            line = line.trim();
                            if (!line.isEmpty()) {
                                try {
                                    long seed = Long.parseLong(line);
                                    batch.add(seed);
                                    if (batch.size() >= BATCH_SIZE_WS) {
                                        final List<Long> batchToProcess = new ArrayList<>(batch);
                                        executor.execute(() -> {
                                            long processedCount = 0;
                                            for (long ws : batchToProcess) {
                                                if (!isRunning) break;
                                                if (checkSeed(ws, x, z)) {
                                                    if (checkHeightCheckBox.isSelected()) {
                                                        if (checkHeight(ws, x, z)) {
                                                            writeResult(ws, writer, fileLock);
                                                        }
                                                    } else {
                                                        writeResult(ws, writer, fileLock);
                                                    }
                                                }
                                                processedCount++;
                                            }
                                            // Batch update progress
                                            if (processedCount > 0) {
                                                completedTasks.addAndGet(processedCount - 1);
                                                updateProgress();
                                            }
                                        });
                                        batch.clear();
                                    }
                                } catch (NumberFormatException e) {
                                    log(LanguageResources.get("skipping_invalid_seed", currentLanguage) + line);
                                }
                            }
                        }
                        // Process remaining seeds
                        if (!batch.isEmpty() && isRunning) {
                            final List<Long> batchToProcess = new ArrayList<>(batch);
                            executor.execute(() -> {
                                long processedCount = 0;
                                for (long ws : batchToProcess) {
                                    if (!isRunning) break;
                                    if (checkSeed(ws, x, z)) {
                                        if (checkHeightCheckBox.isSelected()) {
                                            if (checkHeight(ws, x, z)) {
                                                writeResult(ws, writer, fileLock);
                                            }
                                        } else {
                                            writeResult(ws, writer, fileLock);
                                        }
                                    }
                                    processedCount++;
                                }
                                // Batch update progress
                                if (processedCount > 0) {
                                    completedTasks.addAndGet(processedCount - 1);
                                    updateProgress();
                                }
                            });
                        }
                    }

                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(365, TimeUnit.DAYS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    } finally {
                        executor = null;
                    }
                }
            }
            if (isRunning) {
                long totalElapsedMs = System.currentTimeMillis() - filteringStartTime;
                String totalTimeStr = formatElapsedTime(totalElapsedMs);
                long totalCompleted = completedTasks.get();
                double totalElapsedSec = totalElapsedMs / 1000.0;
                double totalSeedsProcessed = totalCompleted;
                if (structureSeedRadio.isSelected()) {
                    totalSeedsProcessed = totalCompleted * 65536.0;
                }
                double avgSpeed = totalElapsedSec > 0 ? totalSeedsProcessed / totalElapsedSec : 0;
                String avgSpeedStr = formatSpeed(avgSpeed);

                log(LanguageResources.get("filtering_completed", currentLanguage) + exportPath);
                log(String.format(LanguageResources.get("total_time", currentLanguage), totalTimeStr, avgSpeedStr));
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    isRunning = false;
                });
            }
        } catch (Exception e) {
            log(LanguageResources.get("error_colon_msg", currentLanguage) + e.getMessage());
            e.printStackTrace();
            // Ensure executor is released even on exception
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                isRunning = false;
            });
        }
    }
    private void processStructureSeed(long structureSeed, int x, int z, BufferedWriter writer, ReentrantLock fileLock) {
        StructureSeed.getWorldSeeds(structureSeed).forEachRemaining(ws -> {
            if (!isRunning) return;
            if (checkSeed(ws, x, z)) {
                if (checkHeightCheckBox.isSelected()) {
                    if (checkHeight(ws, x, z)) {
                        writeResult(ws, writer, fileLock);
                    }
                } else {
                    writeResult(ws, writer, fileLock);
                }
            }
        });
        updateProgress();
    }
    // Batch process WorldSeed to reduce task count and scheduling overhead
    private void processWorldSeedsBatch(long startSeed, long endSeed, int x, int z, BufferedWriter writer, ReentrantLock fileLock) {
        long processedCount = 0;
        for (long seed = startSeed; seed <= endSeed && isRunning; seed++) {
            if (checkSeed(seed, x, z)) {
                if (checkHeightCheckBox.isSelected()) {
                    if (checkHeight(seed, x, z)) {
                        writeResult(seed, writer, fileLock);
                    }
                } else {
                    writeResult(seed, writer, fileLock);
                }
            }
            processedCount++;
        }
        // Batch update progress: directly add to completedTasks, then call updateProgress() once
        // Note: updateProgress() internally does incrementAndGet(), so here we add (processedCount-1) first, letting updateProgress() add the last one
        if (processedCount > 0) {
            completedTasks.addAndGet(processedCount - 1);
            updateProgress(); // This will add 1 more, totaling processedCount
        }
    }
    private boolean checkSeed(long seed, int x, int z) {
        if (bedrockImpossibleCheckBox.isSelected()) {
            return checkBedrockImpossible(seed, x, z);
        } else {
            return checkNormal(seed, x, z);
        }
    }
    private boolean checkNormal(long seed, int x, int z) {
        NoiseCache cache = new NoiseCache(seed);
        boolean entrance1Only = entrance1OnlyCheckBox.isSelected();
        // Check temperature and humidity (always before cave filtering)
        if (!checkParameter(ParameterType.TEMPERATURE, cache.temperature.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        if (!checkParameter(ParameterType.HUMIDITY, cache.humidity.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        // If only filtering Entrance1, move erosion and ridge after cave filtering
        if (!entrance1Only) {
            if (!checkParameter(ParameterType.EROSION, cache.erosion.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
            if (!checkParameter(ParameterType.RIDGE, cache.ridge.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
        }
        // Check cave depth
        int minDepth = (Integer) depthComboBox.getSelectedItem();
        // Check Entrance and Cheese
        // Entrance at 50 and 60 are always checked first, Cheese does not check 50 and 60
        // At height 40 and below, Entrance and Cheese have an "OR" relationship (either one satisfied counts as satisfied)
        // First check Entrance at heights 60 and 50 (do not check Cheese)
        if (entrance1Only) {
            double entrance1_50 = Entrance1(seed, x, 50, z);
            if (!checkParameter(ParameterType.ENTRANCE, entrance1_50)) {
                return false;
            }
        } else {
            double entrance_50 = Entrance(seed, x, 50, z);
            if (!checkParameter(ParameterType.ENTRANCE, entrance_50)) {
                return false;
            }
        }
        if (entrance1Only) {
            double entrance1_60 = Entrance1(seed, x, 60, z);
            if (!checkParameter(ParameterType.ENTRANCE, entrance1_60)) {
                return false;
            }
        } else {
            double entrance_60 = Entrance(seed, x, 60, z);
            if (!checkParameter(ParameterType.ENTRANCE, entrance_60)) {
                return false;
            }
        }
        // Check heights 40 and below: Entrance and Cheese have "OR" relationship
        for (int y = minDepth; y <=40; y += 10) {
            boolean entrancePass,cheesePass;
            if (y >= 0) {
                if (entrance1Only) {
                    double entrance1 = Entrance1(seed, x, y, z);
                    entrancePass = checkParameter(ParameterType.ENTRANCE, entrance1);
                } else {
                    double entrance = Entrance(seed, x, y, z);
                    entrancePass = checkParameter(ParameterType.ENTRANCE, entrance);
                }
                double cheese = Cheese(seed, x, y, z);
                cheesePass = checkParameter(ParameterType.CHEESE, cheese);
            } else {
                if (entrance1Only) {
                    // Entrance1 mode: remove Entrance2 check, only check Cheese
                    double cheese = Cheese(seed, x, y, z);
                    cheesePass = checkParameter(ParameterType.CHEESE, cheese);
                    if (!cheesePass) {
                        return false;
                    }
                    continue; // Only check Cheese, already checked
                } else {
                    // Normal mode: check Entrance2 and Cheese, "OR" relationship
                    double entrance2 = Entrance2(seed, x, y, z);
                    entrancePass = checkParameter(ParameterType.ENTRANCE, entrance2);
                    double cheese = Cheese(seed, x, y, z);
                    cheesePass = checkParameter(ParameterType.CHEESE, cheese);
                }
            }
            // "OR" relationship: either one satisfied counts as passed
            if (!entrancePass && !cheesePass) {
                return false;
            }
        }
        // If only filtering Entrance1, move erosion and ridge after cave filtering
        if (entrance1Only) {
            if (!checkParameter(ParameterType.EROSION, cache.erosion.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
            if (!checkParameter(ParameterType.RIDGE, cache.ridge.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
        }
        // Continentalness moved separately after cave depth
        if (!checkParameter(ParameterType.CONTINENTALNESS, cache.contientalness.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        // Check Aquifer
        if (isParameterEnabled()) {
            LazyDoublePerlinNoiseSampler aquiferNoise = LazyDoublePerlinNoiseSampler.createNoiseSampler(
                    new Xoroshiro128PlusPlusRandom(seed).createRandomDeriver(),
                    NoiseParameterKey.AQUIFER_FLUID_LEVEL_FLOODEDNESS
            );
            for (int y = minDepth; y <= 60; y += 10) {
                double aquiferValue = aquiferNoise.sample(x, y * 0.67, z);
                if (!checkParameter(ParameterType.AQUIFER, aquiferValue)) {
                    return false;
                }
            }
        }
        return true;
    }
    private boolean checkBedrockImpossible(long seed, int x, int z) {
        // Special check logic for bedrock impossible seeds
        if (Entrance1(seed, x, 45, z) > 0) {
            return false;
        }
        if (Entrance1(seed, x, 55, z) >= 0) {
            return false;
        }
        if (Entrance1(seed, x+5, 55, z) >= 0) {
            return false;
        }
        if (Entrance1(seed, x-5, 55, z) >= 0) {
            return false;
        }
        if (Entrance1(seed, x, 55, z+5) >= 0) {
            return false;
        }
        if (Entrance1(seed, x, 55, z-5) >= 0) {
            return false;
        }
        if (Cheese(seed, x, -50, z) >= -0.2) {
            return false;
        }
        if (Cheese(seed, x, 10, z) >= -0.05) {
            return false;
        }
        if (Cheese(seed, x, 0, z) >= -0.05) {
            return false;
        }
        if (Cheese(seed, x, -10, z) >= -0.05) {
            return false;
        }
        if (Cheese(seed, x, -20, z) >= -0.1) {
            return false;
        }
        if (Cheese(seed, x, -30, z) >= -0.13) {
            return false;
        }
        if (Cheese(seed, x, -40, z) >= -0.13) {
            return false;
        }
        if (Entrance(seed, x, 40, z) >= 0 && Cheese(seed, x, 40, z) >= 0) {
            return false;
        }
        if (Entrance(seed, x, 30, z) >= 0 && Cheese(seed, x, 30, z) >= -0.05) {
            return false;
        }
        if (Entrance(seed, x, 20, z) >= 0 && Cheese(seed, x, 20, z) >= -0.05) {
            return false;
        }
        LazyDoublePerlinNoiseSampler ridgeNoise = LazyDoublePerlinNoiseSampler.createNoiseSampler(
                new Xoroshiro128PlusPlusRandom(seed).createRandomDeriver(),
                NoiseParameterKey.RIDGE
        );
        double ridgeSample = ridgeNoise.sample((double)x/4, 0, (double)z/4);
        if (ridgeSample > -0.15 && ridgeSample < 0.15) {
            return false;
        }
        LazyDoublePerlinNoiseSampler continentalnessNoise = LazyDoublePerlinNoiseSampler.createNoiseSampler(
                new Xoroshiro128PlusPlusRandom(seed).createRandomDeriver(),
                NoiseParameterKey.CONTINENTALNESS
        );
        if (continentalnessNoise.sample((double)x/4, 0, (double)z/4) < -0.12) {
            return false;
        }
        // Check Aquifer
        if (isParameterEnabled()) {
            LazyDoublePerlinNoiseSampler aquiferNoise = LazyDoublePerlinNoiseSampler.createNoiseSampler(
                    new Xoroshiro128PlusPlusRandom(seed).createRandomDeriver(),
                    NoiseParameterKey.AQUIFER_FLUID_LEVEL_FLOODEDNESS
            );
            for (int y = -50; y <= 60; y += 10) {
                if (aquiferNoise.sample((double)x/4, y*0.67, (double)z/4) >0.4) {
                    return false;
                }
            }
        }
        return true;
    }
    private boolean checkHeight(long seed, int x, int z) {
        if (!SeedCheckerInitializer.isInitialized()) {
            log(LanguageResources.get("seedchecker_not_init", currentLanguage));
            return true;
        }
        SeedChecker checker = null;
        try {
            checker = new SeedChecker(seed, TargetState.NO_STRUCTURES, SeedCheckerDimension.OVERWORLD);
            if (bedrockImpossibleCheckBox.isSelected()) {
                // Bedrock impossible mode: use original logic, no type selection
                Box box = new Box(x+8, -54, z+6, x+9, 200, z+7);
                if(checker.getBlockCountInBox(Blocks.AIR, box)<254){
                    return false;
                }else{
                    Box box2 = new Box(x-8, -54, z-6, x-7, 200, z-5);
                    if(checker.getBlockCountInBox(Blocks.AIR, box2)<254){
                        return false;
                    }else{
                        Box box3 = new Box(x+8, -54, z-6, x+9, 200, z-5);
                        if(checker.getBlockCountInBox(Blocks.AIR, box3)<254){
                            return false;
                        }else{
                            Box box4 = new Box(x-8, -54, z+6, x-7, 200, z+7);
                            return checker.getBlockCountInBox(Blocks.AIR, box4)==254;
                        }
                    }
                }
            } else {
                // Check if range check is enabled
                if (rangeCheckCheckBox.isSelected()) {
                    return checkHeightInRange(checker, x, z);
                } else {
                    // Single point check
                    int minDepth = (Integer) depthComboBox.getSelectedItem();
                    if (underwaterHeightRadio.isSelected()) {
                        // Underwater height check
                        Box box = new Box(x, minDepth, z, x+1, 62, z+1);
                        return checker.getBlockCountInBox(Blocks.WATER, box) == 62 - minDepth;
                    } else {
                        // Surface height check (default)
                        Box box = new Box(x, minDepth, z, x+1, 200, z+1);
                        return checker.getBlockCountInBox(Blocks.AIR, box) == 200 - minDepth;
                    }
                }
            }
        } catch (Exception e) {
            log(LanguageResources.get("height_check_failed", currentLanguage) + e.getMessage());
            return false;
        } finally{
            if (checker != null) {
                checker.clearMemory();
            }
        }
    }
    private boolean checkHeightInRange(SeedChecker checker, int baseX, int baseZ) {
        try {
            // Parse range coordinates
            String coordText = rangeCoordinatesField.getText().trim();
            String[] parts = coordText.split("\\s+");
            if (parts.length != 4) {
                log(LanguageResources.get("invalid_range_coord_format", currentLanguage));
                return false;
            }
            int x1 = Integer.parseInt(parts[0]);
            int z1 = Integer.parseInt(parts[1]);
            int x2 = Integer.parseInt(parts[2]);
            int z2 = Integer.parseInt(parts[3]);
            
            // Validate range (max ±16)
            if (Math.abs(x1) > 16 || Math.abs(z1) > 16 || Math.abs(x2) > 16 || Math.abs(z2) > 16) {
                log(LanguageResources.get("range_coord_out_of_range", currentLanguage));
                return false;
            }
            // Validate that max coordinates are greater than min coordinates
            if (x1 >= x2) {
                log(LanguageResources.get("max_x_greater_than_min_x", currentLanguage));
                return false;
            }
            if (z1 >= z2) {
                log(LanguageResources.get("max_z_greater_than_min_z", currentLanguage));
                return false;
            }
            // Calculate actual coordinates
            int minX = baseX + x1;
            int maxX = baseX + x2 + 1;
            int minZ = baseZ + z1;
            int maxZ = baseZ + z2 + 1;
            int minDepth = (Integer) depthComboBox.getSelectedItem();
            String heightType = (String) rangeHeightTypeComboBox.getSelectedItem();
            // Collect heights for all points in range
            List<Integer> heights = new ArrayList<>();
            for (int checkX = minX; checkX < maxX; checkX++) {
                for (int checkZ = minZ; checkZ < maxZ; checkZ++) {
                    boolean isValid;
                    if (underwaterHeightRadio.isSelected()) {
                        // Underwater height: check if water blocks match expected count
                        Box box = new Box(checkX, minDepth, checkZ, checkX+1, 62, checkZ+1);
                        isValid = checker.getBlockCountInBox(Blocks.WATER, box) == 62 - minDepth;
                    } else {
                        // Surface height: check if air blocks match expected count
                        Box box = new Box(checkX, minDepth, checkZ, checkX+1, 200, checkZ+1);
                        isValid = checker.getBlockCountInBox(Blocks.AIR, box) == 200 - minDepth;
                    }
                    
                    if (isValid) {
                        // Find actual height by checking from top to bottom
                        int height = -1;
                        int topY = underwaterHeightRadio.isSelected() ? 61 : 199;
                        for (int y = topY; y >= minDepth; y--) {
                            Box testBox = new Box(checkX, y, checkZ, checkX+1, y+1, checkZ+1);
                            boolean hasBlock = underwaterHeightRadio.isSelected() 
                                ? checker.getBlockCountInBox(Blocks.WATER, testBox) > 0
                                : checker.getBlockCountInBox(Blocks.AIR, testBox) > 0;
                            if (hasBlock) {
                                height = y;
                                break;
                            }
                        }
                        if (height >= 0) {
                            heights.add(height);
                        }
                    }
                }
            }
            if (heights.isEmpty()) {
                return false;
            }
            // Calculate based on height type
            // For range check, we verify that at least one point has valid height
            // The height type selection determines which height value to use for validation
            // Since there's no specific validation condition mentioned, we just check that heights exist
            return switch (heightType) {
                case "最低高度" -> heights.stream().mapToInt(Integer::intValue).min().orElse(-1) >= 0;
                case "平均高度" -> heights.stream().mapToInt(Integer::intValue).average().orElse(-1) >= 0;
                case "最高高度" -> heights.stream().mapToInt(Integer::intValue).max().orElse(-1) >= 0;
                default -> false;
            };
        } catch (NumberFormatException e) {
            log(LanguageResources.get("invalid_number_in_range", currentLanguage) + e.getMessage());
            return false;
        } catch (Exception e) {
            log(LanguageResources.get("range_height_check_failed", currentLanguage) + e.getMessage());
            return false;
        }
    }
    private boolean checkParameter(ParameterType type, double value) {
        for (ParameterControl control : parameterControls) {
            if (control.getType() == type && control.isEnabled()) {
                return control.checkValue(value);
            }
        }
        return true; // If not enabled, pass
    }
    private boolean isParameterEnabled() {
        for (ParameterControl control : parameterControls) {
            if (control.getType() == ParameterType.AQUIFER) {
                return control.isEnabled();
            }
        }
        return false;
    }
    private void writeResult(long seed, BufferedWriter writer, ReentrantLock fileLock) {
        fileLock.lock();
        try {
            writer.write(Long.toString(seed));
            writer.newLine();
            writer.flush();
            log(LanguageResources.get("found_seed", currentLanguage) + seed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            fileLock.unlock();
        }
    }
    private void updateProgress() {
        long completed = completedTasks.incrementAndGet();
        long total = totalTasks.get();
        long currentTime = System.currentTimeMillis();
        // Throttling mechanism: only update GUI when one of the following conditions is met
        // 1. More than UPDATE_INTERVAL_MS milliseconds since last update
        // 2. Completed count increased by more than UPDATE_INTERVAL_COUNT since last update
        long timeSinceLastUpdate = currentTime - lastUpdateTime;
        long countSinceLastUpdate = completed - lastUpdateCompleted;
        boolean shouldUpdate = (timeSinceLastUpdate >= UPDATE_INTERVAL_MS) ||
                (countSinceLastUpdate >= UPDATE_INTERVAL_COUNT);
        if (!shouldUpdate) {
            return; // Skip this update to avoid blocking GUI thread
        }
        // Update throttling variables (use synchronization to avoid race conditions)
        synchronized (this) {
            // Double check to avoid multiple threads updating simultaneously
            if ((currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) &&
                    (completed - lastUpdateCompleted < UPDATE_INTERVAL_COUNT)) {
                return;
            }
            lastUpdateTime = currentTime;
            lastUpdateCompleted = completed;
        }
        long elapsedMs = currentTime - filteringStartTime;
        double elapsedSec = elapsedMs / 1000.0;
        // Calculate speed: in structureSeed mode each seed corresponds to 65536 worldSeeds
        double seedsProcessed = completed;
        if (structureSeedRadio.isSelected()) {
            seedsProcessed = completed * 65536.0;
        }
        double speed = elapsedSec > 0 ? seedsProcessed / elapsedSec : 0;
        // Format elapsed time
        String timeStr = formatElapsedTime(elapsedMs);
        // Format speed
        String speedStr = formatSpeed(speed);
        if (total > 0) {
            // Calculate percentage, handle large values
            final int percentage;
            int calcPercentage;
            if (total > Integer.MAX_VALUE) {
                calcPercentage = (int) ((double) completed * 100.0 / (double) total);
            } else {
                calcPercentage = (int) (completed * 100 / total);
            }
            percentage = Math.max(0, Math.min(100, calcPercentage));
            final long finalTotal = total;
            final long finalCompleted = completed;
            final String finalTimeStr = timeStr;
            final String finalSpeedStr = speedStr;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(percentage);
                statusLabel.setText(String.format(LanguageResources.get("progress_format", currentLanguage),
                        finalCompleted, finalTotal, percentage, finalTimeStr, finalSpeedStr));
            });
        } else if (total == -1) {
            final long finalCompleted = completed;
            final String finalTimeStr = timeStr;
            final String finalSpeedStr = speedStr;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                statusLabel.setText(String.format(LanguageResources.get("completed", currentLanguage),
                        finalCompleted, finalTimeStr, finalSpeedStr));
            });
        }
    }
    private String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        if (hours > 0) {
            return String.format(LanguageResources.get("time_hms", currentLanguage), hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format(LanguageResources.get("time_ms", currentLanguage), minutes, seconds);
        } else {
            return String.format(LanguageResources.get("time_s", currentLanguage), millis / 1000.0);
        }
    }
    private String formatSpeed(double speed) {
        if (speed >= 1_000_000_000) {
            return String.format("%.2fG", speed / 1_000_000_000);
        } else if (speed >= 1_000_000) {
            return String.format("%.2fM", speed / 1_000_000);
        } else if (speed >= 1_000) {
            return String.format("%.2fK", speed / 1_000);
        } else {
            return String.format("%.1f", speed);
        }
    }
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    private static class NoiseCache {
        final LazyDoublePerlinNoiseSampler caveEntrance;
        final LazyDoublePerlinNoiseSampler spaghettiRarity;
        final LazyDoublePerlinNoiseSampler spaghettiThickness;
        final LazyDoublePerlinNoiseSampler spaghetti3D1;
        final LazyDoublePerlinNoiseSampler spaghetti3D2;
        final LazyDoublePerlinNoiseSampler spaghettiRoughnessModulator;
        final LazyDoublePerlinNoiseSampler spaghettiRoughness;
        final LazyDoublePerlinNoiseSampler temperature;
        final LazyDoublePerlinNoiseSampler humidity;
        final LazyDoublePerlinNoiseSampler contientalness;
        final LazyDoublePerlinNoiseSampler erosion;
        final LazyDoublePerlinNoiseSampler ridge;
        NoiseCache(long worldseed) {
            Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(worldseed);
            var deriver = random.createRandomDeriver();
            caveEntrance = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.CAVE_ENTRANCE);
            spaghettiRarity = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_3D_RARITY);
            spaghettiThickness = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_3D_THICKNESS);
            spaghetti3D1 = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_3D_1);
            spaghetti3D2 = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_3D_2);
            spaghettiRoughnessModulator = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_ROUGHNESS_MODULATOR);
            spaghettiRoughness = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.SPAGHETTI_ROUGHNESS);
            temperature = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.TEMPERATURE);
            humidity = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.VEGETATION);
            contientalness = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.CONTINENTALNESS);
            erosion = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.EROSION);
            ridge = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.RIDGE);
        }
    }
    private static class CheeseNoiseCache {
        final LazyDoublePerlinNoiseSampler caveLayer;
        final LazyDoublePerlinNoiseSampler caveCheese;
        CheeseNoiseCache(long worldseed) {
            Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(worldseed);
            var deriver = random.createRandomDeriver();
            caveLayer = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.CAVE_LAYER);
            caveCheese = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.CAVE_CHEESE);
        }
    }
    public static double Entrance(long worldseed, int x, int y, int z) {
        NoiseCache cache = new NoiseCache(worldseed);
        double c = cache.caveEntrance.sample(x * 0.75, y * 0.5, z * 0.75) + 0.37 +
                MathHelper.clampedLerp(0.3, 0.0, (10 + (double)y) / 40.0);
        double d = cache.spaghettiRarity.sample(x * 2, y, z * 2);
        double e = NoiseColumnSampler.CaveScaler.scaleTunnels(d);
        double h = Util.lerpFromProgress(cache.spaghettiThickness, x, y, z, 0.065, 0.088);
        double l = NoiseColumnSampler.sample(cache.spaghetti3D1, x, y, z, e);
        double m = Math.abs(e * l) - h;
        double n = NoiseColumnSampler.sample(cache.spaghetti3D2, x, y, z, e);
        double o = Math.abs(e * n) - h;
        double p = MathHelper.clamp(Math.max(m, o), -1.0, 1.0);
        double q = (-0.05 + (-0.05 * cache.spaghettiRoughnessModulator.sample(x, y, z))) *
                (-0.4 + Math.abs(cache.spaghettiRoughness.sample(x, y, z)));
        return Math.min(c, p + q);
    }
    public static double Cheese(long worldseed, int x, int y, int z) {
        CheeseNoiseCache cache = new CheeseNoiseCache(worldseed);
        double a = 4 * cache.caveLayer.sample(x, y * 8, z) * cache.caveLayer.sample(x, y * 8, z);
        double b = MathHelper.clamp((0.27 + cache.caveCheese.sample(x, y * 0.6666666666666666, z)), -1, 1);
        return a + b;
    }
    public static double Entrance1(long worldseed, int x, int y, int z) {
        NoiseCache cache = new NoiseCache(worldseed);
        return cache.caveEntrance.sample(x * 0.75, y * 0.5, z * 0.75) + 0.37 +
                MathHelper.clampedLerp(0.3, 0.0, (10 + (double)y) / 40.0);
    }
    public static double Entrance2(long worldseed, int x, int y, int z) {
        NoiseCache cache = new NoiseCache(worldseed);
        double d = cache.spaghettiRarity.sample(x * 2, y, z * 2);
        double e = NoiseColumnSampler.CaveScaler.scaleTunnels(d);
        double h = Util.lerpFromProgress(cache.spaghettiThickness, x, y, z, 0.065, 0.088);
        double l = NoiseColumnSampler.sample(cache.spaghetti3D1, x, y, z, e);
        double m = Math.abs(e * l) - h;
        double n = NoiseColumnSampler.sample(cache.spaghetti3D2, x, y, z, e);
        double o = Math.abs(e * n) - h;
        double p = MathHelper.clamp(Math.max(m, o), -1.0, 1.0);
        double q = (-0.05 + (-0.05 * cache.spaghettiRoughnessModulator.sample(x, y, z))) *
                (-0.4 + Math.abs(cache.spaghettiRoughness.sample(x, y, z)));
        return p + q;
    }
    // Parameter control class
    private class ParameterControl {
        private final ParameterType type;
        private final JPanel panel;
        private final JCheckBox enableCheckBox;
        private final JComboBox<ConditionType> conditionComboBox;
        private final JSpinner value1Spinner;
        private final JSpinner value2Spinner;
        private boolean isLocked = false; // Track if externally locked (bedrock impossible mode)
        public ParameterControl(ParameterType type, String label, int fixedWidth) {
            this.type = type;
            // Use GridBagLayout to fix positions
            panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.anchor = GridBagConstraints.WEST;
            // Enable checkbox - use fixed width panel to ensure alignment
            enableCheckBox = new JCheckBox(label);
            JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            checkboxPanel.add(enableCheckBox);
            // Use fixed width to ensure all rows align
            checkboxPanel.setPreferredSize(new Dimension(fixedWidth, 25));
            checkboxPanel.setMaximumSize(new Dimension(fixedWidth, 25));
            checkboxPanel.setMinimumSize(new Dimension(fixedWidth, 25));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(checkboxPanel, gbc);
            // Set available condition types based on parameter type
            conditionComboBox = new JComboBox<>(getAvailableConditions());
            conditionComboBox.setSelectedIndex(0);
            conditionComboBox.setPreferredSize(new Dimension(140, 25)); // Slightly reduce width
            // Set renderer to show translated names immediately
            setConditionTypeRenderer();
            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(conditionComboBox, gbc);
            // Value 1 input (fixed position)
            value1Spinner = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 1.0, 0.01));
            value1Spinner.setPreferredSize(new Dimension(90, 25)); // Slightly reduce width
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(value1Spinner, gbc);
            // Value 2 input (fixed position, always present but may be disabled)
            value2Spinner = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 1.0, 0.01));
            value2Spinner.setPreferredSize(new Dimension(90, 25)); // Slightly reduce width
            gbc.gridx = 3;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(value2Spinner, gbc);
            // Enable/disable second value based on condition type
            conditionComboBox.addActionListener(e -> updateSpinnerVisibility());
            // When checkbox state changes, also need to update second value enabled state
            enableCheckBox.addActionListener(e -> updateSpinnerVisibility());
            // Initial state: set according to default condition type
            updateSpinnerVisibility();
        }
        private void updateSpinnerVisibility() {
            ConditionType condition = (ConditionType) conditionComboBox.getSelectedItem();
            if (condition == null) {
                return; // Prevent null pointer
            }
            boolean needSecond = condition == ConditionType.BETWEEN ||
                    condition == ConditionType.NOT_IN_RANGE ||
                    condition == ConditionType.ABS_IN_RANGE ||
                    condition == ConditionType.ABS_NOT_IN_RANGE;
            // Value 2 is always visible, but enabled/disabled based on condition
            // Also consider whether checkbox is enabled and if externally locked
            value2Spinner.setEnabled(needSecond && enableCheckBox.isSelected() && !isLocked);
        }
        public void setDefaultValues(boolean enabled, ConditionType condition, double value1, double value2) {
            enableCheckBox.setSelected(enabled);
            conditionComboBox.setSelectedItem(condition);
            value1Spinner.setValue(value1);
            value2Spinner.setValue(value2);
            // Ensure visibility state is updated after setting values
            SwingUtilities.invokeLater(this::updateSpinnerVisibility);
            // Also update immediately once to ensure synchronization
            updateSpinnerVisibility();
        }
        public JPanel getPanel() {
            return panel;
        }
        public ParameterType getType() {
            return type;
        }
        public boolean isEnabled() {
            return enableCheckBox.isSelected();
        }
        public void setEnabled(boolean enabled) {
            isLocked = !enabled; // Record locked state
            enableCheckBox.setEnabled(enabled);
            conditionComboBox.setEnabled(enabled);
            value1Spinner.setEnabled(enabled);
            value2Spinner.setEnabled(enabled);
            // Update second value enabled state (considering locked state)
            updateSpinnerVisibility();
        }
        public void updateLanguage() {
            // Update parameter label text
            String labelKey = getParameterLabelKey(type);
            if (labelKey != null) {
                enableCheckBox.setText(LanguageResources.get(labelKey, currentLanguage));
            }
        }
        
        private String getParameterLabelKey(ParameterType type) {
            return switch (type) {
                case TEMPERATURE -> "param_temperature";
                case HUMIDITY -> "param_humidity";
                case EROSION -> "param_erosion";
                case RIDGE -> "param_ridge";
                case ENTRANCE -> "param_entrance";
                case CHEESE -> "param_cheese";
                case CONTINENTALNESS -> "param_continentalness";
                case AQUIFER -> "param_aquifer";
            };
        }
        public void updateConditionTypes() {
            // ParameterControl is inner class, can access currentLanguage directly
            ConditionType selected = (ConditionType) conditionComboBox.getSelectedItem();
            DefaultComboBoxModel<ConditionType> model = new DefaultComboBoxModel<>();
            ConditionType[] availableConditions = getAvailableConditions();
            for (ConditionType ct : availableConditions) {
                model.addElement(ct);
            }
            conditionComboBox.setModel(model);
            if (selected != null) {
                conditionComboBox.setSelectedItem(selected);
            }
            // Update renderer to show translated names
            setConditionTypeRenderer();
        }
        
        private void setConditionTypeRenderer() {
            conditionComboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof ConditionType) {
                        setText(((ConditionType) value).getDisplayName(currentLanguage));
                    }
                    return this;
                }
            });
        }
        private ConditionType[] getAvailableConditions() {
            if (type == ParameterType.RIDGE) {
                return ConditionType.values();
            } else {
                return new ConditionType[]{
                        ConditionType.BETWEEN,
                        ConditionType.GREATER_THAN,
                        ConditionType.LESS_THAN,
                        ConditionType.NOT_IN_RANGE
                };
            }
        }
        public boolean checkValue(double value) {
            if (!isEnabled()) {
                return true;
            }
            ConditionType condition = (ConditionType) conditionComboBox.getSelectedItem();
            double val1 = (Double) value1Spinner.getValue();
            double val2 = (Double) value2Spinner.getValue();
            return switch (condition) {
                case BETWEEN -> value >= Math.min(val1, val2) && value <= Math.max(val1, val2);
                case GREATER_THAN -> value > val1;
                case LESS_THAN -> value < val1;
                case NOT_IN_RANGE -> value < Math.min(val1, val2) || value > Math.max(val1, val2);
                case ABS_IN_RANGE -> {
                    double absValue = Math.abs(value);
                    yield absValue >= Math.min(val1, val2) && absValue <= Math.max(val1, val2);
                }
                case ABS_NOT_IN_RANGE -> {
                    double absValue2 = Math.abs(value);
                    yield absValue2 < Math.min(val1, val2) || absValue2 > Math.max(val1, val2);
                }
            };
        }
    }
    // Language resources management
    private static class LanguageResources {
        private static String get(String key, Language lang) {
            return switch (key) {
                // Window title
                case "title" -> lang == Language.CHINESE ? "洞穴查找器 GUI" : "Cave Finder GUI";
                // Static initialization messages
                case "static_init_start" -> lang == Language.CHINESE ? "CavefinderGUI 静态初始化开始..." : "CavefinderGUI static initialization starting...";
                case "static_init_warning" -> lang == Language.CHINESE ? "CavefinderGUI 静态初始化警告: " : "CavefinderGUI static initialization warning: ";
                // UI Labels
                case "param_settings" -> lang == Language.CHINESE ? "参数设置" : "Parameter Settings";
                case "cave_depth" -> lang == Language.CHINESE ? "洞穴深度:" : "Cave Depth:";
                case "thread_count" -> lang == Language.CHINESE ? "线程数:" : "Thread Count:";
                case "x_coordinate" -> lang == Language.CHINESE ? "X坐标:" : "X Coordinate:";
                case "z_coordinate" -> lang == Language.CHINESE ? "Z坐标:" : "Z Coordinate:";
                case "segment_size" -> lang == Language.CHINESE ? "分段大小:" : "Segment Size:";
                case "segment_size_hint" -> lang == Language.CHINESE ? "(仅递增模式，超过此值将分段处理)" : "(Incremental mode only, will segment if exceeds this value)";
                case "check_height" -> lang == Language.CHINESE ? "筛高度（较慢）" : "Check height (slower)";
                case "filter_be_impossible" -> lang == Language.CHINESE ? "筛基岩版无解种子" : "Filter BE impossible seeds";
                case "entrance1_only" -> lang == Language.CHINESE ? "只筛Entrance1（更大洞穴）" : "Entrance1 only (larger caves)";
                case "filter_mode" -> lang == Language.CHINESE ? "筛选模式" : "Filter Mode";
                case "search_mode" -> lang == Language.CHINESE ? "搜索模式" : "Search Mode";
                case "incremental" -> lang == Language.CHINESE ? "递增筛种" : "Incremental";
                case "filter_from_list" -> lang == Language.CHINESE ? "从列表筛种" : "Filter from list";
                case "seed_type" -> lang == Language.CHINESE ? "种子类型" : "Seed Type";
                case "seed_input" -> lang == Language.CHINESE ? "种子输入" : "Seed Input";
                case "start_seed" -> lang == Language.CHINESE ? "起始种子:" : "Start Seed:";
                case "end_seed" -> lang == Language.CHINESE ? "结束种子:" : "End Seed:";
                case "seed_list" -> lang == Language.CHINESE ? "种子列表（每行一个）:" : "Seed List (one per line):";
                case "load_from_file" -> lang == Language.CHINESE ? "从文件加载" : "Load from file";
                case "biome_params" -> lang == Language.CHINESE ? "群系气候参数" : "Biome Climate Parameters";
                case "log" -> lang == Language.CHINESE ? "日志" : "Log";
                case "export_path" -> lang == Language.CHINESE ? "导出路径:" : "Export Path:";
                case "browse" -> lang == Language.CHINESE ? "浏览..." : "Browse...";
                case "start_filtering" -> lang == Language.CHINESE ? "开始筛选" : "Start Filtering";
                case "stop" -> lang == Language.CHINESE ? "停止" : "Stop";
                case "ready" -> lang == Language.CHINESE ? "就绪" : "Ready";
                // Height check options
                case "height_check_options" -> lang == Language.CHINESE ? "高度检查选项" : "Height Check Options";
                case "height_type" -> lang == Language.CHINESE ? "高度类型:" : "Height Type:";
                case "surface_height" -> lang == Language.CHINESE ? "地表高度" : "Surface Height";
                case "underwater_height" -> lang == Language.CHINESE ? "水下高度" : "Underwater Height";
                case "check_height_in_range" -> lang == Language.CHINESE ? "检查一定范围内的高度" : "Check height in range";
                case "range_height_type" -> lang == Language.CHINESE ? "范围高度类型:" : "Range Height Type:";
                case "range_coordinates" -> lang == Language.CHINESE ? "范围坐标:" : "Range Coordinates:";
                case "range_coord_tooltip" -> lang == Language.CHINESE ? "格式: x1 z1 x2 z2 (相对于基准坐标，最大 ±16)" : "Format: x1 z1 x2 z2 (relative to base coordinates, max ±16)";
                case "min_height" -> lang == Language.CHINESE ? "最低高度" : "Minimum Height";
                case "avg_height" -> lang == Language.CHINESE ? "平均高度" : "Average Height";
                case "max_height" -> lang == Language.CHINESE ? "最高高度" : "Maximum Height";
                // Language selection
                case "language" -> "语言(Language):";
                // Error messages
                case "error" -> lang == Language.CHINESE ? "错误" : "Error";
                case "invalid_seed_numbers" -> lang == Language.CHINESE ? "请输入有效的种子数字" : "Please enter valid seed numbers";
                case "enter_seed_list" -> lang == Language.CHINESE ? "请输入种子列表或从文件加载" : "Please enter seed list or load from file";
                case "cannot_access_dir" -> lang == Language.CHINESE ? "无法访问导出路径的目录：" : "Cannot access directory of export path: ";
                case "please_select_other" -> lang == Language.CHINESE ? "\n请选择其他路径。" : "\nPlease select another path.";
                case "cannot_access_path" -> lang == Language.CHINESE ? "无法访问导出路径：" : "Cannot access export path: ";
                case "access_denied" -> lang == Language.CHINESE ? "\n访问被拒绝。请选择其他路径。" : "\nAccess denied. Please select another path.";
                case "error_colon" -> lang == Language.CHINESE ? "\n错误：" : "\nError: ";
                case "file_exists_overwrite" -> lang == Language.CHINESE ? "结果文件已存在，将被覆盖。是否继续？" : "Result file already exists and will be overwritten. Continue?";
                case "reminder" -> lang == Language.CHINESE ? "提醒" : "Reminder";
                case "load_file_failed" -> lang == Language.CHINESE ? "加载文件失败: " : "Failed to load file: ";
                case "select_export_path" -> lang == Language.CHINESE ? "选择导出路径" : "Select Export Path";
                case "invalid_range_coord_format" -> lang == Language.CHINESE ? "错误: 无效的范围坐标格式。期望格式: x1 z1 x2 z2" : "Error: Invalid range coordinates format. Expected: x1 z1 x2 z2";
                case "range_coord_out_of_range" -> lang == Language.CHINESE ? "错误: 范围坐标必须在 ±16 以内" : "Error: Range coordinates must be within ±16";
                case "max_x_greater_than_min_x" -> lang == Language.CHINESE ? "错误: 最大X坐标应大于最小X坐标" : "Error: Maximum X coordinate should be greater than minimum X coordinate";
                case "max_z_greater_than_min_z" -> lang == Language.CHINESE ? "错误: 最大Z坐标应大于最小Z坐标" : "Error: Maximum Z coordinate should be greater than minimum Z coordinate";
                // Log messages
                case "file_loaded" -> lang == Language.CHINESE ? "已加载文件: " : "Loaded file: ";
                case "filtering_stopped" -> lang == Language.CHINESE ? "筛选已停止" : "Filtering stopped";
                case "checking_seedchecker" -> lang == Language.CHINESE ? "检查 SeedChecker 状态..." : "Checking SeedChecker status...";
                case "attempting_init_seedchecker" -> lang == Language.CHINESE ? "尝试初始化 SeedChecker..." : "Attempting to initialize SeedChecker...";
                case "seedchecker_ready" -> lang == Language.CHINESE ? "SeedChecker 已就绪" : "SeedChecker ready";
                case "seedchecker_init_failed" -> lang == Language.CHINESE ? "警告: SeedChecker 初始化失败，高度检查可能不可用" : "Warning: SeedChecker initialization failed, height check may not be available";
                case "seedchecker_check_exception" -> lang == Language.CHINESE ? "SeedChecker 检查过程中出现异常: " : "Exception during SeedChecker check: ";
                case "starting_filtering" -> lang == Language.CHINESE ? "开始筛选: %d - %d (共 %d 个种子)" : "Starting filtering: %d - %d (Total %d seeds)";
                case "seed_count_exceeds" -> lang == Language.CHINESE ? "种子数量超过 %d，将分 %d 段处理" : "Seed count exceeds %d, will process in %d segments";
                case "processing_segment" -> lang == Language.CHINESE ? "处理第 %d/%d 段: %d - %d" : "Processing segment %d/%d: %d - %d";
                case "starting_list_filtering" -> lang == Language.CHINESE ? "开始筛选列表 (共 %d 行)" : "Starting list filtering (Total %d lines)";
                case "skipping_invalid_seed" -> lang == Language.CHINESE ? "跳过无效种子: " : "Skipping invalid seed: ";
                case "filtering_completed" -> lang == Language.CHINESE ? "筛选完成！结果已保存到 " : "Filtering completed! Results saved to ";
                case "total_time" -> lang == Language.CHINESE ? "总用时: %s, 平均速度: %s seeds/秒" : "Total time: %s, Average speed: %s seeds/second";
                case "error_colon_msg" -> lang == Language.CHINESE ? "错误: " : "Error: ";
                case "seedchecker_not_init" -> lang == Language.CHINESE ? "警告: SeedChecker 未初始化，跳过高度检查" : "Warning: SeedChecker not initialized, skipping height check";
                case "height_check_failed" -> lang == Language.CHINESE ? "错误: 高度检查失败: " : "Error: Height check failed: ";
                case "range_height_check_failed" -> lang == Language.CHINESE ? "错误: 范围高度检查失败: " : "Error: Range height check failed: ";
                case "invalid_number_in_range" -> lang == Language.CHINESE ? "错误: 范围坐标中的无效数字: " : "Error: Invalid number in range coordinates: ";
                case "found_seed" -> lang == Language.CHINESE ? "找到种子: " : "Found seed: ";
                // Status messages
                case "completed" -> lang == Language.CHINESE ? "已完成: %d | 用时: %s | 速度: %s seeds/秒" : "Completed: %d | Time: %s | Speed: %s seeds/second";
                case "progress_format" -> lang == Language.CHINESE ? "%d/%d (%d%%) | 用时: %s | 速度: %s seeds/秒" : "%d/%d (%d%%) | Time: %s | Speed: %s seeds/second";
                // Time format
                case "time_hms" -> lang == Language.CHINESE ? "%d时%02d分%02d秒" : "%dh%02dm%02ds";
                case "time_ms" -> lang == Language.CHINESE ? "%d分%02d秒" : "%dm%02ds";
                case "time_s" -> lang == Language.CHINESE ? "%.1f秒" : "%.1fs";
                // Parameter names
                case "param_temperature" -> lang == Language.CHINESE ? "温度" : "Temperature";
                case "param_humidity" -> lang == Language.CHINESE ? "湿度" : "Humidity";
                case "param_erosion" -> lang == Language.CHINESE ? "侵蚀度" : "Erosion";
                case "param_ridge" -> lang == Language.CHINESE ? "奇异性" : "Weirdness";
                case "param_entrance" -> lang == Language.CHINESE ? "洞穴入口噪声" : "Entrance";
                case "param_cheese" -> lang == Language.CHINESE ? "芝士洞穴噪声" : "Cheese";
                case "param_continentalness" -> lang == Language.CHINESE ? "大陆性" : "Continentalness";
                case "param_aquifer" -> lang == Language.CHINESE ? "含水层洪水水位噪声" : "AquiferFloodLevelFloodness";
                
                // Seed type names
                case "structure_seed" -> lang == Language.CHINESE ? "结构种子(低48位二进制)" : "StructureSeed(low48bit)";
                case "world_seed" -> lang == Language.CHINESE ? "世界种子" : "WorldSeed";
                default -> key;
            };
        }
        static String getConditionTypeName(ConditionType type, Language lang) {
            return switch (type) {
                case BETWEEN -> lang == Language.CHINESE ? "介于两值之间" : "Between two values";
                case GREATER_THAN -> lang == Language.CHINESE ? "大于某值" : "Greater than";
                case LESS_THAN -> lang == Language.CHINESE ? "小于某值" : "Less than";
                case NOT_IN_RANGE -> lang == Language.CHINESE ? "不含某个范围内的值" : "Not in range";
                case ABS_IN_RANGE -> lang == Language.CHINESE ? "绝对值在某个范围" : "Absolute value in range";
                case ABS_NOT_IN_RANGE -> lang == Language.CHINESE ? "绝对值不在某个范围" : "Absolute value not in range";
            };
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new CavefinderGUI().setVisible(true);
        });
    }
}