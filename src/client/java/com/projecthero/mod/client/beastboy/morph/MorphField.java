package com.projecthero.mod.client.beastboy.morph;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.client.beastboy.morph.MorphAnchors.Anchor;
import com.projecthero.mod.client.beastboy.morph.MorphAnchors.Body;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy morph round: the deformation one body is drawn through during a morph. Every vertex is skinned to the body's own
 * anchors (soft weights from how deep inside each anchor box it sits), expressed in that anchor's normalised box coordinates, and put
 * back through the <b>blended</b> anchor frame -- the source body's anchor turned, moved and resized part-way toward the target's (size
 * blended geometrically, so a whale to a fly reads as steady shrinking). Both bodies use the same blended frames, so the old body
 * reshapes into the new one's proportions while it shrinks back into each part's root and the new body grows out of those roots.
 *
 * <p>At blend 0 the source's frames are its own anchors and at 1 the target's, so a body is drawn exactly as itself at its own end of
 * the morph whatever the weights (no snap when the normal renderer takes over). Render thread only; reused per draw.
 */
final class MorphField {
	private final Anchor[] own;
	private final Quaternionf[] ownInv = new Quaternionf[MorphAnchors.COUNT];
	private final Vector3f[] fc = new Vector3f[MorphAnchors.COUNT], fh = new Vector3f[MorphAnchors.COUNT], froot = new Vector3f[MorphAnchors.COUNT];
	private final Quaternionf[] fq = new Quaternionf[MorphAnchors.COUNT];
	/** Body-frame rotation each anchor's geometry has turned through (for the normals). */
	final Quaternionf[] delta = new Quaternionf[MorphAnchors.COUNT];
	/** Per-anchor growth about the part's root: 1 = full size, 0 = shrunk into the root. */
	final float[] k = new float[MorphAnchors.COUNT];
	private final float invSigma;
	/** Whole-body squash and stretch about the feet. */
	float sy = 1f, sxz = 1f;
	/** Skin green 0..1, the energy glow over the body and along the seams (glow pass). */
	float green, glowBase, glowSeam;

	// per-vertex outputs
	float seam;
	int dom;

	private final float[] sd = new float[MorphAnchors.COUNT], w = new float[MorphAnchors.COUNT];
	private final Vector3f[] loc = new Vector3f[MorphAnchors.COUNT];
	private final Vector3f tmp = new Vector3f(), acc = new Vector3f();

	/**
	 * @param own the body being drawn (its geometry is skinned to these anchors)
	 * @param src the body the morph starts from, {@code tgt} the one it ends as; {@code f} 0..1 how far the shape has blended
	 */
	MorphField(Body own, Body src, Body tgt, float f) {
		this.own = own.a();
		float hs = Math.max(0.01f, src.size()), ht = Math.max(0.01f, tgt.size());
		float size = (float) Math.exp(Mth.lerp(f, Math.log(hs), Math.log(ht)));
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			Anchor a = this.own[i], s = src.a()[i], t = tgt.a()[i];
			ownInv[i] = new Quaternionf(a.q()).conjugate();
			fc[i] = new Vector3f(s.c()).div(hs).lerp(new Vector3f(t.c()).div(ht), f).mul(size);
			fh[i] = new Vector3f(s.h()).div(hs).lerp(new Vector3f(t.h()).div(ht), f).mul(size);
			fq[i] = new Quaternionf(s.q()).slerp(t.q(), f);
			Vector3f rn = new Vector3f(s.root()).lerp(t.root(), f);
			froot[i] = fq[i].transform(rn.mul(fh[i]), new Vector3f()).add(fc[i]);
			delta[i] = new Quaternionf(fq[i]).mul(ownInv[i]);
			k[i] = 1f;
			loc[i] = new Vector3f();
		}
		invSigma = 1f / (0.035f * Math.max(0.05f, own.size()));
	}

	/** Same body, frames fixed at its own anchors: positions untouched (the quick-morph partials: only the colour and the glow). */
	MorphField(Body own) {
		this(own, own, own, 0f);
		identity = true;
	}

	private boolean identity;

	/** Deforms {@code p} (body frame, in place); sets {@link #seam} and {@link #dom}. */
	void deform(Vector3f p) {
		if (identity) {
			seam = 0f;
			dom = MorphAnchors.TORSO;
			return;
		}
		float min = Float.MAX_VALUE;
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			Anchor a = own[i];
			Vector3f l = ownInv[i].transform(tmp.set(p).sub(a.c()), loc[i]);
			Vector3f h = a.h();
			float d = Math.max(Math.abs(l.x) - h.x, Math.max(Math.abs(l.y) - h.y, Math.abs(l.z) - h.z));
			sd[i] = d;
			if (d < min) {
				min = d;
			}
		}
		float sum = 0f, best = 0f;
		dom = 0;
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			float wi = (float) Math.exp(-(sd[i] - min) * invSigma);
			w[i] = wi;
			sum += wi;
			if (wi > best) {
				best = wi;
				dom = i;
			}
		}
		acc.set(0f);
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			float wi = w[i] / sum;
			if (wi < 1.0e-4f) {
				continue;
			}
			Anchor a = own[i];
			Vector3f l = loc[i];
			Vector3f h = a.h();
			// normalised box coordinates -> the blended frame. The map is affine per anchor, so a body at its own end of the morph comes out as
			// drawn (the fitted anchors hold every vertex well inside 3 half extents); the bound stops a far vertex that is lightly weighted to
			// another anchor from being flung out as that anchor shrinks
			tmp.set(Mth.clamp(l.x / h.x, -3f, 3f) * fh[i].x, Mth.clamp(l.y / h.y, -3f, 3f) * fh[i].y, Mth.clamp(l.z / h.z, -3f, 3f) * fh[i].z);
			fq[i].transform(tmp).add(fc[i]);
			// grow / shrink about the part's root
			tmp.sub(froot[i]).mul(k[i]).add(froot[i]);
			acc.fma(wi, tmp);
		}
		seam = 1f - best / sum;
		p.set(acc.x * sxz, acc.y * sy, acc.z * sxz);
	}
}
