package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.player.*;

/**
 * Handle events for all Player related events
 *
 * @author neromir
 */
public class AfkBooterPlayerListener extends PlayerListener
{
    private final AfkBooter plugin;

    public AfkBooterPlayerListener(AfkBooter instance)
    {
        plugin = instance;
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event)
    {
        // Experimental code for ignoring jumping.
        if(plugin.getSettings().isUseJumpIgnore() && (event.getTo().getY() > event.getFrom().getY() || event.getTo().getY() < event.getFrom().getY()))
            return;

        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        plugin.stopTrackingPlayer(event.getPlayer().getName());
    }

    @Override
    public void onInventoryOpen(PlayerInventoryEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }
}

