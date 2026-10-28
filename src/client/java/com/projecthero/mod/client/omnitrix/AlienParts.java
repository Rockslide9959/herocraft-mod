package com.projecthero.mod.client.omnitrix;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.1: the helpers an alien client kit draws its extra cubes with.
 * <ul>
 *   <li>{@link #bake}: a {@link ModelPart} tree from a mesh, without registering a model layer (UVs on any texture -- the
 *       alien's own skin for a second pair of arms, or an extras sheet);</li>
 *   <li>{@link #on}: runs a draw in a bone's space (the bone's pivot and rotation, units = pixels / 16);</li>
 *   <li>{@link #box}: one textured cube straight into a buffer, every face showing the same UV rectangle (flames, glows,
 *       visors), coordinates in pixels.</li>
 * </ul>
 */
public final class AlienParts {
	private AlienParts() {
	}

	public static ModelPart bake(int texW, int texH, Consumer<PartDefinition> build) {
		MeshDefinition mesh = new MeshDefinition();
		build.accept(mesh.getRoot());
		return LayerDefinition.create(mesh, texW, texH).bakeRoot();
	}

	/** {@code draw} in {@code bone}'s space. */
	public static void on(PoseStack pose, ModelPart bone, Runnable draw) {
		pose.pushPose();
		bone.translateAndRotate(pose);
		draw.run();
		pose.popPose();
	}

	/** A cube from (x0,y0,z0) to (x1,y1,z1) in pixels, UV rectangle u0..u1 / v0..v1 (0..1) on every face, tinted {@code argb}. */
	public static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float u0, float v0, float u1,
			float v1, int argb, int light) {
		float s = 1f / 16f;
		x0 *= s;
		y0 *= s;
		z0 *= s;
		x1 *= s;
		y1 *= s;
		z1 *= s;
		PoseStack.Pose p = pose.last();
		// -z (north), +z (south), -x, +x, -y (top in model space), +y
		quad(vc, p, argb, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1);
		quad(vc, p, argb, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1);
		quad(vc, p, argb, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1);
		quad(vc, p, argb, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1);
		quad(vc, p, argb, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v0, u1, v1);
		quad(vc, p, argb, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1);
	}

	/** One quad (corners in order), UV u0..u1 across, v0..v1 down. */
	public static void quad(VertexConsumer vc, PoseStack.Pose p, int argb, int light, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1) {
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		vc.addVertex(p, ax, ay, az).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
