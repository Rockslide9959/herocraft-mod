package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 (the Skeleton Horde's armoured zombie): a tarnished, blue-eyed skeleton in full iron with an axe and a tower
 * shield. Slow and heavy, and its shield is a wall: <b>every arrow or other projectile that comes at it from the front
 * glances off</b>, whether the shield is up or not -- flank it or close in. While its target is more than four blocks
 * away it advances behind the raised shield; up close it lowers it to swing.
 */
public class BoneKnight extends HordeSkeleton {
	/** Half-angle of the shield's cover, degrees either side of where it faces. */
	static final double SHIELD_HALF_ANGLE = 75.0;

	public BoneKnight(EntityType<? extends BoneKnight> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
		setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	/** True if {@code source} is a projectile coming at the shield side (its front). */
	public boolean shieldBlocks(DamageSource source) {
		if (!source.is(DamageTypeTags.IS_PROJECTILE) || !getOffhandItem().is(Items.SHIELD)) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1.0e-4) {
			return false;
		}
		Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
		return facing.dot(to.normalize()) >= Math.cos(Math.toRadians(SHIELD_HALF_ANGLE));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide() && shieldBlocks(source)) {
			// the projectile bounces off on its own when hurt() refuses it
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.8f + getRandom().nextFloat() * 0.3f);
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		LivingEntity target = getTarget();
		boolean guard = target != null && target.isAlive() && distanceToSqr(target) > 16.0 && getOffhandItem().is(Items.SHIELD);
		if (guard && !isUsingItem()) {
			startUsingItem(InteractionHand.OFF_HAND);
		} else if (!guard && isUsingItem()) {
			stopUsingItem();
		}
	}
}
