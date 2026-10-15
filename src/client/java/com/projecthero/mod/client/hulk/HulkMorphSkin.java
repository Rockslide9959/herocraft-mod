package com.projecthero.mod.client.hulk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.maxsteel.MaxSteelNano;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.4: the Hulk green spreading through Banner's own skin, texel by texel (the {@code MaxSteelNano} /
 * {@code SymbiotePetSkin} technique). The Hulk model uses the standard 64x64 player-skin layout, so the target of every
 * Banner texel is simply the Hulk texture's texel at the same place (slim arms remapped onto the wide ones) -- when the green
 * has covered him, Banner IS wearing the Hulk's skin, and the GeckoLib Hulk can replace him without a seam.
 *
 * <p>The order the texels turn in ({@link Layout#order}) is a distance field along the body from the sternum: every texel of
 * the 64x64 layout is placed on the standing player model (head, body, arms, legs and their second layers), and its distance
 * is chest -> shoulder / hip / neck -> along the limb, roughened by 3D value noise so the front is ragged. A ridged-noise vein
 * mask ({@link Layout#vein}) marks texels that go dark green AHEAD of the front -- veins bulging under the skin before the
 * colour arrives. On the second layer (shirt, sleeves, trousers, hat) the front tears the cloth away: those texels become the
 * Hulk's (mostly empty) second layer.
 *
 * <p>Banner's skin pixels are read once per skin: from the resource pack for a default skin, else straight off the GPU
 * ({@code glGetTexImage} of the loaded skin texture) -- done in the client tick, never mid-render. Each animating player gets
 * one 64x64 {@link DynamicTexture}, re-painted and uploaded only when the reveal moves a step ({@link #STEPS}); it is released
 * once his morph has been over for a moment or he leaves ({@link #sweep}).
 */
public final class HulkMorphSkin {
	/** Reveal resolution: re-paints happen at most this many times per morph. */
	public static final int STEPS = 72;
	/** Width of the dark leading edge of the green, in order units (0..1). */
	public static final float EDGE = 0.06f;
	/** How far ahead of the front the veins run (order units). */
	public static final float VEIN_LEAD = 0.14f;
	/** Vein density: ridged-noise threshold (higher = fewer, thinner veins). */
	public static final float VEIN_RIDGE = 0.93f;
	/** Ticks a finished morph's texture is kept before it is released. */
	private static final long KEEP_TICKS = 40L;
	/** Retry a skin that was not readable yet (still downloading) after this many ticks. */
	private static final long RETRY_TICKS = 40L;

	private static final ResourceLocation HULK_TEXTURE = ProjectHeroMod.id("textures/entity/hulk.png");
	/** NativeImage is ABGR. */
	private static final int VEIN_ABGR = abgr(34, 74, 28);
	private static final int EDGE_ABGR = abgr(52, 92, 36);

	private static NativeImage hulkImage;
	private static boolean hulkImageTried;
	private static final Layout WIDE = new Layout(false);
	private static final Layout SLIM = new Layout(true);
	private static final Map<UUID, Slot> SLOTS = new HashMap<>();

	private HulkMorphSkin() {
	}

	private static final class Slot {
		final ResourceLocation id;
		DynamicTexture texture;
		ResourceLocation skinId;
		NativeImage skin;
		boolean slim;
		int step = -1;
		long lastUsed;
		long retryAt;
		/** A few of his shirt's colours (RGB), for the torn-cloth particles. */
		int[] cloth = new int[0];

		Slot(UUID player) {
			this.id = ProjectHeroMod.id("dynamic/hulk_morph/" + player.toString().replace("-", ""));
		}

		void releaseTexture() {
			if (this.texture != null) {
				Minecraft.getInstance().getTextureManager().release(this.id); // closes it (and its pixels)
				this.texture = null;
			}
			this.step = -1;
		}

		void close() {
			releaseTexture();
			if (this.skin != null) {
				this.skin.close();
				this.skin = null;
			}
			this.skinId = null;
		}
	}

	// ---------------------------------------------------------------- the texture to draw

	/**
	 * The texture Banner's body (or first-person arm) is drawn with this frame: {@code base} untouched unless he is mid-morph
	 * and his skin has been read, else his composite.
	 */
	public static ResourceLocation texture(Player player, ResourceLocation base, float partial) {
		float green = HulkMorph.green(player, partial);
		if (green < 0.0f) {
			return base;
		}
		Slot slot = SLOTS.get(player.getUUID());
		NativeImage hulk = hulk();
		if (slot == null || slot.skin == null || hulk == null || slot.skinId == null || !slot.skinId.equals(skinOf(player))) {
			return base; // not read yet (HulkMorphFx.tick prepares it) -- his own skin this frame
		}
		slot.lastUsed = player.level().getGameTime();
		int step = Math.max(0, Math.min(STEPS, Math.round(green * STEPS)));
		if (slot.texture == null) {
			slot.texture = new DynamicTexture(64, 64, true);
			Minecraft.getInstance().getTextureManager().register(slot.id, slot.texture);
			slot.step = -1;
		}
		if (step != slot.step) {
			slot.step = step;
			paint(slot.texture.getPixels(), slot.skin, hulk, slot.slim ? SLIM : WIDE, step / (float) STEPS);
			slot.texture.upload();
		}
		return slot.id;
	}

	/** The colours of his shirt (RGB ints), for {@link HulkMorphFx}'s shreds; empty if his skin is not read. */
	public static int[] clothColours(Player player) {
		Slot slot = SLOTS.get(player.getUUID());
		return slot == null ? new int[0] : slot.cloth;
	}

	private static ResourceLocation skinOf(Player player) {
		return player instanceof AbstractClientPlayer cp ? cp.getSkin().texture() : null;
	}

	private static void paint(NativeImage out, NativeImage skin, NativeImage hulk, Layout layout, float p) {
		if (out == null) {
			return;
		}
		float front = p * (1.0f + 2.0f * EDGE) - EDGE;
		for (int i = 0; i < 64 * 64; i++) {
			int x = i & 63;
			int y = i >> 6;
			int own = skin.getPixelRGBA(x, y);
			float o = layout.order[i];
			int px;
			if (o < 0.0f) {
				px = own; // not on the model
			} else if (o <= front - EDGE) {
				int tgt = layout.target[i];
				px = hulk.getPixelRGBA(tgt & 63, tgt >> 6);
				if (!layout.overlay[i] && ((px >>> 24) & 0xFF) < 128) {
					px = EDGE_ABGR; // a hole in the Hulk's base layer: never let Banner show through it
				}
			} else if (o <= front) {
				// the leading edge: skin straining dark green; cloth darkened as it stretches to tearing
				px = layout.overlay[i] ? darken(own, 0.55f) : mix(own, EDGE_ABGR, 0.75f);
			} else if (!layout.overlay[i] && layout.vein[i] > 0.0f && o - VEIN_LEAD * layout.vein[i] <= front) {
				px = mix(own, VEIN_ABGR, 0.45f + 0.4f * layout.vein[i]); // veins rising ahead of the colour
			} else {
				px = own;
			}
			out.setPixelRGBA(x, y, px);
		}
	}

	// ---------------------------------------------------------------- reading skins (client tick)

	/** Makes sure this player's skin has been read (call from the client tick, not mid-render). */
	public static void prepare(Player player) {
		ResourceLocation skinId = skinOf(player);
		if (skinId == null) {
			return;
		}
		long now = player.level().getGameTime();
		Slot slot = SLOTS.computeIfAbsent(player.getUUID(), Slot::new);
		slot.lastUsed = Math.max(slot.lastUsed, now);
		if (skinId.equals(slot.skinId) && slot.skin != null) {
			return;
		}
		if (skinId.equals(slot.skinId) && now < slot.retryAt) {
			return;
		}
		if (slot.skin != null) {
			slot.skin.close();
			slot.skin = null;
		}
		slot.skinId = skinId;
		slot.step = -1;
		slot.slim = player instanceof AbstractClientPlayer cp && cp.getSkin().model() == PlayerSkin.Model.SLIM;
		slot.skin = readSkin(skinId);
		if (slot.skin == null) {
			slot.retryAt = now + RETRY_TICKS;
			return;
		}
		slot.cloth = clothPalette(slot.skin);
	}

	private static NativeImage readSkin(ResourceLocation id) {
		Minecraft mc = Minecraft.getInstance();
		// a default (or resource-pack) skin: straight from the resources
		if (mc.getResourceManager().getResource(id).isPresent()) {
			NativeImage img = MaxSteelNano.read(id);
			if (img != null && img.getWidth() == 64 && img.getHeight() == 64) {
				return img;
			}
			if (img != null) {
				img.close();
			}
		}
		// a downloaded skin: off the GPU (the loaded texture is always the 64x64 layout; legacy 64x32 skins are converted)
		try {
			AbstractTexture tex = mc.getTextureManager().getTexture(id);
			int glId = tex.getId();
			if (glId <= 0) {
				return null;
			}
			RenderSystem.assertOnRenderThread();
			GlStateManager._bindTexture(glId);
			int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
			int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
			if (w != 64 || h != 64) {
				return null; // still downloading (or an odd HD skin): his own skin is drawn, retried later
			}
			NativeImage img = new NativeImage(64, 64, false);
			img.downloadTexture(0, false);
			return img;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read the skin {} for the Hulk morph: {}", id, e.toString());
			return null;
		}
	}

	/** Up to four opaque colours off his shirt (second layer first, else the body). */
	private static int[] clothPalette(NativeImage skin) {
		int[] out = new int[4];
		int n = 0;
		int[][] spots = { { 21, 37 }, { 26, 41 }, { 18, 44 }, { 23, 46 }, { 21, 21 }, { 26, 25 }, { 18, 28 }, { 23, 30 } };
		for (int[] sp : spots) {
			int c = skin.getPixelRGBA(sp[0], sp[1]);
			if (((c >>> 24) & 0xFF) > 128 && n < 4) {
				out[n++] = ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF); // ABGR -> RGB
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}

	private static NativeImage hulk() {
		if (hulkImage == null && !hulkImageTried) {
			hulkImageTried = true;
			NativeImage img = MaxSteelNano.read(HULK_TEXTURE);
			if (img != null && (img.getWidth() != 64 || img.getHeight() != 64)) {
				img.close();
				img = null;
			}
			hulkImage = img;
		}
		return hulkImage;
	}

	/**
	 * Releases the textures of morphs that ended a moment ago and of players no longer in the world (call every client tick;
	 * {@code null} level = everything goes).
	 */
	public static void sweep(Minecraft mc) {
		if (SLOTS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Slot>> it = SLOTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Slot> e = it.next();
			Slot slot = e.getValue();
			Player p = mc.level == null ? null : mc.level.getPlayerByUUID(e.getKey());
			if (p == null) {
				slot.close();
				it.remove();
				continue;
			}
			long now = mc.level.getGameTime();
			if (HulkMorph.green(p, 0.0f) < 0.0f && now - slot.lastUsed > KEEP_TICKS) {
				slot.close();
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- colours

	private static int abgr(int r, int g, int b) {
		return 0xFF000000 | (b << 16) | (g << 8) | r;
	}

	private static int mix(int a, int b, float f) {
		int ar = a & 0xFF, ag = (a >> 8) & 0xFF, ab = (a >> 16) & 0xFF;
		int br = b & 0xFF, bg = (b >> 8) & 0xFF, bb = (b >> 16) & 0xFF;
		int r = Math.round(ar + (br - ar) * f);
		int g = Math.round(ag + (bg - ag) * f);
		int bl = Math.round(ab + (bb - ab) * f);
		return (a & 0xFF000000) | (bl << 16) | (g << 8) | r;
	}

	private static int darken(int a, float f) {
		int r = Math.round((a & 0xFF) * f);
		int g = Math.round(((a >> 8) & 0xFF) * f);
		int b = Math.round(((a >> 16) & 0xFF) * f);
		return (a & 0xFF000000) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- the 64x64 layout, placed on the body

	/**
	 * Per texel of the 64x64 player-skin layout: when it turns ({@code order}, 0..1, -1 = not on the model), its vein strength,
	 * whether it is on the second layer, and the texel of the (wide) Hulk texture it becomes.
	 */
	static final class Layout {
		final float[] order = new float[64 * 64];
		final float[] vein = new float[64 * 64];
		final boolean[] overlay = new boolean[64 * 64];
		final boolean[] on = new boolean[64 * 64];
		final int[] target = new int[64 * 64];

		/** Where the green starts: the sternum, on the front of the chest (model px, y down from the neck). */
		private static final float[] STERNUM = { 0.0f, 3.0f, -2.0f };

		Layout(boolean slim) {
			java.util.Arrays.fill(this.order, -1.0f);
			for (int i = 0; i < this.target.length; i++) {
				this.target[i] = i;
			}
			int aw = slim ? 3 : 4;
			// {u, v, w, h, d, originX, originY, originZ, anchor, overlay, isArm}
			// anchor: 0 body (straight from the sternum), 1 head (via the neck), 2 right arm, 3 left arm, 4 right leg, 5 left leg
			box(0, 0, 8, 8, 8, -4, -8, -4, 1, false, false, 4);
			box(32, 0, 8, 8, 8, -4, -8, -4, 1, true, false, 4);
			box(16, 16, 8, 12, 4, -4, 0, -2, 0, false, false, 4);
			box(16, 32, 8, 12, 4, -4, 0, -2, 0, true, false, 4);
			box(40, 16, aw, 12, 4, -4 - aw, 0, -2, 2, false, slim, 4);
			box(40, 32, aw, 12, 4, -4 - aw, 0, -2, 2, true, slim, 4);
			box(32, 48, aw, 12, 4, 4, 0, -2, 3, false, slim, 4);
			box(48, 48, aw, 12, 4, 4, 0, -2, 3, true, slim, 4);
			box(0, 16, 4, 12, 4, -4, 12, -2, 4, false, false, 4);
			box(0, 32, 4, 12, 4, -4, 12, -2, 4, true, false, 4);
			box(16, 48, 4, 12, 4, 0, 12, -2, 5, false, false, 4);
			box(0, 48, 4, 12, 4, 0, 12, -2, 5, true, false, 4);
			normalise();
		}

		/**
		 * One box of the layout. Faces (MC box UV): top / bottom across the top strip, then right side, front, left side, back
		 * down the main strip. {@code wideW} is the width the Hulk texture has for this box (slim arms map onto it).
		 */
		private void box(int u, int v, int w, int h, int d, float ox, float oy, float oz, int anchor, boolean over, boolean slim, int wideW) {
			int tw = slim ? wideW : w;
			// top and bottom
			for (int fz = 0; fz < d; fz++) {
				for (int fx = 0; fx < w; fx++) {
					put(u + d + fx, v + fz, ox + fx + 0.5f, oy, oz + fz + 0.5f, anchor, over,
							u + d + remap(fx, w, tw), v + fz);
					put(u + d + w + fx, v + fz, ox + fx + 0.5f, oy + h, oz + fz + 0.5f, anchor, over,
							u + d + tw + remap(fx, w, tw), v + fz);
				}
			}
			for (int fy = 0; fy < h; fy++) {
				float py = oy + fy + 0.5f;
				int ty = v + d + fy;
				for (int f = 0; f < d; f++) {
					// right side (x = ox), left side (x = ox + w)
					put(u + f, ty, ox, py, oz + d - f - 0.5f, anchor, over, u + f, ty);
					put(u + d + w + f, ty, ox + w, py, oz + f + 0.5f, anchor, over, u + d + tw + f, ty);
				}
				for (int fx = 0; fx < w; fx++) {
					// front (z = oz), back (z = oz + d)
					put(u + d + fx, ty, ox + fx + 0.5f, py, oz, anchor, over, u + d + remap(fx, w, tw), ty);
					put(u + 2 * d + w + fx, ty, ox + w - fx - 0.5f, py, oz + d, anchor, over, u + 2 * d + tw + remap(fx, w, tw), ty);
				}
			}
		}

		private static int remap(int fx, int w, int tw) {
			return w == tw ? fx : Math.min(tw - 1, (int) ((fx + 0.5f) * tw / w));
		}

		private void put(int tx, int ty, float x, float y, float z, int anchor, boolean over, int hx, int hy) {
			if (tx < 0 || tx >= 64 || ty < 0 || ty >= 64) {
				return;
			}
			int i = ty * 64 + tx;
			float dist;
			switch (anchor) {
				case 1 -> dist = len(0, 0, 0, STERNUM) + 0.9f * len(x, y, z, 0, 0, 0);
				case 2 -> dist = len(-4.5f, 1, 0, STERNUM) + len(x, y, z, -4.5f, 1, 0);
				case 3 -> dist = len(4.5f, 1, 0, STERNUM) + len(x, y, z, 4.5f, 1, 0);
				case 4 -> dist = len(-2, 12, 0, STERNUM) + len(x, y, z, -2, 12, 0);
				case 5 -> dist = len(2, 12, 0, STERNUM) + len(x, y, z, 2, 12, 0);
				default -> dist = len(x, y, z, STERNUM[0], STERNUM[1], STERNUM[2]);
			}
			// a ragged front: low-frequency noise pushes whole patches ahead / behind
			float n = fbm(x * 0.28f, y * 0.28f, z * 0.28f);
			this.order[i] = dist + (n - 0.5f) * 9.0f;
			// ridged noise: thin branching lines along the limbs
			float r = 1.0f - Math.abs(2.0f * noise(x * 0.42f + 17.3f, y * 0.21f + 4.1f, z * 0.42f + 9.7f) - 1.0f);
			this.vein[i] = r > VEIN_RIDGE ? (r - VEIN_RIDGE) / (1.0f - VEIN_RIDGE) : 0.0f;
			this.overlay[i] = over;
			this.on[i] = true;
			this.target[i] = (hy & 63) * 64 + (hx & 63);
		}

		private void normalise() {
			float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
			for (int i = 0; i < this.order.length; i++) {
				if (this.on[i]) {
					min = Math.min(min, this.order[i]);
					max = Math.max(max, this.order[i]);
				}
			}
			float span = Math.max(1.0e-3f, max - min);
			for (int i = 0; i < this.order.length; i++) {
				if (this.on[i]) {
					this.order[i] = Math.max(0.0f, Math.min(1.0f, (this.order[i] - min) / span));
				}
			}
		}

		private static float len(float x, float y, float z, float[] p) {
			return len(x, y, z, p[0], p[1], p[2]);
		}

		private static float len(float x, float y, float z, float px, float py, float pz) {
			float dx = x - px, dy = y - py, dz = z - pz;
			return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		/** Two octaves of value noise, 0..1. */
		private static float fbm(float x, float y, float z) {
			return 0.65f * noise(x, y, z) + 0.35f * noise(x * 2.3f + 31.0f, y * 2.3f + 7.0f, z * 2.3f + 13.0f);
		}

		/** Smooth 3D value noise, 0..1, from a fixed hash (the same pattern for everyone). */
		private static float noise(float x, float y, float z) {
			int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
			float fx = x - x0, fy = y - y0, fz = z - z0;
			fx = fx * fx * (3 - 2 * fx);
			fy = fy * fy * (3 - 2 * fy);
			fz = fz * fz * (3 - 2 * fz);
			float c000 = hash(x0, y0, z0), c100 = hash(x0 + 1, y0, z0), c010 = hash(x0, y0 + 1, z0), c110 = hash(x0 + 1, y0 + 1, z0);
			float c001 = hash(x0, y0, z0 + 1), c101 = hash(x0 + 1, y0, z0 + 1), c011 = hash(x0, y0 + 1, z0 + 1),
					c111 = hash(x0 + 1, y0 + 1, z0 + 1);
			float a = c000 + (c100 - c000) * fx, b = c010 + (c110 - c010) * fx;
			float c = c001 + (c101 - c001) * fx, d = c011 + (c111 - c011) * fx;
			float e = a + (b - a) * fy, f = c + (d - c) * fy;
			return e + (f - e) * fz;
		}

		private static float hash(int x, int y, int z) {
			int h = x * 374761393 + y * 668265263 + z * 1442695041;
			h = (h ^ (h >>> 13)) * 1274126177;
			h ^= h >>> 16;
			return (h & 0xFFFFFF) / (float) 0xFFFFFF;
		}
	}
}
