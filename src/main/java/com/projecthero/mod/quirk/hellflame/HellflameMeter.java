package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: Body Heat's bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.1). Kindle 0-39 (x0.85),
 * Blaze 40-79 (x1.0), Inferno 80-100 (x1.3); the doc's Overheat past 100 (5 s at x1.5 and 1 HP/s, then an 8 s burnout) is
 * the kit build's to switch on ({@code pushPast} here, started through {@code QuirkMeters.pushPast}).
 *
 * <p>Pacing (the user: heat must not "build up too quick"): every natural heat gain x{@link #BUILD} -- a minute of
 * sprint-flight plus an attack every 2 s now takes about 52 s to reach Inferno (it was 6 s); the 4/s vent is unchanged
 * (Inferno to Blaze in about 11.5 s).
 */
public final class HellflameMeter {
	public static final float BUILD = 0.12f;
	public static final float RECOVER = 1.0f;

	private HellflameMeter() {
	}

	static void register() {
		QuirkMeterSpec.builder(Quirk.HELLFLAME)
				.bands(40f, 80f)
				.names("kindle", "blaze", "inferno", "overheat")
				.damage(0.85f, 1.0f, 1.3f, 1.5f)
				// the Hellflame kit build adds the Overheat push here: .pushPast(100, 1.0f, 160) + .onCrash(burnout)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h
						.sustained(HellflameConfig.heat().flightSprintPerSecond)
						.move(HellflameConfig.SPIDER_HEAT, HellflameConfig.SPIDER_COOLDOWN)
						.move(HellflameConfig.JET_HEAT, HellflameConfig.JET_COOLDOWN)
						.move(HellflameConfig.BARRAGE_HEAT_PER_JAB * HellflameConfig.BARRAGE_JABS, HellflameConfig.BARRAGE_COOLDOWN)
						.move(HellflameConfig.PILLAR_HEAT, HellflameConfig.PILLAR_COOLDOWN)
						.move(HellflameConfig.VANISH_HEAT, HellflameConfig.VANISH_COOLDOWN)
						.move(HellflameConfig.WALL_HEAT, HellflameConfig.WALL_COOLDOWN)
						.move(HellflameConfig.MESH_HEAT, HellflameConfig.MESH_COOLDOWN)
						.recovery(HellflameConfig.heat().ventPerSecond, HellflameConfig.heat().ventDelayTicks))
				.register();
	}
}
