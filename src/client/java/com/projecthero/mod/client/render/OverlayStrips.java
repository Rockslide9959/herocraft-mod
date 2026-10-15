package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

import org.joml.Matrix4f;

/**
 * v0.16.5: the wearer's own second skin layer (hat, jacket, sleeves, trousers), drawn one texel at a time.
 *
 * <p>The suit-up / transformation reveals (Nova, Ghost Rider, Cyborg, Blue Beetle) work texel by texel, but the vanilla
 * overlay parts can only be shown or hidden whole -- so the player's second layer used to vanish the instant a change
 * began (user: "only make the part of the second layer disappear when it's being transformed"). The player's skin can't
 * be read on the client, so its overlay cubes are re-drawn here as one quad per texel, and only the texels the reveal
 * hasn't reached are emitted. The vanilla overlay parts stay hidden while this runs.
 *
 * <p>The reveal decides per texel through {@link Shows}, in BASE-layer skin coordinates (64x64): an overlay texel asks
 * about the base texel under it (hat -> head, jacket -> body, sleeves -> arms, trousers -> legs), so every reveal can use
 * the same map it uses for its own suit.
 */
public final class OverlayStrips {
	private OverlayStrips() {
	}

	/** Whether the wearer's overlay texel over base texel ({@code u}, {@code v}) (64x64 skin coordinates) still shows. */
	@FunctionalInterface
	public interface Shows {
		boolean test(int u, int v);
	}

	/**
	 * Per texel colour for {@link #renderSkin}: ARGB multiplied into the skin texel, or 0 to leave the texel out. Asked in
	 * BASE-layer coordinates like {@link Shows}.
	 */
	@FunctionalInterface
	public interface Tint {
		int argb(int u, int v);
	}

	/** One overlay cube: its part, overlay texture offset, the matching BASE offset, size and inflation. */
	private record Cube(ModelPart part, int u, int v, int bu, int bv, float x0, float y0, float z0, int w, int h, int d, float grow) {
	}

	/** Model pixels from the top of the head (0) to the soles (32) of every base-layer texel (-1 = unused). */
	private static float[] heights;

	public static float[] heights() {
		if (heights == null) {
			float[] h = new float[64 * 64];
			java.util.Arrays.fill(h, -1f);
			int[][] cubes = {
					{ 0, 0, 8, 8, 8, 0 }, { 32, 0, 8, 8, 8, 0 },
					{ 16, 16, 8, 12, 4, 8 }, { 16, 32, 8, 12, 4, 8 },
					{ 40, 16, 4, 12, 4, 8 }, { 40, 32, 4, 12, 4, 8 }, { 32, 48, 4, 12, 4, 8 }, { 48, 48, 4, 12, 4, 8 },
					{ 0, 16, 4, 12, 4, 20 }, { 0, 32, 4, 12, 4, 20 }, { 16, 48, 4, 12, 4, 20 }, { 0, 48, 4, 12, 4, 20 } };
			for (int[] c : cubes) {
				int u = c[0], v = c[1], w = c[2], hh = c[3], d = c[4], top = c[5];
				float bottom = top == 0 ? hh - 0.1f : top + hh;
				for (int y = v; y < v + d; y++) {
					for (int x = u + d; x < u + d + w; x++) {
						h[y * 64 + x] = top;
					}
					for (int x = u + d + w; x < u + d + 2 * w; x++) {
						h[y * 64 + x] = bottom;
					}
				}
				for (int y = v + d; y < v + d + hh; y++) {
					for (int x = u; x < u + 2 * d + 2 * w; x++) {
						h[y * 64 + x] = top + (y - v - d) + 0.5f;
					}
				}
			}
			heights = h;
		}
		return heights;
	}

	/** The height of base texel ({@code u}, {@code v}); -1 for unused texels. */
	public static float height(int u, int v) {
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? -1f : heights()[v * 64 + u];
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<?> model,
			Shows shows) {
		render(pose, buffers, light, player, model, shows, true);
	}

	/** As {@link #render(PoseStack, MultiBufferSource, int, AbstractClientPlayer, PlayerModel, Shows)}; {@code withHat} false leaves the hat to vanilla. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<?> model,
			Shows shows, boolean withHat) {
		renderSkin(pose, buffers, light, player, model, (u, v) -> shows.test(u, v) ? 0xFFFFFFFF : 0, false, withHat, 0f);
	}

	/**
	 * The wearer's skin texel by texel: the overlay layer, and with {@code base} the base layer too, every cube {@code extra}
	 * model pixels larger than vanilla's (Cyborg's hologram is drawn over his plates this way).
	 */
	public static void renderSkin(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<?> model,
			Tint tint, boolean base, boolean withHat, float extra) {
		PlayerSkin skin = player.getSkin();
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(skin.texture()));
		int aw = slim ? 3 : 4;
		Cube[] cubes = {
				new Cube(model.head, 32, 0, 0, 0, -4f, -8f, -4f, 8, 8, 8, 0.5f),
				new Cube(model.body, 16, 32, 16, 16, -4f, 0f, -2f, 8, 12, 4, 0.25f),
				new Cube(model.rightArm, 40, 32, 40, 16, slim ? -2f : -3f, -2f, -2f, aw, 12, 4, 0.25f),
				new Cube(model.leftArm, 48, 48, 32, 48, -1f, -2f, -2f, aw, 12, 4, 0.25f),
				new Cube(model.rightLeg, 0, 32, 0, 16, -2f, 0f, -2f, 4, 12, 4, 0.25f),
				new Cube(model.leftLeg, 0, 48, 16, 48, -2f, 0f, -2f, 4, 12, 4, 0.25f) };
		if (base) {
			Cube[] under = {
					new Cube(model.head, 0, 0, 0, 0, -4f, -8f, -4f, 8, 8, 8, 0f),
					new Cube(model.body, 16, 16, 16, 16, -4f, 0f, -2f, 8, 12, 4, 0f),
					new Cube(model.rightArm, 40, 16, 40, 16, slim ? -2f : -3f, -2f, -2f, aw, 12, 4, 0f),
					new Cube(model.leftArm, 32, 48, 32, 48, -1f, -2f, -2f, aw, 12, 4, 0f),
					new Cube(model.rightLeg, 0, 16, 0, 16, -2f, 0f, -2f, 4, 12, 4, 0f),
					new Cube(model.leftLeg, 16, 48, 16, 48, -2f, 0f, -2f, 4, 12, 4, 0f) };
			for (Cube c : under) {
				draw(vc, pose, light, c, tint, extra);
			}
		}
		for (int i = withHat ? 0 : 1; i < cubes.length; i++) {
			draw(vc, pose, light, cubes[i], tint, extra);
		}
	}

	private static void draw(VertexConsumer vc, PoseStack pose, int light, Cube c, Tint tint, float extra) {
		pose.pushPose();
		c.part().translateAndRotate(pose);
		cube(vc, pose.last(), light, new Cube(c.part(), c.u(), c.v(), c.bu(), c.bv(), c.x0(), c.y0(), c.z0(), c.w(), c.h(), c.d(), c.grow() + extra),
				tint);
		pose.popPose();
	}

	private static void cube(VertexConsumer vc, PoseStack.Pose pose, int light, Cube c, Tint tint) {
		float minX = (c.x0() - c.grow()) / 16f, maxX = (c.x0() + c.w() + c.grow()) / 16f;
		float minY = (c.y0() - c.grow()) / 16f, maxY = (c.y0() + c.h() + c.grow()) / 16f;
		float minZ = (c.z0() - c.grow()) / 16f, maxZ = (c.z0() + c.d() + c.grow()) / 16f;
		int w = c.w(), h = c.h(), d = c.d();
		float sx = (maxX - minX) / w, sy = (maxY - minY) / h, sz = (maxZ - minZ) / d;
		// top (y = minY): texture (u+d .. u+d+w, v .. v+d); vanilla puts maxX at the low-u... see the face table below
		for (int ty = 0; ty < d; ty++) {
			for (int tx = 0; tx < w; tx++) {
				int col = tint.argb(c.bu() + d + tx, c.bv() + ty);
				if (col == 0) {
					continue;
				}
				// vanilla top face: minX at u = u+d, maxX at u = u+d+w
				float x0 = minX + tx * sx, x1 = x0 + sx;
				// v grows from maxZ (v = v) to minZ (v = v+d)
				float z1 = maxZ - ty * sz, z0 = z1 - sz;
				quad(vc, pose, light, c.u() + d + tx, c.v() + ty, x0, x1, minY, minY, z0, z1, 0f, -1f, 0f, Plane.Y, col);
			}
		}
		// bottom (y = maxY): texture (u+d+w .. u+d+2w, v .. v+d)
		for (int ty = 0; ty < d; ty++) {
			for (int tx = 0; tx < w; tx++) {
				int col = tint.argb(c.bu() + d + w + tx, c.bv() + ty);
				if (col == 0) {
					continue;
				}
				float x0 = minX + tx * sx, x1 = x0 + sx; // minX at u = u+d+w
				// v grows from minZ (v = v+d is at maxZ? vanilla UP: v1 = s at minZ, v2 = r at maxZ) -> v = v at maxZ
				float z1 = maxZ - ty * sz, z0 = z1 - sz;
				quad(vc, pose, light, c.u() + d + w + tx, c.v() + ty, x0, x1, maxY, maxY, z0, z1, 0f, 1f, 0f, Plane.Y, col);
			}
		}
		// the four sides, row by row, texel by texel
		for (int row = 0; row < h; row++) {
			float y0 = minY + row * sy, y1 = y0 + sy;
			int tv = c.v() + d + row;
			int bv = c.bv() + d + row;
			// west (-x): u .. u+d, z from minZ (u+d side) to maxZ (u side)
			for (int k = 0; k < d; k++) {
				int col = tint.argb(c.bu() + k, bv);
				if (col != 0) {
					float z1 = maxZ - k * sz, z0 = z1 - sz;
					quad(vc, pose, light, c.u() + k, tv, minX, minX, y0, y1, z0, z1, -1f, 0f, 0f, Plane.X, col);
				}
			}
			// north (front, -z): u+d .. u+d+w, x from maxX (u+d+w side) to minX (u+d side)
			for (int k = 0; k < w; k++) {
				int col = tint.argb(c.bu() + d + k, bv);
				if (col != 0) {
					float x0 = minX + k * sx, x1 = x0 + sx;
					quad(vc, pose, light, c.u() + d + k, tv, x0, x1, y0, y1, minZ, minZ, 0f, 0f, -1f, Plane.Z, col);
				}
			}
			// east (+x): u+d+w .. u+2d+w, z from minZ (low u) to maxZ
			for (int k = 0; k < d; k++) {
				int col = tint.argb(c.bu() + d + w + k, bv);
				if (col != 0) {
					float z0 = minZ + k * sz, z1 = z0 + sz;
					quad(vc, pose, light, c.u() + d + w + k, tv, maxX, maxX, y0, y1, z0, z1, 1f, 0f, 0f, Plane.X, col);
				}
			}
			// south (back, +z): u+2d+w .. u+2d+2w, x from maxX (low u) to minX
			for (int k = 0; k < w; k++) {
				int col = tint.argb(c.bu() + 2 * d + w + k, bv);
				if (col != 0) {
					float x1 = maxX - k * sx, x0 = x1 - sx;
					quad(vc, pose, light, c.u() + 2 * d + w + k, tv, x0, x1, y0, y1, maxZ, maxZ, 0f, 0f, 1f, Plane.Z, col);
				}
			}
		}
	}

	private enum Plane {
		X, Y, Z
	}

	/**
	 * One texel quad, drawn from both sides (the per-texel faces have no fixed winding to trust), sampling skin texel
	 * ({@code tu}, {@code tv}) -- inset a hair so neighbouring texels never bleed in.
	 */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int tu, int tv, float x0, float x1, float y0, float y1, float z0,
			float z1, float nx, float ny, float nz, Plane plane, int argb) {
		int ca = (argb >>> 24) & 0xFF, cr = (argb >>> 16) & 0xFF, cg = (argb >>> 8) & 0xFF, cb = argb & 0xFF;
		float u0 = (tu + 0.02f) / 64f, u1 = (tu + 0.98f) / 64f, v0 = (tv + 0.02f) / 64f, v1 = (tv + 0.98f) / 64f;
		float[][] p;
		switch (plane) {
			case X -> p = new float[][] { { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
			case Y -> p = new float[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
			default -> p = new float[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 } };
		}
		Matrix4f m = pose.pose();
		float[][] uv = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(cr, cg, cb, ca).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose, nx, ny, nz);
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(cr, cg, cb, ca).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose, nx, ny, nz);
		}
	}
}
