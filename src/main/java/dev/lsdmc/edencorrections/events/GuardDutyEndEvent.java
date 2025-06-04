package dev.lsdmc.edencorrections.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a guard goes off duty
 */
public class GuardDutyEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;

    public GuardDutyEndEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
} 