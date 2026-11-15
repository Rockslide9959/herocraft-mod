package com.projecthero.mod.gear;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.5: the Hero Alloy Sword, the Alloy Baton and the Vibranium Blade. The baton is a fast one-handed strike whose
 * every hit staggers (Slowness II for {@link #STAGGER_TICKS}).
 */
public class GearSwordItem extends SwordItem {
	public static final int STAGGER_TICKS = 25;

	private final int gearTier;
	private final boolean staggers;
	private final String hintKey;

	public GearSwordItem(Tier tier, int gearTier, boolean staggers, String hintKey, Properties properties) {
		super(tier, properties);
		this.gearTier = gearTier;
		this.staggers = staggers;
		this.hintKey = hintKey;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (staggers && !target.level().isClientSide) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, 1, false, true), attacker);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		GearTooltip.add(tooltip, gearTier, hintKey);
	}
}
