package com.projecthero.mod.client.quirk.darkshadow;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Dark Shadow Quirk's client side -- the creature in the world ({@link DarkShadowRender}, every viewer),
 * the Black Ankh armour layer ({@link DarkShadowAnkhLayer}) and Tokoyami's poses ({@link DarkShadowPose}). The costume
 * itself ({@code textures/entity/quirk/dark_shadow_*.png}) is drawn by the core's costume layer.
 */
public final class DarkShadowClient implements QuirkClientKit {
	private DarkShadowClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.DARK_SHADOW, new DarkShadowClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new DarkShadowAnkhLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(DarkShadowRender::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DarkShadowRender.reset());
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		DarkShadowPose.apply(player, model);
	}

	// v0.16.7: the bird head (and hiding the wearer's hat under it) is the core's -- QuirkCostume.birdHead
}
