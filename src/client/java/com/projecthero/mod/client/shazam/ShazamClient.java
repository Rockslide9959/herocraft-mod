package com.projecthero.mod.client.shazam;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.entity.ShazamEntities;
import com.projecthero.mod.shazam.network.ShazamFlightPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Shazam: the client side, called once from {@code ProjectHeroModClient}: the suit and cape layers, the HUD (with the
 * Eternity Strike darkening and Wisdom's crosshair hint), Wisdom of Solomon's world labels, the Wizard's renderer and the
 * double-tap-Space flight request (sent from {@code ProjectHeroModClient.handleDoubleJump}). The word is typed in ordinary chat
 * and handled by the server, so there is no key here; H stays the power selector and N does nothing for him. His bolts' thunder is
 * turned down by {@code ShazamBoltSoundMixin}.
 */
public final class ShazamClient {
	private ShazamClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new ShazamSuitLayer(playerRenderer));
			}
		});
		FlowingCapeLayer.register(ShazamCapeLayer::new);
		HudRenderCallback.EVENT.register(ShazamHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(ShazamWisdom::render);
		EntityRendererRegistry.register(ShazamEntities.WIZARD, WizardShazamRenderer::new);
		// v0.17.3 effects pass: his lightning as branching bolt geometry
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.shazam.network.ShazamFxPayload.TYPE,
				(payload, context) -> ShazamBoltFx.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(ShazamBoltFx::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ShazamBoltFx.reset());
	}

	/** Double-tap Space in the air while transformed: take off / drop out of flight (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && Shazam.transformed(player) && (!player.onGround() || Shazam.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new ShazamFlightPayload());
	}
}
