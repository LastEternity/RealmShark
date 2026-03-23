package tomato.gui.stats;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import tomato.gui.SmartScroller;
import tomato.realmshark.SendLoot;
import util.PropertiesManager;

public class BridgeReviewGUI extends JPanel {

    private static final String KEY_ENABLED = "realmshark.bridge.enabled";
    private static final String KEY_ENDPOINT = "realmshark.bridge.endpoint";
    private static final String KEY_GUILD_ID = "realmshark.bridge.guild_id";
    private static final String KEY_LINK_TOKEN = "realmshark.bridge.link_token";
    private static final String KEY_CSV_PATH = "realmshark.bridge.csv_path";
    private static final String KEY_LOG_PATH = "realmshark.bridge.local_review_log";
    private static final String KEY_DEBUG = "realmshark.bridge.debug";

    private static JTextArea sentArea;
    private static JTextArea unsentUtStArea;
    private static JPanel sentPanel;
    private static boolean listenerRegistered = false;

    private static JTextField endpointField;
    private static JTextField guildIdField;
    private static JTextField tokenField;
    private static JTextField csvPathField;
    private static JTextField logPathField;
    private static JCheckBox enabledCheck;
    private static JCheckBox debugCheck;

    public BridgeReviewGUI() {
        setLayout(new BorderLayout());

        sentArea = createLogArea();
        unsentUtStArea = createLogArea();

        sentPanel = createSection(
            "Sent to bot (tracked in rotmg_loot_drops_updated.csv)",
            sentArea
        );

        JPanel unsentPanel = createSection(
            "Not sent (UT/ST missing in rotmg_loot_drops_updated.csv)",
            unsentUtStArea
        );

        JPanel listsPanel = new JPanel(new GridLayout(2, 1));
        listsPanel.add(sentPanel);
        listsPanel.add(unsentPanel);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(buildBridgeConfigPanel());
        topPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        add(topPanel, BorderLayout.NORTH);
        add(listsPanel, BorderLayout.CENTER);

        ensureListener();
    }

    private static JPanel buildBridgeConfigPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Bridge Settings"));

        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 4));

        enabledCheck = new JCheckBox("Enabled");
        debugCheck = new JCheckBox("Debug");

        endpointField = new JTextField(getProperty(KEY_ENDPOINT));
        guildIdField = new JTextField(getProperty(KEY_GUILD_ID));
        tokenField = new JTextField(getProperty(KEY_LINK_TOKEN));
        csvPathField = new JTextField(getProperty(KEY_CSV_PATH));
        logPathField = new JTextField(getProperty(KEY_LOG_PATH));

        enabledCheck.setSelected(asBool(getProperty(KEY_ENABLED)));
        debugCheck.setSelected(asBool(getProperty(KEY_DEBUG)));

        fields.add(new JLabel("Endpoint"));
        fields.add(endpointField);
        fields.add(new JLabel("Guild ID"));
        fields.add(guildIdField);
        fields.add(new JLabel("Link Token"));
        fields.add(tokenField);
        fields.add(new JLabel("CSV Path (optional)"));
        fields.add(csvPathField);
        fields.add(new JLabel("Local Review Log (optional)"));
        fields.add(logPathField);
        fields.add(enabledCheck);
        fields.add(debugCheck);

        JButton save = new JButton("Save Bridge Settings");
        save.addActionListener(e -> saveBridgeSettings());

        wrapper.add(fields, BorderLayout.CENTER);
        wrapper.add(save, BorderLayout.SOUTH);
        return wrapper;
    }

    private static String getProperty(String key) {
        String value = PropertiesManager.getProperty(key);
        return value == null ? "" : value;
    }

    private static boolean asBool(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on");
    }

    private static void saveBridgeSettings() {
        PropertiesManager.setProperties(KEY_ENABLED, String.valueOf(enabledCheck.isSelected()));
        PropertiesManager.setProperties(KEY_DEBUG, String.valueOf(debugCheck.isSelected()));
        PropertiesManager.setProperties(KEY_ENDPOINT, endpointField.getText().trim());
        PropertiesManager.setProperties(KEY_GUILD_ID, guildIdField.getText().trim());
        PropertiesManager.setProperties(KEY_LINK_TOKEN, tokenField.getText().trim());
        PropertiesManager.setProperties(KEY_CSV_PATH, csvPathField.getText().trim());
        PropertiesManager.setProperties(KEY_LOG_PATH, logPathField.getText().trim());

        SendLoot.reloadTrackedItemsFromSettings();
        SendLoot.sendSettingsConfirmationPing();

        if (sentArea != null) {
            if (sentArea.getText().startsWith("Waiting for bridge events")) {
                sentArea.setText("");
            }
            sentArea.append("[Config] Bridge settings saved.\n");
        }
    }

    private static JPanel createSection(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(title));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        new SmartScroller(scroll);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea createLogArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText("Waiting for bridge events...\n");
        return area;
    }

    private static synchronized void ensureListener() {
        if (listenerRegistered) return;

        SendLoot.addBridgeEventListener(event -> {
            SwingUtilities.invokeLater(() -> {
                String displayTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String line = "[" + displayTime + "] " + event.itemName;
                if (event.dungeon != null && !event.dungeon.isEmpty()) {
                    line += " | " + event.dungeon;
                }
                line += "\n";

                if (event.sent) {
                    if (sentArea.getText().startsWith("Waiting for bridge events")) {
                        sentArea.setText("");
                    }
                    sentArea.append(line);
                } else {
                    if (unsentUtStArea.getText().startsWith("Waiting for bridge events")) {
                        unsentUtStArea.setText("");
                    }
                    unsentUtStArea.append(line);
                }
            });
        });

        listenerRegistered = true;
    }

    public static void editFont(Font font) {
        if (sentArea != null) sentArea.setFont(font);
        if (unsentUtStArea != null) unsentUtStArea.setFont(font);
    }
}
