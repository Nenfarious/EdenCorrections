package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardTokenManager {
    private final EdenCorrections plugin;
    private final File tokenFile;
    private FileConfiguration tokenConfig;
    
    // Internal token balances
    private final Map<UUID, Integer> tokenBalances = new ConcurrentHashMap<>();
    // Cache for last reward times
    private final Map<UUID, Long> lastRewardTimes = new ConcurrentHashMap<>();
    
    private final SQLiteStorage sqliteStorage;
    
    public GuardTokenManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.tokenFile = new File(plugin.getDataFolder(), "guard_tokens.yml");
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        loadConfiguration();
        startDailyRewardTask();
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
        loadTokenBalances();
        loadLastRewardTimes();
    }
    
    private void loadTokenBalances() {
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
    
    private void saveTokenBalances() {
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
    
    private void loadLastRewardTimes() {
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
    
    private void saveLastRewardTimes() {
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
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkAndGiveDailyReward(player);
            }
        }, 20L * 60 * 60, 20L * 60 * 60); // Run every hour
    }
    
    /**
     * Check and give daily token reward to a player
     */
    public void checkAndGiveDailyReward(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Get player's rank
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank == null) return;
        
        // Get last reward time
        long lastReward = lastRewardTimes.getOrDefault(playerId, 0L);
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
            int dailyTokens = plugin.getConfig().getInt("guard-progression.perks." + rank.toLowerCase() + ".daily-tokens", 0);
            if (dailyTokens > 0) {
                addTokens(playerId, dailyTokens);
                sendTokenMessage(player, dailyTokens, "Daily rank bonus");
                
                // Update last reward time
                lastRewardTimes.put(playerId, now);
                saveLastRewardTimes();
                saveTokenBalances();
            }
        }
    }
    
    // --- Token API ---
    public int getTokens(UUID playerId) {
        return sqliteStorage.getTokens(playerId);
    }
    
    public void setTokens(UUID playerId, int amount) {
        sqliteStorage.setTokens(playerId, Math.max(0, amount));
    }
    
    public void addTokens(UUID playerId, int amount) {
        if (amount <= 0) return;
        sqliteStorage.addTokens(playerId, amount);
    }
    
    public boolean removeTokens(UUID playerId, int amount) {
        if (amount <= 0) return true;
        int current = getTokens(playerId);
        if (current < amount) return false;
        sqliteStorage.setTokens(playerId, current - amount);
        return true;
    }
    
    public boolean hasTokens(UUID playerId, int amount) {
        return getTokens(playerId) >= amount;
    }
    
    public void giveTokens(Player player, int amount, String reason) {
        addTokens(player.getUniqueId(), amount);
        sendTokenMessage(player, amount, reason);
    }
    
    public boolean takeTokens(Player player, int amount, String reason) {
        if (removeTokens(player.getUniqueId(), amount)) {
            sendTokenMessage(player, -amount, reason);
            return true;
        } else {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Not enough tokens!</red>")));
            return false;
        }
    }
    
    private void sendTokenMessage(Player player, int amount, String reason) {
        String msg = amount >= 0 ?
            "<green>You received " + amount + " tokens! (" + reason + ")</green>" :
            "<red>Spent " + (-amount) + " tokens! (" + reason + ")</red>";
        Component message = MessageUtils.parseMessage(msg);
        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info((amount >= 0 ? "Gave " : "Took ") + Math.abs(amount) + " tokens to/from " + player.getName() + " for: " + reason);
        }
    }
    
    /**
     * Check if a player can receive their daily reward
     */
    public boolean canReceiveDailyReward(Player player) {
        UUID playerId = player.getUniqueId();
        long lastReward = lastRewardTimes.getOrDefault(playerId, 0L);
        
        LocalDate lastRewardDate = Instant.ofEpochMilli(lastReward)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate currentDate = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        
        return lastRewardDate.isBefore(currentDate);
    }
    
    /**
     * Get time until next daily reward
     */
    public long getTimeUntilNextReward(Player player) {
        UUID playerId = player.getUniqueId();
        long lastReward = lastRewardTimes.getOrDefault(playerId, 0L);
        
        if (lastReward == 0) return 0;
        
        // Get next reward time (midnight of next day)
        LocalDate nextRewardDate = Instant.ofEpochMilli(lastReward)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1);
        
        long nextRewardTime = nextRewardDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        
        return Math.max(0, nextRewardTime - System.currentTimeMillis());
    }
    
    public long getLastRewardTime(UUID playerId) {
        return sqliteStorage.getLastRewardTime(playerId);
    }
    
    public void setLastRewardTime(UUID playerId, long time) {
        sqliteStorage.setLastRewardTime(playerId, time);
    }
    
    public void shutdown() {
        saveLastRewardTimes();
        saveTokenBalances();
    }
} 