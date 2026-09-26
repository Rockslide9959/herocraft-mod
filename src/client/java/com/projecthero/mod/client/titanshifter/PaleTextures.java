package com.projecthero.mod.client.titanshifter;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.12.39 -- the Emergency Titan is pale. Rather than shipping a second set of skins, the pale version of any Titan texture is
 * built once at runtime (washed out towards a bloodless grey-white) and registered as a dynamic texture.
 */
public final class PaleTextures {
	/** How far each channel is pulled towards the pale tone. */
	private static final float WASH = 0.62f;
	private static final int PALE_R = 226;
	private static final int PALE_G = 224;
	private static final int PALE_B = 230;

	private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

	private PaleTextures() {
	}

	/** The pale twin of {@code source}; falls back to {@code source} itself if it cannot be read. */
	public static ResourceLocation of(ResourceLocation source) {
		Minecraft mc = Minecraft.getInstance();
		TextureManager tm = mc.getTextureManager();
		ResourceLocation cached = CACHE.get(source);
		if (cached != null && tm.getTexture(cached, null) != null) {
			return cached;
		}
		try (InputStream in = mc.getResourceManager().getResourceOrThrow(source).open()) {
			NativeImage img = NativeImage.read(in);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int abgr = img.getPixelRGBA(x, y);
					int a = abgr >>> 24;
					if (a == 0) {
						continue;
					}
					int b = abgr >> 16 & 0xFF;
					int g = abgr >> 8 & 0xFF;
					int r = abgr & 0xFF;
					// keep a little of the original shading so the face and the muscle lines still read
					r = Math.round(r + (PALE_R - r) * WASH);
					g = Math.round(g + (PALE_G - g) * WASH);
					b = Math.round(b + (PALE_B - b) * WASH);
					img.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/pale/" + source.getPath().replace('/', '_').replace('.', '_'));
			tm.register(id, new DynamicTexture(img));
			CACHE.put(source, id);
			return id;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the pale texture for {}", source, e);
			CACHE.put(source, source);
			return source;
		}
	}
}
