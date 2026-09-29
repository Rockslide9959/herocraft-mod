package com.projecthero.mod.client.maxsteel;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteelMode;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: the Max Steel suit materialises pixel by pixel -- a real per-texel nanotech reveal, not whole bones
 * popping in. Every suit texture ships with an <b>order map</b> ({@code textures/armor/max_steel_order/<set>.png},
 * written by {@code scratchpad/gen_maxsteel_v0142.js}): each texel's red channel says when it forms, measured
 * outward from the T.U.R.B.O. core in the chest, with the armour shell and the form's extra plates lagging behind
 * the undersuit. Three effects are all the same composite:
 * <ul>
 *   <li><b>Armour up</b> ({@link #reveal}): nothing -&gt; the suit. The pilot's own skin shows wherever the
 *       nanites have not reached yet.</li>
 *   <li><b>Power down</b>: the same, run backwards -- the suit draws back into the core from the hands, feet and
 *       head first.</li>
 *   <li><b>Mode swap</b> ({@link #swap}): the old form -&gt; the new form, the new one's texels (and its extra
 *       armour pieces) rematerialising over the old ones from the chest out.</li>
 * </ul>
 * The texels on the leading edge are drawn in the form's glow colour, so a bright band of nanites sweeps over the
 * body. Each viewer composites into one small {@link DynamicTexture} per animating player (128x128, re-uploaded
 * only when the edge actually moves), so nothing piles up however many players transform.
 */
public final class MaxSteelNano {
	/** The mode swap runs this long (ticks); the server stamps its start in {@link MaxSteelFx#swapStart()}. */
	public static final int SWAP_TICKS = 14;
	/** Width of the glowing leading edge, in order units (0..1). */
	private static final float EDGE = 0.07f;

	private static final Map<ResourceLocation, NativeImage> IMAGES = new HashMap<>();
	private static final Map<ResourceLocation, int[]> ORDERS = new HashMap<>();
	private static final Map<UUID, Slot> SLOTS = new HashMap<>();

	private static final class Slot {
		final ResourceLocation id;
		final DynamicTexture texture;
		String key = "";

		Slot(UUID player) {
			this.texture = new DynamicTexture(128, 128, true);
			this.id = ProjectHeroMod.id("dynamic/max_steel_nano/" + player.toString().replace("-", ""));
			Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
		}
	}

	private MaxSteelNano() {
	}

	// ---------------------------------------------------------------- clocks

	private static MaxSteelState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
	}

	private static MaxSteelFx fx(Player player) {
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		return fx == null ? MaxSteelFx.EMPTY : fx;
	}

	/** How far on the suit is (0 bare .. 1 fully formed), smoothed with the partial tick. 1 when not transforming. */
	public static float suitProgress(Player player, float partialTick) {
		MaxSteelState s = state(player);
		if (s == null || s.transformDir == MaxSteelState.DIR_IDLE) {
			return 1.0f;
		}
		float raw = (player.level().getGameTime() - s.transformStartTick + partialTick) / Math.max(1, s.transformDurationTicks);
		raw = Math.max(0.0f, Math.min(1.0f, raw));
		return s.transformDir == MaxSteelState.DIR_SUITING_DOWN ? 1.0f - raw : raw;
	}

	/** How far through the current mode swap (0..1), or 1 when no swap is running. */
	public static float swapProgress(Player player, float partialTick) {
		MaxSteelFx f = fx(player);
		if (f.swapFrom() < 0) {
			return 1.0f;
		}
		float t = (player.level().getGameTime() - f.swapStart() + partialTick) / SWAP_TICKS;
		return t >= 1.0f || t < 0.0f ? 1.0f : t;
	}

	/** The form the suit is swapping away from, while a swap runs; else null. */
	public static MaxSteelMode swapFrom(Player player, float partialTick) {
		MaxSteelFx f = fx(player);
		return f.swapFrom() >= 0 && swapProgress(player, partialTick) < 1.0f ? MaxSteelMode.byOrdinal(f.swapFrom()) : null;
	}

	/**
	 * True while parts of the pilot's own body can show through the suit -- the armour-up / power-down reveal, or a
	 * swap out of Turbo Stealth (the suit forming out of nothing again). {@code PlayerModelMixin} keeps the skin's
	 * second layer on while this holds.
	 */
	public static boolean skinShows(Player player) {
		MaxSteelState s = state(player);
		if (s == null) {
			return false;
		}
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			return true;
		}
		return swapFrom(player, 0.0f) == MaxSteelMode.STEALTH;
	}

	public static String setIdFor(MaxSteelMode mode) {
		if (mode.isSpecialised()) {
			return "max_steel_" + mode.lower();
		}
		return mode == MaxSteelMode.CANNON ? "max_steel_cannon" : "max_steel";
	}

	// ---------------------------------------------------------------- the texture to draw

	/**
	 * The texture a Max Steel suit piece should be drawn with this frame: {@code texture} itself when nothing is
	 * animating, else this player's composite.
	 */
	public static ResourceLocation texture(Player player, ResourceLocation texture, float partialTick) {
		float up = suitProgress(player, partialTick);
		if (up < 0.999f) {
			return composite(player, null, texture, up, texture);
		}
		MaxSteelMode from = swapFrom(player, partialTick);
		if (from != null) {
			ResourceLocation fromTex = ProjectHeroMod.id("textures/armor/" + setIdFor(from) + ".png");
			if (!fromTex.equals(texture)) {
				// leaving Stealth there is no old suit to replace -- it forms out of nothing
				return composite(player, from == MaxSteelMode.STEALTH ? null : fromTex, texture,
						swapProgress(player, partialTick), texture);
			}
		}
		return texture;
	}

	private static ResourceLocation composite(Player player, ResourceLocation from, ResourceLocation to, float progress,
			ResourceLocation fallback) {
		NativeImage toImg = image(to);
		int[] order = order(to);
		NativeImage fromImg = from != null ? image(from) : null;
		if (toImg == null || order == null || toImg.getWidth() != 128 || toImg.getHeight() != 128
				|| (fromImg != null && (fromImg.getWidth() != 128 || fromImg.getHeight() != 128))) {
			return fallback;
		}
		// 96 steps is finer than the order map's 256 levels need at 20 tps, and keeps re-uploads rare
		int step = Math.max(0, Math.min(96, Math.round(progress * 96)));
		String key = (from == null ? "-" : from.getPath()) + ">" + to.getPath() + "@" + step;
		Slot slot = SLOTS.computeIfAbsent(player.getUUID(), Slot::new);
		if (!key.equals(slot.key)) {
			slot.key = key;
			paint(slot.texture.getPixels(), fromImg, toImg, order, step / 96.0f, edgeColour(to));
			slot.texture.upload();
		}
		return slot.id;
	}

	private static void paint(NativeImage out, NativeImage from, NativeImage to, int[] order, float p, int edgeAbgr) {
		if (out == null) {
			return;
		}
		// order runs 0..1; a texel is "to" once p passes it, glowing while it is within EDGE of the front
		float front = p * (1.0f + EDGE);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				int i = y * 128 + x;
				int o = order[i];
				int px;
				if (o < 0) {
					px = to.getPixelRGBA(x, y); // not on the model: irrelevant, copy through
				} else {
					float t = o / 255.0f;
					if (t <= front - EDGE) {
						px = to.getPixelRGBA(x, y);
					} else if (t <= front) {
						int base = to.getPixelRGBA(x, y);
						px = ((base >>> 24) & 0xFF) == 0 ? 0 : edgeAbgr;
					} else {
						px = from != null ? from.getPixelRGBA(x, y) : 0;
					}
				}
				out.setPixelRGBA(x, y, px);
			}
		}
	}

	/** The nanite front colour (NativeImage ABGR) -- the same cyan for every form. */
	private static int edgeColour(ResourceLocation to) {
		String p = to.getPath();
		int rgb = 0x8FFFF8; // v0.14.2: every form keeps the suit's cyan
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return 0xFF000000 | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- resources

	private static NativeImage image(ResourceLocation id) {
		return IMAGES.computeIfAbsent(id, MaxSteelNano::read);
	}

	private static int[] order(ResourceLocation texture) {
		return ORDERS.computeIfAbsent(texture, t -> {
			String path = t.getPath(); // textures/armor/max_steel_x.png
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(t.getNamespace(),
					path.replace("textures/armor/", "textures/armor/max_steel_order/"));
			NativeImage img = read(id);
			if (img == null || img.getWidth() != 128 || img.getHeight() != 128) {
				return null;
			}
			int[] out = new int[128 * 128];
			for (int y = 0; y < 128; y++) {
				for (int x = 0; x < 128; x++) {
					int abgr = img.getPixelRGBA(x, y);
					out[y * 128 + x] = ((abgr >>> 24) & 0xFF) == 0 ? -1 : abgr & 0xFF;
				}
			}
			img.close();
			return out;
		});
	}

	/** Reads a PNG from the resource manager; public so the arm-cannon overlay can reuse it. */
	public static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {} for the Max Steel nanotech reveal: {}", id, e.toString());
			return null;
		}
	}

	/** Leaving a world / a resource reload: free every composite and cached image. */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Slot slot : SLOTS.values()) {
			mc.getTextureManager().release(slot.id);
		}
		SLOTS.clear();
		IMAGES.values().forEach(img -> {
			if (img != null) {
				img.close();
			}
		});
		IMAGES.clear();
		ORDERS.clear();
	}
}
