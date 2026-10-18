package com.projecthero.mod.quirk.zerogravity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Zero Gravity's twelve moves (see {@code docs/QUIRKS_PLAN.md}) and the per-player sessions of the ones that
 * run over time (the Meteor Slam impacts, the Gunhead Combo, the Float Leap), plus the Nausea meter. Every public move
 * is called by {@link ZeroGravityKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <pre>
 *   R  Float Touch      Shift+R  Float Fling
 *   G  Release          Shift+G  Meteor Slam
 *   X  Meteor Shower    Shift+X  Debris Shield
 *   Z  Zero G Self      Shift+Z  Float Leap
 *   V  Comet            Shift+V  Uravity Meteor
 *   C  Gunhead Throw    Shift+C  Gunhead Combo
 * </pre>
 * What floats is {@link ZeroGravityFloat}; the rubble is {@link ZeroGravityDebris}.
 */
public final class ZeroGravityMoves {
	static final ResourceLocation LEAP_GRAVITY = ResourceLocation.fromNamespaceAndPath("projecthero", "zero_gravity_leap");

	static final class Slammed {
		final int id;
		final long from;

		Slammed(int id, long from) {
			this.id = id;
			this.from = from;
		}
	}

	static final class Session {
		final List<Slammed> slams = new ArrayList<>();
		int comboTarget = -1;
		int comboStage;
		long comboNext;
		long leapFrom;
		boolean leaping;
		int streak;
		boolean warned;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ZeroGravityMoves() {
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

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static Vec3 hand(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.35, 0);
	}

	private static void syncCount(ServerPlayer p) {
		long n = ZeroGravityFloat.count(p);
		if (Quirks.state(p).val(ZeroGravity.V_FLOATING) != n) {
			Quirks.setVal(p, ZeroGravity.V_FLOATING, n);
		}
	}

	/** What a touch move may reach: anything she can float, or (to say "immune") a boss. */
	private static @Nullable LivingEntity touchTarget(ServerPlayer p, double reach) {
		return ZeroGravityCombat.aimed(p, reach, ZeroGravityConfig.TOUCH_ASSIST_DEGREES,
				e -> ZeroGravityFloat.canFloat(p, e, false) || ZeroGravityFloat.floatedBy(p, e)
						|| (ZeroGravityCombat.isBoss(e) && ZeroGravityCombat.isTarget(p, e)));
	}

	/** Floats {@code e} by touch (the cap, the sparkle, the boop, the sting of Nausea). */
	private static boolean touchFloat(ServerPlayer p, LivingEntity e) {
		if (ZeroGravityFloat.floatedBy(p, e)) {
			return true;
		}
		if (ZeroGravityCombat.isBoss(e)) {
			Quirks.say(p, "message.projecthero.zero_gravity.boss_immune", ChatFormatting.LIGHT_PURPLE);
			return false;
		}
		if (ZeroGravityFloat.count(p) >= ZeroGravityConfig.TOUCH_MAX) {
			Quirks.say(p, "message.projecthero.zero_gravity.max_floats", ChatFormatting.LIGHT_PURPLE, ZeroGravityConfig.TOUCH_MAX);
			return false;
		}
		if (!ZeroGravityFloat.start(p, e, false)) {
			return false;
		}
		Quirks.addMeter(p, ZeroGravityConfig.NAUSEA_PER_FLOAT);
		ZeroGravityCombat.touchFx(p.serverLevel(), ZeroGravityCombat.center(e));
		syncCount(p);
		return true;
	}

	// ================================================================ R: Float Touch / Shift+R: Float Fling

	/** A five-finger hit: the target floats helplessly (up to {@link ZeroGravityConfig#TOUCH_MAX}). */
	public static @Nullable LivingEntity floatTouch(ServerPlayer p) {
		Quirks.setAnim(p, ZeroGravity.ANIM_TOUCH);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		LivingEntity e = touchTarget(p, ZeroGravityConfig.TOUCH_REACH);
		if (e == null) {
			return null;
		}
		boolean ally = ZeroGravityFloat.wasAlly(p, e);
		if (!ally) {
			ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.TOUCH_DAMAGE, 0.0, 0.0);
		}
		Quirks.setCooldown(p, ZeroGravityConfig.TOUCH, ZeroGravityConfig.TOUCH_COOLDOWN);
		return touchFloat(p, e) ? e : null;
	}

	/** Touch, float and fling it the way she looks (it sails on: nothing pulls it down). */
	public static @Nullable LivingEntity floatFling(ServerPlayer p) {
		Quirks.setAnim(p, ZeroGravity.ANIM_FLING);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		LivingEntity e = touchTarget(p, ZeroGravityConfig.TOUCH_REACH);
		if (e == null || !touchFloat(p, e)) {
			return null;
		}
		if (!ZeroGravityFloat.wasAlly(p, e)) {
			ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.TOUCH_DAMAGE, 0.0, 0.0);
		}
		Vec3 v = p.getLookAngle().scale(ZeroGravityConfig.FLING_SPEED).add(0, 0.12, 0);
		AbilityHelpers.push(e, v.subtract(e.getDeltaMovement()));
		ZeroGravityFloat.markFlung(p, e, 40);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
		Quirks.setCooldown(p, ZeroGravityConfig.FLING, ZeroGravityConfig.FLING_COOLDOWN);
		return e;
	}

	// ================================================================ G: Release / Shift+G: Meteor Slam

	/** "Release!" -- fingertips together: everything she floats (and herself) drops. Returns how many creatures. */
	public static int release(ServerPlayer p) {
		boolean self = ZeroGravityFloat.selfFloating(p);
		List<LivingEntity> out = ZeroGravityFloat.releaseAll(p);
		if (self) {
			stopSelf(p);
		}
		syncCount(p);
		if (out.isEmpty() && !self) {
			Quirks.say(p, "message.projecthero.zero_gravity.nothing_floating", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		Quirks.setAnim(p, ZeroGravity.ANIM_RELEASE);
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : out) {
			ZeroGravityCombat.burst(level, ZeroGravityCombat.center(e), 6, 0.3);
		}
		ZeroGravityCombat.boop(level, hand(p), 0.7f);
		Quirks.setCooldown(p, ZeroGravityConfig.RELEASE, ZeroGravityConfig.RELEASE_COOLDOWN);
		return out.size();
	}

	/** Everything she floats is let go and hurled into the ground ({@link ZeroGravityConfig#SLAM_DAMAGE} on impact). */
	public static int meteorSlam(ServerPlayer p) {
		List<LivingEntity> out = ZeroGravityFloat.releaseAll(p);
		syncCount(p);
		if (out.isEmpty()) {
			Quirks.say(p, "message.projecthero.zero_gravity.nothing_floating", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		Session s = session(p);
		long now = now(p);
		int n = 0;
		for (LivingEntity e : out) {
			if (!ZeroGravityCombat.isTarget(p, e)) {
				continue; // a squadmate is just let down gently
			}
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.2, -ZeroGravityConfig.SLAM_SPEED, v.z * 0.2);
			e.hurtMarked = true;
			s.slams.add(new Slammed(e.getId(), now));
			n++;
		}
		Quirks.setAnim(p, ZeroGravity.ANIM_SLAM);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.6f);
		Quirks.setCooldown(p, ZeroGravityConfig.SLAM, ZeroGravityConfig.SLAM_COOLDOWN);
		return n;
	}

	private static void tickSlams(ServerPlayer p, Session s, long now) {
		Iterator<Slammed> it = s.slams.iterator();
		ServerLevel level = p.serverLevel();
		while (it.hasNext()) {
			Slammed sl = it.next();
			if (!(level.getEntity(sl.id) instanceof LivingEntity e) || !e.isAlive() || now - sl.from > ZeroGravityConfig.SLAM_WATCH_TICKS) {
				it.remove();
				continue;
			}
			long age = now - sl.from;
			if (age < 1) {
				continue;
			}
			boolean down = e.onGround() || e.verticalCollisionBelow || (age > 2 && e.getDeltaMovement().y > -0.05);
			if (!down) {
				if (age % 2 == 0) {
					ZeroGravityCombat.trail(level, e.position().add(0, e.getBbHeight(), 0));
				}
				continue;
			}
			it.remove();
			Vec3 c = e.position();
			ZeroGravityCombat.strike(p, e, c, ZeroGravityConfig.SLAM_DAMAGE, 0.0, 0.0);
			for (LivingEntity o : AbilityHelpers.enemiesAround(p, c, ZeroGravityConfig.SLAM_SPLASH_RADIUS)) {
				if (o != e && ZeroGravityCombat.isTarget(p, o)) {
					ZeroGravityCombat.strike(p, o, c, ZeroGravityConfig.SLAM_SPLASH_DAMAGE, 0.6, 0.2);
				}
			}
			BlockPos under = BlockPos.containing(c).below();
			level.levelEvent(2001, under, Block.getId(level.getBlockState(under)));
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			ZeroGravityCombat.burst(level, c.add(0, 0.3, 0), 10, 0.7);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 0.9f);
		}
	}

	// ================================================================ X: Meteor Shower / Shift+X: Debris Shield

	/** Loose rubble floats up around her and rains on the target. Returns the pieces. */
	public static int meteorShower(ServerPlayer p, @Nullable List<BlockPos> taken) {
		LivingEntity target = ZeroGravityCombat.aimed(p, ZeroGravityConfig.SHOWER_RANGE, 12.0, e -> ZeroGravityCombat.isTarget(p, e));
		Vec3 point = target != null ? ZeroGravityCombat.center(target) : AbilityHelpers.aimPoint(p, ZeroGravityConfig.SHOWER_RANGE);
		int n = ZeroGravityDebris.startShower(p, target, point, taken);
		long until = now(p) + ZeroGravityConfig.SHOWER_HOVER_TICKS + (long) n * ZeroGravityConfig.SHOWER_FIRE_GAP + 30L;
		Quirks.setVal(p, ZeroGravity.V_SHOWER, until);
		Quirks.setAnim(p, ZeroGravity.ANIM_SHOWER);
		ZeroGravityCombat.boop(p.serverLevel(), p.position(), 0.8f);
		Quirks.setCooldown(p, ZeroGravityConfig.SHOWER, ZeroGravityConfig.SHOWER_COOLDOWN);
		return n;
	}

	/** Rubble orbits her for {@link ZeroGravityConfig#SHIELD_TICKS} and stops every projectile that comes near. */
	public static int debrisShield(ServerPlayer p, @Nullable List<BlockPos> taken) {
		int n = ZeroGravityDebris.startShield(p, taken);
		Quirks.setVal(p, ZeroGravity.V_SHIELD, now(p) + ZeroGravityConfig.SHIELD_TICKS);
		Quirks.setAnim(p, ZeroGravity.ANIM_SHIELD);
		ZeroGravityCombat.boop(p.serverLevel(), p.position(), 0.9f);
		Quirks.setCooldown(p, ZeroGravityConfig.SHIELD, ZeroGravityConfig.SHIELD_COOLDOWN);
		return n;
	}

	// ================================================================ Z: Zero G Self / Shift+Z: Float Leap

	/** Toggle: she floats (Jump rises, Sneak sinks -- the client side moves her). Returns the new state. */
	public static boolean zeroGSelf(ServerPlayer p) {
		Quirks.setCooldown(p, ZeroGravityConfig.SELF, ZeroGravityConfig.SELF_COOLDOWN);
		if (ZeroGravityFloat.selfFloating(p)) {
			stopSelf(p);
			ZeroGravityCombat.boop(p.serverLevel(), p.position(), 0.7f);
			return false;
		}
		endLeap(p);
		ZeroGravityFloat.startSelf(p);
		Quirks.setVal(p, ZeroGravity.V_SELF, 1L);
		Quirks.setAnim(p, ZeroGravity.ANIM_SELF);
		ZeroGravityCombat.touchFx(p.serverLevel(), p.position().add(0, 1.0, 0));
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.18, 0));
		return true;
	}

	static void stopSelf(ServerPlayer p) {
		ZeroGravityFloat.stopSelf(p);
		if (Quirks.state(p).val(ZeroGravity.V_SELF) != 0L) {
			Quirks.setVal(p, ZeroGravity.V_SELF, 0L);
		}
	}

	/** A huge weightless leap: her gravity nearly gone until she lands (no fall damage on the way down). */
	public static void floatLeap(ServerPlayer p) {
		stopSelf(p);
		Vec3 v = flatLook(p).scale(ZeroGravityConfig.LEAP_FORWARD).add(0, ZeroGravityConfig.LEAP_UP, 0);
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
			g.addTransientModifier(new AttributeModifier(LEAP_GRAVITY, -ZeroGravityConfig.LEAP_GRAVITY_CUT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AbilityHelpers.launchSelf(p, v);
		Session s = session(p);
		s.leaping = true;
		s.leapFrom = now(p);
		Quirks.setVal(p, ZeroGravity.V_LEAP, now(p) + ZeroGravityConfig.LEAP_MAX_TICKS);
		Quirks.setAnim(p, ZeroGravity.ANIM_LEAP);
		ZeroGravityCombat.touchFx(p.serverLevel(), p.position());
		sound(p, SoundEvents.PHANTOM_FLAP, 0.6f, 1.5f);
		Quirks.setCooldown(p, ZeroGravityConfig.LEAP, ZeroGravityConfig.LEAP_COOLDOWN);
	}

	static void endLeap(ServerPlayer p) {
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
		}
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.leaping = false;
		}
		if (Quirks.state(p).val(ZeroGravity.V_LEAP) != 0L) {
			Quirks.setVal(p, ZeroGravity.V_LEAP, 0L);
		}
	}

	public static boolean leaping(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.leaping;
	}

	private static void tickLeap(ServerPlayer p, Session s, long now) {
		p.resetFallDistance();
		long age = now - s.leapFrom;
		if (age % 2 == 0) {
			ZeroGravityCombat.trail(p.serverLevel(), p.position());
		}
		if ((age > 5 && (p.onGround() || p.isInWater())) || age > ZeroGravityConfig.LEAP_MAX_TICKS) {
			endLeap(p);
		}
	}

	// ================================================================ V: Comet / Shift+V: Uravity Meteor

	/** Everything in a wide area floats at once (bosses and squadmates excepted). Returns how many. */
	public static int comet(ServerPlayer p) {
		int n = 0;
		ServerLevel level = p.serverLevel();
		List<LivingEntity> near = AbilityHelpers.enemiesAround(p, p.position(), ZeroGravityConfig.COMET_RADIUS);
		near.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		for (LivingEntity e : near) {
			if (ZeroGravityFloat.count(p) >= ZeroGravityConfig.COMET_MAX) {
				break;
			}
			if (ZeroGravityFloat.start(p, e, true)) {
				Quirks.addMeter(p, ZeroGravityConfig.NAUSEA_PER_FLOAT);
				ZeroGravityCombat.burst(level, ZeroGravityCombat.center(e), 8, 0.3);
				n++;
			}
		}
		// the shockwave ring
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2.0 / 32;
			for (double r = 2.0; r <= ZeroGravityConfig.COMET_RADIUS; r += 3.0) {
				level.sendParticles(ZeroGravityCombat.PINK_BIG, p.getX() + Math.cos(a) * r, p.getY() + 0.3, p.getZ() + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
		ZeroGravityCombat.boop(level, p.position(), 0.6f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
		syncCount(p);
		Quirks.setAnim(p, ZeroGravity.ANIM_COMET);
		Quirks.setCooldown(p, ZeroGravityConfig.COMET, ZeroGravityConfig.COMET_COOLDOWN);
		return n;
	}

	/** Rips up a slab of ground before her, floats it over the target and drops it on it. Returns the pieces. */
	public static int uravityMeteor(ServerPlayer p, @Nullable List<BlockPos> taken) {
		LivingEntity target = ZeroGravityCombat.aimed(p, ZeroGravityConfig.METEOR_RANGE, 10.0, e -> ZeroGravityCombat.isTarget(p, e));
		Vec3 point = target != null ? target.position() : AbilityHelpers.aimPoint(p, ZeroGravityConfig.METEOR_RANGE);
		int n = ZeroGravityDebris.startMeteor(p, target, point, taken);
		Quirks.addMeter(p, ZeroGravityConfig.METEOR_NAUSEA);
		Quirks.setVal(p, ZeroGravity.V_METEOR, now(p) + ZeroGravityConfig.METEOR_LIFE);
		Quirks.setAnim(p, ZeroGravity.ANIM_METEOR);
		ZeroGravityCombat.touchFx(p.serverLevel(), p.position().add(flatLook(p).scale(3.0)));
		Quirks.setCooldown(p, ZeroGravityConfig.METEOR, ZeroGravityConfig.METEOR_COOLDOWN);
		return n;
	}

	// ================================================================ C: Gunhead Throw / Shift+C: Gunhead Combo

	/** Gunhead martial arts: a grab and a throw (a floating target sails far further). */
	public static @Nullable LivingEntity gunheadThrow(ServerPlayer p) {
		Quirks.setAnim(p, ZeroGravity.ANIM_THROW);
		LivingEntity e = ZeroGravityCombat.aimed(p, ZeroGravityConfig.THROW_REACH, 25.0, x -> ZeroGravityCombat.isTarget(p, x));
		if (e == null) {
			return null;
		}
		boolean floating = ZeroGravityFloat.floatedBy(p, e);
		if (!ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.THROW_DAMAGE, 0.0, 0.0)) {
			return null;
		}
		if (!ZeroGravityCombat.isBoss(e)) {
			Vec3 v = floating ? p.getLookAngle().scale(ZeroGravityConfig.THROW_FLOAT_SPEED).add(0, 0.1, 0)
					: flatLook(p).scale(ZeroGravityConfig.THROW_SPEED).add(0, ZeroGravityConfig.THROW_LIFT, 0);
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			if (floating) {
				ZeroGravityFloat.markFlung(p, e, 60);
			}
		}
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 1.1f);
		Quirks.setCooldown(p, ZeroGravityConfig.THROW, ZeroGravityConfig.THROW_COOLDOWN);
		return e;
	}

	/** Three quick Gunhead strikes on the target, the last one knocks it away. */
	public static @Nullable LivingEntity gunheadCombo(ServerPlayer p) {
		LivingEntity e = ZeroGravityCombat.aimed(p, ZeroGravityConfig.COMBO_REACH, 25.0, x -> ZeroGravityCombat.isTarget(p, x));
		Quirks.setAnim(p, ZeroGravity.ANIM_COMBO);
		if (e == null) {
			return null;
		}
		Session s = session(p);
		s.comboTarget = e.getId();
		s.comboStage = 0;
		s.comboNext = now(p);
		Quirks.setVal(p, ZeroGravity.V_COMBO, now(p) + 3L * ZeroGravityConfig.COMBO_GAP + 2L);
		comboHit(p, s);
		Quirks.setCooldown(p, ZeroGravityConfig.COMBO, ZeroGravityConfig.COMBO_COOLDOWN);
		return e;
	}

	private static void comboHit(ServerPlayer p, Session s) {
		int stage = s.comboStage;
		if (stage >= ZeroGravityConfig.COMBO_DAMAGE.length || !(p.serverLevel().getEntity(s.comboTarget) instanceof LivingEntity e)
				|| !e.isAlive() || e.distanceTo(p) > ZeroGravityConfig.COMBO_REACH + 2.0) {
			s.comboTarget = -1;
			return;
		}
		boolean last = stage == ZeroGravityConfig.COMBO_DAMAGE.length - 1;
		ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.COMBO_DAMAGE[stage], last ? ZeroGravityConfig.COMBO_FINISH_KNOCKBACK : 0.15,
				last ? 0.3 : 0.0);
		p.swing(stage % 2 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
		sound(p, last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f + 0.15f * stage);
		s.comboStage++;
		s.comboNext = now(p) + ZeroGravityConfig.COMBO_GAP;
		if (s.comboStage >= ZeroGravityConfig.COMBO_DAMAGE.length) {
			s.comboTarget = -1;
		}
	}

	// ================================================================ the tick: Nausea and running moves

	public static void tick(ServerPlayer p) {
		long now = now(p);
		ZeroGravityFloat.tick(p);
		ZeroGravityDebris.tick(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			if (!s.slams.isEmpty()) {
				tickSlams(p, s, now);
			}
			if (s.comboTarget >= 0 && now >= s.comboNext) {
				comboHit(p, s);
			}
			if (s.leaping) {
				tickLeap(p, s, now);
			}
		}
		if (now % 10 == 0) {
			syncCount(p);
			if (ZeroGravityDebris.count(p) == 0 && Quirks.state(p).val(ZeroGravity.V_SHIELD) > now && !ZeroGravityDebris.shieldUp(p)) {
				Quirks.setVal(p, ZeroGravity.V_SHIELD, 0L);
			}
		}
		nausea(p);
	}

	/** Nausea rises with the weight she floats (and the time it has been floating), falls when nothing floats. */
	static void nausea(ServerPlayer p) {
		double w = ZeroGravityFloat.weight(p) + ZeroGravityDebris.count(p) * ZeroGravityConfig.WEIGHT_RUBBLE;
		if (ZeroGravityFloat.selfFloating(p) || leaping(p)) {
			w += ZeroGravityConfig.WEIGHT_SELF;
		}
		Session s = session(p);
		if (w <= 0.0) {
			s.streak = 0;
			float m = Quirks.addMeter(p, -ZeroGravityConfig.NAUSEA_DECAY_SEC / 20f);
			if (m < 50f) {
				s.warned = false;
			}
			return;
		}
		s.streak++;
		float bonus = 1f + ZeroGravityConfig.NAUSEA_TIME_BONUS_MAX * Math.min(1f, s.streak / (float) ZeroGravityConfig.NAUSEA_TIME_BONUS_TICKS);
		float m = Quirks.addMeter(p, (float) (ZeroGravityConfig.NAUSEA_PER_WEIGHT_SEC * w / 20.0 * bonus));
		if (m >= 100f) {
			sick(p);
			return;
		}
		if (m >= ZeroGravityConfig.NAUSEA_WARN && !s.warned) {
			s.warned = true;
			Quirks.say(p, "message.projecthero.zero_gravity.queasy", ChatFormatting.LIGHT_PURPLE);
		}
	}

	/** 100 Nausea: everything drops (stopAll), she is sick and has no moves for a while. */
	public static void sick(ServerPlayer p) {
		Quirks.overload(p, ZeroGravityConfig.NAUSEA_OVERLOAD_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ZeroGravityConfig.NAUSEA_OVERLOAD_TICKS + 60, 0, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ZeroGravityConfig.NAUSEA_OVERLOAD_TICKS, 0, false, true, true));
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.quirk.overloaded.zero_gravity")
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		sound(p, SoundEvents.PLAYER_BURP, 1.0f, 0.6f);
		session(p).warned = false;
	}

	/** Everything ends: floats drop, her own float and the leap end, the rubble crumbles, the combo stops. */
	public static void endAll(ServerPlayer p) {
		ZeroGravityFloat.releaseAll(p);
		ZeroGravityFloat.stopSelf(p);
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
		}
		ZeroGravityDebris.dropAll(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.slams.clear();
			s.comboTarget = -1;
			s.leaping = false;
		}
		if (Quirks.hasPower(p)) {
			Quirks.edit(p, n -> {
				n.vals.remove(ZeroGravity.V_SELF);
				n.vals.remove(ZeroGravity.V_LEAP);
				n.vals.remove(ZeroGravity.V_FLOATING);
				n.vals.remove(ZeroGravity.V_SHIELD);
				n.vals.remove(ZeroGravity.V_SHOWER);
				n.vals.remove(ZeroGravity.V_METEOR);
				n.vals.remove(ZeroGravity.V_COMBO);
			});
		}
	}
}
