package dev.lsdmc.edencorrections.commands.base;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.commands.admin.AdminCommandHandler;
import dev.lsdmc.edencorrections.commands.guard.GuardDutyCommandHandler;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.HelpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BaseCommandHandler implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;
    private final AdminCommandHandler adminCommandHandler;
    private final GuardDutyCommandHandler guardDutyCommandHandler;

    public BaseCommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
        this.adminCommandHandler = new AdminCommandHandler(plugin);
        this.guardDutyCommandHandler = new GuardDutyCommandHandler(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getHelpManager().showHelp(sender, new String[]{"help"});
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Token commands
        if (subCommand.equals("tokens")) {
            if (args.length == 1) {
                // /cor tokens (self)
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
                    return true;
                }
                if (!player.hasPermission("edencorrections.tokens")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to do that!</red>")));
                    return true;
                }
                int tokens = plugin.getGuardTokenManager().getTokens(player.getUniqueId());
                player.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<yellow>You have <green>" + tokens + "</green> tokens.</yellow>")));
                return true;
            } else if (args.length == 2) {
                // /cor tokens <player> (admin only)
                if (!sender.hasPermission("edencorrections.admin.tokens")) {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to do that!</red>")));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
                    return true;
                }
                int tokens = plugin.getGuardTokenManager().getTokens(target.getUniqueId());
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<yellow>" + target.getName() + " has <green>" + tokens + "</green> tokens.</yellow>")));
                return true;
            }
        }
        if (subCommand.equals("givetokens") || subCommand.equals("taketokens") || subCommand.equals("settokens")) {
            if (!sender.hasPermission("edencorrections.admin.tokens")) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>You don't have permission to do that!</red>")));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Usage: /cor " + subCommand + " <player> <amount></red>")));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player not found!</red>")));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Invalid number format!</red>")));
                return true;
            }
            if (amount < 0 && !subCommand.equals("taketokens")) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Amount must be non-negative!</red>")));
                return true;
            }
            if (subCommand.equals("givetokens")) {
                plugin.getGuardTokenManager().giveTokens(target, amount, "Admin grant");
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<green>Gave " + amount + " tokens to " + target.getName() + ".</green>")));
                return true;
            } else if (subCommand.equals("taketokens")) {
                boolean success = plugin.getGuardTokenManager().removeTokens(target.getUniqueId(), amount);
                if (success) {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<green>Took " + amount + " tokens from " + target.getName() + ".</green>")));
                    target.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>" + amount + " tokens were removed from your balance by an admin.</red>")));
                } else {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<red>Player does not have enough tokens.</red>")));
                }
                return true;
            } else if (subCommand.equals("settokens")) {
                plugin.getGuardTokenManager().setTokens(target.getUniqueId(), amount);
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<green>Set " + target.getName() + "'s tokens to " + amount + ".</green>")));
                target.sendMessage(MessageUtils.getPrefix(plugin).append(MessageUtils.parseMessage("<yellow>Your token balance was set to " + amount + " by an admin.</yellow>")));
                return true;
            }
        }

        // Handle duty management commands
        if (List.of("duty", "status", "time", "addtime", "settime", "convert").contains(subCommand)) {
            return guardDutyCommandHandler.handleCommand(sender, args);
        }

        // Handle admin commands
        if (List.of("checkguards", "fixguards", "checkdeathcooldown", "cleardeathcooldown",
                "checkpenalty", "clearpenalty", "checkperms", "checkrank").contains(subCommand)) {
            return adminCommandHandler.handleCommand(sender, args);
        }

        // Handle GUI commands
        if (List.of("gui", "dutymenu", "statsmenu", "actionsmenu", "equipmentmenu", "shopmenu").contains(subCommand)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
                return true;
            }
            
            switch (subCommand) {
                case "gui" -> plugin.getGuiManager().openMainMenu(player);
                case "dutymenu" -> plugin.getGuiManager().openDutyMenu(player);
                case "statsmenu" -> plugin.getGuiManager().openStatsMenu(player);
                case "actionsmenu" -> plugin.getGuiManager().openActionsMenu(player);
                case "equipmentmenu" -> plugin.getGuiManager().openEquipmentMenu(player);
                case "shopmenu" -> plugin.getGuiManager().openShopMenu(player);
            }
            return true;
        }

        // Handle other commands
        switch (subCommand) {
            case "help":
                plugin.getHelpManager().showHelp(sender, args);
                return true;
            case "gui":
                if (sender instanceof Player player) {
                    plugin.getGuiManager().openMainMenu(player);
                } else {
                    sender.sendMessage(MessageUtils.parseMessage("<red>This command can only be used by players!</red>"));
                }
                return true;
            case "explain":
                handleExplainCommand(sender);
                return true;
            case "reload":
                return handleReloadCommand(sender);
            default:
                return adminCommandHandler.handleCommand(sender, args) ||
                       guardDutyCommandHandler.handleCommand(sender, args);
        }
    }

    /**
     * Handle the reload command
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.reload")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You don't have permission to reload the plugin!</red>")));
            return true;
        }

        try {
            plugin.reload();
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>EdenCorrections plugin reloaded successfully!</green>")));
        } catch (Exception e) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Error reloading plugin: " + e.getMessage() + "</red>")));
            plugin.getLogger().severe("Error during plugin reload: " + e.getMessage());
        }
        return true;
    }

    /**     * Handle the explain command     */    private void handleExplainCommand(CommandSender sender) {        String explanation = plugin.getHelpManager().getSystemExplanation();        sender.sendMessage(MessageUtils.parseMessage(explanation));    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Always available commands
            completions.addAll(List.of("help", "explain", "gui"));

            // Add commands based on permissions
            if (sender.hasPermission("edencorrections.duty")) {
                completions.addAll(List.of("duty", "status", "time", "convert", "dutymenu", "statsmenu",
                        "actionsmenu", "equipmentmenu", "shopmenu", "search", "found", "detect"));
            }
            
            if (sender.hasPermission("edencorrections.tokens")) {
                completions.add("tokens");
            }
            
            if (sender.hasPermission("edencorrections.admin.tokens")) {
                completions.addAll(List.of("givetokens", "taketokens", "settokens"));
            }
            
            if (sender.hasPermission("edencorrections.admin")) {
                completions.addAll(List.of("addtime", "settime", "checkguards", "fixguards",
                        "checkdeathcooldown", "cleardeathcooldown", "checkpenalty", "clearpenalty",
                        "checkperms", "checkrank", "reload", "givehandcuffs", "giveguarditems", 
                        "givespyglass", "setwanted", "clearwanted", "getwanted", "clearglow",
                        "setguardlounge", "setspawn", "setwardenoffice", "locations", "tpguardlounge",
                        "removelocation", "migratelocations", "checkitem", "integrationstatus", 
                        "reloadintegration", "tagcontraband", "removecontrabandtag", "listcontraband", 
                        "clearcontraband"));
            }
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Handle second level tab completion
        String subCommand = args[0].toLowerCase();
        
        // Help command tab completion (simplified - just page numbers)
        if (subCommand.equals("help") && args.length == 2) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        // Token commands tab completion
        if (subCommand.equals("tokens") && args.length == 2 && sender.hasPermission("edencorrections.admin.tokens")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (List.of("givetokens", "taketokens", "settokens").contains(subCommand) && args.length == 2 && sender.hasPermission("edencorrections.admin.tokens")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Duty management commands
        if (List.of("duty", "status", "time", "addtime", "settime", "convert").contains(subCommand)) {
            return guardDutyCommandHandler.onTabComplete(sender, args);
        }

        // Admin commands - delegate to admin handler
        if (List.of("checkguards", "fixguards", "checkdeathcooldown", "cleardeathcooldown",
                "checkpenalty", "clearpenalty", "checkperms", "checkrank", "givehandcuffs", 
                "giveguarditems", "givespyglass", "setwanted", "clearwanted", "getwanted", 
                "clearglow", "setguardlounge", "setspawn", "setwardenoffice", "locations", 
                "tpguardlounge", "removelocation", "migratelocations", "checkitem", 
                "integrationstatus", "reloadintegration", "tagcontraband", "removecontrabandtag", 
                "listcontraband", "clearcontraband").contains(subCommand) && sender.hasPermission("edencorrections.admin")) {
            return adminCommandHandler.onTabComplete(sender, args);
        }

        return new ArrayList<>();
    }
} 