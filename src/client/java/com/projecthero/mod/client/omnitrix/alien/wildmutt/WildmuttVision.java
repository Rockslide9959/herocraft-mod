package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

/**
 * v0.17.2 (user: "can we start working on making wildmutts blind vision"): what Wildmutt "sees", on his own client. He has no
 * eyes -- he gets about by smell, hearing / echolocation and the heat-sensing gills on his neck (a cold that blocks his nostrils
 * leaves him blind). So while you are him the world goes near-black: every {@link #PERIOD} ticks an echolocation pulse sweeps
 * out from him and lights the shapes it passes in pale sonar blue, fading behind it; within a few blocks he feels dim grey
 * shapes. His scent outlines ({@link WildmuttSense}) are drawn after this pass, so creatures stand out in their scent colours.
 * Sniffing carries the echo further and clearer; in water (his nostrils shut) it barely carries.
 * <p>A post chain ({@code shaders/post/wildmutt_sense.json}, program {@code minecraft:projecthero_blindsense} -- vanilla only
 * looks up post programs in its own namespace) run at the END of world rendering, like {@code LadybugVision}.
 */
public final class WildmuttVision {
	/** Ticks between echoes. */
	public static final int PERIOD = 36;
	/** How fast an echo front travels (blocks per tick). */
	public static final float SPEED = 2.0f;

	private static PostChain chain;
	private static boolean failed;
	private static int width;
	private static int height;

	private WildmuttVision() {
	}

	public static void init() {
		WorldRenderEvents.END.register(WildmuttVision::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/** Whether this client is Wildmutt, looking through his own head. */
	public static boolean active(Minecraft mc) {
		return mc.player != null && mc.getCameraEntity() == mc.player && AlienKit.activeKit(mc.player) instanceof WildmuttKit;
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (failed || !active(mc)) {
			return;
		}
		WildmuttKit kit = (WildmuttKit) AlienKit.activeKit(mc.player);
		if (chain == null) {
			try {
				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
						ProjectHeroMod.id("shaders/post/wildmutt_sense.json"));
			} catch (Exception e) {
				ProjectHeroMod.LOGGER.warn("[wildmutt] blind sense post effect failed to load", e);
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
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float t = (mc.player.tickCount % PERIOD) + partial;
		float reach = (float) Math.max(6.0, Math.min(64.0, kit.senseRange(mc.player)));
		boolean sniff = kit.flag(mc.player, WildmuttKit.F_SNIFF);
		chain.setUniform("Pulse", t * SPEED * (reach / 24f) - 1f);
		chain.setUniform("Reach", reach + 8f);
		chain.setUniform("Far", mc.gameRenderer.getDepthFar());
		chain.setUniform("Sniff", sniff ? 1f : 0f);
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
