package com.projecthero.mod.darkseid.entity;

import com.projecthero.mod.darkseid.DarkseidDamage;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Ranged Parademon's energy blast: a straight, fast, non-homing bolt. Aimed with a velocity lead so it is a
 * real threat to a player flying in a straight line, and still dodgeable by anyone who changes course.
 */
public class ParademonBoltEntity extends EnergyProjectile {
	private float damage = 5.0f;

	public ParademonBoltEntity(EntityType<? extends ParademonBoltEntity> type, Level level) {
		super(type, level);
	}

	public static void fire(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, float damage, double speed) {
		ParademonBoltEntity bolt = new ParademonBoltEntity(DarkseidEntityTypes.PARADEMON_BOLT, level);
		bolt.setOwner(shooter);
		bolt.damage = damage;
		bolt.setPos(from.x, from.y, from.z);
		bolt.setDeltaMovement(dir.normalize().scale(speed));
		bolt.faceAlong(bolt.getDeltaMovement());
		level.addFreshEntity(bolt);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.7f, 1.6f);
	}

	@Override
	protected int maxLife() {
		return 70;
	}

	@Override
	protected void steer(ServerLevel level) {
		// straight line -- the aim lead at launch is all the "tracking" it gets
	}

	@Override
	protected boolean canHit(Entity target) {
		return target != getOwner() && !(target instanceof EnergyProjectile) && DarkseidDamage.isValidVictim(target);
	}

	@Override
	protected void onStrikeEntity(ServerLevel level, Entity target) {
		if (target instanceof LivingEntity living) {
			Entity owner = getOwner();
			living.hurt(level.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
		}
		pop(level);
	}

	@Override
	protected void onStrikeBlock(ServerLevel level, Vec3 at) {
		setPos(at.x, at.y, at.z);
		pop(level);
	}

	private void pop(ServerLevel level) {
		level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.04);
		level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.5f, 1.8f);
	}

	@Override
	public int trailColor() {
		return 0xFF7A10;
	}

	@Override
	public int coreColor() {
		return 0xFFB040;
	}

	@Override
	public float trailWidth() {
		return 0.1f;
	}
}
