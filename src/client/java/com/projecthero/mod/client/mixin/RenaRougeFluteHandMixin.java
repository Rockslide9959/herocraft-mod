package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougeClient;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougePose;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougeRender;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6, first person: while one of Rena Rouge's moves has the Flute in her hand (the combo, the sweep, the catch,
 * the Double, the wall) it is drawn in place of the main-hand item, swung with the move; while she plays it (Lure, the
 * Mirage) it is held level across the bottom of the view, to her lips.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class RenaRougeFluteHandMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$renaRougeFlute(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		RenaRougeRender.FluteAt at = RenaRougeClient.firstPersonFlute(player, partialTick);
		if (at == null) {
			return;
		}
		ci.cancel();
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		pose.pushPose();
		if (at == RenaRougeRender.FluteAt.LIPS) {
			pose.translate(0.05f * side, -0.2f, -0.62f);
			pose.mulPose(Axis.ZP.rotationDegrees(-90f * side));
			pose.mulPose(Axis.YP.rotationDegrees(70f)); // the finger holes turned up toward her
			pose.translate(0f, 6f / 16f, 0f);
			pose.scale(0.4f, 0.4f, 0.4f);
			RenaRougeRender.renderFluteModel(pose, buffers, light, 1f);
			pose.popPose();
			return;
		}
		// vanilla's item-arm placement, then the move's own swing round the grip
		pose.translate(0.5f * side, -0.42f, -0.8f);
		float[] p = RenaRougePose.playing(player, partialTick);
		int id = p == null ? 0 : (int) p[0];
		float age = p == null ? 0f : p[1];
		float t = p == null ? 0f : Mth.clamp((age - 1f) / 4f, 0f, 1f);
		t = t * t * (3f - 2f * t);
		switch (id) {
			case RenaRouge.ANIM_STRIKE_1 -> {
				pose.translate(-0.25f * side * t, 0.1f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(t, 40f, -50f) * side));
				pose.mulPose(Axis.ZP.rotationDegrees(-70f * side));
			}
			case RenaRouge.ANIM_STRIKE_2 -> {
				pose.translate(-0.25f * side * (1f - t), 0.1f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(t, -50f, 40f) * side));
				pose.mulPose(Axis.ZP.rotationDegrees(-70f * side));
			}
			case RenaRouge.ANIM_STRIKE_3 -> {
				float c = Mth.clamp((age - 2f) / 4f, 0f, 1f);
				pose.translate(-0.25f * side, 0.25f - 0.2f * c, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(c, 50f, -60f)));
				pose.mulPose(Axis.ZP.rotationDegrees(-80f * side));
			}
			case RenaRouge.ANIM_SWEEP -> {
				pose.translate(-0.2f * side, 0.05f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(Mth.clamp((age - 1f) / 11f, 0f, 1f) * 360f * side));
				pose.mulPose(Axis.ZP.rotationDegrees(-90f * side));
			}
			case RenaRouge.ANIM_CATCH -> {
				float c = Mth.clamp(age / 4f, 0f, 1f);
				pose.translate(0f, 0.35f * (1f - c), 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-30f));
			}
			case RenaRouge.ANIM_DOUBLE, RenaRouge.ANIM_WALL, RenaRouge.ANIM_MIRAGE -> {
				pose.translate(0f, 0.18f, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-15f));
			}
			default -> pose.mulPose(Axis.XP.rotationDegrees(-35f));
		}
		pose.scale(0.42f, 0.42f, 0.42f);
		RenaRougeRender.renderFluteModel(pose, buffers, light, 1f);
		pose.popPose();
	}
}
