package com.projecthero.mod.quirk;

import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * v0.16.7: one Quirk's 12 moves (server side). Registered once in {@link QuirkKits} from the kit's own init; the core
 * ({@link QuirkAbilityManager}) routes R / G / X / Z / V / C and their Shift versions here, debounced, only while the
 * player holds this Quirk, is not Erased ({@link com.projecthero.mod.hero.PowerSuppression}) and is not overloaded
 * (unless {@link #usableWhileOverloaded}).
 *
 * <pre>
 *   SLOT_1 = R   SLOT_2 = G   SLOT_3 = X   SLOT_4 = Z   SLOT_5 = V   SLOT_6 = C
 * </pre>
 * The quirk is always on (no transformation). Each kit owns its meter's meaning ({@link Quirks#meter}, 0..100) and
 * decides when it overloads ({@link Quirks#overload}).
 */
public interface QuirkKit {
	/** A key went down ({@code pressed}) or up (only matters for held moves). {@code shift}: the Shift version. */
	void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift);

	/** Every server tick while the player holds this Quirk: the meter, running moves, timers, passives. */
	default void tick(ServerPlayer player) {
	}

	/**
	 * The quirk is going away or pausing (revoke, death, logout, dimension change, Erased): end every running move, drop
	 * summoned things, remove attribute modifiers / effects the kit added. The kit's vals / vecs are cleared by the core
	 * right after on revoke and death.
	 */
	default void stopAll(ServerPlayer player) {
	}

	/** Damage the holder is about to take; return the new amount (0 cancels). Default: unchanged. */
	default float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return amount;
	}

	/** Drop every static per-session collection (ServerStateReset). */
	default void clearSessionState() {
	}

	/** Forget one player's static scratch state (logout / revoke). */
	default void forget(java.util.UUID id) {
	}

	/** CLIENT-SAFE (reads only the synced {@code QuirkState}): a move on this key is running (the HUD lights the box). */
	default boolean active(net.minecraft.world.entity.player.Player player, AbilitySlot slot) {
		return false;
	}

	/** This key still works while overloaded (e.g. a move that vents the meter). Default: no. */
	default boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return false;
	}

	/** The two moves on one key: [plain, shift] ability ids (HUD, guide, PowerInfoScreen, lang). */
	String[] idsOf(AbilitySlot slot);

	/** Full cooldown of a move, ticks (the HUD's cooldown fill). */
	int maxCooldown(String abilityId);
}
