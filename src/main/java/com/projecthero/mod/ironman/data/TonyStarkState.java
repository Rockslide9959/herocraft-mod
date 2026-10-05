package com.projecthero.mod.ironman.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every bit of the Iron Man / Tony Stark progression, in one object under one namespaced attachment
 * key ({@code projecthero:tony_stark_state}). Deliberately isolated from every Thor attachment and from
 * {@link com.projecthero.mod.hero.data.ExperimentalState} so the three permanent-power stores can never
 * be read as one another.
 *
 * <p>This is the "same way other permanent powers currently persist" store the design calls for: it
 * mirrors {@code ExperimentalState} exactly (own attachment, persistent + copyOnDeath, synced
 * target-only, {@link com.mojang.serialization.Codec}-backed) rather than inventing a new mechanism.
 * The Tony Stark power is a Hero-Tier power like Thor -- it is NOT one of the 27 experimental
 * mutations -- so it needs its own fields (technology level, per-suit energy/integrity, which suit is
 * active) that would not fit the mutation store.
 *
 * <p>Keys used inside the maps/sets are suit ids: {@code mark_1}, {@code mark_2}, {@code mark_iii}, {@code mark_4},
 * {@code mark_v}, {@code mark_6}, {@code mark_vii}. Iron Man ability cooldowns are keyed {@code suitId + "/" + abilityId}
 * and stored as an absolute "ready-at game time" so they survive relog/death/dimension change, exactly
 * like the experimental system's cooldowns.
 */
public final class TonyStarkState {
	/** Whether the player has the permanent Tony Stark Hero-Tier power. */
	public boolean hasPower;
	/**
	 * Iron Man technology tier reached: 0 = Tony Stark acquired, 1 = Mark III, 2 = Mark V, 3 = Mark VII
	 * (the highest any suit needs). Gates the suit-up / summon checks; the Blank Blueprint progression gates the Fabricator.
	 */
	public int techLevel;
	/**
	 * Iron Man build progress. Holds two kinds of entry:
	 * <ul>
	 *   <li>a bare suit id ({@code mark_iii}) once the player has built <b>every</b> piece of that mark
	 *       -- this is what {@link com.projecthero.mod.ironman.TonyStark#hasBuilt} reports and what the
	 *       suit-up / summon gates read;</li>
	 *   <li>"changes 21": a per-piece marker {@code suitId + "/" + pieceName}
	 *       ({@code mark_iii/chestplate}, ...) for every individual armour piece built -- the Stark
	 *       Fabricator adds one on each armour completion, {@code IronManArmorItem#onCraftedBy} adds one
	 *       for each table-built Mark 1 piece. When all four of a mark's markers are present the bare
	 *       suit id is added too, which unlocks the next mark's blueprint in the Blank Blueprint picker
	 *       (the linear Mark 1 -> 2 -> III -> 4 -> V -> 6 -> VII gate).</li>
	 * </ul>
	 * Kept as one set (the save is nested since v0.14.21 round two, see {@link #CODEC}).
	 */
	public final Set<String> builtSuits;
	/** Which suit is currently equipped/deployed, or "" for none. */
	public String activeSuit;
	/** suit id -> current suit energy. */
	public final Map<String, Float> suitEnergy;
	/** suit id -> current suit integrity (0..100). */
	public final Map<String, Float> suitIntegrity;
	/** {@code suitId + "/" + abilityId} -> absolute game-time the ability is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** Persistent V-slot state for suits with {@link com.projecthero.mod.ironman.suit.IronManSuit#toggleableHighlight()}
	 *  (Mark 1 / Mark 2) -- while true, the always-on Iron Man threat highlight is active for the wearer. */
	public boolean mobHighlightOn;
	/**
	 * Mark 1 Flamethrower heat gauge, 0..{@code FLAMETHROWER_MAX_HEAT} ("changes 14"). Climbs while the
	 * stream is held, vents while idle; at max the flamethrower overheats and cuts out. Synced so the HUD
	 * can draw the heat bar, exactly like Pyrokinesis's flamethrower ability.
	 */
	public float flamethrowerHeat;
	/**
	 * Absolute game-time a Mark 1 timed-flight burst (X) forces itself off, or 0 if not active
	 * ("changes 15"). Synced (not transient) so the HUD can draw the "flight remaining" bar. When the
	 * burst ends the {@code timed_flight} ability goes on a 13 s cooldown.
	 */
	public long timedFlightUntil;
	/**
	 * Absolute game-time the Mark 4 wrist-laser beam stops firing, or 0 ("changes 15"). Synced so the
	 * beam can be ticked / shown.
	 */
	public long wristLaserUntil;
	/**
	 * Absolute game-time a Mark 4 "systems overloaded" lockout ends, or 0 ("changes 15"). While in the
	 * future the whole suit behaves as if depleted (no abilities, no flight) and the HUD shows an
	 * "overloaded systems" bar counting down.
	 */
	public long overloadUntil;
	/**
	 * Suit ids whose one-shot wrist laser has been spent and not yet reloaded ("changes 15"). Cleared
	 * for a suit only when it is docked back into an Iron Man Suit Platform.
	 */
	public final Set<String> wristLaserSpent;
	/**
	 * "changes 16": which ability the Mark 7 weapon wheel has bound to slot 3 (X). Synced so the HUD
	 * and the wheel screen can show the current pick. One of the {@code IronManAbilities} ids
	 * {@code micro_missiles / flamethrower / wrist_laser / rocket / supersonic_flight}.
	 */
	public String weaponWheelChoice = com.projecthero.mod.ironman.ability.IronManAbilities.MICRO_MISSILES;

	// --- "changes 17" ---
	/**
	 * Absolute game-time Protocol Phoenix (the emergency resurrection ability) is off cooldown again, or
	 * 0. Persistent + synced so the cooldown survives death / relog / dimension change / dropping armour
	 * and the HUD can count it down.
	 */
	public long phoenixReadyAt;
	/** Built-in air-tank charge of the worn suit, 0..1 (1 = full). Synced for the HUD air bar. */
	public float suitAir = 1.0f;

	// --- transient (not persisted): the in-progress suit-up / suit-down animation ---
	/**
	 * While &gt; the current game-time: Protocol Phoenix has fired and the player is in the incapacitated
	 * "emergency suit inbound" state (invulnerable, blind, frozen in place) until the suit arrives or the
	 * failsafe elapses. 0 otherwise. Server-authoritative; the client is driven purely by action-bar
	 * messages so this does not need to be part of the synced codec.
	 */
	public transient long phoenixEmergencyUntil = 0L;
	/** "changes 17": absolute game-time a Mark 2 altitude-ceiling freeze ends, or 0. */
	public transient long ceilingFreezeUntil = 0L;
	/** "changes 17": was the player above the altitude ceiling last tick (so the freeze only fires on entry). */
	public transient boolean wasAboveCeiling = false;
	/** "changes 17": the suit id Protocol Phoenix is bringing in, or "". */
	public transient String phoenixSuitId = "";
	/** "changes 17": where that suit is coming from (IronManSuitListPayload.SOURCE_*). */
	public transient int phoenixSuitSource = 0;
	/**
	 * "changes 16": absolute game-time a Mark 7 supersonic-flight burst ends, or 0. Not saved; v0.14.16: synced through
	 * {@link #SYNC_CODEC} -- the client's directional flight drives the burst.
	 */
	public transient long supersonicUntil = 0L;
	/** suit id currently mid suit-up / suit-down, or "". */
	public transient String transitionSuit = "";
	/** ticks left in the sequence. Always counts down to 0; {@link #transitionUp} says which way. */
	public transient int transitionTicks = 0;
	/** full length of the current sequence, for progress-fraction maths. */
	public transient int transitionTotal = 0;
	/** true = assembling (suit-up), false = retracting (suit-down). */
	public transient boolean transitionUp = true;
	/** bitmask of armour slots still to equip/remove this sequence (bit 0 HEAD .. bit 3 FEET). */
	public transient int transitionMask = 0;
	/** "changes 15": this suit-down folds the pieces into the Mark V Suitcase item instead of the inventory. */
	public transient boolean transitionToCase = false;
	/** "changes 15": this suit-up materialises the pieces from the Mark V Suitcase, not the inventory. */
	public transient boolean transitionFromCase = false;
	/** v0.14.21: suit-down pieces whose plates are breaking away right now, removed once their release finishes (bit 0 HEAD .. 3 FEET). */
	public transient int transitionReleaseMask = 0;
	/** v0.14.27: the pieces a sequential (3 s per piece) suit-up planned at its start (bit 0 HEAD .. 3 FEET); 0 = none. */
	public transient int transitionPlan = 0;
	/** game time the R-slot (slot 1) hold started, or 0 if not held -- drives the charged-repulsor spin-up. */
	public transient long chargeStartTick = 0L;
	/** true once the current R-hold has crossed the charged threshold, so the "ready" cue only fires once. */
	public transient boolean chargeReadyPinged = false;
	/** game time until which the Unibeam continuous beam is firing, or 0. */
	public transient long unibeamUntil = 0L;
	/** true while the Repulsor Barrier (slot 2 / G) is being held down -- it stays up as long as the
	 *  key is held and there is energy; releasing (or running dry) drops it and starts the cooldown. */
	public transient boolean barrierHeld = false;
	/** true while the flamethrower (Mark 1 / G) is held down. */
	public transient boolean flamethrowerHeld = false;
	/** absolute game-time a windup-repulsor shot (Mark 2 / R) actually fires, or 0 if none pending. */
	public transient long repulsorWindupAt = 0L;
	/** "changes 18": micro-missiles left to launch in the current volley (fired one at a time). */
	public transient int pendingMissiles = 0;
	/** "changes 18": absolute game-time the next micro-missile in the volley launches. */
	public transient long pendingMissileNextTick = 0L;
	/** "changes 18": suit id the in-progress micro-missile volley belongs to, or "". */
	public transient String pendingMissileSuit = "";
	/** v0.14.21 round two: the in-progress volley is Homing Missiles (locked on to {@link #pendingMissileTargetId}). */
	public transient boolean pendingMissileHoming = false;
	/** v0.14.21 round two: entity id the Homing Missiles volley is locked on to, or -1. */
	public transient int pendingMissileTargetId = -1;

	public TonyStarkState() {
		this(false, 0, new HashSet<>(), "", new HashMap<>(), new HashMap<>(), new HashMap<>(), false, 0.0f,
				0L, 0L, 0L, new HashSet<>(),
				com.projecthero.mod.ironman.ability.IronManAbilities.MICRO_MISSILES, 0L, 1.0f);
	}

	public TonyStarkState(boolean hasPower, int techLevel, Set<String> builtSuits,
			String activeSuit,
			Map<String, Float> suitEnergy, Map<String, Float> suitIntegrity, Map<String, Long> abilityReadyAt,
			boolean mobHighlightOn, float flamethrowerHeat,
			long timedFlightUntil, long wristLaserUntil, long overloadUntil, Set<String> wristLaserSpent,
			String weaponWheelChoice, long phoenixReadyAt, float suitAir) {
		this.hasPower = hasPower;
		this.techLevel = techLevel;
		this.builtSuits = new HashSet<>(builtSuits);
		this.activeSuit = activeSuit == null ? "" : activeSuit;
		this.suitEnergy = new HashMap<>(suitEnergy);
		this.suitIntegrity = new HashMap<>(suitIntegrity);
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.mobHighlightOn = mobHighlightOn;
		this.flamethrowerHeat = flamethrowerHeat;
		this.timedFlightUntil = timedFlightUntil;
		this.wristLaserUntil = wristLaserUntil;
		this.overloadUntil = overloadUntil;
		this.wristLaserSpent = new HashSet<>(wristLaserSpent);
		this.weaponWheelChoice = weaponWheelChoice == null
				? com.projecthero.mod.ironman.ability.IronManAbilities.MICRO_MISSILES : weaponWheelChoice;
		this.phoenixReadyAt = phoenixReadyAt;
		this.suitAir = suitAir;
	}

	public TonyStarkState copy() {
		TonyStarkState c = new TonyStarkState(hasPower, techLevel, builtSuits, activeSuit,
				suitEnergy, suitIntegrity, abilityReadyAt, mobHighlightOn, flamethrowerHeat,
				timedFlightUntil, wristLaserUntil, overloadUntil, wristLaserSpent, weaponWheelChoice,
				phoenixReadyAt, suitAir);
		c.supersonicUntil = supersonicUntil;
		c.ceilingFreezeUntil = ceilingFreezeUntil;
		c.wasAboveCeiling = wasAboveCeiling;
		c.phoenixEmergencyUntil = phoenixEmergencyUntil;
		c.phoenixSuitId = phoenixSuitId;
		c.phoenixSuitSource = phoenixSuitSource;
		c.transitionSuit = transitionSuit;
		c.transitionTicks = transitionTicks;
		c.transitionTotal = transitionTotal;
		c.transitionUp = transitionUp;
		c.transitionMask = transitionMask;
		c.transitionToCase = transitionToCase;
		c.transitionFromCase = transitionFromCase;
		c.transitionReleaseMask = transitionReleaseMask;
		c.transitionPlan = transitionPlan;
		c.chargeStartTick = chargeStartTick;
		c.chargeReadyPinged = chargeReadyPinged;
		c.unibeamUntil = unibeamUntil;
		c.barrierHeld = barrierHeld;
		c.flamethrowerHeld = flamethrowerHeld;
		c.repulsorWindupAt = repulsorWindupAt;
		c.pendingMissiles = pendingMissiles;
		c.pendingMissileNextTick = pendingMissileNextTick;
		c.pendingMissileSuit = pendingMissileSuit;
		c.pendingMissileHoming = pendingMissileHoming;
		c.pendingMissileTargetId = pendingMissileTargetId;
		return c;
	}

	private static final String DEFAULT_WHEEL = com.projecthero.mod.ironman.ability.IronManAbilities.MICRO_MISSILES;
	private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf().xmap(HashSet::new, java.util.ArrayList::new);
	private static final Codec<Map<String, Float>> FLOAT_MAP = Codec.unboundedMap(Codec.STRING, Codec.FLOAT).xmap(HashMap::new, HashMap::new);
	private static final Codec<Map<String, Long>> LONG_MAP = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(HashMap::new, HashMap::new);

	/**
	 * v0.14.21 round two: the pre-0.14.21 <b>flat</b> save shape (16 top-level fields -- the {@code RecordCodecBuilder}
	 * maximum). Still read by {@link #CODEC} so every old world loads; never written any more.
	 */
	public static final Codec<TonyStarkState> LEGACY_FLAT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.INT.optionalFieldOf("tech_level", 0).forGetter(s -> s.techLevel),
			STRING_SET.optionalFieldOf("built_suits", new HashSet<>()).forGetter(s -> new HashSet<>(s.builtSuits)),
			Codec.STRING.optionalFieldOf("active_suit", "").forGetter(s -> s.activeSuit),
			FLOAT_MAP.optionalFieldOf("suit_energy", new HashMap<>()).forGetter(s -> new HashMap<>(s.suitEnergy)),
			FLOAT_MAP.optionalFieldOf("suit_integrity", new HashMap<>()).forGetter(s -> new HashMap<>(s.suitIntegrity)),
			LONG_MAP.optionalFieldOf("ability_ready_at", new HashMap<>()).forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.BOOL.optionalFieldOf("mob_highlight_on", false).forGetter(s -> s.mobHighlightOn),
			Codec.FLOAT.optionalFieldOf("flamethrower_heat", 0.0f).forGetter(s -> s.flamethrowerHeat),
			Codec.LONG.optionalFieldOf("timed_flight_until", 0L).forGetter(s -> s.timedFlightUntil),
			Codec.LONG.optionalFieldOf("wrist_laser_until", 0L).forGetter(s -> s.wristLaserUntil),
			Codec.LONG.optionalFieldOf("overload_until", 0L).forGetter(s -> s.overloadUntil),
			STRING_SET.optionalFieldOf("wrist_laser_spent", new HashSet<>()).forGetter(s -> new HashSet<>(s.wristLaserSpent)),
			Codec.STRING.optionalFieldOf("weapon_wheel_choice", DEFAULT_WHEEL).forGetter(s -> s.weaponWheelChoice),
			Codec.LONG.optionalFieldOf("phoenix_ready_at", 0L).forGetter(s -> s.phoenixReadyAt),
			Codec.FLOAT.optionalFieldOf("suit_air", 1.0f).forGetter(s -> s.suitAir)
	).apply(instance, TonyStarkState::new));

	/** Progression: the power, the tech tier, what has been built, which suit is active. */
	private record Core(boolean hasPower, int techLevel, Set<String> builtSuits, String activeSuit) {
		static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("has_power", false).forGetter(Core::hasPower),
				Codec.INT.optionalFieldOf("tech_level", 0).forGetter(Core::techLevel),
				STRING_SET.optionalFieldOf("built_suits", new HashSet<>()).forGetter(Core::builtSuits),
				Codec.STRING.optionalFieldOf("active_suit", "").forGetter(Core::activeSuit)
		).apply(i, Core::new));
	}

	/** Per-suit state and suit settings. */
	private record Suit(Map<String, Float> energy, Map<String, Float> integrity, float air, boolean mobHighlightOn,
			String weaponWheelChoice) {
		static final Codec<Suit> CODEC = RecordCodecBuilder.create(i -> i.group(
				FLOAT_MAP.optionalFieldOf("energy", new HashMap<>()).forGetter(Suit::energy),
				FLOAT_MAP.optionalFieldOf("integrity", new HashMap<>()).forGetter(Suit::integrity),
				Codec.FLOAT.optionalFieldOf("air", 1.0f).forGetter(Suit::air),
				Codec.BOOL.optionalFieldOf("mob_highlight_on", false).forGetter(Suit::mobHighlightOn),
				Codec.STRING.optionalFieldOf("weapon_wheel_choice", DEFAULT_WHEEL).forGetter(Suit::weaponWheelChoice)
		).apply(i, Suit::new));
	}

	/** Every absolute "ready-at" / "until" game time. */
	private record Cooldowns(Map<String, Long> abilityReadyAt, long timedFlightUntil, long wristLaserUntil,
			long overloadUntil, long phoenixReadyAt) {
		static final Codec<Cooldowns> CODEC = RecordCodecBuilder.create(i -> i.group(
				LONG_MAP.optionalFieldOf("ability_ready_at", new HashMap<>()).forGetter(Cooldowns::abilityReadyAt),
				Codec.LONG.optionalFieldOf("timed_flight_until", 0L).forGetter(Cooldowns::timedFlightUntil),
				Codec.LONG.optionalFieldOf("wrist_laser_until", 0L).forGetter(Cooldowns::wristLaserUntil),
				Codec.LONG.optionalFieldOf("overload_until", 0L).forGetter(Cooldowns::overloadUntil),
				Codec.LONG.optionalFieldOf("phoenix_ready_at", 0L).forGetter(Cooldowns::phoenixReadyAt)
		).apply(i, Cooldowns::new));
	}

	/** Weapon state that fits nowhere else. Add new persisted fields to a group with room (or a new group). */
	private record Misc(float flamethrowerHeat, Set<String> wristLaserSpent) {
		static final Codec<Misc> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.optionalFieldOf("flamethrower_heat", 0.0f).forGetter(Misc::flamethrowerHeat),
				STRING_SET.optionalFieldOf("wrist_laser_spent", new HashSet<>()).forGetter(Misc::wristLaserSpent)
		).apply(i, Misc::new));
	}

	/**
	 * v0.14.21 round two: the nested save shape {@code {core:{..}, suit:{..}, cooldowns:{..}, misc:{..}}}. {@code core}
	 * is <em>required</em> -- that is what makes an old flat blob (which has no {@code core}) fail this codec and fall
	 * back to {@link #LEGACY_FLAT_CODEC} inside {@link #CODEC}. Every group has room for more fields.
	 */
	public static final Codec<TonyStarkState> NESTED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Core.CODEC.fieldOf("core").forGetter(s -> new Core(s.hasPower, s.techLevel, new HashSet<>(s.builtSuits), s.activeSuit)),
			Suit.CODEC.optionalFieldOf("suit", new Suit(new HashMap<>(), new HashMap<>(), 1.0f, false, DEFAULT_WHEEL))
					.forGetter(s -> new Suit(new HashMap<>(s.suitEnergy), new HashMap<>(s.suitIntegrity), s.suitAir,
							s.mobHighlightOn, s.weaponWheelChoice)),
			Cooldowns.CODEC.optionalFieldOf("cooldowns", new Cooldowns(new HashMap<>(), 0L, 0L, 0L, 0L))
					.forGetter(s -> new Cooldowns(new HashMap<>(s.abilityReadyAt), s.timedFlightUntil, s.wristLaserUntil,
							s.overloadUntil, s.phoenixReadyAt)),
			Misc.CODEC.optionalFieldOf("misc", new Misc(0.0f, new HashSet<>()))
					.forGetter(s -> new Misc(s.flamethrowerHeat, new HashSet<>(s.wristLaserSpent)))
	).apply(instance, (core, suit, cd, misc) -> new TonyStarkState(core.hasPower(), core.techLevel(), core.builtSuits(),
			core.activeSuit(), suit.energy(), suit.integrity(), cd.abilityReadyAt(), suit.mobHighlightOn(),
			misc.flamethrowerHeat(), cd.timedFlightUntil(), cd.wristLaserUntil(), cd.overloadUntil(),
			misc.wristLaserSpent(), suit.weaponWheelChoice(), cd.phoenixReadyAt(), suit.air())));

	/**
	 * The persisted codec: always <b>writes</b> {@link #NESTED_CODEC}; <b>reads</b> the nested shape and, failing that
	 * (a save from before v0.14.21 round two), the old flat shape.
	 */
	public static final Codec<TonyStarkState> CODEC = Codec.withAlternative(NESTED_CODEC, LEGACY_FLAT_CODEC);

	/**
	 * v0.14.16: what the attachment syncs -- the saved {@link #CODEC} plus
	 * {@link #supersonicUntil}, which the client's directional flight needs to drive the supersonic burst but which is
	 * never saved (a relog drops the burst, as before).
	 */
	public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, TonyStarkState> SYNC_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(
					net.minecraft.network.codec.ByteBufCodecs.fromCodec(CODEC), s -> s,
					net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, s -> s.supersonicUntil,
					(s, supersonicUntil) -> {
						s.supersonicUntil = supersonicUntil;
						return s;
					});
}
