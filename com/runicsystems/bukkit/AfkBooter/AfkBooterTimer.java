package com.runicsystems.bukkit.AfkBooter;

import java.util.logging.Level;

/**
 * @author neromir
 */
public class AfkBooterTimer extends Thread {
    private AfkBooter plugin;
    private long timeToSleep;
    private boolean aborted;

    public AfkBooterTimer(AfkBooter plugin, long timeToSleep) {
        this.plugin = plugin;
        this.timeToSleep = timeToSleep;
        this.aborted = false;
    }

    @Override
    public void run() {
        while (!aborted) {
            try {
                plugin.kickAfkPlayers();
                Thread.sleep(timeToSleep);
            }
            catch (InterruptedException e) {
                plugin.log("Interrupted while sleeping.", Level.SEVERE);
                e.printStackTrace();
            }
        }
    }

    public long getTimeToSleep() {
        return timeToSleep;
    }

    public void setTimeToSleep(long timeToSleep) {
        this.timeToSleep = timeToSleep;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }
}
