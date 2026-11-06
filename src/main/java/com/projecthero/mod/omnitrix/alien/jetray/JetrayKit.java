package com.projecthero.mod.omnitrix.alien.jetray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix): Jetray, the Aerophibian -- a crimson manta ray built for high-speed flight through air, space
 * and water. v0.17.2 revamp (docs/OMNITRIX_REVAMP_JETRAY.md; user: "can you work on revamps for the rest of the alien force aliens
 * please, look at their powers and abilities in the show and do research and make them good please"): an air-superiority striker
 * -- every neuroshock hit builds toward paralysis (the show's "shuts the nervous system down"), his tail carries a creature (or a
 * friend) through the sky, and strong winds and heavy hits knock him out of the air.
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Neuroshock Eyes   twin eye beam (24): the first creature, 7 + a Neuroshock stack
 *   Shift+R    Tail Blast        tail bolt (28): 14, paralysed 1.5 s
 *   G          Dive Strike       streak along the look: 15 to all you pass
 *   Shift+G    Tail Grab         the tail grabs the creature you look at (5): 4, carried under him up to 5 s; Shift+G again drops it
 *   X          Wing Beat         round 4: one huge stroke of the wings throws him along the look (off the ground: up into flight); the
 *                                downwash: 6 in 4, shoved; flight itself is a double-tap of jump
 *   Shift+X    Supersonic Flight round 4: HOLD while flying: 3 s of charging, a sonic boom (12 in 3, shoved), then 200 blocks a second along
 *                                the look with a green trail until the keys come up
 *   Z          Neuroshock Barrage five beams in a fan (16): 6 each + a stack
 *   Shift+Z    Neuroshock Overload ULTIMATE: 1 s charge (held still), a 30-block, 3-wide beam: 60, paralysed 3 s
 *   C          Dive Bomb         climb, then plunge: r 4, 18, paralysed 1 s
 *   Shift+C    Neuroshock Pulse  a ring (r 5): 13 + Slowness II 3 s + a stack
 *   V          Streamline        toggle: faster flight, swimming and running
 *   Shift+V    Barrel Roll       a sideways roll: 0.75 s untouchable by shots, half other damage
 *   Attack     Wing Ram          in flight his hits gain +1 for every 0.4 blocks/tick of speed (up to +5) and knock back
 *   Use        Wing Slash        (empty hand) the wing edge sweeps in front (3.5): 6, shoved
 *   Passives   Aerophibian (no fall / wall damage, never drowns, Dolphin's Grace); Neuroshock (3 stacks in 4 s = paralysed 1 s);
 *              Insulated (lightning halved)
 *   Weak       strong winds (wind charges / blasts: +50% and knocked out of the air); clipped wings (a hit of 8+ in flight stalls him
 *              1.5 s); light body (24 health, 3 armour)
 * </pre>
 */
public final class JetrayKit extends AlienKit {
	public static final String ID = Aliens.JETRAY.id();
	public static final int SHOCK = 0xC8FF3A;

	// ---------------------------------------------------------------- sounds (vanilla samples, quiet and layered)
	public static final OmnitrixSfx.Cue ZAP = OmnitrixSfx.cue("jetray_zap")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.25f, 1.9f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.6f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.12f, 1.8f);
	public static final OmnitrixSfx.Cue TAIL = OmnitrixSfx.cue("jetray_tail")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.6f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.12f, 1.9f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 1.4f, 2);
	// v0.17.2 (user: "whenever i transform into him his flight sound effect plays constantly even if he isnt flying"): no ELYTRA_FLYING layer -- that
	// sample is a ~10 s drone, so every whoosh (the transform one too) left it droning on the ground; short wind sounds instead
	public static final OmnitrixSfx.Cue WHOOSH = OmnitrixSfx.cue("jetray_whoosh")
			.layer(SoundEvents.PHANTOM_FLAP, 0.35f, 0.9f)
			.layer(SoundEvents.PHANTOM_SWOOP, 0.15f, 1.5f)
			.gap(6);
	public static final OmnitrixSfx.Cue DIVE = OmnitrixSfx.cue("jetray_dive")
			.layer(SoundEvents.WIND_CHARGE_THROW, 0.4f, 0.8f)
			.layer(SoundEvents.PHANTOM_SWOOP, 0.35f, 1.2f);
	public static final OmnitrixSfx.Cue IMPACT = OmnitrixSfx.cue("jetray_impact")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.18f, 1.5f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.15f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.35f, 0.8f);
	public static final OmnitrixSfx.Cue SLASH = OmnitrixSfx.cue("jetray_slash")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 0.9f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.3f, 1.3f);
	public static final OmnitrixSfx.Cue PULSE = OmnitrixSfx.cue("jetray_pulse")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 1.0f);
	public static final OmnitrixSfx.Cue STREAM = OmnitrixSfx.cue("jetray_stream")
			.layer(SoundEvents.WIND_CHARGE_BURST.value(), 0.2f, 1.6f) // v0.17.2: not the ~10 s elytra drone
			.layer(SoundEvents.DOLPHIN_PLAY, 0.25f, 0.8f);
	public static final OmnitrixSfx.Cue SCAN = OmnitrixSfx.cue("jetray_scan")
			.layer(SoundEvents.DOLPHIN_AMBIENT_WATER, 0.3f, 0.7f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25f, 0.6f, 3);
	public static final OmnitrixSfx.Cue CHARGE = OmnitrixSfx.cue("jetray_charge")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.35f, 0.6f)
			.layer(SoundEvents.CONDUIT_ACTIVATE, 0.3f, 1.4f);
	public static final OmnitrixSfx.Cue OVERLOAD = OmnitrixSfx.cue("jetray_overload")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.3f, 1.3f)
			.layer(SoundEvents.BEACON_ACTIVATE, 0.4f, 1.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.8f, 2);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue BOOM = OmnitrixSfx.cue("jetray_boom")
			.layer(SoundEvents.WIND_CHARGE_BURST.value(), 0.45f, 0.7f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.9f)
			.layer(SoundEvents.PHANTOM_SWOOP, 0.2f, 2.0f, 2); // v0.17.2: not the ~10 s elytra drone
	public static final OmnitrixSfx.Cue PARALYSE = OmnitrixSfx.cue("jetray_paralyse")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 1.8f, 1);
	public static final OmnitrixSfx.Cue GRAB = OmnitrixSfx.cue("jetray_grab")
			.layer(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.3f, 1.4f)
			.layer(SoundEvents.PHANTOM_BITE, 0.3f, 1.2f);
	public static final OmnitrixSfx.Cue STALL = OmnitrixSfx.cue("jetray_stall")
			.layer(SoundEvents.PHANTOM_HURT, 0.35f, 0.7f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.25f, 0.8f);
	// round 4
	public static final OmnitrixSfx.Cue BEAT = OmnitrixSfx.cue("jetray_beat")
			.layer(SoundEvents.PHANTOM_FLAP, 0.6f, 0.6f)
			.layer(SoundEvents.WIND_CHARGE_BURST.value(), 0.3f, 0.8f);
	public static final OmnitrixSfx.Cue SONIC_HUM = OmnitrixSfx.cue("jetray_sonic_hum")
			.layer(SoundEvents.BEACON_AMBIENT, 0.5f, 1.2f)
			.layer(SoundEvents.PHANTOM_SWOOP, 0.12f, 1.6f);
	public static final OmnitrixSfx.Cue SONIC_RUSH = OmnitrixSfx.cue("jetray_sonic_rush")
			.layer(SoundEvents.WIND_CHARGE_BURST.value(), 0.25f, 0.5f)
			.layer(SoundEvents.PHANTOM_SWOOP, 0.3f, 0.7f);

	// ---------------------------------------------------------------- poses (client: JetrayClient)
	public static final int ANIM_EYES = 1, ANIM_TAIL = 2, ANIM_DIVE = 3, ANIM_SLASH = 4, ANIM_SPREAD = 7,
			ANIM_PULSE = 8, ANIM_STREAM = 9, ANIM_ROLL = 10, ANIM_THUNDER = 11, ANIM_OVERLOAD = 12, ANIM_GRAB = 13, ANIM_RAM = 14,
			ANIM_BEAT = 15, ANIM_SONIC_CHARGE = 16, ANIM_SONIC = 17, ANIM_SONIC_END = 18;

	public static final int F_FLYING = 1;
	public static final int F_STREAM = 2;
	public static final int F_DIVING = 4;
	public static final int F_OVERLOAD = 8;
	public static final int F_THUNDER = 16;
	public static final int F_CARRY = 32;
	public static final int F_ROLL = 64;
	public static final int F_STALL = 128;
	/** Round 4: Shift+X held, charging; Shift+X held, past the barrier. */
	public static final int F_SONIC_CHARGE = 256, F_SONIC = 512;

	// ---------------------------------------------------------------- moves
	public static final String EYES = "neuroshock_eyes", TAIL_BLAST = "tail_blast", DIVE_STRIKE = "dive_strike", TAIL_GRAB = "tail_grab",
			WING_BEAT = "wing_beat", SUPERSONIC = "supersonic_flight", SPREAD = "neuroshock_barrage", OVERLOAD_MOVE = "neuroshock_overload",
			DIVE_BOMB = "dive_bomb", SHOCK_PULSE = "neuroshock_pulse", STREAMLINE = "streamline", ROLL = "barrel_roll", RAM = "wing_ram",
			WING_SLASH = "wing_slash";

	/** Synced kit timers. */
	public static final String DIVE_END = "dive_end", THUNDER_AT = "thunder_at", THUNDER_END = "thunder_end", OVERLOAD_AT = "overload_at",
			CARRY_TARGET = "carry_target", CARRY_UNTIL = "carry_until", ROLL_UNTIL = "roll_until", STALL_UNTIL = "stall_until", SONIC_AT = "sonic_at", SONIC_TAIL = "sonic_tail",
			BEAT_AT = "beat_at", BEAT_LIFT = "beat_lift";

	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md)
	public static final float EYES_DAMAGE = 11f;
	public static final double EYES_RANGE = 24.0;
	public static final float TAIL_DAMAGE = 24f;
	public static final double TAIL_RANGE = 28.0;
	public static final int TAIL_STUN = 30;
	public static final float DIVE_DAMAGE = 26f;
	public static final int DIVE_TICKS = 12;
	public static final float GRAB_DAMAGE = 9f;
	public static final double GRAB_RANGE = 5.0;
	public static final int CARRY_TICKS = 100, GRAB_COOLDOWN = 200;
	public static final float BOOM_DAMAGE = 12f;
	public static final double BOOM_RADIUS = 3.0;
	/**
	 * Round 4 (user: "dont make x his flight, he already flies by double pressing space bar, put another move in x"): Wing Beat. In the show
	 * he goes from a standstill to flat out in one stroke of those manta wings. {@link #BEAT_WINDUP} ticks raising the wings, then the stroke.
	 */
	public static final float BEAT_DAMAGE = 6f;
	public static final double BEAT_RADIUS = 4.0, BEAT_SPEED = 2.4;
	public static final int BEAT_WINDUP = 3, BEAT_COOLDOWN = 60;
	/**
	 * Round 4 (user: "for supersonic flight make it so that while hes holding shift+X while flying he gets a green trail and flies at 200
	 * blocks per second please, player needs to hold shift+X for 3 seconds tho and there must be a chargeup animation aswell"): the speed
	 * and its chunk / terrain safety are {@code DirectionalFlightModel.jetraySupersonic}. The cooldown starts when he lets go.
	 */
	public static final int SONIC_CHARGE = 60, SONIC_COOLDOWN = 100, SONIC_CANCEL_COOLDOWN = 20;
	/** Ticks past the end the server still accepts his last fast moves ({@link #fastMove}). */
	public static final int SONIC_TAIL_TICKS = 20;
	public static final float SPREAD_DAMAGE = 10f;
	public static final double SPREAD_RANGE = 16.0;
	public static final float PULSE_DAMAGE = 22f;
	public static final double PULSE_RADIUS = 5.0;
	public static final float THUNDER_DAMAGE = 30f;
	public static final double THUNDER_RADIUS = 4.0;
	public static final int THUNDER_STUN = 20;
	public static final float OVERLOAD_DAMAGE = 100f;
	public static final double OVERLOAD_RANGE = 30.0;
	public static final int OVERLOAD_CHARGE = 20, OVERLOAD_STUN = 60;
	public static final float FLY_SPEED = 0.1f;
	public static final float STREAM_FLY_SPEED = 0.16f;
	public static final int ROLL_TICKS = 15;
	public static final float ROLL_TAKEN = 0.5f;
	public static final float RAM_PER = 1.6f, RAM_MAX = 8f;
	public static final double RAM_STEP = 0.4;
	public static final float SLASH_DAMAGE = 10f;
	public static final double SLASH_RANGE = 3.5;
	public static final int STACKS_TO_PARALYSE = 3, STACK_WINDOW = 80, PARALYSE_TICKS = 20;
	public static final float LIGHTNING_TAKEN = 0.5f, WIND_TAKEN = 1.5f, CLIP_THRESHOLD = 8f;
	public static final int STALL_TICKS = 30;

	private record Stack(int count, long last) {
	}

	/** Creatures already hit by the running Dive Strike (each once). */
	private static final Map<UUID, Set<Integer>> DIVE_HITS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> (creature id -> its Neuroshock stacks). */
	private static final Map<UUID, Map<Integer, Stack>> STACKS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the carry and the dives must not double up -- mock players can tick twice). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	public static void register() {
		Aliens.register(new JetrayKit());
	}

	public static void clearSessionState() {
		DIVE_HITS.clear();
		STACKS.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(24).speed(1.2f).armor(7, 0).attack(5).knockbackResist(0.1f).safeFall(6).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(EYES, AbilitySlot.SLOT_1, 16), AlienMove.shift(TAIL_BLAST, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(DIVE_STRIKE, AbilitySlot.SLOT_2, 120), AlienMove.shift(TAIL_GRAB, AbilitySlot.SLOT_2, GRAB_COOLDOWN),
				AlienMove.tap(WING_BEAT, AbilitySlot.SLOT_3, BEAT_COOLDOWN), AlienMove.shift(SUPERSONIC, AbilitySlot.SLOT_3, SONIC_COOLDOWN),
				AlienMove.tap(SPREAD, AbilitySlot.SLOT_4, 100), AlienMove.shift(OVERLOAD_MOVE, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(STREAMLINE, AbilitySlot.SLOT_5, 10), AlienMove.shift(ROLL, AbilitySlot.SLOT_5, 120),
				AlienMove.tap(DIVE_BOMB, AbilitySlot.SLOT_6, 240), AlienMove.shift(SHOCK_PULSE, AbilitySlot.SLOT_6, 160),
				AlienMove.vanilla(RAM, "Attack (in flight)", 0), AlienMove.vanilla(WING_SLASH, "Use (empty hand)", 16));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean air = !p.onGround() && !p.isInWater() && !p.isPassenger();
		RecalKitMath.wings(p, air, FLY_SPEED);
		setFlag(p, F_FLYING, air);
		if (air) { // v0.17.2: no flight whoosh when he transforms standing on the ground
			OmnitrixSfx.playOn(p, WHOOSH, 1.0f, 1.0f, SoundSource.PLAYERS);
		}
	}

	private static final String[] STATE_TIMERS = { DIVE_END, THUNDER_AT, THUNDER_END, OVERLOAD_AT, CARRY_TARGET, CARRY_UNTIL, ROLL_UNTIL, STALL_UNTIL, SONIC_AT,
			SONIC_TAIL, BEAT_AT, BEAT_LIFT };

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		DIVE_HITS.remove(id);
		STACKS.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : STATE_TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		RecalKitMath.groundWings(p);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_CARRY);
			case SLOT_3 -> flag(p, F_SONIC_CHARGE) || flag(p, F_SONIC);
			case SLOT_4 -> flag(p, F_OVERLOAD);
			case SLOT_5 -> flag(p, F_STREAM);
			default -> false;
		};
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick (RipjawsKit.tick)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		// clipped wings: no flight until the stall wears off
		if (flag(p, F_STALL)) {
			if (now >= timer(p, STALL_UNTIL)) {
				setFlag(p, F_STALL, false);
				setTimer(p, STALL_UNTIL, 0);
			} else if (p.getAbilities().flying || p.getAbilities().mayfly) {
				p.getAbilities().flying = false;
				p.getAbilities().mayfly = false;
				p.onUpdateAbilities();
			}
		}
		// the wings are always ready (a double-tap of jump)
		if (!p.getAbilities().mayfly && !flag(p, F_STALL)) {
			RecalKitMath.wings(p, false, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED);
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
		}
		// v0.17.2 (user: "his flight sound effect plays constantly even if he isnt flying"): only while actually airborne in flight
		if (flying && !p.onGround() && !p.isInWater() && p.tickCount % 20 == 0 && p.getDeltaMovement().lengthSqr() > 0.2) {
			OmnitrixSfx.playOn(p, WHOOSH, 0.6f, 1.0f, SoundSource.PLAYERS);
		}
		// Aerophibian: at home in water
		if (p.isInWater()) {
			if (now % 5 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 12, flag(p, F_STREAM) ? 1 : 0, false, false, false));
			}
			p.setAirSupply(p.getMaxAirSupply());
		}
		if (flag(p, F_STREAM) && now % 5 == 0 && !p.isInWater()) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 1, false, false, false));
		}
		if (flag(p, F_ROLL) && now >= timer(p, ROLL_UNTIL)) {
			setFlag(p, F_ROLL, false);
			setTimer(p, ROLL_UNTIL, 0);
		}
		p.resetFallDistance();
		tickBeat(p, now);
		tickSonic(p, l, now);
		tickDive(p, l, now);
		tickThunder(p, l, now);
		tickOverload(p, l, now);
		tickCarry(p, l, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_OVERLOAD)) {
			return; // charging the overload: nothing else
		}
		// Supersonic Flight owns his body: his eyes and tail still fire, but nothing that would move him or hold him still
		if ((flag(p, F_SONIC_CHARGE) || flag(p, F_SONIC)) && !(slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_4 && !shift
				|| slot == AbilitySlot.SLOT_6 && shift || slot == AbilitySlot.SLOT_2 && shift && flag(p, F_CARRY))) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					tailBlast(p);
				} else {
					eyes(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					if (flag(p, F_CARRY)) {
						drop(p);
					} else {
						tailGrab(p);
					}
				} else {
					diveStrike(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					supersonicStart(p);
				} else {
					wingBeat(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					overload(p);
				} else {
					spread(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					roll(p);
				} else {
					streamline(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					shockPulse(p);
				} else {
					thunderDive(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3) {
			supersonicStop(p);
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- Neuroshock stacks

	/**
	 * v0.17.2 revamp (Neuroshock: "shuts the nervous system down"): a stack on {@code e}; the third within 4 s paralyses it for
	 * 1 s and clears them. True if it paralysed.
	 */
	public boolean addStack(ServerPlayer p, LivingEntity e) {
		long now = p.level().getGameTime();
		Map<Integer, Stack> m = STACKS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Stack old = m.get(e.getId());
		int c = old == null || now - old.last() > STACK_WINDOW ? 1 : old.count() + 1;
		if (c >= STACKS_TO_PARALYSE) {
			m.remove(e.getId());
			paralyse(p, e, PARALYSE_TICKS);
			return true;
		}
		m.put(e.getId(), new Stack(c, now));
		return false;
	}

	/** Neuroshock stacks on {@code e} (tests). */
	public static int stacks(ServerPlayer p, LivingEntity e) {
		Map<Integer, Stack> m = STACKS.get(p.getUUID());
		Stack s = m == null ? null : m.get(e.getId());
		return s == null || p.level().getGameTime() - s.last() > STACK_WINDOW ? 0 : s.count();
	}

	/** Paralysis: the stun (a boss for a quarter of it), with a green jolt round the body. */
	private void paralyse(ServerPlayer p, LivingEntity e, int ticks) {
		AlienStatus.stun(p, e, ticks);
		QuirkFx.ring(level(p), e.position().add(0, e.getBbHeight() * 0.5, 0)).style(QuirkFx.RING_SOFT).colours(0xF4FFC8, SHOCK).size(1.2f + e.getBbWidth())
				.thickness(0.2f).life(10).spawn();
		OmnitrixSfx.play(level(p), e.position(), PARALYSE, 0.8f, 1f);
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: the twin eye beam -- 7 to the first creature in the line, and a Neuroshock stack. True if it fired. */
	public boolean eyes(ServerPlayer p) {
		if (!use(p, EYES)) {
			return false;
		}
		anim(p, ANIM_EYES);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), EYES_RANGE, 0.45);
		if (t != null) {
			if (hurt(p, t, EYES_DAMAGE, false)) {
				addStack(p, t);
			}
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), ZAP, 1.0f, 1.0f);
		return true;
	}

	public boolean tailBlast(ServerPlayer p) {
		if (!use(p, TAIL_BLAST)) {
			return false;
		}
		anim(p, ANIM_TAIL);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), TAIL_RANGE, 0.7);
		if (t != null) {
			hurt(p, t, TAIL_DAMAGE, false);
			paralyse(p, t, TAIL_STUN);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), TAIL, 1.1f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	public boolean diveStrike(ServerPlayer p) {
		if (!use(p, DIVE_STRIKE)) {
			return false;
		}
		anim(p, ANIM_DIVE);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(2.2));
		p.hurtMarked = true;
		setFlag(p, F_DIVING, true);
		setTimer(p, DIVE_END, p.level().getGameTime() + DIVE_TICKS);
		DIVE_HITS.put(p.getUUID(), new HashSet<>());
		QuirkFx.trail(level(p), p).colours(0xF4FFC8, 0xD8304A).size(0.35f).life(DIVE_TICKS + 6).spawn();
		OmnitrixSfx.playOn(p, DIVE, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	private void tickDive(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_DIVING)) {
			return;
		}
		Set<Integer> hit = DIVE_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.8)) {
			if (e.getId() == (int) (timer(p, CARRY_TARGET) - 1)) {
				continue; // never the one he carries
			}
			if (hit.add(e.getId())) {
				hurt(p, e, DIVE_DAMAGE, false);
				shove(e, p.position(), 1.0, 0.35);
				OmnitrixSfx.play(l, e.position(), IMPACT, 0.7f, 1.2f);
			}
		}
		long end = timer(p, DIVE_END);
		if (now >= end || p.horizontalCollision) {
			setFlag(p, F_DIVING, false);
			setTimer(p, DIVE_END, 0);
			DIVE_HITS.remove(p.getUUID());
			p.setDeltaMovement(p.getDeltaMovement().scale(0.4));
			p.hurtMarked = true;
		}
	}

	/** Shift+G: the tail grabs the creature he looks at (5 blocks) and carries it under him; a squad ally is carried unhurt. */
	public boolean tailGrab(ServerPlayer p) {
		LivingEntity t = lookTarget(p, GRAB_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.jetray.no_target");
			return false;
		}
		if (AlienStatus.isBoss(t) || t.getBbWidth() > 1.6f || t.getBbHeight() > 2.6f || t.isPassenger() || t.isVehicle()) {
			say(p, "message.projecthero.omnitrix.jetray.too_big");
			return false;
		}
		if (!ready(p, TAIL_GRAB)) {
			return use(p, TAIL_GRAB); // the "not ready" line
		}
		anim(p, ANIM_GRAB);
		long now = p.level().getGameTime();
		if (!Squads.areAllies(p, t)) {
			hurt(p, t, GRAB_DAMAGE, false);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(CARRY_TARGET), t.getId() + 1L);
		n.timers.put(key(CARRY_UNTIL), now + CARRY_TICKS);
		n.kitFlags |= F_CARRY;
		Omnitrix.save(p, n);
		OmnitrixSfx.play(level(p), t.position(), GRAB, 1f, 1f);
		return true;
	}

	/** The creature he carries, or null. */
	public LivingEntity carried(ServerPlayer p) {
		long id = timer(p, CARRY_TARGET);
		Entity e = id > 0 ? p.level().getEntity((int) (id - 1)) : null;
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** Lets go of what he carries; the 10 s cooldown starts. */
	public void drop(ServerPlayer p) {
		LivingEntity t = carried(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(CARRY_TARGET));
		n.timers.remove(key(CARRY_UNTIL));
		n.kitFlags &= ~F_CARRY;
		Omnitrix.save(p, n);
		cooldown(p, TAIL_GRAB, -1);
		if (t != null) {
			t.setDeltaMovement(p.getDeltaMovement().scale(0.8));
			t.hurtMarked = true;
			t.fallDistance = 0f; // it falls from here: the fall counts from the drop
		}
	}

	private void tickCarry(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_CARRY)) {
			return;
		}
		LivingEntity t = carried(p);
		if (t == null || now >= timer(p, CARRY_UNTIL) || t.level() != p.level() || t.distanceToSqr(p) > (flag(p, F_SONIC) ? 400 : 64)) {
			drop(p);
			return;
		}
		// held in the tail, just under and behind him
		Vec3 back = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		back = back.lengthSqr() < 1.0e-4 ? Vec3.ZERO : back.normalize().scale(-0.4);
		Vec3 at = p.position().add(back).add(0, -t.getBbHeight() - 0.2, 0);
		if (!l.noCollision(t, t.getBoundingBox().move(at.subtract(t.position())))) {
			at = p.position().add(back); // no room under him (he is low): carried at his side
		}
		if (t instanceof ServerPlayer tp) {
			tp.teleportTo(at.x, at.y, at.z);
		} else {
			t.teleportTo(at.x, at.y, at.z);
		}
		t.setDeltaMovement(Vec3.ZERO);
		t.fallDistance = 0f;
		t.hurtMarked = true;
	}

	// ---------------------------------------------------------------- X / Shift+X

	/**
	 * X: Wing Beat -- the wings go up ({@link #BEAT_WINDUP} ticks), then {@link #wingStroke} throws him. False if he is stalled or it is not
	 * ready. Flight itself starts on the stroke, not here: the client drops {@code flying} the moment it finds itself on the ground.
	 */
	public boolean wingBeat(ServerPlayer p) {
		if (flag(p, F_STALL)) {
			say(p, "message.projecthero.omnitrix.jetray.stalled");
			return false;
		}
		if (timer(p, BEAT_AT) > 0 || !use(p, WING_BEAT)) {
			return false;
		}
		anim(p, ANIM_BEAT);
		setTimer(p, BEAT_AT, p.level().getGameTime() + BEAT_WINDUP);
		return true;
	}

	private void tickBeat(ServerPlayer p, long now) {
		long at = timer(p, BEAT_AT);
		if (at > 0 && now >= at) {
			wingStroke(p);
		}
		// the take-off's first ticks: if the client was still on the ground when the flight reached it, it dropped it again
		if (timer(p, BEAT_LIFT) > now && !p.getAbilities().flying && !p.onGround() && !p.isInWater()) {
			RecalKitMath.wings(p, true, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED);
		}
	}

	/**
	 * The stroke: {@link #BEAT_SPEED} along the look, lifted a little in the air and well up off the ground, straight along the look in water;
	 * the downwash hits everything within {@link #BEAT_RADIUS} ({@link #BEAT_DAMAGE}) and shoves it out. Tests call it straight.
	 */
	public void wingStroke(ServerPlayer p) {
		setTimer(p, BEAT_AT, 0);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 at = p.position().add(0, 0.2, 0);
		boolean ground = p.onGround();
		if (!p.isInWater() && !p.getAbilities().flying) {
			RecalKitMath.wings(p, true, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED);
			setFlag(p, F_FLYING, true);
			setTimer(p, BEAT_LIFT, p.level().getGameTime() + 8);
		}
		Vec3 v = p.isInWater() ? look.scale(BEAT_SPEED + 0.4)
				: ground ? new Vec3(look.x, 0, look.z).scale(BEAT_SPEED * 0.45).add(0, BEAT_SPEED * 0.8, 0)
						: look.add(0, 0.25, 0).normalize().scale(BEAT_SPEED);
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
		for (LivingEntity e : around(p, at, BEAT_RADIUS)) {
			if (e.getId() != (int) (timer(p, CARRY_TARGET) - 1) && hurt(p, e, BEAT_DAMAGE, false)) {
				shove(e, at, 1.0, 0.2);
			}
		}
		QuirkFx.ring(l, at).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xFFD27A).size((float) BEAT_RADIUS).thickness(0.3f).life(10).ground().spawn();
		l.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y, at.z, 14, 1.2, 0.1, 1.2, 0.08);
		OmnitrixSfx.play(l, at, BEAT, 1.0f, 1.0f);
	}

	/**
	 * Shift+X down, flying: the charge starts; {@link #SONIC_CHARGE} ticks later {@link #breakBarrier} if the keys are still down. Refused
	 * (no cooldown) on the ground or stalled: double-tap jump first.
	 */
	public boolean supersonicStart(ServerPlayer p) {
		if (flag(p, F_SONIC_CHARGE) || flag(p, F_SONIC)) {
			return false;
		}
		if (flag(p, F_STALL)) {
			say(p, "message.projecthero.omnitrix.jetray.stalled");
			return false;
		}
		if (!p.getAbilities().flying) {
			say(p, "message.projecthero.omnitrix.jetray.fly_first");
			return false;
		}
		if (!ready(p, SUPERSONIC)) {
			return use(p, SUPERSONIC); // the usual "not ready" line
		}
		anim(p, ANIM_SONIC_CHARGE);
		setTimer(p, SONIC_AT, p.level().getGameTime() + SONIC_CHARGE);
		setFlag(p, F_SONIC_CHARGE, true);
		OmnitrixSfx.playOn(p, CHARGE, 1.0f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	private void tickSonic(ServerPlayer p, ServerLevel l, long now) {
		boolean charging = flag(p, F_SONIC_CHARGE);
		if (!charging && !flag(p, F_SONIC)) {
			return;
		}
		if (!p.isShiftKeyDown()) {
			supersonicStop(p); // "while hes holding shift+X": the Shift half came up first
			return;
		}
		if (!p.getAbilities().flying) {
			RecalKitMath.wings(p, true, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED); // the client landed him for a tick (a scrape along the floor)
		}
		p.resetFallDistance();
		if (charging) {
			long left = timer(p, SONIC_AT) - now;
			if (left <= 0) {
				breakBarrier(p, l);
			} else if (left % 10 == 0) {
				OmnitrixSfx.playOn(p, SONIC_HUM, 0.5f + 0.5f * (SONIC_CHARGE - left) / SONIC_CHARGE, 0.7f + 1.1f * (SONIC_CHARGE - left) / SONIC_CHARGE,
						SoundSource.PLAYERS);
			}
		} else if (now % 15 == 0) {
			OmnitrixSfx.playOn(p, SONIC_RUSH, 0.8f, 1.0f, SoundSource.PLAYERS);
		}
	}

	/** Three seconds held: the sonic boom ({@link #BOOM_DAMAGE} in {@link #BOOM_RADIUS}, shoved), and {@code DirectionalFlight} takes him to 200 b/s. */
	public void breakBarrier(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_SONIC_CHARGE, false);
		setTimer(p, SONIC_AT, 0);
		setFlag(p, F_SONIC, true);
		anim(p, ANIM_SONIC);
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (LivingEntity e : around(p, at, BOOM_RADIUS)) {
			if (e.getId() != (int) (timer(p, CARRY_TARGET) - 1) && hurt(p, e, BOOM_DAMAGE, false)) {
				shove(e, at, 0.9, 0.3);
			}
		}
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SHOCK).colours(0xF4FFC8, SHOCK).size(4.5f).thickness(0.45f).life(12)
				.spawn();
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xBFE8FF).size(7f).thickness(0.3f).life(16)
				.spawn();
		QuirkCamera.fovPunch(p, 0.2f, 10);
		QuirkCamera.shakeAround(l, at, 14, 0.25f, 6);
		OmnitrixSfx.play(l, at, BOOM, 1.5f, 0.9f);
	}

	/** The keys came up (or he was knocked out of the sky): a charge is dropped with a short cooldown; a flight starts the full one. */
	public void supersonicStop(ServerPlayer p) {
		if (flag(p, F_SONIC_CHARGE)) {
			setFlag(p, F_SONIC_CHARGE, false);
			setTimer(p, SONIC_AT, 0);
			cooldown(p, SUPERSONIC, SONIC_CANCEL_COOLDOWN);
			if (Omnitrix.state(p).animId == ANIM_SONIC_CHARGE) {
				anim(p, 0);
			}
		}
		if (flag(p, F_SONIC)) {
			setFlag(p, F_SONIC, false);
			setTimer(p, SONIC_TAIL, p.level().getGameTime() + SONIC_TAIL_TICKS);
			cooldown(p, SUPERSONIC, -1);
			anim(p, ANIM_SONIC_END);
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	public boolean spread(ServerPlayer p) {
		if (!use(p, SPREAD)) {
			return false;
		}
		anim(p, ANIM_SPREAD);
		Vec3 look = p.getLookAngle();
		Set<Integer> hit = new HashSet<>();
		for (int i = -2; i <= 2; i++) {
			LivingEntity t = RecalKitMath.first(p, RecalKitMath.turned(look, i * 15.0), SPREAD_RANGE, 0.45);
			if (t != null && hit.add(t.getId()) && hurt(p, t, SPREAD_DAMAGE, false)) {
				addStack(p, t);
			}
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), ZAP, 1.2f, 0.85f);
		return true;
	}

	public boolean overload(ServerPlayer p) {
		if (!use(p, OVERLOAD_MOVE)) {
			return false;
		}
		anim(p, ANIM_OVERLOAD);
		setFlag(p, F_OVERLOAD, true);
		setTimer(p, OVERLOAD_AT, p.level().getGameTime() + OVERLOAD_CHARGE);
		OmnitrixSfx.playOn(p, CHARGE, 1.2f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	private void tickOverload(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_OVERLOAD)) {
			return;
		}
		// held still while the eyes and tail charge
		p.setDeltaMovement(0, Math.max(0, p.getDeltaMovement().y) * 0.5, 0);
		p.hurtMarked = true;
		if (now >= timer(p, OVERLOAD_AT)) {
			fireOverload(p, l);
		}
	}

	/** The overload's beam: 60 to everything in a 30-block, 3-wide line, paralysed 3 s. Public for tests. */
	public void fireOverload(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_OVERLOAD, false);
		setTimer(p, OVERLOAD_AT, 0);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), OVERLOAD_RANGE, 1.5)) {
			if (hurt(p, e, OVERLOAD_DAMAGE, false)) {
				shove(e, p.position(), 1.2, 0.3);
			}
			if (e.isAlive()) {
				paralyse(p, e, OVERLOAD_STUN);
			}
		}
		Vec3 end = RecalKitMath.beamEnd(p, p.getLookAngle(), OVERLOAD_RANGE);
		QuirkFx.ring(l, end).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SHOCK).colours(0xF4FFC8, SHOCK).size(3f).thickness(0.5f)
				.life(14).spawn();
		QuirkCamera.shakeAround(l, p.position(), 16, 0.35f, 8);
		OmnitrixSfx.play(l, p.getEyePosition(), OVERLOAD, 1.6f, 1.0f);
	}

	// ---------------------------------------------------------------- V / Shift+V

	public boolean streamline(ServerPlayer p) {
		if (!use(p, STREAMLINE)) {
			return false;
		}
		boolean on = !flag(p, F_STREAM);
		setFlag(p, F_STREAM, on);
		p.getAbilities().setFlyingSpeed(on ? STREAM_FLY_SPEED : FLY_SPEED);
		p.onUpdateAbilities();
		if (!on) {
			MobEffectInstance sp = p.getEffect(MobEffects.MOVEMENT_SPEED);
			if (sp != null && sp.getAmplifier() == 1 && sp.getDuration() <= 12) {
				p.removeEffect(MobEffects.MOVEMENT_SPEED);
			}
		}
		anim(p, ANIM_STREAM);
		OmnitrixSfx.playOn(p, STREAM, 1.0f, on ? 1.2f : 0.8f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+V: Barrel Roll -- a sideways roll (the way he is drifting, else right); 0.75 s untouchable by shots, half other damage. */
	public boolean roll(ServerPlayer p) {
		if (!use(p, ROLL)) {
			return false;
		}
		anim(p, ANIM_ROLL);
		Vec3 look = p.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 v = p.getDeltaMovement();
		double side = v.x * right.x + v.z * right.z;
		Vec3 dir = side < -0.02 ? right.scale(-1) : right;
		p.setDeltaMovement(v.scale(0.5).add(dir.scale(1.1)).add(0, 0.15, 0));
		p.hurtMarked = true;
		setTimer(p, ROLL_UNTIL, p.level().getGameTime() + ROLL_TICKS);
		setFlag(p, F_ROLL, true);
		OmnitrixSfx.playOn(p, WHOOSH, 1.0f, 1.3f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- C / Shift+C

	public boolean thunderDive(ServerPlayer p) {
		if (flag(p, F_THUNDER) || !use(p, DIVE_BOMB)) {
			return false;
		}
		anim(p, ANIM_THUNDER);
		long now = p.level().getGameTime();
		Vec3 h = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(0.5).add(0, 1.1, 0));
		p.hurtMarked = true;
		setFlag(p, F_THUNDER, true);
		setTimer(p, THUNDER_AT, now + 8);
		setTimer(p, THUNDER_END, now + 48);
		OmnitrixSfx.playOn(p, WHOOSH, 1.2f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	private void tickThunder(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_THUNDER)) {
			return;
		}
		long at = timer(p, THUNDER_AT);
		if (at > 0) {
			if (now >= at) {
				setTimer(p, THUNDER_AT, 0);
				if (p.getAbilities().flying) {
					p.getAbilities().flying = false;
					p.onUpdateAbilities();
				}
				OmnitrixSfx.playOn(p, DIVE, 1.2f, 0.8f, SoundSource.PLAYERS);
			}
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.y > -1.8) {
			p.setDeltaMovement(v.x * 0.9, Math.min(v.y, -1.0) - 0.2, v.z * 0.9);
			p.hurtMarked = true;
		}
		if (p.onGround() || p.isInWater() || p.verticalCollisionBelow || now >= timer(p, THUNDER_END)) {
			thunderImpact(p, l);
		}
	}

	/** The Dive Bomb lands: a neuroshock burst round the impact (18 in 4, paralysed 1 s). Public for tests. */
	public void thunderImpact(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_THUNDER, false);
		setTimer(p, THUNDER_AT, 0);
		setTimer(p, THUNDER_END, 0);
		for (LivingEntity e : around(p, p.position(), THUNDER_RADIUS)) {
			if (e.getId() == (int) (timer(p, CARRY_TARGET) - 1)) {
				continue;
			}
			hurt(p, e, THUNDER_DAMAGE, false);
			shove(e, p.position(), 0.9, 0.5);
			if (e.isAlive()) {
				paralyse(p, e, THUNDER_STUN);
			}
		}
		anim(p, ANIM_PULSE);
		p.resetFallDistance();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(0xF4FFC8, SHOCK).size((float) THUNDER_RADIUS).thickness(0.6f).life(14)
				.ground().spawn();
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(2.4f).yaw(p.getYRot()).life(160).spawn();
		QuirkCamera.shakeAround(l, p.position(), 10, 0.3f, 6);
		OmnitrixSfx.play(l, p.position(), IMPACT, 1.3f, 1.0f);
	}

	public boolean shockPulse(ServerPlayer p) {
		if (!use(p, SHOCK_PULSE)) {
			return false;
		}
		anim(p, ANIM_PULSE);
		for (LivingEntity e : around(p, p.position(), PULSE_RADIUS)) {
			if (e.getId() == (int) (timer(p, CARRY_TARGET) - 1)) {
				continue;
			}
			if (hurt(p, e, PULSE_DAMAGE, false)) {
				addStack(p, e);
			}
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			shove(e, p.position(), 0.6, 0.2);
		}
		OmnitrixSfx.play(level(p), p.position(), PULSE, 1.2f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	/** Wing Ram's bonus at {@code speed} blocks/tick: +1 every 0.4, up to +5 (tests). */
	public static float ramBonus(double speed) {
		return (float) Math.min(RAM_MAX, Math.floor(speed / RAM_STEP) * RAM_PER);
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && p.getAbilities().flying) {
			float bonus = ramBonus(p.getDeltaMovement().length());
			if (bonus > 0f) {
				anim(p, ANIM_RAM);
				hurt(p, le, bonus, false);
				le.invulnerableTime = 0; // vanilla's hit still lands after it
				shove(le, p.position(), 0.5 + bonus * 0.12, 0.25);
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty() || flag(p, F_OVERLOAD)) {
			return InteractionResult.PASS;
		}
		return wingSlash(p) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Use (empty hand): Wing Slash -- the wing edge sweeps in front (3.5 blocks): 6, shoved. */
	public boolean wingSlash(ServerPlayer p) {
		if (cooldownLeft(p, WING_SLASH) > 0) {
			return false; // quiet: a right-click spam should not flood the action bar
		}
		cooldown(p, WING_SLASH, -1);
		anim(p, ANIM_SLASH);
		for (LivingEntity e : RecalKitMath.cone(p, SLASH_RANGE, 70)) {
			if (e.getId() != (int) (timer(p, CARRY_TARGET) - 1) && hurt(p, e, SLASH_DAMAGE, false)) {
				shove(e, p.position(), 0.8, 0.3);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SLASH, 1.0f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.DROWN)) {
			return false;
		}
		// Barrel Roll: shots slip past
		return !(flag(p, F_ROLL) && source.is(DamageTypeTags.IS_PROJECTILE));
	}

	/** v0.17.2 revamp (strong winds): a wind charge or a blast. */
	public static boolean wind(DamageSource source) {
		return source.is(DamageTypes.WIND_CHARGE) || source.is(DamageTypeTags.IS_EXPLOSION);
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float a = amount;
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			a *= LIGHTNING_TAKEN; // Insulated: his own body runs on neuroshock
		}
		if (wind(source)) {
			a *= WIND_TAKEN;
		}
		if (flag(p, F_ROLL)) {
			a *= ROLL_TAKEN;
		}
		// knocked out of the air: strong winds, or a heavy hit on the wing
		if (p.getAbilities().flying && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && (wind(source) || a >= CLIP_THRESHOLD)) {
			stall(p);
		}
		return a;
	}

	/** Knocked out of the air: no flight for 1.5 s (he drops; still no fall damage). */
	public void stall(ServerPlayer p) {
		supersonicStop(p);
		setTimer(p, STALL_UNTIL, p.level().getGameTime() + STALL_TICKS);
		setFlag(p, F_STALL, true);
		setFlag(p, F_FLYING, false);
		p.getAbilities().flying = false;
		p.getAbilities().mayfly = false;
		p.onUpdateAbilities();
		OmnitrixSfx.play(level(p), p.position(), STALL, 1f, 1f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.jetray.stalled").withStyle(ChatFormatting.RED), true);
	}

	/** Whether {@code p} is Jetray in the air (client-safe). */
	public static boolean flyingNow(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof JetrayKit j && j.flag(p, F_FLYING);
	}

	/** v0.17.2: whether {@code p} is Jetray with Streamline on (client-safe; the directional flight's speed). */
	public static boolean streamlined(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof JetrayKit j && j.flag(p, F_STREAM);
	}

	/** Round 4: {@code p} is charging Supersonic Flight (client-safe; the charge's slow drive). */
	public static boolean supersonicCharging(Player p) {
		return activeKit(p) instanceof JetrayKit j && j.flag(p, F_SONIC_CHARGE);
	}

	/** Round 4: {@code p} is past the barrier, flying at 200 b/s (client-safe). */
	public static boolean supersonic(Player p) {
		return activeKit(p) instanceof JetrayKit j && j.flag(p, F_SONIC);
	}

	/**
	 * Round 4: 10 blocks a tick is exactly vanilla's "moved too quickly" limit, so the server waves that one check through for him, through
	 * the moment the flight ends too: his last fast moves are still arriving then ({@code SpeedForceMoveCheckMixin}).
	 */
	public static boolean fastMove(ServerPlayer p) {
		return activeKit(p) instanceof JetrayKit j && (j.flag(p, F_SONIC) || j.timer(p, SONIC_TAIL) > p.level().getGameTime());
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		UUID id = p.getUUID();
		return DIVE_HITS.containsKey(id) || STACKS.containsKey(id) || LAST_TICK.containsKey(id);
	}
}
