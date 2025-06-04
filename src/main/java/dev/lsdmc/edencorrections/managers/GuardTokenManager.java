package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.time.LocalDateTime;

public class GuardTokenManager {
    private final EdenCorrections plugin;
    private final File tokenFile;
    private FileConfiguration tokenConfig;
    private final ConfigManager configManager;
    private ConfigManager.ShopConfig shopConfig;
    
    // Internal token balances - fully self-contained
    private final Map<UUID, Integer> tokenBalances = new ConcurrentHashMap<>();
    // Cache for last reward times
    private final Map<UUID, Long> lastRewardTimes = new ConcurrentHashMap<>();
    
    private final SQLiteStorage sqliteStorage;
    private BukkitTask dailyRewardTask;
    
    private final Map<UUID, Integer> dailyStreak = new HashMap<>();
    private final Map<UUID, LocalDate> lastDailyReward = new HashMap<>();
    
    public GuardTokenManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.tokenFile = new File(plugin.getDataFolder(), "guard_tokens.yml");
        this.configManager = plugin.getConfigManager();
        this.shopConfig = configManager.getShopConfig();
        
        // Use SQLiteStorage for persistence
        if (plugin.getStorageManager() instanceof SQLiteStorage) {
            this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        } else {
            this.sqliteStorage = null;
            plugin.getLogger().warning("GuardTokenManager: SQLiteStorage not available, using file fallback");
        }
        
        loadConfiguration();
        startDailyRewardTask();
        
        plugin.getLogger().info("GuardTokenManager initialized with internal token system");
    }
    
    private void loadConfiguration() {
        if (!tokenFile.exists()) {
            try {
                tokenFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create guard_tokens.yml");
                e.printStackTrace();
            }
        }
        tokenConfig = YamlConfiguration.loadConfiguration(tokenFile);
        
        // Load token balances and last reward times
        if (sqliteStorage != null) {
            // Load from SQLite
            loadFromSQLite();
        } else {
            // Fallback to file loading
            loadTokenBalancesFromFile();
            loadLastRewardTimesFromFile();
        }
    }
    
    private void loadFromSQLite() {
        try {
            // SQLite handles token storage automatically
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Token data will be loaded from SQLite on demand");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load token data from SQLite: " + e.getMessage());
            loadTokenBalancesFromFile();
            loadLastRewardTimesFromFile();
        }
    }
    
    private void loadTokenBalancesFromFile() {
        tokenBalances.clear();
        if (tokenConfig.contains("balances")) {
            for (String uuidStr : tokenConfig.getConfigurationSection("balances").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    int balance = tokenConfig.getInt("balances." + uuidStr, 0);
                    tokenBalances.put(playerId, balance);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in guard_tokens.yml: " + uuidStr);
                }
            }
        }
    }
    
    private void saveTokenBalancesToFile() {
        for (Map.Entry<UUID, Integer> entry : tokenBalances.entrySet()) {
            tokenConfig.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            tokenConfig.save(tokenFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guard_tokens.yml");
            e.printStackTrace();
        }
    }
    
    private void loadLastRewardTimesFromFile() {
        lastRewardTimes.clear();
        if (tokenConfig.contains("last_rewards")) {
            for (String uuidStr : tokenConfig.getConfigurationSection("last_rewards").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    long lastReward = tokenConfig.getLong("last_rewards." + uuidStr);
                    lastRewardTimes.put(playerId, lastReward);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in guard_tokens.yml: " + uuidStr);
                }
            }
        }
    }
    
    private void saveLastRewardTimesToFile() {
        for (Map.Entry<UUID, Long> entry : lastRewardTimes.entrySet()) {
            tokenConfig.set("last_rewards." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            tokenConfig.save(tokenFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guard_tokens.yml");
            e.printStackTrace();
        }
    }
    
    private void startDailyRewardTask() {
        // Run every hour to check for and distribute daily rewards
        dailyRewardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isPlayerGuard(player)) {
                    checkAndGiveDailyReward(player);
                }
            }
        }, 20L * 60 * 60, 20L * 60 * 60); // Run every hour
    }
    
    /**
     * Check if a player is a guard (has guard permissions)
     */
    private boolean isPlayerGuard(Player player) {
        return player.hasPermission("edencorrections.guard") || 
               player.hasPermission("edencorrections.duty") ||
               plugin.getGuardRankManager().getPlayerRank(player) != null;
    }
    
    /**
     * Check and give daily token reward to a player
     */
    public void checkAndGiveDailyReward(Player player) {
        if (!isPlayerGuard(player)) {
            return; // Only guards can receive token rewards
        }
        
        UUID playerId = player.getUniqueId();
        
        // Get player's rank
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank == null) return;
        
        // Get last reward time
        long lastReward = getLastRewardTime(playerId);
        long now = System.currentTimeMillis();
        
        // Convert timestamps to LocalDate for date comparison
        LocalDate lastRewardDate = Instant.ofEpochMilli(lastReward)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate currentDate = Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        
        // Check if a day has passed since last reward
        if (lastRewardDate.isBefore(currentDate)) {
            // Get daily token amount for rank
            int dailyTokens = shopConfig.tokenEarnings.dailyLogin;
            if (dailyTokens > 0) {
                addTokens(playerId, dailyTokens);
                sendTokenMessage(player, dailyTokens, "Daily login bonus");
                
                // Update last reward time
                setLastRewardTime(playerId, now);
            }
        }
    }
    
    // --- Internal Token API (No external dependencies) ---
    
    /**
     * Get a player's token balance (guards only)
     */
    public int getTokens(UUID playerId) {
        if (sqliteStorage != null) {
            return sqliteStorage.getTokens(playerId);
        } else {
            return tokenBalances.getOrDefault(playerId, 0);
        }
    }
    
    /**
     * Set a player's token balance (guards only, with validation)
     */
    public void setTokens(UUID playerId, int amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !isPlayerGuard(player)) {
            plugin.getLogger().warning("Attempted to set tokens for non-guard player: " + player.getName());
            return;
        }
        
        amount = Math.max(0, amount);
        
        if (sqliteStorage != null) {
            sqliteStorage.setTokens(playerId, amount);
        } else {
            tokenBalances.put(playerId, amount);
            saveTokenBalancesToFile();
        }
    }
    
    /**
     * Add tokens to a player (guards only)
     */
    public void addTokens(UUID playerId, int amount) {
        if (amount <= 0) return;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !isPlayerGuard(player)) {
            plugin.getLogger().warning("Attempted to give tokens to non-guard player: " + player.getName());
            return;
        }
        
        if (sqliteStorage != null) {
            sqliteStorage.addTokens(playerId, amount);
        } else {
            int current = getTokens(playerId);
            setTokens(playerId, current + amount);
        }
    }
    
    /**
     * Remove tokens from a player (guards only)
     */
    public boolean removeTokens(UUID playerId, int amount) {
        if (amount <= 0) return true;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !isPlayerGuard(player)) {
            plugin.getLogger().warning("Attempted to remove tokens from non-guard player: " + player.getName());
            return false;
        }
        
        int current = getTokens(playerId);
        if (current < amount) return false;
        
        setTokens(playerId, current - amount);
        return true;
    }
    
    /**
     * Check if a player has enough tokens
     */
    public boolean hasTokens(UUID playerId, int amount) {
        return getTokens(playerId) >= amount;
    }
    
    /**
     * Give tokens to a player with full multiplier calculation
     */
    public void giveTokens(Player player, int baseAmount, String reason) {
        if (player == null) return;
        
        // Calculate final amount with all multipliers
        int finalAmount = calculateTokensWithMultipliers(player, baseAmount);
        
        // Add tokens
        UUID playerId = player.getUniqueId();
        int currentTokens = tokenBalances.getOrDefault(playerId, 0);
        tokenBalances.put(playerId, currentTokens + finalAmount);
        
        // Save to storage
        if (sqliteStorage != null) {
            sqliteStorage.setTokens(playerId, currentTokens + finalAmount);
        }
        
        // Send message if configured
        Component message = MessageUtils.parseMessage(
            shopConfig != null && shopConfig.gui != null ? 
            "<green>+{amount} tokens earned! ({reason})</green>" :
            "<green>+{amount} tokens earned! ({reason})</green>"
        );
        message = message.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(finalAmount)));
        message = message.replaceText(builder -> builder.matchLiteral("{reason}").replacement(reason));
        
        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Gave " + finalAmount + " tokens to " + player.getName() + " (base: " + baseAmount + ") for: " + reason);
        }
    }
    
    /**
     * Calculate tokens with all configured multipliers
     */
    private int calculateTokensWithMultipliers(Player player, int baseAmount) {
        if (shopConfig == null) return baseAmount;
        
        double multiplier = 1.0;
        
        // Rank multiplier
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank != null && shopConfig.multipliers.rankMultipliers.containsKey(rank.toLowerCase())) {
            multiplier *= shopConfig.multipliers.rankMultipliers.get(rank.toLowerCase());
        }
        
        // Time-based multipliers
        LocalTime now = LocalTime.now();
        DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
        
        // Weekend multiplier
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            multiplier *= shopConfig.multipliers.weekend;
        }
        
        // Night shift multiplier (22:00 - 06:00)
        if (now.isAfter(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(6, 0))) {
            multiplier *= shopConfig.multipliers.nightShift;
        }
        
        // Daily streak multiplier
        UUID playerId = player.getUniqueId();
        int streak = dailyStreak.getOrDefault(playerId, 0);
        for (Map.Entry<Integer, Double> entry : shopConfig.multipliers.dailyStreakMultipliers.entrySet()) {
            if (streak >= entry.getKey()) {
                multiplier = Math.max(multiplier, entry.getValue());
            }
        }
        
        return (int) Math.round(baseAmount * multiplier);
    }
    
    /**
     * Process daily login rewards with streak tracking
     */
    public void processDailyLogin(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();
        LocalDate lastReward = lastDailyReward.get(playerId);
        
        if (lastReward == null || !lastReward.equals(today)) {
            // Give daily login tokens
            int dailyTokens = shopConfig.tokenEarnings.dailyLogin;
            giveTokens(player, dailyTokens, "Daily login bonus");
            
            // Update streak
            if (lastReward != null && lastReward.equals(today.minusDays(1))) {
                // Consecutive day - increment streak
                dailyStreak.put(playerId, dailyStreak.getOrDefault(playerId, 0) + 1);
            } else {
                // Reset streak
                dailyStreak.put(playerId, 1);
            }
            
            lastDailyReward.put(playerId, today);
            
            // Save data
            saveTokenData();
        }
    }
    
    /**
     * Award tokens for specific guard activities
     */
    public void awardActivityTokens(Player player, GuardActivity activity) {
        if (shopConfig == null) return;
        
        int baseAmount = 0;
        String reason = "";
        
        switch (activity) {
            case SEARCH:
                baseAmount = shopConfig.tokenEarnings.search;
                reason = "Player search";
                break;
            case SUCCESSFUL_SEARCH:
                baseAmount = shopConfig.tokenEarnings.successfulSearch;
                reason = "Successful contraband search";
                break;
            case METAL_DETECTION:
                baseAmount = shopConfig.tokenEarnings.metalDetection;
                reason = "Metal detection";
                break;
            case DRUG_DETECTION:
                baseAmount = shopConfig.tokenEarnings.drugDetection;
                reason = "Drug detection";
                break;
            case APPREHENSION:
                baseAmount = shopConfig.tokenEarnings.apprehension;
                reason = "Suspect apprehension";
                break;
            case CHASE_COMPLETION:
                baseAmount = shopConfig.tokenEarnings.chaseCompletion;
                reason = "Chase completion";
                break;
            case SOBRIETY_TEST_PASS:
                baseAmount = shopConfig.tokenEarnings.sobrietyTestPass;
                reason = "Sobriety test (passed)";
                break;
            case SOBRIETY_TEST_FAIL:
                baseAmount = shopConfig.tokenEarnings.sobrietyTestFail;
                reason = "Sobriety test (failed)";
                break;
            case WANTED_LEVEL_INCREASE:
                baseAmount = shopConfig.tokenEarnings.wantedLevelIncrease;
                reason = "Wanted level increase";
                break;
            case SUCCESSFUL_JAIL:
                baseAmount = shopConfig.tokenEarnings.successfulJail;
                reason = "Successful jailing";
                break;
            case GUARD_DEATH_COMPENSATION:
                baseAmount = shopConfig.tokenEarnings.guardDeathCompensation;
                reason = "Guard death compensation";
                break;
        }
        
        if (baseAmount > 0) {
            giveTokens(player, baseAmount, reason);
        }
    }
    
    /**
     * Get player's token balance
     */
    public int getPlayerTokens(Player player) {
        return tokenBalances.getOrDefault(player.getUniqueId(), 0);
    }
    
    /**
     * Spend tokens for purchases
     */
    public boolean spendTokens(Player player, int amount, String reason) {
        UUID playerId = player.getUniqueId();
        int currentTokens = tokenBalances.getOrDefault(playerId, 0);
        
        if (currentTokens >= amount) {
            tokenBalances.put(playerId, currentTokens - amount);
            if (sqliteStorage != null) {
                sqliteStorage.setTokens(playerId, currentTokens - amount);
            }
            
            Component message = MessageUtils.parseMessage("<red>-{amount} tokens spent ({reason})</red>");
            message = message.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(amount)));
            message = message.replaceText(builder -> builder.matchLiteral("{reason}").replacement(reason));
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Load token data from storage
     */
    private void loadTokenData() {
        // Implementation would load from storage manager
        // For now, placeholder
    }
    
    /**
     * Save token data to storage
     */
    private void saveTokenData() {
        // Implementation would save to storage manager
        // For now, placeholder
    }
    
    /**
     * Get last reward time for a player
     */
    public long getLastRewardTime(UUID playerId) {
        if (sqliteStorage != null) {
            return sqliteStorage.getLastRewardTime(playerId);
        } else {
            return lastRewardTimes.getOrDefault(playerId, 0L);
        }
    }
    
    /**
     * Set last reward time for a player
     */
    public void setLastRewardTime(UUID playerId, long time) {
        if (sqliteStorage != null) {
            sqliteStorage.setLastRewardTime(playerId, time);
        } else {
            lastRewardTimes.put(playerId, time);
            saveLastRewardTimesToFile();
        }
    }
    
    /**
     * Get all tokens for online guards (for display purposes)
     */
    public Map<UUID, Integer> getOnlineGuardTokens() {
        Map<UUID, Integer> guardTokens = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerGuard(player)) {
                guardTokens.put(player.getUniqueId(), getTokens(player.getUniqueId()));
            }
        }
        return guardTokens;
    }
    
    /**
     * Get total tokens in circulation
     */
    public int getTotalTokensInCirculation() {
        return getOnlineGuardTokens().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
    
    public void shutdown() {
        // Cancel the daily reward task
        if (dailyRewardTask != null && !dailyRewardTask.isCancelled()) {
            dailyRewardTask.cancel();
        }
        
        // Save data if using file storage
        if (sqliteStorage == null) {
            saveTokenBalancesToFile();
            saveLastRewardTimesToFile();
        }
    }

    /**
     * Reload token configurations and restart tasks
     */
    public void reload() {
        // Cancel existing daily reward task
        if (dailyRewardTask != null && !dailyRewardTask.isCancelled()) {
            dailyRewardTask.cancel();
        }
        
        // Save current data
        if (sqliteStorage == null) {
            saveTokenBalancesToFile();
            saveLastRewardTimesToFile();
        }
        
        // Reload configuration
        loadConfiguration();
        
        // Restart daily reward task
        startDailyRewardTask();
        
        this.shopConfig = configManager.getShopConfig();
        
        plugin.getLogger().info("GuardTokenManager reloaded - restarted daily reward tasks");
    }

    /**
     * Guard activity types for token earning
     */
    public enum GuardActivity {
        SEARCH,
        SUCCESSFUL_SEARCH,
        METAL_DETECTION,
        DRUG_DETECTION,
        APPREHENSION,
        CHASE_COMPLETION,
        SOBRIETY_TEST_PASS,
        SOBRIETY_TEST_FAIL,
        WANTED_LEVEL_INCREASE,
        SUCCESSFUL_JAIL,
        GUARD_DEATH_COMPENSATION
    }

    /**
     * Send token message to player
     */
    private void sendTokenMessage(Player player, int amount, String reason) {
        String msg = amount >= 0 ?
            "<green>You received " + amount + " guard tokens! (" + reason + ")</green>" :
            "<red>Spent " + (-amount) + " guard tokens! (" + reason + ")</red>";
        Component message = MessageUtils.parseMessage(msg);
        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info((amount >= 0 ? "Gave " : "Took ") + Math.abs(amount) + " tokens to/from " + player.getName() + " for: " + reason);
        }
    }

    /**
     * Take tokens from a player (alias for spendTokens for backward compatibility)
     */
    public boolean takeTokens(Player player, int amount, String reason) {
        return spendTokens(player, amount, reason);
    }

    /**
     * Check if player can receive daily reward
     */
    public boolean canReceiveDailyReward(Player player) {
        if (!isPlayerGuard(player)) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();
        LocalDate lastReward = lastDailyReward.get(playerId);
        
        return lastReward == null || !lastReward.equals(today);
    }

    /**
     * Get time until next daily reward in milliseconds
     */
    public long getTimeUntilNextReward(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDate lastReward = lastDailyReward.get(playerId);
        
        if (lastReward == null) {
            return 0; // Can claim immediately
        }
        
        LocalDate today = LocalDate.now();
        if (!lastReward.equals(today)) {
            return 0; // Can claim immediately
        }
        
        // Calculate time until next day
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, tomorrow).toMillis();
    }

    /**
     * Convert off-duty minutes to tokens for guards
     */
    public boolean convertOffDutyMinutesToTokens(Player player, int minutes) {
        if (!isPlayerGuard(player)) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Token conversion is only available to guards!</red>")));
            return false;
        }

        UUID playerId = player.getUniqueId();
        
        // Check if player has enough off-duty minutes
        int currentMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
        if (currentMinutes < minutes) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You only have " + currentMinutes + " off-duty minutes available!</red>")));
            return false;
        }

        // Calculate tokens based on conversion rate
        int tokensToGive = minutes * shopConfig.conversion.offDutyToTokensRate / 60; // Rate per hour, convert to minutes
        
        if (tokensToGive <= 0) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Not enough minutes to convert!</red>")));
            return false;
        }

        // Remove off-duty minutes
        plugin.getDutyManager().setOffDutyMinutes(playerId, currentMinutes - minutes);
        
        // Give tokens
        giveTokens(player, tokensToGive, "Converted " + minutes + " off-duty minutes");
        
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Converted " + minutes + " minutes to " + tokensToGive + " tokens!</green>")));
        
        return true;
    }
} 