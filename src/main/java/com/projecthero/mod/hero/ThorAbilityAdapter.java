package com.projecthero.mod.hero;

import com.projecthero.mod.power.ThorPowers;

import net.minecraft.server.level.ServerPlayer;

/**
 * Pure adapter: routes a universal ability-slot input to the <em>existing</em> Thor ability handlers
 * with (mostly) no behaviour change. v0.11.12 slot layout: R = Call Mjolnir (explicit user request --
 * R is the recall bind now, full stop; throwing moved off the ability slot entirely and lives solely on
 * right-click while holding the hammer, see {@link com.projecthero.mod.item.MjolnirItem#use}, which
 * already called {@link ThorPowers#throwMjolnir} independently of this adapter), G = Lightning Strike,
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
			case SLOT_1 -> { // R -- Call Mjolnir
				if (pressed) {
					ThorPowers.callHammer(player);
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
