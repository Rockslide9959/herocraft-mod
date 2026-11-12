package com.projecthero.mod.client.vision;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.network.VisionActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Vision: the client side, called once from {@code ProjectHeroModClient}: the suit layer (with the Human Hologram's build, the
 * phase shell and the density tint), the cape, the HUD, the beams, and the double-tap-Space flight request (sent from
 * {@code ProjectHeroModClient.handleDoubleJump}). Every move goes through the ability slots; H stays the power selector and N does
 * nothing for him. The suited first-person arm is {@code VisionFirstPersonArmMixin}; the wall-phase shove is skipped by
 * {@code VisionPhaseClientMixin}.
 */
public final class VisionClient {
	private VisionClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new VisionSuitLayer(playerRenderer));
			}
		});
		FlowingCapeLayer.register(VisionCapeLayer::new);
		HudRenderCallback.EVENT.register(VisionHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(VisionBeamRenderer::render);
		// the Cradle's glass lid
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.vision.VisionCradle.CRADLE,
				net.minecraft.client.renderer.RenderType.cutout());
	}

	/** Double-tap Space in the air as Vision: take off / land (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && Vision.hasPower(player) && (!player.onGround() || Vision.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new VisionActionPayload(VisionActionPayload.TOGGLE_FLIGHT));
	}
}
