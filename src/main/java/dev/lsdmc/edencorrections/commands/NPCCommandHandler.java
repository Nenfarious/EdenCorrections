package dev.lsdmc.edencorrections.commands;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.NPCManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NPCCommandHandler implements CommandExecutor, TabCompleter {
    private final EdenCorrections plugin;
    private final NPCManager npcManager;

    public NPCCommandHandler(EdenCorrections plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreateCommand(sender);

            case "remove":
                return handleRemoveCommand(sender, args);

            case "list":
                return handleListCommand(sender);

            case "help":
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private boolean handleCreateCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This command can only be used by players!</red>")));
            return true;
        }

        if (!player.hasPermission("edencorrections.admin.npc")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        // Create NPC at player's location
        boolean success = npcManager.createNpc(player.getLocation(), player);

        if (success) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Duty NPC created successfully!</green>")));
        } else {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Failed to create duty NPC!</red>")));
        }

        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edencorrections.admin.npc")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        if (args.length < 2) {
            if (sender instanceof Player player) {
                // Try to find the closest NPC within 2 blocks
                Entity closestNPC = null;
                double closestDistanceSquared = 4; // 2 blocks squared

                for (UUID npcUuid : npcManager.getDutyNpcs()) {
                    for (Entity entity : player.getNearbyEntities(2, 2, 2)) {
                        if (entity.getUniqueId().equals(npcUuid)) {
                            double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
                            if (distanceSquared < closestDistanceSquared) {
                                closestNPC = entity;
                                closestDistanceSquared = distanceSquared;
                            }
                        }
                    }
                }

                if (closestNPC != null) {
                    boolean success = npcManager.removeNpc(closestNPC.getUniqueId());
                    if (success) {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<green>Duty NPC removed successfully!</green>")));
                    } else {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<red>Failed to remove duty NPC!</red>")));
                    }
                    return true;
                } else {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>No duty NPC found nearby! Use /dutynpc remove <id> or stand closer to an NPC.</red>")));
                    return true;
                }
            } else {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Usage: /dutynpc remove <id></red>")));
                return true;
            }
        }

        // Try to parse UUID
        try {
            UUID npcUuid = UUID.fromString(args[1]);
            boolean success = npcManager.removeNpc(npcUuid);

            if (success) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<green>Duty NPC removed successfully!</green>")));
            } else {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to remove duty NPC. Make sure the ID is valid.</red>")));
            }
        } catch (IllegalArgumentException e) {
            // Try to parse as index
            try {
                int index = Integer.parseInt(args[1]) - 1;
                List<UUID> npcs = npcManager.getDutyNpcs();

                if (index >= 0 && index < npcs.size()) {
                    UUID npcUuid = npcs.get(index);
                    boolean success = npcManager.removeNpc(npcUuid);

                    if (success) {
                        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<green>Duty NPC removed successfully!</green>")));
                    } else {
                        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<red>Failed to remove duty NPC!</red>")));
                    }
                } else {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>Invalid NPC index! Use /dutynpc list to see available NPCs.</red>")));
                }
            } catch (NumberFormatException ex) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Invalid NPC ID! Use a valid UUID or index number.</red>")));
            }
        }

        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("edencorrections.admin.npc")) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage(plugin.getConfig().getString("messages.no-permission",
                            "<red>You don't have permission to do that!</red>"))));
            return true;
        }

        List<UUID> npcs = npcManager.getDutyNpcs();

        if (npcs.isEmpty()) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>There are no duty NPCs.</yellow>")));
            return true;
        }

        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gold><bold>Duty NPCs:</bold></gold>")));

        int index = 1;
        for (UUID npcUuid : npcs) {
            sender.sendMessage(MessageUtils.parseMessage(
                    "<yellow>" + index + ". <aqua>" + npcUuid + "</aqua></yellow>"));
            index++;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        Component header = MessageUtils.parseMessage("<gold><bold>Duty NPC Commands</bold></gold>");

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);

        if (sender.hasPermission("edencorrections.admin.npc")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/dutynpc create</yellow> <gray>- Create a duty NPC at your location</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/dutynpc remove [id]</yellow> <gray>- Remove a duty NPC by ID or the closest one</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/dutynpc list</yellow> <gray>- List all duty NPCs</gray>"));
        }

        sender.sendMessage(MessageUtils.parseMessage("<yellow>/dutynpc help</yellow> <gray>- Show this help message</gray>"));
        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommand
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission("edencorrections.admin.npc")) {
                subCommands.add("create");
                subCommands.add("remove");
                subCommands.add("list");
            }

            subCommands.add("help");

            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Second argument - only for remove
            if (args[0].equalsIgnoreCase("remove") && sender.hasPermission("edencorrections.admin.npc")) {
                List<UUID> npcs = npcManager.getDutyNpcs();
                List<String> npcIds = new ArrayList<>();

                // Add indices
                for (int i = 1; i <= npcs.size(); i++) {
                    npcIds.add(String.valueOf(i));
                }

                // Add UUIDs
                for (UUID npcUuid : npcs) {
                    npcIds.add(npcUuid.toString());
                }

                return npcIds.stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}