package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * AfkBooter block listener
 *
 * @author neromir
 */
public class AfkBooterBlockListener extends BlockListener
{
    private final AfkBooter plugin;

    public AfkBooterBlockListener(final AfkBooter plugin)
    {
        this.plugin = plugin;
    }

    //put all Block related code here

    @Override
    public void onBlockPlace(BlockPlaceEvent event)
    {
        plugin.recordPlayerActivity(event.getPlayer().getName());
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event)
    {
        if(event.getPlayer() != null)
            plugin.recordPlayerActivity(event.getPlayer().getName());
    }
}
