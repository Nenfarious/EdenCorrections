package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages safezone functionality for guard actions
 * Now uses PvP flags instead of specific region names - safezones are regions where PvP is disabled
 */
public class SafezoneManager {
    private final EdenCorrections plugin;
    private String safezoneMode = "require"; // "restrict" or "require"
    private String outsideSafezoneMessage;
    private String protectionMessage;
    
    public SafezoneManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadSafezones();
    }
    
    /**
     * Load safezone configuration
     */
    private void loadSafezones() {
        // Load safezone mode
        safezoneMode = plugin.getConfig().getString("safezones.mode", "require");
        
        // Load messages
        outsideSafezoneMessage = plugin.getConfig().getString("safezones.outside-safezone-message", 
            "<red>You can only use guard items inside designated safezones!</red>");
        protectionMessage = plugin.getConfig().getString("safezones.protection-message", 
            "<red>{target} is protected by a safezone - guard actions are disabled!</red>");
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("SafezoneManager loaded with PvP flag detection");
            plugin.getLogger().info("Safezone mode: " + safezoneMode);
            plugin.getLogger().info("Safezones are now automatically detected as regions with PvP disabled");
        }
    }
    
    /**
     * Check if a player is in a safezone (PvP disabled region)
     */
    public boolean isInSafezone(Player player) {
        return isInSafezone(player.getLocation());
    }
    
    /**
     * Check if a location is in a safezone (PvP disabled region)
     */
    public boolean isInSafezone(Location location) {
        // Safezone = region where PvP is disabled
        return !RegionUtils.isPvPRegion(location);
    }
    
    /**
     * Check if a location is in a PvP zone (PvP enabled region)
     */
    public boolean isInPvPZone(Location location) {
        return RegionUtils.isPvPRegion(location);
    }
    
    /**
     * Check if a player is in a PvP zone
     */
    public boolean isInPvPZone(Player player) {
        return isInPvPZone(player.getLocation());
    }
    
    /**
     * Check if a guard can perform an action on a target based on safezone rules
     * Returns true if the action is allowed, false otherwise
     */
    public boolean canPerformGuardAction(Player guard, Player target) {
        boolean guardInSafezone = isInSafezone(guard);
        boolean targetInSafezone = isInSafezone(target);
        
        switch (safezoneMode.toLowerCase()) {
            case "restrict":
                // Traditional safezone behavior - no actions in safezones
                return !targetInSafezone && !guardInSafezone;
                
            case "require":
                // New behavior - actions only allowed in safezones
                return guardInSafezone && targetInSafezone;
                
            default:
                plugin.getLogger().warning("Unknown safezone mode: " + safezoneMode + ". Defaulting to 'require'.");
                return guardInSafezone && targetInSafezone;
        }
    }
    
    /**
     * Check if a guard can perform an action at their current location
     * (for actions that don't require a target)
     */
    public boolean canPerformGuardAction(Player guard) {
        boolean guardInSafezone = isInSafezone(guard);
        
        switch (safezoneMode.toLowerCase()) {
            case "restrict":
                // Traditional safezone behavior - no actions in safezones
                return !guardInSafezone;
                
            case "require":
                // New behavior - actions only allowed in safezones
                return guardInSafezone;
                
            default:
                plugin.getLogger().warning("Unknown safezone mode: " + safezoneMode + ". Defaulting to 'require'.");
                return guardInSafezone;
        }
    }
    
    /**
     * Check if it's safe to enter a region during a chase (for both guard and target)
     */
    public boolean canEnterRegionDuringChase(Location from, Location to) {
        boolean fromPvP = isInPvPZone(from);
        boolean toPvP = isInPvPZone(to);
        
        // During chase, both guard and target should be able to move between PvP zones
        // but not escape into safezones (unless both are already in safezones)
        if (!fromPvP && !toPvP) {
            // Both locations are safezones - allow movement
            return true;
        } else if (fromPvP && toPvP) {
            // Both locations are PvP zones - allow movement
            return true;
        } else {
            // Moving between PvP and safezone - generally not allowed during chase
            return false;
        }
    }
    
    /**
     * Get the appropriate denial message for the current situation
     */
    public String getDenialMessage(Player guard, Player target) {
        switch (safezoneMode.toLowerCase()) {
            case "restrict":
                return protectionMessage.replace("{target}", target.getName());
                
            case "require":
                return outsideSafezoneMessage;
                
            default:
                return outsideSafezoneMessage;
        }
    }
    
    /**
     * Get the appropriate denial message for actions without a target
     */
    public String getDenialMessage(Player guard) {
        switch (safezoneMode.toLowerCase()) {
            case "restrict":
                return "You cannot use guard items in safezones!";
                
            case "require":
                return outsideSafezoneMessage;
                
            default:
                return outsideSafezoneMessage;
        }
    }
    
    /**
     * Get the type of zone a player is in
     */
    public String getZoneType(Player player) {
        return isInPvPZone(player) ? "PvP Zone" : "Safezone";
    }
    
    /**
     * Get the type of zone at a location
     */
    public String getZoneType(Location location) {
        return isInPvPZone(location) ? "PvP Zone" : "Safezone";
    }
    
    /**
     * Reload safezone configuration
     */
    public void reload() {
        loadSafezones();
        plugin.getLogger().info("SafezoneManager reloaded with PvP flag detection");
    }
    
    /**
     * Get the current safezone mode
     */
    public String getSafezoneMode() {
        return safezoneMode;
    }
    
    /**
     * Set the safezone mode
     */
    public void setSafezoneMode(String mode) {
        if ("restrict".equalsIgnoreCase(mode) || "require".equalsIgnoreCase(mode)) {
            this.safezoneMode = mode.toLowerCase();
            plugin.getConfig().set("safezones.mode", safezoneMode);
            plugin.saveConfig();
            
            plugin.getLogger().info("Safezone mode changed to: " + safezoneMode);
        } else {
            plugin.getLogger().warning("Invalid safezone mode: " + mode + ". Valid modes are 'restrict' and 'require'.");
        }
    }
    
    /**
     * Get safezone statistics
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("mode", safezoneMode);
        stats.put("detectionMethod", "PvP Flag Based");
        stats.put("safezoneDefinition", "Regions with PvP disabled");
        stats.put("pvpZoneDefinition", "Regions with PvP enabled");
        return stats;
    }
} 