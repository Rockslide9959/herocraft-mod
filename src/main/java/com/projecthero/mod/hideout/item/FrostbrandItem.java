package com.projecthero.mod.hideout.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.5 artifact (Frost Giant Hall): <b>Frostbrand</b> -- a Jotun jarl's ice-forged blade, cut down to a mortal's
 * size. A diamond-grade sword (+1 damage) whose every hit frosts the target: Slowness II for 3 s and
 * {@link #FREEZE_TICKS} freeze ticks (the frosted screen and powder-snow slowness build on players).
 */
public class FrostbrandItem extends SwordItem {
	public static final int FREEZE_TICKS = 100;

	public FrostbrandItem(Properties props) {
		super(Tiers.DIAMOND, props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4f)));
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		frost(target);
	}

	/** The frost a hit adds. */
	public static void frost(LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		if (target.canFreeze()) {
			target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + FREEZE_TICKS));
		}
		if (target.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12,
					0.3, 0.4, 0.3, 0.02);
			server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.frostbrand.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.frost_giant_hall"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
