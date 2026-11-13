package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.client.vision.VisionHud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * v0.17.3 Vision's Intangibility (and Silver Surfer's Molecular Phase): with the camera inside an opaque block, vanilla's
 * section-occlusion culling ("smart cull") starts from a section it can't see out of and blacks out the world around him.
 * Vanilla turns smart culling off for a spectator whose camera is in a solid block: {@code renderLevel} passes
 * {@code player.isSpectator()} into {@code setupRender} for exactly that check (it's the only thing the flag does there), so
 * a phasing player is reported as a spectator to it and gets the same clear view.
 */
@Mixin(LevelRenderer.class)
public abstract class VisionPhaseCullMixin {
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"),
			index = 3)
	private boolean projecthero$phaseSeesLikeSpectator(boolean spectator) {
		if (spectator) {
			return true;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && (VisionHud.phaseSight(p) || SilverSurfer.phasing(p));
	}
}
