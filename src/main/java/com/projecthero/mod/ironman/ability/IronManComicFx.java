package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 comic kit: the shared building blocks every comic move is made of -- aiming (the targeting lock first), beams,
 * cones, spheres, missiles, knockback, particles. Server side only. Every hit goes through {@link #hit} (the shared
 * {@code HeroTargets} rules: never the wearer, a squadmate, a pet; players only with ability PvP on).
 */
final class IronManComicFx {
	/** Beam kinds drawn by the client's {@code IronManAbilityVisuals} (0-5 are the original ones). */
	static final int BEAM_REPULSOR = 0;
	static final int BEAM_CHARGED = 1;
	static final int BEAM_UNIBEAM = 2;
	static final int BEAM_LASER = 3;
	static final int BEAM_PULSE = 6;
	static final int BEAM_LIGHT = 7;
	static final int BEAM_TRACTOR = 8;
	static final int BEAM_SOLAR = 9;
	static final int BEAM_STEALTH = 10;
	static final int BEAM_MEGA = 11;

	private IronManComicFx() {
	}

	static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	/** Look direction, pointed at the targeting lock when there is one in range. */
	static Vec3 aim(ServerPlayer p, double range) {
		return IronManTargeting.aimLook(p, range);
	}

	/** The right palm, a little in front of the shoulder. */
	static Vec3 palm(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(p, look);
		double s = p.getScale();
		return p.getEyePosition().add(look.scale(0.6 * s)).add(right.scale(0.35 * s)).add(0, -0.35 * s, 0);
	}

	/** The chest (the arc reactor / Unibeam emitter). */
	static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.68, 0).add(p.getLookAngle().scale(0.4 * p.getScale()));
	}

	/** What the wearer is aiming at: the lock, else the entity under the crosshair (with a small margin), else null. */
	static LivingEntity target(ServerPlayer p, double range) {
		LivingEntity locked = IronManTargeting.lockedWithin(p, range);
		if (locked != null) {
			return locked;
		}
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && HeroTargets.canHarm(p, direct)) {
			return direct;
		}
		// a forgiving pick: the nearest harmable thing within 1 block of the look ray
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double wall = wallDistance(p, eye, look, range);
		LivingEntity best = null;
		double bestT = Double.MAX_VALUE;
		for (LivingEntity e : around(p, eye.add(look.scale(range * 0.5)), range * 0.5 + 2.0)) {
			Vec3 c = centre(e);
			double t = c.subtract(eye).dot(look);
			if (t < 0 || t > wall + 0.5) {
				continue;
			}
			double off = c.distanceTo(eye.add(look.scale(t)));
			if (off <= 1.0 + e.getBbWidth() * 0.5 && t < bestT) {
				bestT = t;
				best = e;
			}
		}
		return best;
	}

	static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** How far along {@code dir} from {@code from} the first solid block is (or {@code range}). */
	static double wallDistance(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? range : from.distanceTo(hit.getLocation());
	}

	/** The point the wearer is aiming at (entity, block face or max range). */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		LivingEntity t = target(p, range);
		if (t != null) {
			return t.position();
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		return eye.add(look.scale(wallDistance(p, eye, look, range)));
	}

	/** The ground (or water surface) at or below {@code at}, within 12 blocks; else {@code at}. */
	static Vec3 groundAt(ServerPlayer p, Vec3 at) {
		BlockHitResult hit = p.level().clip(new ClipContext(at.add(0, 0.5, 0), at.subtract(0, 12, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, p));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
	}

	// ------------------------------------------------------------------ targets

	/** Everything harmable around {@code centre} (sphere to the hit-box), never the wearer. */
	static List<LivingEntity> around(ServerPlayer p, Vec3 centre, double radius) {
		List<LivingEntity> out = new ArrayList<>(AbilityHelpers.enemiesAround(p, centre, radius));
		out.remove(p);
		return out;
	}

	/** Everything harmable in a cone of {@code halfDeg} degrees along {@code dir} from {@code from}, out to {@code range}. */
	static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double halfDeg) {
		double cos = Math.cos(Math.toRadians(halfDeg));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, from, range)) {
			Vec3 to = centre(e).subtract(from);
			double len = to.length();
			if (len < 0.8 || to.normalize().dot(dir) >= cos) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
		return out;
	}

	/** Everything harmable within {@code width} of the segment {@code a}-{@code b}, nearest first. */
	static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double width) {
		Vec3 ab = b.subtract(a);
		double len = ab.length();
		if (len < 1.0e-4) {
			return List.of();
		}
		Vec3 dir = ab.scale(1.0 / len);
		AABB box = new AABB(a, b).inflate(width + 1.5);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive()
				&& HeroTargets.canHarm(p, e))) {
			Vec3 c = centre(e);
			double t = Math.max(0, Math.min(len, c.subtract(a).dot(dir)));
			double off = c.distanceTo(a.add(dir.scale(t)));
			if (off <= width + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.25) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(a)));
		return out;
	}

	// ------------------------------------------------------------------ damage / motion

	/** One hit of {@code damage} (lands even inside the target's hit cooldown -- a one-shot burst). */
	static boolean hit(ServerPlayer p, LivingEntity e, float damage) {
		return damage > 0f && AbilityHelpers.hurtBurst(p, e, damage);
	}

	/** A fire hit (sets it burning for {@code fireTicks}). */
	static boolean burn(ServerPlayer p, LivingEntity e, float damage, int fireTicks) {
		boolean landed = damage <= 0f || AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage);
		if (fireTicks > 0 && HeroTargets.canHarm(p, e)) {
			e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), fireTicks));
		}
		return landed;
	}

	/** Shove {@code e} away from {@code from} horizontally by {@code strength}, plus {@code up}. */
	static void shove(LivingEntity e, Vec3 from, double strength, double up) {
		Vec3 away = e.position().subtract(from);
		Vec3 flat = new Vec3(away.x, 0, away.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : flat.normalize();
		double resist = 1.0 - Math.min(0.9, e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.6);
		e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0.3, 0.3).add(flat.scale(strength * resist)).add(0, up * resist, 0));
		e.hurtMarked = true;
		e.hasImpulse = true;
	}

	/** Pull {@code e} toward {@code to} at {@code speed} blocks/tick. */
	static void pull(net.minecraft.world.entity.Entity e, Vec3 to, double speed) {
		Vec3 d = to.subtract(e.position());
		double len = d.length();
		if (len < 0.6) {
			e.setDeltaMovement(e.getDeltaMovement().scale(0.5));
		} else {
			e.setDeltaMovement(d.scale(Math.min(speed, len * 0.5) / len).add(0, 0.08, 0));
		}
		e.hurtMarked = true;
		e.hasImpulse = true;
	}

	/** Apply a (player-softened) control effect. */
	static void control(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
		AbilityHelpers.applyControl(e, effect, ticks, amp);
	}

	/** Launch the wearer with an exact velocity (synced to their client). */
	static void launch(ServerPlayer p, Vec3 velocity) {
		AbilityHelpers.launchSelf(p, velocity);
	}

	// ------------------------------------------------------------------ beams

	/**
	 * A hit-scan beam from {@code from} along {@code dir}: stops at the first wall; hits the first enemy within
	 * {@code width} of the line (or every one when {@code pierce}). Draws it for everyone. Returns what it hit.
	 */
	static List<LivingEntity> beam(ServerPlayer p, Vec3 from, Vec3 dir, double range, double width, boolean pierce, int kind,
			float damage, double knock) {
		dir = dir.normalize();
		double len = wallDistance(p, from, dir, range);
		Vec3 end = from.add(dir.scale(len));
		List<LivingEntity> hits = along(p, from, end, width);
		if (!pierce && !hits.isEmpty()) {
			hits = List.of(hits.get(0));
			end = centre(hits.get(0));
		}
		IronManAbilities.broadcastBeam(p, from, end, kind);
		for (LivingEntity e : hits) {
			if (hit(p, e, damage) && knock > 0) {
				shove(e, from, knock, 0.2);
			}
		}
		AbilityHelpers.burst(level(p), end, ParticleTypes.ELECTRIC_SPARK, 8, 0.25);
		return hits;
	}

	/** A single-target palm repulsor shot at the aim (the lock first). */
	static LivingEntity repulsor(ServerPlayer p, float damage, double range, int kind) {
		Vec3 from = palm(p);
		List<LivingEntity> hits = beam(p, from, IronManTargeting.aim(p, from, p.getLookAngle(), range), range, 0.6, false, kind,
				damage, 0.9);
		IronManAbilityFx.play(p, IronManAbilityFx.REPULSOR, 10);
		IronManSounds.move(p, IronManSounds.REPULSOR_BLAST, 0.9f, 1.0f + p.getRandom().nextFloat() * 0.1f);
		LivingEntity e = hits.isEmpty() ? null : hits.get(0);
		if (e != null) {
			com.projecthero.mod.ironman.IronManCombo.onRepulsorHit(p, e);
		}
		return e;
	}

	// ------------------------------------------------------------------ missiles

	static IronManMissileEntity missile(ServerPlayer p, Vec3 from, Vec3 dir, double speed, float direct, float splash, float radius) {
		IronManMissileEntity m = new IronManMissileEntity(p.level(), p, dir.normalize().scale(speed)).withDamage(direct, splash)
				.withBlastRadius(radius);
		m.setPos(from.x, from.y, from.z);
		return m;
	}

	static void spawn(ServerPlayer p, IronManMissileEntity m) {
		p.level().addFreshEntity(m);
	}

	/** A shoulder launch point (alternating sides by {@code i}). */
	static Vec3 shoulder(ServerPlayer p, int i) {
		Vec3 right = IronManAbilities.rightOf(p, p.getLookAngle());
		double s = p.getScale();
		return p.getEyePosition().add(0, 0.1 * s, 0).add(right.scale((i % 2 == 0 ? 0.45 : -0.45) * s));
	}

	/** Up to {@code n} hostiles in front of the wearer within {@code range}: the lock first, then nearest. */
	static List<LivingEntity> frontTargets(ServerPlayer p, double range, int n) {
		List<LivingEntity> out = new ArrayList<>();
		LivingEntity lock = IronManTargeting.lockedWithin(p, range);
		if (lock != null) {
			out.add(lock);
		}
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.getEyePosition(), range)) {
			if (out.size() >= n) {
				break;
			}
			if (out.contains(e) || e == p) {
				continue;
			}
			Vec3 to = centre(e).subtract(p.getEyePosition());
			if (to.normalize().dot(p.getLookAngle()) > 0.2) {
				out.add(e);
			}
		}
		if (out.isEmpty()) {
			LivingEntity aimed = target(p, range);
			if (aimed != null) {
				out.add(aimed);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ particles / sound

	static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int points) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** A ring of the ground's own block chunks thrown up (slams, stomps). */
	static void groundRing(ServerLevel level, Vec3 c, double radius, int points) {
		BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.2, c.z));
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(chunk, c.x + Math.cos(a) * radius, c.y + 0.1, c.z + Math.sin(a) * radius, 3, 0.15, 0.1, 0.15, 0.15);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
	}

	static void cloud(ServerLevel level, Vec3 c, ParticleOptions particle, int count, double spread, double speed) {
		level.sendParticles(particle, c.x, c.y, c.z, count, spread, spread, spread, speed);
	}

	static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double perBlock) {
		AbilityHelpers.line(level, a, b, particle, perBlock);
	}

	static void sound(ServerPlayer p, SoundEvent s, float volume, float pitch) {
		IronManSounds.move(p, s, volume, pitch);
	}

	static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	static void soundAt(ServerLevel level, Vec3 at, net.minecraft.core.Holder<SoundEvent> s, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	static void pose(ServerPlayer p, int anim, int ticks) {
		IronManAbilityFx.play(p, anim, ticks);
	}
}
