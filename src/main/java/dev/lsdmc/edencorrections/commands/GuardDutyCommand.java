package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.managers.GuardDutyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuardDutyCommand implements CommandExecutor {
    private final GuardDutyManager guardDutyManager;

    public GuardDutyCommand(GuardDutyManager guardDutyManager) {
        this.guardDutyManager = guardDutyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("duty")) {
            guardDutyManager.toggleDuty(player);
            return true;
        }

        player.sendMessage("Usage: /eco duty");
        return true;
    }
} 