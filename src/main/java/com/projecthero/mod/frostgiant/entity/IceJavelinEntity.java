package com.projecthero.mod.frostgiant.entity;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.frostgiant.item.FrostGiantItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>ice javelin</b> -- thrown by Jotun Raiders (9 damage + 60 freeze ticks) and fanned three at a time by King
 * Laufey's Spear of Jotunheim. It flies flat-ish, shatters into shards on whatever it hits, and never hurts a giant.
 */
public class IceJavelinEntity extends ThrowableItemProjectile {
	private float damage = 9.0f;
	private int freezeTicks = 60;

	public IceJavelinEntity(EntityType<? extends IceJavelinEntity> type, Level level) {
		super(type, level);
	}

	/** A javelin from {@code thrower} at {@code from}, flying along {@code velocity}. */
	public static IceJavelinEntity throwFrom(ServerLevel level, LivingEntity thrower, Vec3 from, Vec3 velocity, float damage, int freezeTicks) {
		IceJavelinEntity j = FrostGiantEntityTypes.ICE_JAVELIN.create(level);
		if (j == null) {
			return null;
		}
		j.setOwner(thrower);
		j.setPos(from.x, from.y, from.z);
		j.setDeltaMovement(velocity);
		j.damage = damage;
		j.freezeTicks = freezeTicks;
		double h = velocity.horizontalDistance();
		j.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
		j.setXRot((float) (Math.atan2(velocity.y, h) * (180.0 / Math.PI)));
		level.addFreshEntity(j);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.4f, 0.6f);
		return j;
	}

	@Override
	protected Item getDefaultItem() {
		return FrostGiantItems.ICE_JAVELIN;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.015;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (tickCount % 2 == 0) {
				server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (tickCount > 100) {
				discard();
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return !FrostCombat.isFrost(target) && !(target instanceof IceJavelinEntity) && super.canHitEntity(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel server && result.getEntity() instanceof LivingEntity target) {
			LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
			FrostCombat.hit(server, damageSources().thrown(this, owner), owner, target, damage, freezeTicks);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel server && !isRemoved()) {
			FrostFx.shatter(server, position(), 0.5);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 1.2f);
			discard();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putInt("Freeze", freezeTicks);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
		freezeTicks = tag.getInt("Freeze");
	}
}
