package dev.lsdmc.edencorrections.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class LuckPermsUtil {
    /**
     * Get the guard rank of a player from LuckPerms and config mapping
     */
    public static String getGuardRank(Player player) {
        try {
            var plugin = dev.lsdmc.edencorrections.EdenCorrections.getInstance();
            var rankManager = plugin.getGuardRankManager();
            if (plugin.getConfig().contains("guard-rank-groups")) {
                var luckPerms = LuckPermsProvider.get();
                var user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    for (String rank : rankManager.getRankList()) {
                        String group = plugin.getConfig().getString("guard-rank-groups." + rank);
                        if (group != null && user.getPrimaryGroup().equalsIgnoreCase(group)) {
                            return rank;
                        }
                    }
                }
            }
            // Fallback: check permissions for rank
            for (int i = rankManager.getRankList().size() - 1; i >= 0; i--) {
                String rank = rankManager.getRankList().get(i);
                if (player.hasPermission("edencorrections.rank." + rank)) {
                    return rank;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a player has a specific guard rank
     */
    public static boolean hasGuardRank(Player player, String rank) {
        try {
            var plugin = dev.lsdmc.edencorrections.EdenCorrections.getInstance();
            var rankManager = plugin.getGuardRankManager();
            var luckPerms = LuckPermsProvider.get();
            var user = luckPerms.getUserManager().getUser(player.getUniqueId());
            String group = plugin.getConfig().getString("guard-rank-groups." + rank);
            if (user != null && group != null) {
                return user.getPrimaryGroup().equalsIgnoreCase(group);
            }
            // Fallback: check permission
            return player.hasPermission("edencorrections.rank." + rank);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a player has any guard rank
     */
    public static boolean hasAnyGuardRank(Player player) {
        return getGuardRank(player) != null;
    }

    /**
     * Get all valid guard ranks from config
     */
    public static List<String> getGuardRanks() {
        var plugin = dev.lsdmc.edencorrections.EdenCorrections.getInstance();
        return plugin.getGuardRankManager().getRankList();
    }
}