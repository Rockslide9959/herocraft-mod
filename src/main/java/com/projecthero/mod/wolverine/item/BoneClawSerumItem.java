package com.projecthero.mod.wolverine.item;

import java.util.List;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.data.ClawTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bone Claw Serum: unlocks a Wolverine's natural bone claws (NONE -> BONE). Only works for a Wolverine
 * who has no claws yet; anything else consumes nothing. All state changes are server-side.
 */
public class BoneClawSerumItem extends Item {
	public BoneClawSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (!Wolverine.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.not_wolverine")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (Wolverine.clawTier(sp) != ClawTier.NONE) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.unlockBoneClaws(sp)) {
			return InteractionResultHolder.fail(held);
		}
		fx(sp);
		held.shrink(1);
		return InteractionResultHolder.success(held);
	}

	private static void fx(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 look = p.getLookAngle();
		Vec3 hands = p.position().add(0, 1.0, 0).add(look.scale(0.5));
		level.sendParticles(ParticleTypes.CRIT, hands.x, hands.y, hands.z, 30, 0.35, 0.25, 0.35, 0.1);
		level.sendParticles(ParticleTypes.HEART, hands.x, hands.y + 0.3, hands.z, 6, 0.3, 0.2, 0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, hands.x, hands.y, hands.z, 20, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.9f, 0.9f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.8f, 0.6f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, true, true));
		p.displayClientMessage(Component.translatable("message.projecthero.bone_serum.success")
				.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.bone_claw_serum.desc0").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.bone_claw_serum.desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.bone_claw_serum.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
