package com.runicsystems.bukkit.AfkBooter;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AfkBooter for Bukkit
 *
 * @author neromir
 */
public class AfkBooter extends JavaPlugin {
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

    public AfkBooter(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin,
                     ClassLoader cLoader) {
        super(pluginLoader, instance, desc, folder, plugin, cLoader);

        settings = new AfkBooterSettings(this);
        kickTimeout = settings.DEFAULT_KICK_TIMEOUT;
        kickMessage = settings.DEFAULT_KICK_MESSAGE;
        timeoutCheckInterval = settings.DEFAULT_TIMEOUT_CHECK;
        kickBroadcastMessage = settings.DEFAULT_KICK_BROADCAST;

        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
    }

    public void onEnable() {
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
        for (int i = 0; i < exemptPlayerList.size(); i++) {
            exemptPlayers += exemptPlayerList.get(i);

            if (i < exemptPlayerList.size() - 1)
                exemptPlayers += ", ";
        }
        log("Kick timeout " + kickTimeout + " sec, exempt players: " + exemptPlayers, Level.INFO);

        // Register our events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.INVENTORY_OPEN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_COMMAND, playerListener, Priority.Monitor, this);
    }

    public void onDisable() {
        lastPlayerActivity.clear();

        threadedTimer.setAborted(true);
        // NOTE: All registered events are automatically unregistered when a plugin is disabled

        settings.saveSettings(getDataFolder());

        log("Shutting down AfkBooter.", Level.INFO);
    }

    public void kickAfkPlayers() {
        // Get the current time and then iterate across all tracked players.
        long now = System.currentTimeMillis();
        if (!playersToKick.isEmpty()) {
            log("Attempting to re-check for players to kick too soon. Please set interval higher.", Level.INFO);
            return;
        }

        if (lastPlayerActivity.size() < 1)
            log("No players in tracking map.", Level.FINEST);

        Set<Map.Entry<String, Long>> trackedPlayers = lastPlayerActivity.entrySet();
        for (Map.Entry<String, Long> activityEntry : trackedPlayers) {
            // If player's last active time + the kick allowance time is earlier than
            // the current time, boot them-- they've been idle too long.
            if ((activityEntry.getValue() + (kickTimeout * 1000)) < now)
                playersToKick.add(activityEntry.getKey());
        }

        if (!playersToKick.isEmpty())
            getServer().getScheduler().scheduleSyncDelayedTask(this, new PlayerKicker());
    }

    public void log(String logMessage, Level logLevel) {
        logger.log(logLevel, "[AfkBooter] " + logMessage);
    }

    public void recordPlayerActivity(String playerName) {
        // Don't even record them if their name is on the exempt list.
        if (settings.getExemptPlayers().contains(playerName))
            return;

        long now = System.currentTimeMillis();
        lastPlayerActivity.put(playerName, now);
    }

    public void stopTrackingPlayer(String playerName) {
        lastPlayerActivity.remove(playerName);
    }

    /**
     * Used for the delayed task of kicking players for thread safety.
     */
    public class PlayerKicker implements Runnable {
        public void run() {
            for (String playerName : playersToKick) {
                Player player = getServer().getPlayer(playerName);
                if (player != null) {
                    log("Kicking player " + playerName, Level.INFO);
                    // Stop tracking them, since we're booting them.
                    lastPlayerActivity.remove(playerName);
                    player.kickPlayer(kickMessage);

                    // Don't output the broadcast message if it's set to empty or null.
                    if (kickBroadcastMessage != null && !kickBroadcastMessage.isEmpty())
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
    private void firstRunSettings(File dataFolder) {
        log("Configuration file not found, creating new one.", Level.INFO);
        if (!dataFolder.mkdirs())
            log("Failed creating settings directory!", Level.SEVERE);

        File configFile = new File(dataFolder, "config.yml");
        try {
            if (!configFile.createNewFile())
                throw new IOException("Failed file creation");
        }
        catch (IOException e) {
            log("Could not create config file!", Level.SEVERE);
        }

        writeSettings(configFile);
    }

    private void writeSettings(File configFile) {
        FileWriter fileWriter = null;
        BufferedWriter bufferWriter = null;
        try {
            if (!configFile.exists())
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
        catch (IOException e) {
            log("Caught exception while writing settings to file: ", Level.SEVERE);
            e.printStackTrace();
        }
        finally {
            try {
                if (bufferWriter != null) {
                    bufferWriter.flush();
                    bufferWriter.close();
                }

                if (fileWriter != null)
                    fileWriter.close();
            }
            catch (IOException e) {
                log("IO Exception writing file: " + configFile.getName(), Level.SEVERE);
            }
        }
    }
}
