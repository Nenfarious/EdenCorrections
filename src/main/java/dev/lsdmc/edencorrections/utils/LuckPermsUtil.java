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
    // Guard ranks from lowest to highest
    private static final List<String> GUARD_RANKS = Arrays.asList(
            "trainee", "private", "officer", "sergeant", "captain"
    );

    /**
     * Get the guard rank of a player from LuckPerms
     * @param player The player to check
     * @return The guard rank, or null if player has no guard rank
     */
    public static String getGuardRank(Player player) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUniqueId());

            if (user == null) {
                return null;
            }

            // Check each rank from highest to lowest priority
            for (int i = GUARD_RANKS.size() - 1; i >= 0; i--) {
                String rank = GUARD_RANKS.get(i);
                // Get the current active inherited groups
                @NonNull @Unmodifiable Collection<Group> inheritedGroups = user.getInheritedGroups(QueryOptions.defaultContextualOptions());

                if (inheritedGroups.stream()
                        .anyMatch(group -> group.getName().equalsIgnoreCase(rank))) {
                    return rank;
                }
            }

            return null; // No guard rank found
        } catch (Exception e) {
            return null; // LuckPerms not available or other error
        }
    }

    /**
     * Check if a player has a specific guard rank
     * @param player The player to check
     * @param rank The rank to check for
     * @return True if player has the rank, false otherwise
     */
    public static boolean hasGuardRank(Player player, String rank) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUniqueId());

            if (user == null) {
                return false;
            }

            // Get the current active inherited groups
            @NonNull @Unmodifiable Collection<Group> inheritedGroups = user.getInheritedGroups(QueryOptions.defaultContextualOptions());

            return inheritedGroups.stream()
                    .anyMatch(group -> group.getName().equalsIgnoreCase(rank));
        } catch (Exception e) {
            return false; // LuckPerms not available or other error
        }
    }

    /**
     * Check if a player has any guard rank
     * @param player The player to check
     * @return True if player has any guard rank, false otherwise
     */
    public static boolean hasAnyGuardRank(Player player) {
        return getGuardRank(player) != null;
    }

    /**
     * Get all valid guard ranks
     * @return List of guard ranks from lowest to highest
     */
    public static List<String> getGuardRanks() {
        return GUARD_RANKS;
    }
}