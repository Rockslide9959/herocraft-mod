package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.greenlantern.GreenLanternFlightPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

/**
 * v0.17.4, first person: sprint-flying with Ring Flight, the bare ring arm (the right one -- the ring is always on the
 * right hand) comes in toward the middle of the view and reaches ahead, so you see the ring fist leading the flight like the
 * third-person Superman pose. Blended by {@link GreenLanternFlightPose#sprintWeight}, so it eases in and out with the
 * sprint; a swing still plays on top (vanilla's swing runs inside the moved frame).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class GreenLanternFirstPersonFlightMixin {
	@Unique
	private boolean projecthero$glPushed;

	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void projecthero$glSprintArmIn(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm side, CallbackInfo ci) {
		this.projecthero$glPushed = false;
		Minecraft mc = Minecraft.getInstance();
		if (side != HumanoidArm.RIGHT || mc.player == null) {
			return;
		}
		float w = GreenLanternFlightPose.sprintWeight(mc.player, mc.getTimer().getGameTimeDeltaPartialTick(false));
		if (w <= 0.001f) {
			return;
		}
		pose.pushPose();
		this.projecthero$glPushed = true;
		float age = mc.player.tickCount + mc.getTimer().getGameTimeDeltaPartialTick(false);
		// in toward the middle, up and further ahead, the fist turned a little inward to point down the flight path
		pose.translate(-0.22f * w, (0.12f + 0.006f * net.minecraft.util.Mth.sin(age * 0.9f)) * w, -0.12f * w);
		pose.mulPose(Axis.YP.rotationDegrees(12f * w));
		pose.mulPose(Axis.XP.rotationDegrees(-14f * w));
	}

	@Inject(method = "renderPlayerArm", at = @At("TAIL"))
	private void projecthero$glSprintArmOut(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm side, CallbackInfo ci) {
		if (this.projecthero$glPushed) {
			this.projecthero$glPushed = false;
			pose.popPose();
		}
	}
}
