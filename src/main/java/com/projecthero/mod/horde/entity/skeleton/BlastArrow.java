package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: a Bone Bomber's arrow -- shot burning, trailing smoke, and bursting where it lands (2.5 blocks,
 * {@link HordeSkeleton#blast}: only the horde's enemies are hurt, no blocks break). Never picked up, never saved.
 */
public class BlastArrow extends AbstractArrow {
	public static final double RADIUS = 2.5;
	private float blastDamage = 6.0f;
	private boolean burst;

	public BlastArrow(EntityType<? extends BlastArrow> type, Level level) {
		super(type, level);
		this.pickup = Pickup.DISALLOWED;
	}

	public BlastArrow(Level level, LivingEntity owner, float blastDamage) {
		super(SkeletonHordeEntityTypes.BLAST_ARROW, owner, level, new ItemStack(Items.ARROW), null);
		this.pickup = Pickup.DISALLOWED;
		this.blastDamage = blastDamage;
		setRemainingFireTicks(2000);
	}

	public float blastDamage() {
		return blastDamage;
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.ARROW);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && !inGround && tickCount % 2 == 0) {
			server.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
			server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		burst(result.getLocation());
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		burst(result.getLocation());
	}

	/** Goes off at {@code at} (once). */
	public void burst(Vec3 at) {
		if (burst || !(level() instanceof ServerLevel server)) {
			return;
		}
		burst = true;
		HordeSkeleton.blast(server, getOwner() instanceof LivingEntity l ? l : null, at, RADIUS, blastDamage, 0.7);
		discard();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("BlastDamage", blastDamage);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		blastDamage = tag.getFloat("BlastDamage");
	}
}
