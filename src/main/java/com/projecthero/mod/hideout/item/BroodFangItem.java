package com.projecthero.mod.hideout.item;

import java.util.List;

import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.7 artifact (Carnage Nest): <b>Brood Fang</b> -- a hardened red symbiote talon off the Brood Spawn. A fast
 * diamond-grade blade (7 damage, quicker swings) that feeds: every hit adds Wither I for 2 s and heals you
 * {@link #HEAL} health.
 */
public class BroodFangItem extends SwordItem {
	public static final float HEAL = 1.0f;
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.06f), 0.9f);

	public BroodFangItem(Properties props) {
		super(Tiers.DIAMOND, props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.0f)));
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		feed(target, attacker);
	}

	/** What a hit does (test hook too). */
	public static void feed(LivingEntity target, LivingEntity attacker) {
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
		attacker.heal(HEAL);
		if (target.level() instanceof ServerLevel server) {
			server.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.brood_fang.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.carnage_nest"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
