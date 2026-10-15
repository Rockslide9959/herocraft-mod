package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.hulk.HulkMorph;
import com.projecthero.mod.client.hulk.HulkMorphSkin;
import com.projecthero.mod.client.hulk.HulkRenderer;
import com.projecthero.mod.hulk.Hulk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.12 (Hulk Phase 3): a player who is the Hulk is drawn as the Hulk model ({@link HulkRenderer}) instead of the
 * vanilla player -- armour, cape and held items included, which is how "hide armour while transformed" works.
 *
 * <p>v0.16.4 (the morph, {@link HulkMorph}): exactly one body is drawn at any moment, always solid -- the Hulk when
 * {@link HulkMorph#hulkShown}, else Banner (vanilla), whose skin is the green-spreading composite of
 * {@link HulkMorphSkin} while he is mid-morph. While Banner is drawn in the first part of a change into the Hulk, the Hulk
 * is also "ghost" rendered (animation only, nothing drawn) so his transform clip is on time at the swap.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHulkMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void projecthero$buildHulkRenderer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		HulkRenderer.rebuild(context);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$renderHulk(AbstractClientPlayer player, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		HulkRenderer renderer = HulkRenderer.get();
		if (renderer == null || !HulkMorph.hulkShown(player, partialTicks)) {
			return; // Banner (mid-morph or not): vanilla draws him
		}
		ci.cancel();
		if (!player.isInvisible()) {
			renderer.renderMorph(player, entityYaw, partialTicks, poseStack, buffer, packedLight);
		}
	}

	/** v0.16.4: phase A of a change -- the Hulk's clip keeps time while Banner is the one drawn. */
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$hulkGhost(AbstractClientPlayer player, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		HulkRenderer renderer = HulkRenderer.get();
		if (renderer != null && HulkMorph.ghostHulk(player, partialTicks)) {
			renderer.renderGhost(player, entityYaw, partialTicks, poseStack, buffer, packedLight);
		}
	}

	/** v0.16.4: Banner's body mid-morph wears the green-spreading composite of his own skin. */
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$hulkMorphSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		if (Hulk.hasPower(player)) {
			float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			cir.setReturnValue(HulkMorphSkin.texture(player, cir.getReturnValue(), partial));
		}
	}
}
