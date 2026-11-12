package com.projecthero.mod.client.raven;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.entity.RavenEntities;
import com.projecthero.mod.raven.network.RavenActionPayload;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Raven: the client side, called once from {@code ProjectHeroModClient}: the HUD (Dark Energy Slab, Composure Hairline, the
 * keys), the aura overlay (white casting eyes, four red eyes at Rage, the Wrath's shadow shell) and the cloak + hood, the three entity
 * renderers, the effects payload and the world-render effects (claws, tentacles, the dome, the wave...), the hand wisps, the
 * double-tap-Space flight request and a left click firing a Debris Storm block. The poses are {@code RavenPoseMixin}. H stays the
 * power selector and N does nothing for her.
 */
public final class RavenClient {
	private RavenClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(RavenHud::render);
		EntityRendererRegistry.register(RavenEntities.DARK_BOLT, DarkBoltRenderer::new);
		EntityRendererRegistry.register(RavenEntities.SOUL_SELF, SoulSelfRenderer::new);
		EntityRendererRegistry.register(RavenEntities.DEBRIS, RavenDebrisRenderer::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer pr) {
				helper.register(new RavenAuraLayer(pr));
			}
		});
		FlowingCapeLayer.register(RavenCloakLayer::new);
		ClientPlayNetworking.registerGlobalReceiver(RavenFxPayload.TYPE, (payload, context) -> RavenFx.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(RavenFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(RavenFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RavenFx.reset());
		// a left click while Debris Storm blocks orbit her fires one (instead of swinging)
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			var s = Raven.peek(player);
			if (clickCount > 0 && s != null && s.hasPower && s.debris > 0 && client.screen == null) {
				ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.FIRE_DEBRIS));
				return true;
			}
			return false;
		});
	}

	/** Double-tap Space in the air as Raven: take off / land (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && Raven.hasPower(player) && !Raven.meditating(player) && (!player.onGround() || Raven.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.TOGGLE_FLIGHT));
	}

	/** Meditation's tune: no movement at all, she just floats. */
	public static DirectionalFlightModel.Tune still() {
		DirectionalFlightModel.Tune k = DirectionalFlightModel.kryptonian(false, false);
		return new DirectionalFlightModel.Tune(0.0, 0.0, 0.0, k.acceleration(), k.reverse(), k.idle(), k.maxHorizontal(), 0.0, false, 0, false);
	}
}
