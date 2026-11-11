package com.projecthero.mod.client.omnitrix.sequence;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType;
import com.projecthero.mod.client.omnitrix.OmnitrixTransformFx;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the holder's screen while a show sequence plays -- a plane just past the near clip covering the whole view, and the flat
 * cartoon primitives drawn on it (gradients, radial flashes, fills, streaks, ray bursts, dots). Screen coordinates: {@code x} and
 * {@code y} run -1..1 across the width and the height (y up). Sizes given "in screen heights" are made round by {@link #k()}.
 * <p>
 * A sequence's {@code drawStage} paints the background on it (the body is then drawn over it); {@code drawOverlay} paints the
 * flashes over everything.
 */
public final class Stage {
	public final MultiBufferSource.BufferSource bs;
	public final PoseStack pose;
	public final Camera cam;
	/** The playing sequence's seed (fixed for the whole sequence; mix a shot number in for per-shot layouts). */
	public final long seed;
	final Vector3f c, r, u;
	final float hw, hh;

	public Stage(MultiBufferSource.BufferSource bs, PoseStack pose, Camera cam, long seed, double fov) {
		this.bs = bs;
		this.pose = pose;
		this.cam = cam;
		this.seed = seed;
		Minecraft mc = Minecraft.getInstance();
		// v0.17.2: right past the near plane -- with Sodium / ImmediatelyFast the depth test can stay on, and ground closer than the stage
		// showed through it
		float d = 0.08f;
		Vector3f look = new Vector3f(cam.getLookVector());
		c = look.mul(d);
		r = new Vector3f(cam.getLeftVector()).negate();
		u = new Vector3f(cam.getUpVector());
		if (fov < 0) {
			fov = mc.options.fov().get();
		}
		hh = d * (float) Math.tan(Math.toRadians(fov) / 2.0) * 1.35f;
		float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
		hw = hh * aspect;
	}

	/** Height / width: multiply an x measured in screen heights by this to keep a shape round. */
	public float k() {
		return hh / hw;
	}

	/** Width / height (the x extent of the screen in screen heights). */
	public float aspect() {
		return hw / hh;
	}

	/** The radius (in screen-height units) that reaches the corners. */
	public float corner() {
		return (float) Math.sqrt(1 + (hw / hh) * (hw / hh)) * 1.05f;
	}

	/** A seed for one shot of the sequence: the same shot gets the same layout every frame, a new shot a new one. */
	public long shotSeed(int shot) {
		return seed * 1_000_003L + shot * 7_919L + 17L;
	}

	public PoseStack.Pose last() {
		return pose.last();
	}

	/** The flat, translucent, position-colour buffer (gradients, flashes, rays, streaks). */
	public VertexConsumer flat() {
		return bs.getBuffer(OmnitrixFxRenderType.STAGE);
	}

	/** The round-dot buffer ({@link #dot}). */
	public VertexConsumer dots() {
		return bs.getBuffer(RenderType.textSeeThrough(OmnitrixTransformFx.DOT));
	}

	/** A point on the plane: {@code x} / {@code y} in -1..1 of the screen (y up). */
	public void put(VertexConsumer vc, PoseStack.Pose p, float x, float y, int rgb, float a) {
		float px = c.x + r.x * x * hw + u.x * y * hh, py = c.y + r.y * x * hw + u.y * y * hh, pz = c.z + r.z * x * hw + u.z * y * hh;
		vc.addVertex(p, px, py, pz).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(Mth.clamp(a, 0f, 1f) * 255));
	}

	/** A radial gradient filling the screen: {@code in} at the centre through {@code mid} to {@code out}. Opaque. */
	public void gradient(int in, int mid, int out) {
		VertexConsumer vc = flat();
		PoseStack.Pose p = pose.last();
		float k = k();
		float[] radii = { 0f, 0.35f, 0.75f, 1.1f, corner() * 1.2f };
		int[] cols = { in, ShowMath.lerpRgb(0.5f, in, mid), mid, out, out };
		int segs = 40;
		for (int ring = 0; ring < radii.length - 1; ring++) {
			for (int i = 0; i < segs; i++) {
				float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
				float r0 = radii[ring], r1 = radii[ring + 1];
				put(vc, p, Mth.cos(a0) * r0 * k, Mth.sin(a0) * r0, cols[ring], 1f);
				put(vc, p, Mth.cos(a0) * r1 * k, Mth.sin(a0) * r1, cols[ring + 1], 1f);
				put(vc, p, Mth.cos(a1) * r1 * k, Mth.sin(a1) * r1, cols[ring + 1], 1f);
				put(vc, p, Mth.cos(a1) * r0 * k, Mth.sin(a1) * r0, cols[ring], 1f);
			}
		}
	}

	/** A full-screen radial wash: alpha {@code aIn} at the centre to {@code aOut} at the edges. */
	public void radial(int in, int mid, int out, float aIn, float aOut) {
		radial(flat(), pose.last(), in, mid, out, aIn, aOut);
	}

	public void radial(VertexConsumer vc, PoseStack.Pose p, int in, int mid, int out, float aIn, float aOut) {
		float k = k();
		float[] radii = { 0f, 0.4f, 0.9f, corner() * 1.2f };
		int[] cols = { in, mid, out, out };
		float[] as = { aIn, (aIn + aOut) * 0.5f, aOut, aOut };
		int segs = 36;
		for (int ring = 0; ring < radii.length - 1; ring++) {
			for (int i = 0; i < segs; i++) {
				float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
				float r0 = radii[ring], r1 = radii[ring + 1];
				put(vc, p, Mth.cos(a0) * r0 * k, Mth.sin(a0) * r0, cols[ring], as[ring]);
				put(vc, p, Mth.cos(a0) * r1 * k, Mth.sin(a0) * r1, cols[ring + 1], as[ring + 1]);
				put(vc, p, Mth.cos(a1) * r1 * k, Mth.sin(a1) * r1, cols[ring + 1], as[ring + 1]);
				put(vc, p, Mth.cos(a1) * r0 * k, Mth.sin(a1) * r0, cols[ring], as[ring]);
			}
		}
	}

	/** The whole screen in one colour. */
	public void fill(int rgb, float a) {
		fill(flat(), pose.last(), rgb, a);
	}

	public void fill(VertexConsumer vc, PoseStack.Pose p, int rgb, float a) {
		rect(vc, p, -1.5f, -1.5f, 1.5f, 1.5f, rgb, a, a);
	}

	/** A rectangle in screen units; {@code aRight} is the alpha at its right end (streaks fade along their length). */
	public void rect(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, int rgb, float aLeft, float aRight) {
		put(vc, p, x0, y0, rgb, aLeft);
		put(vc, p, x1, y0, rgb, aRight);
		put(vc, p, x1, y1, rgb, aRight);
		put(vc, p, x0, y1, rgb, aLeft);
	}

	/** Dark rays wheeling out of the centre (the green burst behind the final stance); {@code t} ticks since they began. */
	public void rays(long seed, float t, int rgb, int count) {
		VertexConsumer vc = flat();
		PoseStack.Pose p = pose.last();
		float k = k();
		float spin = t * 0.006f + ShowMath.hash(seed, 3) * Mth.TWO_PI;
		float grow = ShowMath.smooth(t / 6f);
		float far = corner() * 1.25f;
		for (int i = 0; i < count; i++) {
			float mid = (i + 0.25f * ShowMath.hash(seed, i + 40)) / count * Mth.TWO_PI + spin;
			float half = Mth.PI / count * (0.32f + 0.3f * ShowMath.hash(seed, i + 60));
			float r0 = 0.08f;
			float r1 = Mth.lerp(grow, 0.2f, far);
			put(vc, p, Mth.cos(mid) * r0 * k, Mth.sin(mid) * r0, rgb, 0f);
			put(vc, p, Mth.cos(mid - half) * r1 * k, Mth.sin(mid - half) * r1, rgb, 0.85f);
			put(vc, p, Mth.cos(mid + half) * r1 * k, Mth.sin(mid + half) * r1, rgb, 0.85f);
			put(vc, p, Mth.cos(mid) * r0 * k, Mth.sin(mid) * r0, rgb, 0f);
		}
	}

	/** A solid round dot of radius {@code r} (screen heights) at {@code x y} (x already in width units). */
	public void dot(VertexConsumer vc, PoseStack.Pose p, float x, float y, float r, int rgb) {
		dot(vc, p, x, y, r, rgb, 245);
	}

	public void dot(VertexConsumer vc, PoseStack.Pose p, float x, float y, float r, int rgb, int alpha) {
		float rx = r * hh / hw;
		int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
		float x0 = x - rx, x1 = x + rx, y0 = y - r, y1 = y + r;
		vtx(vc, p, x0, y1, 0, 0, cr, cg, cb, alpha);
		vtx(vc, p, x0, y0, 0, 1, cr, cg, cb, alpha);
		vtx(vc, p, x1, y0, 1, 1, cr, cg, cb, alpha);
		vtx(vc, p, x1, y1, 1, 0, cr, cg, cb, alpha);
	}

	private void vtx(VertexConsumer vc, PoseStack.Pose p, float x, float y, float uu, float vv, int cr, int cg, int cb, int alpha) {
		float px = c.x + r.x * x * hw + u.x * y * hh, py = c.y + r.y * x * hw + u.y * y * hh, pz = c.z + r.z * x * hw + u.z * y * hh;
		vc.addVertex(p, px, py, pz).setColor(cr, cg, cb, alpha).setUv(uu, vv).setLight(LightTexture.FULL_BRIGHT);
	}
}
