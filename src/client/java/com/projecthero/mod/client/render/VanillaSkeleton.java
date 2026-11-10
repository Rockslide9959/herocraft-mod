package com.projecthero.mod.client.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mixin.ModelPartCubeAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 (user: "for the player's skeleton use the same model as the skeleton mob in Minecraft, do the same for the Titan
 * corpse skeletons"): the vanilla skeleton mob's own geometry ({@link ModelLayers#SKELETON}, baked from the game's
 * entity model set -- the 8 px skull, the ribcage body and the thin 2 px arm and leg bones) and its own texture, drawn one
 * part at a time wherever a body needs bones: Undead's Bone and flesh regrowth and the Titan corpse / severed limbs.
 *
 * <p>A part can be drawn clipped to a band of its own height ({@code y0..y1}, part-local model px, +y down), which is how
 * a bone grows out of a stump (the band widens from the joint outward) and how one straight vanilla arm or leg is shared
 * between the Titan's upper and lower limb bones (each draws its own band, so the bone bends at the elbow / knee). A cut
 * that falls inside a cube is closed with that cube's own end face, so a growing bone has a proper tip.
 */
public final class VanillaSkeleton {
	public static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

	public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
	private static final String[] NAMES = { "head", "body", "right_arm", "left_arm", "right_leg", "left_leg" };

	/** One face in part-local model px. */
	private record Face(float[] x, float[] y, float[] z, float[] u, float[] v, Vector3f n) {
	}

	/** One cube of a part: its faces and its bounds. */
	private record Box(Face[] faces, float minY, float maxY, Face top, Face bottom) {
	}

	private static Box[][] parts;
	private static float[][] offsets;

	private VanillaSkeleton() {
	}

	private static void bake() {
		if (parts != null) {
			return;
		}
		ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SKELETON);
		Box[][] p = new Box[NAMES.length][];
		float[][] o = new float[NAMES.length][];
		for (int i = 0; i < NAMES.length; i++) {
			ModelPart part = root.getChild(NAMES[i]);
			o[i] = new float[] { part.getInitialPose().x, part.getInitialPose().y, part.getInitialPose().z };
			List<Box> boxes = new ArrayList<>();
			part.visit(new PoseStack(), (pose, path, index, cube) -> {
				if (!path.isEmpty()) {
					return; // only the part's own cubes (none of the vanilla parts has children, but be sure)
				}
				ModelPart.Polygon[] polys = ((ModelPartCubeAccessor) (Object) cube).projecthero$polygons();
				Face[] faces = new Face[polys.length];
				Face top = null, bottom = null;
				for (int k = 0; k < polys.length; k++) {
					ModelPart.Vertex[] vs = polys[k].vertices;
					int n = vs.length;
					Face f = new Face(new float[n], new float[n], new float[n], new float[n], new float[n], new Vector3f(polys[k].normal));
					boolean allMin = true, allMax = true;
					for (int j = 0; j < n; j++) {
						f.x[j] = vs[j].pos.x();
						f.y[j] = vs[j].pos.y();
						f.z[j] = vs[j].pos.z();
						f.u[j] = vs[j].u;
						f.v[j] = vs[j].v;
						allMin &= Math.abs(f.y[j] - cube.minY) < 1.0e-4f;
						allMax &= Math.abs(f.y[j] - cube.maxY) < 1.0e-4f;
					}
					if (allMin) {
						top = f;
					}
					if (allMax) {
						bottom = f;
					}
					faces[k] = f;
				}
				boxes.add(new Box(faces, cube.minY, cube.maxY, top, bottom));
			});
			p[i] = boxes.toArray(new Box[0]);
		}
		offsets = o;
		parts = p;
	}

	/** The part's pivot in the vanilla skeleton model (model px): the shoulder at (-5, 2, 0) for the right arm, etc. */
	public static float[] offset(int part) {
		bake();
		return offsets[part];
	}

	/**
	 * Draws {@code part} in its own space (the pose already at the part's pivot, model px / 16 units, +y down), only the
	 * band {@code y0..y1} of it, each cut closed with the cube's own end face.
	 */
	public static void draw(PoseStack.Pose pose, VertexConsumer out, int part, float y0, float y1, int light, int overlay, int colour) {
		bake();
		if (y1 <= y0 + 1.0e-4f) {
			return;
		}
		Matrix4f m = pose.pose();
		Matrix3f nm = pose.normal();
		Vector3f w = new Vector3f(), wn = new Vector3f();
		float[] px = new float[8], py = new float[8], pz = new float[8], pu = new float[8], pv = new float[8];
		float[] qx = new float[8], qy = new float[8], qz = new float[8], qu = new float[8], qv = new float[8];
		for (Box b : parts[part]) {
			float lo = Math.max(y0, b.minY), hi = Math.min(y1, b.maxY);
			if (hi <= lo + 1.0e-4f) {
				continue;
			}
			for (Face f : b.faces) {
				// clip to y >= lo, then y <= hi (Sutherland-Hodgman, one plane at a time)
				int n = clip(f.x, f.y, f.z, f.u, f.v, f.x.length, lo, 1f, px, py, pz, pu, pv);
				n = clip(px, py, pz, pu, pv, n, hi, -1f, qx, qy, qz, qu, qv);
				emit(m, nm, out, qx, qy, qz, qu, qv, n, Float.NaN, f.n, light, overlay, colour, w, wn);
			}
			// the cut ends, closed with the cube's own end faces moved to the cut
			if (lo > b.minY + 1.0e-4f && b.top != null) {
				Face f = b.top;
				emit(m, nm, out, f.x, f.y, f.z, f.u, f.v, f.x.length, lo, f.n, light, overlay, colour, w, wn);
			}
			if (hi < b.maxY - 1.0e-4f && b.bottom != null) {
				Face f = b.bottom;
				emit(m, nm, out, f.x, f.y, f.z, f.u, f.v, f.x.length, hi, f.n, light, overlay, colour, w, wn);
			}
		}
	}

	/** Keeps the side of the plane y = {@code at} where {@code (y - at) * keep >= 0}. Returns the new vertex count. */
	private static int clip(float[] x, float[] y, float[] z, float[] u, float[] v, int n, float at, float keep, float[] ox, float[] oy,
			float[] oz, float[] ou, float[] ov) {
		int k = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float da = (y[i] - at) * keep, db = (y[j] - at) * keep;
			if (da >= 0f) {
				ox[k] = x[i];
				oy[k] = y[i];
				oz[k] = z[i];
				ou[k] = u[i];
				ov[k] = v[i];
				k++;
			}
			if ((da < 0f && db > 0f) || (da > 0f && db < 0f)) {
				float t = da / (da - db);
				ox[k] = x[i] + (x[j] - x[i]) * t;
				oy[k] = at;
				oz[k] = z[i] + (z[j] - z[i]) * t;
				ou[k] = u[i] + (u[j] - u[i]) * t;
				ov[k] = v[i] + (v[j] - v[i]) * t;
				k++;
			}
		}
		return k;
	}

	/** A convex polygon as quads (a triangle repeats its last corner). {@code atY}: NaN, or the y to flatten it onto. */
	private static void emit(Matrix4f m, Matrix3f nm, VertexConsumer out, float[] x, float[] y, float[] z, float[] u, float[] v, int n,
			float atY, Vector3f normal, int light, int overlay, int colour, Vector3f w, Vector3f wn) {
		if (n < 3) {
			return;
		}
		nm.transform(normal, wn);
		float nx = wn.x, ny = wn.y, nz = wn.z;
		for (int s = 1; s + 1 < n; s += 2) {
			int[] idx = { 0, s, s + 1, Math.min(s + 2, n - 1) };
			for (int i : idx) {
				float yy = Float.isNaN(atY) ? y[i] : atY;
				m.transformPosition(x[i] / 16f, yy / 16f, z[i] / 16f, w);
				out.addVertex(w.x, w.y, w.z, colour, u[i], v[i], overlay, light, nx, ny, nz);
			}
		}
	}
}
