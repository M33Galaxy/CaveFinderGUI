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
            System.out.println("CavefinderGUI 静态初始化开始...");
            SeedCheckerInitializer.initialize();
        } catch (Exception e) {
            System.err.println("CavefinderGUI 静态初始化警告: " + e.getMessage());
        }
    }
    enum ParameterType {
        TEMPERATURE, HUMIDITY, EROSION, RIDGE, ENTRANCE, CHEESE, CONTINENTALNESS, AQUIFER
    }
    enum ConditionType {
        BETWEEN("介于两值之间"),
        GREATER_THAN("大于某值"),
        LESS_THAN("小于某值"),
        NOT_IN_RANGE("不含某个范围内的值"),
        ABS_IN_RANGE("绝对值在某个范围"),
        ABS_NOT_IN_RANGE("绝对值不在某个范围");

        private final String displayName;

        ConditionType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
    private JComboBox<Integer> depthComboBox;
    private JCheckBox checkHeightCheckBox;
    private JCheckBox bedrockImpossibleCheckBox;
    private JCheckBox entrance1OnlyCheckBox;
    private JRadioButton incrementModeRadio;
    private JRadioButton structureSeedRadio;
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
    private static final long UPDATE_INTERVAL_MS = 100; // GUI更新间隔：100毫秒
    private static final long UPDATE_INTERVAL_COUNT = 1000; // 或每完成1000个任务更新一次
    private String getJarDirectory() {
        try {
            // 获取jar文件路径
            String jarPath = CavefinderGUI.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            // 如果是Windows路径，去掉开头的斜杠
            if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
                jarPath = jarPath.substring(1);
            }
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                // 如果是jar文件，返回其所在目录
                return jarFile.getParent();
            } else {
                // 如果不是jar文件（可能是IDE运行），返回当前工作目录
                return System.getProperty("user.dir");
            }
        } catch (Exception e) {
            // 如果获取失败，返回当前工作目录
            return System.getProperty("user.dir");
        }
    }
    private String getDefaultExportPath() {
        String jarDir = getJarDirectory();
        return new File(jarDir, "result.txt").getAbsolutePath();
    }
    public CavefinderGUI() {
        setTitle("洞穴查找器 GUI");
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
        setSize(1300, 800); // 增加宽度以容纳更宽的参数面板
    }
    private void loadCustomFont() {
        try {
            InputStream fontStream = getClass().getResourceAsStream("/font.ttf");
            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                customFont = baseFont.deriveFont(Font.PLAIN, 12);
                fontStream.close();
            } else {
                // 如果加载失败，使用默认字体
                customFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
        } catch (Exception e) {
            // 如果加载失败，使用默认字体
            customFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            e.printStackTrace();
        }
    }
    private void applyFontToComponent(Component component) {
        if (customFont == null) return;
        // 跳过日志区域，它已经单独设置了字体
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
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // 左侧面板 - 参数设置
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createTitledBorder("参数设置"));
        leftPanel.setPreferredSize(new Dimension(600, Integer.MAX_VALUE));
        // 第一行：洞穴深度和线程数（与坐标对齐）
        JPanel firstRowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc1 = new GridBagConstraints();
        gbc1.insets = new Insets(2, 2, 2, 2);
        gbc1.anchor = GridBagConstraints.WEST;
        // 洞穴深度
        gbc1.gridx = 0;
        gbc1.gridy = 0;
        firstRowPanel.add(new JLabel("洞穴深度:"), gbc1);
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
        // 线程数设置
        gbc1.gridx = 2;
        gbc1.fill = GridBagConstraints.NONE;
        gbc1.weightx = 0;
        firstRowPanel.add(new JLabel("线程数:"), gbc1);
        int maxThreadCount = Runtime.getRuntime().availableProcessors();
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(maxThreadCount, 1, maxThreadCount, 1));
        threadCountSpinner.setPreferredSize(new Dimension(100, 25));
        gbc1.gridx = 3;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        gbc1.weightx = 0;
        firstRowPanel.add(threadCountSpinner, gbc1);
        leftPanel.add(firstRowPanel);
        // 第二行：坐标输入（与第一行对齐）
        JPanel coordinatePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(2, 2, 2, 2);
        gbc2.anchor = GridBagConstraints.WEST;
        gbc2.gridx = 0;
        gbc2.gridy = 0;
        coordinatePanel.add(new JLabel("X坐标:"), gbc2);
        xCoordinateSpinner = new JSpinner(new SpinnerNumberModel(0, -30000000, 30000000, 1));
        xCoordinateSpinner.setPreferredSize(new Dimension(100, 25));
        gbc2.gridx = 1;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.weightx = 0;
        coordinatePanel.add(xCoordinateSpinner, gbc2);
        gbc2.gridx = 2;
        gbc2.fill = GridBagConstraints.NONE;
        gbc2.weightx = 0;
        coordinatePanel.add(new JLabel("Z坐标:"), gbc2);
        zCoordinateSpinner = new JSpinner(new SpinnerNumberModel(0, -30000000, 30000000, 1));
        zCoordinateSpinner.setPreferredSize(new Dimension(100, 25));
        gbc2.gridx = 3;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.weightx = 0;
        coordinatePanel.add(zCoordinateSpinner, gbc2);
        leftPanel.add(coordinatePanel);
        // 第三行：分段大小设置（仅递增模式有效）
        JPanel segmentSizePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc3 = new GridBagConstraints();
        gbc3.insets = new Insets(2, 2, 2, 2);
        gbc3.anchor = GridBagConstraints.WEST;
        gbc3.gridx = 0;
        gbc3.gridy = 0;
        segmentSizePanel.add(new JLabel("分段大小:"), gbc3);
        segmentSizeSpinner = new JSpinner(new SpinnerNumberModel(10_000_000L, 1_000_000L, 1_000_000_000L, 1_000_000L));
        segmentSizeSpinner.setPreferredSize(new Dimension(120, 25));
        gbc3.gridx = 1;
        gbc3.fill = GridBagConstraints.HORIZONTAL;
        gbc3.weightx = 0;
        segmentSizePanel.add(segmentSizeSpinner, gbc3);
        gbc3.gridx = 2;
        gbc3.fill = GridBagConstraints.NONE;
        gbc3.weightx = 0;
        segmentSizePanel.add(new JLabel("(仅递增模式，超过此值将分段处理)"), gbc3);
        leftPanel.add(segmentSizePanel);
        // 选项复选框（放在同一行）
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        checkHeightCheckBox = new JCheckBox("筛高度（较慢）");
        checkboxPanel.add(checkHeightCheckBox);
        bedrockImpossibleCheckBox = new JCheckBox("筛基岩版无解种子");
        bedrockImpossibleCheckBox.addActionListener(e -> updateParameterLockState());
        checkboxPanel.add(bedrockImpossibleCheckBox);
        entrance1OnlyCheckBox = new JCheckBox("只筛Entrance1（更大洞穴）");
        checkboxPanel.add(entrance1OnlyCheckBox);
        leftPanel.add(checkboxPanel);
        // 筛选模式
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new GridLayout(1, 2, 10, 0));
        modePanel.setBorder(BorderFactory.createTitledBorder("筛选模式"));
        // 第一列：搜索模式
        JPanel searchModePanel = new JPanel();
        searchModePanel.setLayout(new BoxLayout(searchModePanel, BoxLayout.Y_AXIS));
        searchModePanel.setBorder(BorderFactory.createTitledBorder("搜索模式"));
        ButtonGroup searchModeGroup = new ButtonGroup();
        incrementModeRadio = new JRadioButton("递增筛种", true);
        JRadioButton listModeRadio = new JRadioButton("从列表筛种");
        searchModeGroup.add(incrementModeRadio);
        searchModeGroup.add(listModeRadio);
        searchModePanel.add(incrementModeRadio);
        searchModePanel.add(listModeRadio);
        // 第二列：种子类型
        JPanel seedTypePanel = new JPanel();
        seedTypePanel.setLayout(new BoxLayout(seedTypePanel, BoxLayout.Y_AXIS));
        seedTypePanel.setBorder(BorderFactory.createTitledBorder("种子类型"));
        ButtonGroup seedTypeGroup = new ButtonGroup();
        structureSeedRadio = new JRadioButton("StructureSeed", true);
        JRadioButton worldSeedRadio = new JRadioButton("WorldSeed");
        seedTypeGroup.add(structureSeedRadio);
        seedTypeGroup.add(worldSeedRadio);
        seedTypePanel.add(structureSeedRadio);
        seedTypePanel.add(worldSeedRadio);
        modePanel.add(searchModePanel);
        modePanel.add(seedTypePanel);
        leftPanel.add(modePanel);
        // 种子输入区域
        JPanel seedInputPanel = new JPanel();
        seedInputPanel.setLayout(new BoxLayout(seedInputPanel, BoxLayout.Y_AXIS));
        seedInputPanel.setBorder(BorderFactory.createTitledBorder("种子输入"));
        JPanel incrementPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        incrementPanel.add(new JLabel("起始种子:"), gbc);
        startSeedField = new JTextField(20);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        incrementPanel.add(startSeedField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        incrementPanel.add(new JLabel("结束种子:"), gbc);
        endSeedField = new JTextField(20);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        incrementPanel.add(endSeedField, gbc);
        seedInputPanel.add(incrementPanel);
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(new JLabel("种子列表（每行一个）:"), BorderLayout.NORTH);
        seedListArea = new JTextArea(12, 40); // 增加高度从8行到12行，宽度从30列到40列
        seedListArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(seedListArea);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        JButton loadFileButton = new JButton("从文件加载");
        loadFileButton.addActionListener(e -> loadSeedFile());
        listPanel.add(loadFileButton, BorderLayout.SOUTH);
        seedInputPanel.add(listPanel);
        leftPanel.add(seedInputPanel);
        // 参数条件面板
        parameterPanel = new JPanel();
        parameterPanel.setLayout(new BoxLayout(parameterPanel, BoxLayout.Y_AXIS));
        parameterPanel.setBorder(BorderFactory.createTitledBorder("群系气候参数"));
        // 按顺序添加参数
        addParameterControl(ParameterType.TEMPERATURE, "Temperature");
        addParameterControl(ParameterType.HUMIDITY, "Humidity");
        addParameterControl(ParameterType.EROSION, "Erosion");
        addParameterControl(ParameterType.RIDGE, "Ridge (Weirdness)");
        addParameterControl(ParameterType.ENTRANCE, "Entrance");
        addParameterControl(ParameterType.CHEESE, "Cheese");
        addParameterControl(ParameterType.CONTINENTALNESS, "Continentalness");
        addParameterControl(ParameterType.AQUIFER, "AquiferFloodlevelFloodness");
        JScrollPane paramScrollPane = new JScrollPane(parameterPanel);
        paramScrollPane.setPreferredSize(new Dimension(750, 300)); // 增加宽度以容纳所有组件，包括第二个值
        // 右侧面板 - 参数和日志
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(750, Integer.MAX_VALUE));
        rightPanel.add(paramScrollPane, BorderLayout.CENTER);
        // 日志区域
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("日志"));
        logArea = new JTextArea(15, 25); // 增加高度从10行到15行
        logArea.setEditable(false);
        // 日志区域使用自定义字体（如果已加载），否则使用等宽字体
        logArea.setFont(Objects.requireNonNullElseGet(customFont, () -> new Font(Font.MONOSPACED, Font.PLAIN, 12)));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        rightPanel.add(logPanel, BorderLayout.SOUTH);
        // 控制按钮和进度
        JPanel controlPanel = new JPanel(new BorderLayout());
        // 导出路径选择
        JPanel exportPanel = new JPanel(new BorderLayout(5, 0));
        exportPanel.add(new JLabel("导出路径:"), BorderLayout.WEST);
        exportPathField = new JTextField(getDefaultExportPath());
        exportPanel.add(exportPathField, BorderLayout.CENTER);
        JButton browseExportPathButton = new JButton("浏览...");
        browseExportPathButton.addActionListener(e -> browseExportPath());
        exportPanel.add(browseExportPathButton, BorderLayout.EAST);
        // 按钮面板（放在导出路径下面，居中显示）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        startButton = new JButton("开始筛选");
        startButton.addActionListener(e -> startFiltering());
        stopButton = new JButton("停止");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopFiltering());
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("就绪");
        // 组装控制面板
        JPanel topControlPanel = new JPanel(new BorderLayout());
        topControlPanel.add(exportPanel, BorderLayout.NORTH);
        topControlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(topControlPanel, BorderLayout.NORTH);
        controlPanel.add(progressBar, BorderLayout.CENTER);
        controlPanel.add(statusLabel, BorderLayout.SOUTH);
        // 组装主面板
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        add(mainPanel);
    }
    // 所有参数标签的最大宽度（用于对齐）
    private static final int MAX_LABEL_WIDTH = 280;
    private void addParameterControl(ParameterType type, String label) {
        ParameterControl control = new ParameterControl(type, label, MAX_LABEL_WIDTH);
        // 根据参数类型设置默认值
        switch (type) {
            case CONTINENTALNESS ->
                // 默认排除<-0.11的（即只接受>= -0.11的值，使用BETWEEN [-0.11, 1.0]）
                    control.setDefaultValues(true, ConditionType.GREATER_THAN, -0.11, 1.0);
            case RIDGE ->
                // 默认排除在-0.16到0.16之间的值
                    control.setDefaultValues(true, ConditionType.NOT_IN_RANGE, -0.16, 0.16);
            case ENTRANCE, CHEESE ->
                // 默认是<0（小于0）
                    control.setDefaultValues(true, ConditionType.LESS_THAN, 0.0, 0.0);
            case AQUIFER ->
                // 默认是<0.4（使用BETWEEN [-1.0, 0.4]）
                    control.setDefaultValues(true, ConditionType.LESS_THAN, 0.4, 0.4);
            default -> {
            }
            // 其他参数默认不启用
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
                log("已加载文件: " + file.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "加载文件失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void browseExportPath() {
        JFileChooser exportChooser = new JFileChooser();
        exportChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        exportChooser.setDialogTitle("选择导出路径");
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
        // 锁定"只筛Entrance1"选项
        entrance1OnlyCheckBox.setEnabled(!locked);
    }
    private void startFiltering() {
        if (isRunning) {
            return;
        }
        // 验证输入
        if (incrementModeRadio.isSelected()) {
            try {
                Long.parseLong(startSeedField.getText());
                Long.parseLong(endSeedField.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "请输入有效的种子数字",
                        "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            if (seedListArea.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入种子列表或从文件加载",
                        "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        // 检查结果文件
        String exportPath = exportPathField.getText().trim();
        if (exportPath.isEmpty()) {
            exportPath = getDefaultExportPath();
            exportPathField.setText(exportPath);
        }
        Path resultPath = Paths.get(exportPath);
        // 检查路径是否可以访问
        try {
            // 检查父目录是否存在且可写
            Path parentPath = resultPath.getParent();
            if (parentPath != null) {
                if (!Files.exists(parentPath)) {
                    // 尝试创建目录
                    Files.createDirectories(parentPath);
                }
                // 检查目录是否可写
                if (!Files.isWritable(parentPath)) {
                    JOptionPane.showMessageDialog(this,
                            "无法访问导出路径的目录：" + parentPath + "\n请选择其他路径。",
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        } catch (AccessDeniedException e) {
            JOptionPane.showMessageDialog(this,
                    "无法访问导出路径：" + exportPath + "\n访问被拒绝。请选择其他路径。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "无法访问导出路径：" + exportPath + "\n错误：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (Files.exists(resultPath)) {
            int result = JOptionPane.showConfirmDialog(this,
                    "结果文件已存在，将被覆盖。是否继续？", "提醒",
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
        // 启动筛选线程
        new Thread(this::runFiltering).start();
    }
    private void stopFiltering() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null; // 释放引用，帮助GC回收
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        log("筛选已停止");
    }
    private void runFiltering() {
        try {
            String exportPath = exportPathField.getText().trim();
            if (exportPath.isEmpty()) {
                exportPath = getDefaultExportPath();
            }
            final String finalExportPath = exportPath; // 用于lambda表达式
            Path resultPath = Paths.get(exportPath);
            // 再次检查路径是否可以访问（在后台线程中）
            try {
                Path parentPath = resultPath.getParent();
                if (parentPath != null) {
                    if (!Files.exists(parentPath)) {
                        Files.createDirectories(parentPath);
                    }
                    if (!Files.isWritable(parentPath)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                "无法访问导出路径的目录：" + parentPath + "\n请选择其他路径。",
                                "错误", JOptionPane.ERROR_MESSAGE));
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
                        "无法访问导出路径：" + finalExportPath + "\n访问被拒绝。请选择其他路径。",
                        "错误", JOptionPane.ERROR_MESSAGE));
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    isRunning = false;
                });
                return;
            } catch (Exception e) {
                final Exception finalException = e; // 用于lambda表达式
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "无法访问导出路径：" + finalExportPath + "\n错误：" + finalException.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE));
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
            // 确保目录存在
            if (resultPath.getParent() != null) {
                Files.createDirectories(resultPath.getParent());
            }
            // 获取坐标
            int x = (Integer) xCoordinateSpinner.getValue();
            int z = (Integer) zCoordinateSpinner.getValue();

            // 计算总任务数（用于进度显示）
            long totalTaskCount = 0;
            if (incrementModeRadio.isSelected()) {
                long start = Long.parseLong(startSeedField.getText());
                long end = Long.parseLong(endSeedField.getText());
                // 计算范围，处理可能的溢出
                if (end >= start) {
                    totalTaskCount = end - start + 1;
                    // 如果范围太大导致溢出，设置为-1表示未知总数
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
                            // 跳过无效种子
                        }
                    }
                }
            }
            totalTasks.set(totalTaskCount);
            completedTasks.set(0);
            // 如果启用了高度检查，在主线程中预先初始化 SeedCheckerSettings
            // 这样可以避免多线程并发初始化导致的 ExceptionInInitializerError
            if (checkHeightCheckBox.isSelected()) {
                try {
                    log("检查 SeedChecker 状态...");
                    // 如果还没有初始化，尝试初始化
                    if (!SeedCheckerInitializer.isInitialized()) {
                        log("尝试初始化 SeedChecker...");
                        SeedCheckerInitializer.initialize();
                    }
                    if (SeedCheckerInitializer.isInitialized()) {
                        log("SeedChecker 已就绪");
                    } else {
                        log("警告: SeedChecker 初始化失败，高度检查可能不可用");
                    }
                } catch (Exception e) {
                    log("SeedChecker 检查过程中出现异常: " + e.getMessage());
                }
            }
            int threadCount = (Integer) threadCountSpinner.getValue();
            // SpinnerNumberModel可能返回Integer、Long或Double，使用Number类型安全转换
            long segmentSize = ((Number) segmentSizeSpinner.getValue()).longValue();
            ReentrantLock fileLock = new ReentrantLock();

            try (BufferedWriter writer = Files.newBufferedWriter(resultPath)) {
                if (incrementModeRadio.isSelected()) {
                    // 递增模式
                    long start = Long.parseLong(startSeedField.getText());
                    long end = Long.parseLong(endSeedField.getText());
                    long totalCount = end - start + 1;

                    log(String.format("开始筛选: %d - %d (共 %d 个种子)", start, end, totalCount));

                    if (totalCount <= segmentSize) {
                        // 小于等于分段大小：使用简单高效方案
                        executor = Executors.newFixedThreadPool(threadCount);
                        if (structureSeedRadio.isSelected()) {
                            // StructureSeed模式：每个任务处理1个structureSeed（内部处理65536个worldSeed）
                            for (long seed = start; seed <= end && isRunning; seed++) {
                                final long finalSeed = seed;
                                executor.execute(() -> processStructureSeed(finalSeed, x, z, writer, fileLock));
                            }
                        } else {
                            // WorldSeed模式：批量处理，减少任务数和调度开销
                            final long BATCH_SIZE_WS = 1000; // 每批处理1000个worldSeed
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
                        // 大于分段大小：分段处理，每段结束后彻底清理
                        long segmentCount = (totalCount + segmentSize - 1) / segmentSize;
                        log(String.format("种子数量超过 %d，将分 %d 段处理", segmentSize, segmentCount));

                        long currentStart = start;
                        int segmentIndex = 1;

                        while (currentStart <= end && isRunning) {
                            long currentEnd = Math.min(currentStart + segmentSize - 1, end);
                            log(String.format("处理第 %d/%d 段: %d - %d", segmentIndex, segmentCount, currentStart, currentEnd));

                            // 创建新的线程池处理当前段
                            executor = Executors.newFixedThreadPool(threadCount);
                            if (structureSeedRadio.isSelected()) {
                                // StructureSeed模式：每个任务处理1个structureSeed
                                for (long seed = currentStart; seed <= currentEnd && isRunning; seed++) {
                                    final long finalSeed = seed;
                                    executor.execute(() -> processStructureSeed(finalSeed, x, z, writer, fileLock));
                                }
                            } else {
                                // WorldSeed模式：批量处理，减少任务数和调度开销
                                final long BATCH_SIZE_WS = 1000; // 每批处理1000个worldSeed
                                long batchStart = currentStart;
                                while (batchStart <= currentEnd && isRunning) {
                                    final long batchStartFinal = batchStart;
                                    final long batchEnd = Math.min(batchStart + BATCH_SIZE_WS - 1, currentEnd);
                                    executor.execute(() -> processWorldSeedsBatch(batchStartFinal, batchEnd, x, z, writer, fileLock));
                                    batchStart = batchEnd + 1;
                                }
                            }

                            // 等待当前段完成
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

                            // 彻底清理内存
                            executor = null;
                            System.gc(); // 建议JVM进行垃圾回收

                            currentStart = currentEnd + 1;
                            segmentIndex++;
                        }
                    }
                } else {
                    // 列表模式
                    executor = Executors.newFixedThreadPool(threadCount);
                    String[] lines = seedListArea.getText().split("\n");
                    log(String.format("开始筛选列表 (共 %d 行)", lines.length));

                    if (structureSeedRadio.isSelected()) {
                        // StructureSeed模式：每个任务处理1个structureSeed
                        for (String line : lines) {
                            if (!isRunning) break;
                            line = line.trim();
                            if (!line.isEmpty()) {
                                try {
                                    final long seed = Long.parseLong(line);
                                    executor.execute(() -> processStructureSeed(seed, x, z, writer, fileLock));
                                } catch (NumberFormatException e) {
                                    log("跳过无效种子: " + line);
                                }
                            }
                        }
                    } else {
                        // WorldSeed模式：批量处理，减少任务数和调度开销
                        final long BATCH_SIZE_WS = 1000; // 每批处理1000个worldSeed
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
                                            // 批量更新进度
                                            if (processedCount > 0) {
                                                completedTasks.addAndGet(processedCount - 1);
                                                updateProgress();
                                            }
                                        });
                                        batch.clear();
                                    }
                                } catch (NumberFormatException e) {
                                    log("跳过无效种子: " + line);
                                }
                            }
                        }
                        // 处理剩余的种子
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
                                // 批量更新进度
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

                log("筛选完成！结果已保存到 " + exportPath);
                log(String.format("总用时: %s, 平均速度: %s seeds/秒", totalTimeStr, avgSpeedStr));
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    isRunning = false;
                });
            }
        } catch (Exception e) {
            log("错误: " + e.getMessage());
            e.printStackTrace();
            // 确保异常时也释放executor
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
    // 批量处理WorldSeed，减少任务数和调度开销
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
        // 批量更新进度：直接增加completedTasks，然后调用一次updateProgress()
        // 注意：updateProgress()内部会incrementAndGet()，所以这里先增加(processedCount-1)，让updateProgress()增加最后一个
        if (processedCount > 0) {
            completedTasks.addAndGet(processedCount - 1);
            updateProgress(); // 这会再增加1，总共增加processedCount
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
        // 检查temperature和humidity（始终在洞穴筛选前）
        if (!checkParameter(ParameterType.TEMPERATURE, cache.temperature.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        if (!checkParameter(ParameterType.HUMIDITY, cache.humidity.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        // 如果只筛Entrance1，将erosion和ridge移到洞穴筛选后
        if (!entrance1Only) {
            if (!checkParameter(ParameterType.EROSION, cache.erosion.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
            if (!checkParameter(ParameterType.RIDGE, cache.ridge.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
        }
        // 检查洞穴深度
        int minDepth = (Integer) depthComboBox.getSelectedItem();
        // 检查Entrance和Cheese
        // Entrance的50和60总是先检查，Cheese不检查50和60
        // 在40及以下的高度Entrance和Cheese之间是"或"的关系（同一高度两个满足一个即算满足）
        // 先检查60和50高度的Entrance（不检查Cheese）
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
        // 检查40及以下的高度：Entrance和Cheese是"或"的关系
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
                    // Entrance1模式：去掉Entrance2检查，只检查Cheese
                    double cheese = Cheese(seed, x, y, z);
                    cheesePass = checkParameter(ParameterType.CHEESE, cheese);
                    if (!cheesePass) {
                        return false;
                    }
                    continue; // 只检查Cheese，已经检查完毕
                } else {
                    // 正常模式：检查Entrance2和Cheese，"或"的关系
                    double entrance2 = Entrance2(seed, x, y, z);
                    entrancePass = checkParameter(ParameterType.ENTRANCE, entrance2);
                    double cheese = Cheese(seed, x, y, z);
                    cheesePass = checkParameter(ParameterType.CHEESE, cheese);
                }
            }
            // "或"的关系：两个满足一个即算满足（通过检查）
            if (!entrancePass && !cheesePass) {
                return false;
            }
        }
        // 如果只筛Entrance1，将erosion和ridge移到洞穴筛选后
        if (entrance1Only) {
            if (!checkParameter(ParameterType.EROSION, cache.erosion.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
            if (!checkParameter(ParameterType.RIDGE, cache.ridge.sample((double)x/4, 0, (double)z/4))) {
                return false;
            }
        }
        // Continentalness单独移动到洞穴深度的后面
        if (!checkParameter(ParameterType.CONTINENTALNESS, cache.contientalness.sample((double)x/4, 0, (double)z/4))) {
            return false;
        }
        // 检查Aquifer
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
        // 基岩版无解种子的特殊检查逻辑
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
        // 检查Aquifer
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
            log("警告: SeedChecker 未初始化，跳过高度检查");
            return true;
        }
        SeedChecker checker = null;
        try {
            checker = new SeedChecker(seed, TargetState.NO_STRUCTURES, SeedCheckerDimension.OVERWORLD);
            if (bedrockImpossibleCheckBox.isSelected()) {
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
            }else {
                int minDepth = (Integer) depthComboBox.getSelectedItem();
                Box box = new Box(x, minDepth, z, x+1, 200, z+1);
                return checker.getBlockCountInBox(Blocks.AIR, box) == 200 - minDepth;
            }
        } catch (Exception e) {
            log("错误: 高度检查失败: " + e.getMessage());
            return false;
        } finally{
            if (checker != null) {
                checker.clearMemory();
            }
        }
    }
    private boolean checkParameter(ParameterType type, double value) {
        for (ParameterControl control : parameterControls) {
            if (control.getType() == type && control.isEnabled()) {
                return control.checkValue(value);
            }
        }
        return true; // 如果未启用，则通过
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
            log("找到种子: " + seed);
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
        // 节流机制：只有当满足以下条件之一时才更新GUI
        // 1. 距离上次更新超过UPDATE_INTERVAL_MS毫秒
        // 2. 完成数量比上次更新时增加了UPDATE_INTERVAL_COUNT个以上
        long timeSinceLastUpdate = currentTime - lastUpdateTime;
        long countSinceLastUpdate = completed - lastUpdateCompleted;
        boolean shouldUpdate = (timeSinceLastUpdate >= UPDATE_INTERVAL_MS) ||
                (countSinceLastUpdate >= UPDATE_INTERVAL_COUNT);
        if (!shouldUpdate) {
            return; // 跳过本次更新，避免GUI线程被阻塞
        }
        // 更新节流变量（使用同步避免竞态条件）
        synchronized (this) {
            // 双重检查，避免多个线程同时更新
            if ((currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) &&
                    (completed - lastUpdateCompleted < UPDATE_INTERVAL_COUNT)) {
                return;
            }
            lastUpdateTime = currentTime;
            lastUpdateCompleted = completed;
        }
        long elapsedMs = currentTime - filteringStartTime;
        double elapsedSec = elapsedMs / 1000.0;
        // 计算速度：structureSeed模式下每个种子对应65536个worldSeed
        double seedsProcessed = completed;
        if (structureSeedRadio.isSelected()) {
            seedsProcessed = completed * 65536.0;
        }
        double speed = elapsedSec > 0 ? seedsProcessed / elapsedSec : 0;
        // 格式化用时
        String timeStr = formatElapsedTime(elapsedMs);
        // 格式化速度
        String speedStr = formatSpeed(speed);
        if (total > 0) {
            // 计算百分比，处理大数值
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
                statusLabel.setText(String.format("%d/%d (%d%%) | 用时: %s | 速度: %s seeds/秒",
                        finalCompleted, finalTotal, percentage, finalTimeStr, finalSpeedStr));
            });
        } else if (total == -1) {
            final long finalCompleted = completed;
            final String finalTimeStr = timeStr;
            final String finalSpeedStr = speedStr;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                statusLabel.setText(String.format("已完成: %d | 用时: %s | 速度: %s seeds/秒",
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
            return String.format("%d时%02d分%02d秒", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d分%02d秒", minutes, seconds);
        } else {
            return String.format("%.1f秒", millis / 1000.0);
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
    // 参数控制类
    private class ParameterControl {
        private final ParameterType type;
        private final JPanel panel;
        private final JCheckBox enableCheckBox;
        private final JComboBox<ConditionType> conditionComboBox;
        private final JSpinner value1Spinner;
        private final JSpinner value2Spinner;
        private boolean isLocked = false; // 跟踪是否被外部锁定（基岩版无解模式）
        public ParameterControl(ParameterType type, String label, int fixedWidth) {
            this.type = type;
            // 使用GridBagLayout来固定位置
            panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.anchor = GridBagConstraints.WEST;
            // 启用复选框 - 使用固定宽度的面板来确保对齐
            enableCheckBox = new JCheckBox(label);
            JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            checkboxPanel.add(enableCheckBox);
            // 使用固定宽度确保所有行对齐
            checkboxPanel.setPreferredSize(new Dimension(fixedWidth, 25));
            checkboxPanel.setMaximumSize(new Dimension(fixedWidth, 25));
            checkboxPanel.setMinimumSize(new Dimension(fixedWidth, 25));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(checkboxPanel, gbc);
            // 根据参数类型设置可用的条件类型
            ConditionType[] availableConditions;
            if (type == ParameterType.RIDGE) {
                availableConditions = ConditionType.values(); // Ridge支持所有条件包括绝对值
            } else {
                // 其他参数不支持绝对值条件
                availableConditions = new ConditionType[]{
                        ConditionType.BETWEEN,
                        ConditionType.GREATER_THAN,
                        ConditionType.LESS_THAN,
                        ConditionType.NOT_IN_RANGE
                };
            }
            conditionComboBox = new JComboBox<>(availableConditions);
            conditionComboBox.setSelectedIndex(0);
            conditionComboBox.setPreferredSize(new Dimension(140, 25)); // 稍微减小宽度
            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(conditionComboBox, gbc);
            // 值1输入框（固定位置）
            value1Spinner = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 1.0, 0.01));
            value1Spinner.setPreferredSize(new Dimension(90, 25)); // 稍微减小宽度
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(value1Spinner, gbc);
            // 值2输入框（固定位置，始终存在但可能被禁用）
            value2Spinner = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 1.0, 0.01));
            value2Spinner.setPreferredSize(new Dimension(90, 25)); // 稍微减小宽度
            gbc.gridx = 3;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(value2Spinner, gbc);
            // 根据条件类型启用/禁用第二个值
            conditionComboBox.addActionListener(e -> updateSpinnerVisibility());
            // 当复选框状态改变时，也需要更新第二个值的启用状态
            enableCheckBox.addActionListener(e -> updateSpinnerVisibility());
            // 初始状态：根据默认条件类型设置
            updateSpinnerVisibility();
        }
        private void updateSpinnerVisibility() {
            ConditionType condition = (ConditionType) conditionComboBox.getSelectedItem();
            if (condition == null) {
                return; // 防止空指针
            }
            boolean needSecond = condition == ConditionType.BETWEEN ||
                    condition == ConditionType.NOT_IN_RANGE ||
                    condition == ConditionType.ABS_IN_RANGE ||
                    condition == ConditionType.ABS_NOT_IN_RANGE;
            // 值2始终显示，但根据条件启用/禁用
            // 同时也要考虑复选框是否启用，以及是否被外部锁定
            value2Spinner.setEnabled(needSecond && enableCheckBox.isSelected() && !isLocked);
        }
        public void setDefaultValues(boolean enabled, ConditionType condition, double value1, double value2) {
            enableCheckBox.setSelected(enabled);
            conditionComboBox.setSelectedItem(condition);
            value1Spinner.setValue(value1);
            value2Spinner.setValue(value2);
            // 确保在设置值后更新可见性状态
            SwingUtilities.invokeLater(this::updateSpinnerVisibility);
            // 也立即更新一次，确保同步
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
            isLocked = !enabled; // 记录锁定状态
            enableCheckBox.setEnabled(enabled);
            conditionComboBox.setEnabled(enabled);
            value1Spinner.setEnabled(enabled);
            value2Spinner.setEnabled(enabled);
            // 更新第二个值的启用状态（考虑锁定状态）
            updateSpinnerVisibility();
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