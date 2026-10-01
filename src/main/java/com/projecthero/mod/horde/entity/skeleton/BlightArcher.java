package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.14.16 (the Skeleton Horde's husk): a rotted, green-black archer that trails spores. Its arrows carry the blight --
 * <b>Wither</b> and <b>Hunger</b> -- and from wave 6 the Wither is level II.
 */
public class BlightArcher extends HordeSkeleton {
	public BlightArcher(EntityType<? extends BlightArcher> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 22.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	@Override
	protected AbstractArrow getArrow(ItemStack arrowItem, float velocity, ItemStack weapon) {
		AbstractArrow arrow = super.getArrow(arrowItem, velocity, weapon);
		if (arrow instanceof Arrow tipped) {
			tipped.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, hordeWave() >= 6 ? 1 : 0));
			tipped.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140, 0));
		}
		return arrow;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && getRandom().nextInt(3) == 0) {
			level().addParticle(ParticleTypes.MYCELIUM, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0, 0);
		}
	}
}
