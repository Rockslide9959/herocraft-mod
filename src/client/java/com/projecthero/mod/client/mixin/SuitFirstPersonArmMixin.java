package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.4 (user: "make it so that you can see the suit on your hand in first person please, check where else this could apply and do
 * it for those powers aswell"): the worn look of the powers that had none on the first-person arm -- Doctor Fate's suit, Shazam's
 * suit, Beast Boy's green tint, Dark Shadow's Black Ankh and Overhaul's Fusion plates / gloves.
 *
 * <p>Same approach as the other per-power sleeves ({@code NovaFirstPersonArmMixin}, {@code VisionFirstPersonArmMixin}): at the TAIL of
 * {@code renderHand} the vanilla arm has just been drawn with its full first-person pose (every custom first-person clip writes
 * into that same arm), and the pose stack is still where it was drawn, so each power's piece copies that arm's pose and rides it.
 * Each power's own layer class owns the drawing ({@code renderArm}), sharing its third-person model and texture.
 */
@Mixin(PlayerRenderer.class)
public abstract class SuitFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$suitSleeve(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		com.projecthero.mod.client.beastboy.BeastTintLayer.renderArm(pose, buffers, light, player, arm, sleeve);
		com.projecthero.mod.client.doctorfate.FateSuitLayer.renderArm(pose, buffers, light, player, arm, right, partial);
		com.projecthero.mod.client.shazam.ShazamSuitLayer.renderArm(pose, buffers, light, player, arm, right);
		com.projecthero.mod.client.quirk.darkshadow.DarkShadowAnkhLayer.renderArm(pose, buffers, light, player, arm, right, partial);
		com.projecthero.mod.client.quirk.overhaul.OverhaulFusionLayer.renderArm(pose, buffers, light, player, arm, right);
	}
}
