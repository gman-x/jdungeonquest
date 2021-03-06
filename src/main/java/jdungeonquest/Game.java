package jdungeonquest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import jdungeonquest.effects.BottomlessPit;
import jdungeonquest.effects.CorridorTile;
import jdungeonquest.effects.Effect;
import jdungeonquest.enums.DeckType;
import jdungeonquest.enums.EntryDirection;
import jdungeonquest.enums.GameState;
import jdungeonquest.enums.MonsterType;
import jdungeonquest.enums.PlayerAttributes;
import jdungeonquest.enums.PlayerState;
import jdungeonquest.enums.PlayerStatus;
import jdungeonquest.enums.RoomWallType;
import jdungeonquest.network.BattleAction;
import jdungeonquest.network.ChangePlayerAttribute;
import jdungeonquest.network.ChatMessage;
import jdungeonquest.network.EndBattle;
import jdungeonquest.network.EndGame;
import jdungeonquest.network.GuessNumber;
import jdungeonquest.network.KillPlayer;
import jdungeonquest.network.Message;
import jdungeonquest.network.MovePlayer;
import jdungeonquest.network.NewTurn;
import jdungeonquest.network.PlaceTile;
import jdungeonquest.network.StartBattle;
import jdungeonquest.network.YesNoQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Game {

    List<Player> players = new ArrayList<>();
    Logger logger = LoggerFactory.getLogger(Game.class);
    Map<String, Boolean> playerClasses = new HashMap();
    Map<String, Boolean> playerReadyStatus = new HashMap();
    public Player currentPlayer;
    PlayerState currentPlayerState;
    int currentPlayerContextValue;
    GameMap map;
    private GameState state = GameState.NOT_STARTED;
    private Random random = new Random();

    TileHolder tileHolder;
    CardHolder cardHolder;
    
    private int turn = 1;
    private int sunPosition = 1;
    private final int sunMaxPosition = 26;
    private boolean battleStarted = false;
    private int monsterHP = 0;
    private boolean usingSecretDoor = false;
    private int doorsToOpen = 0;
    Set<Position> rotatedRooms = new HashSet<>();
    
    public List<Message> messageQueue;
    
    private final Position treasureChamberPositionLeft = new Position (4, 6);
    private final Position treasureChamberPositionRight = new Position (5, 6);
    
    private final Position startingUpLeftPosition = new Position (0, 0);
    private final Position startingUpRightPosition = new Position (0, GameMap.MAX_Y-1);
    private final Position startingDownLeftPosition = new Position (GameMap.MAX_X-1, 0);
    private final Position startingDownRightPosition = new Position (GameMap.MAX_X-1, GameMap.MAX_Y-1);

    public Game() {
        playerClasses.put("A", false);
        playerClasses.put("B", false);
        playerClasses.put("C", false);
        playerClasses.put("D", false);
        
        tileHolder = new TileHolder();
        cardHolder = new CardHolder();
        
        messageQueue = new ArrayList<>();
        map = new GameMap();
    }

    public void changePlayerAttribute(Player player, PlayerAttributes attribute, int amount) {
        switch(attribute){
            case Gold:
                player.setGold(amount);
                break;
            case HP:
                player.setHp(amount);
                break;
            default:
                break;
        }
        String playerName = player.getName();
        logger.debug("Changing " + playerName + " " + attribute + " to " + amount);
        addMessage(new ChangePlayerAttribute(playerName, attribute, amount));
    }

    public int GetPlayerAttribute(Player currentPlayer, PlayerAttributes playerAttributes) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    // SUBJECT TO CHANGE
    // <code>getCurrentPlayer</code> --- возвращает, нарпимер, текстовое имя игрока или некий внутренний id
    public String getCurrentPlayer() {
        return currentPlayer.getName();
    }

    public boolean toggleReadyPlayer(String playerName) {
        boolean value = !(playerReadyStatus.get(playerName));
        playerReadyStatus.put(playerName, value);
        addMessage(new ChatMessage(playerName + (value?" is ":" is not ") + "ready to start.", "Game"));
        logger.debug("Player " + playerName + " ready status:" + value);
//        if ( isEveryoneReady() ) {
//            return true;
//        }
//        return false;
        return true;
    }

    public boolean registerPlayer(String playerName, String playerClass) {
        if(state != GameState.NOT_STARTED){
            return false;
        }
        if(playerName.equals("")){
            return false;
        }
        if(isPlayerRegistered(playerName)){
            return false;
        }
//        if(playerClasses.get(playerClass)){
//            return false;
//        }
        if(players.size() == 4){
            addMessage(new ChatMessage("Maximum of 4 players reached.", "Game"));
            return false;
        }
        Player newPlayer = new Player();
        newPlayer.setName(playerName);
        newPlayer.setClassname(playerClass);
        
        players.add(newPlayer);
        playerClasses.put(playerClass, true);

        playerReadyStatus.put(playerName, false);

        logger.debug("Game registered player " + playerName + " with class " + playerClass);
        return true;
    }
    
    public boolean unregisterPlayer(String playerName) {
        if(playerName.equals("")){
            return false;
        }
        if(!isPlayerRegistered(playerName)){
            return false;
        }
        Iterator<Player> iter = players.iterator();
        while(iter.hasNext()){
            Player p = iter.next();
            if(p.getName().equals(playerName)){
                iter.remove();                
                playerReadyStatus.remove(playerName);
            }
        }
        logger.debug("Game unregistered player " + playerName);
        return true;
    }

    public boolean isPlayerRegistered(String playerName) {
        for (Player p : players) {
            if (playerName.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    public String[] getPlayerList() {
        String[] playerList = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            playerList[i] = players.get(i).getName();
        }
        return playerList;
    }

    public boolean isEveryoneReady() {
        assert (playerReadyStatus.size() == players.size());
        
        if(playerReadyStatus.isEmpty()){
            return false;
        }
        for (Boolean status : playerReadyStatus.values()) {
            if (!status) {
                return false;
            }
        }
        return true;
    }

    public void startGame() {
        state = GameState.IN_PROGRESS;
        setUp();
        turn = 0;
        addMessage(new ChatMessage("Turn: " + turn, "Game"));
        currentPlayer = players.get(0);
        currentPlayer.resetTurnVariables();
        logger.debug("Current player: " + currentPlayer.getName());
        
        for(Player player : players){
            changePlayerAttribute(player, PlayerAttributes.HP, 15);
            changePlayerAttribute(player, PlayerAttributes.Gold, 0);
        }
        
        addMessage(new ChatMessage("Current sun position: " + sunPosition + "/" + sunMaxPosition, "Game"));
        addMessage(new ChatMessage("Current player: " + currentPlayer.getName(), "Game"));
        addMessage(new NewTurn(currentPlayer.getName()));
    }

    private void setUp() {
        placeTile(treasureChamberPositionLeft, tileHolder.dragonTileLeft, 0);
        placeTile(treasureChamberPositionRight, tileHolder.dragonTileRight, 0);
        
        switch (players.size()) {
            case 4:
                movePlayer(startingDownRightPosition, players.get(3));
            case 3:
                movePlayer(startingDownLeftPosition, players.get(2));
            case 2:
                movePlayer(startingUpRightPosition, players.get(1));
            case 1:
                movePlayer(startingUpLeftPosition, players.get(0));
                placeTile(startingDownRightPosition, tileHolder.startingTile, 0);
                placeTile(startingDownLeftPosition, tileHolder.startingTile, 0);
                placeTile(startingUpRightPosition, tileHolder.startingTile, 0);
                placeTile(startingUpLeftPosition, tileHolder.startingTile, 0);
                break;
            //only 4 players are supported right now
            default:
            case 0:
                endGame();
                break;
        }
    }

    private void placeTile(Position p, Tile t, int r) {
        placeTile(p.getX(), p.getY(), t, r);
    }
    
    private void placeTile(int x, int y, Tile tile, int rotation) {
        int tileNumber = tileHolder.getTileNumber(tile);
        tile.rotate(rotation);
        logger.debug("Placing tile " + tileNumber + " " + tile + " at " + x + ":" + y);
        map.setTile(x, y, tile);
        addMessage( new PlaceTile(x, y, tileNumber, tile.getRotate()));
    }

    private void movePlayer(Position p, Player pl) {
        movePlayer(p.getX(), p.getY(), pl);
    }    
    
    private void movePlayer(int x, int y, Player player) {
        logger.debug("Moving " + player.getName() + " from " + player.getPosition() + " to " + new Position(x,y));
        player.setPreviousPosition(player.getPosition());
        player.setPosition( new Position(x,y));
        addMessage( new MovePlayer(x, y, player.getName()));
    }

    public void addMessage(Message m) {
        logger.debug("Adding message to queue: " + m);
        messageQueue.add(m);
    }

    private void endGame() {
        //game could've ended by something else before
        if(state == GameState.ENDED){
            return;
        }
        
        state = GameState.ENDED;
        logger.debug("EVERYONE DIED. BAD END.");
        addMessage(new ChatMessage("Game ended!", "Game"));
        addMessage(new EndGame());
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("<html>");
        sb.append("<h2>Top Players:</h2>");
        //we mess with the actual Players list because it shouldn't be used now anyway
        Collections.sort(players, new Comparator<Player>() {

            @Override
            public int compare(Player a, Player b) {
                //being rich is useless when you're dead and can't use any of it
                //because of this living players are being ranked higher
                if(!a.isDead() && b.isDead()){
                    return -1;
                }
                if(a.isDead() && !b.isDead()){
                    return 1;
                }
                //if they are both dead or both alive
                if(a.getGold() > b.getGold()){
                    return -1;
                }
                if(a.getGold() < b.getGold()){
                    return 1;
                }
                //if their gold amount is the same
                if(a.getHp() > b.getHp()){
                    return -1;
                }
                if(a.getHp() < b.getHp()){
                    return 1;
                }
                return 0;
            }
        });
        sb.append("<ol>");
        for(Player p : players){
            sb.append("<li>");
            sb.append(p.getName());
            if(!p.isDead()){
                sb.append( ". He escaped from dungeon");
            }else{
                sb.append( ". He died");
            }
            sb.append( " with ");
            sb.append(p.getGold());
            sb.append( " gold.");
            sb.append("</li>");
        }        
        sb.append("</ol>");
        sb.append("</html>");
        
        addMessage(new ChatMessage( sb.toString(), "Game"));
    }

    private boolean correctChasmExit(Position prev, Position from, Position to) {
        //todo: refactor to remove duplicated code
        EntryDirection d1 = null;
        EntryDirection d2 = null;

        if( prev.getY() == from.getY()){
            if(prev.getX() > from.getX()){
                d1 = EntryDirection.RIGHT;
            }
            if(prev.getX() < from.getX()){
                d1 = EntryDirection.LEFT;
            }
        }
        if( prev.getX() == from.getX()){
            if(prev.getY() > from.getY()){
                d1 = EntryDirection.DOWN;
            }
            if(prev.getY() < from.getY()){
                d1 = EntryDirection.UP;
            }
        }
        
        if( from.getY() == to.getY()){
            if(from.getX() > to.getX()){
                d2 = EntryDirection.LEFT;
            }
            if(from.getX() < to.getX()){
                d2 = EntryDirection.RIGHT;
            }
        }
        if( from.getX() == to.getX()){
            if(from.getY() > to.getY()){
                d2 = EntryDirection.UP;
            }
            if(from.getY() < to.getY()){
                d2 = EntryDirection.DOWN;
            }
        }
        
        logger.debug(d1 + " " + d2);
        
        if ((d1 == d2)
                || (d1 == EntryDirection.UP && d2 == EntryDirection.LEFT)
                || (d2 == EntryDirection.UP && d1 == EntryDirection.LEFT)
                || (d1 == EntryDirection.DOWN && d2 == EntryDirection.RIGHT)
                || (d2 == EntryDirection.DOWN && d1 == EntryDirection.RIGHT)) {
            return true;
        }
        return false;
    }
    
    public void processPlayerMove(MovePlayer movePlayer, String playerName) {
        if(state != GameState.IN_PROGRESS){
            return;
        }
        
        Player player = null;
        for(Player p : players){
            if(playerName.equals(p.getName())){
                player = p;
                break;
            }
        }
        if(player == null){
            logger.debug("No player " + playerName + "found.");
            return;
        }

        Position from = player.getPosition();
        Position to = new Position(movePlayer.getX(), movePlayer.getY());
        logger.debug("Processing move of " + playerName + " from " + from + " to " + to);
        
        //check that it is this player's turn
        if(!currentPlayer.getName().equals(playerName)){
            logger.debug("Current player is:" + currentPlayer.getName() + " so " + playerName +" can't do anyting now.");
            return;
        }
        
        //check that he haven't moved this turn yet
        if(currentPlayer.isMoved()){
            logger.debug("Player already moved this turn. Can't move now.");
            addMessage(new ChatMessage("You already moved this turn. Can't move again.", "Game"));
            return;
        }
        
        //check that tile is adjacent
        if(!map.isAdjacent(from, to)){
            logger.debug("Not adjacent. Can't move.");
            addMessage(new ChatMessage("This tile is not adjacent. You cannot move there.", "Game"));
            return;
        }
        
        //Chamber of Darkness effect:
        //"If the exit you have rolled is impassible, you must miss your turn"
        if(currentPlayer.status == PlayerStatus.DARKNESS){
            addMessage(new ChatMessage("You blindly move into the darkness.", "Game"));
            List<Position> possiblePositions = new ArrayList<>();
            List<RoomWallType> walls = map.getTile(from).getWalls();
            if( walls.get(0) != RoomWallType.WALL ){ //UP
                possiblePositions.add( new Position(from.getX(),from.getY()-1));
            }
            if( walls.get(1) != RoomWallType.WALL ){ //LEFT
                possiblePositions.add( new Position(from.getX()-1,from.getY()));
            }
            if( walls.get(2) != RoomWallType.WALL ){ //DOWN
                possiblePositions.add( new Position(from.getX(),from.getY()+1));
            }
            if( walls.get(3) != RoomWallType.WALL ){ //RIGHT
                possiblePositions.add( new Position(from.getX()+1,from.getY()));
            }
            
            to = possiblePositions.get(random.nextInt(possiblePositions.size()));
            currentPlayer.setMoved(true);
        }
        
        //check that there is no one in that tile
        //add exception for Treasure Chamber, any number of players can fit there
        if( !to.equals(treasureChamberPositionLeft) && !to.equals(treasureChamberPositionRight)){
            for (Player p : players) {
                if (p.getPosition().equals(to)) {
                    logger.debug("There's " + p.getName() + " on that tile. Can't move.");
                    addMessage(new ChatMessage("There's " + p.getName() + " on that tile. Can't move there.", "Game"));
                    return;
                }
            }
        }
        
        //check that you can enter that tile from current one
        if(!map.canMoveFrom(from, to) && !usingSecretDoor){ //when using Secret Door card player can move anywhere
            addMessage(new ChatMessage("You cannot leave this way.", "Game"));
            logger.debug("Can't leave from " + map.getTile(from.getX(), from.getY()) + " on " + from + " this way. Can't move.");
            return;
        }
        
        if(currentPlayer.status == PlayerStatus.CHASM){
            if(correctChasmExit(currentPlayer.getPreviousPosition(), from, to)){
                currentPlayer.status = PlayerStatus.NONE;
            }else{
                addMessage(new ChatMessage("You can't reach another side of the room.", "Game"));
                return;
            }
        }
        
        //check whether there is something on that tile already
        if(map.isFree(to.getX(), to.getY())){
            //if there's nothing there yet, place a tile there
            logger.debug("Tile is empty.");

            Tile tile = tileHolder.takeTile();
            int tileRotation = map.getRequiredRotation(from, to, tile);
            placeTile(to, tile, tileRotation);
            currentPlayer.setPlacedTile(true);
        }else if(!map.canMoveTo(from, to) && !usingSecretDoor){ //when using Secret Door card player can move anywhere
            //moving in existing tile
            logger.debug("Can't enter " + map.getTile(to.getX(), to.getY()) + " on " + to + " this way. Can't move.");
            addMessage(new ChatMessage("There is no way to enter this way.", "Game"));
            return;
        }

        //when in Cave-In room, player is free to backtrack, but must pass
        //Agility test to move anywhere else
        //secret room overrides CaveIn rules
        if(currentPlayer.status == PlayerStatus.IN_CAVEIN){
            if(usingSecretDoor){
                currentPlayer.status = PlayerStatus.NONE;
            }else if(to.equals(currentPlayer.getPreviousPosition())){
                currentPlayer.status = PlayerStatus.NONE;
            }else{
                if(testPlayerAgility(12)){
                    addMessage(new ChatMessage("You find a way among the rubble.", "Game"));
                    currentPlayer.status = PlayerStatus.NONE;
                }else{
                    addMessage(new ChatMessage("You fail to move past the rubble.", "Game"));
                    currentPlayer.setMoved(true);
                    return;
                }
            }
        }

        //when in Bottomless Pit room, player is free to backtrack, but must pass
        //Agility test to move anywhere else
        //secret room is unavailable since you can't search in Bottomless Pit
        if(currentPlayer.status == PlayerStatus.BOTTOMLESS_PIT){
            if(to.equals(currentPlayer.getPreviousPosition())){
                currentPlayer.status = PlayerStatus.NONE;
            }else{
                if(testPlayerAgility(12)){
                    addMessage(new ChatMessage("You jump across the bottomless pit!", "Game"));
                    currentPlayer.status = PlayerStatus.NONE;
                }else{
                    addMessage(new ChatMessage("You fall down the bottomless pit!", "Game"));
                    killPlayer(currentPlayer, "Your screams echo in the dungeon for some time.");
                    return;
                }
            }
        }        
        
        //if there are doors on either starting and/or ending tile,
        //then all (0,1,2) doors should give Door Opens results otherwise
        //player can't move.
        if(usingSecretDoor){
            doorsToOpen = 0;
        }else{
            doorsToOpen = map.getNumberOfDoorsBetween(from, to);
            processDrawDoorCards();
        }
        if(doorsToOpen > 0 ){
            logger.debug("There are " + doorsToOpen + " closed doors in the way. Can't move.");
            currentPlayer.setMoved(true);
        }else{
            if(currentPlayer.status == PlayerStatus.DARKNESS){
                currentPlayer.status = PlayerStatus.NONE;
            }
            addMessage(new ChatMessage("You enter another room.", "Game"));            
            currentPlayer.setMoved(true);
            currentPlayer.searchInRow = 0;
            movePlayer(to, currentPlayer);
            usingSecretDoor = false;
        
            processCurrentPlayerTile();
        }
    }

    private void processCurrentPlayerTile() {
        //get position of current player 
        Position playerPosition = currentPlayer.getPosition();
        //get tile on this position
        Tile tile = map.getTile(playerPosition.getX(), playerPosition.getY());
        //get effects from this tile
        List<Effect> tileEffects = tile.getEffects();
        logger.debug("Found " + tileEffects.size() + " effects on Tile " + tile + " on " + playerPosition);

        if(tileEffects.size() > 0){
            for(Effect e : tileEffects){
                logger.debug("Activating effect: " + e);
                e.doAction(this);
            }
        }else{
            processDrawRoomCard();
        }
    }

    private void processDrawDragonCard() {
        Card card = cardHolder.dragonDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);
    }      
    
    private void processDrawTreasureCard() {
        Card card = cardHolder.treasureDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);
    }            
    
    public void processDrawTrapCard() {
        Card card = cardHolder.trapDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);
    }
    
    private void processDrawSearchCard() {
        Card card = cardHolder.searchDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);        
    }
    
    private void processDrawRoomCard() {
        Card card = cardHolder.roomDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);
    }
    
    private void processDrawCorpseCard(){
            Card card = cardHolder.corpseDeck.takeCard();
            logger.debug("Activating " + card + " card");
            card.activate(this);
    }
    
    private void processDrawCryptCard(){
            Card card = cardHolder.cryptDeck.takeCard();
            logger.debug("Activating " + card + " card");
            card.activate(this);
    }
    
    private void processDrawDoorCard(){
            Card card = cardHolder.doorDeck.takeCard();
            logger.debug("Activating " + card + " card");
            card.activate(this);
    }
    
    private void processDrawDoorCards(){
        //"If only one card says 'Door Opens', you must follow instructions on the other card"
        if(doorsToOpen == 2){
            Card card = cardHolder.doorDeck.takeCard();
            logger.debug("Activating " + card + " card");
            processDrawDoorCard();
            if(doorsToOpen == 2){ //first card said something else, not 'Door Opens'
                return;
            }
        }
        if (doorsToOpen == 1){ // Either first one opened or there was one door to begin with
            Card card = cardHolder.doorDeck.takeCard();
            logger.debug("Activating " + card + " card");
            processDrawDoorCard();
        }
    }
    
    public void endTurn(String player) {
        if(state != GameState.IN_PROGRESS){
            return;
        }
        if(!currentPlayer.getName().equals(player)){
            logger.debug("Current player is:" + currentPlayer.getName() + " so " + player +" can't do anyting now.");
            return;
        }
        if( !currentPlayer.isDead() && (!currentPlayer.isMoved() && !currentPlayer.isSearched())){
            //can't skip turns in some special rooms
            if( map.getTile(currentPlayer.getPosition()).getEffects().size() > 0){
                 for(Effect e : map.getTile(currentPlayer.getPosition()).getEffects()){
                     if(e instanceof BottomlessPit){
                        addMessage(new ChatMessage("This place is too dangerous to wait here.", "Game"));
                         return;
                     }
                 }
            }
            logger.debug("Player " + currentPlayer.getName() + " ended his turn without doing anything.");
            addMessage(new ChatMessage("You wait and do nothing.", "Game"));
            currentPlayer.setMoved(true);
//            logger.debug("Player " + currentPlayer.getName() + " can't end his turn yet.");
//            addMessage(new ChatMessage("You cannot end your turn without moving or searching.", "Game"));
            return;
        }
        resetTurnVariables();
        newTurn();
    }

    public void newTurn(){
        int currentPlayerIndex = players.indexOf(currentPlayer);
        //cycle through all players and find the first one that is:
        //1. located after current one
        //2. is alive
        while(true){
            if(currentPlayerIndex == players.size()-1){
                currentPlayerIndex = 0;
            }else{
                currentPlayerIndex++;
            }
            if(players.get(currentPlayerIndex).isDead()){
                continue;
            }else{
                break;
            }
        }
        currentPlayer = players.get(currentPlayerIndex);
        
        turn++;
        addMessage(new ChatMessage("Turn " + turn, "Game"));
        if(currentPlayer == players.get(0)){
            sunPosition++;
            addMessage(new ChatMessage("Current sun position: " + sunPosition + "/" + sunMaxPosition, "Game"));
        }
        if(sunPosition == sunMaxPosition-10){
            addMessage(new ChatMessage("10 turns left until the night comes!", "Game"));
        }
        if(sunPosition == sunMaxPosition-5){
            addMessage(new ChatMessage("5 turns left until the night comes!", "Game"));
        }
        if(sunPosition == sunMaxPosition){
            processSun();
            endGame();
            return;
        }
        
        logger.debug("Current player: " + currentPlayer.getName());
        addMessage(new ChatMessage("Current player: " + currentPlayer.getName(), "Game"));
        addMessage(new NewTurn(currentPlayer.getName()));
        currentPlayer.resetTurnVariables();
        
        processCurrentPlayerStatus();
    }

    public void effectCaveIn() {
        currentPlayerState = PlayerState.guessCaveInNumber;
        currentPlayerContextValue = random.nextInt(6)+1;
        logger.debug("Cave-In death is set to " + currentPlayerContextValue);
        addMessage(new ChatMessage("A cave-in! Beware the falling rocks!", "Game"));
        addMessage(new GuessNumber());
    }

    public void processGuessNumber(GuessNumber guessNumber) {
        if(currentPlayerState != PlayerState.guessCaveInNumber){
            logger.debug("Recieved unwanted GuessNumber!");
            return;
        }
        logger.debug("Recieved GuessNumber:" + guessNumber);
        if(guessNumber.value == currentPlayerContextValue){
            killPlayer(currentPlayer, "A giant boulder falls on his head!");
        }else{
            hurtPlayer(currentPlayer, 2, "You are battered by falling rocks!");
        }
        currentPlayerContextValue = 0;
    }

    private void resetTurnVariables(){
        currentPlayerState = PlayerState.idle;
        currentPlayerContextValue = 0;
        battleStarted = false;
    }
    
    private void killPlayer(Player player, String cause) {
        logger.debug("Killing " + player + " because " + cause);
        if(player.isDead()){
            logger.debug("Tried to kill " + player + " because " + cause + " but he's dead already.");
        }
        
        player.setDead(true);
        //end combat if player was killed in it
        if(currentPlayerState == PlayerState.InCombat){
            addMessage(new EndBattle());
        }
        
        changePlayerAttribute(player, PlayerAttributes.HP, 0);
        addMessage(new ChatMessage(cause + " " + player.getName() + " was killed!", "Game"));
        addMessage(new KillPlayer(player.getName()));
        //end game if there's no one left alive
        for(Player p : players){
            if(!p.isDead()){
                endTurn(currentPlayer.getName());
                return;
            }
        }
        endGame();
    }

    public void hurtPlayer(Player player, int value, String description) {
        logger.debug(" Hurting " + player + " for " + value + " he " + description);
        int newHealth = currentPlayer.getHp() - value;
        if(value > 0){
            addMessage(new ChatMessage(description + " " + player.getName() + " was hurt for " + value + " HP!", "Game"));
        }else{
            addMessage(new ChatMessage(description + " " + player.getName() + " left unscratched!", "Game"));
        }
        changePlayerAttribute(player, PlayerAttributes.HP, newHealth);
        if(newHealth < 1){
            killPlayer(currentPlayer, "");
        }
    }

    public void startMonsterCombat(MonsterType type) {
        //set correct variables
        battleStarted = true;

        logger.debug("startMonsterCombat:" + type);
        //give player a list of options and remember them, so it's possible to check answer later
        addMessage(new StartBattle(new int[]{1,2,3}));
        addMessage(new ChatMessage(currentPlayer.getName() + " has encountered " + type + "!", "Game"));
    }

    public void processStartBattle(StartBattle startBattle) {
        int playerDecision = startBattle.choices[0];
        logger.debug("Player decided to " + playerDecision);
        //check if player can do what he wants to
        
        //take a Monster card

        //player wants to Attack
        if (playerDecision == 1) {
            //player Attacks
            currentPlayerState = PlayerState.InCombat;
            monsterHP = 5;
            addMessage(new BattleAction(0));
            addMessage(new ChatMessage(currentPlayer.getName() + " charges into battle!", "Game"));
        }

        //player wants to Wait
        if (playerDecision == 2) {
            //player recieves damage and Attacks
            currentPlayerState = PlayerState.InCombat;
            monsterHP = 3;
            addMessage(new BattleAction(0));
            hurtPlayer(currentPlayer, 2, "A monster viciously attacks from the shadows!");
            if(!currentPlayer.isDead()){
                addMessage(new ChatMessage(currentPlayer.getName() + " have to fight!", "Game"));
            }
        }

        //player wants to Escape
        //todo: apply check for escape:
        //"you cannot escape if there's a portcullis behind you or you entered via secret door"
        if (playerDecision == 3) {
            //player escapes
            Position prevPos = currentPlayer.getPreviousPosition();
            movePlayer(prevPos, currentPlayer);
            addMessage(new ChatMessage(currentPlayer.getName() + " retreated to previous room!", "Game"));
            if(currentPlayer.status == PlayerStatus.IN_CAVEIN){
                currentPlayer.status = PlayerStatus.NONE;                
            }
        }        
        
        //list of possible outcomes:
        //combat
        //escape - move back, don't draw a room or a door card, but special tiles work
        //todo: store previous player position somewhere
        //flee
        //slash/escape
        //slash/combat
    }
    
    public void processBattleAction(BattleAction ba){
        if(!battleStarted){
            logger.debug("Not in combat");
            return;
        }
        if(!(currentPlayerState == PlayerState.InCombat)){
            logger.debug("Not in combat");
            return;
        }
        if(monsterHP <= 0){
            logger.debug("Stop fighting, he's dead already.");
            return;
        }
        int pa = ba.action;
        int ma = 1 + random.nextInt(3);
        logger.debug("Player chose action " + pa);
        logger.debug("Monster chose action " + ma);
        
        int playerDamage = 0;
        int monsterDamage = 0;
        
        switch (pa) {
            default:
            case 1: //MB
                switch (ma) {
                    default:
                    case 1: //MB
                        monsterDamage = 1;
                        playerDamage = 1;
                        break;
                    case 2: //S
                        monsterDamage = 2;
                        break;
                    case 3: //LA
                        playerDamage = 1;
                        break;
                }
                break;

            case 2: //S
                switch (ma) {
                    default:
                    case 1: //MB
                        playerDamage = 1;
                        break;
                    case 2: //S
                        monsterDamage = 1;
                        playerDamage = 1;
                        break;
                    case 3: //LA
                        monsterDamage = 1;
                        break;
                }
                break;

            case 3: //LA
                switch (ma) {
                    default:
                    case 1: //MB
                        monsterDamage = 1;
                        break;
                    case 2: //S
                        playerDamage = 1;
                        break;
                    case 3: //LA
                        monsterDamage = 1;
                        playerDamage = 1;
                        break;
                }
                break;
        }
        
        logger.debug("PDamage: " + playerDamage + " MDamage: " + monsterDamage);
        if(monsterDamage > 0){
            addMessage(new ChatMessage(currentPlayer.getName() + " dealt " + monsterDamage + " damage to a monster!", "Game"));
            monsterHP -= monsterDamage;
        }
        if(monsterHP <= 0){
            addMessage(new ChatMessage(currentPlayer.getName() + " emerged victorious!", "Game"));
            addMessage(new EndBattle());
            return;
        }
        if(playerDamage > 0){
            hurtPlayer(currentPlayer, playerDamage, "Monster lands a hit!");
        }
    }

    public void processSecretDoor() {
        usingSecretDoor = true;
        currentPlayer.setMoved(false);
    }

    public void processPlayerSearchRoom() {
        logger.debug(currentPlayer.getName() + " is trying to search room at " + currentPlayer.getPosition());
        if(state != GameState.IN_PROGRESS){
            return;
        }
        if(currentPlayer.searchInRow == 2){
            logger.debug(currentPlayer.getName() + " can't search this room anymore.");
            addMessage(new ChatMessage("You can't search this room anymore this turn.", "Game"));
            return;
        }
        if(currentPlayer.isMoved()){
            logger.debug(currentPlayer.getName() + " can't search this turn.");
            addMessage(new ChatMessage("You can't search this turn.", "Game"));
            return;
        }        
        if(currentPlayer.isSearched()){
            logger.debug(currentPlayer.getName() + " can't search this room this turn again.");
            addMessage(new ChatMessage("You can't search again this turn.", "Game"));
            return;
        }
        if(currentPlayer.getPosition().equals(treasureChamberPositionLeft) || currentPlayer.getPosition().equals(treasureChamberPositionRight)){
            logger.debug("Searching Treasure Chamber.");
            currentPlayer.setMoved(true);
            currentPlayer.setSearched(true);
            processDrawTreasureCard();
            processDrawTreasureCard();
            processDrawDragonCard();
            return;
        }
        if(!map.getTile( currentPlayer.getPosition()).isIsSearchable()){
            logger.debug("Can't search here.");
            addMessage(new ChatMessage("No use to search here. This place is empty.", "Game"));
            return;
        }
        currentPlayer.searchInRow++;
        currentPlayer.setMoved(true);
        currentPlayer.setSearched(true);
        processDrawSearchCard();
    }

    public void effectDoorOpens() {
        addMessage(new ChatMessage("Door opens!", "Game"));   
        doorsToOpen--;
        if(doorsToOpen == 1){
            addMessage(new ChatMessage("But there's another one behind it!", "Game"));
        }
    }

    public void effectDoorJams() {
        addMessage(new ChatMessage("Door is jammed and refuses to open!", "Game"));         
    }

    public void processCaveInTile() {
        addMessage(new ChatMessage("This room is partially collapsed. Moving forward is difficult.", "Game"));         
        processDrawRoomCard(); //"Take a Room card as usual"
        currentPlayer.status = PlayerStatus.IN_CAVEIN;
    }

    public void processCorridorTile() {
        addMessage(new ChatMessage("This room is empty and you quickly pass through it. You can move again.", "Game"));         
        currentPlayer.setMoved(false);
    }

    public void processBottomlessPitTile() {
        addMessage(new ChatMessage("There is a giant hole in the ground. You will need to jump to cross it.", "Game"));
        currentPlayer.status = PlayerStatus.BOTTOMLESS_PIT;        
    }
    
    public void processChamberOfDarknessTile() {
        addMessage(new ChatMessage("This room is filled with darkness - you can't see where you're going!", "Game"));
        currentPlayer.status = PlayerStatus.DARKNESS;
    }    

    public void processChasmTile() {
        addMessage(new ChatMessage("An impassable rift separates this room in half.", "Game"));
        currentPlayer.status = PlayerStatus.CHASM;
    }

    public void processStartingTile() {
        addMessage(new ChatMessage("You arrive at the Dungeon entrance. You can move again.", "Game"));         
        currentPlayer.setMoved(false);
    }
    
    public void processRotatingRoomTile() {
        Position pos = currentPlayer.getPosition();
        if(!rotatedRooms.contains(pos)){
            rotatedRooms.add(pos);
            addMessage(new ChatMessage("As you step into this room, it starts to move!", "Game"));
            placeTile(pos, map.getTile(pos), 2);
        }else{
            addMessage(new ChatMessage("You can't find anything special here.", "Game"));
        }
    }
    
    private void processCurrentPlayerStatus() {
        if(currentPlayer.turnsToSkip > 0){
            addMessage(new ChatMessage(currentPlayer.turnsSkipReason + " You skip your turn.", "Game"));
            currentPlayer.turnsToSkip--;
            currentPlayer.setMoved(true);
        }else if(currentPlayer.turnsToSkip == 0){
            addMessage(new ChatMessage("You can move now.", "Game"));
            currentPlayer.turnsToSkip = -1;
        }else if(currentPlayer.status == PlayerStatus.IN_PIT){
            addMessage(new ChatMessage("You try to escape from the pit..", "Game"));
            if(testPlayerAgility(12)){
                addMessage(new ChatMessage("..And succeed!", "Game"));
                currentPlayer.status = PlayerStatus.NONE;
            }else{
                addMessage(new ChatMessage("..But you fall back into it. You skip your turn.", "Game"));
                currentPlayer.setMoved(true);
            }
        } else if (currentPlayer.status == PlayerStatus.NO_TORCH) {
            addMessage(new ChatMessage("You try to re-lit your torch..", "Game"));
            if (diceRoll(1, 6, 0) >= 3 ) {
                addMessage(new ChatMessage("..And succeed!", "Game"));
                currentPlayer.status = PlayerStatus.NONE;
            } else {
                addMessage(new ChatMessage("..But fail. Skip your turn.", "Game"));
                currentPlayer.setMoved(true);
            }
        }
    }

    private boolean testPlayerAgility(int i) {
        if(random.nextInt(i) > currentPlayer.agility){
            return false;
        }
        return true;
    }

    public void effectCrossfireTrap() {
        addMessage(new ChatMessage("Arrows are shooting out of the walls!", "Game"));
//        if(currentPlayer.armor > 0){
//            addMessage(new ChatMessage("Your armor partially saves you from the damage.", "Game"));
//        }
        int damage = diceRoll(1,12, -currentPlayer.armor);
        if(damage > 0){
            hurtPlayer(currentPlayer, damage, "You are hurt by arrows!");
        }else{
            addMessage(new ChatMessage("You evade all of them!", "Game"));
        }
    }

    public int diceRoll(int d1, int d2, int mod) {
        int res = 0;
        for(int i = 0; i < d1; i++){
            res += random.nextInt(d2);
        }
        res += mod;
        if(res < 0){
            res = 0;
        }
        logger.debug("Rolled " + d1 + "d" + d2 + (mod==0?"":((mod>0?"+":"") + mod)) + "=" + res);
        return res;
    }

    public void effectExplosion() {
        addMessage(new ChatMessage("As you step on a loose stone, an explosion occurs!", "Game"));
//        if(currentPlayer.armor > 0){
//            addMessage(new ChatMessage("Your armor partially saves you from the damage.", "Game"));
//        }
        hurtPlayer(currentPlayer, 4, "You are hurt by explosion!");
        currentPlayer.turnsToSkip = 1;
        currentPlayer.turnsSkipReason = "Your head is still ringing after explosion.";
    }

    public void processTrapdoor() {
        addMessage(new ChatMessage("Floor below you disappears!..", "Game"));
        if(testPlayerAgility(12)){
            addMessage(new ChatMessage("..But you're fast enough to avoid falling!", "Game"));
        }else{
            addMessage(new ChatMessage("..And you fall into a pit!", "Game"));
            currentPlayer.setMoved(true);
            currentPlayer.status = PlayerStatus.IN_PIT;
            hurtPlayer(currentPlayer, diceRoll(1, 6, 0), "That hurts!");
        }
    }

    public void effectPoisonGas() {
        addMessage(new ChatMessage("The air here is poisoned!", "Game"));
        int dmg = diceRoll(1, 6, -3);
        if(dmg > 0){
            hurtPlayer(currentPlayer, dmg, "Your lungs are burning!");
        }
        currentPlayer.turnsToSkip = diceRoll(1, 6, -3);
        currentPlayer.turnsSkipReason = "You are unconscious from the gas.";
        if(currentPlayer.turnsToSkip > 0){
            addMessage(new ChatMessage("You fall unconscious.", "Game"));
        }else{
            addMessage(new ChatMessage("Gas wasn't strong enough to knock you out.", "Game"));
        }
    }
    
    public void effectTorchGoesOut() {
        addMessage(new ChatMessage("Your torch goes out! You're left in a complete darkness!", "Game"));
        currentPlayer.status = PlayerStatus.NO_TORCH;
    }
    
    public void ShuffleDeck(DeckType type) {
        logger.debug("Shuffling Deck " + type);
        switch(type){
            default:
            case Corpse: cardHolder.corpseDeck.shuffle(); processDrawCorpseCard(); break;
            case Crypt: cardHolder.cryptDeck.shuffle(); processDrawCryptCard(); break;
            case Door: cardHolder.doorDeck.shuffle(); processDrawDoorCard(); break;
            case Dragon: cardHolder.dragonDeck.shuffle(); processDrawDragonCard(); break;
            case Monster: cardHolder.monsterDeck.shuffle(); break;
            case Room: cardHolder.roomDeck.shuffle(); processDrawRoomCard(); break;
            case Search: cardHolder.searchDeck.shuffle(); processDrawSearchCard(); break;
            case Trap: cardHolder.trapDeck.shuffle(); processDrawTrapCard(); break;
            case Treasure: cardHolder.treasureDeck.shuffle(); processDrawTreasureCard(); break;
        }
    }

    public void effectTreasureChamber() {
        logger.debug("You enter the treasure chamber! It's hoarding time!");
        addMessage(new ChatMessage("You enter the treasure chamber!", "Game"));
        
        processDrawTreasureCard();
        processDrawTreasureCard();
        
        Card card = cardHolder.dragonDeck.takeCard();
        logger.debug("Activating " + card + " card");
        card.activate(this);
    }

    public void effectDragonSleeps() {
        logger.debug("Dragon sleeps. " + cardHolder.dragonDeck.size() + " tries left.");
        addMessage(new ChatMessage("The Dragon is asleep.", "Game"));
    }

    public void effectDragonAwakens() {
        logger.debug("Dragon awakens!");
        addMessage(new ChatMessage("To your horror, the Dragon awakens!", "Game"));
        addMessage(new ChatMessage("Drop all your gold and run while you still can!.", "Game"));
        changePlayerAttribute(currentPlayer, PlayerAttributes.Gold, 0);
        hurtPlayer(currentPlayer, diceRoll(1, 12, 0), "Dragon breathes fire at you!");
        movePlayer(currentPlayer.getPreviousPosition(), currentPlayer);
        
        ShuffleDeck(DeckType.Dragon);
    }

    public void effectDoorTrap() {
        addMessage(new ChatMessage("As you touch the door, you activate a hidden trap!", "Game"));
        switch(random.nextInt(3)){
            default:
            //TRAP d6
            case 0:
                hurtPlayer(currentPlayer, diceRoll(1, 6, 0), "A poisoned dart hits you!");
                break;
            //TRAP d6+3-Luck
            case 1:
                hurtPlayer(currentPlayer, diceRoll(1, 6, -currentPlayer.luck), "The doorknob is covered in spikes!");
                break;
            //TRAP d12-Luck
            case 2:
                hurtPlayer(currentPlayer, diceRoll(1, 12, -currentPlayer.luck), "A spear springs from below your feet!");
                break;
        }
    }    
    
    public void effectCryptTrap() {
        addMessage(new ChatMessage("As you touch the coffin, you activate a hidden trap!", "Game"));
        switch(random.nextInt(2)){
            default:
            //TRAP d6-3
            case 0:
                hurtPlayer(currentPlayer, diceRoll(1, 6, -3), "A poisoned dart hits you!");
                break;
            //TRAP d12-3-Luck
            case 1:
                hurtPlayer(currentPlayer, diceRoll(1, 12, -3-currentPlayer.luck), "Poisoned spiders were hiding inside!");
                break;
        }
    }
    
    public void effectSkeleton() {
        addMessage(new ChatMessage("You examine the skeleton remains, but find nothing of value.", "Game"));
    }

    public void effectCrypt() {
        currentPlayerState = PlayerState.yesNoQueryWaitCrypt;
        addMessage(new ChatMessage("You found a crypt. Do you want to search the coffin?", "Game"));
        addMessage( new YesNoQuery());
    }
    
    public void effectDeadAdventurer() {
        currentPlayerState = PlayerState.yesNoQueryWaitCorpse;
        addMessage(new ChatMessage("You found the body of a dead adventurer.<br>Do you want to search his remains?", "Game"));
        addMessage( new YesNoQuery());
    }
    
    public void effectWizardCurse() {
        addMessage(new ChatMessage("Suddenly, the walls began to move! The dungeon is changing!", "Game"));
        int rotate = random.nextInt(3) + 1;
        logger.debug("Rotating all Corridor Tiles by " + rotate);
        //iterate over all map positions
        for (int x = 0; x < GameMap.MAX_X - 1; x++) {
            for (int y = 0; y < GameMap.MAX_Y - 1; y++) {
                //if there is a tile
                if (!map.isFree(x, y)) {
                    Tile t = map.getTile(x, y);
                    if (t.getEffects().isEmpty()) {
                        continue;
                    }
                    //check if it's a corridor
                    for (Effect e : t.getEffects()) {
                        if (e instanceof CorridorTile) {
                            //if it is - rotate it
                            placeTile(x, y, t, rotate);
                        }
                    }
                }
            }
        }
    } 
    
    private void processSun() {
        for(Player p : players){
            if(p.isDead()){
                continue;
            }
            //player is safe only when he's in one of the four starting tiles
            if (!p.getPosition().equals(startingUpLeftPosition)
                    && !p.getPosition().equals(startingUpRightPosition)
                    && !p.getPosition().equals(startingDownLeftPosition)
                    && !p.getPosition().equals(startingDownRightPosition)) {
                killPlayer(p, "With the sunlight gone, so is any hope of escaping.");
            }
        }
    }

    public void processYesNoAnswer(YesNoQuery yesNoQuery) {
        logger.debug("Recieved YesNoAnswer = " + yesNoQuery.answer + " status = " + currentPlayerState);
        if(yesNoQuery.answer == 1){
            currentPlayerState = PlayerState.idle;
            addMessage(new ChatMessage("You leave the remains alone.", "Game"));
            return;
        }
        if(currentPlayerState == PlayerState.yesNoQueryWaitCorpse){
            currentPlayerState = PlayerState.idle;
            processDrawCorpseCard();
        }else if(currentPlayerState == PlayerState.yesNoQueryWaitCrypt){
            currentPlayerState = PlayerState.idle;
            processDrawCryptCard();
        }
    }
}
