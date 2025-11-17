package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.lang.classfile.Label;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientController {

    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private TextField nameField;
    @FXML
    private Button connectBtn;
    @FXML
    private Button joinBtn;
    @FXML
    private Button disconnectBtn;
    @FXML
    private Label statusLabel;

    @FXML
    private VBox lobbyPanel;
    @FXML
    private Button readyBtn;
    @FXML
    private Label readyStatusLabel;
    @FXML
    private TextArea playerListArea;

    @FXML
    private VBox gamePanel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label prefixLabel;
    @FXML
    private Label livesLabel;
    @FXML
    private Label aLabel;

    @FXML
    private VBox roleAPanel;
    @FXML
    private TextField secretField;
    @FXML
    private Button setSecretBtn;
    @FXML
    private TextField guessFieldA;
    @FXML
    private Button guessBtn;

    @FXML
    private VBox roleBPanel;
    @FXML
    private TextField hintPublicField;
    @FXML
    private TextField hintIntendedField;
    @FXML
    private Button startHintBtn;
    @FXML
    private Button connectBtn2;
    @FXML
    private TextField guessFieldB;
    @FXML
    private Button guessBtnB;

    @FXML
    private TextArea historyArea;

    @FXML
    private void initialize() {
        historyArea.setText("");
        playerListArea.setText("Waiting for connection...");
        connectBtn.setDisable(false);
        joinBtn.setDisable(true);
        readyBtn.setDisable(true);
        disconnectBtn.setDisable(true);
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private Thread reconnectThread;
    private volatile boolean isRunning = false; // running
    private volatile boolean disconnected = false; // manualDisconnect
    private volatile boolean joined = false;
    private String prevHost = "";
    private int prevPort = 0;
    private String myRole = null;
    private Map<String, Boolean> playerReadiness = new HashMap<>();

    private void onConnect_GUI_changes() {
        statusLabel.setText("Connected");
        statusLabel.setStyle("-fx-text-fill: green;");
        connectBtn.setDisable(true);
        disconnectBtn.setDisable(false);
        joinBtn.setDisable(false);
    }

    private void reset_connectionBtn() {
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        joinBtn.setDisable(true);
        readyBtn.setDisable(true);
    }

    private void onConnectFailure_GUI_changes(IOException e) {
        statusLabel.setText("Connect failed: " + e.getMessage());
        statusLabel.setStyle("-fx-text-fill: red;");
        reset_connectionBtn();
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    @FXML
    private void onConnect() {
        if (isRunning) {
            return;
        }
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        int port = Integer.parseInt(portText);

        disconnected = false;
        prevHost = host;
        prevPort = port;

        try {
            socket = new Socket(host, port);
            InputStream socket_input = socket.getInputStream();
            OutputStream socket_output = socket.getOutputStream();

            InputStreamReader instream = new InputStreamReader(socket_input);
            in = new BufferedReader(instream);
            out = new PrintWriter(socket_output, true);

            isRunning = true;

            onConnect_GUI_changes();
            startListener();

        } catch (IOException e) {
            onConnectFailure_GUI_changes(e);
        }
    }

    @FXML
    private void onDisconnect() {
        disconnected = true;
        joined = false;
        isRunning = false;

        reset_connectionBtn();
        statusLabel.setText("Disconnected");
        statusLabel.setStyle("-fx-text-fill: #888;");

        closeSocket();
    }

    @FXML
    private void onJoin() {
        String name = nameField.getText().trim();

        if (!name.isEmpty()) {
            send("TYPE:JOIN\nNAME:" + name);

            joined = true;
            joinBtn.setDisable(true);
        }
    }

    @FXML
    private void onReady() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            statusLabel.setText("Enter a name before readying");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        // if (!joined) {
        // send("TYPE:JOIN\nNAME:" + name);

        // joined = true;
        // joinBtn.setDisable(true);
        // }

        send("TYPE:READY");
    }

    @FXML
    private void onSetSecret() {
        String sec = secretField.getText().trim();

        if (!sec.isEmpty()) {
            send("TYPE:SET_SECRET\nSECRET:" + sec);
        }
    }

    @FXML
    private void onGuess() {
        String guess;
        boolean wasChanged = false;

        if ("A".equals(myRole)) {
            guess = guessFieldA.getText().trim();
            wasChanged = true;
        } else if ("B".equals(myRole)) {
            guess = guessFieldB.getText().trim();
            wasChanged = true;
        }

        if (!wasChanged || guess.isEmpty()) {
            return;
        }

        send("TYPE:SUBMIT_GUESS\nGUESS:" + guess);
    }

    @FXML
    private void onStartHint() {
        String hint = hintPublicField.getText().trim();
        String intended = hintIntendedField.getText().trim();

        if (hint.isEmpty() || intended.isEmpty()) {
            return;
        }

        send("TYPE:START_HINT\nHINT:" + hint + "\nINTENDED:" + intended);
    }

    @FXML
    private void onConnectAttempt() {
        send("TYPE:CONNECT");
    }

    @FXML
    private void onQuit() {

        isRunning = false;
        closeSocket();
        Platform.exit();
    }
}
