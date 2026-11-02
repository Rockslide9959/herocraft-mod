package com.projecthero.mod.omnitrix.alien.jetray;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, user: "Just make them basic versions of the aliens for now please that we can revamp later"):
 * Jetray, the Aerophibian -- a crimson manta ray built for high-speed flight through air, space and water. He flies on X, swims
 * like a fish, fires neuroshock blasts from his eyes and tail and dive-bombs from the sky.
 *
 * <pre>
 *   R        Neuroshock Eyes   twin eye beam (24): the first creature, 7       Shift+R  Tail Blast        tail bolt (28): 14 + a 1 s stun
 *   G        Dive Strike       streak along the look: 15 to all you pass     Shift+G  Wing Slash        the wings sweep a cone (4.5): 12, shoved
 *   X        Flight            take off / land (fast flight)                  Shift+X  Afterburner       a burst of speed along the look
 *   Z        Neuroshock Spread five beams in a fan (16): 6 each               Shift+Z  Shock Pulse       a ring (r 5): 13 + Slowness II
 *   V        Streamline        toggle: faster flight, swimming and running    Shift+V  Neural Scan       creatures within 24 glow 8 s
 *   C        Thunder Dive      climb, then plunge: r 4, 19                    Shift+C  Neuroshock Overload ULTIMATE: 1 s charge, a 30-block beam: 55
 * </pre>
 * Passives: no fall / fly-into-wall damage; in water Dolphin's Grace and he never drowns.
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
	public static final OmnitrixSfx.Cue WHOOSH = OmnitrixSfx.cue("jetray_whoosh")
			.layer(SoundEvents.PHANTOM_FLAP, 0.35f, 0.9f)
			.layer(SoundEvents.ELYTRA_FLYING, 0.2f, 1.5f)
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
			.layer(SoundEvents.ELYTRA_FLYING, 0.2f, 1.8f)
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

	// ---------------------------------------------------------------- poses (client: JetrayClient)
	public static final int ANIM_EYES = 1, ANIM_TAIL = 2, ANIM_DIVE = 3, ANIM_SLASH = 4, ANIM_TAKEOFF = 5, ANIM_BOOST = 6, ANIM_SPREAD = 7,
			ANIM_PULSE = 8, ANIM_STREAM = 9, ANIM_SCAN = 10, ANIM_THUNDER = 11, ANIM_OVERLOAD = 12;

	public static final int F_FLYING = 1;
	public static final int F_STREAM = 2;
	public static final int F_DIVING = 4;
	public static final int F_OVERLOAD = 8;
	public static final int F_THUNDER = 16;

	// ---------------------------------------------------------------- moves
	public static final String EYES = "neuroshock_eyes", TAIL_BLAST = "tail_blast", DIVE_STRIKE = "dive_strike", WING_SLASH = "wing_slash",
			FLIGHT = "flight", AFTERBURNER = "afterburner", SPREAD = "neuroshock_spread", SHOCK_PULSE = "shock_pulse", STREAMLINE = "streamline",
			NEURAL_SCAN = "neural_scan", THUNDER_DIVE = "thunder_dive", OVERLOAD_MOVE = "neuroshock_overload";

	public static final float EYES_DAMAGE = 7f;
	public static final double EYES_RANGE = 24.0;
	public static final float TAIL_DAMAGE = 14f;
	public static final int TAIL_STUN = 20;
	public static final float DIVE_DAMAGE = 15f;
	public static final int DIVE_TICKS = 12;
	public static final float SLASH_DAMAGE = 12f;
	public static final float SPREAD_DAMAGE = 6f;
	public static final float PULSE_DAMAGE = 13f;
	public static final double PULSE_RADIUS = 5.0;
	public static final float THUNDER_DAMAGE = 19f;
	public static final double THUNDER_RADIUS = 4.0;
	public static final float OVERLOAD_DAMAGE = 55f;
	public static final double OVERLOAD_RANGE = 30.0;
	public static final int OVERLOAD_CHARGE = 20;
	public static final float FLY_SPEED = 0.1f;
	public static final float STREAM_FLY_SPEED = 0.16f;

	/** Creatures already hit by the running Dive Strike (each once). */
	private static final Map<UUID, Set<Integer>> DIVE_HITS = new ConcurrentHashMap<>();

	public static void register() {
		Aliens.register(new JetrayKit());
	}

	public static void clearSessionState() {
		DIVE_HITS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(24).speed(1.2f).armor(3, 0).attack(3).knockbackResist(0.1f).safeFall(6).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(EYES, AbilitySlot.SLOT_1, 16), AlienMove.shift(TAIL_BLAST, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(DIVE_STRIKE, AbilitySlot.SLOT_2, 120), AlienMove.shift(WING_SLASH, AbilitySlot.SLOT_2, 80),
				AlienMove.tap(FLIGHT, AbilitySlot.SLOT_3, 10), AlienMove.shift(AFTERBURNER, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(SPREAD, AbilitySlot.SLOT_4, 100), AlienMove.shift(SHOCK_PULSE, AbilitySlot.SLOT_4, 160),
				AlienMove.tap(STREAMLINE, AbilitySlot.SLOT_5, 10), AlienMove.shift(NEURAL_SCAN, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(THUNDER_DIVE, AbilitySlot.SLOT_6, 240), AlienMove.shift(OVERLOAD_MOVE, AbilitySlot.SLOT_6, 900));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean air = !p.onGround() && !p.isInWater() && !p.isPassenger();
		RecalKitMath.wings(p, air, FLY_SPEED);
		setFlag(p, F_FLYING, air);
		OmnitrixSfx.playOn(p, WHOOSH, 1.0f, 1.0f, SoundSource.PLAYERS);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		DIVE_HITS.remove(p.getUUID());
		setFlag(p, F_FLYING | F_STREAM | F_DIVING | F_OVERLOAD | F_THUNDER, false);
		for (String t : new String[] { "dive_end", "thunder_at", "thunder_end", "overload_at" }) {
			if (timer(p, t) > 0) {
				setTimer(p, t, 0);
			}
		}
		RecalKitMath.groundWings(p);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 && flag(p, F_FLYING) || slot == AbilitySlot.SLOT_5 && flag(p, F_STREAM);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// the wings are always ready (X or a double-tap of jump)
		if (!p.getAbilities().mayfly) {
			RecalKitMath.wings(p, false, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED);
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
		}
		if (flying && p.tickCount % 20 == 0 && p.getDeltaMovement().lengthSqr() > 0.2) {
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
		p.resetFallDistance();
		tickDive(p, l, now);
		tickThunder(p, l, now);
		tickOverload(p, l, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_OVERLOAD)) {
			return; // charging the overload: nothing else
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
					wingSlash(p);
				} else {
					diveStrike(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					afterburner(p);
				} else {
					flight(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					shockPulse(p);
				} else {
					spread(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					neuralScan(p);
				} else {
					streamline(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					overload(p);
				} else {
					thunderDive(p);
				}
			}
			default -> {
			}
		}
	}

	/** R: the twin eye beam -- 7 to the first creature in the line. True if it fired. */
	public boolean eyes(ServerPlayer p) {
		if (!use(p, EYES)) {
			return false;
		}
		anim(p, ANIM_EYES);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), EYES_RANGE, 0.45);
		if (t != null) {
			hurt(p, t, EYES_DAMAGE, false);
			spark(level(p), t.getBoundingBox().getCenter(), 6);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), ZAP, 1.0f, 1.0f);
		return true;
	}

	private boolean tailBlast(ServerPlayer p) {
		if (!use(p, TAIL_BLAST)) {
			return false;
		}
		anim(p, ANIM_TAIL);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), 28.0, 0.7);
		if (t != null) {
			hurt(p, t, TAIL_DAMAGE, false);
			AlienStatus.stun(p, t, TAIL_STUN);
			spark(level(p), t.getBoundingBox().getCenter(), 12);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), TAIL, 1.1f, 1.0f);
		return true;
	}

	private boolean diveStrike(ServerPlayer p) {
		if (!use(p, DIVE_STRIKE)) {
			return false;
		}
		anim(p, ANIM_DIVE);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(2.2));
		p.hurtMarked = true;
		setFlag(p, F_DIVING, true);
		setTimer(p, "dive_end", p.level().getGameTime() + DIVE_TICKS);
		DIVE_HITS.put(p.getUUID(), new HashSet<>());
		OmnitrixSfx.playOn(p, DIVE, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	private void tickDive(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_DIVING)) {
			return;
		}
		Set<Integer> hit = DIVE_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.8)) {
			if (hit.add(e.getId())) {
				hurt(p, e, DIVE_DAMAGE, false);
				shove(e, p.position(), 1.0, 0.35);
				spark(l, e.getBoundingBox().getCenter(), 8);
				OmnitrixSfx.play(l, e.position(), IMPACT, 0.7f, 1.2f);
			}
		}
		long end = timer(p, "dive_end");
		if (now >= end || p.horizontalCollision) {
			setFlag(p, F_DIVING, false);
			setTimer(p, "dive_end", 0);
			DIVE_HITS.remove(p.getUUID());
			p.setDeltaMovement(p.getDeltaMovement().scale(0.4));
			p.hurtMarked = true;
		}
	}

	private boolean wingSlash(ServerPlayer p) {
		if (!use(p, WING_SLASH)) {
			return false;
		}
		anim(p, ANIM_SLASH);
		for (LivingEntity e : RecalKitMath.cone(p, 4.5, 70)) {
			hurt(p, e, SLASH_DAMAGE, false);
			shove(e, p.position(), 0.8, 0.3);
		}
		OmnitrixSfx.play(level(p), p.position(), SLASH, 1.0f, 1.0f);
		return true;
	}

	/** X: take off / land. True if the wings changed. */
	public boolean flight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		boolean fly = !p.getAbilities().flying;
		RecalKitMath.wings(p, fly, flag(p, F_STREAM) ? STREAM_FLY_SPEED : FLY_SPEED);
		setFlag(p, F_FLYING, fly);
		if (fly) {
			anim(p, ANIM_TAKEOFF);
			p.setDeltaMovement(p.getDeltaMovement().add(0, 0.5, 0));
			p.hurtMarked = true;
			OmnitrixSfx.playOn(p, WHOOSH, 1.0f, 1.0f, SoundSource.PLAYERS);
		}
		return true;
	}

	private boolean afterburner(ServerPlayer p) {
		if (!use(p, AFTERBURNER)) {
			return false;
		}
		anim(p, ANIM_BOOST);
		p.setDeltaMovement(p.getLookAngle().scale(flag(p, F_STREAM) ? 3.0 : 2.5));
		p.hurtMarked = true;
		p.resetFallDistance();
		OmnitrixSfx.playOn(p, DIVE, 1.0f, 1.3f, SoundSource.PLAYERS);
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.6, p.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		return true;
	}

	private boolean spread(ServerPlayer p) {
		if (!use(p, SPREAD)) {
			return false;
		}
		anim(p, ANIM_SPREAD);
		Vec3 look = p.getLookAngle();
		Set<Integer> hit = new HashSet<>();
		for (int i = -2; i <= 2; i++) {
			LivingEntity t = RecalKitMath.first(p, RecalKitMath.turned(look, i * 15.0), 16.0, 0.45);
			if (t != null && hit.add(t.getId())) {
				hurt(p, t, SPREAD_DAMAGE, false);
				spark(level(p), t.getBoundingBox().getCenter(), 4);
			}
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), ZAP, 1.2f, 0.85f);
		return true;
	}

	private boolean shockPulse(ServerPlayer p) {
		if (!use(p, SHOCK_PULSE)) {
			return false;
		}
		anim(p, ANIM_PULSE);
		for (LivingEntity e : around(p, p.position(), PULSE_RADIUS)) {
			hurt(p, e, PULSE_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			shove(e, p.position(), 0.6, 0.2);
		}
		OmnitrixSfx.play(level(p), p.position(), PULSE, 1.2f, 1.0f);
		return true;
	}

	private boolean streamline(ServerPlayer p) {
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

	private boolean neuralScan(ServerPlayer p) {
		if (!use(p, NEURAL_SCAN)) {
			return false;
		}
		anim(p, ANIM_SCAN);
		for (LivingEntity e : around(p, p.position(), 24)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, false, false), p);
		}
		OmnitrixSfx.play(level(p), p.position(), SCAN, 1.0f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- C: Thunder Dive

	private boolean thunderDive(ServerPlayer p) {
		if (flag(p, F_THUNDER) || !use(p, THUNDER_DIVE)) {
			return false;
		}
		anim(p, ANIM_THUNDER);
		long now = p.level().getGameTime();
		Vec3 h = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(0.5).add(0, 1.1, 0));
		p.hurtMarked = true;
		setFlag(p, F_THUNDER, true);
		setTimer(p, "thunder_at", now + 8);
		setTimer(p, "thunder_end", now + 48);
		OmnitrixSfx.playOn(p, WHOOSH, 1.2f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	private void tickThunder(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_THUNDER)) {
			return;
		}
		long at = timer(p, "thunder_at");
		if (at > 0) {
			if (now >= at) {
				setTimer(p, "thunder_at", 0);
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
		if (p.onGround() || p.isInWater() || p.verticalCollisionBelow || now >= timer(p, "thunder_end")) {
			thunderImpact(p, l);
		}
	}

	/** The Thunder Dive lands: a neuroshock burst round the impact. Public for tests. */
	public void thunderImpact(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_THUNDER, false);
		setTimer(p, "thunder_at", 0);
		setTimer(p, "thunder_end", 0);
		for (LivingEntity e : around(p, p.position(), THUNDER_RADIUS)) {
			hurt(p, e, THUNDER_DAMAGE, false);
			shove(e, p.position(), 0.9, 0.5);
		}
		anim(p, ANIM_PULSE);
		p.resetFallDistance();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 16, 1.6, 0.2, 1.6, 0.2);
		OmnitrixSfx.play(l, p.position(), IMPACT, 1.3f, 1.0f);
	}

	// ---------------------------------------------------------------- Shift+C: Neuroshock Overload (ultimate)

	private boolean overload(ServerPlayer p) {
		if (!use(p, OVERLOAD_MOVE)) {
			return false;
		}
		anim(p, ANIM_OVERLOAD);
		setFlag(p, F_OVERLOAD, true);
		setTimer(p, "overload_at", p.level().getGameTime() + OVERLOAD_CHARGE);
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
		if (now % 4 == 0) {
			l.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.78f, 1f, 0.23f), 1.0f), p.getX(), p.getEyeY(), p.getZ(), 2, 0.3, 0.3, 0.3,
					0);
		}
		if (now >= timer(p, "overload_at")) {
			fireOverload(p, l);
		}
	}

	/** The overload's beam: 55 to everything in a 30-block, 3-wide line. Public for tests. */
	public void fireOverload(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_OVERLOAD, false);
		setTimer(p, "overload_at", 0);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), OVERLOAD_RANGE, 1.5)) {
			hurt(p, e, OVERLOAD_DAMAGE, false);
			shove(e, p.position(), 1.2, 0.3);
			spark(l, e.getBoundingBox().getCenter(), 14);
		}
		OmnitrixSfx.play(l, p.getEyePosition(), OVERLOAD, 1.6f, 1.0f);
	}

	private static void spark(ServerLevel l, Vec3 at, int n) {
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, n, 0.25, 0.25, 0.25, 0.15);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			return false;
		}
		return !source.is(DamageTypes.DROWN);
	}

	/** Whether {@code p} is Jetray in the air (client-safe). */
	public static boolean flyingNow(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof JetrayKit j && j.flag(p, F_FLYING);
	}
}
