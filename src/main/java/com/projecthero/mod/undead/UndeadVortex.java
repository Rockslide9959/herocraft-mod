package com.projecthero.mod.undead;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Z (user: "give this a strong ability from what he does in the show and manga"): Ten Twists -- Vortex Bullet. He holds
 * the arm out and wrenches it round ten times against itself ({@link UndeadKitConfig#VORTEX_WINDUP}, the arm visibly twisting:
 * {@link UndeadKitConfig#ANIM_VORTEX_TWIST}); let go, it untwists off his shoulder as a spinning drill that bores through every
 * creature on a {@link UndeadKitConfig#VORTEX_RANGE}-block line ({@link UndeadKitConfig#VORTEX_DAMAGE} each, flung out of the way)
 * and bursts where it stops -- the end of its range or the wall it buries itself in -- blowing everything near away. No blocks
 * are broken. The arm grows back as usual. Left and right arms take turns.
 */
public final class UndeadVortex {
	/** The wind-up: since when, which arm. */
	private record Windup(long start, int arm) {
	}

	/** The drill in flight: the entity, where it set off, which way, where it was last tick, who it has bored through. */
	private static final class Flight {
		final UndeadPartEntity drill;
		final Vec3 origin;
		final Vec3 dir;
		Vec3 last;
		final Set<Integer> hit = new HashSet<>();
		boolean ended;

		Flight(UndeadPartEntity drill, Vec3 origin, Vec3 dir) {
			this.drill = drill;
			this.origin = origin;
			this.dir = dir;
			this.last = origin;
		}
	}

	private static final Map<UUID, Windup> WINDUPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Flight> FLIGHTS = new ConcurrentHashMap<>();

	private UndeadVortex() {
	}

	static void clearSessionState() {
		WINDUPS.clear();
		FLIGHTS.clear();
	}

	static void forget(UUID id) {
		WINDUPS.remove(id);
		FLIGHTS.remove(id);
	}

	public static boolean windingUp(ServerPlayer p) {
		return WINDUPS.containsKey(p.getUUID());
	}

	/** Z: starts the ten twists (false, having said why, if he can't). */
	public static boolean start(ServerPlayer p) {
		if (!Undead.canAct(p) || WINDUPS.containsKey(p.getUUID())) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, UndeadAbilities.VORTEX);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + UndeadAbilities.VORTEX),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		int arm = Undead.shootingArm(p);
		if (arm < 0) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
			return false;
		}
		UndeadAbilities.cancelPunch(p);
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_OFF_ARM, arm != Undead.mainArm(p));
		n.setFlag(UndeadState.FLAG_NEXT_OFF, arm == Undead.mainArm(p)); // the next one comes from the other arm
		n.animId = UndeadKitConfig.ANIM_VORTEX_TWIST;
		n.animStart = now;
		Undead.save(p, n);
		WINDUPS.put(p.getUUID(), new Windup(now, arm));
		UndeadSfx.punchStretch(p.level(), UndeadParts.centre(p, arm));
		return true;
	}

	/** Every tick (from {@link UndeadBoneSword#tick}): the twists, then the shot; the drill's flight. */
	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		Windup w = WINDUPS.get(p.getUUID());
		if (w != null) {
			UndeadState s = Undead.state(p);
			if (!s.armReady(w.arm(), now) || s.reformUntil > now || !p.isAlive()) {
				cancel(p); // lost the arm (or re-forming) mid-twist
			} else {
				long age = now - w.start();
				ServerLevel level = (ServerLevel) p.level();
				Vec3 at = UndeadAbilities.handOrigin(p, w.arm());
				// a creak of sinew at each twist, tighter and higher; blood wrung out of it
				int twist = (int) (age * UndeadKitConfig.VORTEX_TWISTS / UndeadKitConfig.VORTEX_WINDUP);
				int prev = (int) ((age - 1) * UndeadKitConfig.VORTEX_TWISTS / UndeadKitConfig.VORTEX_WINDUP);
				if (age > 0 && twist != prev) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.18f + 0.03f * twist, 0.6f + 0.09f * twist);
					if (twist % 3 == 0) {
						UndeadSfx.punchStretch(level, at);
					}
				}
				// (the blood wrung out of it is the clients' own: UndeadKitFx; dust this close would fill a first-person view)
				if (age >= UndeadKitConfig.VORTEX_WINDUP) {
					WINDUPS.remove(p.getUUID());
					fire(p, w.arm());
				}
			}
		}
		Flight f = FLIGHTS.get(p.getUUID());
		if (f != null) {
			tickFlight(p, f);
		}
	}

	/** The wind-up broken off: no shot, no cooldown. */
	public static void cancel(ServerPlayer p) {
		if (WINDUPS.remove(p.getUUID()) == null) {
			return;
		}
		UndeadState s = Undead.state(p);
		if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST) {
			UndeadState n = s.copy();
			n.animId = UndeadState.ANIM_NONE;
			Undead.save(p, n);
		}
	}

	/** The arm springs off, untwisting, as the drill. */
	static void fire(ServerPlayer p, int arm) {
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(UndeadAbilities.VORTEX, now + UndeadKitConfig.VORTEX_COOLDOWN);
		n.animId = UndeadKitConfig.ANIM_VORTEX_FIRE;
		n.animStart = now;
		Undead.save(p, n);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : -1;
		Vec3 from = p.getEyePosition().add(r.scale(0.36 * side)).add(0, -0.3, 0).add(p.getLookAngle().scale(0.4));
		// at the far end of the look (or the wall it meets), so the line runs true down a row instead of drifting off it
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(UndeadKitConfig.VORTEX_RANGE));
		net.minecraft.world.phys.BlockHitResult bhr = p.level().clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : bhr.getLocation();
		Vec3 dir = end.distanceToSqr(from) < 4.0 ? p.getLookAngle() : end.subtract(from).normalize();
		UndeadPartEntity drill = UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_DRILL, arm, from, dir.scale(UndeadKitConfig.VORTEX_SPEED),
				UndeadKitConfig.VORTEX_DAMAGE);
		FLIGHTS.put(p.getUUID(), new Flight(drill, from, dir));
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.6f, 0.5f);
		UndeadSfx.launch(level, from);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, from.x + dir.x * 2.5, from.y + dir.y * 2.5, from.z + dir.z * 2.5, 1, 0, 0, 0, 0);
		UndeadKitNet.fx(level, UndeadKitNet.VORTEX_FIRE, from, dir, 0);
	}

	private static void tickFlight(ServerPlayer p, Flight f) {
		if (f.ended) {
			FLIGHTS.remove(p.getUUID());
			return;
		}
		UndeadPartEntity d = f.drill;
		Vec3 pos = d.isRemoved() ? f.last : d.position().add(0, d.getBbHeight() * 0.5, 0);
		bore(p, f, f.last, pos);
		f.last = pos;
		double gone = pos.subtract(f.origin).dot(f.dir);
		if (d.isRemoved()) {
			end(p, f, pos, false);
		} else if (d.stuck()) {
			end(p, f, pos, false); // buried in a wall: it stays there; the burst goes off at its tip
		} else if (gone >= UndeadKitConfig.VORTEX_RANGE) {
			end(p, f, pos, true);
		} else if (p.level().getGameTime() % 2 == 0 && gone > 3.0) {
			((ServerLevel) p.level()).sendParticles(Undead.BLOOD, pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.0);
		}
	}

	/** Everything within reach of the line from {@code a} to {@code b} it has not bored through yet. */
	private static void bore(ServerPlayer p, Flight f, Vec3 a, Vec3 b) {
		Vec3 seg = b.subtract(a);
		double len = seg.length();
		AABB box = new AABB(a, b).inflate(UndeadKitConfig.VORTEX_RADIUS + 1.0);
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && !(e instanceof UndeadCloneEntity)
				&& !f.hit.contains(e.getId()) && HeroTargets.canHarm(p, e))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = len < 1.0e-6 ? 0 : Math.max(0, Math.min(len, c.subtract(a).dot(seg) / len));
			Vec3 near = len < 1.0e-6 ? a : a.add(seg.scale(t / len));
			double reach = UndeadKitConfig.VORTEX_RADIUS + e.getBbWidth() * 0.5;
			// (the box's height too: a tall mob is hit anywhere along its body)
			double dy = Math.max(0, Math.abs(near.y - c.y) - e.getBbHeight() * 0.5);
			double dxz = Math.hypot(near.x - c.x, near.z - c.z);
			if (Math.hypot(dxz, dy) <= reach) {
				pierce(p, f, e, near);
			}
		}
	}

	/** From {@link UndeadPartEntity#onHitEntity}: the drill touched {@code le} (it bores through, it is not stopped). */
	public static void entityHit(ServerPlayer p, UndeadPartEntity drill, LivingEntity le) {
		Flight f = FLIGHTS.get(p.getUUID());
		if (f == null || f.drill != drill) {
			// an old drill (a second one fired after this one): just the hit
			UndeadAbilities.strike(p, le, drill.position().subtract(drill.getDeltaMovement()), UndeadKitConfig.VORTEX_DAMAGE, UndeadKitConfig.VORTEX_KNOCK,
					UndeadKitConfig.VORTEX_LIFT);
			return;
		}
		pierce(p, f, le, drill.position().add(0, drill.getBbHeight() * 0.5, 0));
	}

	private static void pierce(ServerPlayer p, Flight f, LivingEntity e, Vec3 near) {
		if (!f.hit.add(e.getId())) {
			return;
		}
		// the shove: away from a point just behind it on the line -- forward and out of the way
		Vec3 from = near.subtract(f.dir.scale(1.2));
		UndeadAbilities.strike(p, e, from, UndeadKitConfig.VORTEX_DAMAGE, UndeadKitConfig.VORTEX_KNOCK, UndeadKitConfig.VORTEX_LIFT);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		level.sendParticles(Undead.BLOOD_BIG, c.x, c.y, c.z, 6, 0.2, 0.25, 0.2, 0.0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 0.6f);
		UndeadSfx.splat(level, c);
	}

	/** It stops: the burst. {@code spent}: out of range in the air, so the arm bursts with it. */
	private static void end(ServerPlayer p, Flight f, Vec3 at, boolean spent) {
		f.ended = true;
		ServerLevel level = (ServerLevel) p.level();
		double r = UndeadKitConfig.VORTEX_BURST_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> e != p && !(e instanceof UndeadCloneEntity)
				&& HeroTargets.canHarm(p, e) && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(at) <= (r + 0.5) * (r + 0.5))) {
			UndeadAbilities.strike(p, e, at.subtract(0, 0.6, 0), UndeadKitConfig.VORTEX_BURST_DAMAGE, UndeadKitConfig.VORTEX_BURST_KNOCK,
					UndeadKitConfig.VORTEX_BURST_LIFT);
		}
		level.sendParticles(Undead.BLOOD_BIG, at.x, at.y, at.z, 18, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 4, 0.8, 0.3, 0.8, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55f, 1.35f);
		UndeadSfx.bloodBurst(level, at, 1.0f);
		UndeadKitNet.fx(level, UndeadKitNet.VORTEX_BURST, at, f.dir, 0);
		if (spent && !f.drill.isRemoved()) {
			f.drill.discard();
		}
		FLIGHTS.remove(p.getUUID());
	}
}
