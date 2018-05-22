package com.drawingly;

import java.net.InetSocketAddress;
import java.util.Objects;
import org.json.simple.JSONObject;


public class Player {
    
    String name;
    int score;
    boolean guessed;
    InetSocketAddress address;
    int roundsSinceDrawed;
    
    public Player(String name, InetSocketAddress address) {
        this.name = name;
        this.score = 0;
        this.guessed = false;
        this.address = address;
        roundsSinceDrawed = 0;
    }
    
    public String getJsonString() {
        JSONObject jsonMessage = new JSONObject();
        jsonMessage.put("name", name);
        jsonMessage.put("score", score);
        jsonMessage.put("guessed", guessed);
        return jsonMessage.toJSONString();
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Player) {
            Player otherPlayer = (Player) other;
            return name.equals(otherPlayer.name) &&
                   address.equals(otherPlayer.address);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.name);
        hash = 89 * hash + this.score;
        hash = 89 * hash + Objects.hashCode(this.address);
        return hash;
    }
    
}
