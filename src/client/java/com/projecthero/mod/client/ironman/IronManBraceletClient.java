package com.projecthero.mod.client.ironman;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.IronManFaceplateLook;
import com.projecthero.mod.ironman.suit.IronManBraceletSuitUp;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.15.4: the client side of the Mark 7's bracelet wrap-on ({@link IronManBraceletSuitUp}). Only ever moves and splits the
 * suit's <em>existing</em> bones -- nothing is added to the user's model:
 * <ul>
 *   <li><b>Body pieces</b> (chestplate, leggings, boots): each bone with geometry slides in from behind and grows to full
 *       size, then is drawn as two half-shells -- the bone's own boxes clipped at its centre line (the cut faces' UVs
 *       re-interpolated, so the texture stays put) -- each half swung open on a hinge at its back corner and swinging shut
 *       around the limb ({@link #splitDeg}, consumed by {@code SuperheroArmorRenderer#renderCubesOfBone}, which
 *       draws each half through {@link #clip}).</li>
 *   <li><b>Helmet</b>: swings up out of the back about a hinge at the back of the neck ({@link IronManBraceletSuitUp#HELMET_HINGE}),
 *       faceplate hidden; near the end the faceplate flips out over the brow into its raised position, from where the
 *       ordinary H faceplate swing closes it.</li>
 * </ul>
 * Everything is read off the synced suit clock through {@link IronManSuitReveal#progress}, so every viewer sees the same
 * frame; render thread only.
 */
public final class IronManBraceletClient {
	/** This bone's half-shell opening (degrees) for the renderer, or 0 for a whole bone. Set per bone, render thread. */
	public static float splitDeg;

	/** Per baked bone: {min x, max x, min z, max z} of all its cube vertices (model units). */
	private static final Map<GeoBone, float[]> BOUNDS = new WeakHashMap<>();

	private IronManBraceletClient() {
	}

	/**
	 * Applies the wrap-on offset of {@code bone} (piece {@code bit}, at piece progress {@code p} < 1) onto {@code pose} and
	 * sets {@link #splitDeg}. Returns false if the bone is not drawn this pass.
	 */
	public static boolean apply(PoseStack pose, GeoBone bone, int bit, float p, boolean glowPass) {
		splitDeg = 0f;
		if (p <= 0f) {
			return false; // the piece is in the slot but its clock has not started yet
		}
		String name = bone.getName();
		if (bit == 0) {
			if (glowPass) {
				return false; // eyes dark until the faceplate seals
			}
			float a = IronManBraceletSuitUp.helmetAngle(p);
			if (a > 0f) {
				hinge(pose, IronManBraceletSuitUp.HELMET_HINGE, a);
			}
			if ("faceplate".equals(name)) {
				float fa = IronManBraceletSuitUp.faceplateAngle(p);
				if (fa < 0f) {
					return false; // the helmet comes out of the back without its faceplate
				}
				hinge(pose, IronManFaceplateLook.HINGE, fa);
			}
			return true;
		}
		if (bone.getCubes().isEmpty()) {
			return true; // a cubeless group: nothing of its own to move (its children ride along anyway)
		}
		if (glowPass && !IronManBraceletSuitUp.closed(p)) {
			return false; // the lights come on once the halves have shut
		}
		// v0.15.15 (user: "it opens and folds in on itself"): the piece comes on like the Mark 8 sentry's -- its FRONT stays
		// whole and only its back panels stand open like doors (SuperheroArmorRenderer#renderBackDoors); it comes in from
		// in front along the wearer's own facing (open back first, so nothing passes through the body), full size (no
		// shrunken piece growing out of the body), then the back panels swing shut round the wearer
		float slide = IronManBraceletSuitUp.slide(p) * IronManBraceletSuitUp.SLIDE_PX / 16f;
		if (slide > 0f) {
			pose.translate(0f, slide * 0.2f, -slide); // from in front (-z), a touch above
		}
		float sc = IronManBraceletSuitUp.scale(p);
		if (sc > 1f) { // only the clasp pulse once shut
			float gx = bone.getPivotX() / 16f;
			float gy = bone.getPivotY() / 16f;
			float gz = bone.getPivotZ() / 16f;
			pose.translate(gx, gy, gz);
			pose.scale(sc, sc, sc);
			pose.translate(-gx, -gy, -gz);
		}
		splitDeg = IronManBraceletSuitUp.openAngle(p);
		return true;
	}

	/** Rotate {@code pose} by {@code deg} about the X axis through a hinge given in bedrock geometry pixels. */
	private static void hinge(PoseStack pose, float[] h, float deg) {
		float hx = -h[0] / 16f;
		float hy = h[1] / 16f;
		float hz = h[2] / 16f;
		pose.translate(hx, hy, hz);
		pose.mulPose(Axis.XP.rotationDegrees(deg));
		pose.translate(-hx, -hy, -hz);
	}

	/** {min x, max x, min z, max z} over every cube vertex of {@code bone}. */
	public static float[] bounds(GeoBone bone) {
		return BOUNDS.computeIfAbsent(bone, b -> {
			float[] r = { Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE };
			for (GeoCube cube : b.getCubes()) {
				for (GeoQuad q : cube.quads()) {
					if (q == null) {
						continue;
					}
					for (GeoVertex v : q.vertices()) {
						r[0] = Math.min(r[0], v.position().x());
						r[1] = Math.max(r[1], v.position().x());
						r[2] = Math.min(r[2], v.position().z());
						r[3] = Math.max(r[3], v.position().z());
					}
				}
			}
			return r;
		});
	}

	/** One half-shell pass: which side ({@code -1} / {@code +1}), the cut line, the hinge and the swing. */
	public record Half(int side, float cutX, float hingeX, float hingeZ, float sin, float cos) {
		public static Half of(float[] bounds, int side, float deg) {
			float cut = (bounds[0] + bounds[1]) * 0.5f;
			float hingeX = side > 0 ? bounds[1] : bounds[0];
			// each half swings its front outward, away from the cut: + side turns one way, - side the other
			double a = Math.toRadians(side > 0 ? -deg : deg);
			return new Half(side, cut, hingeX, bounds[3], (float) Math.sin(a), (float) Math.cos(a));
		}

		/**
		 * v0.15.9 Sentry Mode: the same half-shell, but hinged at its FRONT corner so the BACK swings open (the standing
		 * suit opening up behind for its owner to step in / out).
		 */
		public static Half ofBack(float[] bounds, int side, float deg) {
			float cut = (bounds[0] + bounds[1]) * 0.5f;
			float hingeX = side > 0 ? bounds[1] : bounds[0];
			double a = Math.toRadians(side > 0 ? deg : -deg);
			return new Half(side, cut, hingeX, bounds[2], (float) Math.sin(a), (float) Math.cos(a));
		}

		boolean keeps(float x) {
			return side > 0 ? x >= cutX - 1.0e-4f : x <= cutX + 1.0e-4f;
		}
	}

	/** A vertex of a half-shell: position (model units) and UV. */
	public record Vert(float x, float y, float z, float u, float v) {
	}

	/**
	 * The four vertices of {@code quad} clipped to {@code half} (vertices past the cut slide onto it, their UVs interpolated
	 * along the edge) and swung about the half's hinge -- or null if the whole quad lies on the other side.
	 */
	public static Vert[] clip(GeoQuad quad, Half half) {
		GeoVertex[] vs = quad.vertices();
		boolean any = false;
		for (GeoVertex v : vs) {
			any |= half.keeps(v.position().x());
		}
		if (!any) {
			return null;
		}
		Vert[] out = new Vert[vs.length];
		for (int i = 0; i < vs.length; i++) {
			GeoVertex v = vs[i];
			float x = v.position().x(), y = v.position().y(), z = v.position().z();
			float u = v.texU(), t = v.texV();
			if (!half.keeps(x)) {
				GeoVertex partner = null;
				for (GeoVertex w : vs) {
					if (half.keeps(w.position().x()) && Math.abs(w.position().y() - y) < 1.0e-4f
							&& Math.abs(w.position().z() - z) < 1.0e-4f) {
						partner = w;
						break;
					}
				}
				if (partner != null && Math.abs(x - partner.position().x()) > 1.0e-6f) {
					float k = (half.cutX() - partner.position().x()) / (x - partner.position().x());
					u = partner.texU() + (u - partner.texU()) * k;
					t = partner.texV() + (t - partner.texV()) * k;
				}
				x = half.cutX();
			}
			// swing about the vertical hinge at the half's back corner
			float dx = x - half.hingeX(), dz = z - half.hingeZ();
			float rx = half.hingeX() + dx * half.cos() + dz * half.sin();
			float rz = half.hingeZ() - dx * half.sin() + dz * half.cos();
			out[i] = new Vert(rx, y, rz, u, t);
		}
		return out;
	}

	/** Rotate a normal (model space) by the half's swing. */
	public static float[] swingNormal(Half half, float nx, float ny, float nz) {
		return new float[] { nx * half.cos() + nz * half.sin(), ny, -nx * half.sin() + nz * half.cos() };
	}
}
