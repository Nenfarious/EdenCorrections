package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {
    private final EdenCorrections plugin;

    public PlayerListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule a task to notify player of their off-duty time
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    // Check if they have off-duty time
                    int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());
                    if (offDutyMinutes > 0) {
                        Component message = MessageUtils.parseMessage(plugin.getConfig()
                                .getString("messages.time-remaining", "<yellow>You have {minutes} minutes of off-duty time remaining.</yellow>")
                                .replace("{minutes}", String.valueOf(offDutyMinutes))
                                .replace("{player}", player.getName()));

                        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
                    }

                    // Check if they are on duty
                    if (plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                        Component message = MessageUtils.parseMessage("<yellow>You are currently on duty.</yellow>");
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
                    }
                }
            }
        }.runTaskLater(plugin, 40L); // Run after 2 seconds to ensure player is fully loaded

        plugin.getJailManager().handlePlayerJoin(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // If they are on duty, make sure we record accurate time
        if (plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            // Calculate their time served
            long startTime = plugin.getDutyManager().getSessionStartTime(player.getUniqueId());
            long duration = System.currentTimeMillis() - startTime;
            int minutes = (int) (duration / (1000 * 60));

            // Update their start time to reflect the elapsed time
            plugin.getStorageManager().saveDutyStartTime(
                    player.getUniqueId(),
                    System.currentTimeMillis() - (duration % (1000 * 60)));

            plugin.getLogger().info(player.getName() + " left while on duty. Time served so far: " + minutes + " minutes.");
        }
    }
}