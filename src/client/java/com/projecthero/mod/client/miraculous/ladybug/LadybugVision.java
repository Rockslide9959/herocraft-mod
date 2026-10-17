package com.projecthero.mod.client.miraculous.ladybug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

/**
 * v0.16.6: Ladybug Vision -- for the Ladybug who cast the Lucky Charm only, the world goes grey for
 * {@code LadybugConfig.VISION_TICKS}. A post chain of vanilla's {@code color_convolve} program at saturation 0
 * ({@code shaders/post/ladybug_vision.json}) run at the END of world rendering: the hand, the GUI and the entity
 * outlines are drawn after it, so the red outlines on the charm and the threats ({@link LadybugGlowClient}) -- and
 * the charm in her hand -- stay in colour.
 */
public final class LadybugVision {
	private static PostChain chain;
	private static boolean failed;
	private static int width;
	private static int height;

	private LadybugVision() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || failed || !LadybugKeys.vision(mc.player)) {
			return;
		}
		if (chain == null) {
			try {
				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
						ProjectHeroMod.id("shaders/post/ladybug_vision.json"));
			} catch (Exception e) {
				ProjectHeroMod.LOGGER.warn("[ladybug] Ladybug Vision post effect failed to load", e);
				failed = true;
				return;
			}
			width = -1;
		}
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		if (w != width || h != height) {
			chain.resize(w, h);
			width = w;
			height = h;
		}
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		chain.process(context.tickCounter().getGameTimeDeltaTicks());
		mc.getMainRenderTarget().bindWrite(true);
		RenderSystem.enableDepthTest();
	}

	/** Resource reload / leaving the world: drop the chain (it is rebuilt on the next use). */
	static void reset() {
		if (chain != null) {
			chain.close();
			chain = null;
		}
		failed = false;
	}
}
