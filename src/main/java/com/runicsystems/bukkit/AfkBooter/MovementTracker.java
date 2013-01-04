/**
 * 
 */
package com.runicsystems.bukkit.AfkBooter;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Rather than hooking the expensive PLAYER_MOVE and forcing the Bukkit overhead associated with
 * producing and processing those events on every PLAYER_MOVE, we instead schedule a process to
 * check player locations on a regular schedule, which is a lot less work to accomplish the
 * same effect.
 * 
 * @author morganm
 *
 */
public class MovementTracker implements Runnable {
	private final AfkBooter plugin;
	private final HashMap<Player,Location> positions = new HashMap<Player,Location>(20);
	private boolean isRunning = false;
	
	public MovementTracker(AfkBooter plugin) {
		this.plugin = plugin;
	}
	
	public void playerLogout(Player p) {
		positions.remove(p);
	}
	
	public void checkPlayerMovements() {
		if( isRunning )
			return;
		
		synchronized(MovementTracker.class) {
			try {
				isRunning = true;
				
				Player[] players = plugin.getServer().getOnlinePlayers();
				for(Player p : players) {
					Location curPos = p.getLocation();
					Location prevPos = positions.get(p);
					if( prevPos == null ) {
						positions.put(p, curPos);
						continue;
					}
					positions.put(p, curPos);

					String curWorld = curPos.getWorld().getName();
					String prevWorld = prevPos.getWorld().getName();

					if( !curWorld.equals(prevWorld)
							|| curPos.getBlockX() != prevPos.getBlockX()
							|| curPos.getBlockY() != prevPos.getBlockY()
							|| curPos.getBlockZ() != prevPos.getBlockZ() ) {
						// player moved
						plugin.recordPlayerActivity(p.getName());
					}
				}
			}
			finally {
				isRunning = false;
			}
		}
		
	}
	
	public void run() {
		checkPlayerMovements();
	}
}