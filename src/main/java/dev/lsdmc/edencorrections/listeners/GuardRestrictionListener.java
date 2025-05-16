package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardRestrictionManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listener for handling guard restriction-related events
 */
public class GuardRestrictionListener implements Listener {
    private final GuardRestrictionManager restrictionManager;
    private final EdenCorrections plugin;

    public GuardRestrictionListener(GuardRestrictionManager restrictionManager, EdenCorrections plugin) {
        this.restrictionManager = restrictionManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if player is a guard and the block is restricted
        if (restrictionManager.isPlayerGuard(player) && restrictionManager.isBlockRestricted(block, player)) {
            event.setCancelled(true);
            restrictionManager.handleRestrictedBlockBreak(player);
        }
    }
}