package com.projecthero.mod.client.mutation.water;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Water Manipulation rework: the geometry every water body is built from -- wobbling spheres, tubes along a
 * curve (a whip, a jet, a geyser, a ring), and sheets -- skinned with the vanilla animated water texture (the block
 * atlas's {@code water_still} / {@code water_flow}, which are grey and tinted here) in a translucent entity render type,
 * so they read as real moving water. Positions are in the pose's space (usually blocks from the render origin).
 */
public final class WaterMesh {
	/** Deep blue body water, a lighter blue, white foam, and the green-cyan of Healing Water (ARGB). */
	public static final int BODY = 0xA84E8FEA;
	public static final int LIGHT = 0xB87FB8FF;
	public static final int FOAM = 0xD0E6F4FF;
	public static final int HEAL = 0xB05FE0D8;
	public static final int SHELL = 0x5A5E9EF0;

	private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
	private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

	private WaterMesh() {
	}

	public static VertexConsumer buffer(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
	}

	public static TextureAtlasSprite still() {
		return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STILL);
	}

	public static TextureAtlasSprite flow() {
		return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FLOW);
	}

	/** {@code argb} with its alpha scaled by {@code k} (0..1). */
	public static int fade(int argb, float k) {
		int a = (int) (((argb >>> 24) & 255) * Mth.clamp(k, 0f, 1f));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int argb, int light,
			float nx, float ny, float nz) {
		Matrix4f m = last.pose();
		vc.addVertex(m, x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose last, Vec3 p, float u, float v, int argb, int light, Vec3 n) {
		// tilted mostly upward: water is lit from the sky on every side, never shaded dark like a block face
		Vec3 lit = n.scale(0.35).add(0, 1, 0).normalize();
		vertex(vc, last, (float) p.x, (float) p.y, (float) p.z, u, v, argb, light, (float) lit.x, (float) lit.y, (float) lit.z);
	}

	/** One quad a-b-c-d with the sprite's sub-square [fu0..fu1] x [fv0..fv1] (fractions of the sprite). */
	public static void quad(VertexConsumer vc, PoseStack.Pose last, Vec3 a, Vec3 b, Vec3 c, Vec3 d, TextureAtlasSprite sp, float fu0,
			float fv0, float fu1, float fv1, int argb, int light, Vec3 n) {
		float u0 = sp.getU(fu0), u1 = sp.getU(fu1), v0 = sp.getV(fv0), v1 = sp.getV(fv1);
		vertex(vc, last, a, u0, v0, argb, light, n);
		vertex(vc, last, b, u0, v1, argb, light, n);
		vertex(vc, last, c, u1, v1, argb, light, n);
		vertex(vc, last, d, u1, v0, argb, light, n);
	}

	/** Which quarter-square of the sprite tile {@code i} uses (so a mesh repeats the texture four times across). */
	private static float q0(int i) {
		return Math.floorMod(i, 4) * 0.25f;
	}

	/**
	 * A sphere of radius {@code r} about the pose origin, its surface rippling by {@code wobble} (fraction of r) with time
	 * {@code t}. {@code yStretch} squashes / stretches it vertically.
	 */
	public static void sphere(VertexConsumer vc, PoseStack pose, float r, float yStretch, float wobble, float t, int argb, int light,
			TextureAtlasSprite sp) {
		PoseStack.Pose last = pose.last();
		int rings = 12;
		int segs = 18;
		Vec3[][] pts = new Vec3[rings + 1][segs + 1];
		Vec3[][] nrm = new Vec3[rings + 1][segs + 1];
		for (int i = 0; i <= rings; i++) {
			float lat = Mth.PI * i / rings - Mth.HALF_PI;
			for (int j = 0; j <= segs; j++) {
				float lon = Mth.TWO_PI * j / segs;
				float k = 1f + wobble * Mth.sin(3f * lon + t * 0.31f) * Mth.cos(2f * lat - t * 0.23f)
						+ wobble * 0.5f * Mth.sin(5f * lat + 2f * lon + t * 0.47f);
				float cl = Mth.cos(lat);
				Vec3 n = new Vec3(cl * Mth.cos(lon), Mth.sin(lat), cl * Mth.sin(lon));
				nrm[i][j] = n;
				pts[i][j] = new Vec3(n.x * r * k, n.y * r * k * yStretch, n.z * r * k);
			}
		}
		for (int i = 0; i < rings; i++) {
			for (int j = 0; j < segs; j++) {
				float fu = q0(j), fv = q0(i);
				Vec3 n = nrm[i][j].add(nrm[i + 1][j + 1]).normalize();
				quad(vc, last, pts[i][j], pts[i + 1][j], pts[i + 1][j + 1], pts[i][j + 1], sp, fu, fv, fu + 0.25f, fv + 0.25f, argb, light, n);
			}
		}
	}

	/**
	 * A tube through {@code pts} with radius {@code radii[i]} at each point, {@code sides} round. {@code ripple} makes the
	 * surface churn with time {@code t}. Open ended.
	 */
	public static void tube(VertexConsumer vc, PoseStack pose, Vec3[] pts, float[] radii, int sides, float ripple, float t, int argb,
			int light, TextureAtlasSprite sp) {
		int n = pts.length;
		if (n < 2) {
			return;
		}
		PoseStack.Pose last = pose.last();
		Vec3[][] ring = new Vec3[n][sides + 1];
		Vec3[][] nrm = new Vec3[n][sides + 1];
		Vec3 prevN = null;
		for (int i = 0; i < n; i++) {
			Vec3 tan = (i == 0 ? pts[1].subtract(pts[0]) : i == n - 1 ? pts[n - 1].subtract(pts[n - 2]) : pts[i + 1].subtract(pts[i - 1]));
			tan = tan.lengthSqr() < 1.0e-9 ? new Vec3(0, 1, 0) : tan.normalize();
			// a frame that turns smoothly with the curve (carried over from the last ring)
			Vec3 ref = prevN != null ? prevN : (Math.abs(tan.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
			Vec3 nn = ref.subtract(tan.scale(ref.dot(tan)));
			nn = nn.lengthSqr() < 1.0e-9 ? new Vec3(1, 0, 0) : nn.normalize();
			prevN = nn;
			Vec3 bb = tan.cross(nn);
			for (int j = 0; j <= sides; j++) {
				float a = Mth.TWO_PI * j / sides;
				float rr = radii[i] * (1f + ripple * Mth.sin(a * 3f + i * 0.9f - t * 0.9f));
				Vec3 dir = nn.scale(Mth.cos(a)).add(bb.scale(Mth.sin(a)));
				nrm[i][j] = dir;
				ring[i][j] = pts[i].add(dir.scale(rr));
			}
		}
		for (int i = 0; i < n - 1; i++) {
			for (int j = 0; j < sides; j++) {
				float fu = q0(j), fv = q0(i);
				quad(vc, last, ring[i][j], ring[i + 1][j], ring[i + 1][j + 1], ring[i][j + 1], sp, fu, fv, fu + 0.25f, fv + 0.25f, argb, light,
						nrm[i][j]);
			}
		}
	}

	/** A closed ring (torus) of tube radius {@code tr} and radius {@code r} in the pose's XZ plane. */
	public static void ring(VertexConsumer vc, PoseStack pose, float r, float tr, int segs, float ripple, float t, int argb, int light,
			TextureAtlasSprite sp) {
		Vec3[] pts = new Vec3[segs + 1];
		float[] radii = new float[segs + 1];
		for (int i = 0; i <= segs; i++) {
			float a = Mth.TWO_PI * i / segs;
			pts[i] = new Vec3(Mth.cos(a) * r, 0, Mth.sin(a) * r);
			radii[i] = tr;
		}
		tube(vc, pose, pts, radii, 8, ripple, t, argb, light, sp);
	}

	/** A flat disc of radius {@code r} in the pose's XZ plane at height {@code y}, its edge rippling. */
	public static void disc(VertexConsumer vc, PoseStack pose, float r, float y, float t, int argb, int light, TextureAtlasSprite sp) {
		PoseStack.Pose last = pose.last();
		int segs = 16;
		Vec3 c = new Vec3(0, y, 0);
		Vec3 up = new Vec3(0, 1, 0);
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs, a1 = Mth.TWO_PI * (j + 1) / segs;
			float r0 = r * (1f + 0.08f * Mth.sin(a0 * 4f + t * 0.6f)), r1 = r * (1f + 0.08f * Mth.sin(a1 * 4f + t * 0.6f));
			Vec3 p0 = new Vec3(Mth.cos(a0) * r0, y, Mth.sin(a0) * r0);
			Vec3 p1 = new Vec3(Mth.cos(a1) * r1, y, Mth.sin(a1) * r1);
			quad(vc, last, c, p0, p1, c, sp, 0.5f, 0.5f, 0.5f + 0.5f * Mth.cos(a0), 0.5f + 0.5f * Mth.sin(a0), argb, light, up);
		}
	}

	/**
	 * A sheet swept across the pose's X axis from {@code -half} to {@code half}: at each of {@code cols + 1} columns the
	 * cross-section is {@code profile.at(x, s)} for s in 0..1 ({@code rows} steps) -- the curling face of a wave.
	 */
	public static void sheet(VertexConsumer vc, PoseStack pose, float half, int cols, int rows, Profile profile, int argb, int light,
			TextureAtlasSprite sp) {
		PoseStack.Pose last = pose.last();
		Vec3[][] g = new Vec3[cols + 1][rows + 1];
		for (int i = 0; i <= cols; i++) {
			float x = -half + 2f * half * i / cols;
			for (int k = 0; k <= rows; k++) {
				g[i][k] = profile.at(x, k / (float) rows);
			}
		}
		for (int i = 0; i < cols; i++) {
			for (int k = 0; k < rows; k++) {
				Vec3 a = g[i][k], b = g[i][k + 1], c = g[i + 1][k + 1], d = g[i + 1][k];
				Vec3 n = b.subtract(a).cross(d.subtract(a));
				n = n.lengthSqr() < 1.0e-9 ? new Vec3(0, 1, 0) : n.normalize();
				if (n.y < 0) {
					n = n.scale(-1); // lit from above whichever way the face turns
				}
				float fu = q0(i), fv = q0(k);
				quad(vc, last, a, b, c, d, sp, fu, fv, fu + 0.25f, fv + 0.25f, argb, light, n);
			}
		}
	}

	@FunctionalInterface
	public interface Profile {
		Vec3 at(float x, float s);
	}
}
