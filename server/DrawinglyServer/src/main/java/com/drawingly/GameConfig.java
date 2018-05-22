package com.drawingly;


public class GameConfig {
    
    public final int serverPort;
    public final int maxPlayerNum;
    public final int roundTime;
    public final int idleTime;
    public final int drawerGuessScore;
    public final int guesserGuessScore;
    public final int maxChatCharNum;
    public final char[] validChars;
    
    public GameConfig() {
        serverPort = 8887;
        maxPlayerNum = 30;
        roundTime = 61;
        idleTime = 5;
        drawerGuessScore = 3;
        guesserGuessScore = 10;
        maxChatCharNum = 50;
        validChars = new char[] {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
            'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
            'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2',
            '3', '4', '5', '6', '7', '8', '9', ' ', '-', '_', '.',
            '\u00F6', '\u00E7', '\u015F', '\u0131', '\u011F', '\u00FC',
            '\u00D6', '\u00C7', '\u015E', '\u0130', '\u011E', '\u00DC'};
    }
    
    public GameConfig(int serverPort, int maxPlayerNum, int roundTime, int idleTime,
            int drawerGuessScore, int guesserGuessScore, int maxChatCharNum,
            char[] validChars) {
        
        this.serverPort = serverPort;
        this.maxPlayerNum = maxPlayerNum;
        this.roundTime = roundTime;
        this.idleTime = idleTime;
        this.drawerGuessScore = drawerGuessScore;
        this.guesserGuessScore = guesserGuessScore;
        this.maxChatCharNum = maxChatCharNum;
        this.validChars = validChars;
    }
    
}
