package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GuardListener implements Listener {
    private final EdenCorrections plugin;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastActionType = new ConcurrentHashMap<>();
    
    private static final long MESSAGE_COOLDOWN = 1500; // 1.5 seconds
    private static final long INTERACTION_COOLDOWN = 1000; // 1 second global interaction cooldown
    private static final long SAME_ACTION_COOLDOWN = 2000; // 2 seconds for same action type

    public GuardListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    // Death Events
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        // Handle wanted level system (remove marks, etc.)
        plugin.getWantedLevelManager().handlePlayerDeath(victim);

        // Check if victim is a guard on duty
        if (!plugin.getDutyManager().isOnDuty(victimId)) {
            return;
        }

        // Protect guard items from dropping
        event.getDrops().removeIf(itemStack -> {
            if (itemStack == null || itemStack.getItemMeta() == null) {
                return false;
            }
            
            // Check display name for "Guard Item"
            Component displayName = itemStack.getItemMeta().displayName();
            if (displayName != null) {
                String displayText = PlainTextComponentSerializer.plainText().serialize(displayName).toLowerCase();
                if (displayText.contains("guard item")) {
                    return true;
                }
            }
            
            // Check lore for "Guard Item"
            if (itemStack.getItemMeta().hasLore()) {
                for (Component loreLine : itemStack.getItemMeta().lore()) {
                    if (loreLine != null) {
                        String loreText = PlainTextComponentSerializer.plainText().serialize(loreLine).toLowerCase();
                        if (loreText.contains("guard item")) {
                            return true;
                        }
                    }
                }
            }
            
            return false;
        });

        // Process guard death
        plugin.getGuardLootManager().handleGuardDeath(victim, victim.getKiller());

        // Apply death penalties if enabled
        if (plugin.getConfig().getBoolean("guard-death-penalties.enabled", true)) {
            plugin.getGuardPenaltyManager().applyDeathPenalty(victim);
        }

        // Recalculate lone guard status
        plugin.getGuardBuffManager().recalculateLoneGuardStatus();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has active death penalty
        if (plugin.getDutyManager().isOnDuty(playerId) &&
                plugin.getGuardPenaltyManager().hasActivePenalty(playerId)) {
            // Schedule reminder message
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                int remainingSeconds = plugin.getGuardPenaltyManager().getRemainingPenaltyTime(playerId);
                if (remainingSeconds <= 0) return;

                String message = plugin.getConfig().getString("guard-death-penalties.message",
                        "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <gray>You cannot leave for <red>{time} seconds</red> for dying!</gray>");

                message = message.replace("{time}", String.valueOf(remainingSeconds));
                player.sendMessage(MessageUtils.parseMessage(message));
            }, 20L);
        }
    }

    // Movement Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if block position changed
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(playerId)) {
            return;
        }

        // Check immobilization
        if (plugin.getDutyManager().isPlayerImmobilized(playerId)) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
            return;
        }

        // Check chase restrictions
        if (plugin.getChaseManager().isBeingChased(player)) {
            for (String region : RegionUtils.getRegionsAtLocation(event.getTo())) {
                if (plugin.getChaseManager().isRegionRestricted(region)) {
                    event.setCancelled(true);
                    sendCooldownMessage(player, "§cYou cannot enter this area while being chased!");
                    return;
                }
            }
        }

        // Check penalty restrictions
        if (plugin.getGuardPenaltyManager().isPlayerLocked(playerId)) {
            boolean isLeavingRestricted = true;
            for (String region : plugin.getGuardPenaltyManager().getRestrictedRegions()) {
                if (RegionUtils.isLocationInRegion(event.getTo(), region)) {
                    isLeavingRestricted = false;
                    break;
                }
            }

            if (isLeavingRestricted) {
                event.setCancelled(true);
                plugin.getGuardPenaltyManager().handleRestrictedRegionExit(player);
                return;
            }
        }

        // Check general movement restrictions
        for (String region : RegionUtils.getRegionsAtLocation(event.getTo())) {
            if (plugin.getGuardRestrictionManager().isRegionRestricted(region)) {
                event.setCancelled(true);
                sendCooldownMessage(player, plugin.getGuardRestrictionManager().getMovementRestrictionMessage());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDutyManager().isPlayerImmobilized(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
        }
    }

    // Command Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            return;
        }

        // Check if being chased
        if (plugin.getChaseManager().isBeingChased(player)) {
            String command = event.getMessage().substring(1).toLowerCase();
            if (plugin.getChaseManager().isCommandRestricted(command)) {
                event.setCancelled(true);
                sendCooldownMessage(player, "§cYou cannot use this command while being chased!");
                return;
            }
        }

        // Check general command restrictions
        String baseCommand = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (plugin.getGuardRestrictionManager().isCommandRestricted(baseCommand)) {
            event.setCancelled(true);
            sendCooldownMessage(player, plugin.getGuardRestrictionManager().getCommandRestrictionMessage());
        }
    }

    // Block Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Check if player is a guard and the block is restricted
        if (plugin.getGuardRestrictionManager().isPlayerGuard(player) && 
            plugin.getGuardRestrictionManager().isBlockRestricted(event.getBlock(), player)) {
            event.setCancelled(true);
            plugin.getGuardRestrictionManager().handleRestrictedBlockBreak(player);
        }
    }

    // Join/Quit Events
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && plugin.getGuardBuffManager().isPlayerGuard(player)) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Guard " + player.getName() + " joined - buffs will activate when they go on duty");
                    }
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Handle guard buffs
        if (plugin.getGuardBuffManager().isPlayerGuard(player)) {
            plugin.getGuardBuffManager().onGuardQuit(player);
        }

        // Handle chase logout
        if (plugin.getChaseManager().isBeingChased(player)) {
            Player guard = plugin.getChaseManager().getChasingGuard(player);
            if (guard != null) {
                plugin.getJailManager().jailOfflinePlayer(
                    playerId,
                    plugin.getWantedLevelManager().getJailTime(
                        plugin.getWantedLevelManager().getWantedLevel(playerId)
                    ),
                    "Disconnected during chase",
                    guard
                );
                guard.sendMessage(MessageUtils.parseMessage("<green>" + player.getName() + " logged out during chase and will be jailed when they return.</green>"));
            }
            plugin.getChaseManager().endChase(player, true);
        }

        // Clean up spam protection tracking to prevent memory leaks
        lastMessageTime.remove(playerId);
        lastInteractionTime.remove(playerId);
        lastActionType.remove(playerId);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Cleaned up spam protection data for " + player.getName());
        }
    }

    // Combat Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check immobilization
        if (plugin.getDutyManager().isPlayerImmobilized(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(attacker);
            return;
        }

        if (plugin.getDutyManager().isPlayerImmobilized(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("§c§lYou cannot attack a guard who is preparing for duty!");
        }
    }

    // Interaction Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // CRITICAL: Universal spam protection - prevents ALL rapid interactions
        Long lastInteraction = lastInteractionTime.get(playerId);
        if (lastInteraction != null && now - lastInteraction < INTERACTION_COOLDOWN) {
            event.setCancelled(true);
            return; // Block ALL rapid interactions
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if it's a guard item
        if (plugin.getGuardItemManager().isGuardItem(item)) {
            String itemType = plugin.getGuardItemManager().getGuardItemType(item);
            if (itemType == null) {
                event.setCancelled(true);
                return;
            }
            
            // ENHANCED: Same action type spam protection
            String lastAction = lastActionType.get(playerId);
            if (itemType.equals(lastAction)) {
                Long lastSameAction = lastInteractionTime.get(playerId);
                if (lastSameAction != null && now - lastSameAction < SAME_ACTION_COOLDOWN) {
                    event.setCancelled(true);
                    sendCooldownMessage(player, "<red>" + formatItemName(itemType) + " is on cooldown! Wait " + 
                        ((SAME_ACTION_COOLDOWN - (now - lastSameAction)) / 1000) + " more seconds.</red>");
                    return;
                }
            }
            
            // Cancel the event BEFORE processing to prevent multiple triggers
            event.setCancelled(true);
            
            // Update interaction tracking
            lastInteractionTime.put(playerId, now);
            lastActionType.put(playerId, itemType);
            
            // Check if player is immobilized (BEFORE any processing)
            if (plugin.getDutyManager().isPlayerImmobilized(playerId)) {
                plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
                return;
            }
            
            // Process guard item interaction
            if (event.getRightClicked() instanceof Player) {
                Player target = (Player) event.getRightClicked();
                
                // Comprehensive target validation
                if (!isValidTarget(player, target)) {
                    return;
                }
                
                // Check if guard is on duty for guard items
                if (!plugin.getDutyManager().isOnDuty(playerId)) {
                    sendCooldownMessage(player, "<red>You must be on duty to use guard items!</red>");
                    return;
                }
                
                // Process the guard item usage (with built-in cooldowns)
                boolean handled = plugin.getGuardItemManager().handleGuardItemUsage(player, target, item);
                if (!handled) {
                    sendCooldownMessage(player, "<red>This guard item cannot be used on players!</red>");
                }
            } else {
                // No target required for some guard items
                if (!plugin.getDutyManager().isOnDuty(playerId)) {
                    sendCooldownMessage(player, "<red>You must be on duty to use guard items!</red>");
                    return;
                }
                
                boolean handled = plugin.getGuardItemManager().handleGuardItemUsageNoTarget(player, item);
                if (!handled) {
                    sendCooldownMessage(player, "<red>This guard item requires a target! Right-click on a player.</red>");
                }
            }
        } else {
            // Non-guard item interaction - still apply basic spam protection
            lastInteractionTime.put(playerId, now);
        }
    }
    
    /**
     * ENHANCED: Comprehensive target validation
     */
    private boolean isValidTarget(Player player, Player target) {
        // Null check
        if (target == null) {
            sendCooldownMessage(player, "<red>Invalid target!</red>");
            return false;
        }
        
        // Online check
        if (!target.isOnline()) {
            sendCooldownMessage(player, "<red>Target is not online!</red>");
            return false;
        }
        
        // Self-targeting check
        if (target.equals(player)) {
            sendCooldownMessage(player, "<red>You cannot target yourself!</red>");
            return false;
        }
        
        // Distance check (universal max range)
        double distance = player.getLocation().distance(target.getLocation());
        if (distance > 10.0) { // Universal max range for all guard items
            sendCooldownMessage(player, "<red>Target is too far away!</red>");
            return false;
        }
        
        return true;
    }
    
    /**
     * ENHANCED: Format item names for user-friendly messages
     */
    private String formatItemName(String itemType) {
        return switch (itemType.toLowerCase()) {
            case "handcuffs" -> "Handcuffs";
            case "drug-sniffer" -> "Drug Sniffer";
            case "metal-detector" -> "Metal Detector";
            case "spyglass" -> "Spyglass";
            case "guard-baton" -> "Guard Baton";
            case "prison-remote" -> "Prison Remote";
            case "taser" -> "Taser";
            case "smoke_bomb" -> "Smoke Bomb";
            default -> "Guard Item";
        };
    }

    private void sendCooldownMessage(Player player, String message) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);

        if (lastTime == null || now - lastTime >= MESSAGE_COOLDOWN) {
            player.sendMessage(message);
            lastMessageTime.put(playerId, now);
        }
    }
    
    /**
     * ADDITIONAL: Protect against rapid clicking on blocks/air
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Check if using guard item
        ItemStack item = player.getInventory().getItemInMainHand();
        if (plugin.getGuardItemManager().isGuardItem(item)) {
            String itemType = plugin.getGuardItemManager().getGuardItemType(item);
            
            // Apply same spam protection as entity interactions
            Long lastInteraction = lastInteractionTime.get(playerId);
            if (lastInteraction != null && now - lastInteraction < INTERACTION_COOLDOWN) {
                event.setCancelled(true);
                return;
            }
            
            // Check for same action spam
            String lastAction = lastActionType.get(playerId);
            if (itemType != null && itemType.equals(lastAction)) {
                if (lastInteraction != null && now - lastInteraction < SAME_ACTION_COOLDOWN) {
                    event.setCancelled(true);
                    sendCooldownMessage(player, "<red>" + formatItemName(itemType) + " is on cooldown!</red>");
                    return;
                }
            }
            
            // Update tracking for non-entity guard item usage
            lastInteractionTime.put(playerId, now);
            if (itemType != null) {
                lastActionType.put(playerId, itemType);
            }
            
            // Check immobilization
            if (plugin.getDutyManager().isPlayerImmobilized(playerId)) {
                event.setCancelled(true);
                plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
                return;
            }
            
            // For guard items that don't require targets (like prison remote)
            if (event.getAction().toString().contains("RIGHT_CLICK")) {
                if (!plugin.getDutyManager().isOnDuty(playerId)) {
                    event.setCancelled(true);
                    sendCooldownMessage(player, "<red>You must be on duty to use guard items!</red>");
                    return;
                }
                
                // Let GuardItemManager handle the specific item logic
                boolean handled = plugin.getGuardItemManager().handleGuardItemUsageNoTarget(player, item);
                if (handled) {
                    event.setCancelled(true); // Prevent default block interaction
                }
            }
        }
    }

    /**
     * MAINTENANCE: Periodic cleanup of old entries (runs every 5 minutes)
     */
    public void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            java.util.concurrent.atomic.AtomicInteger cleaned = new java.util.concurrent.atomic.AtomicInteger(0);
            
            // Clean up entries older than 10 minutes
            lastMessageTime.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > 600000) { // 10 minutes
                    cleaned.incrementAndGet();
                    return true;
                }
                return false;
            });
            
            lastInteractionTime.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > 600000) { // 10 minutes
                    cleaned.incrementAndGet();
                    return true;
                }
                return false;
            });
            
            if (cleaned.get() > 0 && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Cleaned up " + cleaned.get() + " old spam protection entries");
            }
        }, 6000L, 6000L); // Run every 5 minutes (6000 ticks)
    }
} 