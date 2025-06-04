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
import java.util.UUID;
import java.util.stream.Collectors;

public class GuardDutyCommandHandler {
    private final EdenCorrections plugin;

    public GuardDutyCommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "duty" -> handleDutyCommand(sender);
            case "status" -> handleStatusCommand(sender, args);
            case "time" -> handleTimeCommand(sender);
            case "addtime" -> handleAddTimeCommand(sender, args);
            case "settime" -> handleSetTimeCommand(sender, args);
            case "convert" -> handleConvertCommand(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                sendHelp(sender);
                return true;
            }
        }
        return true;
    }

    private void handleDutyCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return;
        }
        if (!player.hasPermission("edencorrections.duty")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }
        // Region/NPC check before toggling duty
        if (!plugin.getDutyManager().isPlayerInDutyArea(player)) {
            String msg = plugin.getConfig().getString("messages.not-in-area");
            if (msg == null || msg.isEmpty()) msg = plugin.getConfig().getString("messages.not-in-region");
            if (msg == null || msg.isEmpty()) msg = "<red>You must be in a designated duty area to go on/off duty!</red>";
            player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage(msg)));
            return;
        }
        plugin.getDutyManager().toggleDuty(player);
    }

    private void handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.duty.check")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }

        Player target;

        if (args.length > 1) {
            // Check other player
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Player not found!</red>")));
                return;
            }
        } else if (sender instanceof Player) {
            // Check self
            target = (Player) sender;
        } else {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Please specify a player!</red>")));
            return;
        }

        UUID targetId = target.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(targetId);
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(targetId);

        // Send status message
        Component statusHeader = MessageUtils.parseMessage("<gold><bold>Duty Status for " + target.getName() + "</bold></gold>");
        Component dutyStatus = MessageUtils.parseMessage(isOnDuty ?
                "<green>Currently ON duty</green>" :
                "<yellow>Currently OFF duty</yellow>");
        Component timeRemaining = MessageUtils.parseMessage("<aqua>Off-duty time: " + offDutyMinutes + " minutes</aqua>");

        sender.sendMessage(Component.empty());
        sender.sendMessage(statusHeader);
        sender.sendMessage(dutyStatus);
        sender.sendMessage(timeRemaining);
        sender.sendMessage(Component.empty());

        if (isOnDuty) {
            long startTime = plugin.getDutyManager().getSessionStartTime(targetId);
            long duration = System.currentTimeMillis() - startTime;
            int minutes = (int) (duration / (1000 * 60));
            int threshold = plugin.getDutyManager().getThresholdMinutes();

            Component timeOnDuty = MessageUtils.parseMessage("<yellow>Time on duty: " + minutes + " minutes</yellow>");
            Component thresholdInfo = MessageUtils.parseMessage(minutes >= threshold ?
                    "<green>Threshold reached! Will earn " + plugin.getDutyManager().getRewardMinutes() + " minutes when going off duty.</green>" :
                    "<gray>Need " + (threshold - minutes) + " more minutes to reach threshold.</gray>");

            sender.sendMessage(timeOnDuty);
            sender.sendMessage(thresholdInfo);
            sender.sendMessage(Component.empty());
        }
    }

    private void handleTimeCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return;
        }

        if (!player.hasPermission("edencorrections.duty.check")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }

        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-remaining", "<yellow>You have {minutes} minutes of off-duty time remaining.</yellow>")
                .replace("{minutes}", String.valueOf(offDutyMinutes)));

        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));
    }

    private void handleAddTimeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.settime")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }

        if (args.length < 3) {
            CommandUtils.showUsage(plugin, sender, "addtime");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
            if (minutes <= 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes must be a positive number!</red>")));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return;
        }

        plugin.getDutyManager().addOffDutyMinutes(target.getUniqueId(), minutes);

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-added", "<green>Added {minutes} minutes to {player}'s off-duty time.</green>")
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{player}", target.getName()));

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
    }

    private void handleSetTimeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.settime")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }

        if (args.length < 3) {
            CommandUtils.showUsage(plugin, sender, "settime");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
            if (minutes < 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes cannot be negative!</red>")));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return;
        }

        plugin.getDutyManager().setOffDutyMinutes(target.getUniqueId(), minutes);

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-set", "<green>Set {player}'s off-duty time to {minutes} minutes.</green>")
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{player}", target.getName()));

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));
    }

    private void handleConvertCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return;
        }

        if (!player.hasPermission("edencorrections.converttime")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return;
        }

        if (args.length < 2) {
            CommandUtils.showUsage(plugin, sender, "convert");
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
            if (minutes <= 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes must be a positive number!</red>")));
                return;
            }

            // Check minimum
            int minimum = plugin.getConfig().getInt("conversion.tokens.minimum", 5);
            if (minutes < minimum) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>You must convert at least " + minimum + " minutes!</red>")));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return;
        }

        plugin.getDutyManager().convertOffDutyMinutes(player, minutes);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>=== Duty Commands ===</bold></gold>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor duty <gray>- Toggle guard duty status</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor status [player] <gray>- Check duty status</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor time <gray>- Check your off-duty time balance</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor convert <minutes> <gray>- Convert off-duty time to tokens</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor addtime <player> <minutes> <gray>- Add off-duty time</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor settime <player> <minutes> <gray>- Set off-duty time</gray>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/cor help <gray>- Show this help menu</gray>"));
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Main duty commands
            List<String> dutyCommands = Arrays.asList(
                "duty", "status", "time", "addtime", "settime", "convert", "help"
            );
            
            return dutyCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "status":
                    // Player names for status command
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "addtime", "settime":
                    if (sender.hasPermission("edencorrections.admin.settime")) {
                        // Player names for time commands
                        return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                    }
                    break;
                    
                case "convert":
                    // Minute values for conversion
                    completions.addAll(Arrays.asList("5", "10", "15", "30", "60", "120", "180"));
                    break;
                    
                default:
                    break;
            }
        }
        
        if (args.length == 3) {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "addtime", "settime":
                    if (sender.hasPermission("edencorrections.admin.settime")) {
                        // Minute values for time commands
                        completions.addAll(Arrays.asList("30", "60", "120", "180", "240", "300", "360"));
                    }
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
} 