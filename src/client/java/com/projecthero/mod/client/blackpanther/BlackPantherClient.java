package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.entity.BlackPantherEntityTypes;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.network.BlackPantherActionPayload;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.17.3: Black Panther's client side -- the suit layer, HUD, shockwave, thrown things and the H request. */
public final class BlackPantherClient {
	private BlackPantherClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new BlackPantherLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(BlackPantherHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(BlackPantherEffectsRenderer::render);
		EntityRendererRegistry.register(BlackPantherEntityTypes.VIBRANIUM_DAGGER, VibraniumDaggerRenderer::new);
		EntityRendererRegistry.register(BlackPantherEntityTypes.KIMOYO_BEAD, ThrownItemRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(BlackPantherItems.HERB_BLOCK, RenderType.cutout());
		ClientPlayNetworking.registerGlobalReceiver(PantherSensesPayload.TYPE, (payload, context) -> PantherSensesClient.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PantherSensesClient.reset());
	}

	public static boolean ownsH(LocalPlayer player) {
		return BlackPanther.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new BlackPantherActionPayload(BlackPantherActionPayload.Action.TOGGLE_SUIT));
	}
}
