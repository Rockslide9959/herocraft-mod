package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * One Moon Knight key (R, G, Z, X, C or V). {@link MoonKnightAbilityManager} turns the raw press / release edges into
 * these calls, server-side (the client only reports edges, the server times them and reads sneak itself):
 * <ul>
 *   <li>{@link #sneak} -- the key was pressed while sneaking (fires on the press; the release is ignored);</li>
 *   <li>{@link #tap} -- released before {@code MoonKnightConfig.HOLD_THRESHOLD_TICKS};</li>
 *   <li>{@link #holdStart} once it has been held that long, {@link #holdTick} every tick after, and
 *       {@link #holdRelease} on release (with the total ticks held);</li>
 *   <li>{@link #cancelHold} if a hold is interrupted (un-transform, death, screen opened);</li>
 *   <li>{@link #tick} every server tick while the player is a transformed Moon Knight, for upkeep.</li>
 * </ul>
 * Everything is called only while the player is transformed.
 */
public interface MoonKnightMove {
	default void tap(ServerPlayer player) {
	}

	default void holdStart(ServerPlayer player) {
	}

	default void holdTick(ServerPlayer player, int ticksHeld) {
	}

	default void holdRelease(ServerPlayer player, int ticksHeld) {
	}

	default void cancelHold(ServerPlayer player) {
	}

	default void sneak(ServerPlayer player) {
	}

	default void tick(ServerPlayer player) {
	}

	/** The suit is coming off (or the player died / left): drop anything this key keeps active. */
	default void onUntransform(ServerPlayer player) {
	}
}
