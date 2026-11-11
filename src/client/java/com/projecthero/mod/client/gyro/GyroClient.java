package com.projecthero.mod.client.gyro;

import com.projecthero.mod.gyro.entity.GyroEntities;
import com.projecthero.mod.gyro.network.GyroFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Gyro Zeppeli (the Spin): the client side, called once from {@code ProjectHeroModClient}: the HUD and the Golden Rectangle,
 * the thrown ball's renderer, the hat-and-balls layer on players, the spun / aged layer on every creature, the effects payload and
 * the world-render effects, and the per-tick trail, view drift and loop sounds. His skin, name tag and Scan outlines are mixins
 * ({@code GyroSkinMixin}, {@code GyroNameTagMixin}, {@code GyroScanGlowMixin}, {@code GyroScanColorMixin}). H stays the power
 * selector and N does nothing for him, so there is no key handling here: every move goes through the ability slots.
 */
public final class GyroClient {
	private GyroClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(GyroHud::render);
		EntityRendererRegistry.register(GyroEntities.STEEL_BALL, SteelBallRenderer::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new GyroLayer(playerRenderer));
			}
			helper.register(new GyroMarkLayer(entityRenderer));
		});
		ClientPlayNetworking.registerGlobalReceiver(GyroFxPayload.TYPE, (payload, context) -> GyroFx.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(GyroFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(GyroFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GyroFx.reset());
	}
}
