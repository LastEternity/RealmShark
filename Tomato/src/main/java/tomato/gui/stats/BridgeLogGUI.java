package tomato.gui.stats;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import tomato.gui.SmartScroller;
import tomato.realmshark.SendLoot;

public class BridgeLogGUI extends JPanel {

    private static JTextArea logArea;
    private static boolean listenerRegistered = false;

    public BridgeLogGUI() {
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setText("Waiting for bridge logs...\n");

        JScrollPane scroll = new JScrollPane(logArea);
        new SmartScroller(scroll);

        JButton clearButton = new JButton("Clear Logs");
        clearButton.addActionListener(e -> logArea.setText(""));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(clearButton);

        add(topBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        ensureListener();
    }

    private static synchronized void ensureListener() {
        if (listenerRegistered) return;

        SendLoot.addBridgeLogListener(entry -> {
            SwingUtilities.invokeLater(() -> {
                if (logArea == null) return;

                if (logArea.getText().startsWith("Waiting for bridge logs")) {
                    logArea.setText("");
                }

                String displayTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String line =
                    "[" +
                    displayTime +
                    "] [" +
                    entry.level +
                    "] " +
                    entry.message +
                    "\n";
                logArea.append(line);
            });
        }, true);

        listenerRegistered = true;
    }

    public static void editFont(Font font) {
        if (logArea != null) logArea.setFont(font);
    }
}
