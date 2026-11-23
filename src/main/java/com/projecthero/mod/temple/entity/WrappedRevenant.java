package com.projecthero.mod.temple.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.17.7: a <b>wrapped revenant</b> -- one of the temple's dead in grave-wrappings, eyes burning green. Slow and tough;
 * its grip leaves Hunger and Slowness. Old linen burns: fire hurts it half again as much.
 */
public class WrappedRevenant extends TempleMob {
	public WrappedRevenant(EntityType<? extends WrappedRevenant> type, Level level) {
		super(type, level);
		this.xpReward = 9;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.ATTACK_DAMAGE, 4.5)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected Skin defaultTempleSkin() {
		return Skin.REVENANT_A;
	}

	@Override
	protected void outfit() {
		setTempleSkin(random.nextBoolean() ? Skin.REVENANT_A : Skin.REVENANT_B);
		arm(ItemStack.EMPTY);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0), this);
			l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
		}
		return hit;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return super.hurt(source, source.is(DamageTypeTags.IS_FIRE) ? amount * 1.5f : amount);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}
}
