package com.projecthero.mod.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;

/**
 * v0.16.2: bendable arms and legs. A vanilla humanoid arm or leg is one straight 4 x 12 x 4 box, so it cannot bend at the
 * elbow or knee. While a pose has asked for a joint ({@link #setJoint}) during one player's render, every limb box of
 * every humanoid model drawn for that player -- the skin and its sleeve / pants overlay, the Spider-Man suit's skin rig,
 * vanilla armour, and the GeckoLib armour bones ({@code SuperheroArmorRenderer}) -- is drawn in two halves split at the
 * middle of the limb (the elbow, 6 px down an arm's 12; the knee, 6 px down a leg's 12): the upper half as the part is
 * posed, the lower half turned by an extra rotation about the joint (elbow / knee flexion).
 *
 * <p>The halves are one continuous skin, not two boxes: every face crossing the joint plane is cut there, and the cut
 * edge is turned by HALF the joint's rotation (a mitre), so the upper and lower faces share those vertices and no gap can
 * open on the outside of the bend, at any angle. Each face keeps its own texture coordinates, interpolated at the cut,
 * so each half shows exactly its half of the skin / suit / armour texture (slim arms included -- the cut is measured
 * from the box itself).
 *
 * <p>Limb parts are recognised by identity: {@code HumanoidModelLimbRegistryMixin} / {@code PlayerModelLimbRegistryMixin}
 * register every humanoid model's arm / leg / sleeve / pants part as it is built, and {@code ModelPartJointMixin} draws a
 * registered part's cubes through {@link #drawCubes} while a joint is set. The joints only live between
 * {@link #begin} and {@link #end} (one player's {@code PlayerRenderer.render}), so nothing else is ever bent.
 */
public final class JointedLimbs {
	public static final int RIGHT_ARM = 0;
	public static final int LEFT_ARM = 1;
	public static final int RIGHT_LEG = 2;
	public static final int LEFT_LEG = 3;

	/** Every humanoid model's limb parts (weak, identity: {@code ModelPart} has no equals). */
	private static final Map<ModelPart, Integer> LIMBS = Collections.synchronizedMap(new WeakHashMap<>());

	/** Inside a player's render? */
	private static boolean rendering;
	/** The lower-half rotation per limb, in the limb's own (vanilla) frame; null = straight. */
	private static final Quaternionf[] JOINT = new Quaternionf[4];
	private static boolean anyJoint;

	private JointedLimbs() {
	}

	public static void register(ModelPart part, int limb) {
		if (part != null) {
			LIMBS.put(part, limb);
		}
	}

	/** The limb this part is (one of the constants), or -1. */
	public static int limbOf(ModelPart part) {
		Integer i = LIMBS.get(part);
		return i == null ? -1 : i;
	}

	/** A player's render starts: no joints until its pose sets some. */
	public static void begin() {
		rendering = true;
		clear();
	}

	public static void end() {
		rendering = false;
		clear();
	}

	private static void clear() {
		anyJoint = false;
		for (int i = 0; i < 4; i++) {
			JOINT[i] = null;
		}
	}

	/** Set by a pose (inside a player's render only): the lower half of {@code limb} turned by {@code rot}. */
	public static void setJoint(int limb, Quaternionf rot) {
		if (!rendering) {
			return;
		}
		JOINT[limb] = rot;
		anyJoint |= rot != null;
	}

	public static boolean active() {
		return rendering && anyJoint;
	}

	/** The joint of {@code limb} right now, or null. */
	public static Quaternionf joint(int limb) {
		return rendering && limb >= 0 && limb < 4 ? JOINT[limb] : null;
	}

	/** The joint height for a limb (px below its pivot, vanilla frame): arms 4 (the arm box runs -2..10), legs 6. */
	public static float jointY(int limb) {
		return limb <= LEFT_ARM ? 4.0f : 6.0f;
	}

	// ------------------------------------------------------------------ geometry

	/** One vertex: position (in the caller's units) and texture coordinates. */
	public record V(float x, float y, float z, float u, float v) {
	}

	/**
	 * Draws one face cut at the joint plane and bent: vertices on the upper side unchanged, on the joint plane turned by
	 * {@code half}, on the lower side by {@code full} (both about {@code pivot}).
	 *
	 * @param down +1 when "lower" is +y (vanilla model space, y down), -1 when it is -y (GeckoLib, y up)
	 * @param scale multiplier from vertex units to the pose's units (1/16 for vanilla px, 1 for GeckoLib)
	 * @param n0 the face's own normal (unbent, same frame as the vertices)
	 */
	public static void drawFace(V[] face, Vector3f n0, Vector3f pivot, float down, Quaternionf half, Quaternionf full,
			Matrix4f pose, Matrix3f normalMat, float scale, VertexConsumer out, int colour, int overlay, int light) {
		float split = pivot.y;
		List<V> upper = new ArrayList<>(6);
		List<V> lower = new ArrayList<>(6);
		int n = face.length;
		for (int i = 0; i < n; i++) {
			V a = face[i];
			V b = face[(i + 1) % n];
			float da = (a.y() - split) * down;
			float db = (b.y() - split) * down;
			if (da <= 0f) {
				upper.add(a);
			}
			if (da >= 0f) {
				lower.add(a);
			}
			if ((da < 0f && db > 0f) || (da > 0f && db < 0f)) {
				float t = da / (da - db);
				V c = new V(a.x() + (b.x() - a.x()) * t, split, a.z() + (b.z() - a.z()) * t, a.u() + (b.u() - a.u()) * t,
						a.v() + (b.v() - a.v()) * t);
				upper.add(c);
				lower.add(c);
			}
		}
		emit(upper, n0, pivot, down, half, full, pose, normalMat, scale, out, colour, overlay, light);
		emit(lower, n0, pivot, down, half, full, pose, normalMat, scale, out, colour, overlay, light);
	}

	private static void emit(List<V> poly, Vector3f n0, Vector3f pivot, float down, Quaternionf half, Quaternionf full,
			Matrix4f pose, Matrix3f normalMat, float scale, VertexConsumer out, int colour, int overlay, int light) {
		if (poly.size() < 3) {
			return;
		}
		// is this piece above or below the cut (by its centre)?
		float cy = 0f;
		for (V v : poly) {
			cy += v.y();
		}
		cy /= poly.size();
		boolean isLower = (cy - pivot.y) * down > 1.0e-4f;
		final float eps = 1.0e-4f * (scale < 0.5f ? 16f : 1f) + 1.0e-6f;
		Vector3f[] p = new Vector3f[poly.size()];
		for (int i = 0; i < p.length; i++) {
			V v = poly.get(i);
			float d = (v.y() - pivot.y) * down;
			Vector3f q = new Vector3f(v.x() - pivot.x, v.y() - pivot.y, v.z() - pivot.z);
			if (Math.abs(d) <= eps) {
				half.transform(q);
			} else if (d > 0f) {
				full.transform(q);
			}
			p[i] = q.add(pivot);
		}
		// the bent face's normal: from its own corners, pointing the way the original normal (turned with it) points
		Vector3f ref = (isLower ? full : half).transform(new Vector3f(n0));
		Vector3f nrm = new Vector3f();
		for (int i = 0; i < p.length; i++) {
			Vector3f a = p[i];
			Vector3f b = p[(i + 1) % p.length];
			nrm.add((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y));
		}
		if (nrm.lengthSquared() < 1.0e-10f) {
			nrm.set(ref);
		} else {
			nrm.normalize();
			if (nrm.dot(ref) < 0f) {
				nrm.negate();
			}
		}
		Vector3f wn = normalMat.transform(nrm, new Vector3f());
		// quads only: a triangle repeats its last corner, a pentagon is a quad plus a triangle
		int[][] quads = p.length == 3 ? new int[][] {{0, 1, 2, 2}}
				: p.length == 4 ? new int[][] {{0, 1, 2, 3}}
						: p.length == 5 ? new int[][] {{0, 1, 2, 3}, {0, 3, 4, 4}}
								: new int[][] {{0, 1, 2, 3}, {0, 3, 4, 5}};
		Vector3f w = new Vector3f();
		for (int[] quad : quads) {
			for (int idx : quad) {
				int i = Math.min(idx, p.length - 1);
				V v = poly.get(i);
				pose.transformPosition(p[i].x * scale, p[i].y * scale, p[i].z * scale, w);
				out.addVertex(w.x, w.y, w.z, colour, v.u(), v.v(), overlay, light, wn.x, wn.y, wn.z);
			}
		}
	}

	/** Half of a joint rotation (the mitre for the cut edge). */
	public static Quaternionf half(Quaternionf full) {
		return new Quaternionf().slerp(full, 0.5f);
	}

	/**
	 * A vanilla limb part's cubes, bent (from {@code ModelPartJointMixin}, with the part's own transform already on
	 * {@code pose}). Positions are model px; the joint sits on the first cube's centre line at the limb's joint height.
	 */
	public static void drawCubes(int limb, List<ModelPart.Cube> cubes, java.util.function.Function<ModelPart.Cube, ModelPart.Polygon[]> polys,
			PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int colour) {
		Quaternionf full = joint(limb);
		Quaternionf half = half(full);
		ModelPart.Cube first = cubes.get(0);
		Vector3f pivot = new Vector3f((first.minX + first.maxX) * 0.5f, jointY(limb), (first.minZ + first.maxZ) * 0.5f);
		Matrix4f m = pose.pose();
		Matrix3f nm = pose.normal();
		for (ModelPart.Cube cube : cubes) {
			for (ModelPart.Polygon poly : polys.apply(cube)) {
				V[] face = new V[poly.vertices.length];
				for (int i = 0; i < face.length; i++) {
					ModelPart.Vertex v = poly.vertices[i];
					face[i] = new V(v.pos.x(), v.pos.y(), v.pos.z(), v.u, v.v);
				}
				drawFace(face, poly.normal, pivot, 1f, half, full, m, nm, 1f / 16f, out, colour, overlay, light);
			}
		}
	}
}
