package com.projecthero.mod.syndicate;

import com.projecthero.mod.network.BulletHolePayload;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.14.25: the Syndicate's guns. The Punisher's {@link com.projecthero.mod.firearm.FirearmShooting} is player-only
 * (aiming attachment, recoil, ammo), so the crooks fire through this: a hit-scan ray from the muzzle with some spread,
 * the same tracer / bullet-hole / impact look as the player guns, and shots that pass through other Syndicate members
 * (no friendly fire in the crew). A raised shield blocks a shot like it blocks an arrow.
 */
public final class SyndicateGunfire {
	private SyndicateGunfire() {
	}

	/** Sound / weight of one shot. */
	public enum Report {
		PISTOL(1.7f, 1.0f), SHOTGUN(0.85f, 1.6f), SNIPER(0.7f, 2.2f), CANE(1.25f, 1.1f);

		final float pitch;
		final float volume;

		Report(float pitch, float volume) {
			this.pitch = pitch;
			this.volume = volume;
		}
	}

	/**
	 * Fires {@code pellets} rays from {@code shooter}'s muzzle toward {@code aim}, each spread by up to {@code spread}
	 * (radians-ish), {@code damage} apiece. Returns how many pellets hit a living thing.
	 */
	public static int fire(ServerLevel level, LivingEntity shooter, Vec3 aim, double range, float damage, double spread,
			int pellets, Report report) {
		Vec3 muzzle = muzzle(shooter);
		Vec3 dir = aim.subtract(muzzle).normalize();
		RandomSource r = level.random;
		int hits = 0;
		for (int i = 0; i < pellets; i++) {
			Vec3 d = dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread * 0.6, r.nextGaussian() * spread).normalize();
			if (shot(level, shooter, muzzle, d, range, damage)) {
				hits++;
			}
		}
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.04, 0.04, 0.04, 0.01);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x + dir.x * 0.2, muzzle.y + dir.y * 0.2, muzzle.z + dir.z * 0.2, 2, 0.02, 0.02, 0.02, 0.005);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.4f * report.volume, report.pitch);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.5f * report.volume,
				report.pitch * 1.2f);
		return hits;
	}

	/** Roughly where the gun is: just in front of the right shoulder at eye height. */
	public static Vec3 muzzle(LivingEntity shooter) {
		Vec3 look = shooter.getViewVector(1.0f);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return shooter.getEyePosition().add(look.scale(0.6 * shooter.getScale())).add(right.scale(0.3 * shooter.getScale()))
				.add(0, -0.25 * shooter.getScale(), 0);
	}

	private static boolean shot(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double range, float damage) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, shooter, from, end,
				new AABB(from, end).inflate(1.0), e -> canHit(shooter, e), 0.2f);
		tracer(level, from, entity != null ? entity.getLocation() : end);
		if (entity != null && entity.getEntity() instanceof LivingEntity target) {
			DamageSource source = shooter instanceof net.minecraft.world.entity.player.Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(shooter);
			target.invulnerableTime = 0; // a shotgun's pellets are separate hits
			final float bullet = damage;
			boolean hurt = com.projecthero.mod.firearm.Gunfire.hit(() -> target.hurt(source, bullet)); // v0.14.31: gunfire marker
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getLocation().x, entity.getLocation().y, entity.getLocation().z, 2,
					0.1, 0.1, 0.1, 0.0);
			return hurt;
		}
		if (block.getType() != HitResult.Type.MISS) {
			impact(level, block);
		}
		return false;
	}

	private static boolean canHit(LivingEntity shooter, Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive() || e == shooter || e.isSpectator()) {
			return false;
		}
		if (shooter instanceof SyndicateCriminal && e instanceof SyndicateCriminal) {
			return false; // the crew shoots past each other
		}
		return !(e instanceof ServerPlayer p) || !p.isCreative();
	}

	private static void tracer(ServerLevel level, Vec3 a, Vec3 b) {
		double len = a.distanceTo(b);
		int steps = Math.max(1, (int) (len / 1.5));
		for (int i = 1; i <= steps; i++) {
			Vec3 pt = a.lerp(b, (double) i / (steps + 1));
			level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static void impact(ServerLevel level, BlockHitResult hit) {
		Vec3 loc = hit.getLocation();
		var state = level.getBlockState(hit.getBlockPos());
		if (state.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), loc.x, loc.y, loc.z, 5, 0.1, 0.1, 0.1, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, loc.x, loc.y, loc.z, 1, 0.03, 0.03, 0.03, 0.01);
		BulletHolePayload payload = new BulletHolePayload(loc.x, loc.y, loc.z, hit.getDirection().get3DDataValue());
		for (ServerPlayer viewer : PlayerLookup.around(level, loc, 48.0)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	/** A sniper's laser sight: a thin red line from the muzzle to where the shot will go. */
	public static void laser(ServerLevel level, LivingEntity shooter, Vec3 aim) {
		Vec3 from = muzzle(shooter);
		Vec3 dir = aim.subtract(from);
		double len = Math.min(48, dir.length());
		dir = dir.normalize();
		DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.05f), 0.45f);
		for (double t = 0.5; t < len; t += 0.6) {
			Vec3 p = from.add(dir.scale(t));
			level.sendParticles(red, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}
}
