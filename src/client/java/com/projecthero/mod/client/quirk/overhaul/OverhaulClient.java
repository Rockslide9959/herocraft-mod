package com.projecthero.mod.client.quirk.overhaul;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Overhaul Quirk's client side -- the keyframed poses ({@link OverhaulPose}) and the Fusion form's stone
 * plates and extra stone arms ({@link OverhaulFusionLayer}). The costume itself is drawn by the core's costume layer
 * ({@code textures/entity/quirk/overhaul_*.png}); the move effects are server particles every viewer sees.
 */
public final class OverhaulClient implements QuirkClientKit {
	private OverhaulClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.OVERHAUL, new OverhaulClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new OverhaulFusionLayer(playerRenderer));
			}
		});
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		OverhaulPose.apply(player, model);
	}

	/** The costume (mask, fur collar, jacket) replaces the wearer's own second layer while it is on. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return Quirks.costumeOn(player);
	}
}
