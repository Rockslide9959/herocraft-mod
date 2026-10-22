package com.projecthero.mod.client.titanshifter;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.7 (user, Titan revamp) -- the per-texel build, regrowth and dissolve of a Titan's atlas, the technique of the
 * Iron Man gantry ({@code IronManGantryBuild}): the frame is a copy of the atlas with only the texels that exist at that
 * moment. Each Titan that is mid-change gets its own {@link DynamicTexture}; because a build only ever adds texels and a
 * dissolve only ever removes them (in the order baked into {@link TitanTexels}), a frame is updated in place by walking
 * a cursor along the sorted texels instead of being rebuilt. Frames nobody drew for two seconds are released.
 */
public final class TitanSkinFrames {
	private static final int MODE_BUILD = 1, MODE_DISSOLVE = 2, MODE_REGROW = 3, MODE_EMERGENCY = 4;
	private static final Map<String, NativeImage> SOURCES = new HashMap<>();
	private static final Map<Integer, Frame> FRAMES = new HashMap<>();
	private static long lastSweep;
	private static int made;

	private static final class Frame {
		NativeImage img;
		DynamicTexture tex;
		ResourceLocation id;
		int mode;
		String srcKey;
		int cursor;
		long used;
		long sig = Long.MIN_VALUE;
	}

	private TitanSkinFrames() {
	}

	/** The atlas {@code id} as an image (washed pale for the Emergency Titan), cached; null if it cannot be read. */
	public static NativeImage source(ResourceLocation id, boolean pale) {
		String key = id + (pale ? "|pale" : "");
		if (SOURCES.containsKey(key)) {
			return SOURCES.get(key);
		}
		NativeImage img = null;
		try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
			img = NativeImage.read(in);
			if (pale) {
				PaleTextures.wash(img);
			}
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read the Titan atlas {}: {}", id, e.toString());
		}
		SOURCES.put(key, img);
		return img;
	}

	/** The transformation frame at build progress {@code p} (0..1): skeleton, then muscle, then skin. */
	public static ResourceLocation build(Entity e, TitanTexels tx, ResourceLocation src, boolean pale, float p) {
		NativeImage source = source(src, pale);
		if (source == null || tx == null) {
			return src;
		}
		p = (float) Math.floor(p * 400.0f) / 400.0f;
		Frame f = frame(e, source, MODE_BUILD, src + "|" + pale);
		int target = TitanTexels.upper(tx.buildSorted, p);
		if (target < f.cursor) {
			reset(f, source, MODE_BUILD);
		}
		boolean changed = f.cursor == 0 && f.sig == Long.MIN_VALUE;
		for (int k = f.cursor; k < target; k++) {
			int idx = tx.index[tx.byBuild[k]];
			int x = idx % tx.w, y = idx / tx.w;
			f.img.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
			changed = true;
		}
		f.cursor = target;
		f.sig = 0;
		if (changed) {
			f.tex.upload();
		}
		return f.id;
	}

	/** The corpse frame at dissolve progress {@code t} (0..1): skin, then muscle, then skeleton leave. */
	public static ResourceLocation dissolve(Entity e, TitanTexels tx, ResourceLocation src, boolean pale, float t) {
		NativeImage source = source(src, pale);
		if (source == null || tx == null) {
			return src;
		}
		t = (float) Math.floor(t * 3000.0f) / 3000.0f;
		Frame f = frame(e, source, MODE_DISSOLVE, src + "|" + pale);
		int target = TitanTexels.upper(tx.dissolveSorted, t);
		if (target < f.cursor) {
			reset(f, source, MODE_DISSOLVE);
		}
		boolean changed = false;
		for (int k = f.cursor; k < target; k++) {
			int idx = tx.index[tx.byDissolve[k]];
			f.img.setPixelRGBA(idx % tx.w, idx / tx.w, 0);
			changed = true;
		}
		f.cursor = target;
		if (changed) {
			f.tex.upload();
		}
		return f.id;
	}

	/**
	 * A Titan regrowing limbs: {@code progress[slot]} (slot = limb bit index) is 0..1 while that limb regrows, anything
	 * else (NaN or >= 1) for a whole limb. The limb's texels come back in build order from its own first bone.
	 */
	public static ResourceLocation regrow(Entity e, TitanTexels tx, ResourceLocation src, boolean pale, float[] progress) {
		NativeImage source = source(src, pale);
		if (source == null || tx == null) {
			return src;
		}
		long sig = 0;
		float[] cut = new float[4];
		for (int s = 0; s < 4; s++) {
			float p = progress[s];
			boolean active = !Float.isNaN(p) && p < 1.0f;
			int q = active ? (int) (Math.max(0.0f, p) * 150.0f) : 255;
			sig = sig * 256 + q;
			cut[s] = active ? tx.limbBuildMin[s] + (1.0f - tx.limbBuildMin[s]) * Math.max(0.0f, p) : Float.NaN;
		}
		Frame f = frame(e, source, MODE_REGROW, src + "|" + pale);
		if (f.sig != sig) {
			f.img.copyFrom(source);
			for (int k = 0; k < tx.count; k++) {
				int limb = tx.limb[k];
				if (limb == 0) {
					continue;
				}
				float c = cut[TitanTexels.limbSlot(limb)];
				if (!Float.isNaN(c) && tx.build[k] > c) {
					int idx = tx.index[k];
					f.img.setPixelRGBA(idx % tx.w, idx / tx.w, 0);
				}
			}
			f.sig = sig;
			f.tex.upload();
		}
		return f.id;
	}

	/**
	 * v0.16.7 (user, Titan fixes): the Emergency Titan's frame at build progress {@code p} (1 = the finished, unstable
	 * body): its own timetable ({@link TitanTexels.Emergency}) with the skeleton flickering, the flesh blotchy and the
	 * skin washed pale but never complete -- the patches that never form show the flesh under them, half raw, half pale.
	 * Flicker makes the frame non-monotonic, so it is redrawn whole whenever the (quantised) progress moves.
	 */
	public static ResourceLocation emergency(Entity e, TitanTexels tx, ResourceLocation src, float p) {
		NativeImage pale = source(src, true);
		NativeImage raw = source(src, false);
		if (pale == null || raw == null || tx == null) {
			return src;
		}
		TitanTexels.Emergency em = tx.emergency();
		p = Math.min(1.0f, (float) Math.floor(p * 240.0f) / 240.0f);
		long sig = (long) (p * 240.0f);
		Frame f = frame(e, pale, MODE_EMERGENCY, src + "|em");
		if (f.sig != sig) {
			f.img.fillRect(0, 0, f.img.getWidth(), f.img.getHeight(), 0);
			for (int k = 0; k < tx.count; k++) {
				int layer = tx.layer[k];
				if (!com.projecthero.mod.titanshifter.TitanEmergencyBuild.visible(layer, em.appear[k], em.region[k], em.jitter[k], p)) {
					continue;
				}
				int idx = tx.index[k];
				int x = idx % tx.w, y = idx / tx.w;
				f.img.setPixelRGBA(x, y, layer == 1 ? mix(raw.getPixelRGBA(x, y), pale.getPixelRGBA(x, y)) : pale.getPixelRGBA(x, y));
			}
			f.sig = sig;
			f.tex.upload();
		}
		return f.id;
	}

	/** Half-way between two ABGR colours (alpha of the first). */
	private static int mix(int a, int b) {
		int r = ((a & 0xFF) + (b & 0xFF)) / 2;
		int g = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2;
		int bl = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2;
		return (a & 0xFF000000) | bl << 16 | g << 8 | r;
	}

	private static Frame frame(Entity e, NativeImage source, int mode, String srcKey) {
		long now = e.level().getGameTime();
		sweep(now);
		Frame f = FRAMES.get(e.getId());
		if (f != null && (f.img.getWidth() != source.getWidth() || f.img.getHeight() != source.getHeight())) {
			release(f);
			FRAMES.remove(e.getId());
			f = null;
		}
		if (f == null) {
			f = new Frame();
			f.img = new NativeImage(source.getWidth(), source.getHeight(), true);
			f.tex = new DynamicTexture(f.img);
			f.id = ProjectHeroMod.id("dynamic/titan_frame/" + (made++));
			Minecraft.getInstance().getTextureManager().register(f.id, f.tex);
			f.mode = -1;
			FRAMES.put(e.getId(), f);
		}
		f.used = now;
		if (f.mode != mode || !srcKey.equals(f.srcKey)) {
			f.srcKey = srcKey;
			reset(f, source, mode);
		}
		return f;
	}

	private static void reset(Frame f, NativeImage source, int mode) {
		f.mode = mode;
		f.cursor = 0;
		f.sig = Long.MIN_VALUE;
		if (mode == MODE_BUILD) {
			f.img.fillRect(0, 0, f.img.getWidth(), f.img.getHeight(), 0);
		} else {
			f.img.copyFrom(source);
		}
		f.tex.upload();
	}

	/** True if {@code e} still has a frame (it was mid-change recently). */
	public static boolean has(Entity e) {
		return FRAMES.containsKey(e.getId());
	}

	/** Drops {@code e}'s frame now (its change is over). */
	public static void drop(Entity e) {
		Frame f = FRAMES.remove(e.getId());
		if (f != null) {
			release(f);
		}
	}

	private static void sweep(long now) {
		if (Math.abs(now - lastSweep) < 20) {
			return;
		}
		lastSweep = now;
		for (Iterator<Frame> it = FRAMES.values().iterator(); it.hasNext();) {
			Frame f = it.next();
			if (Math.abs(now - f.used) > 40) {
				release(f);
				it.remove();
			}
		}
	}

	private static void release(Frame f) {
		Minecraft.getInstance().getTextureManager().release(f.id);
	}

	/** Leaving a world / reloading resources: every frame and cached atlas goes. */
	public static void clear() {
		FRAMES.values().forEach(TitanSkinFrames::release);
		FRAMES.clear();
		SOURCES.values().forEach(img -> {
			if (img != null) {
				img.close();
			}
		});
		SOURCES.clear();
		TitanTexels.clear();
	}
}
