package jdungeonquest.effects;

import jdungeonquest.Game;

public class DoorOpens implements Effect{

    @Override
    public void doAction(Game g) {
        g.effectDoorOpens();
    }
    
}
