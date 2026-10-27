package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1: Body Heat's bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.1). Kindle 0-39 (x0.85), Blaze 40-79
 * (x1.0, hot hands), Inferno 80-100 (x1.3, 0.5 HP a second); at 100 the Overheat push (5 s at x1.5 and 1 HP a second), then
 * the 8 s burnout ({@link HellflameMoves#burnout}). The full Endeavor suit builds Heat x{@link HellflameConfig#SUIT_BUILD}
 * (+15% headroom).
 *
 * <p>Pacing (the user: heat must not "build up too quick"): every heat gain x{@link #BUILD}. The kit build's heavy-use
 * script -- sprint flight the whole time plus an attack every 2 s through all twelve costed moves (the new Flashfire Fist,
 * Jet Burst, Flame Wave, a full Hellblast and the Flame Cloak included) -- reaches Inferno after about 50 s; the 4/s vent is
 * unchanged (Inferno to Blaze in about 11.5 s).
 */
public final class HellflameMeter {
	public static final float BUILD = 0.18f; // v0.17.1 (user: "make the heat generate a bit faster"): was 0.12
	public static final float RECOVER = 1.0f;
	/** v0.17.1: Hell Spider: Mesh, then the Hellflame Pillar within 2 s (it erupts under the trapped) = x1.5. */
	public static QuirkCombos.Route MESH_PILLAR;

	private HellflameMeter() {
	}

	static void register() {
		MESH_PILLAR = QuirkCombos.route(Quirk.HELLFLAME, "mesh_pillar", HellflameConfig.MESH, HellflameConfig.PILLAR).window(40).bonus(1.5f).register();
		QuirkMeterSpec.builder(Quirk.HELLFLAME)
				.bands(40f, 80f)
				.names("kindle", "blaze", "inferno", "overheat")
				.damage(0.85f, 1.0f, 1.3f, 1.5f)
				.pushPast(HellflameConfig.OVERHEAT_PUSH_TICKS, HellflameConfig.OVERHEAT_HP_PER_SECOND, HellflameConfig.heat().overheatTicks)
				.onCrash(HellflameMoves::burnout)
				.pacing(BUILD, RECOVER)
				.suitBuild(HellflameConfig.SUIT_BUILD)
				.heavyUse(h -> h
						.sustained(HellflameConfig.heat().flightSprintPerSecond)
						.move(HellflameConfig.SPIDER_HEAT, HellflameConfig.SPIDER_COOLDOWN)
						.move(HellflameConfig.JET_HEAT, HellflameConfig.JET_COOLDOWN)
						.move(HellflameConfig.BARRAGE_HEAT_PER_JAB * HellflameConfig.BARRAGE_JABS, HellflameConfig.BARRAGE_COOLDOWN)
						.move(HellflameConfig.PILLAR_HEAT, HellflameConfig.PILLAR_COOLDOWN)
						.move(HellflameConfig.VANISH_HEAT, HellflameConfig.VANISH_COOLDOWN)
						.move(HellflameConfig.WALL_HEAT, HellflameConfig.WALL_COOLDOWN)
						.move(HellflameConfig.MESH_HEAT, HellflameConfig.MESH_COOLDOWN)
						// v0.17.1 kit build: the new moves join the rotation (a full three-punch Fist, a full-charge Hellblast, a whole Cloak)
						.move(HellflameConfig.FIST_HEAT * 3, HellflameConfig.FIST_COOLDOWN)
						.move(HellflameConfig.BURST_HEAT, HellflameConfig.BURST_COOLDOWN)
						.move(HellflameConfig.WAVE_HEAT, HellflameConfig.WAVE_COOLDOWN)
						.move(HellflameConfig.HELLBLAST_HEAT_PER_SECOND * (HellflameConfig.HELLBLAST_BASE_TICKS + HellflameConfig.HELLBLAST_MAX_CHARGE) / 20f,
								HellflameConfig.HELLBLAST_COOLDOWN)
						.move(HellflameConfig.heat().cloakPerSecond * HellflameConfig.CLOAK_TICKS / 20f, HellflameConfig.CLOAK_COOLDOWN)
						.recovery(HellflameConfig.heat().ventPerSecond, HellflameConfig.heat().ventDelayTicks))
				.register();
	}
}
