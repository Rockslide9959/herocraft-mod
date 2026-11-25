package com.projecthero.mod.client.shazam.journey;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

/** v0.17.7 Shazam acquisition: tiny quad / box helpers for the illusion and the train (NEW_ENTITY vertex format). */
final class Quads {
	private Quads() {
	}

	/** One quad a-b-c-d (counter-clockwise seen from the front), UVs u0..u1 x v0..v1 (v0 at a/b's top edge... see callers). */
	static void quad(VertexConsumer vc, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float dx, float dy, float dz, float u0, float v0, float u1, float v1, int argb, int light, float nx, float ny, float nz) {
		Matrix4f m = pose.pose();
		int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		int ov = OverlayTexture.NO_OVERLAY;
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a).setUv(u0, v1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a).setUv(u1, v1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a).setUv(u1, v0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a).setUv(u0, v0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** A wall in the plane z = {@code z} from (x0, y0) to (x1, y1), facing +z if {@code front} else -z. */
	static void wallZ(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, boolean front, float u0, float v0, float u1,
			float v1, int argb, int light) {
		if (front) {
			quad(vc, pose, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, u0, v0, u1, v1, argb, light, 0, 0, 1);
		} else {
			quad(vc, pose, x1, y0, z, x0, y0, z, x0, y1, z, x1, y1, z, u0, v0, u1, v1, argb, light, 0, 0, -1);
		}
	}

	/** A wall in the plane x = {@code x} from (z0, y0) to (z1, y1), facing +x if {@code front} else -x. */
	static void wallX(VertexConsumer vc, PoseStack.Pose pose, float z0, float y0, float z1, float y1, float x, boolean front, float u0, float v0, float u1,
			float v1, int argb, int light) {
		if (front) {
			quad(vc, pose, x, y0, z1, x, y0, z0, x, y1, z0, x, y1, z1, u0, v0, u1, v1, argb, light, 1, 0, 0);
		} else {
			quad(vc, pose, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0, u0, v0, u1, v1, argb, light, -1, 0, 0);
		}
	}

	/** A floor / ceiling in the plane y = {@code y}, facing up if {@code up}. */
	static void flatY(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, boolean up, float u0, float v0, float u1,
			float v1, int argb, int light) {
		if (up) {
			quad(vc, pose, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, u0, v0, u1, v1, argb, light, 0, 1, 0);
		} else {
			quad(vc, pose, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, u0, v0, u1, v1, argb, light, 0, -1, 0);
		}
	}

	/** A box, every face mapped to the same UV rect. */
	static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float u0, float v0, float u1,
			float v1, int argb, int light) {
		wallZ(vc, pose, x0, y0, x1, y1, z1, true, u0, v0, u1, v1, argb, light);
		wallZ(vc, pose, x0, y0, x1, y1, z0, false, u0, v0, u1, v1, argb, light);
		wallX(vc, pose, z0, y0, z1, y1, x1, true, u0, v0, u1, v1, argb, light);
		wallX(vc, pose, z0, y0, z1, y1, x0, false, u0, v0, u1, v1, argb, light);
		flatY(vc, pose, x0, z0, x1, z1, y1, true, u0, v0, u1, v1, argb, light);
		flatY(vc, pose, x0, z0, x1, z1, y0, false, u0, v0, u1, v1, argb, light);
	}
}
