package com.projecthero.mod.omnitrix.alien.arctiguana;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.17.4 Arctiguana's Ice Decoy (Shift+V): an ice statue of him, left where he stood. A real (if motionless) creature, so mobs can be
 * set on it and hit it: the hostile mobs round it go for it for 5 s ({@link ArctiguanaKit}), and it bursts into frost when it is broken or
 * its time runs out. Never saved; gone the moment its owner is no longer Arctiguana. Drawn as his body in translucent ice
 * ({@code ArctiguanaDecoyRenderer}).
 */
public class ArctiguanaDecoyEntity extends LivingEntity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ArctiguanaDecoyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(ArctiguanaDecoyEntity.class, EntityDataSerializers.FLOAT);
	public static final float MAX_HP = 20f;

	private int life = ArctiguanaKit.DECOY_TICKS;
	private boolean burst;

	public ArctiguanaDecoyEntity(EntityType<? extends ArctiguanaDecoyEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** A statue of {@code owner} where he stands, facing his way. */
	public static ArctiguanaDecoyEntity spawn(ServerPlayer owner) {
		ArctiguanaDecoyEntity e = new ArctiguanaDecoyEntity(ArctiguanaKit.DECOY, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(YAW, owner.yBodyRot);
		e.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.yBodyRot, 0f);
		e.yBodyRot = owner.yBodyRot;
		e.yHeadRot = owner.yBodyRot;
		owner.level().addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, -1);
		builder.define(YAW, 0f);
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	/** The way the statue faces (his body yaw when it was made). */
	public float statueYaw() {
		return entityData.get(YAW);
	}

	@Nullable
	public ServerPlayer owner() {
		return level().getEntity(ownerId()) instanceof ServerPlayer p ? p : null;
	}

	@Override
	public void tick() {
		super.tick();
		yBodyRot = statueYaw();
		yHeadRot = statueYaw();
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (level().isClientSide) {
			return;
		}
		ServerPlayer o = owner();
		if (o == null || !ArctiguanaKit.isArctiguana(o) || o.level() != level()) {
			discard();
			return;
		}
		if (--life <= 0) {
			shatter(o);
		}
	}

	@Override
	public void die(DamageSource source) {
		ServerPlayer o = owner();
		if (o != null && !level().isClientSide) {
			shatter(o);
			return;
		}
		super.die(source);
	}

	/** It bursts into frost (once): the kit's burst, then it is gone. */
	private void shatter(ServerPlayer o) {
		if (burst) {
			return;
		}
		burst = true;
		ArctiguanaKit.decoyBurst(o, this);
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GLASS_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return NonNullList.withSize(4, ItemStack.EMPTY);
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}
}
