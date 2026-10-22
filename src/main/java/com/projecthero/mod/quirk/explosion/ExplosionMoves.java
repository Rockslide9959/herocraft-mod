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
import com.projecthero.mod.quirk.data.QuirkState;

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
 * v0.16.7 (revamp): Bakugo's twelve moves, his Sweat meter and the Arm Strain, plus the per-player sessions of the moves
 * that run over time. User rule: X is movement, V is utility, everything else attacks.
 * <pre>
 *   X (hold) Explosion Propulsion   Shift+X Explosion Dash        (movement)
 *   V        Stun Grenade           Shift+V Cool Off              (utility)
 *   R        AP Shot                Shift+R AP Shot: Auto-Cannon
 *   G        Explosive Punch        Shift+G Blast Rush (a chain of blasts down a line)
 *   Z        Cluster                Shift+Z Howitzer Impact: Cluster
 *   C        Howitzer Impact        Shift+C Gauntlet Grenade
 * </pre>
 * Every public move is called by {@link ExplosionKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <p><b>Sweat</b> ({@link Quirks#meter}, 0..100) builds while he moves and fights, faster in hot biomes, the Nether or on
 * fire, slower in the cold, and dries off when he stands still (washes off in water / rain). Every move spends some;
 * every blast is {@link ExplosionConfig#powerAt} times stronger (0.6x dry .. 1.4x soaked). Propulsion burns it while he
 * flies; Howitzer Impact and the Gauntlet Grenade need a real sweat. <b>Arm Strain</b> (0..100; the arms smoke above
 * 70) builds with every blast and eases off when he stops; full strain is {@link Quirks#overload} for
 * {@link ExplosionConfig#STRAIN_TICKS} with Weakness II, like the anime's arm damage. Cool Off dries the sweat and eases
 * the strain.
 *
 * <p>His explosions never touch him: he is excluded from every blast, and fall / wall damage after his own launches is
 * cancelled ({@link #cancelsDamage}). Blocks only break through {@link ExplosionBlast#grief} (the terrain-damage config and
 * mobGriefing).
 */
public final class ExplosionMoves {
	static final class Session {
		boolean legacyChecked;
		Vec3 lastPos;
		long combatUntil;
		long lastDealtSweat = -1000L;
		long stillSince;
		float strain;
		long lastBlast = -1000L;
		int strainSynced;
		// Explosion Propulsion
		boolean propel;
		long propelStart;
		/** We set the SERVER copy of mayfly (never synced) so a long flight is not kicked as flying; restored when it ends. */
		boolean grantedMayfly;
		// the Auto-Cannon
		int autoLeft;
		long autoNext;
		boolean autoLeftHand;
		// Blast Rush
		long blastRushAt = -1L;
		int blastRushDone;
		Vec3 blastRushFrom = Vec3.ZERO;
		Vec3 blastRushDir = Vec3.ZERO;
		float blastRushPower;
		final Set<Integer> blastRushHit = new HashSet<>();
		// Howitzer Impact (kind 1) / its Cluster (kind 2)
		int howitzer;
		long howitzerStart;
		long howitzerDive;
		Vec3 howitzerTarget = Vec3.ZERO;
		int howitzerTargetId = -1;
		float howitzerPower;
		final Set<Integer> vortexHit = new HashSet<>();
		// the Gauntlet Grenade
		boolean gauntlet;
		long gauntletAt;
		float gauntletPower;
		// the Cluster's second ring
		long clusterRingAt = -1L;
		float clusterPower;
		Vec3 clusterAt = Vec3.ZERO;
		final Set<Integer> clusterHit = new HashSet<>();
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
		if (!s.legacyChecked) {
			s.legacyChecked = true;
			QuirkState st = Quirks.state(p);
			boolean legacy = st.vecs.containsKey(Explosion.LEGACY_VEC_SMOKE);
			for (String k : Explosion.LEGACY_VALS) {
				legacy |= st.vals.containsKey(k);
			}
			if (legacy) {
				Quirks.edit(p, n -> {
					for (String k : Explosion.LEGACY_VALS) {
						n.vals.remove(k);
					}
					n.vecs.remove(Explosion.LEGACY_VEC_SMOKE);
				});
			}
		}
		// ---- Sweat
		Vec3 pos = p.position();
		double moved = s.lastPos == null ? 0.0 : Math.hypot(pos.x - s.lastPos.x, pos.z - s.lastPos.z);
		s.lastPos = pos;
		boolean fighting = s.combatUntil > now;
		float gain = ExplosionConfig.SWEAT_NATURAL; // v0.16.7 (user): it charges on its own
		if (moved > 0.08 || s.propel) {
			gain += p.isSprinting() || s.propel ? ExplosionConfig.SWEAT_SPRINT : ExplosionConfig.SWEAT_WALK;
			s.stillSince = now;
		}
		if (fighting) {
			gain += ExplosionConfig.SWEAT_COMBAT;
			s.stillSince = now;
		}
		if (p.isInWaterOrRain()) {
			Quirks.addMeter(p, -ExplosionConfig.SWEAT_WASH);
		} else if (gain > 0f) {
			// the natural 4/s is steady; only what moving / fighting adds scales with the heat
			Quirks.addMeter(p, ExplosionConfig.SWEAT_NATURAL + (gain - ExplosionConfig.SWEAT_NATURAL) * heat(p));
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
			if (p.onGround() && now - s.softSince > 3 && !s.propel && s.howitzer == 0) {
				s.softUntil = 0L;
			}
		}
		// ---- running moves
		if (s.propel) {
			tickPropel(p, s, now);
		}
		if (s.autoLeft > 0 && now >= s.autoNext) {
			s.autoNext = now + ExplosionConfig.AUTO_INTERVAL;
			s.autoLeft--;
			autoShot(p, s);
			if (s.autoLeft <= 0) {
				Quirks.setVal(p, Explosion.V_AUTO, 0L);
			}
		}
		if (s.blastRushAt >= 0L) {
			tickBlastRush(p, s, now);
		}
		if (s.howitzer != 0) {
			tickHowitzer(p, s, now);
		}
		if (s.gauntlet) {
			tickGauntlet(p, s, now);
		}
		if (s.clusterRingAt >= 0L && now >= s.clusterRingAt) {
			s.clusterRingAt = -1L;
			clusterRing(p, s.clusterAt, ExplosionConfig.CLUSTER_OUTER, s.clusterPower, s.clusterHit);
		}
		if (Explosion.val(p, Explosion.V_COOL) > now && now % 2 == 0) {
			for (boolean right : new boolean[] { true, false }) {
				Vec3 h = palm(p, right, 0.1, 0.8, 0.42);
				level.sendParticles(ParticleTypes.WHITE_SMOKE, h.x, h.y, h.z, 2, 0.08, 0.15, 0.08, 0.02);
				level.sendParticles(ParticleTypes.CLOUD, h.x, h.y + 0.1, h.z, 1, 0.05, 0.1, 0.05, 0.02);
			}
		}
		// a soaked Bakugo's palms crackle
		if (Quirks.meter(p) >= 60f && now % 12 == 0 && !s.propel) {
			ExplosionBlast.palm(level, palm(p, p.getRandom().nextBoolean(), 0.15, 0.75, 0.42), 2);
		}
	}

	/** Ends every running move (Arm Strain, Erased, death, revoke). The soft landing stays so he can't die of it. */
	public static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		boolean any = s.propel || s.autoLeft > 0 || s.blastRushAt >= 0L || s.howitzer != 0 || s.gauntlet;
		if (s.propel || s.howitzer != 0) {
			soft(p, s);
		}
		s.propel = false;
		releaseFloat(p, s);
		s.autoLeft = 0;
		s.blastRushAt = -1L;
		s.howitzer = 0;
		s.gauntlet = false;
		s.clusterRingAt = -1L;
		if (any) {
			Quirks.edit(p, n -> {
				n.vals.remove(Explosion.V_PROPEL);
				n.vals.remove(Explosion.V_AUTO);
				n.vals.remove(Explosion.V_BLAST_RUSH);
				n.vals.remove(Explosion.V_HOWITZER);
				n.vals.remove(Explosion.V_HOWITZER_KIND);
				n.vals.remove(Explosion.V_HOWITZER_DIVE);
				n.vals.remove(Explosion.V_GAUNTLET);
			});
		}
	}

	/** True while a big move (the Howitzer spin, the Gauntlet's pin pulled) holds every other key. */
	public static boolean busy(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && (s.howitzer != 0 || s.gauntlet);
	}

	public static boolean propelling(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.propel;
	}

	/** No fall / wall damage until he lands after his own launch. */
	public static boolean softLanding(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && (s.propel || s.howitzer != 0 || s.softUntil > now(p));
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


	// ================================================================ G: Explosive Punch / Shift+G: Blast Rush

	/** A point-blank explosion from the fist as he lunges into it. Returns how many it hit. */
	public static int blastPunch(ServerPlayer p) {
		float power = power(p);
		spend(p, ExplosionConfig.PUNCH_SWEAT);
		Vec3 look = p.getLookAngle();
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(new Vec3(look.x, 0, look.z).scale(ExplosionConfig.PUNCH_LUNGE)));
		Vec3 at = ExplosionBlast.rayEnd(p, p.getEyePosition(), look, ExplosionConfig.PUNCH_REACH, 0.5, null);
		ExplosionBlast.palm(p.serverLevel(), shotHand(p, true), 6);
		int n = ExplosionBlast.blast(p, at, radius(ExplosionConfig.PUNCH_RADIUS, power), ExplosionConfig.PUNCH_DAMAGE * power,
				ExplosionConfig.PUNCH_KNOCKBACK, 0.35, ExplosionBlast.MEDIUM, null);
		Quirks.setAnim(p, Explosion.ANIM_PUNCH);
		Quirks.setCooldown(p, ExplosionConfig.PUNCH, ExplosionConfig.PUNCH_COOLDOWN);
		strain(p, ExplosionConfig.PUNCH_STRAIN);
		return n;
	}

	/** Both palms slammed down: a chain of blasts marches away from him along the ground, the way he faces. */
	public static void blastRush(ServerPlayer p) {
		Session s = session(p);
		s.blastRushPower = power(p);
		spend(p, ExplosionConfig.BLAST_RUSH_SWEAT);
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		s.blastRushDir = f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
		s.blastRushFrom = p.position().add(0, 0.5, 0);
		s.blastRushAt = now(p);
		s.blastRushDone = 0;
		s.blastRushHit.clear();
		Quirks.setVal(p, Explosion.V_BLAST_RUSH, now(p) + (long) ExplosionConfig.BLAST_RUSH_COUNT * ExplosionConfig.BLAST_RUSH_INTERVAL + 2);
		Quirks.setAnim(p, Explosion.ANIM_BLAST_RUSH);
		Quirks.setCooldown(p, ExplosionConfig.BLAST_RUSH, ExplosionConfig.BLAST_RUSH_COOLDOWN);
		strain(p, ExplosionConfig.BLAST_RUSH_STRAIN);
		tickBlastRush(p, s, now(p));
	}

	private static void tickBlastRush(ServerPlayer p, Session s, long now) {
		long k = now - s.blastRushAt;
		if (k % ExplosionConfig.BLAST_RUSH_INTERVAL != 0) {
			return;
		}
		double d = ExplosionConfig.BLAST_RUSH_START + s.blastRushDone * ExplosionConfig.BLAST_RUSH_STEP;
		Vec3 c = s.blastRushFrom.add(s.blastRushDir.scale(d));
		// follow the ground: up a step or down a drop
		ServerLevel level = p.serverLevel();
		net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(c);
		if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
			c = c.add(0, 1.0, 0);
		} else if (level.getBlockState(bp.below()).getCollisionShape(level, bp.below()).isEmpty()) {
			c = c.add(0, -1.0, 0);
		}
		s.blastRushFrom = s.blastRushFrom.add(0, c.y - (s.blastRushFrom.y + 0.0), 0);
		ExplosionBlast.blast(p, c, radius(ExplosionConfig.BLAST_RUSH_RADIUS, s.blastRushPower), ExplosionConfig.BLAST_RUSH_DAMAGE * s.blastRushPower,
				ExplosionConfig.BLAST_RUSH_KNOCKBACK, 0.45, ExplosionBlast.MEDIUM, s.blastRushHit, false);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 3, 0.3, 0.1, 0.3, 0);
		s.blastRushDone++;
		if (s.blastRushDone >= ExplosionConfig.BLAST_RUSH_COUNT) {
			s.blastRushAt = -1L;
			Quirks.setVal(p, Explosion.V_BLAST_RUSH, 0L);
		}
	}

	/** Test hook: everything the running (or last) Blast Rush has hit. */
	public static Set<Integer> blastRushHit(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Set.of() : Set.copyOf(s.blastRushHit);
	}

	// ================================================================ V: Stun Grenade / Shift+V: Cool Off

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

	/** Dries the sweat off his palms: less Sweat, much less Arm Strain (works while strained). */
	public static void coolOff(ServerPlayer p) {
		Session s = session(p);
		Quirks.addMeter(p, -ExplosionConfig.COOL_SWEAT);
		s.strain = Math.max(0f, s.strain - ExplosionConfig.COOL_STRAIN);
		syncStrain(p, s, true);
		Quirks.setVal(p, Explosion.V_COOL, now(p) + ExplosionConfig.COOL_TICKS);
		Quirks.setAnim(p, Explosion.ANIM_COOL);
		Quirks.setCooldown(p, ExplosionConfig.COOL_OFF, ExplosionConfig.COOL_COOLDOWN);
		ServerLevel level = p.serverLevel();
		for (boolean right : new boolean[] { true, false }) {
			Vec3 h = palm(p, right, 0.2, 0.9, 0.42);
			level.sendParticles(ParticleTypes.CLOUD, h.x, h.y, h.z, 6, 0.1, 0.15, 0.1, 0.03);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, h.x, h.y, h.z, 8, 0.12, 0.2, 0.12, 0.02);
		}
		ExplosionBlast.play(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.1f);
		ExplosionBlast.play(level, p.position(), SoundEvents.BUCKET_EMPTY, 0.6f, 1.3f);
	}

	// ================================================================ X (hold): Explosion Propulsion / Shift+X: Explosion Dash

	/** Hold X: palm blasts carry him through the air (the client flies it; see {@code ExplosionPropulsionClient}). False when dry. */
	public static boolean startPropel(ServerPlayer p) {
		Session s = session(p);
		if (s.propel) {
			return true;
		}
		if (Quirks.meter(p) < ExplosionConfig.PROPEL_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.dry", ChatFormatting.GOLD);
			return false;
		}
		s.propel = true;
		s.propelStart = now(p);
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			s.grantedMayfly = true;
		}
		soft(p, s);
		Quirks.setVal(p, Explosion.V_PROPEL, 1L);
		Quirks.setAnim(p, Explosion.ANIM_PROPEL);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.4, 0), 1.5, ExplosionBlast.SMALL);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(p.getLookAngle().scale(0.4)).add(0, 0.65, 0));
		}
		return true;
	}

	public static void endPropel(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.propel) {
			return;
		}
		s.propel = false;
		releaseFloat(p, s);
		soft(p, s);
		Quirks.setVal(p, Explosion.V_PROPEL, 0L);
		Quirks.setCooldown(p, ExplosionConfig.PROPULSION, ExplosionConfig.PROPEL_COOLDOWN);
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

	private static void tickPropel(ServerPlayer p, Session s, long now) {
		p.resetFallDistance();
		spend(p, ExplosionConfig.PROPEL_SWEAT);
		ServerLevel level = p.serverLevel();
		// the palms, thrown back behind him, blasting
		long age = now - s.propelStart;
		for (boolean right : new boolean[] { true, false }) {
			Vec3 h = palm(p, right, -0.45, 0.65, 0.45);
			level.sendParticles(ParticleTypes.FLAME, h.x, h.y, h.z, 2, 0.06, 0.06, 0.06, 0.03);
			level.sendParticles(ExplosionBlast.SPARK, h.x, h.y, h.z, 2, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.SMOKE, h.x, h.y, h.z, 2, 0.08, 0.08, 0.08, 0.02);
		}
		if (age % ExplosionConfig.PROPEL_POP_INTERVAL == 0) {
			boolean right = (age / ExplosionConfig.PROPEL_POP_INTERVAL) % 2 == 0;
			Vec3 h = palm(p, right, -0.7, 0.6, 0.5);
			level.sendParticles(ParticleTypes.EXPLOSION, h.x, h.y, h.z, 1, 0, 0, 0, 0);
			ExplosionBlast.play(level, h, SoundEvents.GENERIC_EXPLODE, 0.35f, 1.6f + p.getRandom().nextFloat() * 0.35f);
			ExplosionBlast.play(level, h, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, 1.3f + p.getRandom().nextFloat() * 0.4f);
		}
		strain(p, ExplosionConfig.PROPEL_STRAIN);
		if (s.propel && Quirks.meter(p) <= 0.01f) {
			// out of sweat: the blasts sputter out
			Quirks.say(p, "message.projecthero.explosion.dry", ChatFormatting.GOLD);
			level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
			endPropel(p);
		} else if (s.propel && age >= ExplosionConfig.PROPEL_MAX_TICKS) {
			endPropel(p);
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
		Quirks.setCooldown(p, ExplosionConfig.DASH, ExplosionConfig.DASH_COOLDOWN);
		strain(p, ExplosionConfig.DASH_STRAIN);
	}

	// ================================================================ Z: Cluster / Shift+Z: Howitzer Impact: Cluster

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

	/** Howitzer Impact: Cluster -- no rise: a quick spinning dive straight at what he aims at, a smaller blast. False without the Sweat. */
	public static boolean howitzerCluster(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.HCLUSTER_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.HCLUSTER_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		startHowitzer(p, s, 2, ExplosionConfig.powerAt(sweat), ExplosionConfig.HCLUSTER_RANGE, 0);
		spend(p, ExplosionConfig.HCLUSTER_SWEAT);
		Quirks.setAnim(p, Explosion.ANIM_HOWITZER_CLUSTER);
		Quirks.setCooldown(p, ExplosionConfig.HOWITZER_CLUSTER, ExplosionConfig.HCLUSTER_COOLDOWN);
		AbilityHelpers.launchSelf(p, new Vec3(0, p.onGround() ? 0.45 : 0.15, 0));
		return true;
	}

	// ================================================================ C: Howitzer Impact / Shift+C: Gauntlet Grenade

	/**
	 * Bakugo's ultimate: he blasts himself up spinning (from the ground), then corkscrews down at the target in a tornado of
	 * blasts and detonates on it. False (refused) without enough Sweat.
	 */
	public static boolean howitzer(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.HOWITZER_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.HOWITZER_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		int rise = p.onGround() ? ExplosionConfig.HOWITZER_RISE_TICKS : 3;
		startHowitzer(p, s, 1, ExplosionConfig.powerAt(sweat), ExplosionConfig.HOWITZER_RANGE, rise);
		spend(p, Math.max(ExplosionConfig.HOWITZER_MIN_SWEAT, sweat * ExplosionConfig.HOWITZER_SWEAT_FRACTION));
		Quirks.setAnim(p, Explosion.ANIM_HOWITZER);
		Quirks.setCooldown(p, ExplosionConfig.HOWITZER, ExplosionConfig.HOWITZER_COOLDOWN);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.3, 0), 2.0, ExplosionBlast.MEDIUM);
		ExplosionBlast.play(level, p.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
		return true;
	}

	private static void startHowitzer(ServerPlayer p, Session s, int kind, float power, double range, int rise) {
		if (s.propel) {
			endPropel(p);
		}
		s.howitzerPower = power;
		LivingEntity lock = aimed(p, range, 10.0);
		s.howitzerTargetId = lock == null ? -1 : lock.getId();
		s.howitzerTarget = lock != null ? centre(lock) : AbilityHelpers.aimPoint(p, range);
		s.howitzer = kind;
		s.howitzerStart = now(p);
		s.howitzerDive = now(p) + rise;
		s.vortexHit.clear();
		soft(p, s);
		int total = kind == 1 ? rise + ExplosionConfig.HOWITZER_SPIN_TICKS : ExplosionConfig.HCLUSTER_SPIN_TICKS;
		long until = now(p) + total + 4L;
		long dive = s.howitzerDive;
		Quirks.edit(p, n -> {
			n.vals.put(Explosion.V_HOWITZER, until);
			n.vals.put(Explosion.V_HOWITZER_KIND, (long) kind);
			n.vals.put(Explosion.V_HOWITZER_DIVE, dive);
		});
	}

	private static void tickHowitzer(ServerPlayer p, Session s, long now) {
		long age = now - s.howitzerStart;
		ServerLevel level = p.serverLevel();
		boolean full = s.howitzer == 1;
		int total = full ? (int) (s.howitzerDive - s.howitzerStart) + ExplosionConfig.HOWITZER_SPIN_TICKS : ExplosionConfig.HCLUSTER_SPIN_TICKS;
		Vec3 body = p.position().add(0, 0.9, 0);
		p.resetFallDistance();
		if (now < s.howitzerDive) {
			// the rise: blasting himself straight up, a column of fire under him, spinning up
			long k = now - s.howitzerStart;
			double up = ExplosionConfig.HOWITZER_RISE_SPEED * (1.0 - 0.5 * k / Math.max(1.0, s.howitzerDive - s.howitzerStart));
			AbilityHelpers.launchSelf(p, new Vec3(0, up, 0));
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.2, p.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() - 0.4, p.getZ(), 1, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() - 0.6, p.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
			if (k % 2 == 0) {
				ExplosionBlast.play(level, p.position(), SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f + p.getRandom().nextFloat() * 0.3f);
			}
			return;
		}
		if (s.howitzerTargetId >= 0 && level.getEntity(s.howitzerTargetId) instanceof LivingEntity t && t.isAlive()) {
			s.howitzerTarget = centre(t);
		}
		Vec3 to = s.howitzerTarget.subtract(body);
		double dist = to.length();
		boolean landed = p.onGround() && now - s.howitzerDive > 2;
		if (dist <= ExplosionConfig.HOWITZER_ARRIVE || landed || age >= total) {
			detonateHowitzer(p, s);
			return;
		}
		double speed = full ? ExplosionConfig.HOWITZER_SPEED : ExplosionConfig.HCLUSTER_SPEED;
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(speed, dist * 0.5)));
		// the vortex: blasts spiralling round him as he corkscrews in
		double ang = age * Math.toRadians(ExplosionConfig.HOWITZER_SPIN_DEGREES) * 1.3;
		for (int k = 0; k < 2; k++) {
			double a = ang + k * Math.PI;
			double rr = 1.2 + 0.05 * Math.min(age, 24);
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
		boolean full = s.howitzer == 1;
		s.howitzer = 0;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		float power = s.howitzerPower;
		double r = radius(full ? ExplosionConfig.HOWITZER_RADIUS : ExplosionConfig.HCLUSTER_RADIUS, power);
		float dmg = (full ? ExplosionConfig.HOWITZER_DAMAGE : ExplosionConfig.HCLUSTER_DAMAGE) * power;
		ExplosionBlast.blast(p, c, r, dmg, ExplosionConfig.HOWITZER_KNOCKBACK * (full ? 1.0 : 0.7), 0.6, full ? ExplosionBlast.BIG : ExplosionBlast.MEDIUM, null);
		int ring = full ? 8 : 5;
		for (int i = 0; i < ring; i++) {
			double a = i * Math.PI * 2.0 / ring;
			Vec3 at = c.add(Math.cos(a) * r * 0.6, 0.0, Math.sin(a) * r * 0.6);
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.1);
		}
		if (full) {
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.45, 0));
		soft(p, s);
		Quirks.edit(p, n -> {
			n.vals.remove(Explosion.V_HOWITZER);
			n.vals.remove(Explosion.V_HOWITZER_KIND);
			n.vals.remove(Explosion.V_HOWITZER_DIVE);
		});
		strain(p, full ? ExplosionConfig.HOWITZER_STRAIN : ExplosionConfig.HCLUSTER_STRAIN);
	}

	/** Test hook: the Howitzer running now (0 none, 1 Howitzer Impact, 2 its Cluster). */
	public static int howitzerKind(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.howitzer;
	}

	/** The grenade pin pulled: a short charge, then every drop of stored sweat in one cannon blast down the aim. False without enough. */
	public static boolean gauntlet(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.GAUNTLET_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.GAUNTLET_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		if (s.propel) {
			endPropel(p);
		}
		s.gauntletPower = ExplosionConfig.powerAt(sweat);
		Quirks.setMeter(p, 0f);
		s.gauntlet = true;
		s.gauntletAt = now(p) + ExplosionConfig.GAUNTLET_CHARGE;
		Quirks.setVal(p, Explosion.V_GAUNTLET, s.gauntletAt);
		Quirks.setAnim(p, Explosion.ANIM_GAUNTLET);
		Quirks.setCooldown(p, ExplosionConfig.GAUNTLET, ExplosionConfig.GAUNTLET_COOLDOWN);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.CHAIN_BREAK, 1.0f, 1.4f);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
		return true;
	}

	private static void tickGauntlet(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		Vec3 hand = shotHand(p, true);
		if (now < s.gauntletAt) {
			// the gauntlet fills: sparks pulled into the grenade, the air crackling
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.5, 1.0, 0.5));
			level.sendParticles(ExplosionBlast.SPARK, hand.x, hand.y, hand.z, 10, 0.5, 0.5, 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 3, 0.1, 0.1, 0.1, 0.01);
			level.sendParticles(ParticleTypes.LAVA, hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0);
			if ((s.gauntletAt - now) % 4 == 0) {
				ExplosionBlast.play(level, hand, SoundEvents.FIRECHARGE_USE, 0.8f,
						0.7f + 0.1f * (ExplosionConfig.GAUNTLET_CHARGE - (s.gauntletAt - now)) / 4f);
			}
			return;
		}
		fireGauntlet(p, s);
	}

	/** The cannon itself (the charge has run out): blasts every few blocks down the aim, each creature hit once. Returns how many it hit. */
	public static int fireGauntlet(ServerPlayer p) {
		return fireGauntlet(p, session(p));
	}

	private static int fireGauntlet(ServerPlayer p, Session s) {
		s.gauntlet = false;
		Quirks.setVal(p, Explosion.V_GAUNTLET, 0L);
		float power = s.gauntletPower <= 0f ? power(p) : s.gauntletPower;
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		Vec3 from = shotHand(p, true).add(look.scale(0.4));
		Vec3 to = p.getEyePosition().add(look.scale(ExplosionConfig.GAUNTLET_RANGE));
		var bhr = level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : bhr.getLocation().subtract(look.scale(0.4));
		double len = from.distanceTo(end);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		double r = radius(ExplosionConfig.GAUNTLET_RADIUS, power);
		float dmg = ExplosionConfig.GAUNTLET_DAMAGE * power;
		for (double d = ExplosionConfig.GAUNTLET_STEP * 0.5; d < len; d += ExplosionConfig.GAUNTLET_STEP) {
			Vec3 c = from.add(look.scale(d));
			n += ExplosionBlast.blast(p, c, r, dmg, ExplosionConfig.GAUNTLET_KNOCKBACK, 0.4, ExplosionBlast.MEDIUM, hit, false);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 14, 0.5, 0.5, 0.5, 0.05);
		}
		n += ExplosionBlast.blast(p, end, r * 1.2, dmg, ExplosionConfig.GAUNTLET_KNOCKBACK, 0.5, ExplosionBlast.BIG, hit);
		for (int i = 0; i < 3; i++) {
			ExplosionBlast.trail(level, from.add((i - 1) * 0.15, 0, 0), end, 2.0);
		}
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		ExplosionBlast.play(level, from, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.6f);
		ExplosionBlast.play(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.5f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(look.scale(-ExplosionConfig.GAUNTLET_RECOIL)).add(0, 0.2, 0));
		soft(p, s);
		strain(p, ExplosionConfig.GAUNTLET_STRAIN);
		return n;
	}

	// ================================================================ incoming damage

	/** Damage he is about to take: true if it is cancelled -- fall / wall damage after his own launches, his own blasts (never). */
	public static boolean cancelsDamage(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			if (softLanding(p)) {
				p.resetFallDistance();
				return true;
			}
			return false;
		}
		return source.getEntity() == p && source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/** True if this player holds Explosion (server). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.EXPLOSION);
	}
}
