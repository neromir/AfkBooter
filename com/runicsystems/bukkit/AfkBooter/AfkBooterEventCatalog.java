package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.Event;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by IntelliJ IDEA.
 * User: Daen
 * Date: Mar 24, 2011
 * Time: 12:51:02 PM
 */
public class AfkBooterEventCatalog
{
    private AfkBooter plugin;
    private List<EventInfo> blockEvents;
    private List<EventInfo> playerEvents;

    public AfkBooterEventCatalog(AfkBooter plugin)
    {
        this.plugin = plugin;
        blockEvents = new LinkedList<EventInfo>();
        playerEvents = new LinkedList<EventInfo>();
    }

    public void initialize(AfkBooterSettings settings)
    {
        for(String eventName : settings.getListedEvents())
        {
            try
            {
                if(eventName.startsWith("BLOCK"))
                    blockEvents.add(new EventInfo(Event.Type.valueOf(eventName), Event.Priority.Monitor));
                else
                    playerEvents.add(new EventInfo(Event.Type.valueOf(eventName), Event.Priority.Monitor));
            }
            catch(IllegalArgumentException e)
            {
                plugin.log("Invalid listened-events setting, \"" + eventName + "\" please verify that it is correct.", Level.SEVERE);
            }
        }

        if(settings.isBlockItems())
            playerEvents.add(new EventInfo(Event.Type.PLAYER_PICKUP_ITEM, Event.Priority.High));
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
