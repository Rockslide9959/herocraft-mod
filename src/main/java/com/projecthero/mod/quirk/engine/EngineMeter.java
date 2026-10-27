package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: Engine Heat's bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.3). Cold 0-29 (-10%
 * momentum gain, the kit's), Warm 30-79, Redline 80-99 (+15% speed-scaled damage), Stall at 100 (as built: no push past).
 *
 * <p>Pacing: the heat gain goes through the kit's own suit factor first ({@link EngineConfig#NO_SUIT_HEAT_MULT} 0.6 out of
 * the suit, {@link EngineConfig#SUIT_HEAT_MULT} 0.5 in it -- the user's v0.17.1 tuning stays) and then x{@link #BUILD}. Not a
 * double cut: with only the 0.6, Gear 3 sprinting plus a kick every 2 s still reached Redline in 16 s, so the framework
 * factor is the part needed to reach the shared ~50 s target (effective x0.18 of the v0.16.7 rate out of the suit, x0.15
 * in it). Cooling x{@link #RECOVER}: idling in gear Redline to Warm takes about 14 s (was 20 s).
 */
public final class EngineMeter {
	public static final float BUILD = 0.30f;
	public static final float RECOVER = 1.4f;

	private EngineMeter() {
	}

	static void register() {
		float suit = EngineConfig.NO_SUIT_HEAT_MULT; // the heavy-use profile is out of the suit (the faster case)
		QuirkMeterSpec.builder(Quirk.ENGINE)
				.bands(30f, 80f)
				.names("cold", "warm", "redline", "stall")
				.damage(1.0f, 1.0f, 1.15f, 1.15f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h
						.sustained(EngineConfig.GEAR_HEAT_PER_SECOND[3] * suit)
						.move(EngineConfig.KICK_HEAT * suit, EngineConfig.KICK_COOLDOWN)
						.move(EngineConfig.DASH_HEAT * suit, EngineConfig.DASH_COOLDOWN)
						.move(EngineConfig.SPIN_HEAT * suit, EngineConfig.SPIN_COOLDOWN)
						.move(EngineConfig.SWEEP_HEAT * suit, EngineConfig.SWEEP_COOLDOWN)
						.move(EngineConfig.AXE_HEAT * suit, EngineConfig.AXE_COOLDOWN)
						.move(EngineConfig.RISING_HEAT * suit, EngineConfig.RISING_COOLDOWN)
						.move(EngineConfig.BARRAGE_HEAT * suit, EngineConfig.BARRAGE_COOLDOWN)
						.move(EngineConfig.EXTEND_KICK_HEAT * suit, EngineConfig.EXTEND_KICK_COOLDOWN)
						.move(EngineConfig.FINISHER_HEAT * suit, EngineConfig.FINISHER_COOLDOWN)
						.recovery(EngineConfig.IDLE_COOL_PER_SECOND, 0))
				.register();
	}
}
