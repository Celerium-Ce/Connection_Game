package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameServer class
 * Handles connection of players to the game server.
 * Manages player sessions and game state.
 */

class GameServer{
    private final int port; // Port # for connections
    private final Map<String, ClientHandler> handlers = new ConcurrentHashMap<>(); // Map for storing active clients via name
    private final GameState gameState = new GameState(); // to be made in different branch
    private final TimerManager timerManager; // being made in different branch

    public GameServer(int port){ //just sets port
        this.port = port;
        this.timerManager = new TimerManager(this, gameState);
    }

    //main loop which runs for the entire code
    public static void main(String[] args) throws IOException{ //is the main function which does setup etc when server starts 
        int port = 5000; //default port
        if (args != null && args.length > 0){
            try{ port = Integer.parseInt(args[0]); } 
            catch (NumberFormatException ignored){}
        }
        GameServer server = new GameServer(port); // create server
        server.start(); // run server
    }

    public void start() throws IOException{ //starts the server afte setup
        System.out.println("starting server on " + port);
        try (ServerSocket ss = new ServerSocket(port)){
            //main loop for accepting clients
            while (true){
                Socket sock = ss.accept();
                System.out.println("Client connected: " + sock.getRemoteSocketAddress());
                ClientHandler h = new ClientHandler(sock, this); //pass the game server as arguement
                h.start(); //start the clients handler thread
            }
        } finally{
            timerManager.shutdown(); //end the timer
        }
    }


    public void broadcast(String msg){ // function to send message to all connected clients (quite straightforward)
        System.out.println("[Broadcast] " + msg.replace('\n','||'));
        for (ClientHandler h : handlers.values()) { 
            h.send(msg);
        }
    }

    public void sendTo(String playerName, String msg){ // function to send message to specific client
        ClientHandler h = handlers.get(playerName);
        if (h != null) h.send(msg);
    }

    // Main and important function for giving the currennt game state to every client (Should be called after any gameState update)
    public void broadcastState(){
        StringBuilder sb = new StringBuilder(); // To communicate the state we serialize everything into a string and send it
        sb.append("TYPE:STATE_UPDATE\n");
        sb.append("PREFIX:").append(gameState.getPrefix()).append("\n");
        sb.append("LIVES:").append(gameState.getLives()).append("\n");
        sb.append("A:").append(gameState.getActivePlayer() == null ? "" : gameState.getActivePlayer()).append("\n");
        sb.append("PLAYERS_START\n");
        for (String p : handlers.keySet()) {
            boolean r = gameState.isReady(p);
            sb.append("PLAYER:").append(p).append(":READY:").append(r).append("\n");
        }
        sb.append("PLAYERS_END\n");
        sb.append("HISTORY_START\n");
        for (String h : gameState.getHistory()) {
            sb.append(h.replace("\n"," ")).append("\n");
        }
        sb.append("HISTORY_END\n");
        broadcast(sb.toString());
    }

    // when client handler explicitly needs the gameState
    public synchronized GameState getGameState() {
        return gameState;
    }


    // functions for registering and unregistering clients (to be called by client handler)
    public synchronized void registerHandler(String playerName, ClientHandler handler){
        handlers.put(playerName, handler);
        gameState.setReady(playerName, false); // new player is not ready by default
        broadcast("TYPE:PLAYER_JOINED\nNAME:" + playerName);
        broadcastState();
    }

    public synchronized void unregisterHandler(String playerName){
        handlers.remove(playerName);
        gameState.removePlayer(playerName); // remove player from game state
        broadcast("TYPE:PLAYER_LEFT\nNAME:" + playerName);
        broadcastState();
    }

    // functions for starting game ish

    public synchronized void markReady(String playerName){
        if (!handlers.containsKey(playerName)){
            sendTo(playerName, "TYPE:ERROR\nMSG:Not connected");
            return;
        }
        gameState.setReady(playerName,true);
        broadcast("TYPE:PLAYER_READY\nNAME:" +playerName);
        // Check all ready
        if (handlers.size() >= 3){
            boolean allReady = true;
            for (String p : handlers.keySet()){ // check if all ready
                if (!gameState.isReady(p)){allReady = false; break;}
            }
            if (allReady && gameState.getActivePlayer() == null){ // start game
                // select A randomly
                List<String> players = new ArrayList<>(handlers.keySet());
                String a = players.get(new Random().nextInt(players.size()));
                //game state updates
                gameState.setActivePlayer(a);
                gameState.addHistory("=== GAME STARTED ===");
                gameState.addHistory("Role Assignment: Player " + a + " is DEFENDER (A)");
                gameState.addHistory("All other players are ATTACKERS (B)");
                gameState.addHistory("Waiting for A to set secret word...");
                broadcast("TYPE:ROLES_ASSIGNED\nA:" + a);
                // send updated game state to all clients
                broadcastState();
            } else {
                broadcastState();
            }
        } else{
            broadcast("TYPE:INFO\nMSG:Need at least 3 players ready to start");
            broadcastState();
        }
    }
    
    public synchronized void setSecret(String setterName, String secret){ // to be called by A to set the word
        if (gameState.getActivePlayer() != null && !setterName.equals(gameState.getActivePlayer())){ //checks if the player u called is A
            sendTo(setterName, "TYPE:ERROR\nMSG:Only A can set the secret");
            return;
        }
        // Update game state
        gameState.setSecret(secret);
        gameState.setActivePlayer(setterName); // A
        gameState.revealInitialPrefix();
        gameState.resetLives();
        gameState.addHistory("---");
        gameState.addHistory("SECRET SET by A (" + setterName + ")");
        gameState.addHistory("Initial Prefix: " + gameState.getPrefix() + " | Lives: " + gameState.getLives());
        gameState.addHistory("B players can now START_HINT to communicate!");
        // Broadcast updated state
        broadcastState();
    }

    // functions for connections and hints

    public synchronized void startHint(String hinter, String hint, String intendedW){
        if (!gameState.isHintPhaseAllowed()){
            sendTo(hinter, "TYPE:ERROR\nMSG:Hint not allowed in current phase");
            return;
        }
        gameState.setPendingHint(hinter, hint, intendedW);
        gameState.addHistory("---");
        gameState.addHistory("HINT STARTED by " + hinter);
        gameState.addHistory("Public Hint: \"" + hint + "\"");
        gameState.addHistory("(Intended word hidden from A - Timeout: 120s)");
        broadcast("TYPE:HINT_STARTED\nGIVER:" + hinter + "\nHINT:" + hint + "\nTIME:120");
        broadcastState();

        timerManager.scheduleHintTimeout(hinter);
    }

    public synchronized void requestConnect(String requester){
        if (!gameState.isHintActive()){
            sendTo(requester, "TYPE:ERROR\nMSG:No active hint");
            return;
        }
        if (gameState.isConnectionWindowOpen()){
            sendTo(requester, "TYPE:ERROR\nMSG:Connection already in progress");
            return;
        }
        // add to gamelogs
        gameState.setConnectionRequester(requester); 
        gameState.addHistory(">> CONNECTION ATTEMPT by " + requester);
        gameState.addHistory("Connection Window: 10 seconds");
        gameState.addHistory("Both A and " + requester + " must submit guesses!");
        broadcast("TYPE:CONNECTION_WINDOW\nB2:" + requester + "\nTIME:10");
        broadcastState();
        // start timer for connection window
        timerManager.scheduleConnectionWindow(requester);
    }

    public synchronized void submitGuess(String who, String guess){
        // store guess
        gameState.putGuess(who, guess);
        gameState.addHistory(who + " submitted guess (masked).");
        broadcastState();

        // if A and B2 has both guessed, check
        if (gameState.isConnectionReadyToResolve()){
            timerManager.cancelConnectionTimer();
            resolveConnection();
        }
    }

    public synchronized void resolveConnection(){
        // Gather data
        String b1 = gameState.getPendingHintGiver();
        String intended = gameState.getPendingIntended();
        String b2 = gameState.getConnectionRequester();
        String aPlayer = gameState.getActivePlayer();
        String b2Guess = gameState.getGuess(b2);
        String aGuess = gameState.getGuess(aPlayer);
        String secret = gameState.getSecret();

        gameState.addHistory("RESOLVING CONNECTION...");

        // check win condition
        if ((intended != null && intended.equalsIgnoreCase(secret)) || (b2Guess != null && b2Guess.equalsIgnoreCase(secret))){
            gameState.addHistory("=== GAME OVER ===");
            gameState.addHistory("WINNER: B (Attackers)");
            gameState.addHistory("Secret word guessed directly: " + secret);
            broadcast("TYPE:GAME_OVER\nWINNER:B\nMSG:Secret guessed via connection attempt");
            gameState.setGameOver(true);
            clearHintAndConnection();
            broadcastState();
            return;
        }

        // A guesses intended Z        
        if (aGuess != null && intended != null && aGuess.equalsIgnoreCase(intended)){
            gameState.loseLife();
            broadcast("TYPE:LIFE_LOST\nREMAINING:" + gameState.getLives());
            gameState.addHistory("✗ A guessed intended word correctly!");
            gameState.addHistory("PENALTY: Lost 1 life (Remaining: " + gameState.getLives() +")");
            if (gameState.getLives() <= 0){
                gameState.addHistory("=== GAME OVER ===");
                gameState.addHistory("WINNER: A (Defender)");
                gameState.addHistory("B team lost all lives on prefix: " + gameState.getPrefix());
                broadcast("TYPE:GAME_OVER\nWINNER:A\nMSG:B lost all lives on same prefix");
                gameState.setGameOver(true);
                broadcastState();
                return;
            }
            // continue with same prefix
            gameState.addHistory("Game continues with same prefix: " + gameState.getPrefix());
            clearHintAndConnection();
            broadcastState();
            return;
        }

        // A didn't guess intended.
        // If b2 guessed intended -> reveal next letter
        if (b2Guess != null && intended != null && b2Guess.equalsIgnoreCase(intended)){
            gameState.revealNextLetter();
            gameState.resetLives();
            broadcast("TYPE:CONNECTION_SUCCESS\nNEW_PREFIX:" + gameState.getPrefix() + "\nLIVES:5");
            gameState.addHistory("✓ CONNECTION SUCCESS!");
            gameState.addHistory("" + b2 + " guessed the intended word correctly!");
            gameState.addHistory("NEW PREFIX: " + gameState.getPrefix() + " | Lives reset to 5");
            if (gameState.isSecretFullyRevealed()){
                gameState.addHistory("=== GAME OVER ===");
                gameState.addHistory("WINNER: B (Attackers)");
                gameState.addHistory("Secret word fully revealed: " + gameState.getSecret());
                broadcast("TYPE:GAME_OVER\nWINNER:B\nMSG:Secret fully revealed");
                gameState.setGameOver(true);
            }
            clearHintAndConnection();
            broadcastState();
            return;
        }
        // Otherwise, failed connection — reveal b1 intended and b2 guess to history, no life lost
        gameState.addHistory("✗ CONNECTION FAILED");
        gameState.addHistory("Intended: '" + (intended==null?"(none)":intended) + "' | " + b2 + " guessed: '" + (b2Guess==null?"(none)":b2Guess) + "'");
        gameState.addHistory("No penalty. Game continues.");
        clearHintAndConnection();
        broadcast("TYPE:CONNECTION_FAILED\nMSG:Connection failed; no lives lost");
        broadcastState();
    }

    private void clearHintAndConnection() {
        gameState.clearPendingHint();
        gameState.clearConnection();
    }
}
