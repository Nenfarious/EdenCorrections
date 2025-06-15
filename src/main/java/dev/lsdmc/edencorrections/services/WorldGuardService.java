package dev.lsdmc.edencorrections.services;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardService {
    private final EdenCorrections plugin;

    public WorldGuardService(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean isInRegion(Player player, String regionName) {
        Location location = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

        return regions.getRegions().stream()
                .map(ProtectedRegion::getId)
                .anyMatch(id -> id.equals(regionName));
    }
} 