package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardItemManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.ItemsConfig itemsConfig;
    private final NamespacedKey itemTypeKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> searchTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> cuffingTasks = new HashMap<>();
    
    // Cached configuration values for better performance
    private final double cuffingMaxDistance;
    private final double batonMaxDistance;
    private final double taserMaxDistance;
    private final double jailMaxDistance;
    private final String cmiJailName;

    public GuardItemManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.itemsConfig = configManager.getItemsConfig();
        this.itemTypeKey = new NamespacedKey(plugin, "guard_item_type");
        
        // Cache configuration values for better performance
        this.cuffingMaxDistance = itemsConfig.getDouble("handcuffs.max-distance", 5.0);
        this.batonMaxDistance = itemsConfig.getDouble("guard-baton.max-distance", 3.0);
        this.taserMaxDistance = itemsConfig.getDouble("taser.max-distance", 8.0);
        this.jailMaxDistance = itemsConfig.getDouble("commands.jail.max-distance", 5.0);
        this.cmiJailName = itemsConfig.getString("jail.cmi-jail-name", "default");
    }

    /**
     * Reload configuration
     */
    public void reload() {
        this.itemsConfig = configManager.getItemsConfig();
    }

    /**
     * Create a guard item
     */
    public ItemStack createGuardItem(String type, String name, Material material, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            
            List<String> itemLore = new ArrayList<>();
            for (String line : lore) {
                itemLore.add(line);
            }
            meta.setLore(itemLore);

            // Store item type
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(itemTypeKey, PersistentDataType.STRING, type);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Check if an item is a guard item
     */
    public boolean isGuardItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(itemTypeKey, PersistentDataType.STRING);
    }

    /**
     * Get the type of a guard item
     */
    public String getGuardItemType(ItemStack item) {
        if (!isGuardItem(item)) return null;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(itemTypeKey, PersistentDataType.STRING);
    }

    /**
     * Check if an item is on cooldown
     */
    private boolean isOnCooldown(UUID playerId, String itemType) {
        if (!cooldowns.containsKey(playerId)) return false;
        
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        return System.currentTimeMillis() < playerCooldowns.getOrDefault(itemType, 0L);
    }

    /**
     * Get remaining cooldown in seconds
     */
    private int getCooldownSeconds(UUID playerId, String itemType) {
        if (!cooldowns.containsKey(playerId)) return 0;
        
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;
        long remaining = playerCooldowns.getOrDefault(itemType, 0L) - System.currentTimeMillis();
        return Math.max(0, (int)(remaining / 1000));
    }

    /**
     * Set cooldown for an item
     */
    private void setCooldown(UUID playerId, String itemType, int seconds) {
        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(itemType, System.currentTimeMillis() + (seconds * 1000L));
    }

    /**
     * Start a search countdown
     */
    private void startSearchCountdown(Player guard, Player target, int countdown, String itemType, Runnable onComplete) {
        UUID guardId = guard.getUniqueId();
        
        // Cancel any existing search
        if (searchTasks.containsKey(guardId)) {
            searchTasks.get(guardId).cancel();
        }
        
        // Start countdown
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = countdown;
            
            @Override
            public void run() {
                // Check if target moved away
                if (!guard.isOnline() || !target.isOnline() ||
                        guard.getLocation().distance(target.getLocation()) > 5) {
                    cancelSearch(guard, target);
                    return;
                }
                
                if (secondsLeft <= 0) {
                    // Complete search
                    searchTasks.remove(guardId);
                    onComplete.run();
                    return;
                }
                
                // Update countdown
                guard.sendMessage(MessageUtils.parseMessage(
                    "<yellow>Searching " + target.getName() + " in " + secondsLeft + "...</yellow>"));
                target.sendMessage(MessageUtils.parseMessage(
                    "<yellow>Being searched by " + guard.getName() + " in " + secondsLeft + "...</yellow>"));
                
                secondsLeft--;
            }
        }, 0L, 20L);
        
        searchTasks.put(guardId, task);
    }

    /**
     * Cancel an ongoing search
     */
    private void cancelSearch(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        if (searchTasks.containsKey(guardId)) {
            searchTasks.get(guardId).cancel();
            searchTasks.remove(guardId);
            
            guard.sendMessage(MessageUtils.parseMessage("<red>Search cancelled! Target moved away.</red>"));
            target.sendMessage(MessageUtils.parseMessage("<green>Search cancelled! You moved away from the guard.</green>"));
            
            // Only start chase if conditions are met to prevent spam
            if (shouldStartChase(guard, target)) {
                plugin.getChaseManager().startChase(guard, target);
            }
        }
    }

    /**
     * Check if a chase should be started to prevent spam and invalid chases
     */
    private boolean shouldStartChase(Player guard, Player target) {
        // Don't start chase if already being chased
        if (plugin.getChaseManager().isBeingChased(target)) {
            return false;
        }
        
        // Don't start chase if target is already jailed
        if (plugin.getJailManager().isJailed(target.getUniqueId())) {
            return false;
        }
        
        // Don't start chase if players are too far apart (prevents console spam)
        double distance = guard.getLocation().distance(target.getLocation());
        double maxChaseDistance = itemsConfig.general.globalMaxRange;
        if (distance > maxChaseDistance) {
            guard.sendMessage(MessageUtils.parseMessage("<red>Target is too far away to initiate a chase!</red>"));
            return false;
        }
        
        // Don't start chase if target has very low wanted level
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
        int minWantedForChase = 1; // Default minimum wanted level
        if (wantedLevel < minWantedForChase) {
            return false;
        }
        
        // Don't start chase if both players are in safezones (unless safezone mode allows it)
        if (plugin.getSafezoneManager().isInSafezone(guard) && 
            plugin.getSafezoneManager().isInSafezone(target) &&
            "require".equals(plugin.getSafezoneManager().getSafezoneMode())) {
            // In require mode, guard actions happen in safezones, no need for chase
            return false;
        }
        
        return true;
    }

    /**
     * ENHANCED: Internal spam protection check
     * Prevents method spamming even if called directly
     */
    private boolean isInternalSpamProtected(UUID playerId, String actionType) {
        String key = playerId.toString() + ":" + actionType;
        long now = System.currentTimeMillis();
        
        if (cooldowns.containsKey(playerId)) {
            Map<String, Long> playerCooldowns = cooldowns.get(playerId);
            if (playerCooldowns.containsKey("spam_protection_" + actionType)) {
                long lastAction = playerCooldowns.get("spam_protection_" + actionType);
                if (now - lastAction < 1500) { // 1.5 second internal spam protection
                    return true; // Is spam protected
                }
            }
        }
        
        // Update spam protection timestamp
        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put("spam_protection_" + actionType, now);
        
        return false; // Not spam protected
    }

    /**
     * Handle drug sniffer use (ENHANCED with spam protection)
     */
    public void handleDrugSniffer(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        
        // CRITICAL: Internal spam protection (first line of defense)
        if (isInternalSpamProtected(guardId, "drug_sniffer")) {
            return; // Silently ignore spam attempts
        }
        
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return;
        }

        // Check safezone rules
        if (!plugin.getSafezoneManager().canPerformGuardAction(guard, target)) {
            String message = plugin.getSafezoneManager().getDenialMessage(guard, target);
            guard.sendMessage(MessageUtils.parseMessage(message));
            return;
        }

        // Check cooldown
        if (isOnCooldown(guardId, "drug_sniffer")) {
            int cooldownSeconds = getCooldownSeconds(guardId, "drug_sniffer");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Drug sniffer is on cooldown for " + cooldownSeconds + " seconds!</red>")));
            return;
        }

        // Check distance
        double distance = guard.getLocation().distance(target.getLocation());
        double maxDistance = itemsConfig.drugSniffer.range;
        if (distance > maxDistance) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away! Get within " + maxDistance + " blocks.</red>")));
            return;
        }

        // Start search countdown
        int countdown = itemsConfig.drugSniffer.countdown;
        startSearchCountdown(guard, target, countdown, "drug_sniffer", () -> {
            // Check for drugs using comprehensive detection
            boolean foundDrugs = false;
            
            // Check inventory for drug items
            for (org.bukkit.inventory.ItemStack item : target.getInventory().getContents()) {
                if (item != null && plugin.getExternalPluginIntegration().isDrugComprehensive(item)) {
                    foundDrugs = true;
                    break;
                }
            }
            
            // Check for drug effects (player under influence)
            if (!foundDrugs && plugin.getExternalPluginIntegration().isUnderInfluence(target)) {
                foundDrugs = true;
            }
            
            // Handle results
            if (foundDrugs) {
                // Drugs found
                int reward = itemsConfig.drugSniffer.rewardPerDrug;
                plugin.getGuardTokenManager().giveTokens(guard, reward, "Found drugs on " + target.getName());
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Drug sniffer detected drugs on " + target.getName() + "!</red>")));
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>A guard has detected drugs on you!</red>")));
                
                // Record for statistics
                plugin.getGuardStatisticsManager().recordSuccessfulSearch(guard);
                plugin.getGuardProgressionManager().recordContraband(guard);
                
                // Increase wanted level
                plugin.getWantedLevelManager().increaseWantedLevel(target, false);
                
            } else {
                // No drugs found
                int reward = itemsConfig.drugSniffer.rewardNoFind;
                plugin.getGuardTokenManager().giveTokens(guard, reward, "Drug search on " + target.getName());
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Drug sniffer found no drugs on " + target.getName() + ".</green>")));
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Drug search completed - you are clean.</green>")));
            }
            
            // Record the search
            plugin.getGuardStatisticsManager().recordSearch(guard);
            
            // Set cooldown
            int cooldownTime = itemsConfig.drugSniffer.cooldown;
            setCooldown(guardId, "drug_sniffer", cooldownTime);
        });
    }

    /**
     * Handle handcuffs use (ENHANCED with spam protection)
     */
    public void handleHandcuffs(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        
        // CRITICAL: Internal spam protection (first line of defense)
        if (isInternalSpamProtected(guardId, "handcuffs")) {
            return; // Silently ignore spam attempts
        }
        
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return;
        }

        // Check safezone rules
        if (!plugin.getSafezoneManager().canPerformGuardAction(guard, target)) {
            String message = plugin.getSafezoneManager().getDenialMessage(guard, target);
            guard.sendMessage(MessageUtils.parseMessage(message));
            return;
        }

        // Check if target can be jailed
        if (!canJailTarget(guard, target)) {
            return;
        }

        // Check cooldown
        if (isOnCooldown(guardId, "handcuffs")) {
            int remainingSeconds = getCooldownSeconds(guardId, "handcuffs");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Handcuffs on cooldown for " + remainingSeconds + " seconds!</red>")));
            return;
        }

        // Check range
        if (guard.getLocation().distance(target.getLocation()) > itemsConfig.handcuffs.maxDistance) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away to handcuff!</red>")));
            return;
        }

        // Set cooldown
        setCooldown(guardId, "handcuffs", itemsConfig.handcuffs.cooldown);

        // Start cuffing countdown
        int countdownSeconds = itemsConfig.handcuffs.countdown;
        startCuffingCountdown(guard, target, countdownSeconds, () -> {
            // Successfully cuffed - proceed with jail
            jailWithCMI(guard, target);
        });
    }

    /**
     * Start a cuffing countdown (displays countdown, allows escape, starts chase if escaped)
     */
    private void startCuffingCountdown(Player guard, Player target, int countdownSeconds, Runnable onComplete) {
        UUID guardId = guard.getUniqueId();
        UUID targetId = target.getUniqueId();

        // Cancel any existing cuffing task for this guard
        if (cuffingTasks.containsKey(guardId)) {
            cuffingTasks.get(guardId).cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = countdownSeconds;

            @Override
            public void run() {
                if (!guard.isOnline() || !target.isOnline() ||
                    guard.getLocation().distance(target.getLocation()) > cuffingMaxDistance) {
                    // Target escaped!
                    cancelCuffing(guard, target);
                    
                    // Only start chase if conditions are met to prevent spam
                    if (shouldStartChase(guard, target)) {
                        plugin.getChaseManager().startChase(guard, target);
                    }
                    return;
                }

                if (secondsLeft <= 0) {
                    // Cuffing complete
                    cuffingTasks.remove(guardId);
                    onComplete.run();
                    return;
                }

                // Show countdown to both players
                guard.sendMessage(MessageUtils.parseMessage("<yellow>Cuffing " + target.getName() + " in " + secondsLeft + "...</yellow>"));
                target.sendMessage(MessageUtils.parseMessage("<yellow>You are being cuffed by " + guard.getName() + " in " + secondsLeft + "...</yellow>"));
                secondsLeft--;
            }
        }, 0L, 20L);

        cuffingTasks.put(guardId, task);
    }

    /**
     * Cancel an ongoing cuffing attempt
     */
    private void cancelCuffing(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        if (cuffingTasks.containsKey(guardId)) {
            cuffingTasks.get(guardId).cancel();
            cuffingTasks.remove(guardId);
        }
        guard.sendMessage(MessageUtils.parseMessage("<red>Cuffing cancelled! Target moved away.</red>"));
        target.sendMessage(MessageUtils.parseMessage("<green>You escaped being cuffed!</green>"));
    }

    /**
     * Jail a player using CMI's jail command (called after successful cuffing or instant jail during chase)
     * FIXED: Improved CMI integration with better error handling and validation
     */
    private void jailWithCMI(Player guard, Player target) {
        // Double-check if target can still be jailed
        if (!canJailTarget(guard, target)) {
            return;
        }

        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
        double baseMinutes = Math.max(1, wantedLevel * 2); // 2 minutes per wanted star, minimum 1
        
        // Jail the player with enhanced targeting
        plugin.getJailManager().jailPlayer(target, baseMinutes, "Arrested by " + guard.getName(), guard);
        
        // Give rewards, tokens, etc.
        int baseReward = itemsConfig.handcuffs.rewardBase;
        int bonusPerLevel = itemsConfig.handcuffs.rewardPerLevel;
        int totalReward = baseReward + (wantedLevel * bonusPerLevel);
        
        plugin.getGuardTokenManager().giveTokens(guard, totalReward, "Apprehension reward");
        plugin.getDutyManager().addOffDutyMinutes(guard.getUniqueId(), 1 + wantedLevel);
        
        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Successfully jailed " + target.getName() + " for " + baseMinutes + " minutes! +" + totalReward + " tokens</green>")));
            
        // Notify other guards
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(onlinePlayer.getUniqueId()) && !onlinePlayer.equals(guard)) {
                onlinePlayer.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>" + guard.getName() + " successfully apprehended " + target.getName() + "!</yellow>")));
            }
        }
        
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Successfully jailed " + target.getName() + " via CMI integration");
        }
    }

    /**
     * Handle metal detector use (ENHANCED with spam protection)
     */
    public void handleMetalDetector(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        
        // CRITICAL: Internal spam protection (first line of defense)
        if (isInternalSpamProtected(guardId, "metal_detector")) {
            return; // Silently ignore spam attempts
        }
        
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return;
        }

        // Check safezone rules
        if (!plugin.getSafezoneManager().canPerformGuardAction(guard, target)) {
            String message = plugin.getSafezoneManager().getDenialMessage(guard, target);
            guard.sendMessage(MessageUtils.parseMessage(message));
            return;
        }

        // Check cooldown
        if (isOnCooldown(guardId, "metal_detector")) {
            int cooldownSeconds = getCooldownSeconds(guardId, "metal_detector");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Metal detector is on cooldown for " + cooldownSeconds + " seconds!</red>")));
            return;
        }

        // Start countdown
        int countdown = itemsConfig.getInt("items.metal-detector.countdown", 10);
        startSearchCountdown(guard, target, countdown, "metal_detector", () -> {
            // Check for metal items
            boolean foundMetal = false;
            java.util.List<String> metalItems = new java.util.ArrayList<>();
            
            for (org.bukkit.inventory.ItemStack item : target.getInventory().getContents()) {
                if (item != null && isMetalItem(item)) {
                    foundMetal = true;
                    metalItems.add(getItemDisplayName(item));
                }
            }
            
            // Check armor slots
            for (org.bukkit.inventory.ItemStack item : target.getInventory().getArmorContents()) {
                if (item != null && isMetalItem(item)) {
                    foundMetal = true;
                    metalItems.add(getItemDisplayName(item));
                }
            }
            
            // Handle results
            if (foundMetal) {
                // Metal found
                int reward = itemsConfig.getInt("items.metal-detector.reward.find", 250);
                plugin.getGuardTokenManager().giveTokens(guard, reward, "Found metal items on " + target.getName());
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Metal detector found metal items on " + target.getName() + ": " + String.join(", ", metalItems) + "</red>")));
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Metal detection revealed your metal items!</red>")));
                
                // Record statistics
                plugin.getGuardStatisticsManager().recordSuccessfulSearch(guard);
                plugin.getGuardStatisticsManager().recordMetalDetection(guard);
                
            } else {
                // No metal found
                int reward = itemsConfig.getInt("items.metal-detector.reward.no-find", 250);
                plugin.getGuardTokenManager().giveTokens(guard, reward, "Metal detection on " + target.getName());
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Metal detector found no metal items on " + target.getName() + ".</green>")));
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Metal detection completed - no metal items found.</green>")));
            }
            
            // Record the search
            plugin.getGuardStatisticsManager().recordSearch(guard);
            
            // Set cooldown
            int cooldownTime = itemsConfig.getInt("items.metal-detector.cooldown", 1800);
            setCooldown(guardId, "metal_detector", cooldownTime);
        });
    }

    /**
     * Check if an item is considered metal for metal detector
     */
    private boolean isMetalItem(org.bukkit.inventory.ItemStack item) {
        String materialName = item.getType().name();
        return materialName.contains("IRON") || materialName.contains("DIAMOND") || 
               materialName.contains("NETHERITE") || materialName.contains("GOLD") ||
               materialName.contains("SWORD") || materialName.contains("AXE") ||
               materialName.contains("PICKAXE") || materialName.contains("SHOVEL") ||
               materialName.contains("HOE") || materialName.contains("HELMET") ||
               materialName.contains("CHESTPLATE") || materialName.contains("LEGGINGS") ||
               materialName.contains("BOOTS") || materialName.contains("SHEARS");
    }

    /**
     * Handle spyglass use (ENHANCED with spam protection)
     */
    public void handleSpyglass(Player guard, Player target) {
        UUID guardId = guard.getUniqueId();
        
        // CRITICAL: Internal spam protection (first line of defense)
        if (isInternalSpamProtected(guardId, "spyglass")) {
            return; // Silently ignore spam attempts
        }
        
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return;
        }

        // Check safezone rules
        if (!plugin.getSafezoneManager().canPerformGuardAction(guard, target)) {
            String message = plugin.getSafezoneManager().getDenialMessage(guard, target);
            guard.sendMessage(MessageUtils.parseMessage(message));
            return;
        }

        // Check cooldown
        if (isOnCooldown(guardId, "spyglass")) {
            int cooldownSeconds = getCooldownSeconds(guardId, "spyglass");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Spyglass is on cooldown for " + cooldownSeconds + " seconds!</red>")));
            return;
        }

        // Check range
        double distance = guard.getLocation().distance(target.getLocation());
        double maxRange = itemsConfig.getDouble("items.spyglass.range", 10.0);
        if (distance > maxRange) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away! Maximum range is " + maxRange + " blocks.</red>")));
            return;
        }

        // Check wanted level requirement
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
        int minWantedLevel = itemsConfig.getInt("items.spyglass.min-wanted-level", 3);
        if (wantedLevel < minWantedLevel) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target must have at least " + minWantedLevel + " wanted stars to mark! (Current: " + wantedLevel + ")</red>")));
            return;
        }

        // Mark the player
        boolean success = plugin.getWantedLevelManager().markPlayer(target, guard);
        if (success) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Successfully marked " + target.getName() + " with spyglass! They will glow red for all guards.</green>")));
            
            // Set cooldown
            int cooldownTime = itemsConfig.getInt("items.spyglass.cooldown", 30);
            setCooldown(guard.getUniqueId(), "spyglass", cooldownTime);
        }
    }

    /**
     * Handle prison remote use
     */
    public void handlePrisonRemote(Player guard) {
        if (isOnCooldown(guard.getUniqueId(), "prison_remote")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "prison_remote");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to use this again!</red>")));
            return;
        }

        // Check if guard has sufficient rank
        if (!guard.hasPermission("edencorrections.rank.warden")) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be a Warden to use the prison remote!</red>")));
            return;
        }

        // Broadcast lockdown
        Component message = MessageUtils.parseMessage(
            "<red><bold>⚠ EMERGENCY LOCKDOWN ACTIVATED ⚠</bold></red>");
        Component detailMessage = MessageUtils.parseMessage(
            "<red>Warden " + guard.getName() + " has initiated a 30-second prison lockdown!</red>");
        
        Bukkit.broadcast(message);
        Bukkit.broadcast(detailMessage);

        // Apply lockdown effects to all online inmates (non-guards)
        int lockdownDuration = itemsConfig.getInt("items.prison-remote.lockdown-duration", 30);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("edencorrections.guard")) {
                // Apply slowness and weakness to simulate lockdown
                onlinePlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, lockdownDuration * 20, 2));
                onlinePlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, lockdownDuration * 20, 1));
                onlinePlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, lockdownDuration * 20, 0));
                
                onlinePlayer.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>You are under emergency lockdown for " + lockdownDuration + " seconds!</red>")));
            }
        }

        // Schedule lockdown end message
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Component endMessage = MessageUtils.parseMessage("<green>Emergency lockdown has ended. Normal operations resumed.</green>");
            Bukkit.broadcast(endMessage);
        }, lockdownDuration * 20L);

        // Record action and give rewards
        plugin.getGuardProgressionManager().addPoints(guard, 100, "Emergency lockdown activation");
        plugin.getGuardTokenManager().giveTokens(guard, 500, "Prison remote usage");

        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Emergency lockdown activated! +100 points, +500 tokens</green>")));

        // Set cooldown (20 minutes)
        setCooldown(guard.getUniqueId(), "prison_remote", itemsConfig.getInt("items.prison-remote.cooldown", 1200));
    }

    /**
     * Handle guard baton use
     */
    public void handleGuardBaton(Player guard, Player target) {
        // Check if PvP only is enabled and if in PvP region
        boolean pvpOnly = itemsConfig.getBoolean("items.guard-baton.pvp-only", true);
        if (pvpOnly && !plugin.getRegionUtils().isPvPRegion(target.getLocation())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard baton can only be used in PvP regions!</red>")));
            return;
        }

        // Check distance
        if (guard.getLocation().distance(target.getLocation()) > batonMaxDistance) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be closer to use the baton!</red>")));
            return;
        }

        // Apply slowness effect
        int slownessSeconds = itemsConfig.getInt("items.guard-baton.slowness-duration", 5);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessSeconds * 20, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, slownessSeconds * 20, 1));
        
        // Visual and sound effects
        target.getWorld().strikeLightningEffect(target.getLocation());
        
        // Messages
        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Applied enforcement effects to " + target.getName() + " for " + slownessSeconds + " seconds!</green>")));
        target.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<red>You've been struck by a guard baton! You feel weakened.</red>")));

        // Record action
        plugin.getGuardProgressionManager().addPoints(guard, 10, "Baton enforcement");
    }

    /**
     * Handle sobriety test use
     */
    public void handleSobrietyTest(Player guard, Player target) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return;
        }

        // Check safezone rules
        if (!plugin.getSafezoneManager().canPerformGuardAction(guard, target)) {
            String message = plugin.getSafezoneManager().getDenialMessage(guard, target);
            guard.sendMessage(MessageUtils.parseMessage(message));
            return;
        }

        // Check cooldown
        if (isOnCooldown(guard.getUniqueId(), "sobriety_test")) {
            int cooldownSeconds = getCooldownSeconds(guard.getUniqueId(), "sobriety_test");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Sobriety test is on cooldown for " + cooldownSeconds + " seconds!</red>")));
            return;
        }

        // Check if target is under influence
        boolean underInfluence = isUnderInfluence(target);
        
        if (underInfluence) {
            // Failed sobriety test
            int reward = itemsConfig.getInt("guard-tokens.earnings.sobriety-test-fail", 300);
            plugin.getGuardTokenManager().giveTokens(guard, reward, "Sobriety test failed by " + target.getName());
            
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Sobriety test failed! " + target.getName() + " is under the influence.</red>")));
            target.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You failed the sobriety test!</red>")));
            
            // Increase wanted level
            plugin.getWantedLevelManager().increaseWantedLevel(target, false);
            
            // Record statistics
            plugin.getGuardStatisticsManager().recordSuccessfulSearch(guard);
            
        } else {
            // Passed sobriety test
            int reward = itemsConfig.getInt("guard-tokens.earnings.sobriety-test-pass", 150);
            plugin.getGuardTokenManager().giveTokens(guard, reward, "Sobriety test passed by " + target.getName());
            
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Sobriety test passed! " + target.getName() + " is sober.</green>")));
            target.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>You passed the sobriety test.</green>")));
        }
        
        // Record the test
        plugin.getGuardStatisticsManager().recordSearch(guard);
        
        // Set cooldown
        int cooldownTime = itemsConfig.getInt("items.sobriety-test.cooldown", 60);
        setCooldown(guard.getUniqueId(), "sobriety_test", cooldownTime);
    }

    // Helper methods
    private boolean isUnderInfluence(Player player) {
        // Check ExecutableItems integration first (highest priority)
        if (plugin.getExternalPluginIntegration().isExecutableItemsEnabled()) {
            return plugin.getExternalPluginIntegration().isUnderInfluence(player);
        }
        
        // Fallback to checking for potion effects that might indicate drug use
        return player.getActivePotionEffects().stream()
            .anyMatch(effect -> {
                PotionEffectType type = effect.getType();
                return type == PotionEffectType.NAUSEA ||
                       type == PotionEffectType.BLINDNESS ||
                       type == PotionEffectType.SLOWNESS ||
                       type == PotionEffectType.WEAKNESS ||
                       type == PotionEffectType.HUNGER;
            });
    }

    private ItemStack findRandomContraband(Player player) {
        List<ItemStack> found = new ArrayList<>();
        
        // Check inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            
            // Use comprehensive contraband detection
            if (plugin.getExternalPluginIntegration().isContrabandComprehensive(item)) {
                found.add(item);
            }
        }
        
        if (found.isEmpty()) return null;
        return found.get(new java.util.Random().nextInt(found.size()));
    }

    private String getItemDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? 
            meta.getDisplayName() : 
            item.getType().toString().toLowerCase().replace('_', ' ');
    }

    /**
     * Create handcuffs item using configuration
     */
    public ItemStack createHandcuffs() {
        String materialName = itemsConfig.getString("handcuffs.material", "LEAD");
        String name = itemsConfig.getString("handcuffs.name", "§c§lHandcuffs");
        List<String> lore = itemsConfig.getStringList("handcuffs.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for handcuffs: " + materialName + ", using LEAD");
            material = Material.LEAD;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("handcuffs", name, material, loreArray);
    }

    /**
     * Create drug sniffer item using configuration
     */
    public ItemStack createDrugSniffer() {
        String materialName = itemsConfig.getString("drug-sniffer.material", "WARPED_FUNGUS_ON_A_STICK");
        String name = itemsConfig.getString("drug-sniffer.name", "§d§lDrug Sniffer");
        List<String> lore = itemsConfig.getStringList("drug-sniffer.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for drug sniffer: " + materialName + ", using WARPED_FUNGUS_ON_A_STICK");
            material = Material.WARPED_FUNGUS_ON_A_STICK;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("drug-sniffer", name, material, loreArray);
    }

    /**
     * Create metal detector item using configuration
     */
    public ItemStack createMetalDetector() {
        String materialName = itemsConfig.getString("metal-detector.material", "CLOCK");
        String name = itemsConfig.getString("metal-detector.name", "§8§lMetal Detector");
        List<String> lore = itemsConfig.getStringList("metal-detector.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for metal detector: " + materialName + ", using CLOCK");
            material = Material.CLOCK;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("metal-detector", name, material, loreArray);
    }

    /**
     * Create spyglass item using configuration
     */
    public ItemStack createSpyglass() {
        String materialName = itemsConfig.getString("spyglass.material", "SPYGLASS");
        String name = itemsConfig.getString("spyglass.name", "§e§lGuard Spyglass");
        List<String> lore = itemsConfig.getStringList("spyglass.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for spyglass: " + materialName + ", using SPYGLASS");
            material = Material.SPYGLASS;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("spyglass", name, material, loreArray);
    }

    /**
     * Create prison remote item using configuration
     */
    public ItemStack createPrisonRemote() {
        String materialName = itemsConfig.getString("prison-remote.material", "REDSTONE_TORCH");
        String name = itemsConfig.getString("prison-remote.name", "§4§lPrison Remote");
        List<String> lore = itemsConfig.getStringList("prison-remote.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for prison remote: " + materialName + ", using REDSTONE_TORCH");
            material = Material.REDSTONE_TORCH;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("prison-remote", name, material, loreArray);
    }

    /**
     * Create guard baton item using configuration
     */
    public ItemStack createGuardBaton() {
        String materialName = itemsConfig.getString("guard-baton.material", "STICK");
        String name = itemsConfig.getString("guard-baton.name", "§6§lGuard Baton");
        List<String> lore = itemsConfig.getStringList("guard-baton.lore");
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for guard baton: " + materialName + ", using STICK");
            material = Material.STICK;
        }
        
        String[] loreArray = lore.toArray(new String[0]);
        return createGuardItem("guard-baton", name, material, loreArray);
    }

    /**
     * Handle handcuffs item usage (called from PlayerInteractEntityEvent)
     * @param guard The player using handcuffs
     * @param target The target player being cuffed
     * @param handcuffs The handcuffs item stack
     * @return true if the action was handled, false otherwise
     */
    public boolean handleHandcuffsItem(Player guard, Player target, ItemStack handcuffs) {
        // Verify this is actually handcuffs
        if (!isGuardItem(handcuffs) || !"handcuffs".equals(getGuardItemType(handcuffs))) {
            return false;
        }
        
        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use handcuffs!</red>")));
            return true;
        }
        
        // Check if target is valid
        if (target == null || !target.isOnline() || target.equals(guard)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid target!</red>")));
            return true;
        }
        
        // Check distance
        double maxRange = itemsConfig.getDouble("items.handcuffs.range", 5.0);
        if (guard.getLocation().distance(target.getLocation()) > maxRange) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away!</red>")));
            return true;
        }
        
        // Use existing handcuffs logic
        handleHandcuffs(guard, target);
        return true;
    }

    /**
     * Handle spyglass item usage (called from PlayerInteractEntityEvent)
     * @param guard The player using spyglass
     * @param target The target player being marked
     * @param spyglass The spyglass item stack
     * @return true if the action was handled, false otherwise
     */
    public boolean handleSpyglassItem(Player guard, Player target, ItemStack spyglass) {
        // Verify this is actually spyglass
        if (!isGuardItem(spyglass) || !"spyglass".equals(getGuardItemType(spyglass))) {
            return false;
        }
        
        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use the spyglass!</red>")));
            return true;
        }
        
        // Check if target is valid
        if (target == null || !target.isOnline() || target.equals(guard)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Invalid target!</red>")));
            return true;
        }
        
        // Check distance
        double maxRange = itemsConfig.getDouble("items.spyglass.range", 10.0);
        if (guard.getLocation().distance(target.getLocation()) > maxRange) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away!</red>")));
            return true;
        }
        
        // Use spyglass logic
        handleSpyglass(guard, target);
        return true;
    }

    /**
     * Handle guard item usage (called from PlayerInteractEntityEvent)
     * @param guard The player using the item
     * @param target The target player
     * @param item The guard item being used
     * @return true if the action was handled, false otherwise
     */
    public boolean handleGuardItemUsage(Player guard, Player target, ItemStack item) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return false;
        }

        if (!canPerformGuardAction(guard, "item_usage")) {
            return false;
        }

        String itemType = getGuardItemType(item);
        if (itemType == null) {
            return false;
        }

        switch (itemType) {
            case "handcuffs" -> {
                return handleHandcuffsItem(guard, target, item);
            }
            case "spyglass" -> {
                return handleSpyglassItem(guard, target, item);
            }
            case "drug-sniffer" -> {
                handleDrugSniffer(guard, target);
                return true;
            }
            case "metal-detector" -> {
                handleMetalDetector(guard, target);
                return true;
            }
            case "guard-baton" -> {
                handleGuardBaton(guard, target);
                return true;
            }
            case "smoke_bomb" -> {
                handleSmokeBomb(guard, target);
                return true;
            }
            case "taser" -> {
                handleTaser(guard, target);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Handle guard item usage without target (for items that don't require a target)
     * @param guard The player using the item
     * @param item The guard item being used
     * @return true if the action was handled, false otherwise
     */
    public boolean handleGuardItemUsageNoTarget(Player guard, ItemStack item) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Guard systems are temporarily disabled.</red>")));
            return false;
        }

        if (!canPerformGuardAction(guard, "item_usage_no_target")) {
            return false;
        }

        String itemType = getGuardItemType(item);
        if (itemType == null) {
            return false;
        }

        switch (itemType) {
            case "prison-remote" -> {
                handlePrisonRemote(guard);
                return true;
            }
            case "riot_shield" -> {
                handleRiotShield(guard);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Give all basic guard items to a player based on their rank
     */
    public void giveBasicGuardItems(Player player) {
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank == null) {
            rank = "trainee"; // Default rank
        }

        // Basic items for all ranks
        player.getInventory().addItem(createHandcuffs());

        // Rank-specific items
        switch (rank.toLowerCase()) {
            case "trainee":
                // Trainee gets basic items only
                break;
            case "private":
                player.getInventory().addItem(createDrugSniffer());
                break;
            case "officer":
                player.getInventory().addItem(createDrugSniffer());
                player.getInventory().addItem(createMetalDetector());
                player.getInventory().addItem(createSpyglass());
                break;
            case "sergeant":
                player.getInventory().addItem(createDrugSniffer());
                player.getInventory().addItem(createMetalDetector());
                player.getInventory().addItem(createSpyglass());
                player.getInventory().addItem(createGuardBaton());
                break;
            case "captain":
                player.getInventory().addItem(createDrugSniffer());
                player.getInventory().addItem(createMetalDetector());
                player.getInventory().addItem(createSpyglass());
                player.getInventory().addItem(createGuardBaton());
                player.getInventory().addItem(createTaser());
                break;
            case "warden":
                player.getInventory().addItem(createDrugSniffer());
                player.getInventory().addItem(createMetalDetector());
                player.getInventory().addItem(createSpyglass());
                player.getInventory().addItem(createGuardBaton());
                player.getInventory().addItem(createTaser());
                player.getInventory().addItem(createPrisonRemote());
                break;
        }

        player.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>You have received guard items for rank: " + rank + "</green>")));
    }

    /**
     * Give a specific guard item to a player
     */
    public boolean giveGuardItem(Player player, String itemType) {
        ItemStack item = switch (itemType.toLowerCase()) {
            case "handcuffs", "cuffs" -> createHandcuffs();
            case "drug-sniffer", "sniffer", "drugs" -> createDrugSniffer();
            case "metal-detector", "detector", "metal" -> createMetalDetector();
            case "spyglass", "spy", "glass" -> createSpyglass();
            case "prison-remote", "remote" -> createPrisonRemote();
            case "guard-baton", "baton" -> createGuardBaton();
            default -> null;
        };

        if (item != null) {
            player.getInventory().addItem(item);
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>You have received a " + itemType + "!</green>")));
            return true;
        } else {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Unknown guard item type: " + itemType + "</red>")));
            return false;
        }
    }

    /**
     * Get configuration values for guard item ranges and cooldowns
     */
    private double getItemRange(String itemType) {
        return itemsConfig.getDouble("items." + itemType + ".range", 5.0);
    }

    private int getItemCooldown(String itemType) {
        return itemsConfig.getInt("items." + itemType + ".cooldown", 30);
    }

    private int getItemCountdown(String itemType) {
        return itemsConfig.getInt("items." + itemType + ".countdown", 5);
    }

    /**
     * Handle smoke bomb use
     */
    public void handleSmokeBomb(Player guard, Player target) {
        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use smoke bombs!</red>")));
            return;
        }

        // Get config values
        int blackoutDuration = itemsConfig.getInt("shop.smoke-bomb.effects.blackout-duration", 15);
        int darknessDuration = itemsConfig.getInt("shop.smoke-bomb.effects.darkness-duration", 30);
        int range = itemsConfig.getInt("shop.smoke-bomb.effects.range", 5);

        // Apply effects to nearby players
        Location center = target.getLocation();
        int affected = 0;
        
        for (Player nearby : center.getNearbyPlayers(range)) {
            if (nearby.equals(guard)) continue; // Don't affect the guard
            
            // Apply blindness and slowness
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blackoutDuration * 20, 0));
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessDuration * 20, 0));
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, blackoutDuration * 20, 1));
            
            nearby.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You've been hit by a smoke bomb!</red>")));
            affected++;
        }

        // Notify guard
        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Smoke bomb deployed! Affected " + affected + " players.</green>")));
        
        // Visual effect - spawn smoke particles if possible
        center.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, center, 100, 3, 2, 3, 0.1);
        
        // Sound effect
        center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_TNT_PRIMED, 0.5f, 2.0f);
    }

    /**
     * Handle taser use
     */
    public void handleTaser(Player guard, Player target) {
        if (isOnCooldown(guard.getUniqueId(), "taser")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "taser");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Taser is recharging! " + remaining + " seconds remaining.</red>")));
            return;
        }

        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use the taser!</red>")));
            return;
        }

        // Check distance
        if (guard.getLocation().distance(target.getLocation()) > taserMaxDistance) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Target is too far away for taser!</red>")));
            return;
        }

        // Get config values
        double stunDuration = itemsConfig.getDouble("shop.taser.stun-duration", 2.5);
        int droppedCharges = itemsConfig.getInt("shop.taser.dropped-charges", 3);

        // Apply stun effects
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(stunDuration * 20), 255));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, (int)(stunDuration * 20), 255));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (int)(stunDuration * 20), 128)); // Negative jump
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int)(stunDuration * 20), 1));

        // Visual and sound effects
        target.getLocation().getWorld().strikeLightningEffect(target.getLocation());
        target.getLocation().getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 2.0f);
        
        // Drop some charges (simulating taser depletion)
        Location dropLoc = target.getLocation();
        for (int i = 0; i < droppedCharges; i++) {
            ItemStack charge = new ItemStack(Material.REDSTONE, 1);
            ItemMeta meta = charge.getItemMeta();
            meta.setDisplayName("§6Taser Charge");
            charge.setItemMeta(meta);
            dropLoc.getWorld().dropItemNaturally(dropLoc, charge);
        }

        // Messages
        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Taser deployed successfully! Target stunned for " + stunDuration + " seconds.</green>")));
        target.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<red>You've been tased by " + guard.getName() + "! You are stunned!</red>")));

        // Set cooldown (2 minutes)
        setCooldown(guard.getUniqueId(), "taser", 120);

        // Record the action
        plugin.getDutyManager().recordApprehension(guard);
    }

    /**
     * Create smoke bomb item
     */
    public ItemStack createSmokeBomb() {
        return createGuardItem("smoke_bomb", "§8§lSmoke Bomb", Material.FIRE_CHARGE,
            "§7Throw to create a smoke cloud",
            "§7Blinds nearby enemies",
            "§7Range: 5 blocks",
            "",
            "§8§lConsumable Item");
    }

    /**
     * Create taser item
     */
    public ItemStack createTaser() {
        return createGuardItem("taser", "§e§lTaser", Material.TRIPWIRE_HOOK,
            "§7Right-click to stun a target",
            "§7Causes temporary paralysis",
            "§7Range: 8 blocks",
            "§7Cooldown: 2 minutes",
            "",
            "§e§lGuard Equipment");
    }

    /**
     * Handle riot shield use
     */
    public void handleRiotShield(Player guard) {
        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guard.getUniqueId())) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use riot equipment!</red>")));
            return;
        }

        // Apply temporary protection effects
        guard.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 60, 1)); // 1 minute
        guard.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60, 0)); // 1 minute
        
        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Riot shield activated! Enhanced protection for 1 minute.</green>")));
    }

    /**
     * Enhanced guard item creation with rank restrictions
     */
    public ItemStack createRankRestrictedItem(String type, String rank) {
        return switch (type.toLowerCase()) {
            case "handcuffs" -> createHandcuffs();
            case "spyglass" -> createSpyglass();
            case "smoke_bomb" -> createSmokeBomb();
            case "taser" -> createTaser();
            case "riot_shield" -> createGuardItem("riot_shield", "§c§lRiot Shield", Material.SHIELD,
                "§7Right-click to activate protection",
                "§7Grants resistance effects",
                "§7Duration: 1 minute",
                "", 
                "§c§lRank: " + rank.toUpperCase());
            default -> null;
        };
    }

    /**
     * Check if a target can be jailed (prevents issues with OP players, etc.)
     */
    private boolean canJailTarget(Player guard, Player target) {
        // Check if target is OP (OPs typically can't be jailed)
        if (target.isOp()) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Cannot jail " + target.getName() + " - they have operator privileges!</red>")));
            return false;
        }

        // Check if target has jail bypass permission
        if (target.hasPermission("edencorrections.jail.bypass") || target.hasPermission("cmi.command.jail.bypass")) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Cannot jail " + target.getName() + " - they have jail bypass permission!</red>")));
            return false;
        }

        // Check if target is another guard (optional protection)
        if (plugin.getDutyManager().isOnDuty(target.getUniqueId()) && 
            itemsConfig.getBoolean("items.handcuffs.protect-guards", true)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Cannot jail " + target.getName() + " - they are a fellow guard on duty!</red>")));
            return false;
        }

        // Check if target is in a safe region
        List<String> safeRegions = itemsConfig.getStringList("items.handcuffs.safe-regions");
        if (!safeRegions.isEmpty()) {
            for (String region : safeRegions) {
                if (plugin.getRegionUtils().isPlayerInRegion(target, region)) {
                    guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Cannot jail " + target.getName() + " - they are in a protected area!</red>")));
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Check if a player can perform guard actions
     */
    private boolean canPerformGuardAction(Player guard, String actionType) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            return false;
        }

        UUID guardId = guard.getUniqueId();
        
        // General action cooldown (prevents rapid-fire usage of any guard items)
        String generalKey = "general_action";
        if (isOnCooldown(guardId, generalKey)) {
            // Don't send message for general cooldown to avoid spam
            return false;
        }
        
        // Set a short general cooldown (1 second) to prevent rapid clicking
        setCooldown(guardId, generalKey, 1);
        
        // Check if guard is on duty
        if (!plugin.getDutyManager().isOnDuty(guardId)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be on duty to use guard items!</red>")));
            return false;
        }
        
        // Check if player is immobilized
        if (plugin.getDutyManager().isPlayerImmobilized(guardId)) {
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(guard);
            return false;
        }
        
        return true;
    }

    /**
     * Give handcuffs to a player (for admin commands)
     */
    public boolean giveHandcuffs(Player player) {
        try {
            ItemStack handcuffs = createHandcuffs();
            player.getInventory().addItem(handcuffs);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give handcuffs to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Give spyglass to a player (for admin commands)
     */
    public boolean giveSpyglass(Player player) {
        try {
            ItemStack spyglass = createSpyglass();
            player.getInventory().addItem(spyglass);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give spyglass to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
} 