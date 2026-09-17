package com.projecthero.mod.greenlantern.data;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The entire Green Lantern Hero-Tier power for one player, in one isolated namespaced attachment
 * ({@code projecthero:green_lantern_state}). Isolated from every other Hero-Tier/experimental store,
 * exactly like {@code MaxSteelState} is.
 *
 * <p>Persistent and {@code copyOnDeath()} -- the power and its Ring Charge must survive death and relog.
 * Synced to everyone (other clients need {@link #suited} to render the suit).
 * The server stays authoritative: every gameplay check runs against the server-side copy.
 *
 * <p>Transient combat state (flight, active barrier, construct-wheel-hold gesture) deliberately lives
 * in separate, non-persisted attachments instead of here -- see {@code ModAttachments}' Green Lantern
 * section -- both to keep this codec well under the 16-field {@code RecordCodecBuilder} ceiling
 * (Symbiote's split precedent) and because that state should NOT survive a relog.
 *
 * <p>Codec field order is load-bearing -- do not reorder.
 */
public final class GreenLanternState {
	public static final int SUIT_IDLE = 0;
	public static final int SUIT_SUITING_UP = 1;
	public static final int SUIT_SUITING_DOWN = 2;

	/** Permanent Hero-Tier power flag -- set once the ring has bonded. */
	public boolean hasPower;
	/** Ring Charge, 0..{@link GreenLanternConfig#MAX_RING_CHARGE}. */
	public float ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
	/** Whether the suit is on (or actively forming/retracting). */
	public boolean suited;
	/** {@link #SUIT_IDLE}/{@link #SUIT_SUITING_UP}/{@link #SUIT_SUITING_DOWN}. */
	public int suitAnimDir = SUIT_IDLE;
	/** Absolute game-time the current suit-up/suit-down animation started. */
	public long suitAnimStartTick;
	/** {@link ConstructType} ordinal currently selected for the C key. */
	public int selectedConstruct = ConstructType.HARD_LIGHT_WALL.ordinal();
	/** {@code abilityId} -> absolute game-time it is ready again (survives relog/death/dimension). */
	public final Map<String, Long> abilityReadyAt;

	public GreenLanternState() {
		this(false, GreenLanternConfig.MAX_RING_CHARGE, false, SUIT_IDLE, 0L,
				ConstructType.HARD_LIGHT_WALL.ordinal(), new HashMap<>());
	}

	public GreenLanternState(boolean hasPower, float ringCharge, boolean suited, int suitAnimDir,
			long suitAnimStartTick, int selectedConstruct, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.ringCharge = ringCharge;
		this.suited = suited;
		this.suitAnimDir = suitAnimDir;
		this.suitAnimStartTick = suitAnimStartTick;
		this.selectedConstruct = selectedConstruct;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public GreenLanternState copy() {
		return new GreenLanternState(hasPower, ringCharge, suited, suitAnimDir, suitAnimStartTick,
				selectedConstruct, abilityReadyAt);
	}

	public ConstructType selectedConstructType() {
		return ConstructType.byOrdinal(selectedConstruct);
	}

	public static final Codec<GreenLanternState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("ring_charge", GreenLanternConfig.MAX_RING_CHARGE).forGetter(s -> s.ringCharge),
			Codec.BOOL.optionalFieldOf("suited", false).forGetter(s -> s.suited),
			Codec.INT.optionalFieldOf("suit_anim_dir", SUIT_IDLE).forGetter(s -> s.suitAnimDir),
			Codec.LONG.optionalFieldOf("suit_anim_start_tick", 0L).forGetter(s -> s.suitAnimStartTick),
			Codec.INT.optionalFieldOf("selected_construct", ConstructType.HARD_LIGHT_WALL.ordinal())
					.forGetter(s -> s.selectedConstruct),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(instance, GreenLanternState::new));
}
