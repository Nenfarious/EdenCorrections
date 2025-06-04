package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final EdenCorrections plugin;

    public PlayerListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clear any stale immobilization status
        plugin.getDutyManager().clearImmobilization(playerId);

        // Process daily login rewards with full multipliers
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getGuardTokenManager().processDailyLogin(player);
            }
        }, 40L); // 2 second delay

        // Show GUI on join if configured
        ConfigManager.GuiConfig guiConfig = plugin.getConfigManager().getGuiConfig();
        if (guiConfig != null && guiConfig.showOnJoin && player.hasPermission("edencorrections.guard")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGuiManager().openMainMenu(player);
                }
            }, guiConfig.joinDelay);
        }

        plugin.getLogger().info("Player " + player.getName() + " joined - loaded guard data and processed login");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // FIXED: Properly handle duty status on quit to prevent auto-duty on rejoin
        if (plugin.getDutyManager().isOnDuty(playerId)) {
            // Calculate their time served
            long startTime = plugin.getDutyManager().getSessionStartTime(playerId);
            long duration = System.currentTimeMillis() - startTime;
            int minutes = (int) (duration / (1000 * 60));

            plugin.getLogger().info(player.getName() + " left while on duty. Time served: " + minutes + " minutes. Duty status will be cleared.");

            // CRITICAL FIX: Clear duty status on quit to prevent auto-duty
            plugin.getDutyManager().forceOffDuty(player);
            
            // Remove any active immobilization
            plugin.getDutyManager().clearImmobilization(playerId);
        }
    }
}