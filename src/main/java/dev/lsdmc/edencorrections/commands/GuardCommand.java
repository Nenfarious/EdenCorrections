package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import dev.lsdmc.edencorrections.managers.ContrabandManager.DrugEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GuardCommand implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;

    public GuardCommand(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("edencorrections.guard")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiService().showMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "duty" -> plugin.getGuardService().toggleDuty(player);
            case "rules" -> plugin.getGuiService().showRulesMenu(player);
            case "effects" -> {
                if (!plugin.getGuardService().isOnDuty(player)) {
                    player.sendMessage(Component.text("You must be on duty to check drug effects.", NamedTextColor.RED));
                    return true;
                }
                checkDrugEffects(player);
            }
            case "confiscate" -> {
                if (!plugin.getGuardService().isOnDuty(player)) {
                    player.sendMessage(Component.text("You must be on duty to confiscate items.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Please specify a player name.", NamedTextColor.RED));
                    return true;
                }
                plugin.getGuardService().confiscateItems(player, args[1]);
            }
            case "admin" -> {
                if (player.hasPermission("edencorrections.guard.admin")) {
                    plugin.getGuiService().showAdminMenu(player);
                } else {
                    player.sendMessage(Component.text("You don't have permission to use admin commands.", NamedTextColor.RED));
                }
            }
            case "setlounge" -> {
                if (!player.hasPermission("edencorrections.guard.admin")) {
                    player.sendMessage(Component.text("You don't have permission to set the guard lounge region.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Please specify a region name.", NamedTextColor.RED));
                    return true;
                }
                String regionName = args[1];
                plugin.getConfigService().setGuardLoungeRegion(regionName);
                player.sendMessage(Component.text("Guard lounge region set to: " + regionName, NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Unknown subcommand. Use /guard for the main menu.", NamedTextColor.RED));
        }

        return true;
    }

    private void handleConfiscate(Player guard, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            guard.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }

        List<ItemStack> confiscatedItems = plugin.getContrabandManager().confiscateContraband(target);
        if (confiscatedItems.isEmpty()) {
            guard.sendMessage(Component.text("No contraband found.", NamedTextColor.YELLOW));
            return;
        }

        int totalTokens = confiscatedItems.stream()
            .mapToInt(plugin.getContrabandManager()::getTokenReward)
            .sum();

        guard.sendMessage(Component.text("Confiscated " + confiscatedItems.size() + " contraband items.", NamedTextColor.GREEN));
        guard.sendMessage(Component.text("Earned " + totalTokens + " tokens.", NamedTextColor.GREEN));
    }

    private void checkDrugEffects(Player player) {
        if (!plugin.getContrabandManager().isUnderDrugEffect(player)) {
            player.sendMessage(Component.text("No active drug effects.", NamedTextColor.YELLOW));
            return;
        }

        ContrabandManager.DrugEffect effect = plugin.getContrabandManager().getActiveDrugEffect(player);
        if (effect != null) {
            long timeLeft = (effect.drug.duration * 1000L) - (System.currentTimeMillis() - effect.startTime);
            int minutesLeft = (int) (timeLeft / 60000);
            int secondsLeft = (int) ((timeLeft % 60000) / 1000);

            player.sendMessage(Component.text("Current drug effect: " + effect.drug.name, NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Time remaining: " + minutesLeft + "m " + secondsLeft + "s", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player player)) {
            return completions;
        }

        if (!player.hasPermission("edencorrections.guard")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("duty");
            completions.add("rules");
            if (plugin.getGuardService().isOnDuty(player)) {
                completions.add("confiscate");
            }
            if (player.hasPermission("edencorrections.guard.admin")) {
                completions.add("admin");
                completions.add("setlounge");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("confiscate")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
} 