package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.event.block.BlockListener;

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
}
