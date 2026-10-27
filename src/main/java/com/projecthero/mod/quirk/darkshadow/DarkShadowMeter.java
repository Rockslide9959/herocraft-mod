package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: the Shadow meter's bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.4). Restrained
 * 0-29 (x0.6), Hunting 30-74 (x1.0), Ravenous 75-99 (x1.4), Berserk at 100 (as built).
 *
 * <p>Pacing (the user named darkness: it must not "build up too quick"): the climb in the dark and every move's push
 * x{@link #BUILD} -- in pitch dark with a move every 2 s Ravenous now comes after about 51 s (it was 10 s; with no moves at
 * all, 75 s). Light still calms it at the full rate (Ravenous to Hunting in about 4.5 s in bright light).
 */
public final class DarkShadowMeter {
	public static final float BUILD = 0.20f;
	public static final float RECOVER = 1.0f;

	/** v0.17.1 combo route "Ravening Feast": Dark Fang, then Abyssal Rampage within 2 s -- +30% on all three swings. */
	public static final com.projecthero.mod.quirk.QuirkCombos.Route RAVENING_FEAST = com.projecthero.mod.quirk.QuirkCombos.route(Quirk.DARK_SHADOW,
			"ravening_feast", DarkShadowConfig.FANG, DarkShadowConfig.RAMPAGE).window(40).bonus(1.3f).register();

	private DarkShadowMeter() {
	}

	static void register() {
		QuirkMeterSpec.builder(Quirk.DARK_SHADOW)
				.bands(30f, 75f)
				.names("restrained", "hunting", "ravenous", "berserk")
				.damage(0.6f, 1.0f, 1.4f, 1.4f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> {
					DarkShadowConfig c = DarkShadowConfig.get();
					h.sustained(c.meter.pitchRise * 20f)
							.move(c.meter.strikePush, c.moves.strikeCooldown)
							.move(c.meter.fangPush, c.moves.fangCooldown)
							.move(c.meter.grabPush, c.moves.grabCooldown)
							.move(c.meter.fetchPush, c.moves.fetchCooldown)
							.move(c.meter.scoutPush, c.moves.scoutCooldown)
							.move(c.meter.novaPush, c.moves.novaCooldown)
							// v0.17.1 revamp
							.move(c.meter.lungePush, c.moves.lungeCooldown)
							.move(c.meter.rampagePush, c.moves.rampageCooldown)
							.move(c.meter.tossPush, c.moves.grabCooldown)
							.recovery(c.meter.brightFall * 20f, 0);
				})
				.register();
	}
}
