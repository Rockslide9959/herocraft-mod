package com.projecthero.mod.client.mixin;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.render.FrontFaceOnlyPart;

import net.minecraft.client.model.geom.ModelPart;

/**
 * v0.15.11: while {@link FrontFaceOnlyPart#ACTIVE} is up (a part flagged front-face-only is compiling), a cube draws only
 * its front faces -- the polygons whose model-space normal points along -Z -- exactly as vanilla's own
 * {@code Cube.compile} would draw them. See {@code ModelPartFrontFaceMixin}.
 */
@Mixin(ModelPart.Cube.class)
public abstract class ModelPartCubeFrontFaceMixin {
	@Shadow
	@Final
	private ModelPart.Polygon[] polygons;

	@Inject(method = "compile", at = @At("HEAD"), cancellable = true)
	private void projecthero$frontFacesOnly(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int colour,
			CallbackInfo ci) {
		if (!FrontFaceOnlyPart.ACTIVE[0]) {
			return;
		}
		ci.cancel();
		Matrix4f matrix = pose.pose();
		Vector3f scratch = new Vector3f();
		for (ModelPart.Polygon polygon : polygons) {
			if (!FrontFaceOnlyPart.isFront(polygon.normal)) {
				continue;
			}
			Vector3f n = pose.transformNormal(polygon.normal, scratch);
			float nx = n.x();
			float ny = n.y();
			float nz = n.z();
			for (ModelPart.Vertex vertex : polygon.vertices) {
				Vector3f p = matrix.transformPosition(vertex.pos.x() / 16.0F, vertex.pos.y() / 16.0F, vertex.pos.z() / 16.0F,
						scratch);
				buffer.addVertex(p.x(), p.y(), p.z(), colour, vertex.u, vertex.v, overlay, light, nx, ny, nz);
			}
		}
	}
}
