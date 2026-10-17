package com.projecthero.mod.client.miraculous.catnoir;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.16.6: Cat Noir's Baton -- a real 3D telescoping staff, built vertex by vertex (no item model), so its length can
 * be animated segment by segment.
 *
 * <p>The baton lies along its own +Y axis through the middle of the grip (the origin): a dark grip with the green paw
 * print, then on each side four nested silver tubes, each a little thinner than the one it slides out of, a steel
 * collar at every joint and a bright cap on each end. It <b>telescopes</b>: when a side reaches out to length
 * {@code len} (of at most {@code max}) its tubes come out one after the other -- the thickest first, then the next one
 * slides out of it -- exactly like a real telescoping baton; collapsed, every tube is hidden inside the grip. The tubes
 * are octagonal prisms. A rotted baton (after CATACLYSM) uses the black-and-purple texture.
 */
public final class CatNoirBaton {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/baton.png");
	public static final ResourceLocation ROTTED = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/baton_rotted.png");

	/** Half the grip's length and its radius (blocks). */
	public static final float GRIP_HALF = 0.16f;
	private static final float GRIP_R = 0.05f;
	/** The tubes' radii, thickest (first out) to thinnest. */
	private static final float[] TUBE_R = { 0.041f, 0.036f, 0.031f, 0.027f };
	private static final float COLLAR = 0.022f;
	private static final float CAP = 0.03f;
	/** A side's length when collapsed (just the grip half and the cap). */
	public static final float COLLAPSED = GRIP_HALF + CAP;
	/** Texture regions (u, in 1/48ths). */
	private static final float SHAFT_U0 = 0f, SHAFT_U1 = 8f, COLLAR_U0 = 8f, COLLAR_U1 = 12f, PAW_U0 = 12f, PAW_U1 = 28f, GRIP_U0 = 28f,
			GRIP_U1 = 36f, TIP_U0 = 36f, TIP_U1 = 44f;
	private static final int SIDES = 8;

	private CatNoirBaton() {
	}

	/**
	 * The baton along +Y from the grip's middle: {@code front} blocks out the +Y side (of at most {@code frontMax}) and
	 * {@code back} out the -Y side (of at most {@code backMax}). Lengths below {@link #COLLAPSED} draw it collapsed.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float front, float frontMax, float back, float backMax,
			boolean rotted) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(rotted ? ROTTED : TEXTURE));
		PoseStack.Pose last = pose.last();
		// the grip: paw prints on two opposite faces
		prism(vc, last, light, -GRIP_HALF, GRIP_HALF, GRIP_R, true);
		side(vc, last, light, front, frontMax, 1f);
		side(vc, last, light, back, backMax, -1f);
	}

	/** Collapsed (both ends just the caps). */
	public static void renderCollapsed(PoseStack pose, MultiBufferSource buffers, int light, boolean rotted) {
		render(pose, buffers, light, COLLAPSED, COLLAPSED, COLLAPSED, COLLAPSED, rotted);
	}

	private static void side(VertexConsumer vc, PoseStack.Pose last, int light, float len, float max, float sign) {
		float ext = Math.max(0f, len - COLLAPSED);
		float maxExt = Math.max(ext, max - COLLAPSED);
		float seg = maxExt / TUBE_R.length;
		float at = GRIP_HALF;
		float lastR = GRIP_R;
		for (int i = 0; i < TUBE_R.length && seg > 0f; i++) {
			float out = Mth.clamp(ext - i * seg, 0f, seg);
			if (out <= 0.0005f) {
				break;
			}
			// the collar at the mouth of the tube it slides out of
			ring(vc, last, light, at, sign, lastR + 0.006f);
			float a = at, b = at + out;
			prismSigned(vc, last, light, a, b, TUBE_R[i], sign, SHAFT_U0, SHAFT_U1);
			at = b;
			lastR = TUBE_R[i];
		}
		// the end cap
		float r = lastR + 0.004f;
		prismSigned(vc, last, light, at, at + CAP, r, sign, TIP_U0, TIP_U1);
		disc(vc, last, light, sign * (at + CAP), r, sign);
	}

	private static void ring(VertexConsumer vc, PoseStack.Pose last, int light, float at, float sign, float r) {
		prismSigned(vc, last, light, at - COLLAR * 0.5f, at + COLLAR * 0.5f, r, sign, COLLAR_U0, COLLAR_U1);
	}

	private static void prismSigned(VertexConsumer vc, PoseStack.Pose last, int light, float a, float b, float r, float sign, float u0, float u1) {
		float y0 = sign > 0 ? a : -b, y1 = sign > 0 ? b : -a;
		prism(vc, last, light, y0, y1, r, u0, u1, u0, u1);
	}

	private static void prism(VertexConsumer vc, PoseStack.Pose last, int light, float y0, float y1, float r, boolean paw) {
		prism(vc, last, light, y0, y1, r, PAW_U0, PAW_U1, GRIP_U0, GRIP_U1);
	}

	/** An octagonal tube from {@code y0} to {@code y1}: faces 0 and 4 sample ({@code u0}..{@code u1}), the rest ({@code o0}..{@code o1}). */
	private static void prism(VertexConsumer vc, PoseStack.Pose last, int light, float y0, float y1, float r, float u0, float u1, float o0, float o1) {
		Matrix4f m = last.pose();
		for (int k = 0; k < SIDES; k++) {
			double a0 = Math.PI * 2 * k / SIDES + Math.PI / SIDES, a1 = Math.PI * 2 * (k + 1) / SIDES + Math.PI / SIDES;
			float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
			float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
			float nx = (float) Math.cos((a0 + a1) * 0.5), nz = (float) Math.sin((a0 + a1) * 0.5);
			boolean main = k % 4 == 0;
			float fu0 = (main ? u0 : o0) / 48f, fu1 = (main ? u1 : o1) / 48f;
			vertex(vc, m, last, x0, y0, z0, fu0, 1f, nx, 0, nz, light);
			vertex(vc, m, last, x1, y0, z1, fu1, 1f, nx, 0, nz, light);
			vertex(vc, m, last, x1, y1, z1, fu1, 0f, nx, 0, nz, light);
			vertex(vc, m, last, x0, y1, z0, fu0, 0f, nx, 0, nz, light);
		}
	}

	/** The flat end of a cap (an octagon as a fan of quads). */
	private static void disc(VertexConsumer vc, PoseStack.Pose last, int light, float y, float r, float sign) {
		Matrix4f m = last.pose();
		float u = (TIP_U0 + TIP_U1) * 0.5f / 48f;
		for (int k = 0; k < SIDES; k += 2) {
			float[] xs = new float[3], zs = new float[3];
			for (int j = 0; j < 3; j++) {
				double a = Math.PI * 2 * (k + j) / SIDES + Math.PI / SIDES;
				xs[j] = (float) Math.cos(a) * r;
				zs[j] = (float) Math.sin(a) * r;
			}
			vertex(vc, m, last, 0f, y, 0f, u, 0.5f, 0, sign, 0, light);
			vertex(vc, m, last, xs[0], y, zs[0], u, 0.1f, 0, sign, 0, light);
			vertex(vc, m, last, xs[1], y, zs[1], u, 0.5f, 0, sign, 0, light);
			vertex(vc, m, last, xs[2], y, zs[2], u, 0.9f, 0, sign, 0, light);
		}
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float z, float u, float v, float nx, float ny,
			float nz, int light) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(last, nx, ny, nz);
	}
}
