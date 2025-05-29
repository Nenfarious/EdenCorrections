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
        page1.addCommand("/cor help [page]", "Show this help menu (pages 1-8)");
        page1.addCommand("/cor gui", "Open the main corrections GUI");
        page1.addCommand("/cor explain", "Learn about the guard duty system");
        page1.addCommand("/cor status [player]", "Check duty status (yours or another player's)");
        page1.addCommand("/cor tokens", "View your guard token balance", "edencorrections.tokens");
        helpPages.add(page1);

        // Page 2: Duty Management  
        HelpPage page2 = new HelpPage("Duty Management", "Guard duty and time management commands");
        page2.addCommand("/cor duty", "Toggle your guard duty status", "edencorrections.duty");
        page2.addCommand("/cor time", "Check your off-duty time balance", "edencorrections.duty");
        page2.addCommand("/cor convert <minutes>", "Convert off-duty time to tokens", "edencorrections.duty");
        page2.addCommand("/cor dutymenu", "Open duty management GUI", "edencorrections.duty");
        page2.addCommand("/cor statsmenu", "Open guard statistics GUI", "edencorrections.duty");
        helpPages.add(page2);

        // Page 3: Guard Actions
        HelpPage page3 = new HelpPage("Guard Actions", "Active guard enforcement commands");
        page3.addCommand("/cor search", "Record a prisoner search", "edencorrections.duty.actions");
        page3.addCommand("/cor found", "Record contraband discovery", "edencorrections.duty.actions");
        page3.addCommand("/cor detect", "Record metal detection", "edencorrections.duty.actions");
        page3.addCommand("/cor chase <player>", "Initiate chase with player", "edencorrections.duty.actions");
        page3.addCommand("/cor endchase <player>", "End chase with player", "edencorrections.duty.actions");
        page3.addCommand("/cor actionsmenu", "Open guard actions GUI", "edencorrections.duty.actions");
        helpPages.add(page3);

        // Page 4: Equipment & Shop
        HelpPage page4 = new HelpPage("Equipment & Shop", "Token system and equipment management");
        page4.addCommand("/cor equipmentmenu", "Open equipment purchase menu", "edencorrections.tokens");
        page4.addCommand("/cor shopmenu", "Open guard shop", "edencorrections.tokens");
        page4.addCommand("/cor givetokens <player> <amount>", "Give tokens to a player", "edencorrections.admin.tokens");
        page4.addCommand("/cor taketokens <player> <amount>", "Take tokens from a player", "edencorrections.admin.tokens");
        page4.addCommand("/cor settokens <player> <amount>", "Set player's token balance", "edencorrections.admin.tokens");
        helpPages.add(page4);

        // Page 5: Guard Commands
        HelpPage page5 = new HelpPage("Guard Commands", "Core enforcement and contraband commands");
        page5.addCommand("/g chase <player>", "Start pursuit of a suspect", "edencorrections.guard");
        page5.addCommand("/g endchase <player>", "End active pursuit", "edencorrections.guard");
        page5.addCommand("/g jail <player> [time]", "Detain player (auto-calculates time)", "edencorrections.guard");
        page5.addCommand("/g jailoffline <player>", "Queue offline player for detention", "edencorrections.guard");
        page5.addCommand("/sword <player>", "Request weapon drop (alias: /s)", "edencorrections.guard");
        page5.addCommand("/armor <player>", "Request armor removal (alias: /a)", "edencorrections.guard");
        page5.addCommand("/bow <player>", "Request bow drop (alias: /b)", "edencorrections.guard");
        page5.addCommand("/contraband <player>", "Request contraband drop (alias: /c)", "edencorrections.guard");
        helpPages.add(page5);

        // Page 6: Admin - Basic
        HelpPage page6 = new HelpPage("Admin - Basic", "Essential administrative commands");
        page6.addCommand("/cor reload", "Reload plugin configuration", "edencorrections.admin");
        page6.addCommand("/cor checkguards", "View online guards", "edencorrections.admin");
        page6.addCommand("/cor fixguards", "Recalculate guard count", "edencorrections.admin");
        page6.addCommand("/cor checkdeathcooldown <player>", "Check death cooldown", "edencorrections.admin");
        page6.addCommand("/cor cleardeathcooldown <player>", "Clear death cooldown", "edencorrections.admin");
        page6.addCommand("/cor checkpenalty <player>", "Check death penalty", "edencorrections.admin");
        page6.addCommand("/cor clearpenalty <player>", "Clear death penalty", "edencorrections.admin");
        page6.addCommand("/cor checkperms <player>", "Check guard permissions", "edencorrections.admin");
        helpPages.add(page6);

        // Page 7: Admin - Advanced
        HelpPage page7 = new HelpPage("Admin - Advanced", "Advanced administrative features");
        page7.addCommand("/cor checkrank <player>", "Check guard rank", "edencorrections.admin");
        page7.addCommand("/cor givehandcuffs <player>", "Give handcuffs to a player", "edencorrections.admin.wanted");
        page7.addCommand("/cor giveguarditems <player>", "Give guard items based on rank", "edencorrections.admin.wanted");
        page7.addCommand("/cor givespyglass <player>", "Give spyglass to a player", "edencorrections.admin.wanted");
        page7.addCommand("/cor setwanted <player> <level>", "Set player's wanted level (0-5)", "edencorrections.admin.wanted");
        page7.addCommand("/cor clearwanted <player>", "Clear player's wanted level", "edencorrections.admin.wanted");
        page7.addCommand("/cor getwanted <player>", "Check player's wanted level", "edencorrections.admin.wanted");
        page7.addCommand("/cor clearglow <player>", "Remove glow mark from player", "edencorrections.admin.wanted");
        helpPages.add(page7);

        // Page 8: Admin - Backend & Locations
        HelpPage page8 = new HelpPage("Admin - Backend", "Backend systems and location management");
        page8.addCommand("/cor setguardlounge", "Set guard lounge location", "edencorrections.admin.locations");
        page8.addCommand("/cor setspawn", "Set spawn location", "edencorrections.admin.locations");
        page8.addCommand("/cor setwardenoffice", "Set warden office location", "edencorrections.admin.locations");
        page8.addCommand("/cor locations", "List all plugin locations", "edencorrections.admin.locations");
        page8.addCommand("/cor tpguardlounge [player]", "Teleport to guard lounge", "edencorrections.admin.teleport");
        page8.addCommand("/cor checkitem", "Check ExecutableItem status", "edencorrections.admin.checkitem");
        page8.addCommand("/cor integrationstatus", "View integration status", "edencorrections.admin.checkitem");
        page8.addCommand("/cor reloadintegration", "Reload integrations", "edencorrections.admin.checkitem");
        helpPages.add(page8);
    }

    public void showHelp(CommandSender sender, String[] args) {
        int page = 1;

        // Parse page number if provided
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                        // Invalid page number, default to 1
                page = 1;
            }
        }

        // Clamp page to valid range
        page = Math.max(1, Math.min(page, TOTAL_PAGES));

        showHelpPage(sender, page);
    }

    /**
     * Show a specific help page
     */
    private void showHelpPage(CommandSender sender, int page) {
        HelpPage helpPage = helpPages.get(page - 1);
        
        // Send header
        sender.sendMessage(MessageUtils.parseMessage("<gold><bold>=== EdenCorrections Help Menu ===</bold></gold>"));
        sender.sendMessage(MessageUtils.parseMessage("<yellow>Page " + page + " of " + TOTAL_PAGES + " - " + helpPage.getTitle() + "</yellow>"));
        sender.sendMessage(MessageUtils.parseMessage("<gray>" + helpPage.getDescription() + "</gray>"));
        sender.sendMessage(MessageUtils.parseMessage(""));
        
        // Show commands for this page
        List<HelpCommand> commands = helpPage.getCommands();
        for (HelpCommand command : commands) {
            // Check if player has permission for this command
            if (command.hasPermission(sender)) {
                sender.sendMessage(MessageUtils.parseMessage("<yellow>" + command.getCommand() + "</yellow>"));
                sender.sendMessage(MessageUtils.parseMessage("  <gray>" + command.getDescription() + "</gray>"));
            }
        }
        
        // Show navigation
        sender.sendMessage(MessageUtils.parseMessage(""));
            StringBuilder navigation = new StringBuilder("<gray>Navigation: </gray>");
        
        if (page > 1) {
            navigation.append("<yellow>/cor help ").append(page - 1).append("</yellow> <gray>← </gray>");
        }
        
        navigation.append("<white>Page ").append(page).append("/").append(TOTAL_PAGES).append("</white>");
        
        if (page < TOTAL_PAGES) {
            navigation.append("<gray> → </gray><yellow>/cor help ").append(page + 1).append("</yellow>");
        }
        
        sender.sendMessage(MessageUtils.parseMessage(navigation.toString()));
        
        // Show quick access tip
        sender.sendMessage(MessageUtils.parseMessage(""));
        sender.sendMessage(MessageUtils.parseMessage("<green>Tip: Use <yellow>/cor help [1-8]</yellow> to jump to any page</green>"));
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

    /**
     * Inner class representing a help page
     */
    private static class HelpPage {
        private final String title;
        private final String description;
        private final List<HelpCommand> commands = new ArrayList<>();

        public HelpPage(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public void addCommand(String command, String description) {
            this.commands.add(new HelpCommand(command, description, null));
        }

        public void addCommand(String command, String description, String permission) {
            this.commands.add(new HelpCommand(command, description, permission));
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<HelpCommand> getCommands() { return commands; }
    }

    /**
     * Inner class representing a help command
     */
    private static class HelpCommand {
        private final String command;
        private final String description;
        private final String permission;

        public HelpCommand(String command, String description, String permission) {
            this.command = command;
            this.description = description;
            this.permission = permission;
        }

        public String getCommand() { return command; }
        public String getDescription() { return description; }

        public boolean hasPermission(CommandSender sender) {
            if (permission == null || permission.isEmpty()) {
                return true; // No permission required
            }
            return sender.hasPermission(permission);
        }
    }
} 