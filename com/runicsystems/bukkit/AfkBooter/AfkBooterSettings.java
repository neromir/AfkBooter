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
    private final String CONFIG_FILE = "afkbooter.properties";

    public final int DEFAULT_KICK_TIMEOUT = 30;
    public final String DEFAULT_KICK_MESSAGE = "Kicked for idling.";
    public final int DEFAULT_TIMEOUT_CHECK = 10;
    public final String DEFAULT_KICK_BROADCAST = "kicked for idling.";
    public final String DEFAULT_EXEMPT_PLAYERS = "name1,name2,name3";

    private List<String> exemptPlayers;
    private int kickTimeout;
    private String kickMessage;
    private int timeoutCheckInterval;
    private String kickBroadcastMessage;

    private AfkBooter plugin;

    public AfkBooterSettings(AfkBooter plugin)
    {
        this.plugin = plugin;
        exemptPlayers = new LinkedList<String>();
        kickTimeout = DEFAULT_KICK_TIMEOUT;
        kickMessage = DEFAULT_KICK_MESSAGE;
        timeoutCheckInterval = DEFAULT_TIMEOUT_CHECK;
        kickBroadcastMessage = DEFAULT_KICK_BROADCAST;
    }

    public void init(File configFolder)
    {
        // Create the config folder if it doesn't exist.
        if(!configFolder.exists())
        {
            plugin.log("Config folder not found, creating.", Level.INFO);
            configFolder.mkdirs();
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
            if(!configFile.exists())
                configFile.createNewFile();

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
            BufferedOutputStream stream = new BufferedOutputStream(
                    new FileOutputStream(configFile.getAbsolutePath()));
            configProps.store(stream, "Default auto-created config file. Please change.\n" +
                    "kick-timeout is amount of time (sec) players can be idle, kick-message is the message the\n" +
                    "kicked player sees, kick-broadcast is the message all players see when a player is kicked (name + message), \n" +
                    "timeout-check-interval is the frequency (sec) to check for players to boot, and exempt-players is the list\n" +
                    "of players not to kick at all.");
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

    public void setTimeoutCheckInterval(int timeoutCheckInterval)
    {
        this.timeoutCheckInterval = timeoutCheckInterval;
    }

    public String getKickBroadcastMessage()
    {
        return kickBroadcastMessage;
    }

    public void setKickBroadcastMessage(String kickBroadcastMessage)
    {
        this.kickBroadcastMessage = kickBroadcastMessage;
    }
}
