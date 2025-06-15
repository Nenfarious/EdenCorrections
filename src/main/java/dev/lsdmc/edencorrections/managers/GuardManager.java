package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core manager for guard-related functionality.
 * This is the central manager that other guard-related managers interact with.
 */
public class GuardManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private final StorageManager storageManager;

    // Guard status tracking
    private final Map<UUID, Boolean> dutyStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dutyStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> offDutyMinutes = new ConcurrentHashMap<>();

    // Guard buffs
    private final Map<UUID, Set<PotionEffect>> activeBuffs = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> buffTasks = new ConcurrentHashMap<>();
    private int onlineGuardCount = 0;

    // Guard penalties
    private final Map<UUID, Integer> deathCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> movementPenalties = new ConcurrentHashMap<>();

    // Guard restrictions
    private final Set<String> restrictedBlocks = new HashSet<>();
    private final Set<String> restrictedRegions = new HashSet<>();
    private final Set<String> restrictedCommands = new HashSet<>();
    private final Map<String, Set<Material>> tagCache = new HashMap<>();

    // Guard progression
    private final Map<UUID, Integer> guardPoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> guardRanks = new ConcurrentHashMap<>();
    private static final String[] RANK_HIERARCHY = {"trainee", "private", "officer", "sergeant", "captain", "warden"};

    // Guard statistics
    private final Map<UUID, Map<String, Integer>> guardStats = new ConcurrentHashMap<>();

    // Guard tokens
    private final Map<UUID, Integer> guardTokens = new ConcurrentHashMap<>();

    public GuardManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.storageManager = plugin.getStorageManager();
        loadData();
    }

    private void loadData() {
        // Load duty status
        Map<UUID, Boolean> loadedDutyStatus = storageManager.loadDutyStatus();
        if (loadedDutyStatus != null) {
            dutyStatus.putAll(loadedDutyStatus);
        }

        // Load duty start times
        Map<UUID, Long> loadedStartTimes = storageManager.loadDutyStartTimes();
        if (loadedStartTimes != null) {
            dutyStartTimes.putAll(loadedStartTimes);
        }

        // Load off-duty minutes
        Map<UUID, Integer> loadedMinutes = storageManager.loadOffDutyMinutes();
        if (loadedMinutes != null) {
            offDutyMinutes.putAll(loadedMinutes);
        }

        // Load other data from storage
        loadGuardData();
    }

    private void loadGuardData() {
        // Load ranks, points, and stats from storage
        // This would be implemented based on your storage system
    }

    /**
     * Check if a player is a guard
     */
    public boolean isGuard(Player player) {
        return player.hasPermission("edencorrections.duty") || 
               player.hasPermission("edencorrections.guard");
    }

    /**
     * Check if a player is on duty
     */
    public boolean isOnDuty(UUID playerId) {
        return dutyStatus.getOrDefault(playerId, false);
    }

    /**
     * Get a player's guard rank
     */
    public String getGuardRank(Player player) {
        if (player == null) {
            return null;
        }
        // Use GuardRankManager for unified rank resolution
        return plugin.getGuardRankManager().getPlayerRank(player);
    }

    /**
     * Get a player's guard points
     */
    public int getGuardPoints(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return guardPoints.getOrDefault(playerId, 0);
    }

    /**
     * Add points to a player's guard progression
     */
    public void addGuardPoints(Player player, int points, String reason) {
        if (player == null || points <= 0) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        int currentPoints = getGuardPoints(playerId);
        guardPoints.put(playerId, currentPoints + points);

        // Check for rank up
        checkRankUp(player);

        // Notify player
        Component message = MessageUtils.parseMessage(
            "<green>+" + points + " guard points: " + reason + "</green>");
        player.sendMessage(message);
    }

    /**
     * Check if a player should be promoted and handle the promotion
     */
    private void checkRankUp(Player player) {
        if (player == null) {
            return;
        }
        
        String currentRank = getGuardRank(player);
        if (currentRank == null) {
            return;
        }
        
        String newRank = getNextRank(currentRank);
        if (newRank != null && canPromote(player, newRank)) {
            promoteToRank(player, newRank);
        }
    }

    /**
     * Promote a player to a new rank
     */
    private void promoteToRank(Player player, String newRank) {
        if (player == null || newRank == null) {
            return;
        }
        
        try {
            // Execute LuckPerms command
            String command = "lp user " + player.getName() + " parent add " + newRank;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            // Broadcast promotion
            String promotionMessage = "<gold>CONGRATULATIONS! <yellow>{player}</yellow> has been promoted to <yellow>{rank}</yellow>!</gold>";
            Component message = MessageUtils.parseMessage(
                promotionMessage
                    .replace("{player}", player.getName())
                    .replace("{rank}", newRank.toUpperCase())
            );
            
            // Broadcast to all online players
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to promote player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Record a statistic for a player
     */
    public void recordStat(UUID playerId, String stat, int amount) {
        guardStats.computeIfAbsent(playerId, k -> new HashMap<>())
                 .merge(stat, amount, Integer::sum);
    }

    /**
     * Get a player's statistics
     */
    public Map<String, Integer> getPlayerStats(UUID playerId) {
        return new HashMap<>(guardStats.getOrDefault(playerId, new HashMap<>()));
    }

    /**
     * Save all guard data
     */
    public void saveData() {
        // Save duty status
        storageManager.saveDutyStatus(dutyStatus);
        storageManager.saveDutyStartTimes(dutyStartTimes);
        storageManager.saveOffDutyMinutes(offDutyMinutes);

        // Save other data
        saveGuardData();
    }

    private void saveGuardData() {
        // Save ranks, points, and stats to storage
        // This would be implemented based on your storage system
    }

    /**
     * Reload the manager
     */
    public void reload() {
        saveData();
        loadData();
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        saveData();
        dutyStatus.clear();
        dutyStartTimes.clear();
        offDutyMinutes.clear();
        guardRanks.clear();
        guardPoints.clear();
        guardStats.clear();
    }

    /**
     * Get the next rank in the hierarchy
     */
    private String getNextRank(String currentRank) {
        if (currentRank == null) {
            return "trainee";
        }
        
        for (int i = 0; i < RANK_HIERARCHY.length - 1; i++) {
            if (RANK_HIERARCHY[i].equals(currentRank)) {
                return RANK_HIERARCHY[i + 1];
            }
        }
        return null; // Already at highest rank
    }

    /**
     * Check if a player can be promoted to a specific rank
     */
    private boolean canPromote(Player player, String targetRank) {
        if (player == null || targetRank == null) {
            return false;
        }
        
        int currentPoints = getGuardPoints(player.getUniqueId());
        int requiredPoints = getRankPoints(targetRank);
        
        return currentPoints >= requiredPoints;
    }

    /**
     * Get the points required for a rank
     */
    private int getRankPoints(String rank) {
        if (rank == null) {
            return Integer.MAX_VALUE;
        }
        
        return switch (rank.toLowerCase()) {
            case "trainee" -> 0;
            case "private" -> 1000;
            case "officer" -> 3000;
            case "sergeant" -> 6000;
            case "captain" -> 8000;
            case "warden" -> 10000;
            default -> Integer.MAX_VALUE;
        };
    }
} 