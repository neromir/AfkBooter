package com.runicsystems.bukkit.AfkBooter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.morganm.mBukkitLib.JarUtils;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

/**
 * AfkBooter for Bukkit
 *
 * @author neromir
 */
public class AfkBooter extends JavaPlugin
{
    private final AfkBooterPlayerListener playerListener = new AfkBooterPlayerListener(this);
    private final AfkBooterBlockListener blockListener = new AfkBooterBlockListener(this);
    // Defaults to check every 10 seconds.
    private AfkBooterTimer threadedTimer;
    private AfkBooterSettings settings;
    private AfkBooterEventCatalog eventCatalog;

    private final String PERMISSIONS_CONFIG = "afkbooter.config";
    private final String PERMISSIONS_EXEMPT = "afkbooter.exempt";
    private final Object playersToKickLock = new Object();

    private ConcurrentHashMap<String, Long> lastPlayerActivity = new ConcurrentHashMap<String, Long>();
    private List<String> playersToKick = new LinkedList<String>();

    private long lastKickAttempt;
    private Logger logger;
    private JarUtils jarUtils;
	private int buildNumber = -1;
//    private MovementTracker movementTracker;

    public static PermissionHandler permissions;

    public AfkBooter()
    {
        super();

        settings = new AfkBooterSettings(this);
        eventCatalog = new AfkBooterEventCatalog(this);
    }

    public void onEnable()
    {
        logger = Logger.getLogger("Minecraft");
        setupPermissions();

    	jarUtils = new JarUtils(this, getLogger(), getFile());
		buildNumber = jarUtils.getBuildNumber();
		
        settings.init(getDataFolder());
        eventCatalog.initialize(settings);
        lastKickAttempt = System.currentTimeMillis();

        // Start up the threaded timer. Initializing it here allows us to restart
        // it later on re-enable.
        threadedTimer = new AfkBooterTimer(this, settings.getTimeoutCheckInterval() * 1000);
        threadedTimer.setAborted(false);
        threadedTimer.start();

        String exemptPlayers = "";
        List<String> exemptPlayerList = settings.getExemptPlayers();
        for(int i = 0; i < exemptPlayerList.size(); i++)
        {
            exemptPlayers += exemptPlayerList.get(i);

            if(i < exemptPlayerList.size() - 1)
                exemptPlayers += ", ";
        }
        log("Kick timeout " + settings.getKickTimeout() + " sec, exempt players: " + exemptPlayers, Level.INFO);

        getServer().getPluginManager().registerEvents(playerListener, this);
        eventCatalog.registerEvents();

//        movementTracker = new MovementTracker(this);
        
		// set movement check to 1/4th of kick timeout (since this is based on
		// ticks, while original kick thread is not)
//		getServer().getScheduler().scheduleAsyncRepeatingTask(this, movementTracker, 200, (settings.getKickTimeout()*5) - 3);
        
        // Get and output some info about the plugin for the log at startup
        PluginDescriptionFile pdfFile = this.getDescription();
        log("Version " + pdfFile.getVersion() + ", build number "+buildNumber+" is enabled.", Level.INFO);
    }

    public void onDisable()
    {
        lastPlayerActivity.clear();
        synchronized(playersToKickLock)
        {
            playersToKick.clear();
        }

        if(threadedTimer != null)
        {
            threadedTimer.setAborted(true);
            threadedTimer = null;
        }
        // NOTE: All registered events are automatically unregistered when a plugin is disabled

        getServer().getScheduler().cancelTasks(this);

        PluginDescriptionFile pdfFile = this.getDescription();
        log("Version " + pdfFile.getVersion() + ", build number "+buildNumber+" is disabled.", Level.INFO);
    }
    
    public AfkBooterPlayerListener getPlayerListener() { return playerListener; }
    public AfkBooterBlockListener getBlockListener() { return blockListener; }

    private void setupPermissions()
    {
        Plugin test = this.getServer().getPluginManager().getPlugin("Permissions");

        if(permissions == null)
        {
            if(test != null)
            {
                log("Permissions detected, attaching.", Level.INFO);
                permissions = ((Permissions) test).getHandler();
            }
            else
                log("Permissions not detected, defaulting to OP permissions.", Level.INFO);
        }
    }

    private final Map<String, Map<String, PermissionResult>> permCache = new HashMap<String, Map<String, PermissionResult>>();
    private final int MAX_CACHE_TIME = 60000;	// 1 minute
    private boolean hasPermission(Player player, String permission)
    {
        if(player == null || permissions == null)
            return false;

        final String playerName = player.getName();
        
        // because permissions are checked on EVERY event, including PLAYER_MOVE (looking
        // for exempt permissions) and some permission systems are notoriously slow, we
        // cache permission results for a performance boost.
        Map<String, PermissionResult> cachedPlayerPermissions = permCache.get(playerName);
        if(  cachedPlayerPermissions == null ) {
        	cachedPlayerPermissions = new HashMap<String, PermissionResult>();
        	permCache.put(playerName, cachedPlayerPermissions);
        }
        
        PermissionResult result = cachedPlayerPermissions.get(permission);
        if( result == null ) {
        	result = new PermissionResult();
        	cachedPlayerPermissions.put(permission, result);
        }
        
        // if the last cached result has exceeded cache timeout, check again
        if( (System.currentTimeMillis() - result.timestamp) > MAX_CACHE_TIME ) {
        	result.timestamp = System.currentTimeMillis();
        	result.result = permissions.has(player,  permission);
        }

        return result.result;
    }
    private class PermissionResult {
    	long timestamp=0;
    	boolean result = false;		// fail closed
    }

    public void kickAfkPlayers()
    {
        // If we haven't reached the player count threshold, don't even try to kick players.
        if(getServer().getOnlinePlayers().length < settings.getPlayerCountThreshold())
            return;

        final long FAILED_KICK_LENGTH = 60000;
        // Get the current time and then iterate across all tracked players.
        long now = System.currentTimeMillis();

        // Checks when the last time was we set the PlayerKicker task.  If it has been longer than FAILED_KICK_LENGTH
        // then we've had players to be kicked sitting in the list for the kicker task to be activated for that long.
        // Since this length should be at least 60 seconds, that's way too long and indicates a problem with the task,
        // meaning we should start over.
        if(settings.isKickIdlers())
        {
            synchronized(playersToKickLock)
            {
            	// manually run movement tracker, just to be sure movement records are up-to-date.
            	// it is synchronized and protected from multiple runs, so this is a thread-safe call.
//            	movementTracker.checkPlayerMovements();
            	
                if(lastKickAttempt + FAILED_KICK_LENGTH < now && !playersToKick.isEmpty())
                {
                    // If we've reached this timeout, log a severe warning and clear the list of idle players.  We should be
                    // back at normal state then, ready to resume normal operation.
                    log("Failed to kick idle players. Passed timeout (" + FAILED_KICK_LENGTH / 1000 + " sec) after found idlers.",
                        Level.SEVERE);
                    playersToKick.clear();
                    lastKickAttempt = System.currentTimeMillis();
                }
                else if(!playersToKick.isEmpty())
                {
                    log("Player count: " + playersToKick.size() + ". Attempting to re-check for players to kick too soon. Please set interval higher.", Level.INFO);
                    return;
                }
            }
        }

        if(lastPlayerActivity.size() < 1)
            log("No players in tracking map.", Level.FINEST);

        Set<Map.Entry<String, Long>> trackedPlayers = lastPlayerActivity.entrySet();
        for(Map.Entry<String, Long> activityEntry : trackedPlayers)
        {
            // If player's last active time + the kick allowance time is earlier than
            // the current time, boot them-- they've been idle too long.
            if((activityEntry.getValue() + (settings.getKickTimeout() * 1000)) < now)
            {
                synchronized(playersToKickLock)
                {
                    // Make sure we don't add them to the list of players to be kicked if they're already on it.
                    if(playersToKick.contains(activityEntry.getKey()))
                        continue;

                    if(!settings.isKickIdlers())
                    {
                        // If this player is in the AFK state, then we want to check if the config allows us to tell
                        // the server to ignore them for sleeping participation.
                        if(settings.isUseFauxSleep())
                            getServer().getPlayer(activityEntry.getKey()).setSleepingIgnored(true);

                        getServer().broadcastMessage(ChatColor.YELLOW + activityEntry.getKey() + " " + settings.getKickBroadcastMessage());
                    }

                    playersToKick.add(activityEntry.getKey());
                }
            }
        }

        // If we're set to not kick idlers, don't schedule the task.
        if(!settings.isKickIdlers())
            return;

        synchronized(playersToKickLock)
        {
            if(!playersToKick.isEmpty())
            {
                lastKickAttempt = System.currentTimeMillis();
                getServer().getScheduler().scheduleSyncDelayedTask(this, new PlayerKicker());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {
        String commandName = cmd.getName().toLowerCase();

        if(commandName.equals("afkbooter") && args.length > 0)
        {
            String subCommand = args[0];
            subCommand = subCommand.toLowerCase();
            ArrayList<String> subCommandArgs = new ArrayList<String>();
            subCommandArgs.addAll(Arrays.asList(args).subList(1, args.length));

            if(subCommand.equals("kicktimeout"))
                return handleKickTimeoutCommand(sender, subCommandArgs);
            else if(subCommand.equals("kickmessage"))
                return handleKickMessageCommand(sender, subCommandArgs);
            else if(subCommand.equals("kickbroadcast"))
                return handleKickBroadcastCommand(sender, subCommandArgs);
            else if(subCommand.equals("addexempt"))
                return handleAddExemptPlayerCommand(sender, subCommandArgs);
            else if(subCommand.equals("removeexempt"))
                return handleRemoveExemptPlayerCommand(sender, subCommandArgs);
            else if(subCommand.equals("listexempt"))
                return handleListExemptCommand(sender, subCommandArgs);
            else if(subCommand.equals("playercount"))
                return handlePlayerCountCommand(sender, subCommandArgs);
            else if(subCommand.equals("usejumpignore"))
                return handleJumpIgnoreCommand(sender, subCommandArgs);
            else if(subCommand.equals("kickidlers"))
                return handleKickIdlersCommand(sender, subCommandArgs);
            else if(subCommand.equals("ignorevehicles"))
                return handleIgnoreVehiclesCommand(sender, subCommandArgs);
            else if(subCommand.equals("usefauxsleep"))
                return handleUseFauxSleepCommand(sender, subCommandArgs);
            else if(subCommand.equals("blockidleitems"))
                return handleBlockIdleItemsCommand(sender, subCommandArgs);
            else if(subCommand.equals("list"))
                return handleListAfkPlayersCommand(sender, subCommandArgs);
        }

        return false;
    }

    private boolean isPlayer(CommandSender sender)
    {
        return sender instanceof Player;
    }

    private boolean handleKickTimeoutCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change the kick timeout.");
            return true;
        }

        int newKickTimeout;
        try
        {
            newKickTimeout = Integer.parseInt(args.get(0));
        }
        catch(NumberFormatException e)
        {
            sender.sendMessage(ChatColor.RED + "'" + args.get(0) + "' is not a number.");
            return false;
        }

        settings.setKickTimeout(newKickTimeout);
        sender.sendMessage("Kick timeout changed to " + newKickTimeout + " sec.");
        log("Kick timeout changed to " + newKickTimeout + " sec.", Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleKickMessageCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() < 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change the kick message.");
            return true;
        }

        String newKickMessage = "";
        for(String messagePart : args)
        {
            newKickMessage += messagePart + " ";
        }

        newKickMessage = newKickMessage.trim();
        settings.setKickMessage(newKickMessage);
        sender.sendMessage("Kick message changed to \"" + newKickMessage + "\"");
        log("Kick message changed to \"" + newKickMessage + "\"", Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleKickBroadcastCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() < 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change the kick broadcast.");
            return true;
        }

        String newKickBroadcast = "";
        for(String messagePart : args)
        {
            newKickBroadcast += messagePart + " ";
        }

        newKickBroadcast = newKickBroadcast.trim();
        settings.setKickBroadcastMessage(newKickBroadcast);
        sender.sendMessage("Kick broadcast message changed to \"" + newKickBroadcast + "\"");
        log("Kick broadcast message changed to \"" + newKickBroadcast + "\"", Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleAddExemptPlayerCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to add exempt players.");
            return true;
        }

        String newExemptPlayer = args.get(0).trim();

        if(settings.getExemptPlayers().contains(newExemptPlayer))
        {
            sender.sendMessage("Player " + newExemptPlayer + " is already on the exempt list.");
            return true;
        }

        settings.addExemptPlayer(newExemptPlayer);
        if(lastPlayerActivity.contains(newExemptPlayer))
            lastPlayerActivity.remove(newExemptPlayer);
        sender.sendMessage("Added player " + newExemptPlayer + " to the exempt list.");
        log("Added player " + newExemptPlayer + " to the exempt list.", Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleRemoveExemptPlayerCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to remove exempt players.");
            return true;
        }

        String playerToRemove = args.get(0).trim();

        if(!settings.getExemptPlayers().contains(playerToRemove))
        {
            sender.sendMessage("Player " + playerToRemove + " is not on the exempt list.");
            return true;
        }

        settings.removeExemptPlayer(playerToRemove);
        sender.sendMessage("Removed player " + playerToRemove + " from the exempt list.");
        log("Removed player " + playerToRemove + " from the exempt list.", Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleListExemptCommand(CommandSender sender, ArrayList<String> args)
    {
        if(!args.isEmpty())
        {
            return false;
        }

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to see exempt players.");
            return true;
        }

        String playerList = "";
        for(int i = 0; i < settings.getExemptPlayers().size(); i++)
        {
            playerList += settings.getExemptPlayers().get(i);

            if(i < settings.getExemptPlayers().size() - 1)
                playerList += ", ";
        }

        sender.sendMessage("Exempt players: " + playerList);

        return true;
    }

    private boolean handlePlayerCountCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change the kick timeout.");
            return true;
        }

        int newPlayerThreshold;
        try
        {
            newPlayerThreshold = Integer.parseInt(args.get(0));
        }
        catch(NumberFormatException e)
        {
            sender.sendMessage(ChatColor.RED + "'" + args.get(0) + "' is not a number.");
            return false;
        }

        settings.setPlayerCountThreshold(newPlayerThreshold);
        sender.sendMessage("Player count threshold changed to " + newPlayerThreshold);
        log("Player count threshold changed to " + newPlayerThreshold, Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleJumpIgnoreCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change the jump ignore usage.");
            return true;
        }

        boolean useJumpIgnore = Boolean.parseBoolean(args.get(0));

        settings.setUseJumpIgnore(useJumpIgnore);
        sender.sendMessage("Use of jump ignore set to " + ((Boolean) useJumpIgnore).toString());
        log("Use of jump ignore set to " + ((Boolean) useJumpIgnore).toString(), Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleKickIdlersCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change whether or not to kick idlers.");
            return true;
        }

        boolean kickIdlers = Boolean.parseBoolean(args.get(0));

        settings.setKickIdlers(kickIdlers);
        sender.sendMessage("Kicking idlers set to " + ((Boolean) kickIdlers).toString());
        log("Kicking idlers set to " + ((Boolean) kickIdlers).toString(), Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleIgnoreVehiclesCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change whether or not to ignore vehicle movement.");
            return true;
        }

        boolean ignoreVehicles = Boolean.parseBoolean(args.get(0));

        settings.setIgnoreVehicles(ignoreVehicles);
        sender.sendMessage("Ignore vehicle movement set to " + ((Boolean) ignoreVehicles).toString());
        log("Ignore vehicle movement set to " + ((Boolean) ignoreVehicles).toString(), Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleUseFauxSleepCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change whether or not to use faux sleep.");
            return true;
        }

        boolean useFauxSleep = Boolean.parseBoolean(args.get(0));

        settings.setUseFauxSleep(useFauxSleep);
        sender.sendMessage("Use faux sleep set to " + ((Boolean) useFauxSleep).toString());
        log("Use faux sleep set to " + ((Boolean) useFauxSleep).toString(), Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleBlockIdleItemsCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to change whether or not to block items for idlers.");
            return true;
        }

        boolean blockIdleItems = Boolean.parseBoolean(args.get(0));

        settings.setBlockItems(blockIdleItems);
//        if(blockIdleItems)
//            getServer().getPluginManager().registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Event.Priority.High, this);

        sender.sendMessage("Block idler items set to " + ((Boolean) blockIdleItems).toString());
        log("Block idler items set to " + ((Boolean) blockIdleItems).toString(), Level.INFO);

        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleListAfkPlayersCommand(CommandSender sender, ArrayList<String> args)
    {
        if(!args.isEmpty())
            return false;

        if(!sender.isOp() && !(isPlayer(sender) && hasPermission((Player) sender, PERMISSIONS_CONFIG)))
        {
            sender.sendMessage("You do not have permission to view the list of idle players.");
            return true;
        }

        StringBuilder afkList = new StringBuilder();

        synchronized(playersToKickLock)
        {
            for(String playerName : playersToKick)
            {
                afkList.append(playerName);
                afkList.append(", ");
            }
        }

        // Chop off the last comma and space.
        if(afkList.length() > 2)
            afkList.setLength(afkList.length() - 2);

        sender.sendMessage("AFK players: " + afkList.toString());

        return true;
    }

    public void log(String logMessage, Level logLevel)
    {
        logger.log(logLevel, "[AfkBooter] " + logMessage);
    }

    public synchronized void recordPlayerActivity(String playerName)
    {
        // Don't even record them if their name is on the exempt list.
        if(settings.getExemptPlayers().contains(playerName) || hasPermission(getServer().getPlayer(playerName), PERMISSIONS_EXEMPT))
            return;

        if(!settings.isKickIdlers())
        {
            synchronized(playersToKickLock)
            {
                if(playersToKick.contains(playerName))
                {
                    // Don't want to ignore this player for sleeping calculations any longer.
                    if(settings.isUseFauxSleep())
                        getServer().getPlayer(playerName).setSleepingIgnored(false);

                    getServer().broadcastMessage(ChatColor.YELLOW + playerName + " no longer idle.");
                    playersToKick.remove(playerName);
                }
            }
        }

        long now = System.currentTimeMillis();
        lastPlayerActivity.put(playerName, now);
    }

    public void stopTrackingPlayer(String playerName)
    {
        lastPlayerActivity.remove(playerName);
    }

    /**
     * Used for the delayed task of kicking players for thread safety.
     */
    public class PlayerKicker implements Runnable
    {
        public void run()
        {
            synchronized(playersToKickLock)
            {
                for(String playerName : playersToKick)
                {
                    Player player = getServer().getPlayer(playerName);
                    if(player != null)
                    {
                        log("Kicking player " + playerName, Level.INFO);
                        // Stop tracking them, since we're booting them.
                        lastPlayerActivity.remove(playerName);
                        if(player.isOnline())
                            player.kickPlayer(settings.getKickMessage());
                        else
                            continue;

                        // Don't output the broadcast message if it's set to empty or null.
                        if(settings.getKickBroadcastMessage() != null && !settings.getKickBroadcastMessage().isEmpty())
                            getServer().broadcastMessage(ChatColor.YELLOW + playerName + " " + settings.getKickBroadcastMessage());
                    }
                }

                // Wipe out the players we just kicked because they shouldn't be in the to-kick list anyway.
                playersToKick.clear();
            }
        }
    }

    public AfkBooterSettings getSettings()
    {
        return settings;
    }

    public AfkBooterEventCatalog getEventCatalog()
    {
        return eventCatalog;
    }

    public boolean isPlayerIdle(String playerName)
    {
        // Check if we're kicking idlers, if we are, they're obviously not idle.
        if(!settings.isKickIdlers())
        {
            synchronized(playersToKickLock)
            {
                return playersToKick.contains(playerName);
            }
        }

        return false;
    }
}
