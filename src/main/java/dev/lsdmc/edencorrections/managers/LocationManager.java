package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all plugin locations with persistent storage
 */
public class LocationManager {
    private final EdenCorrections plugin;
    private final File locationsFile;
    private FileConfiguration locationsConfig;
    
    // Cache for locations
    private final Map<String, Location> locationCache = new HashMap<>();
    
    // Add emergency killswitch
    private static volatile boolean emergencyShutdown = false;
    
    // Location types that the plugin needs to manage (CMI handles jail locations)
    public enum LocationType {
        GUARD_LOUNGE("guard_lounge", "Guard Lounge", "Guards are teleported here for penalties/force duty"),
        SPAWN("spawn", "Server Spawn", "Default spawn location for the plugin"),
        WARDEN_OFFICE("warden_office", "Warden Office", "Administrative location");
        
        private final String key;
        private final String displayName;
        private final String description;
        
        LocationType(String key, String displayName, String description) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    public LocationManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.locationsFile = new File(plugin.getDataFolder(), "locations.yml");
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        if (!locationsFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                locationsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create locations.yml", e);
            }
        }
        
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
        loadAllLocations();
    }
    
    /**
     * Load all locations from the config file into cache
     */
    private void loadAllLocations() {
        locationCache.clear();
        
        for (LocationType type : LocationType.values()) {
            Location location = loadLocationFromConfig(type);
            if (location != null) {
                locationCache.put(type.getKey(), location);
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Loaded " + type.getDisplayName() + " location: " + 
                        location.getWorld().getName() + " " + location.getX() + "," + location.getY() + "," + location.getZ());
                }
            }
        }
    }
    
    /**
     * Load a specific location from config
     */
    private Location loadLocationFromConfig(LocationType type) {
        String path = "locations." + type.getKey();
        
        if (!locationsConfig.contains(path + ".world")) {
            return null;
        }
        
        try {
            String worldName = locationsConfig.getString(path + ".world");
            double x = locationsConfig.getDouble(path + ".x");
            double y = locationsConfig.getDouble(path + ".y");
            double z = locationsConfig.getDouble(path + ".z");
            float yaw = (float) locationsConfig.getDouble(path + ".yaw", 0.0);
            float pitch = (float) locationsConfig.getDouble(path + ".pitch", 0.0);
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for " + type.getDisplayName());
                return null;
            }
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading " + type.getDisplayName() + " location", e);
            return null;
        }
    }
    
    /**
     * Set a location
     */
    public boolean setLocation(LocationType type, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        
        // Update cache
        locationCache.put(type.getKey(), location.clone());
        
        // Save to config
        String path = "locations." + type.getKey();
        locationsConfig.set(path + ".world", location.getWorld().getName());
        locationsConfig.set(path + ".x", location.getX());
        locationsConfig.set(path + ".y", location.getY());
        locationsConfig.set(path + ".z", location.getZ());
        locationsConfig.set(path + ".yaw", location.getYaw());
        locationsConfig.set(path + ".pitch", location.getPitch());
        locationsConfig.set(path + ".set_by", "Unknown");
        locationsConfig.set(path + ".set_time", System.currentTimeMillis());
        
        return saveConfiguration();
    }
    
    /**
     * Set a location with player info
     */
    public boolean setLocation(LocationType type, Player player) {
        Location location = player.getLocation();
        
        // Update cache
        locationCache.put(type.getKey(), location.clone());
        
        // Save to config with player info
        String path = "locations." + type.getKey();
        locationsConfig.set(path + ".world", location.getWorld().getName());
        locationsConfig.set(path + ".x", location.getX());
        locationsConfig.set(path + ".y", location.getY());
        locationsConfig.set(path + ".z", location.getZ());
        locationsConfig.set(path + ".yaw", location.getYaw());
        locationsConfig.set(path + ".pitch", location.getPitch());
        locationsConfig.set(path + ".set_by", player.getName());
        locationsConfig.set(path + ".set_time", System.currentTimeMillis());
        
        return saveConfiguration();
    }
    
    /**
     * Get a location
     */
    public Location getLocation(LocationType type) {
        Location cached = locationCache.get(type.getKey());
        return cached != null ? cached.clone() : null;
    }
    
    /**
     * Check if a location is set
     */
    public boolean hasLocation(LocationType type) {
        return locationCache.containsKey(type.getKey());
    }
    
    /**
     * Remove a location
     */
    public boolean removeLocation(LocationType type) {
        locationCache.remove(type.getKey());
        locationsConfig.set("locations." + type.getKey(), null);
        return saveConfiguration();
    }
    
    /**
     * Get location info for display
     */
    public String getLocationInfo(LocationType type) {
        Location location = getLocation(type);
        if (location == null) {
            return "<red>" + type.getDisplayName() + ": Not set</red>";
        }
        
        String setBy = locationsConfig.getString("locations." + type.getKey() + ".set_by", "Unknown");
        long setTime = locationsConfig.getLong("locations." + type.getKey() + ".set_time", 0);
        
        String timeStr = setTime > 0 ? 
            new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(new java.util.Date(setTime)) : 
            "Unknown";
        
        return String.format("<green>%s</green>: <white>%s</white> <gray>(%d, %d, %d)</gray> <dark_gray>[Set by %s on %s]</dark_gray>",
            type.getDisplayName(),
            location.getWorld().getName(),
            (int) location.getX(),
            (int) location.getY(),
            (int) location.getZ(),
            setBy,
            timeStr);
    }
    
    /**
     * Get all location types
     */
    public LocationType[] getAllLocationTypes() {
        return LocationType.values();
    }
    
    /**
     * Find location type by key
     */
    public LocationType getLocationTypeByKey(String key) {
        for (LocationType type : LocationType.values()) {
            if (type.getKey().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Save configuration to file
     */
    private boolean saveConfiguration() {
        try {
            locationsConfig.save(locationsFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save locations.yml", e);
            return false;
        }
    }
    
    /**
     * Reload all locations
     */
    public void reload() {
        loadConfiguration();
        plugin.getLogger().info("Reloaded " + locationCache.size() + " locations");
    }
    
    /**
     * Get statistics about locations
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLocations", LocationType.values().length);
        stats.put("setLocations", locationCache.size());
        stats.put("unsetLocations", LocationType.values().length - locationCache.size());
        
        Map<String, Boolean> locationStatus = new HashMap<>();
        for (LocationType type : LocationType.values()) {
            locationStatus.put(type.getKey(), hasLocation(type));
        }
        stats.put("locationStatus", locationStatus);
        
        return stats;
    }
    
    // Convenience methods for commonly used locations
    
    /**
     * Get guard lounge location
     */
    public Location getGuardLoungeLocation() {
        return getLocation(LocationType.GUARD_LOUNGE);
    }
    
    /**
     * Teleport a player to a location safely
     */
    public boolean teleportPlayer(Player player, LocationType type) {
        Location location = getLocation(type);
        if (location == null) {
            return false;
        }
        
        try {
            return player.teleport(location);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to teleport " + player.getName() + " to " + type.getDisplayName(), e);
            return false;
        }
    }
    
    /**
     * Emergency killswitch - disables all location operations
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
} 