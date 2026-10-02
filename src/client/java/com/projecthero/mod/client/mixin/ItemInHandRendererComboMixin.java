package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.thor.WeaponComboPose;
import com.projecthero.mod.power.WeaponCombo;
import com.projecthero.mod.power.WeaponComboState;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.20, first person: while a Mjolnir / Stormbreaker combo swing plays ({@link WeaponComboPose}), vanilla's own
 * swing and its post-hit lowering of the main-hand weapon are held off (the swing / equip progress handed to the main
 * hand's {@code renderArmWithItem} are scaled down by the swing's weight), and the weapon is moved about the hand just
 * before it is drawn. Inside vanilla's own push / pop, so nothing leaks.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererComboMixin {
	private static final String RENDER_ARM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	/** This frame's combo weight for the main-hand weapon (render thread only). */
	@Unique
	private float projecthero$comboWeight;

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void projecthero$comboWeigh(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
			LocalPlayer player, int light, CallbackInfo ci) {
		this.projecthero$comboWeight = WeaponCombo.weaponOf(player.getMainHandItem()) != WeaponComboState.WEAPON_NONE
				? WeaponComboPose.firstPersonWeight(player, partialTick)
				: 0f;
	}

	/** The main hand is the first {@code renderArmWithItem} call; argument 4 is its swing progress. */
	@ModifyArg(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = RENDER_ARM, ordinal = 0), index = 4)
	private float projecthero$comboHoldSwing(float swingProgress) {
		return swingProgress * (1f - this.projecthero$comboWeight);
	}

	/** ...and argument 6 its equip progress (how far the weapon is lowered after a hit, until it recharges). */
	@ModifyArg(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = RENDER_ARM, ordinal = 0), index = 6)
	private float projecthero$comboHoldEquip(float equipProgress) {
		return equipProgress * (1f - this.projecthero$comboWeight);
	}

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$comboSwing(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || WeaponCombo.weaponOf(stack) == WeaponComboState.WEAPON_NONE) {
			return;
		}
		WeaponComboPose.firstPerson(player, player.getMainArm(), partialTick, pose);
	}
}
