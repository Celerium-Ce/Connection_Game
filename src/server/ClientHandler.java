package server; 
import java.io.*; 
import java.net.Socket; 

// to handle one client connection in a thread
public class ClientHandler extends Thread { 
    private final Socket sock; // networking - client SOCKET
    private final GameServer server; 
    private final BufferedReader in; 
    private final PrintWriter out; 
    private String playerName = null; 
    private volatile boolean running = true; // this flag is thread safe to control loop

    // constructor
    public ClientHandler(Socket sock, GameServer server) throws IOException { 
        this.sock = sock; 
        this.server = server; 
        this.in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        this.out = new PrintWriter(sock.getOutputStream(), true);
    }

    @Override //Thread.run()
    public void run() {
        try {
            while (running) {
                Message msg = Message.readFrom(in); // one message from client
                if (msg == null) break;
                handleMessage(msg);
            }
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // each message has a TYPE field to route it, with error handling
    private void handleMessage(Message msg) {
        String type = msg.get("TYPE"); // Get message type
        if (type == null) { 
            send("TYPE:ERROR\nMSG:Missing TYPE"); return; 
        }
        switch (type) {
            case "JOIN": {
                String name = msg.get("NAME"); // gets name from message
                if (name == null || name.trim().isEmpty()) { 
                    send("TYPE:ERROR\nMSG:Missing NAME"); return; 
                }
                playerName = name.trim();
                server.registerHandler(playerName, this); 
                send("TYPE:JOINED\nNAME:" + playerName);
                break;
            }
            case "SET_SECRET": {
                if (playerName == null) { 
                    send("TYPE:ERROR\nMSG:Register (JOIN) first"); return; 
                }
                server.setSecret(playerName, msg.get("SECRET")); 
                break;
            }
            case "READY": {
                if (playerName == null) { 
                    send("TYPE:ERROR\nMSG:Register first"); return; 
                }
                server.markReady(playerName);
                break;
            }
            case "START_HINT": { 
                if (playerName == null) { 
                    send("TYPE:ERROR\nMSG:Register first"); return; 
                }
                server.startHint(playerName, msg.get("HINT"), msg.get("INTENDED"));
                break;
            }
            case "CONNECT": {
                if (playerName == null) { 
                    send("TYPE:ERROR\nMSG:Register first"); return;
                }
                server.requestConnect(playerName); 
                break;
            }
            case "SUBMIT_GUESS": {
                if (playerName == null) { 
                    send("TYPE:ERROR\nMSG:Register first"); return; 
                }
                server.submitGuess(playerName, msg.get("GUESS"));
                break;
            }
            case "PING": { // Heartbeat/keep-alive ping
                send("TYPE:PONG"); 
                break;
            }
            default: send("TYPE:ERROR\nMSG:Unknown TYPE " + type); 
        }
    }

    // send message to client thread safety
    public synchronized void send(String raw) { 
        out.println(raw);
        out.println(); // terminator
        out.flush(); // force send
    }

    private void cleanup() { // on disconnect
        running = false;
        try {
            if (playerName != null) {
                server.unregisterHandler(playerName);
             }
            sock.close(); // networking : SOCKET closed
        } catch (IOException ignored) {}
    }
}