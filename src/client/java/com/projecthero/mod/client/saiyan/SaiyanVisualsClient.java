package com.projecthero.mod.client.saiyan;

import com.projecthero.mod.client.saiyan.aura.SaiyanAuraParticles;
import com.projecthero.mod.client.saiyan.aura.SaiyanAuraRenderer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3: the Saiyan visuals' client side, called from {@link SaiyanClient#initialize}: the aura layer on every player, the
 * aura's client tick (charge easing, floating debris, first-person sparks), and the world-render bracket that keeps the aura
 * out of GUI previews of the player (the inventory screen).
 */
public final class SaiyanVisualsClient {
	private SaiyanVisualsClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new SaiyanLayer(playerRenderer));
			}
		});
		WorldRenderEvents.START.register(ctx -> SaiyanAuraRenderer.inWorld = true);
		WorldRenderEvents.END.register(ctx -> SaiyanAuraRenderer.inWorld = false);
		// v0.17.4: the local player's own 3D aura round the first-person camera (the render layer skips him then)
		WorldRenderEvents.AFTER_ENTITIES.register(SaiyanAuraRenderer::renderFirstPerson);
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanAuraParticles::tick);
		// v0.17.3: the tail's spring chain (Spidermonkey's rules) for every Saiyan in view; pruned, cleared with the level
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanTailRenderer::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			SaiyanFx.clearAll();
			SaiyanAuraParticles.clear();
			SaiyanTailRenderer.reset();
		});
	}
}
