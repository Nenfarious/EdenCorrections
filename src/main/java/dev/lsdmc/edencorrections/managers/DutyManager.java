package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.events.GuardDutyStartEvent;
import dev.lsdmc.edencorrections.events.GuardDutyEndEvent;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the guard duty system including immobilization, inventory management, and activity tracking
 * Updated to use centralized configuration management
 */
public class DutyManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.DutyConfig dutyConfig;
    private ConfigManager.MessagesConfig messagesConfig;

    private final Map<UUID, Boolean> dutyStatus = new HashMap<>();
    private final Map<UUID, Long> dutyStartTimes = new HashMap<>();
    private final Map<UUID, Integer> offDutyMinutes = new HashMap<>();
    private final Map<UUID, InventoryData> savedInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> playerTimers = new HashMap<>();
    private final NPCManager npcManager;
    private final StorageManager storageManager;

    // Immobilization tracking
    private final Map<UUID, Boolean> immobilizedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immobilizedGuards = new HashMap<>();
    private final Map<UUID, BukkitTask> immobilizationTasks = new HashMap<>();
    private final Map<UUID, Long> lastReminderTime = new HashMap<>();

    private BukkitTask decayTask;
    private BukkitTask timeCheckTask;
    private File inventoryFile;
    private FileConfiguration inventoryConfig;

    // Emergency killswitch
    private static volatile boolean emergencyShutdown = false;

    private File dataDir;
    private boolean useInventoryCache;

    public DutyManager(EdenCorrections plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.npcManager = npcManager;
        this.storageManager = plugin.getStorageManager();
        loadConfig();

        this.dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        // Check if we should use file-based inventory caching
        useInventoryCache = plugin.getConfigManager().getDutyConfig().inventoryCacheEnabled;
        
        if (useInventoryCache) {
            inventoryFile = new File(dataDir, "inventories.yml");
            if (!inventoryFile.exists()) {
                try {
                    plugin.getDataFolder().mkdirs();
                    inventoryFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create inventories.yml", e);
                }
            }
            inventoryConfig = YamlConfiguration.loadConfiguration(inventoryFile);
        }

        // Load saved inventories
        loadInventories();

        // Load data from storage
        loadData();

        // Start decay task if enabled (TODO: implement decay feature in config)
        // For now, decay is disabled as it's not in the centralized config
    }

    private void loadConfig() {
        this.dutyConfig = configManager.getDutyConfig();
        this.messagesConfig = configManager.getMessagesConfig();
    }

    private void loadData() {
        // Load duty status directly from StorageManager
        Map<UUID, Boolean> loadedDutyStatus = storageManager.loadDutyStatus();
        if (loadedDutyStatus != null) {
            dutyStatus.putAll(loadedDutyStatus);
        }

        // Load duty start times
        Map<UUID, Long> loadedDutyStartTimes = storageManager.loadDutyStartTimes();
        if (loadedDutyStartTimes != null) {
            dutyStartTimes.putAll(loadedDutyStartTimes);
        }

        // Load off-duty minutes
        Map<UUID, Integer> loadedOffDutyMinutes = storageManager.loadOffDutyMinutes();
        if (loadedOffDutyMinutes != null) {
            offDutyMinutes.putAll(loadedOffDutyMinutes);
        }

        // FIXED: Don't automatically start duty timers on plugin load
        // Instead, only start timers when a player explicitly joins and validates their duty area
        // This prevents auto-duty behavior for players who may have disconnected while on duty
        
        // Clean up any stale duty statuses for players who may have left improperly
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only process currently online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                
                // If player is marked as on duty but hasn't been validated, clear the status
                if (isOnDuty(playerId)) {
                    plugin.getLogger().info("Player " + player.getName() + " was marked as on duty from previous session. Clearing status - they must manually go on duty again.");
                    
                    // Clear duty status to prevent auto-duty
                    dutyStatus.put(playerId, false);
                    storageManager.saveDutyStatus(playerId, false);
                    
                    // Notify player
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<yellow>Your duty status has been reset. Please use /duty to go on duty.</yellow>")
                    ));
                }
            }
        }, 60L); // Wait 3 seconds for full server startup
    }

    public void toggleDuty(Player player) {
        // Emergency killswitch check
        if (emergencyShutdown) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled for maintenance.</red>")));
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean isOnDuty = dutyStatus.getOrDefault(playerId, false);

        // CRITICAL FIX: Prevent execution if player is already immobilized/preparing for duty
        if (immobilizedGuards.containsKey(playerId)) {
            long endTime = immobilizedGuards.get(playerId);
            int remainingSeconds = Math.max(0, (int)((endTime - System.currentTimeMillis()) / 1000));
            
            if (remainingSeconds > 0) {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You are already preparing for duty! Please wait " + remainingSeconds + " more seconds.</red>")));
                return;
            } else {
                // Clean up stale immobilization data
                clearImmobilization(playerId);
            }
        }

        // REGION/NPC CHECK: Only allow going on duty if in a valid area
        if (!isOnDuty) {
            if (!isPlayerInDutyArea(player)) {
                // Prefer not-in-area if both are set, else fallback to not-in-region
                String msg = messagesConfig.notInArea != null && !messagesConfig.notInArea.isEmpty()
                        ? messagesConfig.notInArea
                        : messagesConfig.notInRegion;
                if (msg == null || msg.isEmpty()) {
                    msg = "<red>You must be in a designated duty area to go on/off duty!</red>";
                }
                player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage(msg)));
                return;
            }
        }

        if (!isOnDuty) {
            // Announce to server before starting duty
            Component announcement = MessageUtils.parseMessage(
                "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> " +
                "<yellow>" + player.getName() + " is preparing to go on guard duty! (30 seconds)</yellow>"
            );
            Bukkit.broadcast(announcement);

            // Start full immobilization sequence (bossbar, effects, etc)
            applyDutyStartImmobilization(player);
        } else {
            goOffDuty(player);
        }
    }

    private boolean goOnDuty(Player player) {
        UUID playerId = player.getUniqueId();

        // Store and clear inventory if enabled
        if (dutyConfig.clearInventory) {
            savePlayerInventory(player);
            player.getInventory().clear();
        }

        // Set duty status and start time
        dutyStatus.put(playerId, true);
        dutyStartTimes.put(playerId, System.currentTimeMillis());

        // Apply duty start immobilization
        applyDutyStartImmobilization(player);

        // Give guard kit based on rank
        executeRankKitCommand(player);

        // Broadcast message if enabled
        if (dutyConfig.broadcast && !messagesConfig.broadcastOnDuty.isEmpty()) {
            Component message = MessageUtils.parseMessage(messagesConfig.broadcastOnDuty.replace("{player}", player.getName()));
            Bukkit.broadcast(message);
        }

        // Update guard count and buffs
        plugin.getGuardBuffManager().onGuardJoin(player);

        // Start duty timer (for rewards and tracking)
        startDutyTimer(player);

        // Statistics tracking
        if (plugin.getGuardStatisticsManager().isStatisticsEnabled()) {
            plugin.getGuardStatisticsManager().startDutySession(player);
        }

        // Handle wanted level manager integration (for spyglass glow effects)
        plugin.getWantedLevelManager().handleGuardJoin(player);

        // Fire custom event
        Bukkit.getPluginManager().callEvent(new dev.lsdmc.edencorrections.events.GuardDutyStartEvent(player));

        plugin.getLogger().info(player.getName() + " went on duty");
        return true;
    }

    /**
     * Applies immobilization effect to a guard going on duty
     */
    private void applyDutyStartImmobilization(Player player) {
        UUID playerId = player.getUniqueId();
        
        // CRITICAL: Check if player is already immobilized to prevent duplicate processes
        if (immobilizedGuards.containsKey(playerId)) {
            long endTime = immobilizedGuards.get(playerId);
            int remainingSeconds = Math.max(0, (int)((endTime - System.currentTimeMillis()) / 1000));
            
            if (remainingSeconds > 0) {
                plugin.getLogger().warning("Attempted to start immobilization for " + player.getName() + " but they are already immobilized for " + remainingSeconds + " more seconds - skipping");
                return;
            } else {
                // Clean up stale immobilization data
                clearImmobilization(playerId);
            }
        }
        
        // CRITICAL: Check if there's already an active immobilization task
        if (immobilizationTasks.containsKey(playerId)) {
            BukkitTask existingTask = immobilizationTasks.get(playerId);
            if (existingTask != null && !existingTask.isCancelled()) {
                plugin.getLogger().warning("Attempted to start immobilization for " + player.getName() + " but they already have an active immobilization task - cancelling old task");
                existingTask.cancel();
                immobilizationTasks.remove(playerId);
            }
        }
        
        // Fixed 30 second immobilization
        int seconds = 30;

        // Create immobilization status tracker
        immobilizedGuards.put(playerId, System.currentTimeMillis() + (seconds * 1000));

        // Apply visual effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 255, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, seconds * 20, 128, false, false));

        // Send messages to player and broadcast to server
        String guardRank = plugin.getGuardRankManager().getPlayerRank(player);
        String rankDisplay = guardRank != null ? guardRank.substring(0, 1).toUpperCase() + guardRank.substring(1) : "Guard";

        // Broadcast to all players about the guard going on duty
        Component broadcast = MessageUtils.parseMessage(
            "<red>WARNING: " + rankDisplay + " " + player.getName() + " is going on duty in " + seconds + " seconds!</red>");
        Bukkit.broadcast(broadcast);

        // Send message to the guard
        Component playerMessage = MessageUtils.parseMessage(
            "<yellow>You are immobilized for " + seconds + " seconds while preparing for duty!</yellow>");
        player.sendMessage(MessageUtils.getPrefix(plugin).append(playerMessage));

        // Create boss bar as visual countdown
        BossBar countdownBar = Bukkit.createBossBar(
                "§c§lDuty Preparation: §e" + seconds + " seconds",
                BarColor.RED,
                BarStyle.SOLID);
        countdownBar.addPlayer(player);

        // Schedule task to update boss bar and handle countdown
        BukkitTask immobilizationTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = seconds;

            @Override
            public void run() {
                // CRITICAL: Check if player went offline or is no longer immobilized
                if (!player.isOnline() || !immobilizedGuards.containsKey(playerId)) {
                    // Clean up and cancel
                    if (countdownBar != null) {
                        countdownBar.removeAll();
                    }
                    if (immobilizationTasks.containsKey(playerId)) {
                        immobilizationTasks.get(playerId).cancel();
                        immobilizationTasks.remove(playerId);
                    }
                    clearImmobilization(playerId);
                    return;
                }
                
                secondsLeft--;

                if (secondsLeft <= 0) {
                    // Time's up, complete immobilization
                    finishImmobilization(player, countdownBar, this);
                } else {
                    // Update boss bar
                    countdownBar.setProgress((double) secondsLeft / seconds);
                    countdownBar.setTitle("§c§lDuty Preparation: §e" + secondsLeft + " seconds");

                    // Play tick sound every 5 seconds and for last 5 seconds
                    if (secondsLeft <= 5 || secondsLeft % 5 == 0) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    }

                    // Send reminder broadcast every 10 seconds
                    if (secondsLeft % 10 == 0) {
                        Component reminder = MessageUtils.parseMessage(
                            "<red>WARNING: " + rankDisplay + " " + player.getName() + " is going on duty in " + secondsLeft + " seconds!</red>");
                        Bukkit.broadcast(reminder);
                    }
                }
            }
        }, 20L, 20L); // Run every second

        // Store task for cleanup if needed
        immobilizationTasks.put(playerId, immobilizationTask);
    }

    /**
     * Completes the guard immobilization and initializes duty
     */
    private void finishImmobilization(Player player, BossBar countdownBar, Runnable task) {
        UUID playerId = player.getUniqueId();

        // CRITICAL: Check if player is already on duty to prevent duplicate execution
        if (dutyStatus.getOrDefault(playerId, false)) {
            plugin.getLogger().warning("Attempted to finish immobilization for " + player.getName() + " who is already on duty - skipping");
            // Clean up the boss bar and task anyway
            if (countdownBar != null) {
                countdownBar.removeAll();
            }
            if (immobilizationTasks.containsKey(playerId)) {
                immobilizationTasks.get(playerId).cancel();
                immobilizationTasks.remove(playerId);
            }
            return;
        }

        // CRITICAL: Check if immobilization was already completed/cancelled
        if (!immobilizedGuards.containsKey(playerId)) {
            plugin.getLogger().warning("Attempted to finish immobilization for " + player.getName() + " but no immobilization record found - skipping");
            // Clean up the boss bar and task anyway
            if (countdownBar != null) {
                countdownBar.removeAll();
            }
            return;
        }

        // Set duty status and start time (do not call goOnDuty again)
        dutyStatus.put(playerId, true);
        dutyStartTimes.put(playerId, System.currentTimeMillis());

        // Remove from tracking
        immobilizedGuards.remove(playerId);

        // Remove boss bar
        if (countdownBar != null) {
            countdownBar.removeAll();
        }

        // Cancel task
        if (immobilizationTasks.containsKey(playerId)) {
            immobilizationTasks.get(playerId).cancel();
            immobilizationTasks.remove(playerId);
        }

        // Inventory caching (save on duty if enabled)
        if (dutyConfig.inventoryCacheEnabled && dutyConfig.inventoryCacheSaveOnDuty) {
            try {
                savePlayerInventoryWithConfig(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save inventory for " + player.getName() + " during duty start: " + e.getMessage());
                // Continue with duty process
            }
        }

        // Clear inventory if enabled (this was missing!)
        if (dutyConfig.clearInventory) {
            try {
                player.getInventory().clear();
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Cleared inventory for " + player.getName() + " when going on duty");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to clear inventory for " + player.getName() + " during duty start: " + e.getMessage());
                // Continue with duty process
            }
        }

        // Give guard kit based on rank
        try {
            executeRankKitCommand(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give kit to " + player.getName() + " during duty start: " + e.getMessage());
            // Continue with duty process
        }

        // Update guard count and buffs
        try {
            plugin.getGuardBuffManager().onGuardJoin(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update guard buffs for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Start duty timer
        try {
            startDutyTimer(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start duty timer for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Statistics tracking
        try {
            if (plugin.getGuardStatisticsManager().isStatisticsEnabled()) {
                plugin.getGuardStatisticsManager().startDutySession(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start statistics session for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Handle wanted level manager integration (for spyglass glow effects)
        // CRITICAL: Wrap this in try-catch as this is where the team packet error occurs
        try {
            plugin.getWantedLevelManager().handleGuardJoin(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to handle guard join for WantedLevelManager for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process - this is not critical for duty functionality
        }

        // Fire custom event
        try {
            Bukkit.getPluginManager().callEvent(new dev.lsdmc.edencorrections.events.GuardDutyStartEvent(player));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fire GuardDutyStartEvent for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Send ready message
        try {
            Component readyMessage = MessageUtils.parseMessage("<green>You are now on duty and ready to patrol!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(readyMessage));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send ready message to " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Send final broadcast
        try {
            Component finalBroadcast = MessageUtils.parseMessage("<red>ALERT: " + player.getName() + " is now on duty and patrolling!</red>");
            Bukkit.broadcast(finalBroadcast);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send final broadcast for " + player.getName() + ": " + e.getMessage());
            // Continue with duty process
        }

        // Play alert sound for all players
        try {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play alert sounds for " + player.getName() + " duty start: " + e.getMessage());
            // Continue with duty process
        }

        plugin.getLogger().info(player.getName() + " went on duty");
    }

    /**
     * Check if a player is currently immobilized
     */
    public boolean isPlayerImmobilized(UUID playerId) {
        if (emergencyShutdown) return false;
        
        // Check if player is in the immobilized map with a valid timestamp
        if (immobilizedGuards.containsKey(playerId)) {
            long endTime = immobilizedGuards.get(playerId);
            if (System.currentTimeMillis() < endTime) {
                return true;
            } else {
                // Clean up expired immobilization
                clearImmobilization(playerId);
            }
        }
        
        return immobilizedPlayers.getOrDefault(playerId, false);
    }

    /**
     * Send a reminder about immobilization with a cooldown to prevent spam
     */
    public void sendImmobilizationReminderWithCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check if we've sent a reminder recently (1.5 second cooldown)
        if (lastReminderTime.containsKey(playerId) && now - lastReminderTime.get(playerId) < 1500) {
            return;
        }

        // Calculate remaining time
        long endTime = immobilizedGuards.getOrDefault(playerId, 0L);
        int secondsRemaining = Math.max(0, (int)((endTime - now) / 1000));

        if (secondsRemaining > 0) {
            Component message = MessageUtils.parseMessage(messagesConfig.immobilizationReminder
                    .replace("{seconds}", String.valueOf(secondsRemaining)));
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

            // Update last reminder time
            lastReminderTime.put(playerId, now);
        }
    }

    private boolean goOffDuty(Player player) {
        UUID playerId = player.getUniqueId();

        // Clear immobilization if active
        clearImmobilization(playerId);

        // Calculate time served
        Long startTime = dutyStartTimes.get(playerId);
        int minutesServed = 0;
        if (startTime != null) {
            long timeServed = System.currentTimeMillis() - startTime;
            minutesServed = (int) (timeServed / (1000 * 60));
            dutyStartTimes.remove(playerId);
        }

        // Update duty status
        dutyStatus.put(playerId, false);

        // Check for time reward
        if (minutesServed >= dutyConfig.thresholdMinutes) {
            int currentMinutes = offDutyMinutes.getOrDefault(playerId, 0);
            int newMinutes = Math.min(currentMinutes + dutyConfig.rewardMinutes, dutyConfig.maxOffDutyTime);
            offDutyMinutes.put(playerId, newMinutes);

            // Send reward message
            Component rewardMessage = MessageUtils.parseMessage(
                messagesConfig.offDutyReward.replace("{minutes}", String.valueOf(dutyConfig.rewardMinutes)));
            player.sendMessage(MessageUtils.getPrefix(plugin).append(rewardMessage));

            // Check if we hit the cap
            if (newMinutes >= dutyConfig.maxOffDutyTime) {
                Component capMessage = MessageUtils.parseMessage(
                    messagesConfig.timeAddedCapped.replace("{max}", String.valueOf(dutyConfig.maxOffDutyTime)));
                player.sendMessage(MessageUtils.getPrefix(plugin).append(capMessage));
            }
        }

        // Clear inventory and restore previous inventory
        player.getInventory().clear();
        if (dutyConfig.clearInventory) {
            restorePlayerInventory(player);
        }

        // Broadcast message if enabled
        if (dutyConfig.broadcast && !messagesConfig.broadcastOffDuty.isEmpty()) {
            Component message = MessageUtils.parseMessage(messagesConfig.broadcastOffDuty.replace("{player}", player.getName()));
            Bukkit.broadcast(message);
        }

        // Update guard count and buffs
        plugin.getGuardBuffManager().onGuardQuit(player);

        // Cancel duty timer
        if (playerTimers.containsKey(playerId)) {
            playerTimers.get(playerId).cancel();
            playerTimers.remove(playerId);
        }

        // Statistics tracking
        if (plugin.getGuardStatisticsManager().isStatisticsEnabled()) {
            plugin.getGuardStatisticsManager().endDutySession(player);
        }

        // Handle wanted level manager integration (remove glow effects for this guard)
        plugin.getWantedLevelManager().handleGuardLeave(player);

        // Fire custom event
        Bukkit.getPluginManager().callEvent(new dev.lsdmc.edencorrections.events.GuardDutyEndEvent(player));

        plugin.getLogger().info(player.getName() + " went off duty after " + minutesServed + " minutes");
        return true;
    }

    /**
     * Execute a kit command based on player's guard rank
     * @param player The player to give the kit to
     */
    private void executeRankKitCommand(Player player) {
        // Use the new GuardRankManager to get kit name
        String kitName = plugin.getGuardRankManager().getKitNameForPlayer(player);

        if (kitName == null) {
            plugin.getLogger().warning("No kit found for player " + player.getName() + " - no kit will be given");
            return;
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Giving " + player.getName() + " kit: " + kitName);
        }

        // Get the kit command format from integrations config
        String commandFormat = plugin.getConfigManager().getIntegrationsConfig().kitCommandFormat;
        if (commandFormat == null || commandFormat.isEmpty()) {
            commandFormat = "cmi kit {kit} {player}"; // Default fallback
        }
        
        // Build and execute the command using the configured format
        String kitCommand = commandFormat
                .replace("{kit}", kitName)
                .replace("{player}", player.getName());

        // Execute the command on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Executing kit command: " + kitCommand);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), kitCommand);
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Gave player " + player.getName() + " the " + kitName + " kit.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error executing kit command: " + kitCommand);
                plugin.getLogger().warning(e.getMessage());
            }
        });
    }

    /**
     * Start a duty timer for a player
     * @param player The player to start the timer for
     */
    private void startDutyTimer(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel existing timer if any
        if (playerTimers.containsKey(uuid)) {
            playerTimers.get(uuid).cancel();
        }

        // Create a new timer that runs every minute
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                // Skip if player is offline or not on duty anymore
                if (!player.isOnline() || !dutyStatus.getOrDefault(uuid, false)) {
                    // Cancel the timer
                    if (playerTimers.containsKey(uuid)) {
                        playerTimers.get(uuid).cancel();
                        playerTimers.remove(uuid);
                    }
                    return;
                }

                // Calculate time served so far
                long startTime = dutyStartTimes.getOrDefault(uuid, System.currentTimeMillis());
                long onDutyTime = System.currentTimeMillis() - startTime;
                int minutesServed = (int) (onDutyTime / (1000 * 60));

                // Every 2 minutes on duty = 1 minute off duty time
                if (minutesServed % 2 == 0 && minutesServed > 0) {
                    try {
                        addOffDutyMinutes(uuid, 1);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to add off-duty minutes for " + player.getName() + ": " + e.getMessage());
                        // Continue with other operations
                    }
                }

                // Update progression system with time served
                try {
                    plugin.getGuardProgressionManager().updateTimeServed(player, 60); // 60 seconds per minute
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update time served for " + player.getName() + ": " + e.getMessage());
                    // Continue with other operations
                }

                // Check if player has exceeded their off-duty time
                if (!isOnDuty(uuid)) {
                    try {
                        int currentOffDutyTime = getRemainingOffDutyMinutes(uuid);
                        if (currentOffDutyTime <= 1) {
                            // Send warning message 1 minute before duty ends
                            Component warningMsg = MessageUtils.parseMessage("<red>Warning: Your off-duty time ends in 1 minute!</red>");
                            player.sendMessage(MessageUtils.getPrefix(plugin).append(warningMsg));
                        } else if (currentOffDutyTime <= -3) {
                            // Force teleport after 3 minutes grace period
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    // Teleport to guard lounge
                                    Location guardLounge = getGuardLoungeLocation();
                                    if (guardLounge != null) {
                                        player.teleport(guardLounge);
                                    }
                                    
                                    // Force on duty with penalty
                                    toggleDuty(player);
                                    
                                    // Add penalty time (12 minutes of required duty)
                                    player.sendMessage(MessageUtils.parseMessage("<red>You have exceeded your off-duty time by 3 minutes. You must serve 12 minutes of duty time as penalty.</red>"));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to handle off-duty time penalty for " + player.getName() + ": " + e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to check off-duty time for " + player.getName() + ": " + e.getMessage());
                        // Continue with timer
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error in duty timer for " + player.getName() + ": " + e.getMessage());
                // Don't crash the timer, just log the error and continue
            }
        }, 20L * 60, 20L * 60); // Run every minute

        playerTimers.put(uuid, task);
    }

    private Location getGuardLoungeLocation() {
        return plugin.getLocationManager().getGuardLoungeLocation();
    }

    /**
     * Check if a player is in a valid duty area
     * This method combines both region and NPC checks
     */
    public boolean isPlayerInDutyArea(Player player) {
        // If NPC system is enabled, check if player is near a Corrections NPC
        if (npcManager.isNearCorrectionsNpc(player)) {
            return true;
        }

        // If region system is enabled, check if player is in the duty region
        if (dutyConfig.regionEnabled && RegionUtils.isPlayerInRegion(player, dutyConfig.regionName)) {
            return true;
        }

        // If neither system is enabled, allow toggle anywhere
        return !dutyConfig.regionEnabled;
    }

    /**
     * These methods use StorageManager directly for activity tracking
     */
    public void recordSearch(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            storageManager.incrementSearchCount(uuid);
            plugin.getGuardStatisticsManager().recordSearch(player);
            
            // Award points for search
            plugin.getGuardProgressionManager().addPoints(player, 
                plugin.getGuardProgressionManager().getRewardAmount("search"),
                "Conducting a search");

            // Send message about search being recorded
            Component message = MessageUtils.parseMessage("<green>Search recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    public void recordSuccessfulSearch(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            storageManager.incrementSuccessfulSearchCount(uuid);
            plugin.getGuardStatisticsManager().recordSuccessfulSearch(player);
            
            // Award points for successful search and contraband
            plugin.getGuardProgressionManager().recordContraband(player);

            // Send message about successful search being recorded
            Component message = MessageUtils.parseMessage("<green>Successful search recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    public void recordMetalDetect(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            storageManager.incrementMetalDetectCount(uuid);
            plugin.getGuardStatisticsManager().recordMetalDetection(player);
            
            // Award points for metal detection
            plugin.getGuardProgressionManager().addPoints(player,
                plugin.getGuardProgressionManager().getRewardAmount("metal-detect"),
                "Successful metal detection");

            // Send message about metal detection being recorded
            Component message = MessageUtils.parseMessage("<green>Metal detection recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    public void recordApprehension(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            storageManager.incrementApprehensionCount(uuid);
            plugin.getGuardStatisticsManager().recordApprehension(player);
            
            // Award points for apprehension
            plugin.getGuardProgressionManager().addPoints(player,
                plugin.getGuardProgressionManager().getRewardAmount("apprehension"),
                "Successful apprehension");

            // Send message about apprehension being recorded
            Component message = MessageUtils.parseMessage("<green>Apprehension recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    /**
     * Get a summary of the player's current duty stats
     * @param player The player to check
     * @return A string with the player's activity summary
     */
    public String getActivitySummary(Player player) {
        UUID uuid = player.getUniqueId();

        if (!isOnDuty(uuid)) {
            return "<red>You are not on duty!</red>";
        }

        // Calculate time served
        long onDutyTime = System.currentTimeMillis() - dutyStartTimes.getOrDefault(uuid, System.currentTimeMillis());
        int minutesServed = (int) (onDutyTime / (1000 * 60));

        StringBuilder summary = new StringBuilder();
        summary.append("<gold><bold>Current Duty Session Stats:</bold></gold>\n");
        summary.append("<yellow>Time on duty: ").append(minutesServed).append(" minutes</yellow>\n");

        // Use StorageManager to get activity counts
        summary.append("<yellow>Searches performed: ").append(storageManager.getSearchCount(uuid)).append("</yellow>\n");
        summary.append("<yellow>Successful searches: ").append(storageManager.getSuccessfulSearchCount(uuid)).append("</yellow>\n");
        summary.append("<yellow>Metal detections: ").append(storageManager.getMetalDetectCount(uuid)).append("</yellow>\n");

        // Get guard rank using new rank manager
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank != null) {
            summary.append("<yellow>Current rank: ").append(rank).append("</yellow>\n");
        }

        // Show threshold status
        if (minutesServed >= dutyConfig.thresholdMinutes) {
            summary.append("<green>You've served the minimum time! Going off duty will earn you ")
                    .append(dutyConfig.rewardMinutes).append(" minutes of off-duty time.</green>");
        } else {
            summary.append("<gray>Serve ").append(dutyConfig.thresholdMinutes - minutesServed)
                    .append(" more minutes to earn off-duty time.</gray>");
        }

        return summary.toString();
    }

    public boolean isOnDuty(UUID playerId) {
        return dutyStatus.getOrDefault(playerId, false);
    }

    public long getSessionStartTime(UUID playerId) {
        return dutyStartTimes.getOrDefault(playerId, 0L);
    }

    public int getRemainingOffDutyMinutes(UUID playerId) {
        return offDutyMinutes.getOrDefault(playerId, 0);
    }

    public void addOffDutyMinutes(UUID playerId, int minutes) {
        int current = offDutyMinutes.getOrDefault(playerId, 0);
        int newTotal = Math.min(current + minutes, dutyConfig.maxOffDutyTime);
        boolean capped = (current + minutes) > dutyConfig.maxOffDutyTime;

        offDutyMinutes.put(playerId, newTotal);
        storageManager.saveOffDutyMinutes(playerId, newTotal);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            if (capped) {
                Component message = MessageUtils.parseMessage(messagesConfig.timeAddedCapped
                        .replace("{max}", String.valueOf(dutyConfig.maxOffDutyTime))
                        .replace("{player}", player.getName()));
                player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            } else {
                Component message = MessageUtils.parseMessage(messagesConfig.timeAdded
                        .replace("{minutes}", String.valueOf(minutes))
                        .replace("{player}", player.getName()));
                player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            }
        }
    }

    public void setOffDutyMinutes(UUID playerId, int minutes) {
        int capped = Math.min(minutes, dutyConfig.maxOffDutyTime);
        offDutyMinutes.put(playerId, capped);
        storageManager.saveOffDutyMinutes(playerId, capped);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Component message = MessageUtils.parseMessage(messagesConfig.timeSet
                    .replace("{minutes}", String.valueOf(minutes))
                    .replace("{player}", player.getName()));
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    /**
     * Convert off-duty minutes to tokens (guards only)
     */
    public boolean convertOffDutyMinutes(Player player, int minutes) {
        // Emergency shutdown check
        if (emergencyShutdown) {
            player.sendMessage(MessageUtils.parseMessage("<red>System is temporarily disabled.</red>"));
            return false;
        }

        UUID playerId = player.getUniqueId();
        
        // Verify player is a guard
        if (!isPlayerGuard(player)) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Token conversion is only available to guards!</red>")));
            return false;
        }

        // Check minimum minutes using shop config
        int minimumMinutes = plugin.getConfigManager().getShopConfig().conversion.offDutyToTokensMinimum;
        if (minutes < minimumMinutes) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Minimum conversion is " + minimumMinutes + " minutes!</red>")));
            return false;
        }

        // Check if player has enough off-duty minutes
        int currentMinutes = getRemainingOffDutyMinutes(playerId);
        if (currentMinutes < minutes) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You only have " + currentMinutes + " off-duty minutes available!</red>")));
            return false;
        }

        // Use internal guard token system for conversion
        boolean success = plugin.getGuardTokenManager().convertOffDutyMinutesToTokens(player, minutes);
        
        if (success && plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Converted " + minutes + " off-duty minutes to guard tokens for " + player.getName());
        }
        
        return success;
    }
    
    /**
     * Check if a player is a guard (has guard permissions)
     */
    private boolean isPlayerGuard(Player player) {
        return player.hasPermission("edencorrections.guard") || 
               player.hasPermission("edencorrections.duty") ||
               plugin.getGuardRankManager().getPlayerRank(player) != null;
    }

    public int getThresholdMinutes() {
        return dutyConfig.thresholdMinutes;
    }

    public int getRewardMinutes() {
        return dutyConfig.rewardMinutes;
    }

    public int getMaxOffDutyTime() {
        return dutyConfig.maxOffDutyTime;
    }

    /**
     * Explain the duty system to a player
     * @param player The player to explain to
     */
    public void explainSystemToPlayer(Player player) {
        player.sendMessage(getTimeTerminologyExplanation());
    }

    /**
     * Get an explanation of the duty system
     * @return A formatted explanation string
     */
    public String getTimeTerminologyExplanation() {
        return "§6§lGuard Duty System Explanation:\n" +
                "§e- You can go on guard duty at any time\n" +
                "§e- Being on duty for §f" + dutyConfig.thresholdMinutes + " minutes §eearns you §f" + dutyConfig.rewardMinutes + " minutes §eof off-duty time\n" +
                "§e- Activities like searches and takedowns will earn additional time\n" +
                "§e- The maximum off-duty time you can accumulate is §f" + dutyConfig.maxOffDutyTime + " minutes §e(§f" + (dutyConfig.maxOffDutyTime / 60) + " hours§e)\n" +
                "§e- Use §f/cor time §eto check your remaining off-duty time\n" +
                "§e- Use §f/cor stats §eto check your current duty session stats";
    }

    // Methods for inventory serialization
    private String serializeInventory(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize inventory", e);
            return null;
        }
    }

    private ItemStack[] deserializeInventory(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];

            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize inventory", e);
            return null;
        }
    }

    // Methods for saving and loading inventories to file
    private void saveInventories() {
        for (Map.Entry<UUID, InventoryData> entry : savedInventories.entrySet()) {
            UUID playerId = entry.getKey();
            InventoryData data = entry.getValue();

            String path = playerId.toString();
            inventoryConfig.set(path + ".contents", serializeInventory(data.contents));
            inventoryConfig.set(path + ".armor", serializeInventory(data.armor));
        }

        try {
            inventoryConfig.save(inventoryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save inventories to file", e);
        }
    }

    private void loadInventories() {
        ConfigurationSection section = inventoryConfig.getConfigurationSection("");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    String contentsString = inventoryConfig.getString(key + ".contents");
                    String armorString = inventoryConfig.getString(key + ".armor");

                    if (contentsString != null && armorString != null) {
                        ItemStack[] contents = deserializeInventory(contentsString);
                        ItemStack[] armor = deserializeInventory(armorString);

                        if (contents != null && armor != null) {
                            savedInventories.put(playerId, new InventoryData(contents, armor, null));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in inventories.yml: " + key);
                }
            }
        }
    }

    private void savePlayerInventory(Player player) {
        // Use config-driven version
        savePlayerInventoryWithConfig(player);
    }

    private void restorePlayerInventory(Player player) {
        // Use config-driven version
        restorePlayerInventoryWithConfig(player);
    }

    public void reload() {
        loadConfig();
        
        // Restart any active decay task
        if (decayTask != null && !decayTask.isCancelled()) {
            decayTask.cancel();
        }
        
        // We don't restart the decay task here as decay is not currently implemented
    }

    /**
     * Force a player off duty without location checks or rewards
     * Used when a player leaves the server while on duty
     */
    public void forceOffDuty(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Clear duty status immediately
        dutyStatus.put(uuid, false);
        storageManager.saveDutyStatus(uuid, false);
        
        // Cancel any active duty timer
        if (playerTimers.containsKey(uuid)) {
            playerTimers.get(uuid).cancel();
            playerTimers.remove(uuid);
        }
        
        // Clear any immobilization
        clearImmobilization(uuid);
        
        // Fire duty end event
        plugin.getServer().getPluginManager().callEvent(new GuardDutyEndEvent(player));
        
        // End duty session
        plugin.getGuardStatisticsManager().endDutySession(player);
        
        plugin.getLogger().info("Force-cleared duty status for " + player.getName());
    }

    /**
     * Clear any active immobilization for a player
     */
    public void clearImmobilization(UUID playerId) {
        // Remove from immobilization tracking
        immobilizedPlayers.remove(playerId);
        immobilizedGuards.remove(playerId);
        lastReminderTime.remove(playerId);
        
        // Cancel immobilization task if active
        if (immobilizationTasks.containsKey(playerId)) {
            immobilizationTasks.get(playerId).cancel();
            immobilizationTasks.remove(playerId);
        }
        
        // Remove potion effects if player is online
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    public void onDisable() {
        // Cancel timers
        for (BukkitTask task : playerTimers.values()) {
            task.cancel();
        }
        playerTimers.clear();

        // Cancel all immobilization tasks
        for (BukkitTask task : immobilizationTasks.values()) {
            task.cancel();
        }
        immobilizationTasks.clear();

        // Save all data - now this is handled by StorageManager
        storageManager.saveDutyStatus(dutyStatus);
        storageManager.saveDutyStartTimes(dutyStartTimes);
        storageManager.saveOffDutyMinutes(offDutyMinutes);

        // Save inventories
        saveInventories();
    }

    /**
     * Emergency killswitch - disables all duty operations
     */
    public static void setEmergencyShutdown(boolean shutdown) {
        emergencyShutdown = shutdown;
    }

    /**
     * Check if emergency shutdown is active
     */
    public static boolean isEmergencyShutdown() {
        return emergencyShutdown;
    }

    // --- Inventory Caching with Config ---
    private void savePlayerInventoryWithConfig(Player player) {
        try {
            boolean includeArmor = dutyConfig.inventoryCacheIncludeArmor;
            boolean includeOffhand = dutyConfig.inventoryCacheIncludeOffhand;
            ItemStack[] contents = player.getInventory().getContents();
            ItemStack[] armor = includeArmor ? player.getInventory().getArmorContents() : new ItemStack[0];
            ItemStack offhand = includeOffhand ? player.getInventory().getItemInOffHand() : null;
            savedInventories.put(player.getUniqueId(), new InventoryData(contents, armor, offhand));
            // Save to file
            String path = player.getUniqueId().toString();
            inventoryConfig.set(path + ".contents", serializeInventory(contents));
            inventoryConfig.set(path + ".armor", serializeInventory(armor));
            if (includeOffhand) {
                inventoryConfig.set(path + ".offhand", serializeInventory(new ItemStack[]{offhand}));
            }
            inventoryConfig.save(inventoryFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player inventory (config-driven)", e);
        }
    }

    private void restorePlayerInventoryWithConfig(Player player) {
        try {
            InventoryData data = savedInventories.remove(player.getUniqueId());
            if (data != null) {
                player.getInventory().clear();
                player.getInventory().setContents(data.contents);
                if (dutyConfig.inventoryCacheIncludeArmor && data.armor != null) {
                    player.getInventory().setArmorContents(data.armor);
                }
                if (dutyConfig.inventoryCacheIncludeOffhand && data.offhand != null) {
                    player.getInventory().setItemInOffHand(data.offhand);
                }
                // Remove from file
                inventoryConfig.set(player.getUniqueId().toString(), null);
                inventoryConfig.save(inventoryFile);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore player inventory (config-driven)", e);
        }
    }

    private static class InventoryData {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        public InventoryData(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
        }
    }
}