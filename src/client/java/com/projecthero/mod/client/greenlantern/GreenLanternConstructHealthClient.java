package com.projecthero.mod.client.greenlantern;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.network.GreenLanternConstructHealthPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user-approved revamp): a damaged hard-light block construct (Hard-Light Wall, Containment Cage) shows its
 * health. The server sends each one's HP fraction ({@link GreenLanternConstructHealthPayload}); this draws a skin of
 * darker green just outside every block, only on the outer faces of the construct (no grid lines between its own cells):
 *
 * <ul>
 * <li>full HP: nothing extra -- the block's own bright green;</li>
 * <li>as it takes damage the skin thickens, so the glow dims and the colour slides from bright green toward a deep,
 * murky green;</li>
 * <li>under {@link GreenLanternConfig#DURABILITY_CRACK_BELOW} (50%) the server's vanilla crack overlay shows through as
 * well;</li>
 * <li>under {@link GreenLanternConfig#DURABILITY_FLICKER_BELOW} (20%) the light stutters: the skin flickers between
 * near-black and faint, with the odd frame where the whole construct seems to cut out;</li>
 * <li>at 0 the server's shatter runs and the entry is dropped.</li>
 * </ul>
 *
 * Entries the server stops refreshing (the construct left range, a lost packet) fade out after {@link #STALE_MS}.
 */
public final class GreenLanternConstructHealthClient {
	private static final long STALE_MS = 6000L;
	private static final int BRIGHT = 0x35F075;
	private static final int MURKY = 0x0A3D1A;
	private static final int DEAD = 0x021307;

	private record Entry(float health, List<BlockPos> cells, Set<BlockPos> set, long seenAt) {
	}

	private static final Map<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();

	private GreenLanternConstructHealthClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(GreenLanternConstructHealthPayload.TYPE,
				(payload, context) -> context.client().execute(() -> receive(payload)));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(GreenLanternConstructHealthClient::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ENTRIES.clear());
	}

	private static void receive(GreenLanternConstructHealthPayload p) {
		put(p.id(), p.health(), p.cells());
	}

	/** Record (or forget, health below 0) one construct's health -- also used by the visual-check harness. */
	public static void put(int id, float health, List<BlockPos> cells) {
		if (health < 0f || cells.isEmpty()) {
			ENTRIES.remove(id);
			return;
		}
		ENTRIES.put(id, new Entry(health, List.copyOf(cells), new HashSet<>(cells), System.currentTimeMillis()));
	}

	/** The look for a health fraction: {rgb, alpha} of the skin, before the flicker. */
	static float skinAlpha(float health) {
		float damage = 1f - Mth.clamp(health, 0f, 1f);
		return 0.08f + 0.55f * damage;
	}

	static int skinRgb(float health) {
		float h = Mth.clamp(health, 0f, 1f);
		return h > 0.5f ? lerp(MURKY, BRIGHT, (h - 0.5f) * 2f * 0.35f) : lerp(DEAD, MURKY, h * 2f);
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return (r << 16) | (g << 8) | bl;
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (ENTRIES.isEmpty() || mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		float time = mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		VertexConsumer vc = TurboDraw.buffer(context.consumers());
		for (Map.Entry<Integer, Entry> en : ENTRIES.entrySet()) {
			Entry e = en.getValue();
			long age = nowMs - e.seenAt();
			if (age > STALE_MS) {
				ENTRIES.remove(en.getKey());
				continue;
			}
			float fade = age > STALE_MS - 1000L ? (STALE_MS - age) / 1000f : 1f;
			float alpha = skinAlpha(e.health()) * fade;
			int rgb = skinRgb(e.health());
			if (e.health() < GreenLanternConfig.DURABILITY_FLICKER_BELOW) {
				// the stutter: a fast uneven flicker plus the odd frame where the light all but cuts out
				int id = en.getKey();
				float n = Mth.sin(time * 1.9f + id) * Mth.sin(time * 0.73f + id * 0.37f);
				alpha *= 0.7f + 0.45f * n;
				if (((int) (time * 0.5f) + id) % 9 == 0) {
					alpha = Math.min(0.92f, alpha + 0.45f);
					rgb = DEAD;
				}
			}
			alpha = Mth.clamp(alpha, 0f, 0.95f);
			if (alpha < 0.02f) {
				continue;
			}
			// under 50%: glowing fractures across the outer faces, more of them as it fails (the server's vanilla crack
			// overlay sits under this, but it barely reads on translucent hard light)
			int cracks = e.health() >= GreenLanternConfig.DURABILITY_CRACK_BELOW ? 0
					: 1 + Math.round((1f - e.health() / GreenLanternConfig.DURABILITY_CRACK_BELOW) * 3f);
			float crackAlpha = Mth.clamp(0.75f * fade * (alpha / Math.max(0.05f, skinAlpha(e.health()) * fade)), 0f, 0.9f);
			for (BlockPos c : e.cells()) {
				if (c.distToCenterSqr(cam) > 96 * 96) {
					continue;
				}
				pose.pushPose();
				pose.translate(c.getX() + 0.5 - cam.x, c.getY() + 0.5 - cam.y, c.getZ() + 0.5 - cam.z);
				PoseStack.Pose p = pose.last();
				for (Direction d : Direction.values()) {
					if (!e.set().contains(c.relative(d))) {
						face(vc, p, d, 0.503f, rgb, alpha);
						if (cracks > 0) {
							cracks(vc, p, d, c, cracks, crackAlpha);
						}
					}
				}
				pose.popPose();
			}
		}
	}

	private static double frac(double v) {
		return v - Math.floor(v);
	}

	/**
	 * {@code n} jagged fracture lines on face {@code d} of the cell at the origin -- a fixed pattern per cell and face
	 * (seeded from the position), so they stay put frame to frame. Pale green-white, like light leaking through.
	 */
	private static void cracks(VertexConsumer vc, PoseStack.Pose p, Direction d, BlockPos cell, int n, float alpha) {
		float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
		float ux = Math.abs(ny) > 0.5f || Math.abs(nz) > 0.5f ? 1 : 0, uz = ux == 1 ? 0 : 1, uy = 0;
		float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
		float h = 0.507f;
		long seed = cell.asLong() * 31 + d.ordinal();
		int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
		for (int k = 0; k < n; k++) {
			double s = frac(seed * 0.0001373 + k * 0.618034);
			double t = frac(seed * 0.0000917 + k * 0.414214 + 0.3);
			// start somewhere on the face, walk across it in short kinked steps
			double cu = -0.4 + 0.8 * s, cv = -0.4 + 0.8 * t;
			double ang = frac(s * 7.31 + t * 3.17) * Math.PI * 2;
			for (int step = 0; step < 4; step++) {
				ang += (frac((seed + k * 13 + step * 7) * 0.000713) - 0.5) * 1.6;
				double len = 0.16 + 0.1 * frac((seed + step) * 0.00191 + k);
				double eu = Mth.clamp(cu + Math.cos(ang) * len, -0.49, 0.49);
				double ev = Mth.clamp(cv + Math.sin(ang) * len, -0.49, 0.49);
				double du = eu - cu, dv = ev - cv;
				double l = Math.max(1.0e-4, Math.sqrt(du * du + dv * dv));
				double w = 0.018;
				double ou = -dv / l * w, ov = du / l * w;
				float[][] q = {
						pt(nx, ny, nz, ux, uy, uz, vx, vy, vz, h, cu + ou, cv + ov),
						pt(nx, ny, nz, ux, uy, uz, vx, vy, vz, h, eu + ou, ev + ov),
						pt(nx, ny, nz, ux, uy, uz, vx, vy, vz, h, eu - ou, ev - ov),
						pt(nx, ny, nz, ux, uy, uz, vx, vy, vz, h, cu - ou, cv - ov) };
				for (int i = 0; i < 4; i++) {
					vertex(vc, p, q[i], 0xD8, 0xFF, 0xE4, a, nx, ny, nz);
				}
				for (int i = 3; i >= 0; i--) {
					vertex(vc, p, q[i], 0xD8, 0xFF, 0xE4, a, nx, ny, nz);
				}
				cu = eu;
				cv = ev;
			}
		}
	}

	private static float[] pt(float nx, float ny, float nz, float ux, float uy, float uz, float vx, float vy, float vz, float h,
			double u, double v) {
		return new float[] { (float) (nx * h + ux * u + vx * v), (float) (ny * h + uy * u + vy * v), (float) (nz * h + uz * u + vz * v) };
	}

	/** One face of a cube of half-size {@code h} round the origin, both windings. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, Direction d, float h, int rgb, float alpha) {
		float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
		// two in-plane axes
		float ux = Math.abs(ny) > 0.5f || Math.abs(nz) > 0.5f ? 1 : 0, uy = 0, uz = ux == 1 ? 0 : 1;
		float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
		float cx = nx * h, cy = ny * h, cz = nz * h;
		float[][] corners = {
				{ cx - (ux + vx) * h, cy - (uy + vy) * h, cz - (uz + vz) * h },
				{ cx + (ux - vx) * h, cy + (uy - vy) * h, cz + (uz - vz) * h },
				{ cx + (ux + vx) * h, cy + (uy + vy) * h, cz + (uz + vz) * h },
				{ cx - (ux - vx) * h, cy - (uy - vy) * h, cz - (uz - vz) * h } };
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
		for (int i = 0; i < 4; i++) {
			vertex(vc, p, corners[i], r, g, b, a, nx, ny, nz);
		}
		for (int i = 3; i >= 0; i--) {
			vertex(vc, p, corners[i], r, g, b, a, nx, ny, nz);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float[] v, int r, int g, int b, int a, float nx, float ny, float nz) {
		vc.addVertex(p, v[0], v[1], v[2]).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
	}
}
