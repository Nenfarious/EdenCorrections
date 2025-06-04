package dev.lsdmc.edencorrections.commands.admin;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.NPCManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
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
            showNPCHelp(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                return handleCreateCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "help":
            default:
                showNPCHelp(sender);
                return true;
        }
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
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
        if (args.length < 3) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Usage: /eco npc create <name> <type> [section]</red>")));
            player.sendMessage(MessageUtils.parseMessage("<yellow>Types: DUTY, GUI | Sections (for GUI): MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS</yellow>"));
            return true;
        }
        String name = args[1];
        String typeStr = args[2].toUpperCase();
        dev.lsdmc.edencorrections.managers.NPCManager.NPCType type;
        try {
            type = dev.lsdmc.edencorrections.managers.NPCManager.NPCType.valueOf(typeStr);
        } catch (Exception e) {
            player.sendMessage(MessageUtils.parseMessage("<red>Invalid type! Use DUTY or GUI.</red>"));
            return true;
        }
        dev.lsdmc.edencorrections.managers.NPCManager.GuiSection section = null;
        if (type == dev.lsdmc.edencorrections.managers.NPCManager.NPCType.GUI) {
            if (args.length < 4) {
                player.sendMessage(MessageUtils.parseMessage("<red>For GUI NPCs, specify a section: MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS</red>"));
                return true;
            }
            try {
                section = dev.lsdmc.edencorrections.managers.NPCManager.GuiSection.valueOf(args[3].toUpperCase());
            } catch (Exception e) {
                player.sendMessage(MessageUtils.parseMessage("<red>Invalid section! Use: MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS</red>"));
                return true;
            }
        }
        boolean success = (type == dev.lsdmc.edencorrections.managers.NPCManager.NPCType.GUI)
            ? npcManager.createNpc(player.getLocation(), player, name, type, section)
            : npcManager.createNpc(player.getLocation(), player, name, type, null);
        if (success) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Corrections NPC created successfully!</green>")));
        } else {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Failed to create Corrections NPC!</red>")));
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

                for (UUID npcUuid : npcManager.getCorrectionsNpcs()) {
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
                                MessageUtils.parseMessage("<green>Corrections NPC removed successfully!</green>")));
                    } else {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<red>Failed to remove Corrections NPC!</red>")));
                    }
                    return true;
                } else {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>No Corrections NPC found nearby! Use /eco npc remove <id> or stand closer to an NPC.</red>")));
                    return true;
                }
            } else {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Usage: /eco npc remove <id></red>")));
                return true;
            }
        }

        // Try to parse UUID
        try {
            UUID npcUuid = UUID.fromString(args[1]);
            boolean success = npcManager.removeNpc(npcUuid);

            if (success) {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<green>Corrections NPC removed successfully!</green>")));
            } else {
                sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to remove Corrections NPC. Make sure the ID is valid.</red>")));
            }
        } catch (IllegalArgumentException e) {
            // Try to parse as index
            try {
                int index = Integer.parseInt(args[1]) - 1;
                List<UUID> npcs = npcManager.getCorrectionsNpcs();

                if (index >= 0 && index < npcs.size()) {
                    UUID npcUuid = npcs.get(index);
                    boolean success = npcManager.removeNpc(npcUuid);

                    if (success) {
                        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<green>Corrections NPC removed successfully!</green>")));
                    } else {
                        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                                MessageUtils.parseMessage("<red>Failed to remove Corrections NPC!</red>")));
                    }
                } else {
                    sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>Invalid NPC index! Use /eco npc list to see available NPCs.</red>")));
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
        List<UUID> npcs = npcManager.getCorrectionsNpcs();
        if (npcs.isEmpty()) {
            sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>There are no Corrections NPCs.</yellow>")));
            return true;
        }
        sender.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<gold><bold>Corrections NPCs:</bold></gold>")));
        int index = 1;
        for (UUID npcUuid : npcs) {
            var type = npcManager.getNpcType(npcUuid);
            var section = npcManager.getGuiSection(npcUuid);
            String info = "<yellow>" + index + ". <aqua>" + npcUuid + "</aqua> <gray>Type: " + type + (type == dev.lsdmc.edencorrections.managers.NPCManager.NPCType.GUI && section != null ? ", Section: " + section : "") + "</gray></yellow>";
            sender.sendMessage(MessageUtils.parseMessage(info));
            index++;
        }
        return true;
    }

    /**
     * Show NPC-specific help (this is a separate command, not part of the main help system)
     */
    private void showNPCHelp(CommandSender sender) {
        Component header = MessageUtils.parseMessage("<gold><bold>Corrections NPC Commands</bold></gold>");
        sender.sendMessage(Component.empty());
        sender.sendMessage(header);
        if (sender.hasPermission("edencorrections.admin.npc")) {
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/eco npc create <name> <type> [section]</yellow> <gray>- Create a Corrections NPC at your location. Types: DUTY, GUI. Sections: MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/eco npc remove [id]</yellow> <gray>- Remove a Corrections NPC by ID or the closest one</gray>"));
            sender.sendMessage(MessageUtils.parseMessage("<yellow>/eco npc list</yellow> <gray>- List all Corrections NPCs</gray>"));
        }
        sender.sendMessage(MessageUtils.parseMessage("<yellow>/eco npc help</yellow> <gray>- Show this help message</gray>"));
        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Main NPC commands
            List<String> npcCommands = Arrays.asList("create", "remove", "list", "help");
            
            return npcCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "create":
                    // NPC names/IDs
                    completions.addAll(Arrays.asList("duty_npc", "guard_npc", "corrections_officer", "warden_assistant"));
                    break;
                    
                case "remove":
                    // For remove command, we could list existing NPC IDs
                    // This would require accessing the NPC registry
                    completions.addAll(Arrays.asList("<npc_uuid>", "all"));
                    break;
                    
                default:
                    break;
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "create":
                    // NPC types
                    completions.addAll(Arrays.asList("DUTY", "GUI"));
                    break;
                    
                default:
                    break;
            }
        }
        
        if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            String npcType = args[2].toUpperCase();
            
            switch (subCommand) {
                case "create":
                    if ("GUI".equals(npcType)) {
                        // GUI sections
                        completions.addAll(Arrays.asList("MAIN", "DUTY", "STATS", "ACTIONS", "EQUIPMENT", "SHOP", "TOKENS"));
                    }
                    break;
                    
                default:
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