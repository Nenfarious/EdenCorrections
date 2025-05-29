package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import java.lang.reflect.InvocationTargetException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.lsdmc.edencorrections.storage.SQLiteStorage;

public class WantedLevelManager {
    private final EdenCorrections plugin;
    private final Map<UUID, Integer> wantedLevels = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> resetTasks = new HashMap<>();
    private final Map<UUID, Long> wantedTimers = new ConcurrentHashMap<>();
    private final Set<UUID> markedPlayers = ConcurrentHashMap.newKeySet(); // Players marked by spyglass
    private final Map<UUID, BukkitTask> glowTasks = new HashMap<>();
    
    // Red glow team management with ProtocolLib
    private Team redGlowTeam;
    private static final String RED_GLOW_TEAM_NAME = "ec_red_glow";
    private ProtocolManager protocolManager;
    
    // Persistence
    private final SQLiteStorage sqliteStorage;

    public WantedLevelManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        
        // Initialize ProtocolLib integration
        initializeProtocolLib();
        
        // Set up red glow team for marked players
        setupRedGlowTeam();
        
        loadWantedData();
    }

    /**
     * Initialize ProtocolLib integration
     */
    private void initializeProtocolLib() {
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                protocolManager = ProtocolLibrary.getProtocolManager();
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("ProtocolLib integration enabled for red glow effect");
                }
            } else {
                plugin.getLogger().warning("ProtocolLib not found - red glow effect will not be available");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize ProtocolLib: " + e.getMessage());
        }
    }

    /**
     * Set up the red glow team for marked players
     */
    private void setupRedGlowTeam() {
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            
            // Remove existing team if it exists
            Team existingTeam = mainScoreboard.getTeam(RED_GLOW_TEAM_NAME);
            if (existingTeam != null) {
                existingTeam.unregister();
            }
            
            // Create new team with red color
            redGlowTeam = mainScoreboard.registerNewTeam(RED_GLOW_TEAM_NAME);
            redGlowTeam.setColor(ChatColor.RED);
            redGlowTeam.setDisplayName("§cMarked Players");
            redGlowTeam.setCanSeeFriendlyInvisibles(false);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Created red glow team for marked players");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set up red glow team: " + e.getMessage());
        }
    }

    private void loadWantedData() {
        wantedLevels.clear();
        wantedTimers.clear();
        markedPlayers.clear();
        wantedLevels.putAll(sqliteStorage.loadWantedLevels(wantedTimers, markedPlayers));
        // Schedule reset tasks for loaded wanted data
        for (UUID playerId : wantedLevels.keySet()) {
            long expiry = wantedTimers.getOrDefault(playerId, 0L);
            if (System.currentTimeMillis() < expiry) {
                long remainingTime = expiry - System.currentTimeMillis();
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    clearWantedData(playerId);
                    resetTasks.remove(playerId);
                }, remainingTime / 50);
                resetTasks.put(playerId, task);
            }
        }
    }

    private void saveWantedData() {
        sqliteStorage.saveWantedLevels(wantedLevels, wantedTimers, markedPlayers);
    }

    /**
     * Get a player's wanted level
     */
    public int getWantedLevel(UUID playerId) {
        return wantedLevels.getOrDefault(playerId, 0);
    }

    /**
     * Set a player's wanted level
     */
    public void setWantedLevel(Player player, int level) {
        UUID playerId = player.getUniqueId();
        
        // Clamp level between 0 and 5
        level = Math.min(5, Math.max(0, level));
        
        // Cancel existing reset task
        if (resetTasks.containsKey(playerId)) {
            resetTasks.get(playerId).cancel();
            resetTasks.remove(playerId);
        }

        if (level <= 0) {
            // Remove wanted level
            clearWantedData(playerId);
            return;
        }

        // Set wanted level
        wantedLevels.put(playerId, level);
        
        // Set wanted timer (3 minutes)
        long expiryTime = System.currentTimeMillis() + (180 * 1000);
        wantedTimers.put(playerId, expiryTime);

        // Schedule reset
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            decreaseWantedLevel(player);
            resetTasks.remove(playerId);
        }, 20L * 180); // 3 minutes
        
        resetTasks.put(playerId, task);

        // Apply level 5 glow effect (only if not marked by spyglass)
        if (level == 5 && !isMarked(playerId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 180, 0, false, false));
        }
        
        // Broadcast wanted level
        broadcastWantedLevel(player, level);
        
        // Save data
        saveWantedData();
    }

    /**
     * Increase a player's wanted level
     */
    public void increaseWantedLevel(Player player, boolean isGuardKill) {
        UUID playerId = player.getUniqueId();
        int currentLevel = getWantedLevel(playerId);
        
        if (isGuardKill) {
            // Killing a guard adds 3 levels if no wanted level, 1 otherwise
            if (currentLevel == 0) {
                setWantedLevel(player, 3);
            } else {
                setWantedLevel(player, Math.min(5, currentLevel + 1));
            }
        } else {
            // Regular crime adds 1 level
            setWantedLevel(player, Math.min(5, currentLevel + 1));
        }
    }

    /**
     * Decrease a player's wanted level
     */
    public void decreaseWantedLevel(Player player) {
        UUID playerId = player.getUniqueId();
        int currentLevel = getWantedLevel(playerId);
        
        if (currentLevel > 0) {
            setWantedLevel(player, currentLevel - 1);
        }
    }

    /**
     * Get jail time in minutes for a wanted level
     */
    public double getJailTime(int wantedLevel) {
        return switch (wantedLevel) {
            case 0 -> 3.0;   // 3 minutes
            case 1 -> 5.0;   // 5 minutes
            case 2 -> 7.5;   // 7.5 minutes
            case 3 -> 10.0;  // 10 minutes
            case 4 -> 12.5;  // 12.5 minutes
            case 5 -> 15.0;  // 15 minutes
            default -> 3.0;  // Default to 3 minutes
        };
    }

    /**
     * Get remaining wanted time in seconds
     */
    public int getRemainingWantedTime(UUID playerId) {
        if (!wantedTimers.containsKey(playerId)) {
            return 0;
        }
        
        long endTime = wantedTimers.get(playerId);
        long remaining = endTime - System.currentTimeMillis();
        
        return Math.max(0, (int)(remaining / 1000));
    }

    /**
     * Mark a player as spotted by spyglass (applies persistent glow)
     */
    public boolean markPlayer(Player target, Player guard) {
        UUID targetId = target.getUniqueId();
        int wantedLevel = getWantedLevel(targetId);
        int minWantedLevel = plugin.getConfig().getInt("items.spyglass.min-wanted-level", 3);
        
        // Check if target has sufficient wanted level
        if (wantedLevel < minWantedLevel) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target does not have sufficient wanted level to mark! (Level " + wantedLevel + "/" + minWantedLevel + ")</red>")));
            return false;
        }
        
        // Check if already marked
        if (markedPlayers.contains(targetId)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Target is already marked!</yellow>")));
            return false;
        }
        
        // Mark the player
        markedPlayers.add(targetId);
        applyGlowEffect(target);
        
        // Notify all guards
        Component message = MessageUtils.parseMessage(
            "<red>🎯 WANTED CRIMINAL MARKED: " + target.getName() + " (★" + wantedLevel + ") - spotted by " + guard.getName() + "!</red>");
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }
        
        // Save data
        saveWantedData();
        
        return true;
    }

    /**
     * Apply persistent glow effect to a marked player
     */
    private void applyGlowEffect(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel existing glow task
        if (glowTasks.containsKey(playerId)) {
            glowTasks.get(playerId).cancel();
        }
        
        // Add player to red glow team for red coloring
        if (redGlowTeam != null) {
            try {
                redGlowTeam.addPlayer(player);
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Added " + player.getName() + " to red glow team");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to add " + player.getName() + " to red glow team: " + e.getMessage());
            }
        }
        
        // Create persistent glow task
        BukkitTask glowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && markedPlayers.contains(playerId)) {
                // Apply glow effect (duration slightly longer than refresh rate)
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 35, 0, false, false, false), true);
                
                // Send red team packets only to guards using ProtocolLib
                sendRedTeamPacketsToGuards(player);
            } else {
                // Player offline or no longer marked - cancel task
                unmarkPlayer(playerId);
            }
        }, 0L, 30L); // Refresh every 1.5 seconds
        
        glowTasks.put(playerId, glowTask);
    }

    /**
     * Send team packets only to guards to make the player appear red
     */
    private void sendRedTeamPacketsToGuards(Player targetPlayer) {
        if (protocolManager == null || redGlowTeam == null) {
            return;
        }
        
        try {
            // Create team packets for adding player to red team
            PacketContainer teamPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.SCOREBOARD_TEAM);
            
            // Set team name
            teamPacket.getStrings().write(0, RED_GLOW_TEAM_NAME);
            
            // Set action to ADD_PLAYERS (3)
            teamPacket.getIntegers().write(0, 3);
            
            // Set players to add
            teamPacket.getSpecificModifier(java.util.Collection.class).write(0, java.util.Arrays.asList(targetPlayer.getName()));
            
            // Send packet only to guards
            for (Player guard : Bukkit.getOnlinePlayers()) {
                if (plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
                    try {
                        protocolManager.sendServerPacket(guard, teamPacket);
                    } catch (Exception e) {
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().warning("Failed to send team packet to guard " + guard.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Sent red team packets for " + targetPlayer.getName() + " to guards");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending red team packets: " + e.getMessage());
        }
    }

    /**
     * Remove mark from a player
     */
    public void unmarkPlayer(UUID playerId) {
        if (!markedPlayers.contains(playerId)) {
            return;
        }
        
        markedPlayers.remove(playerId);
        
        // Cancel glow task
        if (glowTasks.containsKey(playerId)) {
            glowTasks.get(playerId).cancel();
            glowTasks.remove(playerId);
        }
        
        // Remove glow effect and red team membership
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            
            // Remove from red glow team
            if (redGlowTeam != null && redGlowTeam.hasPlayer(player)) {
                try {
                    redGlowTeam.removePlayer(player);
                    // Send removal packets to guards
                    sendRemoveFromRedTeamPackets(player);
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Removed " + player.getName() + " from red glow team");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to remove " + player.getName() + " from red glow team: " + e.getMessage());
                }
            }
        }
        
        // Save data
        saveWantedData();
    }

    /**
     * Send packets to remove player from red team (only to guards)
     */
    private void sendRemoveFromRedTeamPackets(Player targetPlayer) {
        if (protocolManager == null || redGlowTeam == null) {
            return;
        }
        
        try {
            // Create team packets for removing player from red team
            PacketContainer teamPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.SCOREBOARD_TEAM);
            
            // Set team name
            teamPacket.getStrings().write(0, RED_GLOW_TEAM_NAME);
            
            // Set action to REMOVE_PLAYERS (4)
            teamPacket.getIntegers().write(0, 4);
            
            // Set players to remove
            teamPacket.getSpecificModifier(java.util.Collection.class).write(0, java.util.Arrays.asList(targetPlayer.getName()));
            
            // Send packet only to guards
            for (Player guard : Bukkit.getOnlinePlayers()) {
                if (plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
                    try {
                        protocolManager.sendServerPacket(guard, teamPacket);
                    } catch (Exception e) {
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().warning("Failed to send team removal packet to guard " + guard.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Sent red team removal packets for " + targetPlayer.getName() + " to guards");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending red team removal packets: " + e.getMessage());
        }
    }

    /**
     * Check if a player is marked
     */
    public boolean isMarked(UUID playerId) {
        return markedPlayers.contains(playerId);
    }

    /**
     * Handle player death (removes mark)
     */
    public void handlePlayerDeath(Player player) {
        UUID playerId = player.getUniqueId();
        if (isMarked(playerId)) {
            unmarkPlayer(playerId);
            
            // Notify guards
            Component message = MessageUtils.parseMessage(
                "<yellow>🎯 Marked player " + player.getName() + " has died - mark removed.</yellow>");
            
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getDutyManager().isOnDuty(online.getUniqueId())) {
                    online.sendMessage(message);
                }
            }
        }
    }

    /**
     * Handle player jail (removes mark and wanted level)
     */
    public void handlePlayerJail(Player player) {
        UUID playerId = player.getUniqueId();
        if (isMarked(playerId)) {
            unmarkPlayer(playerId);
        }
        clearWantedData(playerId);
        
        // Notify guards
        Component message = MessageUtils.parseMessage(
            "<green>🎯 " + player.getName() + " has been jailed - wanted level and mark cleared.</green>");
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }
    }

    /**
     * Broadcast wanted level change
     */
    private void broadcastWantedLevel(Player player, int level) {
        if (level <= 0) return;
        
        String stars = "★".repeat(level);
        Component message = MessageUtils.parseMessage(
            "<red>WANTED: " + player.getName() + " - " + stars + "</red>");
        Bukkit.broadcast(message);
    }

    /**
     * Get number of wanted players
     */
    public int getWantedPlayerCount() {
        return (int) wantedLevels.values().stream().filter(level -> level > 0).count();
    }

    /**
     * Get number of marked players
     */
    public int getMarkedPlayerCount() {
        return markedPlayers.size();
    }

    /**
     * Clear all wanted data for a player
     */
    public void clearWantedData(UUID playerId) {
        wantedLevels.remove(playerId);
        wantedTimers.remove(playerId);
        unmarkPlayer(playerId);
        
        if (resetTasks.containsKey(playerId)) {
            resetTasks.get(playerId).cancel();
            resetTasks.remove(playerId);
        }
        
        // Remove any glow effects
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
        
        saveWantedData();
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        // Cancel all tasks
        for (BukkitTask task : resetTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : glowTasks.values()) {
            task.cancel();
        }
        
        // Clean up red glow team
        if (redGlowTeam != null) {
            try {
                // Remove all players from the team first
                for (String playerName : redGlowTeam.getEntries()) {
                    redGlowTeam.removeEntry(playerName);
                }
                // Unregister the team
                redGlowTeam.unregister();
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Cleaned up red glow team");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to clean up red glow team: " + e.getMessage());
            }
        }
        
        // Save data
        saveWantedData();
        
        resetTasks.clear();
        glowTasks.clear();
    }

    /**
     * Handle when a new guard comes online - send them team packets for marked players
     */
    public void handleGuardJoin(Player guard) {
        if (protocolManager == null || redGlowTeam == null || !plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            return;
        }
        
        // Send team creation packet first
        sendTeamCreationPacket(guard);
        
        // Send add player packets for all marked players
        for (UUID markedId : markedPlayers) {
            Player markedPlayer = Bukkit.getPlayer(markedId);
            if (markedPlayer != null && markedPlayer.isOnline()) {
                sendSingleRedTeamPacket(guard, markedPlayer);
            }
        }
    }

    /**
     * Send team creation packet to a single guard
     */
    private void sendTeamCreationPacket(Player guard) {
        try {
            PacketContainer teamPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.SCOREBOARD_TEAM);
            
            // Set team name
            teamPacket.getStrings().write(0, RED_GLOW_TEAM_NAME);
            
            // Set action to CREATE_TEAM (0)
            teamPacket.getIntegers().write(0, 0);
            
            // Set team display name
            teamPacket.getChatComponents().write(0, WrappedChatComponent.fromText("§cMarked Players"));
            
            // Set team color to RED
            teamPacket.getModifier().write(3, "red");
            
            protocolManager.sendServerPacket(guard, teamPacket);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Sent team creation packet to guard " + guard.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send team creation packet to guard " + guard.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Send red team packet for a single player to a single guard
     */
    private void sendSingleRedTeamPacket(Player guard, Player targetPlayer) {
        try {
            PacketContainer teamPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.SCOREBOARD_TEAM);
            
            // Set team name
            teamPacket.getStrings().write(0, RED_GLOW_TEAM_NAME);
            
            // Set action to ADD_PLAYERS (3)
            teamPacket.getIntegers().write(0, 3);
            
            // Set players to add
            teamPacket.getSpecificModifier(java.util.Collection.class).write(0, java.util.Arrays.asList(targetPlayer.getName()));
            
            protocolManager.sendServerPacket(guard, teamPacket);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Failed to send team packet for " + targetPlayer.getName() + " to guard " + guard.getName() + ": " + e.getMessage());
            }
        }
    }
} 