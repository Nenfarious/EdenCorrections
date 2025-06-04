package dev.lsdmc.edencorrections.config;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Enhanced configuration manager for EdenCorrections
 * Handles loading, validation, and access to all configuration values from multiple files
 * NEW: Multi-file configuration system with organized config files
 */
public class ConfigManager {
    private final EdenCorrections plugin;
    
    // Configuration files map
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();
    
    // Configuration file names
    private static final String[] CONFIG_FILES = {
        "config.yml",           // Main configuration
        "duty.yml",             // Duty system
        "items.yml",            // Guard items
        "ranks.yml",            // Guard ranks & progression
        "combat.yml",           // Combat & security systems
        "loot.yml",             // Guard loot system
        "shop.yml",             // Token system & shop
        "interface.yml",        // GUI & messages
        "integrations.yml"      // External plugin integrations
    };

    // Configuration data structures
    private int autoSaveInterval;
    private String storageType;
    private StorageConfig storageConfig = new StorageConfig();

    // Debug Configuration
    private boolean debugEnabled;
    private String debugLevel;
    private boolean logLootGeneration;
    private boolean logRankDetection;

    // Component configs
    private DutyConfig dutyConfig = new DutyConfig();
    private ItemsConfig itemsConfig = new ItemsConfig();
    private ShopConfig shopConfig = new ShopConfig();
    private RanksConfig ranksConfig = new RanksConfig();
    private CombatConfig combatConfig = new CombatConfig();
    private LootConfig lootConfig = new LootConfig();
    private InterfaceConfig interfaceConfig = new InterfaceConfig();
    private IntegrationsConfig integrationsConfig = new IntegrationsConfig();

    // Legacy configs for backward compatibility
    private GuardBuffConfig guardBuffConfig = new GuardBuffConfig();
    private GuardRestrictionConfig guardRestrictionConfig = new GuardRestrictionConfig();
    private GuardLootConfig guardLootConfig = new GuardLootConfig();
    private GuardPenaltyConfig guardPenaltyConfig = new GuardPenaltyConfig();
    private SafezoneConfig safezoneConfig = new SafezoneConfig();
    private ConversionConfig conversionConfig = new ConversionConfig();
    private GuiConfig guiConfig = new GuiConfig();
    private MessagesConfig messagesConfig = new MessagesConfig();

    public ConfigManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.storageConfig = new StorageConfig();
        this.dutyConfig = new DutyConfig();
        
        // Create default config files if they don't exist
        createDefaultConfigFiles();
        
        // Load all configurations
        loadConfigurations();
    }

    /**
     * Load all configuration files
     */
    public void loadConfigurations() {
        try {
            // Load all config files first
            loadConfigFiles();
            
            // Then load data from each file
            loadMainConfig();
            loadDutyConfig();
            loadItemsConfig();
            loadShopConfig();
            loadRanksConfig();
            loadCombatConfig();
            loadLootConfig();
            loadInterfaceConfig();
            loadIntegrationsConfig();
            
            plugin.getLogger().info("Successfully loaded all configuration files");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create default configuration files if they don't exist
     */
    private void createDefaultConfigFiles() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        for (String fileName : CONFIG_FILES) {
            File configFile = new File(dataFolder, fileName);
            if (!configFile.exists()) {
                try {
                    // Try to copy from plugin resources
                    InputStream resourceStream = plugin.getResource(fileName);
                    if (resourceStream != null) {
                        Files.copy(resourceStream, configFile.toPath());
                        plugin.getLogger().info("Created default configuration file: " + fileName);
                    } else {
                        // Create empty file if resource doesn't exist
                        configFile.createNewFile();
                        plugin.getLogger().warning("Created empty configuration file: " + fileName + " (no default resource found)");
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create configuration file: " + fileName, e);
                }
            }
        }
    }

    /**
     * Load all configuration files into memory
     */
    private void loadConfigFiles() {
        File dataFolder = plugin.getDataFolder();
        
        for (String fileName : CONFIG_FILES) {
            File configFile = new File(dataFolder, fileName);
            if (configFile.exists()) {
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                    configs.put(fileName, config);
                    configFiles.put(fileName, configFile);
                    
                    if (debugEnabled) {
                        plugin.getLogger().info("Loaded configuration file: " + fileName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load configuration file: " + fileName, e);
                }
            } else {
                plugin.getLogger().warning("Configuration file not found: " + fileName);
            }
        }
    }

    /**
     * Get a specific configuration file
     */
    public FileConfiguration getConfig(String fileName) {
        return configs.get(fileName);
    }

    /**
     * Save a specific configuration file
     */
    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        File configFile = configFiles.get(fileName);
        
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
                if (debugEnabled) {
                    plugin.getLogger().info("Saved configuration file: " + fileName);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save configuration file: " + fileName, e);
            }
        }
    }

    /**
     * Reload all configuration files
     */
    public void reload() {
        plugin.getLogger().info("Reloading EdenCorrections multi-file configuration...");
        configs.clear();
        configFiles.clear();
        loadConfigurations();
    }

    // Configuration loading methods for each file

    /**
     * Load main config.yml - Core plugin settings
     */
    private void loadMainConfig() {
        FileConfiguration config = getConfig("config.yml");
        if (config == null) {
            plugin.getLogger().warning("config.yml not found, using defaults");
            return;
        }

        // Storage Configuration
        autoSaveInterval = config.getInt("storage.autosave-interval", 5);
        storageType = config.getString("storage.type", "sqlite").toLowerCase();

        // MySQL Configuration
        storageConfig.mysqlHost = config.getString("storage.mysql.host", "localhost");
        storageConfig.mysqlPort = config.getInt("storage.mysql.port", 3306);
        storageConfig.mysqlDatabase = config.getString("storage.mysql.database", "edencorrections");
        storageConfig.mysqlUsername = config.getString("storage.mysql.username", "root");
        storageConfig.mysqlPassword = config.getString("storage.mysql.password", "password");
        storageConfig.mysqlTablePrefix = config.getString("storage.mysql.table-prefix", "ec_");

        // SQLite Configuration
        storageConfig.sqliteFile = config.getString("storage.sqlite.file", "database.db");

        // Debug Configuration
        debugEnabled = config.getBoolean("debug.enabled", false);
        debugLevel = config.getString("debug.level", "INFO");
        logLootGeneration = config.getBoolean("debug.log-loot-generation", false);
        logRankDetection = config.getBoolean("debug.log-rank-detection", false);
    }

    /**
     * Load duty.yml - Duty system configuration
     */
    private void loadDutyConfig() {
        FileConfiguration config = getConfig("duty.yml");
        if (config == null) {
            plugin.getLogger().warning("duty.yml not found, using defaults");
            setDefaultDutyConfig();
            return;
        }

        // Region configuration
        dutyConfig.regionEnabled = config.getBoolean("region.enabled", true);
        dutyConfig.regionName = config.getString("region.name", "locker_room");

        // Basic duty settings
        dutyConfig.clearInventory = config.getBoolean("duty.clear-inventory", true);
        dutyConfig.broadcast = config.getBoolean("duty.broadcast", true);
        dutyConfig.thresholdMinutes = config.getInt("duty.threshold-minutes", 15);
        dutyConfig.rewardMinutes = config.getInt("duty.reward-minutes", 30);
        dutyConfig.maxOffDutyTime = config.getInt("duty.max-off-duty-time", 4320);
        dutyConfig.immobilizationDuration = config.getInt("duty.immobilization-duration", 30);

        // Inventory cache config
        dutyConfig.inventoryCacheEnabled = config.getBoolean("inventory-cache.enabled", true);
        dutyConfig.inventoryCacheIncludeArmor = config.getBoolean("inventory-cache.include-armor", true);
        dutyConfig.inventoryCacheIncludeOffhand = config.getBoolean("inventory-cache.include-offhand", true);
        dutyConfig.inventoryCacheSaveOnDuty = config.getBoolean("inventory-cache.save-on-duty", true);
        dutyConfig.inventoryCacheRestoreOnOffduty = config.getBoolean("inventory-cache.restore-on-offduty", true);

        // Activity rewards
        dutyConfig.activityRewards = new HashMap<>();
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(true)) {
                if (rewardsSection.isInt(key)) {
                    dutyConfig.activityRewards.put(key, rewardsSection.getInt(key));
                }
            }
        }
    }

    /**
     * Load items.yml - COMPLETELY REWRITTEN to load ALL item configuration
     */
    private void loadItemsConfig() {
        FileConfiguration config = getConfig("items.yml");
        if (config == null) {
            plugin.getLogger().warning("items.yml not found, using defaults");
            setDefaultItemsConfig();
            return;
        }

        // Set the config reference for dynamic access
        itemsConfig.setConfig(config);

        // Load guard items
        loadGuardItem(config, "handcuffs", itemsConfig.handcuffs);
        loadGuardItem(config, "drug-sniffer", itemsConfig.drugSniffer);
        loadGuardItem(config, "metal-detector", itemsConfig.metalDetector);
        loadGuardItem(config, "spyglass", itemsConfig.spyglass);
        loadGuardItem(config, "prison-remote", itemsConfig.prisonRemote);
        loadGuardItem(config, "guard-baton", itemsConfig.guardBaton);
        loadGuardItem(config, "taser", itemsConfig.taser);
        loadGuardItem(config, "smoke-bomb", itemsConfig.smokeBomb);
        loadGuardItem(config, "riot-shield", itemsConfig.riotShield);
        loadGuardItem(config, "sobriety-test", itemsConfig.sobrietyTest);

        // Load general settings
        if (config.contains("general")) {
            ConfigurationSection generalSection = config.getConfigurationSection("general");
            if (generalSection != null) {
                itemsConfig.general.protectOnDeath = generalSection.getBoolean("protect-on-death", true);
                itemsConfig.general.removeOnOffDuty = generalSection.getBoolean("remove-on-off-duty", true);
                itemsConfig.general.globalMaxRange = generalSection.getDouble("global-max-range", 15.0);
                itemsConfig.general.spamProtectionCooldown = generalSection.getLong("spam-protection-cooldown", 1000);
            }
        }

        if (debugEnabled) {
            plugin.getLogger().info("Loaded items configuration with " + 
                "global max range: " + itemsConfig.general.globalMaxRange);
        }
    }

    /**
     * Load a guard item from config
     */
    private void loadGuardItem(FileConfiguration config, String itemKey, GuardItem item) {
        if (!config.contains(itemKey)) return;
        
        ConfigurationSection section = config.getConfigurationSection(itemKey);
        item.material = Material.valueOf(section.getString("material", "GOLD_NUGGET"));
        item.name = section.getString("name", "§eGuard Item");
        item.lore = section.getStringList("lore");
        item.range = section.getDouble("range", 5.0);
        item.maxDistance = section.getDouble("max-distance", section.getDouble("range", 5.0));
        item.countdown = section.getInt("countdown", 5);
        item.cooldown = section.getInt("cooldown", 30);
        
        // Item-specific properties
        if (section.contains("protect-guards")) {
            item.protectGuards = section.getBoolean("protect-guards");
        }
        if (section.contains("safe-regions")) {
            item.safeRegions = section.getStringList("safe-regions");
        }
        if (section.contains("reward")) {
            ConfigurationSection rewardSection = section.getConfigurationSection("reward");
            if (rewardSection.contains("base")) item.rewardBase = rewardSection.getInt("base");
            if (rewardSection.contains("per-level")) item.rewardPerLevel = rewardSection.getInt("per-level");
            if (rewardSection.contains("no-find")) item.rewardNoFind = rewardSection.getInt("no-find");
            if (rewardSection.contains("per-drug")) item.rewardPerDrug = rewardSection.getInt("per-drug");
            if (rewardSection.contains("find")) item.rewardFind = rewardSection.getInt("find");
            if (rewardSection.contains("pass")) item.rewardPass = rewardSection.getInt("pass");
            if (rewardSection.contains("fail")) item.rewardFail = rewardSection.getInt("fail");
        }
        if (section.contains("min-wanted-level")) {
            item.minWantedLevel = section.getInt("min-wanted-level");
        }
        if (section.contains("lockdown-duration")) {
            item.lockdownDuration = section.getInt("lockdown-duration");
        }
        if (section.contains("slowness-duration")) {
            item.slownessDuration = section.getInt("slowness-duration");
        }
        if (section.contains("pvp-only")) {
            item.pvpOnly = section.getBoolean("pvp-only");
        }
        if (section.contains("stun-duration")) {
            item.stunDuration = section.getDouble("stun-duration");
        }
        if (section.contains("dropped-charges")) {
            item.droppedCharges = section.getInt("dropped-charges");
        }
        if (section.contains("effects")) {
            ConfigurationSection effectsSection = section.getConfigurationSection("effects");
            if (effectsSection != null) {
                if (effectsSection.contains("blackout-duration")) {
                    item.blackoutDuration = effectsSection.getInt("blackout-duration");
                }
                if (effectsSection.contains("darkness-duration")) {
                    item.darknessDuration = effectsSection.getInt("darkness-duration");
                }
                if (effectsSection.contains("range")) {
                    item.effectRange = effectsSection.getInt("range");
                }
            }
        }
        if (section.contains("protection-duration")) {
            item.protectionDuration = section.getInt("protection-duration");
        }
    }

    /**
     * Load shop.yml - COMPLETELY REWRITTEN to load ALL shop configuration
     */
    private void loadShopConfig() {
        FileConfiguration config = getConfig("shop.yml");
        if (config == null) {
            plugin.getLogger().warning("shop.yml not found, using defaults");
            setDefaultShopConfig();
            return;
        }

        // Guard Token System
        shopConfig.tokenEarnings.search = config.getInt("guard-tokens.earnings.search", 100);
        shopConfig.tokenEarnings.successfulSearch = config.getInt("guard-tokens.earnings.successful-search", 250);
        shopConfig.tokenEarnings.metalDetection = config.getInt("guard-tokens.earnings.metal-detection", 150);
        shopConfig.tokenEarnings.drugDetection = config.getInt("guard-tokens.earnings.drug-detection", 200);
        shopConfig.tokenEarnings.apprehension = config.getInt("guard-tokens.earnings.apprehension", 500);
        shopConfig.tokenEarnings.chaseCompletion = config.getInt("guard-tokens.earnings.chase-completion", 300);
        shopConfig.tokenEarnings.sobrietyTestPass = config.getInt("guard-tokens.earnings.sobriety-test-pass", 150);
        shopConfig.tokenEarnings.sobrietyTestFail = config.getInt("guard-tokens.earnings.sobriety-test-fail", 300);
        shopConfig.tokenEarnings.wantedLevelIncrease = config.getInt("guard-tokens.earnings.wanted-level-increase", 100);
        shopConfig.tokenEarnings.successfulJail = config.getInt("guard-tokens.earnings.successful-jail", 400);
        shopConfig.tokenEarnings.guardDeathCompensation = config.getInt("guard-tokens.earnings.guard-death-compensation", 200);
        shopConfig.tokenEarnings.dailyLogin = config.getInt("guard-tokens.earnings.daily-login", 50);

        // Token Conversion
        shopConfig.conversion.offDutyToTokensEnabled = config.getBoolean("guard-tokens.conversion.off-duty-to-tokens.enabled", true);
        shopConfig.conversion.offDutyToTokensRate = config.getInt("guard-tokens.conversion.off-duty-to-tokens.rate", 100);
        shopConfig.conversion.offDutyToTokensMinimum = config.getInt("guard-tokens.conversion.off-duty-to-tokens.minimum-minutes", 5);
        shopConfig.conversion.tokensToOffDutyEnabled = config.getBoolean("guard-tokens.conversion.tokens-to-off-duty.enabled", true);
        shopConfig.conversion.tokensToOffDutyRate = config.getInt("guard-tokens.conversion.tokens-to-off-duty.rate", 100);
        shopConfig.conversion.tokensToOffDutyMinimum = config.getInt("guard-tokens.conversion.tokens-to-off-duty.minimum-tokens", 500);

        // Shop Equipment Items
        loadShopCategory(config, "shop.equipment", shopConfig.equipmentItems);
        
        // Shop Consumables
        loadShopCategory(config, "shop.consumables", shopConfig.consumableItems);
        
        // Shop Upgrades
        loadShopCategory(config, "shop.upgrades", shopConfig.upgradeItems);

        // Shop Interface
        shopConfig.gui.title = config.getString("interface.gui.title", "§6§lGuard Shop");
        shopConfig.gui.size = config.getInt("interface.gui.size", 54);
        
        // Shop Navigation
        if (config.contains("interface.navigation")) {
            ConfigurationSection navSection = config.getConfigurationSection("interface.navigation");
            shopConfig.navigation.previousPage.material = Material.valueOf(navSection.getString("previous-page.material", "ARROW"));
            shopConfig.navigation.previousPage.name = navSection.getString("previous-page.name", "§7← Previous Page");
            shopConfig.navigation.previousPage.slot = navSection.getInt("previous-page.slot", 45);
            
            shopConfig.navigation.nextPage.material = Material.valueOf(navSection.getString("next-page.material", "ARROW"));
            shopConfig.navigation.nextPage.name = navSection.getString("next-page.name", "§7Next Page →");
            shopConfig.navigation.nextPage.slot = navSection.getInt("next-page.slot", 53);
            
            shopConfig.navigation.backToMenu.material = Material.valueOf(navSection.getString("back-to-menu.material", "BARRIER"));
            shopConfig.navigation.backToMenu.name = navSection.getString("back-to-menu.name", "§c§lBack to Menu");
            shopConfig.navigation.backToMenu.slot = navSection.getInt("back-to-menu.slot", 49);
            
            shopConfig.navigation.tokenDisplay.material = Material.valueOf(navSection.getString("token-display.material", "SUNFLOWER"));
            shopConfig.navigation.tokenDisplay.name = navSection.getString("token-display.name", "§6§lYour Tokens");
            shopConfig.navigation.tokenDisplay.slot = navSection.getInt("token-display.slot", 4);
        }

        // Shop Categories
        if (config.contains("interface.categories")) {
            ConfigurationSection categoriesSection = config.getConfigurationSection("interface.categories");
            
            shopConfig.categories.equipment.material = Material.valueOf(categoriesSection.getString("equipment.material", "IRON_SWORD"));
            shopConfig.categories.equipment.name = categoriesSection.getString("equipment.name", "§b§lEquipment");
            shopConfig.categories.equipment.slot = categoriesSection.getInt("equipment.slot", 19);
            
            shopConfig.categories.consumables.material = Material.valueOf(categoriesSection.getString("consumables.material", "GOLDEN_APPLE"));
            shopConfig.categories.consumables.name = categoriesSection.getString("consumables.name", "§a§lConsumables");
            shopConfig.categories.consumables.slot = categoriesSection.getInt("consumables.slot", 21);
            
            shopConfig.categories.upgrades.material = Material.valueOf(categoriesSection.getString("upgrades.material", "ANVIL"));
            shopConfig.categories.upgrades.name = categoriesSection.getString("upgrades.name", "§d§lUpgrades");
            shopConfig.categories.upgrades.slot = categoriesSection.getInt("upgrades.slot", 23);
        }

        // Purchase Restrictions
        shopConfig.restrictions.requireOnDuty = config.getBoolean("restrictions.purchase.require-on-duty", true);
        shopConfig.restrictions.requireDutyForUse = config.getBoolean("restrictions.usage.require-duty-for-use", true);
        shopConfig.restrictions.enforceCooldowns = config.getBoolean("restrictions.usage.enforce-cooldowns", true);
        
        // Rank Restrictions
        if (config.contains("restrictions.purchase.rank-restrictions")) {
            ConfigurationSection rankSection = config.getConfigurationSection("restrictions.purchase.rank-restrictions");
            for (String key : rankSection.getKeys(false)) {
                shopConfig.restrictions.rankRestrictions.put(key, rankSection.getString(key));
            }
        }
        
        // Purchase Limits
        if (config.contains("restrictions.purchase.limits")) {
            ConfigurationSection limitsSection = config.getConfigurationSection("restrictions.purchase.limits");
            for (String key : limitsSection.getKeys(false)) {
                shopConfig.restrictions.purchaseLimits.put(key, limitsSection.getInt(key));
            }
        }

        // Token Multipliers
        if (config.contains("multipliers.rank")) {
            ConfigurationSection rankMultipliers = config.getConfigurationSection("multipliers.rank");
            for (String rank : rankMultipliers.getKeys(false)) {
                shopConfig.multipliers.rankMultipliers.put(rank, rankMultipliers.getDouble(rank));
            }
        }
        
        if (config.contains("multipliers.time")) {
            ConfigurationSection timeMultipliers = config.getConfigurationSection("multipliers.time");
            shopConfig.multipliers.weekend = timeMultipliers.getDouble("weekend", 1.5);
            shopConfig.multipliers.nightShift = timeMultipliers.getDouble("night-shift", 1.3);
            shopConfig.multipliers.holiday = timeMultipliers.getDouble("holiday", 2.0);
        }
        
        if (config.contains("multipliers.streaks.daily-streak")) {
            ConfigurationSection streakMultipliers = config.getConfigurationSection("multipliers.streaks.daily-streak");
            for (String days : streakMultipliers.getKeys(false)) {
                shopConfig.multipliers.dailyStreakMultipliers.put(Integer.parseInt(days), streakMultipliers.getDouble(days));
            }
        }

        // Economy Settings
        shopConfig.economy.dynamicPricingEnabled = config.getBoolean("economy.dynamic-pricing.enabled", false);
        shopConfig.economy.fluctuationRange = config.getDouble("economy.dynamic-pricing.fluctuation-range", 0.2);
        shopConfig.economy.bulkDiscountsEnabled = config.getBoolean("economy.bulk-discounts.enabled", true);
        
        if (config.contains("economy.bulk-discounts.thresholds")) {
            ConfigurationSection thresholds = config.getConfigurationSection("economy.bulk-discounts.thresholds");
            for (String quantity : thresholds.getKeys(false)) {
                shopConfig.economy.bulkDiscountThresholds.put(Integer.parseInt(quantity), thresholds.getDouble(quantity));
            }
        }
        
        shopConfig.economy.salesEnabled = config.getBoolean("economy.sales.enabled", true);
        shopConfig.economy.lowGuardCountSale = config.getDouble("economy.sales.auto-sales.low-guard-count", 0.25);
        shopConfig.economy.highCrimeRateSale = config.getDouble("economy.sales.auto-sales.high-crime-rate", 0.20);

        // Update legacy configs for backward compatibility
        conversionConfig.conversionMinimum = shopConfig.conversion.offDutyToTokensMinimum;
        conversionConfig.tokensPerMinuteRatio = shopConfig.conversion.offDutyToTokensRate;
    }

    /**
     * Load a shop category from config
     */
    private void loadShopCategory(FileConfiguration config, String path, Map<String, ShopItem> targetMap) {
        if (!config.contains(path)) return;
        
        ConfigurationSection categorySection = config.getConfigurationSection(path);
        for (String itemKey : categorySection.getKeys(false)) {
            ConfigurationSection itemSection = categorySection.getConfigurationSection(itemKey);
            if (itemSection == null) continue;
            
            ShopItem item = new ShopItem();
            item.cost = itemSection.getInt("cost", 0);
            item.description = itemSection.getString("description", "");
            item.material = Material.valueOf(itemSection.getString("material", "GOLD_NUGGET"));
            item.amount = itemSection.getInt("amount", 1);
            
            // Load effects if present
            if (itemSection.contains("effects")) {
                if (itemSection.isList("effects")) {
                    item.effects = itemSection.getStringList("effects");
                } else {
                    // Handle single effect or nested effects
                    ConfigurationSection effectsSection = itemSection.getConfigurationSection("effects");
                    if (effectsSection != null) {
                        for (String effectKey : effectsSection.getKeys(false)) {
                            Object effectValue = effectsSection.get(effectKey);
                            item.effects.add(effectKey + ":" + effectValue.toString());
                        }
                    }
                }
            }
            
            // Load enchantments if present
            if (itemSection.contains("enchantments")) {
                item.enchantments = itemSection.getStringList("enchantments");
            }
            
            // Load additional properties
            if (itemSection.contains("stun-duration")) {
                item.stunDuration = itemSection.getDouble("stun-duration");
            }
            if (itemSection.contains("dropped-charges")) {
                item.droppedCharges = itemSection.getInt("dropped-charges");
            }
            if (itemSection.contains("range")) {
                item.range = itemSection.getInt("range");
            }
            if (itemSection.contains("duration")) {
                item.duration = itemSection.getInt("duration");
            }
            if (itemSection.contains("radius")) {
                item.radius = itemSection.getInt("radius");
            }
            if (itemSection.contains("protection-duration")) {
                item.protectionDuration = itemSection.getInt("protection-duration");
            }
            
            targetMap.put(itemKey, item);
        }
    }

    /**
     * Load ranks.yml - Guard ranks and progression
     */
    private void loadRanksConfig() {
        FileConfiguration config = getConfig("ranks.yml");
        if (config == null) {
            plugin.getLogger().warning("ranks.yml not found - guard ranks may not function properly");
            return;
        }

        // Ranks are loaded directly by GuardRankManager and GuardProgressionManager
        // This method can be extended to load rank-specific global settings if needed
    }

    /**
     * Load combat.yml - Combat and security systems
     */
    private void loadCombatConfig() {
        FileConfiguration config = getConfig("combat.yml");
        if (config == null) {
            plugin.getLogger().warning("combat.yml not found, using defaults");
            setDefaultCombatConfig();
            return;
        }

        // Guard Buff Configuration
        guardBuffConfig.enabled = config.getBoolean("guard-buffs.enabled", true);
        guardBuffConfig.loneGuardEnabled = config.getBoolean("guard-buffs.lone-guard.enabled", true);
        guardBuffConfig.loneGuardEffects = config.getStringList("guard-buffs.lone-guard.effects");
        guardBuffConfig.loneGuardRemovalDelay = config.getInt("guard-buffs.lone-guard.removal-delay", 10);

        // Messages
        guardBuffConfig.applyMessage = config.getString("guard-buffs.lone-guard.messages.apply",
                "<red>You are the only guard online!</red>");
        guardBuffConfig.removeWarningMessage = config.getString("guard-buffs.lone-guard.messages.remove-warning",
                "<red>Another guard has logged in!</red>");
        guardBuffConfig.removedMessage = config.getString("guard-buffs.lone-guard.messages.removed",
                "<red>Your special effects have been removed!</red>");

        // Guard Restriction Configuration
        guardRestrictionConfig.enabled = config.getBoolean("guard-restrictions.enabled", true);
        guardRestrictionConfig.blockBreaking.enabled = config.getBoolean("guard-restrictions.block-breaking.enabled", true);
        guardRestrictionConfig.blockBreaking.restrictedBlocks = config.getStringList("guard-restrictions.block-breaking.restricted-blocks");
        guardRestrictionConfig.blockBreaking.exceptions = config.getStringList("guard-restrictions.block-breaking.exceptions");
        guardRestrictionConfig.blockBreaking.exemptRegions = config.getStringList("guard-restrictions.block-breaking.exempt-regions");
        guardRestrictionConfig.blockBreaking.message = config.getString("guard-restrictions.block-breaking.message",
                "<red>You cannot farm or mine while on duty!</red>");

        guardRestrictionConfig.movement.enabled = config.getBoolean("guard-restrictions.movement.enabled", true);
        guardRestrictionConfig.movement.restrictedRegions = config.getStringList("guard-restrictions.movement.restricted-regions");
        guardRestrictionConfig.movement.message = config.getString("guard-restrictions.movement.message",
                "<red>You cannot enter this area while on duty!</red>");

        guardRestrictionConfig.commands.enabled = config.getBoolean("guard-restrictions.commands.enabled", true);
        guardRestrictionConfig.commands.restrictedCommands = config.getStringList("guard-restrictions.commands.restricted-commands");
        guardRestrictionConfig.commands.message = config.getString("guard-restrictions.commands.message",
                "<red>You cannot use this command while on duty!</red>");

        // Guard Death Penalties
        guardPenaltyConfig.enabled = config.getBoolean("guard-death-penalties.enabled", true);
        guardPenaltyConfig.lockTime = config.getInt("guard-death-penalties.lock-time", 60);
        guardPenaltyConfig.restrictedRegions = config.getStringList("guard-death-penalties.restricted-regions");
        guardPenaltyConfig.message = config.getString("guard-death-penalties.message",
                "<gray>You cannot leave for </gray><red>{time} seconds</red> <gray>for dying!</gray>");

        // Safezone Configuration
        safezoneConfig.enabled = true; // Always enabled for guard actions
        safezoneConfig.regionName = config.getString("safezones.region-name", "safezone");
        safezoneConfig.radius = config.getInt("safezones.radius", 100);
        safezoneConfig.message = config.getString("safezones.outside-safezone-message", 
                "<red>You can only use guard items inside designated safezones!</red>");
    }

    /**
     * Load loot.yml - Guard loot system
     */
    private void loadLootConfig() {
        FileConfiguration config = getConfig("loot.yml");
        if (config == null) {
            plugin.getLogger().warning("loot.yml not found, using defaults");
            setDefaultLootConfig();
            return;
        }

        guardLootConfig.enabled = config.getBoolean("guard-loot.enabled", true);
        guardLootConfig.cooldown = config.getInt("guard-loot.cooldown", 600);
        guardLootConfig.cooldownMessage = config.getString("guard-loot.cooldown-message",
                "<aqua>{victim}</aqua> <gray>has their guard loot on cooldown!</gray>");

        // Token reward configuration
        guardLootConfig.tokenRewardEnabled = config.getBoolean("guard-loot.token-reward.enabled", true);
        guardLootConfig.tokenRewardAmount = config.getInt("guard-loot.token-reward.amount", 200);
        guardLootConfig.tokenRewardMessage = config.getString("guard-loot.token-reward.message", 
            "<red>You fought bravely in combat and have received {tokens} guard tokens!</red>");

        // Load loot tables for each rank
        guardLootConfig.rankLootTables = new HashMap<>();
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String rank : ranksSection.getKeys(false)) {
                ConfigurationSection rankSection = ranksSection.getConfigurationSection(rank);
                if (rankSection != null) {
                    guardLootConfig.rankLootTables.put(rank, rankSection);
                }
            }
        }
    }

    /**
     * Load interface.yml - GUI and messages
     */
    private void loadInterfaceConfig() {
        FileConfiguration config = getConfig("interface.yml");
        if (config == null) {
            plugin.getLogger().warning("interface.yml not found, using defaults");
            setDefaultInterfaceConfig();
            return;
        }

        // GUI Configuration
        guiConfig.showOnJoin = config.getBoolean("gui.show-on-join", false);
        guiConfig.joinDelay = config.getLong("gui.join-delay", 20L);
        guiConfig.openSound = config.getString("gui.open-sound", "minecraft:block.chest.open");
        guiConfig.useEnhancedGui = config.getBoolean("gui.use-enhanced-gui", true);
        guiConfig.continuingDutyMessage = config.getString("messages.continuing-duty", "<green>You're continuing your guard shift!</green>");
        guiConfig.remainingOffDutyMessage = config.getString("messages.remaining-off-duty", "<yellow>You're remaining off duty.</yellow>");

        // Messages Configuration
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
        messagesConfig.broadcastOffDuty = config.getString("messages.broadcast-off-duty", "");
        messagesConfig.timeAddedCapped = config.getString("messages.time-added-capped", "<green>Added time to your off-duty bank. You've reached the maximum of {max} minutes.</green>");
        messagesConfig.thresholdReached = config.getString("messages.threshold-reached", "<green>You've served enough time to earn a reward! Going off duty now will earn you {minutes} minutes of off-duty time.</green>");

        // Immobilization messages
        messagesConfig.immobilizationStart = config.getString("immobilization.start", "<yellow>You are immobilized for {seconds} seconds while preparing for duty!</yellow>");
        messagesConfig.immobilizationBroadcast = config.getString("immobilization.broadcast", "<red>WARNING: {rank} {player} is going on duty in {seconds} seconds!</red>");
        messagesConfig.immobilizationReminder = config.getString("immobilization.reminder", "<red>You cannot move while preparing for duty! ({seconds}s remaining)</red>");
        messagesConfig.immobilizationComplete = config.getString("immobilization.complete", "<green>You are now on duty and ready to patrol!</green>");
        messagesConfig.immobilizationCompleteBroadcast = config.getString("immobilization.complete-broadcast", "<red>ALERT: {player} is now on duty and patrolling!</red>");
    }

    /**
     * Load integrations.yml - External plugin integrations
     */
    private void loadIntegrationsConfig() {
        FileConfiguration config = getConfig("integrations.yml");
        if (config == null) {
            plugin.getLogger().warning("integrations.yml not found - external integrations may not function properly");
            return;
        }

        // Integrations are loaded directly by their respective managers
        // This method can be extended to load integration-specific global settings if needed
    }

    // Default configuration methods (fallbacks)

    private void setDefaultDutyConfig() {
        dutyConfig.regionEnabled = true;
        dutyConfig.regionName = "locker_room";
        dutyConfig.clearInventory = true;
        dutyConfig.broadcast = true;
        dutyConfig.thresholdMinutes = 15;
        dutyConfig.rewardMinutes = 30;
        dutyConfig.maxOffDutyTime = 4320;
        dutyConfig.immobilizationDuration = 30;
        dutyConfig.inventoryCacheEnabled = true;
        dutyConfig.inventoryCacheIncludeArmor = true;
        dutyConfig.inventoryCacheIncludeOffhand = true;
        dutyConfig.inventoryCacheSaveOnDuty = true;
        dutyConfig.inventoryCacheRestoreOnOffduty = true;
        dutyConfig.activityRewards = new HashMap<>();
    }

    private void setDefaultCombatConfig() {
        guardBuffConfig.enabled = true;
        guardBuffConfig.loneGuardEnabled = true;
        guardBuffConfig.loneGuardEffects = List.of("DAMAGE_RESISTANCE:1:infinite");
        guardBuffConfig.loneGuardRemovalDelay = 10;
        guardBuffConfig.applyMessage = "<red>You are the only guard online!</red>";
        guardBuffConfig.removeWarningMessage = "<red>Another guard has logged in!</red>";
        guardBuffConfig.removedMessage = "<red>Your special effects have been removed!</red>";

        guardRestrictionConfig.enabled = true;
        guardPenaltyConfig.enabled = true;
        guardPenaltyConfig.lockTime = 60;
        guardPenaltyConfig.message = "<red>You cannot leave for {time} seconds for dying!</red>";

        safezoneConfig.enabled = true;
        safezoneConfig.regionName = "safezone";
        safezoneConfig.radius = 100;
        safezoneConfig.message = "<green>You are in the safezone!</green>";
    }

    private void setDefaultLootConfig() {
        guardLootConfig.enabled = true;
        guardLootConfig.cooldown = 600;
        guardLootConfig.cooldownMessage = "<aqua>{victim}</aqua> <gray>has their guard loot on cooldown!</gray>";
        guardLootConfig.tokenRewardEnabled = true;
        guardLootConfig.tokenRewardAmount = 200;
        guardLootConfig.tokenRewardMessage = "<red>You received {tokens} guard tokens!</red>";
        guardLootConfig.rankLootTables = new HashMap<>();
    }

    private void setDefaultConversionConfig() {
        conversionConfig.conversionMinimum = 5;
        conversionConfig.tokensPerMinuteRatio = 100;
    }

    private void setDefaultInterfaceConfig() {
        guiConfig.showOnJoin = false;
        guiConfig.joinDelay = 20L;
        guiConfig.openSound = "minecraft:block.chest.open";
        guiConfig.useEnhancedGui = true;
        guiConfig.continuingDutyMessage = "<green>You're continuing your guard shift!</green>";
        guiConfig.remainingOffDutyMessage = "<yellow>You're remaining off duty.</yellow>";
    }

    private void setDefaultMessagesConfig() {
        messagesConfig.prefix = "<gold>[Corrections]</gold> ";
        messagesConfig.noPermission = "<red>You don't have permission to do that!</red>";
        messagesConfig.notInRegion = "<red>You must be in the duty room!</red>";
        messagesConfig.alreadyOnDuty = "<red>You are already on duty!</red>";
        messagesConfig.alreadyOffDuty = "<red>You are already off duty!</red>";
        messagesConfig.onDuty = "<green>You are now on guard duty!</green>";
        messagesConfig.offDuty = "<yellow>You are now off duty.</yellow>";
        messagesConfig.offDutyReward = "<green>You've earned {minutes} minutes!</green>";
        messagesConfig.timeRemaining = "<yellow>You have {minutes} minutes remaining.</yellow>";
        messagesConfig.timeAdded = "<green>Added {minutes} minutes to {player}.</green>";
        messagesConfig.timeSet = "<green>Set {player}'s time to {minutes} minutes.</green>";
        messagesConfig.convertedTime = "<green>Converted {minutes} minutes to {tokens} tokens!</green>";
        messagesConfig.broadcastOnDuty = "";
        messagesConfig.broadcastOffDuty = "";
        messagesConfig.timeAddedCapped = "<green>Time added. Maximum reached.</green>";
        messagesConfig.thresholdReached = "<green>Threshold reached!</green>";
        messagesConfig.immobilizationStart = "<yellow>Immobilized for {seconds} seconds!</yellow>";
        messagesConfig.immobilizationBroadcast = "<red>WARNING: {player} going on duty!</red>";
        messagesConfig.immobilizationReminder = "<red>Cannot move! {seconds}s remaining</red>";
        messagesConfig.immobilizationComplete = "<green>You are now on duty!</green>";
        messagesConfig.immobilizationCompleteBroadcast = "<red>ALERT: {player} is on duty!</red>";
    }

    private void setDefaultShopConfig() {
        // Set default shop configuration
        shopConfig.tokenEarnings.search = 100;
        shopConfig.tokenEarnings.successfulSearch = 250;
        shopConfig.tokenEarnings.metalDetection = 150;
        shopConfig.tokenEarnings.drugDetection = 200;
        shopConfig.tokenEarnings.apprehension = 500;
        shopConfig.conversion.offDutyToTokensEnabled = true;
        shopConfig.conversion.offDutyToTokensRate = 100;
        shopConfig.conversion.offDutyToTokensMinimum = 5;
        shopConfig.gui.title = "§6§lGuard Shop";
        shopConfig.gui.size = 54;
        shopConfig.restrictions.requireOnDuty = true;
    }

    private void setDefaultItemsConfig() {
        // Set basic default values for items
        itemsConfig.general.protectOnDeath = true;
        itemsConfig.general.removeOnOffDuty = true;
        itemsConfig.general.globalMaxRange = 15.0;
        itemsConfig.general.spamProtectionCooldown = 1000;
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
    public SafezoneConfig getSafezoneConfig() { return safezoneConfig; }

    // Other Config
    public ConversionConfig getConversionConfig() { return conversionConfig; }
    public GuiConfig getGuiConfig() { return guiConfig; }
    public MessagesConfig getMessagesConfig() { return messagesConfig; }

    // Getters for new config classes
    public ItemsConfig getItemsConfig() { return itemsConfig; }
    public ShopConfig getShopConfig() { return shopConfig; }
    public RanksConfig getRanksConfig() { return ranksConfig; }
    public CombatConfig getCombatConfig() { return combatConfig; }
    public LootConfig getLootConfig() { return lootConfig; }
    public InterfaceConfig getInterfaceConfig() { return interfaceConfig; }
    public IntegrationsConfig getIntegrationsConfig() { return integrationsConfig; }

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
        public boolean clearInventory;
        public boolean broadcast;
        public int thresholdMinutes;
        public int rewardMinutes;
        public int maxOffDutyTime;
        public int immobilizationDuration;
        public Map<String, Integer> activityRewards;
        // Inventory cache config
        public boolean inventoryCacheEnabled;
        public boolean inventoryCacheIncludeArmor;
        public boolean inventoryCacheIncludeOffhand;
        public boolean inventoryCacheSaveOnDuty;
        public boolean inventoryCacheRestoreOnOffduty;
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
        public Map<String, ConfigurationSection> rankLootTables;
    }

    public static class GuardPenaltyConfig {
        public boolean enabled;
        public int lockTime;
        public List<String> restrictedRegions;
        public String message;
    }

    public static class SafezoneConfig {
        public boolean enabled;
        public String regionName;
        public int radius;
        public String message;
    }

    public static class ConversionConfig {
        public int conversionMinimum;
        public int tokensPerMinuteRatio;
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

    public static class ShopConfig {
        public TokenEarnings tokenEarnings = new TokenEarnings();
        public TokenConversion conversion = new TokenConversion();
        public Map<String, ShopItem> equipmentItems = new HashMap<>();
        public Map<String, ShopItem> consumableItems = new HashMap<>();
        public Map<String, ShopItem> upgradeItems = new HashMap<>();
        public ShopGui gui = new ShopGui();
        public ShopNavigation navigation = new ShopNavigation();
        public ShopCategories categories = new ShopCategories();
        public ShopRestrictions restrictions = new ShopRestrictions();
        public ShopMultipliers multipliers = new ShopMultipliers();
        public ShopEconomy economy = new ShopEconomy();
        
        public static class TokenEarnings {
            public int search = 100;
            public int successfulSearch = 250;
            public int metalDetection = 150;
            public int drugDetection = 200;
            public int apprehension = 500;
            public int chaseCompletion = 300;
            public int sobrietyTestPass = 150;
            public int sobrietyTestFail = 300;
            public int wantedLevelIncrease = 100;
            public int successfulJail = 400;
            public int guardDeathCompensation = 200;
            public int dailyLogin = 50;
        }
        
        public static class TokenConversion {
            public boolean offDutyToTokensEnabled = true;
            public int offDutyToTokensRate = 100;
            public int offDutyToTokensMinimum = 5;
            public boolean tokensToOffDutyEnabled = true;
            public int tokensToOffDutyRate = 100;
            public int tokensToOffDutyMinimum = 500;
        }
        
        public static class ShopGui {
            public String title = "§6§lGuard Shop";
            public int size = 54;
        }
        
        public static class ShopNavigation {
            public NavigationItem previousPage = new NavigationItem(Material.ARROW, "§7← Previous Page", 45);
            public NavigationItem nextPage = new NavigationItem(Material.ARROW, "§7Next Page →", 53);
            public NavigationItem backToMenu = new NavigationItem(Material.BARRIER, "§c§lBack to Menu", 49);
            public NavigationItem tokenDisplay = new NavigationItem(Material.SUNFLOWER, "§6§lYour Tokens", 4);
        }
        
        public static class NavigationItem {
            public Material material;
            public String name;
            public int slot;
            
            public NavigationItem(Material material, String name, int slot) {
                this.material = material;
                this.name = name;
                this.slot = slot;
            }
        }
        
        public static class ShopCategories {
            public CategoryItem equipment = new CategoryItem(Material.IRON_SWORD, "§b§lEquipment", 19);
            public CategoryItem consumables = new CategoryItem(Material.GOLDEN_APPLE, "§a§lConsumables", 21);
            public CategoryItem upgrades = new CategoryItem(Material.ANVIL, "§d§lUpgrades", 23);
        }
        
        public static class CategoryItem {
            public Material material;
            public String name;
            public int slot;
            
            public CategoryItem(Material material, String name, int slot) {
                this.material = material;
                this.name = name;
                this.slot = slot;
            }
        }
        
        public static class ShopRestrictions {
            public boolean requireOnDuty = true;
            public boolean requireDutyForUse = true;
            public boolean enforceCooldowns = true;
            public Map<String, String> rankRestrictions = new HashMap<>();
            public Map<String, Integer> purchaseLimits = new HashMap<>();
        }
        
        public static class ShopMultipliers {
            public Map<String, Double> rankMultipliers = new HashMap<>();
            public double weekend = 1.5;
            public double nightShift = 1.3;
            public double holiday = 2.0;
            public Map<Integer, Double> dailyStreakMultipliers = new HashMap<>();
        }
        
        public static class ShopEconomy {
            public boolean dynamicPricingEnabled = false;
            public double fluctuationRange = 0.2;
            public boolean bulkDiscountsEnabled = true;
            public Map<Integer, Double> bulkDiscountThresholds = new HashMap<>();
            public boolean salesEnabled = true;
            public double lowGuardCountSale = 0.25;
            public double highCrimeRateSale = 0.20;
        }
    }

    public static class ShopItem {
        public int cost;
        public String description;
        public Material material = Material.GOLD_NUGGET;
        public int amount = 1;
        public List<String> effects = new ArrayList<>();
        public List<String> enchantments = new ArrayList<>();
        public double stunDuration;
        public int droppedCharges;
        public int range;
        public int duration;
        public int radius;
        public int protectionDuration;
    }

    /**
     * Items configuration class
     */
    public static class ItemsConfig {
        public GuardItem handcuffs = new GuardItem();
        public GuardItem drugSniffer = new GuardItem();
        public GuardItem metalDetector = new GuardItem();
        public GuardItem spyglass = new GuardItem();
        public GuardItem prisonRemote = new GuardItem();
        public GuardItem guardBaton = new GuardItem();
        public GuardItem taser = new GuardItem();
        public GuardItem smokeBomb = new GuardItem();
        public GuardItem riotShield = new GuardItem();
        public GuardItem sobrietyTest = new GuardItem();
        public GeneralItemSettings general = new GeneralItemSettings();
        
        // Reference to the configuration file for dynamic access
        private FileConfiguration config;
        
        public void setConfig(FileConfiguration config) {
            this.config = config;
        }
        
        public double getDouble(String path, double defaultValue) {
            if (config != null) {
                return config.getDouble(path, defaultValue);
            }
            return defaultValue;
        }
        
        public int getInt(String path, int defaultValue) {
            if (config != null) {
                return config.getInt(path, defaultValue);
            }
            return defaultValue;
        }
        
        public String getString(String path, String defaultValue) {
            if (config != null) {
                return config.getString(path, defaultValue);
            }
            return defaultValue;
        }
        
        public boolean getBoolean(String path, boolean defaultValue) {
            if (config != null) {
                return config.getBoolean(path, defaultValue);
            }
            return defaultValue;
        }
        
        public List<String> getStringList(String path) {
            if (config != null) {
                return config.getStringList(path);
            }
            return new ArrayList<>();
        }

        public static class GeneralItemSettings {
            public boolean protectOnDeath = true;
            public boolean removeOnOffDuty = true;
            public double globalMaxRange = 15.0;
            public long spamProtectionCooldown = 1000;
        }
    }

    public static class GuardItem {
        public Material material = Material.GOLD_NUGGET;
        public String name = "§eGuard Item";
        public List<String> lore = new ArrayList<>();
        public double range = 5.0;
        public double maxDistance = 5.0;
        public int countdown = 5;
        public int cooldown = 30;
        public boolean protectGuards = true;
        public List<String> safeRegions = new ArrayList<>();
        public int rewardBase;
        public int rewardPerLevel;
        public int rewardNoFind;
        public int rewardPerDrug;
        public int rewardFind;
        public int rewardPass;
        public int rewardFail;
        public int minWantedLevel;
        public int lockdownDuration;
        public int slownessDuration;
        public boolean pvpOnly;
        public double stunDuration;
        public int droppedCharges;
        public int blackoutDuration;
        public int darknessDuration;
        public int effectRange;
        public int protectionDuration;
    }

    public static class RanksConfig {
        // To be implemented when reading ranks.yml
    }

    public static class CombatConfig {
        // To be implemented when reading combat.yml
    }

    public static class LootConfig {
        // To be implemented when reading loot.yml
    }

    public static class InterfaceConfig {
        // To be implemented when reading interface.yml
    }

    public static class IntegrationsConfig {
        // To be implemented when reading integrations.yml
    }
}