package com.projecthero.mod.client.symbiote;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.SymbioteSuitStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5: the Symbiote suit in the wearer's own colours. The suit art is one greyscale TINT MASK
 * ({@code textures/armor/symbiote_host_tint.png}, made by {@code scratchpad/symbiote/gen_symbiote_suit_v0175.js}): R = shade,
 * G = gloss, B = which colour (body / emblem / eyes), A = coverage. {@link #bake} turns it into a normal texture for a colour
 * triple -- shade 0.5 is the chosen colour exactly, darker shades sink to black, lighter ones lift toward a pale tint of the
 * hue, and the gloss lays a near-white of the hue on top -- so a black suit still has its wet highlights and a white one still
 * has its muscle shading. Any RGB works.
 *
 * <p>Cheap: a texture is baked once per colour triple (128 x 128, a few hundred microseconds) and cached; nothing is regenerated
 * per frame. At most {@link #MAX_SUITS} triples are kept (least recently used dropped, its GPU texture released, together with
 * its suit-up frames in {@link SymbioteSpread}). The Classic colours use the shipped {@code symbiote_host.png} (the same bake,
 * done by the script). {@link #clear} drops everything -- on a resource reload and on leaving a world.
 *
 * <p>The tendrils, spikes and blade the suit grows are tinted the same way from their own (dark) textures ({@link #goo}): only
 * for a Normal host in non-Classic colours, everything else (Black Suit, Agent Venom, Carnage, free Symbiotes, pets) keeps its
 * original look.
 */
public final class SymbioteSuitTint {
	public static final ResourceLocation MASK = ProjectHeroMod.id("textures/armor/symbiote_host_tint.png");
	public static final ResourceLocation CLASSIC = ProjectHeroMod.id("textures/armor/symbiote_host.png");
	/** v0.17.5: the Lethal Protector style -- the skin as painted (Classic colours) and its tint mask (A = 0: keep the skin's colour). */
	public static final ResourceLocation LETHAL = ProjectHeroMod.id("textures/armor/symbiote_host_lethal.png");
	public static final ResourceLocation LETHAL_MASK = ProjectHeroMod.id("textures/armor/symbiote_host_lethal_tint.png");
	static final int MAX_SUITS = 12;
	static final int MAX_GOO = 24;

	private static NativeImage mask;
	private static boolean maskMissing;
	private static NativeImage lethalMask;
	private static NativeImage lethalBase;
	private static boolean lethalMissing;
	private static Boolean lethalAvailable;
	/** colour key -> baked suit texture (access order, so the eldest entry is the least recently used). */
	private static final LinkedHashMap<String, ResourceLocation> SUITS = new LinkedHashMap<>(16, 0.75f, true);
	/** original texture + primary colour -> baked goo texture. */
	private static final LinkedHashMap<String, ResourceLocation> GOO = new LinkedHashMap<>(16, 0.75f, true);
	/** Every texture this class registered -> its pixels, so {@link SymbioteSpread} can build suit-up frames from it. */
	private static final Map<ResourceLocation, NativeImage> IMAGES = new HashMap<>();

	private SymbioteSuitTint() {
	}

	/** The suit texture to draw on {@code wearer} (Classic for anything that is not a player). */
	public static ResourceLocation suit(Entity wearer) {
		return suit(colours(wearer));
	}

	/**
	 * The colours to draw {@code wearer}'s suit in: the synced choice, or -- for the local player inside the colour screen's
	 * live 3D preview -- the colours still being picked.
	 */
	public static SymbioteColours colours(Entity wearer) {
		if (!(wearer instanceof Player p)) {
			return SymbioteColours.CLASSIC;
		}
		SymbioteColours preview = com.projecthero.mod.client.gui.SymbioteColourScreen.previewOverride;
		if (preview != null && p == Minecraft.getInstance().player) {
			return preview;
		}
		return SymbioteSuitColours.get(p);
	}

	/**
	 * v0.17.5: the suit style {@code wearer} is drawn in -- their choice, except that {@link SymbioteSuitStyle#LETHAL_PROTECTOR} needs
	 * its (git-ignored, borrowed-skin) textures: a build without them draws Classic.
	 */
	public static SymbioteSuitStyle styleOf(Entity wearer) {
		return effective(colours(wearer)).style();
	}

	/** {@code colours} with the style swapped to Classic when the Lethal Protector textures are not in this build. */
	public static SymbioteColours effective(SymbioteColours colours) {
		if (colours == null) {
			return SymbioteColours.CLASSIC;
		}
		return colours.style() == SymbioteSuitStyle.LETHAL_PROTECTOR && !lethalAvailable()
				? colours.withStyle(SymbioteSuitStyle.CLASSIC) : colours;
	}

	/**
	 * v0.17.5: are the Lethal Protector textures in this build? (They are made from the user's PlanetMinecraft skin, licence unchecked,
	 * so they are .gitignored -- local builds only, like the Gyro and Sentry comics skins. Re-make: node
	 * scratchpad/symbiote/make_lethal_protector.js.) Looked up once per resource reload.
	 */
	public static boolean lethalAvailable() {
		if (lethalAvailable == null) {
			var rm = Minecraft.getInstance().getResourceManager();
			lethalAvailable = rm.getResource(LETHAL).isPresent() && rm.getResource(LETHAL_MASK).isPresent();
		}
		return lethalAvailable;
	}

	public static ResourceLocation suit(SymbioteColours colours) {
		colours = effective(colours);
		boolean lethal = colours.style() == SymbioteSuitStyle.LETHAL_PROTECTOR;
		if (colours.isClassic()) {
			return CLASSIC;
		}
		if (lethal && colours.hasClassicColours()) {
			return LETHAL; // the skin exactly as painted
		}
		String key = colours.key();
		ResourceLocation cached = SUITS.get(key);
		if (cached != null) {
			return cached;
		}
		NativeImage src = lethal ? lethalMask() : mask();
		NativeImage base = lethal ? lethalBase() : null;
		if (src == null || (lethal && base == null)) {
			return lethal ? LETHAL : CLASSIC;
		}
		NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getPixelRGBA(x, y);
				if (lethal && SymbioteSuitTint.alpha(p) == 0) {
					// the Lethal Protector mask leaves the mouth (lips, throat, teeth, tongue) and the drool in the skin's own colours
					out.setPixelRGBA(x, y, base.getPixelRGBA(x, y));
					continue;
				}
				out.setPixelRGBA(x, y, bakeAbgr(SymbioteSuitTint.red(p), SymbioteSuitTint.green(p), SymbioteSuitTint.blue(p), SymbioteSuitTint.alpha(p), colours));
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_suit/" + key);
		register(id, out);
		SUITS.put(key, id);
		trim(SUITS, MAX_SUITS);
		return id;
	}

	/**
	 * The goo texture for a tendril / spike / blade grown by {@code owner}: {@code original} recoloured to the owner's body
	 * colour when the owner is a Normal host in non-Classic colours, else {@code original} untouched.
	 */
	public static ResourceLocation goo(Entity owner, ResourceLocation original) {
		if (!(owner instanceof Player p) || !Symbiote.isNormalHost(p)) {
			return original;
		}
		SymbioteColours c = colours(p);
		if (c.hasClassicColours()) { // v0.17.5: on either suit style
			return original;
		}
		String key = original.getPath().replaceAll("[^a-z0-9_]", "_") + "_" + String.format("%06x", c.primary());
		ResourceLocation cached = GOO.get(key);
		if (cached != null) {
			return cached;
		}
		NativeImage src = read(original);
		if (src == null) {
			return original;
		}
		NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p0 = src.getPixelRGBA(x, y);
				int a = SymbioteSuitTint.alpha(p0);
				// the shipped goo is near-black with grey highlights: stretch its luminance into the mask's shade / gloss ranges
				float lum = (0.299f * SymbioteSuitTint.red(p0) + 0.587f * SymbioteSuitTint.green(p0) + 0.114f * SymbioteSuitTint.blue(p0)) / 255f;
				int shade = Math.round(clamp(0.27f + lum * 0.95f) * 255f);
				int gloss = Math.round(clamp((lum - 0.22f) * 2.2f) * 255f);
				out.setPixelRGBA(x, y, bakeAbgr(shade, gloss, 0, a, c));
			}
		}
		src.close();
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_goo/" + key);
		register(id, out);
		GOO.put(key, id);
		trim(GOO, MAX_GOO);
		return id;
	}

	/** A copy of the pixels behind a texture this class made (the caller closes it), or empty for any other texture. */
	public static Optional<NativeImage> image(ResourceLocation id) {
		NativeImage img = IMAGES.get(id);
		if (img == null) {
			return Optional.empty();
		}
		NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), true);
		copy.copyFrom(img);
		return Optional.of(copy);
	}

	/** Drops every baked texture (GPU and CPU copies) and the cached mask. Render thread. */
	public static void clear() {
		List<ResourceLocation> ids = new ArrayList<>(IMAGES.keySet());
		for (ResourceLocation id : ids) {
			release(id);
		}
		SUITS.clear();
		GOO.clear();
		if (mask != null) {
			mask.close();
			mask = null;
		}
		maskMissing = false;
		if (lethalMask != null) {
			lethalMask.close();
			lethalMask = null;
		}
		if (lethalBase != null) {
			lethalBase.close();
			lethalBase = null;
		}
		lethalMissing = false;
		lethalAvailable = null;
		SymbioteSuitFx.clear();
	}

	/** Test hook / debug: how many colour triples are baked right now. */
	public static int cachedSuits() {
		return SUITS.size();
	}

	// ------------------------------------------------------------------ NativeImage pixels are packed ABGR

	static int alpha(int abgr) {
		return abgr >>> 24;
	}

	static int red(int abgr) {
		return abgr & 0xFF;
	}

	static int green(int abgr) {
		return (abgr >> 8) & 0xFF;
	}

	static int blue(int abgr) {
		return (abgr >> 16) & 0xFF;
	}

	/** Packs a NativeImage pixel from alpha, blue, green, red (the old {@code NativeImage.combine} order). */
	static int abgr(int a, int b, int g, int r) {
		return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	// ------------------------------------------------------------------ the colour maths (twin of the script's bakePixel)

	/** Bakes one mask texel ({@code shade}, {@code gloss}, {@code channel}, {@code alpha}, each 0..255) to an ABGR NativeImage pixel. */
	static int bakeAbgr(int shade, int gloss, int channel, int alpha, SymbioteColours colours) {
		int rgb = bake(shade, gloss, channel, alpha, colours);
		return SymbioteSuitTint.abgr(alpha, rgb & 0xFF, (rgb >> 8) & 0xFF, (rgb >> 16) & 0xFF);
	}

	/** One mask texel to {@code 0xRRGGBB} (0 when transparent). Pure maths. */
	public static int bake(int shade, int gloss, int channel, int alpha, SymbioteColours colours) {
		if (alpha == 0) {
			return 0;
		}
		int base = channel >= 192 ? colours.eyes() : channel >= 64 ? colours.secondary() : colours.primary();
		float[] c = { ((base >> 16) & 0xFF) / 255f, ((base >> 8) & 0xFF) / 255f, (base & 0xFF) / 255f };
		float s = shade / 255f;
		float g = gloss / 255f;
		float m = Math.max(c[0], Math.max(c[1], c[2]));
		float[] hue = m < 0.03f ? new float[] { 0.82f, 0.86f, 1.0f } : new float[] { c[0] / m, c[1] / m, c[2] / m };
		float[] o = new float[3];
		for (int i = 0; i < 3; i++) {
			if (s <= 0.5f) {
				o[i] = c[i] * (s / 0.5f);
			} else {
				float t = (s - 0.5f) / 0.5f;
				float lift = (0.5f + 0.5f * hue[i]) * 0.86f;
				o[i] = c[i] + (Math.max(lift, c[i]) - c[i]) * t;
			}
			float spec = 0.72f + 0.28f * hue[i];
			o[i] = o[i] + (Math.max(spec, o[i]) - o[i]) * g;
		}
		return Math.round(clamp(o[0]) * 255f) << 16 | Math.round(clamp(o[1]) * 255f) << 8 | Math.round(clamp(o[2]) * 255f);
	}

	// ------------------------------------------------------------------ plumbing

	private static NativeImage mask() {
		if (mask == null && !maskMissing) {
			mask = read(MASK);
			maskMissing = mask == null;
		}
		return mask;
	}

	private static NativeImage lethalMask() {
		loadLethal();
		return lethalMask;
	}

	private static NativeImage lethalBase() {
		loadLethal();
		return lethalBase;
	}

	private static void loadLethal() {
		if ((lethalMask == null || lethalBase == null) && !lethalMissing) {
			if (lethalMask == null) {
				lethalMask = read(LETHAL_MASK);
			}
			if (lethalBase == null) {
				lethalBase = read(LETHAL);
			}
			lethalMissing = lethalMask == null || lethalBase == null;
		}
	}

	static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {}: {}", id, e.toString());
			return null;
		}
	}

	private static void register(ResourceLocation id, NativeImage img) {
		NativeImage keep = new NativeImage(img.getWidth(), img.getHeight(), true);
		keep.copyFrom(img);
		IMAGES.put(id, keep);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
	}

	private static void release(ResourceLocation id) {
		NativeImage img = IMAGES.remove(id);
		if (img != null) {
			img.close();
		}
		SymbioteSpread.forget(id);
		Minecraft.getInstance().getTextureManager().release(id);
	}

	private static void trim(LinkedHashMap<String, ResourceLocation> map, int max) {
		Iterator<Map.Entry<String, ResourceLocation>> it = map.entrySet().iterator();
		while (map.size() > max && it.hasNext()) {
			ResourceLocation id = it.next().getValue();
			it.remove();
			release(id);
		}
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
