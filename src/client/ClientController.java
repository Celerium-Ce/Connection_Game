package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.io.*;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientController {

    // FXML variables
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
    private Label timerLabel;

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

    // Initially disable some elements and set value of others
    @FXML
    private void initialize() {
        historyArea.setText("");
        playerListArea.setText("Waiting for connection...");
        connectBtn.setDisable(false);
        joinBtn.setDisable(true);
        readyBtn.setDisable(true);
        disconnectBtn.setDisable(true);
        if (timerLabel != null) {
            timerLabel.setVisible(false);
            timerLabel.setText("");
        }
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private Thread reconnectionThread;
    private volatile boolean isRunning = false; 
    private volatile boolean disconnected = false; 
    private volatile boolean joined = false;
    private String prevHost = "";
    private int prevPort = 0;
    private String myRole = null;
    private Map<String, Boolean> playerReadiness = new HashMap<>();

    // To restore buttons to default 
    private void resetConnectionButtons() {
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        joinBtn.setDisable(true);
        readyBtn.setDisable(true);
    }

    // To update GUI when connection to server happens
    private void onConnect_GUIUpdate() {
        statusLabel.setText("Connected");
        statusLabel.setStyle("-fx-text-fill: green;");
        connectBtn.setDisable(true);
        disconnectBtn.setDisable(false);
        joinBtn.setDisable(false);
    }

    // To update GUI when connection to server fails
    private void onConnectionFailure_GUIUpdate(IOException e) {
        statusLabel.setText("Connect failed: " + e.getMessage());
        statusLabel.setStyle("-fx-text-fill: red;");
        resetConnectionButtons();
    }

    // Close the socket
    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    // Send a message to server using PrintWriter
    private void send(String s) {
        if (out == null) {
            return;
        }
        out.println(s);
        out.println();
        out.flush();
    }

    // To display players in the lobby
    private void updatePlayerList() {
        String playerList = "";
        String ownName = nameField.getText().trim();

        for (String name : playerReadiness.keySet()) {
            boolean isReady = playerReadiness.get(name);
            String playerString = "";

            if (name.equals(ownName)) { // whether its you or another player
                playerString += name + " (You) "; 
            } else {
                playerString += name + " ";
            }

            if (isReady) { // whether other players are ready or waiting
                playerString += "[ready]" + "\n"; 
            } else {
                playerString += "[waiting]" + "\n";
            }

            playerList += playerString;
        }

        playerListArea.setText(playerList);
    }

    // Function to run in timeline
    private void keyframeFunc() {
        countdownRemainingSeconds -= 1;
        final int temp = countdownRemainingSeconds;

        Platform.runLater(() -> {
            if (timerLabel != null) {
                timerLabel.setText(String.format("%ds", Math.max(0, temp)));
            }
        });

        if (countdownRemainingSeconds > 0) {
            return;
        }

        stopCountdown();
    }

    // Make timeline
    private void makeTimeline() {
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            keyframeFunc();
        }));

        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    // Countdown timer management
    private Timeline countdownTimeline = null;
    private int countdownRemainingSeconds = 0;

    private void startCountdown(int seconds) {
        stopCountdown();
        countdownRemainingSeconds = seconds;

        Platform.runLater(() -> {
            if (timerLabel != null) {
                timerLabel.setVisible(true);
                timerLabel.setText(String.format("%ds", countdownRemainingSeconds));
            }
        });

        makeTimeline();
    }

    private void stopTimerGUIUpdate() {
        if (timerLabel == null) {
                return;
        }
        
        timerLabel.setText("");
        timerLabel.setVisible(false);
    }

    // Stop count down of timer
    private void stopCountdown() {
        if (countdownTimeline == null) {
            return;
        }

        countdownTimeline.stop();
        countdownTimeline = null;

        Platform.runLater(() -> {
            stopTimerGUIUpdate();
        });
    }

    // To update GUI when you reconnect to game
    private void reconnectionGUIUpdate() {
        Platform.runLater(() -> {
            statusLabel.setText("Reconnected");
            statusLabel.setStyle("-fx-text-fill: green;");
            connectBtn.setDisable(true);
            disconnectBtn.setDisable(false);
        });
    }

    // Function to run in reconnection thread
    private void reconnectionThreadFunc() {
        while (!disconnected) {
            try {
                Thread.sleep(3000); // don't run constantly

                // create new socket and replace old input/output streams with new ones
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
                
                // Rejoin the game
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

    // Function to run the reconnection thread
    private void attemptReconnectLoop() {
        synchronized (this) {
            if (reconnectionThread != null && reconnectionThread.isAlive()) {  // Don't create thread if already exists
                return;
            }

            reconnectionThread = new Thread(() -> reconnectionThreadFunc(), "reconnect");
            reconnectionThread.setDaemon(true);
            reconnectionThread.start();
        }
    }

    // Process messages received from server
    private void processMessage(List<String> messages) {
        ParsedMessage parsedMessage = parseMessage(messages); // Convert raw strings to an object
        String messageType = parsedMessage.type;

        // Call appropriate handler
        if ("STATE_UPDATE".equals(messageType)) {
            handleStateUpdate(parsedMessage);

        } 
        
        else if ("CONNECTION_WINDOW".equals(messageType)) {
            handleConnectionWindow(messages);

        } 
        
        else if ("HINT_STARTED".equals(messageType)) {
            handleHintStarted(messages);

        } 
        
        else if ("CONNECTION_SUCCESS".equals(messageType) || "CONNECTION_FAILED".equals(messageType) || "HINT_TIMEOUT".equals(messageType)) {
            handleConnectionEnd(messages);

        } 
    }

    // Convert raw strings to objects
    private ParsedMessage parseMessage(List<String> messages) {
        String messageType = null, prefix = null, lives = null, a = null;
        boolean readingPlayersSection = false, readingHistorySection = false;
        String historyString = "";
        Map<String, Boolean> tempReadiness = new HashMap<>();

        for (String message : messages) {
            // History Section
            if (message.equals("HISTORY_START")) { 
                readingHistorySection = true; 
                continue; 
            }
            
            if (message.equals("HISTORY_END")) { 
                readingHistorySection = false; 
                continue; 
            }

            // Players Section
            if (message.equals("PLAYERS_START")) { 
                readingPlayersSection = true; 
                continue; 
            }
            
            if (message.equals("PLAYERS_END")) { 
                readingPlayersSection = false; 
                continue; 
            }

            // If reading players section, get the name of the players and mark them as ready/not ready
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

            // If reading history, add whole message to the history string
            if (readingHistorySection) {
                historyString += message;
                historyString += '\n';
                continue;
            }

            // Construct the actual object
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

        return new ParsedMessage(messageType, prefix, lives, a, historyString, tempReadiness);
    }

    // Handler for update of game
    private void handleStateUpdate(ParsedMessage parsedMessage) {

        // Replacing old player ready map with new one
        playerReadiness.clear();
        playerReadiness.putAll(parsedMessage.playerReadiness);

        Platform.runLater(() -> {
            if (parsedMessage.prefix == null || parsedMessage.prefix.isEmpty()) { // No prefix
                prefixLabel.setText( "-" );
            }

            else {
                prefixLabel.setText(parsedMessage.prefix);
            }

            if (parsedMessage.lives == null || parsedMessage.lives.isEmpty()) { // No lives
                livesLabel.setText("-");
            } else {
                livesLabel.setText(parsedMessage.lives);
            }

            if (parsedMessage.a == null || parsedMessage.a.isEmpty()) { // No value
                aLabel.setText("-");
            } else {
                aLabel.setText(parsedMessage.a);
            }

            historyArea.setText(parsedMessage.history.trim());
            historyArea.positionCaret(historyArea.getLength());

            updatePlayerList();
            updateUIState(parsedMessage.a, parsedMessage.prefix);
        });
    }

    // Handle the start of a connection (for hint)
    private void handleConnectionWindow(List<String> messages) {
        handleHintStarted(messages);

        Platform.runLater(() -> {
            if ("B".equals(myRole)) {
                guessBtnB.setDisable(false);
            }
            else if ("A".equals(myRole)) {
                guessBtn.setDisable(false);
            }
        });
    }

    // Handle the end of a connection (for a hint)
    private void handleConnectionEnd(List<String> messages) {
        stopCountdown();
        Platform.runLater(() -> {
            guessBtn.setDisable(true);
            guessBtnB.setDisable(true);
        });
    }

    // Update timer to handle hint start
    private void handleHintStarted(List<String> messages) {
        int timeSeconds = -1;

        // Extract time
        for (String message : messages) {
            if (message.startsWith("TIME:")) {
                try {
                    String messageTime = message.substring(5).trim();
                    timeSeconds = Integer.parseInt(messageTime);
                } catch (NumberFormatException e) {
                    timeSeconds = -1;
                }
            }
        }

        // Set time
        if (timeSeconds > 0) {
            startCountdown(timeSeconds);
        }
    }

    // Update UI if roles have been given
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

    // Update UI if roles have not been given
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

        // Disable ready button
        boolean disableReadyButton = false;
        if (!joined || playerReadiness.getOrDefault(ownName, false)) {
            disableReadyButton = true;
        }

        readyBtn.setDisable(disableReadyButton);

        // Set status text
        if (joined) {
            boolean amReady = false;
            if (playerReadiness.getOrDefault(ownName, false)) {
                amReady = true;
            }

            if (!amReady) {
                readyStatusLabel.setText("Click Ready when you're ready to play");
            }

            else if (amReady) {
                readyStatusLabel.setText("You are READY! Waiting for others...");
            }

        } else if (!ownName.isEmpty()) {
            readyStatusLabel.setText("Waiting for server to confirm join...");
        }
    }

    private void updateUIState(String activePlayer, String currPrefix) {
        String ownName = nameField.getText().trim();

        // Has the player been assigned roles
        boolean givenRoles = false;
        if (activePlayer != null && !activePlayer.isEmpty()) {
            givenRoles = true;
        }

        // Is the player active
        boolean isA = false;
        if (givenRoles && activePlayer.equals(ownName)) {
            isA = true;
        }

        // Has a secret been chosen
        boolean secretChosen = false;
        if ( currPrefix != null && !currPrefix.isEmpty() && !"-".equals(currPrefix) ) {
            secretChosen = true;
        }
        
        // Assign role
        if (isA) {
            myRole = "A";
        } else if (givenRoles) {
            myRole = "B";
        } else {
            myRole = null;
        }

        // Call appropriate function
        if (givenRoles) {
            givenRolesUpdateGUI(secretChosen, isA);
        }
        
        else if (!givenRoles) {
            notGivenRolesGUIUpdate(ownName);
        } 
    }

    // Function to run in listener thread
    private void listenerThreadFunc() {
        try {
            String ln = in.readLine();
            List<String> list = new ArrayList<>();

            while (isRunning && ln != null) {

                if (!ln.trim().isEmpty()) { // non-empty message
                    list.add(ln);
                } else { // all messages have been added to list
                    if (!list.isEmpty()) { // non-empty list
                        processMessage(list);
                        list.clear();
                    }
                }

                ln = in.readLine();
            }
        } catch (IOException e) {}

        if (!disconnected && isRunning) {
            attemptReconnectLoop(); // launch reconnection thread 
        }

        Platform.runLater(() -> statusLabel.setText("Disconnected")); // once done, set disabled
    }

    // Starts thread to listen from server
    private void startListener() {
        listenerThread = new Thread(this::listenerThreadFunc, "listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Handle pressing connection button
    @FXML
    private void onConnect() {
        if (isRunning) { // if game is already running do nothing
            return;
        }

        // Get host and port
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        int port = Integer.parseInt(portText);

        // Update host and port
        disconnected = false;
        prevHost = host;
        prevPort = port;

        try {
            // Create socket and input/output streams
            socket = new Socket(host, port);

            InputStream socket_input = socket.getInputStream();
            OutputStream socket_output = socket.getOutputStream();
            InputStreamReader instream = new InputStreamReader(socket_input);

            in = new BufferedReader(instream);
            out = new PrintWriter(socket_output, true);

            isRunning = true;

            onConnect_GUIUpdate();
            startListener(); // start new thread to listen from server

        } catch (IOException e) {
            onConnectionFailure_GUIUpdate(e);
        }
    }

    // Handle pressing disconnect button
    @FXML
    private void onDisconnect() {
        closeSocket();

        // Reset booleans
        disconnected = true;
        joined = false;
        isRunning = false;

        // Show status
        statusLabel.setText("Disconnected");
        statusLabel.setStyle("-fx-text-fill: #000000;");

        // Reset GUI
        resetConnectionButtons();
        lobbyPanel.setVisible(false);
        lobbyPanel.setManaged(false);

        gamePanel.setVisible(false);
        gamePanel.setManaged(false);

        playerListArea.clear();
        readyStatusLabel.setText("");

        secretField.clear();
        guessFieldA.clear();

        roleLabel.setText("");
        prefixLabel.setText("");
        livesLabel.setText("");
        aLabel.setText("");
        historyArea.clear();

        roleBPanel.setVisible(false);
        roleBPanel.setManaged(false);

        roleAPanel.setVisible(false);
        roleAPanel.setManaged(false);

        hintPublicField.clear();
        hintIntendedField.clear();
        guessFieldB.clear();

        nameField.setDisable(false);
        hostField.setDisable(false);
        portField.setDisable(false);
    }

    // Handling pressing join button
    @FXML
    private void onJoin() {
        String name = nameField.getText().trim();

        if (!name.isEmpty()) {
            joinBtn.setDisable(true);
            send("TYPE:JOIN\nNAME:" + name);
            joined = true;
        }
    }

    // Handle pressing ready
    @FXML
    private void onReady() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Enter a name before readying");
            return;
        }

        send("TYPE:READY");
    }

    // Handle pressing set secret button
    @FXML
    private void onSetSecret() {
        String sec = secretField.getText().trim();

        if (!sec.isEmpty()) {
            send("TYPE:SET_SECRET\nSECRET:" + sec);
        }
    }

    // Handle pressing guess button 
    @FXML
    private void onGuess() {
        TextField activeField = null;
        boolean wasChanged = false;

        // Get guess field depending on role
        if ("B".equals(myRole)) {
            wasChanged = true;
            activeField = guessFieldB;
        }

        else if ("A".equals(myRole)) {
            wasChanged = true;
            activeField = guessFieldA;
        } 
        
        if (!wasChanged || activeField == null) {
            return;
        }

        // Get actual guess
        String guess = activeField.getText().trim();
        if (guess.isEmpty()) {
            return;
        }
        
        send("TYPE:SUBMIT_GUESS\nGUESS:" + guess);
    }


    // Handle pressing submit hint button
    @FXML
    private void onStartHint() {
        String hint = hintPublicField.getText().trim();
        String intended = hintIntendedField.getText().trim();

        if (hint.isEmpty() || intended.isEmpty()) { // Empty hints
            return;
        }

        send("TYPE:START_HINT\nHINT:" + hint + "\nINTENDED:" + intended);
    }

    // Handle pressing connect button (for hint)
    @FXML
    private void onConnectAttempt() {
        send("TYPE:CONNECT");
    }

    // Handle pressing quit button
    @FXML
    private void onQuit() {
        isRunning = false;
        closeSocket();
        Platform.exit();
    }
}
