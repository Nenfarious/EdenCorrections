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
import dev.lsdmc.edencorrections.managers.StorageManager;

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
        
        // Safety check for storage manager
        StorageManager storageManager = plugin.getStorageManager();
        if (storageManager instanceof SQLiteStorage) {
            this.sqliteStorage = (SQLiteStorage) storageManager;
            plugin.getLogger().info("WantedLevelManager using SQLiteStorage backend");
        } else {
            this.sqliteStorage = null;
            if (storageManager == null) {
                plugin.getLogger().warning("WantedLevelManager: StorageManager is null! Wanted level system will be disabled.");
            } else {
                plugin.getLogger().warning("WantedLevelManager: StorageManager is " + storageManager.getClass().getSimpleName() + 
                    ", not SQLiteStorage. Advanced wanted level features require SQLiteStorage and will be disabled.");
            }
        }
        
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
        if (sqliteStorage == null) {
            plugin.getLogger().info("WantedLevelManager: SQLiteStorage not available, skipping wanted data loading");
            return;
        }
        
        try {
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
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load wanted data: " + e.getMessage());
        }
    }

    private void saveWantedData() {
        if (sqliteStorage == null) {
            return; // Skip saving if SQLiteStorage not available
        }
        
        try {
            sqliteStorage.saveWantedLevels(wantedLevels, wantedTimers, markedPlayers);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save wanted data: " + e.getMessage());
        }
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
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            plugin.getLogger().warning("Wanted level operation blocked due to emergency shutdown");
            return;
        }

        UUID playerId = player.getUniqueId();
        
        // Remove existing timer
        if (resetTasks.containsKey(playerId)) {
            resetTasks.get(playerId).cancel();
            resetTasks.remove(playerId);
        }

        if (level <= 0) {
            // Clear wanted level
            wantedLevels.remove(playerId);
            wantedTimers.remove(playerId);
            unmarkPlayer(playerId); // Remove any spyglass mark
            broadcastWantedLevel(player, 0);
        } else {
            // Set wanted level
            wantedLevels.put(playerId, Math.min(level, 5)); // Cap at 5
            
            // Set timer for wanted level expiry
            int duration = plugin.getConfig().getInt("wanted-levels.duration", 180);
            long expiryTime = System.currentTimeMillis() + (duration * 1000L);
            wantedTimers.put(playerId, expiryTime);
            
            // Schedule automatic reset
            BukkitTask resetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                clearWantedData(playerId);
                if (player.isOnline()) {
                    broadcastWantedLevel(player, 0);
                }
                resetTasks.remove(playerId);
            }, duration * 20L);
            
            resetTasks.put(playerId, resetTask);
            
            // Apply glowing effect for level 5
            if (level >= 5) {
                String effectName = plugin.getConfig().getString("wanted-levels.effects.5");
                if ("GLOWING".equals(effectName)) {
                    // Apply glowing effect through spyglass marking system
                    markPlayer(player, null);
                }
            }
            
            broadcastWantedLevel(player, level);
        }
        
        saveWantedData();
    }

    /**
     * Increase a player's wanted level
     */
    public void increaseWantedLevel(Player player, boolean isGuardKill) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int currentLevel = getWantedLevel(playerId);
        int newLevel = Math.min(currentLevel + (isGuardKill ? 2 : 1), 5);
        
        setWantedLevel(player, newLevel);
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
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            if (guard != null) {
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            }
            return false;
        }

        UUID targetId = target.getUniqueId();
        
        // Check if already marked
        if (markedPlayers.contains(targetId)) {
            if (guard != null) {
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This player is already marked!</red>")));
            }
            return false;
        }

        // Add to marked players
        markedPlayers.add(targetId);
        
        // Apply glow effect
        applyGlowEffect(target);
        
        if (guard != null) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Successfully marked " + target.getName() + " for all guards!</green>")));
        }
        
        // Broadcast to guards
        Component message = MessageUtils.parseMessage(
            "<red>[SPYGLASS] " + target.getName() + " has been marked and is glowing red for all guards!</red>");
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (plugin.getGuardBuffManager().isPlayerGuard(onlinePlayer)) {
                onlinePlayer.sendMessage(message);
            }
        }
        
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
        
        // Create persistent glow task that only applies glow to guards
        BukkitTask glowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && markedPlayers.contains(playerId)) {
                // Apply glow effect only for guards, not for the marked player themselves
                for (Player guard : Bukkit.getOnlinePlayers()) {
                    if (plugin.getDutyManager().isOnDuty(guard.getUniqueId()) && !guard.equals(player)) {
                        // Send glow effect packet only to guards
                        sendGlowEffectToGuard(guard, player, true);
                    }
                }
                
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
     * Send glow effect to a specific guard for a marked player
     */
    private void sendGlowEffectToGuard(Player guard, Player markedPlayer, boolean glow) {
        try {
            if (protocolManager == null) {
                // Fallback: use potion effect visible only to guard
                // This is a workaround when ProtocolLib isn't available
                return;
            }

            // Use ProtocolLib to send entity metadata packet for glowing effect
            PacketContainer packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, markedPlayer.getEntityId());
            
            // Create metadata for glowing effect
            com.comphenix.protocol.wrappers.WrappedDataWatcher watcher = new com.comphenix.protocol.wrappers.WrappedDataWatcher();
            com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject glowingObject = 
                new com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject(0, com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry.get(Byte.class));
            
            byte flags = glow ? (byte) 0x40 : (byte) 0x00; // 0x40 is the glowing flag
            watcher.setObject(glowingObject, flags);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            
            protocolManager.sendServerPacket(guard, packet);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Sent glow effect packet for " + markedPlayer.getName() + " to guard " + guard.getName());
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Failed to send glow effect to guard " + guard.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Send team creation packet to a single guard
     */
    private void sendTeamCreationPacket(Player guard) {
        // CRITICAL FIX: Disable team packet creation entirely to prevent disconnections
        // The errors "Field index 0 is out of bounds for length 0" and ClassCastException
        // are causing players to disconnect. We'll use alternative methods for glow effects.
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Team packet creation disabled for compatibility - using alternative glow method for guard " + guard.getName());
        }
        
        // Instead of using problematic team packets, we'll rely on the entity metadata method
        // This is safer and doesn't cause the packet encoding errors
        return;
    }

    /**
     * Send red team packet for a single player to a single guard
     */
    private void sendSingleRedTeamPacket(Player guard, Player targetPlayer) {
        // CRITICAL FIX: Disable team packets entirely to prevent disconnections
        // Use alternative glow method instead
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Red team packet disabled for compatibility - using entity glow for " + targetPlayer.getName() + " to guard " + guard.getName());
        }
        
        // Use the safer entity metadata glow method instead
        sendGlowEffectToGuard(guard, targetPlayer, true);
    }

    /**
     * Send red team packets only to guards to make the player appear red
     */
    private void sendRedTeamPacketsToGuards(Player targetPlayer) {
        // CRITICAL FIX: Disable problematic team packet system entirely
        // This was causing the "SQLITE_READONLY_DBMOVED" style errors but for packets
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Red team packets disabled for compatibility - using direct glow effects for " + targetPlayer.getName());
        }
        
        // Use safer individual glow effects instead of team packets
        for (Player guard : Bukkit.getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(guard.getUniqueId()) && !guard.equals(targetPlayer)) {
                sendGlowEffectToGuard(guard, targetPlayer, true);
            }
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
            // Remove glow effect from all guards
            for (Player guard : Bukkit.getOnlinePlayers()) {
                if (plugin.getDutyManager().isOnDuty(guard.getUniqueId()) && !guard.equals(player)) {
                    sendGlowEffectToGuard(guard, player, false);
                }
            }
            
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
        // CRITICAL FIX: Disable team removal packets
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Red team removal packets disabled for compatibility - removing glow effects for " + targetPlayer.getName());
        }
        
        // Use safer individual glow removal instead of team packets
        for (Player guard : Bukkit.getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(guard.getUniqueId()) && !guard.equals(targetPlayer)) {
                sendGlowEffectToGuard(guard, targetPlayer, false);
            }
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
        if (protocolManager == null || redGlowTeam == null) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Skipping team setup for guard " + guard.getName() + " - ProtocolLib or team not available");
            }
            return;
        }
        
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Skipping team setup for " + guard.getName() + " - not on duty");
            }
            return;
        }
        
        // Send team creation packet first (with error handling)
        try {
            sendTeamCreationPacket(guard);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Could not send team creation packet for guard " + guard.getName() + ": " + e.getMessage());
            }
            // Continue with the rest of the process
        }
        
        // Send add player packets for all marked players and apply glow effects
        for (UUID markedId : markedPlayers) {
            Player markedPlayer = Bukkit.getPlayer(markedId);
            if (markedPlayer != null && markedPlayer.isOnline() && !markedPlayer.equals(guard)) {
                try {
                    sendSingleRedTeamPacket(guard, markedPlayer);
                } catch (Exception e) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Could not send team packet for " + markedPlayer.getName() + " to guard " + guard.getName() + ": " + e.getMessage());
                    }
                    // Continue with next player
                }
                
                try {
                    sendGlowEffectToGuard(guard, markedPlayer, true);
                } catch (Exception e) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Could not send glow effect for " + markedPlayer.getName() + " to guard " + guard.getName() + ": " + e.getMessage());
                    }
                    // Continue with next player
                }
            }
        }
    }

    /**
     * Reload wanted level configurations and clear active tasks
     */
    public void reload() {
        // Cancel all active reset tasks
        for (BukkitTask task : resetTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        resetTasks.clear();

        // Cancel all glow tasks
        for (BukkitTask task : glowTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        glowTasks.clear();

        // Save current data before clearing
        saveWantedData();

        // Reinitialize ProtocolLib if needed
        initializeProtocolLib();
        setupRedGlowTeam();

        plugin.getLogger().info("WantedLevelManager reloaded - cleared all active tasks");
    }

    /**
     * Check if a player has wanted level restrictions (3+ stars)
     */
    public boolean hasWantedRestrictions(UUID playerId) {
        return getWantedLevel(playerId) >= 3;
    }
    
    /**
     * Check if a player with wanted level can access a region
     */
    public boolean canAccessRegion(Player player, String region) {
        if (!hasWantedRestrictions(player.getUniqueId())) return true;
        
        // Use ChaseManager's region checking logic for consistency
        ChaseManager chaseManager = plugin.getChaseManager();
        if (chaseManager.isMineRegion(region)) return false;
        if (chaseManager.isCellRegion(region)) return false;
        if (chaseManager.isRegionRestricted(region)) return false;
        
        return true;
    }
    
    /**
     * Check if a command is restricted for wanted players
     */
    public boolean isCommandRestricted(Player player, String command) {
        if (!hasWantedRestrictions(player.getUniqueId())) return false;
        
        ChaseManager chaseManager = plugin.getChaseManager();
        return chaseManager.isCommandRestricted(command) || 
               chaseManager.isTeleportCommand(command);
    }
    
    /**
     * Get restriction message for wanted players
     */
    public String getRestrictionMessage(Player player, String restrictionType) {
        int wantedLevel = getWantedLevel(player.getUniqueId());
        String stars = "★".repeat(wantedLevel);
        
        return switch (restrictionType.toLowerCase()) {
            case "mine" -> "§c🚫 Wanted criminals [" + stars + "] cannot access mines!";
            case "cell" -> "§c🚫 Wanted criminals [" + stars + "] cannot access their cells!";
            case "teleport" -> "§c🚫 Wanted criminals [" + stars + "] cannot teleport!";
            case "command" -> "§c🚫 This command is restricted for wanted criminals [" + stars + "]!";
            case "enderchest" -> "§c🚫 Wanted criminals [" + stars + "] cannot access ender chests!";
            case "region" -> "§c🚫 Wanted criminals [" + stars + "] cannot enter this area!";
            default -> "§c🚫 This action is restricted for wanted criminals!";
        };
    }
    
    /**
     * Check if a player should be restricted (either wanted or being chased)
     */
    public boolean shouldRestrict(Player player) {
        return hasWantedRestrictions(player.getUniqueId()) || 
               plugin.getChaseManager().isBeingChased(player);
    }

    /**
     * Handle when a guard goes off duty - remove glow effects for them
     */
    public void handleGuardLeave(Player guard) {
        // Remove glow effects from this guard for all marked players
        for (UUID markedId : markedPlayers) {
            Player markedPlayer = Bukkit.getPlayer(markedId);
            if (markedPlayer != null && markedPlayer.isOnline() && !markedPlayer.equals(guard)) {
                sendGlowEffectToGuard(guard, markedPlayer, false);
            }
        }
    }
} 