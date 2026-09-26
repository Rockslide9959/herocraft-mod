package com.projecthero.mod.wolverine.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole Wolverine Hero-Tier power for one player in one namespaced attachment
 * ({@code projecthero:wolverine_state}), same shape as {@code PunisherState}. Persistent +
 * {@code copyOnDeath} and synced to everyone: other clients need {@link #hasPower}/{@link #clawsOut}
 * to draw the claws, {@link #rageUntil} for the rage look, and the action fields to play the swing.
 * The server stays authoritative -- every check runs on the server-side copy.
 */
public final class WolverineState {
	public boolean hasPower;
	/** Claws deployed. */
	public boolean clawsOut;
	/** Game-time the claws last changed state -- the client eases the extension from this. */
	public long clawsChangedAt;
	/** Absolute game-time Berserker Rage ends (0 = inactive). */
	public long rageUntil;
	/** Absolute game-time the emergency heal may fire again. */
	public long emergencyReadyAt;
	/** Absolute game-time the current emergency heal stops ticking (0 = none running). */
	public long emergencyHealUntil;
	/** Absolute game-time the current Claw Dash ends (drives knockback immunity + the hit sweep). */
	public long dashUntil;
	/** Last combat action (1-6 = ability slot) and when -- drives the client's claw flash. */
	public int lastAction;
	public long lastActionTick;
	/** Berserker Rage bar, 0..RAGE_BAR_MAX; filled by taking and dealing damage, emptied when the rage starts. */
	public float rageMeter;
	/** Game-time the player started holding the Adamantium Execution key (0 = not charging). */
	public long chargeStartedAt;
	/** Game-time the emergency resurrection began (0 = never) -- drives the flesh -> skin fade on every client. */
	public long fleshStartedAt;
	/** Game-time this Wolverine last dealt or took damage -- gates the Rage bar's slow drain. */
	public long lastCombatAt;
	/** v0.12.40: the Healing Factor pool (0..HEAL_POOL_MAX). Every HP he heals passively is paid from it; at 0 the healing factor is off. */
	public float healPool;
	/** v0.12.40: game time he last TOOK damage -- the pool only refills once this is 5 s old. */
	public long lastHurtAt;
	/** v0.12.43: game time he survived a lethal fall (0 = never) -- his legs show the raw flesh model for 20 s, then the skin fades back over 20 s. */
	public long legFleshStartedAt;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public WolverineState() {
		this(false, false, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0.0f, 0L, 0L, 0L, new HashMap<>(), 250.0f, 0L, 0L);
	}

	public WolverineState(boolean hasPower, boolean clawsOut, long clawsChangedAt, long rageUntil,
			long emergencyReadyAt, long emergencyHealUntil, long dashUntil, int lastAction, long lastActionTick,
			float rageMeter, long chargeStartedAt, long fleshStartedAt, long lastCombatAt,
			Map<String, Long> abilityReadyAt, float healPool, long lastHurtAt, long legFleshStartedAt) {
		this.legFleshStartedAt = legFleshStartedAt;
		this.healPool = healPool;
		this.lastHurtAt = lastHurtAt;
		this.fleshStartedAt = fleshStartedAt;
		this.lastCombatAt = lastCombatAt;
		this.hasPower = hasPower;
		this.clawsOut = clawsOut;
		this.clawsChangedAt = clawsChangedAt;
		this.rageUntil = rageUntil;
		this.emergencyReadyAt = emergencyReadyAt;
		this.emergencyHealUntil = emergencyHealUntil;
		this.dashUntil = dashUntil;
		this.lastAction = lastAction;
		this.lastActionTick = lastActionTick;
		this.rageMeter = rageMeter;
		this.chargeStartedAt = chargeStartedAt;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public WolverineState copy() {
		return new WolverineState(hasPower, clawsOut, clawsChangedAt, rageUntil, emergencyReadyAt,
				emergencyHealUntil, dashUntil, lastAction, lastActionTick, rageMeter, chargeStartedAt, fleshStartedAt, lastCombatAt, abilityReadyAt, healPool, lastHurtAt, legFleshStartedAt);
	}

	public static final Codec<WolverineState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("claws_out", false).forGetter(s -> s.clawsOut),
			Codec.LONG.optionalFieldOf("claws_changed_at", 0L).forGetter(s -> s.clawsChangedAt),
			Codec.LONG.optionalFieldOf("rage_until", 0L).forGetter(s -> s.rageUntil),
			Codec.LONG.optionalFieldOf("emergency_ready_at", 0L).forGetter(s -> s.emergencyReadyAt),
			Codec.LONG.optionalFieldOf("emergency_heal_until", 0L).forGetter(s -> s.emergencyHealUntil),
			Codec.LONG.optionalFieldOf("dash_until", 0L).forGetter(s -> s.dashUntil),
			Codec.INT.optionalFieldOf("last_action", 0).forGetter(s -> s.lastAction),
			Codec.LONG.optionalFieldOf("last_action_tick", 0L).forGetter(s -> s.lastActionTick),
			Codec.FLOAT.optionalFieldOf("rage_meter", 0.0f).forGetter(s -> s.rageMeter),
			Codec.LONG.optionalFieldOf("charge_started_at", 0L).forGetter(s -> s.chargeStartedAt),
			Codec.LONG.optionalFieldOf("flesh_started_at", 0L).forGetter(s -> s.fleshStartedAt),
			Codec.LONG.optionalFieldOf("last_combat_at", 0L).forGetter(s -> s.lastCombatAt),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			// the v0.12.40+ fields live in one nested record: the state has hit the codec's 16-field limit
			Extra.CODEC.optionalFieldOf("extra", Extra.DEFAULT).forGetter(s -> new Extra(s.healPool, s.lastHurtAt, s.legFleshStartedAt))
	).apply(instance, (hasPower, clawsOut, clawsChangedAt, rageUntil, emergencyReadyAt, emergencyHealUntil, dashUntil, lastAction,
			lastActionTick, rageMeter, chargeStartedAt, fleshStartedAt, lastCombatAt, abilityReadyAt, extra) -> new WolverineState(hasPower,
			clawsOut, clawsChangedAt, rageUntil, emergencyReadyAt, emergencyHealUntil, dashUntil, lastAction, lastActionTick, rageMeter,
			chargeStartedAt, fleshStartedAt, lastCombatAt, abilityReadyAt, extra.healPool(), extra.lastHurtAt(), extra.legFleshStartedAt())));

	/** Healing Factor pool, last-hurt time and the lethal-fall leg-flesh time (v0.12.40 / v0.12.43). */
	public record Extra(float healPool, long lastHurtAt, long legFleshStartedAt) {
		public static final Extra DEFAULT = new Extra(250.0f, 0L, 0L);
		public static final Codec<Extra> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.optionalFieldOf("heal_pool", 250.0f).forGetter(Extra::healPool),
				Codec.LONG.optionalFieldOf("last_hurt_at", 0L).forGetter(Extra::lastHurtAt),
				Codec.LONG.optionalFieldOf("leg_flesh_started_at", 0L).forGetter(Extra::legFleshStartedAt)
		).apply(i, Extra::new));
	}
}
