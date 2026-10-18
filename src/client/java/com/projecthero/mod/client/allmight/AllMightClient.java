package com.projecthero.mod.client.allmight;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: All Might's client side beyond the HUD and the poses -- the Output glow layer on every player
 * ({@link AllMightGlowLayer}) and the Pass On / Lend screen that opens on sneak + right-click on another player with an
 * empty hand ({@link AllMightOfferScreen}). The server half is {@code AllMightPassOn}.
 */
public final class AllMightClient {
	private AllMightClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new AllMightGlowLayer(playerRenderer));
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide || hand != InteractionHand.MAIN_HAND || !(entity instanceof Player target) || !player.isShiftKeyDown()
					|| !player.getMainHandItem().isEmpty() || player != Minecraft.getInstance().player) {
				return InteractionResult.PASS;
			}
			AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
			if (s == null || !s.hasPower || s.loanRole == AllMightState.LOAN_BORROWER) {
				return InteractionResult.PASS; // a lender's recall and a borrower's refusal are the server's
			}
			Minecraft.getInstance().setScreen(new AllMightOfferScreen(target.getId(), target.getDisplayName()));
			return InteractionResult.SUCCESS;
		});
	}
}
