package com.projecthero.mod.quirk.engine;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Engine's twelve moves, the passive (Speed I + step assist), the heat meter and the Stall. Every public move is
 * called by {@link EngineKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <p>Movement: the speed gears are attribute modifiers (synced to the player's own client, which simulates the run --
 * the server only authorises it), the dashes and leaps one-off {@link AbilityHelpers#launchSelf} impulses (the server
 * sends the motion packet), so the run stays smooth and fully steerable. The sessions are static and keyed by player;
 * they hold entity ids, never entities, and are dropped by {@link #forget} / {@link #clearSessionState}.
 */
public final class EngineMoves {
	public static final ResourceLocation PASSIVE_ID = PowerToggles.id("quirk_engine_passive_speed");
	public static final ResourceLocation GEAR_ID = PowerToggles.id("quirk_engine_gear_speed");
	public static final ResourceLocation STEP_ID = PowerToggles.id("quirk_engine_step");

	static final class Session {
		// Recipro Kick
		long kickAt = -1L;
		long kickNext;
		int kickHits;
		// Recipro Burst: it was running last tick (so its natural end stalls the engines)
		boolean burstOn;
		// Engine Leap / Air Dash: the next landing is cushioned
		long softUntil;
		boolean airDashUsed;
		// Recipro Extend
		long extendAt = -1L;
		Vec3 extendFrom = Vec3.ZERO;
		Vec3 extendDir = Vec3.ZERO;
		double extendLength;
		final Set<Integer> extended = new HashSet<>();
		// Recipro Turbo
		Vec3 turboLast;
		final Set<Integer> turboHit = new HashSet<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private EngineMoves() {
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

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** The engines rev: a piston thump and a minecart roar, higher the higher the gear. */
	private static void rev(ServerPlayer p, float pitch) {
		sound(p, SoundEvents.PISTON_EXTEND, 0.7f, 0.6f + pitch * 0.4f);
		sound(p, SoundEvents.MINECART_RIDING, 0.5f, 0.8f + pitch * 0.6f);
	}

	private static long val(ServerPlayer p, String key) {
		return Quirks.state(p).val(key);
	}

	private static boolean running(ServerPlayer p, String key) {
		return val(p, key) > now(p);
	}

	/** Adds heat; at 100 the engines stall. */
	public static void heat(ServerPlayer p, float amount) {
		if (Quirks.addMeter(p, amount) >= 100f && !Quirks.overloaded(p)) {
			stall(p, EngineConfig.STALL_TICKS);
		}
	}

	/** Stall: Slowness III, no moves, the gears drop out. */
	public static void stall(ServerPlayer p, int ticks) {
		Quirks.overload(p, ticks);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, EngineConfig.STALL_SLOW_AMPLIFIER, false, true, true));
		Quirks.say(p, "message.projecthero.quirk.overloaded.engine", ChatFormatting.RED);
		sound(p, SoundEvents.PISTON_CONTRACT, 1.0f, 0.5f);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.6f);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.4, p.getZ(), 14, 0.3, 0.2, 0.3, 0.02);
	}

	// ================================================================ tick: passive, meter, running moves

	static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		QuirkState st = Quirks.state(p);
		boolean stalled = Quirks.overloaded(p);
		// --- passive: step assist always; Speed I and the gears only while the engines run
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, STEP_ID, EngineConfig.STEP_BONUS, AttributeModifier.Operation.ADD_VALUE);
		int gear = 0;
		if (st.val(Engine.V_TURBO) > now) {
			gear = 3;
		} else if (st.val(Engine.V_GEAR) > now) {
			gear = 2;
		} else if (st.val(Engine.V_BURST) > now) {
			gear = 1;
		}
		if (stalled) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PASSIVE_ID);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
		} else {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, PASSIVE_ID, EngineConfig.PASSIVE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			double gearSpeed = switch (gear) {
				case 3 -> EngineConfig.TURBO_SPEED;
				case 2 -> EngineConfig.GEAR_SPEED;
				case 1 -> EngineConfig.BURST_SPEED;
				default -> 0.0;
			};
			if (gearSpeed > 0.0) {
				PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID, gearSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else {
				PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
			}
		}
		// --- Recipro Burst ends in a stall (unless Cool Down vented it first)
		boolean burst = st.val(Engine.V_BURST) > now;
		if (s.burstOn && !burst && !stalled) {
			s.burstOn = false;
			Quirks.setVal(p, Engine.V_BURST, 0L);
			stall(p, EngineConfig.BURST_STALL_TICKS);
			stalled = true;
		}
		s.burstOn = burst;
		// --- the meter
		float perSecond;
		if (stalled) {
			perSecond = -EngineConfig.STALL_COOL_PER_SECOND;
		} else if (gear == 2) {
			perSecond = EngineConfig.GEAR_HEAT_PER_SECOND;
		} else if (gear == 1) {
			perSecond = EngineConfig.BURST_HEAT_PER_SECOND;
		} else if (gear == 3) {
			perSecond = 0f; // Turbo's heat is all up front
		} else if (p.isSprinting()) {
			perSecond = -EngineConfig.SPRINT_COOL_PER_SECOND;
		} else {
			perSecond = -EngineConfig.COOL_PER_SECOND;
		}
		if (perSecond < 0f && p.isInWaterOrRain()) {
			perSecond *= EngineConfig.WATER_COOL_MULT;
		}
		if (perSecond != 0f) {
			if (perSecond > 0f) {
				heat(p, perSecond / 20f);
			} else {
				Quirks.addMeter(p, perSecond / 20f);
			}
		}
		// --- soft landing after a leap / air dash
		if (p.onGround()) {
			s.airDashUsed = false;
			if (s.softUntil > 0L && now > s.softUntil - EngineConfig.SOFT_LANDING_TICKS + 4) {
				s.softUntil = 0L;
			}
		}
		if (s.softUntil > now) {
			p.resetFallDistance();
		}
		// --- running moves
		if (s.kickAt >= 0L) {
			kickStep(p, s, now);
		}
		if (s.extendAt >= 0L) {
			extendStep(p, s, now - s.extendAt);
		}
		if (gear == 3) {
			turboStep(p, s);
		} else {
			s.turboLast = null;
		}
		carryTick(p);
		// --- the engines' drone while a gear runs (the exhaust flames themselves are client-side: EngineFx)
		if (!stalled && gear > 0 && p.tickCount % 20 == 0) {
			sound(p, SoundEvents.MINECART_RIDING, 0.25f, 1.0f + gear * 0.25f);
		}
		if (st.val(Engine.V_STEAM) > now && p.tickCount % 2 == 0) {
			steam(p.serverLevel(), p, 3);
		}
	}

	// ================================================================ R: Recipro Kick / Shift+R: Spin Kick

	/** A three-kick combo on what is in front of him (the hits land over the next {@link EngineConfig#KICK_INTERVAL}s). */
	public static void kick(ServerPlayer p) {
		Session s = session(p);
		s.kickAt = now(p);
		s.kickHits = 0;
		Quirks.setVal(p, Engine.V_KICK, now(p) + (long) EngineConfig.KICK_HITS * EngineConfig.KICK_INTERVAL);
		Quirks.setAnim(p, Engine.ANIM_KICK);
		Quirks.setCooldown(p, EngineConfig.KICK, EngineConfig.KICK_COOLDOWN);
		heat(p, EngineConfig.KICK_HEAT);
		rev(p, 0.5f);
		kickStep(p, s, now(p));
	}

	private static void kickStep(ServerPlayer p, Session s, long now) {
		if (s.kickHits >= EngineConfig.KICK_HITS) {
			s.kickAt = -1L;
			return;
		}
		if (s.kickHits > 0 && now < s.kickNext) {
			return;
		}
		s.kickNext = now + EngineConfig.KICK_INTERVAL;
		s.kickHits++;
		boolean last = s.kickHits >= EngineConfig.KICK_HITS;
		Vec3 f = flatLook(p);
		Vec3 c = p.position().add(0, 0.8, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(f.scale(1.2)), EngineConfig.KICK_REACH)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.lengthSqr() > 0.04 && to.normalize().dot(f) < 0.35) {
				continue;
			}
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > EngineConfig.KICK_REACH) {
				continue;
			}
			EngineCombat.strike(p, e, p.position(), EngineConfig.KICK_DAMAGE, last ? EngineConfig.KICK_FINAL_KNOCKBACK : EngineConfig.KICK_KNOCKBACK,
					last ? 0.25 : 0.0);
		}
		Vec3 at = c.add(f.scale(1.4));
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		EngineCombat.exhaust(level, p, 3, 2);
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f + s.kickHits * 0.1f);
		if (last) {
			s.kickAt = -1L;
		}
	}

	/** A 360 kick: everything around him is kicked away. Returns how many were hit. */
	public static int spin(ServerPlayer p) {
		Quirks.setAnim(p, Engine.ANIM_SPIN);
		Quirks.setCooldown(p, EngineConfig.SPIN, EngineConfig.SPIN_COOLDOWN);
		heat(p, EngineConfig.SPIN_HEAT);
		Vec3 c = p.position().add(0, 0.7, 0);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, EngineConfig.SPIN_RADIUS + 1.0)) {
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > EngineConfig.SPIN_RADIUS) {
				continue;
			}
			if (EngineCombat.strike(p, e, p.position(), EngineConfig.SPIN_DAMAGE, EngineConfig.SPIN_KNOCKBACK, EngineConfig.SPIN_LIFT)) {
				n++;
			}
		}
		ServerLevel level = p.serverLevel();
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			double x = p.getX() + Math.cos(a) * 1.6;
			double z = p.getZ() + Math.sin(a) * 1.6;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, p.getY() + 0.5, z, 2, 0.05, 0.05, 0.05, 0.02);
			level.sendParticles(EngineCombat.EXHAUST, x, p.getY() + 0.5, z, 2, 0.1, 0.05, 0.1, 0.0);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 0.7, p.getZ(), 3, 0.8, 0.1, 0.8, 0);
		rev(p, 0.7f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		return n;
	}

	// ================================================================ G: Recipro Burst / Shift+G: Gear Up

	/** Ten seconds of huge speed; the engines stall when it ends. Pressed again while it runs: it ends now. */
	public static boolean burst(ServerPlayer p) {
		if (running(p, Engine.V_BURST)) {
			Quirks.setVal(p, Engine.V_BURST, now(p)); // ends next tick -> the stall
			return false;
		}
		Quirks.setVal(p, Engine.V_BURST, now(p) + EngineConfig.BURST_TICKS);
		session(p).burstOn = true;
		Quirks.setAnim(p, Engine.ANIM_BURST);
		Quirks.setCooldown(p, EngineConfig.BURST, EngineConfig.BURST_COOLDOWN);
		heat(p, EngineConfig.BURST_HEAT);
		rev(p, 1.0f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.9f, 0.7f);
		EngineCombat.exhaust(p.serverLevel(), p, 8, 3);
		return true;
	}

	/** Six seconds of even more speed, at double the heat. */
	public static void gearUp(ServerPlayer p) {
		Quirks.setVal(p, Engine.V_GEAR, now(p) + EngineConfig.GEAR_TICKS);
		Quirks.setAnim(p, Engine.ANIM_GEAR);
		Quirks.setCooldown(p, EngineConfig.GEAR, EngineConfig.GEAR_COOLDOWN);
		heat(p, EngineConfig.GEAR_HEAT);
		rev(p, 1.5f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.2f);
		EngineCombat.exhaust(p.serverLevel(), p, 10, 4);
	}

	// ================================================================ X: Engine Leap / Shift+X: Air Dash

	/** A big jump from the exhausts (about 9 blocks up), the landing cushioned. */
	public static void leap(ServerPlayer p) {
		Vec3 f = flatLook(p);
		Vec3 cur = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(cur.x * 0.5 + f.x * EngineConfig.LEAP_FORWARD, EngineConfig.LEAP_UP, cur.z * 0.5 + f.z * EngineConfig.LEAP_FORWARD));
		session(p).softUntil = now(p) + EngineConfig.SOFT_LANDING_TICKS;
		Quirks.setAnim(p, Engine.ANIM_LEAP);
		Quirks.setCooldown(p, EngineConfig.LEAP, EngineConfig.LEAP_COOLDOWN);
		heat(p, EngineConfig.LEAP_HEAT);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 16, 0.2, 0.05, 0.2, 0.06);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0.02, 0.3, 0.01);
		level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.5, 0.05, 0.5, 0.03);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
		rev(p, 0.8f);
	}

	/** A boost the way he looks (once per jump); false if already used before landing. */
	public static boolean airDash(ServerPlayer p) {
		Session s = session(p);
		if (s.airDashUsed && !p.onGround()) {
			Quirks.say(p, "message.projecthero.quirk.engine.air_dash_used", ChatFormatting.AQUA);
			return false;
		}
		s.airDashUsed = !p.onGround();
		Vec3 look = p.getLookAngle();
		AbilityHelpers.launchSelf(p, look.scale(EngineConfig.AIR_DASH_SPEED).add(0, 0.12, 0));
		s.softUntil = now(p) + EngineConfig.SOFT_LANDING_TICKS;
		Quirks.setAnim(p, Engine.ANIM_AIR_DASH);
		Quirks.setCooldown(p, EngineConfig.AIR_DASH, EngineConfig.AIR_DASH_COOLDOWN);
		heat(p, EngineConfig.AIR_DASH_HEAT);
		ServerLevel level = p.serverLevel();
		Vec3 back = p.position().add(0, 0.5, 0).subtract(look.scale(0.6));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, back.x, back.y, back.z, 12, 0.15, 0.15, 0.15, 0.05);
		level.sendParticles(EngineCombat.EXHAUST_WHITE, back.x, back.y, back.z, 10, 0.2, 0.2, 0.2, 0.0);
		sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.9f, 0.9f);
		return true;
	}

	// ================================================================ Z: Recipro Extend / Shift+Z: Afterburner Kick

	/** A 15-block dash-kick along the ground; everything along the path is hit ({@link #tick}). */
	public static void extend(ServerPlayer p) {
		Session s = session(p);
		Vec3 dir = flatLook(p);
		double len = 0.0;
		AABB bb = p.getBoundingBox();
		for (double d = 0.5; d <= EngineConfig.EXTEND_DISTANCE; d += 0.5) {
			Vec3 off = dir.scale(d);
			if (!p.level().noCollision(p, bb.move(off)) && !p.level().noCollision(p, bb.move(off.add(0, 1.05, 0)))) {
				break;
			}
			len = d;
		}
		s.extendAt = now(p);
		s.extendFrom = p.position();
		s.extendDir = dir;
		s.extendLength = len;
		s.extended.clear();
		Quirks.setVal(p, Engine.V_EXTEND, now(p) + EngineConfig.EXTEND_TICKS + 2);
		Quirks.setAnim(p, Engine.ANIM_EXTEND);
		Quirks.setCooldown(p, EngineConfig.EXTEND, EngineConfig.EXTEND_COOLDOWN);
		heat(p, EngineConfig.EXTEND_HEAT);
		rev(p, 1.2f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.1f);
		extendStep(p, s, 0L);
	}

	private static void extendStep(ServerPlayer p, Session s, long k) {
		double step = EngineConfig.EXTEND_DISTANCE / EngineConfig.EXTEND_TICKS;
		double a = Math.min(s.extendLength, Math.max(0.0, (k - 1) * step));
		double b = Math.min(s.extendLength, (k + 1) * step);
		Vec3 from = s.extendFrom.add(s.extendDir.scale(a));
		Vec3 to = s.extendFrom.add(s.extendDir.scale(b));
		EngineCombat.alongPath(p, from, to, EngineConfig.EXTEND_WIDTH, EngineConfig.EXTEND_DAMAGE, EngineConfig.EXTEND_KNOCKBACK, EngineConfig.EXTEND_LIFT,
				s.extended, EngineCombat::isTarget);
		ServerLevel level = p.serverLevel();
		if (k < EngineConfig.EXTEND_TICKS && k * step < s.extendLength) {
			AbilityHelpers.launchSelf(p, new Vec3(s.extendDir.x * step, Math.min(0.0, p.getDeltaMovement().y), s.extendDir.z * step));
			EngineCombat.exhaust(level, p, 4, 3);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.3, 0.05, 0.3, 0.01);
		} else {
			// the dash ends: brake hard so he doesn't skate on
			AbilityHelpers.launchSelf(p, new Vec3(s.extendDir.x * 0.2, Math.min(0.0, p.getDeltaMovement().y), s.extendDir.z * 0.2));
			s.extendAt = -1L;
		}
	}

	/** Test hook: everything the running (or last) Recipro Extend has hit. */
	public static Set<Integer> extended(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Set.of() : Set.copyOf(s.extended);
	}

	/** One kick that launches the target far the way he looks. Returns the target, or null. */
	public static @Nullable LivingEntity afterburner(ServerPlayer p) {
		LivingEntity t = EngineCombat.aimed(p, EngineConfig.AFTERBURNER_REACH, 30.0, EngineCombat::isTarget);
		Quirks.setAnim(p, Engine.ANIM_AFTERBURNER);
		Quirks.setCooldown(p, EngineConfig.AFTERBURNER, EngineConfig.AFTERBURNER_COOLDOWN);
		heat(p, EngineConfig.AFTERBURNER_HEAT);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.8f);
		rev(p, 1.0f);
		EngineCombat.exhaust(p.serverLevel(), p, 10, 4);
		if (t == null) {
			return null;
		}
		if (!EngineCombat.strike(p, t, p.position(), EngineConfig.AFTERBURNER_DAMAGE, 0.0, 0.0)) {
			return null;
		}
		if (!EngineCombat.isBoss(t)) {
			Vec3 f = flatLook(p);
			t.setDeltaMovement(f.scale(EngineConfig.AFTERBURNER_LAUNCH).add(0, EngineConfig.AFTERBURNER_LIFT, 0));
			t.hurtMarked = true;
			t.hasImpulse = true;
		}
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		return t;
	}

	// ================================================================ V: Rescue Carry / Shift+V: Rescue Throw

	private static boolean carryable(ServerPlayer p, LivingEntity e) {
		if (e == p || !e.isAlive() || e instanceof ArmorStand || EngineCombat.isBoss(e) || e.getMaxHealth() > 200f || e.isSpectator()) {
			return false;
		}
		if (e.getVehicle() == p || p.getVehicle() == e || e.isVehicle()) {
			return false;
		}
		if (e instanceof Player) {
			// a squadmate always (it's a rescue); anyone else only with hard crowd control on players allowed
			return Squads.areAllies(p, e) || AbilityHelpers.isValidGrabTarget(e, p);
		}
		return true;
	}

	/** The creature he carries now, or null. */
	public static @Nullable LivingEntity carried(ServerPlayer p) {
		long id = val(p, Engine.V_CARRY);
		if (id == 0L) {
			return null;
		}
		Entity e = p.level().getEntity((int) id);
		return e instanceof LivingEntity le && le.getVehicle() == p ? le : null;
	}

	/**
	 * Picks up the ally / mob he looks at and carries it on his shoulders while he runs (the Super Speed carry); pressed
	 * again it sets it down in front of him. Bosses can't be carried. Returns the creature picked up, or null.
	 */
	public static @Nullable LivingEntity carry(ServerPlayer p) {
		LivingEntity held = carried(p);
		if (held != null || val(p, Engine.V_CARRY) != 0L) {
			setDown(p, held);
			Quirks.setCooldown(p, EngineConfig.CARRY, EngineConfig.CARRY_COOLDOWN);
			return null;
		}
		LivingEntity t = EngineCombat.aimed(p, EngineConfig.CARRY_REACH, 20.0, (o, e) -> e.isAlive() && !(e instanceof ArmorStand));
		if (t == null) {
			// a small creature at his feet, right in front (under the crosshair's cone)
			Vec3 f = flatLook(p);
			double best = 2.5 * 2.5;
			for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(2.5),
					e -> e != p && e.isAlive() && !(e instanceof ArmorStand))) {
				Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
				double d2 = to.lengthSqr();
				if (d2 < best && (d2 < 0.04 || to.normalize().dot(f) > 0.6)) {
					best = d2;
					t = e;
				}
			}
		}
		if (t == null) {
			Quirks.say(p, "message.projecthero.quirk.engine.no_target", ChatFormatting.AQUA);
			return null;
		}
		if (!carryable(p, t)) {
			Quirks.say(p, EngineCombat.isBoss(t) ? "message.projecthero.quirk.engine.too_big" : "message.projecthero.quirk.engine.cant_carry",
					ChatFormatting.AQUA, t.getDisplayName());
			return null;
		}
		if (t.isPassenger()) {
			t.stopRiding();
		}
		if (!t.startRiding(p, true)) {
			return null;
		}
		syncPassengers(p);
		t.resetFallDistance();
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		Quirks.setVal(p, Engine.V_CARRY, t.getId());
		Quirks.setAnim(p, Engine.ANIM_CARRY);
		Quirks.setCooldown(p, EngineConfig.CARRY, EngineConfig.CARRY_COOLDOWN);
		sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.2f);
		return t;
	}

	private static void carryTick(ServerPlayer p) {
		long id = val(p, Engine.V_CARRY);
		if (id == 0L) {
			return;
		}
		Entity e = p.level().getEntity((int) id);
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.getVehicle() != p || !p.isAlive()) {
			setDown(p, e instanceof LivingEntity le2 && le2.isAlive() && le2.distanceToSqr(p) < 9.0 ? le2 : null);
			return;
		}
		le.resetFallDistance();
		if (le instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		if (p.tickCount % 20 == 0) {
			syncPassengers(p);
		}
	}

	/** Sets the carried creature down in front of him (or at his feet). */
	static void setDown(ServerPlayer p, @Nullable LivingEntity le) {
		if (val(p, Engine.V_CARRY) != 0L) {
			Quirks.setVal(p, Engine.V_CARRY, 0L);
		}
		if (le != null && le.getVehicle() == p) {
			le.stopRiding();
		}
		syncPassengers(p);
		if (le == null) {
			return;
		}
		Vec3 front = p.position().add(flatLook(p).scale(1.2));
		AABB box = le.getDimensions(le.getPose()).makeBoundingBox(front);
		Vec3 at = p.level().noCollision(le, box) ? front : p.position();
		le.teleportTo(at.x, at.y, at.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.resetFallDistance();
		le.hurtMarked = true;
	}

	/** Throws whatever he carries the way he looks; null if he carries nothing. */
	public static @Nullable LivingEntity throwCarried(ServerPlayer p) {
		LivingEntity le = carried(p);
		if (le == null) {
			Quirks.say(p, "message.projecthero.quirk.engine.nothing_carried", ChatFormatting.AQUA);
			return null;
		}
		Quirks.setVal(p, Engine.V_CARRY, 0L);
		le.stopRiding();
		syncPassengers(p);
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(look.scale(1.0));
		AABB box = le.getDimensions(le.getPose()).makeBoundingBox(from.subtract(0, le.getBbHeight() * 0.5, 0));
		if (p.level().noCollision(le, box)) {
			le.teleportTo(from.x, from.y - le.getBbHeight() * 0.5, from.z);
		}
		le.setDeltaMovement(look.scale(EngineConfig.THROW_SPEED).add(0, EngineConfig.THROW_LIFT, 0));
		le.hurtMarked = true;
		le.hasImpulse = true;
		boolean ally = (le instanceof Player && Squads.areAllies(p, le)) || !HeroTargets.isHostile(p, le);
		if (ally) {
			le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, EngineConfig.THROW_ALLY_SLOW_FALL, 0, false, true, true));
		}
		le.resetFallDistance();
		Quirks.setAnim(p, Engine.ANIM_THROW);
		Quirks.setCooldown(p, EngineConfig.THROW, EngineConfig.THROW_COOLDOWN);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
		rev(p, 0.6f);
		EngineCombat.exhaust(p.serverLevel(), p, 6, 3);
		return le;
	}

	private static void syncPassengers(ServerPlayer p) {
		// vanilla never tells a player about passengers on themselves (see SuperSpeedHandlers.syncPassengersToSelf)
		if (p.connection != null) {
			p.connection.send(new ClientboundSetPassengersPacket(p));
		}
	}

	// ================================================================ C: Recipro Turbo / Shift+C: Cool Down

	/** Three seconds of extreme speed; every threat he passes is hit once. */
	public static void turbo(ServerPlayer p) {
		Session s = session(p);
		s.turboHit.clear();
		s.turboLast = p.position();
		Quirks.setVal(p, Engine.V_TURBO, now(p) + EngineConfig.TURBO_TICKS);
		Quirks.setAnim(p, Engine.ANIM_TURBO);
		Quirks.setCooldown(p, EngineConfig.TURBO, EngineConfig.TURBO_COOLDOWN);
		heat(p, EngineConfig.TURBO_HEAT);
		rev(p, 2.0f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 1.4f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.3f);
		EngineCombat.exhaust(p.serverLevel(), p, 12, 5);
		turboStep(p, s);
	}

	private static void turboStep(ServerPlayer p, Session s) {
		Vec3 here = p.position();
		Vec3 from = s.turboLast == null ? here : s.turboLast;
		EngineCombat.alongPath(p, from, here, EngineConfig.TURBO_RADIUS, EngineConfig.TURBO_DAMAGE, EngineConfig.TURBO_KNOCKBACK, 0.3, s.turboHit,
				(o, e) -> HeroTargets.isHostile(o, e));
		s.turboLast = here;
	}

	/** Test hook: everything the running (or last) Turbo has hit. */
	public static Set<Integer> turboHit(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Set.of() : Set.copyOf(s.turboHit);
	}

	/** Stops dead and vents {@link EngineConfig#COOL_VENT} heat as steam; every running gear ends (Recipro Burst without its stall). */
	public static void coolDown(ServerPlayer p) {
		session(p).burstOn = false;
		Quirks.edit(p, n -> {
			n.vals.remove(Engine.V_BURST);
			n.vals.remove(Engine.V_GEAR);
			n.vals.remove(Engine.V_TURBO);
			n.vals.put(Engine.V_STEAM, p.level().getGameTime() + 40L);
		});
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
		AbilityHelpers.launchSelf(p, new Vec3(0, Math.min(0.0, p.getDeltaMovement().y), 0));
		Quirks.addMeter(p, -EngineConfig.COOL_VENT);
		Quirks.setAnim(p, Engine.ANIM_COOL);
		Quirks.setCooldown(p, EngineConfig.COOL, EngineConfig.COOL_COOLDOWN);
		steam(p.serverLevel(), p, 20);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.7f);
		sound(p, SoundEvents.LAVA_EXTINGUISH, 0.8f, 1.2f);
		sound(p, SoundEvents.PISTON_CONTRACT, 0.7f, 0.8f);
	}

	static void steam(ServerLevel level, ServerPlayer p, int count) {
		float yaw = p.yBodyRot * ((float) Math.PI / 180f);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 at = p.position().add(right.scale(side * 0.13)).add(fwd.scale(-0.2)).add(0, 0.35, 0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, Math.max(1, count / 2), 0.08, 0.1, 0.08, 0.03);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y + 0.2, at.z, count, 0.15, 0.2, 0.15, 0.02);
		}
	}

	// ================================================================ the end of everything

	/** Every running move ends, the carry is set down, the attribute modifiers come off. */
	static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.kickAt = -1L;
			s.extendAt = -1L;
			s.burstOn = false;
			s.turboLast = null;
		}
		LivingEntity held = carried(p);
		if (held != null || val(p, Engine.V_CARRY) != 0L) {
			setDown(p, held);
		}
		QuirkState st = Quirks.state(p);
		if (st.val(Engine.V_BURST) != 0L || st.val(Engine.V_GEAR) != 0L || st.val(Engine.V_TURBO) != 0L || st.val(Engine.V_EXTEND) != 0L
				|| st.val(Engine.V_KICK) != 0L) {
			Quirks.edit(p, n -> {
				n.vals.remove(Engine.V_BURST);
				n.vals.remove(Engine.V_GEAR);
				n.vals.remove(Engine.V_TURBO);
				n.vals.remove(Engine.V_EXTEND);
				n.vals.remove(Engine.V_KICK);
			});
		}
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PASSIVE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
	}

	/** The holder still has Engine (stopAll leaves the power; revoke clears everything). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.ENGINE);
	}

	/** Fall damage is swallowed while a leap / air dash landing is cushioned. */
	public static boolean softLanding(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.softUntil > now(p);
	}
}
