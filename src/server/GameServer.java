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
    

}