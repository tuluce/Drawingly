/**
 * DrawinglyScript.js
 *
 * The main script that controlls the game client page.
 *
 * @author  Emin Bahadir Tuluce
 * @version 0.1.0
 *
 */

var canvas = document.getElementById('canvas');
var ctx = canvas.getContext('2d');
var canvasX = $(canvas).offset().left;
var canvasY = $(canvas).offset().top;
var prevMouseX = prevMouseY = 0;
var mouseX = mouseY = 0;
var isMouseDown = false;
var currentTool = 'draw';
var currentColor = 'black';
var thicknessSlider = document.getElementById('thickness_slider');

var loginDiv = document.getElementById("login_div");
var gameDiv = document.getElementById("game_div");
var loginStatus = document.getElementById('login_status');
var loading = false;
var playerName;

var wsUri = "ws://127.0.0.1:8887";
var webSocket;
var canvasData;
var players;
var playersDisplay = document.getElementById("player_list");
var drawerDisplay = document.getElementById("drawer_display");
var drawerName = "-";

var timeDisplay = document.getElementById("time_display");
var timeLeft;
var activeTimerInterval;
var wordDisplay = document.getElementById("word_display");

var canvasImg = new Image;

var chatBufferCheckCount = 0;
var chatBufferCheckFreqency = 10;
var maxChatBufferLines = 100;

var maxChatCharacterNum = 50;

$(canvas).on('mousedown', function(e) {
    prevMouseX = mouseX = parseInt(e.pageX - canvasX);
    prevMouseY = mouseY = parseInt(e.pageY - canvasY);
    isMouseDown = true;
    if (currentTool == 'fill' && (drawerName == playerName)) {
        fillCanvas(mouseX, mouseY);
    }
});

$(canvas).on('mouseup', function(e) {
    isMouseDown = false;
});

$(canvas).on('mousemove', function(e) {
    mouseX = parseInt(e.pageX - canvasX);
    mouseY = parseInt(e.pageY - canvasY);
    if (isMouseDown && (drawerName == playerName)) {
        if (currentTool == 'draw') {
            ctx.strokeStyle = currentColor;
        } else if (currentTool == 'erase') {
            ctx.strokeStyle = 'white';
        } else if (currentTool == 'fill') {
            return;
        }
        ctx.beginPath();
        ctx.lineWidth = 7 * thicknessSlider.value - 11;
        ctx.moveTo(prevMouseX,prevMouseY);
        ctx.lineTo(mouseX,mouseY);
        ctx.lineJoin = ctx.lineCap = 'round';
        ctx.stroke();
    }
    prevMouseX = mouseX;
    prevMouseY = mouseY;
});

$(canvas).on('mouseleave', function(e) {
    isMouseDown = false;
});

function fillCanvas(startX, startY) {
    var fillColorRGB = colorNameToRGB(currentColor);
    var backColorData = ctx.getImageData(startX, startY, 1, 1).data;
    var backColorRGB = [backColorData[0], backColorData[1], backColorData[2]];
    var canvasArrayData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    var canvasArray = canvasArrayData.data;
    if (!equalColors(fillColorRGB, backColorRGB)) {
        fillCanvasArray(startX, startY, fillColorRGB, backColorRGB, canvasArray);
        ctx.putImageData(canvasArrayData, 0, 0);
    }
}

class CanvasPixel {
    constructor(x, y) {
        this.x = x;
        this.y = y;
    }
}

function fillCanvasArray(startX, startY, fillColorRGB, backColorRGB, canvasArray) {
    var stack = new Array();
    stack.push(new CanvasPixel(startX, startY));
    while (stack.length != 0) {
        var topPixel = stack.pop();
        var canvasX = topPixel.x;
        var canvasY = topPixel.y;
        if (0 < canvasX && canvasX < canvas.width &&
            0 < canvasY && canvasY < canvas.height) {
            var index = getCanvasArrayIndex(canvasX, canvasY);
            var pixelR = canvasArray[index++];
            var pixelG = canvasArray[index++];
            var pixelB = canvasArray[index++];
            if (equalColors([pixelR, pixelG, pixelB], backColorRGB)) {
                var index = getCanvasArrayIndex(canvasX, canvasY);
                canvasArray[index++] = fillColorRGB[0];
                canvasArray[index++] = fillColorRGB[1];
                canvasArray[index++] = fillColorRGB[2];
                stack.push(new CanvasPixel(canvasX + 1, canvasY));
                stack.push(new CanvasPixel(canvasX - 1, canvasY));
                stack.push(new CanvasPixel(canvasX, canvasY + 1));
                stack.push(new CanvasPixel(canvasX, canvasY - 1));
            }
        }
    }
}

function getCanvasArrayIndex(x, y) {
    return (y * canvas.width + x) * 4;
}

function colorNameToRGB(colorName) {
    var oldColorData = ctx.getImageData(0, 0, 1, 1).data;
    var oldColor = "rgb(" + oldColorData[0] + ", " + oldColorData[1] + ", " + oldColorData[2] + ")";
    ctx.fillStyle = colorName;
    ctx.fillRect(0, 0, 1, 1);
    var result = ctx.getImageData(0, 0, 1, 1).data;
    ctx.fillStyle = oldColor;
    ctx.fillRect(0, 0, 1, 1);
    return result;
}

function equalColors(color1, color2) {
    return color1[0] == color2[0] &&
           color1[1] == color2[1] &&
           color1[2] == color2[2];
}

function use_tool(tool) {
    currentTool = tool;
}

function select_color(color) {
    currentColor = color;
}

function forceClearCanvas() {
    ctx.fillStyle = "#FFFFFF";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
}

function clear_canvas() {
    if (drawerName == playerName) {
        forceClearCanvas();
    }
}

function makeGuess(evt) {
    if(evt.keyCode == 13) {
        guessMessage = document.getElementById('chat_text').value;
        if (guessMessage.length > maxChatCharacterNum) {
            document.getElementById('chat_text').value = "";
            addToChatFormatted("Your message is too long.", "red");
        } else if (0 < guessMessage.length) {
            document.getElementById('chat_text').value = "";
            var messageObject = {"type":"guess", "name":playerName, "word":guessMessage};
            var messageString = JSON.stringify(messageObject);
            webSocket.send(messageString);
        }
    }
}

function addToChat(addedLine) {
    checkBufferLength();
    var prev = document.getElementById('chat_content').innerHTML;
    document.getElementById('chat_content').innerHTML = prev + addedLine + "<br>";
    var chatScroller = document.getElementById('chat_content');
    chatScroller.scrollTop = chatScroller.scrollHeight;
}

function addToChatFormatted(announcement, textColor, isBold) {
    var addedStr = '<font color="' + textColor + '">' + announcement + '</font>'
    if (isBold) {
        addedStr = '<b>' + addedStr + '</b>'
    }
    addToChat(addedStr);
}

function checkBufferLength() {
    if (++chatBufferCheckCount == chatBufferCheckFreqency) {
        chatBufferCheckCount = 0;
        var bufferContent = document.getElementById('chat_content').innerHTML;
        var bufferLines = bufferContent.split("<br>");
        var shortContent = "";
        if (bufferLines.length > maxChatBufferLines) {
            for (var i = bufferLines.length - 1; i >= bufferLines.length - maxChatBufferLines; i--) {
                shortContent = "<br>" + bufferLines[i] + shortContent;
            }
            document.getElementById('chat_content').innerHTML = shortContent;
        }
    }
}

function loginFromText(evt) {
    if(evt.keyCode == 13) {
        login();
    }
}

class Player {
    constructor(name, score) {
        this.name = name;
        this.score = score;
        this.guessed = false;
    }
}

function login() {
    if (!loading) {
        aboutOpen = false;
        checkAbout();
        var addressText = document.getElementById('address_text');
        wsUri = "ws://" + addressText.value;
        var loginText = document.getElementById('login_text');
        loginText.value = clearInvalids(loginText.value);
        if (loginText.value.length < 1) {
            writeStatus("You should enter a name to play.");
        } else if (loginText.value.length > 20) {
            writeStatus("The name can not be more than 20 characters.");
        } else {
            loading = true;
            loginText.disabled = true;
            addressText.disabled = true;
            document.getElementById('login_button').disabled = true;
            playerName = loginText.value;
            writeStatus("Connecting...");
            
            try {
                webSocket = new WebSocket(wsUri);
                webSocket.onopen = function(evt) { onOpen(evt) };
                webSocket.onclose = function(evt) { onClose(evt) };
                webSocket.onmessage = function(evt) { onMessage(evt) };
                webSocket.onerror = function(evt) { onError(evt) };
            } catch (exception) {
                console.log("Exception caught while connecting: " + exception);
                var evt = new Object();
                evt.code = 4002;
                onClose(evt);
            }
        }
    }
}

function onOpen(evt) {
    writeStatus("Connected.");
    loginDiv.style.display = "none";
    gameDiv.style.visibility = "visible";
    forceClearCanvas();
    players = [];
    
    var messageObject = {"type":"enter", "name":playerName};
    var messageString = JSON.stringify(messageObject);
    webSocket.send(messageString);
}

function onClose(evt) {
    loginDiv.style.display = "flex";
    gameDiv.style.visibility = "hidden";
    if (evt.code == 4001) {
        writeStatus("This name is taken by another player.");
    } else if (evt.code == 4002) {
        writeStatus("Please enter a valid server address.");
    } else if (evt.code == 4003) {
        writeStatus("This server is full.");
    } else {
        writeStatus("Disconnected. (Error Code: " + evt.code + ")");
    }
    document.getElementById('address_text').disabled = false;
    document.getElementById('login_text').disabled = false;
    document.getElementById('login_button').disabled = false;
    loading = false;
    drawerName = "-";
}

function onMessage(evt) {
    try {
        var jsonObject = JSON.parse(evt.data);
        var messageType = jsonObject.type;
        
        if (messageType == "players") {
            var updatedPlayers = jsonObject.players;
            players = [];
            for (var i = 0; i < updatedPlayers.length; i++) {
                var currentPlayer = JSON.parse(updatedPlayers[i]);
                players.push(currentPlayer);
            }
            updateScoreboard();
        }
        
        else if (messageType == "entrance") {
            enteredName = jsonObject.name;
            addToChatFormatted(enteredName + " has joined the room.", "yellow");
        }
        
        else if (messageType == "leave") {
            leftName = jsonObject.name;
            addToChatFormatted(leftName + " has left the room.", "red");
        }
        
        else if (messageType == "drawer") {
            setDrawer(jsonObject.name);
        }
        
        else if (messageType == "guessed") {
            guessedName = jsonObject.name;
            guesserScore = jsonObject.score;
            addToChatFormatted(guessedName + " has guessed the word!", "green");
            var playerIndex = findPlayerIndex(guessedName);
            players[playerIndex].guessed = true;
            players[playerIndex].score = guesserScore;
            updateScoreboard();
        }
        
        else if (messageType == "guessedword") {
            wordDisplay.innerHTML = '<font color="green">' + jsonObject.word + '</font>';
        }
        
        else if (messageType == "chat") {
            senderName = jsonObject.name;
            var chatContent = jsonObject.content;
            addToChat("<b>" + senderName + "</b>: " + chatContent);
        }
        
        else if (messageType == "endround") {
            var guesserNum = jsonObject.guessers;
            var guessedWord = jsonObject.word;
            
            if (players.length - 1 > guesserNum) {
                if (guesserNum == 0) {
                    addToChatFormatted("No one was able to guess the word.", "black");
                } else {
                    var pluralPart = guesserNum > 1 ? "s" : "";
                    addToChatFormatted(guesserNum + " player" + pluralPart + " has guessed the word.", "black");
                }
                addToChatFormatted('The word was "' + guessedWord + '".', "black");
            } else {
                addToChatFormatted("Everyone has guessed the word.", "black");
            }
        }
        
        else if (messageType == "startround") {
            forceClearCanvas();
            setDrawer(jsonObject.name);
            var roundTime = jsonObject.time;
            letterCount = jsonObject.count;
            resetGuesses();
            startTimer(roundTime);
            addToChatFormatted(drawerName + " is now drawing.", "yellow");
            setLetterCount(letterCount);
        }
        
        else if (messageType == "drawerselect") {
            wordDisplay.innerHTML = jsonObject.word;
            addToChatFormatted('Your word is "' + jsonObject.word + '".', "yellow");
        }
        
        else if (messageType == "canvasupdate") {
            if (drawerName != playerName) {
                ctx.drawImage(canvasImg, 0, 0);
                canvasImg.src = jsonObject.data;
            }
        }
        
        else if (messageType == "timeupdate") {
            startTimer(jsonObject.time);
        }
        
        else if (messageType == "letterupdate") {
            letterCount = jsonObject.count;
            if (drawerName != playerName) {
                setLetterCount(letterCount);
            }
        }
        
        else {
            console.log("Unknown message type: " + messageType);
        }
        
    } catch(exception) {
        console.log("Exception caught on WebSocket: " + exception);
    }
}

function onError(evt) {
    onClose(evt);
}

function writeStatus(message) {
    loginStatus.innerHTML = message;
}

function updateScoreboard() {
    players.sort(function(a, b) {
        var x = a.score;
        var y = b.score;
        return -((x < y) ? -1 : ((x > y) ? 1 : 0));
    });
    var updatedScoreBoard = "";
    for (var i = 0; i < players.length; i++) {
        if (players[i].name == drawerName) {
            infoString = " (DRAWING)";
        } else if (players[i].guessed) {
            infoString = " (GUESSED)";
        } else {
            infoString = "";
        }
        updatedScoreBoard += players[i].score + " | " + players[i].name + infoString + "<br>";
    }
    playersDisplay.innerHTML = updatedScoreBoard;
}

function updateDrawerDisplay() {
    drawerDisplay.innerHTML = drawerName;
}

function findPlayerIndex(name) {
    for (var i = 0; i < players.length; i++) {
        if (players[i].name == name) {
            return i;
        }
    }
    return -1;
}

function resetGuesses() {
    for (var i = 0; i < players.length; i++) {
        players[i].guessed = false;
    }
}

function startTimer(duration) {
    var minutes, seconds;
    timeLeft = duration - 1;
    clearInterval(activeTimerInterval);
    activeTimerInterval = setInterval(
        function() {
            if (--timeLeft < 0) {
                timeLeft = 0;
            }
            
            minutes = parseInt(timeLeft / 60, 10)
            seconds = parseInt(timeLeft % 60, 10);
            
            minutes = minutes < 10 ? "0" + minutes : minutes;
            seconds = seconds < 10 ? "0" + seconds : seconds;
            
            timeDisplay.innerHTML = minutes + ":" + seconds;
        }, 1000);
}

function setDrawer(updatedDrawer) {
    drawerName = updatedDrawer;
    if (drawerName == playerName) {
        canvas.style.cursor = "crosshair";
    } else {
        canvas.style.cursor = "default";
    }
    updateDrawerDisplay();
}

function startUpdateTimer(updatePeriod) {
    setInterval(function() {
        if (drawerName == playerName) {
            var messageObject = {"type":"draw", "data":canvas.toDataURL()};
            var messageString = JSON.stringify(messageObject);
            webSocket.send(messageString);
        }
    }, updatePeriod);
}

function setLetterCount(letterCounts) {
    var underscoreDisplay = "";
    for (var w = 0; w < letterCounts.length; w++) {
        for (var i = 0; i < letterCounts[w]; i++) {
            underscoreDisplay += "_ ";
        }
        underscoreDisplay += "  ";
    }
    wordDisplay.innerHTML = "<xmp>" + underscoreDisplay + "</xmp>";
}


var VALID_CHARS = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
    'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
    'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0',
    '1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '-', '_', '.',
    '\u{00F6}', '\u{00E7}', '\u{015F}', '\u{0131}', '\u{011F}', '\u{00FC}',
    '\u{00D6}', '\u{00C7}', '\u{015E}', '\u{0130}', '\u{011E}', '\u{00DC}']

function clearInvalids(inputStr) {
    var validStr = "";
    for (var i = 0; i < inputStr.length; i++) {
        if (VALID_CHARS.includes(inputStr.charAt(i))) {
            validStr += inputStr.charAt(i);
        }
    }
    return validStr;
}

var aboutOpen = false;
document.getElementById("about_link").onclick = function() {
    aboutOpen = !aboutOpen;
    checkAbout();
}

function checkAbout() {
    var aboutInfo = '<br><br><i>Drawingly</i> is an open source drawing game project. The aim of the project is creating a portable and customizable drawing game.<br><br>You can setup your own server with your own word list by following the tutorial on <a target="_blank" href="ServerTutorial.html">this page</a>.<br><br>You can also check out the source code and contribute on the <a target="_blank" href="https://github.com/eminbahadir98/Drawingly">GitHub repository</a>.';
    if (aboutOpen) {
        document.getElementById("about_div").innerHTML = aboutInfo;
    } else {
        document.getElementById("about_div").innerHTML = "";
    }
}

startUpdateTimer(256);
$(document.getElementById("login_text")).keydown(loginFromText);
$(document.getElementById("chat_text")).keydown(makeGuess);
