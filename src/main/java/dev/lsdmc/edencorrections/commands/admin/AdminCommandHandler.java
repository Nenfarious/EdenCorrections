package dev.lsdmc.edencorrections.commands.admin;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import dev.lsdmc.edencorrections.managers.LocationManager;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

public class AdminCommandHandler {
    private final EdenCorrections plugin;

    public AdminCommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "checkguards" -> handleCheckGuardsCommand(sender);
            case "fixguards" -> handleFixGuardsCommand(sender);
            case "checkdeathcooldown" -> handleCheckDeathCooldownCommand(sender, args);
            case "cleardeathcooldown" -> handleClearDeathCooldownCommand(sender, args);
            case "checkpenalty" -> handleCheckPenaltyCommand(sender, args);
            case "clearpenalty" -> handleClearPenaltyCommand(sender, args);
            case "checkperms" -> handleCheckPermissionsCommand(sender, args);
            case "checkrank" -> handleCheckRankCommand(sender, args);
            case "givehandcuffs" -> handleGiveHandcuffsCommand(sender, args);
            case "giveguarditems" -> handleGiveGuardItemsCommand(sender, args);
            case "givespyglass" -> handleGiveSpyglassCommand(sender, args);
            case "setwanted" -> handleSetWantedCommand(sender, args);
            case "clearwanted" -> handleClearWantedCommand(sender, args);
            case "getwanted" -> handleGetWantedCommand(sender, args);
            case "clearglow" -> handleClearGlowCommand(sender, args);
            case "setguardlounge" -> handleSetLocationCommand(sender, LocationManager.LocationType.GUARD_LOUNGE);
            case "setspawn" -> handleSetLocationCommand(sender, LocationManager.LocationType.SPAWN);
            case "setwardenoffice" -> handleSetLocationCommand(sender, LocationManager.LocationType.WARDEN_OFFICE);
            case "locations" -> handleLocationsCommand(sender);
            case "tpguardlounge" -> handleTeleportCommand(sender, LocationManager.LocationType.GUARD_LOUNGE, args);
            case "removelocation" -> handleRemoveLocationCommand(sender, args);
            case "migratelocations" -> handleMigrateLocationsCommand(sender);
            case "checkitem" -> handleCheckItemCommand(sender, args);
            case "integrationstatus" -> handleIntegrationStatusCommand(sender);
            case "reloadintegration" -> handleReloadIntegrationCommand(sender);
            case "tagcontraband" -> handleTagContraband(sender, args);
            case "removecontrabandtag" -> handleRemoveContrabandTag(sender);
            case "listcontraband" -> handleListContraband(sender, args);
            case "clearcontraband" -> handleClearContraband(sender, args);
            default -> {
                return false;
            }
        }

        return true;
    }

    private void handleCheckGuardsCommand(CommandSender sender) {
        int count = plugin.getGuardBuffManager().getOnlineGuardCount();
        sender.sendMessage(MessageUtils.parseMessage(plugin.getConfig()
                .getString("commands.check-guards.message", "&8[&4&l𝕏&8] &cThere are currently {count} guards online!")
                .replace("{count}", String.valueOf(count))));
    }

    private void handleFixGuardsCommand(CommandSender sender) {
        plugin.getGuardBuffManager().recalculateOnlineGuards();
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&aGuard count recalculated. There are now " +
                        plugin.getGuardBuffManager().getOnlineGuardCount() + " guards online.")));
    }

    private void handleCheckDeathCooldownCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkdeathcooldown <player></red>")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        int cooldown = plugin.getGuardLootManager().getPlayerCooldown(target.getUniqueId());
        sender.sendMessage(MessageUtils.parseMessage(plugin.getConfig()
                .getString("commands.check-death-cooldown.message", "&8[&4&l𝕏&8] &c{player}'s death cooldown is currently at {time}")
                .replace("{player}", target.getName())
                .replace("{time}", String.valueOf(cooldown))));
    }

    private void handleClearDeathCooldownCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections cleardeathcooldown <player></red>")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        plugin.getGuardLootManager().clearPlayerCooldown(target.getUniqueId());
        sender.sendMessage(MessageUtils.parseMessage(plugin.getConfig()
                .getString("commands.clear-death-cooldown.message", "&8[&4&l𝕏&8] &c{player}'s death cooldown has been cleared!")
                .replace("{player}", target.getName())));
    }

    private void handleCheckPenaltyCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkpenalty <player></red>")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        int penalty = plugin.getGuardPenaltyManager().getPlayerLockTime(target.getUniqueId());
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&c" + target.getName() + "'s death penalty is currently at " + penalty + " seconds.")));
    }

    private void handleClearPenaltyCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections clearpenalty <player></red>")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        plugin.getGuardPenaltyManager().clearPlayerLockTime(target.getUniqueId());
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&c" + target.getName() + "'s death penalty has been cleared!")));
    }

    private void handleCheckPermissionsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkperms <player></red>")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gold>Permissions for " + target.getName() + ":</gold>")));

        // Check all rank permissions
        boolean hasAnyRank = false;
        if (plugin.getConfig().contains("duty.rank-kits")) {
            for (String rank : plugin.getConfig().getConfigurationSection("duty.rank-kits").getKeys(false)) {
                String permName = "edencorrections.rank." + rank;
                boolean hasPerm = target.hasPermission(permName);
                if (hasPerm) {
                    hasAnyRank = true;
                }
                sender.sendMessage(MessageUtils.parseMessage("<yellow>" + permName + ": " +
                        (hasPerm ? "<green>YES</green>" : "<red>NO</red>") + "</yellow>"));
            }
        } else {
            sender.sendMessage(MessageUtils.parseMessage("<red>No rank-kits configuration found!</red>"));
        }

        if (!hasAnyRank) {
            sender.sendMessage(MessageUtils.parseMessage("<red>WARNING: Player has no guard rank permissions!</red>"));
        }

        // Check other important permissions
        String[] importantPerms = {
                "edencorrections.duty",
                "edencorrections.duty.check",
                "edencorrections.duty.actions",
                "edencorrections.converttime"
        };

        boolean hasDutyPerm = false;
        for (String perm : importantPerms) {
            boolean hasPerm = target.hasPermission(perm);
            if (perm.equals("edencorrections.duty") && hasPerm) {
                hasDutyPerm = true;
            }
            sender.sendMessage(MessageUtils.parseMessage("<yellow>" + perm + ": " +
                    (hasPerm ? "<green>YES</green>" : "<red>NO</red>") + "</yellow>"));
        }

        if (!hasDutyPerm) {
            sender.sendMessage(MessageUtils.parseMessage("<red>WARNING: Player doesn't have the basic duty permission!</red>"));
        }
    }

    private void handleCheckRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor checkrank <player></red>"));
            return;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return;
        }

        // Get the player's rank using the GuardRankManager
        String rank = plugin.getGuardRankManager().getPlayerRank(targetPlayer);
        
        if (rank != null) {
            Component message = MessageUtils.parseMessage("<green>" + targetPlayer.getName() + "'s guard rank: " + rank + "</green>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        } else {
            Component message = MessageUtils.parseMessage("<red>" + targetPlayer.getName() + " has no guard rank or permissions.</red>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
    }

    private boolean handleGiveHandcuffsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor givehandcuffs <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        boolean success = plugin.getGuardItemManager().giveHandcuffs(targetPlayer);
        if (success) {
            sender.sendMessage(MessageUtils.parseMessage("<green>Gave handcuffs to " + targetPlayer.getName() + "</green>"));
            targetPlayer.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>You received handcuffs from " + sender.getName() + "!</green>")));
        } else {
            sender.sendMessage(MessageUtils.parseMessage("<red>" + targetPlayer.getName() + "'s inventory is full!</red>"));
        }
        return true;
    }

    private boolean handleGiveGuardItemsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor giveguarditems <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        plugin.getGuardItemManager().giveBasicGuardItems(targetPlayer);
        sender.sendMessage(MessageUtils.parseMessage("<green>Given guard items to " + targetPlayer.getName() + " based on their rank.</green>"));
        return true;
    }

    private boolean handleGiveSpyglassCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor givespyglass <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        boolean success = plugin.getGuardItemManager().giveSpyglass(targetPlayer);
        if (success) {
            sender.sendMessage(MessageUtils.parseMessage("<green>Given spyglass to " + targetPlayer.getName() + ".</green>"));
        } else {
            sender.sendMessage(MessageUtils.parseMessage("<red>Could not give spyglass to " + targetPlayer.getName() + " - inventory full.</red>"));
        }
        return true;
    }

    private boolean handleSetWantedCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor setwanted <player> <level></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        try {
            int level = Integer.parseInt(args[2]);
            if (level < 0 || level > 5) {
                sender.sendMessage(MessageUtils.parseMessage("<red>Wanted level must be between 0 and 5.</red>"));
                return true;
            }

            plugin.getWantedLevelManager().setWantedLevel(targetPlayer, level);
            sender.sendMessage(MessageUtils.parseMessage("<green>Set " + targetPlayer.getName() + "'s wanted level to " + level + ".</green>"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Invalid number: " + args[2] + "</red>"));
        }
        return true;
    }

    private boolean handleClearWantedCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor clearwanted <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        plugin.getWantedLevelManager().clearWantedData(targetPlayer.getUniqueId());
        sender.sendMessage(MessageUtils.parseMessage("<green>Cleared " + targetPlayer.getName() + "'s wanted level.</green>"));
        return true;
    }

    private boolean handleGetWantedCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor getwanted <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(targetPlayer.getUniqueId());
        boolean isMarked = plugin.getWantedLevelManager().isMarked(targetPlayer.getUniqueId());
        
        sender.sendMessage(MessageUtils.parseMessage("<green>" + targetPlayer.getName() + "'s wanted level: " + wantedLevel + 
                                                     (isMarked ? " (Marked with glow)" : " (Not marked)") + "</green>"));
        return true;
    }

    private boolean handleClearGlowCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor clearglow <player></red>"));
            return true;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player '" + playerName + "' not found or not online.</red>"));
            return true;
        }

        boolean wasMarked = plugin.getWantedLevelManager().isMarked(targetPlayer.getUniqueId());
        plugin.getWantedLevelManager().unmarkPlayer(targetPlayer.getUniqueId());
        
        if (wasMarked) {
            sender.sendMessage(MessageUtils.parseMessage("<green>Cleared glow mark from " + targetPlayer.getName() + ".</green>"));
        } else {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>" + targetPlayer.getName() + " was not marked.</yellow>"));
        }
        return true;
    }

    /**
     * Handle setting a location
     */
    private boolean handleSetLocationCommand(CommandSender sender, LocationManager.LocationType locationType) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Only players can set locations!</red>")));
            return true;
        }

        if (!sender.hasPermission("edencorrections.admin.locations")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to set locations!</red>")));
            return true;
        }

        boolean success = plugin.getLocationManager().setLocation(locationType, player);
        
        if (success) {
            Component message = MessageUtils.parseMessage(
                "<green>Successfully set " + locationType.getDisplayName() + " to your current location!</green>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            
            // Log the action
            plugin.getLogger().info(player.getName() + " set " + locationType.getDisplayName() + 
                " to " + player.getLocation().getWorld().getName() + " " + 
                (int)player.getLocation().getX() + "," + (int)player.getLocation().getY() + "," + (int)player.getLocation().getZ());
        } else {
            Component message = MessageUtils.parseMessage(
                "<red>Failed to set " + locationType.getDisplayName() + "!</red>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
        
        return true;
    }

    /**
     * Handle listing all locations
     */
    private boolean handleLocationsCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.locations")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to view locations!</red>")));
            return true;
        }

        Component header = MessageUtils.parseMessage("<gold><bold>=== Plugin Locations ===</bold></gold>");
        sender.sendMessage(header);

        for (LocationManager.LocationType type : plugin.getLocationManager().getAllLocationTypes()) {
            String locationInfo = plugin.getLocationManager().getLocationInfo(type);
            sender.sendMessage(MessageUtils.parseMessage(locationInfo));
        }

        // Show statistics
        var stats = plugin.getLocationManager().getStatistics();
        Component footer = MessageUtils.parseMessage(
            "<gray>Total: " + stats.get("totalLocations") + " | " +
            "Set: " + stats.get("setLocations") + " | " +
            "Unset: " + stats.get("unsetLocations") + "</gray>");
        sender.sendMessage(footer);

        return true;
    }

    /**
     * Handle teleporting to a location
     */
    private boolean handleTeleportCommand(CommandSender sender, LocationManager.LocationType locationType, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.teleport")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to teleport to locations!</red>")));
            return true;
        }

        Player targetPlayer;
        if (args.length > 1) {
            // Teleport another player
            if (!sender.hasPermission("edencorrections.admin.teleport.others")) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You don't have permission to teleport other players!</red>")));
                return true;
            }
            
            targetPlayer = plugin.getServer().getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player '" + args[1] + "' not found!</red>")));
                return true;
            }
        } else {
            // Teleport sender
            if (!(sender instanceof Player)) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Console must specify a player to teleport!</red>")));
                return true;
            }
            targetPlayer = (Player) sender;
        }

        boolean success = plugin.getLocationManager().teleportPlayer(targetPlayer, locationType);
        
        if (success) {
            Component message = MessageUtils.parseMessage(
                "<green>Successfully teleported " + targetPlayer.getName() + " to " + locationType.getDisplayName() + "!</green>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            
            if (!targetPlayer.equals(sender)) {
                Component playerMessage = MessageUtils.parseMessage(
                    "<green>You were teleported to " + locationType.getDisplayName() + " by " + sender.getName() + "!</green>");
                targetPlayer.sendMessage(MessageUtils.getPrefix(plugin).append(playerMessage));
            }
        } else {
            Component message = MessageUtils.parseMessage(
                "<red>" + locationType.getDisplayName() + " is not set! Use /cor set" + locationType.getKey().replace("_", "") + " to set it first.</red>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
        
        return true;
    }

    /**
     * Handle removing a location
     */
    private boolean handleRemoveLocationCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.locations")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to remove locations!</red>")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Usage: /cor removelocation <type></red>")));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>Available types: jail, release, guard_lounge, spawn, warden_office</yellow>"));
            return true;
        }

        LocationManager.LocationType locationType = plugin.getLocationManager().getLocationTypeByKey(args[1]);
        if (locationType == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid location type! Available: jail, release, guard_lounge, spawn, warden_office</red>")));
            return true;
        }

        boolean success = plugin.getLocationManager().removeLocation(locationType);
        
        if (success) {
            Component message = MessageUtils.parseMessage(
                "<green>Successfully removed " + locationType.getDisplayName() + "!</green>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
            
            // Log the action
            plugin.getLogger().info(sender.getName() + " removed " + locationType.getDisplayName());
        } else {
            Component message = MessageUtils.parseMessage(
                "<red>Failed to remove " + locationType.getDisplayName() + "!</red>");
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
        }
        
        return true;
    }

    /**
     * Handle migrating old config-based locations to LocationManager
     */
    private boolean handleMigrateLocationsCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.locations")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to migrate locations!</red>")));
            return true;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<yellow>Attempting to migrate old config-based locations...</yellow>")));

        int migrated = 0;
        int failed = 0;

        // Note: We no longer migrate jail and release locations since CMI handles them

        // Migrate guard lounge location
        if (plugin.getConfig().contains("duty.guard-lounge.world")) {
            try {
                String worldName = plugin.getConfig().getString("duty.guard-lounge.world");
                double x = plugin.getConfig().getDouble("duty.guard-lounge.x");
                double y = plugin.getConfig().getDouble("duty.guard-lounge.y");
                double z = plugin.getConfig().getDouble("duty.guard-lounge.z");
                float yaw = (float) plugin.getConfig().getDouble("duty.guard-lounge.yaw", 0);
                float pitch = (float) plugin.getConfig().getDouble("duty.guard-lounge.pitch", 0);

                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    Location loungeLoc = new Location(world, x, y, z, yaw, pitch);
                    if (plugin.getLocationManager().setLocation(LocationManager.LocationType.GUARD_LOUNGE, loungeLoc)) {
                        sender.sendMessage(MessageUtils.parseMessage("  <green>✓ Migrated guard lounge location from config</green>"));
                        migrated++;
                    } else {
                        sender.sendMessage(MessageUtils.parseMessage("  <red>✗ Failed to migrate guard lounge location</red>"));
                        failed++;
                    }
                } else {
                    sender.sendMessage(MessageUtils.parseMessage("  <red>✗ Guard lounge world '" + worldName + "' not found</red>"));
                    failed++;
                }
            } catch (Exception e) {
                sender.sendMessage(MessageUtils.parseMessage("  <red>✗ Error migrating guard lounge location: " + e.getMessage() + "</red>"));
                failed++;
            }
        }

        // Summary
        Component summary = MessageUtils.parseMessage(
            "<gold>Migration complete! " + migrated + " locations migrated, " + failed + " failed.</gold>");
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(summary));

        if (migrated > 0) {
            sender.sendMessage(MessageUtils.parseMessage(
                "<green>You can now safely remove the old location configurations from config.yml</green>"));
            sender.sendMessage(MessageUtils.parseMessage(
                "<yellow>Use '/cor locations' to verify all locations are set correctly.</yellow>"));
        }

        return true;
    }

    /**
     * Handle checking an item for ExecutableItems integration status
     */
    private boolean handleCheckItemCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkitem")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to check items!</red>")));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        Player player = (Player) sender;
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || item.getType().isAir()) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be holding an item to check!</red>")));
            return true;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<yellow>Checking item: " + item.getType().toString() + "</yellow>")));

        // Check ExecutableItems integration
        if (plugin.getExternalPluginIntegration().isExecutableItemsEnabled()) {
            String executableItemId = plugin.getExternalPluginIntegration().getExecutableItemId(item);
            boolean isDrug = plugin.getExternalPluginIntegration().isDrugComprehensive(item);
            boolean isContraband = plugin.getExternalPluginIntegration().isContrabandComprehensive(item);
            
            if (executableItemId != null) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>ExecutableItem ID: " + executableItemId + "</green>")));
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Is Drug: " + isDrug + "</green>")));
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Is Contraband: " + isContraband + "</green>")));
            } else {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>This is not an ExecutableItem</yellow>")));
                
                // Still check comprehensive detection for manual tags and legacy detection
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Manual/Legacy Is Drug: " + isDrug + "</green>")));
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Manual/Legacy Is Contraband: " + isContraband + "</green>")));
            }
        } else {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>ExecutableItems integration is not enabled!</red>")));
        }

        return true;
    }

    /**
     * Handle checking integration status
     */
    private boolean handleIntegrationStatusCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.integrationstatus")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to check integration status!</red>")));
            return true;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<yellow>=== ExecutableItems Integration Status ===</yellow>")));

        boolean executableItemsEnabled = plugin.getExternalPluginIntegration().isExecutableItemsEnabled();
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>ExecutableItems Enabled: " + executableItemsEnabled + "</green>")));

        if (executableItemsEnabled) {
            java.util.Map<String, Object> status = plugin.getExternalPluginIntegration().getIntegrationStatus();
            
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Drug Items Count: " + status.get("drugItemsCount") + "</green>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Contraband Items Count: " + status.get("contrabandItemsCount") + "</green>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Drug Effects Count: " + status.get("drugEffectsCount") + "</green>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Drug Detection Enabled: " + status.get("drugDetectionEnabled") + "</green>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Contraband Detection Enabled: " + status.get("contrabandDetectionEnabled") + "</green>")));

            // Show configured items
            java.util.Set<String> drugIds = plugin.getExternalPluginIntegration().getDrugItemIds();
            java.util.Set<String> contrabandIds = plugin.getExternalPluginIntegration().getContrabandItemIds();
            
            if (!drugIds.isEmpty()) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>Configured Drug Items: " + String.join(", ", drugIds) + "</yellow>")));
            }
            
            if (!contrabandIds.isEmpty()) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>Configured Contraband Items: " + String.join(", ", contrabandIds) + "</yellow>")));
            }
        } else {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>ExecutableItems plugin not found or integration disabled</red>")));
        }

        return true;
    }

    /**
     * Handle reloading the integration
     */
    private boolean handleReloadIntegrationCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.reloadintegration")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to reload integration!</red>")));
            return true;
        }

        plugin.getExternalPluginIntegration().reload();
        
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>ExecutableItems integration reloaded!</green>")));

        return true;
    }

    /**
     * Handle tagging a held item as contraband
     */
    private void handleTagContraband(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Usage: /cor tagcontraband <type></red>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Types: drug, weapon, communication, tool, general</yellow>")));
            return;
        }

        String typeString = args[1].toLowerCase();
        ContrabandManager.ContrabandType type = ContrabandManager.ContrabandType.fromKey(typeString);

        if (type == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid contraband type! Valid types: drug, weapon, communication, tool, general</red>")));
            return;
        }

        plugin.getContrabandManager().tagHeldItem(player, type);
    }

    /**
     * Handle removing contraband tag from held item
     */
    private void handleRemoveContrabandTag(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return;
        }
        
        plugin.getContrabandManager().removeTagFromHeldItem(player);
    }

    /**
     * Handle listing contraband items
     */
    private void handleListContraband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // List all types with counts
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Contraband Registry Summary:</green>")));
            
            int totalCount = 0;
            for (ContrabandManager.ContrabandType type : ContrabandManager.ContrabandType.values()) {
                int count = plugin.getContrabandManager().getContrabandCount(type);
                totalCount += count;
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>" + type.getDescription() + ": " + count + " items</yellow>")));
            }
            
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Total: " + totalCount + " contraband items</green>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gray>Use /cor listcontraband <type> to see specific items</gray>")));
            return;
        }

        String typeString = args[1].toLowerCase();
        ContrabandManager.ContrabandType type = ContrabandManager.ContrabandType.fromKey(typeString);

        if (type == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid contraband type! Valid types: drug, weapon, communication, tool, general</red>")));
            return;
        }

        List<String> items = plugin.getContrabandManager().listContraband(type);
        
        if (items.isEmpty()) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>No " + type.getDescription().toLowerCase() + " items found in registry.</yellow>")));
            return;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>" + type.getDescription() + " (" + items.size() + " items):</green>")));

        for (String item : items) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gray>- " + item + "</gray>")));
        }
    }

    /**
     * Handle clearing contraband of a specific type
     */
    private void handleClearContraband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Usage: /cor clearcontraband <type></red>")));
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Types: drug, weapon, communication, tool, general</yellow>")));
            return;
        }

        String typeString = args[1].toLowerCase();
        ContrabandManager.ContrabandType type = ContrabandManager.ContrabandType.fromKey(typeString);

        if (type == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid contraband type! Valid types: drug, weapon, communication, tool, general</red>")));
            return;
        }

        int clearedCount = plugin.getContrabandManager().clearContrabandType(type);
        
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Cleared " + clearedCount + " " + type.getDescription().toLowerCase() + " items from registry.</green>")));
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subs = Arrays.asList(
                "help", "reload", "setlocation", "teleport", "jail", "unjail",
                "rank", "handcuffs", "guardchat", "items", "spyglass", "wanted",
                "clearwanted", "mark", "clearmark", "debug", "stats", "data"
            );
            return subs.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "setlocation":
                case "teleport":
                    return Arrays.asList("spawn", "jail", "release", "guard-lounge", "armory")
                            .stream()
                            .filter(loc -> loc.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                
                case "jail":
                case "unjail":
                case "rank":
                case "handcuffs":
                case "items":
                case "spyglass":
                case "wanted":
                case "clearwanted":
                case "mark":
                case "clearmark":
                case "stats":
                    // Player names
                    return getOnlinePlayerNames(args[1]);
                
                case "data":
                    return Arrays.asList("save", "load", "backup", "migrate")
                            .stream()
                            .filter(action -> action.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "jail":
                    // Jail time suggestions
                    return Arrays.asList("5", "10", "15", "30", "60", "120")
                            .stream()
                            .filter(time -> time.startsWith(args[2]))
                            .collect(Collectors.toList());
                
                case "wanted":
                    // Wanted level suggestions
                    return Arrays.asList("1", "2", "3", "4", "5")
                            .stream()
                            .filter(level -> level.startsWith(args[2]))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("jail")) {
            // Jail reason suggestions
            return Arrays.asList("misconduct", "insubordination", "contraband", "violence", "escape")
                    .stream()
                    .filter(reason -> reason.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return suggestions;
    }

    private List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
} 