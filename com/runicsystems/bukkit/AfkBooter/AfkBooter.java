package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private ConcurrentHashMap<String, Long> lastPlayerActivity = new ConcurrentHashMap<String, Long>();
    private List<String> playersToKick = new LinkedList<String>();

    private int kickTimeout;                 // in seconds
    private String kickMessage;
    private int timeoutCheckInterval;        // in seconds
    private String kickBroadcastMessage;

    private Logger logger;

    public AfkBooter()
    {
        super();

        settings = new AfkBooterSettings(this);
        kickTimeout = settings.DEFAULT_KICK_TIMEOUT;
        kickMessage = settings.DEFAULT_KICK_MESSAGE;
        timeoutCheckInterval = settings.DEFAULT_TIMEOUT_CHECK;
        kickBroadcastMessage = settings.DEFAULT_KICK_BROADCAST;
    }

    public void onEnable()
    {
        logger = Logger.getLogger("Minecraft");

        settings.init(getDataFolder());

        kickMessage = settings.getKickMessage();
        kickTimeout = settings.getKickTimeout();
        timeoutCheckInterval = settings.getTimeoutCheckInterval();
        kickBroadcastMessage = settings.getKickBroadcastMessage();

        // Start up the threaded timer. Initializing it here allows us to restart
        // it later on re-enable.
        threadedTimer = new AfkBooterTimer(this, timeoutCheckInterval * 1000);
        threadedTimer.setAborted(false);
        threadedTimer.start();

        // Get and output some info about the plugin for the log at startup
        PluginDescriptionFile pdfFile = this.getDescription();
        log("version " + pdfFile.getVersion() + " is loaded.", Level.INFO);
        String exemptPlayers = "";
        List<String> exemptPlayerList = settings.getExemptPlayers();
        for(int i = 0; i < exemptPlayerList.size(); i++)
        {
            exemptPlayers += exemptPlayerList.get(i);

            if(i < exemptPlayerList.size() - 1)
                exemptPlayers += ", ";
        }
        log("Kick timeout " + kickTimeout + " sec, exempt players: " + exemptPlayers, Level.INFO);

        // Register our events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.INVENTORY_OPEN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_CHAT, playerListener, Priority.Monitor, this);
    }

    public void onDisable()
    {
        lastPlayerActivity.clear();

        threadedTimer.setAborted(true);
        // NOTE: All registered events are automatically unregistered when a plugin is disabled

        log("Shutting down AfkBooter.", Level.INFO);
    }

    public void kickAfkPlayers()
    {
        // Get the current time and then iterate across all tracked players.
        long now = System.currentTimeMillis();
        if(!playersToKick.isEmpty())
        {
            log("Attempting to re-check for players to kick too soon. Please set interval higher.", Level.INFO);
            return;
        }

        if(lastPlayerActivity.size() < 1)
            log("No players in tracking map.", Level.FINEST);

        Set<Map.Entry<String, Long>> trackedPlayers = lastPlayerActivity.entrySet();
        for(Map.Entry<String, Long> activityEntry : trackedPlayers)
        {
            // If player's last active time + the kick allowance time is earlier than
            // the current time, boot them-- they've been idle too long.
            if((activityEntry.getValue() + (kickTimeout * 1000)) < now)
                playersToKick.add(activityEntry.getKey());
        }

        if(!playersToKick.isEmpty())
            getServer().getScheduler().scheduleSyncDelayedTask(this, new PlayerKicker());
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
        }

        return false;
    }

    private boolean handleKickTimeoutCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() != 1)
            return false;

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to change the kick timeout.");
            return false;
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

        threadedTimer.setTimeToSleep(newKickTimeout);
        settings.saveSettings(getDataFolder());

        return true;
    }

    private boolean handleKickMessageCommand(CommandSender sender, ArrayList<String> args)
    {
        if(args.size() < 1)
            return false;

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to change the kick message.");
            return false;
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

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to change the kick broadcast.");
            return false;
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

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to add exempt players.");
            return false;
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

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to remove exempt players.");
            return false;
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

        if(!sender.isOp())
        {
            sender.sendMessage("You do not have permission to see exempt players.");
            return false;
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

    public void log(String logMessage, Level logLevel)
    {
        logger.log(logLevel, "[AfkBooter] " + logMessage);
    }

    public void recordPlayerActivity(String playerName)
    {
        // Don't even record them if their name is on the exempt list.
        if(settings.getExemptPlayers().contains(playerName))
            return;

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
            for(String playerName : playersToKick)
            {
                Player player = getServer().getPlayer(playerName);
                if(player != null)
                {
                    log("Kicking player " + playerName, Level.INFO);
                    // Stop tracking them, since we're booting them.
                    lastPlayerActivity.remove(playerName);
                    player.kickPlayer(kickMessage);

                    // Don't output the broadcast message if it's set to empty or null.
                    if(kickBroadcastMessage != null && !kickBroadcastMessage.isEmpty())
                        getServer().broadcastMessage(playerName + " " + kickBroadcastMessage);
                }
            }

            // Wipe out the players we just kicked because they shouldn't be in the to-kick list anyway.
            playersToKick.clear();
        }
    }

    /**
     * The two methods below are for the old method of gathering settings which attempted to use the getConfiguration()
     * method of the JavaPlugin class.  Unfortunately I was never able to get this to work, so they languish here for
     * now.
     *
     * @param dataFolder The config folder location.
     */
    private void firstRunSettings(File dataFolder)
    {
        log("Configuration file not found, creating new one.", Level.INFO);
        if(!dataFolder.mkdirs())
            log("Failed creating settings directory!", Level.SEVERE);

        File configFile = new File(dataFolder, "config.yml");
        try
        {
            if(!configFile.createNewFile())
                throw new IOException("Failed file creation");
        }
        catch(IOException e)
        {
            log("Could not create config file!", Level.SEVERE);
        }

        writeSettings(configFile);
    }

    private void writeSettings(File configFile)
    {
        FileWriter fileWriter = null;
        BufferedWriter bufferWriter = null;
        try
        {
            if(!configFile.exists())
                configFile.createNewFile();

            fileWriter = new FileWriter(configFile);
            bufferWriter = new BufferedWriter(fileWriter);
            bufferWriter.append("kickTimeout: ");
            bufferWriter.append(((Integer) kickTimeout).toString());
            bufferWriter.append("  # Amount of time (seconds) to allow a person to be idle before kicking them.");
            bufferWriter.newLine();

            bufferWriter.append("kickMesssage: ");
            bufferWriter.append(kickMessage);
            bufferWriter.append("  # Message to display to player when they are kicked.");
            bufferWriter.newLine();

            bufferWriter.append("timeoutCheckInterval: ");
            bufferWriter.append(((Integer) timeoutCheckInterval).toString());
            bufferWriter.append("  # Amount of time (seconds) between checks for idlers to be kicked.");
            bufferWriter.newLine();
            bufferWriter.flush();
        }
        catch(IOException e)
        {
            log("Caught exception while writing settings to file: ", Level.SEVERE);
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if(bufferWriter != null)
                {
                    bufferWriter.flush();
                    bufferWriter.close();
                }

                if(fileWriter != null)
                    fileWriter.close();
            }
            catch(IOException e)
            {
                log("IO Exception writing file: " + configFile.getName(), Level.SEVERE);
            }
        }
    }
}
