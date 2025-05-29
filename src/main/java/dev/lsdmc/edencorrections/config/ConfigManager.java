package dev.lsdmc.edencorrections.config;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Centralized configuration manager for EdenCorrections
 * Handles loading, validation, and access to all configuration values
 */
public class ConfigManager {
    private final EdenCorrections plugin;
    private FileConfiguration config;

    // Storage Configuration
    private int autoSaveInterval;
    private String storageType;
    private StorageConfig storageConfig;

    // Debug Configuration
    private boolean debugEnabled;
    private String debugLevel;
    private boolean logLootGeneration;
    private boolean logRankDetection;

    // Duty Configuration
    private DutyConfig dutyConfig;

    // Guard Systems Configuration
    private GuardBuffConfig guardBuffConfig;
    private GuardRestrictionConfig guardRestrictionConfig;
    private GuardLootConfig guardLootConfig;
    private GuardPenaltyConfig guardPenaltyConfig;

    // Conversion Configuration
    private ConversionConfig conversionConfig;

    // GUI Configuration
    private GuiConfig guiConfig;

    // Messages Configuration
    private MessagesConfig messagesConfig;

    public ConfigManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.storageConfig = new StorageConfig();
        this.dutyConfig = new DutyConfig();
        this.guardBuffConfig = new GuardBuffConfig();
        this.guardRestrictionConfig = new GuardRestrictionConfig();
        this.guardLootConfig = new GuardLootConfig();
        this.guardPenaltyConfig = new GuardPenaltyConfig();
        this.conversionConfig = new ConversionConfig();
        this.guiConfig = new GuiConfig();
        this.messagesConfig = new MessagesConfig();

        loadConfiguration();
    }

    /**
     * Load the configuration from the config file
     */
    public void loadConfiguration() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        plugin.getLogger().info("Loading EdenCorrections configuration...");

        try {
            loadStorageConfig();
            loadDebugConfig();
            loadDutyConfig();
            loadGuardBuffConfig();
            loadGuardRestrictionConfig();
            loadGuardLootConfig();
            loadGuardPenaltyConfig();
            loadConversionConfig();
            loadGuiConfig();
            loadMessagesConfig();

            plugin.getLogger().info("Configuration loaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading configuration", e);
        }
    }

    /**
     * Reload the configuration
     */
    public void reload() {
        plugin.getLogger().info("Reloading EdenCorrections configuration...");
        loadConfiguration();
    }

    /**
     * Validate that all required configuration sections exist
     */
    public boolean validateConfiguration() {
        boolean valid = true;

        // Check required sections
        String[] requiredSections = {
                "storage", "debug", "duty", "guard-buff", "guard-restrictions",
                "guard-loot", "guard-death-penalties", "conversion", "gui", "messages"
        };

        for (String section : requiredSections) {
            if (!config.contains(section)) {
                plugin.getLogger().warning("Missing required configuration section: " + section);
                valid = false;
            }
        }

        return valid;
    }

    // Storage Configuration
    private void loadStorageConfig() {
        autoSaveInterval = config.getInt("storage.autosave-interval", 5);
        storageType = config.getString("storage.type", "yaml").toLowerCase();

        // MySQL Configuration
        storageConfig.mysqlHost = config.getString("storage.mysql.host", "localhost");
        storageConfig.mysqlPort = config.getInt("storage.mysql.port", 3306);
        storageConfig.mysqlDatabase = config.getString("storage.mysql.database", "edencorrections");
        storageConfig.mysqlUsername = config.getString("storage.mysql.username", "root");
        storageConfig.mysqlPassword = config.getString("storage.mysql.password", "password");
        storageConfig.mysqlTablePrefix = config.getString("storage.mysql.table-prefix", "ec_");

        // SQLite Configuration
        storageConfig.sqliteFile = config.getString("storage.sqlite.file", "database.db");
    }

    // Debug Configuration
    private void loadDebugConfig() {
        debugEnabled = config.getBoolean("debug.enabled", true);
        debugLevel = config.getString("debug.level", "INFO");
        logLootGeneration = config.getBoolean("debug.log-loot-generation", true);
        logRankDetection = config.getBoolean("debug.log-rank-detection", true);
    }

    // Duty Configuration
    private void loadDutyConfig() {
        // Region configuration
        dutyConfig.regionEnabled = config.getBoolean("duty.region.enabled", true);
        dutyConfig.regionName = config.getString("duty.region.name", "locker_room");

        // NPC configuration
        dutyConfig.npcEnabled = config.getBoolean("duty.npc.enabled", true);
        dutyConfig.npcUseCitizens = config.getBoolean("duty.npc.use-citizens", true);
        dutyConfig.npcInteractionRadius = config.getInt("duty.npc.interaction-radius", 3);
        dutyConfig.npcName = config.getString("duty.npc.name", "<aqua><bold>Supervisor</bold></aqua>");

        // Basic duty settings
        dutyConfig.clearInventory = config.getBoolean("duty.clear-inventory", true);
        dutyConfig.broadcast = config.getBoolean("duty.broadcast", true);
        dutyConfig.thresholdMinutes = config.getInt("duty.threshold-minutes", 15);
        dutyConfig.rewardMinutes = config.getInt("duty.reward-minutes", 30);
        dutyConfig.maxOffDutyTime = config.getInt("duty.max-off-duty-time", 4320);
        dutyConfig.immobilizationDuration = config.getInt("duty.immobilization-duration", 30);

        // Activity rewards
        dutyConfig.activityRewards = new HashMap<>();
        ConfigurationSection activitySection = config.getConfigurationSection("duty.activity-rewards");
        if (activitySection != null) {
            for (String key : activitySection.getKeys(false)) {
                dutyConfig.activityRewards.put(key, activitySection.getInt(key));
            }
        }

        // Sounds
        dutyConfig.onDutySound = config.getString("duty.on-duty-sound", "minecraft:block.note_block.pling");
        dutyConfig.offDutySound = config.getString("duty.off-duty-sound", "minecraft:block.note_block.bass");

        // Rank kits
        dutyConfig.rankKits = new HashMap<>();
        ConfigurationSection rankKitsSection = config.getConfigurationSection("duty.rank-kits");
        if (rankKitsSection != null) {
            for (String key : rankKitsSection.getKeys(false)) {
                dutyConfig.rankKits.put(key, rankKitsSection.getString(key));
            }
        }

        // Commands
        dutyConfig.onDutyCommands = config.getStringList("duty.commands.on-duty");
        dutyConfig.offDutyCommands = config.getStringList("duty.commands.off-duty");

        // Ensure lists are never null
        if (dutyConfig.onDutyCommands == null) dutyConfig.onDutyCommands = new ArrayList<>();
        if (dutyConfig.offDutyCommands == null) dutyConfig.offDutyCommands = new ArrayList<>();
    }

    // Guard Buff Configuration
    private void loadGuardBuffConfig() {
        guardBuffConfig.enabled = config.getBoolean("guard-buff.enabled", true);
        guardBuffConfig.loneGuardEnabled = config.getBoolean("guard-buff.lone-guard.enabled", true);
        guardBuffConfig.loneGuardEffects = config.getStringList("guard-buff.lone-guard.effects");
        guardBuffConfig.loneGuardRemovalDelay = config.getInt("guard-buff.lone-guard.removal-delay", 10);

        // Messages
        guardBuffConfig.applyMessage = config.getString("guard-buff.lone-guard.messages.apply",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You are the only guard online! You now have damage resistance!</red>");
        guardBuffConfig.removeWarningMessage = config.getString("guard-buff.lone-guard.messages.remove-warning",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>Another guard has logged in! Removing effects in {seconds} seconds!</red>");
        guardBuffConfig.removedMessage = config.getString("guard-buff.lone-guard.messages.removed",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>Your special effects have been removed!</red>");
    }

    // Guard Restriction Configuration
    private void loadGuardRestrictionConfig() {
        guardRestrictionConfig.enabled = config.getBoolean("guard-restrictions.block-breaking.enabled", true);
        guardRestrictionConfig.blockBreaking.enabled = config.getBoolean("guard-restrictions.block-breaking.enabled", true);
        guardRestrictionConfig.blockBreaking.restrictedBlocks = config.getStringList("guard-restrictions.block-breaking.restricted-blocks");
        guardRestrictionConfig.blockBreaking.exceptions = config.getStringList("guard-restrictions.block-breaking.exceptions");
        guardRestrictionConfig.blockBreaking.exemptRegions = config.getStringList("guard-restrictions.block-breaking.exempt-regions");
        guardRestrictionConfig.blockBreaking.message = config.getString("guard-restrictions.block-breaking.message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You are not allowed to farm as a guard!</red>");

        guardRestrictionConfig.movement.enabled = config.getBoolean("guard-restrictions.movement.enabled", true);
        guardRestrictionConfig.movement.restrictedRegions = config.getStringList("guard-restrictions.movement.restricted-regions");
        guardRestrictionConfig.movement.message = config.getString("guard-restrictions.movement.message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You are not allowed to move as a guard!</red>");

        guardRestrictionConfig.commands.enabled = config.getBoolean("guard-restrictions.commands.enabled", true);
        guardRestrictionConfig.commands.restrictedCommands = config.getStringList("guard-restrictions.commands.restricted-commands");
        guardRestrictionConfig.commands.message = config.getString("guard-restrictions.commands.message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You are not allowed to use these commands as a guard!</red>");
    }

    // Guard Loot Configuration
    private void loadGuardLootConfig() {
        guardLootConfig.enabled = config.getBoolean("guard-loot.enabled", true);
        guardLootConfig.cooldown = config.getInt("guard-loot.cooldown", 600);
        guardLootConfig.cooldownMessage = config.getString("guard-loot.cooldown-message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <aqua>{victim}</aqua> <gray>has their guard loot on cooldown! </gray><aqua>({time}s)</aqua>");

        // Token reward configuration
        guardLootConfig.tokenRewardEnabled = config.getBoolean("guard-loot.token-reward.enabled", true);
        guardLootConfig.tokenRewardAmount = config.getInt("guard-loot.token-reward.amount", 200);
        guardLootConfig.tokenRewardMessage = config.getString("guard-loot.token-reward.message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You fought bravely in combat and have received {tokens} tokens!</red>");
        guardLootConfig.tokenRewardCommand = config.getString("guard-loot.token-reward.command", "tokenmanager give {player} {amount}");

        // Load loot tables for each rank
        guardLootConfig.rankLootTables = new HashMap<>();
        ConfigurationSection ranksSection = config.getConfigurationSection("guard-loot.ranks");
        if (ranksSection != null) {
            for (String rank : ranksSection.getKeys(false)) {
                ConfigurationSection rankSection = ranksSection.getConfigurationSection(rank);
                if (rankSection != null) {
                    guardLootConfig.rankLootTables.put(rank, rankSection);
                }
            }
        }
    }

    // Guard Penalty Configuration
    private void loadGuardPenaltyConfig() {
        guardPenaltyConfig.enabled = config.getBoolean("guard-death-penalties.enabled", true);
        guardPenaltyConfig.lockTime = config.getInt("guard-death-penalties.lock-time", 60);
        guardPenaltyConfig.restrictedRegions = config.getStringList("guard-death-penalties.restricted-regions");
        guardPenaltyConfig.message = config.getString("guard-death-penalties.message",
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <gray>You cannot leave for </gray><red>{time} seconds</red> <gray>for dying!</gray>");
    }

    // Conversion Configuration
    private void loadConversionConfig() {
        conversionConfig.minimumMinutes = config.getInt("conversion.tokens.minimum", 5);
        conversionConfig.tokensPerMinuteRatio = config.getInt("conversion.tokens.ratio", 100);
        conversionConfig.tokenCommand = config.getString("conversion.tokens.command", "tokenmanager give {player} {amount}");
    }

    // GUI Configuration
    private void loadGuiConfig() {
        guiConfig.showOnJoin = config.getBoolean("gui.show-on-join", false);
        guiConfig.joinDelay = config.getLong("gui.join-delay", 20L);
        guiConfig.openSound = config.getString("gui.open-sound", "minecraft:block.chest.open");
        guiConfig.useEnhancedGui = config.getBoolean("gui.use-enhanced-gui", true);
        guiConfig.continuingDutyMessage = config.getString("gui.messages.continuing-duty", "<green>You're continuing your guard shift!</green>");
        guiConfig.remainingOffDutyMessage = config.getString("gui.messages.remaining-off-duty", "<yellow>You're remaining off duty.</yellow>");
    }

    // Messages Configuration
    private void loadMessagesConfig() {
        messagesConfig.prefix = config.getString("messages.prefix", "<gold>[Corrections]</gold> ");
        messagesConfig.noPermission = config.getString("messages.no-permission", "<red>You don't have permission to do that!</red>");
        messagesConfig.notInRegion = config.getString("messages.not-in-region", "<red>You must be in the duty room to go on/off duty!</red>");
        messagesConfig.notInArea = config.getString("messages.not-in-area", "<red>You must be in a designated duty area to go on/off duty!</red>");
        messagesConfig.alreadyOnDuty = config.getString("messages.already-on-duty", "<red>You are already on duty!</red>");
        messagesConfig.alreadyOffDuty = config.getString("messages.already-off-duty", "<red>You are already off duty!</red>");
        messagesConfig.onDuty = config.getString("messages.on-duty", "<green>You are now on guard duty!</green>");
        messagesConfig.offDuty = config.getString("messages.off-duty", "<yellow>You are now off duty.</yellow>");
        messagesConfig.offDutyReward = config.getString("messages.off-duty-reward", "<green>You've earned {minutes} minutes of off-duty time!</green>");
        messagesConfig.timeRemaining = config.getString("messages.time-remaining", "<yellow>You have {minutes} minutes of off-duty time remaining.</yellow>");
        messagesConfig.timeAdded = config.getString("messages.time-added", "<green>Added {minutes} minutes to {player}'s off-duty time.</green>");
        messagesConfig.timeSet = config.getString("messages.time-set", "<green>Set {player}'s off-duty time to {minutes} minutes.</green>");
        messagesConfig.convertedTime = config.getString("messages.converted-time", "<green>Converted {minutes} minutes to {tokens} tokens!</green>");
        messagesConfig.broadcastOnDuty = config.getString("messages.broadcast-on-duty", "");
        messagesConfig.broadcastOffDuty = config.getString("messages.broadcast-off-duty", "<gold>{player} is now off duty.</gold>");
        messagesConfig.timeAddedCapped = config.getString("messages.time-added-capped", "<green>Added time to your off-duty bank. You've reached the maximum of {max} minutes.</green>");
        messagesConfig.thresholdReached = config.getString("messages.threshold-reached", "<green>You've served enough time to earn a reward! Going off duty now will earn you {minutes} minutes of off-duty time.</green>");

        // Immobilization messages
        messagesConfig.immobilizationStart = config.getString("messages.immobilization-start", "<yellow>You are immobilized for {seconds} seconds while preparing for duty!</yellow>");
        messagesConfig.immobilizationBroadcast = config.getString("messages.immobilization-broadcast", "<red>WARNING: {rank} {player} is going on duty in {seconds} seconds!</red>");
        messagesConfig.immobilizationReminder = config.getString("messages.immobilization-reminder", "<red>You cannot move while preparing for duty! ({seconds}s remaining)</red>");
        messagesConfig.immobilizationComplete = config.getString("messages.immobilization-complete", "<green>You are now on duty and ready to patrol!</green>");
        messagesConfig.immobilizationCompleteBroadcast = config.getString("messages.immobilization-complete-broadcast", "<red>ALERT: {player} is now on duty and patrolling!</red>");
    }

    // Getters for all configuration sections

    // Storage Config
    public int getAutoSaveInterval() { return autoSaveInterval; }
    public String getStorageType() { return storageType; }
    public StorageConfig getStorageConfig() { return storageConfig; }

    // Debug Config
    public boolean isDebugEnabled() { return debugEnabled; }
    public String getDebugLevel() { return debugLevel; }
    public boolean isLogLootGeneration() { return logLootGeneration; }
    public boolean isLogRankDetection() { return logRankDetection; }

    // Duty Config
    public DutyConfig getDutyConfig() { return dutyConfig; }

    // Guard Systems Config
    public GuardBuffConfig getGuardBuffConfig() { return guardBuffConfig; }
    public GuardRestrictionConfig getGuardRestrictionConfig() { return guardRestrictionConfig; }
    public GuardLootConfig getGuardLootConfig() { return guardLootConfig; }
    public GuardPenaltyConfig getGuardPenaltyConfig() { return guardPenaltyConfig; }

    // Other Config
    public ConversionConfig getConversionConfig() { return conversionConfig; }
    public GuiConfig getGuiConfig() { return guiConfig; }
    public MessagesConfig getMessagesConfig() { return messagesConfig; }

    // Inner classes for configuration sections

    public static class StorageConfig {
        public String mysqlHost;
        public int mysqlPort;
        public String mysqlDatabase;
        public String mysqlUsername;
        public String mysqlPassword;
        public String mysqlTablePrefix;
        public String sqliteFile;
    }

    public static class DutyConfig {
        public boolean regionEnabled;
        public String regionName;
        public boolean npcEnabled;
        public boolean npcUseCitizens;
        public int npcInteractionRadius;
        public String npcName;
        public boolean clearInventory;
        public boolean broadcast;
        public int thresholdMinutes;
        public int rewardMinutes;
        public int maxOffDutyTime;
        public int immobilizationDuration;
        public Map<String, Integer> activityRewards;
        public String onDutySound;
        public String offDutySound;
        public Map<String, String> rankKits;
        public List<String> onDutyCommands;
        public List<String> offDutyCommands;
    }

    public static class GuardBuffConfig {
        public boolean enabled;
        public boolean loneGuardEnabled;
        public List<String> loneGuardEffects;
        public int loneGuardRemovalDelay;
        public String applyMessage;
        public String removeWarningMessage;
        public String removedMessage;
    }

    public static class GuardRestrictionConfig {
        public boolean enabled;
        
        // Block breaking config
        public static class BlockBreakingConfig {
            public boolean enabled;
            public List<String> restrictedBlocks;
            public List<String> exceptions;
            public List<String> exemptRegions;
            public String message;
        }
        
        // Movement config
        public static class MovementConfig {
            public boolean enabled;
            public List<String> restrictedRegions;
            public String message;
        }
        
        // Command config
        public static class CommandConfig {
            public boolean enabled;
            public List<String> restrictedCommands;
            public String message;
        }
        
        public BlockBreakingConfig blockBreaking = new BlockBreakingConfig();
        public MovementConfig movement = new MovementConfig();
        public CommandConfig commands = new CommandConfig();
    }

    public static class GuardLootConfig {
        public boolean enabled;
        public int cooldown;
        public String cooldownMessage;
        public boolean tokenRewardEnabled;
        public int tokenRewardAmount;
        public String tokenRewardMessage;
        public String tokenRewardCommand;
        public Map<String, ConfigurationSection> rankLootTables;
    }

    public static class GuardPenaltyConfig {
        public boolean enabled;
        public int lockTime;
        public List<String> restrictedRegions;
        public String message;
    }

    public static class ConversionConfig {
        public int minimumMinutes;
        public int tokensPerMinuteRatio;
        public String tokenCommand;
    }

    public static class GuiConfig {
        public boolean showOnJoin;
        public long joinDelay;
        public String openSound;
        public boolean useEnhancedGui;
        public String continuingDutyMessage;
        public String remainingOffDutyMessage;
    }

    public static class MessagesConfig {
        public String prefix;
        public String noPermission;
        public String notInRegion;
        public String notInArea;
        public String alreadyOnDuty;
        public String alreadyOffDuty;
        public String onDuty;
        public String offDuty;
        public String offDutyReward;
        public String timeRemaining;
        public String timeAdded;
        public String timeSet;
        public String convertedTime;
        public String broadcastOnDuty;
        public String broadcastOffDuty;
        public String timeAddedCapped;
        public String thresholdReached;
        public String immobilizationStart;
        public String immobilizationBroadcast;
        public String immobilizationReminder;
        public String immobilizationComplete;
        public String immobilizationCompleteBroadcast;
    }
}