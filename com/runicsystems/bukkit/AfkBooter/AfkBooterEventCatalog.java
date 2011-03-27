package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.Event;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Daen
 * Date: Mar 24, 2011
 * Time: 12:51:02 PM
 */
public class AfkBooterEventCatalog
{
    private List<Event.Type> blockEvents;
    private List<Event.Type> playerEvents;

    public AfkBooterEventCatalog()
    {
        blockEvents = new LinkedList<Event.Type>();
        playerEvents = new LinkedList<Event.Type>();
    }

    public void initialize(AfkBooterSettings settings)
    {
        if(settings.isMoveListen())
            playerEvents.add(Event.Type.PLAYER_MOVE);

        if(settings.isInventoryOpenListen())
            playerEvents.add(Event.Type.INVENTORY_OPEN);

        if(settings.isChatListen())
            playerEvents.add(Event.Type.PLAYER_CHAT);

        if(settings.isBlockBreakListen())
            blockEvents.add(Event.Type.BLOCK_BREAK);

        if(settings.isBlockPlaceListen())
            blockEvents.add(Event.Type.BLOCK_PLACED);

        if(settings.isDropItemListen())
            playerEvents.add(Event.Type.PLAYER_DROP_ITEM);
    }

    public List<Event.Type> getPlayerEvents()
    {
        return playerEvents;
    }

    public List<Event.Type> getBlockEvents()
    {
        return blockEvents;
    }
}
