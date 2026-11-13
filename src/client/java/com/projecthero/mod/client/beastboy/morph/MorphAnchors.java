package com.projecthero.mod.client.beastboy.morph;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.client.beastboy.BeastFormArt;
import com.projecthero.mod.client.beastboy.model.BeastPose;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy morph round: every body (the player model and each animal) described by the same seven <b>morph anchors</b> -- head,
 * torso, left / right front limb (arms, forelegs, wings, fins), left / right hind limb (legs, hind legs, the tail stock of a swimmer)
 * and tail. Each anchor is an oriented box in the body's feet frame (+Y up, +Z front, +X his left, blocks): centre, half extents along
 * its own axes, a rotation (its local +Y is the end the part grows from, its local +Z the part's front face) and a root point (where the
 * part sprouts from, in the box's normalised coordinates).
 *
 * <p>The player model's anchors are exact; an animal's come from a per-family template laid over the measured bounds of its idle body
 * (rendered once into a recording buffer and cached), so a new form only needs a family. Anchors a body lacks (a snake's legs, a
 * human's tail) are kept as small boxes inside the body where that part would retract into.
 */
public final class MorphAnchors {
	public static final int HEAD = 0, TORSO = 1, ARM_L = 2, ARM_R = 3, LEG_L = 4, LEG_R = 5, TAIL = 6, COUNT = 7;

	/** One anchor: centre, half extents along its local axes, local-to-body rotation, root (normalised local coordinates). */
	public record Anchor(Vector3f c, Vector3f h, Quaternionf q, Vector3f root) {
		Anchor scaled(float s) {
			return new Anchor(new Vector3f(c).mul(s), new Vector3f(h).mul(s), new Quaternionf(q), new Vector3f(root));
		}
	}

	/** A body's anchors and its size (largest bound, blocks): morphs blend shapes relative to size and the size itself geometrically. */
	public record Body(Anchor[] a, float size, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		public Body scaled(float s) {
			Anchor[] n = new Anchor[a.length];
			for (int i = 0; i < a.length; i++) {
				n[i] = a[i].scaled(s);
			}
			return new Body(n, size * s, minX * s, minY * s, minZ * s, maxX * s, maxY * s, maxZ * s);
		}
	}

	/** Orientations: local +Y (the root end) and local +Z (front face) in the body frame. */
	private static final Quaternionf UP = new Quaternionf();
	/** Lying along the body: root end forward, front face down (a quadruped's torso, a tail, a swimmer's tail stock). */
	private static final Quaternionf FWD = new Quaternionf().rotationX(Mth.HALF_PI);
	/** A left wing / fin (out at +X): root end toward the body. */
	private static final Quaternionf WING_L = new Quaternionf().rotationZ(Mth.HALF_PI);
	private static final Quaternionf WING_R = new Quaternionf().rotationZ(-Mth.HALF_PI);

	private static final Map<BeastForm, Body> CACHE = new EnumMap<>(BeastForm.class);
	private static Body human;

	private MorphAnchors() {
	}

	public static void clear() {
		CACHE.clear();
	}

	// ---------------------------------------------------------------- the player model

	/** The player model at its 0.9375 render scale (pixels * 0.9375 / 16), standing. */
	public static Body human() {
		if (human == null) {
			float k = 0.9375f / 16f;
			Anchor[] a = new Anchor[COUNT];
			a[HEAD] = box(0f, 28f * k, 0f, 4f * k, 4f * k, 4f * k, UP, 0f, 0f, 0f);
			a[TORSO] = box(0f, 18f * k, 0f, 4f * k, 6f * k, 2f * k, UP, 0f, 0f, 0f);
			a[ARM_L] = box(6f * k, 18f * k, 0f, 2f * k, 6f * k, 2f * k, UP, 0f, 1f, 0f);
			a[ARM_R] = box(-6f * k, 18f * k, 0f, 2f * k, 6f * k, 2f * k, UP, 0f, 1f, 0f);
			a[LEG_L] = box(2f * k, 6f * k, 0f, 2f * k, 6f * k, 2f * k, UP, 0f, 1f, 0f);
			a[LEG_R] = box(-2f * k, 6f * k, 0f, 2f * k, 6f * k, 2f * k, UP, 0f, 1f, 0f);
			a[TAIL] = box(0f, 12.5f * k, -2f * k, 0.6f * k, 0.6f * k, 0.6f * k, FWD, 0f, 1f, 0f);
			human = new Body(a, 32f * k, -8f * k, 0f, -2f * k, 8f * k, 32f * k, 2f * k);
		}
		return human;
	}

	/** A box given in body-frame half sizes (world axes) and turned into the orientation's local half extents. */
	private static Anchor box(float cx, float cy, float cz, float hx, float hy, float hz, Quaternionf q, float rx, float ry, float rz) {
		Vector3f world = new Vector3f(hx, hy, hz);
		Vector3f h = new Vector3f();
		for (int j = 0; j < 3; j++) {
			Vector3f axis = new Vector3f(j == 0 ? 1f : 0f, j == 1 ? 1f : 0f, j == 2 ? 1f : 0f);
			q.transform(axis);
			h.setComponent(j, Math.max(0.004f, Math.abs(axis.dot(world))));
		}
		return new Anchor(new Vector3f(cx, cy, cz), h, new Quaternionf(q), new Vector3f(rx, ry, rz));
	}

	// ---------------------------------------------------------------- animals

	public static Body of(BeastForm form) {
		if (form == null || form == BeastForm.HUMAN) {
			return human();
		}
		Body b = CACHE.get(form);
		if (b == null) {
			b = build(form);
			CACHE.put(form, b);
		}
		return b;
	}

	private enum Family {
		QUAD, APE, TREX, FLYER, SWIM, OCTOPUS, SNAKE, INSECT
	}

	private static Family family(BeastForm f) {
		return switch (f) {
			case GORILLA, BEAST -> Family.APE;
			case TREX -> Family.TREX;
			case HAWK, BAT, PTERODACTYL -> Family.FLYER;
			case SHARK, DOLPHIN, WHALE -> Family.SWIM;
			case OCTOPUS -> Family.OCTOPUS;
			case SNAKE -> Family.SNAKE;
			case FLY -> Family.INSECT;
			default -> Family.QUAD;
		};
	}

	/**
	 * A template anchor in fractions of the body's bounds: x from his right (0) to his left (1), y from the ground up, z from the back
	 * (0) to the front (1); orientation and root.
	 */
	private record T(float x0, float x1, float y0, float y1, float z0, float z1, Quaternionf q, float rx, float ry, float rz) {
	}

	private static T t(float x0, float x1, float y0, float y1, float z0, float z1, Quaternionf q, float rx, float ry, float rz) {
		return new T(x0, x1, y0, y1, z0, z1, q, rx, ry, rz);
	}

	/** Templates: HEAD, TORSO, ARM_L, ARM_R, LEG_L, LEG_R, TAIL. */
	private static T[] template(Family fam) {
		return switch (fam) {
			case QUAD -> new T[] {
					t(0.25f, 0.75f, 0.5f, 1f, 0.74f, 1f, UP, 0f, -0.5f, -1f),
					t(0.15f, 0.85f, 0.35f, 0.85f, 0.25f, 0.78f, FWD, 0f, 0f, 0f),
					t(0.6f, 0.95f, 0f, 0.5f, 0.58f, 0.78f, UP, 0f, 1f, 0f),
					t(0.05f, 0.4f, 0f, 0.5f, 0.58f, 0.78f, UP, 0f, 1f, 0f),
					t(0.6f, 0.95f, 0f, 0.5f, 0.14f, 0.36f, UP, 0f, 1f, 0f),
					t(0.05f, 0.4f, 0f, 0.5f, 0.14f, 0.36f, UP, 0f, 1f, 0f),
					t(0.42f, 0.58f, 0.45f, 0.8f, 0f, 0.16f, FWD, 0f, 1f, 0f) };
			case APE -> new T[] {
					t(0.3f, 0.7f, 0.74f, 1f, 0.45f, 0.95f, UP, 0f, -1f, -0.5f),
					t(0.2f, 0.8f, 0.36f, 0.82f, 0.2f, 0.8f, UP, 0f, 0f, 0f),
					t(0.76f, 1f, 0f, 0.82f, 0.35f, 0.9f, UP, 0f, 1f, 0f),
					t(0f, 0.24f, 0f, 0.82f, 0.35f, 0.9f, UP, 0f, 1f, 0f),
					t(0.53f, 0.8f, 0f, 0.4f, 0.1f, 0.5f, UP, 0f, 1f, 0f),
					t(0.2f, 0.47f, 0f, 0.4f, 0.1f, 0.5f, UP, 0f, 1f, 0f),
					t(0.46f, 0.54f, 0.36f, 0.44f, 0.1f, 0.18f, FWD, 0f, 1f, 0f) };
			case TREX -> new T[] {
					t(0.3f, 0.7f, 0.68f, 1f, 0.74f, 1f, UP, 0f, -0.5f, -1f),
					t(0.2f, 0.8f, 0.38f, 0.76f, 0.38f, 0.76f, FWD, 0f, 0f, 0f),
					t(0.55f, 0.72f, 0.42f, 0.6f, 0.68f, 0.8f, UP, 0f, 1f, 0f),
					t(0.28f, 0.45f, 0.42f, 0.6f, 0.68f, 0.8f, UP, 0f, 1f, 0f),
					t(0.58f, 0.9f, 0f, 0.5f, 0.38f, 0.62f, UP, 0f, 1f, 0f),
					t(0.1f, 0.42f, 0f, 0.5f, 0.38f, 0.62f, UP, 0f, 1f, 0f),
					t(0.4f, 0.6f, 0.38f, 0.66f, 0f, 0.4f, FWD, 0f, 1f, 0f) };
			case FLYER -> new T[] {
					t(0.38f, 0.62f, 0.5f, 1f, 0.74f, 1f, UP, 0f, -0.5f, -1f),
					t(0.36f, 0.64f, 0.3f, 0.8f, 0.3f, 0.76f, FWD, 0f, 0f, 0f),
					t(0.62f, 1f, 0.4f, 0.85f, 0.3f, 0.8f, WING_L, 0f, 1f, 0f),
					t(0f, 0.38f, 0.4f, 0.85f, 0.3f, 0.8f, WING_R, 0f, 1f, 0f),
					t(0.5f, 0.62f, 0f, 0.36f, 0.36f, 0.52f, UP, 0f, 1f, 0f),
					t(0.38f, 0.5f, 0f, 0.36f, 0.36f, 0.52f, UP, 0f, 1f, 0f),
					t(0.4f, 0.6f, 0.36f, 0.66f, 0f, 0.3f, FWD, 0f, 1f, 0f) };
			case SWIM -> new T[] {
					t(0.25f, 0.75f, 0.2f, 0.85f, 0.76f, 1f, UP, 0f, 0f, -1f),
					t(0.15f, 0.85f, 0.15f, 0.85f, 0.36f, 0.78f, FWD, 0f, 0f, 0f),
					t(0.75f, 1f, 0.1f, 0.4f, 0.54f, 0.7f, WING_L, 0f, 1f, 0f),
					t(0f, 0.25f, 0.1f, 0.4f, 0.54f, 0.7f, WING_R, 0f, 1f, 0f),
					t(0.5f, 0.68f, 0.25f, 0.7f, 0.1f, 0.38f, FWD, 0f, 1f, 0f),
					t(0.32f, 0.5f, 0.25f, 0.7f, 0.1f, 0.38f, FWD, 0f, 1f, 0f),
					t(0.2f, 0.8f, 0.2f, 0.8f, 0f, 0.12f, FWD, 0f, 1f, 0f) };
			case OCTOPUS -> new T[] {
					t(0.3f, 0.7f, 0.56f, 1f, 0.3f, 0.7f, UP, 0f, -1f, 0f),
					t(0.3f, 0.7f, 0.34f, 0.6f, 0.3f, 0.7f, UP, 0f, 0f, 0f),
					t(0.55f, 1f, 0f, 0.4f, 0.45f, 1f, UP, 0f, 1f, 0f),
					t(0f, 0.45f, 0f, 0.4f, 0.45f, 1f, UP, 0f, 1f, 0f),
					t(0.55f, 1f, 0f, 0.4f, 0f, 0.55f, UP, 0f, 1f, 0f),
					t(0f, 0.45f, 0f, 0.4f, 0f, 0.55f, UP, 0f, 1f, 0f),
					t(0.47f, 0.53f, 0.42f, 0.48f, 0.35f, 0.42f, FWD, 0f, 1f, 0f) };
			case SNAKE -> new T[] {
					t(0.3f, 0.7f, 0.35f, 1f, 0.78f, 1f, UP, 0f, -0.5f, -1f),
					t(0.2f, 0.8f, 0f, 0.6f, 0.42f, 0.84f, FWD, 0f, 0f, 0f),
					t(0.5f, 0.6f, 0.25f, 0.4f, 0.72f, 0.78f, UP, 0f, 1f, 0f),
					t(0.4f, 0.5f, 0.25f, 0.4f, 0.72f, 0.78f, UP, 0f, 1f, 0f),
					t(0.5f, 0.8f, 0f, 0.5f, 0.1f, 0.44f, FWD, 0f, 1f, 0f),
					t(0.2f, 0.5f, 0f, 0.5f, 0.1f, 0.44f, FWD, 0f, 1f, 0f),
					t(0.4f, 0.6f, 0f, 0.35f, 0f, 0.12f, FWD, 0f, 1f, 0f) };
			case INSECT -> new T[] {
					t(0.3f, 0.7f, 0.3f, 0.85f, 0.74f, 1f, UP, 0f, 0f, -1f),
					t(0.3f, 0.7f, 0.2f, 0.72f, 0.22f, 0.76f, FWD, 0f, 0f, 0f),
					t(0.55f, 1f, 0.55f, 1f, 0.2f, 0.72f, WING_L, 0f, 1f, 0f),
					t(0f, 0.45f, 0.55f, 1f, 0.2f, 0.72f, WING_R, 0f, 1f, 0f),
					t(0.55f, 0.78f, 0f, 0.3f, 0.3f, 0.7f, UP, 0f, 1f, 0f),
					t(0.22f, 0.45f, 0f, 0.3f, 0.3f, 0.7f, UP, 0f, 1f, 0f),
					t(0.45f, 0.55f, 0.3f, 0.5f, 0f, 0.14f, FWD, 0f, 1f, 0f) };
		};
	}

	private static Body build(BeastForm form) {
		Bounds rec = measure(form);
		float[] bb = rec == null ? null : new float[] { rec.min.x, rec.min.y, rec.min.z, rec.max.x, rec.max.y, rec.max.z };
		if (bb == null) {
			float w = form.width, h = form.height, l = Math.max(form.width, form.height) * 1.2f;
			bb = new float[] { -w * 0.5f, 0f, -l * 0.5f, w * 0.5f, h, l * 0.5f };
		}
		// keep thin bodies from collapsing an axis
		float sx = Math.max(bb[3] - bb[0], 0.05f), sy = Math.max(bb[4] - bb[1], 0.05f), sz = Math.max(bb[5] - bb[2], 0.05f);
		T[] tp = template(family(form));
		Anchor[] a = new Anchor[COUNT];
		for (int i = 0; i < COUNT; i++) {
			T t = tp[i];
			float x0 = bb[0] + t.x0 * sx, x1 = bb[0] + t.x1 * sx;
			float y0 = bb[1] + t.y0 * sy, y1 = bb[1] + t.y1 * sy;
			float z0 = bb[2] + t.z0 * sz, z1 = bb[2] + t.z1 * sz;
			// heads swap about their centre (the new head forms inside the old one); limbs, wings and tails sprout from their roots
			boolean head = i == HEAD;
			a[i] = box((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f, (x1 - x0) * 0.5f, (y1 - y0) * 0.5f, (z1 - z0) * 0.5f, t.q, head ? 0f : t.rx,
					head ? 0f : t.ry, head ? 0f : t.rz);
		}
		if (rec != null) {
			fit(a, rec.pts);
		}
		return new Body(a, Math.max(sx, Math.max(sy, sz)), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
	}

	/**
	 * Fits the template boxes to the body: every measured vertex goes to the anchor it sits deepest in (or nearest), and each anchor box
	 * moves most of the way to the bounds of its vertices (in its own axes), so a part's geometry fills its box and the morph maps it
	 * cleanly instead of stretching what pokes out.
	 */
	private static void fit(Anchor[] a, java.util.List<Vector3f> pts) {
		Vector3f[] lo = new Vector3f[COUNT], hi = new Vector3f[COUNT];
		int[] n = new int[COUNT];
		Quaternionf[] inv = new Quaternionf[COUNT];
		for (int i = 0; i < COUNT; i++) {
			lo[i] = new Vector3f(Float.MAX_VALUE);
			hi[i] = new Vector3f(-Float.MAX_VALUE);
			inv[i] = new Quaternionf(a[i].q()).conjugate();
		}
		Vector3f l = new Vector3f();
		for (Vector3f p : pts) {
			int best = -1;
			float bd = Float.MAX_VALUE;
			for (int i = 0; i < COUNT; i++) {
				inv[i].transform(l.set(p).sub(a[i].c()));
				Vector3f h = a[i].h();
				float d = Math.max(Math.abs(l.x) - h.x, Math.max(Math.abs(l.y) - h.y, Math.abs(l.z) - h.z));
				if (d < bd) {
					bd = d;
					best = i;
				}
			}
			inv[best].transform(l.set(p).sub(a[best].c()));
			lo[best].min(l);
			hi[best].max(l);
			n[best]++;
		}
		for (int i = 0; i < COUNT; i++) {
			if (n[i] < 12) {
				continue;
			}
			Vector3f mid = new Vector3f(lo[i]).add(hi[i]).mul(0.5f);
			Vector3f half = new Vector3f(hi[i]).sub(lo[i]).mul(0.5f);
			Vector3f c = a[i].q().transform(mid, new Vector3f()).add(a[i].c());
			a[i].c().lerp(c, 0.75f);
			a[i].h().lerp(half, 0.75f).max(new Vector3f(0.004f));
		}
	}

	/** The idle body's vertices and bounds in its feet frame, or null when nothing was drawn. */
	static Bounds measure(BeastForm form) {
		Bounds rec = new Bounds();
		BeastPose s = new BeastPose();
		s.age = 0f;
		try {
			BeastFormArt.renderPose(form, s, new PoseStack(), rec, LightTexture.FULL_BRIGHT, 0xFFFFFFFF);
		} catch (RuntimeException e) {
			return null;
		}
		return rec.n < 8 ? null : rec;
	}

	/** Records the bounds of every vertex drawn into it (any render type). */
	private static final class Bounds implements MultiBufferSource, VertexConsumer {
		final Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
		int n;
		final java.util.List<Vector3f> pts = new java.util.ArrayList<>();

		@Override
		public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
			return this;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
				min.min(new Vector3f(x, y, z));
				max.max(new Vector3f(x, y, z));
				if (pts.size() < 40000) {
					pts.add(new Vector3f(x, y, z));
				}
				n++;
			}
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}
	}
}
