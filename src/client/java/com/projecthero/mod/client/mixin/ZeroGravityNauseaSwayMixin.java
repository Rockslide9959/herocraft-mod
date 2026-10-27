package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.zerogravity.ZeroGravityHud;

import net.minecraft.client.renderer.GameRenderer;

/**
 * v0.17.1 Zero Gravity revamp (Nausea bands, docs 3.7): the screen sways while Uravity is Queasy (gently) or Pushing it
 * (strongly) -- a slow roll and bob of the world view, after vanilla's hurt-bob ({@link ZeroGravityHud#swayRoll}). Scaled by
 * vanilla's "Distortion Effects" slider, off at 0.
 */
@Mixin(GameRenderer.class)
public abstract class ZeroGravityNauseaSwayMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void projecthero$zeroGravitySway(PoseStack pose, float partialTick, CallbackInfo ci) {
		float roll = ZeroGravityHud.swayRoll(partialTick);
		if (roll != 0f) {
			pose.mulPose(Axis.ZP.rotationDegrees(roll));
			pose.translate(0f, ZeroGravityHud.swayBob(partialTick), 0f);
		}
	}
}
