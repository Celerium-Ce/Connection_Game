# Connection Game 

## Idea
A deduction and word-association game where one player tries to hide a word while rest collaborate, give hint, and form connections to reveal next character.

1. Number of players: N ≥ 3. One player is the clue-giver (A). The remaining N−1 players form a team (B).
2. A chooses a hidden word W and submits it to the game server. The server stores W privately for A.
3. A reveals the first letter of W. The known prefix is X (initially the first letter).
4. Team B has 5 lives for each prefix X. If X extends by 1 letter, lives reset to 5.
5. Gameplay loop:
    - Any B member (b1) has 60s to give a descriptive hint (phrase/word) that points to a target word Z which starts with the prefix X.
    - Another B member (b2) can press Connection if they think b1's hint points to Z. This triggers a 10s secret-guess window for b2 and A.
    - During the 10s window both b2 and A submit guesses privately. If A correctly identifies Z, B loses one life for the current X.
    - If b2 correctly guessed Z and A did not, connection succeeds and A reveals the next letter of W (X grows by 1). Lives reset to 5.
    - If connection fails and either b1 or b2 happened to guess W exactly then B immediately wins.
    - If B loses all 5 lives on a prefix X (A guessed 5 hints correctly), A wins the round.
6. End conditions:
    - B wins if full W is revealed or someone explicitly guesses W.
    - A wins if B runs out of 5 lives on the same prefix X.


## Design

Project Structure
```
src/
├─ client/
│  ├─ client.fxml
│  ├─ ClientController.java
│  ├─ GuiClient.java
│  └─ ParsedMessage.java
├─ server/
│  ├─ ClientHandler.java
│  ├─ GameServer.java
│  ├─ GameState.java
│  ├─ Message.java
│  └─ TimerManager.java
└─ README.md
```

## Implementation

- `server/`

    - `GameServer.java` : This is the primary game server component. Responsibilities:
        - Establishes an ServerSocket which accept client connections.
        - Spawns a ClientHandler thread for each connected client.
        - Manages authoritative GameState and broadcasts updates to all connected clients.
    - `ClientHandler.java` : Each client has its own worker thread called ClientHandler. Responsibilities:
        - Reads raw messages sent by client through socket and converting into Message objects.
        - Handles client actions - submit-word, hint, press-connection, secret-guess, guess-W and applies server-side validation.
        - Sending state updates, timer events and reveal messages back to the client.
    - `TimerManager.java` : Provide centralized timer support for the server to support game timing rules. Responsibilities:
        - 60s hint window - time after a hint has been given that it will disappear.
        - 10s secret-guess window - time a player can guesses on the secret word after they have pressed the "connect" button.
        - Per-prefix life tracking and scheduling tasks to trigger life loss and reveal next letter.
    - `Message.java` : Represents protocol object sent from the server to the client when reading raw data from the socket. Typical fields:
        - `type` - HINT, CONNECTION, SECRET_GUESS, SUBMIT_WORD, UPDATE_STATE
        - `from` - player id or name
        - `payload` - map or JSON string
    - `GameState.java` : Represents authoritative state of game including :
        - the current prefix X
        - the hidden word's length
        - revealed letters
        - current lives left
        - player list and roles

        Server methods includes:
        - revealNextLetter()
        - decrementLife()
        - resetLives()
        - checkWin()

- `client/`
    - `GuiClient.java` : Represents UI implementation of Client-side. Responsibilities:
        - Render game and its components: current prefix X, lives left, hint input, Connectionbutton, chat/log and countdown timers.
        - Handle UI events and call ClientController for network operations.
    - `ClientController.java` : Represents networking layer for the client:
        - Maintain a persistent Socket to the server and send/receive Message JSONs.
        - Expose methods - sendHint(), pressConnection(), submitSecretGuess(), and submitWor() for the UI to call.
    - `ParsedMessage.java` : Client-side helper that converts raw `Message` payloads intoricher objects for UI with helpers - `isUpdate()`, `getRemainingTime()`.

## Networking
The project is based on a classic client-server architecture uses TCP sockets. High-level networking notes:

- *Transport*: Java ServerSocket on the server and Socket on the client. Each client connection is being handled concurrently by a ClientHandler thread.
- *Protocol*: Message envelope consist of following parameters - type, from and a payload.
- *Authority*: Server holds and maintains GameState. Clients only send action requests and server validates and broadcast changes in state.
- *Timers*: Timers run on server-side via TimerManager for preventing any client tampering. The server sends countdown updates for UI display.
- *Secret guesses*: Submitted to the server privately and hold them till the 10 sec window ends and then evaluate the result.

## Demo & Repository
- Demo video: Watch demo video : 
- GitHub repo: Open GitHub repository : [Repository Link](https://github.com/Celerium-Ce/CN_Project_G26)
- Web Page: Visit project page : [Project Web Page](https://github.com/MayankK-20/connection-game)

## Team
- Mayank Kumar (2023317)
- Md. Umar (2023324)
- Pratyush Gangwar (2023395)
- Saanvi Singh (2023454)
- Ujjval Dargar (2023564)
