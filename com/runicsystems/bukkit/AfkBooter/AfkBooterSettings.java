package com.runicsystems.bukkit.AfkBooter;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

/**
 * @author neromir
 */
public class AfkBooterSettings
{
    private final String PROP_KICK_MESSAGE = "kick-message";
    private final String PROP_KICK_BROADCAST = "kick-broadcast";
    private final String PROP_KICK_TIMEOUT = "kick-timeout";
    private final String PROP_TIMEOUT_CHECK = "timeout-check-interval";
    private final String PROP_EXEMPT_PLAYERS = "exempt-players";
    private final String PROP_PLAYER_COUNT = "player-count-threshold";
    private final String PROP_USE_JUMP_IGNORE = "use-jump-ignoring";
    private final String PROP_KICK_ILDERS = "kick-idlers";
    private final String PROP_IGNORE_VEHICLES = "ignore-vehicle-movement";
    private final String PROP_USE_FAUX_SLEEP = "use-faux-sleep";
    private final String CONFIG_FILE = "afkbooter.properties";

    private final String PROP_MOVE_LISTEN = "listen-move";
    private final String PROP_INVENTORY_OPEN_LISTEN = "listen-inventory-open";
    private final String PROP_CHAT_LISTEN = "listen-chat";
    private final String PROP_BLOCK_PLACE_LISTEN = "listen-block-place";
    private final String PROP_BLOCK_BREAK_LISTEN = "listen-block-break";
    private final String PROP_DROP_ITEM_LISTEN = "listen-player-drop-item";

    public final int DEFAULT_KICK_TIMEOUT = 30;
    public final String DEFAULT_KICK_MESSAGE = "Kicked for idling.";
    public final int DEFAULT_TIMEOUT_CHECK = 10;
    public final String DEFAULT_KICK_BROADCAST = "kicked for idling.";
    public final String DEFAULT_EXEMPT_PLAYERS = "name1,name2,name3";
    public final int DEFAULT_PLAYER_COUNT = 0;
    public final boolean DEFAULT_USE_JUMP_IGNORE = false;
    public final boolean DEFAULT_IGNORE_VEHICLES = false;
    public final boolean DEFAULT_USE_FAUX_SLEEP = true;
    public final boolean DEFAULT_KICK_IDLERS = true;

    public final boolean DEFAULT_MOVE_LISTEN = true;
    public final boolean DEFAULT_INVENTORY_OPEN_LISTEN = true;
    public final boolean DEFAULT_CHAT_LISTEN = true;
    public final boolean DEFAULT_BLOCK_PLACE_LISTEN = false;
    public final boolean DEFAULT_BLOCK_BREAK_LISTEN = false;
    public final boolean DEFAULT_DROP_ITEM_LISTEN = false;

    private List<String> exemptPlayers;
    private int kickTimeout;
    private String kickMessage;
    private int timeoutCheckInterval;
    private String kickBroadcastMessage;
    private int playerCountThreshold;
    private boolean useJumpIgnore;
    private boolean ignoreVehicles;
    private boolean useFauxSleep;
    private boolean kickIdlers;

    private boolean moveListen;
    private boolean inventoryOpenListen;
    private boolean chatListen;
    private boolean blockPlaceListen;
    private boolean blockBreakListen;
    private boolean dropItemListen;

    private AfkBooter plugin;

    public AfkBooterSettings(AfkBooter plugin)
    {
        this.plugin = plugin;
        exemptPlayers = new LinkedList<String>();
        kickTimeout = DEFAULT_KICK_TIMEOUT;
        kickMessage = DEFAULT_KICK_MESSAGE;
        timeoutCheckInterval = DEFAULT_TIMEOUT_CHECK;
        kickBroadcastMessage = DEFAULT_KICK_BROADCAST;
        playerCountThreshold = DEFAULT_PLAYER_COUNT;
        useJumpIgnore = DEFAULT_USE_JUMP_IGNORE;
        kickIdlers = DEFAULT_KICK_IDLERS;
        ignoreVehicles = DEFAULT_IGNORE_VEHICLES;
        useFauxSleep = DEFAULT_USE_FAUX_SLEEP;

        moveListen = DEFAULT_MOVE_LISTEN;
        inventoryOpenListen = DEFAULT_INVENTORY_OPEN_LISTEN;
        chatListen = DEFAULT_CHAT_LISTEN;
        blockPlaceListen = DEFAULT_BLOCK_PLACE_LISTEN;
        blockBreakListen = DEFAULT_BLOCK_BREAK_LISTEN;
        dropItemListen = DEFAULT_DROP_ITEM_LISTEN;
    }

    public void init(File configFolder)
    {
        // Create the config folder if it doesn't exist.
        if(!configFolder.exists())
        {
            plugin.log("Config folder not found, creating.", Level.INFO);
            if(!configFolder.mkdirs())
                plugin.log("Unable to create config folder.", Level.SEVERE);
        }

        // Create the config file with a set of default settings if it doesn't already exist.
        File configFile = new File(configFolder, CONFIG_FILE);
        if(!configFile.exists())
        {
            plugin.log("Config file is missing, creating.", Level.INFO);
            writeConfigFile(configFile, true);
        }

        // Once that has been done (if necessary) then load the config file.
        loadPropertiesFile(configFolder);
    }

    public void saveSettings(File configFolder)
    {
        File configFile = new File(configFolder, CONFIG_FILE);
        writeConfigFile(configFile);
    }

    private void writeConfigFile(File configFile)
    {
        writeConfigFile(configFile, false);
    }

    private void writeConfigFile(File configFile, boolean firstRun)
    {
        try
        {
            if(!configFile.exists() && !configFile.createNewFile())
                plugin.log("Unable to create config file.", Level.SEVERE);

            Properties configProps = new Properties();

            // Formulate the exemption list based on whether or not we have settings.
            String exemptList = "";
            if(!exemptPlayers.isEmpty())
            {
                for(int i = 0; i < exemptPlayers.size(); i++)
                {
                    exemptList += exemptPlayers.get(i);
                    if(i < exemptPlayers.size() - 1)
                        exemptList += ",";
                }
            }
            else if(firstRun)
                exemptList = DEFAULT_EXEMPT_PLAYERS;

            configProps.setProperty(PROP_EXEMPT_PLAYERS, exemptList);
            configProps.setProperty(PROP_TIMEOUT_CHECK, ((Integer) timeoutCheckInterval).toString());
            configProps.setProperty(PROP_KICK_BROADCAST, kickBroadcastMessage);
            configProps.setProperty(PROP_KICK_MESSAGE, kickMessage);
            configProps.setProperty(PROP_KICK_TIMEOUT, ((Integer) kickTimeout).toString());
            configProps.setProperty(PROP_PLAYER_COUNT, ((Integer) playerCountThreshold).toString());
            configProps.setProperty(PROP_USE_JUMP_IGNORE, ((Boolean) useJumpIgnore).toString());
            configProps.setProperty(PROP_KICK_ILDERS, ((Boolean) kickIdlers).toString());
            configProps.setProperty(PROP_IGNORE_VEHICLES, ((Boolean) ignoreVehicles).toString());
            configProps.setProperty(PROP_USE_FAUX_SLEEP, ((Boolean) useFauxSleep).toString());

            // Set the values for listening properties.
            configProps.setProperty(PROP_MOVE_LISTEN, ((Boolean) moveListen).toString());
            configProps.setProperty(PROP_INVENTORY_OPEN_LISTEN, ((Boolean) inventoryOpenListen).toString());
            configProps.setProperty(PROP_CHAT_LISTEN, ((Boolean) chatListen).toString());
            configProps.setProperty(PROP_BLOCK_PLACE_LISTEN, ((Boolean) blockPlaceListen).toString());
            configProps.setProperty(PROP_BLOCK_BREAK_LISTEN, ((Boolean) blockBreakListen).toString());
            configProps.setProperty(PROP_DROP_ITEM_LISTEN, ((Boolean) dropItemListen).toString());

            BufferedOutputStream stream = new BufferedOutputStream(
                    new FileOutputStream(configFile.getAbsolutePath()));
            configProps.store(stream, "Default auto-created config file. Version " + plugin.getDescription().getVersion() + ". Please change.\n" +
                    "kick-timeout is amount of time (sec) players can be idle, kick-message is the message the\n" +
                    "kicked player sees, kick-broadcast is the message all players see when a player is kicked (name + message), \n" +
                    "timeout-check-interval is the frequency (sec) to check for players to boot, and exempt-players is the list\n" +
                    "of players not to kick at all. player-count-threshold is the number of players that must be present before\n" +
                    "players start getting kicked for idling. Set to 0 for always. Set use-jump-ignoring to use the experimental\n" +
                    "code which ignores vertical movement for activity purposes. Set kick-idlers to determine whether or not idlers\n" +
                    "should actually be kicked or merely announced. ignore-vehicle-movement if set to true will not consider a player's\n" +
                    "movement if they are in a vehicle. use-faux-sleep will count AFK players as \"sleeping\" for the purposes of beds\n" +
                    "moving the clock forward; only works if kick-idlers is false.");
            plugin.log("Finished writing config file.", Level.INFO);
        }
        catch(IOException e)
        {
            plugin.log("Failed writing config file.", Level.SEVERE);
            e.printStackTrace();
        }
    }

    private void loadPropertiesFile(File configFolder)
    {
        Properties configProps = new Properties();
        try
        {
            BufferedInputStream stream = new BufferedInputStream(
                    new FileInputStream(new File(configFolder, CONFIG_FILE)));
            configProps.load(stream);

            try
            {
                kickTimeout = Integer.parseInt(configProps.getProperty(PROP_KICK_TIMEOUT));
            }
            catch(NumberFormatException e)
            {
                plugin.log("Failed reading kick timeout.", Level.SEVERE);
                kickTimeout = DEFAULT_KICK_TIMEOUT;
            }

            kickMessage = configProps.getProperty(PROP_KICK_MESSAGE);
            if(kickMessage == null)
                kickMessage = "";

            kickBroadcastMessage = configProps.getProperty(PROP_KICK_BROADCAST);
            if(kickBroadcastMessage == null)
                kickBroadcastMessage = "";

            try
            {
                timeoutCheckInterval = Integer.parseInt(configProps.getProperty(PROP_TIMEOUT_CHECK));
            }
            catch(NumberFormatException e)
            {
                plugin.log("Failed reading timeout check interval.", Level.SEVERE);
                timeoutCheckInterval = DEFAULT_TIMEOUT_CHECK;
            }

            try
            {
                playerCountThreshold = Integer.parseInt(configProps.getProperty(PROP_PLAYER_COUNT));
            }
            catch(NumberFormatException e)
            {
                plugin.log("Failed reading player count threshold.", Level.SEVERE);
                playerCountThreshold = DEFAULT_PLAYER_COUNT;
            }

            if(!configProps.containsKey(PROP_USE_JUMP_IGNORE))
                useJumpIgnore = DEFAULT_USE_JUMP_IGNORE;
            else
                useJumpIgnore = Boolean.parseBoolean(configProps.getProperty(PROP_USE_JUMP_IGNORE));

            if(!configProps.containsKey(PROP_KICK_ILDERS))
                kickIdlers = DEFAULT_KICK_IDLERS;
            else
                kickIdlers = Boolean.parseBoolean(configProps.getProperty(PROP_KICK_ILDERS));

            if(!configProps.containsKey(PROP_IGNORE_VEHICLES))
                ignoreVehicles = DEFAULT_IGNORE_VEHICLES;
            else
                ignoreVehicles = Boolean.parseBoolean(configProps.getProperty(PROP_IGNORE_VEHICLES));

            if(!configProps.containsKey(PROP_USE_FAUX_SLEEP))
                useFauxSleep = DEFAULT_USE_FAUX_SLEEP;
            else
                useFauxSleep = Boolean.parseBoolean(configProps.getProperty(PROP_USE_FAUX_SLEEP));

            // Pull out event listening properties.
            if(!configProps.containsKey(PROP_MOVE_LISTEN))
                moveListen = DEFAULT_MOVE_LISTEN;
            else
                moveListen = Boolean.parseBoolean(configProps.getProperty(PROP_MOVE_LISTEN));

            if(!configProps.containsKey(PROP_INVENTORY_OPEN_LISTEN))
                inventoryOpenListen = DEFAULT_INVENTORY_OPEN_LISTEN;
            else
                inventoryOpenListen = Boolean.parseBoolean(configProps.getProperty(PROP_INVENTORY_OPEN_LISTEN));

            if(!configProps.containsKey(PROP_CHAT_LISTEN))
                chatListen = DEFAULT_CHAT_LISTEN;
            else
                chatListen = Boolean.parseBoolean(configProps.getProperty(PROP_CHAT_LISTEN));

            if(!configProps.containsKey(PROP_BLOCK_PLACE_LISTEN))
                blockPlaceListen = DEFAULT_BLOCK_PLACE_LISTEN;
            else
                blockPlaceListen = Boolean.parseBoolean(configProps.getProperty(PROP_BLOCK_PLACE_LISTEN));

            if(!configProps.containsKey(PROP_BLOCK_BREAK_LISTEN))
                blockBreakListen = DEFAULT_BLOCK_BREAK_LISTEN;
            else
                blockBreakListen = Boolean.parseBoolean(configProps.getProperty(PROP_BLOCK_BREAK_LISTEN));

            if(!configProps.containsKey(PROP_DROP_ITEM_LISTEN))
                dropItemListen = DEFAULT_DROP_ITEM_LISTEN;
            else
                dropItemListen = Boolean.parseBoolean(configProps.getProperty(PROP_DROP_ITEM_LISTEN));

            String exemptList = configProps.getProperty(PROP_EXEMPT_PLAYERS);
            if(exemptList != null)
            {
                String[] splitExemptList = exemptList.split(",");
                if(splitExemptList != null)
                {
                    for(String playerName : splitExemptList)
                    {
                        if(playerName.length() > 0)
                            exemptPlayers.add(playerName);
                    }
                }
            }
        }
        catch(FileNotFoundException e)
        {
            plugin.log("Failed reading config file.", Level.SEVERE);
            e.printStackTrace();
        }
        catch(IOException e)
        {
            plugin.log("Failed reading config file.", Level.SEVERE);
            e.printStackTrace();
        }
    }

    public List<String> getExemptPlayers()
    {
        return exemptPlayers;
    }

    public void addExemptPlayer(String exemptPlayerName)
    {
        exemptPlayers.add(exemptPlayerName);
    }

    public void removeExemptPlayer(String exemptPlayerName)
    {
        exemptPlayers.remove(exemptPlayerName);
    }

    public int getKickTimeout()
    {
        return kickTimeout;
    }

    public void setKickTimeout(int kickTimeout)
    {
        this.kickTimeout = kickTimeout;
    }

    public String getKickMessage()
    {
        return kickMessage;
    }

    public void setKickMessage(String kickMessage)
    {
        this.kickMessage = kickMessage;
    }

    public int getTimeoutCheckInterval()
    {
        return timeoutCheckInterval;
    }

    public String getKickBroadcastMessage()
    {
        return kickBroadcastMessage;
    }

    public void setKickBroadcastMessage(String kickBroadcastMessage)
    {
        this.kickBroadcastMessage = kickBroadcastMessage;
    }

    public int getPlayerCountThreshold()
    {
        return playerCountThreshold;
    }

    public void setPlayerCountThreshold(int playerCountThreshold)
    {
        this.playerCountThreshold = playerCountThreshold;
    }

    public boolean isUseJumpIgnore()
    {
        return useJumpIgnore;
    }

    public void setUseJumpIgnore(boolean useJumpIgnore)
    {
        this.useJumpIgnore = useJumpIgnore;
    }

    public boolean isKickIdlers()
    {
        return kickIdlers;
    }

    public void setKickIdlers(boolean kickIdlers)
    {
        this.kickIdlers = kickIdlers;
    }

    public boolean isIgnoreVehicles()
    {
        return ignoreVehicles;
    }

    public void setIgnoreVehicles(boolean ignoreVehicles)
    {
        this.ignoreVehicles = ignoreVehicles;
    }

    public boolean isUseFauxSleep()
    {
        return useFauxSleep;
    }

    public void setUseFauxSleep(boolean useFauxSleep)
    {
        this.useFauxSleep = useFauxSleep;
    }

    public boolean isMoveListen()
    {
        return moveListen;
    }

    public boolean isInventoryOpenListen()
    {
        return inventoryOpenListen;
    }

    public boolean isChatListen()
    {
        return chatListen;
    }

    public boolean isBlockPlaceListen()
    {
        return blockPlaceListen;
    }

    public boolean isBlockBreakListen()
    {
        return blockBreakListen;
    }

    public boolean isDropItemListen()
    {
        return dropItemListen;
    }
}
