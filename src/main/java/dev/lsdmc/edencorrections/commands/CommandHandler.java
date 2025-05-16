package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;
    private final GuardSystemCommandHandler guardSystemCommandHandler;

    public CommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
        this.guardSystemCommandHandler = new GuardSystemCommandHandler(
                plugin,
                plugin.getGuardBuffManager(),
                plugin.getGuardLootManager(),
                plugin.getGuardPenaltyManager()
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "duty":
                return handleDutyCommand(sender);

            case "status":
                return handleStatusCommand(sender, args);

            case "time":
                return handleTimeCommand(sender, args);

            case "addtime":
                return handleAddTimeCommand(sender, args);

            case "settime":
                return handleSetTimeCommand(sender, args);

            case "convert":
                return handleConvertCommand(sender, args);

            case "reload":
                return handleReloadCommand(sender);

            case "explain":
                return handleExplainCommand(sender);

            case "stats":
                return handleStatsCommand(sender);

            case "search":
                return handleSearchCommand(sender);

            case "found":
                return handleFoundCommand(sender);

            case "detect":
                return handleDetectCommand(sender);

            case "gui":
                return handleGuiCommand(sender);

            // Guard system commands
            case "checkguards":
                return guardSystemCommandHandler.handleCheckGuardsCommand(sender, args);

            case "fixguards":
                return guardSystemCommandHandler.handleFixGuardsCommand(sender, args);

            case "checkdeathcooldown":
                return guardSystemCommandHandler.handleCheckDeathCooldownCommand(sender, args);

            case "cleardeathcooldown":
                return guardSystemCommandHandler.handleClearDeathCooldownCommand(sender, args);

            case "checkpenalty":
                return guardSystemCommandHandler.handleCheckPenaltyCommand(sender, args);

            case "clearpenalty":
                return guardSystemCommandHandler.handleClearPenaltyCommand(sender, args);

            case "checkperms":
                return handleCheckPermissionsCommand(sender, args);

            case "checkrank":
                return handleCheckRankCommand(sender, args);

            case "help":
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private boolean handleDutyCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        plugin.getDutyManager().toggleDuty(player);
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.duty.check")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        Player target;

        if (args.length > 1) {
            // Check other player
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Player not found!</red>")));
                return true;
            }
        } else if (sender instanceof Player) {
            // Check self
            target = (Player) sender;
        } else {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Please specify a player!</red>")));
            return true;
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

        return true;
    }

    private boolean handleTimeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty.check")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-remaining", "<yellow>You have {minutes} minutes of off-duty time remaining.</yellow>")
                .replace("{minutes}", String.valueOf(offDutyMinutes)));

        player.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        return true;
    }

    private boolean handleAddTimeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.settime")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections addtime <player> <minutes></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
            if (minutes <= 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes must be a positive number!</red>")));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return true;
        }

        plugin.getDutyManager().addOffDutyMinutes(target.getUniqueId(), minutes);

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-added", "<green>Added {minutes} minutes to {player}'s off-duty time.</green>")
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{player}", target.getName()));

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        return true;
    }

    private boolean handleSetTimeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.settime")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections settime <player> <minutes></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
            if (minutes < 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes cannot be negative!</red>")));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return true;
        }

        plugin.getDutyManager().setOffDutyMinutes(target.getUniqueId(), minutes);

        Component message = MessageUtils.parseMessage(plugin.getConfig()
                .getString("messages.time-set", "<green>Set {player}'s off-duty time to {minutes} minutes.</green>")
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{player}", target.getName()));

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(message));

        return true;
    }

    private boolean handleConvertCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.converttime")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections convert <minutes></red>")));
            return true;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
            if (minutes <= 0) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Minutes must be a positive number!</red>")));
                return true;
            }

            // Check minimum
            int minimum = plugin.getConfig().getInt("conversion.tokens.minimum", 5);
            if (minutes < minimum) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>You must convert at least " + minimum + " minutes!</red>")));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid number format!</red>")));
            return true;
        }

        plugin.getDutyManager().convertOffDutyMinutes(player, minutes);

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.reload")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        // Send a message before reload
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Reloading EdenCorrections plugin...</yellow>")));

        // Perform the reload
        plugin.reload();

        // Send a message after reload
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Plugin reloaded successfully!</green>")));

        return true;
    }

    private boolean handleExplainCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        plugin.getDutyManager().explainSystemToPlayer(player);
        return true;
    }

    private boolean handleStatsCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty.check")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        String summary = plugin.getDutyManager().getActivitySummary(player);
        player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage(summary)));
        return true;
    }

    private boolean handleSearchCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty.actions")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You must be on duty to perform this action!</red>")));
            return true;
        }

        // Record the search
        plugin.getDutyManager().recordSearch(player);

        return true;
    }

    private boolean handleFoundCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty.actions")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You must be on duty to perform this action!</red>")));
            return true;
        }

        // Record the successful search
        plugin.getDutyManager().recordSuccessfulSearch(player);

        return true;
    }

    private boolean handleDetectCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty.actions")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You must be on duty to perform this action!</red>")));
            return true;
        }

        // Record the metal detection
        plugin.getDutyManager().recordMetalDetect(player);

        return true;
    }

    private boolean handleGuiCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.duty")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        plugin.getGuiManager().openDutySelectionGui(player);
        return true;
    }

    private boolean handleCheckPermissionsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkperms")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkperms <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
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

        return true;
    }

    private boolean handleCheckRankCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkrank")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkrank <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        // Find the player's guard rank
        String highestRank = null;
        int highestPriority = -1;

        // Check each possible rank from config
        if (plugin.getConfig().contains("duty.rank-kits")) {
            int priority = 0;
            for (String rank : plugin.getConfig().getConfigurationSection("duty.rank-kits").getKeys(false)) {
                // Check for the rank permission
                if (target.hasPermission("edencorrections.rank." + rank)) {
                    // If this rank has higher priority (checked later in config), use it
                    if (priority > highestPriority) {
                        highestRank = rank;
                        highestPriority = priority;
                    }
                }
                priority++;
            }
        }

        if (highestRank == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player has no guard rank!</red>")));
        } else {
            // Get the kit that would be given
            String kitName = plugin.getKitForRank(highestRank);

            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Player's highest guard rank: <gold>" + highestRank + "</gold></green>")));
            sender.sendMessage(MessageUtils.parseMessage("<green>Kit that would be given: <gold>" + kitName + "</gold></green>"));
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        Component header = MessageUtils.parseMessage("<gold><bold>EdenCorrections Commands</bold></gold>");

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);

        // Only show commands the sender has permission to use
        if (sender.hasPermission("edencorrections.duty")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections duty</yellow> <gray>- Toggle guard duty</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections gui</yellow> <gray>- Open duty selection GUI</gray>"));
        }

        if (sender.hasPermission("edencorrections.duty.check")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections status [player]</yellow> <gray>- Check duty status</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections time</yellow> <gray>- Check your off-duty time</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections stats</yellow> <gray>- Check your current duty session stats</gray>"));
        }

        if (sender.hasPermission("edencorrections.duty.actions")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections search</yellow> <gray>- Record a prisoner search</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections found</yellow> <gray>- Record a successful contraband search</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections detect</yellow> <gray>- Record a successful metal detection</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.settime")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections addtime <player> <minutes></yellow> <gray>- Add off-duty time</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections settime <player> <minutes></yellow> <gray>- Set off-duty time</gray>"));
        }

        if (sender.hasPermission("edencorrections.converttime")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections convert <minutes></yellow> <gray>- Convert time to tokens</gray>"));
        }

        // Guard system commands
        if (sender.hasPermission("edencorrections.admin.checkguards")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections checkguards</yellow> <gray>- Check how many guards are online</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.fixguards")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections fixguards</yellow> <gray>- Recalculate the number of online guards</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.checkdeathcooldown")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections checkdeathcooldown <player></yellow> <gray>- Check a player's death cooldown</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.cleardeathcooldown")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections cleardeathcooldown <player></yellow> <gray>- Clear a player's death cooldown</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.checkpenalty")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections checkpenalty <player></yellow> <gray>- Check a player's death penalty</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.clearpenalty")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections clearpenalty <player></yellow> <gray>- Clear a player's death penalty</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.checkperms")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections checkperms <player></yellow> <gray>- Check a player's guard permissions</gray>"));
        }

        if (sender.hasPermission("edencorrections.admin.checkrank")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections checkrank <player></yellow> <gray>- Check a player's guard rank</gray>"));
        }

        sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections explain</yellow> <gray>- Explain the duty system</gray>"));

        if (sender.hasPermission("edencorrections.admin.reload")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections reload</yellow> <gray>- Reload the plugin</gray>"));
        }

        sender.sendMessage(MessageUtils.parseMessage("<yellow>/edencorrections help</yellow> <gray>- Show this help message</gray>"));
        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommand
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission("edencorrections.duty")) {
                subCommands.add("duty");
                subCommands.add("gui");
            }

            if (sender.hasPermission("edencorrections.duty.check")) {
                subCommands.add("status");
                subCommands.add("time");
                subCommands.add("stats");
            }

            if (sender.hasPermission("edencorrections.duty.actions")) {
                subCommands.add("search");
                subCommands.add("found");
                subCommands.add("detect");
            }

            if (sender.hasPermission("edencorrections.admin.settime")) {
                subCommands.add("addtime");
                subCommands.add("settime");
            }

            if (sender.hasPermission("edencorrections.converttime")) {
                subCommands.add("convert");
            }

            // Guard system commands
            if (sender.hasPermission("edencorrections.admin.checkguards")) {
                subCommands.add("checkguards");
            }

            if (sender.hasPermission("edencorrections.admin.fixguards")) {
                subCommands.add("fixguards");
            }

            if (sender.hasPermission("edencorrections.admin.checkdeathcooldown")) {
                subCommands.add("checkdeathcooldown");
            }

            if (sender.hasPermission("edencorrections.admin.cleardeathcooldown")) {
                subCommands.add("cleardeathcooldown");
            }

            if (sender.hasPermission("edencorrections.admin.checkpenalty")) {
                subCommands.add("checkpenalty");
            }

            if (sender.hasPermission("edencorrections.admin.clearpenalty")) {
                subCommands.add("clearpenalty");
            }

            if (sender.hasPermission("edencorrections.admin.checkperms")) {
                subCommands.add("checkperms");
            }

            if (sender.hasPermission("edencorrections.admin.checkrank")) {
                subCommands.add("checkrank");
            }

            if (sender.hasPermission("edencorrections.admin.reload")) {
                subCommands.add("reload");
            }

            // Anyone can use the explain command
            subCommands.add("explain");
            subCommands.add("help");

            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Second argument
            String subCommand = args[0].toLowerCase();

            if ((subCommand.equals("status") && sender.hasPermission("edencorrections.duty.check")) ||
                    ((subCommand.equals("addtime") || subCommand.equals("settime")) &&
                            sender.hasPermission("edencorrections.admin.settime")) ||
                    ((subCommand.equals("checkdeathcooldown") || subCommand.equals("cleardeathcooldown")) &&
                            sender.hasPermission("edencorrections.admin.checkdeathcooldown")) ||
                    ((subCommand.equals("checkpenalty") || subCommand.equals("clearpenalty")) &&
                            sender.hasPermission("edencorrections.admin.checkpenalty")) ||
                    (subCommand.equals("checkperms") &&
                            sender.hasPermission("edencorrections.admin.checkperms")) ||
                    (subCommand.equals("checkrank") &&
                            sender.hasPermission("edencorrections.admin.checkrank"))) {
                // Player name
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("convert") && sender.hasPermission("edencorrections.converttime")) {
                // Suggest some time values
                return Arrays.asList("5", "10", "15", "30", "60", "120", "240", "480")
                        .stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            // Third argument
            String subCommand = args[0].toLowerCase();

            if ((subCommand.equals("addtime") || subCommand.equals("settime")) &&
                    sender.hasPermission("edencorrections.admin.settime")) {
                // Suggest some time values
                return Arrays.asList("5", "10", "15", "30", "60", "120", "240", "480", "1440", "2880", "4320")
                        .stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}