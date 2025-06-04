package dev.lsdmc.edencorrections.managers;

import org.bukkit.entity.Player;
import dev.lsdmc.edencorrections.EdenCorrections;
import java.util.*;

public class GuardRankManager {
    private final EdenCorrections plugin;

    public GuardRankManager(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the list of guard ranks in order from config
     */
    public List<String> getRankList() {
        if (plugin.getConfig().isConfigurationSection("guard-ranks")) {
            return new ArrayList<>(plugin.getConfig().getConfigurationSection("guard-ranks").getKeys(false));
        }
        return List.of("trainee", "private", "officer", "sergeant", "warden");
    }

    /**
     * Gets the permissions for a given rank from config
     */
    public List<String> getPermissionsForRank(String rank) {
        String path = "guard-ranks." + rank + ".permissions";
        if (plugin.getConfig().isList(path)) {
            return plugin.getConfig().getStringList(path);
        }
        return Collections.emptyList();
    }

    /**
     * Gets the highest guard rank a player has (by permission)
     * @param player The player to check
     * @return The rank name or "guard" if no specific rank permission
     */
    public String getPlayerRank(Player player) {
        List<String> ranks = getRankList();
        // Check from highest to lowest rank for permission
        for (int i = ranks.size() - 1; i >= 0; i--) {
            String rank = ranks.get(i);
            if (player.hasPermission("edencorrections.rank." + rank)) {
                plugin.getLogger().info("Found rank " + rank + " for player " + player.getName());
                return rank;
            }
        }
        // Default rank if no specific rank permission but has general guard permission
        if (player.hasPermission("edencorrections.duty") ||
                player.hasPermission("edencorrections.guard")) {
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

    public List<String> getValidRanks() {
        return List.of("trainee", "private", "officer", "sergeant", "warden");
    }
}