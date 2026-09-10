package com.projecthero.mod.hero;

import com.projecthero.mod.power.ThorPowers;

import net.minecraft.server.level.ServerPlayer;

/**
 * Pure adapter: routes a universal ability-slot input to the <em>existing</em> Thor ability handlers
 * with (mostly) no behaviour change. v0.10.10 slot layout: R = Throw Mjolnir, Shift + R = Call Mjolnir
 * (the recall moved onto the sneak variant so the throw -- by far the more frequently used of the two
 * -- gets the bare key, and so the throw finally has a binding at all rather than only right-click),
 * G = Lightning Strike,
 * X = Lightning Beam, Z = God of Thunder's Wrath (ultimate), V = Thunderclap, C = Chain Lightning.
 * Storm Call was dropped from the kit. Each {@code ThorPowers} method already does its own worthiness
 * / hold / cooldown / energy validation, so this class adds none.
 *
 * <p>Flight is unchanged too -- still a double-tap of the jump key handled in {@code ProjectHeroModClient}
 * / {@code ThorActionPayload}, not a slot.
 */
public final class ThorAbilityAdapter {
	private ThorAbilityAdapter() {
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		switch (slot) {
			case SLOT_1 -> { // R -- Throw Mjolnir; Shift + R -- Call Mjolnir
				if (pressed) {
					if (player.isShiftKeyDown()) {
						ThorPowers.callHammer(player);
					} else if (!ThorPowers.throwMjolnir(player)) {
						// Nothing in hand to throw. Say so rather than silently recalling -- a recall the
						// player did not ask for can yank the hammer out of a fight they left it in.
						player.displayClientMessage(
								net.minecraft.network.chat.Component.translatable("message.projecthero.thor.no_hammer_to_throw"), true);
					}
				}
			}
			case SLOT_2 -> { // G -- Lightning Strike
				if (pressed) {
					ThorPowers.lightningStrike(player);
				}
			}
			case SLOT_3 -> // X -- Lightning Beam (held/continuous)
					ThorPowers.setLaserActive(player, pressed);
			case SLOT_4 -> // Z -- God of Thunder's Wrath: hold 5 s to charge, release cancels
					ThorPowers.setWrathCharging(player, pressed);
			case SLOT_5 -> { // V -- Thunderclap
				if (pressed) {
					ThorPowers.thunderclap(player);
				}
			}
			case SLOT_6 -> { // C -- Chain Lightning
				if (pressed) {
					ThorPowers.chainLightningCast(player);
				}
			}
		}
	}
}
