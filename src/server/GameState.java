package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***********
 * stores the state of the game on the server.
 * Is only changed via functions from Server so we dont get any race conditions due to race client(synchronized).
 *********/
public class GameState {
    private String secret = "";
    private String prefix = "";
    private int lives = 5;
    private String activePlayer = null; // Defender's name
    private final Map<String, Boolean> readiness = new HashMap<>(); // to check if player ready or not

    // info for whenever hint is called
    private String pHintGiver = null;
    private String pHintPublic = null;
    private String pIntended = null; 

    // for Connection 
    private String connectionRequester = null; 
    private final Map<String, String> connectionGuesses = new HashMap<>(); // map for guess made for each player (maybe not needed as such CHNAGE LATER)

    private boolean endGame = false;

    private final List<String> history = new ArrayList<>();

    // getters + setters
    public String getSecret(){ return secret;}
    public void setSecret(String secret){ this.secret = secret == null ? "" : secret.trim(); }
    public String getPrefix(){return prefix;}
    public int getLives(){return lives;}
    public String getActivePlayer(){return activePlayer;}
    public void setActivePlayer(String player){this.activePlayer = player;}

    public void revealInitialPrefix() {
        if(secret.length()>0) prefix = secret.substring(0,1);
        else prefix = "";
    }
    public void revealNextLetter(){
        if (prefix.length() < secret.length()) {
            prefix = secret.substring(0, prefix.length() + 1);
        }
    }
    public boolean isSecretFullyRevealed(){
        return prefix.equalsIgnoreCase(secret) && !secret.isEmpty();
    }

    public void resetLives(){lives = 5;}
    public void loseLife(){lives = Math.max(0, lives - 1);}

    // pending hint
    public void setPendingHint(String giver, String publicHint, String intended){
        this.pHintGiver = giver;
        this.pHintPublic = publicHint;
        this.pIntended = intended;
    }
    public String getPendingHintGiver(){ return pHintGiver;}
    public String getPendingIntended(){return pIntended;}
    public String getPendingHintPublic(){return pHintPublic;}
    public boolean isHintActive(){return pHintGiver != null;}
    public boolean isHintPhaseAllowed(){return !endGame && !secret.isEmpty();}

    public void clearPendingHint(){
        pHintGiver = null;
        pHintPublic = null;
        pIntended = null;
    }

    // connection window
    public void setConnectionRequester(String b2){
        this.connectionRequester = b2;
        this.connectionGuesses.clear();
    }
    public String getConnectionRequester(){return connectionRequester;}
    public void putGuess(String playerName, String guess){
        if (playerName != null && connectionRequester != null){
            connectionGuesses.put(playerName, guess);
        }
    }
    public String getGuess(String playerName){return connectionGuesses.get(playerName);}
    public boolean isConnectionWindowOpen(){return connectionRequester != null;}
    public boolean isConnectionReadyToResolve(){
        // require A and b2 guesses
        if (connectionRequester == null || activePlayer == null) return false;
        return connectionGuesses.containsKey(connectionRequester) && connectionGuesses.containsKey(activePlayer);
    }
    public void clearConnection(){
        connectionRequester = null;
        connectionGuesses.clear();
    }
    // mostly self explanatory and basic functions
    public boolean isGameOver(){return endGame;}
    public void setGameOver(boolean v){endGame = v;}

    public void addHistory(String s){history.add(s);}
    public List<String> getHistory(){return new ArrayList<>(history);}

    // readiness management
    public void setReady(String player, boolean ready) {
        if (player != null) readiness.put(player, ready);
    }
    public void removePlayer(String player){readiness.remove(player);}
    public boolean isReady(String player){return readiness.getOrDefault(player, false);}
    public Map<String, Boolean> getReadinessSnapshot(){return new HashMap<>(readiness);}
}
