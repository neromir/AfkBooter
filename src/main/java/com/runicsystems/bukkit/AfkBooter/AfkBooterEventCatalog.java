package com.runicsystems.bukkit.AfkBooter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

/**
 * Created by IntelliJ IDEA.
 * User: Daen
 * Date: Mar 24, 2011
 * Time: 12:51:02 PM
 * 
 * @author Daen, morganm
 */
public class AfkBooterEventCatalog
{
	public enum Type {
		PLAYER_MOVE,
		PLAYER_CHAT,
		PLAYER_COMMAND_PREPROCESS,
		PLAYER_DROP_ITEM,
		PLAYER_INTERACT,
		PLAYER_INTERACT_ENTITY,
		INVENTORY_OPEN,
		BLOCK_PLACE,
		BLOCK_BREAK
	};
    private final AfkBooter plugin;
    private final Set<Type> events = new HashSet<Type>();

    public AfkBooterEventCatalog(AfkBooter plugin)
    {
        this.plugin = plugin;
    }

    public void initialize(AfkBooterSettings settings)
    {
        for(String eventName : settings.getListedEvents())
        {
            try
            {
            	Type type = Type.valueOf(eventName);
            	events.add(type);
            }
            catch(IllegalArgumentException e)
            {
                plugin.log("Invalid listened-events setting, \"" + eventName + "\" please verify that it is correct.", Level.SEVERE);
            }
        }

//        if(settings.isBlockItems())
//            playerEvents.add(new EventInfo(Event.Type.PLAYER_PICKUP_ITEM, Event.Priority.High));
    }
    
    public Set<Type> getEvents() { return Collections.unmodifiableSet(events); }

    /** This is ugly. New-style Bukkit events have a lot of advantages, but dynamic
     * registry of events is NOT one of them. There are 3 options for handling this:
     * 
     *   1 - just register everything anyway. This is not very efficient, as admins might
     *   only want one or two events and we'd be hooking lots more than needed and forcing
     *   extra calls/checks on every event.
     *   2 - put each event type into it's own class handler and register each class
     *   individually if that event type is turned on. This just means lots of classes
     *   to maintain.
     *   3 - do what I'm doing below and dynamically register each event. This is messy
     *   code, but it keeps the event registration in one place and minimizes changes
     *   to the existing structure of the rest of the plugin. This code could be written
     *   much more elegantly using reflection and method signature matching, but that
     *   would be a lot more work and require a lot of testing to get it right, so
     *   straight, simple and ugly wins for now.
     * 
     */
    public void registerEvents() {
    	final PluginManager pm = plugin.getServer().getPluginManager();
    	
    	final AfkBooterBlockListener blockListener = plugin.getBlockListener();
    	final AfkBooterPlayerListener playerListener = plugin.getPlayerListener();
    	
    	for(Type t : events) {
    		switch(t) {
    		case BLOCK_BREAK: {
    	        pm.registerEvent(BlockBreakEvent.class,
    	        		blockListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					blockListener.onBlockBreak((BlockBreakEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    			
    		} break;
			
    		case BLOCK_PLACE: {
    	        pm.registerEvent(BlockPlaceEvent.class,
    	        		blockListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					blockListener.onBlockPlace((BlockPlaceEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    			
    		} break;
			
    		case PLAYER_CHAT: {
    	        pm.registerEvent(PlayerChatEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerChat((PlayerChatEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;

    		case PLAYER_COMMAND_PREPROCESS: {
    	        pm.registerEvent(PlayerCommandPreprocessEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerCommandPreprocess((PlayerCommandPreprocessEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		case PLAYER_MOVE: {
    	        pm.registerEvent(PlayerMoveEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerMove((PlayerMoveEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		case PLAYER_DROP_ITEM: {
    	        pm.registerEvent(PlayerDropItemEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerDropItem((PlayerDropItemEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		case PLAYER_INTERACT: {
    	        pm.registerEvent(PlayerInteractEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerInteract((PlayerInteractEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		case PLAYER_INTERACT_ENTITY: {
    	        pm.registerEvent(PlayerInteractEntityEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onPlayerInteractEntity((PlayerInteractEntityEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		case INVENTORY_OPEN: {
    	        pm.registerEvent(InventoryOpenEvent.class,
    	        		playerListener,
    	        		EventPriority.MONITOR,
    	        		new EventExecutor() {
    	        			public void execute(Listener listener, Event event) throws EventException {
    	        				try {
    	        					playerListener.onInventoryOpen((InventoryOpenEvent) event);
    	        				} catch (Throwable t) {
    	        					throw new EventException(t);
    	        				}
    	        			}
    			        },
    			        plugin);
    		} break;
    		
    		}	// end switch
    	}
    }
}
