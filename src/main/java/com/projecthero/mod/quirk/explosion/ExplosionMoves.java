package com.projecthero.mod.quirk.explosion;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Bakugo's twelve moves, his Sweat meter and the hidden Arm Strain, plus the per-player sessions of the moves
 * that run over time (Blast Rush, the Auto-Cannon volley, the smoke cloud, Howitzer Impact, the Max Shot charge, the
 * Cluster's second ring, the Explosive Counter window, the soft landing after his own launches). Every public move is
 * called by {@link ExplosionKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <p><b>Sweat</b> ({@link Quirks#meter}, 0..100) builds while he moves and fights, faster in hot biomes, the Nether or on
 * fire, and dries off when he stands still (washes off in water / rain). Every move spends some; every blast is
 * {@link ExplosionConfig#powerAt} times stronger (0.6x dry .. 1.4x soaked). Howitzer Impact and Max Shot need a real
 * sweat. <b>Arm Strain</b> (0..100, never on the HUD; the arms smoke above 70) builds with every blast and eases off
 * when he stops; full strain is {@link Quirks#overload} for {@link ExplosionConfig#STRAIN_TICKS} with Weakness II.
 *
 * <p>His explosions never touch him: he is excluded from every blast, and fall / wall damage after his own launches is
 * cancelled ({@link #cancelsDamage}).
 */
public final class ExplosionMoves {
	static final class Session {
		Vec3 lastPos;
		long combatUntil;
		long lastDealtSweat = -1000L;
		long stillSince;
		float strain;
		long lastBlast = -1000L;
		int strainSynced;
		// Blast Rush
		boolean rush;
		long rushStart;
		/** We set the SERVER copy of mayfly (never synced) so a long Rush is not kicked as flying; restored when it ends. */
		boolean grantedMayfly;
		// the Auto-Cannon
		int autoLeft;
		long autoNext;
		boolean autoLeftHand;
		// the smoke cloud
		@Nullable Vec3 smoke;
		long smokeUntil;
		// Howitzer Impact
		boolean howitzer;
		long howitzerStart;
		Vec3 howitzerTarget = Vec3.ZERO;
		int howitzerTargetId = -1;
		float howitzerPower;
		final Set<Integer> vortexHit = new HashSet<>();
		// Max Shot
		boolean maxShot;
		long maxShotAt;
		float maxShotPower;
		// the Cluster's second ring
		long clusterRingAt = -1L;
		float clusterPower;
		Vec3 clusterAt = Vec3.ZERO;
		final Set<Integer> clusterHit = new HashSet<>();
		// the Explosive Counter
		long counterUntil;
		float counterPower;
		// no fall / wall damage until he lands after his own launch
		long softUntil;
		long softSince;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ExplosionMoves() {
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

	// ================================================================ Sweat / power / strain

	/** The blast power right now (from the Sweat he has before the move spends any). */
	public static float power(ServerPlayer p) {
		return ExplosionConfig.powerAt(Quirks.meter(p));
	}

	/** Bigger blasts with more sweat: the radius grows a little with power. */
	public static double radius(double base, float power) {
		return base * (0.7 + 0.3 * power);
	}

	/** Spends up to {@code amount} Sweat (a dry Bakugo can still blast, just weakly). */
	private static void spend(ServerPlayer p, float amount) {
		Quirks.addMeter(p, -amount);
	}

	/** Server: the current Arm Strain, 0..100. */
	public static float strain(ServerPlayer p) {
		return session(p).strain;
	}

	public static void setStrain(ServerPlayer p, float value) {
		Session s = session(p);
		s.strain = Mth.clamp(value, 0f, 100f);
		syncStrain(p, s, true);
	}

	/** A blast strains the arms; full strain is Arm Strain. */
	public static void strain(ServerPlayer p, float amount) {
		Session s = session(p);
		s.lastBlast = now(p);
		s.strain = Math.min(100f, s.strain + amount);
		if (s.strain >= 100f) {
			strainOut(p);
		}
	}

	/** Arm Strain: 5 s of Weakness II and no moves (every running move stops). */
	public static void strainOut(ServerPlayer p) {
		Session s = session(p);
		s.strain = 0f;
		Quirks.overload(p, ExplosionConfig.STRAIN_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ExplosionConfig.STRAIN_TICKS, ExplosionConfig.STRAIN_WEAKNESS_AMP, false, true, true));
		Quirks.setAnim(p, Explosion.ANIM_STRAIN);
		Quirks.say(p, "message.projecthero.quirk.overloaded.explosion", ChatFormatting.RED);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.play(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.7f);
		ExplosionBlast.play(level, p.position(), SoundEvents.PLAYER_HURT, 0.6f, 0.8f);
		for (boolean right : new boolean[] { true, false }) {
			Vec3 h = palm(p, right, 0.1, 0.75, 0.42);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, h.x, h.y, h.z, 8, 0.1, 0.2, 0.1, 0.02);
		}
		syncStrain(p, s, true);
	}

	private static void syncStrain(ServerPlayer p, Session s, boolean force) {
		int v = Math.round(s.strain);
		if (force || Math.abs(v - s.strainSynced) >= 2 || (v == 0 && s.strainSynced != 0)) {
			s.strainSynced = v;
			Quirks.setVal(p, Explosion.V_STRAIN, v);
		}
	}

	/** Marks him as in a fight (hits either way). A hit he lands sweats at most once every {@link ExplosionConfig#HIT_SWEAT_INTERVAL} (a blast hits many at once). */
	public static void markCombat(ServerPlayer p, float sweat, boolean dealt) {
		Session s = session(p);
		long now = now(p);
		s.combatUntil = now + ExplosionConfig.COMBAT_TICKS;
		if (dealt) {
			if (now - s.lastDealtSweat < ExplosionConfig.HIT_SWEAT_INTERVAL && now >= s.lastDealtSweat) {
				return;
			}
			s.lastDealtSweat = now;
		}
		Quirks.addMeter(p, sweat * heat(p));
	}

	/** How much faster he sweats here: hot biomes, the Nether, on fire (cold biomes slower). */
	public static float heat(ServerPlayer p) {
		Level level = p.level();
		float m = 1f;
		if (level.dimension() == Level.NETHER || level.dimensionType().ultraWarm()) {
			m = ExplosionConfig.NETHER_MULTIPLIER;
		} else {
			float t = level.getBiome(p.blockPosition()).value().getBaseTemperature();
			if (t >= ExplosionConfig.HOT_TEMPERATURE) {
				m = ExplosionConfig.HOT_MULTIPLIER;
			} else if (t < ExplosionConfig.COLD_TEMPERATURE) {
				m = ExplosionConfig.COLD_MULTIPLIER;
			}
		}
		if (p.isOnFire() || p.isInLava()) {
			m *= ExplosionConfig.BURNING_MULTIPLIER;
		}
		return m;
	}

	// ================================================================ the tick

	public static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		ServerLevel level = p.serverLevel();
		// ---- Sweat
		Vec3 pos = p.position();
		double moved = s.lastPos == null ? 0.0 : Math.hypot(pos.x - s.lastPos.x, pos.z - s.lastPos.z);
		s.lastPos = pos;
		boolean fighting = s.combatUntil > now;
		float gain = 0f;
		if (moved > 0.08 || s.rush) {
			gain += p.isSprinting() || s.rush ? ExplosionConfig.SWEAT_SPRINT : ExplosionConfig.SWEAT_WALK;
			s.stillSince = now;
		}
		if (fighting) {
			gain += ExplosionConfig.SWEAT_COMBAT;
			s.stillSince = now;
		}
		if (p.isInWaterOrRain()) {
			Quirks.addMeter(p, -ExplosionConfig.SWEAT_WASH);
		} else if (gain > 0f) {
			Quirks.addMeter(p, gain * heat(p));
		} else if (now - s.stillSince > ExplosionConfig.DRY_DELAY) {
			Quirks.addMeter(p, -ExplosionConfig.SWEAT_DRY);
		}
		// ---- Arm Strain eases off
		if (s.strain > 0f && now - s.lastBlast > ExplosionConfig.STRAIN_DECAY_DELAY) {
			s.strain = Math.max(0f, s.strain - ExplosionConfig.STRAIN_DECAY);
		}
		if (now % 10 == 0) {
			syncStrain(p, s, false);
		}
		if (s.strain >= ExplosionConfig.STRAIN_WARN && now % 8 == 0) {
			for (boolean right : new boolean[] { true, false }) {
				Vec3 h = palm(p, right, 0.05, 0.8, 0.42);
				level.sendParticles(ParticleTypes.SMOKE, h.x, h.y, h.z, 2, 0.05, 0.15, 0.05, 0.01);
			}
		}
		// ---- the soft landing after his own launches
		if (s.softUntil > now) {
			p.resetFallDistance();
			if (p.onGround() && now - s.softSince > 3 && !s.rush && !s.howitzer) {
				s.softUntil = 0L;
			}
		}
		// ---- running moves
		if (s.rush) {
			tickRush(p, s, now);
		}
		if (s.autoLeft > 0 && now >= s.autoNext) {
			s.autoNext = now + ExplosionConfig.AUTO_INTERVAL;
			s.autoLeft--;
			autoShot(p, s);
			if (s.autoLeft <= 0) {
				Quirks.setVal(p, Explosion.V_AUTO, 0L);
			}
		}
		if (s.smoke != null) {
			tickSmoke(p, s, now);
		}
		if (s.howitzer) {
			tickHowitzer(p, s, now);
		}
		if (s.maxShot) {
			tickMaxShot(p, s, now);
		}
		if (s.clusterRingAt >= 0L && now >= s.clusterRingAt) {
			s.clusterRingAt = -1L;
			clusterRing(p, s.clusterAt, ExplosionConfig.CLUSTER_OUTER, s.clusterPower, s.clusterHit);
		}
		if (s.counterUntil > 0L && now >= s.counterUntil) {
			s.counterUntil = 0L;
		}
		// a soaked Bakugo's palms crackle
		if (Quirks.meter(p) >= 60f && now % 12 == 0 && !s.rush) {
			ExplosionBlast.palm(level, palm(p, p.getRandom().nextBoolean(), 0.15, 0.75, 0.42), 2);
		}
	}

	/** Ends every running move (Arm Strain, Erased, death, revoke). The soft landing stays so he can't die of it. */
	public static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		boolean any = s.rush || s.autoLeft > 0 || s.smoke != null || s.howitzer || s.maxShot || s.counterUntil > 0L;
		if (s.rush || s.howitzer) {
			soft(p, s);
		}
		s.rush = false;
		releaseFloat(p, s);
		s.autoLeft = 0;
		s.smoke = null;
		s.smokeUntil = 0L;
		s.howitzer = false;
		s.maxShot = false;
		s.clusterRingAt = -1L;
		s.counterUntil = 0L;
		if (any) {
			Quirks.edit(p, n -> {
				n.vals.remove(Explosion.V_RUSH);
				n.vals.remove(Explosion.V_AUTO);
				n.vals.remove(Explosion.V_SMOKE);
				n.vals.remove(Explosion.V_HOWITZER);
				n.vals.remove(Explosion.V_MAX_SHOT);
				n.vals.remove(Explosion.V_COUNTER);
				n.vecs.remove(Explosion.VEC_SMOKE);
			});
		}
	}

	/** True while a big move (the Howitzer spin, the Max Shot charge) holds every other key. */
	public static boolean busy(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && (s.howitzer || s.maxShot);
	}

	public static boolean rushing(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.rush;
	}

	/** No fall / wall damage until he lands after his own launch. */
	public static boolean softLanding(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && (s.rush || s.howitzer || s.softUntil > now(p));
	}

	private static void soft(ServerPlayer p, Session s) {
		s.softSince = now(p);
		s.softUntil = now(p) + ExplosionConfig.SOFT_LANDING_MAX;
		p.resetFallDistance();
	}

	// ================================================================ geometry

	/** A palm in his body frame: {@code fwd} ahead, {@code up} above the feet, {@code side} out to that side. */
	public static Vec3 palm(ServerPlayer p, boolean right, double fwd, double up, double side) {
		Vec3 f = Vec3.directionFromRotation(0f, p.getYRot());
		Vec3 r = f.cross(new Vec3(0, 1, 0)).normalize();
		return p.position().add(f.scale(fwd)).add(r.scale(right ? side : -side)).add(0, up, 0);
	}

	/** The right hand, out in front at shoulder height (where a shot leaves). */
	private static Vec3 shotHand(ServerPlayer p, boolean right) {
		Vec3 look = p.getLookAngle();
		Vec3 r = look.cross(new Vec3(0, 1, 0));
		r = r.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : r.normalize();
		return p.getEyePosition().add(look.scale(0.7)).add(r.scale(right ? 0.38 : -0.38)).add(0, -0.35, 0);
	}

	/** The creature under the crosshair, else the valid one nearest the crosshair within {@code degrees} that he can see. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && ExplosionBlast.isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && ExplosionBlast.isTarget(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > range) {
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

	private static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	// ================================================================ R: AP Shot / Shift+R: AP Shot Auto-Cannon

	/** A focused blast fired from the palm along the look: the first creature or block it meets explodes. Returns the impact point. */
	public static Vec3 apShot(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.AP_SWEAT);
		ServerLevel level = p.serverLevel();
		Vec3 hand = shotHand(p, true);
		Vec3 end = ExplosionBlast.rayEnd(p, p.getEyePosition(), p.getLookAngle(), ExplosionConfig.AP_RANGE, 0.3, null);
		ExplosionBlast.palm(level, hand, 8);
		ExplosionBlast.trail(level, hand, end, 3.0);
		ExplosionBlast.play(level, hand, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.4f);
		ExplosionBlast.blast(p, end, radius(ExplosionConfig.AP_RADIUS, power), ExplosionConfig.AP_DAMAGE * power,
				ExplosionConfig.AP_KNOCKBACK, 0.25, ExplosionBlast.MEDIUM, null);
		Quirks.setAnim(p, Explosion.ANIM_AP_SHOT);
		Quirks.setCooldown(p, ExplosionConfig.AP_SHOT, ExplosionConfig.AP_COOLDOWN);
		strain(p, ExplosionConfig.AP_STRAIN);
		return end;
	}

	/** A rapid volley of small AP shots, alternating palms, following the look ({@link ExplosionConfig#AUTO_SHOTS} of them). */
	public static void autoCannon(ServerPlayer p) {
		Session s = session(p);
		s.autoLeft = ExplosionConfig.AUTO_SHOTS;
		s.autoNext = now(p);
		Quirks.setVal(p, Explosion.V_AUTO, now(p) + (long) ExplosionConfig.AUTO_SHOTS * ExplosionConfig.AUTO_INTERVAL + 4L);
		Quirks.setAnim(p, Explosion.ANIM_AUTO);
		Quirks.setCooldown(p, ExplosionConfig.AUTO_CANNON, ExplosionConfig.AUTO_COOLDOWN);
	}

	private static void autoShot(ServerPlayer p, Session s) {
		float power = power(p);
		spend(p, ExplosionConfig.AUTO_SWEAT);
		s.autoLeftHand = !s.autoLeftHand;
		ServerLevel level = p.serverLevel();
		var rnd = p.getRandom();
		float spread = (float) ExplosionConfig.AUTO_SPREAD_DEGREES;
		Vec3 dir = Vec3.directionFromRotation(p.getXRot() + (rnd.nextFloat() - 0.5f) * 2f * spread,
				p.getYRot() + (rnd.nextFloat() - 0.5f) * 2f * spread);
		Vec3 hand = shotHand(p, !s.autoLeftHand);
		Vec3 end = ExplosionBlast.rayEnd(p, p.getEyePosition(), dir, ExplosionConfig.AUTO_RANGE, 0.25, null);
		ExplosionBlast.palm(level, hand, 4);
		ExplosionBlast.trail(level, hand, end, 1.5);
		ExplosionBlast.blast(p, end, radius(ExplosionConfig.AUTO_RADIUS, power), ExplosionConfig.AUTO_DAMAGE * power, 0.35, 0.1,
				ExplosionBlast.SMALL, null);
		strain(p, ExplosionConfig.AUTO_STRAIN);
	}

	// ================================================================ G: Blast Punch / Shift+G: Explosion Uppercut

	/** A point-blank explosion from the fist, just in front of him. Returns how many it hit. */
	public static int blastPunch(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.PUNCH_SWEAT);
		Vec3 at = ExplosionBlast.rayEnd(p, p.getEyePosition(), p.getLookAngle(), ExplosionConfig.PUNCH_REACH, 0.5, null);
		ExplosionBlast.palm(p.serverLevel(), shotHand(p, true), 6);
		int n = ExplosionBlast.blast(p, at, radius(ExplosionConfig.PUNCH_RADIUS, power), ExplosionConfig.PUNCH_DAMAGE * power,
				ExplosionConfig.PUNCH_KNOCKBACK, 0.35, ExplosionBlast.MEDIUM, null);
		Quirks.setAnim(p, Explosion.ANIM_PUNCH);
		Quirks.setCooldown(p, ExplosionConfig.BLAST_PUNCH, ExplosionConfig.PUNCH_COOLDOWN);
		strain(p, ExplosionConfig.PUNCH_STRAIN);
		return n;
	}

	/** A blast from below that launches the target straight up (bosses are only hurt). Returns how many it hit. */
	public static int uppercut(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.UPPERCUT_SWEAT);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 at = ExplosionBlast.rayEnd(p, p.getEyePosition(), p.getLookAngle(), ExplosionConfig.UPPERCUT_REACH, 0.6, hit);
		Vec3 c = hit[0] != null ? hit[0].position().add(0, 0.3, 0) : new Vec3(at.x, p.getY() + 0.3, at.z);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 20, 0.2, 0.1, 0.2, 0.0);
		level.sendParticles(ExplosionBlast.SPARK, c.x, c.y + 1.0, c.z, 16, 0.2, 1.0, 0.2, 0.0);
		int n = ExplosionBlast.blast(p, c, radius(ExplosionConfig.UPPERCUT_RADIUS, power), ExplosionConfig.UPPERCUT_DAMAGE * power,
				0.2, ExplosionConfig.UPPERCUT_LIFT, ExplosionBlast.MEDIUM, null);
		Quirks.setAnim(p, Explosion.ANIM_UPPERCUT);
		Quirks.setCooldown(p, ExplosionConfig.UPPERCUT, ExplosionConfig.UPPERCUT_COOLDOWN);
		strain(p, ExplosionConfig.UPPERCUT_STRAIN);
		return n;
	}

	// ================================================================ X: Stun Grenade / Shift+X: Smoke Blast

	/** A blinding flash: everything around him is blinded, slowed and loses its target. Returns how many it caught. */
	public static int stunGrenade(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.STUN_SWEAT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(1.2));
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0);
		level.sendParticles(ExplosionBlast.FLASH_WHITE, c.x, c.y, c.z, 40, 1.5, 1.0, 1.5, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.35);
		level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.25);
		ExplosionBlast.play(level, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 1.6f);
		ExplosionBlast.play(level, c, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.6f, 1.2f);
		ExplosionBlast.play(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.9f);
		int n = 0;
		double r = ExplosionConfig.STUN_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> ExplosionBlast.isTarget(p, e))) {
			if (e.distanceToSqr(c) > r * r) {
				continue;
			}
			ExplosionBlast.strike(p, e, c, ExplosionConfig.STUN_DAMAGE * power, 0.0, 0.0);
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, ExplosionConfig.STUN_BLIND_TICKS, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ExplosionConfig.STUN_SLOW_TICKS, ExplosionBlast.isBoss(e) ? 0 : 2);
			if (e instanceof Mob m && !ExplosionBlast.isBoss(e)) {
				m.setTarget(null);
				m.getNavigation().stop();
			}
			n++;
		}
		Quirks.setAnim(p, Explosion.ANIM_STUN);
		Quirks.setCooldown(p, ExplosionConfig.STUN_GRENADE, ExplosionConfig.STUN_COOLDOWN);
		strain(p, ExplosionConfig.STUN_STRAIN);
		return n;
	}

	/** A thick smoke screen around him for 8 s: creatures in it are blinded and lose their targets; he is hidden in it. */
	public static void smokeBlast(ServerPlayer p) {
		spend(p, ExplosionConfig.SMOKE_SWEAT);
		Session s = session(p);
		long until = now(p) + ExplosionConfig.SMOKE_TICKS;
		s.smoke = p.position();
		s.smokeUntil = until;
		Quirks.edit(p, n -> {
			n.vals.put(Explosion.V_SMOKE, until);
			n.vecs.put(Explosion.VEC_SMOKE, java.util.List.of(s.smoke.x, s.smoke.y, s.smoke.z));
		});
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.3, 0), 2.0, ExplosionBlast.SMALL);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 80, 2.5, 1.2, 2.5, 0.05);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 25, 2.0, 0.5, 2.0, 0.01);
		ExplosionBlast.play(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.4f, 0.6f);
		Quirks.setAnim(p, Explosion.ANIM_SMOKE);
		Quirks.setCooldown(p, ExplosionConfig.SMOKE_BLAST, ExplosionConfig.SMOKE_COOLDOWN);
		strain(p, ExplosionConfig.SMOKE_STRAIN);
		tickSmoke(p, s, now(p));
	}

	/** True if {@code pos} is inside his smoke right now. */
	public static boolean inSmoke(ServerPlayer p, Vec3 pos) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.smoke == null) {
			return false;
		}
		double r = ExplosionConfig.SMOKE_RADIUS;
		Vec3 d = pos.subtract(s.smoke);
		return d.x * d.x + d.z * d.z <= r * r && d.y > -2.0 && d.y < 4.5;
	}

	private static void tickSmoke(ServerPlayer p, Session s, long now) {
		if (now >= s.smokeUntil || s.smoke == null) {
			s.smoke = null;
			Quirks.edit(p, n -> {
				n.vals.remove(Explosion.V_SMOKE);
				n.vecs.remove(Explosion.VEC_SMOKE);
			});
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = s.smoke;
		double r = ExplosionConfig.SMOKE_RADIUS;
		if (now % 3 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.2, c.z, 18, r * 0.45, 1.0, r * 0.45, 0.01);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.6, c.z, 4, r * 0.4, 0.5, r * 0.4, 0.005);
		}
		if (now % 5 != 0) {
			return;
		}
		if (inSmoke(p, p.position())) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 15, 0, false, false, true));
		}
		AABB box = new AABB(c, c).inflate(r * 2.5, 6.0, r * 2.5);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> ExplosionBlast.isTarget(p, e))) {
			boolean inside = inSmoke(p, e.position());
			if (inside) {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, ExplosionConfig.SMOKE_BLIND_TICKS, 0);
			}
			if (e instanceof Mob m && !ExplosionBlast.isBoss(e) && m.getTarget() != null && (inside || inSmoke(p, m.getTarget().position()))) {
				m.setTarget(null);
			}
		}
	}

	// ================================================================ Z: Blast Rush / Shift+Z: Blast Dash

	/** Hold Z: explosions from the palms carry him through the air (the client flies it; see {@code ExplosionRushClient}). */
	public static void startRush(ServerPlayer p) {
		Session s = session(p);
		if (s.rush) {
			return;
		}
		s.rush = true;
		s.rushStart = now(p);
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			s.grantedMayfly = true;
		}
		soft(p, s);
		Quirks.setVal(p, Explosion.V_RUSH, 1L);
		Quirks.setAnim(p, Explosion.ANIM_RUSH);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.4, 0), 1.5, ExplosionBlast.SMALL);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(p.getLookAngle().scale(0.4)).add(0, 0.65, 0));
		}
	}

	public static void endRush(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.rush) {
			return;
		}
		s.rush = false;
		releaseFloat(p, s);
		soft(p, s);
		Quirks.setVal(p, Explosion.V_RUSH, 0L);
		Quirks.setCooldown(p, ExplosionConfig.BLAST_RUSH, ExplosionConfig.RUSH_COOLDOWN);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.6f);
	}

	private static void releaseFloat(ServerPlayer p, Session s) {
		if (s.grantedMayfly) {
			s.grantedMayfly = false;
			if (!p.getAbilities().instabuild && !p.isSpectator()) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
			}
			p.onUpdateAbilities();
		}
	}

	private static void tickRush(ServerPlayer p, Session s, long now) {
		p.resetFallDistance();
		spend(p, ExplosionConfig.RUSH_SWEAT);
		ServerLevel level = p.serverLevel();
		// the palms, thrown back behind him, blasting
		long age = now - s.rushStart;
		for (boolean right : new boolean[] { true, false }) {
			Vec3 h = palm(p, right, -0.45, 0.65, 0.45);
			level.sendParticles(ParticleTypes.FLAME, h.x, h.y, h.z, 2, 0.06, 0.06, 0.06, 0.03);
			level.sendParticles(ExplosionBlast.SPARK, h.x, h.y, h.z, 2, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.SMOKE, h.x, h.y, h.z, 2, 0.08, 0.08, 0.08, 0.02);
		}
		if (age % ExplosionConfig.RUSH_POP_INTERVAL == 0) {
			boolean right = (age / ExplosionConfig.RUSH_POP_INTERVAL) % 2 == 0;
			Vec3 h = palm(p, right, -0.7, 0.6, 0.5);
			level.sendParticles(ParticleTypes.EXPLOSION, h.x, h.y, h.z, 1, 0, 0, 0, 0);
			ExplosionBlast.play(level, h, SoundEvents.GENERIC_EXPLODE, 0.35f, 1.6f + p.getRandom().nextFloat() * 0.35f);
			ExplosionBlast.play(level, h, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, 1.3f + p.getRandom().nextFloat() * 0.4f);
		}
		strain(p, ExplosionConfig.RUSH_STRAIN);
		if (s.rush && age >= ExplosionConfig.RUSH_MAX_TICKS) {
			endRush(p);
		}
	}

	/** One burst the way he looks (~12 blocks); a small blast where he left. */
	public static void blastDash(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.DASH_SWEAT);
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		Vec3 behind = p.position().add(0, 0.9, 0).subtract(look.scale(0.8));
		ExplosionBlast.blast(p, behind, radius(ExplosionConfig.DASH_RADIUS, power), ExplosionConfig.DASH_DAMAGE * power, 0.8, 0.2,
				ExplosionBlast.SMALL, null, false);
		Vec3 v = AbilityHelpers.ballisticLaunch(look, ExplosionConfig.DASH_DISTANCE, p.onGround());
		AbilityHelpers.launchSelf(p, v);
		soft(p, s);
		Quirks.setAnim(p, Explosion.ANIM_DASH);
		Quirks.setCooldown(p, ExplosionConfig.BLAST_DASH, ExplosionConfig.DASH_COOLDOWN);
		strain(p, ExplosionConfig.DASH_STRAIN);
	}

	// ================================================================ V: Howitzer Impact / Shift+V: Max Shot

	/** Spins into a tornado of blasts, flying at the target, and detonates on it. False (refused) without enough Sweat. */
	public static boolean howitzer(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.HOWITZER_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.HOWITZER_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		if (s.rush) {
			endRush(p);
		}
		s.howitzerPower = ExplosionConfig.powerAt(sweat);
		spend(p, Math.max(ExplosionConfig.HOWITZER_MIN_SWEAT, sweat * ExplosionConfig.HOWITZER_SWEAT_FRACTION));
		LivingEntity lock = aimed(p, ExplosionConfig.HOWITZER_RANGE, 10.0);
		s.howitzerTargetId = lock == null ? -1 : lock.getId();
		s.howitzerTarget = lock != null ? centre(lock) : AbilityHelpers.aimPoint(p, ExplosionConfig.HOWITZER_RANGE);
		s.howitzer = true;
		s.howitzerStart = now(p);
		s.vortexHit.clear();
		soft(p, s);
		Quirks.setVal(p, Explosion.V_HOWITZER, now(p) + ExplosionConfig.HOWITZER_SPIN_TICKS + 4L);
		Quirks.setAnim(p, Explosion.ANIM_HOWITZER);
		Quirks.setCooldown(p, ExplosionConfig.HOWITZER, ExplosionConfig.HOWITZER_COOLDOWN);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.3, 0), 2.0, ExplosionBlast.MEDIUM);
		ExplosionBlast.play(level, p.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
		// off the ground first
		AbilityHelpers.launchSelf(p, new Vec3(0, p.onGround() ? 0.7 : 0.25, 0));
		return true;
	}

	private static void tickHowitzer(ServerPlayer p, Session s, long now) {
		long age = now - s.howitzerStart;
		ServerLevel level = p.serverLevel();
		if (s.howitzerTargetId >= 0 && level.getEntity(s.howitzerTargetId) instanceof LivingEntity t && t.isAlive()) {
			s.howitzerTarget = centre(t);
		}
		Vec3 body = p.position().add(0, 0.9, 0);
		Vec3 to = s.howitzerTarget.subtract(body);
		double dist = to.length();
		if (dist <= ExplosionConfig.HOWITZER_ARRIVE || age >= ExplosionConfig.HOWITZER_SPIN_TICKS) {
			detonateHowitzer(p, s);
			return;
		}
		// the first few ticks lift him; then he corkscrews in
		if (age >= 3) {
			Vec3 v = to.normalize().scale(Math.min(ExplosionConfig.HOWITZER_SPEED, dist * 0.5));
			AbilityHelpers.launchSelf(p, v);
		}
		p.resetFallDistance();
		// the vortex: blasts spiralling round him
		double ang = age * Math.toRadians(ExplosionConfig.HOWITZER_SPIN_DEGREES) * 1.3;
		for (int k = 0; k < 2; k++) {
			double a = ang + k * Math.PI;
			double rr = 1.4 + 0.08 * age;
			Vec3 c = body.add(Math.cos(a) * rr, (k == 0 ? 0.4 : -0.3), Math.sin(a) * rr);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 4, 0.1, 0.1, 0.1, 0.04);
			level.sendParticles(ExplosionBlast.SPARK, c.x, c.y, c.z, 3, 0.15, 0.15, 0.15, 0);
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 2, 0.15, 0.15, 0.15, 0.02);
		}
		if (age % ExplosionConfig.HOWITZER_VORTEX_INTERVAL == 0) {
			if (age % 8 == 0) {
				s.vortexHit.clear();
			}
			double a = ang + Math.PI * 0.5;
			Vec3 c = body.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			ExplosionBlast.play(level, c, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.4f + p.getRandom().nextFloat() * 0.5f);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ExplosionConfig.HOWITZER_VORTEX_RADIUS),
					e -> ExplosionBlast.isTarget(p, e))) {
				if (s.vortexHit.add(e.getId())) {
					ExplosionBlast.strike(p, e, body, ExplosionConfig.HOWITZER_VORTEX_DAMAGE * s.howitzerPower, 0.3, 0.15);
				}
			}
		}
	}

	private static void detonateHowitzer(ServerPlayer p, Session s) {
		s.howitzer = false;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		float power = s.howitzerPower;
		double r = radius(ExplosionConfig.HOWITZER_RADIUS, power);
		ExplosionBlast.blast(p, c, r, ExplosionConfig.HOWITZER_DAMAGE * power, ExplosionConfig.HOWITZER_KNOCKBACK, 0.6, ExplosionBlast.BIG, null);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			Vec3 ring = c.add(Math.cos(a) * r * 0.6, 0.0, Math.sin(a) * r * 0.6);
			level.sendParticles(ParticleTypes.EXPLOSION, ring.x, ring.y, ring.z, 2, 0.4, 0.4, 0.4, 0);
			level.sendParticles(ParticleTypes.FLAME, ring.x, ring.y, ring.z, 8, 0.4, 0.4, 0.4, 0.1);
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.45, 0));
		soft(p, s);
		Quirks.setVal(p, Explosion.V_HOWITZER, 0L);
		strain(p, ExplosionConfig.HOWITZER_STRAIN);
	}

	/** Both palms together: a short charge, then a giant cannon blast down the look. All his Sweat. False without enough. */
	public static boolean maxShot(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.MAX_SHOT_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.MAX_SHOT_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		if (s.rush) {
			endRush(p);
		}
		s.maxShotPower = ExplosionConfig.powerAt(sweat);
		Quirks.setMeter(p, 0f);
		s.maxShot = true;
		s.maxShotAt = now(p) + ExplosionConfig.MAX_SHOT_CHARGE;
		Quirks.setVal(p, Explosion.V_MAX_SHOT, s.maxShotAt);
		Quirks.setAnim(p, Explosion.ANIM_MAX_SHOT);
		Quirks.setCooldown(p, ExplosionConfig.MAX_SHOT, ExplosionConfig.MAX_SHOT_COOLDOWN);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
		return true;
	}

	private static void tickMaxShot(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		Vec3 hands = p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.3, 0);
		if (now < s.maxShotAt) {
			// gathering: sparks pulled into the palms, the air crackling
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.5, 1.0, 0.5));
			level.sendParticles(ExplosionBlast.SPARK, hands.x, hands.y, hands.z, 10, 0.5, 0.5, 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, hands.x, hands.y, hands.z, 3, 0.1, 0.1, 0.1, 0.01);
			level.sendParticles(ParticleTypes.LAVA, hands.x, hands.y, hands.z, 1, 0.1, 0.1, 0.1, 0);
			if ((s.maxShotAt - now) % 4 == 0) {
				ExplosionBlast.play(level, hands, SoundEvents.FIRECHARGE_USE, 0.8f, 0.7f + 0.1f * (ExplosionConfig.MAX_SHOT_CHARGE - (s.maxShotAt - now)) / 4f);
			}
			return;
		}
		fireMaxShot(p, s);
	}

	/** The cannon itself (the charge has run out): blasts every few blocks down the look, each creature hit once. Returns how many it hit. */
	public static int fireMaxShot(ServerPlayer p) {
		return fireMaxShot(p, session(p));
	}

	private static int fireMaxShot(ServerPlayer p, Session s) {
		s.maxShot = false;
		Quirks.setVal(p, Explosion.V_MAX_SHOT, 0L);
		float power = s.maxShotPower <= 0f ? power(p) : s.maxShotPower;
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(look.scale(1.0)).add(0, -0.3, 0);
		Vec3 to = p.getEyePosition().add(look.scale(ExplosionConfig.MAX_SHOT_RANGE));
		var bhr = level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : bhr.getLocation().subtract(look.scale(0.4));
		double len = from.distanceTo(end);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		double r = radius(ExplosionConfig.MAX_SHOT_RADIUS, power);
		float dmg = ExplosionConfig.MAX_SHOT_DAMAGE * power;
		for (double d = ExplosionConfig.MAX_SHOT_STEP * 0.5; d < len; d += ExplosionConfig.MAX_SHOT_STEP) {
			Vec3 c = from.add(look.scale(d));
			n += ExplosionBlast.blast(p, c, r, dmg, ExplosionConfig.MAX_SHOT_KNOCKBACK, 0.4, ExplosionBlast.MEDIUM, hit, false);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 14, 0.5, 0.5, 0.5, 0.05);
		}
		n += ExplosionBlast.blast(p, end, r * 1.2, dmg, ExplosionConfig.MAX_SHOT_KNOCKBACK, 0.5, ExplosionBlast.BIG, hit);
		// the beam itself
		for (int i = 0; i < 3; i++) {
			ExplosionBlast.trail(level, from.add((i - 1) * 0.15, 0, 0), end, 2.0);
		}
		ExplosionBlast.play(level, from, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.6f);
		ExplosionBlast.play(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.5f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(look.scale(-ExplosionConfig.MAX_SHOT_RECOIL)).add(0, 0.15, 0));
		soft(p, s);
		Quirks.setAnim(p, Explosion.ANIM_MAX_SHOT);
		strain(p, ExplosionConfig.MAX_SHOT_STRAIN);
		return n;
	}

	// ================================================================ C: Cluster / Shift+C: Explosive Counter

	/** A ring of small explosions around him, then a wider second ring; each creature is hit once. Returns the first ring's hits. */
	public static int cluster(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.CLUSTER_SWEAT);
		Session s = session(p);
		s.clusterHit.clear();
		s.clusterAt = p.position();
		s.clusterPower = power;
		s.clusterRingAt = now(p) + ExplosionConfig.CLUSTER_RING_DELAY;
		int n = clusterRing(p, s.clusterAt, ExplosionConfig.CLUSTER_INNER, power, s.clusterHit);
		Quirks.setAnim(p, Explosion.ANIM_CLUSTER);
		Quirks.setCooldown(p, ExplosionConfig.CLUSTER, ExplosionConfig.CLUSTER_COOLDOWN);
		strain(p, ExplosionConfig.CLUSTER_STRAIN);
		return n;
	}

	private static int clusterRing(ServerPlayer p, Vec3 at, double ringRadius, float power, Set<Integer> hit) {
		ServerLevel level = p.serverLevel();
		int count = ExplosionConfig.CLUSTER_RING_COUNT;
		double offset = ringRadius > ExplosionConfig.CLUSTER_INNER ? Math.PI / count : 0.0;
		for (int i = 0; i < count; i++) {
			double a = offset + i * Math.PI * 2.0 / count;
			Vec3 c = at.add(Math.cos(a) * ringRadius, 0.8, Math.sin(a) * ringRadius);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 5, 0.2, 0.2, 0.2, 0.05);
			level.sendParticles(ExplosionBlast.SPARK, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0);
		}
		ExplosionBlast.play(level, at, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.3f + p.getRandom().nextFloat() * 0.3f);
		ExplosionBlast.play(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.4f, 1.0f);
		ExplosionBlast.play(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.4f);
		double reach = ringRadius + 1.5;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, 3.0, reach), e -> ExplosionBlast.isTarget(p, e))) {
			double dx = e.getX() - at.x, dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > reach * reach || hit.contains(e.getId())) {
				continue;
			}
			if (ExplosionBlast.strike(p, e, at, ExplosionConfig.CLUSTER_DAMAGE * power, ExplosionConfig.CLUSTER_KNOCKBACK, 0.3)) {
				hit.add(e.getId());
				n++;
			}
		}
		return n;
	}

	/** Opens a 2 s window: the next melee hit on him is blocked and the attacker is blown back. */
	public static void counter(ServerPlayer p) {
		Session s = session(p);
		s.counterPower = power(p);
		spend(p, ExplosionConfig.COUNTER_SWEAT);
		s.counterUntil = now(p) + ExplosionConfig.COUNTER_WINDOW;
		Quirks.setVal(p, Explosion.V_COUNTER, s.counterUntil);
		Quirks.setAnim(p, Explosion.ANIM_COUNTER);
		Quirks.setCooldown(p, ExplosionConfig.COUNTER, ExplosionConfig.COUNTER_COOLDOWN);
		ExplosionBlast.palm(p.serverLevel(), shotHand(p, true), 6);
		ExplosionBlast.palm(p.serverLevel(), shotHand(p, false), 6);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.FLINTANDSTEEL_USE, 1.0f, 0.8f);
	}

	public static boolean counterOpen(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.counterUntil > now(p);
	}

	/** A melee hit on him with the window open: blast the attacker, block the hit. True if it fired. */
	private static boolean tryCounter(ServerPlayer p, DamageSource source) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.counterUntil <= now(p)) {
			return false;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p || source.getDirectEntity() != attacker
				|| attacker.distanceToSqr(p) > ExplosionConfig.COUNTER_REACH * ExplosionConfig.COUNTER_REACH
				|| !ExplosionBlast.isTarget(p, attacker)) {
			return false;
		}
		s.counterUntil = 0L;
		Quirks.setVal(p, Explosion.V_COUNTER, 0L);
		Vec3 c = centre(attacker);
		ExplosionBlast.blast(p, c, ExplosionConfig.COUNTER_RADIUS, ExplosionConfig.COUNTER_DAMAGE * s.counterPower,
				ExplosionConfig.COUNTER_KNOCKBACK, 0.35, ExplosionBlast.MEDIUM, null, false);
		Quirks.setAnim(p, Explosion.ANIM_COUNTER_HIT);
		strain(p, ExplosionConfig.COUNTER_STRAIN);
		return true;
	}

	// ================================================================ incoming damage

	/**
	 * Damage he is about to take: true if it is cancelled -- fall / wall damage after his own launches, his own blasts
	 * (never; belt and braces), or a melee hit the Explosive Counter answers.
	 */
	public static boolean cancelsDamage(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			if (softLanding(p)) {
				p.resetFallDistance();
				return true;
			}
			return false;
		}
		if (source.getEntity() == p && source.is(DamageTypeTags.IS_EXPLOSION)) {
			return true;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return tryCounter(p, source);
	}

	/** True if this player holds Explosion (server). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.EXPLOSION);
	}
}
