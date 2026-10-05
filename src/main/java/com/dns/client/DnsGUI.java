package com.dns.client;

import com.dns.cache.CacheEntry;
import com.dns.cache.DnsCache;
import com.dns.protocol.DnsHeader;
import com.dns.protocol.DnsRecord;
import com.dns.server.DnsServer;
import com.dns.util.Logger;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modern, high-performance Swing GUI for DNS Cache Resolver.
 * Features live cache statistics, performance metrics, table visualizers,
 * server controls, and real-time logging.
 */
public class DnsGUI extends JFrame {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Modern Palette
    private static final Color BG_DARK = new Color(15, 23, 42);          // slate-900
    private static final Color CARD_BG = new Color(30, 41, 59);          // slate-800
    private static final Color CARD_BORDER = new Color(51, 65, 85);      // slate-700
    private static final Color TEXT_MAIN = new Color(241, 245, 249);     // slate-100
    private static final Color TEXT_MUTED = new Color(148, 163, 184);    // slate-400
    private static final Color ACCENT_CYAN = new Color(56, 189, 248);    // sky-400
    private static final Color ACCENT_GREEN = new Color(52, 211, 153);   // emerald-400
    private static final Color ACCENT_ROSE = new Color(251, 113, 133);   // rose-400
    private static final Color ACCENT_PURPLE = new Color(167, 139, 250); // violet-400
    private static final Color ACCENT_AMBER = new Color(251, 191, 36);   // amber-400

    private final DnsCache cache;
    private final DnsClient client;
    private final DnsServer server;

    // UI Components
    private JTextField domainField;
    private JComboBox<String> recordTypeCombo;
    private JComboBox<String> upstreamCombo;
    private JCheckBox useCacheCheckBox;
    private JButton lookupButton;
    private JButton benchmarkButton;
    private JButton serverToggleButton;
    private JLabel serverStatusBadge;

    // Stat card labels
    private JLabel hitsLabel;
    private JLabel missesLabel;
    private JLabel ratioLabel;
    private JLabel activeEntriesLabel;
    private JLabel expiredEntriesLabel;

    // Performance bar
    private JLabel perfTimeLabel;
    private JLabel perfSourceLabel;
    private JLabel perfSpeedupLabel;

    // Tables
    private DefaultTableModel resultTableModel;
    private DefaultTableModel cacheTableModel;
    private DefaultTableModel historyTableModel;
    private JTable cacheTable;

    // Log Area
    private JTextArea logTextArea;
    private JCheckBox autoScrollCheckBox;

    private int historyCounter = 0;

    public DnsGUI() {
        super("DNS Cache Resolver — High Performance UDP Resolution");

        this.cache = new DnsCache(true);
        this.client = new DnsClient(cache);
        this.server = new DnsServer(cache);

        initTheme();
        initUI();
        initListeners();
        startPeriodicRefreshTimer();

        Logger.info("DNS Cache Resolver GUI initialized successfully.");
    }

    private void initTheme() {
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Table.background", CARD_BG);
        UIManager.put("Table.foreground", TEXT_MAIN);
        UIManager.put("Table.gridColor", CARD_BORDER);
        UIManager.put("Table.selectionBackground", new Color(51, 65, 85));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", new Color(20, 29, 47));
        UIManager.put("TableHeader.foreground", TEXT_MAIN);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 820);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(14, 16, 14, 16));

        // 1. Top Section: Header & Server Controls
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Center Section: Query inputs, Stats Cards, and Main Tabs
        JPanel centerContent = new JPanel(new BorderLayout(10, 10));
        centerContent.setBackground(BG_DARK);

        JPanel queryAndStatsPanel = new JPanel();
        queryAndStatsPanel.setLayout(new BoxLayout(queryAndStatsPanel, BoxLayout.Y_AXIS));
        queryAndStatsPanel.setBackground(BG_DARK);

        queryAndStatsPanel.add(createQueryPanel());
        queryAndStatsPanel.add(Box.createVerticalStrut(10));
        queryAndStatsPanel.add(createStatsPanel());
        queryAndStatsPanel.add(Box.createVerticalStrut(10));
        queryAndStatsPanel.add(createPerformanceBanner());
        queryAndStatsPanel.add(Box.createVerticalStrut(8));

        centerContent.add(queryAndStatsPanel, BorderLayout.NORTH);
        centerContent.add(createTabsPanel(), BorderLayout.CENTER);

        mainPanel.add(centerContent, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));

        // Title and Subtitle
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setBackground(BG_DARK);

        JLabel iconLabel = new JLabel("⚡");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));

        JLabel titleLabel = new JLabel("DNS Cache Resolver");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_MAIN);

        JLabel subtitleLabel = new JLabel("Raw UDP Sockets • RFC 1035 • In-Memory Cache • Real-time TTL");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(TEXT_MUTED);

        JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 0));
        textStack.setBackground(BG_DARK);
        textStack.add(titleLabel);
        textStack.add(subtitleLabel);

        titlePanel.add(iconLabel);
        titlePanel.add(textStack);

        // Right side: Local UDP Server Controls
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        serverPanel.setBackground(BG_DARK);

        serverStatusBadge = new JLabel(" Server: STOPPED ");
        serverStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        serverStatusBadge.setForeground(ACCENT_ROSE);
        serverStatusBadge.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_ROSE, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));

        serverToggleButton = new JButton("Start Local DNS Server (Port 5354)");
        serverToggleButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        serverToggleButton.setBackground(new Color(16, 185, 129));
        serverToggleButton.setForeground(Color.WHITE);
        serverToggleButton.setFocusPainted(false);
        serverToggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverToggleButton.addActionListener(e -> toggleServer());

        serverPanel.add(serverStatusBadge);
        serverPanel.add(serverToggleButton);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(serverPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createQueryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBackground(CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        // Top line: Domain input, Record Type, Upstream Server, Cache checkbox, Buttons
        JPanel inputRow = new JPanel(new GridBagLayout());
        inputRow.setBackground(CARD_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Domain label + input
        JLabel domainLbl = new JLabel("Domain:");
        domainLbl.setForeground(TEXT_MUTED);
        domainLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputRow.add(domainLbl, gbc);

        domainField = new JTextField("example.com", 18);
        domainField.setFont(new Font("Consolas", Font.PLAIN, 14));
        domainField.setBackground(new Color(15, 23, 42));
        domainField.setForeground(TEXT_MAIN);
        domainField.setCaretColor(ACCENT_CYAN);
        domainField.setBorder(new CompoundBorder(new LineBorder(CARD_BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        domainField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLookup(true);
                }
            }
        });
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        inputRow.add(domainField, gbc);

        // Record type combo
        JLabel typeLbl = new JLabel("Type:");
        typeLbl.setForeground(TEXT_MUTED);
        typeLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 2;
        gbc.weightx = 0;
        inputRow.add(typeLbl, gbc);

        recordTypeCombo = new JComboBox<>(new String[]{"A (IPv4)", "AAAA (IPv6)", "CNAME (Alias)", "MX (Mail)", "TXT (Text)", "ANY"});
        recordTypeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 3;
        gbc.weightx = 0;
        inputRow.add(recordTypeCombo, gbc);

        // Upstream Server combo
        JLabel serverLbl = new JLabel("Upstream DNS:");
        serverLbl.setForeground(TEXT_MUTED);
        serverLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 4;
        gbc.weightx = 0;
        inputRow.add(serverLbl, gbc);

        upstreamCombo = new JComboBox<>(new String[]{
                "1.1.1.1 (Cloudflare)",
                "8.8.8.8 (Google)",
                "9.9.9.9 (Quad9)",
                "208.67.222.222 (OpenDNS)",
                "127.0.0.1:5354 (Local DnsServer)"
        });
        upstreamCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        upstreamCombo.addActionListener(e -> updateUpstreamSelection());
        gbc.gridx = 5;
        gbc.weightx = 0;
        inputRow.add(upstreamCombo, gbc);

        // Use Cache checkbox
        useCacheCheckBox = new JCheckBox("Use Cache", true);
        useCacheCheckBox.setForeground(TEXT_MAIN);
        useCacheCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        useCacheCheckBox.setBackground(CARD_BG);
        gbc.gridx = 6;
        gbc.weightx = 0;
        inputRow.add(useCacheCheckBox, gbc);

        // Lookup button
        lookupButton = new JButton("Resolve DNS");
        lookupButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lookupButton.setBackground(new Color(14, 165, 233));
        lookupButton.setForeground(Color.WHITE);
        lookupButton.setFocusPainted(false);
        lookupButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lookupButton.addActionListener(e -> performLookup(useCacheCheckBox.isSelected()));
        gbc.gridx = 7;
        gbc.weightx = 0;
        inputRow.add(lookupButton, gbc);

        // Benchmark / Compare button
        benchmarkButton = new JButton("Compare Cache Speed");
        benchmarkButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        benchmarkButton.setBackground(new Color(139, 92, 246));
        benchmarkButton.setForeground(Color.WHITE);
        benchmarkButton.setFocusPainted(false);
        benchmarkButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        benchmarkButton.setToolTipText("Executes uncached network query followed by cached query to measure speedup");
        benchmarkButton.addActionListener(e -> performBenchmark());
        gbc.gridx = 8;
        gbc.weightx = 0;
        inputRow.add(benchmarkButton, gbc);

        // Quick domain pills row
        JPanel presetsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        presetsRow.setBackground(CARD_BG);
        JLabel presetTitle = new JLabel("Presets:");
        presetTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        presetTitle.setForeground(TEXT_MUTED);
        presetsRow.add(presetTitle);

        String[] sampleDomains = {"example.com", "google.com", "cloudflare.com", "github.com", "wikipedia.org", "yahoo.com"};
        for (String sample : sampleDomains) {
            JButton pill = new JButton(sample);
            pill.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            pill.setBackground(new Color(51, 65, 85));
            pill.setForeground(TEXT_MAIN);
            pill.setBorder(new EmptyBorder(3, 8, 3, 8));
            pill.setFocusPainted(false);
            pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pill.addActionListener(e -> {
                domainField.setText(sample);
                performLookup(useCacheCheckBox.isSelected());
            });
            presetsRow.add(pill);
        }

        panel.add(inputRow, BorderLayout.CENTER);
        panel.add(presetsRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel statsContainer = new JPanel(new GridLayout(1, 6, 10, 0));
        statsContainer.setBackground(BG_DARK);

        hitsLabel = new JLabel("0");
        missesLabel = new JLabel("0");
        ratioLabel = new JLabel("0.0%");
        activeEntriesLabel = new JLabel("0");
        expiredEntriesLabel = new JLabel("0");

        statsContainer.add(createCard("CACHE HITS", hitsLabel, ACCENT_GREEN, "Queries served from memory"));
        statsContainer.add(createCard("CACHE MISSES", missesLabel, ACCENT_ROSE, "Queried upstream over UDP"));
        statsContainer.add(createCard("HIT RATIO", ratioLabel, ACCENT_CYAN, "Efficiency percentage"));
        statsContainer.add(createCard("ACTIVE RECORDS", activeEntriesLabel, ACCENT_PURPLE, "Valid cached items in memory"));
        statsContainer.add(createCard("EXPIRED RECORDS", expiredEntriesLabel, ACCENT_AMBER, "Records evicted past TTL"));

        // Cache Action Card
        JPanel actionCard = new JPanel(new GridLayout(2, 1, 4, 4));
        actionCard.setBackground(CARD_BG);
        actionCard.setBorder(new CompoundBorder(new LineBorder(CARD_BORDER, 1, true), new EmptyBorder(8, 10, 8, 10)));

        JButton clearBtn = new JButton("Clear Cache");
        clearBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        clearBtn.setBackground(new Color(239, 68, 68));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> {
            cache.clear();
            refreshCacheTable();
            refreshStatsLabels();
        });

        JButton evictBtn = new JButton("Evict Expired Now");
        evictBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        evictBtn.setBackground(new Color(71, 85, 105));
        evictBtn.setForeground(TEXT_MAIN);
        evictBtn.setFocusPainted(false);
        evictBtn.addActionListener(e -> {
            int evicted = cache.evictExpired();
            refreshCacheTable();
            refreshStatsLabels();
            JOptionPane.showMessageDialog(this, "Evicted " + evicted + " expired records.", "Cache Eviction", JOptionPane.INFORMATION_MESSAGE);
        });

        actionCard.add(clearBtn);
        actionCard.add(evictBtn);
        statsContainer.add(actionCard);

        return statsContainer;
    }

    private JPanel createCard(String title, JLabel valueLabel, Color accentColor, String tooltip) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(CARD_BG);
        card.setToolTipText(tooltip);
        card.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(accentColor);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPerformanceBanner() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setBackground(new Color(24, 33, 49));
        banner.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setBackground(new Color(24, 33, 49));

        JLabel label = new JLabel("Performance:");
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(TEXT_MUTED);

        perfSourceLabel = new JLabel("Source: Ready");
        perfSourceLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        perfSourceLabel.setForeground(TEXT_MAIN);

        perfTimeLabel = new JLabel("Response Time: —");
        perfTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        perfTimeLabel.setForeground(ACCENT_CYAN);

        left.add(label);
        left.add(perfSourceLabel);
        left.add(perfTimeLabel);

        perfSpeedupLabel = new JLabel("");
        perfSpeedupLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        perfSpeedupLabel.setForeground(ACCENT_GREEN);

        banner.add(left, BorderLayout.WEST);
        banner.add(perfSpeedupLabel, BorderLayout.EAST);
        return banner;
    }

    private JTabbedPane createTabsPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Tab 1: Current Query Results
        tabs.addTab("Query Results", createResultsTab());

        // Tab 2: Cache Explorer (Live Table)
        tabs.addTab("Active Cache Explorer", createCacheTab());

        // Tab 3: History
        tabs.addTab("Lookup History", createHistoryTab());

        // Tab 4: Logs
        tabs.addTab("Live Log Console", createLogsTab());

        return tabs;
    }

    private JPanel createResultsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);

        String[] columns = {"Record Type", "Domain Name", "Resolved Address / Target Value", "Class", "TTL (Seconds)", "Status"};
        resultTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(resultTableModel);
        styleTable(table);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(CARD_BORDER));
        scrollPane.getViewport().setBackground(CARD_BG);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCacheTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);

        String[] columns = {"Domain", "Type", "Resolved Value / IP", "Orig TTL (s)", "Remaining TTL", "Age (s)", "Cached At", "Expires At", "Status"};
        cacheTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        cacheTable = new JTable(cacheTableModel);
        styleTable(cacheTable);

        // Highlight expired records
        cacheTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, r, c);
                String status = String.valueOf(t.getValueAt(r, 8));
                if ("EXPIRED".equals(status)) {
                    comp.setForeground(ACCENT_ROSE);
                } else if (c == 4) { // Remaining TTL
                    comp.setForeground(ACCENT_GREEN);
                } else if (!isSelected) {
                    comp.setForeground(TEXT_MAIN);
                }
                return comp;
            }
        });

        JScrollPane scroll = new JScrollPane(cacheTable);
        scroll.setBorder(new LineBorder(CARD_BORDER));
        scroll.getViewport().setBackground(CARD_BG);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottomBar.setBackground(BG_DARK);

        JButton refreshBtn = new JButton("Refresh Cache View");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        refreshBtn.setBackground(new Color(51, 65, 85));
        refreshBtn.setForeground(TEXT_MAIN);
        refreshBtn.addActionListener(e -> refreshCacheTable());

        bottomBar.add(refreshBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);

        String[] cols = {"#", "Time", "Domain", "Type", "Source", "Response Time", "Result Summary"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(historyTableModel);
        styleTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(CARD_BORDER));
        scroll.getViewport().setBackground(CARD_BG);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottom.setBackground(BG_DARK);
        JButton clearHistBtn = new JButton("Clear History");
        clearHistBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearHistBtn.setBackground(new Color(51, 65, 85));
        clearHistBtn.setForeground(TEXT_MAIN);
        clearHistBtn.addActionListener(e -> historyTableModel.setRowCount(0));
        bottom.add(clearHistBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createLogsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);

        logTextArea = new JTextArea();
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setBackground(new Color(15, 23, 42));
        logTextArea.setForeground(new Color(226, 232, 240));
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setBorder(new LineBorder(CARD_BORDER));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottom.setBackground(BG_DARK);

        autoScrollCheckBox = new JCheckBox("Auto Scroll", true);
        autoScrollCheckBox.setForeground(TEXT_MUTED);
        autoScrollCheckBox.setBackground(BG_DARK);

        JButton clearLogsBtn = new JButton("Clear Console");
        clearLogsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearLogsBtn.setBackground(new Color(51, 65, 85));
        clearLogsBtn.setForeground(TEXT_MAIN);
        clearLogsBtn.addActionListener(e -> logTextArea.setText(""));

        bottom.add(autoScrollCheckBox);
        bottom.add(clearLogsBtn);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(28);
        table.setFont(new Font("Consolas", Font.PLAIN, 13));
        table.setShowGrid(true);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(0, 32));
    }

    private void initListeners() {
        // Connect logger listener for real-time log display
        Logger.addListener(logMessage -> SwingUtilities.invokeLater(() -> {
            if (logTextArea != null) {
                logTextArea.append(logMessage + "\n");
                if (autoScrollCheckBox != null && autoScrollCheckBox.isSelected()) {
                    logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
                }
            }
        }));

        // Connect cache change listener
        cache.addChangeListener(() -> SwingUtilities.invokeLater(() -> {
            refreshStatsLabels();
            refreshCacheTable();
        }));

        // Connect server status listener
        server.setStatusListener(running -> SwingUtilities.invokeLater(() -> {
            if (running) {
                serverStatusBadge.setText(" Server: RUNNING (UDP:" + server.getPort() + ") ");
                serverStatusBadge.setForeground(ACCENT_GREEN);
                serverStatusBadge.setBorder(new CompoundBorder(
                        new LineBorder(ACCENT_GREEN, 1, true),
                        new EmptyBorder(4, 8, 4, 8)
                ));
                serverToggleButton.setText("Stop DNS Server");
                serverToggleButton.setBackground(new Color(239, 68, 68));
            } else {
                serverStatusBadge.setText(" Server: STOPPED ");
                serverStatusBadge.setForeground(ACCENT_ROSE);
                serverStatusBadge.setBorder(new CompoundBorder(
                        new LineBorder(ACCENT_ROSE, 1, true),
                        new EmptyBorder(4, 8, 4, 8)
                ));
                serverToggleButton.setText("Start Local DNS Server (Port 5354)");
                serverToggleButton.setBackground(new Color(16, 185, 129));
            }
        }));
    }

    private void startPeriodicRefreshTimer() {
        // Timer runs every 1 second to update TTL countdowns in the cache table
        Timer timer = new Timer(1000, e -> {
            refreshStatsLabels();
            refreshCacheRemainingTtl();
        });
        timer.setRepeats(true);
        timer.start();
    }

    private int getSelectedRecordType() {
        String selected = (String) recordTypeCombo.getSelectedItem();
        if (selected == null) return DnsRecord.TYPE_A;
        if (selected.startsWith("A ")) return DnsRecord.TYPE_A;
        if (selected.startsWith("AAAA")) return DnsRecord.TYPE_AAAA;
        if (selected.startsWith("CNAME")) return DnsRecord.TYPE_CNAME;
        if (selected.startsWith("MX")) return DnsRecord.TYPE_MX;
        if (selected.startsWith("TXT")) return DnsRecord.TYPE_TXT;
        if (selected.startsWith("ANY")) return DnsRecord.TYPE_ANY;
        return DnsRecord.TYPE_A;
    }

    private void updateUpstreamSelection() {
        String selected = (String) upstreamCombo.getSelectedItem();
        if (selected == null) return;
        if (selected.contains("1.1.1.1")) {
            client.setUpstreamDnsServer("1.1.1.1");
            client.setUpstreamPort(53);
        } else if (selected.contains("8.8.8.8")) {
            client.setUpstreamDnsServer("8.8.8.8");
            client.setUpstreamPort(53);
        } else if (selected.contains("9.9.9.9")) {
            client.setUpstreamDnsServer("9.9.9.9");
            client.setUpstreamPort(53);
        } else if (selected.contains("208.67.222.222")) {
            client.setUpstreamDnsServer("208.67.222.222");
            client.setUpstreamPort(53);
        } else if (selected.contains("127.0.0.1")) {
            client.setUpstreamDnsServer("127.0.0.1");
            client.setUpstreamPort(5354);
        }
    }

    private void toggleServer() {
        if (server.isRunning()) {
            server.stop();
        } else {
            try {
                server.start();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to start DNS Server: " + e.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performLookup(boolean useCache) {
        String domain = domainField.getText().trim();
        if (domain.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a domain name to lookup.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int type = getSelectedRecordType();
        updateUpstreamSelection();

        lookupButton.setEnabled(false);
        lookupButton.setText("Resolving...");

        SwingWorker<DnsClient.LookupResult, Void> worker = new SwingWorker<>() {
            @Override
            protected DnsClient.LookupResult doInBackground() {
                return client.resolve(domain, type, useCache);
            }

            @Override
            protected void done() {
                lookupButton.setEnabled(true);
                lookupButton.setText("Resolve DNS");
                try {
                    DnsClient.LookupResult result = get();
                    displayResult(result);
                } catch (Exception ex) {
                    Logger.error("Failed to execute lookup worker: " + ex.getMessage(), ex);
                }
            }
        };
        worker.execute();
    }

    private void displayResult(DnsClient.LookupResult result) {
        resultTableModel.setRowCount(0);

        if (!result.isSuccess()) {
            perfSourceLabel.setText("Source: ERROR");
            perfSourceLabel.setForeground(ACCENT_ROSE);
            perfTimeLabel.setText("Time: " + DF.format(result.getResponseTimeMs()) + " ms");
            perfSpeedupLabel.setText(result.getErrorMessage() != null ? result.getErrorMessage() : "Lookup Failed");
            perfSpeedupLabel.setForeground(ACCENT_ROSE);

            resultTableModel.addRow(new Object[]{
                    result.getRecordTypeName(),
                    result.getDomain(),
                    "FAILED: " + (result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown Error"),
                    "IN",
                    "—",
                    "ERROR"
            });
            addHistoryEntry(result, "ERROR: " + result.getErrorMessage());
            return;
        }

        // Display results
        List<DnsRecord> records = result.getRecords();
        if (records.isEmpty()) {
            resultTableModel.addRow(new Object[]{
                    result.getRecordTypeName(),
                    result.getDomain(),
                    "No records found of type " + result.getRecordTypeName(),
                    "IN",
                    "—",
                    "NO DATA"
            });
        } else {
            for (DnsRecord rec : records) {
                resultTableModel.addRow(new Object[]{
                        rec.getTypeName(),
                        rec.getName(),
                        rec.getFormattedValue(),
                        "IN",
                        rec.getTtl(),
                        result.isCacheHit() ? "FROM CACHE" : "FROM UPSTREAM"
                });
            }
        }

        // Update performance banner
        if (result.isCacheHit()) {
            perfSourceLabel.setText("Source: CACHE HIT (Local Memory)");
            perfSourceLabel.setForeground(ACCENT_GREEN);
            perfTimeLabel.setText(String.format("Response Time: %.3f ms (Instant)", result.getResponseTimeMs()));
            perfSpeedupLabel.setText("⚡ Served from RAM without network latency");
            perfSpeedupLabel.setForeground(ACCENT_CYAN);
        } else {
            perfSourceLabel.setText("Source: REMOTE UPSTREAM (" + result.getServerUsed() + ")");
            perfSourceLabel.setForeground(ACCENT_CYAN);
            perfTimeLabel.setText(String.format("Response Time: %.2f ms (UDP Round-Trip)", result.getResponseTimeMs()));
            perfSpeedupLabel.setText("Saved to local cache for subsequent requests");
            perfSpeedupLabel.setForeground(TEXT_MUTED);
        }

        addHistoryEntry(result, records.size() + " record(s) resolved");
        refreshStatsLabels();
        refreshCacheTable();
    }

    private void performBenchmark() {
        String domain = domainField.getText().trim();
        if (domain.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a domain for performance benchmark.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int type = getSelectedRecordType();
        benchmarkButton.setEnabled(false);
        benchmarkButton.setText("Benchmarking...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                // 1. Force network lookup (uncached)
                DnsClient.LookupResult networkResult = client.resolve(domain, type, false);

                // 2. Lookup again using cache
                DnsClient.LookupResult cacheResult = client.resolve(domain, type, true);

                double netTime = networkResult.getResponseTimeMs();
                double cacheTime = cacheResult.getResponseTimeMs();
                double speedup = cacheTime > 0 ? (netTime / cacheTime) : 999.0;
                double savedPercent = netTime > 0 ? ((netTime - cacheTime) / netTime) * 100.0 : 0.0;

                return String.format(
                        "Benchmark Results for '%s':\n" +
                                "• Network (UDP Remote): %.2f ms\n" +
                                "• Cache (RAM Instant): %.3f ms\n" +
                                "• Performance Boost: %.1fx FASTER (%.1f%% latency eliminated)",
                        domain, netTime, cacheTime, speedup, savedPercent
                );
            }

            @Override
            protected void done() {
                benchmarkButton.setEnabled(true);
                benchmarkButton.setText("Compare Cache Speed");
                try {
                    String summary = get();
                    JOptionPane.showMessageDialog(DnsGUI.this, summary, "Speed Benchmark", JOptionPane.INFORMATION_MESSAGE);
                    refreshStatsLabels();
                    refreshCacheTable();
                } catch (Exception ex) {
                    Logger.error("Benchmark failed: " + ex.getMessage(), ex);
                }
            }
        };
        worker.execute();
    }

    private void addHistoryEntry(DnsClient.LookupResult result, String summary) {
        historyCounter++;
        historyTableModel.insertRow(0, new Object[]{
                historyCounter,
                LocalTime.now().format(TIME_FMT),
                result.getDomain(),
                result.getRecordTypeName(),
                result.isCacheHit() ? "CACHE" : "REMOTE",
                DF.format(result.getResponseTimeMs()) + " ms",
                summary
        });
    }

    private void refreshStatsLabels() {
        hitsLabel.setText(String.valueOf(cache.getHits()));
        missesLabel.setText(String.valueOf(cache.getMisses()));
        ratioLabel.setText(DF.format(cache.getHitRatio()) + "%");
        activeEntriesLabel.setText(String.valueOf(cache.getActiveEntryCount()));
        expiredEntriesLabel.setText(String.valueOf(cache.getExpiredCount()));
    }

    private void refreshCacheTable() {
        cacheTableModel.setRowCount(0);
        List<CacheEntry> entries = cache.getAllEntries();
        for (CacheEntry entry : entries) {
            cacheTableModel.addRow(new Object[]{
                    entry.getDomain(),
                    entry.getRecordTypeName(),
                    entry.getValue(),
                    entry.getTtlSeconds(),
                    entry.getRemainingTtlSeconds() + "s",
                    entry.getAgeSeconds() + "s",
                    entry.getFormattedCreationTime(),
                    entry.getFormattedExpirationTime(),
                    entry.isExpired() ? "EXPIRED" : "VALID"
            });
        }
    }

    private void refreshCacheRemainingTtl() {
        if (cacheTable == null || cacheTableModel.getRowCount() == 0) return;
        List<CacheEntry> entries = cache.getAllEntries();
        if (entries.size() != cacheTableModel.getRowCount()) {
            refreshCacheTable();
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            CacheEntry ce = entries.get(i);
            cacheTableModel.setValueAt(ce.getRemainingTtlSeconds() + "s", i, 4);
            cacheTableModel.setValueAt(ce.getAgeSeconds() + "s", i, 5);
            cacheTableModel.setValueAt(ce.isExpired() ? "EXPIRED" : "VALID", i, 8);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DnsGUI gui = new DnsGUI();
            gui.setVisible(true);
        });
    }
}
