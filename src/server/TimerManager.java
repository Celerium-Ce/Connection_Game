package server; 
import java.util.concurrent.*;

// for managing our server-side timeouts for hints & connections
public class TimerManager { 
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final GameServer server;
    private final GameState state;
    private ScheduledFuture<?> hintFuture, connectionFuture; // Handles to running/scheduled timer tasks

    //constructor
    public TimerManager(GameServer server, GameState state) { 
        this.server = server; this.state = state; 
    }

    public void scheduleHintTimeout(String giver) { // timeout (60s)

        // if hint timer already running, we don't start a new one
        if (hintFuture != null && !hintFuture.isDone()) {
            return;
        }
         
        hintFuture = scheduler.schedule(() -> { 
            synchronized (server) { // locking server for thread safety
                // after 60 sec if hint still active & from same player, process timeout
                if (state.isHintActive() && giver.equals(state.getPendingHintGiver())) { 
                    state.addHistory("Hint timeout has expired for " + giver); 
                    server.broadcast("TYPE:HINT_TIMEOUT\nGIVER:" + giver); 
                    state.clearPendingHint(); 
                    server.broadcastState(); 
                }
            }
        }, 60, TimeUnit.SECONDS); 
    }

    // cancel hint timer and null ref
    public void cancelHintTimeout() { 
        if (hintFuture != null && !hintFuture.isDone()) {
            hintFuture.cancel(true); 
            hintFuture = null; 
        }
    }

    public void scheduleConnectionWindow(String b2) { // timeout (10s)
        // if connection timer already running, we don't start a new one
        if (connectionFuture != null && !connectionFuture.isDone()){
            return;
        }
        connectionFuture = scheduler.schedule(() -> { 
            synchronized (server) { 
                server.resolveConnection(); 
            } 
        }, 10, TimeUnit.SECONDS);
    }

    
    public void cancelConnectionTimer() { 
        if (connectionFuture != null && !connectionFuture.isDone()) {
            connectionFuture.cancel(true); 
            connectionFuture = null; 
        }
    }

    // for cleanup we cancel timers and stop scheduler
    public void shutdown() { 
        cancelHintTimeout(); 
        cancelConnectionTimer(); 
        scheduler.shutdownNow(); 
    }
}
