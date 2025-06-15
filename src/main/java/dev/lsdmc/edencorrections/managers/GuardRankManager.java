package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GuardRankManager {
    private final EdenCorrections plugin;
    private final Map<String, List<String>> rankPermissions = new HashMap<>();
    private final Map<String, String> rankKits = new HashMap<>();
    private final List<String> rankHierarchy = new ArrayList<>();
    private final Map<String, String> rankToGroupMap = new HashMap<>();

    public GuardRankManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        // Load from ranks.yml which contains the actual rank configuration
        FileConfiguration ranksConfig = plugin.getConfigManager().getRanksConfig();
        FileConfiguration dutyConfig = plugin.getConfigManager().getDutyConfigFile();
        
        rankHierarchy.clear();
        rankPermissions.clear();
        rankKits.clear();
        rankToGroupMap.clear();

        // Load rank hierarchy from ranks.yml
        if (ranksConfig.contains("guard-ranks.hierarchy")) {
            rankHierarchy.addAll(ranksConfig.getStringList("guard-ranks.hierarchy"));
        }

        // Load rank kits from duty.yml
        if (dutyConfig.contains("rank-kits")) {
            for (String rank : rankHierarchy) {
                String kitName = dutyConfig.getString("rank-kits." + rank);
                if (kitName != null) {
                    rankKits.put(rank, kitName);
                }
            }
        }

        // Load rank permissions from ranks.yml individual rank sections
        for (String rank : rankHierarchy) {
            if (ranksConfig.contains("guard-ranks." + rank + ".rank-permissions")) {
                rankPermissions.put(rank, ranksConfig.getStringList("guard-ranks." + rank + ".rank-permissions"));
            }
        }

        // Load LuckPerms group mappings from ranks.yml
        if (ranksConfig.contains("luckperms.group-names")) {
            for (String rank : rankHierarchy) {
                String group = ranksConfig.getString("luckperms.group-names." + rank);
                if (group != null) {
                    rankToGroupMap.put(rank, group);
                }
            }
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Loaded guard rank configuration:");
            plugin.getLogger().info("Hierarchy: " + rankHierarchy);
            plugin.getLogger().info("Kits: " + rankKits);
            plugin.getLogger().info("Group mappings: " + rankToGroupMap);
        }
    }

    public boolean createGuardRank(String rank, String group) {
        if (isValidRank(rank)) {
            plugin.getLogger().warning("Rank already exists: " + rank);
            return false;
        }

        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms API not available!");
            return false;
        }

        Group luckPermsGroup = luckPerms.getGroupManager().getGroup(group);
        if (luckPermsGroup == null) {
            plugin.getLogger().warning("LuckPerms group not found: " + group);
            return false;
        }

        // Add to hierarchy
        rankHierarchy.add(rank.toLowerCase());
        
        // Save to config
        FileConfiguration config = plugin.getConfig();
        List<String> hierarchy = config.getStringList("guard-ranks.hierarchy");
        hierarchy.add(rank.toLowerCase());
        config.set("guard-ranks.hierarchy", hierarchy);
        config.set("guard-rank-groups." + rank.toLowerCase(), group);
        plugin.saveConfig();

        // Update mappings
        rankToGroupMap.put(rank.toLowerCase(), group);
        
        plugin.getLogger().info("Created new guard rank: " + rank + " linked to group: " + group);
        return true;
    }

    public boolean assignRank(Player player, String rank) {
        if (!isValidRank(rank)) {
            plugin.getLogger().warning("Invalid rank: " + rank);
            return false;
        }

        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms API not available!");
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                plugin.getLogger().warning("Could not find LuckPerms user for: " + player.getName());
                return false;
            }

            String group = rankToGroupMap.get(rank.toLowerCase());
            if (group == null) {
                plugin.getLogger().warning("No LuckPerms group mapped for rank: " + rank);
                return false;
            }

            // Remove from all other guard rank groups
            for (String r : rankHierarchy) {
                String otherGroup = rankToGroupMap.get(r);
                if (otherGroup != null && !otherGroup.equals(group)) {
                    user.data().remove(Node.builder("group." + otherGroup).build());
                }
            }

            // Add new group
            Node node = Node.builder("group." + group).build();
            user.data().add(node);

            // Add rank-specific permissions
            List<String> permissions = rankPermissions.getOrDefault(rank.toLowerCase(), Collections.emptyList());
            for (String permission : permissions) {
                Node permNode = Node.builder(permission).build();
                user.data().add(permNode);
            }

            // Save changes
            luckPerms.getUserManager().saveUser(user);
            plugin.getLogger().info("Assigned rank " + rank + " to " + player.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error assigning rank to " + player.getName(), e);
            return false;
        }
    }

    public boolean removeRank(Player player, String rank) {
        if (!isValidRank(rank)) {
            plugin.getLogger().warning("Attempted to remove invalid rank: " + rank);
            return false;
        }

        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms API not available!");
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                plugin.getLogger().warning("Could not find LuckPerms user for: " + player.getName());
                return false;
            }

            String group = rankToGroupMap.get(rank.toLowerCase());
            if (group != null) {
                Node node = Node.builder("group." + group).build();
                user.data().remove(node);
            }

            // Remove rank-specific permissions
            List<String> permissions = rankPermissions.getOrDefault(rank.toLowerCase(), Collections.emptyList());
            for (String permission : permissions) {
                Node permNode = Node.builder(permission).build();
                user.data().remove(permNode);
            }

            // Save changes
            luckPerms.getUserManager().saveUser(user);
            plugin.getLogger().info("Removed rank " + rank + " from " + player.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing rank from " + player.getName(), e);
            return false;
        }
    }

    public String getPlayerRank(Player player) {
        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) return "default";
        
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        
        // Check ranks in order of hierarchy
        for (int i = rankHierarchy.size() - 1; i >= 0; i--) {
            String rank = rankHierarchy.get(i);
            String group = rankToGroupMap.get(rank);
            if (group != null && user.getNodes().stream()
                    .filter(node -> node.getKey().startsWith("group."))
                    .anyMatch(node -> node.getKey().equals("group." + group))) {
                return rank;
            }
        }
        return "default";
    }

    public String getKitNameForPlayer(Player player) {
        String rank = getPlayerRank(player);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Getting kit for player " + player.getName() + " with rank: " + rank);
        }
        
        // If we have a kit mapping for this rank, use it
        if (rankKits.containsKey(rank)) {
            String kitName = rankKits.get(rank);
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Found kit mapping: " + rank + " -> " + kitName);
            }
            return kitName;
        }
        
        // If rank is "default" and no kit mapping exists, try to find the lowest rank
        if ("default".equals(rank) && !rankHierarchy.isEmpty()) {
            String lowestRank = rankHierarchy.get(0); // First rank in hierarchy (usually trainee)
            if (rankKits.containsKey(lowestRank)) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Default rank, using lowest rank kit: " + lowestRank + " -> " + rankKits.get(lowestRank));
                }
                return rankKits.get(lowestRank);
            }
            // Fallback to the rank name itself
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("No kit mapping for lowest rank, using rank name: " + lowestRank);
            }
            return lowestRank;
        }
        
        // Final fallback - return the rank name itself as kit name
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("No kit mapping found, using rank as kit name: " + rank);
        }
        return rank;
    }

    public boolean isValidRank(String rank) {
        return rankHierarchy.contains(rank.toLowerCase());
    }

    public List<String> getValidRanks() {
        return new ArrayList<>(rankHierarchy);
    }

    public List<String> getRankList() {
        return new ArrayList<>(rankHierarchy);
    }

    public List<String> getPermissionsForRank(String rank) {
        return new ArrayList<>(rankPermissions.getOrDefault(rank.toLowerCase(), Collections.emptyList()));
    }

    public List<String> getAvailableLuckPermsGroups() {
        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) return Collections.emptyList();
        
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .map(Group::getName)
                .collect(Collectors.toList());
    }

    public boolean setRankPosition(String rank, int newPosition) {
        if (!isValidRank(rank)) {
            plugin.getLogger().warning("Invalid rank: " + rank);
            return false;
        }

        if (newPosition < 0 || newPosition >= rankHierarchy.size()) {
            plugin.getLogger().warning("Invalid position: " + newPosition);
            return false;
        }

        // Remove rank from current position
        rankHierarchy.remove(rank.toLowerCase());
        // Add rank at new position
        rankHierarchy.add(newPosition, rank.toLowerCase());

        // Save to config
        FileConfiguration config = plugin.getConfig();
        config.set("guard-ranks.hierarchy", rankHierarchy);
        plugin.saveConfig();

        plugin.getLogger().info("Moved rank " + rank + " to position " + newPosition);
        return true;
    }

    public boolean moveRankUp(String rank) {
        int currentIndex = rankHierarchy.indexOf(rank.toLowerCase());
        if (currentIndex <= 0) return false;
        return setRankPosition(rank, currentIndex - 1);
    }

    public boolean moveRankDown(String rank) {
        int currentIndex = rankHierarchy.indexOf(rank.toLowerCase());
        if (currentIndex == -1 || currentIndex >= rankHierarchy.size() - 1) return false;
        return setRankPosition(rank, currentIndex + 1);
    }

    public int getRankPosition(String rank) {
        return rankHierarchy.indexOf(rank.toLowerCase());
    }

    public boolean isHigherRank(String rank1, String rank2) {
        int pos1 = getRankPosition(rank1);
        int pos2 = getRankPosition(rank2);
        return pos1 > pos2;
    }

    public boolean canManageRank(Player player, String targetRank) {
        String playerRank = getPlayerRank(player);
        return isHigherRank(playerRank, targetRank);
    }

    public void reload() {
        loadConfig();
    }
}