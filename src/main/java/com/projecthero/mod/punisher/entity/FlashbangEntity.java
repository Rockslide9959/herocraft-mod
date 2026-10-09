package com.projecthero.mod.punisher.entity;

import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.ability.PunisherSmoke;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: the Punisher's Flashbang (Shift+C). Thrown like the Frag Grenade and, like it, sticks to the first surface
 * it touches; after {@link PunisherConfig#FLASHBANG_FUSE_TICKS} it goes off ({@link PunisherSmoke#flash}). Drawn as a
 * small grey canister (the firework-star item). It does no damage and never breaks blocks.
 */
public class FlashbangEntity extends ThrowableItemProjectile {
	private int fuse = PunisherConfig.FLASHBANG_FUSE_TICKS;
	private boolean stuck;

	public FlashbangEntity(EntityType<? extends FlashbangEntity> type, Level level) {
		super(type, level);
	}

	public FlashbangEntity(Level level, LivingEntity thrower) {
		super(PunisherEntityTypes.FLASHBANG, thrower, level);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIREWORK_STAR;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.045;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (tickCount % 3 == 0) {
				level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
			}
			return;
		}
		if (stuck) {
			setDeltaMovement(Vec3.ZERO);
			setNoGravity(true);
			hasImpulse = false;
		}
		if (--fuse <= 0 && isAlive()) {
			PunisherSmoke.flash((ServerLevel) level(), position(), getOwner());
			discard();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (stuck) {
			return;
		}
		boolean audible = getDeltaMovement().lengthSqr() > 0.02;
		Direction d = hit.getDirection();
		Vec3 at = hit.getLocation().add(d.getStepX() * 0.06, d.getStepY() * 0.06, d.getStepZ() * 0.06);
		setPos(at.x, at.y, at.z);
		stuck = true;
		setDeltaMovement(Vec3.ZERO);
		setNoGravity(true);
		hasImpulse = false;
		if (audible) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.3f, 1.8f);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (!stuck) {
			setDeltaMovement(0.0, -0.2, 0.0);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Fuse", fuse);
		tag.putBoolean("Stuck", stuck);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Fuse")) {
			fuse = tag.getInt("Fuse");
		}
		stuck = tag.getBoolean("Stuck");
	}
}
