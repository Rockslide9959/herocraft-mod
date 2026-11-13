package com.projecthero.mod.client.omnitrix.sequence;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 (the v1b touch-ups of Wildmutt, Diamondhead and Ripjaws): cel-shaded facets growing flat out of a body part's surface,
 * in place of the v1 loose boxes that stood off the limbs at random angles. A shard is a low four-sided pyramid on a diamond base
 * lying ON the face it grows from (so it can never float off the body): the base sits on the surface, the ridge rises {@code height}
 * px out of it, and each of its four facets takes one of three flat shades (light on one side, dark on the other, mid at the tip),
 * the way the show paints crystal, scales and fur tufts. Drawn with the plain white texture and vertex colours, cutout, no culling.
 */
final class Shards {
	static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	/** Faces of a part box: +x, -x, +z, -z, -y (top), +y (bottom). */
	static final int PX = 0, NX = 1, PZ = 2, NZ = 3, TOP = 4, BOTTOM = 5;

	private Shards() {
	}

	/**
	 * One shard on the face {@code face} at the surface point {@code x y z} (px, the part's space). {@code len} along the in-face
	 * direction turned {@code angle} degrees from the part's long axis, {@code wid} half-width across it, {@code height} out of the
	 * face, the apex pushed {@code lean} (0..1) toward the tip. {@code g} scales it (0 = not yet grown).
	 */
	static void draw(PoseStack pose, VertexConsumer vc, int face, float x, float y, float z, float len, float wid, float height, float angle, float lean,
			float g, int light, int cLight, int cMid, int cDark) {
		if (g <= 0.01f) {
			return;
		}
		float nx = 0, ny = 0, nz = 0, ux = 0, uy = 0, uz = 0;
		switch (face) {
			case PX -> {
				nx = 1;
				uy = 1;
			}
			case NX -> {
				nx = -1;
				uy = 1;
			}
			case PZ -> {
				nz = 1;
				uy = 1;
			}
			case NZ -> {
				nz = -1;
				uy = 1;
			}
			case TOP -> {
				ny = -1;
				uz = 1;
			}
			default -> {
				ny = 1;
				uz = 1;
			}
		}
		// v = n x u, then u / v turned by the angle in the face
		float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
		float ca = Mth.cos(angle * Mth.DEG_TO_RAD), sa = Mth.sin(angle * Mth.DEG_TO_RAD);
		float ax = ux * ca + vx * sa, ay = uy * ca + vy * sa, az = uz * ca + vz * sa;
		float bx = vx * ca - ux * sa, by = vy * ca - uy * sa, bz = vz * ca - uz * sa;
		float L = len * g, W = wid * g, H = height * g;
		float[] back = { x - ax * L * 0.45f, y - ay * L * 0.45f, z - az * L * 0.45f };
		float[] tip = { x + ax * L * 0.55f, y + ay * L * 0.55f, z + az * L * 0.55f };
		float[] right = { x + bx * W, y + by * W, z + bz * W };
		float[] left = { x - bx * W, y - by * W, z - bz * W };
		float off = (lean - 0.2f) * L;
		float[] apex = { x + ax * off + nx * H, y + ay * off + ny * H, z + az * off + nz * H };
		PoseStack.Pose p = pose.last();
		tri(p, vc, back, right, apex, cLight, light);
		tri(p, vc, right, tip, apex, cMid, light);
		tri(p, vc, tip, left, apex, cDark, light);
		tri(p, vc, left, back, apex, cMid, light);
	}

	// ================================================================ on the model's limbs, bent or not

	/** The elbow / knee height in the limb's bone space (px). */
	static float jointY(int limb) {
		return limb >= BentLimbs.RIGHT_LEG ? 6f : 4f;
	}

	/** The joint of one of the model's limbs in this render (straight if the show has not bent it). */
	static BentLimbs.Joint joint(int limb, boolean slim) {
		if (BentLimbs.bent(limb)) {
			return BentLimbs.modelJoint(limb, slim);
		}
		boolean leg = limb >= BentLimbs.RIGHT_LEG;
		float xc = leg ? 0f : (limb == BentLimbs.RIGHT_ARM ? -1f : 1f) * (slim ? 0.5f : 1f);
		return new BentLimbs.Joint(0f, 0f, leg, xc, 0f, jointY(limb), BentLimbs.LIMB_HALF);
	}

	/**
	 * A skin over one of the model's limbs from {@code y0} to {@code y1} (px down the limb), {@code grow} px proud of it, bent with
	 * the limb (a true mitre through {@link BentLimbs#draw}: it never gaps or detaches at the elbow / knee).
	 */
	static void sleeve(PoseStack pose, VertexConsumer vc, ModelPart part, int limb, boolean slim, float y0, float y1, float grow, int argb, int light) {
		if (y1 - y0 < 0.05f) {
			return;
		}
		boolean leg = limb >= BentLimbs.RIGHT_LEG;
		float w = leg || !slim ? 4f : 3f;
		float x0 = leg ? -2f : (limb == BentLimbs.RIGHT_ARM ? (slim ? -2f : -3f) : -1f);
		float top = leg ? 0f : -2f;
		ModelPart.Polygon[] faces = BentLimbs.cube(0, 0, x0, top + y0, -2f, w, y1 - y0, 4f, grow, 16, 16);
		BentLimbs.Joint jt = joint(limb, slim);
		AlienParts.on(pose, part, () -> BentLimbs.draw(jt, java.util.List.<ModelPart.Polygon[]>of(faces), pose.last(), vc, light, OverlayTexture.NO_OVERLAY, argb));
	}

	/**
	 * Runs {@code draw} in one of the model's limbs' bone space, on the bent forearm / shin when {@code y} (px, bone space) is below
	 * the joint.
	 */
	static void onLimb(PoseStack pose, ModelPart part, int limb, boolean slim, float y, Runnable draw) {
		AlienParts.on(pose, part, () -> {
			if (y > jointY(limb)) {
				BentLimbs.toForearm(pose, limb, slim);
			}
			draw.run();
		});
	}

	/** A flat quad (px corners, the part's space), one colour. */
	static void quad(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, float[] c, float[] d, int argb, int light) {
		emit(p, vc, a, b, c, d, argb, light);
	}

	static void tri(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, float[] c, int argb, int light) {
		emit(p, vc, a, b, c, c, argb, light);
	}

	private static void emit(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, float[] c, float[] d, int argb, int light) {
		float e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
		float e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
		float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
		float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1e-6f) {
			return;
		}
		Matrix4f m = p.pose();
		Matrix3f nm = p.normal();
		Vector3f n = nm.transform(new Vector3f(nx / l, ny / l, nz / l));
		int al = (argb >>> 24) & 0xFF;
		int col = (al == 0 ? 0xFF000000 : (al << 24)) | (argb & 0xFFFFFF);
		Vector3f v = new Vector3f();
		for (float[] q : new float[][] { a, b, c, d }) {
			m.transformPosition(q[0] / 16f, q[1] / 16f, q[2] / 16f, v);
			vc.addVertex(v.x, v.y, v.z, col, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
		}
	}

	/** A thick line on the holder's stage (screen units, like {@code Stage.rect}). */
	static void line(Stage st, VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float w, int rgb, float a) {
		float asp = st.aspect();
		float dx = (x1 - x0) * asp, dy = y1 - y0;
		float len = Mth.sqrt(dx * dx + dy * dy);
		if (len < 1e-4f) {
			return;
		}
		float nx = -dy / len * w / asp, ny = dx / len * w;
		st.put(vc, p, x0 + nx, y0 + ny, rgb, a);
		st.put(vc, p, x1 + nx, y1 + ny, rgb, a);
		st.put(vc, p, x1 - nx, y1 - ny, rgb, a);
		st.put(vc, p, x0 - nx, y0 - ny, rgb, a);
	}
}
