package dev.lsdmc.edencorrections;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.lsdmc.edencorrections.commands.base.BaseCommandHandler;
import dev.lsdmc.edencorrections.commands.admin.NPCCommandHandler;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.gui.GuiManager;
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
import dev.lsdmc.edencorrections.managers.GuardStatisticsManager;
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

import dev.lsdmc.edencorrections.managers.WantedLevelManager;
import dev.lsdmc.edencorrections.managers.ChaseManager;
import dev.lsdmc.edencorrections.managers.GuardItemManager;
import dev.lsdmc.edencorrections.listeners.ChaseListener;
import dev.lsdmc.edencorrections.managers.ExternalPluginIntegration;
import dev.lsdmc.edencorrections.managers.GuardProgressionManager;
import dev.lsdmc.edencorrections.listeners.GuardListener;
import dev.lsdmc.edencorrections.managers.GuardManager;
import dev.lsdmc.edencorrections.managers.JailManager;
import dev.lsdmc.edencorrections.managers.GuardTokenManager;
import dev.lsdmc.edencorrections.commands.guard.GuardCommandHandler;
import dev.lsdmc.edencorrections.managers.LocationManager;
import dev.lsdmc.edencorrections.utils.HelpManager;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import dev.lsdmc.edencorrections.storage.YamlStorage;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import dev.lsdmc.edencorrections.storage.MySQLStorage;
import dev.lsdmc.edencorrections.managers.loot.ModernLootManager;
import dev.lsdmc.edencorrections.managers.SafezoneManager;
import dev.lsdmc.edencorrections.managers.GuardDutyManager;
import dev.lsdmc.edencorrections.commands.GuardCommand;
import dev.lsdmc.edencorrections.services.GuardService;
import dev.lsdmc.edencorrections.services.GuiService;
import dev.lsdmc.edencorrections.services.ConfigService;
import dev.lsdmc.edencorrections.services.WorldGuardService;
import dev.lsdmc.edencorrections.items.Handcuffs;
import dev.lsdmc.edencorrections.services.HandcuffService;
import dev.lsdmc.edencorrections.services.WantedService;
import dev.lsdmc.edencorrections.services.TokenService;

public class EdenCorrections extends JavaPlugin {
    private static EdenCorrections instance;
    private MiniMessage miniMessage;

    // Configuration
    private ConfigManager configManager;

    // Core managers
    private GuardManager guardManager;
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

    // Add field
    private GuardStatisticsManager guardStatisticsManager;

    // Add new manager fields
    private WantedLevelManager wantedLevelManager;
    private ChaseManager chaseManager;
    private GuardItemManager guardItemManager;
    private ExternalPluginIntegration externalPluginIntegration;
    private GuardProgressionManager guardProgressionManager;
    private JailManager jailManager;

    // Add field
    private GuardTokenManager guardTokenManager;
    
    // Add LocationManager field
    private LocationManager locationManager;
    
    // Add HelpManager field
    private HelpManager helpManager;

    // Add new field
    private ContrabandManager contrabandManager;

    // Add new field
    private ModernLootManager modernLootManager;

    // Add new field
    private SafezoneManager safezoneManager;

    // Emergency killswitch
    private static volatile boolean emergencyShutdown = false;

    private GuardDutyManager guardDutyManager;

    private GuardService guardService;
    private GuiService guiService;
    private ConfigService configService;
    private WorldGuardService worldGuardService;
    private HandcuffService handcuffService;
    private WantedService wantedService;
    private TokenService tokenService;
    private Handcuffs handcuffs;

    @Override
    public void onEnable() {
        instance = this;
        miniMessage = MiniMessage.miniMessage();

        // Initialize configuration manager FIRST
        configManager = new ConfigManager(this);

        // Initialize LocationManager EARLY (before other managers that might need locations)
        locationManager = new LocationManager(this);
        getLogger().info("LocationManager initialized");

        // Initialize GuardRankManager
        guardRankManager = new GuardRankManager(this);

        // Initialize WorldGuard integration
        initWorldGuard();

        // Initialize LuckPerms integration
        initLuckPerms();

        // Initialize storage with DataManager
        initStorage();

        // Initialize GuardStatisticsManager AFTER storage is initialized
        guardStatisticsManager = new GuardStatisticsManager(this);

        // Initialize region utils
        regionUtils = new RegionUtils();

        // Initialize core managers
        guardManager = new GuardManager(this);
        npcManager = new NPCManager(this);
        dutyManager = new DutyManager(this, npcManager);

        // Initialize GUI manager
        guiManager = new GuiManager(this);

        // Initialize guard system managers
        guardBuffManager = new GuardBuffManager(this);
        guardRestrictionManager = new GuardRestrictionManager(this);
        guardLootManager = new GuardLootManager(this);
        guardPenaltyManager = new GuardPenaltyManager(this);
        guardProgressionManager = new GuardProgressionManager(this);
        getLogger().info("Guard system managers initialized");

        // Initialize enforcement managers
        wantedLevelManager = new WantedLevelManager(this);
        chaseManager = new ChaseManager(this);
        guardItemManager = new GuardItemManager(this);
        jailManager = new JailManager(this);
        getLogger().info("Enforcement managers initialized");

        // Initialize external plugin integration
        externalPluginIntegration = new ExternalPluginIntegration(this);
        getLogger().info("External plugin integration initialized");

        // Initialize GuardTokenManager
        guardTokenManager = new GuardTokenManager(this);

        // Initialize HelpManager
        helpManager = new HelpManager(this);
        getLogger().info("HelpManager initialized");

        // Initialize ContrabandManager
        contrabandManager = new ContrabandManager(this);
        getLogger().info("ContrabandManager initialized");

        // Initialize loot manager
        modernLootManager = new ModernLootManager(this);
        getLogger().info("ModernLootManager initialized");

        // Initialize SafezoneManager
        safezoneManager = new SafezoneManager(this);
        getLogger().info("SafezoneManager initialized");

        // Initialize GuardDutyManager
        guardDutyManager = new GuardDutyManager(this);
        getLogger().info("GuardDutyManager initialized");

        // Initialize services
        this.configService = new ConfigService(this);
        this.guardService = new GuardService(this);
        this.guiService = new GuiService(this);
        this.worldGuardService = new WorldGuardService(this);
        this.handcuffs = new Handcuffs(this);
        this.handcuffService = new HandcuffService(this);
        this.wantedService = new WantedService(this);
        this.tokenService = new TokenService(this);

        // Register commands
        BaseCommandHandler baseCommandHandler = new BaseCommandHandler(this);
        Objects.requireNonNull(getCommand("edencorrections")).setExecutor(baseCommandHandler);
        Objects.requireNonNull(getCommand("edencorrections")).setTabCompleter(baseCommandHandler);

        // Register /g command (main guard command)
        GuardCommandHandler guardCommandHandler = new GuardCommandHandler(this);
        Objects.requireNonNull(getCommand("g")).setExecutor((sender, command, label, args) -> guardCommandHandler.handleCommand(sender, args));
        Objects.requireNonNull(getCommand("g")).setTabCompleter((sender, command, label, args) -> guardCommandHandler.onTabComplete(sender, args));

        // Register contraband commands
        Objects.requireNonNull(getCommand("sword")).setExecutor((sender, command, label, args) -> guardCommandHandler.handleContrabandCommand(sender, args, "sword"));
        Objects.requireNonNull(getCommand("sword")).setTabCompleter((sender, command, label, args) -> guardCommandHandler.onTabCompleteContraband(sender, args));

        Objects.requireNonNull(getCommand("armor")).setExecutor((sender, command, label, args) -> guardCommandHandler.handleContrabandCommand(sender, args, "armor"));
        Objects.requireNonNull(getCommand("armor")).setTabCompleter((sender, command, label, args) -> guardCommandHandler.onTabCompleteContraband(sender, args));

        Objects.requireNonNull(getCommand("bow")).setExecutor((sender, command, label, args) -> guardCommandHandler.handleContrabandCommand(sender, args, "bow"));
        Objects.requireNonNull(getCommand("bow")).setTabCompleter((sender, command, label, args) -> guardCommandHandler.onTabCompleteContraband(sender, args));

        Objects.requireNonNull(getCommand("contraband")).setExecutor((sender, command, label, args) -> guardCommandHandler.handleContrabandCommand(sender, args, "contraband"));
        Objects.requireNonNull(getCommand("contraband")).setTabCompleter((sender, command, label, args) -> guardCommandHandler.onTabCompleteContraband(sender, args));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new GuardListener(this), this);
        getServer().getPluginManager().registerEvents(new ChaseListener(this), this);
        getServer().getPluginManager().registerEvents(handcuffService, this);

        // Register placeholders if PlaceholderAPI is present
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CorrectionsPlaceholders(this).register();
            getLogger().info("PlaceholderAPI found, registering placeholders");
        }

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
        // Select storage backend based on config
        String type = configManager.getStorageType();
        
        // Check if advanced features require SQLiteStorage
        boolean needsSQLite = true; // Most advanced features require SQLite
        
        if (needsSQLite && !type.equals("sqlite")) {
            getLogger().warning("========================= STORAGE WARNING =========================");
            getLogger().warning("You have configured storage type: " + type);
            getLogger().warning("However, advanced guard features require SQLiteStorage:");
            getLogger().warning("- Guard Statistics, Progression, Wanted Levels, Jail System, Tokens, Contraband");
            getLogger().warning("Automatically switching to SQLiteStorage for compatibility.");
            getLogger().warning("Update your config.yml to 'storage.type: sqlite' to remove this warning.");
            getLogger().warning("================================================================");
            type = "sqlite";
        }
        
        switch (type) {
            case "mysql" -> {
                storageManager = new MySQLStorage(this);
                getLogger().info("Using MySQLStorage for storage backend");
            }
            case "sqlite" -> {
                storageManager = new SQLiteStorage(this);
                getLogger().info("Using SQLiteStorage for storage backend");
            }
            case "yaml" -> {
                storageManager = new YamlStorage(this);
                getLogger().info("Using YamlStorage for storage backend");
            }
            case "datamanager" -> {
                storageManager = new DataManager(this);
                getLogger().info("Using DataManager for storage backend (legacy)");
            }
            default -> {
                getLogger().warning("Unknown storage type '" + type + "', defaulting to SQLiteStorage");
                storageManager = new SQLiteStorage(this);
            }
        }
        storageManager.initialize();
    }

    @Override
    public void onDisable() {
        // Save all data BEFORE closing storage
        if (guardManager != null) {
            guardManager.shutdown();
        }
        // Save jail data before closing storage
        if (jailManager != null) {
            jailManager.shutdown();
        }
        // Shutdown penalty manager
        if (guardPenaltyManager != null) {
            guardPenaltyManager.shutdown();
        }
        // Shutdown guard progression manager
        if (guardProgressionManager != null) {
            guardProgressionManager.shutdown();
        }
        // Shutdown enforcement managers
        if (chaseManager != null) {
            chaseManager.shutdown();
        }
        // Shutdown NPC manager
        if (npcManager != null) {
            npcManager.shutdown();
        }
        // Now close storage connections
        if (storageManager != null) {
            storageManager.shutdown();
        }
        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("EdenCorrections plugin disabled!");
    }

    public void reload() {
        getLogger().info("Reloading EdenCorrections plugin...");

        try {
            // Set emergency shutdown during reload to prevent conflicts
            setEmergencyShutdown(true);
            
            // Cancel all running tasks first
            getServer().getScheduler().cancelTasks(this);
            
            // Reload configuration first
            reloadConfig();
            configManager.reload();

            // Reload LocationManager
            if (locationManager != null) {
                locationManager.reload();
            }

            // Reload storage manager
            if (storageManager != null) {
                storageManager.reload();
            }

            // Reload core managers
            if (guardManager != null) {
                guardManager.reload();
            }
            if (dutyManager != null) {
                dutyManager.reload();
            }

            // Reload guard system managers
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
            if (guardProgressionManager != null) {
                guardProgressionManager.reload();
            }

            // Reload enforcement managers
            if (chaseManager != null) {
                chaseManager.reload();
            }
            if (guardItemManager != null) {
                guardItemManager.reload();
            }
            if (jailManager != null) {
                jailManager.reload();
            }
            if (wantedLevelManager != null) {
                wantedLevelManager.reload();
            }

            // Reload external integrations
            if (externalPluginIntegration != null) {
                externalPluginIntegration.reload();
            }

            // Reload token manager
            if (guardTokenManager != null) {
                guardTokenManager.reload();
            }

            // Re-enable systems after reload
            setEmergencyShutdown(false);

            getLogger().info("EdenCorrections plugin reloaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin reload", e);
            // Keep emergency shutdown active if reload failed
            getLogger().warning("Emergency shutdown remains active due to reload failure. Use '/cor emergency off' to restore systems.");
        }
    }

    // Utility methods
    public static EdenCorrections getInstance() {
        return instance;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    // Configuration access
    public ConfigManager getConfigManager() {
        return configManager;
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
        if (storageManager instanceof DataManager dm) {
            return dm;
        }
        throw new IllegalStateException("StorageManager is not a DataManager. This method is only valid for legacy/compatibility mode.");
    }

    /**
     * Check if the plugin is fully initialized and ready
     * @return True if all managers are initialized
     */
    public boolean isFullyInitialized() {
        return configManager != null &&
                storageManager != null &&
                dutyManager != null &&
                guardBuffManager != null &&
                guardRestrictionManager != null &&
                guardLootManager != null &&
                guardPenaltyManager != null &&
                guiManager != null &&
                guardRankManager != null;
    }

    /**
     * Get plugin version information
     * @return Version string
     */
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    /**
     * Get plugin status information for debugging
     * @return Map containing status information
     */
    public java.util.Map<String, Object> getStatusInfo() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("version", getPluginVersion());
        status.put("fullyInitialized", isFullyInitialized());
        status.put("worldGuardIntegration", regionContainer != null);
        status.put("luckPermsIntegration", luckPermsApi != null);
        status.put("onlineGuards", guardBuffManager != null ? guardBuffManager.getOnlineGuardCount() : 0);
        status.put("activePenalties", guardPenaltyManager != null ? guardPenaltyManager.getActivePenaltyCount() : 0);
        status.put("wantedPlayers", wantedLevelManager != null ? wantedLevelManager.getWantedPlayerCount() : 0);
        status.put("activeChases", chaseManager != null ? chaseManager.getActiveChaseCount() : 0);

        // Add configuration status
        if (configManager != null) {
            status.put("debugEnabled", configManager.isDebugEnabled());
            status.put("storageType", configManager.getStorageType());
            status.put("autoSaveInterval", configManager.getAutoSaveInterval());
        }

        return status;
    }

    // Add getter
    public GuardStatisticsManager getGuardStatisticsManager() {
        return guardStatisticsManager;
    }

    // Add getters
    public WantedLevelManager getWantedLevelManager() {
        return wantedLevelManager;
    }

    public ChaseManager getChaseManager() {
        return chaseManager;
    }

    public GuardItemManager getGuardItemManager() {
        return guardItemManager;
    }

    public ExternalPluginIntegration getExternalPluginIntegration() {
        return externalPluginIntegration;
    }

    public GuardProgressionManager getGuardProgressionManager() {
        return guardProgressionManager;
    }

    /**
     * Get the core guard manager
     */
    public GuardManager getGuardManager() {
        return guardManager;
    }

    public JailManager getJailManager() {
        return jailManager;
    }

    public GuardTokenManager getGuardTokenManager() {
        return guardTokenManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public HelpManager getHelpManager() {
        return helpManager;
    }

    public ContrabandManager getContrabandManager() {
        return contrabandManager;
    }

    public ModernLootManager getModernLootManager() {
        return modernLootManager;
    }

    public SafezoneManager getSafezoneManager() {
        return safezoneManager;
    }

    /**
     * Emergency killswitch - disables all plugin operations
     */
    public static void setEmergencyShutdown(boolean shutdown) {
        emergencyShutdown = shutdown;
        
        // Propagate to managers
        DutyManager.setEmergencyShutdown(shutdown);
        LocationManager.setEmergencyShutdown(shutdown);
        
        if (shutdown) {
            getInstance().getLogger().warning("EMERGENCY SHUTDOWN ACTIVATED - All plugin operations disabled");
        } else {
            getInstance().getLogger().info("Emergency shutdown deactivated - Plugin operations restored");
        }
    }

    /**
     * Check if emergency shutdown is active
     */
    public static boolean isEmergencyShutdown() {
        return emergencyShutdown;
    }

    public GuardDutyManager getGuardDutyManager() {
        return guardDutyManager;
    }

    public boolean hasExecutableItems() {
        return externalPluginIntegration != null && externalPluginIntegration.isExecutableItemsEnabled();
    }

    public GuardService getGuardService() {
        return guardService;
    }

    public GuiService getGuiService() {
        return guiService;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public WorldGuardService getWorldGuardService() {
        return worldGuardService;
    }

    public Handcuffs getHandcuffs() {
        return handcuffs;
    }

    public HandcuffService getHandcuffService() {
        return handcuffService;
    }

    public WantedService getWantedService() {
        return wantedService;
    }

    public TokenService getTokenService() {
        return tokenService;
    }
}