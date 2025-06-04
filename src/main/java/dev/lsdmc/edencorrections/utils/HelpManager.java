package dev.lsdmc.edencorrections.utils;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HelpManager {
    private final EdenCorrections plugin;
    private static final int COMMANDS_PER_PAGE = 8;
    private static final int TOTAL_PAGES = 8;

    // All help pages organized logically
    private final List<HelpPage> helpPages = new ArrayList<>();

    public HelpManager(EdenCorrections plugin) {
        this.plugin = plugin;
        initializeHelpPages();
    }

    private void initializeHelpPages() {
        // Page 1: Basic Commands
        HelpPage page1 = new HelpPage("Basic Commands", "Essential commands for all players");
        page1.addCommand("/cor help [page]", "Show this help menu (pages 1-8)", null, "/cor help 2");
        page1.addCommand("/cor gui", "Open the main corrections GUI", null, "/cor gui");
        page1.addCommand("/cor explain", "Learn about the guard duty system", null, "/cor explain");
        page1.addCommand("/cor status [player]", "Check duty status (yours or another player's)", null, "/cor status");
        page1.addCommand("/cor tokens", "View your guard token balance", "edencorrections.tokens", "/cor tokens");
        helpPages.add(page1);

        // Page 2: Duty Management  
        HelpPage page2 = new HelpPage("Duty Management", "Guard duty and time management commands");
        page2.addCommand("/cor duty", "Toggle your guard duty status", "edencorrections.duty", "/cor duty");
        page2.addCommand("/cor time", "Check your off-duty time balance", "edencorrections.duty", "/cor time");
        page2.addCommand("/cor convert <minutes>", "Convert off-duty time to tokens", "edencorrections.duty", "/cor convert 10");
        page2.addCommand("/cor dutymenu", "Open duty management GUI", "edencorrections.duty", "/cor dutymenu");
        page2.addCommand("/cor statsmenu", "Open guard statistics GUI", "edencorrections.duty", "/cor statsmenu");
        helpPages.add(page2);

        // Page 3: Guard Actions
        HelpPage page3 = new HelpPage("Guard Actions", "Active guard enforcement commands");
        page3.addCommand("/cor search", "Record a prisoner search", "edencorrections.duty.actions", "/cor search");
        page3.addCommand("/cor found", "Record contraband discovery", "edencorrections.duty.actions", "/cor found");
        page3.addCommand("/cor detect", "Record metal detection", "edencorrections.duty.actions", "/cor detect");
        page3.addCommand("/cor chase <player>", "Initiate chase with player", "edencorrections.duty.actions", "/cor chase Steve");
        page3.addCommand("/cor endchase <player>", "End chase with player", "edencorrections.duty.actions", "/cor endchase Steve");
        page3.addCommand("/cor actionsmenu", "Open guard actions GUI", "edencorrections.duty.actions", "/cor actionsmenu");
        helpPages.add(page3);

        // Page 4: Equipment & Shop
        HelpPage page4 = new HelpPage("Equipment & Shop", "Token system and equipment management");
        page4.addCommand("/cor equipmentmenu", "Open equipment purchase menu", "edencorrections.tokens", "/cor equipmentmenu");
        page4.addCommand("/cor shopmenu", "Open guard shop", "edencorrections.tokens", "/cor shopmenu");
        page4.addCommand("/cor givetokens <player> <amount>", "Give tokens to a player", "edencorrections.admin.tokens", "/cor givetokens Steve 100");
        page4.addCommand("/cor taketokens <player> <amount>", "Take tokens from a player", "edencorrections.admin.tokens", "/cor taketokens Steve 50");
        page4.addCommand("/cor settokens <player> <amount>", "Set player's token balance", "edencorrections.admin.tokens", "/cor settokens Steve 200");
        helpPages.add(page4);

        // Page 5: Guard Commands
        HelpPage page5 = new HelpPage("Guard Commands", "Core enforcement and contraband commands");
        page5.addCommand("/g chase <player>", "Start pursuit of a suspect", "edencorrections.guard", "/g chase Steve");
        page5.addCommand("/g endchase <player>", "End active pursuit", "edencorrections.guard", "/g endchase Steve");
        page5.addCommand("/g jail <player> [time]", "Detain player (auto-calculates time)", "edencorrections.guard", "/g jail Steve");
        page5.addCommand("/g jailoffline <player>", "Queue offline player for detention", "edencorrections.guard", "/g jailoffline Steve");
        page5.addCommand("/sword <player>", "Request weapon drop (alias: /s)", "edencorrections.guard", "/sword Steve");
        page5.addCommand("/armor <player>", "Request armor removal (alias: /a)", "edencorrections.guard", "/armor Steve");
        page5.addCommand("/bow <player>", "Request bow drop (alias: /b)", "edencorrections.guard", "/bow Steve");
        page5.addCommand("/contraband <player>", "Request contraband drop (alias: /c)", "edencorrections.guard", "/contraband Steve");
        helpPages.add(page5);

        // Page 6: Admin - Basic
        HelpPage page6 = new HelpPage("Admin - Basic", "Essential administrative commands");
        page6.addCommand("/cor reload", "Reload plugin configuration", "edencorrections.admin", "/cor reload");
        page6.addCommand("/cor checkguards", "View online guards", "edencorrections.admin", "/cor checkguards");
        page6.addCommand("/cor fixguards", "Recalculate guard count", "edencorrections.admin", "/cor fixguards");
        page6.addCommand("/cor checkdeathcooldown <player>", "Check death cooldown", "edencorrections.admin", "/cor checkdeathcooldown Steve");
        page6.addCommand("/cor cleardeathcooldown <player>", "Clear death cooldown", "edencorrections.admin", "/cor cleardeathcooldown Steve");
        page6.addCommand("/cor checkpenalty <player>", "Check death penalty", "edencorrections.admin", "/cor checkpenalty Steve");
        page6.addCommand("/cor clearpenalty <player>", "Clear death penalty", "edencorrections.admin", "/cor clearpenalty Steve");
        page6.addCommand("/cor checkperms <player>", "Check guard permissions", "edencorrections.admin", "/cor checkperms Steve");
        helpPages.add(page6);

        // Page 7: Admin - Advanced
        HelpPage page7 = new HelpPage("Admin - Advanced", "Advanced administrative features");
        page7.addCommand("/cor checkrank <player>", "Check guard rank", "edencorrections.admin", "/cor checkrank Steve");
        page7.addCommand("/cor givehandcuffs <player>", "Give handcuffs to a player", "edencorrections.admin.wanted", "/cor givehandcuffs Steve");
        page7.addCommand("/cor giveguarditems <player>", "Give guard items based on rank", "edencorrections.admin.wanted", "/cor giveguarditems Steve");
        page7.addCommand("/cor givespyglass <player>", "Give spyglass to a player", "edencorrections.admin.wanted", "/cor givespyglass Steve");
        page7.addCommand("/cor setwanted <player> <level>", "Set player's wanted level (0-5)", "edencorrections.admin.wanted", "/cor setwanted Steve 3");
        page7.addCommand("/cor clearwanted <player>", "Clear player's wanted level", "edencorrections.admin.wanted", "/cor clearwanted Steve");
        page7.addCommand("/cor getwanted <player>", "Check player's wanted level", "edencorrections.admin.wanted", "/cor getwanted Steve");
        page7.addCommand("/cor clearglow <player>", "Remove glow mark from player", "edencorrections.admin.wanted", "/cor clearglow Steve");
        // Add new admin commands for guard rank management
        page7.addCommand("/cor createguardrank <rank> <group>", "Create a new guard rank and map it to a LuckPerms group", "edencorrections.admin", "/cor createguardrank sergeant guards_sergeant");
        page7.addCommand("/cor deleteguardrank <rank>", "Delete a guard rank and its mapping", "edencorrections.admin", "/cor deleteguardrank sergeant");
        page7.addCommand("/cor setplayerrank <player> <rank>", "Set a player's guard rank (move to mapped group)", "edencorrections.admin", "/cor setplayerrank Steve sergeant");
        page7.addCommand("/cor removeplayerrank <player>", "Remove a player's guard rank (remove from all mapped groups)", "edencorrections.admin", "/cor removeplayerrank Steve");
        page7.addCommand("/cor listranks", "List all defined guard ranks and their group mappings", "edencorrections.admin", "/cor listranks");
        page7.addCommand("/cor setguardrank <group> <rank>", "Assign a LuckPerms group to a guard rank and auto-assign required permissions", "edencorrections.admin", "/cor setguardrank guards_sergeant sergeant");
        page7.addCommand("/cor listguardranks", "List all guard rank to LuckPerms group mappings and their permissions", "edencorrections.admin", "/cor listguardranks");
        helpPages.add(page7);

        // Page 8: Admin - Backend & Locations
        HelpPage page8 = new HelpPage("Admin - Backend", "Backend systems and location management");
        page8.addCommand("/cor setguardlounge", "Set guard lounge location", "edencorrections.admin.locations", "/cor setguardlounge");
        page8.addCommand("/cor setspawn", "Set spawn location", "edencorrections.admin.locations", "/cor setspawn");
        page8.addCommand("/cor setwardenoffice", "Set warden office location", "edencorrections.admin.locations", "/cor setwardenoffice");
        page8.addCommand("/cor locations", "List all plugin locations", "edencorrections.admin.locations", "/cor locations");
        page8.addCommand("/cor tpguardlounge [player]", "Teleport to guard lounge", "edencorrections.admin.teleport", "/cor tpguardlounge Steve");
        page8.addCommand("/cor checkitem", "Check ExecutableItem status", "edencorrections.admin.checkitem", "/cor checkitem");
        page8.addCommand("/cor integrationstatus", "View integration status", "edencorrections.admin.checkitem", "/cor integrationstatus");
        page8.addCommand("/cor reloadintegration", "Reload integrations", "edencorrections.admin.checkitem", "/cor reloadintegration");
        helpPages.add(page8);
    }

    public void showHelp(CommandSender sender, String[] args) {
        int page = 1;

        // If user requests help for a specific command
        if (args.length == 2 && !isNumeric(args[1])) {
            String keyword = args[1].toLowerCase();
            for (HelpPage helpPage : helpPages) {
                for (HelpCommand command : helpPage.getCommands()) {
                    if (command.getCommand().toLowerCase().contains(keyword)) {
                        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>Command Help:</bold></gold>"));
                        sender.sendMessage(MessageUtils.parseMessage(buildClickableCommand(command)));
                        return;
                    }
                }
            }
            sender.sendMessage(MessageUtils.parseMessage("<red>No command found matching '<white>" + keyword + "</white>'.</red>"));
            return;
        }

        // Parse page number if provided
        if (args.length >= 2 && isNumeric(args[1])) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        // Clamp page to valid range
        page = Math.max(1, Math.min(page, TOTAL_PAGES));

        showHelpPage(sender, page);
    }

    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Show a specific help page, context-aware for permissions
     */
    private void showHelpPage(CommandSender sender, int page) {
        if (page < 1 || page > TOTAL_PAGES) {
            sender.sendMessage(MessageUtils.parseMessage("<red>Invalid help page. Please use 1-" + TOTAL_PAGES + ".</red>"));
            page = Math.max(1, Math.min(page, TOTAL_PAGES));
        }
        HelpPage helpPage = helpPages.get(page - 1);

        // Send header
        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>=== EdenCorrections Help Menu ===</bold></gold>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>Page " + page + " of " + TOTAL_PAGES + " - " + helpPage.getTitle() + "</yellow>"));
        sender.sendMessage(MessageUtils.parseMessage("<gray>" + helpPage.getDescription() + "</gray>"));
        sender.sendMessage(MessageUtils.parseMessage(""));

        // Show only commands the sender has permission for
        List<HelpCommand> commands = helpPage.getCommands();
        int shown = 0;
        for (HelpCommand command : commands) {
            if (command.hasPermission(sender)) {
                sender.sendMessage(MessageUtils.parseMessage(buildClickableCommand(command)));
                shown++;
            }
        }
        if (shown == 0) {
            sender.sendMessage(MessageUtils.parseMessage("<gray>No commands available on this page for your permissions.</gray>"));
        }

        // Show navigation with clickable arrows and hover text
        sender.sendMessage(MessageUtils.parseMessage(""));
        StringBuilder navigation = new StringBuilder("<gray>Navigation: </gray>");
        if (page > 1) {
            navigation.append("<click:suggest_command:'/cor help ").append(page - 1)
                .append("'><hover:show_text:'Go to previous page'><yellow>←</yellow></hover></click> ");
        } else {
            navigation.append("<gray>← </gray>");
        }
        navigation.append("<white>Page ").append(page).append("/").append(TOTAL_PAGES).append("</white>");
        if (page < TOTAL_PAGES) {
            navigation.append(" <click:suggest_command:'/cor help ").append(page + 1)
                .append("'><hover:show_text:'Go to next page'><yellow>→</yellow></hover></click>");
        } else {
            navigation.append(" <gray>→</gray>");
        }
        sender.sendMessage(MessageUtils.parseMessage(navigation.toString()));

        // Show quick access tip
        sender.sendMessage(MessageUtils.parseMessage(""));
        sender.sendMessage(MessageUtils.parseMessage("<green>Tip: Use <yellow>/cor help [1-8]</yellow> to jump to any page, or <yellow>/cor help <command></yellow> for details.</green>"));
    }

    /**
     * Get tab completions for help commands
     */
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 2) {
            // Add page numbers 1-8
            for (int i = 1; i <= TOTAL_PAGES; i++) {
                String pageNum = String.valueOf(i);
                if (pageNum.startsWith(args[1])) {
                    completions.add(pageNum);
                }
            }
        }
        
        return completions;
    }

    /**
     * Get a quick command summary for /cor explain
     */
    public String getSystemExplanation() {
        return "<gold><bold>EdenCorrections Help System:</bold></gold>\n" +
               "<yellow>Use <white>/cor help [page]</white> to browse all commands across " + TOTAL_PAGES + " pages:</yellow>\n" +
               "<gray>• Page 1: Basic Commands (help, gui, status)</gray>\n" +
               "<gray>• Page 2: Duty Management (duty, time, convert)</gray>\n" +
               "<gray>• Page 3: Guard Actions (search, chase, enforce)</gray>\n" +
               "<gray>• Page 4: Equipment & Shop (tokens, equipment)</gray>\n" +
               "<gray>• Page 5: Guard Commands (/g and contraband)</gray>\n" +
               "<gray>• Page 6-8: Admin Commands (management, locations, backend)</gray>\n" +
               "<yellow>Commands are filtered based on your permissions.</yellow>";
    }

    public List<String> getAvailableCommandKeywords(CommandSender sender) {
        List<String> keywords = new ArrayList<>();
        for (HelpPage helpPage : helpPages) {
            for (HelpCommand command : helpPage.getCommands()) {
                if (command.hasPermission(sender)) {
                    // Extract the main command keyword (e.g., from '/cor jail <player>' get 'jail')
                    String[] parts = command.getCommand().split(" ");
                    if (parts.length > 0) {
                        String keyword = parts[0].replace("/cor", "").replace("/g", "").trim();
                        if (!keyword.isEmpty() && !keywords.contains(keyword)) {
                            keywords.add(keyword);
                        }
                    }
                }
            }
        }
        return keywords;
    }

    /**
     * Inner class representing a help page
     */
    public static class HelpPage {
        private final String title;
        private final String description;
        private final List<HelpCommand> commands = new ArrayList<>();

        public HelpPage(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public void addCommand(String command, String description) {
            this.commands.add(new HelpCommand(command, description, null, null));
        }
        public void addCommand(String command, String description, String permission) {
            this.commands.add(new HelpCommand(command, description, permission, null));
        }
        public void addCommand(String command, String description, String permission, String example) {
            this.commands.add(new HelpCommand(command, description, permission, example));
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<HelpCommand> getCommands() { return commands; }
    }

    /**
     * Inner class representing a help command
     */
    public static class HelpCommand {
        private final String command;
        private final String description;
        private final String permission;
        private final String example;

        public HelpCommand(String command, String description, String permission) {
            this(command, description, permission, null);
        }
        public HelpCommand(String command, String description, String permission, String example) {
            this.command = command;
            this.description = description;
            this.permission = permission;
            this.example = example;
        }
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public String getExample() { return example; }
        public boolean hasPermission(CommandSender sender) {
            if (permission == null || permission.isEmpty()) {
                return true; // No permission required
            }
            return sender.hasPermission(permission);
        }
    }

    public List<HelpPage> getHelpPages() {
        return helpPages;
    }

    private String buildClickableCommand(HelpCommand command) {
        StringBuilder hover = new StringBuilder(command.getDescription());
        if (command.getExample() != null && !command.getExample().isEmpty()) {
            hover.append("\nExample: ").append(command.getExample());
        }
        return "<click:suggest_command:'" + command.getCommand() + "'><hover:show_text:'" + hover + "'><yellow>" + command.getCommand() + "</yellow></hover></click>";
    }
} 