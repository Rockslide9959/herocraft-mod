package com.projecthero.mod.miraculous;

import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * v0.16.6: one Miraculous hero's six keys (server side). Registered once in {@link MiraculousKits}; the core
 * ({@link MiraculousAbilityManager}) routes R / G / X / Z / V / C and their Shift versions here, debounced, only while
 * the player holds this Kwami, is transformed and the transformation has finished ({@link Miraculous#canAct}).
 *
 * <pre>
 *   SLOT_1 = R   SLOT_2 = G   SLOT_3 = X   SLOT_4 = Z   SLOT_5 = V   SLOT_6 = C
 * </pre>
 * Shift+Z is the special. The kit decides what it does; when it really fires (not on a refusal) the kit calls
 * {@link Miraculous#startCountdown} itself -- Lucky Charm's second press (Miraculous Ladybug) must not restart it.
 */
public interface KwamiKit {
	/** A key went down ({@code pressed}) or up (only matters for held moves). {@code shift}: the Shift version. */
	void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift);

	/** Every server tick while the player holds this Kwami (transformed or not): running moves, timers, effects. */
	default void tick(ServerPlayer player) {
	}

	/**
	 * Detransformation (H, the countdown running out, death, logout, dimension change, revoke): end every running move,
	 * drop summoned things, clear the kit's {@code vals} / {@code vecs}. Called BEFORE the core saves the new state.
	 */
	default void onDetransform(ServerPlayer player) {
	}

	/** Damage the holder is about to take while transformed; return the new amount (0 cancels). Default: unchanged. */
	default float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return amount;
	}

	/**
	 * v0.17.1: an ordinary fall's damage while transformed (no safe landing armed): the core's half by default; a kit may
	 * soften it more (Multimouse: a quarter, and never a killing fall).
	 */
	default float modifyFallDamage(ServerPlayer player, float amount) {
		return amount * MiraculousConfig.FALL_DAMAGE_FACTOR;
	}

	/** Drop every static per-session collection (ServerStateReset). */
	default void clearSessionState() {
	}

	/** Forget one player's static scratch state (logout / revoke). */
	default void forget(java.util.UUID id) {
	}

	/**
	 * CLIENT-SAFE (reads only the synced {@code MiraculousState}): whether a move on this key is running right now (the
	 * HUD lights the key's box). Default: never.
	 */
	default boolean active(net.minecraft.world.entity.player.Player player, AbilitySlot slot) {
		return false;
	}

	/** The two moves on one key: [plain, shift] ability ids (HUD, guide, PowerInfoScreen, lang). */
	String[] idsOf(AbilitySlot slot);

	/** Full cooldown of a move, ticks (the HUD's cooldown fill). */
	int maxCooldown(String abilityId);
}
