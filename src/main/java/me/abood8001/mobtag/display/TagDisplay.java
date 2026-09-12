package me.abood8001.mobtag.display;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public interface TagDisplay {

    /** Shows or refreshes this tag for one viewer. Text is tracked per viewer. */
    void update(Location location, String text, Player viewer);

    /** Hides this tag from one viewer without destroying it for the others. */
    void hideFrom(Player viewer);

    /** Hides this tag from a viewer that is no longer online or resolvable. */
    void hideFrom(UUID viewerId);

    void remove();

    boolean isDead();

    /** Live view of the players currently seeing this tag. */
    Set<UUID> getViewers();
}
