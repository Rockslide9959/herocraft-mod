package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.entity.BlackPantherEntityTypes;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.network.BlackPantherSuitPayload;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;
import com.projecthero.mod.client.render.FlowingCapeLayer;

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
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3: Black Panther's client side -- the suit layer, HUD, shockwave and thrown things. v0.17.7 (user: "equips it and presses H
 * to make it transform into the full suit"): H is his only while the necklace is in play, exactly like the Flash Ring
 * ({@code FlashClient.ownsH}): suited, wearing the necklace, or carrying one. Otherwise H stays the power selector, and Shift+H
 * always opens the power wheel.
 */
public final class BlackPantherClient {
	private BlackPantherClient() {
	}

	/** Whether plain H belongs to the Black Panther's necklace: suited, wearing the necklace, or carrying one in the inventory. */
	public static boolean ownsH(LocalPlayer player) {
		if (!BlackPanther.hasPower(player)) {
			return false;
		}
		if (BlackPanther.suited(player) || BlackPanther.wearsNecklace(player)) {
			return true;
		}
		return player.getInventory().contains(new ItemStack(BlackPantherItems.NECKLACE));
	}

	/** H: the suit out of the necklace, or back in (the server re-checks). */
	public static void pressH() {
		ClientPlayNetworking.send(new BlackPantherSuitPayload());
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new BlackPantherLayer(playerRenderer));
				// v0.17.7: the comics suit's high collar, a model (only while the suit is out)
				helper.register(new BlackPantherCollarLayer(playerRenderer));
			}
		});
		// v0.17.7: the comics suit's long cape, on the shared cloth (only while the suit is out)
		FlowingCapeLayer.register(BlackPantherCapeLayer::new);
		HudRenderCallback.EVENT.register(BlackPantherHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(BlackPantherEffectsRenderer::render);
		EntityRendererRegistry.register(BlackPantherEntityTypes.VIBRANIUM_DAGGER, VibraniumDaggerRenderer::new);
		EntityRendererRegistry.register(BlackPantherEntityTypes.KIMOYO_BEAD, ThrownItemRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(BlackPantherItems.HERB_BLOCK, RenderType.cutout());
		ClientPlayNetworking.registerGlobalReceiver(PantherSensesPayload.TYPE, (payload, context) -> PantherSensesClient.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PantherSensesClient.reset());
	}
}
