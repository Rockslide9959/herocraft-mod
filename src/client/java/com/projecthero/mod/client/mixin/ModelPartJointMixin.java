package com.projecthero.mod.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.model.geom.ModelPart;

/**
 * v0.16.2: while a pose has bent a limb ({@link JointedLimbs}), a humanoid model's arm / leg / sleeve / pants part draws
 * its cubes in two halves, the lower one turned about the elbow / knee. Every other part (and every limb outside a bent
 * pose) compiles exactly as vanilla.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartJointMixin {
	@Shadow
	@Final
	private List<ModelPart.Cube> cubes;

	@Inject(method = "compile", at = @At("HEAD"), cancellable = true)
	private void projecthero$jointed(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int colour,
			CallbackInfo ci) {
		if (!JointedLimbs.active() || cubes.isEmpty()) {
			return;
		}
		int limb = JointedLimbs.limbOf((ModelPart) (Object) this);
		if (limb < 0 || JointedLimbs.joint(limb) == null) {
			return;
		}
		ci.cancel();
		JointedLimbs.drawCubes(limb, cubes, c -> ((ModelPartCubeAccessor) (Object) c).projecthero$polygons(), pose, buffer,
				light, overlay, colour);
	}
}
