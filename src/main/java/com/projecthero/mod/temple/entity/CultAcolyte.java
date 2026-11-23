package com.projecthero.mod.temple.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.17.7: a <b>cult acolyte</b> of the Bound King -- hooded in ochre and black, gold at the throat, a ceremonial dagger
 * (golden sword). Quick and lightly built: the temple's Alarm Runners, Medics and Snipers (who carry a bow) are
 * acolytes.
 */
public class CultAcolyte extends TempleMob {
	public CultAcolyte(EntityType<? extends CultAcolyte> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.ATTACK_DAMAGE, 3.5)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 36.0);
	}

	@Override
	protected Skin defaultTempleSkin() {
		return Skin.ACOLYTE_A;
	}

	@Override
	protected void outfit() {
		setTempleSkin(random.nextBoolean() ? Skin.ACOLYTE_A : Skin.ACOLYTE_B);
		arm(new ItemStack(Items.GOLDEN_SWORD));
	}

	/** A sniper acolyte: the dart bow in hand. */
	public void holdBow() {
		arm(new ItemStack(Items.BOW));
		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}
}
