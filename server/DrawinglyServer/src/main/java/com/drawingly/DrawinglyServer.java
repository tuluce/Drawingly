package com.drawingly;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Scanner;
import javax.swing.Timer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class DrawinglyServer extends WebSocketServer {
    
    private static final int NAME_CONFLICT_CODE = 4001;
    private static final int PLAYER_OVERFLOW_CODE = 4003;
    
    private static GameConfig gameConfig;
    
    private ArrayList<String> wordList;
    private Iterator<String> wordIterator;
    private String currentWord;
    
    private final JSONParser jsonParser;
    private final PlayerList playerList;
    private final Timer secondTimer;
    private Player drawerPlayer;
    private int timeLeft;
    private int idleTimeLeft;
    private boolean isIdle;
    
    public DrawinglyServer(InetSocketAddress address) {
        super(address);
        playerList = new PlayerList();
        jsonParser = new JSONParser();
        timeLeft = gameConfig.roundTime;
        secondTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                DrawinglyServer.this.roundTick();
            }
        });
        secondTimer.start();
        isIdle = false;
        initWords();
        changeWord();
    }
    
    private void initWords() {
        Scanner wordScanner;
        try {
            wordScanner = new Scanner(new File("words.txt"));
            serverLog("The words file has been read successfully.");
        } catch (FileNotFoundException exc) {
            serverLog("The words file 'words.txt' not found on the working directory, " +
                    "setting the word list to default.");
            wordScanner = new Scanner(DefaultWords.DEFAULT_WORDS);
        }
        wordList = new ArrayList<String>();
        while (wordScanner.hasNext()) {
            Scanner wordSeparator = new Scanner(wordScanner.nextLine());
            String normalizedStr = "";
            while (wordSeparator.hasNext()) {
                normalizedStr += wordSeparator.next();
                normalizedStr += " ";
            }
            wordList.add(normalizedStr.substring(0, normalizedStr.length() - 1));
        }
        shuffleWordList();
        wordIterator = wordList.iterator();
    }
    
    private void changeWord() {
        if (!wordIterator.hasNext()) {
            shuffleWordList();
            wordIterator = wordList.iterator();
        }
        currentWord = wordIterator.next();
    }
    
    private void shuffleWordList() {
        Collections.shuffle(wordList);
    }
    
    private String getNextDrawer() {
        if (playerList.size() > 0) {
            Player currentDrawer = playerList.get(0);
            Iterator<Player> playerIterator = playerList.iterator();
            while (playerIterator.hasNext()) {
                Player currentPlayer = playerIterator.next();
                if (currentPlayer.roundsSinceDrawed > currentDrawer.roundsSinceDrawed) {
                    currentDrawer = currentPlayer;
                }
            }
            drawerPlayer = currentDrawer;
            return drawerPlayer.name;
        }
        return "Waiting...";
    }
    
    private void roundTick() {
        if (!isIdle) {
            if (--timeLeft <= 0) {
                endTheRoundTimer();
            }
        } else {
            if (--idleTimeLeft <= 0) {
                if (playerList.size() > 1) {
                    timeLeft = gameConfig.roundTime;
                    isIdle = false;
                    startNewRound();
                } else {
                    idleTimeLeft = gameConfig.idleTime;
                    isIdle = true;
                }
            }
        }
    }
    
    private void endTheRoundTimer() {
        idleTimeLeft = gameConfig.idleTime;
        isIdle = true;
        endTheRound();
    }
    
    private int getGuessedNo() {
        int guessedNo = 0;
        Iterator<Player> playerIterator = playerList.iterator();
        while (playerIterator.hasNext()) {
            if (playerIterator.next().guessed) {
                guessedNo++;
            }
        }
        return guessedNo;
    }
    
    private void endTheRound() {
        Iterator<Player> playerIterator = playerList.iterator();
        while (playerIterator.hasNext()) {
            playerIterator.next().roundsSinceDrawed++;
        }
        if (drawerPlayer != null) {
            drawerPlayer.roundsSinceDrawed = 0;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "endround");
        jsonObject.put("word", currentWord);
        jsonObject.put("guessers", getGuessedNo());
        broadcast(jsonObject.toJSONString());
    }
    
    private void startNewRound() {
        changeWord();
        clearGuesses();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "startround");
        jsonObject.put("name", getNextDrawer());
        jsonObject.put("time", gameConfig.roundTime);
        jsonObject.put("count", getLetterCountArray()); // here
        broadcast(jsonObject.toJSONString());
        broadcastLargeUpdate();
        informTheDrawer();
    }
    
    private void informTheDrawer() {
        if (drawerPlayer != null) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "drawerselect");
            jsonObject.put("word", currentWord);
            sendToPlayer(drawerPlayer, jsonObject.toJSONString());
        }
    }
    
    private void sendToPlayer(Player player, String packet) {
        InetSocketAddress drawerAddress = player.address;
        Collection<WebSocket> currentSockets = getConnections();
        Iterator<WebSocket> socketIter = currentSockets.iterator();
        while (socketIter.hasNext()) {
            WebSocket currentSocket = socketIter.next();
            if (currentSocket.getRemoteSocketAddress().equals(drawerAddress)) {
                
                currentSocket.send(packet);
            }
        } 
    }
    
    private void checkAllGuessed() {
        Iterator<Player> playerIterator = playerList.iterator();
        while (playerIterator.hasNext()) {
            Player nextPlayer = playerIterator.next();
            if (!nextPlayer.guessed && nextPlayer != drawerPlayer) {
                return;
            }
        }
        endTheRoundTimer();
    }
    
    private void clearGuesses() {
        Iterator<Player> playerIterator = playerList.iterator();
        while (playerIterator.hasNext()) {
            playerIterator.next().guessed = false;
        }
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        serverLog("New connection to <" + conn.getRemoteSocketAddress() + ">");
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        serverLog("Closed <" + conn.getRemoteSocketAddress() + 
                "> with code " + code + ". Reason: " + reason);
        releasePlayer(conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        parseMessage(conn, message);
    }
    
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        serverLog("Received ByteBuffer from <" + conn.getRemoteSocketAddress() + ">");
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        serverLog("An error occured on connection <" + conn.getRemoteSocketAddress()  + ">: " + ex);
    }
    
    @Override
    public void onStart() {
        serverLog("Server started successfully on port " + gameConfig.serverPort + ".");
        serverLog("Press Ctrl+C to stop the server.");
    }
    
    private void releasePlayer(InetSocketAddress address) {
        Player leftPlayer = playerList.getPlayer(address);
        if (leftPlayer == null) {
            serverLog("The player that left could not be found.");
        } else {
            playerList.removePlayer(leftPlayer);
            broadcastLargeUpdate();
            broadcastLeave(leftPlayer.name);
            serverLog("Player left: " + leftPlayer.name);
            if (drawerPlayer != null && drawerPlayer.equals(leftPlayer)) {
                timeLeft = Math.min(2, timeLeft);
            }
        }
    }
    
    private void broadcastLargeUpdate() {
        if (playerList.size() == 1) {
            endTheRoundTimer();
        }
        broadcastPlayerList();
        broadcastDrawer();
        broadcastTimeLeft();
        broadcastLetterCount();
    }
    
    private void broadcastEntrance(String enteredName) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "entrance");
        jsonObject.put("name", enteredName);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastLeave(String leftName) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "leave");
        jsonObject.put("name", leftName);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastPlayerList() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "players");
        JSONArray playerArray = new JSONArray();
        Iterator<Player> playerIterator = playerList.iterator();
        while (playerIterator.hasNext()) {
            playerArray.add(playerIterator.next().getJsonString());
        }
        jsonObject.put("players", playerArray);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastDrawer() {
        if (drawerPlayer != null) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "drawer");
            jsonObject.put("name", drawerPlayer.name);
            broadcast(jsonObject.toJSONString());
        }
    }
    
    private void broadcastTimeLeft() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "timeupdate");
        jsonObject.put("time", timeLeft);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastLetterCount() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "letterupdate");
        jsonObject.put("count", getLetterCountArray()); // here
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastCanvas(String canvasData) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "canvasupdate");
        jsonObject.put("data", canvasData);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastGuessed(String guesserName, int score) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "guessed");
        jsonObject.put("name", guesserName);
        jsonObject.put("score", score);
        broadcast(jsonObject.toJSONString());
    }
    
    private void broadcastChat(String guesserName, String guessedWord) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "chat");
        jsonObject.put("name", guesserName);
        jsonObject.put("content", guessedWord);
        broadcast(jsonObject.toJSONString());
    }
    
    private void parseMessage(WebSocket conn, String message) {
        try {
            Object object = jsonParser.parse(message);
            JSONObject jsonObject = (JSONObject) object;
            String type = (String) jsonObject.get("type");
            
            if (type.equals("enter")) {
                String name = (String) jsonObject.get("name");
                name = clearInvalids(name);
                if (playerList.size() >= gameConfig.maxPlayerNum) {
                    conn.close(PLAYER_OVERFLOW_CODE);
                    serverLog("Client kicked because of player overflow: " + name);
                } else if (playerList.getPlayer(name) != null) {
                    conn.close(NAME_CONFLICT_CODE);
                    serverLog("Client with name conflict kicked: " + name);
                }else {
                    playerList.addPlayer(name, conn.getRemoteSocketAddress());
                    serverLog("Player joined: " + name);
                    broadcastLargeUpdate();
                    broadcastEntrance(name);
                }
            }
            
            else if (type.equals("draw")) {
                if (drawerPlayer != null && conn.getRemoteSocketAddress().equals(drawerPlayer.address)) {
                    String canvasData = (String) jsonObject.get("data");
                    broadcastCanvas(canvasData);
                }
            }
            
            else if (type.equals("guess")) {
                String guesserName = (String) jsonObject.get("name");
                Player guesserPlayer = playerList.getPlayer(guesserName);
                if (guesserPlayer == null) {
                    serverLog("Unknown player sent message: " + guesserName);
                } else if (!guesserPlayer.address.equals(conn.getRemoteSocketAddress())) {
                    serverLog("Unauthorized message: <" + conn.getRemoteSocketAddress() + "><" + guesserName + ">");
                } else {
                    String guessedWord = (String) jsonObject.get("word");
                    guessedWord = guessedWord.trim();
                    guessedWord = clearInvalids(guessedWord);
                    if (guessedWord.length() > gameConfig.maxChatCharNum) {
                        serverLog("Player sent a too long message: " + guesserName);
                    } else if (guessedWord.length() == 0) {
                        serverLog("Player sent an invalid message: " + guesserName);
                    } else if (isIdle) {
                        broadcastChat(guesserName, guessedWord);
                    } else if (!guesserPlayer.equals(drawerPlayer) && !guesserPlayer.guessed) {
                        if (guessedWord.equals(currentWord)) {
                            guesserPlayer.guessed = true;
                            guesserPlayer.score += gameConfig.guesserGuessScore;
                            drawerPlayer.score += gameConfig.drawerGuessScore;
                            broadcastGuessed(guesserName, guesserPlayer.score);
                            checkAllGuessed();
                            JSONObject jsonObject2 = new JSONObject();
                            jsonObject2.put("type", "guessedword");
                            jsonObject2.put("word", currentWord);
                            conn.send(jsonObject2.toJSONString());
                        } else {
                            broadcastChat(guesserName, guessedWord);
                        }
                    }
                }
            }
            
            else {
                serverLog("Unknown message type: " + type);
            }
            
        } catch (ParseException e) {}
    }
    
    private boolean isValid(char inputChar) {
        for (char c : gameConfig.validChars) {
            if (inputChar == c) {
                return true;
            }
        }
        return false;
    }
    
    private String clearInvalids(String inputString) {
        StringBuilder clearedString = new StringBuilder();
        for (int i = 0; i < inputString.length(); i++) {
            if (isValid(inputString.charAt(i))) {
                clearedString.append(inputString.charAt(i));
            }
        }
        return clearedString.toString();
    }
    
    private JSONArray getLetterCountArray() {
        JSONArray countArray = new JSONArray();
        Scanner wordSelector = new Scanner(currentWord);
        while (wordSelector.hasNext()) {
            countArray.add(wordSelector.next().length());
        }
        return countArray;
    }
    
    private static boolean initGameConfig() {
        try {
            JSONParser jsonParser = new JSONParser();
            FileReader configReader = new FileReader("config.json");
            Object object = jsonParser.parse(configReader);
            JSONObject jsonObject = (JSONObject) object;
            int serverPort = ((Long) jsonObject.get("serverPort")).intValue();
            int maxPlayerNum = ((Long) jsonObject.get("maxPlayerNum")).intValue();
            int roundTime = ((Long) jsonObject.get("roundTime")).intValue();
            int idleTime = ((Long) jsonObject.get("idleTime")).intValue();
            int drawerGuessScore = ((Long) jsonObject.get("drawerGuessScore")).intValue();
            int guesserGuessScore = ((Long) jsonObject.get("guesserGuessScore")).intValue();
            int maxChatCharNum = ((Long) jsonObject.get("maxChatCharNum")).intValue();
            JSONArray validCharsJsArr = (JSONArray) jsonObject.get("validChars");
            Iterator charIter = validCharsJsArr.iterator();
            ArrayList<Character> validCharsArrList = new ArrayList<Character>();
            while (charIter.hasNext()) {
                validCharsArrList.add(((String) charIter.next()).charAt(0));
            }
            char[] validChars = new char[validCharsArrList.size()];
            for (int i = 0; i < validCharsArrList.size(); i++) {
                validChars[i] = validCharsArrList.get(i);
            }
            gameConfig = new GameConfig(serverPort, maxPlayerNum, roundTime, idleTime, drawerGuessScore,
                    guesserGuessScore, maxChatCharNum, validChars);
        } catch (FileNotFoundException exc) {
            serverLog("The config file 'config.json' not found on the working directory, " +
                    "setting the configs to default.");
            gameConfig = new GameConfig();
            return false;
        } catch (IOException exc) {
            serverLog("An error occured while reading the config file 'config.json', " +
                    "setting the configs to default.");
            gameConfig = new GameConfig();
            return false;
        } catch (ParseException exc) {
            serverLog("The config file 'config.json' is not a valid JSON file, " + 
                    "setting the configs to default.");
            gameConfig = new GameConfig();
            return false;
        }
        serverLog("The config file has been read succesfully.");
        return true;
    }
    
    private static void serverLog(String logMessage) {
        System.out.println("> " + logMessage);
    }
    
    public static void main(String[] args) throws Exception {
        serverLog("Starting the server...");
        initGameConfig();
        WebSocketServer server = new DrawinglyServer(new InetSocketAddress(gameConfig.serverPort));
        server.run();
    }
    
}
