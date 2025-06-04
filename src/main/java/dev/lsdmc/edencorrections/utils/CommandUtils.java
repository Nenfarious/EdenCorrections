package dev.lsdmc.edencorrections.utils;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.command.CommandSender;

public class CommandUtils {
    public static void showUsage(EdenCorrections plugin, CommandSender sender, String commandKeyword) {
        for (HelpManager.HelpPage helpPage : plugin.getHelpManager().getHelpPages()) {
            for (HelpManager.HelpCommand helpCommand : helpPage.getCommands()) {
                if (helpCommand.getCommand().toLowerCase().contains(commandKeyword.toLowerCase())) {
                    sender.sendMessage(MessageUtils.parseMessage("<red>Usage:</red> <yellow>" + helpCommand.getCommand() + "</yellow>"));
                    if (helpCommand.getExample() != null && !helpCommand.getExample().isEmpty()) {
                        sender.sendMessage(MessageUtils.parseMessage("<gray>Example: <white>" + helpCommand.getExample() + "</white></gray>"));
                    }
                    return;
                }
            }
        }
    }
} 