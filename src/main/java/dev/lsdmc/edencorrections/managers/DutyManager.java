package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DutyManager {
    private final EdenCorrections plugin;
    private final Map<UUID, Boolean> dutyStatus = new HashMap<>();
    private final Map<UUID, Long> dutyStartTimes = new HashMap<>();
    private final Map<UUID, Integer> offDutyMinutes = new HashMap<>();
    private final Map<UUID, InventoryData> savedInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> playerTimers = new HashMap<>();
    private final NPCManager npcManager;
    private final DataManager dataManager;

    // Immobilization tracking
    private final Map<UUID, Long> immobilizedGuards = new HashMap<>();
    private final Map<UUID, BukkitTask> immobilizationTasks = new HashMap<>();
    private final Map<UUID, Long> lastReminderTime = new HashMap<>();

    // Area detection settings
    private boolean useNpcSystem;
    private boolean useRegionSystem;

    private String dutyRegionName;
    private boolean decayEnabled;
    private int decayInterval;
    private int decayAmount;
    private int thresholdMinutes;
    private int rewardMinutes;
    private boolean broadcastToggle;
    private boolean clearInventory;
    private String onDutySound;
    private String offDutySound;
    private int maxOffDutyTime;
    private int immobilizationDuration;
    private List<String> onDutyCommands;
    private List<String> offDutyCommands;

    private BukkitTask decayTask;
    private BukkitTask timeCheckTask;
    private File inventoryFile;
    private FileConfiguration inventoryConfig;

    public DutyManager(EdenCorrections plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.dataManager = (DataManager) plugin.getStorageManager();
        loadConfig();

        // Initialize inventory storage
        inventoryFile = new File(plugin.getDataFolder(), "inventories.yml");
        if (!inventoryFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                inventoryFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create inventories.yml", e);
            }
        }
        inventoryConfig = YamlConfiguration.loadConfiguration(inventoryFile);

        // Load saved inventories
        loadInventories();

        // Load data from storage
        loadData();

        // Start decay task if enabled
        if (decayEnabled) {
            startDecayTask();
        }
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        dutyRegionName = config.getString("duty.region.name", "duty_room");
        decayEnabled = config.getBoolean("duty.decay.enabled", true);
        decayInterval = config.getInt("duty.decay.interval", 60);
        decayAmount = config.getInt("duty.decay.amount", 1);
        thresholdMinutes = config.getInt("duty.threshold-minutes", 15);
        rewardMinutes = config.getInt("duty.reward-minutes", 30);
        broadcastToggle = config.getBoolean("duty.broadcast", true);
        clearInventory = config.getBoolean("duty.clear-inventory", true);
        onDutySound = config.getString("duty.on-duty-sound", "minecraft:block.note_block.pling");
        offDutySound = config.getString("duty.off-duty-sound", "minecraft:block.note_block.bass");

        // Maximum off-duty time (default: 3 days / 72 hours / 4320 minutes)
        maxOffDutyTime = config.getInt("duty.max-off-duty-time", 4320);

        // Immobilization duration in seconds
        immobilizationDuration = config.getInt("duty.immobilization-duration", 30);

        // Area detection settings
        useNpcSystem = config.getBoolean("duty.npc.enabled", false);
        useRegionSystem = config.getBoolean("duty.region.enabled", true);

        // Load commands
        onDutyCommands = config.getStringList("duty.commands.on-duty");
        offDutyCommands = config.getStringList("duty.commands.off-duty");

        if (onDutyCommands == null) {
            onDutyCommands = new ArrayList<>();
        }

        if (offDutyCommands == null) {
            offDutyCommands = new ArrayList<>();
        }
    }

    private void loadData() {
        // Load duty status directly from DataManager
        Map<UUID, Boolean> loadedDutyStatus = dataManager.loadDutyStatus();
        if (loadedDutyStatus != null) {
            dutyStatus.putAll(loadedDutyStatus);
        }

        // Load duty start times
        Map<UUID, Long> loadedDutyStartTimes = dataManager.loadDutyStartTimes();
        if (loadedDutyStartTimes != null) {
            dutyStartTimes.putAll(loadedDutyStartTimes);
        }

        // Load off-duty minutes
        Map<UUID, Integer> loadedOffDutyMinutes = dataManager.loadOffDutyMinutes();
        if (loadedOffDutyMinutes != null) {
            offDutyMinutes.putAll(loadedOffDutyMinutes);
        }

        // Start duty timers for online players who are on duty
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isOnDuty(player.getUniqueId())) {
                    startDutyTimer(player);
                }
            }
        }, 40L); // Start after 2 seconds to ensure server is fully loaded
    }

    private void startDecayTask() {
        // Cancel existing task if running
        if (decayTask != null) {
            decayTask.cancel();
        }

        // Start new task
        decayTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processDecay,
                20L * decayInterval, 20L * decayInterval);
    }

    private void processDecay() {
        // Decrement off-duty time for players who are off duty
        for (UUID playerId : offDutyMinutes.keySet()) {
            if (!dutyStatus.getOrDefault(playerId, false)) {
                int remaining = offDutyMinutes.get(playerId);
                if (remaining > 0) {
                    offDutyMinutes.put(playerId, Math.max(0, remaining - decayAmount));

                    // Update in DataManager
                    dataManager.saveOffDutyMinutes(playerId, Math.max(0, remaining - decayAmount));

                    // Notify player if time running out
                    if (remaining <= 5 && remaining > 0) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            Component message = MessageUtils.parseMessage(plugin.getConfig()
                                    .getString("messages.time-remaining", "<yellow>You have {minutes} minutes of off-duty time remaining.</yellow>")
                                    .replace("{minutes}", String.valueOf(Math.max(0, remaining - decayAmount))));

                            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
                        }
                    }
                }
            }
        }
    }

    public boolean toggleDuty(Player player) {
        UUID uuid = player.getUniqueId();
        boolean isOnDuty = dutyStatus.getOrDefault(uuid, false);

        // Check if player is in a valid duty area
        if (!isPlayerInDutyArea(player)) {
            Component message = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.not-in-area",
                            "<red>You must be in a designated duty area to go on/off duty!</red>"));

            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            return false;
        }

        if (isOnDuty) {
            return goOffDuty(player);
        } else {
            return goOnDuty(player);
        }
    }

    private boolean goOnDuty(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if already on duty
        if (dutyStatus.getOrDefault(uuid, false)) {
            Component message = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.already-on-duty", "<red>You are already on duty!</red>"));

            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            return false;
        }

        // Log rank information for debugging
        String playerRank = plugin.getGuardRankManager().getPlayerRank(player);
        plugin.getLogger().info("Player " + player.getName() + " going on duty with rank: " + playerRank);

        // Save current inventory if needed
        if (clearInventory) {
            savePlayerInventory(player);
        }

        // Set duty status
        dutyStatus.put(uuid, true);
        dutyStartTimes.put(uuid, System.currentTimeMillis());

        // Save data to DataManager
        dataManager.saveDutyStatus(uuid, true);
        dataManager.saveDutyStartTime(uuid, System.currentTimeMillis());

        // Reset activity counters for the new session
        dataManager.resetActivityCounts(uuid);

        // Apply immobilization effect before executing commands
        applyDutyStartImmobilization(player);

        // Send message
        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.on-duty", "<green>You are now on guard duty!</green>"));

        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        // Play sound
        if (onDutySound != null && !onDutySound.isEmpty()) {
            try {
                String[] parts = onDutySound.split(":");
                if (parts.length > 1) {
                    player.playSound(player.getLocation(), Sound.valueOf(parts[1].toUpperCase()), 1.0f, 1.0f);
                }
            } catch (Exception ignored) {
                // Invalid sound, just ignore
            }
        }

        // Broadcast if enabled
        if (broadcastToggle) {
            Component broadcast = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.broadcast-on-duty", "<gold>{player} is now on guard duty!</gold>")
                    .replace("{player}", player.getName()));

            Bukkit.broadcast(MessageUtils.getPrefix(plugin).append(broadcast));
        }

        // Start duty timer for the player
        startDutyTimer(player);

        // Notify guard buff manager about new guard
        plugin.getGuardBuffManager().recalculateOnlineGuards();

        return true;
    }

    /**
     * Applies immobilization effect to a guard going on duty
     */
    private void applyDutyStartImmobilization(Player player) {
        // Get immobilization duration from config (default 30 seconds)
        int seconds = plugin.getConfig().getInt("duty.immobilization-duration", 30);

        // Create immobilization status tracker
        UUID playerId = player.getUniqueId();
        immobilizedGuards.put(playerId, System.currentTimeMillis() + (seconds * 1000));

        // Apply visual effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 255, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, seconds * 20, 128, false, false));

        // Send messages to player and broadcast to server
        String guardRank = plugin.getGuardRankManager().getPlayerRank(player);
        String rankDisplay = guardRank != null ? guardRank.substring(0, 1).toUpperCase() + guardRank.substring(1) : "Guard";

        Component playerMessage = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.immobilization-start",
                        "<yellow>You are immobilized for {seconds} seconds while preparing for duty!</yellow>")
                .replace("{seconds}", String.valueOf(seconds)));
        player.sendMessage(MessageUtils.getPrefix(plugin).append(playerMessage));

        // Send warning broadcast to other players
        Component broadcast = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.immobilization-broadcast",
                        "<red>WARNING: {rank} {player} is going on duty in {seconds} seconds!</red>")
                .replace("{player}", player.getName())
                .replace("{rank}", rankDisplay)
                .replace("{seconds}", String.valueOf(seconds)));
        Bukkit.broadcast(broadcast);

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

        // Remove from tracking
        immobilizedGuards.remove(playerId);

        // Remove boss bar
        countdownBar.removeAll();

        // Cancel task
        if (immobilizationTasks.containsKey(playerId)) {
            immobilizationTasks.get(playerId).cancel();
            immobilizationTasks.remove(playerId);
        }

        // Now execute delayed actions

        // Execute on-duty commands
        executeCommands(player, onDutyCommands);

        // Execute rank-specific kit command
        executeRankKitCommand(player);

        // Send ready message
        Component readyMessage = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.immobilization-complete",
                        "<green>You are now on duty and ready to patrol!</green>"));
        player.sendMessage(MessageUtils.getPrefix(plugin).append(readyMessage));

        // Play alert sound for all players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
        }

        // Send final broadcast
        Component finalBroadcast = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.immobilization-complete-broadcast",
                        "<red>ALERT: {player} is now on duty and patrolling!</red>")
                .replace("{player}", player.getName()));
        Bukkit.broadcast(finalBroadcast);
    }

    /**
     * Check if a player is currently immobilized
     */
    public boolean isPlayerImmobilized(UUID playerId) {
        if (!immobilizedGuards.containsKey(playerId)) {
            return false;
        }

        long endTime = immobilizedGuards.get(playerId);
        return System.currentTimeMillis() < endTime;
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
            Component message = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.immobilization-reminder",
                            "<red>You cannot move while preparing for duty! ({seconds}s remaining)</red>")
                    .replace("{seconds}", String.valueOf(secondsRemaining)));
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

            // Update last reminder time
            lastReminderTime.put(playerId, now);
        }
    }

    private boolean goOffDuty(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if already off duty
        if (!dutyStatus.getOrDefault(uuid, false)) {
            Component message = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.already-off-duty", "<red>You are already off duty!</red>"));

            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            return false;
        }

        // Calculate time served
        long onDutyTime = System.currentTimeMillis() - dutyStartTimes.getOrDefault(uuid, System.currentTimeMillis());
        int minutesServed = (int) (onDutyTime / (1000 * 60));

        // Set duty status
        dutyStatus.put(uuid, false);

        // Save data to DataManager
        dataManager.saveDutyStatus(uuid, false);

        // Execute off-duty commands
        executeCommands(player, offDutyCommands);

        // Restore inventory if needed
        if (clearInventory) {
            restorePlayerInventory(player);
        }

        // Award off-duty time if threshold met
        if (minutesServed >= thresholdMinutes) {
            // Add time with cap
            int current = offDutyMinutes.getOrDefault(uuid, 0);
            int newTotal = Math.min(current + rewardMinutes, maxOffDutyTime);
            boolean capped = (current + rewardMinutes) > maxOffDutyTime;

            offDutyMinutes.put(uuid, newTotal);
            dataManager.saveOffDutyMinutes(uuid, newTotal);

            if (capped) {
                Component rewardMessage = MessageUtils.parseMessage(plugin.getConfig()
                        .getString("messages.time-added-capped",
                                "<green>You've earned time for your duty service. You've reached the maximum of {max} minutes.</green>")
                        .replace("{max}", String.valueOf(maxOffDutyTime)));

                player.sendMessage(MessageUtils.getPrefix(plugin).append(rewardMessage));
            } else {
                Component rewardMessage = MessageUtils.parseMessage(plugin.getConfig()
                        .getString("messages.off-duty-reward", "<green>You've earned {minutes} minutes of off-duty time!</green>")
                        .replace("{minutes}", String.valueOf(rewardMinutes)));

                player.sendMessage(MessageUtils.getPrefix(plugin).append(rewardMessage));
            }
        } else {
            // Didn't reach threshold
            Component noRewardMessage = MessageUtils.parseMessage(
                    "<yellow>You served for " + minutesServed + " minutes. Serve at least " +
                            thresholdMinutes + " minutes to earn off-duty time.</yellow>");

            player.sendMessage(MessageUtils.getPrefix(plugin).append(noRewardMessage));
        }

        // Send message
        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.off-duty", "<yellow>You are now off duty.</yellow>"));

        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        // Play sound
        if (offDutySound != null && !offDutySound.isEmpty()) {
            try {
                String[] parts = offDutySound.split(":");
                if (parts.length > 1) {
                    player.playSound(player.getLocation(), Sound.valueOf(parts[1].toUpperCase()), 1.0f, 1.0f);
                }
            } catch (Exception ignored) {
                // Invalid sound, just ignore
            }
        }

        // Broadcast if enabled
        if (broadcastToggle) {
            Component broadcast = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.broadcast-off-duty", "<gold>{player} is now off duty.</gold>")
                    .replace("{player}", player.getName()));

            Bukkit.broadcast(MessageUtils.getPrefix(plugin).append(broadcast));
        }

        // Cancel duty timer
        if (playerTimers.containsKey(uuid)) {
            playerTimers.get(uuid).cancel();
            playerTimers.remove(uuid);
        }

        // Notify guard buff manager about guard going off duty
        plugin.getGuardBuffManager().recalculateOnlineGuards();

        return true;
    }

    /**
     * Execute a list of commands for a player
     * @param player The player to execute commands for
     * @param commands The list of commands to execute
     */
    private void executeCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();

        // Schedule command execution on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : commands) {
                // Replace placeholders
                String processedCommand = command
                        .replace("{player}", playerName)
                        .replace("{uuid}", playerUUID);

                // Execute command
                try {
                    if (processedCommand.startsWith("[player]")) {
                        // Player command
                        String playerCommand = processedCommand.substring(8).trim();
                        plugin.getLogger().info("Executing player command for " + player.getName() + ": " + playerCommand);
                        player.performCommand(playerCommand);
                    } else if (processedCommand.startsWith("[op]") && !player.isOp()) {
                        // OP command - temporarily make player OP
                        String opCommand = processedCommand.substring(4).trim();
                        boolean wasOp = player.isOp();
                        try {
                            plugin.getLogger().info("Executing OP command for " + player.getName() + ": " + opCommand);
                            player.setOp(true);
                            player.performCommand(opCommand);
                        } finally {
                            // Ensure we always reset OP status
                            if (!wasOp) {
                                player.setOp(false);
                            }
                        }
                    } else {
                        // Console command
                        plugin.getLogger().info("Executing console command for " + player.getName() + ": " + processedCommand);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error executing command: " + processedCommand);
                    plugin.getLogger().warning(e.getMessage());
                }
            }
        });
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

        plugin.getLogger().info("Giving " + player.getName() + " kit: " + kitName);

        // Build and execute the command
        String kitCommand = "cmi kit " + kitName + " " + player.getName();

        // Execute the command on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                plugin.getLogger().info("Executing kit command: " + kitCommand);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), kitCommand);
                plugin.getLogger().info("Gave player " + player.getName() + " the " + kitName + " kit.");
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

        // Create a new timer that runs every 5 minutes
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
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

            // If player just reached the threshold, notify them
            if (minutesServed == thresholdMinutes) {
                Component thresholdMessage = MessageUtils.parseMessage(plugin.getConfig()
                        .getString("messages.threshold-reached",
                                "<green>You've served enough time to earn a reward! Going off duty now will earn you {minutes} minutes of off-duty time.</green>")
                        .replace("{minutes}", String.valueOf(rewardMinutes)));
                player.sendMessage(MessageUtils.getPrefix(plugin).append(thresholdMessage));
            }
        }, 20 * 300, 20 * 300);  // Run every 5 minutes

        playerTimers.put(uuid, task);
    }

    /**
     * Check if a player is in a valid duty area
     * This method combines both region and NPC checks
     */
    private boolean isPlayerInDutyArea(Player player) {
        // If NPC system is enabled, check if player is near a duty NPC
        if (useNpcSystem && npcManager.isNearDutyNpc(player)) {
            return true;
        }

        // If region system is enabled, check if player is in the duty region
        if (useRegionSystem && RegionUtils.isPlayerInRegion(player, dutyRegionName)) {
            return true;
        }

        // If neither system is enabled, allow toggle anywhere
        return !useNpcSystem && !useRegionSystem;
    }

    /**
     * These methods use DataManager directly for activity tracking
     */
    public void recordSearch(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            dataManager.incrementSearchCount(uuid);

            // Send message about search being recorded
            Component message = MessageUtils.parseMessage("<green>Search recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    public void recordSuccessfulSearch(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            dataManager.incrementSuccessfulSearchCount(uuid);

            // Send message about successful search being recorded
            Component message = MessageUtils.parseMessage("<green>Successful search recorded!</green>");
            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    public void recordMetalDetect(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            dataManager.incrementMetalDetectCount(uuid);

            // Send message about metal detection being recorded
            Component message = MessageUtils.parseMessage("<green>Metal detection recorded!</green>");
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

        // Use DataManager to get activity counts
        summary.append("<yellow>Searches performed: ").append(dataManager.getSearchCount(uuid)).append("</yellow>\n");
        summary.append("<yellow>Successful searches: ").append(dataManager.getSuccessfulSearchCount(uuid)).append("</yellow>\n");
        summary.append("<yellow>Metal detections: ").append(dataManager.getMetalDetectCount(uuid)).append("</yellow>\n");

        // Get guard rank using new rank manager
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank != null) {
            summary.append("<yellow>Current rank: ").append(rank).append("</yellow>\n");
        }

        // Show threshold status
        if (minutesServed >= thresholdMinutes) {
            summary.append("<green>You've served the minimum time! Going off duty will earn you ").append(rewardMinutes).append(" minutes of off-duty time.</green>");
        } else {
            summary.append("<gray>Serve ").append(thresholdMinutes - minutesServed).append(" more minutes to earn off-duty time.</gray>");
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
        int newTotal = Math.min(current + minutes, maxOffDutyTime);
        boolean capped = (current + minutes) > maxOffDutyTime;

        offDutyMinutes.put(playerId, newTotal);
        dataManager.saveOffDutyMinutes(playerId, newTotal);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            if (capped) {
                Component message = MessageUtils.parseMessage(plugin.getConfig()
                        .getString("messages.time-added-capped",
                                "<green>Added time to your off-duty bank. You've reached the maximum of {max} minutes.</green>")
                        .replace("{max}", String.valueOf(maxOffDutyTime)));

                player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            } else {
                Component message = MessageUtils.parseMessage(plugin.getConfig()
                        .getString("messages.time-added", "<green>Added {minutes} minutes to your off-duty time.</green>")
                        .replace("{minutes}", String.valueOf(minutes)));

                player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            }
        }
    }

    public void setOffDutyMinutes(UUID playerId, int minutes) {
        int capped = Math.min(minutes, maxOffDutyTime);
        offDutyMinutes.put(playerId, capped);
        dataManager.saveOffDutyMinutes(playerId, capped);
    }

    public boolean convertOffDutyMinutes(Player player, int minutes) {
        UUID uuid = player.getUniqueId();
        int current = offDutyMinutes.getOrDefault(uuid, 0);

        // Check if player has enough minutes
        if (current < minutes) {
            Component message = MessageUtils.parseMessage(plugin.getConfig()
                    .getString("messages.not-enough-time", "<red>You don't have enough off-duty time!</red>"));

            player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            return false;
        }

        // Get conversion ratio
        int ratio = plugin.getConfig().getInt("conversion.tokens.ratio", 100);
        int tokens = minutes * ratio;

        // Remove minutes
        offDutyMinutes.put(uuid, current - minutes);
        dataManager.saveOffDutyMinutes(uuid, offDutyMinutes.get(uuid));

        // Send message
        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.converted-time", "<green>Converted {minutes} minutes to {tokens} tokens!</green>")
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{tokens}", String.valueOf(tokens)));

        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        // Execute token conversion command if configured
        String tokenCommand = plugin.getConfig().getString("conversion.tokens.command", "tokenmanager give {player} {amount}");
        if (tokenCommand != null && !tokenCommand.isEmpty()) {
            String command = tokenCommand
                    .replace("{player}", player.getName())
                    .replace("{amount}", String.valueOf(tokens));

            plugin.getLogger().info("Executing token conversion command: " + command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        return true;
    }

    public int getThresholdMinutes() {
        return thresholdMinutes;
    }

    public int getRewardMinutes() {
        return rewardMinutes;
    }

    public int getMaxOffDutyTime() {
        return maxOffDutyTime;
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
                "§e- Being on duty for §f" + thresholdMinutes + " minutes §eearns you §f" + rewardMinutes + " minutes §eof off-duty time\n" +
                "§e- Activities like searches and takedowns will earn additional time\n" +
                "§e- The maximum off-duty time you can accumulate is §f" + maxOffDutyTime + " minutes §e(§f" + (maxOffDutyTime / 60) + " hours§e)\n" +
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
                            savedInventories.put(playerId, new InventoryData(contents, armor));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in inventories.yml: " + key);
                }
            }
        }
    }

    private void savePlayerInventory(Player player) {
        // Save current inventory
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();

        savedInventories.put(player.getUniqueId(), new InventoryData(contents, armor));

        // Save to file
        String path = player.getUniqueId().toString();
        inventoryConfig.set(path + ".contents", serializeInventory(contents));
        inventoryConfig.set(path + ".armor", serializeInventory(armor));

        try {
            inventoryConfig.save(inventoryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save inventory to file", e);
        }

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    private void restorePlayerInventory(Player player) {
        // Get saved inventory
        InventoryData data = savedInventories.remove(player.getUniqueId());
        if (data != null) {
            // Clear current inventory
            player.getInventory().clear();

            // Restore inventory
            player.getInventory().setContents(data.contents);
            player.getInventory().setArmorContents(data.armor);

            // Remove from file
            inventoryConfig.set(player.getUniqueId().toString(), null);
            try {
                inventoryConfig.save(inventoryFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update inventory file", e);
            }
        }
    }

    public void reload() {
        loadConfig();

        // Restart decay task if enabled
        if (decayEnabled) {
            startDecayTask();
        } else if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }

        // Reload inventory config
        inventoryConfig = YamlConfiguration.loadConfiguration(inventoryFile);

        // Reload data from storage
        loadData();
    }

    public void onDisable() {
        // Cancel tasks
        if (decayTask != null) {
            decayTask.cancel();
        }

        if (timeCheckTask != null) {
            timeCheckTask.cancel();
        }

        // Cancel all player timers
        for (BukkitTask task : playerTimers.values()) {
            task.cancel();
        }
        playerTimers.clear();

        // Cancel all immobilization tasks
        for (BukkitTask task : immobilizationTasks.values()) {
            task.cancel();
        }
        immobilizationTasks.clear();

        // Save all data - now this is handled by DataManager
        dataManager.saveDutyStatus(dutyStatus);
        dataManager.saveDutyStartTimes(dutyStartTimes);
        dataManager.saveOffDutyMinutes(offDutyMinutes);

        // Save inventories
        saveInventories();
    }

    private static class InventoryData {
        private final ItemStack[] contents;
        private final ItemStack[] armor;

        public InventoryData(ItemStack[] contents, ItemStack[] armor) {
            this.contents = contents;
            this.armor = armor;
        }
    }
}