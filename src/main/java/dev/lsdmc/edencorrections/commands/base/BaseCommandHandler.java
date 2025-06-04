package dev.lsdmc.edencorrections.commands.base;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.commands.admin.AdminCommandHandler;
import dev.lsdmc.edencorrections.commands.guard.GuardDutyCommandHandler;
import dev.lsdmc.edencorrections.commands.admin.NPCCommandHandler;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.HelpManager;
import dev.lsdmc.edencorrections.utils.CommandUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BaseCommandHandler implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;
    private final AdminCommandHandler adminCommandHandler;
    private final GuardDutyCommandHandler guardDutyCommandHandler;
    private final NPCCommandHandler npcCommandHandler;

    public BaseCommandHandler(EdenCorrections plugin) {
        this.plugin = plugin;
        this.adminCommandHandler = new AdminCommandHandler(plugin);
        this.guardDutyCommandHandler = new GuardDutyCommandHandler(plugin);
        this.npcCommandHandler = new NPCCommandHandler(plugin, plugin.getNpcManager());
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
                CommandUtils.showUsage(plugin, sender, subCommand);
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
        if (List.of("checkguards", "checkdeathcooldown", "cleardeathcooldown",
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
            case "explain":
                handleExplainCommand(sender);
                return true;
            case "reload":
                return handleReloadCommand(sender);
            case "npc":
                // Delegate to NPCCommandHandler for /eco npc ...
                String[] npcArgs = new String[args.length - 1];
                System.arraycopy(args, 1, npcArgs, 0, npcArgs.length);
                return npcCommandHandler.onCommand(sender, command, label, npcArgs);
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
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Main command completions - use mutable ArrayList
            List<String> mainCommands = new ArrayList<>(Arrays.asList(
                "help", "gui", "explain", "status", "tokens", "duty", "time", "convert", 
                "dutymenu", "statsmenu", "actionsmenu", "equipmentmenu", "shopmenu",
                "reload", "checkguards", "fixguards", "emergency"
            ));
            
            // Admin commands
            if (sender.hasPermission("edencorrections.admin")) {
                mainCommands.addAll(Arrays.asList(
                    "checkdeathcooldown", "cleardeathcooldown", "checkpenalty", "clearpenalty",
                    "checkperms", "checkrank", "givehandcuffs", "giveguarditems", "givespyglass",
                    "setwanted", "clearwanted", "getwanted", "clearglow", "setguardlounge", 
                    "setspawn", "setwardenoffice", "locations", "tpguardlounge", "checkitem",
                    "integrationstatus", "reloadintegration", "tagcontraband", "removecontrabandtag",
                    "listcontraband", "clearcontraband", "setguardrank", "listguardranks",
                    "createguardrank", "deleteguardrank", "setplayerrank", "removeplayerrank",
                    "listranks", "testloot", "togglelootsystem", "lootinfo"
                ));
            }
            
            // Token admin commands
            if (sender.hasPermission("edencorrections.admin.tokens")) {
                mainCommands.addAll(Arrays.asList("givetokens", "taketokens", "settokens"));
            }
            
            return mainCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "help":
                    // Help page numbers and categories
                    completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8"));
                    completions.addAll(Arrays.asList("basic", "duty", "guard", "admin", "equipment", "tokens"));
                    break;
                    
                case "status", "checkdeathcooldown", "cleardeathcooldown", "checkpenalty", 
                     "clearpenalty", "checkperms", "checkrank", "givehandcuffs", "giveguarditems",
                     "givespyglass", "clearwanted", "getwanted", "clearglow", "setplayerrank", 
                     "removeplayerrank", "testloot":
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
                        
                case "givetokens", "taketokens", "settokens":
                    if (sender.hasPermission("edencorrections.admin.tokens")) {
                        return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                    }
                    break;
                    
                case "convert":
                    // Suggest common minute values
                    completions.addAll(Arrays.asList("5", "10", "15", "30", "60", "120"));
                    break;
                    
                case "tpguardlounge":
                    // Player names for teleport command
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                        
                case "tagcontraband":
                    // Contraband types
                    completions.addAll(Arrays.asList("drug", "weapon", "communication", "tool", "general"));
                    break;
                    
                case "listcontraband", "clearcontraband":
                    // Contraband types
                    completions.addAll(Arrays.asList("drug", "weapon", "communication", "tool", "general", "all"));
                    break;
                    
                case "setguardrank":
                    // LuckPerms group names (if available)
                    completions.addAll(Arrays.asList("guards_trainee", "guards_private", "guards_officer", 
                        "guards_sergeant", "guards_warden", "trainee", "private", "officer", "sergeant", "warden"));
                    break;
                    
                case "createguardrank", "deleteguardrank":
                    // Rank names
                    completions.addAll(Arrays.asList("trainee", "private", "officer", "sergeant", "warden"));
                    break;
                    
                case "emergency":
                    completions.addAll(Arrays.asList("on", "off", "status"));
                    break;
                    
                case "togglelootsystem":
                    completions.addAll(Arrays.asList("on", "off", "enable", "disable", "true", "false"));
                    break;
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "setwanted":
                    // Wanted level values (0-5)
                    completions.addAll(Arrays.asList("0", "1", "2", "3", "4", "5"));
                    break;
                    
                case "givetokens", "taketokens", "settokens":
                    // Common token amounts
                    completions.addAll(Arrays.asList("100", "250", "500", "1000", "2500", "5000"));
                    break;
                    
                case "setguardrank":
                    // Guard rank names
                    completions.addAll(Arrays.asList("trainee", "private", "officer", "sergeant", "warden"));
                    break;
                    
                case "setplayerrank":
                    // Guard rank names
                    completions.addAll(Arrays.asList("trainee", "private", "officer", "sergeant", "warden"));
                    break;
                    
                case "createguardrank":
                    // LuckPerms group suggestions
                    completions.addAll(Arrays.asList("guards_trainee", "guards_private", "guards_officer", 
                        "guards_sergeant", "guards_warden"));
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