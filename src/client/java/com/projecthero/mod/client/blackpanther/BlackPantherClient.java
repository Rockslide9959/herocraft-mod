package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.entity.BlackPantherEntityTypes;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.network.BlackPantherActionPayload;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;
import com.projecthero.mod.client.ModKeyBindings;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3: Black Panther's client side -- the suit layer, HUD, shockwave, thrown things and H. A TAP of H toggles the suit
 * (sent on the release); HOLDING it {@link BlackPantherConfig#NECKLACE_HOLD_TICKS} ticks takes the necklace off or puts it
 * back on (sent the moment the hold completes).
 */
public final class BlackPantherClient {
	/** Ticks H has been held since a Black Panther press; -1 = not watching, -2 = the hold already fired (wait for the release). */
	private static int hHeld = -1;

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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PantherSensesClient.reset();
			hHeld = -1;
		});
		ClientTickEvents.END_CLIENT_TICK.register(BlackPantherClient::tick);
	}

	public static boolean ownsH(LocalPlayer player) {
		return BlackPanther.hasPower(player);
	}

	/** H went down: start timing it (the tick decides tap or hold). */
	public static void pressH() {
		hHeld = 0;
	}

	/** How far the necklace hold has got, 0..1 (0 when nothing is being held). For the HUD. */
	public static float holdProgress() {
		return hHeld <= 0 ? 0f : Math.min(1f, hHeld / (float) BlackPantherConfig.NECKLACE_HOLD_TICKS);
	}

	private static void tick(Minecraft client) {
		if (hHeld == -1) {
			return;
		}
		boolean connected = client.player != null && client.getConnection() != null;
		boolean down = connected && ModKeyBindings.POWER_SELECT.isDown();
		if (hHeld == -2) {
			if (!down) {
				hHeld = -1;
			}
			return;
		}
		if (down) {
			hHeld++;
			if (hHeld >= BlackPantherConfig.NECKLACE_HOLD_TICKS) {
				ClientPlayNetworking.send(new BlackPantherActionPayload(BlackPantherActionPayload.Action.TOGGLE_NECKLACE));
				hHeld = -2;
			}
			return;
		}
		// let go before the hold completed: a tap -- the suit
		hHeld = -1;
		if (connected) {
			ClientPlayNetworking.send(new BlackPantherActionPayload(BlackPantherActionPayload.Action.TOGGLE_SUIT));
		}
	}
}
