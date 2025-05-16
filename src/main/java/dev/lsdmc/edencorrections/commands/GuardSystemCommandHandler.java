package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardBuffManager;
import dev.lsdmc.edencorrections.managers.GuardLootManager;
import dev.lsdmc.edencorrections.managers.GuardPenaltyManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles subcommands related to the guard system
 */
public class GuardSystemCommandHandler {
    private final EdenCorrections plugin;
    private final GuardBuffManager buffManager;
    private final GuardLootManager lootManager;
    private final GuardPenaltyManager penaltyManager;

    public GuardSystemCommandHandler(EdenCorrections plugin, GuardBuffManager buffManager,
                                     GuardLootManager lootManager, GuardPenaltyManager penaltyManager) {
        this.plugin = plugin;
        this.buffManager = buffManager;
        this.lootManager = lootManager;
        this.penaltyManager = penaltyManager;
    }

    /**
     * Handle the checkguards command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleCheckGuardsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkguards")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        int count = buffManager.getOnlineGuardCount();

        String message = plugin.getConfig().getString("commands.check-guards.message",
                "&8[&4&l𝕏&8] &cThere are currently {count} guards online!");

        sender.sendMessage(MessageUtils.parseMessage(message.replace("{count}", String.valueOf(count))));

        return true;
    }

    /**
     * Handle the fixguards command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleFixGuardsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.fixguards")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        buffManager.recalculateOnlineGuards();

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&aGuard count recalculated. There are now " +
                        buffManager.getOnlineGuardCount() + " guards online.")));

        return true;
    }

    /**
     * Handle the checkdeathcooldown command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleCheckDeathCooldownCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkdeathcooldown")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkdeathcooldown <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        int cooldown = lootManager.getPlayerCooldown(target.getUniqueId());

        String message = plugin.getConfig().getString("commands.check-death-cooldown.message",
                "&8[&4&l𝕏&8] &c{player}'s death cooldown is currently at {time}");

        sender.sendMessage(MessageUtils.parseMessage(message
                .replace("{player}", target.getName())
                .replace("{time}", String.valueOf(cooldown))));

        return true;
    }

    /**
     * Handle the cleardeathcooldown command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleClearDeathCooldownCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.cleardeathcooldown")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections cleardeathcooldown <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        lootManager.clearPlayerCooldown(target.getUniqueId());

        String message = plugin.getConfig().getString("commands.clear-death-cooldown.message",
                "&8[&4&l𝕏&8] &c{player}'s death cooldown has been cleared!");

        sender.sendMessage(MessageUtils.parseMessage(message.replace("{player}", target.getName())));

        return true;
    }

    /**
     * Handle the checkpenalty command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleCheckPenaltyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.checkpenalty")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections checkpenalty <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        int penalty = penaltyManager.getPlayerLockTime(target.getUniqueId());

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&c" + target.getName() + "'s death penalty is currently at " + penalty + " seconds.")));

        return true;
    }

    /**
     * Handle the clearpenalty command
     * @param sender Command sender
     * @param args Command arguments
     * @return True if command was handled
     */
    public boolean handleClearPenaltyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.clearpenalty")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /edencorrections clearpenalty <player></red>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Player not found!</red>")));
            return true;
        }

        penaltyManager.clearPlayerLockTime(target.getUniqueId());

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("&c" + target.getName() + "'s death penalty has been cleared!")));

        return true;
    }

    /**
     * Get tab completions for guard system commands
     * @param sender Command sender
     * @param command Command name
     * @param args Command arguments
     * @return List of tab completions
     */
    public List<String> getTabCompletions(CommandSender sender, String command, String[] args) {
        List<String> completions = new ArrayList<>();

        // Player name completions for commands that require a player
        if (args.length == 2) {
            if (command.equalsIgnoreCase("checkdeathcooldown") ||
                    command.equalsIgnoreCase("cleardeathcooldown") ||
                    command.equalsIgnoreCase("checkpenalty") ||
                    command.equalsIgnoreCase("clearpenalty")) {

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}