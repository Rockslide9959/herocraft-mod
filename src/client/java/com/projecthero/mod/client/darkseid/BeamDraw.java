package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * Glowing-ribbon geometry for the Omega weapons. Two buffers: the soft outer glow goes into
 * {@code RenderType.debugQuads()} (position + colour, ordinary alpha blending, no culling) so a red beam stays red
 * against a blue sky, and the hot core into {@code RenderType.lightning()} (additive -- vanilla's lightning buffer) so
 * it burns bright. Additive alone washed the v0.13.18 first draft out to pink over the sky and orange over grass.
 * All positions are relative to the current pose (the entity being rendered).
 */
public final class BeamDraw {
	private BeamDraw() {
	}

	/**
	 * One camera-facing segment {@code a -> b}, {@code widthA}/{@code widthB} half-widths, colour/alpha per end.
	 * {@code toCamera} is the camera position in the same (entity-relative) space.
	 */
	public static void segment(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 toCamera, float widthA, float widthB,
			int rgb, float alphaA, float alphaB) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-8) {
			return;
		}
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 view = toCamera.subtract(mid);
		Vec3 side = dir.cross(view);
		if (side.lengthSqr() < 1.0e-8) {
			side = dir.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0e-8) {
				side = new Vec3(1, 0, 0);
			}
		}
		side = side.normalize();
		Matrix4f m = pose.pose();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int bl = rgb & 0xFF;
		int aa = Math.max(0, Math.min(255, (int) (alphaA * 255)));
		int ab = Math.max(0, Math.min(255, (int) (alphaB * 255)));
		Vec3 a1 = a.add(side.scale(widthA));
		Vec3 a2 = a.subtract(side.scale(widthA));
		Vec3 b1 = b.add(side.scale(widthB));
		Vec3 b2 = b.subtract(side.scale(widthB));
		// both windings: RenderType.lightning() back-face culls, and which side of a camera-facing ribbon counts as
		// "front" flips with the segment's direction -- drawn one-sided, half of every beam simply vanished
		vc.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, bl, aa);
		vc.addVertex(m, (float) a2.x, (float) a2.y, (float) a2.z).setColor(r, g, bl, aa);
		vc.addVertex(m, (float) b2.x, (float) b2.y, (float) b2.z).setColor(r, g, bl, ab);
		vc.addVertex(m, (float) b1.x, (float) b1.y, (float) b1.z).setColor(r, g, bl, ab);
		vc.addVertex(m, (float) b1.x, (float) b1.y, (float) b1.z).setColor(r, g, bl, ab);
		vc.addVertex(m, (float) b2.x, (float) b2.y, (float) b2.z).setColor(r, g, bl, ab);
		vc.addVertex(m, (float) a2.x, (float) a2.y, (float) a2.z).setColor(r, g, bl, aa);
		vc.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, bl, aa);
	}

	/**
	 * A straight beam's soft outer glow ({@code glow == true}, into the debugQuads buffer) or its bright core (into the
	 * lightning buffer). Callers draw every glow first and every core second: asking a batched buffer source for a
	 * second render type closes the first one's builder, so interleaving them per segment crashes ("Not building!").
	 */
	public static void beam(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 toCamera, float width,
			int color, float alpha) {
		if (glow) {
			segment(vc, pose, a, b, toCamera, width, width, color, alpha * 0.6f, alpha * 0.6f);
		} else {
			segment(vc, pose, a, b, toCamera, width * 0.35f, width * 0.35f, color, alpha, alpha);
		}
	}
}
