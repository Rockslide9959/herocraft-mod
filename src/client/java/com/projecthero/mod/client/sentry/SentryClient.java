package com.projecthero.mod.client.sentry;

import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.5 Sentry &amp; the Void, the client side (called once from {@code ProjectHeroModClient}): the suit / Void Form layer, the HUD
 * and the Void's thresholds, the effects (the Sunbeam and every {@code SentryFxPayload}), the takeover view (camera, input, the Shame
 * Rooms, the dark sky) and the block render layers. X, V and the rest go through the shared ability keys; H stays the power selector.
 * v0.17.5 rework: the Sentry Suit's cape ({@link SentryCapeLayer}), the suit / fade textures ({@link SentrySuitTextures}), the Let It
 * Out trial screen ({@link LetItOutScreen}, opened when the server starts a trial) and double-tap-Space flight (sent from
 * {@code ProjectHeroModClient.handleDoubleJump}).
 */
public final class SentryClient {
	/** The trial (its start time) the screen was last opened for: never reopened once reported. */
	private static long trialShown;

	private SentryClient() {
	}

	/** The server started a Let It Out trial: open "Hold the Light" (once per trial). */
	private static void openTrial(net.minecraft.client.Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		com.projecthero.mod.sentry.data.SentryState s = com.projecthero.mod.sentry.Sentry.peek(mc.player);
		if (com.projecthero.mod.sentry.LetItOut.running(s) && s.letOutTrialStart != trialShown && !(mc.screen instanceof LetItOutScreen)) {
			if (mc.screen != null) {
				mc.screen.onClose();
			}
			trialShown = s.letOutTrialStart;
			mc.setScreen(new LetItOutScreen(s.letOutTrialStart));
		}
	}

	/** Double-tap Space as the Sentry (either form, never in the takeover): take off / land (the server re-checks). */
	public static boolean wantsFlightToggle(net.minecraft.client.player.LocalPlayer player) {
		return player != null && com.projecthero.mod.sentry.Sentry.hasPower(player) && !com.projecthero.mod.sentry.Sentry.inTakeover(player)
				&& (!player.onGround() || com.projecthero.mod.sentry.Sentry.isFlying(player));
	}

	/** Round 3: plain H belongs to the Sentry (Let It Out / back), never in the takeover (the keys are dead then) -- Shift+H stays the wheel. */
	public static boolean ownsH(net.minecraft.client.player.LocalPlayer player) {
		return player != null && com.projecthero.mod.sentry.Sentry.hasPower(player) && !com.projecthero.mod.sentry.Sentry.inTakeover(player)
				&& sentryActive(player);
	}

	/** The Sentry is the power the keys drive (no mutation is selected over it). */
	private static boolean sentryActive(net.minecraft.client.player.LocalPlayer player) {
		var exp = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return exp == null || exp.activePower.isEmpty();
	}

	public static void pressH() {
		ClientPlayNetworking.send(new com.projecthero.mod.sentry.network.SentryActionPayload(
				com.projecthero.mod.sentry.network.SentryActionPayload.LET_IT_OUT, false));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new com.projecthero.mod.sentry.network.SentryActionPayload(
				com.projecthero.mod.sentry.network.SentryActionPayload.TOGGLE_FLIGHT, false));
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new SentrySuitLayer(playerRenderer));
			}
		});
		com.projecthero.mod.client.render.FlowingCapeLayer.register(SentryCapeLayer::new);
		SentrySuitTextures.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(SentryClient::openTrial);
		HudRenderCallback.EVENT.register(SentryHud::render);
		HudRenderCallback.EVENT.register(VoidTakeoverClient::renderHud);
		HudRenderCallback.EVENT.register(SentryFxClient::renderFlash);
		ClientPlayNetworking.registerGlobalReceiver(SentryFxPayload.TYPE, (payload, context) -> SentryFxClient.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(SentryFxClient::render);
		// rework 2: blocks fading to black under the Void
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.sentry.network.VoidDecayPayload.TYPE,
				(payload, context) -> VoidDecayClient.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(VoidDecayClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(VoidDecayClient::render);
		ClientTickEvents.START_CLIENT_TICK.register(VoidTakeoverClient::startTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SentryFxClient.reset();
			VoidTakeoverClient.reset();
			VoidDecayClient.reset();
			trialShown = 0L;
		});
		BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.sentry.block.SentryBlocks.BURNT_SHADOW, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.sentry.block.SentryBlocks.SERUM_VAT, RenderType.cutout());
	}
}
