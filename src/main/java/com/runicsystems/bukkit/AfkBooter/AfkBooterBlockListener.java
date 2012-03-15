package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * AfkBooter block listener
 *
 * @author neromir, morganm
 */
public class AfkBooterBlockListener implements Listener
{
    private final AfkBooter plugin;

    public AfkBooterBlockListener(final AfkBooter plugin)
    {
        this.plugin = plugin;
    }

    public void onBlockPlace(BlockPlaceEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    public void onBlockBreak(BlockBreakEvent event)
    {
        if(event.getPlayer() != null)
            plugin.recordPlayerActivity(event.getPlayer().getName());
    }
}
