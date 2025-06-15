package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GUIManager;
import dev.lsdmc.edencorrections.managers.GuardDutyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EcoCommand implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;
    private final GuardDutyManager guardDutyManager;
    private final GUIManager guiManager;

    public EcoCommand(EdenCorrections plugin, GuardDutyManager guardDutyManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.guardDutyManager = guardDutyManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("edencorrections.guard")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            guiManager.showMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "duty" -> guardDutyManager.toggleDuty(player);
            case "breaktime" -> guardDutyManager.handleBreakTimeCommand(player);
            case "menu" -> guiManager.showMainMenu(player);
            case "rules" -> guiManager.showRulesMenu(player);
            default -> player.sendMessage(Component.text("Unknown subcommand. Use /eco for help.", NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("duty");
            completions.add("breaktime");
            completions.add("menu");
            completions.add("rules");
        }
        
        return completions;
    }
} 