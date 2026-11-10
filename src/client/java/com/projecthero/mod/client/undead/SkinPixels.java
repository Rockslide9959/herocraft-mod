package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * v0.17.3: which texels of a skin are see-through. Read back once per skin texture from the GPU (the player's skin isn't
 * kept on the client as an image), so Undead's pixel bursts and regrowth skip the empty texels of the overlay layer instead of
 * flinging invisible cubes. Render thread only. Forgotten on disconnect.
 */
public final class SkinPixels {
	private static final Map<ResourceLocation, boolean[]> OPAQUE = new HashMap<>();
	private static final Map<ResourceLocation, Long> FAILED = new HashMap<>();

	private SkinPixels() {
	}

	public static void reset() {
		OPAQUE.clear();
		FAILED.clear();
	}

	/** Whether 64x64 skin texel (u, v) of {@code tex} has any colour (true when the skin can't be read). */
	public static boolean shows(ResourceLocation tex, int u, int v) {
		boolean[] o = opaque(tex);
		return o == null || o[(v & 63) * 64 + (u & 63)];
	}

	private static boolean[] opaque(ResourceLocation tex) {
		boolean[] o = OPAQUE.get(tex);
		if (o != null) {
			return o;
		}
		if (!RenderSystem.isOnRenderThread()) {
			return null;
		}
		long now = System.currentTimeMillis();
		Long failed = FAILED.get(tex);
		if (failed != null && now - failed < 3000L) {
			return null;
		}
		try {
			AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
			if (t == null) {
				FAILED.put(tex, now);
				return null;
			}
			t.bind();
			int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
			int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
			if (w < 64 || h < 32) {
				FAILED.put(tex, now);
				return null;
			}
			boolean[] out = new boolean[64 * 64];
			try (NativeImage img = new NativeImage(w, h, false)) {
				img.downloadTexture(0, false);
				int scale = w / 64;
				for (int v = 0; v < 64; v++) {
					for (int u = 0; u < 64; u++) {
						int py = v * scale;
						if (py >= h) {
							continue;
						}
						// any of the scale x scale pixels under the texel with colour counts
						boolean any = false;
						for (int dy = 0; dy < scale && !any; dy++) {
							for (int dx = 0; dx < scale && !any; dx++) {
								any = ((img.getPixelRGBA(u * scale + dx, py + dy) >>> 24) & 0xFF) > 8;
							}
						}
						out[v * 64 + u] = any;
					}
				}
			}
			OPAQUE.put(tex, out);
			return out;
		} catch (Throwable e) {
			FAILED.put(tex, now);
			return null;
		}
	}
}
