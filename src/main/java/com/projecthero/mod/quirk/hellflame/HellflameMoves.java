package com.projecthero.mod.quirk.hellflame;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp ({@code docs/QUIRK_REVAMP_V0167.md}): Hellflame's twelve moves, Body Heat and Overheat, Plus Ultra and its
 * burnout, and the per-player sessions of the moves that run over time. {@link HellflameKit#handle} calls the public moves
 * once their cooldown is ready; the gametests call them directly. Sessions hold entity ids, never entities, and are dropped
 * by {@link #forget} / {@link #clearSessionState} ({@code ServerStateReset} through {@code QuirkKits}).
 *
 * <p><b>Body Heat</b> ({@link #heat}): every fire move adds to it, and the sustained ones (Fire Jet Flight, the Flame Cloak,
 * Prominence Burn) add every tick. Once no fire has burned for a moment it vents ({@link HellflameConfig.Heat}, faster wet).
 * At 100 he <b>overheats</b> ({@link #overheat}): every move ends, no fire moves, and he burns from the inside until the Heat
 * has drained back down -- only V (Cool Down) still works. <b>Plus Ultra</b> (Shift+V): for 15 s every fire move does double
 * damage, is 40% bigger and recharges twice as fast, and Heat cannot build; when it ends he is <b>burnt out</b>: the Heat is
 * pinned at 100 for 10 s and nothing works, not even Cool Down.
 *
 * <p>Fire blocks: only Fire Wall, the Fire Pillar and the end of the Prominence beam place any, always temporary (the
 * {@link GeoTerrain} ledger takes them back), only with the terrain-damage config and {@code mobGriefing} on, where he may
 * build, on solid ground and never within two blocks of anything that burns ({@link #tryFire}).
 */
public final class HellflameMoves {
	static final class Session {
		long lastFireAt = Long.MIN_VALUE / 2;
		// X (hold): Fire Jet Flight
		boolean flight;
		boolean floating;
		// Shift+X: the Jet Burn dash
		long dashUntil;
		Vec3 dashDir = Vec3.ZERO;
		// Shift+R: the Mesh
		long meshUntil;
		Vec3 meshCenter = Vec3.ZERO;
		final Set<Integer> meshed = new HashSet<>();
		// C: Fire Wall
		long wallUntil;
		Vec3 wallA = Vec3.ZERO;
		Vec3 wallB = Vec3.ZERO;
		// Shift+C: the Cloak
		boolean cloak;
		// Z: the Barrage
		int barrageLeft;
		int barrageIndex;
		long barrageNext;
		// Shift+Z: the Pillar
		long pillarAt;
		Vec3 pillarPos = Vec3.ZERO;
		boolean pillarErupted;
		// G: Prominence Burn
		boolean charge;
		long chargeAt;
		boolean beam;
		long beamAt;
		boolean released;
		// Shift+G: Vanishing Fist
		int vanishTarget = -1;
		long vanishStrikeAt;
		// Shift+V: Plus Ultra and the burnout after it
		long plusUntil;
		long burnoutUntil;
		// the fire blocks he has lit: position -> when they go out
		final Map<Long, Long> fires = new java.util.HashMap<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private HellflameMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static @Nullable Session peekSession(ServerPlayer p) {
		return SESSIONS.get(p.getUUID());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	/** Where the flames leave his (right) hand. */
	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	/** The first block on the way {@code from} + {@code dir} * {@code range}, else the full length. */
	private static Vec3 clip(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	// ================================================================ Plus Ultra multipliers

	public static boolean plusUltra(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.plusUntil > now(p);
	}

	/** {@code base} damage, doubled during Plus Ultra. */
	static float dmg(ServerPlayer p, float base) {
		return plusUltra(p) ? base * HellflameConfig.PLUS_DAMAGE_MULT : base;
	}

	/** Size multiplier now (Plus Ultra: 1.4). */
	static double size(ServerPlayer p) {
		return plusUltra(p) ? HellflameConfig.PLUS_SIZE_MULT : 1.0;
	}

	/** Cooldown, halved during Plus Ultra. */
	private static void cooldown(ServerPlayer p, String id, int ticks) {
		Quirks.setCooldown(p, id, plusUltra(p) ? Math.max(1, Math.round(ticks * HellflameConfig.PLUS_COOLDOWN_MULT)) : ticks);
	}

	// ================================================================ Body Heat / Overheat

	/** Adds Body Heat from a fire move (venting pauses); Overheat at 100. During Plus Ultra no Heat builds. Returns the Heat. */
	public static float heat(ServerPlayer p, float amount) {
		session(p).lastFireAt = now(p);
		if (plusUltra(p)) {
			return Quirks.meter(p);
		}
		float h = Quirks.addMeterPaced(p, amount); // v0.17.1: paced (HellflameMeter.BUILD)
		if (h >= 100f && !Quirks.overloaded(p)) {
			overheat(p);
		}
		return h;
	}

	/** 100 Heat: every move ends, Slowness II, no fire moves while he burns from the inside and the Heat drains (V still works). */
	public static void overheat(ServerPlayer p) {
		boolean airborne = !p.onGround();
		Quirks.overload(p, HellflameConfig.heat().overheatTicks); // runs stopAll
		Quirks.setMeter(p, 100f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HellflameConfig.heat().overheatTicks, 1, false, true, true));
		if (airborne) {
			// the jets cut out mid-air: he drifts down instead of dropping like a stone
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		}
		Quirks.setAnim(p, Hellflame.ANIM_OVERHEAT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.SMOKE, c.x, p.getY() + 0.3, c.z, 18, 0.5, 0.3, 0.5, 0.03);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 8, 0.3, 0.5, 0.3, 0.0);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.6f);
		sound(p, SoundEvents.LAVA_EXTINGUISH, 0.8f, 0.8f);
		sound(p, SoundEvents.PLAYER_HURT_ON_FIRE, 0.8f, 0.8f);
		Quirks.say(p, "message.projecthero.quirk.overloaded.hellflame", ChatFormatting.RED);
	}

	/** Burning up from the inside (overheated or burnt out): fire damage, so Fire Resistance helps. */
	private static void innerBurn(ServerPlayer p, long now) {
		if (now % 20 == 0 && HellflameConfig.heat().overheatDamagePerSecond > 0f) {
			p.hurt(p.damageSources().onFire(), HellflameConfig.heat().overheatDamagePerSecond);
		}
		ServerLevel level = p.serverLevel();
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.6, p.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
		}
		if (now % 20 == 0) {
			sound(p, SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f);
		}
	}

	private static boolean wet(ServerPlayer p) {
		return p.isInWaterOrRain();
	}

	// ================================================================ fire blocks (temporary, safe)

	/**
	 * Lights a temporary fire at {@code cell} (or the air cell just above / below it on solid ground), taken back by the
	 * {@link GeoTerrain} ledger after {@code ttl}. Only with the terrain-damage config and {@code mobGriefing} on, where he may
	 * build ({@link GeoTerrain#canEdit}), never on ground that burns forever and never within two blocks of anything
	 * flammable (so it cannot spread). True if a fire is there now.
	 */
	public static boolean tryFire(ServerPlayer p, BlockPos cell, int ttl) {
		ServerLevel level = p.serverLevel();
		if (!AbilityHelpers.canGrief() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
			return false;
		}
		BlockPos at = null;
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos c = cell.offset(0, dy, 0);
			BlockState st = level.getBlockState(c);
			BlockPos under = c.below();
			if (st.isAir() && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
				at = c.immutable();
				break;
			}
		}
		if (at == null || !GeoTerrain.canEdit(p, level, at) || level.getBlockState(at.below()).is(level.dimensionType().infiniburn())) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 3, 2))) {
			BlockState st = level.getBlockState(q);
			if (st.ignitedByLava() || !st.getFluidState().isEmpty() && q.getY() >= at.getY()) {
				return false;
			}
		}
		if (!GeoTerrain.placeTemp(p, level, at, BaseFireBlock.getState(level, at), ttl)) {
			return false;
		}
		// the ledger only takes back a fire that has not aged (doFireTick changes its state): put out whatever is left ourselves
		session(p).fires.put(at.asLong(), level.getGameTime() + ttl + 2);
		return true;
	}

	/** Puts out his fires whose time is up (still fire: the ledger missed an aged one). */
	private static void firesTick(ServerPlayer p, Session s, long now) {
		if (s.fires.isEmpty() || now % 10 != 0) {
			return;
		}
		ServerLevel level = p.serverLevel();
		s.fires.entrySet().removeIf(en -> {
			if (now < en.getValue()) {
				return false;
			}
			BlockPos pos = BlockPos.of(en.getKey());
			if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(pos, false);
			}
			return true;
		});
	}

	/** Test read: fires he has lit that are still on his clock. */
	public static int firesLit(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? 0 : s.fires.size();
	}

	// ================================================================ Shift+X: Flashfire Fist: Jet Burn

	/** He rockets along the look on flame; the first creature in reach takes the burning punch. */
	public static void jetBurn(ServerPlayer p) {
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		Vec3 dir = s.flight ? look : new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
		s.dashDir = dir;
		s.dashUntil = now(p) + HellflameConfig.JET_DASH_TICKS;
		Quirks.setVal(p, Hellflame.V_DASH, s.dashUntil);
		Quirks.setAnim(p, Hellflame.ANIM_JET);
		cooldown(p, HellflameConfig.JET, HellflameConfig.JET_COOLDOWN);
		ServerLevel level = p.serverLevel();
		spray(level, p.position().add(0, 0.8, 0), dir.scale(-1), 40, 0.3, 0.6);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 0.8f);
		heat(p, HellflameConfig.JET_HEAT);
		if (s.dashUntil == 0L) {
			return; // overheated on the spot
		}
		if (!dashHit(p, s)) {
			AbilityHelpers.launchSelf(p, dir.scale(HellflameConfig.JET_DASH_SPEED));
		}
	}

	/** Test read: the dash is still running. */
	public static boolean dashing(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.dashUntil > now(p);
	}

	/** Punch whatever is in reach now; true if something was (the dash ends). */
	private static boolean dashHit(ServerPlayer p, Session s) {
		Vec3 fist = p.position().add(0, 1.0, 0).add(s.dashDir.scale(0.8));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : HellflameCombat.around(p, fist, HellflameConfig.JET_REACH)) {
			double d = AbilityHelpers.distanceSqToBox(e, fist);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = best.position().add(0, best.getBbHeight() * 0.5, 0);
		HellflameCombat.strike(p, best, p.position(), dmg(p, HellflameConfig.JET_DAMAGE), HellflameConfig.JET_BURN_SECONDS, HellflameConfig.JET_KNOCKBACK,
				0.25, true);
		spray(level, c, s.dashDir, 50, 0.3, 0.85);
		HellflameCombat.flare(level, c, 28, 0.55);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		soundAt(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
		soundAt(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		soundAt(level, c, SoundEvents.FIRECHARGE_USE, 0.8f, 0.8f);
		AbilityHelpers.launchSelf(p, s.dashDir.scale(0.15));
		s.dashUntil = 0L;
		Quirks.setVal(p, Hellflame.V_DASH, 0L);
		p.resetFallDistance();
		return true;
	}

	private static void dashTick(ServerPlayer p, Session s, long now) {
		if (s.dashUntil == 0L) {
			return;
		}
		if (now >= s.dashUntil) {
			s.dashUntil = 0L;
			Quirks.setVal(p, Hellflame.V_DASH, 0L);
			p.resetFallDistance();
			return;
		}
		p.resetFallDistance();
		ServerLevel level = p.serverLevel();
		Vec3 back = p.position().add(0, 0.9, 0).subtract(s.dashDir.scale(0.6));
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 10, 0.2, 0.3, 0.2, 0.02);
		level.sendParticles(HellflameCombat.ORANGE, back.x, back.y, back.z, 5, 0.25, 0.3, 0.25, 0.0);
		if (!dashHit(p, s)) {
			AbilityHelpers.launchSelf(p, s.dashDir.scale(HellflameConfig.JET_DASH_SPEED));
		}
	}

	// ================================================================ X (hold): Fire Jet Flight

	/** Toggles the flight; returns whether he flies now. */
	public static boolean toggleFlight(ServerPlayer p) {
		Session s = session(p);
		if (s.flight) {
			endFlight(p);
			return false;
		}
		s.flight = true;
		Quirks.setVal(p, Hellflame.V_FLIGHT, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_FLIGHT);
		floatTolerance(p, s, true);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.65, 0));
		}
		ServerLevel level = p.serverLevel();
		spray(level, p.position(), new Vec3(0, -1, 0), 40, 0.4, 0.45);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
		HellflameCombat.flare(level, p.position().add(0, 0.2, 0), 16, 0.5);
		sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7f, 0.6f);
		s.lastFireAt = now(p);
		return true;
	}

	public static void endFlight(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.flight) {
			return;
		}
		s.flight = false;
		Quirks.setVal(p, Hellflame.V_FLIGHT, 0L);
		Quirks.setCooldown(p, HellflameConfig.FLIGHT, HellflameConfig.FLIGHT_COOLDOWN);
		floatTolerance(p, s, false);
		p.resetFallDistance();
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.3f);
	}

	public static boolean flying(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.flight;
	}

	/** Heat a tick of flight at this horizontal speed (blocks a tick). */
	public static float flightHeatPerTick(double speed) {
		HellflameConfig.Heat h = HellflameConfig.heat();
		float perSecond = speed < 0.12 ? h.flightHoverPerSecond : speed > 0.95 ? h.flightSprintPerSecond : h.flightPerSecond;
		return perSecond / 20f;
	}

	private static void flightTick(ServerPlayer p, Session s, long now) {
		floatTolerance(p, s, true);
		p.resetFallDistance();
		if (p.getAbilities().flying && !p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		if (now % 8 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 0.5f, 0.8f + p.getRandom().nextFloat() * 0.2f);
		}
		double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
		heat(p, flightHeatPerTick(speed));
	}

	/**
	 * Vanilla kicks a player who floats with no downward motion ("flying is not enabled"); the jets look just like that.
	 * {@code mayfly} for the duration, {@code flying} kept off, handed back after unless another power still flies him.
	 */
	private static void floatTolerance(ServerPlayer p, Session s, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				s.floating = true;
			}
			return;
		}
		if (s.floating) {
			s.floating = false;
			if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
					&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) && !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
		}
	}

	// ================================================================ R: Hell Spider / Shift+R: Mesh

	/**
	 * Five thin cutting strings flick out from his fingertips and sweep right to left across the look (drawn as glowing
	 * lines by the client from {@link Hellflame#V_SPIDER} / {@link Hellflame#VEC_SPIDER}); everything they cross is cut once.
	 */
	public static int hellSpider(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		double sz = size(p);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		int threads = HellflameConfig.SPIDER_THREADS;
		int steps = HellflameConfig.SPIDER_SWEEP_STEPS;
		for (int k = 0; k < steps; k++) {
			double sweep = Mth.lerp(k / (double) (steps - 1), HellflameConfig.SPIDER_SWEEP_DEGREES, -HellflameConfig.SPIDER_SWEEP_DEGREES);
			for (int i = 0; i < threads; i++) {
				Vec3 dir = spiderDir(p.getYRot(), p.getXRot(), i, sweep);
				Vec3 end = clip(p, from, dir, HellflameConfig.SPIDER_RANGE * sz);
				for (LivingEntity e : HellflameCombat.alongPath(p, from, end, HellflameConfig.SPIDER_THREAD_RADIUS * sz, hit)) {
					if (HellflameCombat.strike(p, e, from, dmg(p, HellflameConfig.SPIDER_DAMAGE), HellflameConfig.SPIDER_BURN_SECONDS, 0.3, 0.0, true)) {
						n++;
						Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
						level.sendParticles(HellflameCombat.WHITE_HOT, c.x, c.y, c.z, 6, 0.3, 0.05, 0.3, 0.0);
					}
				}
				if (k == steps - 1) {
					level.sendParticles(ParticleTypes.SMALL_FLAME, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.02);
				}
			}
		}
		Quirks.setVal(p, Hellflame.V_SPIDER, now(p));
		Quirks.setVec(p, Hellflame.VEC_SPIDER, new Vec3(p.getYRot(), p.getXRot(), sz));
		Quirks.setAnim(p, Hellflame.ANIM_SPIDER);
		cooldown(p, HellflameConfig.SPIDER, HellflameConfig.SPIDER_COOLDOWN);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 1.8f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.7f, 1.9f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.4f);
		heat(p, HellflameConfig.SPIDER_HEAT);
		return n;
	}

	/**
	 * CLIENT-SAFE: the direction of string {@code i} (0..threads-1) with the fan swept {@code sweepDegrees} (positive: to his
	 * right). Alternate strings tilt a little up and down: a fan, not a flat sheet.
	 */
	public static Vec3 spiderDir(float yaw, float pitch, int i, double sweepDegrees) {
		int threads = HellflameConfig.SPIDER_THREADS;
		double off = (i - (threads - 1) / 2.0) * HellflameConfig.SPIDER_SPREAD_DEGREES;
		float pitchOff = (i % 2 == 0 ? -1.5f : 1.5f) * (i == threads / 2 ? 0f : 1f);
		return Vec3.directionFromRotation(pitch + pitchOff, (float) (yaw + off + sweepDegrees));
	}

	/** A dome of burning strings round the aimed spot: everything inside is trapped (rooted) and burns. */
	public static int mesh(ServerPlayer p) {
		Session s = session(p);
		LivingEntity lock = HellflameCombat.aimed(p, HellflameConfig.MESH_RANGE, 8.0);
		Vec3 center = lock != null ? lock.position() : AbilityHelpers.aimPoint(p, HellflameConfig.MESH_RANGE);
		s.meshCenter = center;
		s.meshUntil = now(p) + HellflameConfig.MESH_TICKS;
		s.meshed.clear();
		double r = HellflameConfig.MESH_RADIUS * size(p);
		for (LivingEntity e : HellflameCombat.around(p, center.add(0, 1.0, 0), r)) {
			trap(p, s, e);
			HellflameCombat.strike(p, e, center, dmg(p, HellflameConfig.MESH_BURN_DAMAGE), 3, 0.0, 0.0, true);
		}
		ServerLevel level = p.serverLevel();
		Quirks.setVal(p, Hellflame.V_MESH, s.meshUntil);
		Quirks.setVal(p, Hellflame.V_MESH_AT, now(p));
		Quirks.setVec(p, Hellflame.VEC_MESH, center);
		Quirks.setAnim(p, Hellflame.ANIM_MESH);
		cooldown(p, HellflameConfig.MESH, HellflameConfig.MESH_COOLDOWN);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 1.2f);
		soundAt(level, center, SoundEvents.FIRECHARGE_USE, 1.0f, 0.9f);
		soundAt(level, center, SoundEvents.FIRE_AMBIENT, 1.2f, 0.8f);
		heat(p, HellflameConfig.MESH_HEAT);
		return s.meshed.size();
	}

	private static void trap(ServerPlayer p, Session s, LivingEntity e) {
		if (s.meshed.add(e.getId()) && !HellflameCombat.isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, (int) Math.max(1, s.meshUntil - now(p)), 9);
		}
	}

	private static void meshTick(ServerPlayer p, Session s, long now) {
		if (s.meshUntil == 0L) {
			return;
		}
		if (now >= s.meshUntil) {
			s.meshUntil = 0L;
			s.meshed.clear();
			return;
		}
		ServerLevel level = p.serverLevel();
		double r = HellflameConfig.MESH_RADIUS * size(p);
		// anything that walks into the dome is caught too
		if (now % 5 == 0) {
			for (LivingEntity e : HellflameCombat.around(p, s.meshCenter.add(0, 1.0, 0), r)) {
				trap(p, s, e);
			}
		}
		boolean burn = (s.meshUntil - now) % HellflameConfig.MESH_BURN_INTERVAL == 0;
		for (Iterator<Integer> it = s.meshed.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			if (!HellflameCombat.isBoss(le)) {
				// trapped: no walking, no jumping out of it
				Vec3 v = le.getDeltaMovement();
				le.setDeltaMovement(0, Math.min(0.0, v.y), 0);
				le.hurtMarked = true;
				if (le instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
			if (burn) {
				HellflameCombat.strike(p, le, s.meshCenter, dmg(p, HellflameConfig.MESH_BURN_DAMAGE), 2, 0.0, 0.0, true);
			}
		}
		if (now % 20 == 0) {
			soundAt(level, s.meshCenter, SoundEvents.FIRE_AMBIENT, 0.8f, 1.1f);
		}
	}

	/** Test read: entities trapped in the running Mesh. */
	public static Set<Integer> meshed(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null || s.meshUntil == 0L ? Set.of() : Set.copyOf(s.meshed);
	}

	// ================================================================ C: Fire Wall / Shift+C: Flame Cloak

	/** A 7-block wall of fire rises across his front for 6 s: it burns what crosses it and stops arrows. Returns the fires lit. */
	public static int fireWall(ServerPlayer p) {
		Session s = session(p);
		Vec3 fwd = flatLook(p);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double half = HellflameConfig.WALL_HALF_LENGTH * size(p);
		Vec3 c = p.position().add(fwd.scale(HellflameConfig.WALL_DISTANCE));
		s.wallA = c.subtract(right.scale(half));
		s.wallB = c.add(right.scale(half));
		s.wallUntil = now(p) + HellflameConfig.WALL_TICKS;
		ServerLevel level = p.serverLevel();
		int fires = 0;
		int cells = (int) Math.round(half * 2);
		for (int i = 0; i <= cells; i++) {
			Vec3 at = s.wallA.lerp(s.wallB, i / (double) cells);
			if (tryFire(p, BlockPos.containing(at.x, at.y + 0.2, at.z), HellflameConfig.WALL_TICKS)) {
				fires++;
			}
			HellflameCombat.flare(level, at.add(0, 0.6, 0), 6, 0.3);
		}
		Quirks.setVal(p, Hellflame.V_WALL, s.wallUntil);
		Quirks.setVec(p, Hellflame.VEC_WALL_A, s.wallA);
		Quirks.setVec(p, Hellflame.VEC_WALL_B, s.wallB);
		Quirks.setAnim(p, Hellflame.ANIM_WALL);
		cooldown(p, HellflameConfig.WALL, HellflameConfig.WALL_COOLDOWN);
		soundAt(level, c, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
		soundAt(level, c, SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		soundAt(level, c, SoundEvents.FIRE_AMBIENT, 1.4f, 0.7f);
		heat(p, HellflameConfig.WALL_HEAT);
		return fires;
	}

	/** True if {@code pos} (a point) is inside the wall slab. */
	private static boolean inWall(Session s, Vec3 pos, double pad) {
		Vec3 a = s.wallA, b = s.wallB;
		Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		double t = Mth.clamp(((pos.x - a.x) * ab.x + (pos.z - a.z) * ab.z) / len2, 0.0, 1.0);
		double nx = a.x + ab.x * t - pos.x, nz = a.z + ab.z * t - pos.z;
		return nx * nx + nz * nz <= Math.pow(HellflameConfig.WALL_THICKNESS * 0.5 + pad, 2) && pos.y >= a.y - 1.0
				&& pos.y <= a.y + HellflameConfig.WALL_HEIGHT + 0.5;
	}

	private static void wallTick(ServerPlayer p, Session s, long now) {
		if (s.wallUntil == 0L) {
			return;
		}
		if (now >= s.wallUntil) {
			s.wallUntil = 0L;
			Quirks.setVal(p, Hellflame.V_WALL, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		AABB box = new AABB(s.wallA, s.wallB).inflate(1.5, 0, 1.5).expandTowards(0, HellflameConfig.WALL_HEIGHT + 1.0, 0).move(0, -1.0, 0);
		if ((s.wallUntil - now) % HellflameConfig.WALL_HIT_INTERVAL == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && HellflameCombat.isTarget(p, e))) {
				if (inWall(s, e.position().add(0, 0.1, 0), e.getBbWidth() * 0.5)) {
					HellflameCombat.strike(p, e, e.position().add(0, 0.5, 0), dmg(p, HellflameConfig.WALL_DAMAGE), HellflameConfig.WALL_BURN_SECONDS, 0.0, 0.0,
							true);
				}
			}
		}
		// arrows and other projectiles burn up in it
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> pr.isAlive() && pr.getOwner() != p)) {
			if (inWall(s, pr.position(), 0.3) || inWall(s, pr.position().add(pr.getDeltaMovement()), 0.3)) {
				Vec3 at = pr.position();
				HellflameCombat.flare(level, at, 8, 0.2);
				soundAt(level, at, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.6f);
				pr.discard();
			}
		}
		if ((s.wallUntil - now) % 20 == 0) {
			soundAt(level, s.wallA.lerp(s.wallB, 0.5), SoundEvents.FIRE_AMBIENT, 1.0f, 0.8f);
		}
	}

	/** Test read: the wall is up. */
	public static boolean wallUp(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.wallUntil > now(p);
	}

	/** Toggles the Cloak; returns whether it is on now. */
	public static boolean toggleCloak(ServerPlayer p) {
		Session s = session(p);
		s.cloak = !s.cloak;
		Quirks.setVal(p, Hellflame.V_CLOAK, s.cloak ? 1L : 0L);
		if (s.cloak) {
			Quirks.setAnim(p, Hellflame.ANIM_CLOAK);
			HellflameCombat.flare(p.serverLevel(), p.position().add(0, 1.0, 0), 22, 0.5);
			sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
			sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 0.5f);
			s.lastFireAt = now(p);
		} else {
			Quirks.setCooldown(p, HellflameConfig.CLOAK, HellflameConfig.CLOAK_COOLDOWN);
			sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.2f);
		}
		return s.cloak;
	}

	public static boolean cloaked(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.cloak;
	}

	/** One Cloak tick; returns how many the aura burned (only on its beat). */
	static int cloakTick(ServerPlayer p, Session s, long now) {
		if (p.getRemainingFireTicks() > 0) {
			p.clearFire();
		}
		if (now % 20 == 0) {
			sound(p, SoundEvents.FIRE_AMBIENT, 0.7f, 0.9f);
		}
		int n = 0;
		if (now % HellflameConfig.CLOAK_AURA_INTERVAL == 0) {
			for (LivingEntity e : HellflameCombat.around(p, p.position().add(0, 0.9, 0), HellflameConfig.CLOAK_RADIUS * size(p))) {
				if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.CLOAK_AURA_DAMAGE), HellflameConfig.CLOAK_IGNITE_SECONDS, 0.2, 0.0, true)) {
					n++;
				}
			}
		}
		heat(p, HellflameConfig.heat().cloakPerSecond / 20f);
		return n;
	}

	/** Test hook: one aura beat now. */
	public static int cloakPulseForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.cloak) {
			return 0;
		}
		long beat = now(p) - now(p) % HellflameConfig.CLOAK_AURA_INTERVAL;
		return cloakTick(p, s, beat);
	}

	/** Melee while cloaked: the target catches fire. */
	public static void cloakMelee(ServerPlayer p, LivingEntity target) {
		if (!cloaked(p) || !HellflameCombat.isTarget(p, target)) {
			return;
		}
		target.igniteForSeconds(HellflameConfig.CLOAK_IGNITE_SECONDS + 1);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		p.serverLevel().sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.25, 0.3, 0.25, 0.05);
		p.serverLevel().sendParticles(HellflameCombat.ORANGE, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.0);
		soundAt(p.serverLevel(), c, SoundEvents.FIRECHARGE_USE, 0.5f, 1.4f);
	}

	/**
	 * Damage about to hit him. His body is built for heat: ordinary fire (standing in it, burning) does nothing -- except
	 * while overheated / burnt out, when he is burning up from the inside. Lava still hurts. The Cloak sets close attackers
	 * alight.
	 */
	public static float incoming(ServerPlayer p, DamageSource source, float amount) {
		boolean ordinaryFire = source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.HOT_FLOOR)
				|| source.is(DamageTypes.CAMPFIRE);
		if (ordinaryFire && !Quirks.overloaded(p)) {
			return 0f;
		}
		if (cloaked(p) && source.getEntity() instanceof LivingEntity attacker && attacker != p
				&& attacker.distanceTo(p) <= HellflameConfig.CLOAK_RADIUS + 2.5 && HellflameCombat.isTarget(p, attacker)) {
			attacker.igniteForSeconds(HellflameConfig.CLOAK_IGNITE_SECONDS + 1);
			Vec3 c = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
			p.serverLevel().sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
		}
		return amount;
	}

	// ================================================================ Z: Hellflame Barrage / Shift+Z: Fire Pillar

	/** Eight rapid jabs, each a short burst of flame from alternating fists. */
	public static void barrage(ServerPlayer p) {
		Session s = session(p);
		s.barrageLeft = HellflameConfig.BARRAGE_JABS;
		s.barrageIndex = 0;
		s.barrageNext = now(p);
		Quirks.setVal(p, Hellflame.V_BARRAGE, now(p) + (long) HellflameConfig.BARRAGE_JABS * HellflameConfig.BARRAGE_INTERVAL + 2L);
		Quirks.setAnim(p, Hellflame.ANIM_BARRAGE);
		cooldown(p, HellflameConfig.BARRAGE, HellflameConfig.BARRAGE_COOLDOWN);
		sound(p, SoundEvents.BLAZE_AMBIENT, 0.8f, 1.2f);
		barrageTick(p, s, now(p));
	}

	/** One jab; returns how many it burned. */
	static int jab(ServerPlayer p, int index) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double range = HellflameConfig.BARRAGE_RANGE * size(p);
		double cos = Math.cos(Math.toRadians(HellflameConfig.BARRAGE_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, eye, range + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth() * 0.5 || (d > 1.0 && to.scale(1.0 / d).dot(look) < cos)) {
				continue;
			}
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.BARRAGE_DAMAGE), HellflameConfig.BARRAGE_BURN_SECONDS,
					HellflameConfig.BARRAGE_PUSH, 0.05, true)) {
				n++;
			}
		}
		// the fist: right on even jabs, left on odd ones
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 fist = eye.add(look.scale(0.7)).add(right.scale((index % 2 == 0 ? 1 : -1) * 0.35)).add(0, -0.35, 0);
		spray(level, fist, look, 22, 0.16, 0.7 * size(p));
		level.sendParticles(HellflameCombat.WHITE_HOT, fist.x, fist.y, fist.z, 3, 0.05, 0.05, 0.05, 0.0);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.55f, 1.25f + p.getRandom().nextFloat() * 0.3f);
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 1.3f);
		heat(p, HellflameConfig.BARRAGE_HEAT_PER_JAB);
		return n;
	}

	private static void barrageTick(ServerPlayer p, Session s, long now) {
		if (s.barrageLeft <= 0 || now < s.barrageNext) {
			return;
		}
		jab(p, s.barrageIndex++);
		s.barrageLeft--;
		s.barrageNext = now + HellflameConfig.BARRAGE_INTERVAL;
		if (s.barrageLeft <= 0) {
			Quirks.setVal(p, Hellflame.V_BARRAGE, 0L);
		}
	}

	/** Test read: jabs left. */
	public static int barrageLeft(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? 0 : s.barrageLeft;
	}

	/** The ground under the target glows, then a pillar of fire erupts there. Returns the eruption point. */
	public static Vec3 firePillar(ServerPlayer p) {
		Session s = session(p);
		LivingEntity lock = HellflameCombat.aimed(p, HellflameConfig.PILLAR_RANGE, 10.0);
		Vec3 point = lock != null ? lock.position() : AbilityHelpers.aimPoint(p, HellflameConfig.PILLAR_RANGE);
		Vec3 ground = groundBelow(p, point);
		s.pillarPos = ground;
		s.pillarAt = now(p) + HellflameConfig.PILLAR_WARN_TICKS;
		s.pillarErupted = false;
		Quirks.setVal(p, Hellflame.V_PILLAR, s.pillarAt);
		Quirks.setVec(p, Hellflame.VEC_PILLAR, ground);
		Quirks.setAnim(p, Hellflame.ANIM_PILLAR);
		cooldown(p, HellflameConfig.PILLAR, HellflameConfig.PILLAR_COOLDOWN);
		ServerLevel level = p.serverLevel();
		soundAt(level, ground, SoundEvents.LAVA_AMBIENT, 1.2f, 0.6f);
		soundAt(level, ground, SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
		heat(p, HellflameConfig.PILLAR_HEAT);
		return ground;
	}

	/** The top of the ground at or under {@code point} (searches 8 blocks down). */
	private static Vec3 groundBelow(ServerPlayer p, Vec3 point) {
		Vec3 top = point.add(0, 1.0, 0);
		BlockHitResult hit = p.level().clip(new ClipContext(top, point.add(0, -8.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? point : hit.getLocation();
	}

	private static List<LivingEntity> inPillar(ServerPlayer p, Session s) {
		double r = HellflameConfig.PILLAR_RADIUS * size(p);
		Vec3 b = s.pillarPos;
		AABB col = new AABB(b.x - r, b.y - 0.5, b.z - r, b.x + r, b.y + HellflameConfig.PILLAR_HEIGHT, b.z + r);
		return p.serverLevel().getEntitiesOfClass(LivingEntity.class, col, e -> e != p && e.isAlive() && HellflameCombat.isTarget(p, e)
				&& Math.hypot(e.getX() - b.x, e.getZ() - b.z) <= r + e.getBbWidth() * 0.5);
	}

	/** Erupts now; returns how many it hit. */
	static int erupt(ServerPlayer p, Session s) {
		s.pillarErupted = true;
		ServerLevel level = p.serverLevel();
		Vec3 b = s.pillarPos;
		int n = 0;
		for (LivingEntity e : inPillar(p, s)) {
			if (HellflameCombat.strike(p, e, b.add(0, -1, 0), dmg(p, HellflameConfig.PILLAR_DAMAGE), HellflameConfig.PILLAR_BURN_SECONDS, 0.2,
					HellflameConfig.PILLAR_LIFT, true)) {
				n++;
			}
		}
		double r = HellflameConfig.PILLAR_RADIUS * size(p);
		for (int y = 0; y <= (int) HellflameConfig.PILLAR_HEIGHT; y++) {
			level.sendParticles(HellflameCombat.BIG_ORANGE, b.x, b.y + y, b.z, 3, r * 0.35, 0.4, r * 0.35, 0.0);
			level.sendParticles(ParticleTypes.FLAME, b.x, b.y + y, b.z, 6, r * 0.4, 0.4, r * 0.4, 0.06);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, b.x, b.y + 1.0, b.z, 2, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.LAVA, b.x, b.y + 0.5, b.z, 10, r * 0.5, 0.3, r * 0.5, 0.0);
		for (int i = 0; i < 8; i++) {
			double a = i / 8.0 * Math.PI * 2.0;
			tryFire(p, BlockPos.containing(b.x + Math.cos(a) * r * 0.8, b.y + 0.2, b.z + Math.sin(a) * r * 0.8), 80);
		}
		soundAt(level, b, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.4f, 0.6f);
		soundAt(level, b, SoundEvents.GHAST_SHOOT, 1.2f, 0.5f);
		soundAt(level, b, SoundEvents.BLAZE_SHOOT, 1.2f, 0.45f);
		return n;
	}

	private static void pillarTick(ServerPlayer p, Session s, long now) {
		if (s.pillarAt == 0L) {
			return;
		}
		if (!s.pillarErupted) {
			if (now >= s.pillarAt) {
				erupt(p, s);
			} else if ((s.pillarAt - now) % 5 == 0) {
				soundAt(p.serverLevel(), s.pillarPos, SoundEvents.LAVA_POP, 1.0f, 0.7f + 0.1f * (s.pillarAt - now) / 5f);
			}
			return;
		}
		if (now >= s.pillarAt + HellflameConfig.PILLAR_BURN_TICKS) {
			s.pillarAt = 0L;
			Quirks.setVal(p, Hellflame.V_PILLAR, 0L);
			return;
		}
		if ((now - s.pillarAt) % 5 == 4) {
			for (LivingEntity e : inPillar(p, s)) {
				HellflameCombat.strike(p, e, s.pillarPos, dmg(p, HellflameConfig.PILLAR_LINGER_DAMAGE), 3, 0.0, 0.15, true);
			}
		}
	}

	/** Test hook: erupt the waiting pillar now (skipping the warning); returns the hits. */
	public static int eruptForTest(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null || s.pillarAt == 0L ? -1 : erupt(p, s);
	}

	// ================================================================ G: Prominence Burn (hold)

	/** Hold: the fist draws back and flame gathers; the huge beam fires after {@link HellflameConfig#PROMINENCE_CHARGE_TICKS}. */
	public static boolean startProminence(ServerPlayer p) {
		Session s = session(p);
		if (s.charge || s.beam) {
			return false;
		}
		s.charge = true;
		s.chargeAt = now(p);
		s.released = false;
		s.lastFireAt = now(p);
		Quirks.setVal(p, Hellflame.V_CHARGE, now(p));
		sound(p, SoundEvents.BLAZE_AMBIENT, 1.0f, 0.5f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
		return true;
	}

	/** The key came up: a running beam ends (a tapped one still burns its minimum first). */
	public static void releaseProminence(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null) {
			return;
		}
		if (s.charge) {
			s.released = true;
		} else if (s.beam) {
			if (now(p) - s.beamAt >= HellflameConfig.PROMINENCE_MIN_TICKS) {
				endBeam(p);
			} else {
				s.released = true;
			}
		}
	}

	/** The beam fires now (the charge is done). */
	static void fireBeam(ServerPlayer p, Session s) {
		s.charge = false;
		s.beam = true;
		s.beamAt = now(p);
		Quirks.setVal(p, Hellflame.V_CHARGE, 0L);
		Quirks.setVal(p, Hellflame.V_BEAM, now(p));
		Vec3 h = hand(p);
		ServerLevel level = p.serverLevel();
		HellflameCombat.flare(level, h.add(p.getLookAngle().scale(0.6)), 18, 0.25);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.4f, 0.5f);
		sound(p, SoundEvents.GHAST_SHOOT, 1.2f, 0.45f);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.4f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(p.getLookAngle().scale(-0.25)));
	}

	public static void endBeam(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.beam) {
			return;
		}
		s.beam = false;
		s.released = false;
		Quirks.setVal(p, Hellflame.V_BEAM, 0L);
		cooldown(p, HellflameConfig.PROMINENCE, HellflameConfig.PROMINENCE_COOLDOWN);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.9f);
	}

	public static boolean beaming(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.beam;
	}

	public static boolean charging(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.charge;
	}

	/** The beam's segment now: from the hand to the first block (or full range). */
	public static Vec3[] beamSegment(ServerPlayer p) {
		Vec3 from = hand(p);
		return new Vec3[] { from, clip(p, from, p.getLookAngle(), HellflameConfig.PROMINENCE_RANGE) };
	}

	private static void chargeTick(ServerPlayer p, Session s, long now) {
		s.lastFireAt = now;
		long age = now - s.chargeAt;
		if (age >= HellflameConfig.PROMINENCE_CHARGE_TICKS) {
			fireBeam(p, s);
			return;
		}
		if (age % 4 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 0.7f, 0.5f + age / 24f);
		}
	}

	/** One beam tick; returns how many were burned this tick. */
	static int beamTick(ServerPlayer p, Session s, long now) {
		long age = now - s.beamAt;
		if (age >= HellflameConfig.PROMINENCE_MAX_TICKS || (s.released && age >= HellflameConfig.PROMINENCE_MIN_TICKS)) {
			endBeam(p);
			return 0;
		}
		int n = 0;
		Vec3[] seg = beamSegment(p);
		ServerLevel level = p.serverLevel();
		if (age % HellflameConfig.PROMINENCE_HIT_INTERVAL == 0) {
			for (LivingEntity e : HellflameCombat.alongPath(p, seg[0], seg[1], HellflameConfig.PROMINENCE_RADIUS * size(p), null)) {
				Vec3 push = p.getLookAngle().scale(HellflameConfig.PROMINENCE_PUSH);
				if (HellflameCombat.strike(p, e, seg[0], dmg(p, HellflameConfig.PROMINENCE_DAMAGE), HellflameConfig.PROMINENCE_BURN_SECONDS, 0.0, 0.0, true)) {
					n++;
					if (!HellflameCombat.isBoss(e)) {
						AbilityHelpers.push(e, push);
					}
				}
			}
		}
		if (age % 2 == 0) {
			HellflameCombat.flare(level, seg[1], 10, 0.6 * size(p));
		}
		if (age % 10 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 1.0f, 0.5f);
			soundAt(level, seg[1], SoundEvents.FIRE_AMBIENT, 1.4f, 0.8f);
			soundAt(level, seg[1], SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.6f);
			if (seg[1].distanceToSqr(seg[0]) < HellflameConfig.PROMINENCE_RANGE * HellflameConfig.PROMINENCE_RANGE - 1.0) {
				tryFire(p, BlockPos.containing(seg[1].subtract(p.getLookAngle().scale(0.3))), 100);
			}
		}
		heat(p, HellflameConfig.heat().prominencePerSecond / 20f);
		return n;
	}

	/** Test hook: one beam tick now (on a hit beat). */
	public static int beamTickForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.beam) {
			return 0;
		}
		long t = now(p);
		long age = t - s.beamAt;
		long beat = s.beamAt + age - age % HellflameConfig.PROMINENCE_HIT_INTERVAL;
		return beamTick(p, s, beat);
	}

	/** Test hook: the charge is done now. */
	public static void fireBeamForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null && s.charge) {
			fireBeam(p, s);
		}
	}

	// ================================================================ Shift+G: Vanishing Fist

	/**
	 * A burst of flame leaves a burning decoy where he stood (anything hunting him loses him), and he reappears behind the
	 * aimed creature and strikes. Returns the target, or null (nothing aimed at: nothing happens, no cooldown).
	 */
	public static @Nullable LivingEntity vanishingFist(ServerPlayer p) {
		LivingEntity target = HellflameCombat.aimed(p, HellflameConfig.VANISH_RANGE, 10.0);
		if (target == null) {
			Quirks.say(p, "message.projecthero.hellflame.no_target", ChatFormatting.GOLD);
			return null;
		}
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		Vec3 from = p.position();
		long now = now(p);
		// the decoy
		Quirks.setVal(p, Hellflame.V_DECOY, now + HellflameConfig.VANISH_DECOY_TICKS);
		Quirks.setVal(p, Hellflame.V_DECOY_YAW, Math.round(Mth.wrapDegrees(p.getYRot()) * 10f) + 100000L);
		Quirks.setVec(p, Hellflame.VEC_DECOY, from);
		HellflameCombat.flare(level, from.add(0, 1.0, 0), 30, 0.5);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 10, 0.3, 0.6, 0.3, 0.02);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
			m.getNavigation().moveTo(from.x, from.y, from.z, 1.0);
		}
		soundAt(level, from, SoundEvents.FIRECHARGE_USE, 1.0f, 0.5f);
		soundAt(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9f, 0.8f);
		// behind the target
		Vec3 back = behind(p, target);
		float yaw = (float) (Mth.atan2(target.getZ() - back.z, target.getX() - back.x) * Mth.RAD_TO_DEG) - 90f;
		p.teleportTo(level, back.x, back.y, back.z, yaw, 10f);
		p.setYHeadRot(yaw);
		p.resetFallDistance();
		HellflameCombat.flare(level, back.add(0, 1.0, 0), 24, 0.5);
		soundAt(level, back, SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f);
		s.vanishTarget = target.getId();
		s.vanishStrikeAt = now + HellflameConfig.VANISH_STRIKE_DELAY;
		Quirks.setAnim(p, Hellflame.ANIM_VANISH);
		cooldown(p, HellflameConfig.VANISH, HellflameConfig.VANISH_COOLDOWN);
		heat(p, HellflameConfig.VANISH_HEAT);
		return target;
	}

	/** A free spot just behind {@code target} (its back to him), else beside it, else where he stands. */
	private static Vec3 behind(ServerPlayer p, LivingEntity target) {
		Vec3 facing = Vec3.directionFromRotation(0f, target.getYRot());
		Vec3 toHim = new Vec3(p.getX() - target.getX(), 0, p.getZ() - target.getZ());
		double gap = target.getBbWidth() * 0.5 + 1.1;
		Vec3[] tries = { facing.scale(-1), facing.yRot((float) Math.PI / 2f), facing.yRot((float) -Math.PI / 2f),
				toHim.lengthSqr() < 1.0e-4 ? facing : toHim.normalize() };
		for (Vec3 d : tries) {
			Vec3 at = target.position().add(d.scale(gap));
			AABB box = p.getBoundingBox().move(at.subtract(p.position()));
			if (p.level().noCollision(p, box) && p.level().getWorldBorder().isWithinBounds(BlockPos.containing(at))) {
				return at;
			}
		}
		return p.position();
	}

	private static void vanishTick(ServerPlayer p, Session s, long now) {
		if (s.vanishTarget < 0 || now < s.vanishStrikeAt) {
			return;
		}
		Entity e = p.serverLevel().getEntity(s.vanishTarget);
		s.vanishTarget = -1;
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > 4.5) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		HellflameCombat.strike(p, target, p.position(), dmg(p, HellflameConfig.VANISH_DAMAGE), HellflameConfig.VANISH_BURN_SECONDS,
				HellflameConfig.VANISH_KNOCKBACK, 0.3, true);
		spray(level, c, c.subtract(p.getEyePosition()).normalize(), 40, 0.3, 0.8);
		HellflameCombat.flare(level, c, 24, 0.5);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		soundAt(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
		soundAt(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
	}

	/** Test hook: strike now. */
	public static void vanishStrikeForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null && s.vanishTarget >= 0) {
			vanishTick(p, s, s.vanishStrikeAt);
		}
	}

	// ================================================================ V: Cool Down / Shift+V: Plus Ultra

	/**
	 * Dumps every point of Heat in a radial burst of flame and steam (damage and radius grow with the Heat); ends an Overheat.
	 * Refused while burnt out. Returns the hit count (-1 refused).
	 */
	public static int coolDown(ServerPlayer p) {
		if (Hellflame.burntOut(p)) {
			Quirks.say(p, "message.projecthero.hellflame.burnt_out", ChatFormatting.RED);
			return -1;
		}
		float h = Quirks.meter(p);
		float damage = HellflameConfig.COOL_BASE_DAMAGE + HellflameConfig.COOL_DAMAGE_PER_HEAT * h;
		double r = HellflameConfig.COOL_BASE_RADIUS + HellflameConfig.COOL_RADIUS_PER_HEAT * h;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, c, r)) {
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, damage), HellflameConfig.COOL_BURN_SECONDS, HellflameConfig.COOL_KNOCKBACK, 0.3, true)) {
				n++;
			}
		}
		Quirks.setMeter(p, 0f);
		if (Quirks.overloaded(p)) {
			Quirks.edit(p, st -> st.overloadUntil = 0L);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
		p.clearFire();
		for (int ring = 0; ring < 3; ring++) {
			double y = c.y - 0.6 + ring * 0.6;
			int pts = 30;
			for (int i = 0; i < pts; i++) {
				double a = i / (double) pts * Math.PI * 2.0 + ring * 0.2;
				level.sendParticles(ParticleTypes.FLAME, c.x, y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.25 + r * 0.06);
			}
		}
		HellflameCombat.flare(level, c, 20 + (int) (h * 0.3), 0.6);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, c.x, c.y + 0.6, c.z, 26, 0.6, 0.9, 0.6, 0.05);
		level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.6, 0.5, 0.6, 0.06);
		Quirks.setAnim(p, Hellflame.ANIM_COOL);
		Quirks.setCooldown(p, HellflameConfig.COOL, HellflameConfig.COOL_COOLDOWN);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.7f);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.5f);
		sound(p, SoundEvents.LAVA_EXTINGUISH, 1.0f, 0.7f);
		session(p).lastFireAt = now(p) - HellflameConfig.heat().ventDelayTicks;
		return n;
	}

	/** Shift+V: 15 s of full power (double damage, bigger flames, faster cooldowns, no Heat), then the burnout. */
	public static boolean plusUltraStart(ServerPlayer p) {
		Session s = session(p);
		if (s.plusUntil > now(p)) {
			return false;
		}
		s.plusUntil = now(p) + HellflameConfig.heat().plusUltraSeconds * 20L;
		Quirks.setVal(p, Hellflame.V_PLUS, s.plusUntil);
		Quirks.setAnim(p, Hellflame.ANIM_PLUS);
		Quirks.setCooldown(p, HellflameConfig.PLUS, HellflameConfig.PLUS_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		HellflameCombat.flare(level, p.position().add(0, 0.3, 0), 50, 0.9);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, p.getY() + 0.2, c.z, 4, 1.4, 0.1, 1.4, 0);
		for (int i = 0; i < 40; i++) {
			double a = i / 40.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.FLAME, c.x, p.getY() + 0.2, c.z, 0, Math.cos(a), 0.15, Math.sin(a), 0.6);
		}
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.5f);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.4f);
		sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 1.4f);
		Quirks.say(p, "message.projecthero.hellflame.plus_ultra", ChatFormatting.GOLD);
		s.lastFireAt = now(p);
		return true;
	}

	/** Plus Ultra runs out: the Heat jumps to 100 and is pinned there while he is burnt out. */
	static void endPlus(ServerPlayer p, Session s, long now) {
		s.plusUntil = 0L;
		Quirks.setVal(p, Hellflame.V_PLUS, 0L);
		int burn = HellflameConfig.heat().burnoutSeconds * 20;
		boolean airborne = !p.onGround();
		Quirks.overload(p, burn); // runs stopAll
		Quirks.setMeter(p, 100f);
		s.burnoutUntil = now + burn;
		Quirks.setVal(p, Hellflame.V_BURNOUT, s.burnoutUntil);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, burn, 1, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, burn, 0, false, true, true));
		if (airborne) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
		}
		Quirks.setAnim(p, Hellflame.ANIM_OVERHEAT);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.3, p.getZ(), 24, 0.6, 0.3, 0.6, 0.04);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.4f);
		sound(p, SoundEvents.LAVA_EXTINGUISH, 1.0f, 0.6f);
		Quirks.say(p, "message.projecthero.hellflame.burnout", ChatFormatting.RED);
	}

	/** Test hook: Plus Ultra ends now. */
	public static void endPlusForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null && s.plusUntil != 0L) {
			endPlus(p, s, now(p));
		}
	}

	/** Test read: burnt out. */
	public static boolean burntOut(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.burnoutUntil > now(p);
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		firesTick(p, s, now);
		if (s.plusUntil != 0L && now >= s.plusUntil) {
			endPlus(p, s, now);
		}
		if (s.burnoutUntil != 0L && now >= s.burnoutUntil) {
			s.burnoutUntil = 0L;
			Quirks.setVal(p, Hellflame.V_BURNOUT, 0L);
			s.lastFireAt = now - HellflameConfig.heat().ventDelayTicks;
		}
		if (Quirks.overloaded(p)) {
			if (s.burnoutUntil > now) {
				Quirks.setMeter(p, 100f);
			} else {
				HellflameConfig.Heat h = HellflameConfig.heat();
				float rate = (100f - h.overheatRecoverTo) / Math.max(1, h.overheatTicks);
				if (Quirks.meter(p) > h.overheatRecoverTo) {
					Quirks.addMeter(p, -rate * (wet(p) ? h.wetMultiplier : 1f));
				}
			}
			innerBurn(p, now);
			return;
		}
		// each of these can overheat him, which ends everything: re-check as we go
		if (s.flight) {
			flightTick(p, s, now);
		}
		if (s.charge && !Quirks.overloaded(p)) {
			chargeTick(p, s, now);
		}
		if (s.beam && !Quirks.overloaded(p)) {
			beamTick(p, s, now);
		}
		if (s.cloak && !Quirks.overloaded(p)) {
			cloakTick(p, s, now);
		}
		if (!Quirks.overloaded(p)) {
			barrageTick(p, s, now);
		}
		dashTick(p, s, now);
		meshTick(p, s, now);
		wallTick(p, s, now);
		pillarTick(p, s, now);
		vanishTick(p, s, now);
		HellflameConfig.Heat h = HellflameConfig.heat();
		if (!Quirks.overloaded(p) && now - s.lastFireAt > h.ventDelayTicks && Quirks.meter(p) > 0f) {
			Quirks.addMeterPaced(p, -h.ventPerSecond / 20f * (wet(p) ? h.wetMultiplier : 1f));
		}
	}

	/** Test hook: pretend the last fire move was long ago (so the Heat starts venting now). */
	public static void coolForTest(ServerPlayer p) {
		session(p).lastFireAt = now(p) - 1000L;
	}

	/** Every running move ends and goes (revoke / death / logout / Erased / Overheat). The Heat and the burnout stay. */
	static void stopAll(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null) {
			return;
		}
		if (s.beam && Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.setCooldown(p, HellflameConfig.PROMINENCE, HellflameConfig.PROMINENCE_COOLDOWN);
		}
		boolean wasFlying = s.flight;
		s.flight = false;
		floatTolerance(p, s, false);
		if (wasFlying) {
			p.resetFallDistance();
			if (!p.onGround() && p.isAlive()) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
		}
		s.beam = false;
		s.charge = false;
		s.released = false;
		s.dashUntil = 0L;
		s.meshUntil = 0L;
		s.meshed.clear();
		s.wallUntil = 0L;
		s.cloak = false;
		s.barrageLeft = 0;
		s.pillarAt = 0L;
		s.vanishTarget = -1;
		s.plusUntil = 0L;
		if (Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.edit(p, n -> {
				for (String k : new String[] { Hellflame.V_FLIGHT, Hellflame.V_CLOAK, Hellflame.V_DASH, Hellflame.V_MESH, Hellflame.V_WALL,
						Hellflame.V_BARRAGE, Hellflame.V_PILLAR, Hellflame.V_CHARGE, Hellflame.V_BEAM, Hellflame.V_PLUS }) {
					n.vals.remove(k);
				}
			});
		}
	}

	/** {@code count} flame particles flying out along {@code dir} with {@code spread} at {@code speed}. */
	static void spray(ServerLevel level, Vec3 from, Vec3 dir, int count, double spread, double speed) {
		var rnd = level.getRandom();
		for (int i = 0; i < count; i++) {
			Vec3 d = dir.add((rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2).normalize();
			double sp = speed * (0.55 + rnd.nextDouble() * 0.6);
			ParticleOptions type = i % 5 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
			level.sendParticles(type, from.x, from.y, from.z, 0, d.x, d.y, d.z, sp);
		}
	}
}
