package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.render.FrontFaceOnlyPart;

import net.minecraft.client.model.geom.ModelPart;

/**
 * v0.15.11: lets one model part be drawn with only its FRONT (north, -Z) faces -- used for the player's skin hat layer
 * while an Iron Man faceplate is open (see {@link FrontFaceOnlyPart} and {@code PlayerModelMixin}). While a flagged part
 * compiles its cubes, {@link FrontFaceOnlyPart#ACTIVE} is up and {@code ModelPartCubeFrontFaceMixin} skips every other
 * face. Unflagged parts (everything else) are untouched.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartFrontFaceMixin implements FrontFaceOnlyPart {
	@Unique
	private boolean projecthero$frontFaceOnly;

	@Override
	public void projecthero$setFrontFaceOnly(boolean on) {
		projecthero$frontFaceOnly = on;
	}

	@Override
	public boolean projecthero$frontFaceOnly() {
		return projecthero$frontFaceOnly;
	}

	@Inject(method = "compile", at = @At("HEAD"))
	private void projecthero$frontOnlyBegin(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int colour,
			CallbackInfo ci) {
		if (projecthero$frontFaceOnly) {
			FrontFaceOnlyPart.ACTIVE[0] = true;
		}
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void projecthero$frontOnlyEnd(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int colour,
			CallbackInfo ci) {
		if (projecthero$frontFaceOnly) {
			FrontFaceOnlyPart.ACTIVE[0] = false;
		}
	}
}
