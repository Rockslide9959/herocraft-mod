package com.projecthero.mod.client.symbiote.monster;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.symbiote.SymbioteSuitTint;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteTintMath;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.5 Symbiote Monster form: the monster's texture in its host's colours -- the tint mask {@code symbiote_monster_tint.png} (the
 * suit's format: R shade, G gloss, B channel body / mouth / emblem / eyes) baked through {@link SymbioteTintMath#bake}, exactly like
 * {@code SymbioteSuitTint} bakes the suit, with the teeth from {@code symbiote_monster_keep.png} (drawn as painted on every colour).
 * One 1024 x 512 {@link DynamicTexture} per colour scheme, cached (LRU, 6); released on a resource reload and on leaving a world.
 * Client-only, no world / entity references.
 */
public final class SymbioteMonsterTint {
	public static final ResourceLocation MASK = ProjectHeroMod.id("textures/entity/symbiote_monster_tint.png");
	/** Held back (.gitignored, made by make_lethal_protector.js): the same mask wearing the Lethal Protector suit's spider. */
	public static final ResourceLocation MASK_LETHAL = ProjectHeroMod.id("textures/entity/symbiote_monster_tint_lethal.png");
	public static final ResourceLocation KEEP =ProjectHeroMod.id("textures/entity/symbiote_monster_keep.png");
	public static final ResourceLocation CLASSIC = ProjectHeroMod.id("textures/entity/symbiote_monster.png");
	private static final int MAX = 6;
	private static final LinkedHashMap<String, ResourceLocation> BAKED = new LinkedHashMap<>(8, 0.75f, true);
	private static NativeImage mask;
	private static NativeImage keep;
	private static boolean missing;

	private SymbioteMonsterTint() {
	}

	public static ResourceLocation texture(Entity owner) {
		return texture(SymbioteSuitTint.colours(owner));
	}

	public static ResourceLocation texture(SymbioteColours colours) {
		if (colours == null) {
			colours = SymbioteColours.CLASSIC;
		}
		String key = colours.key();
		ResourceLocation cached = BAKED.get(key);
		if (cached != null) {
			return cached;
		}
		if (mask == null && !missing) {
			// v0.17.5: the monster wears the same spider as the suit the player sees -- the held-back Lethal Protector mask when that
			// suit's (borrowed, unlicensed) art is in this build, else the committed mask with the Classic suit's own spider
			if (SymbioteSuitTint.artStyle() == com.projecthero.mod.symbiote.SymbioteSuitStyle.LETHAL_PROTECTOR) {
				mask = read(MASK_LETHAL);
			}
			if (mask == null) {
				mask = read(MASK);
			}
			keep = read(KEEP);
			missing = mask == null || keep == null;
		}
		if (missing) {
			return CLASSIC;
		}
		NativeImage out = new NativeImage(mask.getWidth(), mask.getHeight(), true);
		for (int y = 0; y < mask.getHeight(); y++) {
			for (int x = 0; x < mask.getWidth(); x++) {
				int k = keep.getPixelRGBA(x, y);
				if (((k >>> 24) & 0xFF) != 0) {
					out.setPixelRGBA(x, y, k);
					continue;
				}
				int p = mask.getPixelRGBA(x, y); // ABGR
				int a = (p >>> 24) & 0xFF;
				if (a == 0) {
					out.setPixelRGBA(x, y, 0);
					continue;
				}
				int rgb = SymbioteTintMath.bake(p & 0xFF, (p >> 8) & 0xFF, (p >> 16) & 0xFF, a, colours);
				out.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF);
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_monster/" + key);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
		BAKED.put(key, id);
		Iterator<Map.Entry<String, ResourceLocation>> it = BAKED.entrySet().iterator();
		while (BAKED.size() > MAX && it.hasNext()) {
			ResourceLocation old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	private static final LinkedHashMap<Integer, ResourceLocation> GLOW = new LinkedHashMap<>(8, 0.75f, true);

	/**
	 * The eyes' additive glow for an eye colour ({@code SymbioteTintMath.eyeGlowStrength}: 40% for bright eyes, none for dark ones, so
	 * the lenses read at night without ever brightening dark eyes): black everywhere but the lens texels. Null when there is no glow.
	 */
	public static ResourceLocation glow(Entity owner) {
		SymbioteColours colours = SymbioteSuitTint.colours(owner);
		int eyes = colours.eyes();
		float k = SymbioteTintMath.eyeGlowStrength(eyes);
		if (k <= 0.01f) {
			return null;
		}
		ResourceLocation cached = GLOW.get(eyes);
		if (cached != null) {
			return cached;
		}
		if (mask == null && !missing) {
			texture(colours);
		}
		if (missing || mask == null) {
			return null;
		}
		int r = Math.round(((eyes >> 16) & 0xFF) * k), g = Math.round(((eyes >> 8) & 0xFF) * k), b = Math.round((eyes & 0xFF) * k);
		NativeImage out = new NativeImage(mask.getWidth(), mask.getHeight(), true);
		for (int y = 0; y < mask.getHeight(); y++) {
			for (int x = 0; x < mask.getWidth(); x++) {
				int p = mask.getPixelRGBA(x, y);
				boolean eye = ((p >>> 24) & 0xFF) != 0 && SymbioteTintMath.channelOf((p >> 16) & 0xFF) == SymbioteTintMath.EYES;
				out.setPixelRGBA(x, y, eye ? 0xFF000000 | b << 16 | g << 8 | r : 0xFF000000);
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_monster_glow/" + String.format("%06x", eyes & 0xFFFFFF));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
		GLOW.put(eyes, id);
		Iterator<Map.Entry<Integer, ResourceLocation>> it = GLOW.entrySet().iterator();
		while (GLOW.size() > MAX && it.hasNext()) {
			ResourceLocation old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	// ------------------------------------------------------------------------------------------------ the cocoon (polish pass)

	private static final int COCOON_SIZE = 128;
	private static final LinkedHashMap<String, ResourceLocation> COCOON = new LinkedHashMap<>(8, 0.75f, true);
	private static int[] cocoonMask;
	private static ResourceLocation cocoonGloss, cocoonVeins, cocoonGlow;

	/**
	 * The cocoon's living surface in {@code owner}'s colours: a tileable 128 x 128 tint mask made here (no asset) -- wavy sinew fibres and
	 * lumps in the body colour (shade a little under the colour, so the gloss and lift read as wet highlights on any colour), sunken veins
	 * in the emblem colour, wet streaks of gloss -- baked through {@link SymbioteTintMath#bake} like the suit, every texel opaque (nothing
	 * foreign shows through). Used for the shell, every strand, the pool and the burst / melt goo.
	 */
	public static ResourceLocation cocoon(Entity owner) {
		SymbioteColours colours = SymbioteSuitTint.colours(owner);
		if (colours == null) {
			colours = SymbioteColours.CLASSIC;
		}
		String key = colours.key();
		ResourceLocation cached = COCOON.get(key);
		if (cached != null) {
			return cached;
		}
		int[] mask = cocoonMask();
		NativeImage out = new NativeImage(COCOON_SIZE, COCOON_SIZE, true);
		for (int i = 0; i < mask.length; i++) {
			int m = mask[i];
			int rgb = SymbioteTintMath.bake(m & 0xFF, (m >> 8) & 0xFF, (m >> 16) & 0xFF, 255, colours);
			out.setPixelRGBA(i % COCOON_SIZE, i / COCOON_SIZE, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF);
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_cocoon/" + key);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
		COCOON.put(key, id);
		Iterator<Map.Entry<String, ResourceLocation>> it = COCOON.entrySet().iterator();
		while (COCOON.size() > MAX && it.hasNext()) {
			ResourceLocation old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	/** The cocoon's wet streaks as a grey additive layer (drawn over the shell, sliding slowly, tinted by the vertex colour). */
	public static ResourceLocation cocoonGloss() {
		if (cocoonGloss == null) {
			cocoonGloss = additive("gloss", (u, v) -> {
				double streak = Math.pow(Math.max(0, Math.sin(2 * u + 0.9 * Math.sin(v + 0.6) + 0.4 * Math.sin(2 * v))), 6);
				double band = 0.55 + 0.45 * Math.sin(v + 2 * u);
				double crest = Math.pow(0.5 + 0.5 * fibre(u, v), 6);
				return Math.min(1, streak * band * 0.9 + crest * 0.35);
			});
		}
		return cocoonGloss;
	}

	/** The cocoon's veins as a white additive layer (the heartbeat flushes them). */
	public static ResourceLocation cocoonVeins() {
		if (cocoonVeins == null) {
			cocoonVeins = additive("veins", (u, v) -> Math.pow(1 - Math.min(1, vein(u, v) / 0.11), 2));
		}
		return cocoonVeins;
	}

	/** A soft mottled glow (the light inside the cocoon, through its tears). */
	public static ResourceLocation cocoonGlow() {
		if (cocoonGlow == null) {
			cocoonGlow = additive("glow", (u, v) -> 0.6 + 0.2 * Math.sin(2 * u + 3 * v) + 0.2 * Math.sin(5 * u - v + 1));
		}
		return cocoonGlow;
	}

	private interface Field {
		double at(double u, double v);
	}

	private static ResourceLocation additive(String name, Field f) {
		NativeImage out = new NativeImage(COCOON_SIZE, COCOON_SIZE, true);
		for (int y = 0; y < COCOON_SIZE; y++) {
			for (int x = 0; x < COCOON_SIZE; x++) {
				int g = (int) Math.round(Math.max(0, Math.min(1, f.at(Math.PI * 2 * x / COCOON_SIZE, Math.PI * 2 * y / COCOON_SIZE))) * 255);
				out.setPixelRGBA(x, y, 0xFF000000 | g << 16 | g << 8 | g);
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_cocoon_" + name);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
		return id;
	}

	/** Wavy sinew fibres running up the cocoon (-1..1), tileable (integer frequencies in u, v = 0..2 pi). */
	private static double fibre(double u, double v) {
		return Math.sin(3 * u + 1.3 * Math.sin(v + 0.7) + 0.6 * Math.sin(2 * u - v));
	}

	/** Distance-like value, small on a vein (two branching families of sinuous lines). */
	private static double vein(double u, double v) {
		double a = Math.abs(Math.sin(2 * u + 1.9 * Math.sin(2 * v + 1.3) + 0.6 * Math.sin(5 * v)));
		double b = Math.abs(Math.sin(3 * u - 2 * v + 1.4 * Math.sin(4 * u + 0.5)));
		return Math.min(a, b * 1.3);
	}

	/** The cocoon tint mask, packed {@code shade | gloss << 8 | channel << 16}. */
	private static int[] cocoonMask() {
		if (cocoonMask != null) {
			return cocoonMask;
		}
		int[] m = new int[COCOON_SIZE * COCOON_SIZE];
		for (int y = 0; y < COCOON_SIZE; y++) {
			for (int x = 0; x < COCOON_SIZE; x++) {
				double u = Math.PI * 2 * x / COCOON_SIZE, v = Math.PI * 2 * y / COCOON_SIZE;
				double r = 0.5 + 0.5 * fibre(u, v);
				double lumps = 0.5 + 0.25 * Math.sin(3 * u + 2 * v) + 0.25 * Math.sin(5 * u - 3 * v + 1);
				double shade = 0.3 + 0.09 * r + 0.14 * lumps;
				double gloss = Math.max(0, (r - 0.72) * 2.4) * (0.55 + 0.45 * Math.sin(v + 2 * u));
				gloss += Math.pow(Math.max(0, Math.sin(2 * u + 0.9 * Math.sin(v + 0.6) + 0.4 * Math.sin(2 * v))), 10) * 0.4;
				if (r < 0.12) {
					shade -= 0.08; // the creases between fibres
				}
				int channel = 0;
				double vd = vein(u, v);
				if (vd < 0.05) { // a vein: the emblem colour, darkened, a glint along it
					channel = 128;
					shade = 0.26 + 0.1 * (1 - vd / 0.05);
					gloss = 0.12;
				} else if (vd < 0.1) { // the groove each vein sits in
					shade -= 0.07;
					gloss *= 0.4;
				}
				int sh = (int) Math.round(Math.max(0, Math.min(1, shade)) * 255);
				int gl = (int) Math.round(Math.max(0, Math.min(1, gloss)) * 255);
				m[y * COCOON_SIZE + x] = sh | gl << 8 | channel << 16;
			}
		}
		cocoonMask = m;
		return m;
	}

	/** Resource reload / disconnect: drop every baked texture and the cached mask pixels. */
	public static void clear() {
		for (ResourceLocation id : BAKED.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		BAKED.clear();
		for (ResourceLocation id : GLOW.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		GLOW.clear();
		for (ResourceLocation id : COCOON.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		COCOON.clear();
		if (cocoonGloss != null) {
			Minecraft.getInstance().getTextureManager().release(cocoonGloss);
		}
		if (cocoonVeins != null) {
			Minecraft.getInstance().getTextureManager().release(cocoonVeins);
		}
		if (cocoonGlow != null) {
			Minecraft.getInstance().getTextureManager().release(cocoonGlow);
		}
		cocoonGlow = null;
		cocoonGloss = null;
		cocoonVeins = null;
		if (mask != null) {
			mask.close();
		}
		if (keep != null) {
			keep.close();
		}
		mask = null;
		keep = null;
		missing = false;
	}

	private static NativeImage read(ResourceLocation id) {
		var res = Minecraft.getInstance().getResourceManager().getResource(id);
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
}
