package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
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
        this.itemTypeKey = new NamespacedKey(plugin, "guard_item_type");
        
        // Cache configuration values for better performance
        this.cuffingMaxDistance = plugin.getConfig().getDouble("items.handcuffs.max-distance", 5.0);
        this.batonMaxDistance = plugin.getConfig().getDouble("items.guard-baton.max-distance", 3.0);
        this.taserMaxDistance = plugin.getConfig().getDouble("items.taser.max-distance", 8.0);
        this.jailMaxDistance = plugin.getConfig().getDouble("commands.jail.max-distance", 5.0);
        this.cmiJailName = plugin.getConfig().getString("jail.cmi-jail-name", "default");
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
        if (!playerCooldowns.containsKey(itemType)) return false;
        
        return System.currentTimeMillis() < playerCooldowns.get(itemType);
    }

    /**
     * Get remaining cooldown in seconds
     */
    private int getCooldownSeconds(UUID playerId, String itemType) {
        if (!cooldowns.containsKey(playerId)) return 0;
        
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (!playerCooldowns.containsKey(itemType)) return 0;
        
        long remaining = playerCooldowns.get(itemType) - System.currentTimeMillis();
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
            
            // Start chase if configured
            plugin.getChaseManager().startChase(guard, target);
        }
    }

    /**
     * Handle drug sniffer use
     */
    public void handleDrugSniffer(Player guard, Player target) {
        if (isOnCooldown(guard.getUniqueId(), "drug_sniffer")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "drug_sniffer");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to use this again!</red>")));
            return;
        }

        // Set cooldown immediately to prevent spam
        int drugSnifferCooldown = plugin.getConfig().getInt("items.drug-sniffer.cooldown", 30);
        setCooldown(guard.getUniqueId(), "drug_sniffer", drugSnifferCooldown);

        // Start search countdown (5 seconds)
        int countdown = plugin.getConfig().getInt("items.drug-sniffer.countdown", 5);
        startSearchCountdown(guard, target, countdown, "drug_sniffer", () -> {
            // Automatically record the search attempt
            plugin.getDutyManager().recordSearch(guard);
            
            // Search for drugs in target's inventory
            boolean foundDrugs = false;
            int drugsFound = 0;
            
            for (ItemStack item : target.getInventory().getContents()) {
                if (item != null && plugin.getExternalPluginIntegration().isDrugComprehensive(item)) {
                    drugsFound++;
                    target.getInventory().remove(item);
                    foundDrugs = true;
                }
            }
            
            // If drugs were found, record successful search
            if (foundDrugs) {
                plugin.getDutyManager().recordSuccessfulSearch(guard);
                
                int rewardPerDrug = plugin.getConfig().getInt("items.drug-sniffer.reward.per-drug", 50);
                int totalReward = drugsFound * rewardPerDrug;
                plugin.getGuardTokenManager().giveTokens(guard, totalReward, "Drug sniffer reward");
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Found " + drugsFound + " drugs! +" + totalReward + " tokens</green>")));

                // Award off-duty time
                plugin.getDutyManager().addOffDutyMinutes(guard.getUniqueId(), drugsFound);
            } else {
                int noFindReward = plugin.getConfig().getInt("items.drug-sniffer.reward.no-find", 250);
                plugin.getGuardTokenManager().giveTokens(guard, noFindReward, "Drug sniffer reward");
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>No drugs found. +" + noFindReward + " tokens</yellow>")));
            }
        });
    }

    /**
     * Handle handcuffs use (cuffing logic with CMI jail integration)
     */
    public void handleHandcuffs(Player guard, Player target) {
        if (isOnCooldown(guard.getUniqueId(), "handcuffs")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "handcuffs");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to use this again!</red>")));
            return;
        }

        // Check if target can be jailed (not OP, not in protected mode, etc.)
        if (!canJailTarget(guard, target)) {
            return;
        }

        // Set cooldown immediately to prevent spam (even if action fails)
        int handcuffsCooldown = plugin.getConfig().getInt("items.handcuffs.cooldown", 30);
        setCooldown(guard.getUniqueId(), "handcuffs", handcuffsCooldown);

        // If already in chase, allow instant jail
        if (plugin.getChaseManager().isBeingChased(target)) {
            jailWithCMI(guard, target);
            return;
        }

        // Start cuffing countdown (5 seconds, or configurable)
        int countdown = plugin.getConfig().getInt("items.handcuffs.countdown", 5);
        startCuffingCountdown(guard, target, countdown, () -> jailWithCMI(guard, target));
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
                    plugin.getChaseManager().startChase(guard, target);
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
     */
    private void jailWithCMI(Player guard, Player target) {
        // Double-check if target can still be jailed
        if (!canJailTarget(guard, target)) {
            return;
        }

        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(target.getUniqueId());
        double jailTime = plugin.getWantedLevelManager().getJailTime(wantedLevel);
        String reason = "Arrested by guard " + guard.getName();
        
        // Automatically record the apprehension action  
        plugin.getDutyManager().recordApprehension(guard);
        
        try {
            // Use CMI to jail the player
            String cmiJailCommand = String.format("cmi jail %s %s %d %s", 
                target.getName(), 
                plugin.getConfig().getString("jail.cmi-jail-name", "default"), 
                (int)jailTime, 
                reason);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Executing CMI jail command: " + cmiJailCommand);
            }
            
            boolean commandSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmiJailCommand);
            
            if (!commandSuccess) {
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Failed to jail " + target.getName() + " - CMI jail command failed!</red>")));
                return;
            }
            
            // Jail the player using our own manager as well for tracking
            plugin.getJailManager().jailPlayer(target, jailTime, "Apprehended by guard");

            // Give rewards, tokens, etc.
            int baseReward = plugin.getConfig().getInt("items.handcuffs.reward.base", 250);
            int bonusPerLevel = plugin.getConfig().getInt("items.handcuffs.reward.per-level", 150);
            int totalReward = baseReward + (wantedLevel * bonusPerLevel);
            plugin.getGuardTokenManager().giveTokens(guard, totalReward, "Apprehension reward");
            plugin.getDutyManager().addOffDutyMinutes(guard.getUniqueId(), 1 + wantedLevel);
            
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Successfully jailed " + target.getName() + " for " + jailTime + " minutes! +" + totalReward + " tokens</green>")));
                
            // Notify other guards
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getDutyManager().isOnDuty(onlinePlayer.getUniqueId()) && !onlinePlayer.equals(guard)) {
                    onlinePlayer.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<yellow>Guard " + guard.getName() + " has jailed " + target.getName() + "</yellow>")));
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error executing jail command for " + target.getName() + ": " + e.getMessage());
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Failed to jail " + target.getName() + " - an error occurred!</red>")));
        }
    }

    /**
     * Handle metal detector use
     */
    public void handleMetalDetector(Player guard, Player target) {
        if (!guard.hasPermission("edencorrections.rank.officer")) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be an Officer or higher to use the metal detector!</red>")));
            return;
        }

        if (isOnCooldown(guard.getUniqueId(), "metal_detector")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "metal_detector");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to use this again!</red>")));
            return;
        }

        // Check player-specific cooldown
        String cooldownKey = "metal_detector_" + target.getUniqueId();
        if (isOnCooldown(guard.getUniqueId(), cooldownKey)) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), cooldownKey);
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to search this player again!</red>")));
            return;
        }

        // Start search countdown (10 seconds)
        startSearchCountdown(guard, target, 10, "metal_detector", () -> {
            // Automatically record the metal detection action
            plugin.getDutyManager().recordMetalDetect(guard);
            
            // Find random contraband
            ItemStack contraband = findRandomContraband(target);
            
            if (contraband != null) {
                // Ask player to drop item
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Metal detector found: " + getItemDisplayName(contraband) + 
                        ". Please drop this item for the guard!</red>")));
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Found contraband! Player has been asked to drop: " + 
                        getItemDisplayName(contraband) + "</green>")));
                
                // Give rewards
                plugin.getGuardTokenManager().giveTokens(guard, 250, "Metal detector reward");
                plugin.getGuardTokenManager().giveTokens(target, 250, "Cooperation reward");
                
                // Set player-specific cooldown (30 minutes)
                setCooldown(guard.getUniqueId(), cooldownKey, 1800);
            } else {
                // No contraband found
                plugin.getGuardTokenManager().giveTokens(guard, 250, "Metal detector reward");
                
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>No contraband found. +250 tokens</yellow>")));
            }
            
            // Set general cooldown (10 seconds)
            setCooldown(guard.getUniqueId(), "metal_detector", 10);
        });
    }

    /**
     * Handle spyglass use (mark wanted players for all guards to see)
     */
    public void handleSpyglass(Player guard, Player target) {
        if (isOnCooldown(guard.getUniqueId(), "spyglass")) {
            int remaining = getCooldownSeconds(guard.getUniqueId(), "spyglass");
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must wait " + remaining + " seconds to use this again!</red>")));
            return;
        }

        // Check if target is valid
        if (target.equals(guard)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You cannot mark yourself!</red>")));
            return;
        }

        // Use WantedLevelManager to mark the player
        boolean success = plugin.getWantedLevelManager().markPlayer(target, guard);
        
        if (success) {
            // Set cooldown
            int cooldown = plugin.getConfig().getInt("items.spyglass.cooldown", 30);
            setCooldown(guard.getUniqueId(), "spyglass", cooldown);
            
            // Award progression points
            plugin.getGuardProgressionManager().addPoints(guard, 25, "Marked wanted criminal");
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
        if (!guard.hasPermission("edencorrections.rank.captain")) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be a Captain to use the prison remote!</red>")));
            return;
        }

        // Broadcast lockdown
        Component message = MessageUtils.parseMessage(
            "<red><bold>⚠ EMERGENCY LOCKDOWN ACTIVATED ⚠</bold></red>");
        Component detailMessage = MessageUtils.parseMessage(
            "<red>Captain " + guard.getName() + " has initiated a 30-second prison lockdown!</red>");
        
        Bukkit.broadcast(message);
        Bukkit.broadcast(detailMessage);

        // Apply lockdown effects to all online inmates (non-guards)
        int lockdownDuration = plugin.getConfig().getInt("items.prison-remote.lockdown-duration", 30);
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
        setCooldown(guard.getUniqueId(), "prison_remote", plugin.getConfig().getInt("items.prison-remote.cooldown", 1200));
    }

    /**
     * Handle guard baton use
     */
    public void handleGuardBaton(Player guard, Player target) {
        // Check if PvP only is enabled and if in PvP region
        boolean pvpOnly = plugin.getConfig().getBoolean("items.guard-baton.pvp-only", true);
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
        int slownessSeconds = plugin.getConfig().getInt("items.guard-baton.slowness-duration", 5);
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
        // Check if player is under influence
        if (isUnderInfluence(target)) {
            // Give wanted level and start chase
            plugin.getWantedLevelManager().increaseWantedLevel(target, false);
            plugin.getChaseManager().startChase(guard, target);
            
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>" + target.getName() + " is under the influence!</green>")));
        } else {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>" + target.getName() + " is not under the influence.</yellow>")));
        }
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
     * Create handcuffs guard item with configurable properties
     */
    public ItemStack createHandcuffs() {
        String materialName = plugin.getConfig().getString("items.handcuffs.material", "TRIPWIRE_HOOK");
        String name = plugin.getConfig().getString("items.handcuffs.name", "§c§lHandcuffs");
        List<String> loreConfig = plugin.getConfig().getStringList("items.handcuffs.lore");
        
        // Default lore if not configured
        if (loreConfig.isEmpty()) {
            loreConfig = List.of(
                "§7Right-click on a player to cuff them",
                "§7Hold still during the countdown or they'll escape!",
                "§c§lGuard Item"
            );
        }
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid handcuffs material '" + materialName + "', using TRIPWIRE_HOOK");
            material = Material.TRIPWIRE_HOOK;
        }
        
        return createGuardItem("handcuffs", name, material, loreConfig.toArray(new String[0]));
    }

    /**
     * Create spyglass guard item with configurable properties
     */
    public ItemStack createSpyglass() {
        String materialName = plugin.getConfig().getString("items.spyglass.material", "SPYGLASS");
        String name = plugin.getConfig().getString("items.spyglass.name", "§e§lGuard Spyglass");
        List<String> loreConfig = plugin.getConfig().getStringList("items.spyglass.lore");
        
        // Default lore if not configured
        if (loreConfig.isEmpty()) {
            int minWantedLevel = plugin.getConfig().getInt("items.spyglass.min-wanted-level", 3);
            loreConfig = List.of(
                "§7Right-click on a player to mark them",
                "§7Target must have " + minWantedLevel + "+ wanted stars",
                "§7Marked players glow red for all guards",
                "§7Mark persists until death/jail/wanted cleared",
                "",
                "§e§lGuard Item"
            );
        }
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid spyglass material '" + materialName + "', using SPYGLASS");
            material = Material.SPYGLASS;
        }
        
        return createGuardItem("spyglass", name, material, loreConfig.toArray(new String[0]));
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
        double maxRange = plugin.getConfig().getDouble("items.handcuffs.range", 5.0);
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
        double maxRange = plugin.getConfig().getDouble("items.spyglass.range", 10.0);
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
        // Check if this is a guard item
        if (!isGuardItem(item)) {
            return false;
        }

        // Get the guard item type
        String itemType = getGuardItemType(item);
        if (itemType == null) {
            return false;
        }

        // Apply general anti-spam protection
        if (!canPerformGuardAction(guard, itemType)) {
            return true; // Action was handled (but blocked)
        }

        // Handle specific guard items
        switch (itemType) {
            case "handcuffs":
                return handleHandcuffsItem(guard, target, item);
            case "spyglass":
                return handleSpyglassItem(guard, target, item);
            case "taser":
                handleTaser(guard, target);
                return true;
            case "smoke_bomb":
                handleSmokeBomb(guard, target);
                return true;
            case "drug_sniffer":
                handleDrugSniffer(guard, target);
                return true;
            case "metal_detector":
                handleMetalDetector(guard, target);
                return true;
            // Future guard items can be added here
            default:
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This guard item is not yet implemented!</red>")));
                return false;
        }
    }

    /**
     * Handle guard item usage without target (for items that don't require a target)
     * @param guard The player using the item
     * @param item The guard item being used
     * @return true if the action was handled, false otherwise
     */
    public boolean handleGuardItemUsageNoTarget(Player guard, ItemStack item) {
        // Check if this is a guard item
        if (!isGuardItem(item)) {
            return false;
        }

        // Get the guard item type
        String itemType = getGuardItemType(item);
        if (itemType == null) {
            return false;
        }

        // Apply general anti-spam protection
        if (!canPerformGuardAction(guard, itemType)) {
            return true; // Action was handled (but blocked)
        }

        // Handle specific guard items that don't require targets
        switch (itemType) {
            case "riot_shield":
                handleRiotShield(guard);
                return true;
            case "prison_remote":
                handlePrisonRemote(guard);
                return true;
            case "drug_sniffer":
                // This needs a target, so return false
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This item requires a target! Right-click on a player.</red>")));
                return false;
            case "metal_detector":
                // This needs a target, so return false
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>This item requires a target! Right-click on a player.</red>")));
                return false;
            default:
                return false;
        }
    }

    /**
     * Give handcuffs to a player
     * @param player The player to give handcuffs to
     * @return true if successful, false if inventory is full
     */
    public boolean giveHandcuffs(Player player) {
        ItemStack handcuffs = createHandcuffs();
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(handcuffs);
            return true;
        }
        return false;
    }

    /**
     * Give spyglass to a player
     * @param player The player to give spyglass to
     * @return true if successful, false if inventory is full
     */
    public boolean giveSpyglass(Player player) {
        ItemStack spyglass = createSpyglass();
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(spyglass);
            return true;
        }
        return false;
    }

    /**
     * Give basic guard items to a player based on their rank
     * @param player The player to give items to
     */
    public void giveBasicGuardItems(Player player) {
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        if (rank == null) {
            return;
        }

        // Get available tools for the rank
        List<String> availableTools = plugin.getConfig().getStringList("guard-progression.perks." + rank.toLowerCase() + ".can-use");
        
        if (availableTools.contains("handcuffs")) {
            if (giveHandcuffs(player)) {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Received handcuffs!</green>")));
            } else {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Your inventory is full! Could not give handcuffs.</red>")));
            }
        }

        if (availableTools.contains("spyglass")) {
            if (giveSpyglass(player)) {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>Received spyglass!</green>")));
            } else {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Your inventory is full! Could not give spyglass.</red>")));
            }
        }

        // Add other guard items as they're implemented
        // if (availableTools.contains("taser")) {
        //     giveTaser(player);
        // }
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
        int blackoutDuration = plugin.getConfig().getInt("shop.smoke-bomb.effects.blackout-duration", 15);
        int darknessDuration = plugin.getConfig().getInt("shop.smoke-bomb.effects.darkness-duration", 30);
        int range = plugin.getConfig().getInt("shop.smoke-bomb.effects.range", 5);

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
        double stunDuration = plugin.getConfig().getDouble("shop.taser.stun-duration", 2.5);
        int droppedCharges = plugin.getConfig().getInt("shop.taser.dropped-charges", 3);

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
            plugin.getConfig().getBoolean("items.handcuffs.protect-guards", true)) {
            guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Cannot jail " + target.getName() + " - they are a fellow guard on duty!</red>")));
            return false;
        }

        // Check if target is in a safe region
        List<String> safeRegions = plugin.getConfig().getStringList("items.handcuffs.safe-regions");
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
     * Check if a guard item action can be performed (general anti-spam protection)
     */
    private boolean canPerformGuardAction(Player guard, String actionType) {
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
} 