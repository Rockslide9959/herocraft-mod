package com.projecthero.mod.quirk.explosion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.QuirkPerks;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (revamp), v0.17.1 (the Quirks revamp, {@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.6): Bakugo's moves, his Sweat
 * meter, the Arm Strain, the passives and the per-player sessions of everything that runs over time. User rule: X is
 * movement, V is utility, everything else attacks; a key with a HOLD move fires its TAP move on the release.
 * <pre>
 *   key  tap                  Shift                    hold
 *   R    AP Shot              AP Shot: Auto-Cannon     AP Shot: Pierce (charge, release fires)
 *   G    Explosive Punch      Blast Rush               Crush Grip
 *   X    Explosion Dash       Spin Dodge               Explosion Propulsion
 *   Z    Cluster              Explosive Uppercut
 *   C    Howitzer Impact      Gauntlet Grenade         Plus Ultra: Howitzer Impact -- Cluster (the framework's hold)
 *   V    Stun Grenade         Cool Off
 * </pre>
 * {@link ExplosionKit#handle} calls the public moves once their cooldown is ready; the gametests call them directly.
 *
 * <p><b>Sweat</b> ({@link Quirks#meter}, 0..100) is fuel and is never paced: it builds while he moves and fights and powers
 * the blasts by band (Dry x0.6, Primed x1.0, Soaked x1.4). <b>Arm Strain</b> is the drawback, paced by
 * {@link ExplosionMeter#BUILD}: from 70 every blast is +15% and costs 0.5 HP; at 100 the arms are shot (6 s, no blasts,
 * Weakness II). The Ground Zero suit's gauntlets bank Sweat for the Gauntlet Grenade.
 *
 * <p>His explosions never touch him; fall / wall damage after his own launches is cancelled ({@link #cancelsDamage}); a long
 * fall with sweat on his palms is a landing blast. Blocks only break through {@link ExplosionBlast#grief} (the
 * terrain-damage config and mobGriefing) or his own blast mining (his own block rights).
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
		// WASD from the client (ExplosionInputPayload)
		int inputForward;
		int inputStrafe;
		// tap / hold keys: the tick a plain press went down (-1: none) and whether its hold move started
		final long[] pressAt = { -1L, -1L, -1L, -1L, -1L, -1L };
		final boolean[] holdStarted = new boolean[6];
		// Explosion Propulsion
		boolean propel;
		long propelStart;
		boolean grantedMayfly;
		// the Auto-Cannon
		int autoLeft;
		long autoNext;
		boolean autoLeftHand;
		// AP Shot: Pierce
		boolean pierce;
		long pierceAt;
		// Blast Rush
		long blastRushAt = -1L;
		int blastRushDone;
		Vec3 blastRushFrom = Vec3.ZERO;
		Vec3 blastRushDir = Vec3.ZERO;
		float blastRushPower;
		final Set<Integer> blastRushHit = new HashSet<>();
		// Crush Grip
		int gripTarget = -1;
		long gripAt;
		int gripBeat;
		boolean gripBoss;
		float gripPower;
		// Spin Dodge
		long dodgeUntil;
		int dodgeSide;
		long iframesUntil;
		// Howitzer Impact (kind 1) / the ultimate (kind 2)
		int howitzer;
		long howitzerStart;
		long howitzerDive;
		Vec3 howitzerTarget = Vec3.ZERO;
		int howitzerTargetId = -1;
		float howitzerPower;
		final Set<Integer> vortexHit = new HashSet<>();
		final List<Integer> ultTargets = new ArrayList<>();
		int ultIndex;
		long ultLegStart;
		int ultHits;
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
		long lastLanding = -100L;
		// the gauntlets' bank, blast mining
		float bank;
		int bankSynced;
		boolean fullSet;
		long mineReadyAt;
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

	/**
	 * The blast power right now (from the Sweat he has before the move spends any): the Sweat band's multiplier, x
	 * {@link ExplosionConfig#STRAIN_HOT_BONUS} once the arms are past {@link ExplosionConfig#STRAIN_WARN}.
	 */
	public static float power(ServerPlayer p) {
		float pw = ExplosionConfig.powerAt(Quirks.meter(p));
		return strain(p) >= ExplosionConfig.STRAIN_WARN ? pw * ExplosionConfig.STRAIN_HOT_BONUS : pw;
	}

	/** Bigger blasts with more sweat: the radius grows a little with power. */
	public static double radius(double base, float power) {
		return base * (0.7 + 0.3 * power);
	}

	/** Spends up to {@code amount} Sweat (a dry Bakugo can still blast, just weakly). */
	private static void spend(ServerPlayer p, float amount) {
		Quirks.addMeter(p, -amount);
	}

	/**
	 * One blast move's price: the Sweat, the arms' 0.5 HP while they are past 70 strain (never below 1 HP), then the strain
	 * (paced). Call it after {@link #power} is read.
	 */
	private static void cost(ServerPlayer p, float sweat, float strain) {
		spend(p, sweat);
		if (strain(p) >= ExplosionConfig.STRAIN_WARN && p.getHealth() > ExplosionConfig.STRAIN_HOT_HP + 1f) {
			p.hurt(p.damageSources().magic(), ExplosionConfig.STRAIN_HOT_HP);
		}
		strain(p, strain);
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

	/** A blast strains the arms (paced, {@link ExplosionMeter#BUILD}); full strain shoots them. */
	public static void strain(ServerPlayer p, float amount) {
		Session s = session(p);
		s.lastBlast = now(p);
		s.strain = Math.min(100f, s.strain + com.projecthero.mod.quirk.meter.QuirkMeters.pace(p, amount));
		if (s.strain >= 100f) {
			strainOut(p);
		}
	}

	/** Arms shot: 6 s of Weakness II and no blasts (every running move stops). */
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
			level.sendParticles(QuirkParticles.SMOKE_PUFF, h.x, h.y, h.z, 6, 0.1, 0.2, 0.1, 0.02);
		}
		QuirkCamera.vignette(p, 0xC02010, 0.5f, 30);
		syncStrain(p, s, true);
	}

	private static void syncStrain(ServerPlayer p, Session s, boolean force) {
		int v = Math.round(s.strain);
		if (force || Math.abs(v - s.strainSynced) >= 2 || (v == 0 && s.strainSynced != 0)) {
			s.strainSynced = v;
			Quirks.setVal(p, Explosion.V_STRAIN, v);
		}
	}

	/** Marks him as in a fight (hits either way). A hit he lands sweats at most once every {@link ExplosionConfig#HIT_SWEAT_INTERVAL}. */
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

	// ================================================================ the client's movement keys, tap / hold

	/** From {@link ExplosionInputPayload}: his WASD now. */
	static void input(ServerPlayer p, int forward, int strafe) {
		Session s = session(p);
		s.inputForward = Mth.clamp(forward, -1, 1);
		s.inputStrafe = Mth.clamp(strafe, -1, 1);
	}

	/** Test hook: pretend these keys are held. */
	public static void inputForTest(ServerPlayer p, int forward, int strafe) {
		input(p, forward, strafe);
	}

	/** A plain press of a key that has a hold move: wait to see whether it is a tap or a hold. */
	static void keyDown(ServerPlayer p, int slot) {
		Session s = session(p);
		s.pressAt[slot] = now(p);
		s.holdStarted[slot] = false;
	}

	/** The key came up: true if it was a tap (its hold never started); clears the press either way. */
	static boolean keyUp(ServerPlayer p, int slot) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.pressAt[slot] < 0L) {
			return false;
		}
		boolean tap = !s.holdStarted[slot];
		s.pressAt[slot] = -1L;
		s.holdStarted[slot] = false;
		return tap;
	}

	/** True if this key's press has become a hold now (once): the kit starts the hold move. */
	static boolean holdDue(ServerPlayer p, int slot) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.pressAt[slot] < 0L || s.holdStarted[slot]) {
			return false;
		}
		if (now(p) - s.pressAt[slot] >= ExplosionConfig.HOLD_TICKS) {
			s.holdStarted[slot] = true;
			return true;
		}
		return false;
	}

	/** CLIENT-UNSAFE test read: a plain press is waiting on this key. */
	public static boolean pressPending(ServerPlayer p, int slot) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.pressAt[slot] >= 0L;
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
		float gain = ExplosionConfig.SWEAT_NATURAL;
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
			Quirks.addMeter(p, ExplosionConfig.SWEAT_NATURAL + (gain - ExplosionConfig.SWEAT_NATURAL) * heat(p));
		} else if (now - s.stillSince > ExplosionConfig.DRY_DELAY) {
			Quirks.addMeter(p, -ExplosionConfig.SWEAT_DRY);
		}
		// ---- the Ground Zero gauntlets bank Sweat while he is soaked (full suit)
		if (s.fullSet && Quirks.meter(p) >= ExplosionConfig.SOAKED_FROM && s.bank < ExplosionConfig.BANK_MAX) {
			s.bank = Math.min(ExplosionConfig.BANK_MAX, s.bank + ExplosionConfig.BANK_FILL);
		}
		if (now % 10 == 0 && Math.round(s.bank) != s.bankSynced) {
			s.bankSynced = Math.round(s.bank);
			Quirks.setVal(p, Explosion.V_BANK, s.bankSynced);
		}
		// ---- Arm Strain eases off
		if (s.strain > 0f && now - s.lastBlast > ExplosionConfig.STRAIN_DECAY_DELAY) {
			s.strain = Math.max(0f, s.strain + com.projecthero.mod.quirk.meter.QuirkMeters.pace(p, -ExplosionConfig.STRAIN_DECAY));
		}
		if (now % 10 == 0) {
			syncStrain(p, s, false);
		}
		if (s.strain >= ExplosionConfig.STRAIN_WARN && now % 8 == 0) {
			for (boolean right : new boolean[] { true, false }) {
				Vec3 h = palm(p, right, 0.05, 0.8, 0.42);
				level.sendParticles(QuirkParticles.SMOKE_PUFF, h.x, h.y, h.z, 1, 0.05, 0.1, 0.05, 0.01);
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
		if (s.pierce) {
			tickPierce(p, s, now);
		}
		if (s.blastRushAt >= 0L) {
			tickBlastRush(p, s, now);
		}
		if (s.gripTarget >= 0) {
			tickGrip(p, s, now);
		}
		if (s.dodgeUntil != 0L) {
			tickDodge(p, s, now);
		}
		if (s.howitzer == 1) {
			tickHowitzer(p, s, now);
		} else if (s.howitzer == 2) {
			tickUltimate(p, s, now);
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
				level.sendParticles(QuirkParticles.STEAM, h.x, h.y, h.z, 2, 0.08, 0.15, 0.08, 0.02);
			}
		}
		// v0.17.1 (user): no palm crackle / sound at high sweat any more
	}

	/** The full suit went on / came off: the gauntlets' bank (it empties when the gauntlets come off). */
	static void suitChanged(ServerPlayer p, boolean fullSet) {
		Session s = session(p);
		s.fullSet = fullSet;
		if (!fullSet && s.bank > 0f) {
			s.bank = 0f;
			s.bankSynced = 0;
			Quirks.setVal(p, Explosion.V_BANK, 0L);
		}
	}

	/** Server: the gauntlets' banked Sweat. */
	public static float bank(ServerPlayer p) {
		return session(p).bank;
	}

	/** Test hook: set the bank (and pretend the full suit is on when {@code value} > 0). */
	public static void bankForTest(ServerPlayer p, float value) {
		Session s = session(p);
		s.fullSet = value > 0f;
		s.bank = Mth.clamp(value, 0f, ExplosionConfig.BANK_MAX);
	}

	/** Ends every running move (Arm Strain, Erased, death, revoke). The soft landing stays so he can't die of it. */
	public static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		boolean any = s.propel || s.autoLeft > 0 || s.blastRushAt >= 0L || s.howitzer != 0 || s.gauntlet || s.pierce || s.gripTarget >= 0
				|| s.dodgeUntil != 0L;
		if (s.propel || s.howitzer != 0) {
			soft(p, s);
		}
		s.propel = false;
		releaseFloat(p, s);
		s.autoLeft = 0;
		s.pierce = false;
		s.blastRushAt = -1L;
		s.gripTarget = -1;
		s.dodgeUntil = 0L;
		s.howitzer = 0;
		s.ultTargets.clear();
		s.gauntlet = false;
		s.clusterRingAt = -1L;
		java.util.Arrays.fill(s.pressAt, -1L);
		java.util.Arrays.fill(s.holdStarted, false);
		if (any) {
			Quirks.edit(p, n -> {
				for (String k : new String[] { Explosion.V_PROPEL, Explosion.V_AUTO, Explosion.V_BLAST_RUSH, Explosion.V_HOWITZER, Explosion.V_HOWITZER_KIND,
						Explosion.V_HOWITZER_DIVE, Explosion.V_GAUNTLET, Explosion.V_PIERCE, Explosion.V_GRIP, Explosion.V_GRIP_TARGET, Explosion.V_DODGE,
						Explosion.V_DODGE_SIDE }) {
					n.vals.remove(k);
				}
			});
		}
	}

	/** True while a big move (a Howitzer, the Gauntlet's pin pulled, a grip) holds every other key. */
	public static boolean busy(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && (s.howitzer != 0 || s.gauntlet || s.gripTarget >= 0);
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

	/** His right, flat (look x up). */
	private static Vec3 rightOf(ServerPlayer p) {
		Vec3 f = Vec3.directionFromRotation(0f, p.getYRot());
		return f.cross(new Vec3(0, 1, 0)).normalize();
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
			if (d < 0.5 || d > range + le.getBbWidth() * 0.5) {
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

	// ================================================================ R: AP Shot / Shift+R: Auto-Cannon / hold R: Pierce

	/** A focused blast fired from the palm along the look: the first creature or block it meets explodes. Returns the impact point. */
	public static Vec3 apShot(ServerPlayer p) {
		float power = power(p);
		// v0.17.1: the routes into it (Stun Grenade -> AP Shot is a crit, the Uppercut -> AP Shot an air shot)
		QuirkCombos.Route route = QuirkCombos.used(p, ExplosionConfig.AP_SHOT);
		power *= QuirkCombos.mult(route);
		cost(p, ExplosionConfig.AP_SWEAT, ExplosionConfig.AP_STRAIN);
		ServerLevel level = p.serverLevel();
		Vec3 hand = shotHand(p, true);
		Vec3 end = ExplosionBlast.rayEnd(p, p.getEyePosition(), p.getLookAngle(), ExplosionConfig.AP_RANGE, 0.3, null);
		ExplosionBlast.muzzle(level, hand, p.getLookAngle(), 1f);
		ExplosionBlast.trail(level, hand, end, 3.0);
		QuirkSounds.play(level, hand, ExplosionSounds.BLAST_SMALL, 1.2f, 1.15f);
		ExplosionBlast.blast(p, end, radius(ExplosionConfig.AP_RADIUS, power), ExplosionConfig.AP_DAMAGE * power, ExplosionConfig.AP_KNOCKBACK, 0.25,
				ExplosionBlast.MEDIUM, null);
		// v0.17.1: the compressed shot bores a short tunnel into whatever it hit
		ExplosionTerrain.tunnel(p, end, p.getLookAngle(), 3.0, 0.9, ExplosionTerrain.Tier.POCKET.maxHardness);
		QuirkCamera.preset(p, route != null ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		Quirks.setAnim(p, Explosion.ANIM_AP_SHOT);
		Quirks.setCooldown(p, ExplosionConfig.AP_SHOT, ExplosionConfig.AP_COOLDOWN);
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
		cost(p, ExplosionConfig.AUTO_SWEAT, ExplosionConfig.AUTO_STRAIN);
		s.autoLeftHand = !s.autoLeftHand;
		ServerLevel level = p.serverLevel();
		var rnd = p.getRandom();
		float spread = (float) ExplosionConfig.AUTO_SPREAD_DEGREES;
		Vec3 dir = Vec3.directionFromRotation(p.getXRot() + (rnd.nextFloat() - 0.5f) * 2f * spread, p.getYRot() + (rnd.nextFloat() - 0.5f) * 2f * spread);
		Vec3 hand = shotHand(p, !s.autoLeftHand);
		Vec3 end = ExplosionBlast.rayEnd(p, p.getEyePosition(), dir, ExplosionConfig.AUTO_RANGE, 0.25, null);
		ExplosionBlast.muzzle(level, hand, dir, 0.6f);
		ExplosionBlast.trail(level, hand, end, 1.5);
		ExplosionBlast.blast(p, end, radius(ExplosionConfig.AUTO_RADIUS, power), ExplosionConfig.AUTO_DAMAGE * power, 0.35, 0.1, ExplosionBlast.SMALL, null);
		QuirkCamera.shake(p, 0.08f, 3);
	}

	/** Hold R: the palm compresses a blast (the charge runs up to {@link ExplosionConfig#PIERCE_CHARGE_TICKS}); the release fires it. */
	public static boolean startPierce(ServerPlayer p) {
		Session s = session(p);
		if (s.pierce) {
			return false;
		}
		s.pierce = true;
		s.pierceAt = now(p);
		Quirks.setVal(p, Explosion.V_PIERCE, s.pierceAt);
		Quirks.setAnim(p, Explosion.ANIM_PIERCE_CHARGE);
		QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.CHARGE_UP, 0.8f, 1.35f);
		return true;
	}

	public static boolean piercing(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.pierce;
	}

	private static void tickPierce(ServerPlayer p, Session s, long now) {
		long age = now - s.pierceAt;
		if (age >= ExplosionConfig.PIERCE_CHARGE_TICKS) {
			firePierce(p);
			return;
		}
		// sparks pulled in to the palm
		ServerLevel level = p.serverLevel();
		Vec3 hand = shotHand(p, true);
		float c = age / (float) ExplosionConfig.PIERCE_CHARGE_TICKS;
		for (int i = 0; i < 3; i++) {
			Vec3 d = new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian(), p.getRandom().nextGaussian()).normalize().scale(0.9);
			Vec3 from = hand.add(d);
			level.sendParticles(QuirkParticles.SPARK, from.x, from.y, from.z, 0, -d.x, -d.y, -d.z, 0.22 + 0.2 * c);
		}
		if (age % 5 == 0) {
			ExplosionBlast.palm(level, hand, 2 + (int) (6 * c));
		}
	}

	/** Fires the charged Pierce: a thin beam that goes through every creature to {@link ExplosionConfig#PIERCE_RANGE}. Returns the hits. */
	public static int firePierce(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.pierce) {
			return 0;
		}
		s.pierce = false;
		Quirks.setVal(p, Explosion.V_PIERCE, 0L);
		float charge = Mth.clamp((now(p) - s.pierceAt) / (float) ExplosionConfig.PIERCE_CHARGE_TICKS, 0f, 1f);
		float power = power(p);
		cost(p, ExplosionConfig.PIERCE_SWEAT, ExplosionConfig.PIERCE_STRAIN);
		float dmg = Mth.lerp(charge, ExplosionConfig.PIERCE_MIN_DAMAGE, ExplosionConfig.PIERCE_MAX_DAMAGE) * power;
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		Vec3 hand = shotHand(p, true);
		Vec3 eye = p.getEyePosition();
		var bhr = level.clip(new ClipContext(eye, eye.add(look.scale(ExplosionConfig.PIERCE_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? eye.add(look.scale(ExplosionConfig.PIERCE_RANGE)) : bhr.getLocation();
		int n = 0;
		AABB sweep = new AABB(eye, end).inflate(ExplosionConfig.PIERCE_RADIUS + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> ExplosionBlast.isTarget(p, e))) {
			if (e.getBoundingBox().inflate(ExplosionConfig.PIERCE_RADIUS).clip(eye, end).isEmpty()) {
				continue;
			}
			if (ExplosionBlast.strike(p, e, eye, dmg, 0.6, 0.1)) {
				n++;
				Vec3 c = centre(e);
				QuirkFx.blast(level, c).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.1f).life(10).flags(QuirkFx.F_NO_RING | QuirkFx.F_NO_SMOKE)
						.spawn();
			}
		}
		float width = 0.12f + 0.22f * charge;
		QuirkFx.cone(level, hand, end.subtract(hand)).style(QuirkFx.CONE_BEAM).colours(ExplosionBlast.WHITE, 0xFFA030).size(width)
				.length((float) hand.distanceTo(end)).life(10).spawn();
		QuirkFx.ring(level, hand.add(look.scale(1.3))).flags(QuirkFx.F_VERTICAL).vec(look).colours(ExplosionBlast.WHITE, ExplosionBlast.CORE)
				.size(0.8f + 0.7f * charge).thickness(0.18f).life(9).spawn();
		ExplosionBlast.fx(level, end.subtract(look.scale(0.3)), 1.5 + charge, ExplosionBlast.MEDIUM);
		ExplosionTerrain.tunnel(p, end, look, 3.0 + 4.0 * charge, 0.8 + 0.4 * charge, ExplosionTerrain.Tier.POCKET.maxHardness);
		QuirkSounds.play(level, hand, ExplosionSounds.PIERCE, 1.6f, 1.1f - 0.2f * charge);
		QuirkCamera.preset(p, charge > 0.6f ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(look.scale(-0.35 * charge)));
		Quirks.setAnim(p, Explosion.ANIM_PIERCE);
		Quirks.setCooldown(p, ExplosionConfig.PIERCE, ExplosionConfig.PIERCE_COOLDOWN);
		return n;
	}

	// ================================================================ G: Explosive Punch / Shift+G: Blast Rush / hold G: Crush Grip

	/** A point-blank explosion from the fist as he lunges into it. Returns how many it hit. */
	public static int blastPunch(ServerPlayer p) {
		float power = power(p);
		cost(p, ExplosionConfig.PUNCH_SWEAT, ExplosionConfig.PUNCH_STRAIN);
		Vec3 look = p.getLookAngle();
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(new Vec3(look.x, 0, look.z).scale(ExplosionConfig.PUNCH_LUNGE)));
		Vec3 at = ExplosionBlast.rayEnd(p, p.getEyePosition(), look, ExplosionConfig.PUNCH_REACH, 0.5, null);
		ExplosionBlast.muzzle(p.serverLevel(), shotHand(p, true), look, 1.2f);
		int n = ExplosionBlast.blast(p, at, radius(ExplosionConfig.PUNCH_RADIUS, power), ExplosionConfig.PUNCH_DAMAGE * power,
				ExplosionConfig.PUNCH_KNOCKBACK, 0.35, ExplosionBlast.MEDIUM, null);
		QuirkSounds.play(p.serverLevel(), at, QuirkSounds.IMPACT_HEAVY, 1.2f, 1.1f);
		QuirkCamera.preset(p, n > 0 ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		Quirks.setAnim(p, Explosion.ANIM_PUNCH);
		Quirks.setCooldown(p, ExplosionConfig.PUNCH, ExplosionConfig.PUNCH_COOLDOWN);
		return n;
	}

	/** Both palms slammed down: a chain of blasts marches away from him along the ground, the way he faces. */
	public static void blastRush(ServerPlayer p) {
		Session s = session(p);
		s.blastRushPower = power(p);
		cost(p, ExplosionConfig.BLAST_RUSH_SWEAT, ExplosionConfig.BLAST_RUSH_STRAIN);
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
		QuirkFx.ring(p.serverLevel(), p.position().add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(2.5f).thickness(0.4f).life(10)
				.ground().spawn();
		tickBlastRush(p, s, now(p));
	}

	private static void tickBlastRush(ServerPlayer p, Session s, long now) {
		long k = now - s.blastRushAt;
		if (k % ExplosionConfig.BLAST_RUSH_INTERVAL != 0) {
			return;
		}
		double d = ExplosionConfig.BLAST_RUSH_START + s.blastRushDone * ExplosionConfig.BLAST_RUSH_STEP;
		Vec3 c = s.blastRushFrom.add(s.blastRushDir.scale(d));
		ServerLevel level = p.serverLevel();
		BlockPos bp = BlockPos.containing(c);
		if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
			c = c.add(0, 1.0, 0);
		} else if (level.getBlockState(bp.below()).getCollisionShape(level, bp.below()).isEmpty()) {
			c = c.add(0, -1.0, 0);
		}
		s.blastRushFrom = s.blastRushFrom.add(0, c.y - s.blastRushFrom.y, 0);
		ExplosionBlast.blast(p, c, radius(ExplosionConfig.BLAST_RUSH_RADIUS, s.blastRushPower), ExplosionConfig.BLAST_RUSH_DAMAGE * s.blastRushPower,
				ExplosionConfig.BLAST_RUSH_KNOCKBACK, 0.45, ExplosionBlast.MEDIUM, s.blastRushHit, false);
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

	/**
	 * Hold G: he grabs the face in reach, three detonations in his palm (6 / 6 / 12), then throws it. A boss takes the hits
	 * but is never grabbed or thrown. Nothing in reach: a pop of sparks and a short cooldown, nothing spent. Returns the target.
	 */
	public static @Nullable LivingEntity crushGrip(ServerPlayer p) {
		LivingEntity t = aimed(p, ExplosionConfig.GRIP_REACH, 28.0);
		ServerLevel level = p.serverLevel();
		if (t == null) {
			ExplosionBlast.palm(level, shotHand(p, true), 8);
			QuirkSounds.play(level, p.position(), ExplosionSounds.CRACKLE, 0.6f, 1.2f);
			Quirks.setCooldown(p, ExplosionConfig.CRUSH_GRIP, ExplosionConfig.GRIP_WHIFF_COOLDOWN);
			return null;
		}
		Session s = session(p);
		s.gripPower = power(p);
		cost(p, ExplosionConfig.GRIP_SWEAT, ExplosionConfig.GRIP_STRAIN);
		s.gripTarget = t.getId();
		s.gripAt = now(p);
		s.gripBeat = 0;
		s.gripBoss = ExplosionBlast.isBoss(t);
		long at = s.gripAt;
		int id = t.getId();
		Quirks.edit(p, n -> {
			n.vals.put(Explosion.V_GRIP, at);
			n.vals.put(Explosion.V_GRIP_TARGET, (long) id);
		});
		Quirks.setAnim(p, Explosion.ANIM_GRIP);
		Quirks.setCooldown(p, ExplosionConfig.CRUSH_GRIP, ExplosionConfig.GRIP_COOLDOWN);
		QuirkSounds.play(level, p.position(), QuirkSounds.IMPACT, 1.0f, 0.8f);
		if (!s.gripBoss && t instanceof Mob m) {
			m.getNavigation().stop();
		}
		return t;
	}

	public static boolean gripping(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.gripTarget >= 0;
	}

	private static void endGrip(ServerPlayer p, Session s) {
		s.gripTarget = -1;
		Quirks.edit(p, n -> {
			n.vals.remove(Explosion.V_GRIP);
			n.vals.remove(Explosion.V_GRIP_TARGET);
		});
	}

	private static void tickGrip(ServerPlayer p, Session s, long now) {
		Entity e = p.serverLevel().getEntity(s.gripTarget);
		long age = now - s.gripAt;
		if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceTo(p) > 6.0) {
			endGrip(p, s);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 hold = p.getEyePosition().add(look.scale(1.25 + t.getBbWidth() * 0.5)).add(0, -t.getBbHeight() * 0.55, 0);
		if (!s.gripBoss && age < ExplosionConfig.GRIP_THROW_AT) {
			// held by the face out in front of him
			t.setDeltaMovement(Vec3.ZERO);
			t.teleportTo(hold.x, hold.y, hold.z);
			t.hurtMarked = true;
			t.resetFallDistance();
		}
		ServerLevel level = p.serverLevel();
		if (s.gripBeat < ExplosionConfig.GRIP_BEATS.length && age >= ExplosionConfig.GRIP_BEATS[s.gripBeat]) {
			int beat = s.gripBeat++;
			boolean last = beat == ExplosionConfig.GRIP_BEATS.length - 1;
			Vec3 face = t.getEyePosition();
			ExplosionBlast.strike(p, t, p.getEyePosition(), ExplosionConfig.GRIP_DAMAGE[beat] * s.gripPower, 0.0, 0.0);
			QuirkFx.blast(level, face).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(last ? 2.2f : 1.3f).life(last ? 16 : 11)
					.flags(QuirkFx.F_NO_RING).spawn();
			QuirkSounds.play(level, face, last ? ExplosionSounds.BLAST : ExplosionSounds.BLAST_SMALL, last ? 1.6f : 1.1f, last ? 0.9f : 1.2f);
			level.sendParticles(QuirkParticles.SPARK, face.x, face.y, face.z, last ? 24 : 12, 0.2, 0.2, 0.2, 0.4);
			QuirkCamera.preset(p, last ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		}
		if (age >= ExplosionConfig.GRIP_THROW_AT) {
			if (!s.gripBoss && t.isAlive()) {
				t.setDeltaMovement(look.scale(ExplosionConfig.GRIP_THROW).add(0, 0.45, 0));
				t.hurtMarked = true;
				QuirkFx.trail(level, t).colours(0xFFE0A0, 0x6A5A50).size(0.4f).size2(10f).life(24).vec(new Vec3(0, t.getBbHeight() * 0.5, 0))
						.style(QuirkFx.RIBBON_FLAME).spawn();
			}
			QuirkSounds.play(level, p.position(), QuirkSounds.WHOOSH, 1.0f, 0.8f);
			endGrip(p, s);
		}
	}

	// ================================================================ V: Stun Grenade / Shift+V: Cool Off

	/** A blinding flash: everything around him is blinded, slowed and loses its target. Returns how many it caught. */
	public static int stunGrenade(ServerPlayer p) {
		float power = power(p);
		cost(p, ExplosionConfig.STUN_SWEAT, ExplosionConfig.STUN_STRAIN);
		QuirkCombos.used(p, ExplosionConfig.STUN_GRENADE);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(1.2));
		QuirkFx.blast(level, c).colours(0xFFFFFF, 0xFFF4C8).size(3.2f).life(14).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.ring(level, c.add(p.getLookAngle().scale(0.8))).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xFFF0B0).size(4f).thickness(0.3f).life(10).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xFFE8A0).size((float) ExplosionConfig.STUN_RADIUS).thickness(0.5f).life(14)
				.ground().spawn();
		level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 50, 0.4, 0.4, 0.4, 0.7);
		QuirkSounds.play(level, c, ExplosionSounds.STUN, 2.2f, 1.0f);
		QuirkCamera.vignette(p, 0xFFFFFF, 0.35f, 8);
		int n = 0;
		double r = ExplosionConfig.STUN_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> ExplosionBlast.isTarget(p, e))) {
			if (e.distanceToSqr(c) > r * r) {
				continue;
			}
			boolean boss = ExplosionBlast.isBoss(e);
			ExplosionBlast.strike(p, e, c, ExplosionConfig.STUN_DAMAGE * power, 0.0, 0.0);
			if (!boss) {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, ExplosionConfig.STUN_BLIND_TICKS, 0);
			}
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ExplosionConfig.STUN_SLOW_TICKS, boss ? 0 : 2);
			// bosses keep the shared threat table's target (the flash never resets their aggro)
			if (e instanceof Mob m && !boss) {
				m.setTarget(null);
				m.getNavigation().stop();
			}
			n++;
		}
		Quirks.setAnim(p, Explosion.ANIM_STUN);
		Quirks.setCooldown(p, ExplosionConfig.STUN_GRENADE, ExplosionConfig.STUN_COOLDOWN);
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
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(0xE8F0FF, 0xC8D0DA).size(3f).thickness(0.7f).life(24).ground()
				.spawn();
		for (boolean right : new boolean[] { true, false }) {
			Vec3 h = palm(p, right, 0.2, 0.9, 0.42);
			level.sendParticles(QuirkParticles.STEAM, h.x, h.y, h.z, 8, 0.12, 0.2, 0.12, 0.03);
		}
		ExplosionBlast.play(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.1f);
		ExplosionBlast.play(level, p.position(), SoundEvents.BUCKET_EMPTY, 0.6f, 1.3f);
	}

	// ================================================================ X: Dash / Shift+X: Spin Dodge / hold X: Propulsion

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
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(3f).thickness(0.45f).life(10).ground()
				.spawn();
		propelTrails(p);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(p.getLookAngle().scale(0.4)).add(0, 0.65, 0));
		}
		return true;
	}

	/** The two palm exhausts: flame ribbons trailing from his thrown-back palms (renewed while he flies). */
	private static void propelTrails(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		for (int side : new int[] { 1, -1 }) {
			QuirkFx.trail(level, p).colours(0xFFF0C0, ExplosionBlast.SHELL).size(0.22f).size2(9f).life(44).vec(new Vec3(0.45 * side, 0.7, -0.4))
					.style(QuirkFx.RIBBON_FLAME).spawn();
		}
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
		long age = now - s.propelStart;
		if (age > 0 && age % 40 == 0) {
			propelTrails(p);
		}
		if (age % ExplosionConfig.PROPEL_POP_INTERVAL == 0) {
			boolean right = (age / ExplosionConfig.PROPEL_POP_INTERVAL) % 2 == 0;
			Vec3 h = palm(p, right, -0.7, 0.6, 0.5);
			QuirkFx.flipbook(level, h).style(QuirkFx.BOOK_FIREBALL).size(0.7f).life(8).spawn();
			level.sendParticles(QuirkParticles.SPARK, h.x, h.y, h.z, 3, 0.1, 0.1, 0.1, 0.2);
			if (age % (ExplosionConfig.PROPEL_POP_INTERVAL * 2) == 0) {
				QuirkSounds.play(level, h, ExplosionSounds.BLAST_SMALL, 0.45f, 1.3f + p.getRandom().nextFloat() * 0.35f);
			}
		}
		if (age % 6 == 0) {
			level.sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
		}
		strain(p, ExplosionConfig.PROPEL_STRAIN);
		if (s.propel && Quirks.meter(p) <= 0.01f) {
			Quirks.say(p, "message.projecthero.explosion.dry", ChatFormatting.GOLD);
			level.sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
			endPropel(p);
		} else if (s.propel && age >= ExplosionConfig.PROPEL_MAX_TICKS) {
			endPropel(p);
		}
	}

	/** The way WASD points (the look when nothing is held): W along the full look, S back, A / D sideways. */
	public static Vec3 dashDirection(ServerPlayer p) {
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		if (s.inputForward == 0 && s.inputStrafe == 0) {
			return look;
		}
		Vec3 flat = Vec3.directionFromRotation(0f, p.getYRot());
		Vec3 fwd = s.inputForward > 0 ? look : s.inputForward < 0 ? flat.scale(-1) : Vec3.ZERO;
		// leftImpulse +1 is A (his left)
		Vec3 side = rightOf(p).scale(-s.inputStrafe);
		Vec3 d = fwd.add(side);
		return d.lengthSqr() < 1.0e-4 ? look : d.normalize();
	}

	/** One back-blast toward the WASD direction (~12 blocks); a small blast where he left. */
	public static void blastDash(ServerPlayer p) {
		float power = power(p);
		cost(p, ExplosionConfig.DASH_SWEAT, ExplosionConfig.DASH_STRAIN);
		Session s = session(p);
		Vec3 dir = dashDirection(p);
		Vec3 behind = p.position().add(0, 0.9, 0).subtract(dir.scale(0.8));
		ExplosionBlast.blast(p, behind, radius(ExplosionConfig.DASH_RADIUS, power), ExplosionConfig.DASH_DAMAGE * power, 0.8, 0.2, ExplosionBlast.SMALL, null,
				false);
		Vec3 v = AbilityHelpers.ballisticLaunch(dir, ExplosionConfig.DASH_DISTANCE, p.onGround());
		AbilityHelpers.launchSelf(p, v);
		QuirkFx.trail(p.serverLevel(), p).colours(0xFFF0C0, ExplosionBlast.SHELL).size(0.35f).size2(8f).life(16).vec(new Vec3(0, 0.9, 0))
				.style(QuirkFx.RIBBON_FLAME).spawn();
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		soft(p, s);
		Quirks.setAnim(p, Explosion.ANIM_DASH);
		Quirks.setCooldown(p, ExplosionConfig.DASH, ExplosionConfig.DASH_COOLDOWN);
	}

	/**
	 * Shift+X: a sideways blast spin (A / D picks the side; neither: to his right), about 5 blocks, invulnerable for
	 * {@link ExplosionConfig#DODGE_IFRAMES} ticks, ending turned to face whoever is after him. Returns the side (+1 right).
	 */
	public static int spinDodge(ServerPlayer p) {
		cost(p, ExplosionConfig.DODGE_SWEAT, ExplosionConfig.DODGE_STRAIN);
		Session s = session(p);
		int side = s.inputStrafe > 0 ? -1 : 1;
		Vec3 dir = rightOf(p).scale(side);
		ServerLevel level = p.serverLevel();
		Vec3 push = p.position().add(0, 0.9, 0).subtract(dir.scale(0.7));
		QuirkFx.blast(level, push).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.2f).life(10).flags(QuirkFx.F_NO_RING).spawn();
		QuirkSounds.play(level, push, ExplosionSounds.BLAST_SMALL, 1.0f, 1.3f);
		AbilityHelpers.launchSelf(p, AbilityHelpers.ballisticLaunch(new Vec3(dir.x, 0.05, dir.z), ExplosionConfig.DODGE_DISTANCE, p.onGround()));
		QuirkFx.trail(level, p).colours(0xFFF0C0, ExplosionBlast.SHELL).size(0.3f).size2(7f).life(14).vec(new Vec3(0, 1.0, 0)).style(QuirkFx.RIBBON_FLAME)
				.spawn();
		long now = now(p);
		s.dodgeUntil = now + ExplosionConfig.DODGE_TICKS;
		s.dodgeSide = side;
		s.iframesUntil = now + ExplosionConfig.DODGE_IFRAMES;
		long until = s.dodgeUntil;
		Quirks.edit(p, n -> {
			n.vals.put(Explosion.V_DODGE, until);
			n.vals.put(Explosion.V_DODGE_SIDE, (long) side);
		});
		soft(p, s);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		Quirks.setAnim(p, Explosion.ANIM_DODGE);
		Quirks.setCooldown(p, ExplosionConfig.SPIN_DODGE, ExplosionConfig.DODGE_COOLDOWN);
		return side;
	}

	/** Test read: the dodge's invulnerability is on. */
	public static boolean invulnerable(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.iframesUntil > now(p);
	}

	private static void tickDodge(ServerPlayer p, Session s, long now) {
		p.resetFallDistance();
		if (now < s.dodgeUntil) {
			return;
		}
		s.dodgeUntil = 0L;
		faceAttacker(p);
	}

	/** The end of the dodge: he turns to face whoever is after him (the last thing that hurt him, else the nearest hunter). */
	public static @Nullable LivingEntity faceAttacker(ServerPlayer p) {
		double range = ExplosionConfig.DODGE_FACE_RANGE;
		LivingEntity who = p.getLastHurtByMob();
		if (who == null || !who.isAlive() || who.distanceTo(p) > range) {
			who = null;
			double best = range * range;
			for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range),
					m -> m.isAlive() && ExplosionBlast.isTarget(p, m) && (m.getTarget() == p || m instanceof net.minecraft.world.entity.monster.Enemy))) {
				double d = m.distanceToSqr(p);
				if (d < best) {
					best = d;
					who = m;
				}
			}
		}
		if (who == null) {
			return null;
		}
		Vec3 to = who.getEyePosition().subtract(p.getEyePosition());
		float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
		p.connection.teleport(0, 0, 0, yaw, pitch, java.util.EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
		p.setYHeadRot(yaw);
		return who;
	}

	// ================================================================ Z: Cluster / Shift+Z: Explosive Uppercut

	/** A ring of small explosions around him, then a wider second ring; each creature is hit once. Returns the first ring's hits. */
	public static int cluster(ServerPlayer p) {
		float power = power(p);
		cost(p, ExplosionConfig.CLUSTER_SWEAT, ExplosionConfig.CLUSTER_STRAIN);
		Session s = session(p);
		s.clusterHit.clear();
		s.clusterAt = p.position();
		s.clusterPower = power;
		s.clusterRingAt = now(p) + ExplosionConfig.CLUSTER_RING_DELAY;
		int n = clusterRing(p, s.clusterAt, ExplosionConfig.CLUSTER_INNER, power, s.clusterHit);
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		Quirks.setAnim(p, Explosion.ANIM_CLUSTER);
		Quirks.setCooldown(p, ExplosionConfig.CLUSTER, ExplosionConfig.CLUSTER_COOLDOWN);
		return n;
	}

	private static int clusterRing(ServerPlayer p, Vec3 at, double ringRadius, float power, Set<Integer> hit) {
		ServerLevel level = p.serverLevel();
		int count = ExplosionConfig.CLUSTER_RING_COUNT;
		double offset = ringRadius > ExplosionConfig.CLUSTER_INNER ? Math.PI / count : 0.0;
		for (int i = 0; i < count; i++) {
			double a = offset + i * Math.PI * 2.0 / count;
			Vec3 c = at.add(Math.cos(a) * ringRadius, 0.8, Math.sin(a) * ringRadius);
			QuirkFx.blast(level, c).colours(0xFFC860, ExplosionBlast.SHELL).size(1.2f).life(13).flags(QuirkFx.F_NO_RING).spawn();
			ExplosionTerrain.blow(p, c.add(0, -0.8, 0), ExplosionTerrain.Tier.CHIP);
		}
		QuirkFx.ring(level, at.add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size((float) (ringRadius + 1.5)).thickness(0.55f).life(12)
				.ground().spawn();
		QuirkSounds.play(level, at, ExplosionSounds.BLAST, 1.6f, 1.1f + p.getRandom().nextFloat() * 0.2f);
		QuirkSounds.play(level, at, ExplosionSounds.BLAST_SMALL, 1.2f, 0.9f);
		QuirkCamera.shakeAround(level, at, 16, 0.35f, 8);
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

	/**
	 * Shift+Z: a rising blast uppercut into the creature in reach -- it is launched, and so is he, after it, for an air
	 * follow-up (the Uppercut then AP Shot route). Nothing in reach: he still rockets up. Returns the target hit, or null.
	 */
	public static @Nullable LivingEntity uppercut(ServerPlayer p) {
		float power = power(p);
		cost(p, ExplosionConfig.UPPERCUT_SWEAT, ExplosionConfig.UPPERCUT_STRAIN);
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		QuirkCombos.used(p, ExplosionConfig.UPPERCUT);
		LivingEntity t = aimed(p, ExplosionConfig.UPPERCUT_REACH, 35.0);
		Vec3 flat = Vec3.directionFromRotation(0f, p.getYRot());
		Vec3 under = t != null ? t.position().add(0, 0.4, 0) : p.position().add(flat.scale(1.2)).add(0, 0.6, 0);
		QuirkFx.cone(level, under, new Vec3(0, 1, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.0f).length(4.5f).life(10).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(3f).thickness(0.45f).life(10).ground()
				.spawn();
		QuirkFx.blast(level, under).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.6f).life(14).spawn();
		QuirkSounds.play(level, under, ExplosionSounds.BLAST, 1.5f, 1.15f);
		// a crater blown out of the ground under the target
		ExplosionTerrain.blow(p, (t != null ? t.position() : under).add(0, -0.6, 0), ExplosionTerrain.Tier.CRATER);
		boolean hit = false;
		if (t != null && ExplosionBlast.strike(p, t, under.add(0, -1, 0), ExplosionConfig.UPPERCUT_DAMAGE * power, 0.0, 0.0)) {
			hit = true;
			if (!ExplosionBlast.isBoss(t)) {
				t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, ExplosionConfig.UPPERCUT_LIFT, 0));
				t.hurtMarked = true;
			}
			QuirkCombos.hit(p, t, ExplosionConfig.UPPERCUT);
		}
		AbilityHelpers.launchSelf(p, new Vec3(flat.x * 0.25, ExplosionConfig.UPPERCUT_SELF_LIFT, flat.z * 0.25));
		QuirkFx.trail(level, p).colours(0xFFF0C0, ExplosionBlast.SHELL).size(0.3f).size2(8f).life(18).vec(new Vec3(0.35, 1.2, 0.3)).style(QuirkFx.RIBBON_FLAME)
				.spawn();
		QuirkCamera.preset(p, hit ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.DASH);
		soft(p, s);
		Quirks.setAnim(p, Explosion.ANIM_UPPERCUT);
		Quirks.setCooldown(p, ExplosionConfig.UPPERCUT, ExplosionConfig.UPPERCUT_COOLDOWN);
		return hit ? t : null;
	}

	// ================================================================ C: Howitzer Impact / Shift+C: Gauntlet Grenade

	/**
	 * He blasts himself up spinning (from the ground), then corkscrews down at the target in a tornado of blasts and detonates
	 * on it. False (refused) without enough Sweat.
	 */
	public static boolean howitzer(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		if (sweat < ExplosionConfig.HOWITZER_MIN_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.HOWITZER_MIN_SWEAT);
			return false;
		}
		Session s = session(p);
		int rise = p.onGround() ? ExplosionConfig.HOWITZER_RISE_TICKS : 3;
		startHowitzer(p, s, 1, power(p), ExplosionConfig.HOWITZER_RANGE, rise, ExplosionConfig.HOWITZER_SPIN_TICKS + rise);
		spend(p, Math.max(ExplosionConfig.HOWITZER_MIN_SWEAT, sweat * ExplosionConfig.HOWITZER_SWEAT_FRACTION));
		Quirks.setAnim(p, Explosion.ANIM_HOWITZER);
		Quirks.setCooldown(p, ExplosionConfig.HOWITZER, ExplosionConfig.HOWITZER_COOLDOWN);
		ServerLevel level = p.serverLevel();
		ExplosionBlast.fx(level, p.position().add(0, 0.3, 0), 2.0, ExplosionBlast.MEDIUM);
		QuirkSounds.play(level, p.position(), ExplosionSounds.HOWITZER, 2.2f, 1.0f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		return true;
	}

	private static void startHowitzer(ServerPlayer p, Session s, int kind, float power, double range, int rise, int total) {
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
		long until = now(p) + total + 4L;
		long dive = s.howitzerDive;
		Quirks.edit(p, n -> {
			n.vals.put(Explosion.V_HOWITZER, until);
			n.vals.put(Explosion.V_HOWITZER_KIND, (long) kind);
			n.vals.put(Explosion.V_HOWITZER_DIVE, dive);
		});
		ServerLevel level = p.serverLevel();
		// the spiral fire vortex he corkscrews in (two ribbons; the client draws the helix round him too)
		for (int side : new int[] { 1, -1 }) {
			QuirkFx.trail(level, p).colours(0xFFF0C0, ExplosionBlast.SHELL).size(0.4f).size2(12f).life(total + 8).vec(new Vec3(1.1 * side, 0.9, 0))
					.style(QuirkFx.RIBBON_FLAME).spawn();
		}
	}

	private static void tickHowitzer(ServerPlayer p, Session s, long now) {
		long age = now - s.howitzerStart;
		ServerLevel level = p.serverLevel();
		int total = (int) (s.howitzerDive - s.howitzerStart) + ExplosionConfig.HOWITZER_SPIN_TICKS;
		Vec3 body = p.position().add(0, 0.9, 0);
		p.resetFallDistance();
		if (now < s.howitzerDive) {
			long k = now - s.howitzerStart;
			double up = ExplosionConfig.HOWITZER_RISE_SPEED * (1.0 - 0.5 * k / Math.max(1.0, s.howitzerDive - s.howitzerStart));
			AbilityHelpers.launchSelf(p, new Vec3(0, up, 0));
			QuirkFx.flipbook(level, p.position().add(0, -0.3, 0)).style(QuirkFx.BOOK_FIREBALL).size(1.1f).life(10).spawn();
			level.sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() - 0.6, p.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
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
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(ExplosionConfig.HOWITZER_SPEED, dist * 0.5)));
		vortex(p, s, age, body);
	}

	/** The corkscrew's tornado: blasts spiralling round him as he spins in, nicking what they pass. */
	private static void vortex(ServerPlayer p, Session s, long age, Vec3 body) {
		ServerLevel level = p.serverLevel();
		double ang = age * Math.toRadians(ExplosionConfig.HOWITZER_SPIN_DEGREES) * 1.3;
		for (int k = 0; k < 2; k++) {
			double a = ang + k * Math.PI;
			double rr = 1.2 + 0.05 * Math.min(age, 24);
			Vec3 c = body.add(Math.cos(a) * rr, (k == 0 ? 0.4 : -0.3), Math.sin(a) * rr);
			level.sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.04);
			level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 2, 0.15, 0.15, 0.15, 0.2);
		}
		if (age % ExplosionConfig.HOWITZER_VORTEX_INTERVAL == 0) {
			if (age % 8 == 0) {
				s.vortexHit.clear();
			}
			double a = ang + Math.PI * 0.5;
			Vec3 c = body.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
			QuirkFx.flipbook(level, c).style(QuirkFx.BOOK_FIREBALL).size(1.0f).life(9).spawn();
			if (age % 4 == 0) {
				QuirkSounds.play(level, c, ExplosionSounds.BLAST_SMALL, 0.6f, 1.3f + p.getRandom().nextFloat() * 0.4f);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ExplosionConfig.HOWITZER_VORTEX_RADIUS),
					e -> ExplosionBlast.isTarget(p, e))) {
				if (s.vortexHit.add(e.getId())) {
					ExplosionBlast.strike(p, e, body, ExplosionConfig.HOWITZER_VORTEX_DAMAGE * s.howitzerPower, 0.3, 0.15);
				}
			}
		}
	}

	private static void detonateHowitzer(ServerPlayer p, Session s) {
		s.howitzer = 0;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		float power = s.howitzerPower;
		double r = radius(ExplosionConfig.HOWITZER_RADIUS, power);
		ExplosionBlast.blast(p, c, r, ExplosionConfig.HOWITZER_DAMAGE * power, ExplosionConfig.HOWITZER_KNOCKBACK, 0.6, ExplosionBlast.BIG, null);
		QuirkCamera.preset(p, QuirkCamera.Preset.FINISHER);
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.45, 0));
		soft(p, s);
		Quirks.edit(p, n -> {
			n.vals.remove(Explosion.V_HOWITZER);
			n.vals.remove(Explosion.V_HOWITZER_KIND);
			n.vals.remove(Explosion.V_HOWITZER_DIVE);
		});
		strain(p, ExplosionConfig.HOWITZER_STRAIN);
	}

	/** Test hook: the Howitzer running now (0 none, 1 Howitzer Impact, 2 the ultimate). */
	public static int howitzerKind(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.howitzer;
	}

	/**
	 * The grenade pin pulled: a short charge, then every drop of stored sweat in one cannon blast down the aim. Needs 50
	 * Sweat -- or the Ground Zero gauntlets' full bank. False without either.
	 */
	public static boolean gauntlet(ServerPlayer p) {
		float sweat = Quirks.meter(p);
		Session s = session(p);
		boolean banked = sweat < ExplosionConfig.GAUNTLET_MIN_SWEAT && s.fullSet && s.bank >= ExplosionConfig.BANK_MAX - 0.01f;
		if (sweat < ExplosionConfig.GAUNTLET_MIN_SWEAT && !banked) {
			Quirks.say(p, "message.projecthero.explosion.need_sweat", ChatFormatting.GOLD, (int) ExplosionConfig.GAUNTLET_MIN_SWEAT);
			return false;
		}
		if (s.propel) {
			endPropel(p);
		}
		if (banked) {
			// the gauntlets hold a full grenade: fired as if soaked, the bank emptied, his own sweat untouched
			s.gauntletPower = ExplosionConfig.POWER_SOAKED;
			s.bank = 0f;
			s.bankSynced = 0;
			Quirks.setVal(p, Explosion.V_BANK, 0L);
		} else {
			s.gauntletPower = power(p);
			Quirks.setMeter(p, 0f);
		}
		s.gauntlet = true;
		s.gauntletAt = now(p) + ExplosionConfig.GAUNTLET_CHARGE;
		Quirks.setVal(p, Explosion.V_GAUNTLET, s.gauntletAt);
		Quirks.setAnim(p, Explosion.ANIM_GAUNTLET);
		Quirks.setCooldown(p, ExplosionConfig.GAUNTLET, ExplosionConfig.GAUNTLET_COOLDOWN);
		ExplosionBlast.play(p.serverLevel(), p.position(), SoundEvents.CHAIN_BREAK, 1.0f, 1.4f);
		QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.CHARGE_UP, 1.0f, 1.2f);
		return true;
	}

	private static void tickGauntlet(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		Vec3 hand = shotHand(p, true);
		if (now < s.gauntletAt) {
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.5, 1.0, 0.5));
			for (int i = 0; i < 3; i++) {
				Vec3 d = new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian(), p.getRandom().nextGaussian()).normalize().scale(1.1);
				Vec3 from = hand.add(d);
				level.sendParticles(QuirkParticles.SPARK, from.x, from.y, from.z, 0, -d.x, -d.y, -d.z, 0.3);
			}
			if ((s.gauntletAt - now) % 4 == 0) {
				QuirkSounds.play(level, hand, ExplosionSounds.CRACKLE, 0.8f, 0.8f + 0.06f * (ExplosionConfig.GAUNTLET_CHARGE - (s.gauntletAt - now)));
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
		var bhr = level.clip(new ClipContext(p.getEyePosition(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation().subtract(look.scale(0.4));
		double len = from.distanceTo(end);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		double r = radius(ExplosionConfig.GAUNTLET_RADIUS, power);
		float dmg = ExplosionConfig.GAUNTLET_DAMAGE * power;
		QuirkFx.cone(level, from, look).style(QuirkFx.CONE_BEAM).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.1f).length((float) len).life(14)
				.spawn();
		QuirkFx.cone(level, from, look).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(2.6f).length(7f).life(12).spawn();
		for (double d = ExplosionConfig.GAUNTLET_STEP * 0.5; d < len; d += ExplosionConfig.GAUNTLET_STEP) {
			Vec3 c = from.add(look.scale(d));
			n += ExplosionBlast.blast(p, c, r, dmg, ExplosionConfig.GAUNTLET_KNOCKBACK, 0.4, ExplosionBlast.SMALL, hit, false);
		}
		n += ExplosionBlast.blast(p, end, r * 1.2, dmg, ExplosionConfig.GAUNTLET_KNOCKBACK, 0.5, ExplosionBlast.BIG, hit);
		QuirkFx.ring(level, from.add(look.scale(0.6))).flags(QuirkFx.F_VERTICAL).vec(look).colours(ExplosionBlast.WHITE, ExplosionBlast.CORE).size(3.2f)
				.thickness(0.5f).life(12).spawn();
		QuirkSounds.play(level, from, ExplosionSounds.HOWITZER, 2.6f, 1.2f);
		QuirkCamera.preset(p, QuirkCamera.Preset.FINISHER);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(look.scale(-ExplosionConfig.GAUNTLET_RECOIL)).add(0, 0.2, 0));
		soft(p, s);
		strain(p, ExplosionConfig.GAUNTLET_STRAIN);
		return n;
	}

	// ================================================================ Plus Ultra: Howitzer Impact -- Cluster

	/**
	 * The ultimate (hold C at a full Plus Ultra gauge; the gauge is already spent): a spinning rise that marks up to five
	 * targets within 20 blocks, a corkscrew chain-dive through each (15 a target), and a 40 blast in radius 8 to finish.
	 * Afterwards the arms are shot (Arm Strain to 100).
	 */
	public static void ultimate(ServerPlayer p) {
		Session s = session(p);
		if (s.propel) {
			endPropel(p);
		}
		s.autoLeft = 0;
		s.pierce = false;
		s.gauntlet = false;
		if (s.gripTarget >= 0) {
			endGrip(p, s);
		}
		ServerLevel level = p.serverLevel();
		s.ultTargets.clear();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ExplosionConfig.ULT_MARK_RADIUS),
				e -> e.isAlive() && ExplosionBlast.isTarget(p, e) && e.distanceTo(p) <= ExplosionConfig.ULT_MARK_RADIUS);
		near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		for (LivingEntity e : near) {
			if (s.ultTargets.size() >= ExplosionConfig.ULT_TARGETS) {
				break;
			}
			s.ultTargets.add(e.getId());
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false, true));
		}
		s.ultIndex = 0;
		s.ultHits = 0;
		int legs = Math.max(1, s.ultTargets.size());
		int total = ExplosionConfig.ULT_RISE_TICKS + legs * ExplosionConfig.ULT_LEG_TICKS + 6;
		startHowitzer(p, s, 2, power(p), ExplosionConfig.HOWITZER_RANGE, ExplosionConfig.ULT_RISE_TICKS, total);
		s.ultLegStart = s.howitzerDive;
		Quirks.setAnim(p, Explosion.ANIM_HOWITZER_CLUSTER);
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size((float) ExplosionConfig.ULT_MARK_RADIUS)
				.thickness(0.8f).life(20).ground().spawn();
		ExplosionBlast.fx(level, p.position().add(0, 0.3, 0), 2.5, ExplosionBlast.MEDIUM);
		QuirkSounds.play(level, p.position(), ExplosionSounds.HOWITZER, 3.0f, 0.85f);
		QuirkCamera.preset(p, QuirkCamera.Preset.ULTIMATE);
	}

	/** Test read: the creatures the ultimate marked. */
	public static List<Integer> ultimateTargets(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? List.of() : List.copyOf(s.ultTargets);
	}

	private static @Nullable LivingEntity ultTarget(ServerLevel level, Session s) {
		while (s.ultIndex < s.ultTargets.size()) {
			if (level.getEntity(s.ultTargets.get(s.ultIndex)) instanceof LivingEntity t && t.isAlive()) {
				return t;
			}
			s.ultIndex++;
		}
		return null;
	}

	private static void tickUltimate(ServerPlayer p, Session s, long now) {
		long age = now - s.howitzerStart;
		ServerLevel level = p.serverLevel();
		Vec3 body = p.position().add(0, 0.9, 0);
		p.resetFallDistance();
		if (now < s.howitzerDive) {
			// the spinning rise
			double k = (now - s.howitzerStart) / (double) ExplosionConfig.ULT_RISE_TICKS;
			AbilityHelpers.launchSelf(p, new Vec3(0, ExplosionConfig.ULT_RISE_SPEED * (1.0 - 0.8 * k), 0));
			QuirkFx.flipbook(level, p.position().add(0, -0.2, 0)).style(QuirkFx.BOOK_FIREBALL).size(1.3f).life(10).spawn();
			vortex(p, s, age, body);
			return;
		}
		LivingEntity t = ultTarget(level, s);
		long cap = ExplosionConfig.ULT_RISE_TICKS + (long) Math.max(1, s.ultTargets.size()) * ExplosionConfig.ULT_LEG_TICKS + 6;
		if (t == null || age >= cap) {
			// out of targets: the final blast where he is (or diving onto the aim point first, when he marked nobody)
			if (s.ultTargets.isEmpty() && age < cap) {
				Vec3 to = s.howitzerTarget.subtract(body);
				if (to.length() > ExplosionConfig.HOWITZER_ARRIVE && !(p.onGround() && now - s.howitzerDive > 2)) {
					AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(ExplosionConfig.ULT_DIVE_SPEED, to.length() * 0.5)));
					vortex(p, s, age, body);
					return;
				}
			}
			finishUltimate(p, s);
			return;
		}
		Vec3 to = centre(t).subtract(body);
		double dist = to.length();
		if (dist <= ExplosionConfig.HOWITZER_ARRIVE || now - s.ultLegStart >= ExplosionConfig.ULT_LEG_TICKS) {
			// through it: 15 and a blast, then on to the next
			if (dist <= ExplosionConfig.HOWITZER_ARRIVE + 1.5) {
				ExplosionBlast.blast(p, centre(t), ExplosionConfig.ULT_TARGET_RADIUS, ExplosionConfig.ULT_TARGET_DAMAGE * s.howitzerPower, 1.2, 0.5,
						ExplosionBlast.MEDIUM, null, false);
				s.ultHits++;
				QuirkCamera.hitStop(p, 2);
			}
			s.ultIndex++;
			s.ultLegStart = now;
			return;
		}
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(ExplosionConfig.ULT_DIVE_SPEED, dist * 0.6)));
		vortex(p, s, age, body);
	}

	private static void finishUltimate(ServerPlayer p, Session s) {
		s.howitzer = 0;
		s.ultTargets.clear();
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.6, 0);
		ExplosionBlast.blast(p, c, ExplosionConfig.ULT_FINAL_RADIUS, ExplosionConfig.ULT_FINAL_DAMAGE * s.howitzerPower, ExplosionConfig.HOWITZER_KNOCKBACK,
				0.8, ExplosionBlast.BIG, null, false);
		ExplosionTerrain.blow(p, p.position().add(0, -0.5, 0), ExplosionTerrain.Tier.HUGE);
		QuirkFx.blast(level, c).colours(0xFFD890, ExplosionBlast.SHELL).size(7.5f).life(30).spawn();
		QuirkFx.decal(level, p.position()).style(QuirkFx.DECAL_SCORCH).colour(0x120A06).size(7f).life(600).spawn();
		QuirkFx.decal(level, p.position()).style(QuirkFx.DECAL_CRACK).colour(0x161210).size(6f).life(600).spawn();
		QuirkFx.ring(level, c).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(16f).thickness(1.2f).life(22).ground().spawn();
		QuirkSounds.play(level, c, ExplosionSounds.HOWITZER, 3.5f, 0.7f);
		QuirkCamera.preset(p, QuirkCamera.Preset.ULTIMATE);
		QuirkCamera.shakeAround(level, c, 40, 0.9f, 20);
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.5, 0));
		soft(p, s);
		Quirks.edit(p, n -> {
			n.vals.remove(Explosion.V_HOWITZER);
			n.vals.remove(Explosion.V_HOWITZER_KIND);
			n.vals.remove(Explosion.V_HOWITZER_DIVE);
		});
		// the drawback: the arms are shot
		strainOut(p);
	}

	/** Test hook: the ultimate's leg / finish happens now (skip the flight time). */
	public static void finishUltimateForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.howitzer == 2) {
			ServerLevel level = p.serverLevel();
			for (LivingEntity t; (t = ultTarget(level, s)) != null;) {
				p.teleportTo(t.getX(), t.getY() + 0.2, t.getZ() - 1.0);
				ExplosionBlast.blast(p, centre(t), ExplosionConfig.ULT_TARGET_RADIUS, ExplosionConfig.ULT_TARGET_DAMAGE * s.howitzerPower, 1.2, 0.5,
						ExplosionBlast.MEDIUM, null, false);
				s.ultHits++;
				s.ultIndex++;
			}
			finishUltimate(p, s);
		}
	}

	// ================================================================ passives: the landing blast, blast mining

	/** A long fall with sweat on his palms: a blast under him breaks it (no fall damage), spending {@link ExplosionConfig#LANDING_SWEAT}. */
	public static boolean landingBlast(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (now - s.lastLanding <= 1) {
			return true; // the same fall (both damage hooks run): already blasted
		}
		if (Quirks.meter(p) < ExplosionConfig.LANDING_SWEAT) {
			return false;
		}
		s.lastLanding = now;
		spend(p, ExplosionConfig.LANDING_SWEAT);
		Vec3 c = p.position().add(0, 0.2, 0);
		ExplosionBlast.blast(p, c, ExplosionConfig.LANDING_RADIUS, ExplosionConfig.LANDING_DAMAGE * power(p), 0.9, 0.3, ExplosionBlast.MEDIUM, null, false);
		ExplosionTerrain.blow(p, p.position().add(0, -0.8, 0), ExplosionTerrain.Tier.CRATER);
		QuirkFx.ring(p.serverLevel(), p.position().add(0, 0.05, 0)).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(4f).thickness(0.5f).life(12)
				.ground().spawn();
		p.resetFallDistance();
		return true;
	}

	/**
	 * Sneak + punch with an empty hand: a palm blast breaks the 3x3 of soft stone / earth on the face he hit into drops
	 * ({@link ExplosionConfig#MINING_SWEAT} Sweat). Only what he may break himself (spawn protection, claims, adventure
	 * mode, the break event), never a block entity, nothing harder than stone. Returns the blocks broken (-1: refused).
	 */
	public static int blastMine(ServerPlayer p, BlockPos pos, Direction face) {
		Session s = session(p);
		long now = now(p);
		if (now < s.mineReadyAt) {
			return -1;
		}
		ServerLevel level = p.serverLevel();
		if (Quirks.meter(p) < ExplosionConfig.MINING_SWEAT) {
			Quirks.say(p, "message.projecthero.explosion.dry", ChatFormatting.GOLD);
			s.mineReadyAt = now + ExplosionConfig.MINING_COOLDOWN;
			return -1;
		}
		Direction.Axis axis = face.getAxis();
		int n = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos q = switch (axis) {
					case Y -> pos.offset(a, 0, b);
					case X -> pos.offset(0, a, b);
					default -> pos.offset(a, b, 0);
				};
				if (mineable(p, level, q)) {
					BlockState st = level.getBlockState(q);
					if (level.destroyBlock(q, !p.isCreative(), p)) {
						n++;
						if (n <= 3) {
							QuirkFx.shards(level, Vec3.atCenterOf(q)).block(st).size(1.2f).count(10).life(24).spawn();
						}
					}
				}
			}
		}
		s.mineReadyAt = now + ExplosionConfig.MINING_COOLDOWN;
		if (n == 0) {
			return 0;
		}
		spend(p, ExplosionConfig.MINING_SWEAT);
		Vec3 c = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.6));
		QuirkFx.blast(level, c).colours(ExplosionBlast.WHITE, ExplosionBlast.SHELL).size(1.6f).life(12).flags(QuirkFx.F_NO_RING).spawn();
		QuirkSounds.play(level, c, ExplosionSounds.BLAST_SMALL, 1.2f, 0.95f);
		Quirks.setAnim(p, Explosion.ANIM_MINE);
		return n;
	}

	private static boolean mineable(ServerPlayer p, ServerLevel level, BlockPos q) {
		BlockState st = level.getBlockState(q);
		if (!st.is(BlockTags.MINEABLE_WITH_PICKAXE) && !st.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return false;
		}
		// the same player-caused break rules as every blast (rights, claims, mod guards, no block entities, hardness)
		return ExplosionTerrain.canBreak(p, level, q, ExplosionConfig.MINING_MAX_HARDNESS);
	}

	// ================================================================ incoming damage

	/**
	 * Damage he is about to take: true if it is cancelled -- the Spin Dodge's moment of invulnerability, fall / wall damage
	 * after his own launches, a long fall broken by the landing blast, his own blasts (never).
	 */
	public static boolean cancelsDamage(ServerPlayer p, DamageSource source) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.iframesUntil > now(p) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			if (softLanding(p)) {
				p.resetFallDistance();
				return true;
			}
			if (source.is(DamageTypes.FALL) && p.fallDistance > ExplosionConfig.LANDING_FALL && landingBlast(p)) {
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

	/** v0.17.1: the full-set flag the kit last saw ({@link QuirkPerks}). */
	static boolean fullSet(ServerPlayer p) {
		return QuirkPerks.fullSet(p);
	}
}
