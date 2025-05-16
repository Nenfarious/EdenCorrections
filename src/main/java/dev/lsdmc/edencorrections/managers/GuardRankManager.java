package dev.lsdmc.edencorrections.managers;

import org.bukkit.entity.Player;
import dev.lsdmc.edencorrections.EdenCorrections;

public class GuardRankManager {
    private final EdenCorrections plugin;
    private static final String[] RANK_HIERARCHY = {"trainee", "private", "officer", "sergeant", "captain"};

    public GuardRankManager(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the highest guard rank a player has
     * @param player The player to check
     * @return The rank name or "guard" if no specific rank permission
     */
    public String getPlayerRank(Player player) {
        // Check from highest to lowest rank for better efficiency
        for (int i = RANK_HIERARCHY.length - 1; i >= 0; i--) {
            String rank = RANK_HIERARCHY[i];
            if (player.hasPermission("edencorrections.rank." + rank)) {
                plugin.getLogger().info("Found rank " + rank + " for player " + player.getName());
                return rank;
            }
        }

        // Default rank if no specific rank permission but has general guard permission
        if (player.hasPermission("edencorrections.duty") ||
                player.hasPermission("edenprison.guard")) {
            plugin.getLogger().info("Using default 'guard' rank for player " + player.getName());
            return "guard";
        }

        plugin.getLogger().info("No guard rank found for player " + player.getName());
        return null;
    }

    /**
     * Gets the kit name to use for a player based on their rank
     * @param player The player to check
     * @return The kit name to use
     */
    public String getKitNameForPlayer(Player player) {
        String rank = getPlayerRank(player);
        if (rank == null) return null;

        // Get custom kit override from config if specified
        String kitName = plugin.getConfig().getString("duty.rank-kits." + rank, rank);
        plugin.getLogger().info("Using kit '" + kitName + "' for player " + player.getName() + " with rank " + rank);
        return kitName;
    }
}