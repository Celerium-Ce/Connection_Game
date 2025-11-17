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


    
}