package dev.lsdmc.edencorrections.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Utility class for WorldGuard region operations
 */
public class RegionUtils {
    private static EdenCorrections plugin;

    public RegionUtils() {
        plugin = EdenCorrections.getInstance();
    }

    /**
     * Check if a player is in a specific region
     * @param player The player to check
     * @param regionName The name of the region
     * @return true if the player is in the region, false otherwise
     */
    public static boolean isPlayerInRegion(Player player, String regionName) {
        return isLocationInRegion(player.getLocation(), regionName);
    }

    /**
     * Check if a location is in a specific region
     * @param location The location to check
     * @param regionName The name of the region
     * @return true if the location is in the region, false otherwise
     */
    public static boolean isLocationInRegion(Location location, String regionName) {
        // Get WorldGuard region container
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (container == null) {
            return false;
        }

        // Get region manager for the world
        RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));
        if (regions == null) {
            return false;
        }

        // Get the region with the given name
        ProtectedRegion region = regions.getRegion(regionName);
        if (region == null) {
            return false;
        }

        // Check if the location is within the region
        BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
        return region.contains(vector);
    }

    /**
     * Check if a location is in any of the specified regions
     * @param location The location to check
     * @param regionNames The names of the regions
     * @return true if the location is in any of the regions, false otherwise
     */
    public static boolean isLocationInAnyRegion(Location location, List<String> regionNames) {
        for (String regionName : regionNames) {
            if (isLocationInRegion(location, regionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a location is in a valid duty region
     * @param location The location to check
     * @return true if in a valid duty region, false otherwise
     */
    public boolean isInDutyRegion(Location location) {
        // Get valid duty regions from config
        List<String> validRegions = plugin.getConfig().getStringList("duty.valid-regions");
        if (validRegions.isEmpty()) {
            // If no regions are configured, default to allowing anywhere
            return true;
        }

        // Check if location is in any valid region
        return isLocationInAnyRegion(location, validRegions);
    }

    /**
     * Get all region names at a location
     * @param location The location to check
     * @return A list of region names at the location
     */
    public static List<String> getRegionsAtLocation(Location location) {
        // Get WorldGuard region container
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (container == null) {
            return List.of();
        }

        // Create a query for the container
        RegionQuery query = container.createQuery();

        // Get all regions at the location
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

        // Convert to list of region names
        return regions.getRegions().stream()
                .map(ProtectedRegion::getId)
                .toList();
    }

    /**
     * Check if a region exists
     * @param worldName The name of the world
     * @param regionName The name of the region
     * @return true if the region exists, false otherwise
     */
    public static boolean regionExists(String worldName, String regionName) {
        // Get WorldGuard region container
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (container == null) {
            return false;
        }

        // Get region manager for the world
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return false;
        }

        RegionManager regions = container.get(BukkitAdapter.adapt(world));
        if (regions == null) {
            return false;
        }

        // Check if the region exists
        return regions.hasRegion(regionName);
    }

    /**
     * Get the highest priority region at a location
     * @param location The location to check
     * @return The name of the highest priority region, or null if none
     */
    public static String getHighestPriorityRegion(Location location) {
        // Get WorldGuard region container
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (container == null) {
            return null;
        }

        // Create a query for the container
        RegionQuery query = container.createQuery();

        // Get all regions at the location
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

        // Find highest priority region
        ProtectedRegion highestRegion = null;
        for (ProtectedRegion region : regions.getRegions()) {
            if (highestRegion == null || region.getPriority() > highestRegion.getPriority()) {
                highestRegion = region;
            }
        }

        return highestRegion != null ? highestRegion.getId() : null;
    }
}