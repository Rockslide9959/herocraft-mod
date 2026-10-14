package com.projecthero.mod.syndicate;

import com.projecthero.mod.firearm.GunFx;
import com.projecthero.mod.firearm.GunSounds;
import com.projecthero.mod.network.BulletHolePayload;
import com.projecthero.mod.syndicate.entity.SyndicateBulletEntity;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
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
 *
 * <p>v0.16.2: the crooks (gunmen and the Kingpin) now fire real bullets ({@link #fireBullets} /
 * {@link SyndicateBulletEntity}) aimed like a skeleton aims, so they can miss and you can dodge; only the player's
 * Kingpin's Cane still fires the hit-scan {@link #fire}.
 */
public final class SyndicateGunfire {
	private SyndicateGunfire() {
	}

	/** Sound / weight of one shot. */
	public enum Report {
		// legacy pitch / volume (the hit-scan cane), then v0.16.2 the Punisher gun it fires like: GunFx kind, volume, pitch
		PISTOL(1.7f, 1.0f, 0, 3.0f, 1.0f), SHOTGUN(0.85f, 1.6f, 2, 3.5f, 1.0f), SNIPER(0.7f, 2.2f, 3, 4.0f, 1.0f),
		CANE(1.25f, 1.1f, 1, 3.0f, 0.92f);

		final float pitch;
		final float volume;
		/** {@link GunFx} gun kind: 0 pistol, 1 rifle, 2 shotgun, 3 sniper. */
		public final int gunKind;
		final float gunVolume;
		final float gunPitch;

		Report(float pitch, float volume, int gunKind, float gunVolume, float gunPitch) {
			this.pitch = pitch;
			this.volume = volume;
			this.gunKind = gunKind;
			this.gunVolume = gunVolume;
			this.gunPitch = gunPitch;
		}

		/** The Punisher gun's own synthesised report (looked up at fire time, after the sounds are registered). */
		SoundEvent gunSound() {
			return switch (this) {
				case PISTOL -> GunSounds.PISTOL_FIRE;
				case SHOTGUN -> GunSounds.SHOTGUN_FIRE;
				case SNIPER -> GunSounds.SNIPER_FIRE;
				case CANE -> GunSounds.RIFLE_FIRE;
			};
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
		muzzleFx(level, muzzle, dir, report);
		return hits;
	}

	/**
	 * Vanilla skeleton inaccuracy for a difficulty ({@code AbstractSkeleton.performRangedAttack}: 14 - 4 x difficulty,
	 * so Easy 10, Normal 6, Hard 2), the base every Syndicate gun scales.
	 */
	public static float skeletonInaccuracy(Difficulty difficulty) {
		return Math.max(0, 14 - difficulty.getId() * 4);
	}

	/**
	 * v0.16.2: fires {@code pellets} real bullets from {@code shooter}'s muzzle straight at {@code aim} (where the target
	 * is now: no lead, like a skeleton), each scattered by vanilla {@code inaccuracy} and flying {@code speed} blocks a
	 * tick until it hits something or has flown {@code range}. Returns how many bullets were spawned.
	 */
	public static int fireBullets(ServerLevel level, LivingEntity shooter, Vec3 aim, double range, float damage, float speed,
			float inaccuracy, int pellets, Report report) {
		Vec3 muzzle = muzzle(shooter);
		Vec3 dir = aim.subtract(muzzle);
		if (dir.lengthSqr() < 1.0e-6) {
			dir = shooter.getViewVector(1.0f);
		}
		dir = dir.normalize();
		int spawned = 0;
		for (int i = 0; i < pellets; i++) {
			if (SyndicateBulletEntity.fire(level, shooter, muzzle, dir, speed, inaccuracy, damage, range, report.gunKind) != null) {
				spawned++;
			}
		}
		// v0.16.2: the Punisher's gun report (near + distant), muzzle smoke / sparks, casing and pump / bolt, and the
		// muzzle flash on the gun in his hand (SyndicateCriminal.EVENT_GUNSHOT)
		GunFx.shot(level, shooter, report.gunKind, report.gunSound(), report.gunVolume, report.gunPitch, muzzle, dir, SoundSource.HOSTILE);
		if (shooter instanceof SyndicateCriminal) {
			level.broadcastEntityEvent(shooter, SyndicateCriminal.EVENT_GUNSHOT);
		}
		return spawned;
	}

	private static void muzzleFx(ServerLevel level, Vec3 muzzle, Vec3 dir, Report report) {
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.04, 0.04, 0.04, 0.01);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x + dir.x * 0.2, muzzle.y + dir.y * 0.2, muzzle.z + dir.z * 0.2, 2, 0.02, 0.02, 0.02, 0.005);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.4f * report.volume, report.pitch);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.5f * report.volume,
				report.pitch * 1.2f);
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
			Vec3 at = entity.getLocation();
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
			return strike(level, shooter, target, damage);
		}
		if (block.getType() != HitResult.Type.MISS) {
			impact(level, block);
		}
		return false;
	}

	/**
	 * One bullet landing on {@code target}: ordinary attack damage from {@code shooter} (generic if the shooter is gone)
	 * inside the {@code Gunfire.hit} marker, so shields block it and bulletproof armour knows it was a gun. Pellets are
	 * separate hits (no invulnerability window between them). Returns whether it hurt.
	 */
	public static boolean strike(ServerLevel level, LivingEntity shooter, LivingEntity target, float damage) {
		DamageSource source = shooter instanceof net.minecraft.world.entity.player.Player p ? level.damageSources().playerAttack(p)
				: shooter != null ? level.damageSources().mobAttack(shooter) : level.damageSources().generic();
		target.invulnerableTime = 0; // a shotgun's pellets are separate hits
		return com.projecthero.mod.firearm.Gunfire.hit(() -> target.hurt(source, damage)); // v0.14.31: gunfire marker
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

	/**
	 * v0.16.2: a Syndicate bullet hitting a block, the Punisher's way: chips, dust, sparks and a thud (sometimes a
	 * ricochet) from {@link GunFx#impactBlock}, the bullet hole, and the tracer impact flash.
	 */
	public static void bulletImpact(ServerLevel level, BlockHitResult hit, int shooterId, int gunKind, Vec3 dir) {
		Vec3 loc = hit.getLocation();
		if (level.getBlockState(hit.getBlockPos()).isAir()) {
			return;
		}
		GunFx.impactBlock(level, loc, hit.getBlockPos());
		BulletHolePayload payload = new BulletHolePayload(loc.x, loc.y, loc.z, hit.getDirection().get3DDataValue());
		for (ServerPlayer viewer : PlayerLookup.around(level, loc, 64.0)) {
			ServerPlayNetworking.send(viewer, payload);
		}
		GunFx.tracer(level, shooterId, gunKind, loc.subtract(dir.scale(0.05)), loc, 1);
	}

	/** v0.16.2: a Syndicate bullet striking a creature: the Punisher's wet hit and red spray, and the impact flash. */
	public static void bulletFlesh(ServerLevel level, LivingEntity target, Vec3 at, int shooterId, int gunKind, Vec3 dir) {
		GunFx.impactFlesh(level, at, target);
		GunFx.tracer(level, shooterId, gunKind, at.subtract(dir.scale(0.05)), at, 2);
	}

	/** A hit-scan shot hitting a block: debris, a puff of smoke and a bullet hole. */
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
