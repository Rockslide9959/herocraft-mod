package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Overhaul revamp: <b>Matter</b>'s bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.2) and his combo route. A
 * {@link QuirkMeterSpec.Direction#SPENDS SPENDS} meter: what he builds empties it, so its danger is the LOW band -- Starved
 * 0-24 (constructs small), Stocked 25-79, Overflowing 80-100 (spikes and walls x1.5; the HUD's gold band). The bands change
 * the SIZE of what he builds ({@link OverhaulMoves#constructScale}), not the damage multiplier, so every band's damage is x1.
 *
 * <p>Pacing (the user: "i dont want like heat or darkness or whatever to build up too quick"): the heavy-use script is every
 * Matter move in rotation, one every 2 s -- the spenders at their costs, the disassembling moves as gains -- with the stone /
 * earth refill (10 a second after its short delay) between presses. It never starves inside 45 s; from Starved, resting on
 * stone refills to Overflowing in about 5.5 s.
 */
public final class OverhaulMeter {
	public static final float BUILD = 1.0f;
	public static final float RECOVER = 1.0f;

	/** "Buried Alive": Disassemble Terrain, then Ground Spikes through the pits within 2 s -- +50%. */
	public static final QuirkCombos.Route BURIED_ALIVE = QuirkCombos.route(Quirk.OVERHAUL, "buried_alive", OverhaulConfig.PIT,
			OverhaulConfig.SPIKE_WAVE).window(40).bonus(1.5f).register();

	private OverhaulMeter() {
	}

	static void register() {
		float regen = OverhaulConfig.MATTER_GROUND_PER_SECOND;
		QuirkMeterSpec.builder(Quirk.OVERHAUL)
				.direction(QuirkMeterSpec.Direction.SPENDS)
				.bands(25f, 80f)
				.names("starved", "stocked", "overflowing", "beyond")
				.damage(1.0f, 1.0f, 1.0f, 1.0f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h
						// between presses the refill runs (after its short delay): the share of a press interval it runs for
						.regen(regen * (h.pressEvery - OverhaulConfig.MATTER_REGEN_DELAY) / (float) h.pressEvery)
						.move(OverhaulConfig.SPIKE_WAVE_COST, OverhaulConfig.SPIKE_WAVE_COOLDOWN)
						.move(-OverhaulConfig.DISASSEMBLE_GAIN, OverhaulConfig.DISASSEMBLE_COOLDOWN)
						.move(OverhaulConfig.SPIKE_FIELD_COST, OverhaulConfig.SPIKE_FIELD_COOLDOWN)
						.move(OverhaulConfig.THORN_COST, OverhaulConfig.THORN_COOLDOWN)
						.move(OverhaulConfig.WALL_COST, OverhaulConfig.WALL_COOLDOWN)
						.move(-OverhaulConfig.SHRAPNEL_GAIN, OverhaulConfig.SHRAPNEL_COOLDOWN)
						.move(OverhaulConfig.PILLAR_COST, OverhaulConfig.PILLAR_COOLDOWN)
						.move(OverhaulConfig.GROUND_SHIFT_COST, OverhaulConfig.GROUND_SHIFT_COOLDOWN)
						.move(-OverhaulConfig.PIT_GAIN, OverhaulConfig.PIT_COOLDOWN)
						.move(OverhaulConfig.MEND_COST, OverhaulConfig.MEND_COOLDOWN)
						.move(OverhaulConfig.REASSEMBLE_COST, OverhaulConfig.REASSEMBLE_COOLDOWN)
						.move(OverhaulConfig.FUSION_COST, OverhaulConfig.FUSION_COOLDOWN)
						.recovery(regen, OverhaulConfig.MATTER_REGEN_DELAY))
				.register();
	}
}
