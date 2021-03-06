package jdungeonquest;

import jdungeonquest.enums.EntryDirection;
import jdungeonquest.enums.RoomWallType;

public class GameMap {
    public final static int MAX_X = 10;
    public final static int MAX_Y = 13;

    private Tile[][] tiles =  new Tile[MAX_X][MAX_Y];
    
    public GameMap(){
        
    }
   
    public void setTile(int x, int y, Tile tile) {
        tiles[x][y] = tile;
    }

    public Tile getTile(int x, int y) {
        return tiles[x][y];
    }

    public Tile[][] getTiles(){
        return tiles;
    }
    
    /**
     * 
     * @param x
     * @param y
     * @return 
     */
    public boolean isFree(int x, int y) {
        if(x < 0 || y < 0){
            return false;
        }
        if (tiles[x][y] == null) {
            return true;
        }
        return false;
    }

    public boolean isAdjacent(Position p1, Position p2){
        if(p1 == p2){
            return false;
        }
        if( (p1.getX() == p2.getX()) && (Math.abs( p1.getY() - p2.getY()) == 1)){
            return true;
        }
        if( (p1.getY() == p2.getY()) && (Math.abs( p1.getX() - p2.getX()) == 1)){
            return true;
        }
        return false;
    }
    
    public boolean canMoveFrom(Position p1, Position p2){
        if(p1.getX() < 0 || p1.getY() < 0 || p2.getX() < 0 || p2.getY() < 0){
            return false;
        }        
        //U[0] L[1] D[2] R[3]
        if( p1.getY() == p2.getY()){ //same column
            if(p1.getX() > p2.getX()){ //going left
                if(getTile(p1).getWalls().get(1) != RoomWallType.WALL){
                    return true;
                }
            }
            if(p1.getX() < p2.getX()){ //going right
                if(getTile(p1).getWalls().get(3) != RoomWallType.WALL){
                    return true;
                }
            }
            return false;
        }
        if( p1.getX() == p2.getX()){ //same row
            if(p1.getY() > p2.getY()){ //going up
                if(getTile(p1).getWalls().get(0) != RoomWallType.WALL){
                    return true;
                }
            }
            if(p1.getY() < p2.getY()){ //going down
                if(getTile(p1).getWalls().get(2) != RoomWallType.WALL){
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public boolean canMoveTo(Position p1, Position p2){
        return canMoveFrom(p2, p1);
    }    
    
    /**
     * @param playerPos
     * @param tilePos
     * @param tile
     * @return number of rotations needed to place tile correctly.
     */
    public int getRequiredRotation(Position playerPos, Position tilePos, Tile tile) {
        //U[0] L[1] D[2] R[3]
        EntryDirection direction = null;
        if( playerPos.getY() == tilePos.getY()){ //same column
            if(playerPos.getX() > tilePos.getX()){ //going left, make sure entry is on the right
                direction = EntryDirection.RIGHT;
            }
            if(playerPos.getX() < tilePos.getX()){ //going right
                direction = EntryDirection.LEFT;
            }
        }
        if( playerPos.getX() == tilePos.getX()){ //same row
            if(playerPos.getY() > tilePos.getY()){ //going up
                direction = EntryDirection.DOWN;
            }
            if(playerPos.getY() < tilePos.getY()){ //going down
                direction = EntryDirection.UP;
            }
        }
        
        int turns = 0;
        
        Tile dummy = new Tile(tile);
        while(dummy.getEntryDirection() != direction){
            turns++;
            dummy.rotate(1);
        }
        setTile(tilePos.getX(), tilePos.getY(), tile);
        return turns;
    }

    Tile getTile(Position position) {
        return getTile(position.getX(), position.getY());
    }

    //todo: there's a lot of duplicated code here, need think of a way to fix this
    int getNumberOfDoorsBetween(Position from, Position to) {
        int doors = 0;
        
        if( from.getY() == to.getY()){ //same column
            if(from.getX() > to.getX()){ //going left
                if(getTile(from).getWalls().get(1) == RoomWallType.DOOR){
                    doors++;
                }
                if(getTile(to).getWalls().get(3) == RoomWallType.DOOR){
                    doors++;
                }
            }
            if(from.getX() < to.getX()){ //going right
                if(getTile(from).getWalls().get(3) == RoomWallType.DOOR){
                    doors++;
                }
                if(getTile(to).getWalls().get(1) == RoomWallType.DOOR){
                    doors++;
                }            }
        }else if( from.getX() == to.getX()){ //same row
            if(from.getY() > to.getY()){ //going up
                if(getTile(from).getWalls().get(0) == RoomWallType.DOOR){
                    doors++;
                }
                if(getTile(to).getWalls().get(2) == RoomWallType.DOOR){
                    doors++;
                }
            }
            if(from.getY() < to.getY()){ //going down
                if(getTile(from).getWalls().get(2) == RoomWallType.DOOR){
                    doors++;
                }
                if(getTile(to).getWalls().get(0) == RoomWallType.DOOR){
                    doors++;
                }
            }
        }        
        return doors;
    }
}
