package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Optimized data manager that handles all plugin data with minimal overhead
 * Implements StorageManager interface for compatibility with existing code
 */
public class DataManager implements StorageManager {
    private final EdenCorrections plugin;

    // Use ConcurrentHashMap for thread safety without locking overhead
    private final Map<UUID, Boolean> dutyStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dutyStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> offDutyMinutes = new ConcurrentHashMap<>();

    // Activity tracking maps
    private final Map<UUID, Integer> searchCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> successfulSearchCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> metalDetectCount = new ConcurrentHashMap<>();

    // Track which data has changed to optimize saves
    private final Map<UUID, Boolean> dirtyData = new ConcurrentHashMap<>();

    // Data files
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Autosave task
    private BukkitTask autoSaveTask;
    private final int autoSaveInterval;

    public DataManager(EdenCorrections plugin) {
        this.plugin = plugin;

        // Get autosave interval from config (default: 5 minutes)
        autoSaveInterval = plugin.getConfig().getInt("storage.autosave-interval", 5) * 20 * 60;

        // Initialize data file
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create data.yml", e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Initialize the storage system
     */
    @Override
    public void initialize() {
        // Load all data
        loadAllData();

        // Start autosave task
        startAutoSaveTask();

        plugin.getLogger().info("DataManager initialized successfully");
    }

    /**
     * Reload the storage system
     */
    @Override
    public void reload() {
        // Cancel existing autosave task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // Save any pending changes
        saveAll();

        // Reload configuration
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load all data
        loadAllData();

        // Restart autosave task
        startAutoSaveTask();

        plugin.getLogger().info("DataManager reloaded successfully");
    }

    /**
     * Shutdown the storage system
     */
    @Override
    public void shutdown() {
        // Cancel autosave task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // Save all data
        saveAll();

        plugin.getLogger().info("DataManager shutdown successfully");
    }

    /**
     * Load all plugin data from storage
     */
    private void loadAllData() {
        // Clear existing data
        dutyStatus.clear();
        dutyStartTimes.clear();
        offDutyMinutes.clear();
        searchCount.clear();
        successfulSearchCount.clear();
        killCount.clear();
        metalDetectCount.clear();

        // Load duty status
        if (dataConfig.contains("duty_status")) {
            for (String key : dataConfig.getConfigurationSection("duty_status").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    boolean status = dataConfig.getBoolean("duty_status." + key);
                    dutyStatus.put(playerId, status);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load duty start times
        if (dataConfig.contains("duty_start_times")) {
            for (String key : dataConfig.getConfigurationSection("duty_start_times").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    long startTime = dataConfig.getLong("duty_start_times." + key);
                    dutyStartTimes.put(playerId, startTime);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load off-duty minutes
        if (dataConfig.contains("off_duty_minutes")) {
            for (String key : dataConfig.getConfigurationSection("off_duty_minutes").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int minutes = dataConfig.getInt("off_duty_minutes." + key);
                    offDutyMinutes.put(playerId, minutes);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load activity data
        loadActivityData();
    }

    /**
     * Load activity tracking data
     */
    private void loadActivityData() {
        // Load search count
        if (dataConfig.contains("search_count")) {
            for (String key : dataConfig.getConfigurationSection("search_count").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int count = dataConfig.getInt("search_count." + key);
                    searchCount.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load successful search count
        if (dataConfig.contains("successful_search_count")) {
            for (String key : dataConfig.getConfigurationSection("successful_search_count").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int count = dataConfig.getInt("successful_search_count." + key);
                    successfulSearchCount.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load kill count
        if (dataConfig.contains("kill_count")) {
            for (String key : dataConfig.getConfigurationSection("kill_count").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int count = dataConfig.getInt("kill_count." + key);
                    killCount.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }

        // Load metal detect count
        if (dataConfig.contains("metal_detect_count")) {
            for (String key : dataConfig.getConfigurationSection("metal_detect_count").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int count = dataConfig.getInt("metal_detect_count." + key);
                    metalDetectCount.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in data.yml: " + key);
                }
            }
        }
    }

    /**
     * Start the autosave task
     */
    private void startAutoSaveTask() {
        // Cancel existing task if running
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // Start new task
        autoSaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::saveAllDirtyData,
                autoSaveInterval,
                autoSaveInterval
        );
    }

    /**
     * Save all dirty data to storage
     */
    private void saveAllDirtyData() {
        if (dirtyData.isEmpty()) {
            return; // Nothing to save
        }

        try {
            // Save each player's data that has been marked as dirty
            for (UUID playerId : dirtyData.keySet()) {
                // Duty status
                if (dutyStatus.containsKey(playerId)) {
                    dataConfig.set("duty_status." + playerId.toString(), dutyStatus.get(playerId));
                }

                // Duty start times
                if (dutyStartTimes.containsKey(playerId)) {
                    dataConfig.set("duty_start_times." + playerId.toString(), dutyStartTimes.get(playerId));
                }

                // Off-duty minutes
                if (offDutyMinutes.containsKey(playerId)) {
                    dataConfig.set("off_duty_minutes." + playerId.toString(), offDutyMinutes.get(playerId));
                }

                // Activity data
                saveActivityData(playerId);
            }

            // Save the file
            dataConfig.save(dataFile);

            // Clear dirty flags
            dirtyData.clear();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data.yml", e);
        }
    }

    /**
     * Save activity data for a player
     */
    private void saveActivityData(UUID playerId) {
        // Search count
        if (searchCount.containsKey(playerId)) {
            dataConfig.set("search_count." + playerId.toString(), searchCount.get(playerId));
        }

        // Successful search count
        if (successfulSearchCount.containsKey(playerId)) {
            dataConfig.set("successful_search_count." + playerId.toString(), successfulSearchCount.get(playerId));
        }

        // Kill count
        if (killCount.containsKey(playerId)) {
            dataConfig.set("kill_count." + playerId.toString(), killCount.get(playerId));
        }

        // Metal detect count
        if (metalDetectCount.containsKey(playerId)) {
            dataConfig.set("metal_detect_count." + playerId.toString(), metalDetectCount.get(playerId));
        }
    }

    /**
     * Mark a player's data as dirty (needing to be saved)
     */
    private void markDirty(UUID playerId) {
        dirtyData.put(playerId, true);
    }

    /**
     * Force save all data immediately
     */
    public void saveAll() {
        // Mark all players with data as dirty
        for (UUID playerId : dutyStatus.keySet()) markDirty(playerId);
        for (UUID playerId : dutyStartTimes.keySet()) markDirty(playerId);
        for (UUID playerId : offDutyMinutes.keySet()) markDirty(playerId);
        for (UUID playerId : searchCount.keySet()) markDirty(playerId);
        for (UUID playerId : successfulSearchCount.keySet()) markDirty(playerId);
        for (UUID playerId : killCount.keySet()) markDirty(playerId);
        for (UUID playerId : metalDetectCount.keySet()) markDirty(playerId);

        // Save all dirty data
        saveAllDirtyData();
    }

    // StorageManager interface implementation
    /**
     * Save a player's duty status
     */
    @Override
    public void saveDutyStatus(UUID playerId, boolean isOnDuty) {
        dutyStatus.put(playerId, isOnDuty);
        markDirty(playerId);
    }

    /**
     * Save all players' duty status
     */
    @Override
    public void saveDutyStatus(Map<UUID, Boolean> dutyStatusMap) {
        dutyStatus.putAll(dutyStatusMap);
        for (UUID playerId : dutyStatusMap.keySet()) {
            markDirty(playerId);
        }
    }

    /**
     * Load all players' duty status
     */
    @Override
    public Map<UUID, Boolean> loadDutyStatus() {
        return new HashMap<>(dutyStatus);
    }

    /**
     * Save a player's duty start time
     */
    @Override
    public void saveDutyStartTime(UUID playerId, long startTime) {
        dutyStartTimes.put(playerId, startTime);
        markDirty(playerId);
    }

    /**
     * Save all players' duty start times
     */
    @Override
    public void saveDutyStartTimes(Map<UUID, Long> dutyStartTimesMap) {
        dutyStartTimes.putAll(dutyStartTimesMap);
        for (UUID playerId : dutyStartTimesMap.keySet()) {
            markDirty(playerId);
        }
    }

    /**
     * Load all players' duty start times
     */
    @Override
    public Map<UUID, Long> loadDutyStartTimes() {
        return new HashMap<>(dutyStartTimes);
    }

    /**
     * Save a player's off-duty minutes
     */
    @Override
    public void saveOffDutyMinutes(UUID playerId, int minutes) {
        offDutyMinutes.put(playerId, minutes);
        markDirty(playerId);
    }

    /**
     * Save all players' off-duty minutes
     */
    @Override
    public void saveOffDutyMinutes(Map<UUID, Integer> offDutyMinutesMap) {
        offDutyMinutes.putAll(offDutyMinutesMap);
        for (UUID playerId : offDutyMinutesMap.keySet()) {
            markDirty(playerId);
        }
    }

    /**
     * Load all players' off-duty minutes
     */
    @Override
    public Map<UUID, Integer> loadOffDutyMinutes() {
        return new HashMap<>(offDutyMinutes);
    }

    /**
     * Get a player's search count
     */
    public int getSearchCount(UUID playerId) {
        return searchCount.getOrDefault(playerId, 0);
    }

    /**
     * Increment a player's search count
     */
    public void incrementSearchCount(UUID playerId) {
        searchCount.put(playerId, getSearchCount(playerId) + 1);
        markDirty(playerId);
    }

    /**
     * Get a player's successful search count
     */
    public int getSuccessfulSearchCount(UUID playerId) {
        return successfulSearchCount.getOrDefault(playerId, 0);
    }

    /**
     * Increment a player's successful search count
     */
    public void incrementSuccessfulSearchCount(UUID playerId) {
        successfulSearchCount.put(playerId, getSuccessfulSearchCount(playerId) + 1);
        markDirty(playerId);
    }

    /**
     * Get a player's kill count
     */
    public int getKillCount(UUID playerId) {
        return killCount.getOrDefault(playerId, 0);
    }

    /**
     * Increment a player's kill count
     */
    public void incrementKillCount(UUID playerId) {
        killCount.put(playerId, getKillCount(playerId) + 1);
        markDirty(playerId);
    }

    /**
     * Get a player's metal detect count
     */
    public int getMetalDetectCount(UUID playerId) {
        return metalDetectCount.getOrDefault(playerId, 0);
    }

    /**
     * Increment a player's metal detect count
     */
    public void incrementMetalDetectCount(UUID playerId) {
        metalDetectCount.put(playerId, getMetalDetectCount(playerId) + 1);
        markDirty(playerId);
    }

    /**
     * Reset a player's activity counts
     */
    public void resetActivityCounts(UUID playerId) {
        searchCount.put(playerId, 0);
        successfulSearchCount.put(playerId, 0);
        killCount.put(playerId, 0);
        metalDetectCount.put(playerId, 0);
        markDirty(playerId);
    }
}