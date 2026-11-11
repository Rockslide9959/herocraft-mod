package com.projecthero.mod.client.omnitrix.sequence;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mixin.ModelPartCubeAccessor;
import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * v0.17.3 (user: "remake the fourarms transformation sequence please, you may bend the limbs ... dont break the body or make limbs
 * detatch"): elbows and knees for the show transformations. A limb box is drawn in two halves split at its middle (the elbow 6 px
 * down a 12 px arm, the knee 6 px down a leg); the lower half turns about the joint. It is one solid limb at any angle:
 * <ul>
 *   <li><b>A true mitre.</b> Every face crossing the joint is cut there, and the cut edge is moved onto the plane that bisects the
 *       bend, keeping its depth (the upper half's outer side grows to meet the lower half's, the inner side shortens). Both halves
 *       share those vertices exactly, so nothing can open, and the limb keeps its full thickness through the bend (no pinched
 *       elbow).</li>
 *   <li><b>The skin continues across the joint.</b> Each half keeps its own half of the texture: a cut vertex takes the texture
 *       coordinate of the joint line, on both halves.</li>
 *   <li><b>No inside-out faces at a deep bend.</b> The turning axis starts on the limb's centre line and slides toward the inside
 *       of the bend only as far as needed to keep 20% of the inner side (a 150 degree bend is fine).</li>
 *   <li><b>No spike at a deep bend.</b> Past about 105 degrees the sharp outer corner of the mitre is cut off by a flat cap (a
 *       chamfer, textured with the skin round the joint line), the way a real elbow or knee rounds off.</li>
 * </ul>
 * A limb can also be <b>twisted</b> about its own long axis (the whole limb, so there is no seam): the twist picks which way the
 * elbow / knee points (forearm across the body, knees out in a wide stance).
 *
 * <p>The model's own limbs (skin, sleeves / pants, the alien's skin too) are bent through {@link JointedLimbs}' hook (its
 * {@code ModelPartJointMixin} hands a registered limb part's cubes to {@link #drawModelLimb} while a joint is set, inside that one
 * player's render only): a show calls {@link #bend} from its {@code pose()}. Extra limbs (Four Arms' second pair) draw with
 * {@link #drawPart}. Anything riding on a forearm (the watch, the swelling lumps) uses {@link #toForearm} / {@link Joint#toLower}.
 * Never used outside a show sequence, so normal rendering never changes.
 *
 * <p>Flex: + is the natural way (elbow: the forearm forward, toward the arm's front face; knee: the shin back). Twist: radians
 * about the limb's long axis (+ turns the arm's front face toward its -x side: inward for the left arm, outward for the right).
 */
public final class BentLimbs {
	public static final int RIGHT_ARM = JointedLimbs.RIGHT_ARM, LEFT_ARM = JointedLimbs.LEFT_ARM, RIGHT_LEG = JointedLimbs.RIGHT_LEG,
			LEFT_LEG = JointedLimbs.LEFT_LEG;
	/** A limb's half depth with its outer layer (px): the axis and the chamfer are measured from it, so skin and sleeve agree. */
	public static final float HALF_DEPTH = 2.25f;
	/** Half a 12 px limb. */
	public static final float LIMB_HALF = 6f;
	/** The inner side of a bend keeps at least this share of its length. */
	private static final float INNER_KEEP = 0.8f;
	/** The mitre's corner is capped past this half-bend (tan of 52.5 degrees: a 105 degree bend). */
	private static final float CHAMFER_TAN = (float) Math.tan(Math.toRadians(52.5));
	/** The deepest bend drawn (radians, ~160 degrees). */
	private static final float MAX_BEND = 2.8f;

	private static final float[] FLEX = new float[4], TWIST = new float[4];
	private static final JointedLimbs.CubeDrawer DRAWER = BentLimbs::drawModelLimb;

	private BentLimbs() {
	}

	// ================================================================ the model's own limbs

	/**
	 * From a show's {@code pose()} (inside the player's render): bend {@code limb} ({@link #RIGHT_ARM} ...) by {@code flex}
	 * radians and twist it by {@code twist}. Both ~0 leaves it vanilla.
	 */
	public static void bend(int limb, float flex, float twist) {
		FLEX[limb] = flex;
		TWIST[limb] = twist;
		JointedLimbs.setDrawer(DRAWER);
		boolean straight = Math.abs(flex) < 1e-3f && Math.abs(twist) < 1e-3f;
		// the quaternion only switches the joint on (and turns a held item roughly with the forearm); the geometry is ours
		JointedLimbs.setJoint(limb, straight ? null : new Quaternionf().rotationX(phi(limb >= RIGHT_LEG, flex)));
	}

	/** Is {@code limb} bent by us in this render? */
	public static boolean bent(int limb) {
		return JointedLimbs.drawer() == DRAWER && JointedLimbs.joint(limb) != null;
	}

	/** The joint of one of the model's own limbs as bent in this render (slim = Alex arms). */
	public static Joint modelJoint(int limb, boolean slim) {
		boolean leg = limb >= RIGHT_LEG;
		float xc = leg ? 0f : (limb == RIGHT_ARM ? -1f : 1f) * (slim ? 0.5f : 1f);
		return new Joint(FLEX[limb], TWIST[limb], leg, xc, 0f, leg ? 6f : 4f, LIMB_HALF);
	}

	/**
	 * In the limb's bone space already ({@code AlienParts.on(pose, model.leftArm, ...)}): moves {@code pose} onto the bent forearm /
	 * shin, if this render bends that limb. For anything riding on it (the watch, swelling lumps).
	 */
	public static void toForearm(PoseStack pose, int limb, boolean slim) {
		if (bent(limb)) {
			modelJoint(limb, slim).toLower(pose);
		}
	}

	/** {@link JointedLimbs.CubeDrawer}: a registered limb part's cubes, bent. */
	private static void drawModelLimb(int limb, List<ModelPart.Cube> cubes, java.util.function.Function<ModelPart.Cube, ModelPart.Polygon[]> polys,
			PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int colour) {
		ModelPart.Cube first = cubes.get(0);
		Joint jt = new Joint(FLEX[limb], TWIST[limb], limb >= RIGHT_LEG, (first.minX + first.maxX) * 0.5f, (first.minZ + first.maxZ) * 0.5f,
				(first.minY + first.maxY) * 0.5f, LIMB_HALF);
		List<ModelPart.Polygon[]> faces = new ArrayList<>(cubes.size());
		for (ModelPart.Cube c : cubes) {
			faces.add(polys.apply(c));
		}
		draw(jt, faces, pose, out, light, overlay, colour);
	}

	// ================================================================ extra limbs

	/** A cube's faces (for an extra limb drawn with {@link #drawPart}); {@code grow} inflates it like {@code CubeDeformation}. */
	public static ModelPart.Polygon[] cube(int u, int v, float x, float y, float z, float w, float h, float d, float grow, int texW, int texH) {
		ModelPart.Cube c = new ModelPart.Cube(u, v, x, y, z, w, h, d, grow, grow, grow, false, texW, texH, EnumSet.allOf(Direction.class));
		return ((ModelPartCubeAccessor) (Object) c).projecthero$polygons();
	}

	/** An extra limb: {@code faces} in {@code part}'s space (its pivot, rotation and scale), bent by {@code jt}. */
	public static void drawPart(PoseStack pose, ModelPart part, List<ModelPart.Polygon[]> faces, Joint jt, VertexConsumer out, int light) {
		pose.pushPose();
		part.translateAndRotate(pose);
		draw(jt, faces, pose.last(), out, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	// ================================================================ the joint

	/** One joint's shape, in the limb's own px frame (before its twist). */
	public static final class Joint {
		final float xc, zc, j, c, phi, sin, cos, twist, uy, uz, d;
		final boolean chamfer;
		final int outer;

		/**
		 * @param xc / zc the limb box's centre line (px)
		 * @param j the joint's height (px, model space: y down)
		 * @param halfLen the shorter half's length (px)
		 */
		public Joint(float flex, float twist, boolean leg, float xc, float zc, float j, float halfLen) {
			this.xc = xc;
			this.zc = zc;
			this.j = j;
			this.twist = twist;
			this.phi = phi(leg, flex);
			this.sin = Mth.sin(phi);
			this.cos = Mth.cos(phi);
			float t = (float) Math.tan(phi * 0.5f);
			float zmin = zc - HALF_DEPTH, zmax = zc + HALF_DEPTH, keep = INNER_KEEP * halfLen;
			// the turning axis: on the centre line, slid toward the inside of a deep bend so its inner side never turns inside out
			float axis = zc;
			if (t < -1e-4f) {
				axis = Math.min(zc, zmin + keep / -t);
			} else if (t > 1e-4f) {
				axis = Math.max(zc, zmax - keep / t);
			}
			this.c = axis;
			// the outside of the bend (+z when the lower half turns toward -z) and the mitre's outward direction in it
			this.outer = phi < 0f ? 1 : -1;
			float n = (float) Math.sqrt(1f + t * t);
			this.uy = outer * (-t / n);
			this.uz = outer * (1f / n);
			float dOut = Math.abs((outer > 0 ? zmax : zmin) - axis);
			this.chamfer = Math.abs(t) > CHAMFER_TAN;
			this.d = dOut * (float) Math.sqrt(1f + CHAMFER_TAN * CHAMFER_TAN);
		}

		float tanHalf() {
			return (float) Math.tan(phi * 0.5f);
		}

		/** The lower half's turn about the axis. */
		void lower(W p) {
			float dy = p.y - j, dz = p.z - c;
			p.y = j + dy * cos - dz * sin;
			p.z = c + dy * sin + dz * cos;
		}

		/** Signed distance past the chamfer plane (> 0 is cut off). */
		float past(W p) {
			return (p.y - j) * uy + (p.z - c) * uz - d;
		}

		/** The twist, on {@code pose} (in the limb's bone space). */
		public void toTwist(PoseStack pose) {
			if (twist != 0f) {
				pose.translate(xc / 16f, 0f, zc / 16f);
				pose.mulPose(Axis.YP.rotation(twist));
				pose.translate(-xc / 16f, 0f, -zc / 16f);
			}
		}

		/** The twist and the lower half's turn, on {@code pose} (in the limb's bone space): the forearm / shin frame. */
		public void toLower(PoseStack pose) {
			toTwist(pose);
			pose.translate(0f, j / 16f, c / 16f);
			pose.mulPose(Axis.XP.rotation(phi));
			pose.translate(0f, -j / 16f, -c / 16f);
		}
	}

	static float phi(boolean leg, float flex) {
		float f = Mth.clamp(flex, -MAX_BEND, MAX_BEND);
		return leg ? f : -f;
	}

	// ================================================================ geometry

	/** A working vertex (px, texture coordinates); {@code cut} = on the joint line. */
	static final class W {
		float x, y, z, u, v;
		boolean cut;

		W(float x, float y, float z, float u, float v, boolean cut) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.u = u;
			this.v = v;
			this.cut = cut;
		}

		W copy() {
			return new W(x, y, z, u, v, cut);
		}

		static W lerp(W a, W b, float f, boolean cut) {
			return new W(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f, a.u + (b.u - a.u) * f, a.v + (b.v - a.v) * f, cut);
		}
	}

	/** Draws boxes' faces bent by {@code jt} (positions in px, {@code pose} in the limb's bone space). */
	public static void draw(Joint jt, List<ModelPart.Polygon[]> boxes, PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int colour) {
		Matrix4f m = new Matrix4f(pose.pose());
		Matrix3f nm = new Matrix3f(pose.normal());
		if (jt.twist != 0f) {
			m.translate(jt.xc / 16f, 0f, jt.zc / 16f).rotateY(jt.twist).translate(-jt.xc / 16f, 0f, -jt.zc / 16f);
			nm.rotateY(jt.twist);
		}
		float t = jt.tanHalf();
		for (ModelPart.Polygon[] box : boxes) {
			List<W> capUp = new ArrayList<>(2), capLo = new ArrayList<>(2);
			for (ModelPart.Polygon poly : box) {
				int n = poly.vertices.length;
				List<W> up = new ArrayList<>(6), lo = new ArrayList<>(6);
				for (int i = 0; i < n; i++) {
					ModelPart.Vertex a = poly.vertices[i], b = poly.vertices[(i + 1) % n];
					float da = a.pos.y() - jt.j, db = b.pos.y() - jt.j;
					W wa = new W(a.pos.x(), a.pos.y(), a.pos.z(), a.u, a.v, Math.abs(da) < 1e-5f);
					if (da <= 1e-5f) {
						up.add(wa);
					}
					if (da >= -1e-5f) {
						lo.add(wa.copy());
					}
					if ((da < -1e-5f && db > 1e-5f) || (da > 1e-5f && db < -1e-5f)) {
						W wb = new W(b.pos.x(), b.pos.y(), b.pos.z(), b.u, b.v, false);
						W cv = W.lerp(wa, wb, da / (da - db), true);
						cv.y = jt.j;
						up.add(cv);
						lo.add(cv.copy());
					}
				}
				// the cut edge onto the mitre plane; the lower half turned
				for (W w : up) {
					if (w.cut) {
						w.y = jt.j - (w.z - jt.c) * t;
					}
				}
				for (W w : lo) {
					if (w.cut) {
						w.y = jt.j + (w.z - jt.c) * t;
					}
					jt.lower(w);
				}
				Vector3f n0 = poly.normal;
				boolean outerFace = n0.z() * jt.outer > 0.9f;
				if (jt.chamfer) {
					up = chamfer(up, jt, outerFace ? capUp : null);
					lo = chamfer(lo, jt, outerFace ? capLo : null);
				}
				emit(up, n0.x(), n0.y(), n0.z(), m, nm, out, light, overlay, colour);
				emit(lo, n0.x(), n0.y() * jt.cos - n0.z() * jt.sin, n0.y() * jt.sin + n0.z() * jt.cos, m, nm, out, light, overlay, colour);
			}
			// the chamfer's cap: between the two halves' outer faces, where the plane cut them
			if (capUp.size() == 2 && capLo.size() == 2) {
				capUp.sort((a, b) -> Float.compare(a.x, b.x));
				capLo.sort((a, b) -> Float.compare(a.x, b.x));
				List<W> cap = List.of(capUp.get(0), capUp.get(1), capLo.get(1), capLo.get(0));
				emit(cap, 0f, jt.uy, jt.uz, m, nm, out, light, overlay, colour);
			}
		}
	}

	/** Sutherland-Hodgman against the chamfer plane; the new vertices on it go to {@code onPlane}. */
	private static List<W> chamfer(List<W> in, Joint jt, List<W> onPlane) {
		int n = in.size();
		if (n < 3) {
			return in;
		}
		List<W> res = new ArrayList<>(n + 2);
		for (int i = 0; i < n; i++) {
			W a = in.get(i), b = in.get((i + 1) % n);
			float sa = jt.past(a), sb = jt.past(b);
			if (sa <= 0f) {
				res.add(a);
			}
			if ((sa < 0f && sb > 0f) || (sa > 0f && sb < 0f)) {
				W c = W.lerp(a, b, sa / (sa - sb), false);
				res.add(c);
				if (onPlane != null) {
					onPlane.add(c);
				}
			}
		}
		return res;
	}

	/** One polygon as quads (a fan; a triangle repeats its last corner), lit by its own normal turned the way {@code ref} points. */
	private static void emit(List<W> poly, float rx, float ry, float rz, Matrix4f m, Matrix3f nm, VertexConsumer out, int light, int overlay, int colour) {
		int n = poly.size();
		if (n < 3) {
			return;
		}
		float nx = 0f, ny = 0f, nz = 0f;
		for (int i = 0; i < n; i++) {
			W a = poly.get(i), b = poly.get((i + 1) % n);
			nx += (a.y - b.y) * (a.z + b.z);
			ny += (a.z - b.z) * (a.x + b.x);
			nz += (a.x - b.x) * (a.y + b.y);
		}
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) {
			nx = rx;
			ny = ry;
			nz = rz;
		} else {
			nx /= len;
			ny /= len;
			nz /= len;
			if (nx * rx + ny * ry + nz * rz < 0f) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
		}
		Vector3f wn = nm.transform(new Vector3f(nx, ny, nz));
		Vector3f p = new Vector3f();
		for (int k = 1; k < n - 1; k += 2) {
			int[] q = { 0, k, k + 1, Math.min(k + 2, n - 1) };
			for (int idx : q) {
				W v = poly.get(idx);
				m.transformPosition(v.x / 16f, v.y / 16f, v.z / 16f, p);
				out.addVertex(p.x, p.y, p.z, colour, v.u, v.v, overlay, light, wn.x, wn.y, wn.z);
			}
		}
	}
}
