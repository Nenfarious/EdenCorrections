package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChaseManager {
    private final EdenCorrections plugin;
    private final Map<UUID, UUID> activeChases = new ConcurrentHashMap<>(); // target -> guard
    private final Map<UUID, BossBar> chaseBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> chaseTasks = new ConcurrentHashMap<>();
    private final Set<String> restrictedCommands = new HashSet<>();
    private final Set<String> restrictedRegions = new HashSet<>();
    
    // New restriction sets for enhanced chase restrictions
    private final Set<String> mineRegions = new HashSet<>();
    private final Set<String> cellRegions = new HashSet<>();
    private final Set<String> teleportCommands = new HashSet<>();

    public ChaseManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        restrictedCommands.clear();
        restrictedRegions.clear();
        
        // Load from config or use defaults
        List<String> configCommands = plugin.getConfig().getStringList("chase.restricted-commands");
        if (configCommands.isEmpty()) {
            restrictedCommands.addAll(Arrays.asList(
                "storage", "market", "arena", "shop", "sell", "trade",
                "ah", "auction", "chest", "enderchest", "ec",
                "tpa", "tp", "spawn", "home", "sethome", "warp"
            ));
        } else {
            restrictedCommands.addAll(configCommands);
        }

        List<String> configRegions = plugin.getConfig().getStringList("chase.restricted-regions");
        if (configRegions.isEmpty()) {
            restrictedRegions.addAll(Arrays.asList(
                "donor_lounge", "market", "arena", "shop", "safe"
            ));
        } else {
            restrictedRegions.addAll(configRegions);
        }

        // Load new restriction categories
        loadMineRegions();
        loadCellRegions();
        loadTeleportCommands();
    }

    private void loadRestrictedCommands() {
        // Moved to loadConfig()
    }

    private void loadRestrictedRegions() {
        // Moved to loadConfig()
    }

    private void loadMineRegions() {
        mineRegions.clear();
        List<String> mines = plugin.getConfig().getStringList("chase.restricted-mines");
        if (mines == null) {
            // Default mine region names
            mines = List.of("mine", "mines", "mining", "quarry", "excavation", "a_mine", "b_mine", "c_mine", "d_mine");
        }
        mineRegions.addAll(mines);
        plugin.getLogger().info("Loaded " + mineRegions.size() + " mine regions for chase restrictions");
    }
    
    private void loadCellRegions() {
        cellRegions.clear();
        List<String> cells = plugin.getConfig().getStringList("chase.restricted-cells");
        if (cells == null) {
            // Default cell region patterns
            cells = List.of("cell", "cells", "cellblock", "a_cells", "b_cells", "c_cells", "d_cells", "h_cells", "j_cells");
        }
        cellRegions.addAll(cells);
        plugin.getLogger().info("Loaded " + cellRegions.size() + " cell regions for chase restrictions");
    }
    
    private void loadTeleportCommands() {
        teleportCommands.clear();
        List<String> tpCmds = plugin.getConfig().getStringList("chase.restricted-teleport-commands");
        if (tpCmds == null) {
            // Default teleport commands
            tpCmds = List.of("/tp", "/teleport", "/tpa", "/tphere", "/home", "/spawn", "/warp", 
                           "/back", "/return", "/tpaccept", "/tpyes", "/rtp", "/randomtp");
        }
        teleportCommands.addAll(tpCmds);
        plugin.getLogger().info("Loaded " + teleportCommands.size() + " teleport commands for chase restrictions");
    }

    /**
     * Start a chase with validation
     */
    public boolean startChase(Player guard, Player target) {
        UUID targetId = target.getUniqueId();
        UUID guardId = guard.getUniqueId();

        // Validation checks
        if (!plugin.getDutyManager().isOnDuty(guardId)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to start a chase!</red>")));
            return false;
        }

        if (guard.equals(target)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You cannot chase yourself!</red>")));
            return false;
        }

        // Check if target is already being chased
        if (isBeingChased(target)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This player is already being chased!</red>")));
            return false;
        }

        // Check if guard is already chasing someone
        if (isGuardChasing(guard)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You are already chasing someone!</red>")));
            return false;
        }

        // Check distance (optional - guards can start chase from reasonable distance)
        double distance = guard.getLocation().distance(target.getLocation());
        double maxStartDistance = plugin.getConfig().getDouble("chase.max-start-distance", 20.0);
        if (distance > maxStartDistance) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away to start a chase!</red>")));
            return false;
        }

        // Start chase
        activeChases.put(targetId, guardId);

        // Get configurable chase duration
        int chaseDuration = plugin.getConfig().getInt("chase.duration", 180);

        // Create boss bar for both players
        BossBar bossBar = Bukkit.createBossBar(
            "§c§lChase Time Remaining: §e" + chaseDuration + "s",
            BarColor.RED,
            BarStyle.SOLID
        );
        bossBar.addPlayer(guard);
        bossBar.addPlayer(target);
        chaseBars.put(targetId, bossBar);

        // Schedule chase end
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int timeLeft = chaseDuration;

            @Override
            public void run() {
                timeLeft--;
                if (timeLeft <= 0) {
                    endChase(target, true);
                    return;
                }

                // Check if players are still online
                if (!target.isOnline() || !guard.isOnline()) {
                    endChase(target, true);
                    return;
                }

                // Check if guard caught target (within 3 blocks)
                if (guard.getLocation().distance(target.getLocation()) <= 3.0) {
                    endChase(target, false); // Successful catch
                    return;
                }

                // Update boss bar
                bossBar.setProgress(timeLeft / (double)chaseDuration);
                bossBar.setTitle("§c§lChase Time Remaining: §e" + timeLeft + "s");

                // Alert at certain intervals
                if (timeLeft == 60 || timeLeft == 30 || timeLeft == 10) {
                    Component message = MessageUtils.parseMessage(
                        "<red>Chase ends in " + timeLeft + " seconds!</red>");
                    guard.sendMessage(message);
                    target.sendMessage(message);
                }
            }
        }, 20L, 20L);

        chaseTasks.put(targetId, task);

        // Increase wanted level for being chased
        plugin.getWantedLevelManager().increaseWantedLevel(target, false);

        // Send messages
        Component startMessage = MessageUtils.parseMessage(
            "<red>🚨 CHASE INITIATED! Guard " + guard.getName() + " is pursuing " + target.getName() + "! 🚨</red>");
        Bukkit.broadcast(startMessage);

        // Notify the target
        target.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<red>⚠️ You are being chased by guard " + guard.getName() + "! Run!</red>")));

        return true;
    }

    /**
     * Check if a guard is currently chasing someone
     */
    public boolean isGuardChasing(Player guard) {
        UUID guardId = guard.getUniqueId();
        return activeChases.containsValue(guardId);
    }

    /**
     * Get the target a guard is chasing
     */
    public Player getChaseTarget(Player guard) {
        UUID guardId = guard.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : activeChases.entrySet()) {
            if (entry.getValue().equals(guardId)) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        return null;
    }

    /**
     * End a chase (called by command or automatically)
     */
    public boolean endChase(Player target) {
        return endChase(target, false);
    }

    /**
     * End a chase
     */
    public boolean endChase(Player target, boolean expired) {
        if (target == null) {
            return false;
        }
        
        UUID targetId = target.getUniqueId();
        
        // Check if chase exists
        if (!activeChases.containsKey(targetId)) {
            return false;
        }
        
        Player guard = activeChases.containsKey(targetId) ? 
            Bukkit.getPlayer(activeChases.get(targetId)) : null;

        // Remove chase data
        activeChases.remove(targetId);

        // Remove boss bar
        if (chaseBars.containsKey(targetId)) {
            chaseBars.get(targetId).removeAll();
            chaseBars.remove(targetId);
        }

        // Cancel task
        if (chaseTasks.containsKey(targetId)) {
            chaseTasks.get(targetId).cancel();
            chaseTasks.remove(targetId);
        }

        boolean successful = false;

        // Jail if not expired and both are online and close
        if (!expired && target != null && guard != null && 
            target.isOnline() && guard.isOnline() && 
            target.getLocation().distance(guard.getLocation()) < 5) {
            
            // Get jail time based on wanted level
            int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(targetId);
            double jailTime = plugin.getWantedLevelManager().getJailTime(wantedLevel);
            
            // Jail the player
            plugin.getJailManager().jailPlayer(target, jailTime, 
                "Caught after chase by " + guard.getName());
            
            // Award points for successful chase
            if (guard != null) {
                plugin.getGuardProgressionManager().addPoints(guard,
                    plugin.getGuardProgressionManager().getRewardAmount("chase-complete"),
                    "Successfully completed chase");

                // Award off-duty time (1 minute + wanted level)
                plugin.getDutyManager().addOffDutyMinutes(guard.getUniqueId(), 1 + wantedLevel);
            }
            
            successful = true;
        }

        // Send messages
        if (target != null && guard != null) {
            Component message = MessageUtils.parseMessage(expired ?
                "<red>Chase has expired! " + target.getName() + " has escaped!</red>" :
                successful ? "<green>Chase successful! " + target.getName() + " has been caught!</green>" :
                "<red>Chase has ended!</red>");
            guard.sendMessage(message);
            target.sendMessage(message);
            
            // Broadcast result
            Component broadcast = MessageUtils.parseMessage(expired ?
                "<red>🚨 " + target.getName() + " has escaped from guard " + guard.getName() + "!</red>" :
                successful ? "<green>🚨 " + target.getName() + " was caught by guard " + guard.getName() + "!</green>" :
                "<yellow>🚨 Chase between " + guard.getName() + " and " + target.getName() + " has ended.</yellow>");
            Bukkit.broadcast(broadcast);
        }
        
        return successful;
    }

    /**
     * Check if a player is being chased
     */
    public boolean isBeingChased(Player player) {
        return activeChases.containsKey(player.getUniqueId());
    }

    /**
     * Get the guard chasing a player
     */
    public Player getChasingGuard(Player target) {
        UUID guardId = activeChases.get(target.getUniqueId());
        return guardId != null ? Bukkit.getPlayer(guardId) : null;
    }

    /**
     * Check if a command is restricted during chase
     */
    public boolean isCommandRestricted(String command) {
        String cmdLower = command.toLowerCase();
        return restrictedCommands.stream()
            .anyMatch(restricted -> cmdLower.startsWith(restricted.toLowerCase()));
    }

    /**
     * Check if a region is restricted during chase
     */
    public boolean isRegionRestricted(String region) {
        return restrictedRegions.contains(region.toLowerCase());
    }

    /**
     * Get number of active chases
     */
    public int getActiveChaseCount() {
        return activeChases.size();
    }

    /**
     * Clear all chases
     */
    public void clearAllChases() {
        // End all chases
        for (UUID targetId : new HashSet<>(activeChases.keySet())) {
            endChase(Bukkit.getPlayer(targetId), true);
        }
    }

    /**
     * Reload the manager
     */
    public void reload() {
        clearAllChases();
        loadConfig();
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        clearAllChases();
    }

    /**
     * Force end a chase by guard command
     */
    public boolean forceEndChase(Player guard, Player target) {
        if (!isBeingChased(target)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This player is not being chased!</red>")));
            return false;
        }

        Player chasingGuard = getChasingGuard(target);
        if (chasingGuard != null && !chasingGuard.equals(guard)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Only the guard who started the chase can end it!</red>")));
            return false;
        }

        return endChase(target, true); // End as expired/cancelled
    }

    /**
     * Get chase information for a player
     */
    public String getChaseInfo(Player player) {
        if (isBeingChased(player)) {
            Player guard = getChasingGuard(player);
            String guardName = guard != null ? guard.getName() : "Unknown";
            return "§cBeing chased by: §e" + guardName;
        } else if (isGuardChasing(player)) {
            Player target = getChaseTarget(player);
            String targetName = target != null ? target.getName() : "Unknown";
            return "§bChasing: §e" + targetName;
        } else {
            return "§7Not involved in any chase";
        }
    }

    /**
     * Check if a region is a restricted mine
     */
    public boolean isMineRegion(String region) {
        return mineRegions.stream().anyMatch(mine -> 
            region.toLowerCase().contains(mine.toLowerCase()));
    }
    
    /**
     * Check if a region is a restricted cell area
     */
    public boolean isCellRegion(String region) {
        return cellRegions.stream().anyMatch(cell -> 
            region.toLowerCase().contains(cell.toLowerCase()));
    }
    
    /**
     * Check if a command is a teleportation command
     */
    public boolean isTeleportCommand(String command) {
        String cmd = command.toLowerCase();
        return teleportCommands.stream().anyMatch(tpCmd -> 
            cmd.startsWith(tpCmd.toLowerCase()));
    }
    
    /**
     * Check if a player can access a region during chase
     */
    public boolean canAccessRegion(Player player, String region) {
        if (!isBeingChased(player)) return true;
        
        // Check all restriction types
        if (isRegionRestricted(region)) return false;
        if (isMineRegion(region)) return false;
        if (isCellRegion(region)) return false;
        
        return true;
    }
    
    /**
     * Get restriction message for different types
     */
    public String getRestrictionMessage(Player player, String restrictionType) {
        String playerName = player.getName();
        Player guard = getChasingGuard(player);
        String guardName = guard != null ? guard.getName() : "a guard";
        
        return switch (restrictionType.toLowerCase()) {
            case "mine" -> "§c🚫 You cannot access mines while being chased by " + guardName + "!";
            case "cell" -> "§c🚫 You cannot access your cell while being chased by " + guardName + "!";
            case "teleport" -> "§c🚫 Teleportation is disabled while being chased by " + guardName + "!";
            case "command" -> "§c🚫 This command is restricted while being chased by " + guardName + "!";
            case "enderchest" -> "§c🚫 You cannot access your ender chest while being chased by " + guardName + "!";
            case "region" -> "§c🚫 You cannot enter this area while being chased by " + guardName + "!";
            default -> "§c🚫 This action is restricted while being chased!";
        };
    }
} 