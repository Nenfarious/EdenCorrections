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
     * @param format The format to use (default, compact, or detailed)
     * @return The formatted time string
     */
    public static String formatTime(long seconds, String format) {
        if (seconds < 60) {
            return seconds + "s";
        }

        int minutes = (int) (seconds / 60);
        seconds = (int) (seconds % 60);

        if (minutes < 60) {
            switch (format) {
                case "compact":
                    return minutes + "m";
                case "detailed":
                    return minutes + " minutes " + seconds + " seconds";
                default:
                    return minutes + "m " + seconds + "s";
            }
        }

        int hours = minutes / 60;
        minutes = minutes % 60;

        if (hours < 24) {
            switch (format) {
                case "compact":
                    return hours + "h";
                case "detailed":
                    return hours + " hours " + minutes + " minutes";
                default:
                    return hours + "h " + minutes + "m";
            }
        }

        int days = hours / 24;
        hours = hours % 24;

        switch (format) {
            case "compact":
                return days + "d";
            case "detailed":
                return days + " days " + hours + " hours";
            default:
                return days + "d " + hours + "h";
        }
    }

    /**
     * Format a time duration in a human-readable format using default format
     * @param seconds The time in seconds
     * @return The formatted time string
     */
    public static String formatTime(long seconds) {
        return formatTime(seconds, "default");
    }
}