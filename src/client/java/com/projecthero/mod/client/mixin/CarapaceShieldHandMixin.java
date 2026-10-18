package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.carapace.CarapaceClient;
import com.projecthero.mod.client.miraculous.carapace.CarapacePose;
import com.projecthero.mod.client.miraculous.carapace.CarapaceRender;
import com.projecthero.mod.miraculous.carapace.Carapace;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6, first person: while one of Carapace's moves has the Shield on his arm (the slam, the taunt, the barrier...)
 * it is drawn in place of the off-hand item, low at the side of the view; for Shell Guard and the Bash it is raised in
 * front, covering the lower part of the view, the shell's face turned a little toward him so it reads as the shell.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class CarapaceShieldHandMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$carapaceShield(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.OFF_HAND) {
			return;
		}
		CarapaceRender.ShieldAt at = CarapaceClient.firstPersonShield(player, partialTick);
		if (at == null) {
			return;
		}
		ci.cancel();
		// the off hand's side of the view
		float side = player.getMainArm() == HumanoidArm.RIGHT ? -1f : 1f;
		pose.pushPose();
		if (at == CarapaceRender.ShieldAt.GUARD) {
			float bash = Carapace.running(player, Carapace.V_BASH) ? 0.08f : 0f;
			pose.translate(0.4f * side, -0.42f, -0.72f - bash);
			pose.mulPose(Axis.YP.rotationDegrees(18f * side));
			pose.mulPose(Axis.XP.rotationDegrees(-6f));
			pose.scale(0.68f, 0.68f, 0.68f);
		} else {
			float[] p = CarapacePose.playing(player, partialTick);
			int id = p == null ? 0 : (int) p[0];
			float age = p == null ? 0f : p[1];
			float lift = 0f;
			if (id == Carapace.ANIM_SLAM) {
				lift = age < 5f ? 0.3f * age / 5f : Mth.lerp(Mth.clamp((age - 5f) / 2f, 0f, 1f), 0.3f, -0.25f);
			} else if (id == Carapace.ANIM_BARRIER) {
				lift = 0.08f;
			}
			pose.translate(0.52f * side, -0.45f + lift, -0.78f);
			pose.mulPose(Axis.YP.rotationDegrees(40f * side));
			pose.mulPose(Axis.XP.rotationDegrees(-10f));
			pose.scale(0.8f, 0.8f, 0.8f);
		}
		CarapaceRender.renderShieldModel(pose, buffers, light, 1f);
		pose.popPose();
	}
}
