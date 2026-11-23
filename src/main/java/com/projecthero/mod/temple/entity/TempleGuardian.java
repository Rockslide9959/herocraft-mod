package com.projecthero.mod.temple.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.17.7: a <b>temple guardian</b> -- a jackal-masked warrior in bronze scale and white linen, with a khopesh (an iron
 * sword in hand). The temple's line troops: the Shield-bearers and the Captain are guardians.
 */
public class TempleGuardian extends TempleMob {
	public TempleGuardian(EntityType<? extends TempleGuardian> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.FOLLOW_RANGE, 36.0);
	}

	@Override
	protected Skin defaultTempleSkin() {
		return Skin.GUARDIAN_A;
	}

	@Override
	protected void outfit() {
		setTempleSkin(random.nextBoolean() ? Skin.GUARDIAN_A : Skin.GUARDIAN_B);
		arm(new ItemStack(Items.IRON_SWORD));
		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMOR_EQUIP_IRON.value();
	}
}
