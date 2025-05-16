package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardBuffManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for handling guard buff-related events
 */
public class GuardBuffListener implements Listener {
    private final GuardBuffManager guardBuffManager;
    private final EdenCorrections plugin;

    public GuardBuffListener(GuardBuffManager guardBuffManager, EdenCorrections plugin) {
        this.guardBuffManager = guardBuffManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is a guard with a slight delay to allow permissions to load
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && guardBuffManager.isPlayerGuard(player)) {
                    guardBuffManager.onGuardJoin(player);
                }
            }
        }.runTaskLater(plugin, 20L); // 1-second delay
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (guardBuffManager.isPlayerGuard(player)) {
            // Slight delay to ensure all quit events are processed
            new BukkitRunnable() {
                @Override
                public void run() {
                    guardBuffManager.onGuardQuit(player);
                }
            }.runTaskLater(plugin, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Track permissions changes through permission commands
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/perm") || cmd.startsWith("/lp") ||
                cmd.startsWith("/permission") || cmd.startsWith("/op") ||
                cmd.startsWith("/deop")) {

            // Schedule a recalculation after the command executes
            new BukkitRunnable() {
                @Override
                public void run() {
                    guardBuffManager.recalculateOnlineGuards();
                }
            }.runTaskLater(plugin, 20L);
        }
    }
}