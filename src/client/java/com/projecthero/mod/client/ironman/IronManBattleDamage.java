package com.projecthero.mod.client.ironman;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent F): visible battle damage on every Iron Man suit, driven by the synced suit integrity.
 *
 * <ul>
 *   <li><b>Below {@value #SPARK_BELOW}</b>: the suit throws the odd electric spark from a shoulder, arm or the chest.</li>
 *   <li><b>Below {@value #DAMAGE_BELOW}</b>: the armour model is drawn with a <b>generated damaged copy</b> of its own
 *       texture -- scorch blotches and dark cracks with a bright bevel, in three steps that each add to the last
 *       ({@link #level}). The base PNGs and glowmasks are never touched; glowmask texels (eyes, reactor, palms) are left
 *       out of the damage so the lights keep their exact look. Smoke puffs start, and the eye / reactor glow flickers
 *       ({@link #glowFlickerOff}).</li>
 *   <li>The closed visor HUD gets cracks across the screen at the same thresholds ({@link #renderVisorCracks}).</li>
 * </ul>
 * Full-health suits are untouched: {@link #texture} returns the base texture, no particles, the glow never flickers.
 * Damaged copies are built lazily on the render thread, once per texture and level, and kept for the session.
 */
public final class IronManBattleDamage {
	public static final float SPARK_BELOW = 0.60f;
	public static final float DAMAGE_BELOW = 0.35f;
	public static final float HEAVY_BELOW = 0.20f;
	public static final float WRECKED_BELOW = 0.08f;
	public static final int LEVELS = 3;

	private static final Map<ResourceLocation, ResourceLocation[]> CACHE = new HashMap<>();

	private IronManBattleDamage() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(IronManBattleDamage::clientTick);
	}

	// ---------------- integrity ----------------

	/** The integrity fraction (0..1) of the suit a piece belongs to, as worn by {@code player}; 1 if unknown. */
	public static float integrity(Player player, String suitId) {
		TonyStarkState s = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		if (s == null || suitId == null) {
			return 1f;
		}
		float max = IronManEnergy.maxIntegrity(suitId);
		Float v = s.suitIntegrity.get(suitId);
		return v == null || max <= 0f ? 1f : Mth.clamp(v / max, 0f, 1f);
	}

	/** The suit id of the worn chestplate (or helmet), else null. */
	public static String wornSuit(Player player) {
		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem c) {
			return c.suitId();
		}
		if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem h) {
			return h.suitId();
		}
		return null;
	}

	/** 0 = clean, 1 = scorched (&lt; 35%), 2 = heavy (&lt; 20%), 3 = wrecked (&lt; 8%). */
	public static int level(float integrity) {
		return integrity < WRECKED_BELOW ? 3 : integrity < HEAVY_BELOW ? 2 : integrity < DAMAGE_BELOW ? 1 : 0;
	}

	// ---------------- armour texture ----------------

	/**
	 * Hooked from {@code SuperheroArmorRenderer#getRenderType}: the damaged copy of {@code texture} for a worn piece,
	 * or {@code texture} unchanged (clean suit, or a mid-build reveal texture -- those are left alone).
	 */
	public static ResourceLocation texture(Player player, IronManArmorItem piece, ResourceLocation texture) {
		int lvl = level(integrity(player, piece.suitId()));
		if (lvl == 0 || !texture.getPath().startsWith("textures/armor/")) {
			return texture;
		}
		ResourceLocation[] built = CACHE.computeIfAbsent(texture, t -> build(t, piece.armorSetId()));
		return built.length < lvl ? texture : built[lvl - 1];
	}

	private static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	private static ResourceLocation[] build(ResourceLocation base, String setId) {
		NativeImage src = read(base);
		if (src == null) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] no battle-damage texture for {}", base);
			return new ResourceLocation[0];
		}
		NativeImage glow = read(ProjectHeroMod.id("textures/armor/" + setId + "_glowmask.png"));
		int w = src.getWidth();
		int h = src.getHeight();
		boolean[][] lit = new boolean[w][h];
		if (glow != null) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int gx = x * glow.getWidth() / w;
					int gy = y * glow.getHeight() / h;
					int c = glow.getPixelRGBA(gx, gy);
					int a = (c >>> 24) & 0xFF;
					int sum = (c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF);
					lit[x][y] = a > 16 && sum > 48;
				}
			}
			glow.close();
			lit = protectLights(lit, w, h);
		}
		Random rng = new Random(base.toString().hashCode() * 7919L + 29L);
		double scale = w / 64.0;
		// value noise: a coarse and a fine lattice
		double[][] coarse = lattice(rng, w, h, 5.0 * scale);
		double[][] fine = lattice(rng, w, h, 2.0 * scale);
		// crack walks, the first N of them used at each level
		int cracksTotal = 26;
		List<int[]> crackPx = new ArrayList<>();      // {x, y, crackIndex}
		List<int[]> bevelPx = new ArrayList<>();
		for (int c = 0; c < cracksTotal; c++) {
			int x = rng.nextInt(w);
			int y = rng.nextInt(h);
			int tries = 0;
			while (tries++ < 40 && (alpha(src, x, y) == 0 || lit[x][y])) {
				x = rng.nextInt(w);
				y = rng.nextInt(h);
			}
			int len = (int) Math.round((3 + rng.nextInt(5)) * scale);
			int dx = rng.nextBoolean() ? 1 : -1;
			int dy = rng.nextBoolean() ? 1 : -1;
			for (int i = 0; i < len; i++) {
				if (x < 0 || y < 0 || x >= w || y >= h) {
					break;
				}
				crackPx.add(new int[]{x, y, c});
				int bx = x + (dy > 0 ? 1 : -1);
				if (bx >= 0 && bx < w) {
					bevelPx.add(new int[]{bx, y, c});
				}
				// mostly diagonal, jagged
				int r = rng.nextInt(4);
				if (r == 0) {
					x += dx;
				} else if (r == 1) {
					y += dy;
				} else {
					x += dx;
					y += dy;
				}
				if (rng.nextInt(6) == 0) {
					dx = -dx;
				}
			}
		}
		int[] cracksAt = {9, 17, 26};
		double[] scorchAt = {0.66, 0.56, 0.46};
		ResourceLocation[] out = new ResourceLocation[LEVELS];
		String tag = base.getNamespace() + "_" + base.getPath().replaceAll("[^a-z0-9_]", "_");
		for (int lvl = 0; lvl < LEVELS; lvl++) {
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			double th = scorchAt[lvl];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (alpha(src, x, y) == 0 || lit[x][y]) {
						continue;
					}
					double n = coarse[x][y] * 0.7 + fine[x][y] * 0.3;
					if (n > th) {
						double t = Math.min(1.0, (n - th) / (1.0 - th) * 1.6);
						img.setPixelRGBA(x, y, mix(img.getPixelRGBA(x, y), 0x0F151A, 0.30 + 0.5 * t)); // ABGR soot (warm brown)
					}
				}
			}
			for (int[] p : bevelPx) {
				if (p[2] < cracksAt[lvl] && alpha(src, p[0], p[1]) != 0 && !lit[p[0]][p[1]]) {
					img.setPixelRGBA(p[0], p[1], mix(img.getPixelRGBA(p[0], p[1]), 0xB0B8BC, 0.30));
				}
			}
			for (int[] p : crackPx) {
				if (p[2] < cracksAt[lvl] && alpha(src, p[0], p[1]) != 0 && !lit[p[0]][p[1]]) {
					img.setPixelRGBA(p[0], p[1], mix(img.getPixelRGBA(p[0], p[1]), 0x08090A, 0.85));
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/ironman_battle_damage/" + tag + "_" + (lvl + 1));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
			out[lvl] = id;
		}
		src.close();
		return out;
	}

	/**
	 * The texels the damage must leave alone: every glowmask texel, its partner on the other skin layer (a scorched
	 * outer-layer texel in front of a lit eye would hide it), and a one-texel margin round both.
	 */
	private static boolean[][] protectLights(boolean[][] lit, int w, int h) {
		boolean[][] layered = new boolean[w][h];
		double s = w / 64.0;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (!lit[x][y]) {
					continue;
				}
				layered[x][y] = true;
				int[] p = partner((int) (x / s), (int) (y / s));
				if (p != null) {
					int px = (int) (p[0] * s + (x - (int) (x / s) * s));
					int py = (int) (p[1] * s + (y - (int) (y / s) * s));
					if (px >= 0 && py >= 0 && px < w && py < h) {
						layered[px][py] = true;
					}
				}
			}
		}
		boolean[][] out = new boolean[w][h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (!layered[x][y]) {
					continue;
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						int nx = x + dx;
						int ny = y + dy;
						if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
							out[nx][ny] = true;
						}
					}
				}
			}
		}
		return out;
	}

	/** The same body texel on the other skin layer, in 64x64 player-skin UV space (null if none). */
	private static int[] partner(int u, int v) {
		if (v < 16) { // head <-> hat
			return u < 32 ? new int[]{u + 32, v} : new int[]{u - 32, v};
		}
		if (v < 32) { // right leg, body, right arm -> their jacket / pants / sleeve below
			return u < 56 ? new int[]{u, v + 16} : null;
		}
		if (v < 48) {
			return u < 56 ? new int[]{u, v - 16} : null;
		}
		// bottom row: left leg pants (0-15) <-> left leg (16-31), left arm (32-47) <-> left sleeve (48-63)
		if (u < 16) {
			return new int[]{u + 16, v};
		}
		if (u < 32) {
			return new int[]{u - 16, v};
		}
		return u < 48 ? new int[]{u + 16, v} : new int[]{u - 16, v};
	}

	private static double[][] lattice(Random rng, int w, int h, double cell) {
		int gw = (int) Math.ceil(w / cell) + 2;
		int gh = (int) Math.ceil(h / cell) + 2;
		double[][] grid = new double[gw][gh];
		for (int i = 0; i < gw; i++) {
			for (int j = 0; j < gh; j++) {
				grid[i][j] = rng.nextDouble();
			}
		}
		double[][] out = new double[w][h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				double gx = x / cell;
				double gy = y / cell;
				int ix = (int) gx;
				int iy = (int) gy;
				double fx = smooth(gx - ix);
				double fy = smooth(gy - iy);
				double a = Mth.lerp(fx, grid[ix][iy], grid[ix + 1][iy]);
				double b = Mth.lerp(fx, grid[ix][iy + 1], grid[ix + 1][iy + 1]);
				out[x][y] = Mth.lerp(fy, a, b);
			}
		}
		return out;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static int alpha(NativeImage img, int x, int y) {
		return (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
	}

	/** Blend an ABGR pixel's colour toward {@code bgr} (0xBBGGRR) by {@code t}, keeping its alpha. */
	private static int mix(int abgr, int bgr, double t) {
		int a = abgr & 0xFF000000;
		int r = (int) Mth.lerp(t, abgr & 0xFF, bgr & 0xFF);
		int g = (int) Mth.lerp(t, (abgr >> 8) & 0xFF, (bgr >> 8) & 0xFF);
		int b = (int) Mth.lerp(t, (abgr >> 16) & 0xFF, (bgr >> 16) & 0xFF);
		return a | (b << 16) | (g << 8) | r;
	}

	// ---------------- glow flicker ----------------

	/** True on the frames a damaged suit's eye / reactor glow should drop out. Never below the damage threshold. */
	public static boolean glowFlickerOff(Player player) {
		String suit = wornSuit(player);
		float f = suit == null ? 1f : integrity(player, suit);
		if (f >= DAMAGE_BELOW || player.level() == null) {
			return false;
		}
		long slot = player.level().getGameTime() / 2;
		int hash = (int) ((slot * 0x9E3779B97F4A7C15L + player.getId() * 0xC2B2AE3D27D4EB4FL) >>> 40) & 0xFF;
		int chance = f < WRECKED_BELOW ? 70 : f < HEAVY_BELOW ? 45 : 25; // of 256
		return hash < chance;
	}

	// ---------------- sparks + smoke ----------------

	private static void clientTick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		RandomSource rnd = level.getRandom();
		for (Player p : level.players()) {
			if (p.isInvisible() || p.distanceToSqr(mc.player) > 48 * 48) {
				continue;
			}
			String suit = wornSuit(p);
			if (suit == null) {
				continue;
			}
			float f = integrity(p, suit);
			if (f >= SPARK_BELOW) {
				continue;
			}
			boolean firstPersonSelf = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
			float rate = firstPersonSelf ? 0.4f : 1f;
			float sparkChance = (0.03f + 0.15f * (1f - f / SPARK_BELOW)) * rate;
			if (rnd.nextFloat() < sparkChance) {
				Vec3 at = bodyPoint(p, rnd, firstPersonSelf);
				int n = 2 + rnd.nextInt(3);
				for (int i = 0; i < n; i++) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
							(rnd.nextDouble() - 0.5) * 0.25, rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.25);
				}
			}
			if (f < DAMAGE_BELOW) {
				float smokeChance = (0.08f + 0.22f * (1f - f / DAMAGE_BELOW)) * rate;
				if (rnd.nextFloat() < smokeChance) {
					Vec3 at = bodyPoint(p, rnd, firstPersonSelf);
					level.addParticle(f < HEAVY_BELOW && rnd.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
							at.x, at.y, at.z, 0, 0.04 + rnd.nextDouble() * 0.03, 0);
				}
			}
		}
	}

	/** A random point on the suit's shoulders, chest, arms or legs, in world space (behind the camera for first person). */
	private static Vec3 bodyPoint(Player p, RandomSource rnd, boolean firstPersonSelf) {
		double side;
		double up;
		double fwd;
		switch (rnd.nextInt(firstPersonSelf ? 3 : 5)) {
			case 0 -> { side = rnd.nextBoolean() ? 0.36 : -0.36; up = 1.38; fwd = -0.05; }  // shoulders
			case 1 -> { side = (rnd.nextDouble() - 0.5) * 0.4; up = 1.0 + rnd.nextDouble() * 0.3; fwd = -0.18; } // back
			case 2 -> { side = rnd.nextBoolean() ? 0.14 : -0.14; up = 0.3 + rnd.nextDouble() * 0.4; fwd = 0.0; } // legs
			case 3 -> { side = rnd.nextBoolean() ? 0.42 : -0.42; up = 0.85 + rnd.nextDouble() * 0.4; fwd = 0.0; } // arms
			default -> { side = (rnd.nextDouble() - 0.5) * 0.3; up = 1.05 + rnd.nextDouble() * 0.25; fwd = 0.17; } // chest
		}
		if (p.isCrouching()) {
			up *= 0.85;
		}
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		// body space: +x = player's left, +z = forward
		double wx = side * cos - fwd * sin;
		double wz = side * sin + fwd * cos;
		return new Vec3(p.getX() + wx, p.getY() + up, p.getZ() + wz);
	}

	// ---------------- visor cracks ----------------

	/** Fixed crack paths in screen fractions: {startX, startY, angle, length}. */
	private static final double[][] CRACKS = {
			{0.0, 0.08, 0.35, 0.30},
			{1.0, 0.85, Math.PI + 0.25, 0.26},
			{0.82, 0.0, Math.PI * 0.62, 0.22},
			{0.06, 1.0, -0.9, 0.24},
			{1.0, 0.30, Math.PI - 0.45, 0.20},
			{0.38, 0.0, Math.PI * 0.45, 0.16},
	};

	/** Cracks across the closed visor at low integrity -- 2 below 35%, 4 below 20%, 6 below 8%. */
	public static void renderVisorCracks(GuiGraphics g, float integrity, boolean retro) {
		int lvl = level(integrity);
		if (lvl == 0) {
			return;
		}
		int count = lvl == 1 ? 2 : lvl == 2 ? 4 : 6;
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		int glass = retro ? 0x8CFFE0B0 : 0x8CD8F4FF;
		int shadow = 0x50000000;
		for (int i = 0; i < count; i++) {
			double[] c = CRACKS[i];
			Random rng = new Random(9173L * (i + 1));
			crack(g, rng, c[0] * sw, c[1] * sh, c[2], c[3] * Math.min(sw, sh * 1.6), glass, shadow, 2);
		}
	}

	private static void crack(GuiGraphics g, Random rng, double x, double y, double angle, double length,
			int glass, int shadow, int depth) {
		double travelled = 0;
		while (travelled < length) {
			double seg = 6 + rng.nextDouble() * 10;
			double a = angle + (rng.nextDouble() - 0.5) * 0.9;
			double nx = x + Math.cos(a) * seg;
			double ny = y + Math.sin(a) * seg;
			line(g, (int) x + 1, (int) y + 1, (int) nx + 1, (int) ny + 1, shadow);
			line(g, (int) x, (int) y, (int) nx, (int) ny, glass);
			if (depth > 0 && rng.nextInt(4) == 0) {
				crack(g, rng, nx, ny, a + (rng.nextBoolean() ? 0.8 : -0.8), (length - travelled) * 0.45,
						glass & 0x80FFFFFF, shadow, depth - 1);
			}
			x = nx;
			y = ny;
			travelled += seg;
		}
	}

	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		int guard = 0;
		while (guard++ < 400) {
			g.fill(x0, y0, x0 + 1, y0 + 1, color);
			if (x0 == x1 && y0 == y1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 >= dy) {
				err += dy;
				x0 += sx;
			}
			if (e2 <= dx) {
				err += dx;
				y0 += sy;
			}
		}
	}
}
