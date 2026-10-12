package com.projecthero.mod.ultron.contagion.item;

import java.util.List;

import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the <b>Override Spike</b> -- use it on an Ultron Nexus (right-click the spire, or aim at it within 8 blocks)
 * to start the Override capture ({@link UltronNexusEntity#startCapture}). One spike per capture; it is only used up if the
 * capture actually starts.
 */
public class OverrideSpikeItem extends Item {
	public OverrideSpikeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (target instanceof UltronNexusEntity nexus) {
			if (player instanceof ServerPlayer sp && nexus.startCapture(sp)) {
				stack.consume(1, player);
			}
			return InteractionResult.sidedSuccess(player.level().isClientSide());
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(stack);
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(8.0));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(2.0),
				e -> e instanceof UltronNexusEntity && e.isAlive(), 64.0);
		if (hit != null && hit.getEntity() instanceof UltronNexusEntity nexus) {
			if (nexus.startCapture(sp)) {
				stack.consume(1, player);
			}
			return InteractionResultHolder.success(stack);
		}
		sp.displayClientMessage(Component.translatable("message.projecthero.contagion.spike_no_nexus").withStyle(ChatFormatting.GRAY), true);
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.override_spike.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.override_spike.hint2").withStyle(ChatFormatting.DARK_AQUA));
	}
}
