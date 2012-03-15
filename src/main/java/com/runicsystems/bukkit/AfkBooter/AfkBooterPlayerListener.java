package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handle events for all Player related events
 *
 * @author neromir, morganm
 */
public class AfkBooterPlayerListener implements Listener
{
    private final AfkBooter plugin;

    public AfkBooterPlayerListener(AfkBooter instance)
    {
        plugin = instance;
    }

    /* onPlayerJoin and onPlayerQuit are always hooked, so they are defined here with
     * Bukkit annotations to indicate that. All the other event methods are dynamically
     * hooked based on the config file as defined by the admin, so they are only hooked
     * if configured to do so. Hooking handled externally in AfkBooterEventCatalog.
     */
    
    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        plugin.stopTrackingPlayer(event.getPlayer().getName());
    }

    public void onPlayerMove(PlayerMoveEvent event)
    {
        if(event.isCancelled())
            return;

        // Experimental code for ignoring jumping.
        if(plugin.getSettings().isUseJumpIgnore() && (event.getTo().getY() > event.getFrom().getY() || event.getTo().getY() < event.getFrom().getY()))
            return;

        // Ignores player movement if they are sitting in a vehicle.
        if(plugin.getSettings().isIgnoreVehicles() && event.getPlayer().getVehicle() != null)
            return;

        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onInventoryOpen(InventoryOpenEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerChat(PlayerChatEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerDropItem(PlayerDropItemEvent event)
    {
        if(!event.isCancelled())
            plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if(!event.isCancelled())
            plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
    {
        if(!event.isCancelled())
            plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onPlayerPickupItem(PlayerPickupItemEvent event)
    {
        if(!plugin.getSettings().isKickIdlers() && plugin.getSettings().isBlockItems() &&
                plugin.isPlayerIdle(event.getPlayer().getName()))
        {
            event.setCancelled(true);
        }
    }
}

