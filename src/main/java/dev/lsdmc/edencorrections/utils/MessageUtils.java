package dev.lsdmc.edencorrections.utils;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class MessageUtils {
    /**
     * Parse a MiniMessage string into a Component
     * @param message The MiniMessage string to parse
     * @return The parsed Component
     */
    public static Component parseMessage(String message) {
        return MiniMessage.miniMessage().deserialize(message);
    }

    /**
     * Get the plugin's prefix as a Component
     * @param plugin The plugin instance
     * @return The prefix Component
     */
    public static Component getPrefix(EdenCorrections plugin) {
        // Use centralized config management for better performance
        String prefixString = plugin.getConfigManager().getMessagesConfig().prefix;
        if (prefixString == null || prefixString.isEmpty()) {
            prefixString = "<gold>[Corrections]</gold> ";
        }
        return parseMessage(prefixString);
    }

    /**
     * Send a message to a CommandSender with the plugin's prefix
     * @param plugin The plugin instance
     * @param sender The CommandSender to send the message to
     * @param message The message to send
     */
    public static void sendPrefixedMessage(EdenCorrections plugin, CommandSender sender, String message) {
        sender.sendMessage(getPrefix(plugin).append(parseMessage(message)));
    }

    /**
     * Format a time duration in a human-readable format
     * @param seconds The time in seconds
     * @return The formatted time string
     */
    public static String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }

        int minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }

        int hours = minutes / 60;
        minutes = minutes % 60;

        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }

        int days = hours / 24;
        hours = hours % 24;

        return days + "d " + hours + "h";
    }
}