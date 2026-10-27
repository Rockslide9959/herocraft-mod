package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: Focus' bands and pacing (the Overhaul kit build turns Focus into ground-fed Matter with the same
 * bands, {@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.2). A {@link QuirkMeterSpec.Direction#SPENDS SPENDS} meter: moves empty it,
 * so its danger is the LOW band -- Starved 0-24 (small constructs), Stocked 25-79, Overflowing 80-100 (1.5x constructs).
 *
 * <p>Pacing: unchanged (x1.0). The steady 12/s refill already out-runs an attack every 2 s, so heavy use never starves it
 * (the shared 45 s test passes as built), and it refills from Starved to Overflowing in under 5 s.
 */
public final class OverhaulMeter {
	public static final float BUILD = 1.0f;
	public static final float RECOVER = 1.0f;

	private OverhaulMeter() {
	}

	static void register() {
		float regen = OverhaulConfig.FOCUS_REGEN_PER_TICK * 20f;
		QuirkMeterSpec.builder(Quirk.OVERHAUL)
				.direction(QuirkMeterSpec.Direction.SPENDS)
				.bands(25f, 80f)
				.names("starved", "stocked", "overflowing", "beyond")
				.damage(0.85f, 1.0f, 1.0f, 1.0f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h
						// between presses the refill runs (after its short delay): the share of a press interval it runs for
						.regen(regen * (h.pressEvery - OverhaulConfig.FOCUS_REGEN_DELAY) / (float) h.pressEvery)
						.move(OverhaulConfig.DISASSEMBLE_COST, OverhaulConfig.DISASSEMBLE_COOLDOWN)
						.move(OverhaulConfig.SPIKE_WAVE_COST, OverhaulConfig.SPIKE_WAVE_COOLDOWN)
						.move(OverhaulConfig.STRIKE_COST, OverhaulConfig.STRIKE_COOLDOWN)
						.move(OverhaulConfig.PILLAR_COST, OverhaulConfig.PILLAR_COOLDOWN)
						.move(OverhaulConfig.WALL_COST, OverhaulConfig.WALL_COOLDOWN)
						.move(OverhaulConfig.SPIKE_FIELD_COST, OverhaulConfig.SPIKE_FIELD_COOLDOWN)
						.move(OverhaulConfig.PIT_COST, OverhaulConfig.PIT_COOLDOWN)
						.move(OverhaulConfig.DISARM_COST, OverhaulConfig.DISARM_COOLDOWN)
						.recovery(regen, OverhaulConfig.FOCUS_REGEN_DELAY))
				.register();
	}
}
