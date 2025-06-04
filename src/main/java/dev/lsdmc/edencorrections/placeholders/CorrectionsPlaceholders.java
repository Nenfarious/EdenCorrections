package dev.lsdmc.edencorrections.placeholders;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.LuckPermsUtil;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CorrectionsPlaceholders extends PlaceholderExpansion {
    private final EdenCorrections plugin;

    public CorrectionsPlaceholders(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "edencorrections";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Stay registered until the server stops
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) {
            // Some global placeholders that don't need a player
            if (identifier.equals("online_guards")) {
                return String.valueOf(plugin.getGuardBuffManager().getOnlineGuardCount());
            }
            return "";
        }

        UUID uuid = player.getUniqueId();

        switch (identifier) {
            case "is_on_duty":
                return plugin.getDutyManager().isOnDuty(uuid) ? "Yes" : "No";

            case "off_duty_minutes":
                return String.valueOf(plugin.getDutyManager().getRemainingOffDutyMinutes(uuid));

            case "duty_status":
                return plugin.getDutyManager().isOnDuty(uuid) ? "On Duty" : "Off Duty";

            case "duty_status_colored":
                return plugin.getDutyManager().isOnDuty(uuid) ? "§aOn Duty" : "§eOff Duty";

            case "duty_time_served":
                if (plugin.getDutyManager().isOnDuty(uuid)) {
                    long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
                    long duration = System.currentTimeMillis() - startTime;
                    return String.valueOf((int) (duration / (1000 * 60)));
                }
                return "0";

            case "duty_time_until_reward":
                if (plugin.getDutyManager().isOnDuty(uuid)) {
                    long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
                    long duration = System.currentTimeMillis() - startTime;
                    int minutes = (int) (duration / (1000 * 60));
                    int threshold = plugin.getDutyManager().getThresholdMinutes();

                    if (minutes >= threshold) {
                        return "0";
                    } else {
                        return String.valueOf(threshold - minutes);
                    }
                }
                return String.valueOf(plugin.getDutyManager().getThresholdMinutes());

            case "guard_rank":
                // New placeholder for guard rank
                if (player.isOnline()) {
                    String rank = LuckPermsUtil.getGuardRank(player.getPlayer());
                    return rank != null ? rank : "none";
                }
                return "none";

            case "time_formatted":
                // Return the off-duty time formatted nicely
                int minutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);
                return MessageUtils.formatTime(minutes * 60);

            case "max_off_duty_minutes":
                // Return the maximum off-duty minutes
                return String.valueOf(plugin.getDutyManager().getMaxOffDutyTime());

            // New placeholders for the guard system
            case "death_cooldown":
                // Return the death cooldown time remaining
                return String.valueOf(plugin.getGuardLootManager().getPlayerCooldown(uuid));

            case "death_cooldown_formatted":
                // Return the death cooldown time formatted nicely
                int cooldownSeconds = plugin.getGuardLootManager().getPlayerCooldown(uuid);
                return MessageUtils.formatTime(cooldownSeconds);

            case "death_penalty":
                // Return the death penalty time remaining
                return String.valueOf(plugin.getGuardPenaltyManager().getPlayerLockTime(uuid));

            case "death_penalty_formatted":
                // Return the death penalty time formatted nicely
                int penaltySeconds = plugin.getGuardPenaltyManager().getPlayerLockTime(uuid);
                return MessageUtils.formatTime(penaltySeconds);

            case "is_locked":
                // Return whether the player is locked (has an active penalty)
                return plugin.getGuardPenaltyManager().isPlayerLocked(uuid) ? "Yes" : "No";

            // Guard token system placeholders
            case "guard_tokens":
                // Return the player's current guard token balance
                return String.valueOf(plugin.getGuardTokenManager().getTokens(uuid));

            case "guard_tokens_formatted":
                // Return the player's guard token balance with formatting
                int tokens = plugin.getGuardTokenManager().getTokens(uuid);
                return String.format("%,d", tokens);

            case "is_guard":
                // Return whether the player is a guard
                if (player.isOnline()) {
                    return plugin.getGuardRankManager().getPlayerRank(player.getPlayer()) != null ? "Yes" : "No";
                }
                return "No";

            default:
                return null;
        }
    }
}