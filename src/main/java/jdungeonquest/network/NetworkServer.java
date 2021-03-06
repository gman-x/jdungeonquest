package jdungeonquest.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdungeonquest.Game;
import jdungeonquest.enums.NetworkMessageType;
import static jdungeonquest.enums.NetworkMessageType.BattleAction;
import static jdungeonquest.enums.NetworkMessageType.ChatMessage;
import static jdungeonquest.enums.NetworkMessageType.StartBattle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkServer implements Runnable {

    private Game game = new Game();
    private Server server = null;
    Logger logger = LoggerFactory.getLogger(NetworkServer.class);
    private int serverPort = 3334;
    public static final int DEFAULT_PORT = 4446;

    private Map<Integer, List<String>> clientPlayersMap = new HashMap<>();
    
    public NetworkServer() {
        this(DEFAULT_PORT);
        Log.set(Log.LEVEL_DEBUG);
    }

    public NetworkServer(int port) {
        server = new Server();
        Network.registerClasses(server);
        this.serverPort = port;
    }

    @Override
    public void run() {
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                logger.debug("Client '" + connection + "' " + connection.getID() + " connected");
                connection.setTimeout(0);
            }

            @Override
            public void disconnected(Connection connection) {
                logger.debug("Client '" + connection + "' " + connection.getID() + " disconnected");
                if(clientPlayersMap.containsKey(connection.getID())){
                    broadcast("Client " + connection.toString() + " has quit.");
                }
                if(!clientPlayersMap.containsKey(connection.getID())){
                    logger.debug("There is no client " + connection.getID() + " (anymore?)");
                    return;
                }
                for(String player : clientPlayersMap.get(connection.getID())){
                    //game.removePlayer(player);
                }
                broadcast(new PlayerList(game.getPlayerList()));
            }

            @Override
            public void received(Connection connection, Object object) {
                logger.debug(connection.getID() + " " + connection.toString() + " Recieved package: " + object);
                if (object instanceof String) {
                    String test = (String) object;
                    if (test.equals("he")) {
                        clientPlayersMap.put(connection.getID(), new ArrayList<String>());
                        connection.sendTCP(new String("lo"));
                        connection.sendTCP(new PlayerList(game.getPlayerList()));
                    }
                }else if (object instanceof Message) {
                    switch (((Message) object).msgType) {
                        //We got a client wanting to register a new player.
                        //Check if it can be done and send him result of it.
                        case RegistrationRequest:
                            RegistrationRequest r = ((RegistrationRequest) object);
                            registerPlayer(connection, r.playerName, r.playerClass);
                            break;

                        //We got a chat message from one of the players, so
                        //we need to broadcast it to everyone else for them to see.
                        case ChatMessage:
                            ChatMessage msg = (ChatMessage)object;
                            logger.debug(msg.author + ":" + msg.message);
                            server.sendToAllExceptTCP(connection.getID(), msg);
                            break;
                            
                        //We recieve this only when client has disconnected
                        //here we need to correct the entry in the clientPlayerMap
                        //according to the players that were registered on that client
                        case PlayerList:
                            //confirm connection with the server if client is valid
                            //connection.sendTCP("lo");
                            break;
                            
                        //We got a client wanting to remove a local player.
                        //Check if it can be done by him and send result of it.
                        case UnregisterRequest:
                            UnregisterRequest u = ((UnregisterRequest) object);
                            unregisterPlayer(connection, u.playerName);
                            break;
                            
                        //One of the clients is ready to start the game
                        case ClientReady:
                            toggleClientReadyStatus(connection.getID());
                            break;
                            
                        case MovePlayer:
                            MovePlayer movePlayer = (MovePlayer) object;
                            if(!havePlayer(connection.getID(), movePlayer.getPlayer())){
                                break;
                            }
                            game.processPlayerMove(movePlayer, movePlayer.getPlayer());
                            processMessageQueue();
                            break;
                        
                        case NewTurn:
                            NewTurn newTurn = (NewTurn) object;
                            if(!havePlayer(connection.getID(), newTurn.player)){
                                break;
                            }                            
                            game.endTurn(newTurn.player);
                            processMessageQueue();
                            break;
                            
                        case GuessNumber:
                            game.processGuessNumber((GuessNumber)object);
                            processMessageQueue();
                            break;

                        case StartBattle:
                            game.processStartBattle((StartBattle)object);
                            processMessageQueue();
                            break;                            

                        case BattleAction:
                            game.processBattleAction((BattleAction)object);
                            processMessageQueue();
                            break;
                            
                        case SearchRoom:
                            game.processPlayerSearchRoom();
                            processMessageQueue();
                            break;                            

                        case YesNoQuery:
                            game.processYesNoAnswer((YesNoQuery)object);
                            processMessageQueue();
                            break;
                            
                        default:
                            logger.debug("Unhandled message found: " + object);
                            break;
                    }
                } else if (object instanceof com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive){
                } else {
                    logger.info("Recieved unkown package: " + object);
                }
//            processMessageQueue();
            }


        });
        try {
            server.bind(this.serverPort);
        } catch (IOException ex) {
            logger.error("Exception" + ex);
        }
        server.start();
        logger.debug("Server started on port " + this.serverPort);
    }

    private boolean havePlayer(int id, String player) {
        if(!clientPlayersMap.containsKey(id)){
            logger.debug("There is no client " + id + " (anymore?)");
            return false;
        }
        for(String clientPlayer : clientPlayersMap.get(id)){
            if(player.equals(clientPlayer)){
                return true;
            }
        }
        logger.debug("Client " + id + " doesn't have player " + player);
        return false;
    }
    
    private void toggleClientReadyStatus(int id) {
        for(String playerName : clientPlayersMap.get(id)){
            logger.debug("Changing ready status of " + playerName + " from Client " + id);
            game.toggleReadyPlayer(playerName);
        }
        if( game.isEveryoneReady() ){
            logger.debug("Everyone is ready. Starting game.");
            server.sendToAllTCP(new Message(NetworkMessageType.StartGame));
            game.startGame();
        }
        processMessageQueue();
    }

    private void registerPlayer(Connection conn, String playerName, String playerClass) {
        boolean result = game.registerPlayer(playerName, playerClass);
        if (result) {
            conn.sendTCP(new RegistrationRequest(playerName, playerClass));
            attachPlayerToClient(conn.getID(), playerName);
            broadcast("Player " + playerName + " joined.");
            broadcast(new PlayerList(game.getPlayerList()));
        } else {
            conn.sendTCP(new RegistrationRequest("", ""));
        }
        processMessageQueue();
        logger.debug("Registering player " + playerName + " - " + result);
    }

    private void unregisterPlayer(Connection conn, String playerName) {
        boolean result = game.unregisterPlayer(playerName);
        if (result) {
            conn.sendTCP(new UnregisterRequest(playerName));
            detachPlayerFromClient(conn.getID(), playerName);
            broadcast("Player " + playerName + " has left.");
            broadcast(new PlayerList(game.getPlayerList()));
        } else {
            conn.sendTCP(new UnregisterRequest(""));
        }
        logger.debug("Unregistering player " + playerName + " " + result);
    } 
    
    private void attachPlayerToClient(int id, String name) {
        if(clientPlayersMap.containsKey(id)){
            List<String> players = clientPlayersMap.get(id);
            players.add(name);
        }else{
            clientPlayersMap.put(id, new ArrayList<>(Arrays.asList(new String[]{name})));
        }
        logger.debug("Added " + name);
        logger.debug("List of players for Client " + id + " : " + clientPlayersMap.get(id));
    }

    private void detachPlayerFromClient(int id, String name) {
        if (clientPlayersMap.containsKey(id)) {
            List<String> players = clientPlayersMap.get(id);
            players.remove(name);
            clientPlayersMap.put(id, players);
            logger.debug("Removed " + name);
        }
        logger.debug("List of players for Client " + id + " : " + clientPlayersMap.get(id));
    }
    
    public void stop() {
        server.stop();
        logger.debug("Server stopped");
    }

    public Game getGame() {
        return game;
    }

    private void broadcast(String text) {
        logger.debug("Broadcasting text:" + text);
        server.sendToAllTCP(new ChatMessage(text, "Server"));
    }

    private void broadcast(PlayerList playerList) {
        logger.debug("Broadcasting playerList:" + playerList);
        server.sendToAllTCP(playerList);
    }    

    private void processMessageQueue() {
        if(game.messageQueue.isEmpty()){
            return;
        }
        for(Message m : game.messageQueue){
            if(m instanceof GuessNumber || m instanceof StartBattle || m instanceof EndBattle || m instanceof BattleAction || m instanceof YesNoQuery){
                String curPlayer = game.getCurrentPlayer();
                int id = -1;
                for(int key : clientPlayersMap.keySet()){
                    if(havePlayer(key, curPlayer)){
                        id = key;
                        break;
                    }
                }
                if(id == -1){
                    logger.debug("Unable to find client with player " + curPlayer);
                    return;
                }
                logger.debug("Sending message " + m + " to " + id);
                server.sendToTCP(id, m);
            }else{
                logger.debug("Sending all message " + m);
                server.sendToAllTCP(m);
            }
        }
        game.messageQueue.clear();
    }
}
