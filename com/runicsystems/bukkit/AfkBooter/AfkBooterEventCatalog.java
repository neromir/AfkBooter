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
    private List<EventInfo> blockEvents;
    private List<EventInfo> playerEvents;

    private List<EventInfo> allEvents;

    public AfkBooterEventCatalog()
    {
        blockEvents = new LinkedList<EventInfo>();
        playerEvents = new LinkedList<EventInfo>();
        allEvents = new LinkedList<EventInfo>();
    }

    public void initialize(AfkBooterSettings settings)
    {
        for(String eventName : settings.getListedEvents())
        {
            if(eventName.startsWith("BLOCK"))
                blockEvents.add(new EventInfo(Event.Type.valueOf(eventName), Event.Priority.Monitor));
            else
                playerEvents.add(new EventInfo(Event.Type.valueOf(eventName), Event.Priority.Monitor));
        }

        if(settings.isBlockItems())
            playerEvents.add(new EventInfo(Event.Type.PLAYER_PICKUP_ITEM, Event.Priority.High));
    }

    public List<EventInfo> getAllEvents()
    {
        return allEvents;
    }

    public List<EventInfo> getPlayerEvents()
    {
        return playerEvents;
    }

    public List<EventInfo> getBlockEvents()
    {
        return blockEvents;
    }

    public class EventInfo
    {
        public Event.Type type;
        public Event.Priority priority;

        public EventInfo(Event.Type type, Event.Priority priority)
        {
            this.type = type;
            this.priority = priority;
        }
    }
}
