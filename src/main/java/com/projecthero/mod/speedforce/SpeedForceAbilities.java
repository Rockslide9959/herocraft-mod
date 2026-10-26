package com.projecthero.mod.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Speed Force Speedster's twelve moves (see {@link SpeedForceAbilityManager} for the keys). The ordinary
 * moves cost little or nothing; the big ones spend the Speed Force, and the momentum cash-ins (Infinite Mass Punch) spend
 * the whole run for one huge hit. Numbers in {@link SpeedForceConfig}.
 */
public final class SpeedForceAbilities {
	public static final String PUNCHES = "speed_punches";
	public static final String MASS = "infinite_mass_punch";
	public static final String THROW = "lightning_throw";
	public static final String TORNADO = "speed_tornado";
	public static final String STEP = "speed_force_step";
	public static final String PHASE = "phase";
	public static final String FLASH = "flash_time";
	public static final String REMNANT = "time_remnant";
	public static final String RESCUE = "rescue_run";
	public static final String HEAL = "speed_heal";
	public static final String SURGE = "speed_force_surge";
	public static final String REWIND = "rewind";

	/** Runner -> the Time Remnant's entity id. */
	private static final Map<UUID, Integer> REMNANTS = new ConcurrentHashMap<>();
	/** Set down from a Rescue Run -> game time the fall / suffocation protection ends. */
	static final Map<UUID, Long> RESCUE_GRACE = new ConcurrentHashMap<>();

	private SpeedForceAbilities() {
	}

	static void clearSessionState() {
		REMNANTS.clear();
		RESCUE_GRACE.clear();
	}

	// ---------------------------------------------------------------- shared

	static boolean ready(ServerPlayer p, String id) {
		return SpeedForceSpeedster.cooldownRemaining(p, id) <= 0;
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
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
		SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.low_meter", ChatFormatting.GOLD,
				String.format(java.util.Locale.ROOT, "%.0f", cost));
		return false;
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

	// ---------------------------------------------------------------- R: Speed Punches / Shift+R: Infinite Mass Punch

	/** Three blows on everything right in front, 4 to 10 each with the momentum. */
	public static int speedPunches(ServerPlayer p) {
		if (!gate(p, PUNCHES)) {
			return 0;
		}
		float dmg = SpeedForceConfig.PUNCH_MIN + (SpeedForceConfig.PUNCH_MAX - SpeedForceConfig.PUNCH_MIN) * m(p);
		Vec3 at = p.getEyePosition().add(flatLook(p).scale(1.5)).subtract(0, 0.4, 0);
		int struck = 0;
		for (LivingEntity e : SpeedForceCombat.within(p, at, SpeedForceConfig.PUNCH_RADIUS)) {
			boolean any = false;
			for (int i = 0; i < SpeedForceConfig.PUNCHES; i++) {
				any |= SpeedForceCombat.strike(p, e, p.position(), dmg, i == SpeedForceConfig.PUNCHES - 1 ? 0.35 : 0.0, 0.0);
			}
			if (any) {
				struck++;
			}
		}
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_PUNCHES);
		SpeedForceSpeedster.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.8f);
		if (struck > 0) {
			SpeedForceSpeedster.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 2.0f);
		}
		cooldown(p, PUNCHES, SpeedForceConfig.PUNCH_COOLDOWN);
		return struck;
	}

	/** Every bit of momentum into one punch: 10 + up to 60, a huge knockback and a shockwave. Spends the run. */
	public static boolean infiniteMassPunch(ServerPlayer p) {
		if (!gate(p, MASS)) {
			return false;
		}
		LivingEntity target = SuperSpeedMoves.aimTarget(p, SpeedForceConfig.MASS_REACH);
		if (target == null || !SpeedForceCombat.isTarget(p, target)) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!pay(p, SpeedForceConfig.MASS_COST)) {
			return false;
		}
		float mom = m(p);
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
		level.sendParticles(SpeedForceSpeedster.GOLD_BIG, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.5);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOOM, c, c);
		SpeedForceSpeedster.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.2f);
		SpeedForceSpeedster.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.1f);
		SpeedForceSpeedster.setMomentum(p, 0f);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_MASS);
		cooldown(p, MASS, SpeedForceConfig.MASS_COOLDOWN);
		return true;
	}

	// ---------------------------------------------------------------- G: Lightning Throw / Shift+G: Speed Tornado

	/** Wind up (a beat of crackling run-up), then hurl a bolt of Speed Force lightning: 8 to 20, chaining to two more. */
	public static boolean lightningThrow(ServerPlayer p) {
		if (SpeedForceSpeedster.state(p).throwLeft > 0 || !gate(p, THROW) || !pay(p, SpeedForceConfig.THROW_COST)) {
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.throwLeft = SpeedForceConfig.THROW_WINDUP;
		n.animId = SpeedForceState.ANIM_THROW;
		n.animStart = p.level().getGameTime();
		SpeedForceSpeedster.save(p, n);
		cooldown(p, THROW, SpeedForceConfig.THROW_COOLDOWN);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.9f);
		return true;
	}

	private static void throwTick(ServerPlayer p, SpeedForceState s) {
		if (s.throwLeft <= 0) {
			return;
		}
		SpeedForceState n = s.copy();
		n.throwLeft = s.throwLeft - 1;
		SpeedForceSpeedster.save(p, n);
		ServerLevel level = level(p);
		double a = s.throwLeft * 1.1;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + Math.cos(a) * 0.6, p.getY() + 0.3 + (SpeedForceConfig.THROW_WINDUP - s.throwLeft) * 0.18,
				p.getZ() + Math.sin(a) * 0.6, 3, 0.05, 0.05, 0.05, 0.02);
		if (n.throwLeft == 0) {
			fireBolt(p);
		}
	}

	/** The bolt itself (public for the gametests). */
	public static LivingEntity fireBolt(ServerPlayer p) {
		ServerLevel level = level(p);
		float mom = m(p);
		float dmg = SpeedForceConfig.THROW_MIN + SpeedForceConfig.THROW_MOMENTUM * mom;
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

	/** Run tight circles: a cyclone that drags everything within 7 blocks round and up, then flings it out. */
	public static boolean speedTornado(ServerPlayer p) {
		if (SpeedForceSpeedster.state(p).tornadoLeft > 0 || !gate(p, TORNADO) || !pay(p, SpeedForceConfig.TORNADO_COST)) {
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.tornadoLeft = SpeedForceConfig.TORNADO_TICKS;
		n.animId = SpeedForceState.ANIM_TORNADO;
		n.animStart = p.level().getGameTime();
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
		if (left == 0) {
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
				level.sendParticles(SpeedForceSpeedster.GOLD, from.x, from.y + 1.0, from.z, 3, 0.1, 0.4, 0.1, 0.0);
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

	// ---------------------------------------------------------------- X: Speed Force Step / Shift+X: Phase

	/** A short burst along the aim that ADDS momentum (+15%) -- for getting a run started. */
	public static boolean speedForceStep(ServerPlayer p) {
		if (!gate(p, STEP) || !pay(p, SpeedForceConfig.STEP_COST)) {
			return false;
		}
		Vec3 look = p.getLookAngle().normalize();
		Vec3 v = look.scale(SpeedForceConfig.STEP_LAUNCH);
		if (p.onGround() && v.y < 0.25) {
			v = new Vec3(v.x, 0.25, v.z);
		}
		Vec3 from = p.position();
		SpeedForceSpeedster.graceMotion(p, 4);
		AbilityHelpers.launchSelf(p, v);
		SpeedForceSpeedster.setMomentum(p, (float) (m(p) + SpeedForceConfig.STEP_MOMENTUM_GAIN));
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, from, from.add(look.scale(4.0)));
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 1.0, from.z, 14, 0.3, 0.6, 0.3, 0.15);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_STEP);
		SpeedForceSpeedster.sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 1.8f);
		cooldown(p, STEP, SpeedForceConfig.STEP_COOLDOWN);
		return true;
	}

	/** Hold Shift+X: vibrate through walls on your own level (no damage taken, none dealt, no other move). */
	public static boolean startPhase(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		if (s.phasing || !gate(p, PHASE)) {
			return false;
		}
		if (s.meter < SpeedForceConfig.PHASE_MIN) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.low_meter", ChatFormatting.GOLD,
					String.format(java.util.Locale.ROOT, "%.0f", SpeedForceConfig.PHASE_MIN));
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
		com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
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

	// ---------------------------------------------------------------- Z: Flash Time / Shift+Z: Time Remnant

	/**
	 * Time slows to a crawl for everyone but you (the game-wide tick-rate slow the plain Speedster's Time Slow uses), for
	 * 4 s plus up to 16 s more with the momentum, on your own clock. Z again ends it. 40 Speed Force; the cooldown starts at
	 * the end. No charge-up and no exhaustion afterwards -- the Speed Force carries you.
	 */
	public static boolean flashTime(ServerPlayer p) {
		if (SuperSpeedTimeSlow.isCasting(p)) {
			SuperSpeedTimeSlow.end(p, true);
			return false;
		}
		if (!gate(p, FLASH)) {
			return false;
		}
		if (SuperSpeedTimeSlow.anyActive()) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed.time_taken"), true);
			return false;
		}
		if (!pay(p, SpeedForceConfig.FLASH_TIME_COST)) {
			return false;
		}
		int ticks = SpeedForceConfig.FLASH_TIME_BASE + Math.round(SpeedForceConfig.FLASH_TIME_MOMENTUM * m(p));
		SuperSpeedTimeSlow.start(p, ticks);
		if (!SuperSpeedTimeSlow.isCasting(p)) {
			SpeedForceSpeedster.addMeter(p, SpeedForceConfig.FLASH_TIME_COST);
			return false;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.flashLeft = ticks;
		n.flashTotal = ticks;
		SpeedForceSpeedster.save(p, n);
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(SpeedForceSpeedster.GOLD_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
		return true;
	}

	/** Flash Time is over (from {@code SuperSpeedTimeSlow}): the cooldown starts now. */
	public static void onFlashTimeEnded(ServerPlayer p) {
		if (!SpeedForceSpeedster.hasPower(p)) {
			return;
		}
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.flashLeft = 0;
		n.flashTotal = 0;
		n.abilityReadyAt.put(FLASH, p.level().getGameTime() + HeroConfig.get().scaledCooldown(SpeedForceConfig.FLASH_TIME_COOLDOWN));
		SpeedForceSpeedster.save(p, n);
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
		level.sendParticles(SpeedForceSpeedster.GOLD_BIG, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.0);
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

	// ---------------------------------------------------------------- V: Rescue Run / Shift+V: Speed Heal

	/** Grab the ally or mob in front of you and carry it at full speed; V again sets it down. */
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

	/** Burn Speed Force to heal fast: 10 health at once, Regeneration III for 4 s, every harmful effect gone. */
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
		level.sendParticles(SpeedForceSpeedster.GOLD, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.8, 0.4, 0.2);
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_HEAL);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
		cooldown(p, HEAL, SpeedForceConfig.HEAL_COOLDOWN);
		return true;
	}

	// ---------------------------------------------------------------- C: Speed Force Surge / Shift+C: Rewind

	/** Lock the momentum at 100% for 15 s with blinding lightning off the body. 50 Speed Force. */
	public static boolean surge(ServerPlayer p) {
		if (SpeedForceSpeedster.surging(p) || !gate(p, SURGE) || !pay(p, SpeedForceConfig.SURGE_COST)) {
			return false;
		}
		long now = p.level().getGameTime();
		SpeedForceState n = SpeedForceSpeedster.state(p).copy();
		n.surgeUntil = now + SpeedForceConfig.SURGE_TICKS;
		n.momentum = 1f;
		n.animId = SpeedForceState.ANIM_SURGE;
		n.animStart = now;
		n.abilityReadyAt.put(SURGE, now + HeroConfig.get().scaledCooldown(SpeedForceConfig.SURGE_COOLDOWN));
		SpeedForceSpeedster.save(p, n);
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 60, 0.6, 1.0, 0.6, 0.5);
		level.sendParticles(SpeedForceSpeedster.GOLD_BIG, c.x, c.y, c.z, 40, 0.6, 1.0, 0.6, 0.0);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.SURGE, c, c);
		SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
		SpeedForceSpeedster.sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.8f);
		return true;
	}

	/** Snap back to where you stood 5 s ago, with the health you had then (never less than now). 60 Speed Force. */
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

	// ---------------------------------------------------------------- upkeep

	static void tick(ServerPlayer p, long now) {
		SpeedForceState s = SpeedForceSpeedster.state(p);
		throwTick(p, s);
		tornadoTick(p, SpeedForceSpeedster.state(p));
		phaseTick(p, SpeedForceSpeedster.state(p));
		carryTick(p, SpeedForceSpeedster.state(p));
		remnantTick(p, SpeedForceSpeedster.state(p), now);
		s = SpeedForceSpeedster.state(p);
		// Flash Time's ticks left on the HUD (the caster's own clock)
		if (s.flashTotal > 0 && p.tickCount % 5 == 0) {
			int left = SuperSpeedTimeSlow.isCasting(p) ? SuperSpeedTimeSlow.ticksLeft() : 0;
			if (left != s.flashLeft) {
				SpeedForceState n = s.copy();
				n.flashLeft = left;
				if (left == 0) {
					n.flashTotal = 0;
				}
				SpeedForceSpeedster.save(p, n);
			}
		}
		if (s.surgeUntil != 0L && s.surgeUntil <= now) {
			SpeedForceState n = s.copy();
			n.surgeUntil = 0L;
			SpeedForceSpeedster.save(p, n);
		}
		if (s.animId != SpeedForceState.ANIM_NONE && s.animId != SpeedForceState.ANIM_TORNADO && now - s.animStart > 30) {
			SpeedForceState n = SpeedForceSpeedster.state(p).copy();
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
		if (s.phasing) {
			SpeedForceState n = SpeedForceSpeedster.state(p).copy();
			n.phasing = false;
			SpeedForceSpeedster.save(p, n);
		}
		killRemnant(p);
		SuperSpeedTimeSlow.end(p, false);
	}
}
