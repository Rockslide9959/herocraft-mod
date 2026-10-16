package com.projecthero.mod.client.mutation.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.5 Cryokinesis: raw-quad ice geometry shared by the Cryokinesis renderers -- a cuboid whose ice texture tiles once
 * per block (so a big block of ice still looks like ice, not one stretched pixel), and a tapered four-sided crystal
 * (the shape of every spike, shard, pip and icicle).
 */
public final class IceDraw {
	public static final ResourceLocation ICE = ResourceLocation.withDefaultNamespace("textures/block/ice.png");
	public static final ResourceLocation PACKED = ResourceLocation.withDefaultNamespace("textures/block/packed_ice.png");
	public static final ResourceLocation BLUE = ResourceLocation.withDefaultNamespace("textures/block/blue_ice.png");
	public static final ResourceLocation WHITE = com.projecthero.mod.ProjectHeroMod.id("textures/entity/mutation/white.png");

	private IceDraw() {
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float w, int argb, int light,
			float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/** A cuboid from (x0,y0,z0) to (x1,y1,z1), the texture tiling every {@code tile} units. */
	public static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float tile,
			int argb, int light) {
		PoseStack.Pose p = ps.last();
		float sx = (x1 - x0) / tile;
		float sy = (y1 - y0) / tile;
		float sz = (z1 - z0) / tile;
		// -Z / +Z
		v(vc, p, x0, y0, z0, 0, sy, argb, light, 0, 0, -1);
		v(vc, p, x0, y1, z0, 0, 0, argb, light, 0, 0, -1);
		v(vc, p, x1, y1, z0, sx, 0, argb, light, 0, 0, -1);
		v(vc, p, x1, y0, z0, sx, sy, argb, light, 0, 0, -1);
		v(vc, p, x1, y0, z1, 0, sy, argb, light, 0, 0, 1);
		v(vc, p, x1, y1, z1, 0, 0, argb, light, 0, 0, 1);
		v(vc, p, x0, y1, z1, sx, 0, argb, light, 0, 0, 1);
		v(vc, p, x0, y0, z1, sx, sy, argb, light, 0, 0, 1);
		// -X / +X
		v(vc, p, x0, y0, z1, 0, sy, argb, light, -1, 0, 0);
		v(vc, p, x0, y1, z1, 0, 0, argb, light, -1, 0, 0);
		v(vc, p, x0, y1, z0, sz, 0, argb, light, -1, 0, 0);
		v(vc, p, x0, y0, z0, sz, sy, argb, light, -1, 0, 0);
		v(vc, p, x1, y0, z0, 0, sy, argb, light, 1, 0, 0);
		v(vc, p, x1, y1, z0, 0, 0, argb, light, 1, 0, 0);
		v(vc, p, x1, y1, z1, sz, 0, argb, light, 1, 0, 0);
		v(vc, p, x1, y0, z1, sz, sy, argb, light, 1, 0, 0);
		// -Y / +Y
		v(vc, p, x0, y0, z1, 0, sz, argb, light, 0, -1, 0);
		v(vc, p, x0, y0, z0, 0, 0, argb, light, 0, -1, 0);
		v(vc, p, x1, y0, z0, sx, 0, argb, light, 0, -1, 0);
		v(vc, p, x1, y0, z1, sx, sz, argb, light, 0, -1, 0);
		v(vc, p, x0, y1, z0, 0, sz, argb, light, 0, 1, 0);
		v(vc, p, x0, y1, z1, 0, 0, argb, light, 0, 1, 0);
		v(vc, p, x1, y1, z1, sx, 0, argb, light, 0, 1, 0);
		v(vc, p, x1, y1, z0, sx, sz, argb, light, 0, 1, 0);
	}

	/**
	 * A four-sided ice crystal standing on the origin along +Y: {@code w} wide at the base, tapering to a point at
	 * {@code h} (with a short straight collar of {@code collar} first). Drawn twice-sided via its own winding.
	 */
	public static void crystal(PoseStack ps, VertexConsumer vc, float w, float h, float collar, int argb, int light) {
		PoseStack.Pose p = ps.last();
		float r = w / 2;
		float[][] base = { { -r, -r }, { r, -r }, { r, r }, { -r, r } };
		for (int i = 0; i < 4; i++) {
			float[] a = base[i];
			float[] b = base[(i + 1) % 4];
			float nx = (a[0] + b[0]) / 2;
			float nz = (a[1] + b[1]) / 2;
			float len = (float) Math.sqrt(nx * nx + nz * nz);
			nx /= len;
			nz /= len;
			// the collar (a short prism)
			if (collar > 0) {
				v(vc, p, a[0], 0, a[1], 0, 1, argb, light, nx, 0, nz);
				v(vc, p, a[0], collar, a[1], 0, 0, argb, light, nx, 0, nz);
				v(vc, p, b[0], collar, b[1], 1, 0, argb, light, nx, 0, nz);
				v(vc, p, b[0], 0, b[1], 1, 1, argb, light, nx, 0, nz);
			}
			// the taper to the tip (a degenerate quad = a triangle)
			v(vc, p, a[0], collar, a[1], 0, 1, argb, light, nx, 0.4f, nz);
			v(vc, p, 0, h, 0, 0.5f, 0, argb, light, nx, 0.4f, nz);
			v(vc, p, 0, h, 0, 0.5f, 0, argb, light, nx, 0.4f, nz);
			v(vc, p, b[0], collar, b[1], 1, 1, argb, light, nx, 0.4f, nz);
		}
		// the base
		v(vc, p, -r, 0, r, 0, 1, argb, light, 0, -1, 0);
		v(vc, p, -r, 0, -r, 0, 0, argb, light, 0, -1, 0);
		v(vc, p, r, 0, -r, 1, 0, argb, light, 0, -1, 0);
		v(vc, p, r, 0, r, 1, 1, argb, light, 0, -1, 0);
	}

	/** A double-ended crystal (a bipyramid) centred on the origin along +Y, {@code len} long. */
	public static void shard(PoseStack ps, VertexConsumer vc, float w, float len, int argb, int light) {
		ps.pushPose();
		crystal(ps, vc, w, len / 2, 0, argb, light);
		ps.mulPose(Axis.XP.rotationDegrees(180));
		crystal(ps, vc, w, len * 0.3f, 0, argb, light);
		ps.popPose();
	}
}
