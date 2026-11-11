package com.projecthero.mod.client.gyro.model;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Gyro Zeppeli: draws one Zeppeli Steel Ball. CONTRACT shared by the two build agents -- the POWER agent calls it from every
 * place a ball is drawn: the thrown-ball entity renderer, the hand, the belt, Scan, Spin Shield orbit, the Golden Spin shot. Keep the
 * signature.
 *
 * <p>The model (references: {@code docs/reference/gyro/steel_ball_ref_1..5.png}):
 * <ul>
 * <li>a UV sphere (32x20 segments at hand size, 56x32 mid, 96x56 for big effect balls) with the equirectangular albedo
 * {@code textures/entity/gyro/steel_ball(_small).png} (made by {@code scratchpad/gyro/build_steel_ball_textures.js}): green metal,
 * the inked grooves with a lit and a shadowed lip, the crease round each plate;</li>
 * <li>a raised, domed <b>hexagon plate</b> on the +Z face (circumradius 34 deg, 5.5 % proud, 14 % chamfer) and a smaller raised
 * <b>round plate</b> on the back (20 deg, 3.5 % proud), both real geometry with bevelled sides;</li>
 * <li>a view-dependent shade baked into the vertex colours (dark rim, lit upper left) on top of the usual entity lighting, and an
 * additive <b>environment pass</b>: {@code steel_ball_env.png} looked up by the view-space normal (a matcap), so the glossy
 * highlights and window reflection stay put while the ball spins under them;</li>
 * <li>{@code golden}: a gold-tinted base and highlights, a full-bright gold fresnel rim and a soft additive halo billboard.</li>
 * </ul>
 * The ball spins about its local Y axis, so the hexagon rolls round the equator. Works in the world, in the hand (first and third
 * person) and in orthographic GUI renders.
 */
public final class SteelBallRender {
	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/gyro/steel_ball.png");
	private static final ResourceLocation TEX_SMALL = ProjectHeroMod.id("textures/entity/gyro/steel_ball_small.png");
	private static final ResourceLocation ENV = ProjectHeroMod.id("textures/entity/gyro/steel_ball_env.png");
	private static final ResourceLocation HALO = ProjectHeroMod.id("textures/entity/gyro/steel_ball_halo.png");
	private static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/gyro/steel_ball_glow.png");

	// shared with scratchpad/gyro/build_steel_ball_textures.js
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final float HEX_R = 34f * DEG;
	private static final float HEX_CHAMFER = 0.86f;
	private static final float HEX_TOP = 1.055f;
	private static final float BACK_R = 20f * DEG;
	private static final float BACK_CHAMFER = 0.86f;
	private static final float BACK_TOP = 1.035f;
	private static final float PLATE_BASE = 0.995f;

	/** One quad = 4 vertices x (x, y, z, nx, ny, nz, u, v) on the unit ball. */
	private static final int STRIDE = 8;
	private static float[] meshSmall, meshMid, meshBig;

	private SteelBallRender() {
	}

	/**
	 * Draws a ball centred on the pose's origin.
	 *
	 * @param pose     positioned at the ball's centre
	 * @param buffers  the render buffers
	 * @param light    packed light
	 * @param radius   ball radius in blocks (about 0.11 in the hand / on the belt, larger for effects)
	 * @param spinDeg  current spin angle in degrees (the caller advances it; the ball spins about its local Y axis)
	 * @param golden   true while the ball carries a Golden Spin (gold glow / sheen)
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float radius, float spinDeg, boolean golden) {
		if (radius <= 0f) {
			return;
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
		PoseStack.Pose last = pose.last();
		float[] mesh = mesh(radius);

		// ---- the view frame: f = towards the camera from the ball centre, right/up across it (all in view space)
		Matrix4f mv = RenderSystem.getModelViewMatrix();
		Matrix3f mv3 = mv.get3x3(new Matrix3f());
		Matrix3f toView = new Matrix3f(mv3).mul(last.normal());
		Vector4f c4 = new Vector4f(0f, 0f, 0f, 1f).mul(last.pose()).mul(mv);
		boolean ortho = RenderSystem.getProjectionMatrix().m33() > 0.5f;
		Vector3f f = ortho || c4.lengthSquared() < 1.0e-8f ? new Vector3f(0f, 0f, 1f) : new Vector3f(-c4.x, -c4.y, -c4.z).normalize();
		Vector3f right = new Vector3f(0f, 1f, 0f).cross(f);
		if (right.lengthSquared() < 1.0e-6f) {
			right.set(1f, 0f, 0f);
		}
		right.normalize();
		Vector3f up = new Vector3f(f).cross(right).normalize();
		Vector3f key = new Vector3f(right).mul(-0.5f).add(new Vector3f(up).mul(0.62f)).add(new Vector3f(f).mul(0.6f)).normalize();

		// ---- how bright the additive passes may be here (the env pass ignores the lightmap)
		float envScale = envScale(light);
		float gr = 1f, gg = 1f, gb = 1f; // env tint
		if (golden) {
			gr = 1.25f;
			gg = 1.0f;
			gb = 0.42f;
		}

		VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(radius < 0.3f ? TEX_SMALL : TEX));
		int n = mesh.length / STRIDE;
		float[] shade = new float[n];
		float[] eu = new float[n];
		float[] ev = new float[n];
		Vector3f nv = new Vector3f();
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			nv.set(mesh[o + 3], mesh[o + 4], mesh[o + 5]);
			toView.transform(nv).normalize();
			float ndv = Math.max(0f, nv.dot(f));
			float lit = Math.max(0f, nv.dot(key));
			shade[i] = (0.17f + 0.83f * (float) Math.pow(ndv, 0.7)) * (0.58f + 0.52f * lit);
			eu[i] = 0.5f + 0.49f * nv.dot(right);
			ev[i] = 0.5f - 0.49f * nv.dot(up);
			float s = shade[i];
			float r = Math.min(1f, s * (golden ? 1.18f : 1f));
			float g = Math.min(1f, s * (golden ? 1.02f : 1f));
			float b = Math.min(1f, s * (golden ? 0.50f : 1f));
			solid.addVertex(last, mesh[o] * radius, mesh[o + 1] * radius, mesh[o + 2] * radius)
					.setColor(r, g, b, 1f)
					.setUv(mesh[o + 6], mesh[o + 7])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(last, mesh[o + 3], mesh[o + 4], mesh[o + 5]);
		}

		// ---- additive environment highlights
		VertexConsumer env = buffers.getBuffer(RenderType.eyes(ENV));
		float er = Math.min(1f, envScale * gr), eg = Math.min(1f, envScale * gg), eb = Math.min(1f, envScale * gb);
		float rs = radius * 1.002f;
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			env.addVertex(last, mesh[o] * rs, mesh[o + 1] * rs, mesh[o + 2] * rs)
					.setColor(er, eg, eb, 1f)
					.setUv(eu[i], ev[i])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT)
					.setNormal(last, mesh[o + 3], mesh[o + 4], mesh[o + 5]);
		}

		if (golden) {
			// ---- full-bright gold fresnel rim
			VertexConsumer rim = buffers.getBuffer(RenderType.eyes(GLOW));
			float rr = radius * 1.004f;
			for (int i = 0; i < n; i++) {
				int o = i * STRIDE;
				nv.set(mesh[o + 3], mesh[o + 4], mesh[o + 5]);
				toView.transform(nv).normalize();
				float fr = (float) Math.pow(1f - Math.max(0f, nv.dot(f)), 2.2) * 0.95f;
				rim.addVertex(last, mesh[o] * rr, mesh[o + 1] * rr, mesh[o + 2] * rr)
						.setColor(fr, fr * 0.76f, fr * 0.26f, 1f)
						.setUv(0.5f, 0.5f)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightTexture.FULL_BRIGHT)
						.setNormal(last, mesh[o + 3], mesh[o + 4], mesh[o + 5]);
			}
			// ---- soft halo billboard (faces the camera, slowly turns with the spin)
			Matrix3f toViewPos = new Matrix3f(mv3).mul(last.pose().get3x3(new Matrix3f()));
			Matrix3f inv = toViewPos.invert(new Matrix3f());
			float a = spinDeg * 0.35f * DEG;
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
			Vector3f hr = inv.transform(new Vector3f(right).mul(ca).add(new Vector3f(up).mul(sa))).normalize().mul(radius / 0.45f);
			Vector3f hu = inv.transform(new Vector3f(up).mul(ca).sub(new Vector3f(right).mul(sa))).normalize().mul(radius / 0.45f);
			VertexConsumer halo = buffers.getBuffer(RenderType.eyes(HALO));
			float k = 0.85f;
			haloVertex(halo, last, -hr.x - hu.x, -hr.y - hu.y, -hr.z - hu.z, 0f, 1f, k);
			haloVertex(halo, last, hr.x - hu.x, hr.y - hu.y, hr.z - hu.z, 1f, 1f, k);
			haloVertex(halo, last, hr.x + hu.x, hr.y + hu.y, hr.z + hu.z, 1f, 0f, k);
			haloVertex(halo, last, -hr.x + hu.x, -hr.y + hu.y, -hr.z + hu.z, 0f, 0f, k);
		}
		pose.popPose();
	}

	private static void haloVertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, float k) {
		vc.addVertex(last, x, y, z)
				.setColor(k, k, k, 1f)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(last, 0f, 1f, 0f);
	}

	/** 0.12 (pitch dark) .. 1 (daylight or full block light): the additive highlights would otherwise glow at night. */
	private static float envScale(int light) {
		float block = LightTexture.block(light) / 15f;
		float sky = LightTexture.sky(light) / 15f;
		Minecraft mc = Minecraft.getInstance();
		float day = mc.level != null ? mc.level.getSkyDarken(1f) : 1f;
		float b = Math.max(block, sky * day);
		return 0.12f + 0.88f * b * b;
	}

	// ------------------------------------------------------------------ mesh

	private static float[] mesh(float radius) {
		if (radius <= 0.2f) {
			if (meshSmall == null) {
				meshSmall = build(32, 20, 3, 4);
			}
			return meshSmall;
		}
		if (radius <= 1.0f) {
			if (meshMid == null) {
				meshMid = build(56, 32, 5, 6);
			}
			return meshMid;
		}
		if (meshBig == null) {
			meshBig = build(96, 56, 8, 10);
		}
		return meshBig;
	}

	private static float[] build(int lon, int lat, int plateSub, int bevelSub) {
		List<float[]> quads = new ArrayList<>();
		// sphere: lon from -90 deg (the seam at -X) round to 270 deg; v = 0 at the top
		for (int j = 0; j < lat; j++) {
			for (int i = 0; i < lon; i++) {
				float[] q = new float[4 * STRIDE];
				quads.add(q);
				sphereVertex(q, 0, i / (float) lon, j / (float) lat);
				sphereVertex(q, 1, i / (float) lon, (j + 1) / (float) lat);
				sphereVertex(q, 2, (i + 1) / (float) lon, (j + 1) / (float) lat);
				sphereVertex(q, 3, (i + 1) / (float) lon, j / (float) lat);
			}
		}
		// front hexagon plate (vertices at 0, 60, .. 300 deg round +Z)
		float hexRv = (float) Math.tan(HEX_R);
		float[][] hex = new float[6][];
		for (int k = 0; k < 6; k++) {
			hex[k] = new float[] { hexRv * (float) Math.cos(k * 60 * DEG), hexRv * (float) Math.sin(k * 60 * DEG) };
		}
		plate(quads, hex, HEX_CHAMFER, HEX_TOP, plateSub, bevelSub, false);
		// back round plate (built at +Z, turned 180 deg about X)
		float backRv = (float) Math.tan(BACK_R);
		float[][] circle = new float[24][];
		for (int k = 0; k < 24; k++) {
			circle[k] = new float[] { backRv * (float) Math.cos(k * 15 * DEG), backRv * (float) Math.sin(k * 15 * DEG) };
		}
		plate(quads, circle, BACK_CHAMFER, BACK_TOP, Math.max(1, plateSub - 2), Math.max(1, bevelSub / 3), true);

		float[] out = new float[quads.size() * 4 * STRIDE];
		int p = 0;
		for (float[] q : quads) {
			System.arraycopy(q, 0, out, p, 4 * STRIDE);
			p += 4 * STRIDE;
		}
		return out;
	}

	private static void sphereVertex(float[] q, int slot, float u, float v) {
		double lon = u * 2 * Math.PI - Math.PI / 2, la = (0.5 - v) * Math.PI;
		float x = (float) (Math.cos(la) * Math.sin(lon)), y = (float) Math.sin(la), z = (float) (Math.cos(la) * Math.cos(lon));
		int o = slot * STRIDE;
		q[o] = x;
		q[o + 1] = y;
		q[o + 2] = z;
		q[o + 3] = x;
		q[o + 4] = y;
		q[o + 5] = z;
		q[o + 6] = u;
		q[o + 7] = v;
	}

	/**
	 * A raised plate: outline = convex CCW polygon in the gnomonic plane z = 1 (around +Z). The top face is the outline scaled by
	 * {@code chamfer}, domed at radius {@code top}; bevel quads run from it down to the full outline at {@link #PLATE_BASE}.
	 */
	private static void plate(List<float[]> quads, float[][] outline, float chamfer, float top, int sub, int bevelSub, boolean back) {
		int k = outline.length;
		for (int e = 0; e < k; e++) {
			float[] a = outline[e], b = outline[(e + 1) % k];
			// top: fan sector (centre, a, b) as a triangle grid
			for (int i = 0; i < sub; i++) {
				for (int j = 0; j <= i; j++) {
					float[] p00 = sector(a, b, chamfer, i, j, sub), p10 = sector(a, b, chamfer, i + 1, j, sub);
					float[] p11 = sector(a, b, chamfer, i + 1, j + 1, sub);
					quads.add(quad(back, top, top, top, top, p00, p10, p11, p11));
					if (j < i) {
						float[] p01 = sector(a, b, chamfer, i, j + 1, sub);
						quads.add(quad(back, top, top, top, top, p00, p11, p01, p01));
					}
				}
			}
			// bevel strip along this edge
			for (int s = 0; s < bevelSub; s++) {
				float t0 = s / (float) bevelSub, t1 = (s + 1) / (float) bevelSub;
				float[] in0 = lerp2(a, b, t0, chamfer), in1 = lerp2(a, b, t1, chamfer);
				float[] out0 = lerp2(a, b, t0, 1f), out1 = lerp2(a, b, t1, 1f);
				float[] q = quad(back, top, PLATE_BASE, PLATE_BASE, top, in0, out0, out1, in1);
				flatNormal(q);
				quads.add(q);
			}
		}
	}

	private static float[] sector(float[] a, float[] b, float scale, int i, int j, int sub) {
		// point = (a * (i - j) + b * j) / sub, scaled
		float x = (a[0] * (i - j) + b[0] * j) / sub * scale;
		float y = (a[1] * (i - j) + b[1] * j) / sub * scale;
		return new float[] { x, y };
	}

	private static float[] lerp2(float[] a, float[] b, float t, float scale) {
		return new float[] { (a[0] + (b[0] - a[0]) * t) * scale, (a[1] + (b[1] - a[1]) * t) * scale };
	}

	/** Four plane points at the given radii -> a quad on the ball (normal = radial; uv from the direction). */
	private static float[] quad(boolean back, float r0, float r1, float r2, float r3, float[] p0, float[] p1, float[] p2, float[] p3) {
		float[] q = new float[4 * STRIDE];
		float[][] ps = { p0, p1, p2, p3 };
		float[] rs = { r0, r1, r2, r3 };
		for (int s = 0; s < 4; s++) {
			float x = ps[s][0], y = ps[s][1], z = 1f;
			float l = (float) Math.sqrt(x * x + y * y + 1f);
			x /= l;
			y /= l;
			z /= l;
			if (back) { // 180 deg about X: (x, y, z) -> (x, -y, -z), keeps the winding
				y = -y;
				z = -z;
			}
			int o = s * STRIDE;
			q[o] = x * rs[s];
			q[o + 1] = y * rs[s];
			q[o + 2] = z * rs[s];
			q[o + 3] = x;
			q[o + 4] = y;
			q[o + 5] = z;
			double lon = Math.atan2(x, z);
			float u = (float) ((lon + Math.PI / 2) / (2 * Math.PI));
			if (u < 0f) {
				u += 1f;
			}
			q[o + 6] = u;
			q[o + 7] = (float) (0.5 - Math.asin(Math.max(-1f, Math.min(1f, y))) / Math.PI);
		}
		return q;
	}

	/** Bevel quads get the face normal (from the actual positions), so the chamfer catches the light. */
	private static void flatNormal(float[] q) {
		Vector3f p0 = new Vector3f(q[0], q[1], q[2]);
		Vector3f e1 = new Vector3f(q[STRIDE], q[STRIDE + 1], q[STRIDE + 2]).sub(p0);
		Vector3f e2 = new Vector3f(q[3 * STRIDE], q[3 * STRIDE + 1], q[3 * STRIDE + 2]).sub(p0);
		Vector3f nrm = e1.cross(e2);
		if (nrm.lengthSquared() < 1.0e-12f) {
			return;
		}
		nrm.normalize();
		// lean the bevel normal a little towards the radial one so it doesn't facet
		for (int s = 0; s < 4; s++) {
			int o = s * STRIDE;
			Vector3f rad = new Vector3f(q[o + 3], q[o + 4], q[o + 5]);
			Vector3f m = new Vector3f(nrm).mul(0.8f).add(rad.mul(0.2f)).normalize();
			q[o + 3] = m.x;
			q[o + 4] = m.y;
			q[o + 5] = m.z;
		}
	}
}
