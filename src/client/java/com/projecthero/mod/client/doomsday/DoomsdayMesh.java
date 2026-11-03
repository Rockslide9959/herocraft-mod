package com.projecthero.mod.client.doomsday;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.2: the few shapes Doomsday's renderers draw by hand -- bone spikes (four-sided pyramids), cable bands (boxes),
 * and flat glowing ribbons (heat vision, the Phantom Zone pane). Coordinates are in the current pose's units.
 */
final class DoomsdayMesh {
	private DoomsdayMesh() {
	}

	/** A four-sided spike from {@code base} (half-width {@code half}) along {@code dir} for {@code len}. Bone texture: v 0 at the tip. */
	static void spike(VertexConsumer vc, PoseStack.Pose pose, int light, int color, Vector3f base, Vector3f dir, float half, float len) {
		Vector3f d = new Vector3f(dir).normalize();
		Vector3f helper = Math.abs(d.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		Vector3f u = new Vector3f(d).cross(helper).normalize().mul(half);
		Vector3f v = new Vector3f(d).cross(u).normalize().mul(half);
		Vector3f tip = new Vector3f(d).mul(len).add(base);
		Vector3f[] c = {
				new Vector3f(base).add(u).add(v), new Vector3f(base).sub(u).add(v),
				new Vector3f(base).sub(u).sub(v), new Vector3f(base).add(u).sub(v) };
		for (int i = 0; i < 4; i++) {
			Vector3f a = c[i];
			Vector3f b = c[(i + 1) % 4];
			Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(tip).sub(a)).normalize();
			vertex(vc, pose, light, color, a, 0f, 1f, n);
			vertex(vc, pose, light, color, b, 1f, 1f, n);
			vertex(vc, pose, light, color, tip, 0.55f, 0f, n);
			vertex(vc, pose, light, color, tip, 0.45f, 0f, n);
		}
	}

	/** An axis-aligned box (every face the whole texture). */
	static void box(VertexConsumer vc, PoseStack.Pose pose, int light, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
		face(vc, pose, light, color, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
		face(vc, pose, light, color, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
		face(vc, pose, light, color, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
		face(vc, pose, light, color, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
		face(vc, pose, light, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
		face(vc, pose, light, color, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
	}

	static void face(VertexConsumer vc, PoseStack.Pose pose, int light, int color, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		Vector3f n = new Vector3f(nx, ny, nz);
		vertex(vc, pose, light, color, new Vector3f(ax, ay, az), 0f, 1f, n);
		vertex(vc, pose, light, color, new Vector3f(bx, by, bz), 1f, 1f, n);
		vertex(vc, pose, light, color, new Vector3f(cx, cy, cz), 1f, 0f, n);
		vertex(vc, pose, light, color, new Vector3f(dx, dy, dz), 0f, 0f, n);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, int color, Vector3f p, float u, float v, Vector3f n) {
		vc.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, n.x, n.y, n.z);
	}

	/** A flat ribbon a -> b facing {@code toCamera} (POSITION_COLOR, e.g. {@code RenderType.lightning()}). */
	static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f toCamera, float width, int argb) {
		Vector3f along = new Vector3f(b).sub(a);
		Vector3f side = along.cross(toCamera, new Vector3f());
		if (side.lengthSquared() < 1.0e-8f) {
			side.set(1, 0, 0);
		}
		side.normalize().mul(width * 0.5f);
		int al = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
		vc.addVertex(pose, a.x - side.x, a.y - side.y, a.z - side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, b.x - side.x, b.y - side.y, b.z - side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, b.x + side.x, b.y + side.y, b.z + side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, a.x + side.x, a.y + side.y, a.z + side.z).setColor(r, g, bl, al);
		// and the back face (lightning culls back faces; which side faces the camera depends on the beam)
		vc.addVertex(pose, a.x + side.x, a.y + side.y, a.z + side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, b.x + side.x, b.y + side.y, b.z + side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, b.x - side.x, b.y - side.y, b.z - side.z).setColor(r, g, bl, al);
		vc.addVertex(pose, a.x - side.x, a.y - side.y, a.z - side.z).setColor(r, g, bl, al);
	}

	/** A flat coloured quad (POSITION_COLOR). */
	static void flat(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
		int al = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
		vc.addVertex(pose, a.x, a.y, a.z).setColor(r, g, bl, al);
		vc.addVertex(pose, b.x, b.y, b.z).setColor(r, g, bl, al);
		vc.addVertex(pose, c.x, c.y, c.z).setColor(r, g, bl, al);
		vc.addVertex(pose, d.x, d.y, d.z).setColor(r, g, bl, al);
	}
}
