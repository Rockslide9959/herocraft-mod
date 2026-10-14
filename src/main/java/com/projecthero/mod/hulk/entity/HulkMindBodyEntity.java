package com.projecthero.mod.hulk.entity;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * v0.16.4 (user): "back in the normal world the player's body must still be there for other players to see". While a
 * player is in his own mind ({@code HulkMind}) his real entity is in another dimension; this stands in for him where he
 * knelt -- drawn with his own skin (wide or slim, {@code HulkMindBodyRenderer}), on his knees, both hands on his head,
 * looking down, shaking. It cannot be hurt, pushed or blown away, is never saved, and removes itself the moment its
 * owner's mind session is gone ({@code HulkMind#bodyLive}).
 */
public class HulkMindBodyEntity extends LivingEntity {
	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(HulkMindBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	public HulkMindBodyEntity(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
		setInvulnerable(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	public void setOwner(UUID owner) {
		this.entityData.set(OWNER, Optional.of(owner));
	}

	public Optional<UUID> ownerId() {
		return this.entityData.get(OWNER);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel && this.tickCount % 5 == 0
				&& !com.projecthero.mod.hulk.HulkMind.bodyLive(ownerId().orElse(null), getUUID())) {
			discard(); // the owner came back (or left the game): no orphans
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(net.minecraft.world.entity.Entity entity) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return NonNullList.withSize(4, ItemStack.EMPTY);
	}

	@Override
	public Iterable<ItemStack> getHandSlots() {
		return Collections.emptyList();
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
