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
	public static final ResourceLocation KEEP = ProjectHeroMod.id("textures/entity/symbiote_monster_keep.png");
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
			mask = read(MASK);
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

	/** Resource reload / disconnect: drop every baked texture and the cached mask pixels. */
	public static void clear() {
		for (ResourceLocation id : BAKED.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		BAKED.clear();
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
