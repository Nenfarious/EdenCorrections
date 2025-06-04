package dev.lsdmc.edencorrections.commands.guard;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.CommandUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class GuardCommandHandler {
    private final EdenCorrections plugin;

    public GuardCommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    // Handle the main /g command (chase, endchase, jail, jailoffline)
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }
        if (!player.hasPermission("edencorrections.guard")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You don't have permission to use guard commands!</red>")));
            return true;
        }
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "chase":
            case "pursue": {
                if (args.length < 2) {
                    CommandUtils.showUsage(plugin, player, "chase");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
                    return true;
                }
                
                // Check permissions
                if (!player.hasPermission("edencorrections.guard.chase")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to start chases!</red>")));
                    return true;
                }
                
                // Start the chase
                boolean success = plugin.getChaseManager().startChase(player, target);
                if (success) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<green>Chase started against " + target.getName() + "!</green>")));
                }
                return true;
            }
            case "endchase":
            case "stop": {
                if (args.length < 2) {
                    CommandUtils.showUsage(plugin, player, "endchase");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
                    return true;
                }
                
                // Check permissions
                if (!player.hasPermission("edencorrections.guard.chase")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to end chases!</red>")));
                    return true;
                }
                
                // End the chase
                boolean success = plugin.getChaseManager().forceEndChase(player, target);
                if (success) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<green>Chase against " + target.getName() + " has been ended.</green>")));
                } else {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to end chase. Are you chasing this player?</red>")));
                }
                return true;
            }
            case "jail":
            case "detain": {
                if (args.length < 2) {
                    CommandUtils.showUsage(plugin, player, "jail");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
                    return true;
                }
                
                // Check permissions
                if (!player.hasPermission("edencorrections.guard.jail")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to jail players!</red>")));
                    return true;
                }
                
                // Check if guard is on duty
                if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You must be on duty to jail players!</red>")));
                    return true;
                }
                
                // Check distance (guards must be close to jail someone manually)
                double distance = player.getLocation().distance(target.getLocation());
                double maxJailDistance = plugin.getConfig().getDouble("commands.jail.max-distance", 5.0);
                if (distance > maxJailDistance) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You must be closer to the player to jail them!</red>")));
                    return true;
                }
                
                // Parse jail time if provided, otherwise use wanted level
                double jailTime;
                if (args.length >= 3) {
                    try {
                        jailTime = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Invalid time! Use a number (in minutes).</red>")));
                        return true;
                    }
                } else {
                    // Use wanted level to determine jail time
                    int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
                    jailTime = plugin.getWantedLevelManager().getJailTime(wantedLevel);
                }
                
                // Jail the player
                plugin.getJailManager().jailPlayer(target, jailTime, "Jailed by guard " + player.getName());
                
                // End any active chase
                if (plugin.getChaseManager().isBeingChased(target)) {
                    plugin.getChaseManager().endChase(target, false);
                }
                
                // Award points and time to the guard
                plugin.getGuardProgressionManager().recordArrest(player);
                int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
                plugin.getDutyManager().addOffDutyMinutes(player.getUniqueId(), 1 + wantedLevel);
                
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Successfully jailed " + target.getName() + " for " + jailTime + " minutes!</green>")));
                return true;
            }
            case "jailoffline": {
                if (args.length < 2) {
                    CommandUtils.showUsage(plugin, player, "jailoffline");
                    return true;
                }
                
                // Check permissions
                if (!player.hasPermission("edencorrections.admin.jail")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to jail offline players!</red>")));
                    return true;
                }
                
                String targetName = args[1];
                
                // Try to get UUID from name (this is basic - in production you'd want a proper UUID lookup)
                Player onlineTarget = Bukkit.getPlayer(targetName);
                if (onlineTarget != null) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player " + targetName + " is online! Use /g jail instead.</red>")));
                    return true;
                }
                
                // For now, we'll queue the player for jail when they next join
                try {
                    java.util.UUID targetUUID = java.util.UUID.fromString(targetName);
                    plugin.getJailManager().jailOfflinePlayer(targetUUID, 
                        plugin.getConfig().getDouble("jail.offline-jail-time", 5.0), 
                        "Offline jail by guard " + player.getName(),
                        player);
                    player.sendMessage(MessageUtils.parseMessage("<green>Player " + targetName + " queued for jail when they come online.</green>"));
                    return true;
                } catch (IllegalArgumentException e) {
                    player.sendMessage(MessageUtils.parseMessage("<red>Invalid player UUID: " + targetName + "</red>"));
                    return true;
                }
            }
            default:
                sendHelp(player);
                return true;
        }
    }

    // Handle individual contraband commands
    public boolean handleContrabandCommand(CommandSender sender, String[] args, String contrabandType) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }
        if (!player.hasPermission("edencorrections.guard")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You don't have permission to use guard commands!</red>")));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /" + contrabandType + " <player></red>")));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }
        
        // Send appropriate message based on contraband type
        String guardMessage;
        String targetMessage;
        
        switch (contrabandType.toLowerCase()) {
            case "sword":
            case "s":
                guardMessage = "Requested weapon drop from <yellow>{player}</yellow>.";
                targetMessage = "<red>Guard {guard} has requested you drop your weapons!</red>";
                break;
            case "armor":
            case "a":
                guardMessage = "Requested armor drop from <yellow>{player}</yellow>.";
                targetMessage = "<red>Guard {guard} has requested you remove your armor!</red>";
                break;
            case "bow":
            case "b":
                guardMessage = "Requested bow drop from <yellow>{player}</yellow>.";
                targetMessage = "<red>Guard {guard} has requested you drop your bow!</red>";
                break;
            case "contraband":
            case "c":
                guardMessage = "Requested contraband drop from <yellow>{player}</yellow>.";
                targetMessage = "<red>Guard {guard} has requested you drop any contraband!</red>";
                break;
            default:
                guardMessage = "Requested item drop from <yellow>{player}</yellow>.";
                targetMessage = "<red>Guard {guard} has requested you drop items!</red>";
                break;
        }
        
        player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage(
            guardMessage.replace("{player}", target.getName()))));
        target.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage(
            targetMessage.replace("{guard}", player.getName()))));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        if (args.length == 1) {
            // Main guard commands
            List<String> guardCommands = Arrays.asList(
                "chase", "endchase", "jail", "jailoffline", "help"
            );
            
            return guardCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "chase":
                    // Players without wanted level for chase command
                    return Bukkit.getOnlinePlayers().stream()
                        .filter(player -> plugin.getWantedLevelManager().getWantedLevel(player.getUniqueId()) == 0)
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "endchase":
                    // Players currently being chased
                    return Bukkit.getOnlinePlayers().stream()
                        .filter(player -> plugin.getChaseManager().isBeingChased(player))
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "jail":
                    // Players with wanted levels
                    return getWantedPlayersForCompletion().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                        
                case "jailoffline":
                    // All players (since they might be offline)
                    completions.addAll(getAllPlayersForCompletion().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
                    // Add example UUIDs for offline players
                    if (args[1].isEmpty()) {
                        completions.add("UUID-format-example");
                    }
                    break;
                    
                default:
                    // For other commands, show all players
                    return getAllPlayersForCompletion().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "jail":
                    // Jail time suggestions (in minutes)
                    completions.addAll(Arrays.asList("3", "5", "7.5", "10", "12.5", "15", "20", "30"));
                    break;
                    
                case "jailoffline":
                    // Offline jail time suggestions (in minutes)
                    completions.addAll(Arrays.asList("5", "10", "15", "20", "30"));
                    break;
                    
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
    
    // Tab completion for contraband commands
    public List<String> onTabCompleteContraband(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        if (args.length == 1) {
            // Player names for contraband commands
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        // No additional arguments for contraband commands
        return completions;
    }

    private void sendHelp(Player player) {
        player.sendMessage(MessageUtils.parseMessage("<gold><bold>=== Guard Commands ===</bold></gold>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/g chase <player> <gray>- Start pursuit</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/g endchase <player> <gray>- End pursuit</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/g jail <player> [time] <gray>- Detain player</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/g jailoffline <player> <gray>- Queue offline detention</gray>"));
        player.sendMessage(MessageUtils.parseMessage(""));
        player.sendMessage(MessageUtils.parseMessage("<gold><bold>=== Contraband Commands ===</bold></gold>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/sword <player> <gray>- Request weapon drop</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/armor <player> <gray>- Request armor removal</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/bow <player> <gray>- Request bow drop</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/contraband <player> <gray>- Request contraband drop</gray>"));
        player.sendMessage(MessageUtils.parseMessage("<yellow>/g help <gray>- Show this help menu</gray>"));
    }

    /**
     * Get all players for tab completion
     */
    private List<String> getAllPlayersForCompletion() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Get players with wanted levels for tab completion
     */
    private List<String> getWantedPlayersForCompletion() {
        return Bukkit.getOnlinePlayers().stream()
            .filter(player -> plugin.getWantedLevelManager().getWantedLevel(player.getUniqueId()) > 0)
            .map(Player::getName)
            .sorted()
            .collect(Collectors.toList());
    }
} 