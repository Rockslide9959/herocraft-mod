package com.projecthero.mod.client.omnitrix.alien.spidermonkey;

import java.io.IOException;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * v0.17.2 (user: "make spidermonkeys arms slim"): Spidermonkey's 64x64 WIDE-arm skin re-laid for the SLIM (3 px) arm model, built
 * in memory from {@code spidermonkey.png} every time textures load (so edits to that skin carry over, F3+T included). Each arm
 * face 4 px across keeps 3 of its columns (the outer edge, the shading and the inner edge -- the second column on the right arm's
 * front, mirrored on the left), and the inner side and back slide one column left to where the slim arm reads them. Arm and
 * sleeve layers both.
 */
public final class SpidermonkeySlimSkin extends SimpleTexture {
	private static boolean registered;

	private SpidermonkeySlimSkin(ResourceLocation wide) {
		super(wide);
	}

	/** The slim copy of {@code wide}, registered under {@code slim} on first use. */
	public static ResourceLocation get(ResourceLocation wide, ResourceLocation slim) {
		if (!registered) {
			registered = true;
			Minecraft.getInstance().getTextureManager().register(slim, new SpidermonkeySlimSkin(wide));
		}
		return slim;
	}

	@Override
	protected TextureImage getTextureImage(ResourceManager resources) {
		try (TextureImage wide = TextureImage.load(resources, location)) {
			NativeImage src = wide.getImage();
			NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
			out.copyFrom(src);
			if (src.getWidth() == 64 && src.getHeight() == 64) {
				slim(src, out, 40, 16, true); // right arm
				slim(src, out, 40, 32, true); // right sleeve
				slim(src, out, 32, 48, false); // left arm
				slim(src, out, 48, 48, false); // left sleeve
			}
			return new TextureImage(null, out);
		} catch (IOException e) {
			return new TextureImage(e);
		}
	}

	/** One arm's 16x16 UV block at (u, v): wide (4 px) layout in {@code src} -> slim (3 px) layout in {@code out}. */
	private static void slim(NativeImage src, NativeImage out, int u, int v, boolean right) {
		for (int y = v; y < v + 16; y++) {
			for (int x = u; x < u + 16; x++) {
				out.setPixelRGBA(x, y, 0);
			}
		}
		// which 3 of the 4 columns a front-like face keeps (the right arm drops its 2nd, the left arm -- mirrored -- its 3rd)
		int[] front = right ? new int[] { 0, 2, 3 } : new int[] { 0, 1, 3 };
		int[] back = right ? new int[] { 0, 1, 3 } : new int[] { 0, 2, 3 };
		for (int i = 0; i < 3; i++) {
			for (int y = 0; y < 4; y++) {
				out.setPixelRGBA(u + 4 + i, v + y, src.getPixelRGBA(u + 4 + front[i], v + y)); // top
				out.setPixelRGBA(u + 7 + i, v + y, src.getPixelRGBA(u + 8 + front[i], v + y)); // bottom
			}
		}
		for (int y = v + 4; y < v + 16; y++) {
			for (int i = 0; i < 4; i++) {
				out.setPixelRGBA(u + i, y, src.getPixelRGBA(u + i, y)); // outer side
				out.setPixelRGBA(u + 7 + i, y, src.getPixelRGBA(u + 8 + i, y)); // inner side
			}
			for (int i = 0; i < 3; i++) {
				out.setPixelRGBA(u + 4 + i, y, src.getPixelRGBA(u + 4 + front[i], y)); // front
				out.setPixelRGBA(u + 11 + i, y, src.getPixelRGBA(u + 12 + back[i], y)); // back
			}
		}
	}
}
