package dev.lsdmc.edencorrections;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.lsdmc.edencorrections.commands.CommandHandler;
import dev.lsdmc.edencorrections.commands.NPCCommandHandler;
import dev.lsdmc.edencorrections.gui.GuiManager;
import dev.lsdmc.edencorrections.listeners.GuardDeathListener;
import dev.lsdmc.edencorrections.listeners.GuardImmobilizationListener;
import dev.lsdmc.edencorrections.listeners.GuiListener;
import dev.lsdmc.edencorrections.listeners.PlayerListener;
import dev.lsdmc.edencorrections.managers.DataManager;
import dev.lsdmc.edencorrections.managers.DutyManager;
import dev.lsdmc.edencorrections.managers.GuardBuffManager;
import dev.lsdmc.edencorrections.managers.GuardLootManager;
import dev.lsdmc.edencorrections.managers.GuardPenaltyManager;
import dev.lsdmc.edencorrections.managers.GuardRankManager;
import dev.lsdmc.edencorrections.managers.GuardRestrictionManager;
import dev.lsdmc.edencorrections.managers.NPCManager;
import dev.lsdmc.edencorrections.managers.StorageManager;
import dev.lsdmc.edencorrections.placeholders.CorrectionsPlaceholders;
import dev.lsdmc.edencorrections.utils.GuardLootProcessor;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public class EdenCorrections extends JavaPlugin {
    private static EdenCorrections instance;
    private MiniMessage miniMessage;
    private StorageManager storageManager;
    private NPCManager npcManager;
    private DutyManager dutyManager;
    private RegionContainer regionContainer;
    private LuckPerms luckPermsApi;

    // Managers
    private GuardBuffManager guardBuffManager;
    private GuardRestrictionManager guardRestrictionManager;
    private GuardLootManager guardLootManager;
    private GuardPenaltyManager guardPenaltyManager;
    private GuiManager guiManager;
    private RegionUtils regionUtils;

    // New managers/processors
    private GuardRankManager guardRankManager;
    private GuardLootProcessor guardLootProcessor;

    @Override
    public void onEnable() {
        instance = this;
        miniMessage = MiniMessage.miniMessage();

        // Set up enhanced logging
        getLogger().info("Initializing EdenCorrections plugin...");

        // Save default config
        saveDefaultConfig();

        // Initialize WorldGuard integration
        initWorldGuard();

        // Initialize LuckPerms integration
        initLuckPerms();

        // Initialize storage with DataManager
        initStorage();

        // Initialize region utils
        regionUtils = new RegionUtils();

        // Initialize NPC manager
        npcManager = new NPCManager(this);

        // Initialize GUI manager
        guiManager = new GuiManager(this);

        // Initialize new guard rank manager
        guardRankManager = new GuardRankManager(this);
        getLogger().info("Guard rank manager initialized");

        // Initialize guard loot processor
        guardLootProcessor = new GuardLootProcessor(this, guardRankManager);
        getLogger().info("Guard loot processor initialized");

        // Initialize duty manager with NPC manager
        dutyManager = new DutyManager(this, npcManager);

        // Initialize new managers
        guardBuffManager = new GuardBuffManager(this);
        guardRestrictionManager = new GuardRestrictionManager(this);
        guardLootManager = new GuardLootManager(this);
        guardPenaltyManager = new GuardPenaltyManager(this);

        // Register commands
        CommandHandler commandHandler = new CommandHandler(this);
        Objects.requireNonNull(getCommand("edencorrections")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("edencorrections")).setTabCompleter(commandHandler);

        // Register NPC commands
        NPCCommandHandler npcCommandHandler = new NPCCommandHandler(this, npcManager);
        Objects.requireNonNull(getCommand("dutynpc")).setExecutor(npcCommandHandler);
        Objects.requireNonNull(getCommand("dutynpc")).setTabCompleter(npcCommandHandler);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // Register the death listener
        getServer().getPluginManager().registerEvents(new GuardDeathListener(this), this);
        getLogger().info("Registered GuardDeathListener");

        // Register the immobilization listener (NEW)
        getServer().getPluginManager().registerEvents(new GuardImmobilizationListener(this, dutyManager), this);
        getLogger().info("Registered GuardImmobilizationListener");

        // Register placeholders if PlaceholderAPI is present
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CorrectionsPlaceholders(this).register();
            getLogger().info("PlaceholderAPI found, registering placeholders");
        }

        // Validate config
        validateConfig();

        getLogger().info("EdenCorrections plugin enabled successfully!");
    }

    private void initWorldGuard() {
        try {
            regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            getLogger().info("WorldGuard integration successful");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize WorldGuard integration: " + e.getMessage());
            getLogger().severe("The plugin will not function correctly without WorldGuard");
        }
    }

    private void initLuckPerms() {
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPermsApi = LuckPermsProvider.get();
                getLogger().info("LuckPerms integration successful");
            } else {
                getLogger().warning("LuckPerms not found, guard rank features will be disabled");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to initialize LuckPerms integration: " + e.getMessage());
            getLogger().warning("Guard rank features will be disabled");
        }
    }

    private void initStorage() {
        // Initialize DataManager as our storage implementation
        storageManager = new DataManager(this);
        getLogger().info("Initializing optimized DataManager for storage");
        storageManager.initialize();
    }

    /**
     * Validate config settings and ensure required entries exist
     */
    private void validateConfig() {
        try {
            getLogger().info("Validating configuration...");

            // Check for immobilization settings
            if (!getConfig().contains("duty.immobilization-duration")) {
                getConfig().set("duty.immobilization-duration", 30);
                getLogger().info("Added missing config: duty.immobilization-duration");
            }

            if (!getConfig().contains("messages.immobilization-start")) {
                getConfig().set("messages.immobilization-start",
                        "<yellow>You are immobilized for {seconds} seconds while preparing for duty!</yellow>");
                getLogger().info("Added missing config: messages.immobilization-start");
            }

            if (!getConfig().contains("messages.immobilization-broadcast")) {
                getConfig().set("messages.immobilization-broadcast",
                        "<red>WARNING: {rank} {player} is going on duty in {seconds} seconds!</red>");
                getLogger().info("Added missing config: messages.immobilization-broadcast");
            }

            if (!getConfig().contains("messages.immobilization-reminder")) {
                getConfig().set("messages.immobilization-reminder",
                        "<red>You cannot move while preparing for duty! ({seconds}s remaining)</red>");
                getLogger().info("Added missing config: messages.immobilization-reminder");
            }

            if (!getConfig().contains("messages.immobilization-complete")) {
                getConfig().set("messages.immobilization-complete",
                        "<green>You are now on duty and ready to patrol!</green>");
                getLogger().info("Added missing config: messages.immobilization-complete");
            }

            if (!getConfig().contains("messages.immobilization-complete-broadcast")) {
                getConfig().set("messages.immobilization-complete-broadcast",
                        "<red>ALERT: {player} is now on duty and patrolling!</red>");
                getLogger().info("Added missing config: messages.immobilization-complete-broadcast");
            }

            // Add enhanced GUI setting
            if (!getConfig().contains("gui.use-enhanced-gui")) {
                getConfig().set("gui.use-enhanced-gui", true);
                getLogger().info("Added missing config: gui.use-enhanced-gui");
            }

            // Set show on join to false
            if (getConfig().contains("gui.show-on-join")) {
                getConfig().set("gui.show-on-join", false);
                getLogger().info("Updated config: gui.show-on-join set to false");
            }

            // Save config changes
            saveConfig();

            // Check for shield in each rank's loot table
            if (getConfig().contains("guard-loot.ranks")) {
                for (String rank : getConfig().getConfigurationSection("guard-loot.ranks").getKeys(false)) {
                    String weaponsPath = "guard-loot.ranks." + rank + ".weapons";

                    if (getConfig().contains(weaponsPath)) {
                        boolean hasShield = false;

                        // Check if shield is configured for this rank
                        for (Object item : getConfig().getList(weaponsPath, java.util.Collections.emptyList())) {
                            if (item instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> itemMap = (java.util.Map<String, Object>) item;
                                if (itemMap.containsKey("item") && "SHIELD".equals(itemMap.get("item"))) {
                                    hasShield = true;
                                    break;
                                }
                            }
                        }

                        if (!hasShield) {
                            getLogger().warning("Shield not found in loot table for rank: " + rank);
                            getLogger().warning("Guards with this rank will receive a default shield");
                        }
                    }
                }
            }

            getLogger().info("Configuration validation complete");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during config validation", e);
        }
    }

    @Override
    public void onDisable() {
        // Save all data
        if (dutyManager != null) {
            dutyManager.onDisable();
        }

        // Close storage connections
        if (storageManager != null) {
            storageManager.shutdown();
        }

        // Shutdown NPC manager
        if (npcManager != null) {
            npcManager.shutdown();
        }

        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);

        getLogger().info("EdenCorrections plugin disabled!");
    }

    public void reload() {
        getLogger().info("Reloading EdenCorrections plugin...");
        reloadConfig();

        if (storageManager != null) {
            storageManager.reload();
        }

        if (dutyManager != null) {
            dutyManager.reload();
        }

        // Reload managers
        if (guardBuffManager != null) {
            guardBuffManager.reload();
        }

        if (guardRestrictionManager != null) {
            guardRestrictionManager.reload();
        }

        if (guardLootManager != null) {
            guardLootManager.reload();
        }

        if (guardPenaltyManager != null) {
            guardPenaltyManager.reload();
        }

        // Validate config after reload
        validateConfig();

        getLogger().info("EdenCorrections plugin reloaded successfully!");
    }

    // Utility methods
    public static EdenCorrections getInstance() {
        return instance;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public DutyManager getDutyManager() {
        return dutyManager;
    }

    public NPCManager getNpcManager() {
        return npcManager;
    }

    public RegionContainer getRegionContainer() {
        return regionContainer;
    }

    public LuckPerms getLuckPermsApi() {
        return luckPermsApi;
    }

    public boolean hasLuckPerms() {
        return luckPermsApi != null;
    }

    // Getters for managers
    public GuardBuffManager getGuardBuffManager() {
        return guardBuffManager;
    }

    public GuardRestrictionManager getGuardRestrictionManager() {
        return guardRestrictionManager;
    }

    public GuardLootManager getGuardLootManager() {
        return guardLootManager;
    }

    public GuardPenaltyManager getGuardPenaltyManager() {
        return guardPenaltyManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public RegionUtils getRegionUtils() {
        return regionUtils;
    }

    public GuardRankManager getGuardRankManager() {
        return guardRankManager;
    }

    public GuardLootProcessor getGuardLootProcessor() {
        return guardLootProcessor;
    }

    // Cast storage manager to DataManager to access additional methods
    public DataManager getDataManager() {
        return (DataManager) storageManager;
    }

    /**
     * Get the kit name for a given guard rank
     * @param rank The guard rank
     * @return The kit name from config, or the rank name if not configured
     * @deprecated Use GuardRankManager.getKitNameForPlayer() instead
     */
    @Deprecated
    public String getKitForRank(String rank) {
        // Check if rank is null or empty
        if (rank == null || rank.isEmpty()) {
            return "guard"; // Default kit
        }

        // Check for custom kit override in config
        return getConfig().getString("duty.rank-kits." + rank.toLowerCase(), rank);
    }

    /**
     * Get the guard rank of a player from their permissions
     * @param player The player to check
     * @return The highest guard rank the player has, or null if none
     * @deprecated Use GuardRankManager.getPlayerRank() instead
     */
    @Deprecated
    public String getPlayerGuardRank(Player player) {
        if (player == null) {
            return null;
        }

        String highestRank = null;
        int highestPriority = -1;

        // Check each possible rank from config
        if (getConfig().contains("duty.rank-kits")) {
            int priority = 0;
            for (String rank : getConfig().getConfigurationSection("duty.rank-kits").getKeys(false)) {
                // Check for the rank permission
                if (player.hasPermission("edencorrections.rank." + rank)) {
                    // If this rank has higher priority (checked later in config), use it
                    if (priority > highestPriority) {
                        highestRank = rank;
                        highestPriority = priority;
                    }
                }
                priority++;
            }
        }

        return highestRank;
    }
}