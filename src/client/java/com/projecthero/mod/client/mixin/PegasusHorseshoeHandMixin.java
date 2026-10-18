package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.pegasus.PegasusPose;
import com.projecthero.mod.client.miraculous.pegasus.PegasusRender;
import com.projecthero.mod.miraculous.pegasus.Pegasus;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6, first person: while one of Pegasus's moves has the Horseshoe in his hand (the throw's wind-up, the Volley,
 * Portal Hop, Portal Trap, the Voyage, the Portal Shield, the waypoint tap, the Linked Portals) it is drawn in place of
 * the main-hand item, raised with the move.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class PegasusHorseshoeHandMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$pegasusHorseshoe(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || !Pegasus.is(player) || Pegasus.shoeOut(player) || PegasusRender.cover(player, partialTick) < 0.999f
				|| !PegasusPose.inHand(player, partialTick)) {
			return;
		}
		ci.cancel();
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		float[] p = PegasusPose.playing(player, partialTick);
		int id = p == null ? 0 : (int) p[0];
		float age = p == null ? 0f : p[1];
		pose.pushPose();
		pose.translate(0.42f * side, -0.36f, -0.75f);
		switch (id) {
			case Pegasus.ANIM_THROW -> {
				float t = Mth.clamp(age / 3f, 0f, 1f);
				pose.translate(-0.3f * side * t, 0.05f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(40f * side * t));
			}
			case Pegasus.ANIM_HOP, Pegasus.ANIM_VOYAGE, Pegasus.ANIM_LINK -> {
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose.translate(-0.18f * side * t, 0.22f * t, -0.1f * t);
			}
			case Pegasus.ANIM_TRAP -> {
				float t = Mth.clamp((age - 7f) / 4f, 0f, 1f);
				pose.translate(-0.1f * side, -0.05f + 0.3f * t, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-40f + 70f * t));
			}
			case Pegasus.ANIM_WAYPOINT -> pose.translate(0f, -0.2f * Mth.sin(Mth.clamp(age / 12f, 0f, 1f) * (float) Math.PI), 0f);
			default -> pose.translate(-0.15f * side, 0.15f, 0f); // the shield: held up before him
		}
		// toe up, turned a little toward the middle of the view
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pose.mulPose(Axis.YP.rotationDegrees(-20f * side));
		pose.scale(0.5f, 0.5f, 0.5f);
		PegasusRender.renderShoeModel(pose, buffers, light);
		pose.popPose();
	}
}
