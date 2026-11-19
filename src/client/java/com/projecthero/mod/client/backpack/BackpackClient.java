package com.projecthero.mod.client.backpack;

import com.projecthero.mod.backpack.BackpackItem;
import com.projecthero.mod.backpack.BackpackMod;
import com.projecthero.mod.backpack.OpenBackpackPayload;
import com.projecthero.mod.client.ModKeyBindings;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.Item;

/** v0.17.5 backpacks, client side: the screen, the worn layer, the tinted icons and the Open Backpack key. */
public final class BackpackClient {
	private BackpackClient() {
	}

	public static void initialize() {
		MenuScreens.register(BackpackMod.MENU, BackpackScreen::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new BackpackLayer(playerRenderer));
			}
		});
		// layer 0 is the canvas (dye or tier colour), layer 1 the tier's straps and metal, untinted
		ColorProviderRegistry.ITEM.register((stack, layer) -> layer == 0 ? 0xFF000000 | BackpackItem.color(stack) : -1,
				BackpackMod.all().toArray(new Item[0]));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (ModKeyBindings.OPEN_BACKPACK.consumeClick()) {
				if (mc.player != null && mc.screen == null && ClientPlayNetworking.canSend(OpenBackpackPayload.TYPE)) {
					ClientPlayNetworking.send(new OpenBackpackPayload());
				}
			}
		});
	}
}
