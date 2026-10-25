package com.projecthero.mod.falcon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.entity.FalconEntityTypes;
import com.projecthero.mod.falcon.entity.RedwingEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Falcon's twelve moves (v0.17.1). Each is gated by {@link #begin} (off cooldown, enough Thrust), runs as a small
 * per-player session ticked from {@link #tick}, and deals damage through {@link FalconCombat}.
 *
 * <pre>
 *   R (1)  Wrist Guns (hold)       Shift+R  Micro-Missile Volley
 *   G (2)  Wing Slash              Shift+G  Wing Shield (hold; release = bash)
 *   X (3)  Afterburner             Shift+X  Barrel Roll
 *   Z (4)  Redwing Strike          Shift+Z  Redwing Bombing Run
 *   V (5)  Tactical Goggles        Shift+V  Lift &amp; Carry
 *   C (6)  Dive Bomb               Shift+C  Talon Drop
 * </pre>
 * Flight is double-tap jump (no key). No H / N moves.
 */
public final class FalconAbilities {
	public static final String GUNS = "wrist_guns";
	public static final String MISSILES = "micro_missiles";
	public static final String SLASH = "wing_slash";
	public static final String SHIELD = "wing_shield";
	public static final String AFTERBURNER = "afterburner";
	public static final String ROLL = "barrel_roll";
	public static final String STRIKE = "redwing_strike";
	public static final String BOMBING = "redwing_bombing_run";
	public static final String GOGGLES = "tactical_goggles";
	public static final String CARRY = "lift_and_carry";
	public static final String DIVE = "dive_bomb";
	public static final String TALON = "talon_drop";
	/** Not a key: the wing flap (Space while gliding) and the flight itself, for cooldowns / costs / the guide. */
	public static final String FLAP = "wing_flap";
	public static final String FLIGHT = "flight";

	private static final class Guns {
		final long start;
		long nextShot;
		int shot;

		Guns(long start) {
			this.start = start;
			this.nextShot = start;
		}
	}

	private static final class Missile {
		Vec3 pos;
		Vec3 vel;
		final int target;
		final long born;

		Missile(Vec3 pos, Vec3 vel, int target, long born) {
			this.pos = pos;
			this.vel = vel;
			this.target = target;
			this.born = born;
		}
	}

	private static final class DiveSlash {
		final Vec3 dir;
		final long start;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		DiveSlash(Vec3 dir, long start) {
			this.dir = dir;
			this.start = start;
		}
	}

	private static final class Carry {
		final int target;
		final long start;
		final boolean talon;

		Carry(int target, long start, boolean talon) {
			this.target = target;
			this.start = start;
			this.talon = talon;
		}
	}

	private static final class Dive {
		final double startY;
		final long start;

		Dive(double startY, long start) {
			this.startY = startY;
			this.start = start;
		}
	}

	private static final Map<UUID, Guns> GUN_SESSIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Missile>> VOLLEYS = new ConcurrentHashMap<>();
	private static final Map<UUID, DiveSlash> DIVE_SLASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Carry> CARRIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Dive> DIVES = new ConcurrentHashMap<>();
	/** V: the game time it went down while the goggles were already on (a short press turns them off, a hold zooms). */
	private static final Map<UUID, Long> GOGGLE_PRESS = new ConcurrentHashMap<>();
	/** Each Falcon's Redwing drone. */
	private static final Map<UUID, RedwingEntity> REDWINGS = new ConcurrentHashMap<>();
	/** Barrel Roll: which way the next one rolls (alternates). */
	private static final Map<UUID, Boolean> ROLL_LEFT = new ConcurrentHashMap<>();

	private FalconAbilities() {
	}

	static void clearSessionState() {
		GUN_SESSIONS.clear();
		VOLLEYS.clear();
		DIVE_SLASHES.clear();
		CARRIES.clear();
		DIVES.clear();
		GOGGLE_PRESS.clear();
		REDWINGS.clear();
		ROLL_LEFT.clear();
	}

	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		GUN_SESSIONS.remove(id);
		VOLLEYS.remove(id);
		DIVE_SLASHES.remove(id);
		DIVES.remove(id);
		GOGGLE_PRESS.remove(id);
		ROLL_LEFT.remove(id);
		Carry c = CARRIES.remove(id);
		if (c != null && p.level().getEntity(c.target) instanceof LivingEntity held) {
			held.resetFallDistance();
		}
	}

	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case GUNS -> GUN_SESSIONS.containsKey(u);
			case MISSILES -> VOLLEYS.containsKey(u);
			case SLASH -> DIVE_SLASHES.containsKey(u);
			case SHIELD -> Falcon.shieldUp(p);
			case ROLL -> Falcon.rolling(p);
			case CARRY -> CARRIES.containsKey(u) && !CARRIES.get(u).talon;
			case TALON -> CARRIES.containsKey(u) && CARRIES.get(u).talon;
			case DIVE -> DIVES.containsKey(u);
			case STRIKE, BOMBING -> {
				RedwingEntity r = REDWINGS.get(u);
				yield r != null && r.isAlive() && r.busy();
			}
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!Falcon.canAct(p)) {
			return false;
		}
		int cd = Falcon.cooldownRemaining(p, id);
		if (cd > 0) {
			Falcon.say(p, "message.projecthero.falcon.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.falcon.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!Falcon.spendThrust(p, cost)) {
			Falcon.say(p, "message.projecthero.falcon.low_thrust", ChatFormatting.RED, (int) Math.ceil(cost), (int) Math.floor(Falcon.thrust(p)));
			return false;
		}
		return true;
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
		FalconState n = Falcon.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		Falcon.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 right(ServerPlayer p) {
		Vec3 r = p.getLookAngle().cross(new Vec3(0, 1, 0));
		return r.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : r.normalize();
	}

	/** Where a wrist launcher sits ({@code side} +1 right, -1 left). */
	static Vec3 wrist(ServerPlayer p, int side) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.7)).add(right(p).scale(0.38 * side)).add(0, -0.45, 0);
	}

	private static double heightAboveGround(ServerPlayer p, double max) {
		Vec3 from = p.position();
		BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(0, -max, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return hit.getType() == HitResult.Type.MISS ? max : from.y - hit.getLocation().y;
	}

	// ================================================================ R: Wrist Guns (hold)

	public static void startGuns(ServerPlayer p) {
		if (GUN_SESSIONS.containsKey(p.getUUID()) || !begin(p, GUNS, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		GUN_SESSIONS.put(p.getUUID(), new Guns(now));
		FalconState n = Falcon.state(p).copy();
		n.firingUntil = now + FalconConfig.GUN_MAX_TICKS;
		n.animId = FalconState.ANIM_GUNS;
		n.animStart = now;
		Falcon.save(p, n);
	}

	public static void stopGuns(ServerPlayer p) {
		if (GUN_SESSIONS.remove(p.getUUID()) == null) {
			return;
		}
		FalconState n = Falcon.state(p).copy();
		n.firingUntil = 0L;
		n.abilityReadyAt.put(GUNS, p.level().getGameTime() + FalconConfig.GUN_COOLDOWN);
		Falcon.save(p, n);
	}

	private static void tickGuns(ServerPlayer p, Guns g, long now) {
		if (now - g.start >= FalconConfig.GUN_MAX_TICKS) {
			stopGuns(p);
			return;
		}
		if (now < g.nextShot) {
			return;
		}
		g.nextShot = now + FalconConfig.GUN_INTERVAL;
		int side = (g.shot++ & 1) == 0 ? 1 : -1;
		Vec3 from = wrist(p, side);
		// a little spray: each round off the look by up to ~2 degrees
		Vec3 look = p.getLookAngle();
		Vec3 dir = look.add((p.getRandom().nextDouble() - 0.5) * 0.06, (p.getRandom().nextDouble() - 0.5) * 0.06,
				(p.getRandom().nextDouble() - 0.5) * 0.06).normalize();
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = FalconCombat.ray(p, p.getEyePosition(), dir, FalconConfig.GUN_RANGE, 0.3, e -> FalconCombat.isTarget(p, e), hit);
		ServerLevel level = level(p);
		AbilityHelpers.line(level, from, end, ParticleTypes.CRIT, 1.2);
		level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 1, 0.02, 0.02, 0.02, 0.0);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f + p.getRandom().nextFloat() * 0.2f);
		if (hit[0] != null) {
			FalconCombat.strike(p, hit[0], p.position(), FalconConfig.GUN_DAMAGE, 0.05, 0.0);
		} else {
			level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
	}

	// ================================================================ Shift+R: Micro-Missile Volley

	public static void missiles(ServerPlayer p) {
		if (VOLLEYS.containsKey(p.getUUID()) || !begin(p, MISSILES, FalconConfig.MISSILE_COST)) {
			return;
		}
		cooldown(p, MISSILES, FalconConfig.MISSILE_COOLDOWN);
		Falcon.setAnim(p, FalconState.ANIM_MISSILES);
		// lock on: up to four targets in the crosshair cone, nearest-to-centre first
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> cands = new ArrayList<>(FalconCombat.within(p, eye.add(look.scale(FalconConfig.MISSILE_RANGE * 0.5)),
				FalconConfig.MISSILE_RANGE * 0.5 + 2.0, e -> FalconCombat.isTarget(p, e)));
		cands.removeIf(e -> {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			return to.length() > FalconConfig.MISSILE_RANGE || to.normalize().dot(look) < FalconConfig.MISSILE_CONE || !p.hasLineOfSight(e);
		});
		cands.sort((a, b) -> Double.compare(-a.position().subtract(eye).normalize().dot(look), -b.position().subtract(eye).normalize().dot(look)));
		long now = p.level().getGameTime();
		List<Missile> live = new ArrayList<>();
		Vec3 r = right(p);
		for (int i = 0; i < FalconConfig.MISSILES; i++) {
			int target = cands.isEmpty() ? -1 : cands.get(i % cands.size()).getId();
			// two from each wing, fanning out before they turn in
			int side = (i & 1) == 0 ? 1 : -1;
			Vec3 from = p.position().add(0, 1.2, 0).add(r.scale(0.9 * side)).subtract(look.scale(0.3));
			Vec3 vel = look.scale(0.6).add(r.scale(0.35 * side)).add(0, 0.25 + 0.1 * (i / 2), 0);
			live.add(new Missile(from, vel, target, now + i));
		}
		VOLLEYS.put(p.getUUID(), live);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.3f);
		sound(p, SoundEvents.DISPENSER_LAUNCH, 0.7f, 1.6f);
	}

	private static void tickVolley(ServerPlayer p, List<Missile> live, long now) {
		ServerLevel level = level(p);
		java.util.Iterator<Missile> it = live.iterator();
		while (it.hasNext()) {
			Missile m = it.next();
			if (now < m.born) {
				continue;
			}
			LivingEntity target = m.target >= 0 && level.getEntity(m.target) instanceof LivingEntity le && le.isAlive() ? le : null;
			Vec3 desired;
			if (target != null) {
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(m.pos);
				desired = aim.normalize().scale(FalconConfig.MISSILE_SPEED);
			} else {
				desired = m.vel.lengthSqr() < 1.0e-4 ? p.getLookAngle().scale(FalconConfig.MISSILE_SPEED) : m.vel.normalize().scale(FalconConfig.MISSILE_SPEED);
			}
			m.vel = m.vel.add(desired.subtract(m.vel).scale(0.3));
			Vec3 next = m.pos.add(m.vel);
			BlockHitResult bhr = level.clip(new ClipContext(m.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			boolean boom = bhr.getType() != HitResult.Type.MISS || now - m.born > FalconConfig.MISSILE_LIFE;
			Vec3 at = bhr.getType() != HitResult.Type.MISS ? bhr.getLocation() : next;
			if (!boom) {
				for (LivingEntity e : FalconCombat.within(p, next, 1.0, e -> FalconCombat.isTarget(p, e))) {
					boom = true;
					at = next;
					FalconCombat.strike(p, e, m.pos, FalconConfig.MISSILE_DAMAGE, 0.3, 0.1);
					break;
				}
			}
			level.sendParticles(ParticleTypes.SMOKE, m.pos.x, m.pos.y, m.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.FLAME, m.pos.x, m.pos.y, m.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(Falcon.RED, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
			m.pos = next;
			if (boom) {
				final Vec3 c = at;
				for (LivingEntity e : FalconCombat.within(p, c, FalconConfig.MISSILE_BLAST, e -> FalconCombat.isTarget(p, e))) {
					FalconCombat.strike(p, e, c, FalconConfig.MISSILE_SPLASH, 0.4, 0.15);
				}
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
				it.remove();
			}
		}
		if (live.isEmpty()) {
			VOLLEYS.remove(p.getUUID());
		}
	}

	// ================================================================ G: Wing Slash

	public static void slash(ServerPlayer p) {
		if (DIVE_SLASHES.containsKey(p.getUUID()) || !begin(p, SLASH, 0f)) {
			return;
		}
		cooldown(p, SLASH, FalconConfig.SLASH_COOLDOWN);
		Falcon.setAnim(p, FalconState.ANIM_SLASH);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
		sound(p, SoundEvents.PHANTOM_FLAP, 0.8f, 1.5f);
		if (!p.onGround()) {
			// in the air: a diving slash that carries him through
			Vec3 dir = p.getLookAngle();
			if (dir.y > -0.25) {
				dir = new Vec3(dir.x, -0.25, dir.z).normalize();
			}
			DIVE_SLASHES.put(p.getUUID(), new DiveSlash(dir, p.level().getGameTime()));
			AbilityHelpers.launchSelf(p, dir.scale(FalconConfig.DIVE_SLASH_SPEED));
			return;
		}
		// on the ground: the steel wings sweep a 120 degree arc in front
		Vec3 eye = p.getEyePosition();
		Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		final Vec3 f = flat;
		for (LivingEntity e : FalconCombat.within(p, p.position().add(0, 1, 0), FalconConfig.SLASH_RANGE, e -> FalconCombat.isTarget(p, e))) {
			Vec3 to = e.position().subtract(p.position());
			Vec3 toFlat = new Vec3(to.x, 0, to.z);
			if (toFlat.lengthSqr() > 1.0e-4 && toFlat.normalize().dot(f) < FalconConfig.SLASH_ARC) {
				continue;
			}
			FalconCombat.strike(p, e, p.position(), FalconConfig.SLASH_DAMAGE, 0.9, 0.2);
		}
		ServerLevel level = level(p);
		for (int i = -6; i <= 6; i++) {
			double a = Math.atan2(f.z, f.x) + i * (Math.PI / 3) / 6;
			Vec3 pt = p.position().add(Math.cos(a) * 2.6, 1.1, Math.sin(a) * 2.6);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
			level.sendParticles(Falcon.WHITE, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
		}
		level.sendParticles(ParticleTypes.CRIT, eye.x, eye.y - 0.3, eye.z, 6, 0.6, 0.2, 0.6, 0.2);
	}

	private static void tickDiveSlash(ServerPlayer p, DiveSlash d, long now) {
		Vec3 step = d.dir.scale(FalconConfig.DIVE_SLASH_SPEED);
		d.travelled += step.length();
		AbilityHelpers.launchSelf(p, step);
		for (LivingEntity e : FalconCombat.within(p, p.position().add(0, 1, 0), 2.2, e -> FalconCombat.isTarget(p, e))) {
			if (d.hit.add(e.getId())) {
				FalconCombat.strike(p, e, p.position(), FalconConfig.DIVE_SLASH_DAMAGE, 0.8, 0.3);
				level(p).playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
		}
		Vec3 c = p.position().add(0, 1, 0);
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0.3, 0.2, 0.3, 0);
		if (d.travelled >= FalconConfig.DIVE_SLASH_DISTANCE || p.onGround() || now - d.start > 12) {
			DIVE_SLASHES.remove(p.getUUID());
			AbilityHelpers.launchSelf(p, d.dir.scale(0.5));
		}
	}

	// ================================================================ Shift+G: Wing Shield (hold; release = bash)

	public static void raiseShield(ServerPlayer p) {
		if (Falcon.shieldUp(p) || !begin(p, SHIELD, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		FalconState n = Falcon.state(p).copy();
		n.shieldUntil = now + FalconConfig.SHIELD_MAX_TICKS;
		n.shieldHp = FalconConfig.SHIELD_HP;
		Falcon.save(p, n);
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 1.2f);
		sound(p, SoundEvents.PHANTOM_FLAP, 0.8f, 0.6f);
	}

	/** Let go (or the shield broke / timed out): the wings snap open in a bash. */
	public static void lowerShield(ServerPlayer p) {
		FalconState s = Falcon.state(p);
		if (s.shieldUntil == 0L) {
			return;
		}
		boolean intact = s.shieldHp > 0f && s.shieldUntil > p.level().getGameTime();
		FalconState n = s.copy();
		n.shieldUntil = 0L;
		n.shieldHp = 0f;
		n.abilityReadyAt.put(SHIELD, p.level().getGameTime() + FalconConfig.SHIELD_COOLDOWN);
		n.animId = FalconState.ANIM_BASH;
		n.animStart = p.level().getGameTime();
		Falcon.save(p, n);
		if (!intact) {
			return;
		}
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : FalconCombat.within(p, p.position().add(0, 1, 0), FalconConfig.BASH_RANGE, e -> FalconCombat.isTarget(p, e))) {
			Vec3 to = e.position().subtract(p.position());
			if (to.lengthSqr() > 1.0e-4 && to.normalize().dot(look) < 0.2) {
				continue;
			}
			FalconCombat.strike(p, e, p.position(), FalconConfig.BASH_DAMAGE, FalconConfig.BASH_KNOCKBACK, 0.3);
		}
		Vec3 c = p.getEyePosition().add(look.scale(1.2));
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.3, c.z, 3, 0.5, 0.3, 0.5, 0);
		level(p).sendParticles(ParticleTypes.CLOUD, c.x, c.y - 0.3, c.z, 8, 0.4, 0.3, 0.4, 0.08);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 1.3f);
	}

	// ================================================================ X: Afterburner

	public static void afterburner(ServerPlayer p) {
		if (!begin(p, AFTERBURNER, FalconConfig.AFTERBURNER_COST)) {
			return;
		}
		cooldown(p, AFTERBURNER, FalconConfig.AFTERBURNER_COOLDOWN);
		Falcon.setAnim(p, FalconState.ANIM_AFTERBURNER);
		Vec3 look = p.getLookAngle();
		if (p.onGround()) {
			// a boosted leap that becomes flight
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			AbilityHelpers.launchSelf(p, flat.scale(FalconConfig.LEAP_FORWARD).add(0, FalconConfig.LEAP_UP, 0));
			Falcon.takeOff(p);
			if (Falcon.state(p).flying) {
				// takeOff re-launched straight up; keep the leap's forward push
				AbilityHelpers.launchSelf(p, flat.scale(FalconConfig.LEAP_FORWARD).add(0, FalconConfig.LEAP_UP, 0));
			}
		} else {
			AbilityHelpers.launchSelf(p, look.scale(FalconConfig.AFTERBURNER_SPEED));
			if (!Falcon.state(p).flying) {
				Falcon.takeOff(p);
			}
		}
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 0.9, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.08);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.06);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.8f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.7f, 0.6f);
	}

	// ================================================================ Shift+X: Barrel Roll

	public static void barrelRoll(ServerPlayer p) {
		if (!begin(p, ROLL, FalconConfig.ROLL_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		FalconState n = Falcon.state(p).copy();
		n.rollUntil = now + FalconConfig.ROLL_TICKS;
		n.abilityReadyAt.put(ROLL, now + FalconConfig.ROLL_COOLDOWN);
		n.animId = FalconState.ANIM_ROLL;
		n.animStart = now;
		Falcon.save(p, n);
		boolean left = !Boolean.TRUE.equals(ROLL_LEFT.get(p.getUUID()));
		ROLL_LEFT.put(p.getUUID(), left);
		Vec3 side = right(p).scale(left ? -1 : 1);
		Vec3 cur = p.getDeltaMovement();
		Vec3 keep = Falcon.flying(p) ? p.getLookAngle().scale(Math.max(0.8, Falcon.recentSpeed(p))) : new Vec3(cur.x * 0.3, 0.25, cur.z * 0.3);
		AbilityHelpers.launchSelf(p, keep.add(side.scale(FalconConfig.ROLL_SPEED)));
		sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 1.6f);
		sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.8f);
	}

	private static void tickRoll(ServerPlayer p) {
		// every projectile close by is turned back the way it came
		ServerLevel level = level(p);
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(FalconConfig.ROLL_REFLECT_RADIUS), e -> e instanceof Projectile)) {
			Projectile pr = (Projectile) e;
			if (pr.getOwner() == p) {
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			if (v.lengthSqr() < 0.01) {
				continue;
			}
			pr.setDeltaMovement(v.scale(-1.0));
			pr.setOwner(p);
			pr.hurtMarked = true;
			level.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.1);
			level.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.6f);
		}
		Vec3 c = p.position().add(0, 0.9, 0);
		level.sendParticles(Falcon.WHITE, c.x, c.y, c.z, 2, 0.6, 0.4, 0.6, 0.0);
	}

	// ================================================================ Z / Shift+Z: Redwing

	/** The player's Redwing, spawning it beside them if it is missing. */
	static RedwingEntity redwing(ServerPlayer p) {
		RedwingEntity r = REDWINGS.get(p.getUUID());
		if (r != null && r.isAlive() && r.level() == p.level()) {
			return r;
		}
		if (r != null) {
			r.discard();
		}
		r = FalconEntityTypes.REDWING.create(p.level());
		if (r == null) {
			return null;
		}
		Vec3 at = p.position().add(right(p).scale(1.0)).add(0, 2.2, 0);
		r.moveTo(at.x, at.y, at.z, p.getYRot(), 0f);
		r.setOwner(p);
		p.level().addFreshEntity(r);
		REDWINGS.put(p.getUUID(), r);
		FalconState n = Falcon.state(p).copy();
		n.redwingId = r.getId();
		Falcon.save(p, n);
		return r;
	}

	public static void removeRedwing(ServerPlayer p) {
		RedwingEntity r = REDWINGS.remove(p.getUUID());
		if (r != null) {
			r.discard();
		}
	}

	public static void redwingStrike(ServerPlayer p) {
		RedwingEntity r = redwing(p);
		if (r == null || r.busy()) {
			return;
		}
		LivingEntity[] hit = new LivingEntity[1];
		FalconCombat.ray(p, FalconConfig.REDWING_RANGE, 1.0, hit);
		LivingEntity target = hit[0];
		if (target == null) {
			// nothing under the crosshair: the nearest threat
			double best = FalconConfig.REDWING_RANGE * FalconConfig.REDWING_RANGE * 0.5;
			for (LivingEntity e : FalconCombat.within(p, p.position(), FalconConfig.REDWING_RANGE * 0.7, e -> FalconCombat.isHostile(p, e))) {
				double d = e.distanceToSqr(p);
				if (d < best) {
					best = d;
					target = e;
				}
			}
		}
		if (target == null) {
			Falcon.say(p, "message.projecthero.falcon.no_target", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, STRIKE, FalconConfig.STRIKE_COST)) {
			return;
		}
		cooldown(p, STRIKE, FalconConfig.STRIKE_COOLDOWN);
		Falcon.setAnim(p, FalconState.ANIM_REDWING);
		r.strike(target);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 2.0f);
	}

	public static void redwingBombingRun(ServerPlayer p) {
		RedwingEntity r = redwing(p);
		if (r == null || r.busy()) {
			return;
		}
		if (!begin(p, BOMBING, FalconConfig.BOMB_COST)) {
			return;
		}
		cooldown(p, BOMBING, FalconConfig.BOMB_COOLDOWN);
		Falcon.setAnim(p, FalconState.ANIM_REDWING);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 at = FalconCombat.ray(p, FalconConfig.BOMB_RANGE, 0.5, hit);
		if (hit[0] != null) {
			at = hit[0].position();
		}
		r.bomb(at, p.getLookAngle());
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.5f);
	}

	// ================================================================ V: Tactical Goggles (toggle; hold = zoom)

	public static void gogglesPress(ServerPlayer p) {
		if (!Falcon.canAct(p) || Falcon.cooldownRemaining(p, GOGGLES) > 0) {
			return;
		}
		FalconState s = Falcon.state(p);
		if (s.goggles) {
			GOGGLE_PRESS.put(p.getUUID(), p.level().getGameTime());
			return;
		}
		FalconState n = s.copy();
		n.goggles = true;
		n.abilityReadyAt.put(GOGGLES, p.level().getGameTime() + FalconConfig.GOGGLES_COOLDOWN);
		Falcon.save(p, n);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.5f, 2.0f);
		Falcon.say(p, "message.projecthero.falcon.goggles_on", ChatFormatting.RED);
	}

	public static void gogglesRelease(ServerPlayer p) {
		Long down = GOGGLE_PRESS.remove(p.getUUID());
		if (down == null) {
			return;
		}
		if (p.level().getGameTime() - down >= FalconConfig.GOGGLES_HOLD_TICKS) {
			return; // that was a zoom
		}
		FalconState n = Falcon.state(p).copy();
		n.goggles = false;
		n.abilityReadyAt.put(GOGGLES, p.level().getGameTime() + FalconConfig.GOGGLES_COOLDOWN);
		Falcon.save(p, n);
		sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 2.0f);
	}

	// ================================================================ Shift+V: Lift & Carry / Shift+C: Talon Drop

	private static LivingEntity grabTarget(ServerPlayer p, double reach, boolean enemiesOnly) {
		LivingEntity[] hit = new LivingEntity[1];
		FalconCombat.ray(p, p.getEyePosition(), p.getLookAngle(), reach, 0.6, e -> e != p
				&& (enemiesOnly ? FalconCombat.isTarget(p, e) : (FalconCombat.isTarget(p, e) || ally(p, e))), hit);
		return hit[0];
	}

	private static boolean ally(ServerPlayer p, LivingEntity e) {
		return e instanceof Player other && other != p && com.projecthero.mod.squad.Squads.shields(p, other);
	}

	/** Shift+V: grab the ally or creature in front (or throw the one held). */
	public static void liftAndCarry(ServerPlayer p) {
		Carry held = CARRIES.get(p.getUUID());
		if (held != null) {
			if (!held.talon) {
				release(p, true);
			}
			return;
		}
		if (!Falcon.canAct(p)) {
			return;
		}
		LivingEntity t = grabTarget(p, FalconConfig.CARRY_REACH, false);
		if (t == null) {
			Falcon.say(p, "message.projecthero.falcon.nothing_to_grab", ChatFormatting.GRAY);
			return;
		}
		if (FalconCombat.isBoss(t) || !(ally(p, t) || FalconCombat.grabbable(p, t))) {
			Falcon.say(p, "message.projecthero.falcon.too_big", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, CARRY, 0f)) {
			return;
		}
		grab(p, t, false);
	}

	/** Shift+C: snatch an enemy, rocket up, let go at the top. */
	public static void talonDrop(ServerPlayer p) {
		if (CARRIES.containsKey(p.getUUID()) || !Falcon.canAct(p)) {
			return;
		}
		LivingEntity t = grabTarget(p, FalconConfig.TALON_REACH, true);
		if (t == null) {
			Falcon.say(p, "message.projecthero.falcon.nothing_to_grab", ChatFormatting.GRAY);
			return;
		}
		if (!FalconCombat.grabbable(p, t)) {
			Falcon.say(p, "message.projecthero.falcon.too_big", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, TALON, FalconConfig.TALON_COST)) {
			return;
		}
		cooldown(p, TALON, FalconConfig.TALON_COOLDOWN);
		grab(p, t, true);
		AbilityHelpers.launchSelf(p, new Vec3(0, FalconConfig.TALON_RISE_SPEED, 0));
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
	}

	private static void grab(ServerPlayer p, LivingEntity t, boolean talon) {
		long now = p.level().getGameTime();
		CARRIES.put(p.getUUID(), new Carry(t.getId(), now, talon));
		FalconState n = Falcon.state(p).copy();
		n.carryId = t.getId();
		n.animId = talon ? FalconState.ANIM_TALON : FalconState.ANIM_CARRY;
		n.animStart = now;
		Falcon.save(p, n);
		sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0f, 0.8f);
		if (t instanceof net.minecraft.world.entity.Mob mob) {
			mob.setTarget(null);
		}
	}

	/** Lets go of whatever is held; {@code thrown}: hurled along the look. */
	static void release(ServerPlayer p, boolean thrown) {
		Carry c = CARRIES.remove(p.getUUID());
		if (c == null) {
			return;
		}
		FalconState n = Falcon.state(p).copy();
		n.carryId = -1;
		if (!c.talon) {
			n.abilityReadyAt.put(CARRY, p.level().getGameTime() + FalconConfig.CARRY_COOLDOWN);
		}
		Falcon.save(p, n);
		if (!(p.level().getEntity(c.target) instanceof LivingEntity held) || !held.isAlive()) {
			return;
		}
		if (thrown) {
			Vec3 v = p.getLookAngle().scale(FalconConfig.THROW_SPEED).add(0, 0.2, 0);
			throwEntity(held, v);
			sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		} else {
			throwEntity(held, new Vec3(0, -0.3, 0));
		}
		if (c.talon) {
			level(p).sendParticles(ParticleTypes.CLOUD, held.getX(), held.getY(), held.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
			sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.9f);
		} else {
			held.resetFallDistance();
		}
	}

	private static void throwEntity(LivingEntity e, Vec3 v) {
		e.setDeltaMovement(v);
		e.hurtMarked = true;
		if (e instanceof ServerPlayer sp) {
			sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
		}
	}

	private static void tickCarry(ServerPlayer p, Carry c, long now) {
		if (!(p.level().getEntity(c.target) instanceof LivingEntity held) || !held.isAlive() || held.level() != p.level()) {
			release(p, false);
			return;
		}
		long age = now - c.start;
		if (c.talon) {
			if (age < FalconConfig.TALON_RISE_TICKS) {
				AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x * 0.5, FalconConfig.TALON_RISE_SPEED, p.getDeltaMovement().z * 0.5));
			} else {
				release(p, false);
				if (!Falcon.state(p).flying) {
					Falcon.takeOff(p);
				}
				return;
			}
		} else if (age > FalconConfig.CARRY_MAX_TICKS) {
			release(p, false);
			return;
		}
		// held under him, in his talons (just under the feet, a little forward)
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		Vec3 at = p.position().add(flat.scale(0.4)).add(0, -held.getBbHeight() - 0.15, 0);
		if (held instanceof ServerPlayer hp) {
			hp.connection.teleport(at.x, at.y, at.z, hp.getYRot(), hp.getXRot());
		} else {
			held.teleportTo(at.x, at.y, at.z);
			held.setDeltaMovement(Vec3.ZERO);
		}
		if (!c.talon) {
			held.resetFallDistance();
		}
	}

	// ================================================================ C: Dive Bomb

	public static void diveBomb(ServerPlayer p) {
		if (DIVES.containsKey(p.getUUID()) || !Falcon.canAct(p)) {
			return;
		}
		if (p.onGround() || heightAboveGround(p, FalconConfig.DIVE_MIN_HEIGHT + 1) < FalconConfig.DIVE_MIN_HEIGHT) {
			Falcon.say(p, "message.projecthero.falcon.too_low", ChatFormatting.GRAY, (int) FalconConfig.DIVE_MIN_HEIGHT);
			return;
		}
		if (!begin(p, DIVE, FalconConfig.DIVE_COST)) {
			return;
		}
		cooldown(p, DIVE, FalconConfig.DIVE_COOLDOWN);
		long now = p.level().getGameTime();
		DIVES.put(p.getUUID(), new Dive(p.getY(), now));
		Falcon.setAnim(p, FalconState.ANIM_DIVE);
		sound(p, SoundEvents.PHANTOM_SWOOP, 1.4f, 0.8f);
	}

	private static Vec3 diveVelocity(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		return flat.scale(0.5).add(0, -FalconConfig.DIVE_SPEED, 0);
	}

	private static void tickDive(ServerPlayer p, Dive d, long now) {
		boolean hitGround = p.onGround() || p.isInWater() || heightAboveGround(p, 3.0) < 1.2;
		if (!hitGround && now - d.start < FalconConfig.DIVE_MAX_TICKS) {
			AbilityHelpers.launchSelf(p, diveVelocity(p));
			Vec3 c = p.position().add(0, 1, 0);
			level(p).sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1.0, c.z, 2, 0.2, 0.3, 0.2, 0.0);
			level(p).sendParticles(Falcon.WHITE, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0.0);
			return;
		}
		DIVES.remove(p.getUUID());
		impact(p, Math.max(0.0, d.startY - p.getY()));
	}

	/** The Dive Bomb lands: damage and shockwave scale with the drop. Public for the gametests. */
	public static float diveDamage(double drop) {
		return (float) Math.max(FalconConfig.DIVE_MIN_DAMAGE, Math.min(FalconConfig.DIVE_MAX_DAMAGE,
				FalconConfig.DIVE_MIN_DAMAGE + drop * FalconConfig.DIVE_DAMAGE_PER_BLOCK));
	}

	static void impact(ServerPlayer p, double drop) {
		float dmg = diveDamage(drop);
		float scale = dmg / FalconConfig.DIVE_MAX_DAMAGE;
		double radius = FalconConfig.DIVE_RADIUS * (0.6 + 0.4 * scale);
		Vec3 c = p.position();
		for (LivingEntity e : FalconCombat.within(p, c.add(0, 0.5, 0), radius, e -> FalconCombat.isTarget(p, e))) {
			double dist = e.position().distanceTo(c);
			float falloff = (float) Math.max(0.5, 1.0 - dist / (radius + 1.0));
			FalconCombat.strike(p, e, c, dmg * falloff, 1.0 + scale, 0.4 + 0.3 * scale);
		}
		ServerLevel level = level(p);
		var ground = level.getBlockState(p.blockPosition().below());
		if (!ground.isAir()) {
			var dust = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 36; i++) {
				double a = i * Math.PI * 2 / 36;
				level.sendParticles(dust, c.x + Math.cos(a) * radius * 0.7, c.y + 0.1, c.z + Math.sin(a) * radius * 0.7, 2, 0.1, 0.05, 0.1, 0.15);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.6, 0.1, 0.6, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 24, radius * 0.4, 0.1, radius * 0.4, 0.12);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.1f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.6f);
		p.resetFallDistance();
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.15, 0));
		Falcon.stopFlying(p);
		Falcon.setAnim(p, FalconState.ANIM_LAND);
	}

	// ================================================================ tick

	public static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		Guns g = GUN_SESSIONS.get(id);
		if (g != null) {
			tickGuns(p, g, now);
		}
		List<Missile> v = VOLLEYS.get(id);
		if (v != null) {
			tickVolley(p, v, now);
		}
		DiveSlash ds = DIVE_SLASHES.get(id);
		if (ds != null) {
			tickDiveSlash(p, ds, now);
		}
		Carry c = CARRIES.get(id);
		if (c != null) {
			tickCarry(p, c, now);
		}
		Dive d = DIVES.get(id);
		if (d != null) {
			tickDive(p, d, now);
		}
		FalconState s = Falcon.state(p);
		if (s.shieldUntil != 0L && (s.shieldUntil <= now || s.shieldHp <= 0f)) {
			lowerShield(p);
		}
		if (s.rollUntil > now) {
			tickRoll(p);
		}
		if (s.carryId != -1 && c == null) {
			FalconState n = s.copy();
			n.carryId = -1;
			Falcon.save(p, n);
		}
		// Redwing rides along whenever he is the Falcon
		if (p.isAlive() && !p.isSpectator() && (now % 20 == 0 || !REDWINGS.containsKey(id))) {
			redwing(p);
		}
	}

	/** v0.17.1 (gametests): the drone this player owns, or null. */
	public static RedwingEntity redwingOf(ServerPlayer p) {
		RedwingEntity r = REDWINGS.get(p.getUUID());
		return r != null && r.isAlive() ? r : null;
	}

	/** Every creature the goggles would mark (server-side twin of the client's outline, for the gametests). */
	public static List<LivingEntity> marked(ServerPlayer p) {
		return new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.position(), p.position()).inflate(FalconConfig.GOGGLES_RANGE),
				e -> e != p && e.isAlive() && FalconCombat.isHostile(p, e)));
	}
}
