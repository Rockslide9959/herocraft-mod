package com.projecthero.mod.client.spin;

import com.projecthero.mod.spin.entity.SpinEntities;
import com.projecthero.mod.spin.network.SpinFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 the Spin (the Spin): the client side, called once from {@code ProjectHeroModClient}: the HUD and the Golden Rectangle,
 * the thrown ball's renderer, the held Steel Ball item's 3D renderer, the spun / aged layer on every creature, the effects payload and
 * the world-render effects, and the per-tick trail, view drift and loop sounds. His name tag and Scan outlines are mixins
 * ({@code SpinNameTagMixin}, {@code SpinScanGlowMixin}, {@code SpinScanColorMixin}). H stays the power
 * selector and N does nothing for him, so there is no key handling here: every move goes through the ability slots.
 */
public final class SpinClient {
	private SpinClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(SpinHud::render);
		GyroCapeLayer.initialize();
		EntityRendererRegistry.register(SpinEntities.STEEL_BALL, SteelBallRenderer::new);
		// v0.17.3 round 2: the balls are items he holds, drawn as the 3D ball (no automatic hand / belt balls any more)
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
				com.projecthero.mod.spin.SpinItems.STEEL_BALL, new SteelBallItemRenderer());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			helper.register(new SpinMarkLayer(entityRenderer));
			// v0.17.4: Gyro's holster trousers, with the balls he carries
			if (entityRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
				helper.register(new SpinHolsterLayer(pr));
				// v0.17.4: Gyro's cape, a real cloth panel while the coat is on
				helper.register(new GyroCapeLayer(pr));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SpinFxPayload.TYPE, (payload, context) -> SpinFx.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(SpinFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpinFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpinFx.reset());
	}
}
