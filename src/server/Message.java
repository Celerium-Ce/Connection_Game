package server;
import java.io.BufferedReader; 
import java.io.IOException;
import java.util.*; 

// to parse key:value messages
public class Message {
    private final Map<String, String> map = new HashMap<>(); 
    public void put(String k, String v) 
    { map.put(k, v); 
    }
    public String get(String k) { 
        return map.get(k); 
    }

    // to read one message from stream
    public static Message readFrom(BufferedReader in) throws IOException {
        Message m = new Message();
        String cur_line;
        boolean any = false; // becomes true if we read any non-empty line
        while ((cur_line = in.readLine()) != null) {
            if (cur_line.trim().isEmpty()) {
                if (!any){
                    return null;
                }
                break;
            }
            any = true; 
            int idx = cur_line.indexOf(':');
            if (idx <= 0) continue; // irrelevant line
            String k = cur_line.substring(0, idx).trim(); // key
            String v = cur_line.substring(idx+1).trim(); // value
            m.put(k, v); 
        }
        if (!any) return null;
        return m; // the message map
    }
}