package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;

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
    private Thread reconnectionThread;
    private volatile boolean isRunning = false; // running
    private volatile boolean disconnected = false; // manualDisconnect
    private volatile boolean joined = false;
    private String prevHost = "";
    private int prevPort = 0;
    private String myRole = null;
    private Map<String, Boolean> playerReadiness = new HashMap<>();

    private void onConnect_GUIUpdate() {
        statusLabel.setText("Connected");
        statusLabel.setStyle("-fx-text-fill: green;");
        connectBtn.setDisable(true);
        disconnectBtn.setDisable(false);
        joinBtn.setDisable(false);
    }

    private void resetConnectionButtons() {
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        joinBtn.setDisable(true);
        readyBtn.setDisable(true);
    }

    private void onConnectionFailure_GUIUpdate(IOException e) {
        statusLabel.setText("Connect failed: " + e.getMessage());
        statusLabel.setStyle("-fx-text-fill: red;");
        resetConnectionButtons();
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void send(String s) {
        if (out == null) {
            return;
        }
        out.println(s);
        out.println();
        out.flush();
    }

    private void updatePlayerList() {
        String playerList = "";
        String ownName = nameField.getText().trim();

        for (String name : playerReadiness.keySet()) {
            boolean isReady = playerReadiness.get(name);
            String playerString = "";

            if (name.equals(ownName)) {
                playerString += name + " (You) ";
            }

            if (isReady) {
                playerString += "[ready]" + "\n";
            } else {
                playerString += "[waiting]" + "\n";
            }

            playerList += playerString;
        }

        playerListArea.setText(playerList);
    }

    private void reconnectionGUIUpdate() {
        Platform.runLater(() -> {
            statusLabel.setText("Reconnected");
            statusLabel.setStyle("-fx-text-fill: green;");
            connectBtn.setDisable(true);
            disconnectBtn.setDisable(false);
        });
    }

    private void reconnectionThreadFunc() {
        while (!disconnected) {
            try {
                Thread.sleep(2000);
                Socket s = new Socket(prevHost, prevPort);
                socket = s;

                InputStream inputStream = s.getInputStream();
                OutputStream outputStream = s.getOutputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                BufferedReader bufferedIn = new BufferedReader(inputStreamReader);
                in = bufferedIn;

                PrintWriter bufferedOut = new PrintWriter(outputStream, true);
                out = bufferedOut;
            
                isRunning = true;
                reconnectionGUIUpdate();

                if (joined) {
                    String name = nameField.getText().trim();
                    if (!name.isEmpty()) {
                        send("TYPE:JOIN\nNAME:" + name);
                    }
                }

                startListener();
                return;
            } 
            
            catch (IOException e) {}
            catch (InterruptedException e) {}
        }
    }

    private void attemptReconnectLoop() {
        synchronized (this) {
            if (reconnectionThread != null && reconnectionThread.isAlive()) {
                return;
            }

            reconnectionThread = new Thread(() -> reconnectionThreadFunc(), "reconnect");
            reconnectionThread.setDaemon(true);
            reconnectionThread.start();
        }
    }

    private void processMessage(List<String> messages) {
        ParsedMessage parsedMessage = parseMessage(messages);
        String messageType = parsedMessage.type;

        if ("STATE_UPDATE".equals(messageType)) {
            handleStateUpdate(parsedMessage);

        } 
        
        else if ("CONNECTION_WINDOW".equals(messageType)) {
            handleConnectionWindow(messages);

        } 
        
        else if ("CONNECTION_SUCCESS".equals(messageType) || "CONNECTION_FAILED".equals(messageType) || "HINT_TIMEOUT".equals(messageType)) {
            handleConnectionEnd(messages);

        } 
    }

    private ParsedMessage parseMessage(List<String> messages) {
        String messageType = null, prefix = null, lives = null, a = null;
        boolean readingPlayersSection = false, readingHistorySection = false;
        String historyString = "";
        Map<String, Boolean> tempReadiness = new HashMap<>();

        for (String message : messages) {
            if (message.equals("HISTORY_START") || message.equals("HISTORY_END")) { 
                readingHistorySection = true; 
                continue; 
            }

            if (message.equals("PLAYERS_START") || message.equals("PLAYERS_END")) { 
                readingPlayersSection = true; 
                continue; 
            }

            if (readingPlayersSection) {
                if (message.startsWith("PLAYER:")) {
                    String[] playerStrParts = message.split(":", 4);
                    if (playerStrParts.length >= 4) { 
                        Boolean readiness = "true".equalsIgnoreCase(playerStrParts[3]);
                        tempReadiness.put(playerStrParts[1], readiness); 
                    }
                }
                
                continue;
            }

            if (readingHistorySection) {
                historyString += message;
                historyString += '\n';
                continue;
            }

            int idx = message.indexOf(':');
            if (idx > 0) {
                String key = message.substring(0, idx).trim();
                String value = message.substring(idx + 1).trim();

                if ("TYPE".equals(key)) {
                    messageType = value;
                }

                else if ("PREFIX".equals(key)) { 
                    prefix = value;
                }

                else if ("LIVES".equals(key)) {
                    lives = value;
                }

                else if ("A".equals(key)) {
                    a = value;
                }
            }
        }

        return new ParsedMessage(messageType, prefix, lives, a, historyString = "".toString(), tempReadiness);
    }

    private void handleStateUpdate(ParsedMessage parsedMessage) {
        playerReadiness.clear();
        playerReadiness.putAll(parsedMessage.playerReadiness);

        Platform.runLater(() -> {
            if (parsedMessage.prefix == null || parsedMessage.prefix.isEmpty()) {
                prefixLabel.setText( "-" );
            }

            else {
                prefixLabel.setText(parsedMessage.prefix);
            }

            if (parsedMessage.lives == null || parsedMessage.lives.isEmpty()) {
                livesLabel.setText("-");
            } else {
                livesLabel.setText(parsedMessage.lives);
            }

            if (parsedMessage.a == null || parsedMessage.a.isEmpty()) {
                aLabel.setText("-");
            } else {
                aLabel.setText(parsedMessage.a);
            }

            historyArea.setText(parsedMessage.history.trim());
            updatePlayerList();
            updateUIState(parsedMessage.a, parsedMessage.prefix);
        });
    }

    private void handleConnectionWindow(List<String> messages) {
        Platform.runLater(() -> {
            if ("B".equals(myRole)) {
                guessBtnB.setDisable(false);
            }
            else if ("A".equals(myRole)) {
                guessBtn.setDisable(false);
            }
        });
    }

    private void handleConnectionEnd(List<String> messages) {
        Platform.runLater(() -> {
            guessBtnB.setDisable(true);
            guessBtn.setDisable(true);
        });
    }

    private void givenRolesUpdateGUI(Boolean secretChosen, Boolean isA) {
        roleAPanel.setVisible(isA);
        roleAPanel.setManaged(isA);
        roleBPanel.setVisible(!isA);
        roleBPanel.setManaged(!isA);

        lobbyPanel.setVisible(false);
        lobbyPanel.setManaged(false);
        gamePanel.setVisible(true);
        gamePanel.setManaged(true);

        if (!isA) {
            hintPublicField.setDisable(!secretChosen);
            hintIntendedField.setDisable(!secretChosen);
            startHintBtn.setDisable(!secretChosen);
            connectBtn2.setDisable(!secretChosen);
        }
                
        else if (isA) {
            secretField.setDisable(secretChosen);
            setSecretBtn.setDisable(secretChosen);
        }

        if (isA) {
            roleLabel.setText("A (Defender)");
        }

        else {
            roleLabel.setText("B (Communicator)");
        }
    }

    private void notGivenRolesGUIUpdate(String ownName) {
        gamePanel.setVisible(false);
        gamePanel.setManaged(false);

        lobbyPanel.setVisible(true);
        lobbyPanel.setManaged(true);
        
        // Enable ready button if joined
        boolean joined = false;
        if (!ownName.isEmpty() && playerReadiness.containsKey(ownName)) {
            joined = true;
        }

        boolean disableReadyButton = false;
        if (!joined || playerReadiness.getOrDefault(ownName, false)) {
            disableReadyButton = true;
        }

        readyBtn.setDisable(disableReadyButton);

        if (joined) {
            boolean amReady = false;
            if (playerReadiness.getOrDefault(ownName, false)) {
                amReady = true;
            }

            if (amReady) {
                readyStatusLabel.setText("You are READY! Waiting for others...");
            }

            else {
                readyStatusLabel.setText("Click Ready when you're ready to play");
            }

        } else if (!ownName.isEmpty()) {
            readyStatusLabel.setText("Waiting for server to confirm join...");
        }
    }

    private void updateUIState(String activePlayer, String currPrefix) {
        String ownName = nameField.getText().trim();

        boolean givenRoles = false;
        if (activePlayer != null && !activePlayer.isEmpty()) {
            givenRoles = true;
        }

        boolean isA = false;
        if (givenRoles && activePlayer.equals(ownName)) {
            isA = true;
        }

        boolean secretChosen = false;
        if ( currPrefix != null && !currPrefix.isEmpty() && !"-".equals(currPrefix) ) {
            secretChosen = true;
        }
        
        if (isA) {
            myRole = "A";
        } else if (givenRoles) {
            myRole = "B";
        } else {
            myRole = null;
        }

        if (givenRoles) {
            givenRolesUpdateGUI(secretChosen, isA);
        }
        
        else if (!givenRoles) {
            notGivenRolesGUIUpdate(ownName);
        } 
    }

    private void listenerThreadFunc() {
        try {
            String ln = in.readLine();
            List<String> list = new ArrayList<>();

            while (isRunning && ln != null) {

                if (!ln.trim().isEmpty()) {
                    list.add(ln);
                } else {
                    if (!list.isEmpty()) {
                        processMessage(list);
                        list.clear();
                    }
                }

                ln = in.readLine();
            }
        } catch (IOException e) {}

        if (isRunning && !disconnected) {
            attemptReconnectLoop();
        }

        Platform.runLater(() -> statusLabel.setText("Disconnected"));
    }

    private void startListener() {
        listenerThread = new Thread(this::listenerThreadFunc, "listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
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

            onConnect_GUIUpdate();
            startListener();

        } catch (IOException e) {
            onConnectionFailure_GUIUpdate(e);
        }
    }

    @FXML
    private void onDisconnect() {
        disconnected = true;
        joined = false;
        isRunning = false;

        resetConnectionButtons();
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
        String guess = "";
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
