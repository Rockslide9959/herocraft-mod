package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Iron Man Era II fold polish, FIRST person (the third-person half is {@code ItemInHandLayerSuitcaseMixin}):
 * <ul>
 *   <li>a carried Mk VII / Mk VIII pack is "worn on the back", so its sprite is never drawn in your own hand -- the hand
 *       shows bare, as if the slot were empty;</li>
 *   <li>while a hand-carried portable form (the Mk VI briefcase, the Mk IX heat case) deploys or folds, the floor prop IS
 *       the case, so the vanilla held case is not also drawn in the main hand.</li>
 * </ul>
 * Swapping the stack for EMPTY (rather than cancelling) keeps vanilla's bare main-hand arm.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererPortableMixin {
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack projecthero$portableHidesHeldCase(ItemStack stack, AbstractClientPlayer player, float partialTicks,
			float pitch, InteractionHand hand) {
		if (stack.getItem() instanceof com.projecthero.mod.ironman.item.IronManPackItem) {
			return ItemStack.EMPTY;
		}
		if (hand == InteractionHand.MAIN_HAND && !stack.isEmpty()
				&& com.projecthero.mod.client.ironman.IronManPortableClient.firstPersonHidesHeld(player, partialTicks)) {
			return ItemStack.EMPTY;
		}
		return stack;
	}
}
