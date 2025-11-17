package client;

import java.util.Map;

public class ParsedMessage {
    final String type;            
    final String prefix; 
    final String lives;
    final String roleAValue;     
    final String history;
    final Map<String, Boolean> playerReadiness;

    public ParsedMessage(String type, String prefix, String lives, String roleAValue, String history, Map<String, Boolean> playerReadiness) {
        this.type = type;
        this.prefix = prefix;
        this.lives = lives;
        this.roleAValue = roleAValue;
        this.history = history;
        this.playerReadiness = playerReadiness;
    }
}
