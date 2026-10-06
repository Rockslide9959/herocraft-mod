package com.projecthero.mod.sentinel.entity;

import java.util.ArrayDeque;
import java.util.function.Supplier;

import com.projecthero.mod.sentinel.SentinelTargets;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.3: a Sentinel Program beam shot you can dodge -- the Drone's laser, the Sentinel's chest beam and palm blast.
 * The old shots were fast bolts fired at a lead-aimed point, which in practice never missed. Now every shot is
 * <ol>
 *   <li><b>Tracking</b> (a thin aiming line): the aim point chases where the target was {@link #LAG_TICKS} ago, and only
 *       at {@code trackSpeed} blocks per tick -- so it trails anyone moving;</li>
 *   <li><b>Locked</b> for the last {@code lockTicks} before firing (the line brightens and pulses): the aim point is
 *       frozen;</li>
 *   <li><b>Fire</b>: one or more instant beams along the line from the emitter through the locked point, stopped by the
 *       first block, hurting everything (that a Sentinel may target) within {@link #HIT_RADIUS} of it.</li>
 * </ol>
 * Standing still gets you hit; strafing a couple of blocks during the lock dodges it. The damage per pulse is the same
 * as the old bolts'. The state is mirrored to the client through {@link SentinelRobot#syncShot} for the telegraph and
 * beam drawing.
 */
public final class SentinelAimedShot {
	public static final int STATE_OFF = 0, STATE_TRACK = 1, STATE_LOCK = 2, STATE_FIRE = 3;
	/** How far behind the target the tracking aim runs (ticks; 0.4 s). */
	public static final int LAG_TICKS = 8;
	/** How close to the beam's line a body has to be to be hit (added to its hit-box on every side). */
	public static final double HIT_RADIUS = 0.3;
	/** How long each pulse's beam stays drawn. */
	static final int FLASH_TICKS = 4;

	private final SentinelRobot owner;
	private final LivingEntity target;
	private final SentinelBeamEntity.Kind kind;
	private final Supplier<Vec3> origin;
	private final float damage;
	private final int fireTick;
	private final int lockTicks;
	private final int pulses;
	private final int pulseGap;
	private final double range;
	private final double trackSpeed;
	private final ArrayDeque<Vec3> history = new ArrayDeque<>();
	private Vec3 aim;
	private int ticks;
	private int hits;

	/**
	 * @param fireTick   ticks from the start to the first pulse
	 * @param lockTicks  how many ticks before the first pulse the aim freezes
	 * @param pulses     beams fired along the locked line, {@code pulseGap} ticks apart, each for {@code damage}
	 * @param trackSpeed how fast (blocks per tick) the aim point may move while tracking
	 */
	public SentinelAimedShot(SentinelRobot owner, LivingEntity target, SentinelBeamEntity.Kind kind, Supplier<Vec3> origin, float damage,
			int fireTick, int lockTicks, int pulses, int pulseGap, double range, double trackSpeed) {
		this.owner = owner;
		this.target = target;
		this.kind = kind;
		this.origin = origin;
		this.damage = damage;
		this.fireTick = fireTick;
		this.lockTicks = lockTicks;
		this.pulses = pulses;
		this.pulseGap = pulseGap;
		this.range = range;
		this.trackSpeed = trackSpeed;
		Vec3 at = centre(target);
		for (int i = 0; i <= LAG_TICKS; i++) {
			history.add(at);
		}
		this.aim = at;
	}

	private static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	public int state() {
		if (ticks < fireTick - lockTicks) {
			return STATE_TRACK;
		}
		if (ticks < fireTick) {
			return STATE_LOCK;
		}
		return STATE_FIRE;
	}

	public boolean locked() {
		return ticks >= fireTick - lockTicks;
	}

	public Vec3 aimPoint() {
		return aim;
	}

	public LivingEntity target() {
		return target;
	}

	/** How many bodies the pulses have hit so far (tests). */
	public int hits() {
		return hits;
	}

	public int lastPulseTick() {
		return fireTick + (pulses - 1) * pulseGap;
	}

	public boolean done() {
		return ticks > lastPulseTick() + FLASH_TICKS;
	}

	/** One server tick. Returns true once the shot is over (and the client line is cleared). */
	public boolean tick(ServerLevel level) {
		if (done()) {
			owner.syncShot(STATE_OFF, kind, Vec3.ZERO, Vec3.ZERO);
			return true;
		}
		Vec3 from = origin.get();
		if (target.isAlive()) {
			history.addLast(centre(target));
			while (history.size() > LAG_TICKS + 1) {
				history.removeFirst();
			}
		}
		if (!locked()) {
			Vec3 want = history.peekFirst();
			Vec3 delta = want.subtract(aim);
			double len = delta.length();
			if (len > 1.0e-4) {
				aim = aim.add(delta.scale(Math.min(len, trackSpeed) / len));
			}
		}
		Vec3 end = lineEnd(level, from);
		int state = state();
		if (ticks == 0) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, kind == SentinelBeamEntity.Kind.LASER ? 0.4f : 0.9f,
					kind == SentinelBeamEntity.Kind.LASER ? 2.0f : 1.4f);
		}
		if (ticks == fireTick - lockTicks) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 0.9f, 1.9f);
		}
		DustParticleOptions dust = new DustParticleOptions(kind == SentinelBeamEntity.Kind.LASER ? new Vector3f(1.0f, 0.2f, 0.12f)
				: new Vector3f(1.0f, 0.3f, 0.85f), state == STATE_LOCK ? 1.0f : 0.6f);
		if (state != STATE_FIRE && ticks % 2 == 0) {
			level.sendParticles(dust, from.x, from.y, from.z, state == STATE_LOCK ? 3 : 1, 0.06, 0.06, 0.06, 0.0);
		}
		int sinceFire = ticks - fireTick;
		if (sinceFire >= 0 && sinceFire % pulseGap == 0 && sinceFire / pulseGap < pulses) {
			pulse(level, from, end, dust);
		}
		owner.syncShot(state, kind, from, end);
		ticks++;
		return false;
	}

	/** The line from the emitter through the aim point, cut short by the first block. */
	private Vec3 lineEnd(ServerLevel level, Vec3 from) {
		Vec3 dir = aim.subtract(from);
		if (dir.lengthSqr() < 1.0e-6) {
			dir = Vec3.directionFromRotation(0, owner.yBodyRot);
		}
		Vec3 tip = from.add(dir.normalize().scale(range));
		var hit = level.clip(new ClipContext(from, tip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		return hit.getType() == HitResult.Type.MISS ? tip : hit.getLocation();
	}

	private void pulse(ServerLevel level, Vec3 from, Vec3 end, DustParticleOptions dust) {
		AABB box = new AABB(from, end).inflate(HIT_RADIUS + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, SentinelTargets::canTarget)) {
			if (e == owner || !e.getBoundingBox().inflate(HIT_RADIUS).clip(from, end).isPresent() && !e.getBoundingBox().contains(from)) {
				continue;
			}
			if (e.hurt(owner.damageSources().mobProjectile(owner, owner), damage)) {
				hits++;
			}
		}
		boolean laser = kind == SentinelBeamEntity.Kind.LASER;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, laser ? 5 : 8, 0.2, 0.2, 0.2, 0.1);
		level.sendParticles(dust, end.x, end.y, end.z, laser ? 4 : 10, 0.25, 0.25, 0.25, 0.05);
		level.playSound(null, from.x, from.y, from.z, laser ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE,
				laser ? 0.6f : 1.1f, laser ? 2.0f : 1.6f);
		if (!laser) {
			level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.6f, 1.7f);
		}
	}
}
