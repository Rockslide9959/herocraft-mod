package com.projecthero.mod.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.1: the Speed Force Speedster's moves (see {@link SpeedForceAbilityManager} for the keys: tap / Shift / hold). The
 * plain Speedster's moves are borrowed on tap and Shift (Rapid Assault, Mach Punch, Blitz, Momentum Dash, Speed Sweep,
 * Afterimage Decoys -- their code lives in {@code SuperSpeedMoves} / {@code SuperSpeedHandlers}, on this power's own
 * cooldowns); the Speed Force moves are the holds. Numbers in {@link SpeedForceConfig}.
 */
public final class SpeedForceAbilities {
	// R
	public static final String RAPID = "rapid_assault";
	public static final String MACH = "mach_punch";
	public static final String MASS = "infinite_mass_punch";
	// G
	public static final String BLITZ = "blitz";
	public static final String TORNADO = "speed_tornado"; // Shift+G Speed Vortex
	public static final String THROW = "lightning_throw";
	// X
	public static final String DASH = "momentum_dash";
	public static final String SWEEP = "speed_sweep";
	public static final String ARMS = "tornado_arms";
	// Z
	public static final String FLASH = "flash_time";
	public static final String REMNANT = "time_remnant";
	public static final String PORTAL = "speed_force_portal";
	// V
	public static final String DECOYS = "afterimage_decoys";
	public static final String HEAL = "speed_heal";
	public static final String REWIND = "rewind";
	// C
	public static final String SPEED_MODE = "speed_mode";
	public static final String PHASE = "phase";
	public static final String LAUNCH = "speed_launch";
	// N
	public static final String RESCUE = "rescue_run";

	/** Runner -> the Time Remnant's entity id. */
	private static final Map<UUID, Integer> REMNANTS = new ConcurrentHashMap<>();
	/** Set down from a Rescue Run -> game time the fall / suffocation protection ends. */
	static final Map<UUID, Long> RESCUE_GRACE = new ConcurrentHashMap<>();
	/** Hold R: the runner's tick count when the wind-up began. */
	private static final Map<UUID, Integer> MASS_WINDUP = new ConcurrentHashMap<>();
	/** Hold G: the runner's tick count when the charge began. */
	private static final Map<UUID, Integer> THROW_CHARGE = new ConcurrentHashMap<>();

	/** A hold move nobody let go of (a lost release packet) gives up after this long. */
	private static final int HOLD_FAILSAFE = 10 * 20;

	private SpeedForceAbilities() {
	}

	static void clearSessionState() {
		REMNANTS.clear();
		RESCUE_GRACE.clear();
		MASS_WINDUP.clear();
		THROW_CHARGE.clear();
	}

	// ---------------------------------------------------------------- shared

	static boolean ready(ServerPlayer p, String id) {
		return SpeedForceSpeedster.cooldownRemaining(p, id) <= 0;
	}

	public static void cooldown(ServerPlayer p, String id, int ticks) {
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + HeroConfig.get().scaledCooldown(ticks));
		SpeedForceSpeedster.save(p, n);
	}

	private static boolean gate(ServerPlayer p, String id) {
		if (!SpeedForceSpeedster.canAct(p)) {
			return false;
		}
		int cd = SpeedForceSpeedster.cooldownRemaining(p, id);
		if (cd > 0) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.speed_force_speedster.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	private static boolean pay(ServerPlayer p, float cost) {
		if (SpeedForceSpeedster.spendMeter(p, cost)) {
			return true;
		}
		lowMeter(p, cost);
		return false;
	}

	private static boolean has(ServerPlayer p, float need) {
		if (SpeedForceSpeedster.state(p).meter + 1.0e-3f >= need) {
			return true;
		}
		lowMeter(p, need);
		return false;
	}

	private static void lowMeter(ServerPlayer p, float need) {
		SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.low_meter", ChatFormatting.GOLD,
				String.format(java.util.Locale.ROOT, "%.0f", need));
	}

	private static float m(ServerPlayer p) {
		return SpeedForceSpeedster.state(p).momentum;
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** The runner's lightning colour as a dust particle (tone 1, the bolt's body). */
	static DustParticleOptions dust(ServerPlayer p, float size) {
		int rgb = SpeedForceColours.tone(SpeedForceColours.of(p), 1);
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	/** A hold move's pose: held until {@link #clearHoldAnim} (the key let go). */
	private static void setHoldAnim(ServerPlayer p, int anim) {
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.animId = anim;
		n.animStart = p.level().getGameTime();
		n.holdAnim = true;
		SpeedForceSpeedster.save(p, n);
	}

	private static void clearHoldAnim(ServerPlayer p, int anim) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.holdAnim && s.animId == anim) {
			SpeedForceState n = s.copy();
			n.animId = SpeedForceState.ANIM_NONE;
			n.holdAnim = false;
			SpeedForceSpeedster.save(p, n);
		}
	}

	private static boolean holding(Player p, int anim) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		return s != null && s.hasPower && s.holdAnim && s.animId == anim;
	}

	// ---------------------------------------------------------------- R: Rapid Assault / Shift+R Mach Punch / hold R Infinite Mass Punch

	/** Tap R: the plain Speedster's Rapid Assault -- four punches of 5 on everything in front, each landing in full. */
	public static int rapidAssault(ServerPlayer p) {
		if (!gate(p, RAPID)) {
			return 0;
		}
		int struck = SuperSpeedHandlers.rapidAssaultCore(p);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_PUNCHES);
		cooldown(p, RAPID, SpeedForceConfig.RAPID_COOLDOWN);
		return struck;
	}

	/** Shift+R: the plain Speedster's Mach Punch (a flat 20 and a shockwave), the knockback growing with the momentum. */
	public static boolean machPunch(ServerPlayer p) {
		if (!gate(p, MACH)) {
			return false;
		}
		SuperSpeedMoves.machPunchCore(p, m(p));
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_MASS);
		cooldown(p, MACH, SpeedForceConfig.MACH_COOLDOWN);
		return true;
	}

	/** Hold R: wind the punch up (the fist cocked back, lightning gathering in it); let go to throw it. */
	public static boolean startMassWindup(ServerPlayer p) {
		if (MASS_WINDUP.containsKey(p.getUUID()) || !gate(p, MASS) || !has(p, SpeedForceConfig.MASS_COST)) {
			return false;
		}
		MASS_WINDUP.put(p.getUUID(), p.tickCount);
		setHoldAnim(p, SpeedForceState.ANIM_MASS_WINDUP);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.4f);
		return true;
	}

	public static boolean massWindingUp(Player p) {
		return MASS_WINDUP.containsKey(p.getUUID());
	}

	/** R let go after the wind-up: the punch locks on and goes. */
	public static boolean releaseMass(ServerPlayer p) {
		if (MASS_WINDUP.remove(p.getUUID()) == null) {
			return false;
		}
		clearHoldAnim(p, SpeedForceState.ANIM_MASS_WINDUP);
		return infiniteMassPunch(p);
	}

	/**
	 * v0.17.1 (user: "make the infinite mass punch auto track to the nearest enemy so that its easier to land if running at fast
	 * speeds"): who the punch locks onto -- the nearest foe within {@link SpeedForceConfig#MASS_TRACK_RANGE} that is under the
	 * crosshair (the look cone) and in sight, else the nearest one in sight at all. Null if nothing is in range.
	 */
	public static LivingEntity massTarget(ServerPlayer p) {
		double range = SpeedForceConfig.MASS_TRACK_RANGE;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity inCone = null;
		double coneD = Double.MAX_VALUE;
		LivingEntity any = null;
		double anyD = Double.MAX_VALUE;
		for (LivingEntity e : SpeedForceCombat.within(p, p.position().add(0, 1.0, 0), range + 1.0)) {
			Vec3 c = center(e);
			double d = c.distanceToSqr(eye);
			if (d > (range + 0.5) * (range + 0.5) || !p.hasLineOfSight(e)) {
				continue;
			}
			Vec3 to = c.subtract(eye);
			double cos = to.lengthSqr() < 1.0e-6 ? 1.0 : to.normalize().dot(look);
			if (cos >= SpeedForceConfig.MASS_TRACK_CONE && d < coneD) {
				coneD = d;
				inCone = e;
			}
			if (d < anyD) {
				anyD = d;
				any = e;
			}
		}
		return inCone != null ? inCone : any;
	}

	/**
	 * Every bit of momentum into one punch: it locks onto {@link #massTarget}, zips you up against it and lands 10 + up to 60
	 * (the boss cap holds), a huge knockback and a shockwave round it. Spends the run.
	 */
	public static boolean infiniteMassPunch(ServerPlayer p) {
		if (!gate(p, MASS)) {
			return false;
		}
		LivingEntity target = massTarget(p);
		if (target == null) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!pay(p, SpeedForceConfig.MASS_COST)) {
			return false;
		}
		float mom = m(p);
		// the zip: right up against it, facing it (a free spot beside it; where you stand if there is none)
		Vec3 from = p.position();
		Vec3 spot = SuperSpeedMoves.strikeSpot(p, target, from);
		if (spot != null && spot.distanceToSqr(from) > 1.0) {
			Vec3 c = center(target);
			double dx = c.x - spot.x;
			double dz = c.z - spot.z;
			float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
			SpeedForceSpeedster.graceMotion(p, 3);
			p.teleportTo(level(p), spot.x, spot.y, spot.z, yaw, p.getXRot());
			p.setYHeadRot(yaw);
			p.resetFallDistance();
			p.hurtMarked = true;
			SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, spot);
		}
		float dmg = SpeedForceConfig.MASS_MIN + SpeedForceConfig.MASS_MOMENTUM * mom;
		Vec3 c = center(target);
		SpeedForceCombat.strike(p, target, p.position(), dmg, 1.0 + 4.0 * mom, 0.4 + 0.6 * mom);
		float shock = SpeedForceConfig.MASS_SHOCK_MIN + SpeedForceConfig.MASS_SHOCK_MOMENTUM * mom;
		for (LivingEntity e : SpeedForceCombat.within(p, c, SpeedForceConfig.MASS_SHOCK_RADIUS)) {
			if (e != target) {
				SpeedForceCombat.strike(p, e, c, shock, 1.2, 0.3);
			}
		}
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0);
		level.sendParticles(dust(p, 2.2f), c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.5);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOOM, c, c);
		SpeedForceSpeedster.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.2f);
		SpeedForceSpeedster.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.1f);
		SpeedForceSpeedster.setMomentum(p, 0f);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_MASS);
		cooldown(p, MASS, SpeedForceConfig.MASS_COOLDOWN);
		return true;
	}

	private static void massTick(ServerPlayer p) {
		Integer start = MASS_WINDUP.get(p.getUUID());
		if (start == null) {
			return;
		}
		int age = p.tickCount - start;
		if (age > HOLD_FAILSAFE || age < 0) {
			releaseMass(p);
			return;
		}
		if (age % 2 == 0) {
			// lightning gathering in the cocked fist (behind the right shoulder)
			Vec3 f = flatLook(p);
			Vec3 right = new Vec3(-f.z, 0, f.x);
			Vec3 fist = p.position().add(0, 1.3, 0).add(right.scale(0.45)).subtract(f.scale(0.45));
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 2 + Math.min(6, age / 6), 0.12, 0.12, 0.12, 0.05);
			level(p).sendParticles(dust(p, 1.0f), fist.x, fist.y, fist.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		if (age % 10 == 0) {
			SpeedForceSpeedster.sound(p, SoundEvents.ELYTRA_FLYING, 0.2f, 1.9f);
		}
	}

	// ---------------------------------------------------------------- G: Blitz / Shift+G Speed Vortex / hold G Lightning Throw

	/** Tap G: the plain Speedster's Blitz -- zip to the foe under the crosshair (24 blocks) and hit it for 20, a shockwave round it. */
	public static boolean blitz(ServerPlayer p) {
		if (!gate(p, BLITZ)) {
			return false;
		}
		SpeedForceSpeedster.graceMotion(p, 3);
		if (!SuperSpeedMoves.blitzCore(p)) {
			return false;
		}
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_BLITZ);
		cooldown(p, BLITZ, SpeedForceConfig.BLITZ_COOLDOWN);
		return true;
	}

	/** Hold G: lightning builds in the raised hand while held; let go to throw it (the longer the charge, the harder). */
	public static boolean startThrowCharge(ServerPlayer p) {
		if (THROW_CHARGE.containsKey(p.getUUID()) || !gate(p, THROW) || !has(p, SpeedForceConfig.THROW_COST)) {
			return false;
		}
		THROW_CHARGE.put(p.getUUID(), p.tickCount);
		setHoldAnim(p, SpeedForceState.ANIM_THROW_CHARGE);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.9f);
		return true;
	}

	/** G let go: the bolt goes, +up to 50% with the charge. */
	public static boolean releaseThrow(ServerPlayer p) {
		Integer start = THROW_CHARGE.remove(p.getUUID());
		if (start == null) {
			return false;
		}
		clearHoldAnim(p, SpeedForceState.ANIM_THROW_CHARGE);
		if (!pay(p, SpeedForceConfig.THROW_COST)) {
			return false;
		}
		float charge = Mth.clamp((p.tickCount - start) / (float) SpeedForceConfig.THROW_CHARGE_TICKS, 0f, 1f);
		fireBolt(p, charge);
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.animId = SpeedForceState.ANIM_THROW;
		n.animStart = p.level().getGameTime() - 7; // straight into the release of the throw
		SpeedForceSpeedster.save(p, n);
		cooldown(p, THROW, SpeedForceConfig.THROW_COOLDOWN);
		return true;
	}

	private static void throwTick(ServerPlayer p) {
		Integer start = THROW_CHARGE.get(p.getUUID());
		if (start == null) {
			return;
		}
		int age = p.tickCount - start;
		if (age > HOLD_FAILSAFE || age < 0) {
			releaseThrow(p);
			return;
		}
		float t = Math.min(1f, age / (float) SpeedForceConfig.THROW_CHARGE_TICKS);
		double a = age * 1.1;
		Vec3 f = flatLook(p);
		Vec3 right = new Vec3(-f.z, 0, f.x);
		Vec3 hand = p.position().add(0, 2.0, 0).add(right.scale(0.35));
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x + Math.cos(a) * 0.3, hand.y + Math.sin(a * 0.7) * 0.2, hand.z + Math.sin(a) * 0.3,
				2 + Math.round(t * 4), 0.05, 0.05, 0.05, 0.02);
		if (age == SpeedForceConfig.THROW_CHARGE_TICKS) {
			SpeedForceSpeedster.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
		}
	}

	/** The bolt itself at no charge (public for the gametests). */
	public static LivingEntity fireBolt(ServerPlayer p) {
		return fireBolt(p, 0f);
	}

	/** A bolt of Speed Force lightning (in the runner's colour): 8 to 20 with the momentum, x(1 + 0.5 x charge), chaining on. */
	public static LivingEntity fireBolt(ServerPlayer p, float charge) {
		ServerLevel level = level(p);
		float mom = m(p);
		float dmg = (SpeedForceConfig.THROW_MIN + SpeedForceConfig.THROW_MOMENTUM * mom) * (1f + SpeedForceConfig.THROW_CHARGE_BONUS * charge);
		LivingEntity target = SuperSpeedMoves.aimTarget(p, SpeedForceConfig.THROW_RANGE);
		if (target != null && !SpeedForceCombat.isTarget(p, target)) {
			target = null;
		}
		Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.6)).subtract(0, 0.3, 0);
		Vec3 end = target != null ? center(target) : AbilityHelpers.aimPoint(p, SpeedForceConfig.THROW_RANGE);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOLT, hand, end);
		SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1f, 1.7f);
		SpeedForceSpeedster.sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.9f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 20, 0.4, 0.4, 0.4, 0.2);
		if (target == null) {
			return null;
		}
		SpeedForceCombat.strike(p, target, hand, dmg, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		List<LivingEntity> hit = new ArrayList<>();
		hit.add(target);
		Vec3 from = end;
		for (int i = 0; i < SpeedForceConfig.THROW_CHAINS; i++) {
			LivingEntity next = SpeedForceCombat.nearest(p, from, SpeedForceConfig.THROW_CHAIN_RADIUS, hit);
			if (next == null) {
				break;
			}
			Vec3 to = center(next);
			SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOLT, from, to);
			SpeedForceCombat.strike(p, next, from, dmg * 0.5f, 0.2, 0.0);
			hit.add(next);
			from = to;
		}
		return target;
	}

	/** Shift+G Speed Vortex: run tight circles -- a cyclone that drags everything within 7 blocks round and up, then flings it out. */
	public static boolean speedTornado(ServerPlayer p) {
		if (SpeedForceSpeedster.state(p).tornadoLeft > 0 || !gate(p, TORNADO) || !pay(p, SpeedForceConfig.TORNADO_COST)) {
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.tornadoLeft = SpeedForceConfig.TORNADO_TICKS;
		n.animId = SpeedForceState.ANIM_TORNADO;
		n.animStart = p.level().getGameTime();
		n.holdAnim = false;
		SpeedForceSpeedster.save(p, n);
		cooldown(p, TORNADO, SpeedForceConfig.TORNADO_COOLDOWN);
		SpeedForceSpeedster.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 0.6f);
		SpeedForceSpeedster.sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.6f);
		return true;
	}

	private static void tornadoTick(ServerPlayer p, SpeedForceState s) {
		if (s.tornadoLeft <= 0) {
			return;
		}
		int left = s.tornadoLeft - 1;
		SpeedForceState n = s.copy();
		n.tornadoLeft = left;
		if (left == 0 && n.animId == SpeedForceState.ANIM_TORNADO) {
			n.animId = SpeedForceState.ANIM_NONE;
		}
		SpeedForceSpeedster.save(p, n);
		ServerLevel level = level(p);
		Vec3 c = p.position();
		float mom = s.momentum;
		List<LivingEntity> caught = SpeedForceCombat.within(p, c.add(0, 1, 0), SpeedForceConfig.TORNADO_RADIUS);
		if (left > 0) {
			for (LivingEntity e : caught) {
				if (!SpeedForceCombat.isBoss(e)) {
					Vec3 to = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
					double dist = Math.max(0.1, to.length());
					Vec3 in = to.scale(1.0 / dist);
					Vec3 around = new Vec3(-in.z, 0, in.x);
					double pull = dist > 3.0 ? 0.28 : -0.08;
					double lift = e.getY() < c.y + 3.5 ? 0.16 : -0.02;
					e.setDeltaMovement(in.scale(pull).add(around.scale(0.5)).add(0, lift, 0));
					e.hurtMarked = true;
				}
				if (left % 10 == 0) {
					SpeedForceCombat.strike(p, e, c, SpeedForceConfig.TORNADO_TICK_MIN + SpeedForceConfig.TORNADO_TICK_MOMENTUM * mom, 0.0, 0.0);
				}
			}
			double t = (SpeedForceConfig.TORNADO_TICKS - left) * 0.6;
			for (int arm = 0; arm < 3; arm++) {
				for (int k = 0; k < 7; k++) {
					double h = k * 0.7;
					double r = 1.4 + h * 0.6;
					double a = t + arm * Math.PI * 2 / 3 + k * 0.45;
					level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.01);
				}
			}
			if (left % 2 == 0) {
				double r = 2.0;
				Vec3 from = c.add(Math.cos(t) * r, 0, Math.sin(t) * r);
				Vec3 to = c.add(Math.cos(t + 1.4) * r, 0, Math.sin(t + 1.4) * r);
				SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, to);
				level.sendParticles(dust(p, 1.2f), from.x, from.y + 1.0, from.z, 3, 0.1, 0.4, 0.1, 0.0);
			}
			if (left % 12 == 0) {
				SpeedForceSpeedster.sound(p, SoundEvents.ELYTRA_FLYING, 0.25f, 1.9f);
			}
			return;
		}
		for (LivingEntity e : caught) {
			Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			out = out.lengthSqr() < 1.0e-4 ? flatLook(p) : out.normalize();
			SpeedForceCombat.strike(p, e, c, SpeedForceConfig.TORNADO_FINAL_MIN + SpeedForceConfig.TORNADO_FINAL_MOMENTUM * mom, 0.0, 0.0);
			if (!SpeedForceCombat.isBoss(e)) {
				AbilityHelpers.push(e, out.scale(1.6).add(0, 0.9, 0));
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 3, 1.2, 0.6, 1.2, 0);
		SpeedForceSpeedster.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.3f);
	}

	// ---------------------------------------------------------------- X: Momentum Dash / Shift+X Speed Sweep / hold X Tornado Arms

	/** Tap X: the plain Speedster's Momentum Dash along the full look -- and +15% momentum, to get a run going. */
	public static boolean momentumDash(ServerPlayer p) {
		if (!gate(p, DASH) || !pay(p, SpeedForceConfig.DASH_COST)) {
			return false;
		}
		Vec3 look = p.getLookAngle().normalize();
		Vec3 v = SuperSpeedHandlers.dashVelocity(look, p.onGround(), SpeedForceConfig.DASH_LAUNCH);
		Vec3 from = p.position();
		SpeedForceSpeedster.graceMotion(p, 4);
		AbilityHelpers.launchSelf(p, v);
		SpeedForceSpeedster.setMomentum(p, (float) (m(p) + SpeedForceConfig.DASH_MOMENTUM_GAIN));
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, from.add(look.scale(4.0)));
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 1.0, from.z, 14, 0.3, 0.6, 0.3, 0.15);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_STEP);
		SpeedForceSpeedster.sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 1.8f);
		cooldown(p, DASH, SpeedForceConfig.DASH_COOLDOWN);
		return true;
	}

	/** Shift+X: the plain Speedster's Speed Sweep -- blink through every foe within 50 blocks (15 each), then home. */
	public static boolean speedSweep(ServerPlayer p) {
		if (SuperSpeedMoves.sweeping(p)) {
			SuperSpeedMoves.endSweep(p);
			return false;
		}
		if (!gate(p, SWEEP)) {
			return false;
		}
		SpeedForceSpeedster.graceMotion(p, 3);
		if (!SuperSpeedMoves.beginSweep(p)) {
			return false;
		}
		cooldown(p, SWEEP, SpeedForceConfig.SWEEP_COOLDOWN);
		return true;
	}

	/** Hold X: the arms spin into a whirlwind in front (Speed Force a second) -- see {@link #armsTick}. */
	public static boolean startTornadoArms(ServerPlayer p) {
		if (tornadoArms(p) || !gate(p, ARMS) || !has(p, SpeedForceConfig.ARMS_MIN)) {
			return false;
		}
		setHoldAnim(p, SpeedForceState.ANIM_ARMS);
		SpeedForceSpeedster.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0f, 0.8f);
		SpeedForceSpeedster.sound(p, SoundEvents.ELYTRA_FLYING, 0.6f, 1.5f);
		return true;
	}

	/** X let go (or the Speed Force ran out): the wind drops. */
	public static void endTornadoArms(ServerPlayer p) {
		if (!tornadoArms(p)) {
			return;
		}
		clearHoldAnim(p, SpeedForceState.ANIM_ARMS);
		SpeedForceSpeedster.sound(p, SoundEvents.BREEZE_DEFLECT, 0.7f, 1.0f);
		cooldown(p, ARMS, SpeedForceConfig.ARMS_COOLDOWN);
	}

	/** Whether Tornado Arms is running (client-safe: the pose and the wind are drawn from it). */
	public static boolean tornadoArms(Player p) {
		return holding(p, SpeedForceState.ANIM_ARMS);
	}

	/** Whether {@code at} is inside the Tornado Arms cone of {@code p} (eye-level apex, along the look). */
	static boolean inArmsCone(ServerPlayer p, Vec3 at) {
		Vec3 apex = p.getEyePosition().subtract(0, 0.5, 0);
		Vec3 to = at.subtract(apex);
		double d = to.length();
		if (d < 0.3) {
			return true;
		}
		return d <= SpeedForceConfig.ARMS_RANGE && to.scale(1.0 / d).dot(p.getLookAngle()) >= SpeedForceConfig.ARMS_CONE;
	}

	/**
	 * v0.17.1 (user): "The Flash spins his arms into a whirlwind in front of him" -- one tick of it: everything in the cone
	 * (10 blocks long) is pushed back and lifted (bosses only slowed), every projectile in it is blown away, fire in it goes
	 * out (burning blocks and burning creatures); wind and gusts down the cone. Costs Speed Force a second.
	 */
	public static void armsTick(ServerPlayer p) {
		if (!tornadoArms(p)) {
			return;
		}
		if (!p.isAlive() || (p.tickCount % 5 == 0 && !SpeedForceSpeedster.spendMeter(p, SpeedForceConfig.ARMS_COST_PER_SECOND / 4f))) {
			if (p.isAlive()) {
				lowMeter(p, SpeedForceConfig.ARMS_MIN);
			}
			endTornadoArms(p);
			return;
		}
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 apex = p.getEyePosition().subtract(0, 0.5, 0);
		double r = SpeedForceConfig.ARMS_RANGE;
		AABB box = new AABB(apex, apex).inflate(r + 1.0);
		for (LivingEntity e : SpeedForceCombat.within(p, apex, r + 1.0)) {
			if (!inArmsCone(p, center(e))) {
				continue;
			}
			if (SpeedForceCombat.isBoss(e)) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.5));
			} else {
				Vec3 away = center(e).subtract(apex);
				Vec3 push = (away.lengthSqr() < 1.0e-4 ? look : away.normalize()).add(look).normalize();
				e.setDeltaMovement(push.x * SpeedForceConfig.ARMS_PUSH, Math.max(e.getDeltaMovement().y, SpeedForceConfig.ARMS_LIFT), push.z * SpeedForceConfig.ARMS_PUSH);
				e.resetFallDistance();
			}
			e.hurtMarked = true;
			if (e.isOnFire()) {
				e.clearFire();
			}
		}
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, e -> e.getOwner() != p)) {
			if (!inArmsCone(p, pr.position())) {
				continue;
			}
			double speed = Math.max(0.8, pr.getDeltaMovement().length());
			pr.setDeltaMovement(look.scale(speed).add(0, 0.3, 0));
			pr.hurtMarked = true;
			if (pr instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile hp) {
				hp.accelerationPower = 0.1;
			}
			level.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
		}
		// fire in the cone is blown out (every few ticks: a box of block checks)
		if (p.tickCount % 4 == 0) {
			BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
			int ir = (int) Math.ceil(r);
			BlockPos c = BlockPos.containing(apex);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dy = -3; dy <= 3; dy++) {
					for (int dz = -ir; dz <= ir; dz++) {
						mp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
						if (level.getBlockState(mp).is(BlockTags.FIRE) && inArmsCone(p, Vec3.atCenterOf(mp))) {
							level.setBlockAndUpdate(mp, Blocks.AIR.defaultBlockState());
							level.sendParticles(ParticleTypes.SMOKE, mp.getX() + 0.5, mp.getY() + 0.3, mp.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
						}
					}
				}
			}
		}
		// the wind itself: gusts and spinning cloud down the cone
		double t = p.tickCount * 0.9;
		Vec3 side = new Vec3(-look.z, 0, look.x);
		if (side.lengthSqr() < 1.0e-4) {
			side = new Vec3(1, 0, 0);
		}
		side = side.normalize();
		Vec3 up = side.cross(look).normalize();
		for (int k = 0; k < 6; k++) {
			double s = 1.0 + (k + (p.tickCount % 3) / 3.0) * (r - 1.0) / 6.0;
			double rad = 0.3 + s * 0.28;
			double a = t + k * 1.1;
			Vec3 at = apex.add(look.scale(s)).add(side.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, look.x * 0.6, look.y * 0.6, look.z * 0.6, 1.0);
		}
		if (p.tickCount % 3 == 0) {
			Vec3 at = apex.add(look.scale(2.5 + (p.tickCount % 9) * 0.6));
			level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.0);
		}
		if (p.tickCount % 10 == 0) {
			SpeedForceSpeedster.sound(p, SoundEvents.ELYTRA_FLYING, 0.35f, 1.8f);
		}
	}

	// ---------------------------------------------------------------- Z: hold Flash Time (and the portal) / Shift+Z Time Remnant

	/** Whether Z is holding Flash Time right now (client-safe). */
	public static boolean flashHeld(Player p) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		return s != null && s.hasPower && s.flashLeft > 0;
	}

	/**
	 * v0.17.1 (user: "make flash time a held down ability that drains speed force while active"): Z down -- time slows to a
	 * crawl for everything but you (the game-wide slow the plain Speedster's Time Slow uses) for as long as Z is held,
	 * draining {@link SpeedForceConfig#FLASH_COST_PER_SECOND} Speed Force a second on your own clock. Ends on release or when
	 * the meter is empty.
	 */
	public static boolean startFlashTime(ServerPlayer p) {
		if (SuperSpeedTimeSlow.isCasting(p) || !gate(p, FLASH)) {
			return false;
		}
		if (SuperSpeedTimeSlow.anyActive()) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed.time_taken"), true);
			return false;
		}
		if (!has(p, SpeedForceConfig.FLASH_MIN)) {
			return false;
		}
		SuperSpeedTimeSlow.start(p, SpeedForceConfig.FLASH_TIME_MAX);
		if (!SuperSpeedTimeSlow.isCasting(p)) {
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.flashLeft = 1;
		n.flashTotal = 0;
		SpeedForceSpeedster.save(p, n);
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(dust(p, 2.2f), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
		return true;
	}

	/** Z let go (or the meter is empty): time runs again; any portal still forming collapses. */
	public static void endFlashTime(ServerPlayer p) {
		collapsePortal(p);
		if (SuperSpeedTimeSlow.isCasting(p)) {
			SuperSpeedTimeSlow.end(p, true); // -> onFlashTimeEnded
		} else if (flashHeld(p)) {
			onFlashTimeEnded(p);
		}
	}

	/** Flash Time is over (from {@code SuperSpeedTimeSlow}, or the release): the short cooldown starts now. */
	public static void onFlashTimeEnded(ServerPlayer p) {
		if (!SpeedForceSpeedster.hasPower(p)) {
			return;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		boolean was = n.flashLeft > 0;
		n.flashLeft = 0;
		n.flashTotal = 0;
		if (was) {
			n.abilityReadyAt.put(FLASH, p.level().getGameTime() + HeroConfig.get().scaledCooldown(SpeedForceConfig.FLASH_TIME_COOLDOWN));
		}
		SpeedForceSpeedster.save(p, n);
	}

	/** The portal's charge so far, in caster ticks (0 = none forming). */
	public static int portalCharge(Player p) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		return s == null || !s.hasPower || s.flashLeft <= 0 ? 0 : s.flashTotal;
	}

	/** Where the portal forms: in front of the runner along the run, at chest height. */
	static Vec3 portalCentre(ServerPlayer p) {
		return p.position().add(flatLook(p).scale(SpeedForceConfig.PORTAL_DISTANCE)).add(0, 1.2, 0);
	}

	private static void collapsePortal(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.flashTotal <= 0) {
			return;
		}
		SpeedForceState n = s.copy();
		n.flashTotal = 0;
		SpeedForceSpeedster.save(p, n);
		Vec3 c = portalCentre(p);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.PORTAL_COLLAPSE, c, c);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.6, 0.6, 0.6, 0.2);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
		SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.portal_collapsed", ChatFormatting.GRAY);
	}

	/**
	 * One caster tick of a held Flash Time: the drain, and -- v0.17.1 (user: "holding z for 5 seconds while running at max
	 * momentum open up the speed force ... the portal should start forming in front of them before finally allowing them in")
	 * -- the Speed Force portal: running flat out at 100% momentum (or locked there by the charge), the portal grows in front
	 * of you over 5 s, a ring of your lightning widening, arcs crackling off it and sparks drawn in; at 5 s it takes you in.
	 * Slowing below max, stopping or letting go collapses it.
	 */
	private static void flashTick(ServerPlayer p) {
		if (!flashHeld(p)) {
			return;
		}
		if (!SuperSpeedTimeSlow.isCasting(p) || !p.isAlive()) {
			endFlashTime(p);
			return;
		}
		if (p.tickCount % 5 == 0 && !SpeedForceSpeedster.spendMeter(p, SpeedForceConfig.FLASH_COST_PER_SECOND / 4f)) {
			lowMeter(p, SpeedForceConfig.FLASH_MIN);
			endFlashTime(p);
			return;
		}
		SpeedForceState s = SpeedForceSpeedster.state(p);
		boolean canOpen = SpeedForceSpeedster.atMaxRun(p) && ready(p, PORTAL) && s.drained <= 0 && !SpeedForceRealm.inside(p);
		if (!canOpen) {
			collapsePortal(p);
			return;
		}
		int charge = s.flashTotal + 1;
		SpeedForceState n = s.copy();
		n.flashTotal = charge;
		SpeedForceSpeedster.save(p, n);
		float t = Math.min(1f, charge / (float) SpeedForceConfig.PORTAL_TICKS);
		Vec3 c = portalCentre(p);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.PORTAL, c, new Vec3(t, p.getYRot(), 0));
		// sparks and the runner's lightning drawn in to it
		ServerLevel level = level(p);
		double radius = 0.3 + SpeedForceConfig.PORTAL_RADIUS * t;
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		for (int k = 0; k < 2 + Math.round(t * 4); k++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double out = radius + 1.5 + level.random.nextDouble() * 2.0;
			Vec3 at = c.add(side.scale(Math.cos(a) * out)).add(0, Math.sin(a) * out, 0);
			Vec3 in = c.subtract(at).normalize();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, in.x, in.y, in.z, 0.45);
		}
		if (charge % 10 == 0) {
			SpeedForceSpeedster.sound(p, SoundEvents.BEACON_AMBIENT, 0.6f + 0.6f * t, 0.6f + 1.2f * t);
		}
		if (charge % 25 == 0) {
			SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f + 0.4f * t, 1.6f);
		}
		if (charge >= SpeedForceConfig.PORTAL_TICKS) {
			SpeedForceSpeedster.fx(p, SpeedForceFxPayload.PORTAL_ENTER, c, c);
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
			SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 1.4f);
			SpeedForceState m = SpeedForceSpeedster.state(p).copy();
			m.flashTotal = 0;
			SpeedForceSpeedster.save(p, m);
			SpeedForceRealm.enter(p);
		}
	}

	/** A copy of you from a second ago runs out of the Speed Force and fights beside you for 8 s. */
	public static MirrorImageEntity timeRemnant(ServerPlayer p) {
		if (!gate(p, REMNANT)) {
			return null;
		}
		ServerLevel level = level(p);
		SpeedForceSpeedster.Snap then = SpeedForceSpeedster.history(p, SpeedForceConfig.REMNANT_LOOKBACK);
		Vec3 at = then != null ? then.pos() : p.position().subtract(flatLook(p).scale(1.5));
		MirrorImageEntity img = BatchDContent.MIRROR_IMAGE.create(level);
		if (img == null) {
			return null;
		}
		if (!level.noCollision(img, img.getType().getDimensions().makeBoundingBox(at))) {
			at = p.position();
		}
		if (!pay(p, SpeedForceConfig.REMNANT_COST)) {
			return null;
		}
		killRemnant(p);
		img.moveTo(at.x, at.y, at.z, then != null ? then.yRot() : p.getYRot(), 0.0f);
		img.setYHeadRot(img.getYRot());
		img.copyFrom(p);
		img.makeSpeedster(SpeedForceConfig.REMNANT_TICKS);
		level.addFreshEntity(img);
		REMNANTS.put(p.getUUID(), img.getId());
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.remnantUntil = p.level().getGameTime() + SpeedForceConfig.REMNANT_TICKS;
		SpeedForceSpeedster.save(p, n);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, p.position(), at);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 20, 0.3, 0.6, 0.3, 0.1);
		level.sendParticles(dust(p, 2.2f), at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.0);
		SpeedForceSpeedster.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.5f);
		cooldown(p, REMNANT, SpeedForceConfig.REMNANT_COOLDOWN);
		return img;
	}

	private static MirrorImageEntity remnant(ServerPlayer p) {
		Integer id = REMNANTS.get(p.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = p.level().getEntity(id);
		return e instanceof MirrorImageEntity img && img.isAlive() ? img : null;
	}

	private static void killRemnant(ServerPlayer p) {
		MirrorImageEntity img = remnant(p);
		if (img != null) {
			img.shatter(null);
		}
		REMNANTS.remove(p.getUUID());
	}

	private static void remnantTick(ServerPlayer p, SpeedForceState s, long now) {
		if (s.remnantUntil == 0L) {
			return;
		}
		MirrorImageEntity img = remnant(p);
		if (img == null || now >= s.remnantUntil) {
			killRemnant(p);
			SpeedForceState n = SpeedForceSpeedster.state(p).copy();
			n.remnantUntil = 0L;
			SpeedForceSpeedster.save(p, n);
			return;
		}
		if ((s.remnantUntil - now) % SpeedForceConfig.REMNANT_HIT_EVERY != 0) {
			return;
		}
		LivingEntity foe = SpeedForceCombat.nearest(p, img.position(), SpeedForceConfig.REMNANT_RANGE, null);
		if (foe == null || foe instanceof MirrorImageEntity) {
			return;
		}
		Vec3 dir = new Vec3(foe.getX() - img.getX(), 0, foe.getZ() - img.getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? flatLook(p) : dir.normalize();
		Vec3 from = img.position();
		Vec3 spot = foe.position().subtract(dir.scale(foe.getBbWidth() * 0.5 + 0.8));
		if (level(p).noCollision(img, img.getType().getDimensions().makeBoundingBox(spot))) {
			img.teleportTo(spot.x, spot.y, spot.z);
			float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
			img.setYRot(yaw);
			img.setYHeadRot(yaw);
			img.yBodyRot = yaw;
		}
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, img.position());
		SpeedForceCombat.strike(p, foe, img.position(), SpeedForceConfig.REMNANT_DAMAGE, 0.3, 0.0);
		if (foe instanceof Mob mob && mob.getTarget() == p) {
			mob.setTarget(img);
		}
	}

	// ---------------------------------------------------------------- V: Afterimage Decoys / Shift+V Speed Heal / hold V Rewind

	/** Tap V: the plain Speedster's Afterimage Decoys -- three images every foe after you goes for instead, for 6 s. */
	public static boolean decoys(ServerPlayer p) {
		if (!gate(p, DECOYS)) {
			return false;
		}
		if (!SuperSpeedMoves.decoysCore(p)) {
			return false;
		}
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_STEP);
		cooldown(p, DECOYS, SpeedForceConfig.DECOY_COOLDOWN);
		return true;
	}

	/** Shift+V: burn Speed Force to heal fast -- 10 health at once, Regeneration III for 4 s, every harmful effect gone. */
	public static boolean speedHeal(ServerPlayer p) {
		if (!gate(p, HEAL) || !pay(p, SpeedForceConfig.HEAL_COST)) {
			return false;
		}
		p.heal(SpeedForceConfig.HEAL_AMOUNT);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SpeedForceConfig.HEAL_REGEN_TICKS, 2, false, true, true));
		for (MobEffectInstance effect : List.copyOf(p.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				p.removeEffect(effect.getEffect());
			}
		}
		p.clearFire();
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.4, 0.6, 0.4, 0.0);
		level.sendParticles(dust(p, 1.2f), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.8, 0.4, 0.2);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_HEAL);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
		cooldown(p, HEAL, SpeedForceConfig.HEAL_COOLDOWN);
		return true;
	}

	/** Hold V: snap back to where you stood 5 s ago, with the health you had then (never less than now). 60 Speed Force. */
	public static boolean rewind(ServerPlayer p) {
		if (!gate(p, REWIND)) {
			return false;
		}
		SpeedForceSpeedster.Snap then = SpeedForceSpeedster.history(p, SpeedForceConfig.REWIND_TICKS);
		if (then == null) {
			return false;
		}
		if (!pay(p, SpeedForceConfig.REWIND_COST)) {
			return false;
		}
		Vec3 from = p.position();
		SpeedForceSpeedster.graceMotion(p, 3);
		p.teleportTo(p.serverLevel(), then.pos().x, then.pos().y, then.pos().z, then.yRot(), then.xRot());
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		p.hurtMarked = true;
		if (then.health() > p.getHealth()) {
			p.setHealth(Math.min(p.getMaxHealth(), then.health()));
		}
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, then.pos());
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, then.pos().x, then.pos().y + 1.0, then.pos().z, 30, 0.3, 0.6, 0.3, 0.15);
		SpeedForceSpeedster.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.9f);
		cooldown(p, REWIND, SpeedForceConfig.REWIND_COOLDOWN);
		return true;
	}

	// ---------------------------------------------------------------- N: Rescue Run

	/** Grab the ally or mob in front of you and carry it at full speed; N again sets it down. */
	public static boolean rescueRun(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.carryId != 0) {
			Entity e = p.level().getEntity(s.carryId);
			release(p, e instanceof LivingEntity le ? le : null);
			SpeedForceSpeedster.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
			return false;
		}
		if (!gate(p, RESCUE)) {
			return false;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, SpeedForceConfig.RESCUE_RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, p) || target.getVehicle() == p) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (target.isPassenger()) {
			target.stopRiding();
		}
		if (!target.startRiding(p, true)) {
			return false;
		}
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
		target.resetFallDistance();
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.carryId = target.getId();
		n.animId = SpeedForceState.ANIM_GRAB;
		n.animStart = p.level().getGameTime();
		n.holdAnim = false;
		SpeedForceSpeedster.save(p, n);
		SpeedForceSpeedster.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
		cooldown(p, RESCUE, SpeedForceConfig.RESCUE_COOLDOWN);
		return true;
	}

	/** Sets the carried creature down in front (or at your feet) with a short fall / suffocation protection. */
	public static void release(ServerPlayer p, LivingEntity le) {
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.carryId = 0;
		SpeedForceSpeedster.save(p, n);
		if (le != null && le.getVehicle() == p) {
			le.stopRiding();
		}
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
		if (le == null || le.distanceToSqr(p) > 9.0) {
			return;
		}
		Vec3 front = p.position().add(flatLook(p).scale(1.2));
		AABB box = le.getDimensions(le.getPose()).makeBoundingBox(front);
		Vec3 at = p.level().noCollision(le, box) ? front : p.position();
		le.teleportTo(at.x, at.y, at.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.resetFallDistance();
		le.hurtMarked = true;
		RESCUE_GRACE.put(le.getUUID(), p.level().getGameTime() + SpeedForceConfig.RESCUE_GRACE);
	}

	/** Whether {@code e} is carried by a Speed Force Speedster right now, or was set down less than 3 s ago. */
	public static boolean rescued(Entity e) {
		if (e.getVehicle() instanceof ServerPlayer carrier && SpeedForceSpeedster.hasPower(carrier)
				&& SpeedForceSpeedster.state(carrier).carryId == e.getId()) {
			return true;
		}
		Long until = RESCUE_GRACE.get(e.getUUID());
		if (until == null) {
			return false;
		}
		if (until <= e.level().getGameTime()) {
			RESCUE_GRACE.remove(e.getUUID());
			return false;
		}
		return true;
	}

	private static void carryTick(ServerPlayer p, SpeedForceState s) {
		if (s.carryId == 0) {
			return;
		}
		Entity e = p.level().getEntity(s.carryId);
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.getVehicle() != p || !p.isAlive() || s.phasing) {
			release(p, e instanceof LivingEntity l2 ? l2 : null);
			return;
		}
		le.resetFallDistance();
		if (p.tickCount % 20 == 0) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
		}
	}

	// ---------------------------------------------------------------- C: Speed Mode / Shift+C Phase (held) / hold C the charge

	/** v0.17.1 (user: "there needs to be a way to turn it on and off"): tap C turns Speed Mode on (20 b/s base) or off. */
	public static boolean toggleSpeedMode(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.chargeStart != 0L) {
			return false;
		}
		SpeedForceState n = s.copy();
		n.speedMode = !s.speedMode;
		if (!n.speedMode) {
			n.momentum = 0f;
			n.surgeUntil = 0L;
		}
		SpeedForceSpeedster.save(p, n);
		SpeedForceSpeedster.applySpeed(p);
		if (n.speedMode) {
			SpeedForceSpeedster.sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 2.0f);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.15);
		} else {
			SpeedForceSpeedster.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
		}
		p.displayClientMessage(Component.translatable(n.speedMode ? "message.projecthero.speed_force_speedster.speed_on"
				: "message.projecthero.speed_force_speedster.speed_off").withStyle(n.speedMode ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
		return true;
	}

	/** Hold Shift+C: vibrate through walls on your own level (no damage taken, none dealt, no other move). */
	public static boolean startPhase(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.phasing || !gate(p, PHASE)) {
			return false;
		}
		if (s.meter < SpeedForceConfig.PHASE_MIN) {
			lowMeter(p, SpeedForceConfig.PHASE_MIN);
			return false;
		}
		SpeedForceState n = s.copy();
		n.phasing = true;
		SpeedForceSpeedster.save(p, n);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 2.0f);
		return true;
	}

	public static void endPhase(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (!s.phasing) {
			return;
		}
		SpeedForceState n = s.copy();
		n.phasing = false;
		SpeedForceSpeedster.save(p, n);
		SuperSpeedHandlers.settleOutsideBlocks(p);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 2.0f);
		cooldown(p, PHASE, SpeedForceConfig.PHASE_COOLDOWN);
	}

	private static void phaseTick(ServerPlayer p, SpeedForceState s) {
		if (!s.phasing) {
			return;
		}
		if (!p.isAlive()) {
			endPhase(p);
			return;
		}
		if (p.tickCount % 5 == 0 && !SpeedForceSpeedster.spendMeter(p, SpeedForceConfig.PHASE_COST_PER_SECOND / 4f)) {
			endPhase(p);
			return;
		}
		if (p.tickCount % 3 == 0) {
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.6, 0.3, 0.05);
		}
	}

	/** Hold C: drop into the charge stance (rooted, the Speed Force building round you for 3 s). */
	public static boolean startCharge(ServerPlayer p) {
		if (SpeedForceSpeedster.state(p).chargeStart != 0L || !gate(p, LAUNCH)) {
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.chargeStart = p.level().getGameTime();
		n.animId = SpeedForceState.ANIM_CHARGE;
		n.animStart = n.chargeStart;
		n.holdAnim = false;
		SpeedForceSpeedster.save(p, n);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
		return true;
	}

	/** C let go: after the full 3 s, Speed Mode on at full momentum at once; earlier, the charge fizzles. */
	public static boolean releaseCharge(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.chargeStart == 0L) {
			return false;
		}
		long now = p.level().getGameTime();
		boolean full = now - s.chargeStart >= SpeedForceConfig.CHARGE_TICKS;
		SpeedForceState n = s.copy();
		n.chargeStart = 0L;
		n.animId = SpeedForceState.ANIM_NONE;
		if (full) {
			n.speedMode = true;
			n.momentum = 1f;
			n.surgeUntil = Math.max(s.surgeUntil, now + SpeedForceConfig.LAUNCH_HOLD_TICKS);
		}
		SpeedForceSpeedster.save(p, n);
		if (!full) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.charge_cancelled")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		SpeedForceSpeedster.applySpeed(p);
		cooldown(p, LAUNCH, SpeedForceConfig.LAUNCH_COOLDOWN);
		ServerLevel level = level(p);
		Vec3 c = p.position();
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 40, 0.6, 0.6, 0.6, 0.4);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 24, 1.2, 0.05, 1.2, 0.2);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOOM, c.add(0, 1, 0), c.add(0, 1, 0));
		SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 1.6f);
		SpeedForceSpeedster.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.launch").withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** While charging: held in place, lightning gathering, a chime when it is ready. */
	private static void chargeTick(ServerPlayer p, long now) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.chargeStart == 0L) {
			return;
		}
		if (p.isDeadOrDying() || s.phasing) {
			SpeedForceState n = s.copy();
			n.chargeStart = 0L;
			n.animId = SpeedForceState.ANIM_NONE;
			SpeedForceSpeedster.save(p, n);
			return;
		}
		long age = now - s.chargeStart;
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(0, Math.min(0, v.y), 0);
		p.hurtMarked = true;
		ServerLevel level = level(p);
		float t = Math.min(1f, age / (float) SpeedForceConfig.CHARGE_TICKS);
		double a = age * 0.7;
		double r = 1.4 - 0.8 * t;
		level.sendParticles(dust(p, 1.2f), p.getX() + Math.cos(a) * r, p.getY() + 0.2 + (age % 10) * 0.15, p.getZ() + Math.sin(a) * r,
				1 + (int) (t * 3), 0.05, 0.05, 0.05, 0.0);
		if (age % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.8, p.getZ(), 2 + (int) (t * 6), 0.35, 0.5, 0.35, 0.05 + t * 0.1);
		}
		if (age % 10 == 0 && age < SpeedForceConfig.CHARGE_TICKS) {
			SpeedForceSpeedster.sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 0.35f, 0.8f + t);
		}
		if (age == SpeedForceConfig.CHARGE_TICKS) {
			SpeedForceSpeedster.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
			p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.charged").withStyle(ChatFormatting.GOLD), true);
		}
	}

	// ---------------------------------------------------------------- upkeep

	static void tick(ServerPlayer p, long now) {
		chargeTick(p, now);
		SpeedForceAbilityManager.tick(p, now);
		massTick(p);
		throwTick(p);
		armsTick(p);
		flashTick(p);
		if (!SuperSpeedHandlers.owns(p)) {
			SuperSpeedMoves.sweepTick(p); // the borrowed Speed Sweep (a plain Speedster's own tick runs it otherwise)
		}
		tornadoTick(p, SpeedForceSpeedster.state(p));
		phaseTick(p, SpeedForceSpeedster.state(p));
		carryTick(p, SpeedForceSpeedster.state(p));
		remnantTick(p, SpeedForceSpeedster.state(p), now);
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.surgeUntil != 0L && s.surgeUntil <= now) {
			SpeedForceState n = s.copy();
			n.surgeUntil = 0L;
			SpeedForceSpeedster.save(p, n);
		}
		s = SpeedForceSpeedster.state(p);
		if (s.animId != SpeedForceState.ANIM_NONE && !s.holdAnim && s.animId != SpeedForceState.ANIM_TORNADO && now - s.animStart > 30) {
			SpeedForceState n = s.copy();
			n.animId = SpeedForceState.ANIM_NONE;
			SpeedForceSpeedster.save(p, n);
		}
	}

	/** Power lost / death / logout: stop everything running. */
	static void clear(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null) {
			return;
		}
		if (s.carryId != 0) {
			Entity e = p.level().getEntity(s.carryId);
			release(p, e instanceof LivingEntity le ? le : null);
		}
		if (s.phasing || s.holdAnim || s.flashLeft > 0) {
			SpeedForceState n = SpeedForceSpeedster.state(p).copy();
			n.phasing = false;
			n.holdAnim = false;
			n.animId = SpeedForceState.ANIM_NONE;
			n.flashTotal = 0;
			SpeedForceSpeedster.save(p, n);
		}
		MASS_WINDUP.remove(p.getUUID());
		THROW_CHARGE.remove(p.getUUID());
		killRemnant(p);
		if (!SuperSpeedHandlers.owns(p)) {
			SuperSpeedMoves.endSweep(p);
		}
		SuperSpeedTimeSlow.end(p, false); // -> onFlashTimeEnded
		SpeedForceState n = SpeedForceSpeedster.state(p);
		if (n.flashLeft > 0) {
			SpeedForceState c = n.copy();
			c.flashLeft = 0;
			SpeedForceSpeedster.save(p, c);
		}
	}

	static void forget(UUID id) {
		MASS_WINDUP.remove(id);
		THROW_CHARGE.remove(id);
	}
}
