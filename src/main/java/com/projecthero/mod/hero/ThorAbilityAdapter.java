package com.projecthero.mod.hero;

import com.projecthero.mod.power.ThorKit;

import net.minecraft.server.level.ServerPlayer;

/**
 * Pure adapter: routes a universal ability-slot input to Thor's kit. v0.17.7 Cosmic revamp: the move each key (and its Shift
 * variant) does now depends on Thor's weapon state -- unarmed, Mjolnir, Stormbreaker or both ({@link com.projecthero.mod.power.ThorState})
 * -- so the routing table lives in {@link ThorKit#handle}. R still calls the weapon (right-click throws, outside this adapter),
 * V is Storm Call and Shift+V God of Thunder in every state; H (Thor's Armour) and N (weapon selector) are handled elsewhere.
 * Each move does its own worthiness / hold / cooldown / energy validation.
 *
 * <p>Flight is unchanged: a double-tap of the jump key with a weapon in hand, handled in {@code ProjectHeroModClient} /
 * {@code ThorActionPayload}, not a slot. Unarmed Thor has no flight.
 */
public final class ThorAbilityAdapter {
	private ThorAbilityAdapter() {
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		ThorKit.handle(player, slot, pressed);
	}
}
