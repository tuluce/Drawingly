package com.drawingly;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;


public class PlayerList extends ArrayList<Player> {
    
    public void addPlayer(String name, InetSocketAddress address) {
        Player player = new Player(name, address);
        add(player);
    }
    
    public Player getPlayer(InetSocketAddress address) {
        Iterator<Player> playerIterator = iterator();
        while (playerIterator.hasNext()) {
            Player player = playerIterator.next();
            if (player.address.equals(address)) {
                return player;
            }
        }
        return null;
    }
    
    public Player getPlayer(String name) {
        Iterator<Player> playerIterator = iterator();
        while (playerIterator.hasNext()) {
            Player player = playerIterator.next();
            if (player.name.equals(name)) {
                return player;
            }
        }
        return null;
    }
    
    public boolean removePlayer(Player player) {
        Iterator<Player> playerIterator = iterator();
        while (playerIterator.hasNext()) {
            if (playerIterator.next().equals(player)) {
                playerIterator.remove();
                return true;
            }
        }
        return false;
    }
    
}
