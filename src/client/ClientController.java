package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class ClientController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField nameField;
    @FXML private Button connectBtn;
    @FXML private Button joinBtn;
    @FXML private Button disconnectBtn;
    @FXML private Label statusLabel;

    @FXML private VBox lobbyPanel;
    @FXML private Button readyBtn;
    @FXML private Label readyStatusLabel;
    @FXML private TextArea playerListArea;

    @FXML private VBox gamePanel;
    @FXML private Label roleLabel;
    @FXML private Label prefixLabel;
    @FXML private Label livesLabel;
    @FXML private Label aLabel;

    @FXML private VBox roleAPanel;
    @FXML private TextField secretField;
    @FXML private Button setSecretBtn;
    @FXML private TextField guessFieldA;
    @FXML private Button guessBtn;

    @FXML private VBox roleBPanel;
    @FXML private TextField hintPublicField;
    @FXML private TextField hintIntendedField;
    @FXML private Button startHintBtn;
    @FXML private Button connectBtn2;
    @FXML private TextField guessFieldB;
    @FXML private Button guessBtnB;

    @FXML private TextArea historyArea;

    @FXML
    private void initialize() {
    }

    @FXML
    private void onConnect() {
    }

    @FXML
    private void onDisconnect() {
    }

    @FXML
    private void onJoin() {
    }

    @FXML
    private void onReady() {
    }

    @FXML
    private void onSetSecret() {
    }

    @FXML
    private void onGuess() {
    }

    @FXML
    private void onStartHint() {
    }

    @FXML
    private void onConnectAttempt() {
    }

    @FXML
    private void onPing() {
    }

    @FXML
    private void onQuit() {
    }
}
