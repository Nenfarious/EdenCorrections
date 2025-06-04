package dev.lsdmc.edencorrections.commands.admin;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.CommandUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import dev.lsdmc.edencorrections.managers.LocationManager;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

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
            case "emergency" -> handleEmergencyCommand(sender, args);
            case "checkguards" -> handleCheckGuardsCommand(sender);
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
            case "testloot" -> handleTestLootCommand(sender, args);
            case "togglelootsystem" -> handleToggleLootSystemCommand(sender);
            case "lootinfo" -> handleLootInfoCommand(sender);
            case "tagcontraband" -> handleTagContraband(sender, args);
            case "removecontrabandtag" -> handleRemoveContrabandTag(sender);
            case "listcontraband" -> handleListContraband(sender, args);
            case "clearcontraband" -> handleClearContraband(sender, args);
            case "setguardrank" -> handleSetGuardRankCommand(sender, args);
            case "listguardranks" -> handleListGuardRanksCommand(sender);
            case "createguardrank" -> handleCreateGuardRankCommand(sender, args);
            case "deleteguardrank" -> handleDeleteGuardRankCommand(sender, args);
            case "setplayerrank" -> handleSetPlayerRankCommand(sender, args);
            case "removeplayerrank" -> handleRemovePlayerRankCommand(sender, args);
            case "listranks" -> handleListRanksCommand(sender);
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

    private void handleCheckDeathCooldownCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandUtils.showUsage(plugin, sender, "checkdeathcooldown");
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
            CommandUtils.showUsage(plugin, sender, "cleardeathcooldown");
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
            CommandUtils.showUsage(plugin, sender, "checkpenalty");
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
            CommandUtils.showUsage(plugin, sender, "clearpenalty");
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
            CommandUtils.showUsage(plugin, sender, "checkperms");
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
            CommandUtils.showUsage(plugin, sender, "checkrank");
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
            CommandUtils.showUsage(plugin, sender, "givehandcuffs");
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
            CommandUtils.showUsage(plugin, sender, "giveguarditems");
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
            CommandUtils.showUsage(plugin, sender, "givespyglass");
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
            CommandUtils.showUsage(plugin, sender, "setwanted");
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
            CommandUtils.showUsage(plugin, sender, "clearwanted");
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
            CommandUtils.showUsage(plugin, sender, "getwanted");
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
            CommandUtils.showUsage(plugin, sender, "clearglow");
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
            CommandUtils.showUsage(plugin, sender, "removelocation");
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
            CommandUtils.showUsage(plugin, sender, "tagcontraband");
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
            CommandUtils.showUsage(plugin, sender, "clearcontraband");
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

    private void handleSetGuardRankCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor setguardrank <group> <rank></red>"));
            return;
        }
        String groupName = args[1];
        String rank = args[2].toLowerCase();
        LuckPerms luckPerms = LuckPermsProvider.get();
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            sender.sendMessage(MessageUtils.parseMessage("<red>LuckPerms group not found: " + groupName + "</red>"));
            return;
        }
        List<String> validRanks = plugin.getGuardRankManager().getRankList();
        if (!validRanks.contains(rank)) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Invalid guard rank: " + rank + "</red>"));
            return;
        }
        List<String> perms = plugin.getGuardRankManager().getPermissionsForRank(rank);
        for (String perm : perms) {
            group.data().add(Node.builder(perm).value(true).build());
        }
        luckPerms.getGroupManager().saveGroup(group);
        // Store mapping in config
        plugin.getConfig().set("guard-rank-groups." + rank, groupName);
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.parseMessage("<green>Assigned LuckPerms group <yellow>" + groupName + "</yellow> to guard rank <yellow>" + rank + "</yellow> and added required permissions.</green>"));
    }

    private void handleListGuardRanksCommand(CommandSender sender) {
        if (!plugin.getConfig().contains("guard-rank-groups")) {
            sender.sendMessage(MessageUtils.parseMessage("<red>No guard rank group mappings found.</red>"));
            return;
        }
        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>Guard Rank Group Mappings:</bold></gold>"));
        for (String rank : plugin.getGuardRankManager().getRankList()) {
            String group = plugin.getConfig().getString("guard-rank-groups." + rank);
            if (group == null) continue;
            List<String> perms = plugin.getGuardRankManager().getPermissionsForRank(rank);
            sender.sendMessage(MessageUtils.parseMessage("<yellow>" + rank + ": <white>" + group + "</white></yellow>"));
            for (String perm : perms) {
                sender.sendMessage(MessageUtils.parseMessage("  <gray>- " + perm + "</gray>"));
            }
        }
    }

    private void handleCreateGuardRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor createguardrank <rank></red>"));
            return;
        }
        String rank = args[1].toLowerCase();
        if (plugin.getGuardRankManager().getRankList().contains(rank)) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Rank already exists: " + rank + "</red>"));
            return;
        }
        // Add to config with empty permissions (admin can edit config or use a future command to add perms)
        plugin.getConfig().set("guard-ranks." + rank + ".permissions", new ArrayList<String>());
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.parseMessage("<green>Created guard rank <yellow>" + rank + "</yellow>. Add permissions via config or future command.</green>"));
    }

    private void handleDeleteGuardRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor deleteguardrank <rank></red>"));
            return;
        }
        String rank = args[1].toLowerCase();
        if (!plugin.getGuardRankManager().getRankList().contains(rank)) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Rank not found: " + rank + "</red>"));
            return;
        }
        plugin.getConfig().set("guard-ranks." + rank, null);
        plugin.getConfig().set("guard-rank-groups." + rank, null);
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.parseMessage("<green>Deleted guard rank <yellow>" + rank + "</yellow> and its mapping.</green>"));
    }

    private void handleSetPlayerRankCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor setplayerrank <player> <rank></red>"));
            return;
        }
        String playerName = args[1];
        String rank = args[2].toLowerCase();
        if (!plugin.getGuardRankManager().getRankList().contains(rank)) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Rank not found: " + rank + "</red>"));
            return;
        }
        String group = plugin.getConfig().getString("guard-rank-groups." + rank);
        if (group == null) {
            sender.sendMessage(MessageUtils.parseMessage("<red>No group mapped to rank: " + rank + "</red>"));
            return;
        }
        var luckPerms = net.luckperms.api.LuckPermsProvider.get();
        var offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        var user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
        if (user == null) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player not found or never joined: " + playerName + "</red>"));
            return;
        }
        // Remove from all other guard rank groups
        for (String r : plugin.getGuardRankManager().getRankList()) {
            String otherGroup = plugin.getConfig().getString("guard-rank-groups." + r);
            if (otherGroup != null && !otherGroup.equalsIgnoreCase(group)) {
                user.data().remove(net.luckperms.api.node.types.InheritanceNode.builder(otherGroup).build());
            }
        }
        // Add to new group
        user.data().add(net.luckperms.api.node.types.InheritanceNode.builder(group).build());
        luckPerms.getUserManager().saveUser(user);
        sender.sendMessage(MessageUtils.parseMessage("<green>Set <yellow>" + playerName + "</yellow> to guard rank <yellow>" + rank + "</yellow> (group <yellow>" + group + "</yellow>).</green>"));
    }

    private void handleRemovePlayerRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Usage: /cor removeplayerrank <player></red>"));
            return;
        }
        String playerName = args[1];
        var luckPerms = net.luckperms.api.LuckPermsProvider.get();
        var offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        var user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
        if (user == null) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Player not found or never joined: " + playerName + "</red>"));
            return;
        }
        for (String r : plugin.getGuardRankManager().getRankList()) {
            String group = plugin.getConfig().getString("guard-rank-groups." + r);
            if (group != null) {
                user.data().remove(net.luckperms.api.node.types.InheritanceNode.builder(group).build());
            }
        }
        luckPerms.getUserManager().saveUser(user);
        sender.sendMessage(MessageUtils.parseMessage("<green>Removed <yellow>" + playerName + "</yellow> from all guard rank groups.</green>"));
    }

    private void handleListRanksCommand(CommandSender sender) {
        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>Guard Ranks:</bold></gold>"));
        for (String rank : plugin.getGuardRankManager().getRankList()) {
            String group = plugin.getConfig().getString("guard-rank-groups." + rank);
            sender.sendMessage(MessageUtils.parseMessage("<yellow>" + rank + ": <white>" + (group != null ? group : "<gray>(no group mapped)</gray>") + "</white></yellow>"));
        }
    }

    /**
     * Handle test loot command
     */
    private boolean handleTestLootCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This command can only be run by players!</red>")));
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Usage: /cor testloot <player> [killer]</red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Player '" + args[1] + "' not found!</red>")));
            return true;
        }

        Player killer = args.length > 2 ? Bukkit.getPlayer(args[2]) : player;

        // Test the loot system
        plugin.getGuardLootManager().testLootComparison(target, killer);
        
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Loot test completed! Check " + target.getName() + "'s chat for results.</green>")));
        
        return true;
    }

    /**
     * Handle toggle loot system command
     */
    private boolean handleToggleLootSystemCommand(CommandSender sender) {
        boolean currentSetting = plugin.getConfig().getBoolean("loot-system.use-modern-system", false);
        boolean newSetting = !currentSetting;
        
        plugin.getConfig().set("loot-system.use-modern-system", newSetting);
        plugin.saveConfig();
        
        String systemName = newSetting ? "Modern" : "Legacy";
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Loot system switched to: <yellow>" + systemName + "</yellow></green>")));
        
        return true;
    }

    /**
     * Handle loot info command
     */
    private boolean handleLootInfoCommand(CommandSender sender) {
        boolean useModern = plugin.getConfig().getBoolean("loot-system.use-modern-system", false);
        boolean lootEnabled = plugin.getGuardLootManager().isLootEnabled();
        
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<gold><bold>Loot System Information:</bold></gold>")));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>Current System: <white>" + (useModern ? "Modern (Context-Aware)" : "Legacy (Config-Based)") + "</white>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>Loot Enabled: <white>" + (lootEnabled ? "Yes" : "No") + "</white>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>Cooldown Time: <white>" + plugin.getGuardLootManager().getRemainingCooldown(java.util.UUID.randomUUID()) + "s</white>"));
        
        if (useModern) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>Quality System: <white>" + 
                plugin.getConfig().getBoolean("loot-system.quality.enabled", true) + "</white>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>Context Bonuses: <white>Enabled</white>"));
        }
        
        sender.sendMessage(MessageUtils.parseMessage("<gray>Use '/cor togglelootsystem' to switch systems"));
        sender.sendMessage(MessageUtils.parseMessage("<gray>Use '/cor testloot <player>' to test loot generation"));
        
        return true;
    }

    /**
     * Handle emergency killswitch commands
     */
    private void handleEmergencyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You don't have permission to use emergency commands!</red>")));
            return;
        }

        if (args.length < 2) {
            boolean currentStatus = EdenCorrections.isEmergencyShutdown();
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gold>Emergency Status: " + (currentStatus ? "<red>ACTIVE" : "<green>INACTIVE") + "</gold>")));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>Usage: /cor emergency <on|off|status></yellow>"));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "on", "activate", "enable" -> {
                EdenCorrections.setEmergencyShutdown(true);
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red><bold>EMERGENCY SHUTDOWN ACTIVATED</bold></red>")));
                sender.sendMessage(MessageUtils.parseMessage("<yellow>All guard systems have been disabled to prevent issues.</yellow>"));
                
                // Broadcast to all online players
                Component broadcast = MessageUtils.parseMessage(
                    "<red><bold>[EMERGENCY]</bold> Guard systems temporarily disabled for maintenance by " + sender.getName() + "</red>");
                plugin.getServer().broadcast(broadcast);
            }
            case "off", "deactivate", "disable" -> {
                EdenCorrections.setEmergencyShutdown(false);
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green><bold>EMERGENCY SHUTDOWN DEACTIVATED</bold></green>")));
                sender.sendMessage(MessageUtils.parseMessage("<yellow>All guard systems have been restored.</yellow>"));
                
                // Broadcast to all online players
                Component broadcast = MessageUtils.parseMessage(
                    "<green><bold>[SYSTEMS RESTORED]</bold> Guard systems are now operational - restored by " + sender.getName() + "</green>");
                plugin.getServer().broadcast(broadcast);
            }
            case "status" -> {
                boolean currentStatus = EdenCorrections.isEmergencyShutdown();
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<gold>Emergency Status: " + (currentStatus ? "<red>ACTIVE" : "<green>INACTIVE") + "</gold>")));
                
                if (currentStatus) {
                    sender.sendMessage(MessageUtils.parseMessage("<red>All guard systems are currently disabled.</red>"));
                    sender.sendMessage(MessageUtils.parseMessage("<yellow>Use '/cor emergency off' to restore systems.</yellow>"));
                } else {
                    sender.sendMessage(MessageUtils.parseMessage("<green>All guard systems are operational.</green>"));
                }
            }
            default -> {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid action! Use: on, off, or status</red>")));
            }
        }
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // All admin commands
            List<String> adminCommands = Arrays.asList(
                "checkguards", "fixguards", "checkdeathcooldown", "cleardeathcooldown",
                "checkpenalty", "clearpenalty", "checkperms", "checkrank", "givehandcuffs",
                "giveguarditems", "givespyglass", "setwanted", "clearwanted", "getwanted",
                "clearglow", "setguardlounge", "setspawn", "setwardenoffice", "locations",
                "tpguardlounge", "tpspawn", "tpwardenoffice", "removelocation", "migratelocations",
                "checkitem", "integrationstatus", "reloadintegration", "tagcontraband",
                "removecontrabandtag", "listcontraband", "clearcontraband", "setguardrank",
                "listguardranks", "createguardrank", "deleteguardrank", "setplayerrank",
                "removeplayerrank", "listranks", "testloot", "togglelootsystem", "lootinfo",
                "emergency"
            );
            
            return adminCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "checkdeathcooldown", "cleardeathcooldown", "checkpenalty", "clearpenalty",
                     "checkperms", "checkrank", "givehandcuffs", "giveguarditems", "givespyglass",
                     "clearwanted", "getwanted", "clearglow", "setplayerrank", "removeplayerrank",
                     "testloot":
                    // Player names
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "setwanted":
                    // Player names for setwanted
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "tpguardlounge", "tpspawn", "tpwardenoffice":
                    // Optional player names for teleport commands
                    List<String> teleportOptions = new ArrayList<>();
                    teleportOptions.add("@self");
                    teleportOptions.addAll(
                        Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList())
                    );
                    return teleportOptions;
                    
                case "removelocation":
                    // Location types
                    completions.addAll(Arrays.asList("guard_lounge", "spawn", "warden_office"));
                    break;
                    
                case "tagcontraband":
                    // Contraband types
                    completions.addAll(getContrabandTypes());
                    break;
                    
                case "listcontraband", "clearcontraband":
                    // Contraband types plus "all"
                    completions.add("all");
                    completions.addAll(getContrabandTypes());
                    break;
                    
                case "setguardrank":
                    // LuckPerms group names for setguardrank <group> <rank>
                    completions.addAll(getLuckPermsGroups());
                    break;
                    
                case "createguardrank", "deleteguardrank":
                    // Guard rank names
                    completions.addAll(getGuardRanks());
                    break;
                    
                case "togglelootsystem":
                    // Toggle options
                    completions.addAll(Arrays.asList("on", "off", "enable", "disable", "true", "false"));
                    break;
                    
                case "emergency":
                    // Emergency options
                    completions.addAll(Arrays.asList("on", "off", "status", "enable", "disable"));
                    break;
                    
                default:
                    break;
            }
        }
        
        if (args.length == 3) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "setwanted":
                    // Wanted level values (0-5)
                    completions.addAll(Arrays.asList("0", "1", "2", "3", "4", "5"));
                    break;
                    
                case "setguardrank":
                    // LuckPerms group names
                    completions.addAll(getLuckPermsGroups());
                    break;
                    
                case "setplayerrank":
                    // Guard rank names for setplayerrank <player> <rank>
                    completions.addAll(getGuardRanks());
                    break;
                    
                case "createguardrank":
                    // LuckPerms group suggestions for createguardrank <rank> <group>
                    completions.addAll(getLuckPermsGroups());
                    break;
                    
                case "testloot":
                    // Guard rank names for loot testing
                    completions.addAll(getGuardRanks());
                    break;
                    
                default:
                    break;
            }
        }
        
        if (args.length == 4) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "setwanted":
                    // Optional reason for wanted level
                    completions.addAll(Arrays.asList("Criminal", "Assault", "Theft", "Escape", "Murder", "Contraband", "Resistance"));
                    break;
                    
                case "testloot":
                    // Killer player name for loot testing
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                default:
                    break;
            }
        }
        
        // Filter completions based on what the user has typed
        if (!completions.isEmpty() && args.length > 0) {
            String lastArg = args[args.length - 1].toLowerCase();
            return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(lastArg))
                .sorted()
                .collect(Collectors.toList());
        }
        
        return completions;
    }

    /**
     * Get available LuckPerms groups for tab completion
     */
    private List<String> getLuckPermsGroups() {
        List<String> groups = new ArrayList<>();
        
        try {
            if (plugin.hasLuckPerms()) {
                var luckPerms = plugin.getLuckPermsApi();
                var groupManager = luckPerms.getGroupManager();
                groups.addAll(groupManager.getLoadedGroups().stream()
                    .map(group -> group.getName())
                    .filter(name -> name.toLowerCase().contains("guard") || 
                                  name.toLowerCase().contains("trainee") ||
                                  name.toLowerCase().contains("private") ||
                                  name.toLowerCase().contains("officer") ||
                                  name.toLowerCase().contains("sergeant") ||
                                  name.toLowerCase().contains("warden"))
                    .sorted()
                    .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            // Fallback to default suggestions
            plugin.getLogger().warning("Could not get LuckPerms groups for tab completion: " + e.getMessage());
        }
        
        // Always include common guard group patterns
        groups.addAll(Arrays.asList("guards_trainee", "guards_private", "guards_officer", 
            "guards_sergeant", "guards_warden"));
        
        return groups.stream().distinct().sorted().collect(Collectors.toList());
    }

    /**
     * Get available guard ranks from configuration
     */
    private List<String> getGuardRanks() {
        return Arrays.asList("trainee", "private", "officer", "sergeant", "warden");
    }

    /**
     * Get available contraband types
     */
    private List<String> getContrabandTypes() {
        return Arrays.asList("drug", "weapon", "communication", "tool", "general");
    }

    /**
     * Get online players plus recently seen players for tab completion
     */
    private List<String> getAllPlayersForCompletion() {
        List<String> players = new ArrayList<>();
        
        // Add online players
        players.addAll(Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList()));
        
        // Could add recently seen players from storage here if needed
        // For now, just online players is sufficient
        
        return players.stream().distinct().sorted().collect(Collectors.toList());
    }
} 