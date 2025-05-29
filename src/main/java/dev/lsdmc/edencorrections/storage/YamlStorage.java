package dev.lsdmc.edencorrections.storage;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.StorageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class YamlStorage implements StorageManager {
    private final EdenCorrections plugin;
    private File dutyFile;
    private FileConfiguration dutyConfig;

    public YamlStorage(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize duty file
        dutyFile = new File(plugin.getDataFolder(), "duty_data.yml");
        if (!dutyFile.exists()) {
            try {
                dutyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create duty_data.yml", e);
            }
        }

        // Load duty config
        dutyConfig = YamlConfiguration.loadConfiguration(dutyFile);
    }

    @Override
    public void reload() {
        // Reload duty config
        dutyConfig = YamlConfiguration.loadConfiguration(dutyFile);
    }

    @Override
    public void shutdown() {
        // Save duty config
        try {
            dutyConfig.save(dutyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty_data.yml", e);
        }
    }

    @Override
    public void saveDutyStatus(UUID playerId, boolean isOnDuty) {
        dutyConfig.set("duty_status." + playerId.toString(), isOnDuty);
        saveConfig();
    }

    @Override
    public void saveDutyStatus(Map<UUID, Boolean> dutyStatus) {
        for (Map.Entry<UUID, Boolean> entry : dutyStatus.entrySet()) {
            dutyConfig.set("duty_status." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    @Override
    public Map<UUID, Boolean> loadDutyStatus() {
        Map<UUID, Boolean> dutyStatus = new HashMap<>();

        if (dutyConfig.contains("duty_status")) {
            for (String key : dutyConfig.getConfigurationSection("duty_status").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    boolean isOnDuty = dutyConfig.getBoolean("duty_status." + key);
                    dutyStatus.put(playerId, isOnDuty);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in duty_data.yml: " + key);
                }
            }
        }

        return dutyStatus;
    }

    @Override
    public void saveDutyStartTime(UUID playerId, long startTime) {
        dutyConfig.set("duty_start_times." + playerId.toString(), startTime);
        saveConfig();
    }

    @Override
    public void saveDutyStartTimes(Map<UUID, Long> dutyStartTimes) {
        for (Map.Entry<UUID, Long> entry : dutyStartTimes.entrySet()) {
            dutyConfig.set("duty_start_times." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    @Override
    public Map<UUID, Long> loadDutyStartTimes() {
        Map<UUID, Long> dutyStartTimes = new HashMap<>();

        if (dutyConfig.contains("duty_start_times")) {
            for (String key : dutyConfig.getConfigurationSection("duty_start_times").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    long startTime = dutyConfig.getLong("duty_start_times." + key);
                    dutyStartTimes.put(playerId, startTime);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in duty_data.yml: " + key);
                }
            }
        }

        return dutyStartTimes;
    }

    @Override
    public void saveOffDutyMinutes(UUID playerId, int minutes) {
        dutyConfig.set("off_duty_minutes." + playerId.toString(), minutes);
        saveConfig();
    }

    @Override
    public void saveOffDutyMinutes(Map<UUID, Integer> offDutyMinutes) {
        for (Map.Entry<UUID, Integer> entry : offDutyMinutes.entrySet()) {
            dutyConfig.set("off_duty_minutes." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    @Override
    public Map<UUID, Integer> loadOffDutyMinutes() {
        Map<UUID, Integer> offDutyMinutes = new HashMap<>();

        if (dutyConfig.contains("off_duty_minutes")) {
            for (String key : dutyConfig.getConfigurationSection("off_duty_minutes").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    int minutes = dutyConfig.getInt("off_duty_minutes." + key);
                    offDutyMinutes.put(playerId, minutes);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in duty_data.yml: " + key);
                }
            }
        }

        return offDutyMinutes;
    }

    // Activity tracking methods (not supported in YamlStorage yet)
    @Override
    public int getSearchCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void incrementSearchCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public int getSuccessfulSearchCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void incrementSuccessfulSearchCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public int getKillCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void incrementKillCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public int getMetalDetectCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void incrementMetalDetectCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public int getApprehensionCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void incrementApprehensionCount(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }
    @Override
    public void resetActivityCounts(UUID playerId) { throw new UnsupportedOperationException("Activity tracking not supported in YamlStorage yet"); }

    private void saveConfig() {
        try {
            dutyConfig.save(dutyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty_data.yml", e);
        }
    }
}