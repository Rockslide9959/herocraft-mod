package com.projecthero.mod.client.thor;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.14.16: registers Thor's cape ({@link ThorCapeLayer}) on both player renderers (wide + slim). */
public final class ThorCapeClient {
	/** The live layer instances (weak: a resource reload rebuilds the renderers), so their easing can be dropped on disconnect. */
	private static final Set<ThorCapeLayer> LAYERS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private ThorCapeClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				ThorCapeLayer layer = new ThorCapeLayer(playerRenderer);
				LAYERS.add(layer);
				helper.register(layer);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			synchronized (LAYERS) {
				LAYERS.forEach(ThorCapeLayer::clear);
			}
		});
	}
}
