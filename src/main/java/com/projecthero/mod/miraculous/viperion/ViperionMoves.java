package com.projecthero.mod.miraculous.viperion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7: Viperion's moves (see {@code docs/MIRACULOUS_VIPERION.md}) -- everything but Second Chance
 * ({@link SecondChance}). Every public move is called by {@link ViperionKit#handle} once its cooldown is ready, and by
 * the gametests directly. Moves that run over time (the combo, the slither, the strings, the hiss) keep a per-player
 * {@link Session}: entity ids, never entities; dropped by {@link ViperionKit#forget} / {@link ViperionKit#clearSessionState}.
 * Every hit goes through {@link #hit}: the mod-wide targeting rules ({@link HeroTargets}), bosses capped at
 * {@link ViperionConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and never knocked about.
 */
public final class ViperionMoves {
	/** The lyre's teal, its brighter strings, and Sass's green. */
	public static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.18f, 0.78f, 0.74f), 1.2f);
	public static final DustParticleOptions TEAL_BIG = new DustParticleOptions(new Vector3f(0.25f, 0.9f, 0.85f), 1.9f);
	public static final DustParticleOptions STRING = new DustParticleOptions(new Vector3f(0.75f, 1.0f, 0.95f), 0.7f);
	public static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 0.95f, 0.45f), 1.4f);

	static final class Session {
		// Lyre Strike: swings pressed so far (1..3), the last press at, its hit lands at (0 = none), a press queued
		int comboStep;
		long comboLast;
		long hitAt;
		int hitStep;
		boolean queued;
		// Serpent Dash
		Vec3 driveDir;
		int driveTicks;
		// Coil Leap: in the air from it (no fall damage on the landing), since
		boolean coilAir;
		long coilAt;
		// Constrict
		int constrictId = -1;
		Vec3 constrictAt;
		long nextSqueeze;
		// Hiss
		final List<Integer> scared = new ArrayList<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ViperionMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	static Vec3 chest(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static void sound(ServerPlayer p, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Within {@link ViperionConfig#FRONT_DOT} of his flattened look. */
	static boolean inFront(Player p, Vec3 at, double dot) {
		Vec3 d = at.subtract(p.position());
		Vec3 flatD = new Vec3(d.x, 0, d.z);
		if (flatD.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flatD.normalize().dot(flatLook(p)) > dot;
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= ViperionConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean hit(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * ViperionConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(0.5f, dmg))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(TEAL, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** A stun (bosses: only a short heavy slow, as the other kits do). */
	static void stun(LivingEntity e, int ticks) {
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
		} else {
			PunisherControl.stun(e, ticks);
		}
	}

	/** A flat ring of particles. */
	static void ring(ServerLevel level, ParticleOptions particle, Vec3 c, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(particle, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/** The nearest creature he may hit in front within {@code reach}, or null. */
	static @Nullable LivingEntity nearestInFront(ServerPlayer p, double reach) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, chest(p), reach + 1.0)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, chest(p)));
			if (d > reach || !inFront(p, e.position(), ViperionConfig.FRONT_DOT)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The creature under the crosshair, else the valid one nearest it within {@code degrees}, in sight. */
	static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && HeroTargets.canHarm(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && HeroTargets.canHarm(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	// ================================================================ R: Lyre Strike / Shift+R: Lyre Sweep

	/**
	 * v0.16.7 (user): one press = one swing of a three-press combo, as Cat Noir's Baton Strike (his reach, cone, damage,
	 * knockback): forehand, backhand, then the overhead finisher (harder knockback, and the cooldown starts). Each swing
	 * lands {@link ViperionConfig#STRIKE_WINDUP} ticks after its press (in {@link #tick}). The next press must come within
	 * {@link ViperionConfig#STRIKE_WINDOW} of the last, else the combo starts over; a press sooner than
	 * {@link ViperionConfig#STRIKE_GAP} is queued. Returns the swing started (1..3), or 0 if the press was queued.
	 */
	public static int strike(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (s.comboStep >= ViperionConfig.STRIKE_HITS || (s.comboStep > 0 && now - s.comboLast > ViperionConfig.STRIKE_WINDOW)) {
			s.comboStep = 0;
		}
		if (s.comboStep > 0 && now - s.comboLast < ViperionConfig.STRIKE_GAP) {
			s.queued = true;
			return 0;
		}
		return swing(p, s, now);
	}

	/** Gametests / HUD: swings of the running combo so far (0 = none). */
	public static int comboStep(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.comboStep;
	}

	private static int swing(ServerPlayer p, Session s, long now) {
		if (s.hitAt > 0L) {
			land(p, s); // a swing still winding up lands first
		}
		s.comboStep++;
		s.comboLast = now;
		s.queued = false;
		s.hitAt = now + ViperionConfig.STRIKE_WINDUP;
		s.hitStep = s.comboStep;
		int step = s.comboStep;
		Miraculous.setVal(p, Viperion.V_COMBO, now + (step >= ViperionConfig.STRIKE_HITS ? ViperionConfig.STRIKE_SWING_TICKS : ViperionConfig.STRIKE_WINDOW));
		Miraculous.setAnim(p, step == 1 ? Viperion.ANIM_STRIKE : step == 2 ? Viperion.ANIM_STRIKE_2 : Viperion.ANIM_STRIKE_3);
		if (step >= ViperionConfig.STRIKE_HITS) {
			Miraculous.setCooldown(p, ViperionConfig.STRIKE, ViperionConfig.STRIKE_COOLDOWN);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, step >= ViperionConfig.STRIKE_HITS ? 0.8f : 1.3f + 0.15f * step);
		return step;
	}

	/** A swing lands: everything in the cone in front, as Cat Noir's. Returns how many it hit. */
	private static int land(ServerPlayer p, Session s) {
		boolean finisher = s.hitStep >= ViperionConfig.STRIKE_HITS;
		s.hitAt = 0L;
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, finisher ? 0.8f : 1.2f + 0.15f * s.hitStep);
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 0.7f, finisher ? 0.7f : 1.0f + 0.25f * s.hitStep);
		Vec3 front = p.position().add(flatLook(p).scale(1.6)).add(0, 1.0, 0);
		p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
		p.serverLevel().sendParticles(STRING, front.x, front.y, front.z, 5, 0.4, 0.25, 0.4, 0.0);
		int n = 0;
		for (LivingEntity e : com.projecthero.mod.miraculous.catnoir.CatNoirCombat.cone(p, ViperionConfig.STRIKE_REACH, ViperionConfig.STRIKE_CONE)) {
			if (hit(p, e, p.position(), ViperionConfig.STRIKE_DAMAGE, finisher ? ViperionConfig.STRIKE_FINISHER_KNOCKBACK : ViperionConfig.STRIKE_KNOCKBACK,
					finisher ? ViperionConfig.STRIKE_FINISHER_LIFT : 0.0)) {
				n++;
			}
		}
		if (n > 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, finisher ? 0.8f : 1.1f);
		}
		return n;
	}

	private static void tickStrike(ServerPlayer p, Session s, long now) {
		if (s.hitAt > 0L && now >= s.hitAt) {
			land(p, s);
		}
		if (s.queued && s.comboStep > 0 && s.comboStep < ViperionConfig.STRIKE_HITS && now - s.comboLast >= ViperionConfig.STRIKE_GAP) {
			swing(p, s, now);
		}
		if (s.comboStep > 0 && s.hitAt == 0L && !s.queued
				&& (s.comboStep >= ViperionConfig.STRIKE_HITS || now - s.comboLast > ViperionConfig.STRIKE_WINDOW)) {
			s.comboStep = 0; // done, or the window lapsed: the next press starts over
		}
	}

	/** One wide swing: everything within {@link ViperionConfig#SWEEP_RADIUS} in front (a wide arc). Returns how many. */
	public static int sweep(ServerPlayer p) {
		Miraculous.setAnim(p, Viperion.ANIM_SWEEP);
		Miraculous.setCooldown(p, ViperionConfig.SWEEP, ViperionConfig.SWEEP_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		Vec3 f = flatLook(p);
		double r = ViperionConfig.SWEEP_RADIUS;
		for (int i = -10; i <= 10; i++) {
			double a = Math.atan2(f.z, f.x) + i * Math.toRadians(10.0);
			level.sendParticles(i % 2 == 0 ? TEAL_BIG : STRING, c.x + Math.cos(a) * r * 0.8, c.y + 1.1, c.z + Math.sin(a) * r * 0.8, 1, 0, 0.05, 0, 0);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + f.x * 1.5, c.y + 1.1, c.z + f.z * 1.5, 3, 0.8, 0.1, 0.8, 0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 1.0f, 0.6f);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1.0, 0), r)) {
			if (!inFront(p, e.position(), ViperionConfig.SWEEP_DOT) || Math.abs(e.getY() - c.y) > 2.5) {
				continue;
			}
			if (hit(p, e, c, ViperionConfig.SWEEP_DAMAGE, ViperionConfig.SWEEP_KNOCKBACK, 0.2)) {
				n++;
			}
		}
		return n;
	}

	// ================================================================ G: Sonic Chord / Shift+G: Resonance

	/** One strum: a cone of sound {@link ViperionConfig#CHORD_RANGE} long along his look -- damage and a hard push. Returns how many. */
	public static int chord(ServerPlayer p) {
		Miraculous.setAnim(p, Viperion.ANIM_CHORD);
		Miraculous.setCooldown(p, ViperionConfig.CHORD, ViperionConfig.CHORD_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle().normalize();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		// the wave: rings of sound widening along the cone, notes riding them
		double spread = Math.sqrt(1.0 - ViperionConfig.CHORD_DOT * ViperionConfig.CHORD_DOT) / ViperionConfig.CHORD_DOT;
		for (int k = 1; k <= 6; k++) {
			double d = ViperionConfig.CHORD_RANGE * k / 6.0;
			double rr = Math.max(0.4, d * spread);
			int n = 8 + k * 3;
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				Vec3 q = eye.add(look.scale(d)).add(side.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr));
				level.sendParticles(k % 2 == 0 ? TEAL : STRING, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			}
			Vec3 m = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.NOTE, m.x, m.y, m.z, 2, rr * 0.4, rr * 0.4, rr * 0.4, 1.0);
		}
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 1.4f, 0.5f);
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 1.2f, 0.75f);
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 1.0f, 1.0f);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.8f);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye, ViperionConfig.CHORD_RANGE + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > ViperionConfig.CHORD_RANGE + e.getBbWidth() * 0.5 || (d > 1.2 && to.scale(1.0 / d).dot(look) < ViperionConfig.CHORD_DOT) || !p.hasLineOfSight(e)) {
				continue;
			}
			if (hit(p, e, p.position(), ViperionConfig.CHORD_DAMAGE, ViperionConfig.CHORD_KNOCKBACK, ViperionConfig.CHORD_LIFT)) {
				hit++;
			}
		}
		return hit;
	}

	/** A ring of sound round him: everything within {@link ViperionConfig#RESONANCE_RADIUS} is hurt a little and stunned. Returns how many. */
	public static int resonance(ServerPlayer p) {
		Miraculous.setAnim(p, Viperion.ANIM_RESONANCE);
		Miraculous.setCooldown(p, ViperionConfig.RESONANCE, ViperionConfig.RESONANCE_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		for (int k = 1; k <= 4; k++) {
			double r = ViperionConfig.RESONANCE_RADIUS * k / 4.0;
			ring(level, k % 2 == 0 ? TEAL_BIG : STRING, c.add(0, 0.3 + 0.25 * k, 0), r, 10 + 6 * k);
		}
		level.sendParticles(ParticleTypes.NOTE, c.x, c.y + 2.0, c.z, 10, 1.5, 0.4, 1.5, 1.0);
		sound(p, SoundEvents.NOTE_BLOCK_BELL, 1.4f, 0.6f);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.6f);
		sound(p, SoundEvents.BELL_RESONATE, 0.8f, 1.4f);
		int n = 0;
		double r = ViperionConfig.RESONANCE_RADIUS;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1.0, 0), r)) {
			if (e.distanceToSqr(p) > r * r) {
				continue;
			}
			if (hit(p, e, c, ViperionConfig.RESONANCE_DAMAGE, 0.2, 0.0)) {
				stun(e, ViperionConfig.RESONANCE_STUN_TICKS);
				level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 3, 0.3, 0.1, 0.3, 1.0);
				n++;
			}
		}
		return n;
	}

	// ================================================================ X: Serpent Dash / Shift+X: Coil Leap

	/** A low, fast slither {@link ViperionConfig#DASH_DISTANCE} along his look (the swim / crawl pose: under attacks). */
	public static void dash(ServerPlayer p) {
		Session s = session(p);
		s.driveDir = flatLook(p);
		s.driveTicks = (int) Math.ceil(ViperionConfig.DASH_DISTANCE / ViperionConfig.DASH_SPEED);
		Miraculous.setVal(p, Viperion.V_DASH, now(p) + s.driveTicks + ViperionConfig.DASH_LOW_EXTRA);
		Miraculous.setAnim(p, Viperion.ANIM_DASH);
		Miraculous.setCooldown(p, ViperionConfig.DASH, ViperionConfig.DASH_COOLDOWN);
		dashStep(p, true);
		drive(p, s);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 1.6f);
		sound(p, SoundEvents.SAND_BREAK, 0.8f, 1.6f);
	}

	private static final net.minecraft.resources.ResourceLocation DASH_STEP_ID = com.projecthero.mod.hero.power.PowerToggles.id("viperion_dash_step");

	/** Serpent Dash's step assist on (while the dash runs) or off. */
	public static void dashStep(ServerPlayer p, boolean on) {
		if (on) {
			com.projecthero.mod.hero.power.PowerToggles.modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, DASH_STEP_ID,
					ViperionConfig.DASH_STEP_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		} else {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, DASH_STEP_ID);
		}
	}

	private static void drive(ServerPlayer p, Session s) {
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(s.driveDir.x * ViperionConfig.DASH_SPEED, Math.min(v.y, 0.0) - 0.04, s.driveDir.z * ViperionConfig.DASH_SPEED));
	}

	/** Coil and spring: forward and up, about {@link ViperionConfig#COIL_BLOCKS} blocks (the landing is free). */
	public static Vec3 coil(ServerPlayer p) {
		Miraculous.setAnim(p, Viperion.ANIM_COIL);
		Miraculous.setCooldown(p, ViperionConfig.COIL, ViperionConfig.COIL_COOLDOWN);
		Miraculous.setVal(p, Viperion.V_LEAP, now(p) + 40);
		// v0.16.7 (user): the landing never hurts -- the core's safe landing (armed by the cooldown) and, belt and
		// braces, the fall is forgotten every tick until he is down again (see tick)
		Miraculous.safeLanding(p);
		Session s = session(p);
		s.coilAir = true;
		s.coilAt = now(p);
		Vec3 f = flatLook(p);
		double fwd = ViperionConfig.COIL_BLOCKS / 12.0;
		Vec3 v = new Vec3(f.x * fwd, ViperionConfig.COIL_UP, f.z * fwd);
		AbilityHelpers.launchSelf(p, v);
		ServerLevel level = p.serverLevel();
		ring(level, TEAL, p.position().add(0, 0.1, 0), 0.8, 14);
		ring(level, GREEN, p.position().add(0, 0.4, 0), 0.5, 10);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.8f);
		sound(p, SoundEvents.GOAT_LONG_JUMP, 1.0f, 1.2f);
		return v;
	}

	// ================================================================ Z: Venom Strike (Shift+Z: SecondChance)

	/** A fang-like jab of the lyre's point: {@link ViperionConfig#VENOM_DAMAGE} and strong Poison. Returns what it hit. */
	public static @Nullable LivingEntity venom(ServerPlayer p) {
		Miraculous.setAnim(p, Viperion.ANIM_VENOM);
		Miraculous.setCooldown(p, ViperionConfig.VENOM, ViperionConfig.VENOM_COOLDOWN);
		LivingEntity t = AbilityHelpers.raycastEntity(p, ViperionConfig.VENOM_REACH);
		if (t == null || !HeroTargets.canHarm(p, t)) {
			t = nearestInFront(p, ViperionConfig.VENOM_REACH);
		}
		sound(p, SoundEvents.CAT_HISS, 0.5f, 1.6f);
		sound(p, SoundEvents.TRIDENT_HIT, 0.7f, 1.6f);
		if (t == null || !hit(p, t, p.position(), ViperionConfig.VENOM_DAMAGE, 0.2, 0.0)) {
			return null;
		}
		t.addEffect(new MobEffectInstance(MobEffects.POISON, ViperionConfig.VENOM_POISON_TICKS, ViperionConfig.VENOM_POISON_AMP, false, true, true), p);
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
		p.serverLevel().sendParticles(GREEN, c.x, c.y, c.z, 14, 0.25, 0.3, 0.25, 0.0);
		p.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.05);
		return t;
	}

	// ================================================================ V: Constrict / Shift+V: String Shield

	/** The strings wrap the creature he aims at: rooted (bosses only slowed) and squeezed for {@link ViperionConfig#CONSTRICT_TICKS}. Returns it. */
	public static @Nullable LivingEntity constrict(ServerPlayer p) {
		LivingEntity t = aimed(p, ViperionConfig.CONSTRICT_RANGE, ViperionConfig.CONSTRICT_ASSIST_DEGREES);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.viperion.no_target", net.minecraft.ChatFormatting.AQUA);
			return null;
		}
		Session s = session(p);
		s.constrictId = t.getId();
		s.constrictAt = t.position();
		s.nextSqueeze = now(p) + ViperionConfig.CONSTRICT_SQUEEZE_EVERY;
		Miraculous.setVal(p, Viperion.V_CONSTRICT, now(p) + ViperionConfig.CONSTRICT_TICKS);
		Miraculous.setVal(p, Viperion.V_CONSTRICT_ID, t.getId() + 1L);
		Miraculous.setAnim(p, Viperion.ANIM_CONSTRICT);
		Miraculous.setCooldown(p, ViperionConfig.CONSTRICT, ViperionConfig.CONSTRICT_COOLDOWN);
		if (isBoss(t)) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, ViperionConfig.CONSTRICT_TICKS, 2);
		}
		AbilityHelpers.line(p.serverLevel(), chest(p), t.position().add(0, t.getBbHeight() * 0.5, 0), STRING, 4.0);
		sound(p, SoundEvents.NOTE_BLOCK_HARP, 1.0f, 1.6f);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.8f);
		return t;
	}

	private static void tickConstrict(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(Viperion.V_CONSTRICT);
		if (until == 0L) {
			return;
		}
		Entity e = s == null || s.constrictId < 0 ? null : p.serverLevel().getEntity(s.constrictId);
		if (now >= until || !(e instanceof LivingEntity t) || !t.isAlive()) {
			Miraculous.setVal(p, Viperion.V_CONSTRICT, 0L);
			Miraculous.setVal(p, Viperion.V_CONSTRICT_ID, 0L);
			if (s != null) {
				s.constrictId = -1;
			}
			if (e != null) {
				p.serverLevel().sendParticles(STRING, e.getX(), e.getY() + 1.0, e.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
			}
			return;
		}
		boolean boss = isBoss(t);
		if (!boss && s.constrictAt != null) {
			// rooted: back on its spot, no motion (it may still turn its head)
			if (t.position().distanceToSqr(s.constrictAt) > 0.01) {
				t.teleportTo(s.constrictAt.x, s.constrictAt.y, s.constrictAt.z);
			}
			t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
			t.hurtMarked = true;
			if (t instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
		// the strings spiral round it
		ServerLevel level = p.serverLevel();
		double h = t.getBbHeight();
		double r = t.getBbWidth() * 0.7 + 0.15;
		for (int i = 0; i < 3; i++) {
			double a = (now * 0.6) + i * (Math.PI * 2 / 3);
			double y = t.getY() + h * (0.15 + 0.7 * ((now + i * 7) % 20) / 20.0);
			level.sendParticles(STRING, t.getX() + Math.cos(a) * r, y, t.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		if (now % 5 == 0) {
			AbilityHelpers.line(level, chest(p), t.position().add(0, h * 0.5, 0), TEAL, 1.5);
		}
		if (now >= s.nextSqueeze) {
			s.nextSqueeze = now + ViperionConfig.CONSTRICT_SQUEEZE_EVERY;
			if (hit(p, t, t.position(), ViperionConfig.CONSTRICT_SQUEEZE_DAMAGE, 0.0, 0.0)) {
				level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.6f, 0.5f + level.random.nextFloat() * 0.3f);
			}
			// the strings keep it in place (hit's own knockback is 0; a creature stays put)
			t.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** The lyre spins before him for {@link ViperionConfig#SHIELD_TICKS}: hits and projectiles from the front stop. */
	public static void shield(ServerPlayer p) {
		Miraculous.setVal(p, Viperion.V_SHIELD, now(p) + ViperionConfig.SHIELD_TICKS);
		Miraculous.setAnim(p, Viperion.ANIM_SHIELD);
		Miraculous.setCooldown(p, ViperionConfig.SHIELD, ViperionConfig.SHIELD_COOLDOWN);
		Vec3 c = chest(p).add(flatLook(p).scale(0.8));
		p.serverLevel().sendParticles(TEAL_BIG, c.x, c.y, c.z, 16, 0.4, 0.4, 0.4, 0.0);
		sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1.0f, 1.2f);
		sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0f, 1.2f);
	}

	private static void tickShield(ServerPlayer p, MiraculousState st, long now) {
		long until = st.val(Viperion.V_SHIELD);
		if (until == 0L) {
			return;
		}
		if (now >= until) {
			Miraculous.setVal(p, Viperion.V_SHIELD, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = chest(p);
		double r = ViperionConfig.SHIELD_REACH;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), Entity::isAlive)) {
			if (proj.getOwner() == p || !inFront(p, proj.position(), ViperionConfig.FRONT_DOT)) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			if (proj.position().distanceTo(c) > r + 0.6 && proj.position().add(v).distanceTo(c) > r + 0.6) {
				continue;
			}
			if (v.lengthSqr() > 1.0e-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
				continue; // moving away
			}
			Vec3 at = proj.position();
			proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
			proj.setOwner(p);
			if (proj instanceof AbstractArrow arrow) {
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			}
			level.sendParticles(STRING, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.9f, 1.8f);
		}
		if (now % 2 == 0) {
			// the spinning lyre's ring of light before him
			Vec3 f = flatLook(p);
			Vec3 side = new Vec3(-f.z, 0, f.x);
			Vec3 mid = c.add(f.scale(0.9));
			double a = now * 0.9;
			for (int i = 0; i < 4; i++) {
				double b = a + i * Math.PI / 2;
				Vec3 q = mid.add(side.scale(Math.cos(b) * 0.9)).add(0, Math.sin(b) * 0.9, 0);
				level.sendParticles(i % 2 == 0 ? TEAL : STRING, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			}
		}
	}

	// ================================================================ C: Heat Sense (toggle) / Shift+C: Hiss

	/** Toggles the snake's infrared (the glow is drawn by his own client). True: now on. */
	public static boolean heatSense(ServerPlayer p) {
		boolean on = Miraculous.state(p).val(Viperion.V_HEAT) == 0L;
		Miraculous.setVal(p, Viperion.V_HEAT, on ? 1L : 0L);
		if (on) {
			Miraculous.setAnim(p, Viperion.ANIM_HEAT);
			p.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.8f);
		} else {
			Miraculous.setCooldown(p, ViperionConfig.HEAT, ViperionConfig.HEAT_COOLDOWN);
			p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
		}
		return on;
	}

	/** Is this one scared off by the Hiss? Mobs only -- never a player, a boss or his own pet. */
	public static boolean scares(ServerPlayer p, LivingEntity e) {
		if (e instanceof Player || !(e instanceof Mob) || isBoss(e) || !e.isAlive()) {
			return false;
		}
		return !(e instanceof net.minecraft.world.entity.OwnableEntity pet && p.getUUID().equals(pet.getOwnerUUID()));
	}

	/** A loud hiss: every mob within {@link ViperionConfig#HISS_RADIUS} runs from him for {@link ViperionConfig#HISS_TICKS}. Returns how many. */
	public static int hiss(ServerPlayer p) {
		Session s = session(p);
		s.scared.clear();
		double r = ViperionConfig.HISS_RADIUS;
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.distanceToSqr(p) <= r * r)) {
			if (!scares(p, e)) {
				continue;
			}
			s.scared.add(e.getId());
			flee((Mob) e, p.position());
			level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
		}
		Miraculous.setVal(p, Viperion.V_HISS, now(p) + ViperionConfig.HISS_TICKS);
		Miraculous.setAnim(p, Viperion.ANIM_HISS);
		Miraculous.setCooldown(p, ViperionConfig.HISS, ViperionConfig.HISS_COOLDOWN);
		Vec3 m = p.getEyePosition().add(p.getLookAngle().scale(0.6));
		level.sendParticles(GREEN, m.x, m.y - 0.2, m.z, 18, 0.3, 0.2, 0.3, 0.0);
		ring(level, GREEN, p.position().add(0, 0.2, 0), 2.5, 20);
		sound(p, SoundEvents.CAT_HISS, 2.0f, 0.55f);
		sound(p, SoundEvents.LLAMA_SPIT, 0.8f, 0.6f);
		return s.scared.size();
	}

	private static void flee(Mob mob, Vec3 from) {
		mob.setTarget(null);
		if (mob instanceof PathfinderMob pm) {
			Vec3 away = DefaultRandomPos.getPosAway(pm, 16, 7, from);
			if (away != null) {
				pm.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
			}
		}
	}

	/** Is {@code mob} still running from a Hiss? (GameTests) */
	public static boolean fleeing(ServerPlayer p, Mob mob) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.scared.contains(mob.getId()) && Miraculous.state(p).val(Viperion.V_HISS) > now(p);
	}

	// ================================================================ incoming damage

	/** The kit's {@code modifyIncomingDamage}: the String Shield stops what comes from the front. */
	public static float incoming(ServerPlayer p, DamageSource source, float amount) {
		if (Miraculous.state(p).val(Viperion.V_SHIELD) > now(p)) {
			Vec3 from = source.getDirectEntity() != null ? source.getDirectEntity().position() : source.getSourcePosition();
			if (from != null && source.getDirectEntity() != p && inFront(p, from, ViperionConfig.FRONT_DOT)) {
				sound(p, SoundEvents.NOTE_BLOCK_HARP, 0.9f, 1.6f);
				Vec3 c = chest(p).add(flatLook(p).scale(0.7));
				p.serverLevel().sendParticles(STRING, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.05);
				return 0f;
			}
		}
		return amount;
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		long now = now(p);
		MiraculousState st = Miraculous.state(p);
		Session s = SESSIONS.get(p.getUUID());
		// v0.16.7 (user): transformed, poison does not take
		if (p.hasEffect(MobEffects.POISON)) {
			p.removeEffect(MobEffects.POISON);
		}
		// the combo's swings
		if (s != null) {
			tickStrike(p, s, now);
		}
		if (st.val(Viperion.V_COMBO) != 0L && now >= st.val(Viperion.V_COMBO) && (s == null || (s.hitAt == 0L && !s.queued))) {
			Miraculous.setVal(p, Viperion.V_COMBO, 0L);
		}
		// Coil Leap: no fall damage on the landing (the fall is forgotten until he is down)
		if (s != null && s.coilAir) {
			p.resetFallDistance();
			boolean down = p.onGround() || p.isInWater() || p.onClimbable();
			if ((down && now - s.coilAt > 6) || now - s.coilAt > 20 * 20) {
				s.coilAir = false;
			} else if (!down && !Miraculous.hasSafeLanding(p)) {
				Miraculous.safeLanding(p); // (dropped early by a brush with the ground: armed again while still airborne)
			}
		}
		// the slither
		if (s != null && s.driveTicks > 0) {
			s.driveTicks--;
			if (s.driveTicks > 0) {
				drive(p, s);
				if (now % 2 == 0) {
					net.minecraft.core.BlockPos under = p.blockPosition().below();
					var b = p.serverLevel().getBlockState(under);
					if (!b.isAir()) {
						p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, b), p.getX(), p.getY() + 0.1, p.getZ(), 3,
								0.3, 0.02, 0.3, 0.1);
					}
					p.serverLevel().sendParticles(TEAL, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
				}
			}
			// v0.16.7 (user): when it ends nothing brakes him -- the last push carries on and slows naturally
		}
		if (st.val(Viperion.V_DASH) != 0L && now >= st.val(Viperion.V_DASH)) {
			Miraculous.setVal(p, Viperion.V_DASH, 0L);
		}
		// v0.16.7 (user): the slither climbs steps up to 2 blocks while it runs
		dashStep(p, Miraculous.state(p).vals.getOrDefault(Viperion.V_DASH, 0L) != 0L);
		long leap = st.val(Viperion.V_LEAP);
		if (leap != 0L && (now >= leap || (p.onGround() && now >= leap - 30))) {
			Miraculous.setVal(p, Viperion.V_LEAP, 0L);
		}
		tickConstrict(p, Miraculous.state(p), s, now);
		tickShield(p, Miraculous.state(p), now);
		// the hiss: keep them running
		long hiss = st.val(Viperion.V_HISS);
		if (hiss != 0L) {
			if (now >= hiss) {
				Miraculous.setVal(p, Viperion.V_HISS, 0L);
				if (s != null) {
					s.scared.clear();
				}
			} else if (s != null && now % 10 == 0) {
				for (int id : s.scared) {
					if (p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive() && (m.getTarget() != null || m.getNavigation().isDone())) {
						flee(m, p.position());
					}
				}
			}
		}
	}

	/** Detransformation / logout: every running move of his ends. */
	static void endAll(ServerPlayer p) {
		dashStep(p, false);
		Miraculous.edit(p, n -> {
			n.vals.keySet().removeIf(k -> k.startsWith("vp_"));
			n.vecs.keySet().removeIf(k -> k.startsWith("vp_"));
		});
		SESSIONS.remove(p.getUUID());
	}

	/** Gametests: whether he holds Sass and is transformed (shorthand). */
	public static boolean active(Player p) {
		return Miraculous.transformedAs(p, Kwami.SASS);
	}
}
