package com.projecthero.mod.client.miraculous.viperion;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Viperion's client side -- the suit layer (with the Lyre), the poses, the Second Chance HUD and its screen
 * flashes ({@link ViperionHud}) and Heat Sense ({@link ViperionHeatSense}). First person: the sleeve, the Snake Bracelet
 * and the Lyre in the fist are {@code ViperionFirstPersonArmMixin}, the bare arm instead of the held item while the Lyre
 * is in it {@code ViperionItemInHandMixin} / {@code ViperionItemInHandLayerMixin}; worn armour is hidden by
 * {@code ViperionArmorMixin}; Heat Sense's glow {@code ViperionHeatGlowMixin} / {@code ViperionHeatColorMixin}.
 */
public final class ViperionClient implements KwamiClientKit {
	private ViperionClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.SASS, new ViperionClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new ViperionLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(ViperionHud::render);
		ViperionHud.initialize(); // v0.16.7 (user): the Second Chance card, above the ability HUD
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is drawn over his own skin: while any of it shows, his jacket / sleeve / trouser layers go (the reveal
		// redraws them texel by texel); his hat layer -- his hair -- stays, the suit has no hood
		if (model instanceof PlayerModel<?> pm && ViperionRender.cover(player, 0f) > 0f) {
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		ViperionPose.apply(player, model);
	}

	/** False on purpose -- worn armour is hidden by {@code ViperionArmorMixin}; the second layer by {@link #applyPose}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}
}
