package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardLootManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listener for handling guard loot-related events
 */
public class GuardLootListener implements Listener {
    private final GuardLootManager lootManager;
    private final EdenCorrections plugin;

    public GuardLootListener(GuardLootManager lootManager, EdenCorrections plugin) {
        this.lootManager = lootManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Skip if loot system is disabled
        if (!lootManager.isLootEnabled()) return;

        Player victim = event.getEntity();

        // Check if victim is a guard
        if (!isPlayerGuard(victim)) return;

        // Process arena regions - skip if in arena
        if (inArena(victim)) return;

        // Get the killer
        Player killer = victim.getKiller();
        if (killer == null) return;

        // Skip if killer is also a guard
        if (isPlayerGuard(killer)) return;

        // Handle guard death with the manager
        lootManager.handleGuardDeath(victim, killer);
    }

    /**
     * Check if a player is a guard
     * @param player The player to check
     * @return True if the player is a guard
     */
    private boolean isPlayerGuard(Player player) {
        return player.hasPermission("edenprison.guard") || player.hasPermission("edencorrections.duty");
    }

    /**
     * Check if a player is in an arena
     * @param player The player to check
     * @return True if the player is in an arena
     */
    private boolean inArena(Player player) {
        // Check for arena1 to arena10 regions
        for (int i = 1; i <= 10; i++) {
            if (plugin.getRegionUtils().isPlayerInRegion(player, "arena" + i)) {
                return true;
            }
        }
        return false;
    }
}