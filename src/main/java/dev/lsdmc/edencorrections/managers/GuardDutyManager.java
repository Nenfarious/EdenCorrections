package dev.lsdmc.edencorrections.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuardDutyManager {
    private final EdenCorrections plugin;
    private final Map<UUID, Boolean> dutyStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dutyStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tokens = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> onDutyTimeMap = new HashMap<>();
    private final Map<UUID, Integer> breakTimeMap = new HashMap<>();
    private final Map<UUID, Integer> effectLevelMap = new HashMap<>();
    private final Map<UUID, BukkitTask> breakTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> dutyTasks = new HashMap<>();
    private final Map<UUID, Boolean> onBreakMap = new HashMap<>();
    private final String guardLoungeRegion = "guard_lounge";

    // Configuration values
    private final int BREAK_TIME_RATIO = 1; // 1 minute of break time for 5 minutes of duty
    private final int MAX_BREAK_TIME = 3600; // 60 minutes maximum break time
    private final int EFFECT_INTERVAL = 300; // 5 minutes between effects
    private final int DUTY_TIME_FOR_BREAK = 300; // 5 minutes of duty for 1 minute of break

    public GuardDutyManager(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean isOnDuty(Player player) {
        return dutyStatus.getOrDefault(player.getUniqueId(), false);
    }

    public void toggleDuty(Player player) {
        UUID playerId = player.getUniqueId();
        if (isOnDuty(player)) {
            goOffDuty(player);
        } else {
            goOnDuty(player);
        }
    }

    private boolean isInGuardLounge(Player player) {
        String regionName = plugin.getConfigManager().getConfig("duty.yml").getString("guard_duty.guard_lounge_region", "guard_lounge");
        Location location = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

        for (ProtectedRegion region : regions) {
            if (region.getId().equalsIgnoreCase(regionName)) {
                return true;
            }
        }
        return false;
    }

    private void goOnDuty(Player player) {
        UUID playerId = player.getUniqueId();
        dutyStatus.put(playerId, true);
        dutyStartTime.put(playerId, System.currentTimeMillis());
        
        // Apply guard effects
        applyGuardEffects(player);
        
        player.sendMessage(Component.text("You are now on duty.", NamedTextColor.GREEN));
    }

    private void goOffDuty(Player player) {
        UUID playerId = player.getUniqueId();
        dutyStatus.remove(playerId);
        dutyStartTime.remove(playerId);
        
        // Remove guard effects
        removeGuardEffects(player);
        
        player.sendMessage(Component.text("You are now off duty.", NamedTextColor.YELLOW));
    }

    private void applyGuardEffects(Player player) {
        // Night vision for better visibility
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        
        // Speed boost for patrolling
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        
        // Jump boost for mobility
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0, false, false));
    }

    private void removeGuardEffects(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    public void startBreak(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is on duty
        if (!isOnDuty(player)) {
            player.sendMessage(Component.text("You must be on duty to take a break.", NamedTextColor.RED));
            return;
        }

        // Check if player is already on break
        if (isOnBreak(player)) {
            player.sendMessage(Component.text("You are already on break.", NamedTextColor.RED));
            return;
        }

        // Check if player has any break time
        int breakTime = breakTimeMap.getOrDefault(playerId, 0);
        if (breakTime <= 0) {
            player.sendMessage(Component.text("You have no break time available.", NamedTextColor.RED));
            return;
        }

        // Start break
        onBreakMap.put(playerId, true);
        scheduleOffDutyTask(player, breakTime);

        player.sendMessage(Component.text("Break started. You have " + (breakTime / 60) + " minutes.", NamedTextColor.GREEN));
    }

    public void endBreak(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (!isOnBreak(player)) {
            player.sendMessage(Component.text("You are not on break.", NamedTextColor.RED));
            return;
        }

        // Cancel break task
        if (breakTasks.containsKey(playerId)) {
            breakTasks.get(playerId).cancel();
            breakTasks.remove(playerId);
        }

        // Reset break state
        onBreakMap.put(playerId, false);
        breakTimeMap.put(playerId, 0);

        player.sendMessage(Component.text("Break ended.", NamedTextColor.YELLOW));
    }

    public boolean isOnBreak(Player player) {
        return onBreakMap.getOrDefault(player.getUniqueId(), false);
    }

    public void convertBreakTimeToTokens(Player player) {
        UUID playerId = player.getUniqueId();
        int breakTime = breakTimeMap.getOrDefault(playerId, 0);
        
        if (breakTime <= 0) {
            player.sendMessage(Component.text("You have no break time to convert.", NamedTextColor.RED));
            return;
        }

        // Convert break time to tokens (1 minute = 10 tokens)
        int tokens = (breakTime / 60) * 10;
        addTokens(player, tokens);
        breakTimeMap.put(playerId, 0);

        player.sendMessage(Component.text("Converted " + (breakTime / 60) + " minutes of break time to " + tokens + " tokens.", NamedTextColor.GREEN));
    }

    public String getBreakTimeDisplay(Player player) {
        int timeLeft = getBreakTime(player);
        int hours = timeLeft / 60;
        int minutes = timeLeft % 60;
        return String.format("%d hours and %d minutes", hours, minutes);
    }

    private void scheduleOffDutyTask(Player player, int breakTime) {
        UUID playerId = player.getUniqueId();
        
        // Cancel any existing task
        if (breakTasks.containsKey(playerId)) {
            breakTasks.get(playerId).cancel();
        }

        // Schedule new task
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isOnDuty(player)) {
                applyNegativeEffects(player);
            }
        }, breakTime * 20L); // Convert seconds to ticks

        breakTasks.put(playerId, task);
    }

    private void applyNegativeEffects(Player player) {
        UUID playerId = player.getUniqueId();
        int effectLevel = effectLevelMap.getOrDefault(playerId, 0) + 1;
        effectLevelMap.put(playerId, effectLevel);

        // Remove any existing effects first
        removeNegativeEffects(player);

        // Apply effects based on level
        switch (effectLevel) {
            case 1 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_INTERVAL * 20, 0));
                player.sendMessage(Component.text("You're feeling a bit sluggish...", NamedTextColor.YELLOW));
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_INTERVAL * 20, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, EFFECT_INTERVAL * 20, 0));
                player.sendMessage(Component.text("You're feeling very tired...", NamedTextColor.YELLOW));
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_INTERVAL * 20, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, EFFECT_INTERVAL * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, EFFECT_INTERVAL * 20, 0));
                player.sendMessage(Component.text("You're feeling exhausted and hungry...", NamedTextColor.YELLOW));
            }
        }

        // Schedule next effect application if still on duty
        if (isOnDuty(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isOnDuty(player)) {
                    applyNegativeEffects(player);
                }
            }, EFFECT_INTERVAL * 20L);
        }
    }

    private void removeNegativeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.HUNGER);
    }

    public void showTimeGUI(Player player) {
        int timeLeft = breakTimeMap.getOrDefault(player.getUniqueId(), 0);
        int hours = timeLeft / 60;
        int minutes = timeLeft % 60;

        // Create GUI
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, Component.text("Break Time", NamedTextColor.DARK_BLUE));

        // Time display item
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        timeMeta.displayName(Component.text("Break Time Remaining", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("Hours: %d", hours), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format("Minutes: %d", minutes), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        timeMeta.lore(lore);
        timeItem.setItemMeta(timeMeta);
        gui.setItem(13, timeItem);

        // Fill empty slots with glass panes
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }

        player.openInventory(gui);
    }

    public void handleBreakTimeCommand(Player player) {
        int timeLeft = breakTimeMap.getOrDefault(player.getUniqueId(), 0);
        if (timeLeft <= 0) {
            player.sendMessage(Component.text("You have no break time remaining.", NamedTextColor.RED));
            return;
        }

        int hours = timeLeft / 60;
        int minutes = timeLeft % 60;
        String timeString = String.format("%d hours and %d minutes", hours, minutes);
        player.sendMessage(Component.text("You have " + timeString + " of break time remaining.", NamedTextColor.GREEN));
    }

    public int getBreakTime(Player player) {
        return breakTimeMap.getOrDefault(player.getUniqueId(), 0);
    }

    public long getDutyTime(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isOnDuty(player)) {
            return 0;
        }
        return (System.currentTimeMillis() - dutyStartTime.get(playerId)) / 1000;
    }

    public void cancelBreak(Player player) {
        UUID playerId = player.getUniqueId();
        cancelTasks(playerId);
        effectLevelMap.remove(playerId);
        removeNegativeEffects(player);
    }

    private void cancelTasks(UUID playerId) {
        if (breakTasks.containsKey(playerId)) {
            breakTasks.get(playerId).cancel();
            breakTasks.remove(playerId);
        }
        if (dutyTasks.containsKey(playerId)) {
            dutyTasks.get(playerId).cancel();
            dutyTasks.remove(playerId);
        }
    }

    public void setDutyTime(Player player, int seconds) {
        UUID playerId = player.getUniqueId();
        dutyStartTime.put(playerId, System.currentTimeMillis() - (seconds * 1000L));
    }

    public int getTokens(Player player) {
        return tokens.getOrDefault(player.getUniqueId(), 0);
    }

    public void addTokens(Player player, int amount) {
        plugin.getTokenService().addTokens(player, amount);
    }

    public void removeTokens(Player player, int amount) {
        plugin.getTokenService().removeTokens(player, amount);
    }

    public void setTokens(Player player, int amount) {
        plugin.getTokenService().setTokens(player, amount);
    }

    // LuckPerms rank management utilities
    public boolean assignRank(Player player, String group) {
        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) return false;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        Node node = Node.builder("group." + group).build();
        user.data().add(node);
        luckPerms.getUserManager().saveUser(user);
        return true;
    }

    public boolean removeRank(Player player, String group) {
        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) return false;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        Node node = Node.builder("group." + group).build();
        user.data().remove(node);
        luckPerms.getUserManager().saveUser(user);
        return true;
    }

    public boolean hasRank(Player player, String group) {
        LuckPerms luckPerms = plugin.getLuckPermsApi();
        if (luckPerms == null) return false;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        return user.getNodes().stream().anyMatch(n -> n.getKey().equals("group." + group));
    }
} 