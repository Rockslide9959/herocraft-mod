package com.projecthero.mod.client.miraculous.catnoir;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.catnoir.entity.CatNoirEntityTypes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir's client side -- the suit layer (skin, ears, belt-tail, bell, the Baton), the poses, the thrown
 * baton's renderer, the long baton in the world (Extend / Vault), the CATACLYSM crackle and the Night Vision outlines
 * (mixins). Registered with the Miraculous core from {@code MiraculousClient.initialize}.
 */
public final class CatNoirClient implements KwamiClientKit {
	private CatNoirClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.PLAGG, new CatNoirClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new CatNoirLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(CatNoirEntityTypes.THROWN_BATON, CatBatonRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(CatNoirWorldFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(CatNoirWorldFx::tickParticles);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		CatNoirPose.apply(player, model);
	}

	@Override
	public boolean hidesSkinOverlay(Player player) {
		return CatNoirRender.hidesSkinOverlay(player);
	}
}
