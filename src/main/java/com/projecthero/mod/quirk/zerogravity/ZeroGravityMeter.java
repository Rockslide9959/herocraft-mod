package com.projecthero.mod.quirk.zerogravity;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: Nausea's bands, pacing and the Uravity suit perk ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.7).
 * Steady 0-49, Queasy 50-84 (screen sway, +25% float capacity), Pushing it 85-99 (strong sway, x1.3 drop damage), Sick at 100.
 *
 * <p>Pacing: the weight-per-second climb and every touch's sting x{@link #BUILD} -- floating herself, three mobs and five
 * rubble blocks with a touch every 2 s now reaches "Pushing it" after about 51 s (it was 14 s). The full Uravity suit:
 * x{@link #SUIT} on top (-25% Nausea, the doc's set perk). The 8/s decay once nothing floats is unchanged (85 to 50 in about
 * 4.4 s).
 */
public final class ZeroGravityMeter {
	public static final float BUILD = 0.20f;
	public static final float RECOVER = 1.0f;
	public static final float SUIT = 0.75f;
	/** The heavy-use profile's floated weight: herself, three zombie-sized mobs and five rubble blocks. */
	public static final float HEAVY_WEIGHT = 1.5f + 3f + 5 * 0.4f;

	private ZeroGravityMeter() {
	}

	static void register() {
		QuirkMeterSpec.builder(Quirk.ZERO_GRAVITY)
				.bands(50f, 85f)
				.names("steady", "queasy", "pushing", "sick")
				.damage(1.0f, 1.0f, 1.3f, 1.3f)
				.pacing(BUILD, RECOVER)
				.suitBuild(SUIT)
				.heavyUse(h -> {
					ZeroGravityConfig.Nausea n = ZeroGravityConfig.nausea();
					h.sustained(n.perWeightPerSecond * HEAVY_WEIGHT)
							.ramp(1f + n.timeBonusMax, n.timeBonusTicks)
							.move(n.perFloat, ZeroGravityConfig.TOUCH_COOLDOWN)
							.move(n.perFloat, ZeroGravityConfig.SLAM_COOLDOWN)
							.move(n.perFloat, ZeroGravityConfig.SHOWER_COOLDOWN)
							.move(n.perFloat, ZeroGravityConfig.FIELD_COOLDOWN)
							.move(ZeroGravityConfig.CATCH_NAUSEA, ZeroGravityConfig.CATCH_COOLDOWN)
							// v0.17.1 revamp: the new moves that cost Nausea
							.move(n.perFloat * ZeroGravityConfig.BOMB_STING, ZeroGravityConfig.BOMB_COOLDOWN)
							.move(n.perFloat * ZeroGravityConfig.PUSH_STING, ZeroGravityConfig.PUSH_COOLDOWN)
							.move(n.perFloat, ZeroGravityConfig.GUNHEAD_COOLDOWN) // the Gunhead throw floats it
							.recovery(n.decayPerSecond, 0);
				})
				.register();
	}
}
