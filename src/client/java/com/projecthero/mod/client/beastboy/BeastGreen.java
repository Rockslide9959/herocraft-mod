package com.projecthero.mod.client.beastboy;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.17.3 Beast Boy: green copies of vanilla mob textures, made at runtime from the game's own (so no Mojang art is shipped):
 * every pixel's brightness becomes Beast Boy green, alpha kept. Used for the tinted vanilla-model forms and the green
 * (Sakutia) ocelot. Built once per texture and kept until the client leaves the world ({@link #clear}).
 */
public final class BeastGreen {
	private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

	private BeastGreen() {
	}

	/** The green copy of {@code src} (or {@code src} itself if it can't be read). Render thread only. */
	public static ResourceLocation of(ResourceLocation src) {
		ResourceLocation done = CACHE.get(src);
		if (done != null) {
			return done;
		}
		ResourceLocation out = build(src);
		CACHE.put(src, out);
		return out;
	}

	private static ResourceLocation build(ResourceLocation src) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(src);
		if (res.isEmpty()) {
			return src;
		}
		try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
			NativeImage green = new NativeImage(img.getWidth(), img.getHeight(), true);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					green.setPixelRGBA(x, y, greenOf(img.getPixelRGBA(x, y)));
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/beast_green/" + src.getNamespace() + "/" + src.getPath());
			mc.getTextureManager().register(id, new DynamicTexture(green));
			return id;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("Beast Boy: couldn't make a green copy of {}", src, e);
			return src;
		}
	}

	/** One pixel (NativeImage's ABGR) recoloured: brightness -> Beast Boy green, alpha kept. */
	static int greenOf(int abgr) {
		int a = (abgr >>> 24) & 255;
		int b = (abgr >> 16) & 255;
		int g = (abgr >> 8) & 255;
		int r = abgr & 255;
		float lum = (0.3f * r + 0.59f * g + 0.11f * b) / 255f;
		// lift the darks a little so the shape still reads, keep a hint of the original warmth
		float l = 0.18f + 0.82f * lum;
		int nr = clamp(Math.round(255 * l * 0.36f + r * 0.06f));
		int ng = clamp(Math.round(255 * l * 0.92f + 10));
		int nb = clamp(Math.round(255 * l * 0.26f + b * 0.04f));
		return (a << 24) | (nb << 16) | (ng << 8) | nr;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (ResourceLocation id : CACHE.values()) {
			if (id.getNamespace().equals(ProjectHeroMod.MOD_ID)) {
				mc.getTextureManager().release(id);
			}
		}
		CACHE.clear();
	}
}
